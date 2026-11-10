package com.animallocket.discovery.geo;

/**
 * Bảo vệ vị trí. Toạ độ chính xác KHÔNG bao giờ được lưu hay trả ra; mọi toạ độ đi qua lớp này trước.
 */
public final class LocationPrivacy {
    public static final double CELL_METERS = 500.0;
    private static final double METERS_PER_DEG_LAT = 111_320.0;

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

    public static double distanceMeters(LatLon a, LatLon b) {
        double r = 6_371_000;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(s)));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
