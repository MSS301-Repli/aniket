package com.animallocket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Hồ sơ ẩn danh. {@code keycloakSub} chỉ tồn tại trong bảng này và không bao giờ ra khỏi service
 * (trừ endpoint /internal/identity cho gateway).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {
    public enum AgeGroup { MINOR, ADULT }

    public enum Status { ACTIVE, SUSPENDED, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 20)
    private String publicId;
    @Column(nullable = false, unique = true, length = 100)
    private String keycloakSub;
    @Column(nullable = false, length = 30)
    private String alias;
    @Column(nullable = false, length = 40)
    private String leadAnimal;
    /** Avatar đang dùng "animal:palette:accessory"; duy nhất toàn hệ thống. Null chỉ với tài khoản đã xoá trước khi có avatar. */
    @Column(unique = true, length = 80)
    private String avatarKey;
    private Integer avatarVersion;
    private LocalDate birthDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AgeGroup ageGroup;
    @Column(nullable = false)
    private boolean discoverable;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.ACTIVE;
    private Instant aliasChangedAt;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Mã avatar đầy đủ "v:animal:palette:accessory" gửi cho client/service khác, null nếu chưa có. */
    public String avatarCode() {
        return avatarKey == null ? null : avatarVersion + ":" + avatarKey;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
