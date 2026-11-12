package com.animallocket.chat.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("messages")
@CompoundIndexes({
        @CompoundIndex(name = "conv_created", def = "{'conversationId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "sender_client", unique = true,
                def = "{'senderId': 1, 'clientId': 1}",
                partialFilter = "{'clientId': {$exists: true}}")
})
@Getter
@NoArgsConstructor
public class Message {
    public enum Type { TEXT, IMAGE, GLYPH }

    @Id
    private String id;
    private String conversationId;
    private String senderId;
    private Type type;
    private String text;
    private String mediaId;
    private List<String> glyphIds;
    private Long dictionaryRevision;
    @Setter
    private String clientId;
    private Instant createdAt = Instant.now();

    public Message(String conversationId, String senderId, Type type, String text, String mediaId) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.type = type;
        this.text = text;
        this.mediaId = mediaId;
    }

    public static Message glyph(String conversationId, String senderId, List<String> glyphIds,
                                String fallback, long dictionaryRevision) {
        Message message = new Message(conversationId, senderId, Type.GLYPH, fallback, null);
        message.glyphIds = List.copyOf(glyphIds);
        message.dictionaryRevision = dictionaryRevision;
        return message;
    }
}
