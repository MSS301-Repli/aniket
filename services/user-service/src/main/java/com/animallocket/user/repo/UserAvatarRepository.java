package com.animallocket.user.repo;

import com.animallocket.user.domain.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
    List<UserAvatar> findByPublicIdOrderByCreatedAtAsc(String publicId);
}
