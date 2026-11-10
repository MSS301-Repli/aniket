package com.animallocket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Bản sao quyền lợi Pro, cập nhật bằng event từ billing-service (không gọi billing mỗi request). */
@Entity
@Table(name = "entitlements")
@Getter
@Setter
@NoArgsConstructor
public class Entitlement {
    @Id
    @Column(length = 20)
    private String publicId;
    @Column(nullable = false, length = 10)
    private String plan;
    private Instant expiresAt;

    public Entitlement(String publicId, String plan, Instant expiresAt) {
        this.publicId = publicId;
        this.plan = plan;
        this.expiresAt = expiresAt;
    }

    public boolean isActivePro() {
        return "PRO".equals(plan) && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
