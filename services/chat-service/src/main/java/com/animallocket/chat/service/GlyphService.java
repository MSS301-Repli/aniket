package com.animallocket.chat.service;

import com.animallocket.chat.bus.MessageBus;
import com.animallocket.chat.domain.Conversation;
import com.animallocket.chat.domain.ConversationGlyph;
import com.animallocket.chat.repo.GlyphRepository;
import com.animallocket.common.kv.KeyValueStore;
import com.animallocket.common.web.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Từ điển ký hiệu dùng chung theo hội thoại. Nguồn chuẩn là MongoDB; WebSocket chỉ báo "có thay đổi"
 * ({@code dictionary_changed}), client tự gọi delta sync để lấy dữ liệu.
 *
 * <p>Không log nghĩa, nét vẽ hay id glyph.
 */
@Service
public class GlyphService {
    public static final int MAX_MEANING = 64;
    public static final int MAX_SYNC_PAGE = 100;
    private static final int MAX_WRITE_ATTEMPTS = 5;
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-F]{6}$");

    public record CreateCommand(String clientMutationId, String meaning, String color, List<? extends Number> points,
                                Integer pointCount) {
    }

    public record GlyphView(String id, String conversationId, String creatorPublicId, String meaning, String color,
                            List<Integer> points, String shapeHash, ConversationGlyph.Status status, long revision,
                            Instant createdAt, Instant archivedAt) {
        static GlyphView of(ConversationGlyph g) {
            return new GlyphView(g.getId(), g.getConversationId(), g.getCreatorPublicId(), g.getMeaning(), g.getColor(),
                    g.getNormalizedPoints(), g.getShapeHash(), g.getStatus(), g.getRevision(), g.getCreatedAt(),
                    g.getArchivedAt());
        }
    }

    /** {@code created} = false khi đây là kết quả của một request lặp lại (cùng clientMutationId). */
    public record CreateResult(GlyphView glyph, boolean created) {
    }

    public record SyncPage(String conversationId, long revision, List<GlyphView> items, boolean hasMore,
                           long nextAfterRevision) {
    }

    private final ChatService chat;
    private final GlyphRepository glyphs;
    private final GlyphSettings settings;
    private final KeyValueStore kv;
    private final MessageBus bus;

    public GlyphService(ChatService chat, GlyphRepository glyphs, GlyphSettings settings, KeyValueStore kv,
                        MessageBus bus) {
        this.chat = chat;
        this.glyphs = glyphs;
        this.settings = settings;
        this.kv = kv;
        this.bus = bus;
    }

    // ---------- đọc (luôn mở, kể cả khi rollback hoặc hội thoại đã đóng) ----------

    /**
     * Trả glyph (cả ACTIVE lẫn tombstone ARCHIVED) có revision trong (afterRevision, revision hiện tại], tăng dần.
     * Chốt revision hiện tại trước khi đọc để thay đổi đến giữa chừng không bị nhảy cóc.
     */
    public SyncPage sync(String me, String conversationId, long afterRevision, int limit) {
        chat.participant(me, conversationId);
        long after = Math.max(0, afterRevision);
        int size = Math.max(1, Math.min(limit, MAX_SYNC_PAGE));
        long current = chat.glyphRevision(conversationId);
        if (after >= current) {
            return new SyncPage(conversationId, current, List.of(), false, current);
        }
        List<ConversationGlyph> rows = glyphs
                .findByConversationIdAndRevisionGreaterThanAndRevisionLessThanEqualOrderByRevisionAsc(
                        conversationId, after, current, PageRequest.of(0, size + 1));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }
        long next = hasMore ? rows.get(rows.size() - 1).getRevision() : current;
        return new SyncPage(conversationId, current, rows.stream().map(GlyphView::of).toList(), hasMore, next);
    }

    public GlyphView get(String me, String conversationId, String glyphId) {
        chat.participant(me, conversationId);
        return GlyphView.of(find(conversationId, glyphId));
    }

    // ---------- ghi ----------

    public CreateResult create(String me, String conversationId, CreateCommand cmd) {
        requireWriteEnabled();
        Conversation c = chat.writable(me, conversationId);

        String mutationId = cmd.clientMutationId() == null ? "" : cmd.clientMutationId().strip().toLowerCase(Locale.ROOT);
        if (!UUID.matcher(mutationId).matches()) {
            throw GlyphErrors.invalidMutationId();
        }
        String meaning = meaning(cmd.meaning());
        String color = color(cmd.color());
        int[] canonical = GlyphShapes.canonicalize(GlyphShapes.validate(cmd.points(), cmd.pointCount()));
        String shapeHash = GlyphShapes.hash(canonical);

        Optional<ConversationGlyph> replay = byMutation(conversationId, me, mutationId);
        if (replay.isPresent()) {
            return new CreateResult(GlyphView.of(replay.get()), false);
        }
        // Mỗi lần từ chối đều kiểm tra lại "có phải request lặp lại không": bản gốc đến đồng thời có thể vừa ghi xong
        // giữa lần kiểm tra replay ở trên và lúc này (khi đó nó chiếm đúng hình/chỗ/hạn mức của chính request này).
        if (glyphs.existsByConversationIdAndShapeHashAndStatus(conversationId, shapeHash, ConversationGlyph.Status.ACTIVE)) {
            return replayOr(conversationId, me, mutationId, GlyphErrors.duplicate());
        }
        // Đếm rồi ghi không nguyên tử: hai request đồng thời có thể vượt giới hạn tối đa 1 glyph; chấp nhận được.
        if (glyphs.countByConversationIdAndStatus(conversationId, ConversationGlyph.Status.ACTIVE) >= settings.maxActive()) {
            return replayOr(conversationId, me, mutationId, GlyphErrors.limit());
        }
        String quotaKey = "chat:glyph:rl:" + me + ":" + LocalDate.now(ZoneOffset.UTC).toEpochDay();
        if (kv.increment(quotaKey, Duration.ofDays(2)) > settings.maxCreatesPerDay()) {
            kv.decrement(quotaKey);
            return replayOr(conversationId, me, mutationId, GlyphErrors.rateLimited());
        }

        ConversationGlyph g = new ConversationGlyph();
        g.setConversationId(conversationId);
        g.setCreatorPublicId(me);
        g.setClientMutationId(mutationId);
        g.setMeaning(meaning);
        g.setColor(color);
        g.setNormalizedPoints(Arrays.stream(canonical).boxed().toList());
        g.setShapeHash(shapeHash);
        g.setStatus(ConversationGlyph.Status.ACTIVE);

        for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
            g.setRevision(chat.glyphRevision(conversationId) + 1);
            try {
                ConversationGlyph saved = glyphs.insert(g);
                publishChange(c, saved, "UPSERT");
                return new CreateResult(GlyphView.of(saved), true);
            } catch (DuplicateKeyException e) {
                // Phân biệt ba loại va chạm: request lặp lại đồng thời, trùng hình, hay chỉ trùng revision (thử lại).
                replay = byMutation(conversationId, me, mutationId);
                if (replay.isPresent()) {
                    kv.decrement(quotaKey);
                    return new CreateResult(GlyphView.of(replay.get()), false);
                }
                if (glyphs.existsByConversationIdAndShapeHashAndStatus(conversationId, shapeHash,
                        ConversationGlyph.Status.ACTIVE)) {
                    kv.decrement(quotaKey);
                    // Phải kiểm tra lại replay y như nhánh trước khi ghi: giữa byMutation ở trên và câu này, một
                    // request GIỐNG HỆT đang chạy song song có thể vừa ghi xong và chiếm đúng hình của chính nó.
                    // Throw thẳng ở đây làm mọi lần gửi lại đồng thời đều hỏng thay vì trả về bản đã ghi.
                    return replayOr(conversationId, me, mutationId, GlyphErrors.duplicate());
                }
            }
        }
        kv.decrement(quotaKey);
        throw GlyphErrors.busy();
    }

    /** Xoá mềm. Gọi lại khi đã archive vẫn thành công và không tăng revision. */
    public void archive(String me, String conversationId, String glyphId) {
        requireWriteEnabled();
        Conversation c = chat.writable(me, conversationId);
        for (int attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
            ConversationGlyph g = find(conversationId, glyphId);
            if (!me.equals(g.getCreatorPublicId())) {
                throw GlyphErrors.notOwner();
            }
            if (g.getStatus() == ConversationGlyph.Status.ARCHIVED) {
                return;
            }
            g.setStatus(ConversationGlyph.Status.ARCHIVED);
            g.setArchivedAt(Instant.now());
            g.setRevision(chat.glyphRevision(conversationId) + 1);
            try {
                glyphs.save(g);
                publishChange(c, g, "ARCHIVE");
                return;
            } catch (DuplicateKeyException e) {
                // trùng revision với người ghi khác: đọc lại và thử tiếp
            }
        }
        throw GlyphErrors.busy();
    }

    // ---------- nội bộ ----------

    private void requireWriteEnabled() {
        if (!settings.writeEnabled()) {
            throw GlyphErrors.disabled();
        }
    }

    private ConversationGlyph find(String conversationId, String glyphId) {
        if (glyphId == null || glyphId.isBlank()) {
            throw GlyphErrors.notFound();
        }
        return glyphs.findById(glyphId).filter(g -> conversationId.equals(g.getConversationId()))
                .orElseThrow(GlyphErrors::notFound);
    }

    private CreateResult replayOr(String conversationId, String me, String mutationId,
                                  ApiException rejection) {
        return byMutation(conversationId, me, mutationId).map(g -> new CreateResult(GlyphView.of(g), false))
                .orElseThrow(() -> rejection);
    }

    private Optional<ConversationGlyph> byMutation(String conversationId, String me, String mutationId) {
        return glyphs.findByConversationIdAndCreatorPublicIdAndClientMutationId(conversationId, me, mutationId);
    }

    private static String meaning(String raw) {
        String m = raw == null ? "" : raw.strip();
        int len = m.codePointCount(0, m.length());
        if (len < 1 || len > MAX_MEANING || m.codePoints().anyMatch(Character::isISOControl)) {
            throw GlyphErrors.invalidMeaning();
        }
        return m;
    }

    private String color(String raw) {
        String c = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        if (!HEX_COLOR.matcher(c).matches() || !settings.palette().contains(c)) {
            throw GlyphErrors.invalidColor();
        }
        return c;
    }

    /** Chỉ báo có thay đổi; không gửi dữ liệu glyph qua WebSocket. Gửi tới mọi thiết bị của cả hai người. */
    private void publishChange(Conversation c, ConversationGlyph g, String change) {
        String frame = ChatService.frame("dictionary_changed", Map.of(
                "conversationId", c.getId(), "revision", g.getRevision(), "glyphId", g.getId(), "change", change));
        for (String p : c.getParticipants()) {
            bus.publish(p, frame);
        }
    }
}
