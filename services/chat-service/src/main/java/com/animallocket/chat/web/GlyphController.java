package com.animallocket.chat.web;

import com.animallocket.chat.service.GlyphService;
import com.animallocket.common.Headers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Từ điển ký hiệu theo hội thoại. Chỉ thành viên hội thoại mới truy cập được. */
@RestController
@RequestMapping("/api/v1/chat/conversations/{conversationId}/glyphs")
public class GlyphController {
    /**
     * {@code points} nhận {@link Number} để số thực hoặc số quá lớn đi tới bước kiểm tra và trả
     * {@code GLYPH_INVALID_POINTS} thay vì lỗi parse chung. Server bỏ qua mọi trường khác (vd. shapeHash).
     */
    public record CreateRequest(String clientMutationId, String meaning, String color, List<Number> points,
                                Integer pointCount) {
    }

    private final GlyphService glyphs;

    public GlyphController(GlyphService glyphs) {
        this.glyphs = glyphs;
    }

    @GetMapping
    public GlyphService.SyncPage sync(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String conversationId,
                                      @RequestParam(defaultValue = "0") long afterRevision,
                                      @RequestParam(defaultValue = "100") int limit) {
        return glyphs.sync(me, conversationId, afterRevision, limit);
    }

    @GetMapping("/{glyphId}")
    public GlyphService.GlyphView get(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String conversationId,
                                      @PathVariable String glyphId) {
        return glyphs.get(me, conversationId, glyphId);
    }

    /** 201 khi tạo mới, 200 khi là request lặp lại (cùng clientMutationId) và trả lại glyph cũ. */
    @PostMapping
    public ResponseEntity<GlyphService.GlyphView> create(@RequestHeader(Headers.PUBLIC_ID) String me,
                                                         @PathVariable String conversationId,
                                                         @RequestBody CreateRequest req) {
        var r = glyphs.create(me, conversationId, new GlyphService.CreateCommand(
                req.clientMutationId(), req.meaning(), req.color(), req.points(), req.pointCount()));
        return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK).body(r.glyph());
    }

    @DeleteMapping("/{glyphId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@RequestHeader(Headers.PUBLIC_ID) String me, @PathVariable String conversationId,
                        @PathVariable String glyphId) {
        glyphs.archive(me, conversationId, glyphId);
    }
}
