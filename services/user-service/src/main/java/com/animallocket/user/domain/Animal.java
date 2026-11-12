package com.animallocket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "animals")
@Getter
@NoArgsConstructor
public class Animal {
    public enum Rarity { COMMON, RARE, EPIC }

    @Id
    @Column(length = 40)
    private String code;
    @Column(nullable = false, length = 60)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Rarity rarity;
    /** null = thuộc bộ cơ bản (có thể quay trúng); khác null = chỉ có khi mua gói. */
    @Column(length = 40)
    private String packCode;

    public Animal(String code, String name, Rarity rarity, String packCode) {
        this.code = code;
        this.name = name;
        this.rarity = rarity;
        this.packCode = packCode;
    }
}
