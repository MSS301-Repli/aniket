package com.animallocket.discovery.service;

import com.animallocket.common.kv.KeyValueStore;
import com.animallocket.common.web.ApiException;
import com.animallocket.discovery.client.Gateways;
import com.animallocket.discovery.geo.LocationPrivacy;
import com.animallocket.discovery.geo.LocationPrivacy.LatLon;
import com.animallocket.discovery.store.DiscoveryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class DiscoveryService {
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(60);

    private final DiscoveryStore store;
    private final Gateways.UserGateway users;
    private final LocationPrivacy privacy;
    private final KeyValueStore kv;
    private final Clock clock;
    private final Duration locationTtl;

    public DiscoveryService(DiscoveryStore store, Gateways.UserGateway users,
                            LocationPrivacy privacy, KeyValueStore kv, Clock clock,
                            @Value("${animal.discovery.location-ttl:30m}") Duration locationTtl) {
        this.store = store;
        this.users = users;
        this.privacy = privacy;
        this.kv = kv;
        this.clock = clock;
        this.locationTtl = locationTtl;
    }

    // ---------------- vị trí và online ----------------

    /** Lưu vị trí đã làm tròn/làm nhiễu. Chỉ khi người dùng đang bật chế độ khám phá. */
    public void updateLocation(String me, double lat, double lon) {
        Gateways.Profile p = requireDiscoverable(me);
        LatLon stored = privacy.storedPosition(me, new LatLon(lat, lon));
        Instant now = clock.instant();
        store.putLocation(p.ageGroup(), me, stored, now.plus(locationTtl));
        store.touchOnline(p.ageGroup(), me, now);
    }

    /** Người dùng tắt khám phá thì xoá vị trí ngay. */
    public void clearLocation(String me) {
        store.removePresence(me);
        kv.delete(presenceKey(me));
    }

    public void heartbeat(String me) {
        Gateways.Profile p = users.profile(me);
        if (p == null || !p.active()) {
            throw ApiException.notFound("Người dùng");
        }
        Instant now = clock.instant();
        // Trạng thái online cho BẠN BÈ xem: ghi cho mọi người dùng.
        kv.set(presenceKey(me), String.valueOf(now.getEpochSecond()), Duration.ofMinutes(3));
        // Danh sách online của bộ thẻ chỉ chứa người đã bật khám phá; nếu không, người không bật khám phá
        // sẽ chiếm chỗ trong danh sách ứng viên (bị cắt trước khi lọc) và đẩy người hợp lệ ra ngoài.
        if (p.discoverable()) {
            store.touchOnline(p.ageGroup(), me, now);
        }
    }

    /** Trạng thái online chỉ xem được với bạn bè. */
    public DiscoveryDtos.PresenceResponse presence(String me, Set<String> ids) {
        Set<String> friends = new HashSet<>(users.exclusions(me).friends());
        Set<String> allowed = new HashSet<>(ids);
        allowed.retainAll(friends);
        long threshold = clock.instant().minus(ONLINE_WINDOW).getEpochSecond();
        Map<String, Boolean> status = new HashMap<>();
        for (String id : allowed) {
            status.put(id, kv.get(presenceKey(id)).map(v -> Long.parseLong(v) > threshold).orElse(false));
        }
        return new DiscoveryDtos.PresenceResponse(status);
    }

    // ---------------- nội bộ ----------------

    private Gateways.Profile requireDiscoverable(String me) {
        Gateways.Profile p = users.profile(me);
        if (p == null || !p.active()) {
            throw ApiException.notFound("Người dùng");
        }
        if (!p.discoverable()) {
            throw ApiException.forbidden("Hãy bật chế độ khám phá trước");
        }
        return p;
    }

    private static String presenceKey(String id) {
        return "presence:" + id;
    }
}
