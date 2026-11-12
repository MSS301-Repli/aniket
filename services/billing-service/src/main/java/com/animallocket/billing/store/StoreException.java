package com.animallocket.billing.store;

public class StoreException extends Exception {
    public enum Kind {
        /** Biên lai sai, giả hoặc không khớp ứng dụng: lỗi của người gọi, không retry. */
        INVALID,
        /** Store lỗi hoặc chưa cấu hình: tạm thời, client được phép thử lại. */
        UNAVAILABLE
    }

    private final Kind kind;

    public StoreException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public StoreException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
