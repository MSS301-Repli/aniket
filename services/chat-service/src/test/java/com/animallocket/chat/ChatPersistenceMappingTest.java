package com.animallocket.chat;

import com.animallocket.chat.domain.Conversation;
import com.animallocket.chat.domain.ConversationGlyph;
import com.animallocket.chat.domain.Message;
import com.animallocket.chat.repo.ConversationRepository;
import com.animallocket.chat.repo.GlyphRepository;
import com.animallocket.chat.repo.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPersistenceMappingTest {
    @Test
    void mapsEachAggregateToItsOwnCollection() {
        assertThat(Conversation.class.getAnnotation(Document.class).collection())
                .isEqualTo("conversations");
        assertThat(Message.class.getAnnotation(Document.class).collection())
                .isEqualTo("messages");
        assertThat(ConversationGlyph.class.getAnnotation(Document.class).collection())
                .isEqualTo("conversation_glyphs");
    }

    @Test
    void definesStableConversationKeys() {
        assertThat(Conversation.keyOf("user-b", "user-a")).isEqualTo("user-a:user-b");

        Conversation conversation = new Conversation();
        conversation.setParticipants(List.of("user-a", "user-b"));
        assertThat(conversation.other("user-a")).isEqualTo("user-b");
        assertThat(conversation.has("user-b")).isTrue();
    }

    @Test
    void declaresIdempotencyAndRevisionIndexes() {
        CompoundIndexes messageIndexes = Message.class.getAnnotation(CompoundIndexes.class);
        CompoundIndexes glyphIndexes = ConversationGlyph.class.getAnnotation(CompoundIndexes.class);

        assertThat(messageIndexes.value()).extracting(index -> index.name())
                .contains("conv_created", "sender_client");
        assertThat(glyphIndexes.value()).extracting(index -> index.name())
                .contains("conv_creator_mutation", "conv_revision", "conv_shape_active");
    }

    @Test
    void exposesSpringDataRepositories() {
        assertThat(MongoRepository.class).isAssignableFrom(ConversationRepository.class);
        assertThat(MongoRepository.class).isAssignableFrom(MessageRepository.class);
        assertThat(MongoRepository.class).isAssignableFrom(GlyphRepository.class);
    }
}
