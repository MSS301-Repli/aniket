package com.animallocket.post;

import com.animallocket.common.EventTypes;
import com.animallocket.common.event.Events;
import com.animallocket.outbox.OutboxRepository;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Post;
import com.animallocket.post.repo.CommentRepository;
import com.animallocket.post.repo.PostLikeRepository;
import com.animallocket.post.repo.PostRepository;
import com.animallocket.post.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest
class PostLikeConcurrencyTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";

    @Autowired PostService service;
    @Autowired PostRepository posts;
    @Autowired PostLikeRepository likes;
    @Autowired CommentRepository comments;
    @Autowired OutboxRepository outbox;
    @MockBean UserDirectory users;
    @MockBean JwtDecoder jwtDecoder;

    String postId;

    @BeforeEach
    void setUp() {
        likes.deleteAll();
        comments.deleteAll();
        posts.deleteAll();
        outbox.deleteAll();
        when(users.areFriends(BOB, ALICE)).thenReturn(true);
        Post p = new Post();
        p.setId(UUID.randomUUID().toString());
        p.setAuthorId(ALICE);
        p.setMediaId(UUID.randomUUID().toString());
        p.setStatus(Post.Status.PUBLISHED);
        p.setPublishedAt(Instant.now());
        postId = posts.save(p).getId();
    }

    private long likedEvents() {
        return outbox.findAll().stream().filter(e -> e.getType().equals(EventTypes.POST_LIKED)).count();
    }

    @Test
    void likingTwiceCountsOnceAndEmitsOneEvent() {
        service.like(BOB, postId);
        service.like(BOB, postId);
        assertEquals(1, likes.count());
        assertEquals(1, posts.findById(postId).orElseThrow().getLikeCount());
        assertEquals(1, likedEvents());
    }

    @Test
    void concurrentLikesFromSameUserCountOnceAndEmitOneEvent() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> done = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            done.add(pool.submit(() -> {
                go.await();
                service.like(BOB, postId);
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : done) {
            f.get(); // không request nào được lỗi
        }
        pool.shutdown();

        assertEquals(1, likes.count());
        assertEquals(1, posts.findById(postId).orElseThrow().getLikeCount());
        assertEquals(1, likedEvents());
    }

    @Test
    void unlikeThenLikeAgainWorks() {
        service.like(BOB, postId);
        service.unlike(BOB, postId);
        assertEquals(0, posts.findById(postId).orElseThrow().getLikeCount());
        service.like(BOB, postId);
        assertEquals(1, posts.findById(postId).orElseThrow().getLikeCount());
        assertEquals(2, likedEvents());
    }
}
