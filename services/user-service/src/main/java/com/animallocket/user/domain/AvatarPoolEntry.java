package com.animallocket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Một tổ hợp avatar trong kho. {@code ownerPublicId} đã gán thì không bao giờ về null,
 * kể cả khi tài khoản bị xoá (docs/19-avatar-mechanism.md §4.5). Chỉ ghi bằng câu UPDATE có điều kiện
 * trong {@code AvatarPoolRepository.claim}, không sửa qua entity.
 */
@Entity
@Table(name = "avatar_pool", indexes = @Index(name = "idx_pool_owner", columnList = "owner_public_id"))
@Getter
@NoArgsConstructor
public class AvatarPoolEntry {
    @Id
    @Column(name = "avatar_key", length = 80)
    private String avatarKey;
    @Column(name = "lib_version", nullable = false)
    private int libVersion;
    @Column(name = "owner_public_id", length = 20)
    private String ownerPublicId;
    @Column(name = "assigned_at")
    private Instant assignedAt;
}
