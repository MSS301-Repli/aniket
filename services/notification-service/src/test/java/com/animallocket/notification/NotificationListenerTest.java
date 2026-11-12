package com.animallocket.notification;

import com.animallocket.common.EventTypes;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationListenerTest {
    private final Executor direct = Runnable::run;

    private NotificationDispatcher dispatcher(DeviceDirectory dir, PushSender sender) {
        NotificationDispatcher d = new NotificationDispatcher(dir, sender, new SimpleMeterRegistry(), 3, 100);
        d.setSleeper(ms -> { });
        return d;
    }

    private NotificationPlanner planner(DeviceDirectory dir) {
        return new NotificationPlanner(dir, new NotificationMessagesConfig().notificationMessages(), "vi");
    }

    private NotificationListener listener(FakeDirectory dir, PushSender sender, Executor executor, int batchSize) {
        return new NotificationListener(planner(dir), dispatcher(dir, sender), executor, batchSize);
    }

    private static String liked() {
        return EventJson.write(EventEnvelope.of(EventTypes.POST_LIKED, "test", "k",
                new Events.PostLiked("p", "owner", "x")));
    }

    private static String published(String author) {
        return EventJson.write(EventEnvelope.of(EventTypes.POST_PUBLISHED, "test", "k",
                new Events.PostPublished("p1", author)));
    }

    @Test
    void sameEventIdIsDispatchedOnlyOnce() {
        var dir = new FakeDirectory(Map.of("owner", List.of("tok")), Map.of());
        var sender = new ScriptedSender(PushSender.Result.OK);
        var listener = listener(dir, sender, direct, 200);

        String wire = liked();
        listener.onMessage(wire);
        listener.onMessage(wire); // cùng eventId → bỏ qua
        assertEquals(1, sender.calls);
    }

    @Test
    void unreadableMessageIsRejectedSoItGoesToDlq() {
        var dir = new FakeDirectory(Map.of(), Map.of());
        var listener = listener(dir, new ScriptedSender(PushSender.Result.OK), direct, 200);
        assertThrows(IllegalArgumentException.class, () -> listener.onMessage("{{ garbage"));
    }

    @Test
    void postPublishedFansOutInBatches() {
        var friends = List.of("f1", "f2", "f3", "f4", "f5");
        var tokens = Map.of("f1", List.of("t1"), "f2", List.of("t2"), "f3", List.of("t3"),
                "f4", List.of("t4"), "f5", List.of("t5"));
        var dir = new FakeDirectory(tokens, Map.of("author", friends));
        var sender = new ScriptedSender(PushSender.Result.OK);
        List<Runnable> submitted = new ArrayList<>();
        var listener = listener(dir, sender, submitted::add, 2);

        listener.onMessage(published("author"));
        assertEquals(3, submitted.size());   // 2 + 2 + 1
        assertEquals(0, sender.calls);       // chưa chạy cho tới khi executor chạy tác vụ
        submitted.forEach(Runnable::run);
        assertEquals(5, sender.calls);
    }

    @Test
    void planningFailurePropagatesAndRetryIsNotDropped() {
        var dir = new FakeDirectory(Map.of("f1", List.of("t1")), Map.of("author", List.of("f1")));
        var sender = new ScriptedSender(PushSender.Result.OK);
        var listener = listener(dir, sender, direct, 200);
        String wire = published("author");

        dir.failFriends = true;
        assertThrows(IllegalStateException.class, () -> listener.onMessage(wire));
        assertEquals(0, sender.calls);

        dir.failFriends = false;
        listener.onMessage(wire); // retry cùng eventId vẫn được xử lý vì lần lỗi chưa bị đánh dấu
        assertEquals(1, sender.calls);
    }

    @Test
    void executorRejectionPropagatesAndRetryIsNotDropped() {
        var dir = new FakeDirectory(Map.of("owner", List.of("tok")), Map.of());
        var sender = new ScriptedSender(PushSender.Result.OK);
        boolean[] full = {true};
        Executor flaky = task -> {
            if (full[0]) {
                throw new RejectedExecutionException("queue đầy");
            }
            task.run();
        };
        var listener = listener(dir, sender, flaky, 200);
        String wire = liked();

        assertThrows(RejectedExecutionException.class, () -> listener.onMessage(wire));
        full[0] = false;
        listener.onMessage(wire);
        assertEquals(1, sender.calls);
    }

    @Test
    void deviceFailuresInsideABatchAreNotRethrown() {
        var dir = new FakeDirectory(Map.of("f1", List.of("t1"), "f2", List.of("t2")),
                Map.of("author", List.of("f1", "f2")));
        var sender = new ScriptedSender(PushSender.Result.TRANSIENT_ERROR);
        var listener = listener(dir, sender, direct, 200);

        listener.onMessage(published("author")); // không ném lỗi; dispatcher đã retry và tính metric
        assertEquals(6, sender.calls);           // 2 thiết bị x 3 lần thử
    }
}
