package com.animallocket.media.repo;

import com.animallocket.media.domain.OwnerAnimal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerAnimalRepository extends JpaRepository<OwnerAnimal, String> {
}
