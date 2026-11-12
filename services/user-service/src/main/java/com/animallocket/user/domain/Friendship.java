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

/** Quan hệ bạn bè không có hướng: luôn lưu theo thứ tự {@code low < high} để tránh bản ghi kép. */
@Entity
@Table(name = "friendships", uniqueConstraints = @UniqueConstraint(columnNames = {"user_low", "user_high"}))
@Getter
@NoArgsConstructor
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_low", nullable = false, length = 20)
    private String userLow;
    @Column(name = "user_high", nullable = false, length = 20)
    private String userHigh;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Friendship(String a, String b) {
        if (a.compareTo(b) < 0) {
            this.userLow = a;
            this.userHigh = b;
        } else {
            this.userLow = b;
            this.userHigh = a;
        }
    }

    public String other(String me) {
        return me.equals(userLow) ? userHigh : userLow;
    }
}
