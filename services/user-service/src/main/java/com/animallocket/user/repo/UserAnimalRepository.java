package com.animallocket.user.repo;

import com.animallocket.user.domain.UserAnimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserAnimalRepository extends JpaRepository<UserAnimal, Long> {
    List<UserAnimal> findByPublicId(String publicId);

    boolean existsByPublicIdAndAnimalCode(String publicId, String animalCode);

    @Modifying
    @Query("delete from UserAnimal u where u.publicId = :id")
    int deleteAllOf(String id);
}
