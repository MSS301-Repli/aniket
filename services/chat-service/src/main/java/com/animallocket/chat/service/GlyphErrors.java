package com.animallocket.chat.service;

import com.animallocket.common.web.ApiException;
import org.springframework.http.HttpStatus;

/** Mã lỗi ổn định cho glyph (client chỉ map theo {@code code}). Thông điệp không chứa dữ liệu người dùng. */
public final class GlyphErrors {
    private GlyphErrors() {
    }

    public static ApiException invalidPoints() {
        return ApiException.badRequest("GLYPH_INVALID_POINTS", "Nét vẽ phải gồm đúng 64 điểm, toạ độ -1000..1000");
    }

    public static ApiException invalidColor() {
        return ApiException.badRequest("GLYPH_INVALID_COLOR", "Màu không hợp lệ");
    }

    public static ApiException invalidMeaning() {
        return ApiException.badRequest("GLYPH_INVALID_MEANING", "Nghĩa của ký hiệu dài 1–64 ký tự");
    }

    public static ApiException invalidMutationId() {
        return ApiException.badRequest("VALIDATION_FAILED", "clientMutationId phải là UUID");
    }

    public static ApiException duplicate() {
        return ApiException.conflict("GLYPH_DUPLICATE", "Ký hiệu có hình này đã tồn tại trong hội thoại");
    }

    public static ApiException limit() {
        return ApiException.conflict("GLYPH_LIMIT", "Từ điển ký hiệu của hội thoại đã đầy");
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "GLYPH_NOT_FOUND", "Không tìm thấy ký hiệu trong hội thoại");
    }

    public static ApiException notActive() {
        return ApiException.conflict("GLYPH_NOT_ACTIVE", "Ký hiệu đã bị gỡ");
    }

    public static ApiException notOwner() {
        return new ApiException(HttpStatus.FORBIDDEN, "GLYPH_NOT_OWNER", "Chỉ người tạo mới gỡ được ký hiệu");
    }

    public static ApiException sequenceTooLong() {
        return ApiException.badRequest("GLYPH_SEQUENCE_TOO_LONG", "Mỗi tin tối đa " + ChatService.MAX_GLYPHS + " ký hiệu");
    }

    public static ApiException invalidMessage() {
        return ApiException.badRequest("INVALID_GLYPH_MESSAGE", "Tin ký hiệu không hợp lệ");
    }

    public static ApiException rateLimited() {
        return ApiException.tooMany("GLYPH_CREATE_RATE_LIMIT", "Bạn đã tạo đủ số ký hiệu cho hôm nay");
    }

    public static ApiException disabled() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GLYPH_DISABLED", "Tính năng ký hiệu đang tạm tắt");
    }

    public static ApiException busy() {
        return ApiException.conflict("GLYPH_BUSY", "Từ điển đang được cập nhật, hãy thử lại");
    }
}
