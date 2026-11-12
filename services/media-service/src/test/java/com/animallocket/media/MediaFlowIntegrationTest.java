package com.animallocket.media;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Headers;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.media.client.UserClient;
import com.animallocket.media.domain.MediaJob;
import com.animallocket.media.messaging.MediaEventsListener;
import com.animallocket.media.repo.MediaJobRepository;
import com.animallocket.media.service.MediaService;
import com.animallocket.media.storage.ObjectStorage;
import com.animallocket.outbox.OutboxEvent;
import com.animallocket.outbox.OutboxRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MediaFlowIntegrationTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRepository outbox;
    @Autowired MediaJobRepository jobs;
    @Autowired MediaService media;
    @Autowired MediaEventsListener listener;
    @MockBean ObjectStorage storage;
    @MockBean UserClient users;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        jobs.deleteAll();
        outbox.deleteAll();
        when(storage.presignUpload(any(), any(), any()))
                .thenReturn(new ObjectStorage.PresignedPut("http://files/raw", Map.of("Content-Type", "image/jpeg")));
        when(storage.presignDownload(any(), any())).thenReturn("http://files/public/signed");
        when(users.user(ALICE)).thenReturn(Optional.of(new UserClient.InternalUser(ALICE, "fox", "ADULT", true)));
    }

    private String requestUpload(String owner) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/media/uploads").header(Headers.PUBLIC_ID, owner).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\":\"POST\",\"contentType\":\"image/jpeg\",\"sizeBytes\":12345}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value("http://files/raw"))
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("mediaId").asText();
    }

    private void deliver(String type, Object payload) {
        listener.onMessage(EventJson.write(EventEnvelope.of(type, "face-worker", "k", payload)));
    }

    private List<OutboxEvent> events(String type, String key) {
        return outbox.findAll().stream().filter(e -> e.getType().equals(type) && e.getKey().equals(key)).toList();
    }

    @Test
    void rejectsUnsupportedTypeAndOversize() throws Exception {
        mvc.perform(post("/api/v1/media/uploads").header(Headers.PUBLIC_ID, ALICE).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\":\"POST\",\"contentType\":\"application/pdf\",\"sizeBytes\":100}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post("/api/v1/media/uploads").header(Headers.PUBLIC_ID, ALICE).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\":\"POST\",\"contentType\":\"image/png\",\"sizeBytes\":5000000}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void completeWithoutObjectIsConflict() throws Exception {
        String id = requestUpload(ALICE);
        when(storage.stat(any())).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/media/" + id + "/complete").header(Headers.PUBLIC_ID, ALICE).with(jwt()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_UPLOADED"));
        assertTrue(events(EventTypes.PHOTO_READY, id).isEmpty());
    }

    @Test
    void othersCannotTouchMyUpload() throws Exception {
        String id = requestUpload(ALICE);
        mvc.perform(post("/api/v1/media/" + id + "/complete").header(Headers.PUBLIC_ID, BOB).with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/media/" + id).header(Headers.PUBLIC_ID, BOB).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullHappyPathUploadToReadyToView() throws Exception {
        String id = requestUpload(ALICE);
        when(storage.stat(any())).thenReturn(Optional.of(new ObjectStorage.ObjectInfo("k", 12345, Instant.now(), "image/jpeg")));

        // Chưa complete thì bạn bè chưa xem được.
        when(users.relationship(BOB, ALICE)).thenReturn(new UserClient.Relationship(true, false));
        mvc.perform(get("/api/v1/media/" + id + "/url").header(Headers.PUBLIC_ID, BOB).with(jwt()))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/media/" + id + "/complete").header(Headers.PUBLIC_ID, ALICE).with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        List<OutboxEvent> ready = events(EventTypes.PHOTO_READY, id);
        assertEquals(1, ready.size());
        var payload = EventJson.read(ready.get(0).getPayload()).payloadAs(Events.PhotoReady.class);
        assertEquals(ALICE, payload.ownerPublicId());
        assertEquals(id + ".jpg", payload.publicKey());

        // Bấm complete lần nữa không phát thêm event.
        mvc.perform(post("/api/v1/media/" + id + "/complete").header(Headers.PUBLIC_ID, ALICE).with(jwt()))
                .andExpect(status().isOk());
        assertEquals(1, events(EventTypes.PHOTO_READY, id).size());

        mvc.perform(get("/api/v1/media/" + id + "/url").header(Headers.PUBLIC_ID, BOB).with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("http://files/public/signed"));
        // Chủ ảnh luôn xem được, không cần gọi user-service.
        mvc.perform(get("/api/v1/media/" + id + "/url").header(Headers.PUBLIC_ID, ALICE).with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void strangersAndBlockedUsersCannotViewReadyPhoto() throws Exception {
        String id = requestUpload(ALICE);
        when(storage.stat(any())).thenReturn(Optional.of(new ObjectStorage.ObjectInfo("k", 10, Instant.now(), "image/jpeg")));
        mvc.perform(post("/api/v1/media/" + id + "/complete").header(Headers.PUBLIC_ID, ALICE).with(jwt()));

        when(users.relationship(BOB, ALICE)).thenReturn(new UserClient.Relationship(false, false));
        mvc.perform(get("/api/v1/media/" + id + "/url").header(Headers.PUBLIC_ID, BOB).with(jwt()))
                .andExpect(status().isNotFound());
        when(users.relationship(BOB, ALICE)).thenReturn(new UserClient.Relationship(true, true));
        mvc.perform(get("/api/v1/media/" + id + "/url").header(Headers.PUBLIC_ID, BOB).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void oversizeObjectAtCompleteIsRejectedAndRawDeleted() throws Exception {
        String id = requestUpload(ALICE);
        when(storage.stat(any())).thenReturn(Optional.of(new ObjectStorage.ObjectInfo("k", 50_000_000L, Instant.now(), "image/jpeg")));
        mvc.perform(post("/api/v1/media/" + id + "/complete").header(Headers.PUBLIC_ID, ALICE).with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        MediaJob job = jobs.findById(id).orElseThrow();
        assertEquals("FILE_TOO_LARGE", job.getRejectReason());
        verify(storage).delete(job.getRawKey());
        assertEquals(1, events(EventTypes.PHOTO_REJECTED, id).size());
        assertTrue(events(EventTypes.PHOTO_READY, id).isEmpty());
    }

    @Test
    void unsupportedContentTypeAtCompleteIsRejectedAndDeleted() throws Exception {
        String id = requestUpload(ALICE);
        when(storage.stat(any())).thenReturn(Optional.of(new ObjectStorage.ObjectInfo("k", 100, Instant.now(), "text/html")));
        mvc.perform(post("/api/v1/media/" + id + "/complete").header(Headers.PUBLIC_ID, ALICE).with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        MediaJob job = jobs.findById(id).orElseThrow();
        assertEquals("UNSUPPORTED_MEDIA_TYPE", job.getRejectReason());
        verify(storage).delete(job.getRawKey());
        assertTrue(events(EventTypes.PHOTO_READY, id).isEmpty());
    }

    @Test
    void abandonedUploadRequestsAreDeleted() throws Exception {
        String id = requestUpload(ALICE);
        assertEquals(0, media.expireAbandoned());                      // chưa quá hạn
        MediaJob job = jobs.findById(id).orElseThrow();
        job.setUpdatedAt(Instant.now().minus(Duration.ofHours(2)));
        jobs.save(job);
        assertTrue(media.expireAbandoned() >= 1);
        assertTrue(jobs.findById(id).isEmpty());
    }

    @Test
    void userDeletedRemovesMediaAndObjects() throws Exception {
        String id = requestUpload(ALICE);
        deliver(EventTypes.USER_DELETED, new Events.UserDeleted(ALICE));
        assertTrue(jobs.findById(id).isEmpty());
        verify(storage).delete(eq(id + ".jpg"));
    }

    @Test
    void uploadRateLimitPerHour() throws Exception {
        String owner = "cccccccccccccccccccc";
        for (int i = 0; i < 5; i++) {
            requestUpload(owner);
        }
        mvc.perform(post("/api/v1/media/uploads").header(Headers.PUBLIC_ID, owner).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\":\"POST\",\"contentType\":\"image/jpeg\",\"sizeBytes\":100}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("UPLOAD_LIMIT"));
    }
}
