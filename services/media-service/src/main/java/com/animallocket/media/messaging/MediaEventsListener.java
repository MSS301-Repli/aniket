package com.animallocket.media.messaging;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.media.service.MediaService;
import com.animallocket.outbox.IdempotencyService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MediaEventsListener {
    private final IdempotencyService idempotency;
    private final MediaService media;

    public MediaEventsListener(IdempotencyService idempotency, MediaService media) {
        this.idempotency = idempotency;
        this.media = media;
    }

    @KafkaListener(topics = {Topics.USER}, groupId = "media-service")
    @Transactional
    public void onMessage(String json) {
        handle(EventJson.read(json));
    }

    void handle(EventEnvelope e) {
        if (!idempotency.firstTime(e.eventId())) {
            return;
        }
        switch (e.type()) {
            case EventTypes.USER_REGISTERED -> {
                var p = e.payloadAs(Events.UserRegistered.class);
                media.setOwnerAnimal(p.publicId(), p.leadAnimal());
            }
            case EventTypes.LEAD_ANIMAL_CHANGED -> {
                var p = e.payloadAs(Events.LeadAnimalChanged.class);
                media.setOwnerAnimal(p.publicId(), p.leadAnimal());
            }
            case EventTypes.USER_DELETED -> media.onUserDeleted(e.payloadAs(Events.UserDeleted.class).publicId());
            default -> {
                // event khác không liên quan
            }
        }
    }
}
