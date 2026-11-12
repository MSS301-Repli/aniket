package com.animallocket.user.repo;

import com.animallocket.user.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ReportRepository extends JpaRepository<Report, Long> {
    long countByReporterIdAndCreatedAtGreaterThanEqual(String reporterId, Instant since);
}
