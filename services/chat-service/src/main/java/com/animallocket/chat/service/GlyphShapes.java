package com.animallocket.chat.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Kiểm tra và canonical hoá nét vẽ glyph. Không tin hash do client gửi: server luôn tự tính.
 *
 * <p>Canonical: dời trọng tâm về gốc toạ độ, phóng/thu để toạ độ có trị tuyệt đối lớn nhất bằng {@link #RANGE},
 * làm tròn về số nguyên. Hai nét chỉ khác vị trí hoặc kích thước cho cùng một hình canonical.
 */
public final class GlyphShapes {
    public static final int POINT_PAIRS = 64;
    public static final int VALUES = POINT_PAIRS * 2;
    public static final int RANGE = 1000;

    private GlyphShapes() {
    }

    /**
     * Kiểm tra số lượng và miền toạ độ. Nhận {@link Number} để bắt được số thực, số quá lớn và null do JSON đưa vào.
     */
    public static int[] validate(List<? extends Number> points, Integer pointCount) {
        if (points == null || points.size() != VALUES || (pointCount != null && pointCount != POINT_PAIRS)) {
            throw GlyphErrors.invalidPoints();
        }
        int[] out = new int[VALUES];
        for (int i = 0; i < VALUES; i++) {
            Number n = points.get(i);
            if (n == null) {
                throw GlyphErrors.invalidPoints();
            }
            double d = n.doubleValue();
            if (Double.isNaN(d) || d != Math.rint(d) || d < -RANGE || d > RANGE) {
                throw GlyphErrors.invalidPoints();
            }
            out[i] = (int) d;
        }
        return out;
    }

    public static int[] canonicalize(int[] points) {
        if (points.length != VALUES) {
            throw GlyphErrors.invalidPoints();
        }
        double cx = 0, cy = 0;
        for (int i = 0; i < VALUES; i += 2) {
            cx += points[i];
            cy += points[i + 1];
        }
        cx /= POINT_PAIRS;
        cy /= POINT_PAIRS;
        double max = 0;
        for (int i = 0; i < VALUES; i += 2) {
            max = Math.max(max, Math.max(Math.abs(points[i] - cx), Math.abs(points[i + 1] - cy)));
        }
        if (max < 1e-9) {
            throw GlyphErrors.invalidPoints(); // mọi điểm trùng nhau: không phải một nét vẽ
        }
        double scale = RANGE / max;
        int[] out = new int[VALUES];
        for (int i = 0; i < VALUES; i += 2) {
            out[i] = clamp(Math.round((points[i] - cx) * scale));
            out[i + 1] = clamp(Math.round((points[i + 1] - cy) * scale));
        }
        return out;
    }

    public static String hash(int[] canonical) {
        StringBuilder sb = new StringBuilder(canonical.length * 5);
        for (int v : canonical) {
            sb.append(v).append(',');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int clamp(long v) {
        return (int) Math.max(-RANGE, Math.min(RANGE, v));
    }
}
