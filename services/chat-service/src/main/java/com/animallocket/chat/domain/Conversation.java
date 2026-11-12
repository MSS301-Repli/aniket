package com.animallocket.chat.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hội thoại 1-1. {@code key} = "idThấp:idCao" bảo đảm mỗi cặp chỉ có một hội thoại. */
@Document("conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {
    public enum Status { ACTIVE, CLOSED }

    @Id
    private String id;
    @Indexed(unique = true)
    private String key;
    @Indexed
    private List<String> participants;
    private Status status = Status.ACTIVE;
    private String closedReason;
    private Instant createdAt = Instant.now();
    private Instant lastMessageAt;
    private Map<String, Instant> lastReadAt = new HashMap<>();

    public static String keyOf(String a, String b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public String other(String me) {
        return participants.get(0).equals(me) ? participants.get(1) : participants.get(0);
    }

    public boolean has(String publicId) {
        return participants.contains(publicId);
    }
}
