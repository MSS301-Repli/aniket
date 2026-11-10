package com.animallocket.discovery;

import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.web.ApiException;
import com.animallocket.discovery.client.Gateways;
import com.animallocket.discovery.geo.LocationPrivacy;
import com.animallocket.discovery.service.DiscoveryDtos;
import com.animallocket.discovery.service.DiscoveryService;
import com.animallocket.discovery.store.InMemoryDiscoveryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        final Map<String, List<String>> friends = new HashMap<>();
        final Map<String, List<String>> blocked = new HashMap<>();
        String swipeResult = "REQUESTED";
        boolean swipeFails;
        final List<String> swipes = new ArrayList<>();

        void add(String id, String ageGroup, boolean discoverable) {
            profiles.put(id, new Gateways.Profile(id, "alias-" + id, "fox", "1:fox:0:none", ageGroup, discoverable, true, false));
        }

        public Gateways.Profile profile(String id) {
            return profiles.get(id);
        }

        public Map<String, Gateways.Profile> profiles(List<String> ids) {
            Map<String, Gateways.Profile> m = new HashMap<>();
            ids.forEach(i -> {
                if (profiles.containsKey(i)) m.put(i, profiles.get(i));
            });
            return m;
        }

        public Gateways.Exclusions exclusions(String id) {
            return new Gateways.Exclusions(friends.getOrDefault(id, List.of()), blocked.getOrDefault(id, List.of()));
        }

        public String swipeRight(String from, String to) {
            if (swipeFails) {
                throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "X", "down");
            }
            swipes.add(from + ">" + to);
            return swipeResult;
        }
    }

    static class FakeBilling implements Gateways.BillingGateway {
        boolean ok = true;
        /** Mỗi lần trừ ví thành công ghi lại "publicId:khoá". */
        final List<String> consumed = new ArrayList<>();

        public boolean consumeBoost(String publicId, String idempotencyKey) {
            if (ok) {
                consumed.add(publicId + ":" + idempotencyKey);
            }
            return ok;
        }
    }

    /** Store giả lập Redis chập chờn: ghi boost thất bại {@code boostFailures} lần đầu. */
    static class FlakyStore extends InMemoryDiscoveryStore {
        int boostFailures;

        @Override
        public void boost(String id, Instant until) {
            if (boostFailures > 0) {
                boostFailures--;
                throw new IllegalStateException("redis down");
            }
            super.boost(id, until);
        }
    }

    final FakeUsers users = new FakeUsers();
    final MutableClock clock = new MutableClock();
    final InMemoryKeyValueStore kv = new InMemoryKeyValueStore();
    final FlakyStore store = new FlakyStore();
    final FakeBilling billing = new FakeBilling();
    final LocationPrivacy privacy = new LocationPrivacy("unit-test-secret-0123456789");
    DiscoveryService svc;

    @BeforeEach
    void setUp() {
        svc = new DiscoveryService(store, users, billing, privacy, kv, clock, 3, 6, Duration.ofMinutes(30), 10_000, 5_000, 1000);
    }

    /** Đặt người dùng tại vị trí cách "me" khoảng dxMeters về phía đông. */
    private void place(String id, double dxMeters) {
        double dLon = dxMeters / (111_320 * Math.cos(Math.toRadians(LAT)));
        svc.updateLocation(id, LAT, LON + dLon);
    }

    /** Có vị trí nhưng KHÔNG online (không heartbeat): chỉ xuất hiện nếu nằm trong bán kính. */
    private void placeOffline(String id, String ageGroup, double dxMeters) {
        double dLon = dxMeters / (111_320 * Math.cos(Math.toRadians(LAT)));
        store.putLocation(ageGroup, id, privacy.storedPosition(id, new LocationPrivacy.LatLon(LAT, LON + dLon)),
                clock.instant().plusSeconds(1800));
    }

    private List<String> cardIds(String me) {
        return svc.cards(me, 30).cards().stream().map(DiscoveryDtos.Card::publicId).toList();
    }

    @Test
    void locationRequiresDiscoveryEnabled() {
        users.add("shy", "ADULT", false);
        ApiException e = assertThrows(ApiException.class, () -> svc.updateLocation("shy", LAT, LON));
        assertEquals(403, e.status().value());
        assertThrows(ApiException.class, () -> svc.cards("shy", 5));
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

    @Test
    void deckContainsOnlyEligibleNearbyOrOnlinePeople() {
        for (String id : List.of("me", "near", "mid", "far", "friend", "blocked", "hidden", "i_online")) {
            users.add(id, "ADULT", !id.equals("hidden"));
        }
        users.add("minor", "MINOR", true);
        users.friends.put("me", List.of("friend"));
        users.blocked.put("me", List.of("blocked"));

        svc.updateLocation("me", LAT, LON);            // hidden không gọi được updateLocation (không bật)
        place("near", 700);
        place("mid", 5000);
        placeOffline("far", "ADULT", 50_000);
        place("friend", 300);
        place("blocked", 300);
        place("minor", 300);
        store.putLocation("ADULT", "hidden", new LocationPrivacy.LatLon(LAT, LON + 0.003), clock.instant().plusSeconds(600));
        svc.heartbeat("i_online");                     // chỉ online, không có vị trí

        List<String> ids = cardIds("me");

        assertTrue(ids.contains("near") && ids.contains("mid"), ids.toString());
        assertTrue(ids.contains("i_online"), "người đang online phải có trong bộ thẻ");
        assertFalse(ids.contains("far"), "ngoài bán kính");
        assertFalse(ids.contains("friend"), "đã là bạn");
        assertFalse(ids.contains("blocked"), "đã chặn");
        assertFalse(ids.contains("hidden"), "đã tắt chế độ khám phá");
        assertFalse(ids.contains("minor"), "khác nhóm tuổi");
        assertFalse(ids.contains("me"), "không tự gợi ý chính mình");
        assertTrue(ids.indexOf("near") < ids.indexOf("mid"), "gần hơn xếp trước");
    }

    @Test
    void onlineStrangersFarAwayAppearWithoutAnyDistanceInformation() {
        users.add("me", "ADULT", true);
        users.add("faraway", "ADULT", true);
        svc.updateLocation("me", LAT, LON);
        svc.updateLocation("faraway", LAT + 3.0, LON);   // ~330 km, đang online
        var card = svc.cards("me", 5).cards().get(0);
        assertEquals("faraway", card.publicId());
        assertEquals(null, card.distanceBand(), "ngoài bán kính thì không tiết lộ khoảng cách");
        assertTrue(card.online());
    }

    @Test
    void cardsNeverExposeCoordinatesOrDistancesInMeters() {
        for (RecordComponent c : DiscoveryDtos.Card.class.getRecordComponents()) {
            String n = c.getName().toLowerCase();
            assertFalse(n.contains("lat") || n.contains("lon") || n.contains("meter") || n.contains("coord"), n);
        }
        users.add("me", "ADULT", true);
        users.add("near", "ADULT", true);
        svc.updateLocation("me", LAT, LON);
        place("near", 400);
        var card = svc.cards("me", 5).cards().get(0);
        assertEquals("UNDER_1KM", card.distanceBand());
    }

    @Test
    void minorsOnlySeeMinorsWithCoarseBands() {
        users.add("kid1", "MINOR", true);
        users.add("kid2", "MINOR", true);
        users.add("adult", "ADULT", true);
        svc.updateLocation("kid1", LAT, LON);
        place("kid2", 200);
        place("adult", 200);

        var cards = svc.cards("kid1", 10).cards();
        assertEquals(List.of("kid2"), cards.stream().map(DiscoveryDtos.Card::publicId).toList());
        assertEquals("UNDER_3KM", cards.get(0).distanceBand());
        // chiều ngược lại cũng vậy
        users.add("adult2", "ADULT", true);
        svc.updateLocation("adult2", LAT, LON);
        assertFalse(cardIds("adult2").contains("kid2"));
    }

    @Test
    void minorRadiusIsSmallerThanAdultRadius() {
        users.add("kid", "MINOR", true);
        users.add("kidFar", "MINOR", true);
        users.add("adult", "ADULT", true);
        users.add("adultFar", "ADULT", true);
        svc.updateLocation("kid", LAT, LON);
        placeOffline("kidFar", "MINOR", 7_000);
        svc.updateLocation("adult", LAT, LON);
        placeOffline("adultFar", "ADULT", 7_000);
        assertFalse(cardIds("kid").contains("kidFar"));
        assertTrue(cardIds("adult").contains("adultFar"));
    }

    @Test
    void searchesAreRateLimited() {
        DiscoveryService limited = new DiscoveryService(store, users, billing, privacy, new InMemoryKeyValueStore(), clock, 3, 6, Duration.ofMinutes(30),
        10_000, 5_000, 2);
        users.add("me", "ADULT", true);
        limited.cards("me", 5);
        limited.cards("me", 5);
        ApiException e = assertThrows(ApiException.class, () -> limited.cards("me", 5));
        assertEquals("TOO_MANY_SEARCHES", e.code());
    }

    @Test
    void presenceIsOnlyVisibleForFriends() {
        users.add("me", "ADULT", true);
        users.add("friend", "ADULT", true);
        users.add("stranger", "ADULT", true);
        users.friends.put("me", List.of("friend"));
        svc.heartbeat("friend");
        svc.heartbeat("stranger");

        var res = svc.presence("me", Set.of("friend", "stranger"));
        assertEquals(Map.of("friend", true), res.online());

        clock.now = clock.now.plusSeconds(90); // quá 60 giây không heartbeat
        assertEquals(Map.of("friend", false), svc.presence("me", Set.of("friend", "stranger")).online());
    }

    @Test
    void nonDiscoverableUsersAreVisibleOnlineToFriendsButNeverEnterTheDeck() {
        users.add("me", "ADULT", true);
        users.add("shy", "ADULT", false);      // đã tắt khám phá nhưng vẫn mở app
        users.friends.put("me", List.of("shy"));
        svc.updateLocation("me", LAT, LON);
        svc.heartbeat("shy");

        assertEquals(Map.of("shy", true), svc.presence("me", Set.of("shy")).online(), "bạn bè vẫn thấy trạng thái online");
        assertTrue(store.online("ADULT", clock.instant().minusSeconds(60), 100).stream().noneMatch("shy"::equals),
                "không được lọt vào danh sách ứng viên của bộ thẻ");
    }

    @Test
    void swipeLeftHidesCardAndSwipeRightIsRecorded() {
        users.add("me", "ADULT", true);
        users.add("a", "ADULT", true);
        users.add("b", "ADULT", true);
        svc.updateLocation("me", LAT, LON);
        place("a", 100);
        place("b", 200);

        assertEquals("SKIPPED", svc.swipe("me", "a", DiscoveryDtos.Direction.LEFT).result());
        assertEquals(List.of("b"), cardIds("me"));
        assertEquals("REQUESTED", svc.swipe("me", "b", DiscoveryDtos.Direction.RIGHT).result());
        assertTrue(cardIds("me").isEmpty(), "đã quẹt phải thì không hiện lại");

        // Quẹt trái chỉ nhớ 30 ngày.
        clock.now = clock.now.plus(Duration.ofDays(31));
        assertEquals(List.of("a"), cardIds("me"));
    }

    @Test
    void rightSwipesAreLimitedPerDayAndFailuresAreRefunded() {
        users.add("me", "ADULT", true);
        for (int i = 0; i < 6; i++) {
            users.add("t" + i, "ADULT", true);
        }
        // hạn mức free = 3
        for (int i = 0; i < 3; i++) {
            assertEquals(2 - i, svc.swipe("me", "t" + i, DiscoveryDtos.Direction.RIGHT).swipesLeftToday());
        }
        ApiException e = assertThrows(ApiException.class, () -> svc.swipe("me", "t3", DiscoveryDtos.Direction.RIGHT));
        assertEquals("SWIPE_LIMIT", e.code());

        // Sang ngày mới (giờ Việt Nam) thì được quẹt tiếp.
        clock.now = clock.now.plus(Duration.ofHours(24));
        assertEquals("REQUESTED", svc.swipe("me", "t3", DiscoveryDtos.Direction.RIGHT).result());
    }

    @Test
    void blockedOrAlreadyFriendsOrServiceErrorsDoNotConsumeQuota() {
        users.add("me", "ADULT", true);
        users.add("x", "ADULT", true);
        users.swipeResult = "BLOCKED";
        assertEquals(3, svc.swipe("me", "x", DiscoveryDtos.Direction.RIGHT).swipesLeftToday());
        users.swipeResult = "ALREADY_FRIENDS";
        assertEquals(3, svc.swipe("me", "x", DiscoveryDtos.Direction.RIGHT).swipesLeftToday());
        users.swipeFails = true;
        assertThrows(ApiException.class, () -> svc.swipe("me", "x", DiscoveryDtos.Direction.RIGHT));
        users.swipeFails = false;
        users.swipeResult = "MATCHED";
        assertEquals(2, svc.swipe("me", "x", DiscoveryDtos.Direction.RIGHT).swipesLeftToday());
    }

    @Test
    void cannotSwipeSelf() {
        assertThrows(ApiException.class, () -> svc.swipe("me", "me", DiscoveryDtos.Direction.RIGHT));
    }

    @Test
    void turningDiscoveryOffRemovesLocationButKeepsSwipeHistory() {
        users.add("me", "ADULT", true);
        users.add("a", "ADULT", true);
        users.add("b", "ADULT", true);
        svc.updateLocation("me", LAT, LON);
        place("a", 100);
        place("b", 200);
        svc.swipe("me", "a", DiscoveryDtos.Direction.LEFT);

        svc.clearLocation("me");

        assertTrue(store.location("ADULT", "me").isEmpty(), "vị trí phải bị xoá ngay");
        users.add("me", "ADULT", true); // bật lại
        svc.updateLocation("me", LAT, LON);
        assertEquals(List.of("b"), cardIds("me"), "người đã quẹt trái vẫn bị ẩn sau khi bật lại");
    }

    private List<String> boostedIds() {
        return store.boosted(clock.instant()).stream().sorted().toList();
    }

    @Test
    void boostedUsersComeFirstAndBoostNeedsBalance() {
        users.add("me", "ADULT", true);
        users.add("near", "ADULT", true);
        users.add("mid", "ADULT", true);
        svc.updateLocation("me", LAT, LON);
        place("near", 300);
        place("mid", 4000);

        svc.boost("mid", null);
        var cards = svc.cards("me", 10).cards();
        assertEquals("mid", cards.get(0).publicId());
        assertTrue(cards.get(0).boosted());

        clock.now = clock.now.plus(Duration.ofMinutes(31)); // hết 30 phút
        assertEquals("near", svc.cards("me", 10).cards().get(0).publicId());

        billing.ok = false;
        ApiException e = assertThrows(ApiException.class, () -> svc.boost("near", null));
        assertEquals("NO_BOOST", e.code());
        assertEquals(402, e.status().value());
        assertEquals(List.of("mid"), billing.consumed.stream().map(s -> s.split(":")[0]).toList(),
                "hết Boost thì không trừ gì thêm");
    }

    @Test
    void repeatedKeyDoesNotConsumeTwice() {
        users.add("me", "ADULT", true);

        svc.boost("me", "key-1");
        svc.boost("me", "key-1");

        assertEquals(1, billing.consumed.size());
        assertEquals(List.of("me:key-1"), billing.consumed, "khoá được chuyển nguyên cho billing");
        assertEquals(List.of("me"), boostedIds());
    }

    @Test
    void requestsWithoutKeyOrWithNewKeyEachConsumeOnce() {
        users.add("me", "ADULT", true);

        svc.boost("me", null);
        svc.boost("me", " ");
        svc.boost("me", "key-2");

        assertEquals(3, billing.consumed.size());
        assertEquals(3, billing.consumed.stream().distinct().count(), "mỗi yêu cầu có khoá riêng");
    }

    @Test
    void storeFailureIsRetriedOnceBeforeGivingUp() {
        users.add("me", "ADULT", true);
        store.boostFailures = 1;

        svc.boost("me", "key-1");

        assertEquals(1, billing.consumed.size());
        assertEquals(List.of("me"), boostedIds());
    }

    @Test
    void storeFailureThenRetryWithSameKeyAppliesWithoutSecondConsume() {
        users.add("me", "ADULT", true);
        store.boostFailures = 2; // lần ghi đầu và lần thử lại đều lỗi

        ApiException e = assertThrows(ApiException.class, () -> svc.boost("me", "key-1"));
        assertEquals(503, e.status().value());
        assertEquals("BOOST_PENDING", e.code());
        assertEquals(1, billing.consumed.size(), "ví đã bị trừ một lần");
        assertTrue(boostedIds().isEmpty());

        svc.boost("me", "key-1"); // client gửi lại cùng khoá, store đã khoẻ

        assertEquals(1, billing.consumed.size(), "không được trừ Boost lần hai");
        assertEquals(List.of("me"), boostedIds());

        svc.boost("me", "key-1"); // đã APPLIED: thành công mà không làm gì thêm
        assertEquals(1, billing.consumed.size());
    }

    @Test
    void concurrentDuplicateWhileKeyIsPendingGetsConflictAndDoesNotConsume() {
        users.add("me", "ADULT", true);
        kv.set("boost:idem:me:key-1", "PENDING", Duration.ofHours(24)); // yêu cầu đầu còn đang gọi billing

        ApiException e = assertThrows(ApiException.class, () -> svc.boost("me", "key-1"));

        assertEquals(409, e.status().value());
        assertEquals("BOOST_IN_PROGRESS", e.code());
        assertEquals(0, billing.consumed.size());
        assertTrue(boostedIds().isEmpty());
    }

    @Test
    void failedConsumeDoesNotRememberTheKey() {
        users.add("me", "ADULT", true);
        billing.ok = false;
        assertThrows(ApiException.class, () -> svc.boost("me", "key-1"));
        assertEquals(0, billing.consumed.size());

        billing.ok = true; // nạp thêm Boost rồi thử lại cùng khoá
        svc.boost("me", "key-1");
        assertEquals(1, billing.consumed.size());
        assertEquals(List.of("me"), boostedIds());
    }

    @Test
    void keysAreScopedPerUser() {
        users.add("a", "ADULT", true);
        users.add("b", "ADULT", true);

        svc.boost("a", "same-key");
        svc.boost("b", "same-key");

        assertEquals(2, billing.consumed.size());
    }
}
