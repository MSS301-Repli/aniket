package com.animallocket.common.event;

import java.time.Instant;

/**
 * Payload của từng loại event: nguồn contract duy nhất giữa producer và consumer.
 * Chỉ chứa publicId/mã, không bao giờ chứa email/SĐT/sub Keycloak.
 * Đổi hợp đồng: chỉ THÊM trường (consumer cũ bỏ qua trường lạ); đổi/xoá trường là breaking change.
 */
public final class Events {
    private Events() {
    }

    // ---- user.events ----

    /** {@code avatar} = mã avatar "v:animal:palette:accessory". */
    public record UserRegistered(String publicId, String ageGroup, String leadAnimal, String avatar) {
    }

    public record UserDeleted(String publicId) {
    }

    public record UserBlocked(String blockerPublicId, String blockedPublicId) {
    }

    public record LeadAnimalChanged(String publicId, String leadAnimal) {
    }

    /** Avatar đang dùng thay đổi. */
    public record AvatarChanged(String publicId, String avatar, String leadAnimal) {
    }

    /** Người dùng tắt chế độ khám phá: discovery phải xoá vị trí ngay (không chờ TTL). */
    public record DiscoveryDisabled(String publicId) {
    }

    // ---- friend.events ----

    public record FriendRequestReceived(String fromPublicId, String toPublicId) {
    }

    public record FriendAccepted(String userAPublicId, String userBPublicId) {
    }

    /** Huỷ kết bạn: post/chat dọn cache và đóng hội thoại. */
    public record FriendRemoved(String userAPublicId, String userBPublicId) {
    }

    // ---- media.events ----

    /** method: AI, STICKER hoặc NONE. context: POST hoặc CHAT. refId: id bài đăng hoặc id hội thoại. */
    public record PhotoReady(String mediaId, String ownerPublicId, String publicKey, String method,
                             int facesDetected, String context, String refId) {
    }

    public record PhotoRejected(String mediaId, String ownerPublicId, String reason, String context,
                                String refId) {
    }

    // ---- post.events ----

    public record PostPublished(String postId, String authorPublicId) {
    }

    public record PostLiked(String postId, String authorPublicId, String likerPublicId) {
    }

    public record PostCommented(String postId, String authorPublicId, String commenterPublicId) {
    }

    // ---- chat.events ----

    public record MessageSent(String conversationId, String senderPublicId, String recipientPublicId) {
    }

    // ---- billing.events ----

    /** Dùng cho SUBSCRIPTION_ACTIVATED và SUBSCRIPTION_EXPIRED. */
    public record SubscriptionChanged(String publicId, String plan, Instant expiresAt) {
    }

    /** item: BOOST, PRIORITY_INVITE, ANIMAL_PACK:&lt;code&gt;. */
    public record ConsumableGranted(String publicId, String item, int quantity) {
    }
}
