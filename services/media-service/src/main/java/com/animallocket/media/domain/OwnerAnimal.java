package com.animallocket.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bản sao con vật chủ đạo của từng người, cập nhật bằng event USER_REGISTERED/LEAD_ANIMAL_CHANGED. */
@Entity
@Table(name = "owner_animals")
@Getter
@Setter
@NoArgsConstructor
public class OwnerAnimal {
    @Id
    @Column(length = 20)
    private String publicId;
    @Column(nullable = false, length = 40)
    private String leadAnimal;

    public OwnerAnimal(String publicId, String leadAnimal) {
        this.publicId = publicId;
        this.leadAnimal = leadAnimal;
    }
}
