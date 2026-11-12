package com.animallocket.post;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Headers;
import com.animallocket.outbox.OutboxRepository;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.MediaState;
import com.animallocket.post.domain.Post;
import com.animallocket.post.repo.MediaStateRepository;
import com.animallocket.post.repo.PostRepository;
import com.animallocket.post.service.PendingPostReconciler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PendingPostReconcilerTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";

    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    @Autowired MediaStateRepository mediaStates;
    @Autowired OutboxRepository outbox;
    @Autowired PendingPostReconciler reconciler;
    @MockBean UserDirectory users;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        posts.deleteAll();
        mediaStates.deleteAll();
        outbox.deleteAll();
    }

    private String create(String media) throws Exception {
        mvc.perform(post("/api/v1/posts").header(Headers.PUBLIC_ID, ALICE).with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mediaId\":\"" + media + "\"}"))
                .andExpect(status().isCreated());
        return posts.findByMediaId(media).orElseThrow().getId();
    }

    private long published() {
        return outbox.findAll().stream().filter(e -> e.getType().equals(EventTypes.POST_PUBLISHED)).count();
    }

    @Test
    void publishesPostWhoseReadyResultWasStoredButNotApplied() throws Exception {
        String media = UUID.randomUUID().toString();
        String id = create(media);
        // Mô phỏng cuộc đua: kết quả ảnh đã lưu nhưng bài vẫn PENDING_MEDIA.
        mediaStates.save(new MediaState(media, ALICE, "READY", "pub/" + media + ".jpg", null));

        reconciler.reconcile();
        reconciler.reconcile(); // chạy lại không phát event lần hai

        Post p = posts.findById(id).orElseThrow();
        assertEquals(Post.Status.PUBLISHED, p.getStatus());
        assertEquals("pub/" + media + ".jpg", p.getPublicKey());
        assertEquals(1, published());
    }

    @Test
    void rejectsPostWhoseRejectedResultWasStoredButNotApplied() throws Exception {
        String media = UUID.randomUUID().toString();
        String id = create(media);
        mediaStates.save(new MediaState(media, ALICE, "REJECTED", null, "PROCESSING_TIMEOUT"));

        reconciler.reconcile();

        Post p = posts.findById(id).orElseThrow();
        assertEquals(Post.Status.REJECTED, p.getStatus());
        assertEquals("PROCESSING_TIMEOUT", p.getRejectReason());
        assertEquals(0, published());
    }

    @Test
    void leavesPostPendingWhilePhotoResultIsUnknown() throws Exception {
        String id = create(UUID.randomUUID().toString());
        reconciler.reconcile();
        assertEquals(Post.Status.PENDING_MEDIA, posts.findById(id).orElseThrow().getStatus());
        assertEquals(0, published());
    }
}
