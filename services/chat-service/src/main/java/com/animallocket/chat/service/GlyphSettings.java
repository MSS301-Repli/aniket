package com.animallocket.chat.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cấu hình glyph ({@code animal.chat.glyph.*}).
 *
 * @param writeEnabled     false = rollback: chặn tạo/archive/gửi glyph nhưng vẫn đọc được lịch sử và từ điển
 * @param palette          màu hợp lệ, dạng {@code #RRGGBB} (so khớp không phân biệt hoa thường)
 * @param maxActive        số glyph ACTIVE tối đa mỗi hội thoại
 * @param maxCreatesPerDay số glyph một người được tạo mỗi ngày (UTC)
 */
public record GlyphSettings(boolean writeEnabled, Set<String> palette, int maxActive, int maxCreatesPerDay) {
    public static final List<String> DEFAULT_PALETTE = List.of(
            "#818CF8", "#34D399", "#FBBF24", "#F472B6", "#38BDF8", "#FB923C", "#C084FC");

    public GlyphSettings {
        palette = palette.stream().map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public static GlyphSettings defaults() {
        return new GlyphSettings(true, Set.copyOf(DEFAULT_PALETTE), 100, 20);
    }
}
