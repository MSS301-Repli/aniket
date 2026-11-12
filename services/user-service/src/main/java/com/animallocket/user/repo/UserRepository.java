package com.animallocket.user.repo;

import com.animallocket.user.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByPublicId(String publicId);

    Optional<UserAccount> findByKeycloakSub(String sub);

    List<UserAccount> findByPublicIdIn(Collection<String> publicIds);

    /** Tài khoản còn hoạt động/tạm khoá nhưng chưa có avatar (tạo trước khi có cơ chế avatar). */
    @Query("select u.publicId from UserAccount u where u.avatarKey is null and u.status <> :deleted order by u.createdAt")
    List<String> findPublicIdsWithoutAvatar(@Param("deleted") UserAccount.Status deleted);

    /** Gán avatar chỉ khi người đó chưa có: hai instance cùng backfill thì một bên nhận 0 và rollback. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAccount u set u.avatarKey = :key, u.avatarVersion = :version, u.leadAnimal = :animal "
            + "where u.publicId = :id and u.avatarKey is null")
    int assignAvatarIfMissing(@Param("id") String publicId, @Param("key") String key,
                              @Param("version") int version, @Param("animal") String animal);
}
