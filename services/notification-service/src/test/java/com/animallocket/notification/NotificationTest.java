package com.animallocket.notification;

import com.animallocket.common.EventTypes;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.Events;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NotificationTest {
    @Autowired LoggingPushSender loggingSender;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean DeviceDirectory contextDirectory;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private NotificationDispatcher dispatcher(DeviceDirectory dir, PushSender sender, List<Long> sleeps) {
        NotificationDispatcher d = new NotificationDispatcher(dir, sender, registry, 3, 100);
        d.setSleeper(sleeps::add);
        return d;
    }

    private EventEnvelope env(String type, Object payload) {
        return EventEnvelope.of(type, "test", "k", payload);
    }

    private NotificationPlanner planner(DeviceDirectory dir) {
        return new NotificationPlanner(dir, new NotificationMessagesConfig().notificationMessages(), "vi");
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
        var planner = planner(dir);
        var out = planner.plan(env(EventTypes.POST_PUBLISHED, new Events.PostPublished("p1", "author")));
        assertEquals(List.of("f1", "f2", "f3"), out.stream().map(Notification::recipientPublicId).toList());
        assertEquals("p1", out.get(0).data().get("postId"));
    }

    @Test
    void friendAcceptedNotifiesBothSides() {
        var planner = planner(new FakeDirectory(Map.of(), Map.of()));
        var out = planner.plan(env(EventTypes.FRIEND_ACCEPTED, new Events.FriendAccepted("a", "b")));
        assertEquals(List.of("a", "b"), out.stream().map(Notification::recipientPublicId).toList());
    }

    @Test
    void transientErrorsRetryWithBackoffThenSucceed() {
        var dir = new FakeDirectory(Map.of("u", List.of("tok1")), Map.of());
        var sender = new ScriptedSender(PushSender.Result.TRANSIENT_ERROR, PushSender.Result.TRANSIENT_ERROR, PushSender.Result.OK);
        List<Long> sleeps = new ArrayList<>();
        dispatcher(dir, sender, sleeps).dispatch(new Notification("u", "T", "t", "b", Map.of()));
        assertEquals(3, sender.calls);
        assertEquals(List.of(100L, 200L), sleeps);
    }

    @Test
    void persistentFailureIsDroppedAfterMaxAttempts() {
        var dir = new FakeDirectory(Map.of("u", List.of("tok1")), Map.of());
        var sender = new ScriptedSender(PushSender.Result.TRANSIENT_ERROR);
        dispatcher(dir, sender, new ArrayList<>()).dispatch(new Notification("u", "T", "t", "b", Map.of()));
        assertEquals(3, sender.calls); // dừng, không ném lỗi, không vòng lặp vô hạn
    }

    @Test
    void invalidTokenIsRemovedWithoutRetry() {
        var dir = new FakeDirectory(Map.of("u", List.of("dead", "alive")), Map.of());
        var sender = new ScriptedSender(PushSender.Result.INVALID_TOKEN, PushSender.Result.OK);
        dispatcher(dir, sender, new ArrayList<>()).dispatch(new Notification("u", "T", "t", "b", Map.of()));
        assertEquals(List.of("dead"), dir.removed);
        assertEquals(List.of("dead", "alive"), sender.sentTo);
    }

    @Test
    void metricsCountSentInvalidAndDropped() {
        var dir = new FakeDirectory(Map.of("u", List.of("ok", "dead", "flaky")), Map.of());
        var sender = new ScriptedSender(PushSender.Result.OK, PushSender.Result.INVALID_TOKEN, PushSender.Result.TRANSIENT_ERROR);
        dispatcher(dir, sender, new ArrayList<>()).dispatch(new Notification("u", "T", "t", "b", Map.of()));
        assertEquals(1.0, registry.counter("push.sent", "type", "T").count());
        assertEquals(1.0, registry.counter("push.invalid_token").count());
        assertEquals(1.0, registry.counter("push.dropped", "type", "T").count());
    }
}
