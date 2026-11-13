package com.animallocket.billing.store;

/**
 * Xác thực request đẩy (push) của Google Pub/Sub tới webhook RTDN. Trả về bình thường nếu hợp lệ,
 * ném {@link SecurityException} nếu thiếu/sai token.
 */
public interface GooglePushAuth {
    void authenticate(String authorizationHeader);
}
