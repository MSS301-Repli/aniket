package com.animallocket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {
    public enum TargetType { USER, POST, COMMENT, MESSAGE }

    public enum Status { OPEN, ACTIONED, DISMISSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String reporterId;
    @Column(nullable = false, length = 20)
    private String targetPublicId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TargetType targetType;
    @Column(length = 64)
    private String targetRef;
    @Column(nullable = false, length = 30)
    private String reason;
    @Column(length = 500)
    private String details;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
