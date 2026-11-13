package com.animallocket.billing.store;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Phần payOS (VietQR) mà thanh toán web cần. Mọi lỗi hoặc thiếu cấu hình ném {@link StoreException} (fail closed).
 * Bean này chỉ tồn tại khi đã cấu hình khoá payOS.
 */
public interface PayOsClient {

    /** @param checkoutUrl trang thanh toán của payOS (hiện mã VietQR) */
    record PaymentLink(long orderCode, String paymentLinkId, String checkoutUrl, String status) {
    }

    /**
     * Trạng thái lấy từ payOS. {@code status}: PAID | PENDING | PROCESSING | CANCELLED (có thể thêm giá trị mới).
     * Người gọi phải coi mọi giá trị khác PAID là chưa thanh toán.
     */
    record PaymentInfo(long orderCode, String status, long amount, long amountPaid) {
    }

    PaymentLink createPaymentLink(long orderCode, int amount, String description, String itemName,
                                  String returnUrl, String cancelUrl) throws StoreException;

    /** Hỏi payOS trạng thái thật của một orderCode. */
    PaymentInfo getPaymentInfo(long orderCode) throws StoreException;

    /** @param data đối tượng {@code data} của webhook; @param signature chữ ký ở cấp ngoài của webhook */
    boolean verifyWebhookSignature(JsonNode data, String signature);
}
