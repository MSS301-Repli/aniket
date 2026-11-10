package com.animallocket.post.messaging;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.outbox.IdempotencyService;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.service.PostService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostEventsListener {
    private final IdempotencyService idempotency;
    private final PostService posts;
    private final UserDirectory users;

    public PostEventsListener(IdempotencyService idempotency, PostService posts, UserDirectory users) {
        this.idempotency = idempotency;
        this.posts = posts;
        this.users = users;
    }

    @KafkaListener(topics = {Topics.MEDIA, Topics.USER, Topics.FRIEND}, groupId = "post-service")
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
            case EventTypes.USER_DELETED -> posts.onUserDeleted(e.payloadAs(Events.UserDeleted.class).publicId());
            case EventTypes.USER_BLOCKED -> {
                var p = e.payloadAs(Events.UserBlocked.class);
                users.invalidateFriends(p.blockerPublicId(), p.blockedPublicId());
            }
            case EventTypes.FRIEND_ACCEPTED -> {
                var p = e.payloadAs(Events.FriendAccepted.class);
                users.invalidateFriends(p.userAPublicId(), p.userBPublicId());
            }
            case EventTypes.FRIEND_REMOVED -> {
                var p = e.payloadAs(Events.FriendRemoved.class);
                users.invalidateFriends(p.userAPublicId(), p.userBPublicId());
            }
            default -> {
                // không liên quan tới post-service
            }
        }
    }
}
