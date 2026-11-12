package com.animallocket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
@Getter
@NoArgsConstructor
public class Block {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "blocker_id", nullable = false, length = 20)
    private String blockerId;
    @Column(name = "blocked_id", nullable = false, length = 20)
    private String blockedId;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Block(String blockerId, String blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }
}
