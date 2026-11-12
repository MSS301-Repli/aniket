package com.animallocket.discovery;

import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.web.ApiException;
import com.animallocket.discovery.client.Gateways;
import com.animallocket.discovery.geo.LocationPrivacy;
import com.animallocket.discovery.service.DiscoveryService;
import com.animallocket.discovery.store.InMemoryDiscoveryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryServiceTest {
    static final double LAT = 21.0285, LON = 105.8542;

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-03-10T05:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId z) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    static class FakeUsers implements Gateways.UserGateway {
        final Map<String, Gateways.Profile> profiles = new HashMap<>();

        void add(String id, String ageGroup, boolean discoverable) {
            profiles.put(id, new Gateways.Profile(id, "alias-" + id, "fox", "1:fox:0:none", ageGroup, discoverable, true, false));
        }

        public Gateways.Profile profile(String id) {
            return profiles.get(id);
        }
    }

    final FakeUsers users = new FakeUsers();
    final MutableClock clock = new MutableClock();
    final InMemoryKeyValueStore kv = new InMemoryKeyValueStore();
    final InMemoryDiscoveryStore store = new InMemoryDiscoveryStore();
    final LocationPrivacy privacy = new LocationPrivacy("unit-test-secret-0123456789");
    DiscoveryService svc;

    @BeforeEach
    void setUp() {
        svc = new DiscoveryService(store, users, privacy, kv, clock, Duration.ofMinutes(30));
    }

    /** Đặt người dùng tại vị trí cách "me" khoảng dxMeters về phía đông. */
    private void place(String id, double dxMeters) {
        double dLon = dxMeters / (111_320 * Math.cos(Math.toRadians(LAT)));
        svc.updateLocation(id, LAT, LON + dLon);
    }

    @Test
    void locationRequiresDiscoveryEnabled() {
        users.add("shy", "ADULT", false);
        ApiException e = assertThrows(ApiException.class, () -> svc.updateLocation("shy", LAT, LON));
        assertEquals(403, e.status().value());
    }

    @Test
    void locationIsStoredRoundedAndCanBeCleared() {
        users.add("me", "ADULT", true);
        svc.updateLocation("me", LAT, LON);

        LocationPrivacy.LatLon stored = store.location("ADULT", "me").orElseThrow();
        assertFalse(stored.equals(new LocationPrivacy.LatLon(LAT, LON)), "không được lưu toạ độ thật");
        assertTrue(LocationPrivacy.distanceMeters(stored, new LocationPrivacy.LatLon(LAT, LON)) <= 360 + 355);

        svc.clearLocation("me");
        assertTrue(store.location("ADULT", "me").isEmpty(), "vị trí phải bị xoá ngay");
    }
}
