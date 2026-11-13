package com.animallocket.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kết quả xử lý ảnh nhận từ media-service. Cần vì PHOTO_READY có thể đến TRƯỚC khi người dùng tạo bài
 * (client upload xong rồi mới gọi tạo bài).
 */
@Entity
@Table(name = "media_states")
@Getter
@NoArgsConstructor
public class MediaState {
    @Id
    @Column(length = 36)
    private String mediaId;
    @Column(nullable = false, length = 20)
    private String ownerId;
    @Column(nullable = false, length = 10)
    private String status; // READY | REJECTED
    @Column(length = 120)
    private String publicKey;
    @Column(length = 60)
    private String reason;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public MediaState(String mediaId, String ownerId, String status, String publicKey, String reason) {
        this.mediaId = mediaId;
        this.ownerId = ownerId;
        this.status = status;
        this.publicKey = publicKey;
        this.reason = reason;
    }

    public boolean ready() {
        return "READY".equals(status);
    }
}
