package com.animallocket.post;

import com.animallocket.common.EventTypes;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.outbox.OutboxRepository;
import com.animallocket.outbox.ProcessedEventRepository;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Comment;
import com.animallocket.post.domain.Post;
import com.animallocket.post.domain.PostLike;
import com.animallocket.post.messaging.PostEventsListener;
import com.animallocket.post.repo.CommentRepository;
import com.animallocket.post.repo.PostLikeRepository;
import com.animallocket.post.repo.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserEventsListenerTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";

    @Autowired PostEventsListener listener;
    @Autowired PostRepository posts;
    @Autowired PostLikeRepository likes;
    @Autowired CommentRepository comments;
    @Autowired OutboxRepository outbox;
    @Autowired ProcessedEventRepository processed;
    @MockBean UserDirectory users;
    @MockBean JwtDecoder jwtDecoder;

    String postId;

    @BeforeEach
    void setUp() {
        likes.deleteAll();
        comments.deleteAll();
        posts.deleteAll();
        outbox.deleteAll();
        processed.deleteAll();
        Post p = new Post();
        p.setId(UUID.randomUUID().toString());
        p.setAuthorId(ALICE);
        p.setMediaId(UUID.randomUUID().toString());
        p.setStatus(Post.Status.PUBLISHED);
        p.setPublishedAt(Instant.now());
        postId = posts.save(p).getId();
    }

    private void deliver(EventEnvelope e) {
        listener.onMessage(EventJson.write(e));
    }

    @Test
    void userDeletedWipesPostsLikesAndCommentsOfThatUser() {
        likes.save(new PostLike(postId, BOB));
        comments.save(new Comment(postId, BOB, "hi"));
        deliver(EventEnvelope.of(EventTypes.USER_DELETED, "user-service", ALICE, new Events.UserDeleted(ALICE)));
        assertTrue(posts.findById(postId).isEmpty());
        assertEquals(0, likes.count());
        assertEquals(0, comments.count());
    }

    @Test
    void userDeletedRemovesTheirLikesAndCommentsFromOthersPosts() {
        likes.save(new PostLike(postId, BOB));
        comments.save(new Comment(postId, BOB, "hi"));
        Post before = posts.findById(postId).orElseThrow();
        before.setLikeCount(1);
        before.setCommentCount(1);
        posts.save(before);
        deliver(EventEnvelope.of(EventTypes.USER_DELETED, "user-service", BOB, new Events.UserDeleted(BOB)));
        Post p = posts.findById(postId).orElseThrow();
        assertEquals(0, comments.count());
        assertEquals(0, likes.count());
        assertEquals(0, p.getCommentCount());
    }

    @Test
    void duplicateDeliveryIsHandledOnce() {
        EventEnvelope e = EventEnvelope.of(EventTypes.USER_BLOCKED, "user-service", ALICE,
                new Events.UserBlocked(ALICE, BOB));
        deliver(e);
        deliver(e);
        verify(users, times(1)).invalidateFriends(ALICE, BOB);
    }

    @Test
    void friendEventsInvalidateTheFriendCacheOfBothSides() {
        deliver(EventEnvelope.of(EventTypes.FRIEND_ACCEPTED, "user-service", ALICE, new Events.FriendAccepted(ALICE, BOB)));
        deliver(EventEnvelope.of(EventTypes.FRIEND_REMOVED, "user-service", ALICE, new Events.FriendRemoved(ALICE, BOB)));
        verify(users, times(2)).invalidateFriends(ALICE, BOB);
    }
}
