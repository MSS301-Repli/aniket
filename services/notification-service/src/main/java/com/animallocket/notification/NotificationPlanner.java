package com.animallocket.notification;

import com.animallocket.common.EventTypes;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Biến event nghiệp vụ thành danh sách thông báo. Nội dung cố ý chung chung để giữ ẩn danh.
 * Tiêu đề và nội dung lấy từ template i18n theo khoá {@code notification.<TYPE>.title|body}; thiếu khoá ở ngôn ngữ
 * đang dùng thì rơi về tiếng Việt.
 */
@Component
public class NotificationPlanner {
    private static final Logger log = LoggerFactory.getLogger(NotificationPlanner.class);
    private static final Locale FALLBACK = Locale.forLanguageTag("vi");

    private final DeviceDirectory directory;
    private final MessageSource messages;
    private final Locale locale;

    public NotificationPlanner(DeviceDirectory directory,
                               @Qualifier("notificationMessages") MessageSource messages,
                               @Value("${animal.notification.default-locale:vi}") String defaultLocale) {
        this.directory = directory;
        this.messages = messages;
        this.locale = defaultLocale == null || defaultLocale.isBlank() ? FALLBACK : Locale.forLanguageTag(defaultLocale.trim());
    }

    public List<Notification> plan(EventEnvelope e) {
        switch (e.type()) {
            case EventTypes.FRIEND_REQUEST_RECEIVED -> {
                var p = e.payloadAs(Events.FriendRequestReceived.class);
                return List.of(n(p.toPublicId(), "FRIEND_REQUEST", Map.of()));
            }
            case EventTypes.FRIEND_ACCEPTED -> {
                var p = e.payloadAs(Events.FriendAccepted.class);
                return List.of(
                        n(p.userAPublicId(), "FRIEND_ACCEPTED", Map.of()),
                        n(p.userBPublicId(), "FRIEND_ACCEPTED", Map.of()));
            }
            case EventTypes.POST_PUBLISHED -> {
                var p = e.payloadAs(Events.PostPublished.class);
                List<Notification> out = new ArrayList<>();
                for (String friend : directory.friendIds(p.authorPublicId())) {
                    out.add(n(friend, "NEW_POST", Map.of("postId", p.postId())));
                }
                return out;
            }
            case EventTypes.POST_LIKED -> {
                var p = e.payloadAs(Events.PostLiked.class);
                return List.of(n(p.authorPublicId(), "POST_LIKED", Map.of("postId", p.postId())));
            }
            case EventTypes.POST_COMMENTED -> {
                var p = e.payloadAs(Events.PostCommented.class);
                return List.of(n(p.authorPublicId(), "POST_COMMENTED", Map.of("postId", p.postId())));
            }
            case EventTypes.MESSAGE_SENT -> {
                var p = e.payloadAs(Events.MessageSent.class);
                return List.of(n(p.recipientPublicId(), "MESSAGE", Map.of("conversationId", p.conversationId())));
            }
            case EventTypes.PHOTO_REJECTED -> {
                var p = e.payloadAs(Events.PhotoRejected.class);
                return List.of(n(p.ownerPublicId(), "PHOTO_REJECTED", Map.of("mediaId", p.mediaId())));
            }
            case EventTypes.SUBSCRIPTION_ACTIVATED -> {
                var p = e.payloadAs(Events.SubscriptionChanged.class);
                return List.of(n(p.publicId(), "SUBSCRIPTION", Map.of()));
            }
            default -> {
                return List.of();
            }
        }
    }

    private Notification n(String to, String type, Map<String, String> data) {
        Map<String, String> all = new java.util.HashMap<>(data);
        all.put("type", type);
        return new Notification(to, type, text(type, "title"), text(type, "body"), all);
    }

    private String text(String type, String part) {
        String code = "notification." + type + "." + part;
        try {
            return messages.getMessage(code, null, locale);
        } catch (NoSuchMessageException ex) {
            return messages.getMessage(code, null, FALLBACK);
        }
    }

    void logSkipped(String type) {
        log.debug("Không có thông báo cho event {}", type);
    }
}
