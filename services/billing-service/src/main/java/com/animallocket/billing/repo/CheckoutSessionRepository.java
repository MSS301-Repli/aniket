package com.animallocket.billing.repo;

import com.animallocket.billing.domain.CheckoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {
    Optional<CheckoutSession> findByTokenHash(String tokenHash);

    Optional<CheckoutSession> findFirstByPaypalOrderId(String paypalOrderId);

    Optional<CheckoutSession> findFirstByPaypalCaptureId(String paypalCaptureId);
}
