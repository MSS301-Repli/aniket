package com.animallocket.billing.web;

import com.animallocket.billing.domain.Store;
import com.animallocket.billing.service.BillingService;
import com.animallocket.billing.store.AppleJwsVerifier;
import com.animallocket.billing.store.AppleProperties;
import com.animallocket.billing.store.StoreException;
import com.animallocket.common.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * App Store Server Notifications V2. Chữ ký JWS được kiểm tra (chuỗi x5c → Apple Root CA) TRƯỚC mọi xử lý;
 * không hợp lệ → 401 và không đổi trạng thái nào.
 */
@Component
public class AppleWebhookHandler {
    private static final Logger log = LoggerFactory.getLogger(AppleWebhookHandler.class);

    private final ObjectProvider<AppleJwsVerifier> verifierProvider;
    private final AppleProperties props;
    private final WebhookProcessor processor;
    private final BillingService billing;

    public AppleWebhookHandler(ObjectProvider<AppleJwsVerifier> verifierProvider, AppleProperties props,
                               WebhookProcessor processor, BillingService billing) {
        this.verifierProvider = verifierProvider;
        this.props = props;
        this.processor = processor;
        this.billing = billing;
    }

    /** @return processed | duplicate | ignored */
    public String handle(String signedPayload) {
        AppleJwsVerifier verifier = verifierProvider.getIfAvailable();
        if (verifier == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED", "Chưa cấu hình xác thực Apple");
        }
        JsonNode n = verify(verifier, signedPayload);
        String type = text(n, "notificationType");
        String uuid = text(n, "notificationUUID");
        if (type == null || uuid == null) {
            throw ApiException.badRequest("BAD_NOTIFICATION", "Thiếu notificationType/notificationUUID");
        }
        JsonNode data = n.path("data");
        if (data.hasNonNull("bundleId") && !props.getBundleId().equals(data.get("bundleId").asText())) {
            throw ApiException.badRequest("BUNDLE_MISMATCH", "Thông báo không thuộc ứng dụng này");
        }

        Kind kind = kindOf(type);
        if (kind == Kind.IGNORE) {
            log.info("Bỏ qua thông báo Apple {} ({})", type, text(n, "subtype"));
            return "ignored";
        }
        JsonNode tx = data.hasNonNull("signedTransactionInfo") ? verify(verifier, data.get("signedTransactionInfo").asText()) : null;
        if (tx == null || !tx.hasNonNull("originalTransactionId")) {
            log.error("Thông báo Apple {} thiếu thông tin giao dịch, bỏ qua", type);
            return "ignored";
        }
        if (tx.hasNonNull("bundleId") && !props.getBundleId().equals(tx.get("bundleId").asText())) {
            throw ApiException.badRequest("BUNDLE_MISMATCH", "Giao dịch không thuộc ứng dụng này");
        }
        String original = tx.get("originalTransactionId").asText();
        String txId = tx.hasNonNull("transactionId") ? tx.get("transactionId").asText() : original;

        Runnable action = switch (kind) {
            case RENEW -> {
                if (!tx.hasNonNull("expiresDate")) {
                    log.error("Thông báo {} thiếu expiresDate, bỏ qua", type);
                    yield null;
                }
                Instant exp = Instant.ofEpochMilli(tx.get("expiresDate").asLong());
                yield () -> {
                    if (!billing.onSubscriptionUpdated(Store.APPLE, original, exp)) {
                        log.info("Chưa có thuê bao Apple {} (client chưa xác thực), bỏ qua", original);
                    }
                };
            }
            case END -> () -> billing.onSubscriptionEnded(Store.APPLE, original);
            case REFUND -> () -> {
                // Giao dịch chưa từng được client xác thực thì không có bản ghi mua; thuê bao (nếu có) vẫn phải bị thu hồi.
                if (!billing.onRefund(Store.APPLE, txId)) {
                    billing.onSubscriptionEnded(Store.APPLE, original);
                }
            };
            default -> null;
        };
        if (action == null) {
            return "ignored";
        }
        return processor.process("apple:" + uuid, Store.APPLE, type, action) ? "processed" : "duplicate";
    }

    private enum Kind { RENEW, END, REFUND, IGNORE }

    private static Kind kindOf(String type) {
        return switch (type) {
            case "SUBSCRIBED", "DID_RENEW", "RENEWAL_EXTENDED" -> Kind.RENEW;
            case "EXPIRED", "GRACE_PERIOD_EXPIRED", "REVOKE" -> Kind.END;
            case "REFUND" -> Kind.REFUND;
            default -> Kind.IGNORE; // DID_FAIL_TO_RENEW, DID_CHANGE_RENEWAL_STATUS, TEST, ...
        };
    }

    private static JsonNode verify(AppleJwsVerifier verifier, String jws) {
        try {
            return verifier.verify(jws);
        } catch (StoreException e) {
            if (e.kind() == StoreException.Kind.UNAVAILABLE) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED", e.getMessage());
            }
            log.warn("Từ chối webhook Apple: {}", e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Chữ ký không hợp lệ");
        }
    }

    private static String text(JsonNode n, String f) {
        return n.hasNonNull(f) ? n.get(f).asText() : null;
    }
}
