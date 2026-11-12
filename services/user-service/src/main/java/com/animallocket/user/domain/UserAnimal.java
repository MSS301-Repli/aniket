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
@Table(name = "user_animals", uniqueConstraints = @UniqueConstraint(columnNames = {"public_id", "animal_code"}))
@Getter
@NoArgsConstructor
public class UserAnimal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, length = 20)
    private String publicId;
    @Column(name = "animal_code", nullable = false, length = 40)
    private String animalCode;
    @Column(nullable = false)
    private Instant acquiredAt = Instant.now();

    public UserAnimal(String publicId, String animalCode) {
        this.publicId = publicId;
        this.animalCode = animalCode;
    }
}
