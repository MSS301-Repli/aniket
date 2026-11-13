package com.animallocket.chat;

import com.animallocket.chat.web.ChatController;
import com.animallocket.chat.web.GlyphController;
import com.animallocket.chat.ws.ChatWebSocketHandler;
import com.animallocket.chat.ws.SessionRegistry;
import com.animallocket.common.Headers;
import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.web.ApiExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.animallocket.chat.GlyphFixture.ALICE;
import static com.animallocket.chat.GlyphFixture.BOB;
import static com.animallocket.chat.GlyphFixture.CAROL;
import static com.animallocket.chat.GlyphFixture.PINK;
import static com.animallocket.chat.GlyphFixture.zigzag;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST (MockMvc standalone + RFC 7807 handler thật) và WebSocket cho glyph, chạy trên kho giả trong bộ nhớ.
 * Phần Mongo thật nằm ở {@code GlyphMongoIntegrationTest} (cần Docker).
 */
class GlyphApiTest {
    final ObjectMapper json = new ObjectMapper();
    GlyphFixture f;
    MockMvc mvc;
    String cid;

    @BeforeEach
    void setUp() {
        f = new GlyphFixture();
        mvc = MockMvcBuilders.standaloneSetup(new GlyphController(f.glyphs), new ChatController(f.chat))
                .setControllerAdvice(new ApiExceptionHandler())
                .defaultResponseCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8).build();
        cid = f.openAliceBob();
    }

    private String body(String mutationId, String meaning, List<Number> points) throws Exception {
        ObjectNode n = json.createObjectNode();
        n.put("clientMutationId", mutationId);
        n.put("meaning", meaning);
        n.put("color", PINK);
        ArrayNode arr = n.putArray("points");
        points.forEach(p -> arr.add(p.intValue()));
        n.put("pointCount", 64);
        return json.writeValueAsString(n);
    }

    private ResultActions postGlyph(String me, String conv, String body) throws Exception {
        return mvc.perform(post("/api/v1/chat/conversations/" + conv + "/glyphs").header(Headers.PUBLIC_ID, me)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void createRetrySyncArchiveOverRest() throws Exception {
        String mutation = UUID.randomUUID().toString();
        String b = body(mutation, "Đi cà phê không?", zigzag(1));

        JsonNode created = read(postGlyph(ALICE, cid, b).andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.points.length()").value(128)));
        String id = created.get("id").asText();

        // retry: 200, cùng glyph, không tăng revision
        postGlyph(ALICE, cid, b).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.revision").value(1));

        mvc.perform(get("/api/v1/chat/conversations/" + cid + "/glyphs").param("afterRevision", "0").param("limit", "100")
                        .header(Headers.PUBLIC_ID, BOB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(cid))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextAfterRevision").value(1));

        mvc.perform(get("/api/v1/chat/conversations/" + cid + "/glyphs/" + id).header(Headers.PUBLIC_ID, BOB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meaning").value("Đi cà phê không?"));

        mvc.perform(delete("/api/v1/chat/conversations/" + cid + "/glyphs/" + id).header(Headers.PUBLIC_ID, BOB))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GLYPH_NOT_OWNER"));
        mvc.perform(delete("/api/v1/chat/conversations/" + cid + "/glyphs/" + id).header(Headers.PUBLIC_ID, ALICE))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/chat/conversations/" + cid + "/glyphs/" + id).header(Headers.PUBLIC_ID, ALICE))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/chat/conversations/" + cid + "/glyphs").param("afterRevision", "1")
                        .header(Headers.PUBLIC_ID, BOB))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.items[0].status").value("ARCHIVED"));
    }

    @Test
    void errorsAreProblemDetailsWithStableCodes() throws Exception {
        List<Number> bad = new ArrayList<>(zigzag(1));
        bad.set(0, 5000);
        postGlyph(ALICE, cid, body(UUID.randomUUID().toString(), "x", bad))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GLYPH_INVALID_POINTS"));

        // số thực trong points vẫn trả mã ổn định chứ không phải lỗi parse chung
        String fractional = body(UUID.randomUUID().toString(), "x", zigzag(1)).replaceFirst("\\[-320,", "[-320.5,");
        postGlyph(ALICE, cid, fractional)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GLYPH_INVALID_POINTS"));

        String wrongColor = body(UUID.randomUUID().toString(), "x", zigzag(1)).replace(PINK, "#000000");
        postGlyph(ALICE, cid, wrongColor)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GLYPH_INVALID_COLOR"));

        postGlyph(ALICE, cid, body(UUID.randomUUID().toString(), "x", zigzag(1))).andExpect(status().isCreated());
        postGlyph(BOB, cid, body(UUID.randomUUID().toString(), "y", zigzag(1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GLYPH_DUPLICATE"));

        mvc.perform(get("/api/v1/chat/conversations/" + cid + "/glyphs/nope").header(Headers.PUBLIC_ID, BOB))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GLYPH_NOT_FOUND"));

        mvc.perform(post("/api/v1/chat/conversations/" + cid + "/messages").header(Headers.PUBLIC_ID, ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GLYPH\",\"glyphIds\":[\"nope\"],\"clientId\":\"c1\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GLYPH_NOT_FOUND"));
    }

    @Test
    void securityOtherConversationSpoofedOwnerAndMissingHeader() throws Exception {
        JsonNode g = read(postGlyph(ALICE, cid, body(UUID.randomUUID().toString(), "x", zigzag(1))));
        String id = g.get("id").asText();

        // người ngoài hội thoại: như không tồn tại
        mvc.perform(get("/api/v1/chat/conversations/" + cid + "/glyphs").header(Headers.PUBLIC_ID, CAROL))
                .andExpect(status().isNotFound());
        postGlyph(CAROL, cid, body(UUID.randomUUID().toString(), "y", zigzag(2))).andExpect(status().isNotFound());

        // creatorPublicId / shapeHash / revision do client gửi bị bỏ qua
        ObjectNode spoof = (ObjectNode) json.readTree(body(UUID.randomUUID().toString(), "y", zigzag(2)));
        spoof.put("creatorPublicId", BOB);
        spoof.put("shapeHash", "deadbeef");
        spoof.put("revision", 999);
        JsonNode created = read(postGlyph(ALICE, cid, json.writeValueAsString(spoof)).andExpect(status().isCreated()));
        assertEquals(ALICE, created.get("creatorPublicId").asText());
        assertFalse("deadbeef".equals(created.get("shapeHash").asText()));
        assertEquals(2, created.get("revision").asLong());

        // glyph của hội thoại A không truy cập được qua đường dẫn hội thoại B
        f.relations.put(ALICE + ">" + CAROL, new com.animallocket.chat.client.Gateways.Relationship(true, false));
        String other = f.chat.open(ALICE, CAROL).id();
        mvc.perform(get("/api/v1/chat/conversations/" + other + "/glyphs/" + id).header(Headers.PUBLIC_ID, ALICE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GLYPH_NOT_FOUND"));
        mvc.perform(delete("/api/v1/chat/conversations/" + other + "/glyphs/" + id).header(Headers.PUBLIC_ID, ALICE))
                .andExpect(status().isNotFound());

        // thiếu X-Public-Id
        mvc.perform(get("/api/v1/chat/conversations/" + cid + "/glyphs"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));

        // nghĩa chứa HTML được lưu nguyên văn như văn bản thuần (server không render)
        JsonNode html = read(postGlyph(ALICE, cid, body(UUID.randomUUID().toString(), "<b>hi</b>", zigzag(3)))
                .andExpect(status().isCreated()));
        assertEquals("<b>hi</b>", html.get("meaning").asText());
    }

    @Test
    void glyphMessageOverRestAndOldTextContract() throws Exception {
        String id = read(postGlyph(ALICE, cid, body(UUID.randomUUID().toString(), "Ok", zigzag(1)))).get("id").asText();
        String req = "{\"type\":\"GLYPH\",\"glyphIds\":[\"" + id + "\",\"" + id + "\"],\"clientId\":\"cid-1\"}";

        JsonNode sent = read(mvc.perform(post("/api/v1/chat/conversations/" + cid + "/messages")
                        .header(Headers.PUBLIC_ID, ALICE).contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated()));
        assertEquals("GLYPH", sent.get("type").asText());
        assertEquals("Ok · Ok", sent.get("text").asText());
        assertEquals(1, sent.get("dictionaryRevision").asLong());

        // retry cùng clientId: cùng messageId
        JsonNode again = read(mvc.perform(post("/api/v1/chat/conversations/" + cid + "/messages")
                .header(Headers.PUBLIC_ID, ALICE).contentType(MediaType.APPLICATION_JSON).content(req)));
        assertEquals(sent.get("id").asText(), again.get("id").asText());

        // client cũ: gửi text như trước, đọc lịch sử chỉ cần text
        mvc.perform(post("/api/v1/chat/conversations/" + cid + "/messages").header(Headers.PUBLIC_ID, BOB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"xin chào\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.glyphIds").doesNotExist());
        mvc.perform(get("/api/v1/chat/conversations/" + cid + "/messages").header(Headers.PUBLIC_ID, BOB))
                .andExpect(jsonPath("$[0].text").value("xin chào"))
                .andExpect(jsonPath("$[1].text").value("Ok · Ok"))
                .andExpect(jsonPath("$[1].glyphIds.length()").value(2));
    }

    // ------------------------------------------------------------------ WebSocket

    private WebSocketSession session(String id, String publicId, List<String> rx) throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("publicId", publicId);
        when(s.getAttributes()).thenReturn(attrs);
        doAnswer(inv -> rx.add(inv.<TextMessage>getArgument(0).getPayload())).when(s).sendMessage(any(TextMessage.class));
        return s;
    }

    /**
     * Mô phỏng hai instance chat-service: mỗi instance có registry riêng, bus phát tới mọi instance như Redis
     * PSUBSCRIBE. Alice nối instance 1, Bob nối instance 2. (Redis thật chưa được chạy trong test này.)
     */
    @Test
    void glyphSendAckErrorsAndDictionaryChangedAcrossInstances() throws Exception {
        SessionRegistry node1 = new SessionRegistry(new InMemoryKeyValueStore());
        SessionRegistry node2 = new SessionRegistry(new InMemoryKeyValueStore());
        f.bus.subscribe(node1::deliver);
        f.bus.subscribe(node2::deliver);
        var ws1 = new ChatWebSocketHandler(node1, f.chat);
        var ws2 = new ChatWebSocketHandler(node2, f.chat);
        List<String> aliceRx = new ArrayList<>(), bobRx = new ArrayList<>();
        WebSocketSession a = session("s1", ALICE, aliceRx), b = session("s2", BOB, bobRx);
        ws1.afterConnectionEstablished(a);
        ws2.afterConnectionEstablished(b);

        // tạo glyph qua REST -> Bob (instance khác) nhận dictionary_changed
        String id = read(postGlyph(ALICE, cid, body(UUID.randomUUID().toString(), "Ok", zigzag(1)))).get("id").asText();
        JsonNode changed = bobRx.stream().map(this::tree).filter(n -> "dictionary_changed".equals(n.get("type").asText()))
                .findFirst().orElseThrow();
        assertEquals(cid, changed.get("data").get("conversationId").asText());
        assertEquals(id, changed.get("data").get("glyphId").asText());
        assertEquals("UPSERT", changed.get("data").get("change").asText());

        ws1.handleMessage(a, new TextMessage("{\"type\":\"send\",\"conversationId\":\"" + cid
                + "\",\"messageType\":\"GLYPH\",\"glyphIds\":[\"" + id + "\"],\"clientId\":\"u-1\"}"));
        JsonNode ack = aliceRx.stream().map(this::tree).filter(n -> "ack".equals(n.get("type").asText())).findFirst().orElseThrow();
        assertEquals("u-1", ack.get("data").get("clientId").asText());
        assertTrue(ack.get("data").has("messageId"));
        JsonNode msg = bobRx.stream().map(this::tree).filter(n -> "message".equals(n.get("type").asText())).findFirst().orElseThrow();
        assertEquals("GLYPH", msg.get("data").get("type").asText());
        assertEquals("Ok", msg.get("data").get("text").asText());

        // lỗi trả về khung error kèm clientId
        ws1.handleMessage(a, new TextMessage("{\"type\":\"send\",\"conversationId\":\"" + cid
                + "\",\"messageType\":\"GLYPH\",\"glyphIds\":\"not-an-array\",\"clientId\":\"u-2\"}"));
        ws1.handleMessage(a, new TextMessage("{\"type\":\"send\",\"conversationId\":\"" + cid
                + "\",\"messageType\":\"GLYPH\",\"glyphIds\":[123],\"clientId\":\"u-3\"}"));
        ws1.handleMessage(a, new TextMessage("{\"type\":\"send\",\"conversationId\":\"" + cid
                + "\",\"messageType\":\"GLYPH\",\"glyphIds\":[\"missing\"],\"clientId\":\"u-4\"}"));
        List<JsonNode> errors = aliceRx.stream().map(this::tree).filter(n -> "error".equals(n.get("type").asText())).toList();
        assertEquals(List.of("INVALID_GLYPH_MESSAGE", "INVALID_GLYPH_MESSAGE", "GLYPH_NOT_FOUND"),
                errors.stream().map(n -> n.get("data").get("code").asText()).toList());
        assertEquals("u-4", errors.get(2).get("data").get("clientId").asText());

        // archive -> cả hai nhận ARCHIVE
        mvc.perform(delete("/api/v1/chat/conversations/" + cid + "/glyphs/" + id).header(Headers.PUBLIC_ID, ALICE))
                .andExpect(status().isNoContent());
        assertTrue(bobRx.stream().map(this::tree).anyMatch(n -> "dictionary_changed".equals(n.get("type").asText())
                && "ARCHIVE".equals(n.get("data").get("change").asText())));
        assertTrue(aliceRx.stream().map(this::tree).anyMatch(n -> "dictionary_changed".equals(n.get("type").asText())
                && "ARCHIVE".equals(n.get("data").get("change").asText())));

        // client cũ gửi text không có messageType vẫn hoạt động
        ws2.handleMessage(b, new TextMessage("{\"type\":\"send\",\"conversationId\":\"" + cid + "\",\"text\":\"hi\",\"clientId\":\"old-1\"}"));
        assertTrue(aliceRx.stream().map(this::tree).anyMatch(n -> "message".equals(n.get("type").asText())
                && "TEXT".equals(n.get("data").get("type").asText())));
    }

    private JsonNode tree(String s) {
        try {
            return json.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
