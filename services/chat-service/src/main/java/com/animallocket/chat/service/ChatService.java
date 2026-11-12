package com.animallocket.chat.service;

import com.animallocket.chat.bus.MessageBus;
import com.animallocket.chat.client.Gateways;
import com.animallocket.chat.domain.Conversation;
import com.animallocket.chat.domain.ConversationGlyph;
import com.animallocket.chat.domain.Message;
import com.animallocket.chat.repo.ConversationRepository;
import com.animallocket.chat.repo.GlyphRepository;
import com.animallocket.chat.repo.MessageRepository;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.kv.KeyValueStore;
import com.animallocket.common.web.ApiException;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatService {
    public static final int MAX_TEXT = 2000;
    public static final int MAX_GLYPHS = 12;
    public static final int MAX_CLIENT_ID = 64;
    static final String GLYPH_FALLBACK_SEPARATOR = " · ";
    private static final Duration REL_CACHE = Duration.ofSeconds(30);

    /** {@code clientId} (tuỳ chọn) chống gửi trùng khi client thử lại: cùng người gửi + clientId trả lại tin cũ. */
    public record SendCommand(Message.Type type, String text, String mediaId, List<String> glyphIds, String clientId) {
        public SendCommand(Message.Type type, String text, String mediaId) {
            this(type, text, mediaId, null, null);
        }
    }

    /** Trường mới (glyphIds, dictionaryRevision, clientId) bị bỏ khi null nên contract TEXT/IMAGE giữ nguyên. */
    public record MessageView(String id, String conversationId, String senderId, Message.Type type, String text,
                              String mediaId, Instant createdAt,
                              @JsonInclude(JsonInclude.Include.NON_NULL) List<String> glyphIds,
                              @JsonInclude(JsonInclude.Include.NON_NULL) Long dictionaryRevision,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String clientId) {
        static MessageView of(Message m) {
            return new MessageView(m.getId(), m.getConversationId(), m.getSenderId(), m.getType(), m.getText(),
                    m.getMediaId(), m.getCreatedAt(), m.getGlyphIds(), m.getDictionaryRevision(), m.getClientId());
        }
    }

    public record ConversationView(String id, String withPublicId, Conversation.Status status, long unread,
                                   Instant lastMessageAt) {
    }

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final GlyphRepository glyphs;
    private final GlyphSettings glyphSettings;
    private final Gateways.UserGateway users;
    private final Gateways.MediaGateway media;
    private final Gateways.EventPublisher events;
    private final MessageBus bus;
    private final KeyValueStore kv;
    private final int maxPerWindow;
    /** Loại tin được GỬI MỚI. Mặc định chỉ GLYPH: chat chỉ bằng ký hiệu; lịch sử TEXT/IMAGE vẫn đọc được. */
    private final Set<Message.Type> allowedTypes;

    /** Dùng trong test dựng tay: cho phép mọi loại tin. */
    public ChatService(ConversationRepository conversations, MessageRepository messages, GlyphRepository glyphs,
                       GlyphSettings glyphSettings, Gateways.UserGateway users,
                       Gateways.MediaGateway media, Gateways.EventPublisher events, MessageBus bus, KeyValueStore kv,
                       int maxPerWindow) {
        this(conversations, messages, glyphs, glyphSettings, users, media, events, bus, kv, maxPerWindow,
                "TEXT,IMAGE,GLYPH");
    }

    @Autowired
    public ChatService(ConversationRepository conversations, MessageRepository messages, GlyphRepository glyphs,
                       GlyphSettings glyphSettings, Gateways.UserGateway users,
                       Gateways.MediaGateway media, Gateways.EventPublisher events, MessageBus bus, KeyValueStore kv,
                       @Value("${animal.chat.max-messages-per-10s:20}") int maxPerWindow,
                       @Value("${animal.chat.allowed-types:GLYPH}") String allowedTypes) {
        this.allowedTypes = parseTypes(allowedTypes);
        this.conversations = conversations;
        this.messages = messages;
        this.glyphs = glyphs;
        this.glyphSettings = glyphSettings;
        this.users = users;
        this.media = media;
        this.events = events;
        this.bus = bus;
        this.kv = kv;
        this.maxPerWindow = maxPerWindow;
    }

    public static Set<Message.Type> parseTypes(String csv) {
        EnumSet<Message.Type> set = EnumSet.noneOf(Message.Type.class);
        for (String t : (csv == null ? "" : csv).split(",")) {
            if (!t.isBlank()) {
                set.add(Message.Type.valueOf(t.strip().toUpperCase(java.util.Locale.ROOT)));
            }
        }
        return Collections.unmodifiableSet(set);
    }

    /** Mở (hoặc lấy lại) hội thoại với một người bạn. Người lạ chưa kết bạn không thể chat. */
    public ConversationView open(String me, String other) {
        if (me.equals(other)) {
            throw ApiException.badRequest("SELF_CHAT", "Không thể nhắn cho chính mình");
        }
        requireFriends(me, other);
        String key = Conversation.keyOf(me, other);
        Conversation c = conversations.findByKey(key).orElse(null);
        if (c == null) {
            c = new Conversation();
            c.setKey(key);
            c.setParticipants(List.of(me.compareTo(other) < 0 ? me : other, me.compareTo(other) < 0 ? other : me));
            try {
                c = conversations.save(c);
            } catch (DuplicateKeyException e) {
                c = conversations.findByKey(key).orElseThrow();
            }
        } else if (c.getStatus() == Conversation.Status.CLOSED) {
            // Hội thoại từng bị đóng (do chặn): chỉ mở lại khi đã là bạn và không còn chặn (requireFriends đã kiểm tra).
            c.setStatus(Conversation.Status.ACTIVE);
            c.setClosedReason(null);
            c = conversations.save(c);
        }
        return view(me, c);
    }

    public List<ConversationView> mine(String me) {
        return conversations.findByParticipantsOrderByLastMessageAtDesc(me).stream()
                .filter(c -> c.getStatus() == Conversation.Status.ACTIVE).map(c -> view(me, c)).toList();
    }

    public MessageView send(String me, String conversationId, SendCommand cmd) {
        Message.Type type = cmd.type() == null ? Message.Type.TEXT : cmd.type();
        if (!allowedTypes.contains(type)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "MESSAGE_TYPE_DISABLED",
                    "Chỉ được nhắn bằng ký hiệu");
        }
        if (type == Message.Type.GLYPH && !glyphSettings.writeEnabled()) {
            throw GlyphErrors.disabled();
        }
        Conversation c = writable(me, conversationId);
        String other = c.other(me);

        String clientId = cmd.clientId() == null || cmd.clientId().isBlank() ? null : cmd.clientId().strip();
        if (clientId != null && clientId.length() > MAX_CLIENT_ID) {
            throw ApiException.badRequest("INVALID_MESSAGE", "clientId quá dài");
        }
        if (clientId != null) {
            var replay = messages.findBySenderIdAndClientId(me, clientId);
            if (replay.isPresent()) {
                return MessageView.of(replay.get()); // thử lại: không lưu và không phát lại
            }
        }

        long window = Instant.now().getEpochSecond() / 10;
        if (kv.increment("chat:rl:" + me + ":" + window, Duration.ofSeconds(30)) > maxPerWindow) {
            throw ApiException.tooMany("CHAT_RATE_LIMIT", "Bạn nhắn quá nhanh, hãy chậm lại một chút");
        }

        Message m = switch (type) {
            case TEXT -> {
                String text = cmd.text() == null ? "" : cmd.text().strip();
                if (text.isEmpty() || text.length() > MAX_TEXT) {
                    throw ApiException.badRequest("INVALID_MESSAGE", "Tin nhắn dài 1–" + MAX_TEXT + " ký tự");
                }
                yield new Message(conversationId, me, Message.Type.TEXT, text, null);
            }
            case IMAGE -> {
                if (cmd.mediaId() == null || cmd.mediaId().isBlank()) {
                    throw ApiException.badRequest("INVALID_MESSAGE", "Thiếu mediaId");
                }
                Gateways.MediaInfo info = media.media(cmd.mediaId());
                if (info == null || !me.equals(info.ownerPublicId()) || !"CHAT".equals(info.context())) {
                    throw ApiException.badRequest("INVALID_MEDIA", "Ảnh không hợp lệ");
                }
                if (!"READY".equals(info.status())) {
                    throw ApiException.conflict("MEDIA_NOT_READY", "Ảnh đang được xử lý, hãy thử lại sau giây lát");
                }
                yield new Message(conversationId, me, Message.Type.IMAGE, null, cmd.mediaId());
            }
            case GLYPH -> glyphMessage(conversationId, me, cmd);
        };
        m.setClientId(clientId);
        try {
            m = messages.save(m);
        } catch (DuplicateKeyException e) {
            // Hai lần thử lại đến đồng thời: lần ghi sau thua index (senderId, clientId).
            if (clientId != null) {
                var replay = messages.findBySenderIdAndClientId(me, clientId);
                if (replay.isPresent()) {
                    return MessageView.of(replay.get());
                }
            }
            throw e;
        }
        c.setLastMessageAt(m.getCreatedAt());
        // Người gửi mặc định đã đọc tới tin của chính mình.
        c.getLastReadAt().put(me, m.getCreatedAt());
        conversations.save(c);

        MessageView v = MessageView.of(m);
        String frame = frame("message", v);
        bus.publish(other, frame);
        bus.publish(me, frame); // các thiết bị khác của người gửi
        // Người nhận đang mở app (có kết nối chat) đã nhận tin qua WebSocket nên không gửi push thừa.
        if (kv.get("chat:conn:" + other).isEmpty()) {
            events.messageSent(conversationId, me, other);
        }
        return v;
    }

    public List<MessageView> history(String me, String conversationId, Instant before, int limit) {
        participant(me, conversationId);
        int size = Math.max(1, Math.min(limit, 100));
        return messages.findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                conversationId, before == null ? Instant.now().plusSeconds(60) : before, PageRequest.of(0, size))
                .stream().map(MessageView::of).toList();
    }

    public void markRead(String me, String conversationId) {
        Conversation c = participant(me, conversationId);
        // Mốc đã đọc = thời điểm của tin mới nhất thực sự tồn tại (không dùng "bây giờ") để không rơi tin đến
        // cùng mili-giây; MongoDB chỉ lưu thời gian ở độ phân giải mili-giây.
        Instant newest = messages.findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        conversationId, Instant.now().plusSeconds(60), PageRequest.of(0, 1))
                .stream().findFirst().map(Message::getCreatedAt).orElse(Instant.now());
        c.getLastReadAt().put(me, newest);
        conversations.save(c);
        bus.publish(c.other(me), frame("read", Map.of("conversationId", conversationId, "by", me)));
    }

    // ---------- sự kiện từ user-service ----------

    /** Chặn: đóng hội thoại và báo cả hai đầu ngay. */
    public void onBlocked(String a, String b) {
        kv.delete(relKey(a, b), relKey(b, a));
        conversations.findByKey(Conversation.keyOf(a, b)).ifPresent(c -> {
            c.setStatus(Conversation.Status.CLOSED);
            c.setClosedReason("BLOCKED");
            conversations.save(c);
            String f = frame("closed", Map.of("conversationId", c.getId(), "reason", "BLOCKED"));
            bus.publish(a, f);
            bus.publish(b, f);
        });
    }

    /** Huỷ kết bạn: đóng hội thoại và báo hai đầu. Kết bạn lại thì {@link #open} mở lại hội thoại cũ. */
    public void onUnfriended(String a, String b) {
        kv.delete(relKey(a, b), relKey(b, a));
        conversations.findByKey(Conversation.keyOf(a, b)).ifPresent(c -> {
            if (c.getStatus() == Conversation.Status.ACTIVE) {
                c.setStatus(Conversation.Status.CLOSED);
                c.setClosedReason("UNFRIENDED");
                conversations.save(c);
                String f = frame("closed", Map.of("conversationId", c.getId(), "reason", "UNFRIENDED"));
                bus.publish(a, f);
                bus.publish(b, f);
            }
        });
    }

    public void onFriendshipChanged(String a, String b) {
        kv.delete(relKey(a, b), relKey(b, a));
    }

    /** Xoá tài khoản: xoá mọi hội thoại và tin nhắn có người đó. */
    public void onUserDeleted(String publicId) {
        for (Conversation c : conversations.findByParticipantsOrderByLastMessageAtDesc(publicId)) {
            messages.deleteByConversationId(c.getId());
            glyphs.deleteByConversationId(c.getId());
            conversations.delete(c);
            bus.publish(c.other(publicId), frame("closed", Map.of("conversationId", c.getId(), "reason", "DELETED")));
        }
    }

    // ---------- nội bộ ----------

    /**
     * Tin GLYPH: mọi glyph phải thuộc hội thoại và đang ACTIVE; giữ nguyên thứ tự (cho phép lặp).
     * Client không được gửi text/mediaId: fallback do server tạo từ nghĩa của glyph.
     */
    private Message glyphMessage(String conversationId, String me, SendCommand cmd) {
        List<String> ids = cmd.glyphIds();
        if (ids == null || ids.isEmpty() || cmd.text() != null || cmd.mediaId() != null
                || ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 64)) {
            throw GlyphErrors.invalidMessage();
        }
        if (ids.size() > MAX_GLYPHS) {
            throw GlyphErrors.sequenceTooLong();
        }
        long revision = glyphRevision(conversationId);
        Map<String, ConversationGlyph> found = glyphs.findByConversationIdAndIdIn(conversationId, new HashSet<>(ids))
                .stream().collect(Collectors.toMap(ConversationGlyph::getId, Function.identity()));
        StringBuilder fallback = new StringBuilder();
        for (String id : ids) {
            ConversationGlyph g = found.get(id);
            if (g == null) {
                throw GlyphErrors.notFound();
            }
            if (g.getStatus() != ConversationGlyph.Status.ACTIVE) {
                throw GlyphErrors.notActive();
            }
            if (!fallback.isEmpty()) {
                fallback.append(GLYPH_FALLBACK_SEPARATOR);
            }
            fallback.append(g.getMeaning());
        }
        return Message.glyph(conversationId, me, ids, fallback.toString(), revision);
    }

    /** Revision từ điển hiện tại của hội thoại = revision lớn nhất đã ghi (0 nếu chưa có glyph). */
    long glyphRevision(String conversationId) {
        return glyphs.findFirstByConversationIdOrderByRevisionDesc(conversationId)
                .map(ConversationGlyph::getRevision).orElse(0L);
    }

    /** Người gọi là thành viên, hội thoại còn mở và hai bên vẫn là bạn (không chặn). */
    Conversation writable(String me, String conversationId) {
        Conversation c = participant(me, conversationId);
        if (c.getStatus() != Conversation.Status.ACTIVE) {
            throw ApiException.forbidden("Hội thoại đã bị đóng");
        }
        requireFriends(me, c.other(me));
        return c;
    }

    Conversation participant(String me, String id) {
        Conversation c = conversations.findById(id).orElseThrow(() -> ApiException.notFound("Hội thoại"));
        if (!c.has(me)) {
            throw ApiException.notFound("Hội thoại");
        }
        return c;
    }

    private void requireFriends(String me, String other) {
        String key = relKey(me, other);
        String cached = kv.get(key).orElse(null);
        if (cached == null) {
            Gateways.Relationship r = users.relationship(me, other);
            cached = r.friends() && !r.blocked() ? "ok" : "no";
            kv.set(key, cached, REL_CACHE);
        }
        if (!"ok".equals(cached)) {
            throw ApiException.forbidden("Chỉ nhắn tin được với bạn bè");
        }
    }

    private ConversationView view(String me, Conversation c) {
        Instant read = c.getLastReadAt().getOrDefault(me, Instant.EPOCH);
        long unread = messages.countByConversationIdAndSenderIdNotAndCreatedAtGreaterThan(c.getId(), me, read);
        return new ConversationView(c.getId(), c.other(me), c.getStatus(), unread, c.getLastMessageAt());
    }

    private static String relKey(String a, String b) {
        return "chat:rel:" + a + ":" + b;
    }

    public static String frame(String type, Object data) {
        try {
            return EventJson.mapper().writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
