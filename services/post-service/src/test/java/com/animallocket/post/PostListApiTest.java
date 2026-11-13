package com.animallocket.post;

import com.animallocket.common.Headers;
import com.animallocket.common.web.ApiException;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Post;
import com.animallocket.post.repo.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostListApiTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";
    static final String CAROL = "cccccccccccccccccccc";

    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    @MockBean UserDirectory users;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        posts.deleteAll();
        when(users.areFriends(BOB, ALICE)).thenReturn(true);
        when(users.areFriends(CAROL, ALICE)).thenReturn(false);
    }

    private String seed(String author, Post.Status status, int ageMinutes) {
        Post p = new Post();
        p.setId(UUID.randomUUID().toString());
        p.setAuthorId(author);
        p.setMediaId(UUID.randomUUID().toString());
        p.setStatus(status);
        Instant t = Instant.now().minusSeconds(60L * ageMinutes);
        p.setCreatedAt(t);
        if (status == Post.Status.PUBLISHED) {
            p.setPublishedAt(t);
        }
        return posts.save(p).getId();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String me) {
        return b.header(Headers.PUBLIC_ID, me).with(jwt());
    }

    @Test
    void mineListsOwnPostsNewestFirstWithoutDeleted() throws Exception {
        String old = seed(ALICE, Post.Status.PUBLISHED, 30);
        String pending = seed(ALICE, Post.Status.PENDING_MEDIA, 10);
        seed(ALICE, Post.Status.DELETED, 5);
        seed(BOB, Post.Status.PUBLISHED, 1);
        mvc.perform(as(get("/api/v1/posts/mine"), ALICE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(pending))
                .andExpect(jsonPath("$[1].id").value(old));
    }

    @Test
    void mineClampsLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            seed(ALICE, Post.Status.PUBLISHED, i + 1);
        }
        mvc.perform(as(get("/api/v1/posts/mine?limit=2"), ALICE)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(as(get("/api/v1/posts/mine?limit=0"), ALICE)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void ownPostsPageIncludesPendingAndRejected() throws Exception {
        seed(ALICE, Post.Status.PENDING_MEDIA, 3);
        seed(ALICE, Post.Status.REJECTED, 2);
        seed(ALICE, Post.Status.DELETED, 1);
        mvc.perform(as(get("/api/v1/posts/by/" + ALICE), ALICE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void friendSeesOnlyPublishedPosts() throws Exception {
        String published = seed(ALICE, Post.Status.PUBLISHED, 5);
        seed(ALICE, Post.Status.PENDING_MEDIA, 4);
        seed(ALICE, Post.Status.REJECTED, 3);
        mvc.perform(as(get("/api/v1/posts/by/" + ALICE), BOB)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(published))
                .andExpect(jsonPath("$.degraded").value(false));
    }

    @Test
    void notAFriendGetsNotFound() throws Exception {
        seed(ALICE, Post.Status.PUBLISHED, 5);
        mvc.perform(as(get("/api/v1/posts/by/" + ALICE), CAROL)).andExpect(status().isNotFound());
    }

    @Test
    void unverifiableFriendshipGetsNotFound() throws Exception {
        seed(ALICE, Post.Status.PUBLISHED, 5);
        when(users.areFriends(BOB, ALICE)).thenThrow(ApiException.conflict("UPSTREAM", "down"));
        mvc.perform(as(get("/api/v1/posts/by/" + ALICE), BOB)).andExpect(status().isNotFound());
    }

    @Test
    void cursorPagesThroughAFriendsPosts() throws Exception {
        String newest = seed(ALICE, Post.Status.PUBLISHED, 1);
        String middle = seed(ALICE, Post.Status.PUBLISHED, 2);
        String oldest = seed(ALICE, Post.Status.PUBLISHED, 3);
        String first = mvc.perform(as(get("/api/v1/posts/by/" + ALICE + "?limit=2"), BOB))
                .andExpect(jsonPath("$.items[0].id").value(newest))
                .andExpect(jsonPath("$.items[1].id").value(middle))
                .andReturn().getResponse().getContentAsString();
        String cursor = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).get("nextBefore").asText();
        mvc.perform(as(get("/api/v1/posts/by/" + ALICE + "?limit=2&before=" + cursor), BOB))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(oldest))
                .andExpect(jsonPath("$.nextBefore").doesNotExist());
    }
}
