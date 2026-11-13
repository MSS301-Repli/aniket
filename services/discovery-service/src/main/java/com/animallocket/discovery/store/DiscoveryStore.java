package com.animallocket.discovery.store;

import com.animallocket.discovery.geo.LocationPrivacy.LatLon;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lưu trữ cho discovery: vị trí (GEO), online, boost và lịch sử quẹt. Redis cho runtime, bộ nhớ cho test. */
public interface DiscoveryStore {
    record Neighbor(String publicId, double distanceMeters) {
    }

    // ---- vị trí: mỗi nhóm tuổi một tập riêng để người lớn và người dưới 18 không bao giờ thấy nhau ----
    void putLocation(String ageGroup, String publicId, LatLon storedPosition, Instant expiresAt);

    Optional<LatLon> location(String ageGroup, String publicId);

    List<Neighbor> nearby(String ageGroup, LatLon center, double radiusMeters, int limit);

    // ---- online ----
    void touchOnline(String ageGroup, String publicId, Instant now);

    List<String> online(String ageGroup, Instant since, int limit);

    Map<String, Boolean> onlineStatus(Set<String> publicIds, Instant since);

    // ---- boost ----
    void boost(String publicId, Instant until);

    Set<String> boosted(Instant now);

    // ---- quẹt ----
    void recordLeft(String me, String target, Instant at);

    Set<String> recentLeft(String me, Instant since);

    void recordRight(String me, String target, Instant at);

    Set<String> rights(String me);

    // ---- dọn dẹp ----
    /** Xoá vị trí, trạng thái online và boost nhưng GIỮ lịch sử quẹt (khi người dùng tắt khám phá). */
    void removePresence(String publicId);

    /** Xoá vị trí/online hết hạn. Trả số mục đã xoá. */
    int purgeExpired(Instant now);

    /** Xoá mọi dấu vết của một người (xoá tài khoản). */
    void forget(String publicId);
}
