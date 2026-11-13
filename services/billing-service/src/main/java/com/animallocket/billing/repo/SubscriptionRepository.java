package com.animallocket.billing.repo;

import com.animallocket.billing.domain.Store;
import com.animallocket.billing.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByStoreAndOriginalTransactionId(Store store, String originalTransactionId);

    List<Subscription> findByPublicId(String publicId);

    Optional<Subscription> findFirstByPublicIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
            String publicId, Subscription.Status status, Instant after);

    List<Subscription> findByStatusAndExpiresAtBefore(Subscription.Status status, Instant before);

    void deleteByPublicId(String publicId);
}
