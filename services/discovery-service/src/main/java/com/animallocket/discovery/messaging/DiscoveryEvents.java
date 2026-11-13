package com.animallocket.discovery.messaging;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.discovery.service.DiscoveryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Các thao tác ở đây đều idempotent nên không cần bảng khử trùng lặp (service không có DB). */
@Component
public class DiscoveryEvents {
    private final DiscoveryService discovery;

    public DiscoveryEvents(DiscoveryService discovery) {
        this.discovery = discovery;
    }

    @KafkaListener(topics = {Topics.USER, Topics.BILLING}, groupId = "discovery-service")
    public void onMessage(String json) {
        handle(EventJson.read(json));
    }

    void handle(EventEnvelope e) {
        switch (e.type()) {
            case EventTypes.USER_DELETED -> discovery.forget(e.payloadAs(Events.UserDeleted.class).publicId());
            case EventTypes.DISCOVERY_DISABLED -> discovery.clearLocation(e.payloadAs(Events.DiscoveryDisabled.class).publicId());
            case EventTypes.SUBSCRIPTION_ACTIVATED -> {
                var p = e.payloadAs(Events.SubscriptionChanged.class);
                discovery.setPro(p.publicId(), p.expiresAt());
            }
            case EventTypes.SUBSCRIPTION_EXPIRED -> discovery.clearPro(e.payloadAs(Events.SubscriptionChanged.class).publicId());
            default -> {
                // USER_BLOCKED, FRIEND_ACCEPTED: bộ thẻ lấy danh sách loại trừ trực tiếp từ user-service mỗi lần
            }
        }
    }

    /** GEO của Redis không có TTL cho từng member nên dọn định kỳ. */
    @Scheduled(fixedDelayString = "${animal.discovery.purge-interval-ms:60000}")
    public void purge() {
        discovery.purge();
    }
}
