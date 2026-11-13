package com.animallocket.discovery;

import com.animallocket.discovery.geo.LocationPrivacy.LatLon;
import com.animallocket.discovery.store.DiscoveryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cùng một bộ kiểm tra cho mọi cài đặt DiscoveryStore để chắc chắn bản Redis hành xử giống bản bộ nhớ.
 * Bản Redis chỉ chạy khi có biến môi trường REDIS_TEST_HOST (ví dụ: docker run -p 6379:6379 redis).
 */
abstract class DiscoveryStoreContractTest {
    abstract DiscoveryStore newStore();

    DiscoveryStore store;
    final Instant now = Instant.now();
    final LatLon hanoi = new LatLon(21.0285, 105.8542);

    @BeforeEach
    void init() {
        store = newStore();
    }

    private LatLon eastOf(LatLon p, double meters) {
        return new LatLon(p.lat(), p.lon() + meters / (111_320 * Math.cos(Math.toRadians(p.lat()))));
    }

    @Test
    void nearbyIsSortedByDistanceAndRespectsRadiusAndAgeGroup() {
        String a = "a" + System.nanoTime(), b = "b" + System.nanoTime(), c = "c" + System.nanoTime(), k = "k" + System.nanoTime();
        store.putLocation("ADULT", a, eastOf(hanoi, 300), now.plusSeconds(600));
        store.putLocation("ADULT", b, eastOf(hanoi, 2000), now.plusSeconds(600));
        store.putLocation("ADULT", c, eastOf(hanoi, 20_000), now.plusSeconds(600));
        store.putLocation("MINOR", k, eastOf(hanoi, 100), now.plusSeconds(600));

        var res = store.nearby("ADULT", hanoi, 5000, 10);
        List<String> ids = res.stream().map(DiscoveryStore.Neighbor::publicId).toList();
        assertTrue(ids.indexOf(a) >= 0 && ids.indexOf(a) < ids.indexOf(b), ids.toString());
        assertFalse(ids.contains(c));
        assertFalse(ids.contains(k), "nhóm tuổi khác không được lẫn vào");
        double da = res.stream().filter(n -> n.publicId().equals(a)).findFirst().orElseThrow().distanceMeters();
        assertTrue(da > 250 && da < 350, "distance=" + da);
    }

    @Test
    void movingBetweenAgeGroupsLeavesNoStaleEntry() {
        String id = "mv" + System.nanoTime();
        store.putLocation("MINOR", id, hanoi, now.plusSeconds(600));
        store.putLocation("ADULT", id, hanoi, now.plusSeconds(600));
        assertTrue(store.nearby("MINOR", hanoi, 1000, 50).stream().noneMatch(n -> n.publicId().equals(id)));
        assertTrue(store.location("ADULT", id).isPresent());
    }

    @Test
    void expiredLocationsArePurged() {
        String old = "old" + System.nanoTime(), fresh = "fresh" + System.nanoTime();
        store.putLocation("ADULT", old, hanoi, now.minusSeconds(10));
        store.putLocation("ADULT", fresh, hanoi, now.plusSeconds(600));
        assertTrue(store.purgeExpired(now) >= 1);
        assertTrue(store.location("ADULT", old).isEmpty());
        assertTrue(store.location("ADULT", fresh).isPresent());
    }

    @Test
    void onlineWindowAndStatus() {
        String u = "on" + System.nanoTime(), v = "off" + System.nanoTime();
        store.touchOnline("ADULT", u, now);
        store.touchOnline("ADULT", v, now.minusSeconds(300));
        List<String> online = store.online("ADULT", now.minusSeconds(60), 50);
        assertTrue(online.contains(u));
        assertFalse(online.contains(v));
        Map<String, Boolean> st = store.onlineStatus(Set.of(u, v, "nobody"), now.minusSeconds(60));
        assertEquals(true, st.get(u));
        assertEquals(false, st.get(v));
        assertEquals(false, st.get("nobody"));
    }

    @Test
    void boostExpires() {
        String u = "bo" + System.nanoTime();
        store.boost(u, now.plusSeconds(60));
        assertTrue(store.boosted(now).contains(u));
        assertFalse(store.boosted(now.plusSeconds(120)).contains(u));
    }

    @Test
    void swipeHistory() {
        String me = "me" + System.nanoTime();
        store.recordLeft(me, "x", now.minusSeconds(100));
        store.recordLeft(me, "y", now.minusSeconds(100_000_000L));
        store.recordRight(me, "z", now);
        assertEquals(Set.of("x"), store.recentLeft(me, now.minusSeconds(1000)));
        assertEquals(Set.of("z"), store.rights(me));
    }

    @Test
    void removePresenceKeepsSwipeHistory() {
        String u = "rp" + System.nanoTime();
        store.putLocation("ADULT", u, hanoi, now.plusSeconds(600));
        store.touchOnline("ADULT", u, now);
        store.boost(u, now.plusSeconds(600));
        store.recordLeft(u, "x", now);
        store.recordRight(u, "y", now);
        store.removePresence(u);
        assertTrue(store.location("ADULT", u).isEmpty());
        assertFalse(store.online("ADULT", now.minusSeconds(60), 100).contains(u));
        assertFalse(store.boosted(now).contains(u));
        assertEquals(Set.of("y"), store.rights(u), "lịch sử quẹt phải còn nguyên");
        assertEquals(Set.of("x"), store.recentLeft(u, now.minusSeconds(1000)));
    }

    @Test
    void forgetRemovesEverything() {
        String u = "gone" + System.nanoTime();
        store.putLocation("ADULT", u, hanoi, now.plusSeconds(600));
        store.touchOnline("ADULT", u, now);
        store.boost(u, now.plusSeconds(600));
        store.recordLeft(u, "x", now);
        store.recordRight(u, "y", now);
        store.forget(u);
        assertTrue(store.location("ADULT", u).isEmpty());
        assertFalse(store.online("ADULT", now.minusSeconds(60), 100).contains(u));
        assertFalse(store.boosted(now).contains(u));
        assertTrue(store.rights(u).isEmpty());
        assertTrue(store.recentLeft(u, now.minusSeconds(1000)).isEmpty());
    }
}
