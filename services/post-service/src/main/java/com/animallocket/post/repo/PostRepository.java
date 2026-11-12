package com.animallocket.post.repo;

import com.animallocket.post.domain.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, String> {
    Optional<Post> findByMediaId(String mediaId);

    /** Đọc có khoá (SELECT ... FOR UPDATE): thấy bản đã commit mới nhất và chặn người cập nhật song song. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.mediaId = :mediaId")
    Optional<Post> findForUpdateByMediaId(String mediaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findForUpdateById(String id);

    /** Bài còn PENDING_MEDIA nhưng post-service đã nhận kết quả ảnh (READY/REJECTED). */
    @Query("select p.id from Post p where p.status = com.animallocket.post.domain.Post.Status.PENDING_MEDIA"
            + " and exists (select 1 from MediaState m where m.mediaId = p.mediaId)")
    List<String> findStuckPendingIds(Pageable page);

    List<Post> findByAuthorIdInAndStatusAndPublishedAtLessThanOrderByPublishedAtDesc(
            Collection<String> authors, Post.Status status, Instant before, Pageable page);

    List<Post> findByAuthorIdAndStatusNotOrderByCreatedAtDesc(String authorId, Post.Status status, Pageable page);

    List<Post> findByAuthorIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDesc(
            String authorId, Post.Status status, Instant before, Pageable page);

    List<Post> findByAuthorId(String authorId);

    @Modifying
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id")
    void incLikes(String id);

    @Modifying
    @Query("update Post p set p.likeCount = case when p.likeCount > 0 then p.likeCount - 1 else 0 end where p.id = :id")
    void decLikes(String id);

    @Modifying
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :id")
    void incComments(String id);

    @Modifying
    @Query("update Post p set p.commentCount = case when p.commentCount > 0 then p.commentCount - 1 else 0 end where p.id = :id")
    void decComments(String id);
}
