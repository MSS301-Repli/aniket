package com.animallocket.billing.domain;

public enum Store {
    APPLE, GOOGLE, DEV,
    /** Thanh toán web qua PayPal (trang /pay/**). Không đi qua StoreRouter / API verify công khai. */
    PAYPAL,
    /** Thanh toán web qua VietQR/payOS (VND). Không đi qua StoreRouter / API verify công khai. */
    PAYOS
}
