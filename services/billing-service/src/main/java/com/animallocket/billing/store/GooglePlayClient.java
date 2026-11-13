package com.animallocket.billing.store;

import java.time.Instant;
import java.util.List;

/** Cổng gọi Google Play Developer API. Tách interface để test không cần mạng. */
public interface GooglePlayClient {

    record LineItem(String productId, Instant expiryTime) {
    }

    /** @param state ví dụ SUBSCRIPTION_STATE_ACTIVE */
    record SubscriptionInfo(String state, String latestOrderId, String linkedPurchaseToken,
                            boolean acknowledged, List<LineItem> lineItems) {
    }

    /** @param purchaseState 0 = đã mua, 1 = đã huỷ, 2 = đang chờ */
    record ProductInfo(String orderId, int purchaseState, String productId, Instant purchaseTime, boolean acknowledged) {
    }

    /** INVALID nếu token không tồn tại/đã bị xoá (404/410); UNAVAILABLE nếu Google lỗi. */
    SubscriptionInfo getSubscription(String purchaseToken) throws StoreException;

    ProductInfo getProduct(String sku, String purchaseToken) throws StoreException;

    void acknowledgeSubscription(String sku, String purchaseToken) throws StoreException;

    void acknowledgeProduct(String sku, String purchaseToken) throws StoreException;
}
