package com.animallocket.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Sổ cái chỉ thêm, không sửa. (reason, ref, item) duy nhất nên cộng/trừ cùng một sự kiện hai lần là không thể. */
@Entity
@Table(name = "wallet_ledger",
        uniqueConstraints = @UniqueConstraint(name = "uk_ledger_ref", columnNames = {"reason", "ref", "item"}),
        indexes = @Index(name = "idx_ledger_user", columnList = "public_id,created_at"))
@Getter
@NoArgsConstructor
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, length = 20)
    private String publicId;
    @Column(nullable = false, length = 40)
    private String item;
    @Column(nullable = false)
    private int delta;
    /** PURCHASE | CONSUME | REFUND */
    @Column(nullable = false, length = 12)
    private String reason;
    @Column(nullable = false, length = 100)
    private String ref;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public LedgerEntry(String publicId, String item, int delta, String reason, String ref) {
        this.publicId = publicId;
        this.item = item;
        this.delta = delta;
        this.reason = reason;
        this.ref = ref;
    }
}
