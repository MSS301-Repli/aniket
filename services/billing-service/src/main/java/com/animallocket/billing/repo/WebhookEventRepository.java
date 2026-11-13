package com.animallocket.billing.repo;

import com.animallocket.billing.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {
}
