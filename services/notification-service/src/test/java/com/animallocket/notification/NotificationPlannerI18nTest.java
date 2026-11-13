package com.animallocket.notification;

import com.animallocket.common.EventTypes;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.Events;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPlannerI18nTest {
    private static final List<String> TYPES = List.of("FRIEND_REQUEST", "FRIEND_ACCEPTED", "NEW_POST", "POST_LIKED",
            "POST_COMMENTED", "MESSAGE", "PHOTO_REJECTED", "SUBSCRIPTION");

    private final FakeDirectory dir = new FakeDirectory(Map.of(), Map.of());

    private NotificationPlanner planner(String locale) {
        return new NotificationPlanner(dir, new NotificationMessagesConfig().notificationMessages(), locale);
    }

    private static EventEnvelope liked() {
        return EventEnvelope.of(EventTypes.POST_LIKED, "test", "k", new Events.PostLiked("p1", "owner", "x"));
    }

    @Test
    void vietnameseIsTheDefaultLanguage() {
        Notification n = planner("vi").plan(liked()).get(0);
        assertEquals("Có người thích ảnh của bạn", n.title());
        assertEquals("Mở app để xem 💛", n.body());
    }

    @Test
    void englishTemplatesAreUsedWhenConfigured() {
        Notification n = planner("en").plan(liked()).get(0);
        assertEquals("Someone liked your photo", n.title());
        assertEquals("Open the app to see 💛", n.body());
    }

    @Test
    void everyTypeHasBothLanguages() {
        var messages = new NotificationMessagesConfig().notificationMessages();
        for (String type : TYPES) {
            for (String part : List.of("title", "body")) {
                String code = "notification." + type + "." + part;
                String vi = messages.getMessage(code, null, Locale.forLanguageTag("vi"));
                String en = messages.getMessage(code, null, Locale.forLanguageTag("en"));
                assertTrue(!vi.isBlank() && !en.isBlank(), code);
                assertNotEquals(vi, en, code);
            }
        }
    }

    @Test
    void unsupportedLocaleFallsBackToVietnamese() {
        Notification n = planner("fr").plan(liked()).get(0);
        assertEquals("Có người thích ảnh của bạn", n.title());
    }

    @Test
    void missingKeyInLocaleFallsBackToVietnamese() {
        var messages = new StaticMessageSource();
        messages.addMessage("notification.FRIEND_REQUEST.title", Locale.forLanguageTag("vi"), "Lời mời kết bạn mới");
        messages.addMessage("notification.FRIEND_REQUEST.body", Locale.forLanguageTag("vi"), "Nội dung vi");
        messages.addMessage("notification.FRIEND_REQUEST.title", Locale.forLanguageTag("en"), "New friend request");
        // thiếu body tiếng Anh: phải rơi về tiếng Việt thay vì ném lỗi
        var planner = new NotificationPlanner(dir, messages, "en");
        var out = planner.plan(EventEnvelope.of(EventTypes.FRIEND_REQUEST_RECEIVED, "test", "k",
                new Events.FriendRequestReceived("a", "b")));
        assertEquals("New friend request", out.get(0).title());
        assertEquals("Nội dung vi", out.get(0).body());
    }
}
