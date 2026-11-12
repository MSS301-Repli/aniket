package com.animallocket.post.repo;

import com.animallocket.post.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(String postId, String userId);

    long deleteByPostIdAndUserId(String postId, String userId);

    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    List<String> likedAmong(String userId, Collection<String> postIds);

    @Modifying
    @Query("delete from PostLike l where l.postId = :postId")
    void deleteAllOfPost(String postId);

    @Modifying
    @Query("delete from PostLike l where l.userId = :userId")
    void deleteAllOfUser(String userId);
}
