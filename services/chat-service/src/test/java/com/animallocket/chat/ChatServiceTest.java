package com.animallocket.chat;

import com.animallocket.chat.bus.LocalMessageBus;
import com.animallocket.chat.client.Gateways;
import com.animallocket.chat.domain.Conversation;
import com.animallocket.chat.domain.Message;
import com.animallocket.chat.repo.ConversationRepository;
import com.animallocket.chat.repo.GlyphRepository;
import com.animallocket.chat.repo.MessageRepository;
import com.animallocket.chat.service.ChatService;
import com.animallocket.chat.service.GlyphSettings;
import com.animallocket.chat.ws.ChatWebSocketHandler;
import com.animallocket.chat.ws.PublicIdHandshakeInterceptor;
import com.animallocket.chat.ws.SessionRegistry;
import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm thử logic chat với repository giả lập trong bộ nhớ (không cần MongoDB hay Docker). */
class ChatServiceTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";
    static final String CAROL = "cccccccccccccccccccc";

    final Map<String, Conversation> convStore = new LinkedHashMap<>();
    final List<Message> msgStore = new ArrayList<>();
    final AtomicLong ids = new AtomicLong();
    final ConversationRepository convRepo = mock(ConversationRepository.class);
    final MessageRepository msgRepo = mock(MessageRepository.class);
    final LocalMessageBus bus = new LocalMessageBus();
    final List<String[]> frames = new ArrayList<>(); // {recipient, json}
    final Map<String, Gateways.Relationship> relations = new HashMap<>();
    final AtomicInteger relationCalls = new AtomicInteger();
    final Map<String, Gateways.MediaInfo> mediaMap = new HashMap<>();
    final List<String[]> pushes = new ArrayList<>();
    final ObjectMapper json = new ObjectMapper();
    final InMemoryKeyValueStore kv = new InMemoryKeyValueStore();
    ChatService chat;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(convRepo.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("c" + ids.incrementAndGet());
            }
            convStore.put(c.getId(), c);
            return c;
        });
        when(convRepo.findById(anyString())).thenAnswer(inv -> java.util.Optional.ofNullable(convStore.get(inv.<String>getArgument(0))));
        when(convRepo.findByKey(anyString())).thenAnswer(inv -> convStore.values().stream()
                .filter(c -> c.getKey().equals(inv.getArgument(0))).findFirst());
        when(convRepo.findByParticipantsOrderByLastMessageAtDesc(anyString())).thenAnswer(inv -> convStore.values().stream()
                .filter(c -> c.getParticipants().contains(inv.<String>getArgument(0))).toList());
        org.mockito.Mockito.doAnswer(inv -> {
            convStore.remove(inv.<Conversation>getArgument(0).getId());
            return null;
        }).when(convRepo).delete(any(Conversation.class));

        when(msgRepo.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", "m" + ids.incrementAndGet());
            msgStore.add(m);
            return m;
        });
        when(msgRepo.findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(anyString(), any(Instant.class), any(Pageable.class)))
                .thenAnswer(inv -> msgStore.stream()
                        .filter(m -> m.getConversationId().equals(inv.getArgument(0)) && m.getCreatedAt().isBefore(inv.getArgument(1)))
                        .sorted((x, y) -> y.getCreatedAt().compareTo(x.getCreatedAt()))
                        .limit(inv.<Pageable>getArgument(2).getPageSize()).toList());
        when(msgRepo.countByConversationIdAndSenderIdNotAndCreatedAtGreaterThan(anyString(), anyString(), any(Instant.class)))
                .thenAnswer(inv -> msgStore.stream().filter(m -> m.getConversationId().equals(inv.getArgument(0))
                        && !m.getSenderId().equals(inv.getArgument(1))
                        && m.getCreatedAt().isAfter(inv.getArgument(2))).count());
        org.mockito.Mockito.doAnswer(inv -> {
            msgStore.removeIf(m -> m.getConversationId().equals(inv.getArgument(0)));
            return null;
        }).when(msgRepo).deleteByConversationId(anyString());

        relations.put(rel(ALICE, BOB), new Gateways.Relationship(true, false));
        relations.put(rel(BOB, ALICE), new Gateways.Relationship(true, false));
        bus.subscribe((id, f) -> frames.add(new String[]{id, f}));
        chat = newChat(20);
    }

    private ChatService newChat(int limit) {
        return new ChatService(convRepo, msgRepo, mock(GlyphRepository.class), GlyphSettings.defaults(),
                (a, b) -> {
                    relationCalls.incrementAndGet();
                    return relations.getOrDefault(rel(a, b), new Gateways.Relationship(false, false));
                },
                mediaMap::get, (cid, from, to) -> pushes.add(new String[]{cid, from, to}), bus, kv, limit);
    }

    static String rel(String a, String b) {
        return a + ">" + b;
    }

    private String openAliceBob() {
        return chat.open(ALICE, BOB).id();
    }

    private JsonNode frame(String[] f) throws Exception {
        return json.readTree(f[1]);
    }

    // ------------------------------------------------------------------

    @Test
    void openRequiresFriendshipAndIsIdempotent() {
        ApiException e = assertThrows(ApiException.class, () -> chat.open(ALICE, CAROL));
        assertEquals(403, e.status().value());
        assertThrows(ApiException.class, () -> chat.open(ALICE, ALICE));

        String id1 = chat.open(ALICE, BOB).id();
        String id2 = chat.open(BOB, ALICE).id(); // từ phía kia vẫn là cùng một hội thoại
        assertEquals(id1, id2);
        assertEquals(1, convStore.size());
        assertEquals(List.of(ALICE, BOB), convStore.get(id1).getParticipants());
        assertEquals(BOB, chat.open(ALICE, BOB).withPublicId());
    }

    @Test
    void glyphOnlyModeRejectsTextAndImageAndKeepsHistoryReadable() {
        String cid = openAliceBob();
        chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "tin cũ", null)); // trước khi bật
        ChatService glyphOnly = new ChatService(convRepo, msgRepo, mock(GlyphRepository.class), GlyphSettings.defaults(),
                (x, y) -> relations.getOrDefault(rel(x, y), new Gateways.Relationship(false, false)),
                mediaMap::get, (c, from, to) -> pushes.add(new String[]{c, from, to}), bus, kv, 20, "GLYPH");
        for (Message.Type t : new Message.Type[]{Message.Type.TEXT, Message.Type.IMAGE, null}) {
            ApiException e = assertThrows(ApiException.class, () ->
                    glyphOnly.send(ALICE, cid, new ChatService.SendCommand(t, "xin chào", "m1")));
            assertEquals(403, e.status().value());
            assertEquals("MESSAGE_TYPE_DISABLED", e.code());
        }
        assertEquals(1, msgStore.size(), "không lưu tin bị chặn");
        assertEquals(java.util.EnumSet.of(Message.Type.GLYPH), ChatService.parseTypes(" glyph "));
        assertEquals(java.util.EnumSet.of(Message.Type.TEXT, Message.Type.GLYPH), ChatService.parseTypes("TEXT,,GLYPH"));
    }

    @Test
    void blockedPairCannotOpen() {
        relations.put(rel(ALICE, BOB), new Gateways.Relationship(true, true));
        assertThrows(ApiException.class, () -> chat.open(ALICE, BOB));
    }

    @Test
    void sendDeliversToBothSidesPersistsAndNotifies() throws Exception {
        String cid = openAliceBob();
        var sent = chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "  xin chào  ", null));

        assertEquals("xin chào", sent.text());
        assertEquals(1, msgStore.size());
        assertEquals(2, frames.size());
        assertEquals(BOB, frames.get(0)[0]);
        assertEquals(ALICE, frames.get(1)[0]); // thiết bị khác của người gửi
        assertEquals("message", frame(frames.get(0)).get("type").asText());
        assertEquals("xin chào", frame(frames.get(0)).get("data").get("text").asText());
        assertEquals(1, pushes.size());
        assertEquals(BOB, pushes.get(0)[2]);
    }

    @Test
    void noPushWhenRecipientIsConnected() {
        String cid = openAliceBob();
        kv.set("chat:conn:" + BOB, "1", java.time.Duration.ofSeconds(60)); // Bob đang mở app
        chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "hi", null));
        assertTrue(pushes.isEmpty());
        assertEquals(2, frames.size(), "vẫn giao qua WebSocket");

        kv.delete("chat:conn:" + BOB); // Bob thoát
        chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "hi again", null));
        assertEquals(1, pushes.size());
    }

    @Test
    void historyIsNewestFirstAndPaged() throws Exception {
        String cid = openAliceBob();
        for (int i = 0; i < 5; i++) {
            chat.send(i % 2 == 0 ? ALICE : BOB, cid, new ChatService.SendCommand(Message.Type.TEXT, "m" + i, null));
            Thread.sleep(3);
        }
        var page1 = chat.history(ALICE, cid, null, 3);
        assertEquals(List.of("m4", "m3", "m2"), page1.stream().map(ChatService.MessageView::text).toList());
        var page2 = chat.history(ALICE, cid, page1.get(2).createdAt(), 3);
        assertEquals(List.of("m1", "m0"), page2.stream().map(ChatService.MessageView::text).toList());
    }

    @Test
    void unreadCountsAndMarkRead() throws Exception {
        String cid = openAliceBob();
        chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "1", null));
        Thread.sleep(3);
        chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "2", null));

        assertEquals(2, chat.mine(BOB).get(0).unread());
        assertEquals(0, chat.mine(ALICE).get(0).unread()); // tin của chính mình không tính

        Thread.sleep(3);
        chat.markRead(BOB, cid);
        assertEquals(0, chat.mine(BOB).get(0).unread());
        assertEquals("read", frame(frames.get(frames.size() - 1)).get("type").asText());
    }

    @Test
    void strangersCannotReadOrWriteSomeoneElsesConversation() {
        String cid = openAliceBob();
        assertEquals(404, assertThrows(ApiException.class, () -> chat.history(CAROL, cid, null, 10)).status().value());
        assertEquals(404, assertThrows(ApiException.class,
                () -> chat.send(CAROL, cid, new ChatService.SendCommand(Message.Type.TEXT, "hi", null))).status().value());
        assertEquals(404, assertThrows(ApiException.class, () -> chat.markRead(CAROL, cid)).status().value());
        assertTrue(chat.mine(CAROL).isEmpty());
    }

    @Test
    void textValidation() {
        String cid = openAliceBob();
        for (String bad : new String[]{null, "", "   ", "x".repeat(ChatService.MAX_TEXT + 1)}) {
            ApiException e = assertThrows(ApiException.class,
                    () -> chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, bad, null)));
            assertEquals("INVALID_MESSAGE", e.code());
        }
        chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "x".repeat(ChatService.MAX_TEXT), null));
    }

    @Test
    void blockClosesConversationNotifiesBothAndStopsSending() throws Exception {
        String cid = openAliceBob();
        frames.clear();

        chat.onBlocked(ALICE, BOB);

        assertEquals(Conversation.Status.CLOSED, convStore.get(cid).getStatus());
        assertEquals("BLOCKED", convStore.get(cid).getClosedReason());
        assertEquals(2, frames.size());
        assertEquals("closed", frame(frames.get(0)).get("type").asText());
        assertEquals(List.of(ALICE, BOB), frames.stream().map(f -> f[0]).sorted().toList());
        assertEquals(403, assertThrows(ApiException.class,
                () -> chat.send(BOB, cid, new ChatService.SendCommand(Message.Type.TEXT, "hello?", null))).status().value());
        assertTrue(chat.mine(ALICE).isEmpty(), "hội thoại đã đóng không còn trong danh sách");
    }

    @Test
    void unfriendingClosesTheConversationAndNotifiesBothSides() throws Exception {
        String cid = openAliceBob();
        frames.clear();
        relations.put(rel(ALICE, BOB), new Gateways.Relationship(false, false));

        chat.onUnfriended(ALICE, BOB);

        assertEquals(Conversation.Status.CLOSED, convStore.get(cid).getStatus());
        assertEquals("UNFRIENDED", convStore.get(cid).getClosedReason());
        assertEquals(2, frames.size());
        assertEquals("UNFRIENDED", frame(frames.get(0)).get("data").get("reason").asText());
        assertEquals(403, assertThrows(ApiException.class,
                () -> chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "hello?", null))).status().value());
        chat.onUnfriended(ALICE, BOB); // lặp lại không phát thêm
        assertEquals(2, frames.size());

        relations.put(rel(ALICE, BOB), new Gateways.Relationship(true, false)); // kết bạn lại
        chat.onFriendshipChanged(ALICE, BOB);
        assertEquals(cid, chat.open(ALICE, BOB).id());
        assertEquals(Conversation.Status.ACTIVE, convStore.get(cid).getStatus());
    }

    @Test
    void conversationCanReopenAfterBlockIsLifted() {
        String cid = openAliceBob();
        chat.onBlocked(ALICE, BOB);
        relations.put(rel(ALICE, BOB), new Gateways.Relationship(false, false)); // sau chặn không còn là bạn
        assertThrows(ApiException.class, () -> chat.open(ALICE, BOB));

        relations.put(rel(ALICE, BOB), new Gateways.Relationship(true, false));   // kết bạn lại
        chat.onFriendshipChanged(ALICE, BOB);
        assertEquals(cid, chat.open(ALICE, BOB).id());
        assertEquals(Conversation.Status.ACTIVE, convStore.get(cid).getStatus());
    }

    @Test
    void friendshipLookupIsCachedBriefly() {
        String cid = openAliceBob();
        int before = relationCalls.get();
        for (int i = 0; i < 5; i++) {
            chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "hi" + i, null));
        }
        assertTrue(relationCalls.get() - before <= 1, "gọi user-service " + (relationCalls.get() - before) + " lần");
    }

    @Test
    void unfriendingStopsMessagesOnceCacheInvalidated() {
        String cid = openAliceBob();
        chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "hi", null));
        relations.put(rel(ALICE, BOB), new Gateways.Relationship(false, false));
        chat.onFriendshipChanged(ALICE, BOB);
        assertEquals(403, assertThrows(ApiException.class,
                () -> chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "again", null))).status().value());
    }

    @Test
    void sendingIsRateLimited() {
        ChatService strict = newChat(3);
        String cid = strict.open(ALICE, BOB).id();
        for (int i = 0; i < 3; i++) {
            strict.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "m" + i, null));
        }
        ApiException e = assertThrows(ApiException.class,
                () -> strict.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "spam", null)));
        assertEquals("CHAT_RATE_LIMIT", e.code());
        assertEquals(429, e.status().value());
    }

    @Test
    void imageMessagesRequireOwnReadyChatMedia() {
        String cid = openAliceBob();
        ChatService.SendCommand img = new ChatService.SendCommand(Message.Type.IMAGE, null, "media1");

        assertEquals("INVALID_MEDIA", assertThrows(ApiException.class, () -> chat.send(ALICE, cid, img)).code()); // không tồn tại

        mediaMap.put("media1", new Gateways.MediaInfo("media1", BOB, "CHAT", "READY"));                         // của người khác
        assertEquals("INVALID_MEDIA", assertThrows(ApiException.class, () -> chat.send(ALICE, cid, img)).code());

        mediaMap.put("media1", new Gateways.MediaInfo("media1", ALICE, "POST", "READY"));                       // sai ngữ cảnh
        assertEquals("INVALID_MEDIA", assertThrows(ApiException.class, () -> chat.send(ALICE, cid, img)).code());

        mediaMap.put("media1", new Gateways.MediaInfo("media1", ALICE, "CHAT", "UPLOADED"));                    // chưa ẩn mặt xong
        assertEquals("MEDIA_NOT_READY", assertThrows(ApiException.class, () -> chat.send(ALICE, cid, img)).code());
        assertTrue(msgStore.isEmpty(), "ảnh chưa xử lý xong không được lọt vào hội thoại");

        mediaMap.put("media1", new Gateways.MediaInfo("media1", ALICE, "CHAT", "READY"));
        var sent = chat.send(ALICE, cid, img);
        assertEquals("media1", sent.mediaId());
        assertEquals(Message.Type.IMAGE, sent.type());
    }

    @Test
    void deletingAccountWipesConversationsAndTellsTheOtherSide() throws Exception {
        String cid = openAliceBob();
        chat.send(ALICE, cid, new ChatService.SendCommand(Message.Type.TEXT, "bye", null));
        frames.clear();

        chat.onUserDeleted(ALICE);

        assertTrue(convStore.isEmpty());
        assertTrue(msgStore.isEmpty());
        assertEquals(BOB, frames.get(0)[0]);
        assertEquals("DELETED", frame(frames.get(0)).get("data").get("reason").asText());
    }

    // ------------------------------------------------------------------ WebSocket

    private WebSocketSession fakeSession(String id, String publicId, List<String> received) throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("publicId", publicId);
        when(s.getAttributes()).thenReturn(attrs);
        org.mockito.Mockito.doAnswer(inv -> {
            received.add(inv.<TextMessage>getArgument(0).getPayload());
            return null;
        }).when(s).sendMessage(any(TextMessage.class));
        return s;
    }

    @Test
    void webSocketEndToEndAcrossTwoClients() throws Exception {
        var registry = new SessionRegistry(new InMemoryKeyValueStore());
        var wsBus = new LocalMessageBus();
        wsBus.subscribe(registry::deliver);
        ChatService wsChat = new ChatService(convRepo, msgRepo, mock(GlyphRepository.class), GlyphSettings.defaults(),
                (a, b) -> new Gateways.Relationship(true, false),
                mediaMap::get, (c, f, t) -> { }, wsBus, new InMemoryKeyValueStore(), 20);
        var handler = new ChatWebSocketHandler(registry, wsChat);

        List<String> aliceRx = new ArrayList<>(), bobRx = new ArrayList<>();
        WebSocketSession a = fakeSession("s1", ALICE, aliceRx), b = fakeSession("s2", BOB, bobRx);
        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);
        assertEquals(2, registry.localSessionCount());
        assertTrue(registry.isConnectedAnywhere(ALICE));

        String cid = wsChat.open(ALICE, BOB).id();
        handler.handleMessage(a, new TextMessage(
                "{\"type\":\"send\",\"conversationId\":\"" + cid + "\",\"text\":\"hi bob\",\"clientId\":\"c-1\"}"));

        assertTrue(bobRx.stream().anyMatch(f -> f.contains("\"type\":\"message\"") && f.contains("hi bob")), bobRx.toString());
        assertTrue(aliceRx.stream().anyMatch(f -> f.contains("\"type\":\"ack\"") && f.contains("c-1")), aliceRx.toString());

        // lỗi nghiệp vụ trả về dạng khung "error" chứ không làm đứt kết nối
        handler.handleMessage(a, new TextMessage("{\"type\":\"send\",\"conversationId\":\"nope\",\"text\":\"x\",\"clientId\":\"c-2\"}"));
        assertTrue(aliceRx.stream().anyMatch(f -> f.contains("\"type\":\"error\"") && f.contains("NOT_FOUND") && f.contains("c-2")));
        handler.handleMessage(a, new TextMessage("not json"));
        assertTrue(aliceRx.stream().anyMatch(f -> f.contains("BAD_FRAME")));
        handler.handleMessage(a, new TextMessage("{\"type\":\"ping\"}"));
        assertTrue(aliceRx.stream().anyMatch(f -> f.contains("pong")));
        handler.handleMessage(a, new TextMessage("{\"type\":\"launch_missiles\"}"));
        assertTrue(aliceRx.stream().anyMatch(f -> f.contains("UNKNOWN_TYPE")));

        handler.afterConnectionClosed(b, org.springframework.web.socket.CloseStatus.NORMAL);
        assertEquals(1, registry.localSessionCount());
        assertFalse(registry.isConnectedAnywhere(BOB));
    }

    @Test
    void oversizedFramesCloseTheSession() throws Exception {
        var registry = new SessionRegistry(new InMemoryKeyValueStore());
        var handler = new ChatWebSocketHandler(registry, chat);
        List<String> rx = new ArrayList<>();
        WebSocketSession s = fakeSession("s1", ALICE, rx);
        handler.afterConnectionEstablished(s);
        handler.handleMessage(s, new TextMessage("x".repeat(20_000)));
        ArgumentCaptor<org.springframework.web.socket.CloseStatus> cap = ArgumentCaptor.forClass(org.springframework.web.socket.CloseStatus.class);
        verify(s, atLeastOnce()).close(cap.capture());
        assertEquals(org.springframework.web.socket.CloseStatus.TOO_BIG_TO_PROCESS.getCode(), cap.getValue().getCode());
    }

    @Test
    void handshakeRejectsConnectionsWithoutPublicId() {
        var interceptor = new PublicIdHandshakeInterceptor();

        MockHttpServletRequest noId = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.beforeHandshake(new ServletServerHttpRequest(noId), new ServletServerHttpResponse(res), null, new HashMap<>()));
        assertEquals(403, res.getStatus());

        MockHttpServletRequest ok = new MockHttpServletRequest();
        ok.addHeader("X-Public-Id", ALICE);
        Map<String, Object> attrs = new HashMap<>();
        assertTrue(interceptor.beforeHandshake(new ServletServerHttpRequest(ok), new ServletServerHttpResponse(new MockHttpServletResponse()), null, attrs));
        assertEquals(ALICE, attrs.get("publicId"));
    }
}
