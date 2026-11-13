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

/** Mỗi giao dịch đã xác thực với store. (store, storeTransactionId) duy nhất để chống nhận lại cùng một biên lai. */
@Entity
@Table(name = "purchases",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_store_tx", columnNames = {"store", "store_transaction_id"}),
        indexes = @Index(name = "idx_purchase_user", columnList = "public_id"))
@Getter
@Setter
@NoArgsConstructor
public class Purchase {
    public enum Status { GRANTED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Store store;
    @Column(name = "store_transaction_id", nullable = false, length = 100)
    private String storeTransactionId;
    @Column(length = 100)
    private String originalTransactionId;
    /** "deleted" sau khi người dùng xoá tài khoản (giữ lại bản ghi tài chính nhưng bỏ liên kết danh tính). */
    @Column(name = "public_id", nullable = false, length = 20)
    private String publicId;
    @Column(nullable = false, length = 60)
    private String sku;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.GRANTED;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
