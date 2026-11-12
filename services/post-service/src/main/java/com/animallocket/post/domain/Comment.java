package com.animallocket.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "comments", indexes = @Index(name = "idx_comment_post", columnList = "post_id,id"))
@Getter
@NoArgsConstructor
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "post_id", nullable = false, length = 36)
    private String postId;
    @Column(name = "author_id", nullable = false, length = 20)
    private String authorId;
    @Column(nullable = false, length = 300)
    private String text;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Comment(String postId, String authorId, String text) {
        this.postId = postId;
        this.authorId = authorId;
        this.text = text;
    }
}
