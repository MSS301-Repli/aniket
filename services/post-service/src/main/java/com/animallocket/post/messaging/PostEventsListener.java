package com.animallocket.post.messaging;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.outbox.IdempotencyService;
import com.animallocket.post.service.PostService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostEventsListener {
    private final IdempotencyService idempotency;
    private final PostService posts;

    public PostEventsListener(IdempotencyService idempotency, PostService posts) {
        this.idempotency = idempotency;
        this.posts = posts;
    }

    @KafkaListener(topics = {Topics.MEDIA}, groupId = "post-service")
    @Transactional
    public void onMessage(String json) {
        handle(EventJson.read(json));
    }

    void handle(EventEnvelope e) {
        if (!idempotency.firstTime(e.eventId())) {
            return;
        }
        switch (e.type()) {
            case EventTypes.PHOTO_READY -> {
                var p = e.payloadAs(Events.PhotoReady.class);
                if ("POST".equals(p.context())) {
                    posts.onPhotoReady(p);
                }
            }
            case EventTypes.PHOTO_REJECTED -> {
                var p = e.payloadAs(Events.PhotoRejected.class);
                if ("POST".equals(p.context())) {
                    posts.onPhotoRejected(p);
                }
            }
            default -> {
                // không liên quan tới post-service
            }
        }
    }
}
