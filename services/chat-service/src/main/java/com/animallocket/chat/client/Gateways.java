package com.animallocket.chat.client;

public final class Gateways {
    private Gateways() {
    }

    public record Relationship(boolean friends, boolean blocked) {
    }

    public record MediaInfo(String mediaId, String ownerPublicId, String context, String status) {
    }

    public interface UserGateway {
        Relationship relationship(String a, String b);
    }

    public interface MediaGateway {
        MediaInfo media(String mediaId);
    }

    /** Thông báo "có tin mới" cho notification-service. Fail soft: lỗi không được làm hỏng việc gửi tin. */
    public interface EventPublisher {
        void messageSent(String conversationId, String sender, String recipient);
    }
}
