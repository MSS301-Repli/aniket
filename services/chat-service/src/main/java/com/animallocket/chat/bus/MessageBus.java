package com.animallocket.chat.bus;

import java.util.function.BiConsumer;

/**
 * Phân phối tin tới người dùng đang kết nối ở BẤT KỲ instance nào. Redis pub/sub cho runtime
 * (nhiều instance), bản cục bộ cho test và chạy một instance.
 */
public interface MessageBus {
    /** Gửi một khung JSON tới mọi thiết bị đang kết nối của {@code publicId}. */
    void publish(String publicId, String json);

    /** Đăng ký nơi nhận khung tin ở instance này. */
    void subscribe(BiConsumer<String, String> deliverLocally);
}
