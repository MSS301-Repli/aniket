package com.animallocket.chat;

import com.animallocket.chat.domain.ConversationGlyph;
import com.animallocket.chat.domain.Message;
import com.animallocket.chat.service.ChatService;
import com.animallocket.chat.service.GlyphService;
import com.animallocket.chat.service.GlyphSettings;
import com.animallocket.chat.service.GlyphShapes;
import com.animallocket.common.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static com.animallocket.chat.GlyphFixture.ALICE;
import static com.animallocket.chat.GlyphFixture.BOB;
import static com.animallocket.chat.GlyphFixture.CAROL;
import static com.animallocket.chat.GlyphFixture.PINK;
import static com.animallocket.chat.GlyphFixture.cmd;
import static com.animallocket.chat.GlyphFixture.square;
import static com.animallocket.chat.GlyphFixture.zigzag;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Logic từ điển glyph và tin GLYPH trên kho giả trong bộ nhớ. */
class GlyphServiceTest {
    final ObjectMapper json = new ObjectMapper();
    GlyphFixture f;
    String cid;

    @BeforeEach
    void setUp() {
        f = new GlyphFixture();
        cid = f.openAliceBob();
    }

    private static String code(Runnable r) {
        return assertThrows(ApiException.class, r::run).code();
    }

    private GlyphService.GlyphView create(String meaning, List<Number> points) {
        return f.glyphs.create(ALICE, cid, cmd(meaning, points)).glyph();
    }

    // ------------------------------------------------------------------ validation & canonical hash

