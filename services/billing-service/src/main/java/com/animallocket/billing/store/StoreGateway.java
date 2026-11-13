package com.animallocket.billing.store;

import com.animallocket.billing.domain.Store;

/** Cổng xác thực giao dịch với từng store. Mọi lỗi hoặc thiếu cấu hình phải ném StoreException (fail closed). */
public interface StoreGateway {
    Store store();

    /** @param token transactionId (Apple) hoặc purchaseToken (Google) do client gửi lên */
    VerifiedPurchase verify(String sku, String token) throws StoreException;
}
