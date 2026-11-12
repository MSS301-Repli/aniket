package com.animallocket.post.domain;

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

/** PENDING_MEDIA → PUBLISHED (khi PHOTO_READY) | REJECTED (khi PHOTO_REJECTED); mọi trạng thái → DELETED. */
@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_post_feed", columnList = "author_id,status,published_at"),
        @Index(name = "idx_post_status_created", columnList = "status,created_at")})
@Getter
@Setter
@NoArgsConstructor
public class Post {
    public enum Status { PENDING_MEDIA, PUBLISHED, REJECTED, DELETED }

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "author_id", nullable = false, length = 20)
    private String authorId;
    @Column(length = 200)
    private String caption;
    @Column(nullable = false, unique = true, length = 36)
    private String mediaId;
    /** Chỉ lưu đường dẫn ảnh ĐÃ ẩn mặt; ảnh gốc không bao giờ đến đây. */
    @Column(length = 120)
    private String publicKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private Status status = Status.PENDING_MEDIA;
    @Column(length = 60)
    private String rejectReason;
    @Column(nullable = false)
    private int likeCount;
    @Column(nullable = false)
    private int commentCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "published_at")
    private Instant publishedAt;
}
