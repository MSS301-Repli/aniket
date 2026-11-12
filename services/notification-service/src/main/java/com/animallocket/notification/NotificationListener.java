package com.animallocket.notification;

import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Nghe event rồi giao việc gửi push cho thread pool riêng, để push chậm không làm nghẽn consumer.
 * Danh sách thông báo được chia batch ({@code animal.notification.fanout-batch-size}) và mỗi batch là một tác vụ.
 * Lỗi đọc event, lập kế hoạch hoặc executor từ chối được ném ra khỏi listener để error handler chung retry rồi
 * chuyển sang DLQ; eventId chỉ được đánh dấu đã thấy sau khi xử lý xong, nên lần retry không bị bỏ qua.
 * Lỗi gửi từng thiết bị nằm trong dispatcher (retry và metric), không ném ra đây.
 * Khử trùng lặp bằng cache bộ nhớ (best-effort; trùng một push là chấp nhận được).
 */
@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationPlanner planner;
    private final NotificationDispatcher dispatcher;
    private final Executor executor;
    private final int batchSize;
    private final Cache<String, Boolean> seen = Caffeine.newBuilder()
            .maximumSize(200_000).expireAfterWrite(Duration.ofHours(1)).build();

    public NotificationListener(NotificationPlanner planner, NotificationDispatcher dispatcher,
                                @org.springframework.beans.factory.annotation.Qualifier("pushExecutor") Executor executor,
                                @Value("${animal.notification.fanout-batch-size:200}") int batchSize) {
        this.planner = planner;
        this.dispatcher = dispatcher;
        this.executor = executor;
        this.batchSize = Math.max(1, batchSize);
    }

    @KafkaListener(topics = {Topics.USER, Topics.FRIEND, Topics.POST, Topics.CHAT, Topics.MEDIA, Topics.BILLING},
            groupId = "notification-service")
    public void onMessage(String json) {
        EventEnvelope e = EventJson.read(json); // không đọc được: IllegalArgumentException, vào DLQ ngay
        if (seen.getIfPresent(e.eventId()) != null) {
            return;
        }
        List<Notification> notifications = planner.plan(e);
        for (int from = 0; from < notifications.size(); from += batchSize) {
            List<Notification> batch = notifications.subList(from, Math.min(from + batchSize, notifications.size()));
            executor.execute(() -> dispatchBatch(batch));
        }
        seen.put(e.eventId(), Boolean.TRUE);
    }

    private void dispatchBatch(List<Notification> batch) {
        for (Notification n : batch) {
            try {
                dispatcher.dispatch(n);
            } catch (Exception ex) {
                log.warn("Gửi thông báo lỗi: {}", ex.toString());
            }
        }
    }
}
