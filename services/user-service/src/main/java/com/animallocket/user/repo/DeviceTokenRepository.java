package com.animallocket.user.repo;

import com.animallocket.user.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByPublicId(String publicId);

    long deleteByTokenAndPublicId(String token, String publicId);

    @Modifying
    @Query("delete from DeviceToken d where d.publicId = :id")
    int deleteAllOf(String id);
}
