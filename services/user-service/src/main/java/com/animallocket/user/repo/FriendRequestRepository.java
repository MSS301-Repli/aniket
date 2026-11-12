package com.animallocket.user.repo;

import com.animallocket.user.domain.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    Optional<FriendRequest> findByFromIdAndToId(String from, String to);

    List<FriendRequest> findByToIdAndStatusOrderByCreatedAtDesc(String to, FriendRequest.Status status);

    List<FriendRequest> findByFromIdAndStatusOrderByCreatedAtDesc(String from, FriendRequest.Status status);

    long countByFromIdAndCreatedAtGreaterThanEqual(String from, Instant since);

    @Modifying
    @Query("delete from FriendRequest r where r.fromId = :id or r.toId = :id")
    int deleteAllOf(String id);
}
