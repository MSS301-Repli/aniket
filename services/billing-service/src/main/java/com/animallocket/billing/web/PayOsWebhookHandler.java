package com.animallocket.billing.web;

import com.animallocket.billing.service.CheckoutService;
import com.animallocket.billing.store.PayOsClient;
import com.animallocket.common.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Webhook payOS (báo có tiền vào). Chữ ký HMAC-SHA256 trên {@code data} được kiểm tra bằng checksum key trước khi làm gì khác.
 * Nội dung webhook không quyết định quyền lợi: chỉ lấy {@code orderCode} để tìm phiên, rồi
 * {@link CheckoutService#settlePayOsByOrderCode} hỏi lại payOS trạng thái thật và đối chiếu số tiền. Việc chốt phiên idempotent
 * nên không cần bảng khử trùng lặp; payOS chỉ cần mã 2xx.
 */
@Component
public class PayOsWebhookHandler {
    private static final Logger log = LoggerFactory.getLogger(PayOsWebhookHandler.class);

    private final ObjectProvider<PayOsClient> payos;
    private final CheckoutService checkout;

    public PayOsWebhookHandler(ObjectProvider<PayOsClient> payos, CheckoutService checkout) {
        this.payos = payos;
        this.checkout = checkout;
    }

    /** @return processed | ignored */
    public String handle(JsonNode body) {
        PayOsClient client = payos.getIfAvailable();
        if (client == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED", "Chưa cấu hình webhook payOS");
        }
        if (body == null || !body.path("data").isObject()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Thiếu dữ liệu hoặc chữ ký webhook");
        }
        JsonNode data = body.path("data");
        if (!client.verifyWebhookSignature(data, body.path("signature").asText(""))) {
            log.warn("Từ chối webhook payOS: chữ ký không hợp lệ");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Chữ ký không hợp lệ");
        }
        // Chữ ký đúng. Chỉ giao dịch thành công mới đáng chốt.
        if (!body.path("success").asBoolean(false) || !"00".equals(data.path("code").asText(""))) {
            log.info("Webhook payOS không phải giao dịch thành công (code={}), bỏ qua", data.path("code").asText(""));
            return "ignored";
        }
        long orderCode = data.path("orderCode").asLong(0);
        if (orderCode <= 0) {
            return "ignored";
        }
        CheckoutService.Outcome o = checkout.settlePayOsByOrderCode(orderCode);
        if (o == null) {
            // Gồm cả dữ liệu mẫu payOS gửi khi bạn gọi confirm-webhook.
            log.info("Webhook payOS cho orderCode {} không thuộc phiên nào, bỏ qua", orderCode);
            return "ignored";
        }
        return switch (o.state()) {
            case COMPLETED -> "processed";
            // payOS/mạng chưa trả lời được: 503 để payOS gửi lại sau.
            case UNAVAILABLE -> throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORE_UNAVAILABLE",
                    "Chưa chốt được giao dịch, sẽ thử lại");
            default -> {
                log.warn("Webhook payOS orderCode {} nhưng phiên ở trạng thái {}: {}", orderCode, o.state(), o.message());
                yield "ignored";
            }
        };
    }
}
