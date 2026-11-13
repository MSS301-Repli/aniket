package com.animallocket.post;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Headers;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.outbox.OutboxRepository;
import com.animallocket.outbox.ProcessedEventRepository;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Post;
import com.animallocket.post.messaging.PostEventsListener;
import com.animallocket.post.repo.MediaStateRepository;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "animal.limits.photos-per-day.free=1")
class PostMediaEventsTest {
    // Mỗi test dùng người dùng riêng vì bộ đếm quota sống suốt vòng đời context.
    final String ALICE = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    final String BOB = UUID.randomUUID().toString().replace("-", "").substring(0, 20);

    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    @Autowired MediaStateRepository mediaStates;
    @Autowired OutboxRepository outbox;
    @Autowired ProcessedEventRepository processed;
    @Autowired PostEventsListener listener;
    @MockBean UserDirectory users;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        posts.deleteAll();
        mediaStates.deleteAll();
        outbox.deleteAll();
        processed.deleteAll();
    }

    private String create(String me, String media) throws Exception {
        mvc.perform(post("/api/v1/posts").header(Headers.PUBLIC_ID, me).with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mediaId\":\"" + media + "\"}"))
                .andExpect(status().isCreated());
        return posts.findByMediaId(media).orElseThrow().getId();
    }

    private void ready(String media, String owner) {
        listener.onMessage(EventJson.write(EventEnvelope.of(EventTypes.PHOTO_READY, "media-service", media,
                new Events.PhotoReady(media, owner, "pub/" + media + ".jpg", "STICKER", 1, "POST", null))));
    }

    private void rejected(String media, String owner, String reason) {
        listener.onMessage(EventJson.write(EventEnvelope.of(EventTypes.PHOTO_REJECTED, "media-service", media,
                new Events.PhotoRejected(media, owner, reason, "POST", null))));
    }

    private long published() {
        return outbox.findAll().stream().filter(e -> e.getType().equals(EventTypes.POST_PUBLISHED)).count();
    }

    @Test
    void photoReadyPublishesPendingPostAndEmitsEventOnce() throws Exception {
        String media = UUID.randomUUID().toString();
        String id = create(ALICE, media);
        assertEquals(Post.Status.PENDING_MEDIA, posts.findById(id).orElseThrow().getStatus());

        ready(media, ALICE);

        Post p = posts.findById(id).orElseThrow();
        assertEquals(Post.Status.PUBLISHED, p.getStatus());
        assertEquals("pub/" + media + ".jpg", p.getPublicKey());
        assertNotNull(p.getPublishedAt());
        assertEquals(1, published());
    }

    @Test
    void redeliveredEventDoesNotEmitTwice() throws Exception {
        String media = UUID.randomUUID().toString();
        create(ALICE, media);
        EventEnvelope e = EventEnvelope.of(EventTypes.PHOTO_READY, "media-service", media,
                new Events.PhotoReady(media, ALICE, "pub/x.jpg", "STICKER", 1, "POST", null));
        listener.onMessage(EventJson.write(e));
        listener.onMessage(EventJson.write(e));
        assertEquals(1, published());
    }

    @Test
    void photoReadyBeforeCreateMakesPostPublishedImmediately() throws Exception {
        String media = UUID.randomUUID().toString();
        ready(media, ALICE);
        mvc.perform(post("/api/v1/posts").header(Headers.PUBLIC_ID, ALICE).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mediaId\":\"" + media + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertEquals(1, published());
    }

    @Test
    void cannotAttachMediaThatBelongsToSomeoneElse() throws Exception {
        String media = UUID.randomUUID().toString();
        ready(media, BOB);
        mvc.perform(post("/api/v1/posts").header(Headers.PUBLIC_ID, ALICE).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mediaId\":\"" + media + "\"}"))
                .andExpect(status().isForbidden());
        assertEquals(0, posts.count());
    }

    @Test
    void ownerMismatchOnLateReadyEventRejectsPost() throws Exception {
        String media = UUID.randomUUID().toString();
        String id = create(ALICE, media);
        ready(media, BOB);
        Post p = posts.findById(id).orElseThrow();
        assertEquals(Post.Status.REJECTED, p.getStatus());
        assertEquals("OWNER_MISMATCH", p.getRejectReason());
        assertEquals(0, published());
    }

    @Test
    void photoRejectedRejectsPostAndRefundsQuota() throws Exception {
        String media = UUID.randomUUID().toString();
        String id = create(ALICE, media); // giới hạn 1 lượt/ngày trong test này

        rejected(media, ALICE, "PROCESSING_TIMEOUT");

        Post p = posts.findById(id).orElseThrow();
        assertEquals(Post.Status.REJECTED, p.getStatus());
        assertEquals("PROCESSING_TIMEOUT", p.getRejectReason());
        assertEquals(0, published());
        // Lượt đã hoàn lại nên đăng được ảnh mới.
        create(ALICE, UUID.randomUUID().toString());
    }
}
