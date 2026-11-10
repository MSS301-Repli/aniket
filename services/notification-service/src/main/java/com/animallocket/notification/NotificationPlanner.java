package com.animallocket.notification;

import com.animallocket.common.EventTypes;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Biến event nghiệp vụ thành danh sách thông báo. Nội dung cố ý chung chung để giữ ẩn danh. */
@Component
public class NotificationPlanner {
    private static final Logger log = LoggerFactory.getLogger(NotificationPlanner.class);
    private final DeviceDirectory directory;

    public NotificationPlanner(DeviceDirectory directory) {
        this.directory = directory;
    }

    public List<Notification> plan(EventEnvelope e) {
        switch (e.type()) {
            case EventTypes.FRIEND_REQUEST_RECEIVED -> {
                var p = e.payloadAs(Events.FriendRequestReceived.class);
                return List.of(n(p.toPublicId(), "FRIEND_REQUEST", "Lời mời kết bạn mới",
                        "Có một người bạn mới muốn làm quen với bạn 🐾", Map.of()));
            }
            case EventTypes.FRIEND_ACCEPTED -> {
                var p = e.payloadAs(Events.FriendAccepted.class);
                return List.of(
                        n(p.userAPublicId(), "FRIEND_ACCEPTED", "Đã thành bạn!", "Bạn có thêm một người bạn mới 🎉", Map.of()),
                        n(p.userBPublicId(), "FRIEND_ACCEPTED", "Đã thành bạn!", "Bạn có thêm một người bạn mới 🎉", Map.of()));
            }
            case EventTypes.POST_PUBLISHED -> {
                var p = e.payloadAs(Events.PostPublished.class);
                List<Notification> out = new ArrayList<>();
                for (String friend : directory.friendIds(p.authorPublicId())) {
                    out.add(n(friend, "NEW_POST", "Ảnh mới", "Một người bạn vừa đăng ảnh mới", Map.of("postId", p.postId())));
                }
                return out;
            }
            case EventTypes.POST_LIKED -> {
                var p = e.payloadAs(Events.PostLiked.class);
                return List.of(n(p.authorPublicId(), "POST_LIKED", "Có người thích ảnh của bạn", "Mở app để xem 💛",
                        Map.of("postId", p.postId())));
            }
            case EventTypes.POST_COMMENTED -> {
                var p = e.payloadAs(Events.PostCommented.class);
                return List.of(n(p.authorPublicId(), "POST_COMMENTED", "Bình luận mới", "Có người vừa bình luận ảnh của bạn",
                        Map.of("postId", p.postId())));
            }
            case EventTypes.MESSAGE_SENT -> {
                var p = e.payloadAs(Events.MessageSent.class);
                return List.of(n(p.recipientPublicId(), "MESSAGE", "Tin nhắn mới", "Bạn có tin nhắn mới",
                        Map.of("conversationId", p.conversationId())));
            }
            case EventTypes.PHOTO_REJECTED -> {
                var p = e.payloadAs(Events.PhotoRejected.class);
                return List.of(n(p.ownerPublicId(), "PHOTO_REJECTED", "Ảnh chưa đăng được",
                        "Ảnh của bạn không xử lý được, hãy thử lại nhé", Map.of("mediaId", p.mediaId())));
            }
            case EventTypes.SUBSCRIPTION_ACTIVATED -> {
                var p = e.payloadAs(Events.SubscriptionChanged.class);
                return List.of(n(p.publicId(), "SUBSCRIPTION", "Chào mừng đến với Pro", "Cảm ơn bạn đã ủng hộ Animal Locket 🐾", Map.of()));
            }
            default -> {
                return List.of();
            }
        }
    }

    private Notification n(String to, String type, String title, String body, Map<String, String> data) {
        Map<String, String> all = new java.util.HashMap<>(data);
        all.put("type", type);
        return new Notification(to, type, title, body, all);
    }

    void logSkipped(String type) {
        log.debug("Không có thông báo cho event {}", type);
    }
}
