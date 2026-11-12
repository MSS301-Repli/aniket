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

@Document("conversation_glyphs")
@CompoundIndexes({
        @CompoundIndex(name = "conv_creator_mutation", unique = true,
                def = "{'conversationId': 1, 'creatorPublicId': 1, 'clientMutationId': 1}"),
        @CompoundIndex(name = "conv_revision", unique = true,
                def = "{'conversationId': 1, 'revision': 1}"),
        @CompoundIndex(name = "conv_status", def = "{'conversationId': 1, 'status': 1}"),
        @CompoundIndex(name = "conv_shape_active", unique = true,
                def = "{'conversationId': 1, 'shapeHash': 1}",
                partialFilter = "{'status': 'ACTIVE'}")
})
@Getter
@Setter
@NoArgsConstructor
public class ConversationGlyph {
    public enum Status { ACTIVE, ARCHIVED }

    @Id
    private String id;
    private String conversationId;
    private String creatorPublicId;
    private String clientMutationId;
    private String meaning;
    private String color;
    private List<Integer> normalizedPoints;
    private String shapeHash;
    private Status status = Status.ACTIVE;
    private long revision;
    private Instant createdAt = Instant.now();
    private Instant archivedAt;
}
