package com.animallocket.post;

import com.animallocket.common.Headers;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Post;
import com.animallocket.post.repo.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "animal.limits.photos-per-day.free=2")
class PostCreateApiTest {
    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    @MockBean UserDirectory users;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        posts.deleteAll();
    }

    private static String user() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private MockHttpServletRequestBuilder create(String me, String mediaId, String caption) {
        String body = caption == null ? "{\"mediaId\":\"" + mediaId + "\"}"
                : "{\"mediaId\":\"" + mediaId + "\",\"caption\":\"" + caption + "\"}";
        return post("/api/v1/posts").header(Headers.PUBLIC_ID, me).with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void createsPendingPostWithTrimmedCaption() throws Exception {
        String me = user();
        String media = UUID.randomUUID().toString();
        mvc.perform(create(me, media, "  xin chào  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_MEDIA"))
                .andExpect(jsonPath("$.caption").value("xin chào"))
                .andExpect(jsonPath("$.author.publicId").value(me))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());
        Post saved = posts.findByMediaId(media).orElseThrow();
        assertEquals(me, saved.getAuthorId());
        assertEquals(Post.Status.PENDING_MEDIA, saved.getStatus());
    }

    @Test
    void blankCaptionIsStoredAsNull() throws Exception {
        String media = UUID.randomUUID().toString();
        mvc.perform(create(user(), media, "   ")).andExpect(status().isCreated());
        assertNull(posts.findByMediaId(media).orElseThrow().getCaption());
    }

    @Test
    void rejectsMissingMediaId() throws Exception {
        mvc.perform(create(user(), "", null)).andExpect(status().isBadRequest());
        assertEquals(0, posts.count());
    }

    @Test
    void repeatingTheSameMediaReturnsTheSamePostWithoutUsingQuota() throws Exception {
        String me = user();
        String media = UUID.randomUUID().toString();
        mvc.perform(create(me, media, null)).andExpect(status().isCreated());
        mvc.perform(create(me, media, null)).andExpect(status().isCreated());
        assertEquals(1, posts.count());
        // Hai lượt miễn phí: lần lặp lại không tốn lượt nào nên vẫn đăng thêm được một ảnh khác.
        mvc.perform(create(me, UUID.randomUUID().toString(), null)).andExpect(status().isCreated());
    }

    @Test
    void cannotCreateFromMediaOfAnotherAuthor() throws Exception {
        String media = UUID.randomUUID().toString();
        mvc.perform(create(user(), media, null)).andExpect(status().isCreated());
        mvc.perform(create(user(), media, null)).andExpect(status().isForbidden());
        assertEquals(1, posts.count());
    }

    @Test
    void dailyQuotaIsEnforced() throws Exception {
        String me = user();
        mvc.perform(create(me, UUID.randomUUID().toString(), null)).andExpect(status().isCreated());
        mvc.perform(create(me, UUID.randomUUID().toString(), null)).andExpect(status().isCreated());
        mvc.perform(create(me, UUID.randomUUID().toString(), null))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PHOTO_LIMIT"));
        assertEquals(2, posts.count());
        // Người khác không bị ảnh hưởng.
        mvc.perform(create(user(), UUID.randomUUID().toString(), null)).andExpect(status().isCreated());
    }
}
