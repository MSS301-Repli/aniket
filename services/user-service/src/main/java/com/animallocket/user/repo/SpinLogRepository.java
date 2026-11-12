package com.animallocket.user.repo;

import com.animallocket.user.domain.SpinLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface SpinLogRepository extends JpaRepository<SpinLog, Long> {
    Optional<SpinLog> findFirstByPublicIdOrderBySpunAtDesc(String publicId);

    long countByPublicIdAndSpunAtGreaterThanEqual(String publicId, Instant since);

    @Modifying
    @Query("delete from SpinLog s where s.publicId = :id")
    int deleteAllOf(String id);
}
