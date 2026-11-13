package com.animallocket.billing.store;

import java.time.Instant;

/** Hỏi Google trạng thái thuê bao của một purchaseToken (webhook RTDN dùng, không tin nội dung thông báo). */
public interface GoogleSubscriptionResolver {

    /**
     * @param originalTransactionId SHA-256 của purchaseToken gốc (khoá chung giữa verify và webhook)
     * @param entitled              có còn được dùng Pro ở thời điểm hiện tại không
     * @param expiresAt             hạn hiệu lực (nếu không còn quyền lợi thì không muộn hơn hiện tại)
     */
    record Resolved(String originalTransactionId, String latestOrderId, String productId,
                    boolean entitled, Instant expiresAt, boolean acknowledged) {
    }

    Resolved resolveSubscription(String purchaseToken) throws StoreException;
}
