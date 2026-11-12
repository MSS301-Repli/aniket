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
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "device_tokens", indexes = @Index(name = "idx_device_user", columnList = "public_id"))
@Getter
@Setter
@NoArgsConstructor
public class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, length = 20)
    private String publicId;
    @Column(nullable = false, unique = true, length = 255)
    private String token;
    @Column(nullable = false, length = 10)
    private String platform;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
