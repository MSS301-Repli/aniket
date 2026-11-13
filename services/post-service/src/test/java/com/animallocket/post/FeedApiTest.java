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
class FeedApiTest {
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
        when(users.friendIds(ALICE)).thenReturn(Set.of(BOB));
        when(users.friendIds(BOB)).thenReturn(Set.of(ALICE));
        when(users.friendIds(CAROL)).thenReturn(Set.of());
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

    private MockHttpServletRequestBuilder feed(String me, String query) {
        return get("/api/v1/feed" + query).header(Headers.PUBLIC_ID, me).with(jwt());
    }

    @Test
    void showsPublishedPostsOfFriendsAndSelfNewestFirst() throws Exception {
        String mine = seed(ALICE, Post.Status.PUBLISHED, 1);
        String friends = seed(BOB, Post.Status.PUBLISHED, 2);
        seed(BOB, Post.Status.PENDING_MEDIA, 3);
        seed(BOB, Post.Status.DELETED, 4);
        seed(CAROL, Post.Status.PUBLISHED, 5); // người lạ
        mvc.perform(feed(ALICE, "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(mine))
                .andExpect(jsonPath("$.items[1].id").value(friends))
                .andExpect(jsonPath("$.nextBefore").doesNotExist())
                .andExpect(jsonPath("$.degraded").value(false));
    }

    @Test
    void cursorPagingWalksThroughAllPostsWithoutRepeats() throws Exception {
        String[] ids = new String[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = seed(i % 2 == 0 ? ALICE : BOB, Post.Status.PUBLISHED, i + 1);
        }
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String query = "?limit=2";
        int seen = 0;
        for (int page = 0; page < 3; page++) {
            var node = mapper.readTree(mvc.perform(feed(ALICE, query)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            for (var item : node.get("items")) {
                assertEquals(ids[seen++], item.get("id").asText());
            }
            if (page < 2) {
                query = "?limit=2&before=" + node.get("nextBefore").asText();
            } else {
                assertEquals(true, node.get("nextBefore").isNull());
            }
        }
        assertEquals(5, seen);
    }

    @Test
    void limitIsClampedToTheAllowedRange() throws Exception {
        for (int i = 0; i < 55; i++) {
            seed(ALICE, Post.Status.PUBLISHED, i + 1);
        }
        mvc.perform(feed(ALICE, "?limit=1000")).andExpect(jsonPath("$.items.length()").value(50));
        mvc.perform(feed(ALICE, "?limit=0")).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(feed(ALICE, "?limit=-5")).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(feed(ALICE, "")).andExpect(jsonPath("$.items.length()").value(20));
    }

    @Test
    void failsSoftWhenFriendListIsUnavailable() throws Exception {
        seed(ALICE, Post.Status.PUBLISHED, 1);
        when(users.friendIds(ALICE)).thenThrow(ApiException.conflict("UPSTREAM", "down"));
        mvc.perform(feed(ALICE, "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.degraded").value(true));
    }
}