    @Test
    void pointValidationRejectsWrongCountRangeAndNonIntegers() {
        assertEquals(128, GlyphShapes.validate(square(160), 64).length);
        Supplier<List<Number>> base = () -> new ArrayList<>(square(160));

        List<Number> tooFew = base.get();
        tooFew.remove(0);
        List<Number> outOfRange = base.get();
        outOfRange.set(5, 1001);
        List<Number> fractional = base.get();
        fractional.set(3, 1.5);
        List<Number> withNull = base.get();
        withNull.set(0, null);
        List<Number> huge = base.get();
        huge.set(0, Long.MAX_VALUE);

        for (List<Number> bad : List.of(tooFew, outOfRange, fractional, withNull, huge)) {
            assertEquals("GLYPH_INVALID_POINTS", code(() -> GlyphShapes.validate(bad, null)));
        }
        assertEquals("GLYPH_INVALID_POINTS", code(() -> GlyphShapes.validate(square(160), 32)));
        assertEquals("GLYPH_INVALID_POINTS", code(() -> GlyphShapes.validate(null, 64)));
        // mọi điểm trùng nhau: không phải nét vẽ
        List<Number> dot = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            dot.add(7);
        }
        assertEquals("GLYPH_INVALID_POINTS", code(() -> GlyphShapes.canonicalize(GlyphShapes.validate(dot, 64))));
    }

    @Test
    void canonicalHashIgnoresPositionAndScaleButNotShape() {
        int[] small = GlyphShapes.validate(square(160), 64);
        int[] big = GlyphShapes.validate(square(800), 64);
        int[] moved = small.clone();
        for (int i = 0; i < moved.length; i++) {
            moved[i] += (i % 2 == 0 ? -300 : 120);
        }
        String h = GlyphShapes.hash(GlyphShapes.canonicalize(small));
        assertEquals(h, GlyphShapes.hash(GlyphShapes.canonicalize(big)));
        assertEquals(h, GlyphShapes.hash(GlyphShapes.canonicalize(moved)));
        assertNotEquals(h, GlyphShapes.hash(GlyphShapes.canonicalize(GlyphShapes.validate(zigzag(1), 64))));
        assertEquals(64, h.length()); // SHA-256 hex

        int[] canonical = GlyphShapes.canonicalize(big);
        int maxAbs = 0;
        for (int v : canonical) {
            maxAbs = Math.max(maxAbs, Math.abs(v));
        }
        assertEquals(1000, maxAbs);
    }

    @Test
    void meaningColorAndMutationIdAreValidated() {
        assertEquals("GLYPH_INVALID_MEANING", code(() -> create("   ", zigzag(1))));
        assertEquals("GLYPH_INVALID_MEANING", code(() -> create("x".repeat(65), zigzag(1))));
        assertEquals("GLYPH_INVALID_MEANING", code(() -> create("a\nb", zigzag(1))));
        // 64 ký tự kể cả emoji (đếm theo code point)
        assertEquals("😀".repeat(64), create("😀".repeat(64), zigzag(1)).meaning());

        assertEquals("GLYPH_INVALID_COLOR", code(() -> f.glyphs.create(ALICE, cid,
                new GlyphService.CreateCommand(UUID.randomUUID().toString(), "x", "#123456", zigzag(2), 64))));
        assertEquals("GLYPH_INVALID_COLOR", code(() -> f.glyphs.create(ALICE, cid,
                new GlyphService.CreateCommand(UUID.randomUUID().toString(), "x", "red", zigzag(2), 64))));
        // màu trong palette, khác hoa thường vẫn được và được chuẩn hoá
        assertEquals(PINK, f.glyphs.create(ALICE, cid, new GlyphService.CreateCommand(
                UUID.randomUUID().toString(), "x", "#f472b6", zigzag(2), 64)).glyph().color());

        assertEquals("VALIDATION_FAILED", code(() -> f.glyphs.create(ALICE, cid,
                new GlyphService.CreateCommand("not-a-uuid", "x", PINK, zigzag(3), 64))));
    }

    // ------------------------------------------------------------------ create / idempotency / revision

    @Test
    void createAssignsIncreasingRevisionsAndNotifiesBothSides() throws Exception {
        f.frames.clear();
        var g1 = create("Đi cà phê không?", zigzag(1));
        var g2 = create("Ok", zigzag(2));

        assertEquals(1, g1.revision());
        assertEquals(2, g2.revision());
        assertEquals(ConversationGlyph.Status.ACTIVE, g1.status());
        assertEquals(ALICE, g1.creatorPublicId());
        assertEquals(128, g1.points().size());

        List<String> recipients = f.frames.stream().map(x -> x[0]).toList();
        assertEquals(List.of(ALICE, BOB, ALICE, BOB), recipients);
        JsonNode frame = json.readTree(f.frames.get(3)[1]);
        assertEquals("dictionary_changed", frame.get("type").asText());
        assertEquals(cid, frame.get("data").get("conversationId").asText());
        assertEquals(2, frame.get("data").get("revision").asLong());
        assertEquals(g2.id(), frame.get("data").get("glyphId").asText());
        assertEquals("UPSERT", frame.get("data").get("change").asText());
        // chỉ báo thay đổi, không đẩy dữ liệu glyph qua WebSocket
        assertFalse(f.frames.get(3)[1].contains("Ok"));
    }

    @Test
    void retryWithSameMutationIdReturnsSameGlyphWithoutNewRevision() {
        var c = cmd("Ok", zigzag(1));
        var first = f.glyphs.create(ALICE, cid, c);
        f.frames.clear();
        var again = f.glyphs.create(ALICE, cid, c);

        assertTrue(first.created());
        assertFalse(again.created());
        assertEquals(first.glyph().id(), again.glyph().id());
        assertEquals(1, f.glyphStore.size());
        assertEquals(1, f.glyphs.sync(ALICE, cid, 0, 100).revision());
        assertTrue(f.frames.isEmpty());
        // không tốn hạn mức tạo trong ngày
        String quotaKey = "chat:glyph:rl:" + ALICE + ":" + java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
        assertEquals("1", f.kv.get(quotaKey).orElseThrow());
    }

    @Test
    void duplicateCanonicalShapeIsRejectedUntilArchived() {
        var g = create("vuông", square(160));
        assertEquals("GLYPH_DUPLICATE", code(() -> create("vuông to", square(800))));
        f.glyphs.archive(ALICE, cid, g.id());
        assertEquals(ConversationGlyph.Status.ACTIVE, create("vuông mới", square(800)).status());
    }

    @Test
    void activeLimitPerConversation() {
        GlyphFixture small = new GlyphFixture(new GlyphSettings(true, Set.of(PINK), 2, 20));
        String c = small.openAliceBob();
        small.glyphs.create(ALICE, c, cmd("a", zigzag(1)));
        var b = small.glyphs.create(BOB, c, cmd("b", zigzag(2))).glyph();
        assertEquals("GLYPH_LIMIT", code(() -> small.glyphs.create(ALICE, c, cmd("c", zigzag(3)))));
        small.glyphs.archive(BOB, c, b.id()); // archive giải phóng chỗ
        small.glyphs.create(ALICE, c, cmd("c", zigzag(3)));
    }

    @Test
    void dailyCreateQuotaPerUser() {
        GlyphFixture small = new GlyphFixture(new GlyphSettings(true, Set.of(PINK), 100, 2));
        String c = small.openAliceBob();
        small.glyphs.create(ALICE, c, cmd("a", zigzag(1)));
        small.glyphs.create(ALICE, c, cmd("b", zigzag(2)));
        assertEquals("GLYPH_CREATE_RATE_LIMIT", code(() -> small.glyphs.create(ALICE, c, cmd("c", zigzag(3)))));
        small.glyphs.create(BOB, c, cmd("c", zigzag(3))); // hạn mức tính theo từng người
    }

    @Test
    void revisionCollisionWithAnotherWriterIsRetried() {
        create("a", zigzag(1));
        // Một instance khác ghi revision 2 ngay sau khi request này đã tính revision = 2.
        f.beforeNextInsert = () -> {
            ConversationGlyph other = new ConversationGlyph();
            other.setId("g-other");
            other.setConversationId(cid);
            other.setCreatorPublicId(BOB);
            other.setClientMutationId(UUID.randomUUID().toString());
            other.setMeaning("khác");
            other.setColor(PINK);
            other.setShapeHash("other-hash");
            other.setRevision(2);
            f.glyphStore.put(other.getId(), other);
        };
        var g = create("b", zigzag(2));
        assertEquals(3, g.revision());
    }

    @Test
    void concurrentCreatesWithSameMutationIdProduceOneGlyph() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            // Nhiều vòng để race giữa "kiểm tra replay" và "kiểm tra trùng hình" xảy ra ổn định.
            for (int round = 0; round < 30; round++) {
                GlyphFixture fx = new GlyphFixture();
                String conv = fx.openAliceBob();
                var c = cmd("cùng lúc", zigzag(5));
                CountDownLatch go = new CountDownLatch(1);
                List<Future<GlyphService.CreateResult>> results = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    results.add(pool.submit(() -> {
                        go.await();
                        return fx.glyphs.create(ALICE, conv, c);
                    }));
                }
                go.countDown();
                List<GlyphService.CreateResult> done = new ArrayList<>();
                for (var r : results) {
                    done.add(r.get()); // không request nào được nhận GLYPH_DUPLICATE
                }
                assertEquals(1, fx.glyphStore.size());
                assertEquals(1, done.stream().filter(GlyphService.CreateResult::created).count());
                assertEquals(1, done.stream().map(r -> r.glyph().id()).distinct().count());
                assertEquals(1, fx.glyphs.sync(BOB, conv, 0, 100).revision());
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void concurrentCreatesOfDifferentGlyphsGetDistinctRevisions() throws Exception {
        int threads = 7; // zigzag(0..6) là 7 hình khác nhau
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<GlyphService.CreateResult>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int seed = i;
            results.add(pool.submit(() -> {
                go.await();
                return f.glyphs.create(seed % 2 == 0 ? ALICE : BOB, cid, cmd("g" + seed, zigzag(seed)));
            }));
        }
        go.countDown();
        int ok = 0;
        for (var r : results) {
            try {
                r.get();
                ok++;
            } catch (java.util.concurrent.ExecutionException e) {
                // nhiều người ghi cùng lúc có thể hết lượt thử: phải là lỗi ổn định GLYPH_BUSY, không phải lỗi lạ
                assertEquals("GLYPH_BUSY", ((ApiException) e.getCause()).code());
            }
        }
        pool.shutdown();
        assertEquals(ok, f.glyphStore.size());
        assertEquals(ok, f.glyphStore.values().stream().map(ConversationGlyph::getRevision).distinct().count());
        assertEquals(ok, f.glyphs.sync(ALICE, cid, 0, 100).revision());
    }

    // ------------------------------------------------------------------ sync / tombstone / get

    @Test
    void deltaSyncPagesInRevisionOrderAndIncludesTombstones() {
        var a = create("a", zigzag(1));
        var b = create("b", zigzag(2));
        var c = create("c", zigzag(3));
        f.glyphs.archive(ALICE, cid, a.id()); // a -> revision 4, ARCHIVED

        var full = f.glyphs.sync(BOB, cid, 0, 100);
        assertEquals(4, full.revision());
        assertEquals(List.of(b.id(), c.id(), a.id()), full.items().stream().map(GlyphService.GlyphView::id).toList());
        assertEquals(ConversationGlyph.Status.ARCHIVED, full.items().get(2).status());
        assertEquals("a", full.items().get(2).meaning()); // tombstone vẫn giữ nghĩa và nét
        assertEquals(128, full.items().get(2).points().size());
        assertFalse(full.hasMore());
        assertEquals(4, full.nextAfterRevision());

        var p1 = f.glyphs.sync(BOB, cid, 0, 2);
        assertTrue(p1.hasMore());
        assertEquals(2, p1.items().size());
        assertEquals(3, p1.nextAfterRevision());
        var p2 = f.glyphs.sync(BOB, cid, p1.nextAfterRevision(), 2);
        assertFalse(p2.hasMore());
        assertEquals(List.of(a.id()), p2.items().stream().map(GlyphService.GlyphView::id).toList());
        assertEquals(4, p2.nextAfterRevision());

        var upToDate = f.glyphs.sync(BOB, cid, 4, 100);
        assertTrue(upToDate.items().isEmpty());
        assertEquals(4, upToDate.nextAfterRevision());
        // limit ngoài 1..100 bị kẹp lại, afterRevision âm coi như 0
        assertEquals(1, f.glyphs.sync(BOB, cid, -5, 0).items().size());
        assertEquals(3, f.glyphs.sync(BOB, cid, 0, 1000).items().size());
    }

    @Test
    void offlineClientRecoversFullDictionaryFromItsLastRevision() {
        create("a", zigzag(1));
        long seen = f.glyphs.sync(BOB, cid, 0, 100).nextAfterRevision();
        // Bob offline: Alice tạo thêm và gỡ một ký hiệu
        var b = create("b", zigzag(2));
        create("c", zigzag(3));
        f.glyphs.archive(ALICE, cid, b.id());

        List<GlyphService.GlyphView> got = new ArrayList<>();
        long after = seen;
        GlyphService.SyncPage page;
        do {
            page = f.glyphs.sync(BOB, cid, after, 1);
            got.addAll(page.items());
            after = page.nextAfterRevision();
        } while (page.hasMore());
        assertEquals(4, after);
        assertEquals(Set.of("b", "c"), Set.copyOf(got.stream().map(GlyphService.GlyphView::meaning).toList()));
        assertTrue(got.stream().anyMatch(g -> g.id().equals(b.id()) && g.status() == ConversationGlyph.Status.ARCHIVED));
    }

    @Test
    void getSingleGlyphOnlyWithinItsConversation() {
        var g = create("a", zigzag(1));
        assertEquals("a", f.glyphs.get(BOB, cid, g.id()).meaning());
        assertEquals("GLYPH_NOT_FOUND", code(() -> f.glyphs.get(BOB, cid, "missing")));

        f.relations.put(ALICE + ">" + CAROL, new com.animallocket.chat.client.Gateways.Relationship(true, false));
        String other = f.chat.open(ALICE, CAROL).id();
        assertEquals("GLYPH_NOT_FOUND", code(() -> f.glyphs.get(ALICE, other, g.id())));
    }

    // ------------------------------------------------------------------ archive

    @Test
    void archiveIsOwnerOnlyIdempotentAndBumpsRevision() throws Exception {
        var g = create("a", zigzag(1));
        assertEquals("GLYPH_NOT_OWNER", code(() -> f.glyphs.archive(BOB, cid, g.id())));
        assertEquals("GLYPH_NOT_FOUND", code(() -> f.glyphs.archive(ALICE, cid, "missing")));

        f.frames.clear();
        f.glyphs.archive(ALICE, cid, g.id());
        f.glyphs.archive(ALICE, cid, g.id()); // lặp lại: vẫn thành công, không tăng revision lần hai

        assertEquals(2, f.glyphs.sync(ALICE, cid, 0, 100).revision());
        assertEquals(2, f.frames.size()); // một thông báo cho mỗi người
        JsonNode data = json.readTree(f.frames.get(0)[1]).get("data");
        assertEquals("ARCHIVE", data.get("change").asText());
        assertEquals(2, data.get("revision").asLong());
        ConversationGlyph stored = f.glyphStore.get(g.id());
        assertEquals("a", stored.getMeaning());
        assertEquals(128, stored.getNormalizedPoints().size());
        assertTrue(stored.getArchivedAt() != null);
    }

    // ------------------------------------------------------------------ tin GLYPH

    @Test
    void glyphMessageKeepsOrderAndUsesServerFallback() throws Exception {
        var a = create("Đi cà phê không?", zigzag(1));
        var b = create("Ok", zigzag(2));
        f.frames.clear();

        var sent = f.chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.GLYPH, null, null,
                List.of(b.id(), a.id(), b.id()), "client-1"));

        assertEquals(Message.Type.GLYPH, sent.type());
        assertEquals(List.of(b.id(), a.id(), b.id()), sent.glyphIds());
        assertEquals("Ok · Đi cà phê không? · Ok", sent.text());
        assertEquals(2L, sent.dictionaryRevision());
        assertEquals("client-1", sent.clientId());

        JsonNode frame = json.readTree(f.frames.get(0)[1]);
        assertEquals("message", frame.get("type").asText());
        assertEquals("GLYPH", frame.get("data").get("type").asText());
        assertEquals(3, frame.get("data").get("glyphIds").size());
        assertEquals("Ok · Đi cà phê không? · Ok", frame.get("data").get("text").asText());
    }

    @Test
    void glyphMessageValidation() {
        var a = create("a", zigzag(1));
        f.glyphs.archive(ALICE, cid, a.id());
        var b = create("b", zigzag(2));
        Supplier<List<String>> thirteen = () -> java.util.Collections.nCopies(13, b.id());

        assertEquals("GLYPH_NOT_ACTIVE", code(() -> sendGlyphs(List.of(b.id(), a.id()))));
        assertEquals("GLYPH_NOT_FOUND", code(() -> sendGlyphs(List.of("nope"))));
        assertEquals("GLYPH_SEQUENCE_TOO_LONG", code(() -> sendGlyphs(thirteen.get())));
        assertEquals("INVALID_GLYPH_MESSAGE", code(() -> sendGlyphs(List.of())));
        assertEquals("INVALID_GLYPH_MESSAGE", code(() -> sendGlyphs(null)));
        List<String> withBlank = new ArrayList<>(List.of(b.id(), " "));
        assertEquals("INVALID_GLYPH_MESSAGE", code(() -> sendGlyphs(withBlank)));
        // client không được tự gửi fallback
        assertEquals("INVALID_GLYPH_MESSAGE", code(() -> f.chat.send(ALICE, cid,
                new ChatService.SendCommand(Message.Type.GLYPH, "fake fallback", null, List.of(b.id()), null))));

        // glyph của hội thoại khác
        f.relations.put(ALICE + ">" + CAROL, new com.animallocket.chat.client.Gateways.Relationship(true, false));
        f.relations.put(CAROL + ">" + ALICE, new com.animallocket.chat.client.Gateways.Relationship(true, false));
        String other = f.chat.open(ALICE, CAROL).id();
        var foreign = f.glyphs.create(ALICE, other, cmd("x", zigzag(9))).glyph();
        assertEquals("GLYPH_NOT_FOUND", code(() -> sendGlyphs(List.of(b.id(), foreign.id()))));
        assertTrue(f.msgStore.isEmpty());
    }

    private void sendGlyphs(List<String> ids) {
        f.chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.GLYPH, null, null, ids, null));
    }

    @Test
    void archivingDoesNotBreakHistoricMessages() {
        var a = create("a", zigzag(1));
        var sent = f.chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.GLYPH, null, null, List.of(a.id()), null));
        f.glyphs.archive(ALICE, cid, a.id());

        var history = f.chat.history(BOB, cid, null, 30);
        assertEquals(sent.id(), history.get(0).id());
        assertEquals(List.of(a.id()), history.get(0).glyphIds());
        assertEquals("a", history.get(0).text());
        // client vẫn lấy được glyph đã archive để vẽ tin cũ
        assertEquals(ConversationGlyph.Status.ARCHIVED, f.glyphs.get(BOB, cid, a.id()).status());
    }

    @Test
    void retriedMessageWithSameClientIdIsNotDuplicated() {
        var a = create("a", zigzag(1));
        var cmd = new ChatService.SendCommand(Message.Type.GLYPH, null, null, List.of(a.id()), "same-client-id");
        var first = f.chat.send(ALICE, cid, cmd);
        f.frames.clear();
        var again = f.chat.send(ALICE, cid, cmd);

        assertEquals(first.id(), again.id());
        assertEquals(1, f.msgStore.size());
        assertTrue(f.frames.isEmpty());
        // clientId là của từng người gửi: Bob dùng cùng giá trị vẫn là tin riêng
        f.chat.send(BOB, cid, new ChatService.SendCommand(Message.Type.TEXT, "hi", null, null, "same-client-id"));
        assertEquals(2, f.msgStore.size());
    }

    // ------------------------------------------------------------------ quyền & vòng đời

    @Test
    void onlyParticipantsCanReadOrWriteTheDictionary() {
        var g = create("a", zigzag(1));
        assertEquals("NOT_FOUND", code(() -> f.glyphs.sync(CAROL, cid, 0, 100)));
        assertEquals("NOT_FOUND", code(() -> f.glyphs.get(CAROL, cid, g.id())));
        assertEquals("NOT_FOUND", code(() -> f.glyphs.create(CAROL, cid, cmd("x", zigzag(2)))));
        assertEquals("NOT_FOUND", code(() -> f.glyphs.archive(CAROL, cid, g.id())));
        assertEquals("NOT_FOUND", code(() -> f.chat.send(CAROL, cid,
                new ChatService.SendCommand(Message.Type.GLYPH, null, null, List.of(g.id()), null))));
    }

    @Test
    void blockingStopsCreateArchiveAndSendButKeepsReadPath() {
        var g = create("a", zigzag(1));
        f.block(ALICE, BOB);

        assertEquals("FORBIDDEN", code(() -> create("b", zigzag(2))));
        assertEquals("FORBIDDEN", code(() -> f.glyphs.archive(ALICE, cid, g.id())));
        assertEquals("FORBIDDEN", code(() -> sendGlyphs(List.of(g.id()))));
        assertEquals(1, f.glyphs.sync(BOB, cid, 0, 100).items().size());
    }

    @Test
    void unfriendingStopsWrites() {
        create("a", zigzag(1));
        f.relations.put(ALICE + ">" + BOB, new com.animallocket.chat.client.Gateways.Relationship(false, false));
        f.chat.onUnfriended(ALICE, BOB);
        assertEquals("FORBIDDEN", code(() -> create("b", zigzag(2))));
    }

    @Test
    void deletingAccountRemovesGlyphsWithTheConversation() {
        create("a", zigzag(1));
        f.chat.onUserDeleted(BOB);
        assertTrue(f.glyphStore.isEmpty());
        assertTrue(f.convStore.isEmpty());
    }

    @Test
    void rollbackFlagDisablesWritesButKeepsReads() {
        GlyphFixture on = new GlyphFixture();
        String c = on.openAliceBob();
        var g = on.glyphs.create(ALICE, c, cmd("a", zigzag(1))).glyph();
        on.chat.send(ALICE, c, new ChatService.SendCommand(Message.Type.GLYPH, null, null, List.of(g.id()), null));

        // cùng dữ liệu, cấu hình rollback
        GlyphFixture off = new GlyphFixture(new GlyphSettings(false, Set.of(PINK), 100, 20));
        off.convStore.putAll(on.convStore);
        off.glyphStore.putAll(on.glyphStore);
        off.msgStore.addAll(on.msgStore);

        assertEquals("GLYPH_DISABLED", code(() -> off.glyphs.create(ALICE, c, cmd("b", zigzag(2)))));
        assertEquals("GLYPH_DISABLED", code(() -> off.glyphs.archive(ALICE, c, g.id())));
        assertEquals("GLYPH_DISABLED", code(() -> off.chat.send(ALICE, c,
                new ChatService.SendCommand(Message.Type.GLYPH, null, null, List.of(g.id()), null))));
        assertEquals(1, off.glyphs.sync(BOB, c, 0, 100).items().size());
        assertEquals(Message.Type.GLYPH, off.chat.history(BOB, c, null, 30).get(0).type());
        // TEXT vẫn chạy bình thường khi tắt glyph
        off.chat.send(ALICE, c, new ChatService.SendCommand(Message.Type.TEXT, "hi", null));
    }

    @Test
    void textAndImageViewsDoNotGainNewFields() throws Exception {
        var sent = f.chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "hi", null));
        assertNull(sent.glyphIds());
        JsonNode v = new ObjectMapper().findAndRegisterModules().valueToTree(sent);
        assertFalse(v.has("glyphIds"));
        assertFalse(v.has("dictionaryRevision"));
        assertFalse(v.has("clientId"));
        assertTrue(v.has("mediaId")); // trường cũ giữ nguyên (kể cả null)
    }
}
