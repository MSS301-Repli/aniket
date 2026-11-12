package com.animallocket.post;

import com.animallocket.common.Headers;
import com.animallocket.outbox.OutboxRepository;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Post;
import com.animallocket.post.repo.CommentRepository;
import com.animallocket.post.repo.PostLikeRepository;
import com.animallocket.post.repo.PostRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommentApiTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
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

    private MockHttpServletRequestBuilder comment(String text) throws Exception {
        return post("/api/v1/posts/" + postId + "/comments").header(Headers.PUBLIC_ID, BOB).with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text", text)));
    }

    @Test
    void storesNormalisedText() throws Exception {
        mvc.perform(comment("  xin\u0000 chào\tbạn\n\n\n\nhẹn gặp \u200B "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("xin chào bạn\n\nhẹn gặp"));
        assertEquals("xin chào bạn\n\nhẹn gặp", comments.findAll().get(0).getText());
    }

    @Test
    void rejectsTextThatIsEmptyAfterNormalisation() throws Exception {
        mvc.perform(comment("\u200B\u0007\u0000")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_EMPTY"));
        mvc.perform(comment("   \n\t  ")).andExpect(status().isBadRequest());
        assertEquals(0, comments.count());
        assertEquals(0, posts.findById(postId).orElseThrow().getCommentCount());
    }

    @Test
    void lengthLimitAppliesAfterNormalisation() throws Exception {
        // Thô dài hơn 300 nhưng sau khi bỏ ký tự điều khiển đúng 300 thì được nhận.
        mvc.perform(comment("a".repeat(300) + "\u0000".repeat(50))).andExpect(status().isCreated());
        mvc.perform(comment("a".repeat(301))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_TOO_LONG"));
        assertEquals(1, comments.count());
    }
}
