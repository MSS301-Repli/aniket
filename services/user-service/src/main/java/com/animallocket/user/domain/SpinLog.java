package com.animallocket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "spin_log", indexes = @Index(name = "idx_spin_user_time", columnList = "public_id,spun_at"))
@Getter
@NoArgsConstructor
public class SpinLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, length = 20)
    private String publicId;
    @Column(nullable = false, length = 40)
    private String animalCode;
    @Column(name = "spun_at", nullable = false)
    private Instant spunAt = Instant.now();

    public SpinLog(String publicId, String animalCode) {
        this.publicId = publicId;
        this.animalCode = animalCode;
    }
}
