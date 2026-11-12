package com.animallocket.chat.repo;

import com.animallocket.chat.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            String conversationId, Instant before, Pageable page);

    long countByConversationIdAndSenderIdNotAndCreatedAtGreaterThan(
            String conversationId, String notSender, Instant after);

    Optional<Message> findBySenderIdAndClientId(String senderId, String clientId);

    void deleteByConversationId(String conversationId);
}
