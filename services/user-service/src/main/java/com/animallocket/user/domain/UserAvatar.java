package com.animallocket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Bộ sưu tập avatar. {@code avatarKey} UNIQUE toàn bảng: lớp chặn trùng thứ hai sau kho. */
@Entity
@Table(name = "user_avatars",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_avatars_key", columnNames = "avatar_key"),
        indexes = @Index(name = "idx_user_avatars_owner", columnList = "public_id"))
@Getter
@NoArgsConstructor
public class UserAvatar {
    public enum Source { REGISTER, BACKFILL, PURCHASE, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, length = 20)
    private String publicId;
    @Column(name = "avatar_key", nullable = false, length = 80)
    private String avatarKey;
    @Column(name = "lib_version", nullable = false)
    private int libVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Source source;
    @Column(name = "source_ref", length = 64)
    private String sourceRef;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UserAvatar(String publicId, String avatarKey, int libVersion, Source source) {
        this.publicId = publicId;
        this.avatarKey = avatarKey;
        this.libVersion = libVersion;
        this.source = source;
    }
}
