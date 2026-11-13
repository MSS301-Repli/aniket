package com.animallocket.billing.service;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.outbox.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** Việc nền: hạ thuê bao quá hạn về miễn phí, và dọn dữ liệu khi người dùng xoá tài khoản. */
@Component
public class BillingMaintenance {
    private static final Logger log = LoggerFactory.getLogger(BillingMaintenance.class);

    private final BillingService billing;
    private final IdempotencyService idempotency;
    private final Duration grace;

    public BillingMaintenance(BillingService billing, IdempotencyService idempotency,
                              @Value("${animal.billing.expiry-grace:1h}") Duration grace) {
        this.billing = billing;
        this.idempotency = idempotency;
        this.grace = grace;
    }

    /** Có thời gian ân hạn vì thông báo gia hạn từ store có thể đến trễ vài phút sau hạn cũ. */
    @Scheduled(fixedDelayString = "${animal.billing.expiry-interval-ms:300000}")
    public void expire() {
        try {
            int n = billing.expireDue(grace);
            if (n > 0) {
                log.info("Hạ {} thuê bao quá hạn về miễn phí", n);
            }
        } catch (RuntimeException e) {
            log.error("expire lỗi", e);
        }
    }

    @KafkaListener(topics = Topics.USER, groupId = "billing-service")
    @Transactional
    public void onUserEvent(String json) {
        EventEnvelope e = EventJson.read(json);
        if (!EventTypes.USER_DELETED.equals(e.type()) || !idempotency.firstTime(e.eventId())) {
            return;
        }
        billing.onUserDeleted(e.payloadAs(Events.UserDeleted.class).publicId());
    }
}
