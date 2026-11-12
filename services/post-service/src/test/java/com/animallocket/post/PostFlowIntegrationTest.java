package com.animallocket.post;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Headers;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.common.web.ApiException;
import com.animallocket.outbox.OutboxEvent;
import com.animallocket.outbox.OutboxRepository;
import com.animallocket.post.client.UserDirectory;
import com.animallocket.post.domain.Post;
import com.animallocket.post.messaging.PostEventsListener;
import com.animallocket.post.repo.PostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostFlowIntegrationTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";
    static final String CAROL = "cccccccccccccccccccc";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRepository outbox;
    @Autowired PostRepository posts;
    @Autowired PostEventsListener listener;
    @Autowired com.animallocket.post.service.PendingPostReconciler reconciler;
    @MockBean UserDirectory users;
    @MockBean JwtDecoder jwtDecoder;

    @Autowired com.animallocket.post.repo.PostLikeRepository likeRepo;
    @Autowired com.animallocket.post.repo.CommentRepository commentRepo;
    @Autowired com.animallocket.post.repo.MediaStateRepository mediaStateRepo;
    @Autowired com.animallocket.outbox.ProcessedEventRepository processedRepo;

    @BeforeEach
    void setUp() {
        likeRepo.deleteAll();
        commentRepo.deleteAll();
        posts.deleteAll();
        mediaStateRepo.deleteAll();
        outbox.deleteAll();
        processedRepo.deleteAll();
        // ALICE và BOB là bạn; CAROL là người lạ.
        when(users.friendIds(ALICE)).thenReturn(Set.of(BOB));
        when(users.friendIds(BOB)).thenReturn(Set.of(ALICE));
        when(users.friendIds(CAROL)).thenReturn(Set.of());
        when(users.areFriends(ALICE, BOB)).thenReturn(true);
        when(users.areFriends(BOB, ALICE)).thenReturn(true);
        when(users.areFriends(CAROL, ALICE)).thenReturn(false);
        when(users.profiles(org.mockito.ArgumentMatchers.anySet())).thenReturn(Map.of(
                ALICE, new UserDirectory.Profile(ALICE, "Cáo Vui", "fox", "1:fox:0:none")));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String me) {
        return b.header(Headers.PUBLIC_ID, me).with(jwt()).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String createPost(String me, String mediaId) throws Exception {
        MvcResult r = mvc.perform(as(post("/api/v1/posts"), me)
                .content("{\"mediaId\":\"" + mediaId + "\",\"caption\":\"hello\"}")).andExpect(status().isCreated()).andReturn();
        return body(r).get("id").asText();
    }

    private void deliver(String type, Object payload) {
        listener.onMessage(EventJson.write(EventEnvelope.of(type, "media-service", "k", payload)));
    }

    private void ready(String mediaId, String owner) {
        deliver(EventTypes.PHOTO_READY, new Events.PhotoReady(mediaId, owner, "pub/" + mediaId + ".jpg", "STICKER", 1, "POST", null));
    }

    private List<OutboxEvent> events(String type) {
        return outbox.findAll().stream().filter(e -> e.getType().equals(type)).toList();
    }

    private String media() {
        return UUID.randomUUID().toString();
    }

    @Test
    void postStaysPendingUntilPhotoReadyThenAppearsInFriendsFeed() throws Exception {
        String m = media();
        String id = createPost(ALICE, m);
        mvc.perform(as(get("/api/v1/posts/mine"), ALICE)).andExpect(jsonPath("$[0].status").value("PENDING_MEDIA"));

        // Chưa xử lý xong ảnh: bạn bè không thấy gì (cả feed lẫn truy cập trực tiếp).
        mvc.perform(as(get("/api/v1/feed"), BOB)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(as(get("/api/v1/posts/" + id), BOB)).andExpect(status().isNotFound());

        ready(m, ALICE);

        mvc.perform(as(get("/api/v1/feed"), BOB)).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.items[0].author.alias").value("Cáo Vui"))
                .andExpect(jsonPath("$.items[0].author.avatar").value("1:fox:0:none"));
        assertEquals(1, events(EventTypes.POST_PUBLISHED).size());
        // DTO không chứa đường dẫn kho ảnh.
        String raw = mvc.perform(as(get("/api/v1/posts/" + id), BOB)).andReturn().getResponse().getContentAsString();
        assertTrue(!raw.contains("pub/"), raw);
    }

    @Test
    void photoReadyBeforePostCreationPublishesImmediately() throws Exception {
        String m = media();
        ready(m, ALICE);
        MvcResult r = mvc.perform(as(post("/api/v1/posts"), ALICE).content("{\"mediaId\":\"" + m + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PUBLISHED")).andReturn();
        assertTrue(body(r).get("publishedAt").asText().length() > 5);
    }

    @Test
    void reconcilerPublishesPostThatLostTheRaceWithPhotoReady() throws Exception {
        String m = media();
        String id = createPost(ALICE, m);
        // Mô phỏng cuộc đua: listener đã lưu kết quả ảnh nhưng không thấy bài (chưa commit) nên không cập nhật.
        mediaStateRepo.save(new com.animallocket.post.domain.MediaState(m, ALICE, "READY", "pub/" + m + ".jpg", null));
        assertEquals(Post.Status.PENDING_MEDIA, posts.findById(id).orElseThrow().getStatus());

        reconciler.reconcile();
        reconciler.reconcile(); // chạy lại không phát event lần hai

        Post p = posts.findById(id).orElseThrow();
        assertEquals(Post.Status.PUBLISHED, p.getStatus());
        assertEquals("pub/" + m + ".jpg", p.getPublicKey());
        assertEquals(1, events(EventTypes.POST_PUBLISHED).size());
        mvc.perform(as(get("/api/v1/feed"), BOB)).andExpect(jsonPath("$.items[0].id").value(id));
    }

    @Test
    void rejectedPhotoRejectsPost() throws Exception {
        String m1 = media();
        String id = createPost(ALICE, m1);
        deliver(EventTypes.PHOTO_REJECTED, new Events.PhotoRejected(m1, ALICE, "PROCESSING_TIMEOUT", "POST", null));
        assertEquals(Post.Status.REJECTED, posts.findById(id).orElseThrow().getStatus());
        assertEquals("PROCESSING_TIMEOUT", posts.findById(id).orElseThrow().getRejectReason());
        mvc.perform(as(get("/api/v1/feed"), BOB)).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void cannotAttachSomeoneElsesMedia() throws Exception {
        String m = media();
        ready(m, BOB);
        mvc.perform(as(post("/api/v1/posts"), ALICE).content("{\"mediaId\":\"" + m + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerMismatchOnLateReadyEventRejectsPost() throws Exception {
        String m = media();
        String id = createPost(ALICE, m);
        ready(m, BOB); // media thuộc người khác
        assertEquals(Post.Status.REJECTED, posts.findById(id).orElseThrow().getStatus());
        assertEquals("OWNER_MISMATCH", posts.findById(id).orElseThrow().getRejectReason());
    }

    @Test
    void strangersCannotSeeLikeOrCommentOnPost() throws Exception {
        String m = media();
        String id = createPost(ALICE, m);
        ready(m, ALICE);
        mvc.perform(as(get("/api/v1/posts/" + id), CAROL)).andExpect(status().isNotFound());
        mvc.perform(as(put("/api/v1/posts/" + id + "/like"), CAROL)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/v1/posts/" + id + "/comments"), CAROL).content("{\"text\":\"hi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void likesAndCommentsWorkForFriendsAndEmitEvents() throws Exception {
        String m = media();
        String id = createPost(ALICE, m);
        ready(m, ALICE);

        mvc.perform(as(put("/api/v1/posts/" + id + "/like"), BOB)).andExpect(status().isNoContent());
        mvc.perform(as(put("/api/v1/posts/" + id + "/like"), BOB)).andExpect(status().isNoContent()); // idempotent
        mvc.perform(as(get("/api/v1/posts/" + id), BOB)).andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true));
        assertEquals(1, events(EventTypes.POST_LIKED).size());

        // Tác giả tự like thì không phát event thông báo.
        mvc.perform(as(put("/api/v1/posts/" + id + "/like"), ALICE)).andExpect(status().isNoContent());
        assertEquals(1, events(EventTypes.POST_LIKED).size());

        mvc.perform(as(delete("/api/v1/posts/" + id + "/like"), BOB)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/v1/posts/" + id), ALICE)).andExpect(jsonPath("$.likeCount").value(1)); // còn like của ALICE

        MvcResult c = mvc.perform(as(post("/api/v1/posts/" + id + "/comments"), BOB).content("{\"text\":\"đẹp quá\"}"))
                .andExpect(status().isCreated()).andReturn();
        long commentId = body(c).get("id").asLong();
        mvc.perform(as(get("/api/v1/posts/" + id + "/comments"), ALICE)).andExpect(jsonPath("$.items.length()").value(1));
        assertEquals(1, events(EventTypes.POST_COMMENTED).size());

        // Người lạ trong thread không xoá được; tác giả bài xoá được bình luận của người khác.
        mvc.perform(as(delete("/api/v1/posts/" + id + "/comments/" + commentId), CAROL)).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/v1/posts/" + id + "/comments/" + commentId), ALICE)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/v1/posts/" + id), ALICE)).andExpect(jsonPath("$.commentCount").value(0));
    }

    @Test
    void onlyAuthorCanDelete() throws Exception {
        String m = media();
        String id = createPost(ALICE, m);
        ready(m, ALICE);
        mvc.perform(as(delete("/api/v1/posts/" + id), BOB)).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/v1/posts/" + id), ALICE)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/v1/feed"), BOB)).andExpect(jsonPath("$.items.length()").value(0));
        Post p = posts.findById(id).orElseThrow();
        assertEquals(Post.Status.DELETED, p.getStatus());
        assertEquals(null, p.getPublicKey());
    }

    @Test
    void feedHidesPostsWhenFriendListUnavailable() throws Exception {
        String m = media();
        createPost(ALICE, m);
        ready(m, ALICE);
        when(users.friendIds(BOB)).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FRIENDS_UNAVAILABLE", "x"));
        mvc.perform(as(get("/api/v1/feed"), BOB)).andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true)).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void feedPaginatesNewestFirst() throws Exception {
        for (int i = 0; i < 3; i++) {
            String m = media();
            createPostWithQuotaReset(m);
            ready(m, BOB);
            Thread.sleep(5);
        }
        MvcResult p1 = mvc.perform(as(get("/api/v1/feed?limit=2"), ALICE)).andExpect(jsonPath("$.items.length()").value(2)).andReturn();
        String next = body(p1).get("nextBefore").asText();
        mvc.perform(as(get("/api/v1/feed?limit=2&before=" + next), ALICE)).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void friendSeesOnlyPublishedPostsOfOnePersonWithPaging() throws Exception {
        String pending = createPost(BOB, media()); // chưa có PHOTO_READY
        for (int i = 0; i < 3; i++) {
            String m = media();
            createPostWithQuotaReset(m);
            ready(m, BOB);
            Thread.sleep(5);
        }
        MvcResult p1 = mvc.perform(as(get("/api/v1/posts/by/" + BOB + "?limit=2"), ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].status").value("PUBLISHED")).andReturn();
        String next = body(p1).get("nextBefore").asText();
        mvc.perform(as(get("/api/v1/posts/by/" + BOB + "?limit=2&before=" + next), ALICE))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextBefore").doesNotExist());

        // Chính chủ thấy cả bài đang chờ xử lý.
        mvc.perform(as(get("/api/v1/posts/by/" + BOB), BOB))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[3].id").value(pending));
    }

    @Test
    void strangerAndUnverifiableRelationshipCannotListPosts() throws Exception {
        String m = media();
        createPost(ALICE, m);
        ready(m, ALICE);
        mvc.perform(as(get("/api/v1/posts/by/" + ALICE), CAROL)).andExpect(status().isNotFound());

        when(users.areFriends(BOB, ALICE))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FRIENDS_UNAVAILABLE", "x"));
        mvc.perform(as(get("/api/v1/posts/by/" + ALICE), BOB)).andExpect(status().isNotFound());
    }

    private void createPostWithQuotaReset(String mediaId) throws Exception {
        mvc.perform(as(post("/api/v1/posts"), BOB).content("{\"mediaId\":\"" + mediaId + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void userDeletedWipesTheirContent() throws Exception {
        String m = media();
        String id = createPost(ALICE, m);
        ready(m, ALICE);
        mvc.perform(as(put("/api/v1/posts/" + id + "/like"), BOB));
        deliver(EventTypes.USER_DELETED, new Events.UserDeleted(ALICE));
        assertTrue(posts.findById(id).isEmpty());
        mvc.perform(as(get("/api/v1/posts/" + id), BOB)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateEventsAreProcessedOnce() throws Exception {
        String m = media();
        createPost(ALICE, m);
        EventEnvelope env = EventEnvelope.of(EventTypes.PHOTO_READY, "media-service", "k",
                new Events.PhotoReady(m, ALICE, "pub/x.jpg", "AI", 1, "POST", null));
        String wire = EventJson.write(env);
        listener.onMessage(wire);
        listener.onMessage(wire);
        assertEquals(1, events(EventTypes.POST_PUBLISHED).stream().filter(e -> e.getPayload().contains(ALICE)).count());
    }

    @Test
    void chatPhotosAreIgnoredByPostService() throws Exception {
        String m = media();
        deliver(EventTypes.PHOTO_READY, new Events.PhotoReady(m, ALICE, "pub/c.jpg", "AI", 1, "CHAT", "conv1"));
        // Không tạo MediaState: tạo post với media này vẫn ở PENDING.
        mvc.perform(as(post("/api/v1/posts"), ALICE).content("{\"mediaId\":\"" + m + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_MEDIA"));
    }
}
