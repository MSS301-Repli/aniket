package com.animallocket.billing.store;

import java.time.Instant;

/**
 * Phần PayPal Orders v2 mà thanh toán web cần: tạo đơn và capture. Mọi lỗi hoặc thiếu cấu hình ném
 * {@link StoreException} (fail closed). Bean này chỉ tồn tại khi đã cấu hình khoá PayPal.
 */
public interface PayPalClient {

    /** @param approveUrl địa chỉ trên paypal.com để người mua duyệt thanh toán */
    record CreatedOrder(String orderId, String approveUrl) {
    }

    /**
     * Kết quả capture lấy từ PayPal. Các trường có thể null nếu PayPal không trả; người gọi phải coi null là không hợp lệ.
     *
     * @param orderStatus   trạng thái đơn (COMPLETED khi đã thu tiền)
     * @param captureStatus trạng thái khoản thu (COMPLETED | PENDING | DECLINED ...)
     * @param amountValue   số tiền dạng chuỗi thập phân, ví dụ "2.99"
     * @param customId      giá trị custom_id đã gắn lúc tạo đơn (có thể null)
     */
    record Capture(String orderId, String orderStatus, String captureId, String captureStatus,
                   String amountValue, String currency, String customId, Instant createdAt) {
    }

    /** {@code requestId} dùng làm PayPal-Request-Id nên gọi lặp với cùng giá trị không tạo đơn thứ hai. */
    CreatedOrder createOrder(String requestId, String customId, String description, String amountValue,
                             String currency, String returnUrl, String cancelUrl) throws StoreException;

    /**
     * Hỏi PayPal xem webhook có đúng do PayPal gửi không (verify-webhook-signature).
     *
     * @param headers các header PAYPAL-* của yêu cầu webhook
     * @param event   nội dung sự kiện đã đọc từ body
     * @return true chỉ khi PayPal trả SUCCESS; mọi trường hợp khác (kể cả thiếu header) là false
     */
    boolean verifyWebhook(WebhookHeaders headers, com.fasterxml.jackson.databind.JsonNode event) throws StoreException;

    record WebhookHeaders(String authAlgo, String certUrl, String transmissionId, String transmissionSig,
                          String transmissionTime) {
        public boolean complete() {
            return notBlank(authAlgo) && notBlank(certUrl) && notBlank(transmissionId)
                    && notBlank(transmissionSig) && notBlank(transmissionTime);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    /** Capture đơn đã được người mua duyệt. Nếu đơn đã capture từ trước thì trả lại kết quả đó (idempotent). */
    Capture capture(String orderId, String requestId) throws StoreException;
}
