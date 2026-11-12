package com.animallocket.user.repo;

import com.animallocket.user.domain.Entitlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntitlementRepository extends JpaRepository<Entitlement, String> {
}
