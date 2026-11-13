package com.animallocket.common;

/**
 * Kafka topic. Khoá message luôn là publicId liên quan (hoặc mediaId) để giữ thứ tự theo thực thể.
 * Message xử lý lỗi được chuyển sang {@code <topic>.dlq} (xem KafkaErrorHandlingAutoConfiguration).
 */
public final class Topics {
    public static final String USER = "user.events";
    public static final String FRIEND = "friend.events";
    public static final String MEDIA = "media.events";
    public static final String POST = "post.events";
    public static final String CHAT = "chat.events";
    public static final String BILLING = "billing.events";

    private Topics() {
    }
}
