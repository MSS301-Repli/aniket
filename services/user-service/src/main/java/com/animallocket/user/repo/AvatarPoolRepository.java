package com.animallocket.user.repo;

import com.animallocket.user.domain.AvatarPoolEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AvatarPoolRepository extends JpaRepository<AvatarPoolEntry, String> {
    long countByOwnerPublicIdIsNull();

    /** Avatar trống thứ {@code offset} theo thứ tự khoá (offset ngẫu nhiên → chọn đều trên phần còn trống). */
    @Query(value = "SELECT avatar_key FROM avatar_pool WHERE owner_public_id IS NULL ORDER BY avatar_key "
            + "LIMIT 1 OFFSET :offset", nativeQuery = true)
    List<String> freeKeyAt(@Param("offset") long offset);

    /** Giữ chỗ nguyên tử: chỉ thành công (trả 1) nếu avatar vẫn chưa có chủ. Đây là chỗ đảm bảo không trùng. */
    @Modifying(flushAutomatically = true)
    @Query("update AvatarPoolEntry p set p.ownerPublicId = :owner, p.assignedAt = :now "
            + "where p.avatarKey = :key and p.ownerPublicId is null")
    int claim(@Param("key") String key, @Param("owner") String owner, @Param("now") Instant now);

    @Query("select p.avatarKey from AvatarPoolEntry p")
    List<String> allKeys();
}
