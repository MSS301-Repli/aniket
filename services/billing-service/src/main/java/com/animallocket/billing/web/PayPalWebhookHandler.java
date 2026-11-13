package com.animallocket.billing.web;

import com.animallocket.billing.domain.Store;
import com.animallocket.billing.service.BillingService;
import com.animallocket.billing.service.CheckoutService;
import com.animallocket.billing.store.PayPalClient;
import com.animallocket.billing.store.PayPalProperties;
import com.animallocket.billing.store.StoreException;
import com.animallocket.common.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Webhook PayPal. Thanh toán thành công đã được chốt ở bước quay về của trình duyệt ({@link CheckoutService#complete});
 * webhook chỉ bù những thứ trình duyệt không biết:
 * <ul>
 *   <li>{@code PAYMENT.CAPTURE.COMPLETED}: chốt nốt phiên nếu người dùng không quay về, hoặc khoản PENDING vừa hoàn tất;</li>
 *   <li>{@code PAYMENT.CAPTURE.REFUNDED}: hoàn tiền toàn phần → thu hồi quyền lợi (hoàn một phần thì giữ);</li>
 *   <li>{@code PAYMENT.CAPTURE.REVERSED}: chargeback → thu hồi quyền lợi.</li>
 * </ul>
 * Chữ ký được PayPal xác nhận trước khi làm bất cứ điều gì. Nội dung sự kiện không quyết định quyền lợi: với COMPLETED, trạng
 * thật luôn hỏi lại PayPal; với hoàn tiền chỉ dùng capture id để tìm giao dịch đã có.
 */
@Component
public class PayPalWebhookHandler {
    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookHandler.class);
    private static final Pattern EVENT_ID = Pattern.compile("^[A-Za-z0-9_.\\-]{1,80}$");
    private static final Pattern PAYPAL_ID = Pattern.compile("^[A-Za-z0-9_\\-]{1,100}$");

    private final ObjectProvider<PayPalClient> paypal;
    private final PayPalProperties props;
    private final CheckoutService checkout;
    private final BillingService billing;
    private final WebhookProcessor processor;

    public PayPalWebhookHandler(ObjectProvider<PayPalClient> paypal, PayPalProperties props, CheckoutService checkout,
                                BillingService billing, WebhookProcessor processor) {
        this.paypal = paypal;
        this.props = props;
        this.checkout = checkout;
        this.billing = billing;
        this.processor = processor;
    }

    /** @return processed | duplicate | ignored */
    public String handle(PayPalClient.WebhookHeaders headers, JsonNode body) {
        PayPalClient client = paypal.getIfAvailable();
        if (client == null || props.getWebhookId().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED", "Chưa cấu hình webhook PayPal");
        }
        if (body == null || !headers.complete()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Thiếu chữ ký webhook");
        }
        boolean authentic;
        try {
            authentic = client.verifyWebhook(headers, body);
        } catch (StoreException e) {
            if (e.kind() == StoreException.Kind.UNAVAILABLE) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORE_UNAVAILABLE", "Chưa xác minh được với PayPal, sẽ thử lại");
            }
            log.warn("PayPal từ chối yêu cầu xác minh webhook: {}", e.getMessage());
            authentic = false;
        }
        if (!authentic) {
            log.warn("Từ chối webhook PayPal: chữ ký không hợp lệ");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Chữ ký không hợp lệ");
        }

        String eventId = body.path("id").asText("");
        if (!EVENT_ID.matcher(eventId).matches()) {
            throw ApiException.badRequest("BAD_NOTIFICATION", "Thiếu hoặc sai id sự kiện");
        }
        String type = body.path("event_type").asText("");
        JsonNode resource = body.path("resource");
        return switch (type) {
            case "PAYMENT.CAPTURE.COMPLETED" -> completed(resource);
            case "PAYMENT.CAPTURE.REFUNDED" -> revoke(eventId, type, captureIdOfRefund(resource), resource, true);
            case "PAYMENT.CAPTURE.REVERSED" -> revoke(eventId, type, resource.path("id").asText(""), resource, false);
            default -> {
                log.info("Bỏ qua webhook PayPal {} (không đổi quyền lợi)", type);
                yield "ignored";
            }
        };
    }

    /** Chốt nốt phiên. Idempotent (capture và cấp quyền đều idempotent) nên không cần bảng khử trùng lặp. */
    private String completed(JsonNode resource) {
        String orderId = resource.path("supplementary_data").path("related_ids").path("order_id").asText("");
        if (!PAYPAL_ID.matcher(orderId).matches()) {
            log.warn("PAYMENT.CAPTURE.COMPLETED không có order id, bỏ qua");
            return "ignored";
        }
        CheckoutService.Outcome o = checkout.settleByOrderId(orderId);
        if (o == null) {
            log.info("Webhook PayPal cho đơn {} không thuộc phiên nào, bỏ qua", orderId);
            return "ignored";
        }
        return switch (o.state()) {
            case COMPLETED -> "processed";
            // PayPal chưa trả lời được: trả 503 để PayPal gửi lại sau.
            case UNAVAILABLE -> throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORE_UNAVAILABLE",
                    "Chưa chốt được giao dịch, sẽ thử lại");
            default -> {
                log.warn("Webhook PayPal COMPLETED cho đơn {} nhưng phiên ở trạng thái {}: {}", orderId, o.state(), o.message());
                yield "ignored";
            }
        };
    }

    /** Hoàn tiền / chargeback: thu hồi quyền lợi của giao dịch đã có. Việc ghi DB và dấu "đã nhận" nằm cùng một transaction. */
    private String revoke(String eventId, String type, String captureId, JsonNode resource, boolean requireFullRefund) {
        if (!PAYPAL_ID.matcher(captureId).matches()) {
            log.error("Webhook PayPal {} không xác định được capture id: kiểm tra thủ công", type);
            return "ignored";
        }
        if (requireFullRefund && !checkout.isFullRefund(captureId, resource.path("amount").path("value").asText(""),
                resource.path("amount").path("currency_code").asText(""))) {
            log.info("Hoàn tiền một phần (hoặc không rõ) cho capture {}: giữ nguyên quyền lợi", captureId);
            return "ignored";
        }
        Runnable action = () -> {
            if (!billing.onRefund(Store.PAYPAL, captureId)) {
                log.warn("Webhook PayPal {} cho capture {} không khớp giao dịch nào", type, captureId);
            }
        };
        return processor.process("paypal:" + eventId, Store.PAYPAL, type, action) ? "processed" : "duplicate";
    }

    /** Với sự kiện hoàn tiền, resource là khoản hoàn; capture gốc nằm ở liên kết rel=up (…/v2/payments/captures/{id}). */
    private static String captureIdOfRefund(JsonNode refund) {
        for (JsonNode link : refund.path("links")) {
            if ("up".equals(link.path("rel").asText(""))) {
                String href = link.path("href").asText("");
                return href.substring(href.lastIndexOf('/') + 1);
            }
        }
        return "";
    }
}
