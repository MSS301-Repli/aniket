package com.animallocket.post.service;

import com.animallocket.common.web.ApiException;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Post;
import com.animallocket.post.dto.PostDtos;
import com.animallocket.post.repo.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PostService {
    private final PostRepository posts;
    private final QuotaService quota;

    public PostService(PostRepository posts, QuotaService quota) {
        this.posts = posts;
        this.quota = quota;
    }

    // ---------------- tạo / xoá ----------------

    @Transactional
    public PostDtos.PostView create(String me, PostDtos.CreatePostRequest req) {
        Optional<Post> existing = posts.findByMediaId(req.mediaId());
        if (existing.isPresent()) {
            if (!existing.get().getAuthorId().equals(me)) {
                throw ApiException.forbidden("Ảnh này không thuộc về bạn");
            }
            return view(me, existing.get(), Map.of(), Set.of());
        }
        quota.consume(me);

        Post p = new Post();
        p.setId(UUID.randomUUID().toString());
        p.setAuthorId(me);
        p.setMediaId(req.mediaId());
        p.setCaption(req.caption() == null || req.caption().isBlank() ? null : req.caption().strip());
        posts.save(p);
        return view(me, p, Map.of(), Set.of());
    }

    // ---------------- nội bộ ----------------

    private PostDtos.PostView view(String me, Post p, Map<String, UserDirectory.Profile> profiles, Set<String> liked) {
        UserDirectory.Profile prof = profiles.get(p.getAuthorId());
        PostDtos.Author author = new PostDtos.Author(p.getAuthorId(),
                prof == null ? null : prof.alias(), prof == null ? null : prof.leadAnimal(),
                prof == null ? null : prof.avatar());
        return new PostDtos.PostView(p.getId(), author, p.getCaption(), p.getMediaId(), p.getStatus(),
                p.getLikeCount(), p.getCommentCount(), liked.contains(p.getId()), p.getCreatedAt(), p.getPublishedAt());
    }
}
