package com.animallocket.notification;

import com.animallocket.common.EventTypes;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationListenerTest {
    private NotificationDispatcher dispatcher(DeviceDirectory dir, PushSender sender) {
        NotificationDispatcher d = new NotificationDispatcher(dir, sender, new SimpleMeterRegistry(), 3, 100);
        d.setSleeper(ms -> { });
        return d;
    }

    private NotificationPlanner planner(DeviceDirectory dir) {
        return new NotificationPlanner(dir, new NotificationMessagesConfig().notificationMessages(), "vi");
    }

    @Test
    void listenerDeduplicatesAndSurvivesGarbage() {
        var dir = new FakeDirectory(Map.of("owner", List.of("tok")), Map.of());
        var sender = new ScriptedSender(PushSender.Result.OK);
        Executor direct = Runnable::run;
        var listener = new NotificationListener(planner(dir), dispatcher(dir, sender), direct);

        String wire = EventJson.write(EventEnvelope.of(EventTypes.POST_LIKED, "test", "k",
                new Events.PostLiked("p", "owner", "x")));
        listener.onMessage(wire);
        listener.onMessage(wire);           // cùng eventId → bỏ qua
        listener.onMessage("{{ garbage");   // không ném lỗi
        assertEquals(1, sender.calls);
    }
}
