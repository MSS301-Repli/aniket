package com.animallocket.chat.repo;

import com.animallocket.chat.domain.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {
    Optional<Conversation> findByKey(String key);

    List<Conversation> findByParticipantsOrderByLastMessageAtDesc(String participant);
}
