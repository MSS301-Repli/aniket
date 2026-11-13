package com.animallocket.billing.store;

import java.time.Instant;

/**
 * Dữ liệu giao dịch lấy TỪ STORE (không phải từ client). Mọi quyền lợi chỉ được cấp dựa trên object này.
 *
 * @param transactionId         id giao dịch duy nhất trong store
 * @param originalTransactionId id gốc của chuỗi thuê bao (giống transactionId với mua lẻ)
 * @param expiresAt             hạn thuê bao; null với mua lẻ
 */
public record VerifiedPurchase(String transactionId, String originalTransactionId, String sku,
                               Instant purchasedAt, Instant expiresAt, boolean revoked) {
}
