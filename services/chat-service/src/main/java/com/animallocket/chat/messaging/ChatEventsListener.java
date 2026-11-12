package com.animallocket.chat.messaging;

import com.animallocket.chat.service.ChatService;
import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Các thao tác đều idempotent (đóng hội thoại, xoá dữ liệu), nên không cần bảng khử trùng lặp. */
@Component
public class ChatEventsListener {
    private final ChatService chat;

    public ChatEventsListener(ChatService chat) {
        this.chat = chat;
    }

    @KafkaListener(topics = {Topics.USER, Topics.FRIEND}, groupId = "chat-service")
    public void onMessage(String json) {
        handle(EventJson.read(json));
    }

    void handle(EventEnvelope e) {
        switch (e.type()) {
            case EventTypes.USER_BLOCKED -> {
                var p = e.payloadAs(Events.UserBlocked.class);
                chat.onBlocked(p.blockerPublicId(), p.blockedPublicId());
            }
            case EventTypes.FRIEND_ACCEPTED -> {
                var p = e.payloadAs(Events.FriendAccepted.class);
                chat.onFriendshipChanged(p.userAPublicId(), p.userBPublicId());
            }
            case EventTypes.FRIEND_REMOVED -> {
                var p = e.payloadAs(Events.FriendRemoved.class);
                chat.onUnfriended(p.userAPublicId(), p.userBPublicId());
            }
            case EventTypes.USER_DELETED -> chat.onUserDeleted(e.payloadAs(Events.UserDeleted.class).publicId());
            default -> {
                // không liên quan
            }
        }
    }
}
