package com.animallocket.discovery.geo;

/**
 * Bảo vệ vị trí. Toạ độ chính xác KHÔNG bao giờ được lưu hay trả ra; mọi toạ độ đi qua lớp này trước.
 */
public final class LocationPrivacy {
    public record LatLon(double lat, double lon) {
        public LatLon {
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180 || Double.isNaN(lat) || Double.isNaN(lon)) {
                throw new IllegalArgumentException("Toạ độ không hợp lệ");
            }
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
}
