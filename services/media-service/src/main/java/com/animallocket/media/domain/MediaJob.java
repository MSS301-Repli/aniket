package com.animallocket.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Một lần xử lý ảnh. PENDING_UPLOAD → READY | REJECTED. */
@Entity
@Table(name = "media_jobs", indexes = {
        @Index(name = "idx_media_owner", columnList = "owner_public_id"),
        @Index(name = "idx_media_status_updated", columnList = "status,updated_at")})
@Getter
@Setter
@NoArgsConstructor
public class MediaJob {
    public enum Status { PENDING_UPLOAD, READY, REJECTED }

    public enum Context { POST, CHAT }

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "owner_public_id", nullable = false, length = 20)
    private String ownerPublicId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Context context;
    @Column(length = 64)
    private String refId;
    @Column(nullable = false, length = 120)
    private String rawKey;
    @Column(length = 120)
    private String publicKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING_UPLOAD;
    @Column(length = 40)
    private String leadAnimal;
    @Column(length = 10)
    private String method;
    private Integer facesDetected;
    @Column(length = 60)
    private String rejectReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
