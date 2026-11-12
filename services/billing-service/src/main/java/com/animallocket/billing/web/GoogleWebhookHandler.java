package com.animallocket.billing.web;

import com.animallocket.billing.domain.Store;
import com.animallocket.billing.service.BillingService;
import com.animallocket.billing.store.GoogleProperties;
import com.animallocket.billing.store.GooglePushAuth;
import com.animallocket.billing.store.GoogleSubscriptionResolver;
import com.animallocket.billing.store.StoreException;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Real-time Developer Notifications (Google Pub/Sub push).
 * Xác thực OIDC token của Pub/Sub trước; nội dung thông báo CHỈ dùng để biết "token nào vừa đổi",
 * trạng thái thật luôn được hỏi lại Google trước khi áp dụng.
 */
@Component
public class GoogleWebhookHandler {
    private static final Logger log = LoggerFactory.getLogger(GoogleWebhookHandler.class);
    private static final Pattern MESSAGE_ID = Pattern.compile("^[A-Za-z0-9_\\-]{1,80}$");

    private final ObjectProvider<GooglePushAuth> authProvider;
    private final ObjectProvider<GoogleSubscriptionResolver> resolverProvider;
    private final GoogleProperties props;
    private final WebhookProcessor processor;
    private final BillingService billing;

    public GoogleWebhookHandler(ObjectProvider<GooglePushAuth> authProvider,
                                ObjectProvider<GoogleSubscriptionResolver> resolverProvider, GoogleProperties props,
                                WebhookProcessor processor, BillingService billing) {
        this.authProvider = authProvider;
        this.resolverProvider = resolverProvider;
        this.props = props;
        this.processor = processor;
        this.billing = billing;
    }

    /** @return processed | duplicate | ignored */
    public String handle(String authorization, JsonNode body) {
        GooglePushAuth auth = authProvider.getIfAvailable();
        if (auth == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED", "Chưa cấu hình xác thực Google");
        }
        try {
            auth.authenticate(authorization);
        } catch (SecurityException e) {
            log.warn("Từ chối webhook Google: {}", e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token không hợp lệ");
        }

        JsonNode message = body == null ? null : body.get("message");
        String messageId = message == null || !message.hasNonNull("messageId") ? null : message.get("messageId").asText();
        if (messageId == null || !MESSAGE_ID.matcher(messageId).matches() || !message.hasNonNull("data")) {
            throw ApiException.badRequest("BAD_NOTIFICATION", "Thiếu messageId/data");
        }
        JsonNode note;
        try {
            note = EventJson.mapper().readTree(Base64.getDecoder().decode(message.get("data").asText()));
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_NOTIFICATION", "data không phải JSON base64");
        }
        if (!props.getPackageName().equals(note.path("packageName").asText(""))) {
            log.error("RTDN của package khác ({}), bỏ qua", note.path("packageName").asText(""));
            return "ignored";
        }
        String dedupe = "google:" + messageId;

        if (note.has("subscriptionNotification")) {
            JsonNode sn = note.get("subscriptionNotification");
            GoogleSubscriptionResolver.Resolved r = resolve(sn.path("purchaseToken").asText(""));
            if (r == null) {
                return "ignored";
            }
            Runnable action = () -> {
                boolean found = r.entitled()
                        ? billing.onSubscriptionUpdated(Store.GOOGLE, r.originalTransactionId(), r.expiresAt())
                        : billing.onSubscriptionEnded(Store.GOOGLE, r.originalTransactionId());
                if (!found) {
                    log.info("Chưa có thuê bao Google {} (client chưa xác thực), bỏ qua", r.originalTransactionId());
                }
            };
            return run(dedupe, "SUBSCRIPTION_" + sn.path("notificationType").asText("?"), action);
        }
        if (note.has("voidedPurchaseNotification")) {
            JsonNode vn = note.get("voidedPurchaseNotification");
            String orderId = vn.path("orderId").asText("");
            if (orderId.isBlank()) {
                throw ApiException.badRequest("BAD_NOTIFICATION", "Thiếu orderId");
            }
            // productType 1 = thuê bao: cần khoá thuê bao để thu hồi nếu chưa có bản ghi mua cho orderId này.
            GoogleSubscriptionResolver.Resolved r = vn.path("productType").asInt(0) == 1
                    ? resolve(vn.path("purchaseToken").asText("")) : null;
            Runnable action = () -> {
                if (!billing.onRefund(Store.GOOGLE, orderId) && r != null) {
                    billing.onSubscriptionEnded(Store.GOOGLE, r.originalTransactionId());
                }
            };
            return run(dedupe, "VOIDED", action);
        }
        // testNotification, oneTimeProductNotification, ...: không đổi quyền lợi
        log.info("Bỏ qua RTDN Google (không có thay đổi quyền lợi)");
        return "ignored";
    }

    private String run(String dedupe, String type, Runnable action) {
        return processor.process(dedupe, Store.GOOGLE, type, action) ? "processed" : "duplicate";
    }

    /** UNAVAILABLE → 503 để Pub/Sub gửi lại; INVALID (token đã bị xoá...) → bỏ qua, trả null. */
    private GoogleSubscriptionResolver.Resolved resolve(String token) {
        GoogleSubscriptionResolver resolver = resolverProvider.getIfAvailable();
        if (resolver == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED", "Chưa cấu hình Google Play API");
        }
        try {
            return resolver.resolveSubscription(token);
        } catch (StoreException e) {
            if (e.kind() == StoreException.Kind.UNAVAILABLE) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORE_UNAVAILABLE", "Chưa hỏi được Google, sẽ thử lại");
            }
            log.warn("Không xác nhận được thuê bao Google, bỏ qua: {}", e.getMessage());
            return null;
        }
    }
}
