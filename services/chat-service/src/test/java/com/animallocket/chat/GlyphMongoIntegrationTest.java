package com.animallocket.chat;

import com.animallocket.chat.client.Gateways;
import com.animallocket.chat.domain.ConversationGlyph;
import com.animallocket.chat.repo.ConversationRepository;
import com.animallocket.chat.repo.GlyphRepository;
import com.animallocket.chat.repo.MessageRepository;
import com.animallocket.chat.service.ChatService;
import com.animallocket.chat.service.GlyphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.animallocket.chat.GlyphFixture.ALICE;
import static com.animallocket.chat.GlyphFixture.BOB;
import static com.animallocket.chat.GlyphFixture.cmd;
import static com.animallocket.chat.GlyphFixture.zigzag;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Index unique và tạo đồng thời trên MongoDB thật. Tự bỏ qua khi máy không có Docker
 * (chạy lại khi có Docker: mvn -pl services/chat-service test).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // bean webSocketContainer cần servlet container thật
class GlyphMongoIntegrationTest {
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> mongo.getReplicaSetUrl("chat_glyph_test"));
    }

    @Autowired ChatService chat;
    @Autowired GlyphService glyphs;
    @Autowired GlyphRepository glyphRepo;
    @Autowired MessageRepository msgRepo;
    @Autowired ConversationRepository convRepo;
    @MockBean Gateways.UserGateway users;
    @MockBean Gateways.MediaGateway media;
    @MockBean Gateways.EventPublisher events;
    @MockBean org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    String cid;

    @BeforeEach
    void setUp() {
        glyphRepo.deleteAll();
        msgRepo.deleteAll();
        convRepo.deleteAll();
        when(users.relationship(anyString(), anyString())).thenReturn(new Gateways.Relationship(true, false));
        cid = chat.open(ALICE, BOB).id();
    }

    @Test
    void uniqueIndexesAreEnforced() {
        var g = glyphs.create(ALICE, cid, cmd("a", zigzag(1))).glyph();
        ConversationGlyph sameRevision = new ConversationGlyph();
        sameRevision.setConversationId(cid);
        sameRevision.setCreatorPublicId(BOB);
        sameRevision.setClientMutationId(UUID.randomUUID().toString());
        sameRevision.setShapeHash("other");
        sameRevision.setRevision(g.revision());
        assertThrows(DuplicateKeyException.class, () -> glyphRepo.insert(sameRevision));

        // glyph đã archive không chặn hình trùng (partial index chỉ trên ACTIVE)
        glyphs.archive(ALICE, cid, g.id());
        assertEquals(3, glyphs.create(BOB, cid, cmd("a again", zigzag(1))).glyph().revision());
    }

    @Test
    void syncPagesByRevisionRangeOnRealMongo() {
        glyphs.create(ALICE, cid, cmd("một", zigzag(1)));
        glyphs.create(BOB, cid, cmd("hai", zigzag(2)));
        glyphs.create(ALICE, cid, cmd("ba", zigzag(3)));

        var all = glyphs.sync(BOB, cid, 0, 100);
        assertEquals(List.of(1L, 2L, 3L), all.items().stream().map(GlyphService.GlyphView::revision).toList());

        var page = glyphs.sync(BOB, cid, 1, 1);
        assertEquals(List.of(2L), page.items().stream().map(GlyphService.GlyphView::revision).toList());
        assertTrue(page.hasMore());
    }

    @Test
    void concurrentCreatesAgainstRealIndexes() throws Exception {
        var same = cmd("cùng lúc", zigzag(0));
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads * 2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<GlyphService.CreateResult>> retries = new ArrayList<>();
        List<Future<GlyphService.CreateResult>> distinct = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int seed = i + 1;
            retries.add(pool.submit(() -> {
                go.await();
                return glyphs.create(ALICE, cid, same);
            }));
            distinct.add(pool.submit(() -> {
                go.await();
                return glyphs.create(BOB, cid, cmd("g" + seed, zigzag(seed)));
            }));
        }
        go.countDown();
        long createdRetries = 0;
        for (var r : retries) {
            createdRetries += r.get().created() ? 1 : 0;
        }
        int okDistinct = 0;
        for (var r : distinct) {
            try {
                r.get();
                okDistinct++;
            } catch (ExecutionException e) {
                assertEquals("GLYPH_BUSY", ((com.animallocket.common.web.ApiException) e.getCause()).code());
            }
        }
        pool.shutdown();

        assertEquals(1, createdRetries);
        List<ConversationGlyph> all = glyphRepo.findAll();
        assertEquals(1 + okDistinct, all.size());
        assertEquals(all.size(), all.stream().map(ConversationGlyph::getRevision).distinct().count());
        assertEquals(all.size(), glyphs.sync(BOB, cid, 0, 100).revision());
    }

    @Test
    void glyphMessageRoundTripAndClientIdIndex() {
        var g = glyphs.create(ALICE, cid, cmd("Ok", zigzag(1))).glyph();
        var send = new ChatService.SendCommand(com.animallocket.chat.domain.Message.Type.GLYPH, null, null,
                List.of(g.id()), "client-1");
        var first = chat.send(ALICE, cid, send);
        var again = chat.send(ALICE, cid, send);
        assertEquals(first.id(), again.id());
        assertEquals(1, msgRepo.count());

        var history = chat.history(BOB, cid, null, 10);
        assertEquals(List.of(g.id()), history.get(0).glyphIds());
        assertEquals("Ok", history.get(0).text());
        assertEquals(1L, history.get(0).dictionaryRevision());

        // tin cũ không có clientId không đụng partial index
        chat.send(ALICE, cid, new ChatService.SendCommand(com.animallocket.chat.domain.Message.Type.TEXT, "a", null));
        chat.send(ALICE, cid, new ChatService.SendCommand(com.animallocket.chat.domain.Message.Type.TEXT, "b", null));
        assertEquals(3, msgRepo.count());
    }
}
