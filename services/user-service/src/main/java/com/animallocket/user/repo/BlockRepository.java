package com.animallocket.user.repo;

import com.animallocket.user.domain.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {
    boolean existsByBlockerIdAndBlockedId(String blocker, String blocked);

    long deleteByBlockerIdAndBlockedId(String blocker, String blocked);

    List<Block> findByBlockerId(String blocker);

    List<Block> findByBlockedId(String blocked);

    @Query("select count(b) > 0 from Block b where (b.blockerId = :a and b.blockedId = :b) or (b.blockerId = :b and b.blockedId = :a)")
    boolean existsEitherWay(String a, String b);

    @Modifying
    @Query("delete from Block b where b.blockerId = :id or b.blockedId = :id")
    int deleteAllOf(String id);
}
