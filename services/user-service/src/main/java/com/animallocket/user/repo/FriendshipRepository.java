package com.animallocket.user.repo;

import com.animallocket.user.domain.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    Optional<Friendship> findByUserLowAndUserHigh(String low, String high);

    @Query("select f from Friendship f where f.userLow = :id or f.userHigh = :id")
    List<Friendship> findAllOf(String id);

    @Modifying
    @Query("delete from Friendship f where f.userLow = :id or f.userHigh = :id")
    int deleteAllOf(String id);
}
