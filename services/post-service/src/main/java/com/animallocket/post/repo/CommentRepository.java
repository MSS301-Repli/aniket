package com.animallocket.post.repo;

import com.animallocket.post.domain.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdAndIdGreaterThanOrderByIdAsc(String postId, long afterId, Pageable page);

    @Modifying
    @Query("delete from Comment c where c.postId = :postId")
    void deleteAllOfPost(String postId);

    List<Comment> findByAuthorId(String authorId);
}
