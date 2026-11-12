package com.animallocket.user.messaging;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.outbox.IdempotencyService;
import com.animallocket.user.domain.Entitlement;
import com.animallocket.user.repo.EntitlementRepository;
import com.animallocket.user.repo.UserRepository;
import com.animallocket.user.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Giữ bản sao quyền lợi Pro và mở gói con vật theo event của billing-service. */
@Component
public class BillingEventsListener {
    private static final Logger log = LoggerFactory.getLogger(BillingEventsListener.class);
    private static final String PACK_PREFIX = "ANIMAL_PACK:";

    private final IdempotencyService idempotency;
    private final EntitlementRepository entitlements;
    private final UserRepository users;
    private final AccountService accounts;

    public BillingEventsListener(IdempotencyService idempotency, EntitlementRepository entitlements,
                                 UserRepository users, AccountService accounts) {
        this.idempotency = idempotency;
        this.entitlements = entitlements;
        this.users = users;
        this.accounts = accounts;
    }

    @KafkaListener(topics = Topics.BILLING, groupId = "user-service")
    @Transactional
    public void onMessage(String json) {
        handle(EventJson.read(json));
    }

    void handle(EventEnvelope e) {
        if (!idempotency.firstTime(e.eventId())) {
            return;
        }
        switch (e.type()) {
            case EventTypes.SUBSCRIPTION_ACTIVATED -> {
                var p = e.payloadAs(Events.SubscriptionChanged.class);
                if (users.findByPublicId(p.publicId()).isPresent()) {
                    entitlements.save(new Entitlement(p.publicId(), "PRO", p.expiresAt()));
                }
            }
            case EventTypes.SUBSCRIPTION_EXPIRED -> {
                var p = e.payloadAs(Events.SubscriptionChanged.class);
                if (users.findByPublicId(p.publicId()).isPresent()) {
                    entitlements.save(new Entitlement(p.publicId(), "FREE", null));
                }
            }
            case EventTypes.CONSUMABLE_GRANTED -> {
                var p = e.payloadAs(Events.ConsumableGranted.class);
                if (p.item() != null && p.item().startsWith(PACK_PREFIX)) {
                    accounts.grantPack(p.publicId(), p.item().substring(PACK_PREFIX.length()));
                }
            }
            default -> log.debug("Bỏ qua event {}", e.type());
        }
    }
}
