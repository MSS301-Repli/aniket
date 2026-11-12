package com.animallocket.billing.repo;

import com.animallocket.billing.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {
    boolean existsByReasonAndRefAndItem(String reason, String ref, String item);

    List<LedgerEntry> findByPublicIdOrderByIdDesc(String publicId);

    void deleteByPublicId(String publicId);
}
