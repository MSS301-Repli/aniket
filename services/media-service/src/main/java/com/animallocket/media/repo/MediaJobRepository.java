package com.animallocket.media.repo;

import com.animallocket.media.domain.MediaJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MediaJobRepository extends JpaRepository<MediaJob, String> {
    List<MediaJob> findByStatusInAndUpdatedAtBefore(Collection<MediaJob.Status> statuses, Instant before);

    List<MediaJob> findByOwnerPublicId(String ownerPublicId);

    long countByOwnerPublicIdAndCreatedAtGreaterThanEqual(String ownerPublicId, Instant since);
}
