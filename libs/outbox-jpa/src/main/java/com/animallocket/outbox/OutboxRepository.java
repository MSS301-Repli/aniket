package com.animallocket.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    /** Event chờ đẩy (chưa đẩy, chưa FAILED). SKIP LOCKED (timeout -2): nhiều instance relay không giẫm nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findTop100ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();

    long countByFailedAtIsNotNull();

    long countByPublishedAtIsNullAndFailedAtIsNull();

    @Query("select min(e.createdAt) from OutboxEvent e where e.publishedAt is null and e.failedAt is null")
    Instant oldestPendingCreatedAt();

    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :before")
    int deletePublishedBefore(Instant before);
}
