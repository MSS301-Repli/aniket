package com.animallocket.post.repo;

import com.animallocket.post.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(String postId, String userId);

    long deleteByPostIdAndUserId(String postId, String userId);

    /**
     * Chèn một lượt like, bỏ qua khi (post_id, user_id) đã có. Trả về 1 nếu chèn mới, 0 nếu đã like từ trước,
     * kể cả khi hai request đồng thời cùng chèn.
     */
    @Modifying
    @Query(value = "insert ignore into post_likes (post_id, user_id, created_at) values (:postId, :userId, :createdAt)",
            nativeQuery = true)
    int insertIfAbsent(String postId, String userId, Instant createdAt);

    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    List<String> likedAmong(String userId, Collection<String> postIds);

    @Modifying
    @Query("delete from PostLike l where l.postId = :postId")
    void deleteAllOfPost(String postId);

    @Modifying
    @Query("delete from PostLike l where l.userId = :userId")
    void deleteAllOfUser(String userId);
}
