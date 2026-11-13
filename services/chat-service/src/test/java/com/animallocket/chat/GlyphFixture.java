package com.animallocket.chat;

import com.animallocket.chat.bus.MessageBus;
import com.animallocket.chat.client.Gateways;
import com.animallocket.chat.domain.Conversation;
import com.animallocket.chat.domain.ConversationGlyph;
import com.animallocket.chat.domain.Message;
import com.animallocket.chat.repo.ConversationRepository;
import com.animallocket.chat.repo.GlyphRepository;
import com.animallocket.chat.repo.MessageRepository;
import com.animallocket.chat.service.ChatService;
import com.animallocket.chat.service.GlyphService;
import com.animallocket.chat.service.GlyphSettings;
import com.animallocket.common.kv.InMemoryKeyValueStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Môi trường chat + glyph trong bộ nhớ (không cần MongoDB/Docker). Kho glyph và tin nhắn giả lập các index unique
 * của Mongo (ném {@link DuplicateKeyException}) và trả bản sao để code thật không sửa trực tiếp "dữ liệu đã lưu".
 */
class GlyphFixture {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";
    static final String CAROL = "cccccccccccccccccccc";
    static final String PINK = "#F472B6";

    final Map<String, Conversation> convStore = new LinkedHashMap<>();
    final List<Message> msgStore = new CopyOnWriteArrayList<>();
    final Map<String, ConversationGlyph> glyphStore = new LinkedHashMap<>();
    final AtomicLong ids = new AtomicLong();
    final Map<String, Gateways.Relationship> relations = new HashMap<>();
    final InMemoryKeyValueStore kv = new InMemoryKeyValueStore();
    final List<String[]> frames = new CopyOnWriteArrayList<>(); // {recipient, json}
    /** Chạy một lần ngay trước lần insert glyph kế tiếp: dùng để chen một người ghi khác vào giữa. */
    volatile Runnable beforeNextInsert;

    final ConversationRepository convRepo = mock(ConversationRepository.class);
    final MessageRepository msgRepo = mock(MessageRepository.class);
    final GlyphRepository glyphRepo = mock(GlyphRepository.class);
    MessageBus bus;
    ChatService chat;
    GlyphService glyphs;

    GlyphFixture() {
        this(GlyphSettings.defaults());
    }

    GlyphFixture(GlyphSettings settings) {
        bus = new MessageBus() {
            private final List<BiConsumer<String, String>> subs = new CopyOnWriteArrayList<>();

            @Override
            public void publish(String publicId, String json) {
                frames.add(new String[]{publicId, json});
                subs.forEach(s -> s.accept(publicId, json));
            }

            @Override
            public void subscribe(BiConsumer<String, String> deliverLocally) {
                subs.add(deliverLocally); // giống Redis PSUBSCRIBE: mọi instance đều nhận
            }
        };
        wireConversations();
        wireMessages();
        wireGlyphs();
        relations.put(ALICE + ">" + BOB, new Gateways.Relationship(true, false));
        relations.put(BOB + ">" + ALICE, new Gateways.Relationship(true, false));
        chat = new ChatService(convRepo, msgRepo, glyphRepo, settings,
                (a, b) -> relations.getOrDefault(a + ">" + b, new Gateways.Relationship(false, false)),
                id -> null, (c, f, t) -> { }, bus, kv, 1000);
        glyphs = new GlyphService(chat, glyphRepo, settings, kv, bus);
    }

    String openAliceBob() {
        return chat.open(ALICE, BOB).id();
    }

