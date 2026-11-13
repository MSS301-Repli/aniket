package com.animallocket.post.service;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.Events;
import com.animallocket.common.web.ApiException;
import com.animallocket.outbox.OutboxService;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Comment;
import com.animallocket.post.domain.MediaState;
import com.animallocket.post.domain.Post;
import com.animallocket.post.dto.PostDtos;
import com.animallocket.post.messaging.PostDeleted;
import com.animallocket.post.repo.CommentRepository;
import com.animallocket.post.repo.MediaStateRepository;
import com.animallocket.post.repo.PostLikeRepository;
import com.animallocket.post.repo.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PostService {
    static final int MAX_PAGE = 50;

    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final CommentRepository comments;
    private final MediaStateRepository mediaStates;
    private final UserDirectory users;
    private final QuotaService quota;
    private final OutboxService outbox;

    public PostService(PostRepository posts, PostLikeRepository likes, CommentRepository comments,
                       MediaStateRepository mediaStates, UserDirectory users, QuotaService quota,
                       OutboxService outbox) {
        this.posts = posts;
        this.likes = likes;
        this.comments = comments;
        this.mediaStates = mediaStates;
        this.users = users;
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

    @Transactional
    public void delete(String me, String postId) {
        Post p = posts.findById(postId).filter(x -> x.getStatus() != Post.Status.DELETED)
                .orElseThrow(() -> ApiException.notFound("Bài viết"));
        if (!p.getAuthorId().equals(me)) {
            throw ApiException.notFound("Bài viết");
        }
        p.setStatus(Post.Status.DELETED);
        p.setPublicKey(null);
        likes.deleteAllOfPost(postId);
        comments.deleteAllOfPost(postId);
        outbox.publish(Topics.POST, PostDeleted.TYPE, postId, new PostDeleted(postId, me));
    }

    // ---------------- đọc ----------------

    @Transactional(readOnly = true)
    public PostDtos.PostView get(String me, String postId) {
        Post p = visible(me, postId);
        return view(me, p, Map.of(), likes.existsByPostIdAndUserId(postId, me) ? Set.of(postId) : Set.of());
    }

    /** Feed: bài đã PUBLISHED của bạn bè và của chính mình, mới nhất trước, phân trang theo thời điểm đăng. */
    @Transactional(readOnly = true)
    public PostDtos.FeedPage feed(String me, Instant before, int limit) {
        Set<String> authors;
        try {
            authors = new HashSet<>(users.friendIds(me));
        } catch (ApiException e) {
            return new PostDtos.FeedPage(List.of(), null, true); // fail soft: ẩn bài khi không biết ai là bạn
        }
        authors.add(me);
        int size = Math.max(1, Math.min(limit, MAX_PAGE));
        List<Post> page = posts.findByAuthorIdInAndStatusAndPublishedAtLessThanOrderByPublishedAtDesc(
                authors, Post.Status.PUBLISHED, before == null ? Instant.now().plusSeconds(60) : before,
                PageRequest.of(0, size));
        List<PostDtos.PostView> items = views(me, page);
        Instant next = page.size() == size ? page.get(page.size() - 1).getPublishedAt() : null;
        return new PostDtos.FeedPage(items, next, false);
    }

    /**
     * Bài của một người, mới nhất trước. Chính mình: mọi bài trừ DELETED (phân trang theo createdAt).
     * Bạn bè: chỉ PUBLISHED (phân trang theo publishedAt). Người khác, hoặc không xác minh được quan hệ:
     * 404 để không lộ người đó có bài, cùng luật với {@link #get}.
     */
    @Transactional(readOnly = true)
    public PostDtos.FeedPage userPosts(String me, String author, Instant before, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_PAGE));
        Instant cursor = before == null ? Instant.now().plusSeconds(60) : before;
        if (author.equals(me)) {
            List<Post> page = posts.findByAuthorIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDesc(
                    me, Post.Status.DELETED, cursor, PageRequest.of(0, size));
            Instant next = page.size() == size ? page.get(page.size() - 1).getCreatedAt() : null;
            return new PostDtos.FeedPage(views(me, page), next, false);
        }
        boolean friend;
        try {
            friend = users.areFriends(me, author);
        } catch (ApiException e) {
            friend = false; // không xác minh được thì ẩn
        }
        if (!friend) {
            throw ApiException.notFound("Người dùng");
        }
        List<Post> page = posts.findByAuthorIdInAndStatusAndPublishedAtLessThanOrderByPublishedAtDesc(
                Set.of(author), Post.Status.PUBLISHED, cursor, PageRequest.of(0, size));
        Instant next = page.size() == size ? page.get(page.size() - 1).getPublishedAt() : null;
        return new PostDtos.FeedPage(views(me, page), next, false);
    }

    @Transactional(readOnly = true)
    public List<PostDtos.PostView> mine(String me, int limit) {
        List<Post> page = posts.findByAuthorIdAndStatusNotOrderByCreatedAtDesc(me, Post.Status.DELETED,
                PageRequest.of(0, Math.max(1, Math.min(limit, MAX_PAGE))));
        return views(me, page);
    }

    // ---------------- like / comment ----------------

    @Transactional
    public void like(String me, String postId) {
        Post p = visible(me, postId);
        if (p.getStatus() != Post.Status.PUBLISHED) {
            throw ApiException.notFound("Bài viết");
        }
        // Dựa vào ràng buộc unique (post_id, user_id): chèn bỏ qua khi trùng nên hai request đồng thời
        // cũng chỉ ghi một dòng, chỉ request thắng mới tăng bộ đếm và phát event.
        if (likes.insertIfAbsent(postId, me, Instant.now()) == 0) {
            return;
        }
        posts.incLikes(postId);
        if (!p.getAuthorId().equals(me)) {
            outbox.publish(Topics.POST, EventTypes.POST_LIKED, postId,
                    new Events.PostLiked(postId, p.getAuthorId(), me));
        }
    }

    @Transactional
    public void unlike(String me, String postId) {
        visible(me, postId);
        if (likes.deleteByPostIdAndUserId(postId, me) > 0) {
            posts.decLikes(postId);
        }
    }

    @Transactional
    public PostDtos.CommentView comment(String me, String postId, String text) {
        String clean = CommentText.normalize(text);
        if (clean.isEmpty()) {
            throw ApiException.badRequest("COMMENT_EMPTY", "Bình luận không được để trống");
        }
        if (clean.codePointCount(0, clean.length()) > CommentText.MAX_LENGTH) {
            throw ApiException.badRequest("COMMENT_TOO_LONG", "Bình luận tối đa " + CommentText.MAX_LENGTH + " ký tự");
        }
        Post p = visible(me, postId);
        if (p.getStatus() != Post.Status.PUBLISHED) {
            throw ApiException.notFound("Bài viết");
        }
        Comment c = comments.save(new Comment(postId, me, clean));
        posts.incComments(postId);
        if (!p.getAuthorId().equals(me)) {
            outbox.publish(Topics.POST, EventTypes.POST_COMMENTED, postId,
                    new Events.PostCommented(postId, p.getAuthorId(), me));
        }
        return commentView(c, users.profiles(Set.of(me)));
    }

    @Transactional(readOnly = true)
    public PostDtos.CommentPage comments(String me, String postId, long afterId, int limit) {
        visible(me, postId);
        int size = Math.max(1, Math.min(limit, MAX_PAGE));
        List<Comment> page = comments.findByPostIdAndIdGreaterThanOrderByIdAsc(postId, afterId, PageRequest.of(0, size));
        Map<String, UserDirectory.Profile> profiles = users.profiles(
                page.stream().map(Comment::getAuthorId).collect(Collectors.toSet()));
        List<PostDtos.CommentView> items = page.stream().map(c -> commentView(c, profiles)).toList();
        return new PostDtos.CommentPage(items, page.size() == size ? page.get(page.size() - 1).getId() : null);
    }

    @Transactional
    public void deleteComment(String me, String postId, long commentId) {
        Post p = visible(me, postId);
        Comment c = comments.findById(commentId).filter(x -> x.getPostId().equals(postId))
                .orElseThrow(() -> ApiException.notFound("Bình luận"));
        if (!c.getAuthorId().equals(me) && !p.getAuthorId().equals(me)) {
            throw ApiException.forbidden("Không có quyền xoá bình luận này");
        }
        comments.delete(c);
        posts.decComments(postId);
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

    /**
     * Gỡ một bài kẹt ở PENDING_MEDIA dù đã có kết quả ảnh. Xảy ra khi {@link #create} và {@link #onPhotoReady}
     * chạy song song: mỗi transaction không thấy dòng của bên kia nên không bên nào chuyển trạng thái.
     * Gọi từ {@link PendingPostReconciler}, mỗi bài một transaction.
     */
    @Transactional
    public void settlePending(String postId) {
        posts.findForUpdateById(postId).ifPresent(p ->
                mediaStates.findById(p.getMediaId()).ifPresent(state -> settle(p, state)));
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

    /** Xoá toàn bộ dữ liệu của người dùng đã xoá tài khoản (bài, like, bình luận của họ). */
    @Transactional
    public void onUserDeleted(String publicId) {
        for (Post p : posts.findByAuthorId(publicId)) {
            likes.deleteAllOfPost(p.getId());
            comments.deleteAllOfPost(p.getId());
            posts.delete(p);
        }
        for (Comment c : new ArrayList<>(comments.findByAuthorId(publicId))) {
            posts.decComments(c.getPostId());
            comments.delete(c);
        }
        likes.deleteAllOfUser(publicId);
        mediaStates.deleteByOwnerId(publicId);
        users.invalidateFriends(publicId);
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

    /** Bài chỉ hiện với tác giả hoặc bạn bè; người khác thấy "không tồn tại" (không lộ sự tồn tại). */
    private Post visible(String me, String postId) {
        Post p = posts.findById(postId).filter(x -> x.getStatus() != Post.Status.DELETED)
                .orElseThrow(() -> ApiException.notFound("Bài viết"));
        if (!p.getAuthorId().equals(me)) {
            boolean friend;
            try {
                friend = users.areFriends(me, p.getAuthorId());
            } catch (ApiException e) {
                throw ApiException.notFound("Bài viết"); // không xác minh được thì ẩn
            }
            if (!friend || p.getStatus() != Post.Status.PUBLISHED) {
                throw ApiException.notFound("Bài viết");
            }
        }
        return p;
    }

    private List<PostDtos.PostView> views(String me, List<Post> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        Map<String, UserDirectory.Profile> profiles = users.profiles(
                page.stream().map(Post::getAuthorId).collect(Collectors.toSet()));
        Set<String> liked = new HashSet<>(likes.likedAmong(me, page.stream().map(Post::getId).toList()));
        return page.stream().map(p -> view(me, p, profiles, liked)).toList();
    }

    private PostDtos.PostView view(String me, Post p, Map<String, UserDirectory.Profile> profiles, Set<String> liked) {
        UserDirectory.Profile prof = profiles.get(p.getAuthorId());
        PostDtos.Author author = new PostDtos.Author(p.getAuthorId(),
                prof == null ? null : prof.alias(), prof == null ? null : prof.leadAnimal(),
                prof == null ? null : prof.avatar());
        return new PostDtos.PostView(p.getId(), author, p.getCaption(), p.getMediaId(), p.getStatus(),
                p.getLikeCount(), p.getCommentCount(), liked.contains(p.getId()), p.getCreatedAt(), p.getPublishedAt());
    }

    private PostDtos.CommentView commentView(Comment c, Map<String, UserDirectory.Profile> profiles) {
        UserDirectory.Profile prof = profiles.get(c.getAuthorId());
        return new PostDtos.CommentView(c.getId(), new PostDtos.Author(c.getAuthorId(),
                prof == null ? null : prof.alias(), prof == null ? null : prof.leadAnimal(),
                prof == null ? null : prof.avatar()), c.getText(), c.getCreatedAt());
    }
}
