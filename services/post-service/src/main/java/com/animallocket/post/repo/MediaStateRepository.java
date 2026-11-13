package com.animallocket.post.repo;

import com.animallocket.post.domain.MediaState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaStateRepository extends JpaRepository<MediaState, String> {
    void deleteByOwnerId(String ownerId);
}
