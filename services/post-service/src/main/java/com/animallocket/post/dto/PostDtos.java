package com.animallocket.post.dto;

import com.animallocket.post.domain.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PostDtos {
    private PostDtos() {
    }

    public record CreatePostRequest(@NotBlank @Size(max = 36) String mediaId, @Size(max = 200) String caption) {
    }

    /** {@code avatar} = "v:animal:palette:accessory", client tự vẽ (docs/19-avatar-mechanism.md). */
    public record Author(String publicId, String alias, String leadAnimal, String avatar) {
    }

    public record PostView(String id, Author author, String caption, String mediaId, Post.Status status,
                           int likeCount, int commentCount, boolean likedByMe, Instant createdAt,
                           Instant publishedAt) {
    }

    /** {@code degraded=true}: không tải được danh sách bạn nên feed tạm thời trống (fail soft). */
    public record FeedPage(List<PostView> items, Instant nextBefore, boolean degraded) {
    }
}
