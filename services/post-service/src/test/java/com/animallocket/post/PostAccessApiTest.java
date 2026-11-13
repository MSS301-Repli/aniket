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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostAccessApiTest {
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
        // ALICE và BOB là bạn; CAROL là người lạ.
        when(users.areFriends(BOB, ALICE)).thenReturn(true);
        when(users.areFriends(CAROL, ALICE)).thenReturn(false);
    }

    private String seed(Post.Status status) {
        Post p = new Post();
        p.setId(UUID.randomUUID().toString());
        p.setAuthorId(ALICE);
        p.setMediaId(UUID.randomUUID().toString());
        p.setStatus(status);
        if (status == Post.Status.PUBLISHED) {
            p.setPublicKey("pub/" + p.getMediaId() + ".jpg");
            p.setPublishedAt(Instant.now());
        }
        return posts.save(p).getId();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String me) {
        return b.header(Headers.PUBLIC_ID, me).with(jwt());
    }

    @Test
    void authorSeesOwnPostInAnyStatusExceptDeleted() throws Exception {
        String pending = seed(Post.Status.PENDING_MEDIA);
        mvc.perform(as(get("/api/v1/posts/" + pending), ALICE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_MEDIA"));
        String deleted = seed(Post.Status.DELETED);
        mvc.perform(as(get("/api/v1/posts/" + deleted), ALICE)).andExpect(status().isNotFound());
    }

    @Test
    void friendSeesPublishedPostOnly() throws Exception {
        String published = seed(Post.Status.PUBLISHED);
        String pending = seed(Post.Status.PENDING_MEDIA);
        mvc.perform(as(get("/api/v1/posts/" + published), BOB)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(published))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(as(get("/api/v1/posts/" + pending), BOB)).andExpect(status().isNotFound());
    }

    @Test
    void responseNeverExposesTheStorageKey() throws Exception {
        String id = seed(Post.Status.PUBLISHED);
        String raw = mvc.perform(as(get("/api/v1/posts/" + id), BOB)).andReturn().getResponse().getContentAsString();
        assertEquals(false, raw.contains("pub/"), raw);
    }

    @Test
    void strangerAndUnknownPostLookTheSame() throws Exception {
        String id = seed(Post.Status.PUBLISHED);
        mvc.perform(as(get("/api/v1/posts/" + id), CAROL)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/v1/posts/" + UUID.randomUUID()), CAROL)).andExpect(status().isNotFound());
    }

    @Test
    void unverifiableFriendshipHidesThePost() throws Exception {
        String id = seed(Post.Status.PUBLISHED);
        when(users.areFriends(anyString(), anyString())).thenThrow(ApiException.conflict("UPSTREAM", "down"));
        mvc.perform(as(get("/api/v1/posts/" + id), BOB)).andExpect(status().isNotFound());
    }

    @Test
    void ownerCanDeleteAndPostDisappears() throws Exception {
        String id = seed(Post.Status.PUBLISHED);
        mvc.perform(as(delete("/api/v1/posts/" + id), ALICE)).andExpect(status().isNoContent());
        Post p = posts.findById(id).orElseThrow();
        assertEquals(Post.Status.DELETED, p.getStatus());
        assertEquals(null, p.getPublicKey());
        mvc.perform(as(get("/api/v1/posts/" + id), ALICE)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/v1/posts/" + id), BOB)).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/v1/posts/" + id), ALICE)).andExpect(status().isNotFound());
    }

    @Test
    void nonOwnerCannotDelete() throws Exception {
        String id = seed(Post.Status.PUBLISHED);
        mvc.perform(as(delete("/api/v1/posts/" + id), BOB)).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/v1/posts/" + id), CAROL)).andExpect(status().isNotFound());
        assertEquals(Post.Status.PUBLISHED, posts.findById(id).orElseThrow().getStatus());
    }
}
