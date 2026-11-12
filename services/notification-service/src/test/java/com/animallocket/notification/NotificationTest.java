package com.animallocket.notification;

import com.animallocket.common.EventTypes;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.Events;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NotificationTest {
    @Autowired LoggingPushSender loggingSender;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean DeviceDirectory contextDirectory;

    private EventEnvelope env(String type, Object payload) {
        return EventEnvelope.of(type, "test", "k", payload);
    }

    @Test
    void contextLoadsWithLoggingSenderWhenFcmNotConfigured() {
        assertTrue(loggingSender != null);
    }

    @Test
    void loggingSenderOnlyReportsOk() {
        assertEquals(PushSender.Result.OK, loggingSender.send("device-token-123456", "t", "b", Map.of()));
    }

    @Test
    void newPostFansOutToEveryFriendButNotAuthor() {
        var dir = new FakeDirectory(Map.of(), Map.of("author", List.of("f1", "f2", "f3")));
        var planner = new NotificationPlanner(dir);
        var out = planner.plan(env(EventTypes.POST_PUBLISHED, new Events.PostPublished("p1", "author")));
        assertEquals(List.of("f1", "f2", "f3"), out.stream().map(Notification::recipientPublicId).toList());
        assertEquals("p1", out.get(0).data().get("postId"));
    }

    @Test
    void friendAcceptedNotifiesBothSides() {
        var planner = new NotificationPlanner(new FakeDirectory(Map.of(), Map.of()));
        var out = planner.plan(env(EventTypes.FRIEND_ACCEPTED, new Events.FriendAccepted("a", "b")));
        assertEquals(List.of("a", "b"), out.stream().map(Notification::recipientPublicId).toList());
    }
}
