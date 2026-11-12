package com.animallocket.post.service;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.Events;
import com.animallocket.common.web.ApiException;
import com.animallocket.outbox.OutboxService;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.MediaState;
import com.animallocket.post.domain.Post;
import com.animallocket.post.dto.PostDtos;
import com.animallocket.post.repo.MediaStateRepository;
import com.animallocket.post.repo.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PostService {
    private final PostRepository posts;
    private final MediaStateRepository mediaStates;
    private final QuotaService quota;
    private final OutboxService outbox;

    public PostService(PostRepository posts, MediaStateRepository mediaStates, QuotaService quota,
                       OutboxService outbox) {
        this.posts = posts;
        this.mediaStates = mediaStates;
        this.quota = quota;
        this.outbox = outbox;
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
        Optional<MediaState> state = mediaStates.findById(req.mediaId());
        if (state.isPresent() && !state.get().getOwnerId().equals(me)) {
            throw ApiException.forbidden("Ảnh này không thuộc về bạn");
        }
        quota.consume(me);

        Post p = new Post();
        p.setId(UUID.randomUUID().toString());
        p.setAuthorId(me);
        p.setMediaId(req.mediaId());
        p.setCaption(req.caption() == null || req.caption().isBlank() ? null : req.caption().strip());
        if (state.isPresent()) {
            applyMedia(p, state.get());
        }
        posts.save(p);
        if (p.getStatus() == Post.Status.PUBLISHED) {
            outbox.publish(Topics.POST, EventTypes.POST_PUBLISHED, p.getId(), new Events.PostPublished(p.getId(), me));
        }
        return view(me, p, Map.of(), Set.of());
    }

    // ---------------- sự kiện từ media-service ----------------

    @Transactional
    public void onPhotoReady(Events.PhotoReady e) {
        MediaState state = mediaStates.save(new MediaState(e.mediaId(), e.ownerPublicId(), "READY", e.publicKey(), null));
        posts.findForUpdateByMediaId(e.mediaId()).ifPresent(p -> settle(p, state));
    }

    @Transactional
    public void onPhotoRejected(Events.PhotoRejected e) {
        MediaState state = mediaStates.save(new MediaState(e.mediaId(), e.ownerPublicId(), "REJECTED", null, e.reason()));
        posts.findForUpdateByMediaId(e.mediaId()).ifPresent(p -> settle(p, state));
    }

    /** Chỉ đổi bài đang PENDING_MEDIA; bài đã được gọi phải đang giữ khoá dòng để không phát event hai lần. */
    private void settle(Post p, MediaState state) {
        if (p.getStatus() != Post.Status.PENDING_MEDIA) {
            return;
        }
        applyMedia(p, state);
        if (p.getStatus() == Post.Status.PUBLISHED) {
            outbox.publish(Topics.POST, EventTypes.POST_PUBLISHED, p.getId(),
                    new Events.PostPublished(p.getId(), p.getAuthorId()));
        } else if (!state.ready()) {
            quota.refund(p.getAuthorId(), p.getCreatedAt());
        }
    }

    // ---------------- nội bộ ----------------

    private void applyMedia(Post p, MediaState s) {
        if (!s.getOwnerId().equals(p.getAuthorId())) {
            p.setStatus(Post.Status.REJECTED);
            p.setRejectReason("OWNER_MISMATCH");
        } else if (s.ready()) {
            p.setStatus(Post.Status.PUBLISHED);
            p.setPublicKey(s.getPublicKey());
            p.setPublishedAt(Instant.now());
        } else {
            p.setStatus(Post.Status.REJECTED);
            p.setRejectReason(s.getReason());
        }
    }

    private PostDtos.PostView view(String me, Post p, Map<String, UserDirectory.Profile> profiles, Set<String> liked) {
        UserDirectory.Profile prof = profiles.get(p.getAuthorId());
        PostDtos.Author author = new PostDtos.Author(p.getAuthorId(),
                prof == null ? null : prof.alias(), prof == null ? null : prof.leadAnimal(),
                prof == null ? null : prof.avatar());
        return new PostDtos.PostView(p.getId(), author, p.getCaption(), p.getMediaId(), p.getStatus(),
                p.getLikeCount(), p.getCommentCount(), liked.contains(p.getId()), p.getCreatedAt(), p.getPublishedAt());
    }
}
