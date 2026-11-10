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
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "friend_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"from_id", "to_id"}),
        indexes = {@Index(name = "idx_fr_to_status", columnList = "to_id,status"),
                @Index(name = "idx_fr_from_created", columnList = "from_id,created_at")})
@Getter
@Setter
@NoArgsConstructor
public class FriendRequest {
    public enum Source { SWIPE, INVITE }

    public enum Status { PENDING, ACCEPTED, DECLINED, CANCELED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "from_id", nullable = false, length = 20)
    private String fromId;
    @Column(name = "to_id", nullable = false, length = 20)
    private String toId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public FriendRequest(String fromId, String toId, Source source) {
        this.fromId = fromId;
        this.toId = toId;
        this.source = source;
    }
}
