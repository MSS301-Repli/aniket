package com.animallocket.notification;

import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Nghe event rồi giao việc gửi push cho thread pool riêng, để push chậm không làm nghẽn consumer.
 * Khử trùng lặp theo eventId bằng cache bộ nhớ (best-effort; trùng một push là chấp nhận được).
 */
@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationPlanner planner;
    private final NotificationDispatcher dispatcher;
    private final Executor executor;
    private final Cache<String, Boolean> seen = Caffeine.newBuilder()
            .maximumSize(200_000).expireAfterWrite(Duration.ofHours(1)).build();

    public NotificationListener(NotificationPlanner planner, NotificationDispatcher dispatcher,
                                @org.springframework.beans.factory.annotation.Qualifier("pushExecutor") Executor executor) {
        this.planner = planner;
        this.dispatcher = dispatcher;
        this.executor = executor;
    }

    @KafkaListener(topics = {Topics.USER, Topics.FRIEND, Topics.POST, Topics.CHAT, Topics.MEDIA, Topics.BILLING},
            groupId = "notification-service")
    public void onMessage(String json) {
        EventEnvelope e;
        try {
            e = EventJson.read(json);
        } catch (IllegalArgumentException ex) {
            log.warn("Bỏ qua message không đọc được");
            return;
        }
        if (seen.asMap().putIfAbsent(e.eventId(), Boolean.TRUE) != null) {
            return;
        }
        List<Notification> notifications;
        try {
            notifications = planner.plan(e);
        } catch (Exception ex) {
            log.warn("Không lập được thông báo cho {}: {}", e.type(), ex.toString());
            return;
        }
        for (Notification n : notifications) {
            executor.execute(() -> {
                try {
                    dispatcher.dispatch(n);
                } catch (Exception ex) {
                    log.warn("Gửi thông báo lỗi: {}", ex.toString());
                }
            });
        }
    }
}
