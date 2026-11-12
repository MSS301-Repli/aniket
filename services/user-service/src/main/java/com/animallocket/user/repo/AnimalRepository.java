package com.animallocket.user.repo;

import com.animallocket.user.domain.Animal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnimalRepository extends JpaRepository<Animal, String> {
    List<Animal> findByPackCodeIsNull();

    List<Animal> findByPackCode(String packCode);

    List<Animal> findByPackCodeIsNullAndRarity(Animal.Rarity rarity);
}