    static List<Number> square(int size) {
        List<Number> pts = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int side = i / 16, t = (i % 16) * size / 16;
            int x = switch (side) { case 0 -> t; case 1 -> size; case 2 -> size - t; default -> 0; };
            int y = switch (side) { case 0 -> 0; case 1 -> t; case 2 -> size; default -> size - t; };
            pts.add(x);
            pts.add(y);
        }
        return pts;
    }

    /** Một nét khác hẳn mỗi giá trị {@code seed} (đường zigzag). */
    static List<Number> zigzag(int seed) {
        List<Number> pts = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            pts.add(i * 10 - 320);
            pts.add(((i * (seed + 2)) % 7) * 50 - 150 + seed);
        }
        return pts;
    }

    static GlyphService.CreateCommand cmd(String meaning, List<Number> points) {
        return new GlyphService.CreateCommand(UUID.randomUUID().toString(), meaning, PINK, points, 64);
    }

    void block(String a, String b) {
        relations.put(a + ">" + b, new Gateways.Relationship(true, true));
        relations.put(b + ">" + a, new Gateways.Relationship(true, true));
        chat.onBlocked(a, b);
    }

    // ------------------------------------------------------------------ kho giả

    private void wireConversations() {
        when(convRepo.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("c" + ids.incrementAndGet());
            }
            convStore.put(c.getId(), c);
            return c;
        });
        when(convRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(convStore.get(inv.<String>getArgument(0))));
        when(convRepo.findByKey(anyString())).thenAnswer(inv -> convStore.values().stream()
                .filter(c -> c.getKey().equals(inv.getArgument(0))).findFirst());
        when(convRepo.findByParticipantsOrderByLastMessageAtDesc(anyString())).thenAnswer(inv -> convStore.values().stream()
                .filter(c -> c.getParticipants().contains(inv.<String>getArgument(0))).toList());
        doAnswer(inv -> convStore.remove(inv.<Conversation>getArgument(0).getId())).when(convRepo).delete(any(Conversation.class));
    }

    private void wireMessages() {
        when(msgRepo.save(any(Message.class))).thenAnswer(inv -> {
            synchronized (msgStore) {
                Message m = inv.getArgument(0);
                if (m.getClientId() != null && msgStore.stream().anyMatch(x ->
                        x.getSenderId().equals(m.getSenderId()) && m.getClientId().equals(x.getClientId()))) {
                    throw new DuplicateKeyException("sender_client");
                }
                ReflectionTestUtils.setField(m, "id", "m" + ids.incrementAndGet());
                msgStore.add(m);
                return m;
            }
        });
        when(msgRepo.findBySenderIdAndClientId(anyString(), anyString())).thenAnswer(inv -> msgStore.stream()
                .filter(m -> m.getSenderId().equals(inv.getArgument(0)) && inv.getArgument(1).equals(m.getClientId()))
                .findFirst());
        when(msgRepo.findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(anyString(), any(Instant.class), any(Pageable.class)))
                .thenAnswer(inv -> msgStore.stream()
                        .filter(m -> m.getConversationId().equals(inv.getArgument(0)) && m.getCreatedAt().isBefore(inv.getArgument(1)))
                        .sorted((x, y) -> y.getCreatedAt().compareTo(x.getCreatedAt()))
                        .limit(inv.<Pageable>getArgument(2).getPageSize()).toList());
        doAnswer(inv -> msgStore.removeIf(m -> m.getConversationId().equals(inv.getArgument(0))))
                .when(msgRepo).deleteByConversationId(anyString());
    }

    private void wireGlyphs() {
        when(glyphRepo.insert(any(ConversationGlyph.class))).thenAnswer(inv -> {
            Runnable hook = beforeNextInsert;
            if (hook != null) {
                beforeNextInsert = null;
                hook.run();
            }
            ConversationGlyph g = inv.getArgument(0);
            synchronized (glyphStore) {
                if (g.getId() == null) {
                    g.setId("g" + ids.incrementAndGet());
                }
                checkUnique(g);
                glyphStore.put(g.getId(), copy(g));
                return g;
            }
        });
        when(glyphRepo.save(any(ConversationGlyph.class))).thenAnswer(inv -> {
            ConversationGlyph g = inv.getArgument(0);
            synchronized (glyphStore) {
                checkUnique(g);
                glyphStore.put(g.getId(), copy(g));
                return g;
            }
        });
        when(glyphRepo.findById(anyString())).thenAnswer(inv -> {
            synchronized (glyphStore) {
                return Optional.ofNullable(glyphStore.get(inv.<String>getArgument(0))).map(GlyphFixture::copy);
            }
        });
        when(glyphRepo.findFirstByConversationIdOrderByRevisionDesc(anyString())).thenAnswer(inv ->
                snapshot(inv.getArgument(0)).stream().max(Comparator.comparingLong(ConversationGlyph::getRevision)));
        when(glyphRepo.findByConversationIdAndRevisionGreaterThanAndRevisionLessThanEqualOrderByRevisionAsc(
                anyString(), anyLong(), anyLong(), any(Pageable.class))).thenAnswer(inv -> snapshot(inv.getArgument(0)).stream()
                .filter(g -> g.getRevision() > inv.<Long>getArgument(1) && g.getRevision() <= inv.<Long>getArgument(2))
                .sorted(Comparator.comparingLong(ConversationGlyph::getRevision))
                .limit(inv.<Pageable>getArgument(3).getPageSize()).toList());
        when(glyphRepo.findByConversationIdAndCreatorPublicIdAndClientMutationId(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> snapshot(inv.getArgument(0)).stream()
                        .filter(g -> g.getCreatorPublicId().equals(inv.getArgument(1))
                                && g.getClientMutationId().equals(inv.getArgument(2))).findFirst());
        when(glyphRepo.existsByConversationIdAndShapeHashAndStatus(anyString(), anyString(), any())).thenAnswer(inv ->
                snapshot(inv.getArgument(0)).stream().anyMatch(g -> g.getShapeHash().equals(inv.getArgument(1))
                        && g.getStatus() == inv.getArgument(2)));
        when(glyphRepo.countByConversationIdAndStatus(anyString(), any())).thenAnswer(inv ->
                snapshot(inv.getArgument(0)).stream().filter(g -> g.getStatus() == inv.getArgument(1)).count());
        when(glyphRepo.findByConversationIdAndIdIn(anyString(), anyCollection())).thenAnswer(inv ->
                snapshot(inv.getArgument(0)).stream().filter(g -> inv.<Collection<String>>getArgument(1).contains(g.getId())).toList());
        doAnswer(inv -> {
            synchronized (glyphStore) {
                return glyphStore.values().removeIf(g -> g.getConversationId().equals(inv.getArgument(0)));
            }
        }).when(glyphRepo).deleteByConversationId(anyString());
    }

    /** Ba index unique của {@code conversation_glyphs}. */
    private void checkUnique(ConversationGlyph g) {
        for (ConversationGlyph o : glyphStore.values()) {
            if (o.getId().equals(g.getId()) || !o.getConversationId().equals(g.getConversationId())) {
                continue;
            }
            boolean sameMutation = o.getCreatorPublicId().equals(g.getCreatorPublicId())
                    && o.getClientMutationId().equals(g.getClientMutationId());
            boolean sameRevision = o.getRevision() == g.getRevision();
            boolean sameActiveShape = o.getStatus() == ConversationGlyph.Status.ACTIVE
                    && g.getStatus() == ConversationGlyph.Status.ACTIVE && o.getShapeHash().equals(g.getShapeHash());
            if (sameMutation || sameRevision || sameActiveShape) {
                throw new DuplicateKeyException("E11000 duplicate key");
            }
        }
    }

    private List<ConversationGlyph> snapshot(String conversationId) {
        synchronized (glyphStore) {
            return glyphStore.values().stream().filter(g -> g.getConversationId().equals(conversationId))
                    .map(GlyphFixture::copy).toList();
        }
    }

    static ConversationGlyph copy(ConversationGlyph g) {
        ConversationGlyph c = new ConversationGlyph();
        c.setId(g.getId());
        c.setConversationId(g.getConversationId());
        c.setCreatorPublicId(g.getCreatorPublicId());
        c.setClientMutationId(g.getClientMutationId());
        c.setMeaning(g.getMeaning());
        c.setColor(g.getColor());
        c.setNormalizedPoints(g.getNormalizedPoints());
        c.setShapeHash(g.getShapeHash());
        c.setStatus(g.getStatus());
        c.setRevision(g.getRevision());
        c.setCreatedAt(g.getCreatedAt());
        c.setArchivedAt(g.getArchivedAt());
        return c;
    }
}
