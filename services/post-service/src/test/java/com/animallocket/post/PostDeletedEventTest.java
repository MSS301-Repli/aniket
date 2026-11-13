package com.animallocket.post;

import com.animallocket.common.Headers;
import com.animallocket.outbox.OutboxEvent;
import com.animallocket.outbox.OutboxRepository;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Post;
import com.animallocket.post.messaging.PostDeleted;
import com.animallocket.post.repo.CommentRepository;
import com.animallocket.post.repo.PostLikeRepository;
import com.animallocket.post.repo.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostDeletedEventTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";

    @Autowired MockMvc mvc;
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

    private List<OutboxEvent> deletedEvents() {
        return outbox.findAll().stream().filter(e -> e.getType().equals(PostDeleted.TYPE)).toList();
    }

    @Test
    void authorDeletingPostEmitsOnePostDeletedEvent() throws Exception {
        mvc.perform(delete("/api/v1/posts/" + postId).header(Headers.PUBLIC_ID, ALICE).with(jwt()))
                .andExpect(status().isNoContent());
        assertEquals(Post.Status.DELETED, posts.findById(postId).orElseThrow().getStatus());
        List<OutboxEvent> events = deletedEvents();
        assertEquals(1, events.size());
        assertEquals("post.events", events.get(0).getTopic());
        assertEquals(postId, events.get(0).getKey());
        assertTrue(events.get(0).getPayload().contains(ALICE), events.get(0).getPayload());
    }

    @Test
    void failedDeleteDoesNotEmitEvent() throws Exception {
        mvc.perform(delete("/api/v1/posts/" + postId).header(Headers.PUBLIC_ID, BOB).with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/posts/" + postId).header(Headers.PUBLIC_ID, ALICE).with(jwt()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/posts/" + postId).header(Headers.PUBLIC_ID, ALICE).with(jwt()))
                .andExpect(status().isNotFound());
        assertEquals(1, deletedEvents().size());
    }
}
