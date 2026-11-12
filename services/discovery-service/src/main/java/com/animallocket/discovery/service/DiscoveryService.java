package com.animallocket.discovery.service;

import com.animallocket.common.kv.KeyValueStore;
import com.animallocket.common.web.ApiException;
import com.animallocket.discovery.client.Gateways;
import com.animallocket.discovery.geo.LocationPrivacy;
import com.animallocket.discovery.geo.LocationPrivacy.LatLon;
import com.animallocket.discovery.store.DiscoveryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DiscoveryService {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(60);
    private static final Duration LEFT_MEMORY = Duration.ofDays(30);
    static final Duration BOOST_DURATION = Duration.ofMinutes(30);
    /** Khoá idempotency của Boost nhớ 24 giờ: đủ cho client retry, không giữ vô hạn. */
    static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String PENDING = "PENDING";
    private static final String CONSUMED = "CONSUMED";
    private static final String APPLIED = "APPLIED";

    private final DiscoveryStore store;
    private final Gateways.UserGateway users;
    private final Gateways.BillingGateway billing;
    private final LocationPrivacy privacy;
    private final KeyValueStore kv;
    private final Clock clock;
    private final int swipesFree;
    private final int swipesPro;
    private final Duration locationTtl;
    private final double radiusAdult;
    private final double radiusMinor;
    private final int cardsPerMinute;

    public DiscoveryService(DiscoveryStore store, Gateways.UserGateway users,
                            Gateways.BillingGateway billing,
                            LocationPrivacy privacy, KeyValueStore kv, Clock clock,
                            @Value("${animal.limits.swipes-per-day.free:30}") int swipesFree,
                            @Value("${animal.limits.swipes-per-day.pro:150}") int swipesPro,
                            @Value("${animal.discovery.location-ttl:30m}") Duration locationTtl,
                            @Value("${animal.discovery.radius-adult-m:10000}") double radiusAdult,
                            @Value("${animal.discovery.radius-minor-m:5000}") double radiusMinor,
                            @Value("${animal.discovery.cards-per-minute:10}") int cardsPerMinute) {
        this.store = store;
        this.users = users;
        this.billing = billing;
        this.privacy = privacy;
        this.kv = kv;
        this.clock = clock;
        this.swipesFree = swipesFree;
        this.swipesPro = swipesPro;
        this.locationTtl = locationTtl;
        this.radiusAdult = radiusAdult;
        this.radiusMinor = radiusMinor;
        this.cardsPerMinute = cardsPerMinute;
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

    // ---------------- bộ thẻ ----------------

    public DiscoveryDtos.CardsResponse cards(String me, int limit) {
        long minute = clock.instant().getEpochSecond() / 60;
        if (kv.increment("rl:cards:" + me + ":" + minute, Duration.ofMinutes(2)) > cardsPerMinute) {
            throw ApiException.tooMany("TOO_MANY_SEARCHES", "Bạn tìm quá nhanh, vui lòng thử lại sau ít phút");
        }
        Gateways.Profile self = requireDiscoverable(me);
        int size = Math.max(1, Math.min(limit, 30));
        boolean minor = "MINOR".equals(self.ageGroup());
        Instant now = clock.instant();

        Gateways.Exclusions ex = users.exclusions(me);
        Set<String> excluded = new HashSet<>(ex.friends());
        excluded.addAll(ex.blockedEitherWay());
        excluded.add(me);
        excluded.addAll(store.recentLeft(me, now.minus(LEFT_MEMORY)));
        excluded.addAll(store.rights(me));

        Map<String, Double> distance = new HashMap<>();
        java.util.Optional<LatLon> center = store.location(self.ageGroup(), me);
        center.ifPresent(c -> store.nearby(self.ageGroup(), c, minor ? radiusMinor : radiusAdult, size * 6)
                .forEach(n -> distance.put(n.publicId(), n.distanceMeters())));
        List<String> onlineIds = store.online(self.ageGroup(), now.minus(ONLINE_WINDOW), size * 6);
        Set<String> onlineSet = new HashSet<>(onlineIds);

        Set<String> candidateIds = new HashSet<>(distance.keySet());
        candidateIds.addAll(onlineIds);
        candidateIds.removeAll(excluded);
        if (candidateIds.isEmpty()) {
            return new DiscoveryDtos.CardsResponse(List.of(), false, swipesLeft(me));
        }

        // Hồ sơ lấy mới từ user-service để chắc chắn người đó vẫn bật khám phá, còn hoạt động và cùng nhóm tuổi.
        Map<String, Gateways.Profile> profiles = users.profiles(new ArrayList<>(candidateIds));
        Set<String> boosted = store.boosted(now);

        List<DiscoveryDtos.Card> cards = candidateIds.stream()
                .map(profiles::get)
                .filter(p -> p != null && p.active() && p.discoverable() && self.ageGroup().equals(p.ageGroup()))
                .sorted(Comparator
                        .comparing((Gateways.Profile p) -> !boosted.contains(p.publicId()))
                        .thenComparingDouble(p -> distance.getOrDefault(p.publicId(), Double.MAX_VALUE))
                        .thenComparing(p -> !onlineSet.contains(p.publicId()))
                        .thenComparing(Gateways.Profile::publicId))
                .limit(size)
                .map(p -> new DiscoveryDtos.Card(p.publicId(), p.alias(), p.leadAnimal(), p.avatar(),
                        distance.containsKey(p.publicId())
                                ? LocationPrivacy.band(distance.get(p.publicId()), minor) : null,
                        onlineSet.contains(p.publicId()), boosted.contains(p.publicId())))
                .toList();
        return new DiscoveryDtos.CardsResponse(cards, false, swipesLeft(me));
    }

    // ---------------- quẹt ----------------

    public DiscoveryDtos.SwipeResponse swipe(String me, String target, DiscoveryDtos.Direction dir) {
        if (me.equals(target)) {
            throw ApiException.badRequest("SELF_SWIPE", "Không thể quẹt chính mình");
        }
        Instant now = clock.instant();
        if (dir == DiscoveryDtos.Direction.LEFT) {
            store.recordLeft(me, target, now);
            return new DiscoveryDtos.SwipeResponse("SKIPPED", swipesLeft(me));
        }

        String key = swipeQuotaKey(me);
        long used = kv.increment(key, Duration.ofHours(30));
        if (used > limitFor(me)) {
            kv.decrement(key);
            throw ApiException.tooMany("SWIPE_LIMIT", "Bạn đã hết lượt quẹt phải hôm nay");
        }
        String result;
        try {
            result = users.swipeRight(me, target);
        } catch (RuntimeException e) {
            kv.decrement(key); // lỗi hệ thống không được trừ lượt của người dùng
            throw e;
        }
        if ("BLOCKED".equals(result) || "ALREADY_FRIENDS".equals(result) || "ALREADY_REQUESTED".equals(result)) {
            kv.decrement(key);
        }
        if (!"BLOCKED".equals(result)) {
            store.recordRight(me, target, now);
        }
        return new DiscoveryDtos.SwipeResponse(result, swipesLeft(me));
    }

    /**
     * Boost: trừ 1 đơn vị trong ví (billing-service), được ưu tiên trong bộ thẻ của người khác 30 phút.
     * <p>
     * billing-service chưa có endpoint hoàn trả, nên việc "trừ ví rồi ghi boost" được bảo vệ bằng khoá idempotency
     * lưu theo từng người và được chiếm nguyên tử: PENDING = đang gọi billing, CONSUMED = đã trừ ví nhưng chưa ghi boost,
     * APPLIED = đã xong. Client gửi lại cùng khoá
     * thì không bao giờ bị trừ lần hai. Không gửi khoá thì mỗi yêu cầu được một khoá mới.
     */
    public void boost(String me, String idempotencyKey) {
        requireDiscoverable(me);
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? UUID.randomUUID().toString() : idempotencyKey;
        String stateKey = "boost:idem:" + me + ":" + key;
        if (kv.setIfAbsent(stateKey, PENDING, IDEMPOTENCY_TTL)) {
            boolean consumed;
            try {
                consumed = billing.consumeBoost(me, key);
            } catch (RuntimeException e) {
                kv.delete(stateKey); // chưa trừ được ví thì cho client thử lại cùng khoá
                throw e;
            }
            if (!consumed) {
                kv.delete(stateKey);
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "NO_BOOST", "Bạn không còn Boost nào");
            }
            kv.set(stateKey, CONSUMED, IDEMPOTENCY_TTL);
        } else {
            String state = kv.get(stateKey).orElse(null);
            if (APPLIED.equals(state)) {
                return;
            }
            if (PENDING.equals(state)) {
                throw new ApiException(HttpStatus.CONFLICT, "BOOST_IN_PROGRESS", "Yêu cầu Boost cùng khoá đang được xử lý");
            }
            // CONSUMED (hoặc khoá vừa hết hạn): ví đã bị trừ ở lần trước, chỉ áp Boost.
        }
        applyBoost(me);
        kv.set(stateKey, APPLIED, IDEMPOTENCY_TTL);
    }

    /** Ghi boost vào store, thử lại một lần. Vẫn lỗi thì giữ khoá ở CONSUMED và báo 503 để client gửi lại cùng khoá. */
    private void applyBoost(String me) {
        Instant until = clock.instant().plus(BOOST_DURATION);
        try {
            store.boost(me, until);
        } catch (RuntimeException first) {
            log.warn("Ghi boost lỗi, thử lại một lần: {}", first.toString());
            try {
                store.boost(me, until);
            } catch (RuntimeException second) {
                log.error("Ghi boost vẫn lỗi sau khi đã trừ ví, chờ client gửi lại cùng khoá: {}", second.toString());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BOOST_PENDING",
                        "Boost đã được trừ nhưng chưa kích hoạt xong, vui lòng thử lại");
            }
        }
    }

    // ---------------- sự kiện ----------------

    public void forget(String publicId) {
        store.forget(publicId);
    }

    public void setPro(String publicId, Instant expiresAt) {
        Duration ttl = expiresAt == null ? Duration.ofDays(400) : Duration.between(clock.instant(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            kv.delete("pro:" + publicId);
        } else {
            kv.set("pro:" + publicId, "1", ttl);
        }
    }

    public void clearPro(String publicId) {
        kv.delete("pro:" + publicId);
    }

    public int purge() {
        try {
            return store.purgeExpired(clock.instant());
        } catch (RuntimeException e) {
            log.warn("purge lỗi: {}", e.toString());
            return 0;
        }
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

    private int limitFor(String me) {
        return kv.get("pro:" + me).isPresent() ? swipesPro : swipesFree;
    }

    private int swipesLeft(String me) {
        long used = kv.get(swipeQuotaKey(me)).map(Long::parseLong).orElse(0L);
        return (int) Math.max(0, limitFor(me) - used);
    }

    private String swipeQuotaKey(String me) {
        return "quota:swipe:" + me + ":" + DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(clock.withZone(ZONE)));
    }
}
