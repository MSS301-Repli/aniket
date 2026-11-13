package com.animallocket.discovery.geo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Bảo vệ vị trí. Toạ độ chính xác KHÔNG bao giờ được lưu hay trả ra:
 * <ol>
 *   <li>Làm tròn về tâm ô lưới ~500 m ngay khi nhận.</li>
 *   <li>Cộng độ lệch cố định theo từng người (HMAC của publicId với khoá bí mật của server), ±250 m.
 *       Cố định nên kẻ tấn công không thể "lấy trung bình" nhiều lần truy vấn để triệt nhiễu.</li>
 *   <li>API chỉ trả khoảng cách theo mức, không trả số mét hay toạ độ.</li>
 * </ol>
 */
public final class LocationPrivacy {
    public static final double CELL_METERS = 500.0;
    public static final double MAX_OFFSET_METERS = 250.0;
    private static final double METERS_PER_DEG_LAT = 111_320.0;

    private final byte[] secret;

    public LocationPrivacy(String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("animal.discovery.secret phải dài ít nhất 16 ký tự");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public record LatLon(double lat, double lon) {
        public LatLon {
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180 || Double.isNaN(lat) || Double.isNaN(lon)) {
                throw new IllegalArgumentException("Toạ độ không hợp lệ");
            }
        }
    }

    /** Tâm ô lưới chứa điểm. Hai điểm trong cùng ô cho cùng kết quả. */
    public static LatLon snap(LatLon p) {
        double latStep = CELL_METERS / METERS_PER_DEG_LAT;
        double lat = (Math.floor(p.lat() / latStep) + 0.5) * latStep;
        double cos = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double lonStep = CELL_METERS / (METERS_PER_DEG_LAT * cos);
        double lon = (Math.floor(p.lon() / lonStep) + 0.5) * lonStep;
        return new LatLon(clamp(lat, -90, 90), clamp(lon, -180, 180));
    }

    /** Vị trí lưu trữ = tâm ô lưới + độ lệch cố định theo người. */
    public LatLon storedPosition(String publicId, LatLon raw) {
        LatLon cell = snap(raw);
        double[] off = offsetMeters(publicId);
        double lat = cell.lat() + off[0] / METERS_PER_DEG_LAT;
        double cos = Math.max(0.01, Math.cos(Math.toRadians(cell.lat())));
        double lon = cell.lon() + off[1] / (METERS_PER_DEG_LAT * cos);
        return new LatLon(clamp(lat, -90, 90), clamp(lon, -180, 180));
    }

    double[] offsetMeters(String publicId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] h = mac.doFinal(publicId.getBytes(StandardCharsets.UTF_8));
            double a = ((h[0] & 0xFF) << 8 | (h[1] & 0xFF)) / 65535.0;
            double b = ((h[2] & 0xFF) << 8 | (h[3] & 0xFF)) / 65535.0;
            return new double[]{(a * 2 - 1) * MAX_OFFSET_METERS, (b * 2 - 1) * MAX_OFFSET_METERS};
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static double distanceMeters(LatLon a, LatLon b) {
        double r = 6_371_000;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(s)));
    }

    /**
     * Mức khoảng cách trả cho client. Người dưới 18 dùng mức thô hơn (không có "dưới 1 km").
     * Khoảng cách tính trên vị trí đã làm nhiễu nên không thể dùng để tìm vị trí thật.
     */
    public static String band(double meters, boolean minor) {
        if (minor) {
            if (meters < 3_000) return "UNDER_3KM";
            if (meters < 10_000) return "3_TO_10KM";
            return "OVER_10KM";
        }
        if (meters < 1_000) return "UNDER_1KM";
        if (meters < 3_000) return "1_TO_3KM";
        if (meters < 10_000) return "3_TO_10KM";
        return "OVER_10KM";
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
