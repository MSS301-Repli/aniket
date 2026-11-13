package com.animallocket.billing.domain;

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
@Table(name = "subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_sub_store_orig", columnNames = {"store", "original_transaction_id"}),
        indexes = {@Index(name = "idx_sub_user", columnList = "public_id"),
                @Index(name = "idx_sub_status_exp", columnList = "status,expires_at")})
@Getter
@Setter
@NoArgsConstructor
public class Subscription {
    public enum Status { ACTIVE, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, length = 20)
    private String publicId;
    @Column(nullable = false, length = 60)
    private String sku;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Store store;
    @Column(name = "original_transaction_id", nullable = false, length = 100)
    private String originalTransactionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ACTIVE;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
