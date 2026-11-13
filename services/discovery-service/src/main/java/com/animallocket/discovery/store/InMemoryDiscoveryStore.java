package com.animallocket.discovery.store;

import com.animallocket.discovery.geo.LocationPrivacy;
import com.animallocket.discovery.geo.LocationPrivacy.LatLon;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Cài đặt bộ nhớ: dùng cho test và chạy dev không cần Redis. Hành vi phải khớp RedisDiscoveryStore. */
public class InMemoryDiscoveryStore implements DiscoveryStore {
    private record Loc(LatLon pos, Instant expires) {
    }

    private final Map<String, Map<String, Loc>> geo = new ConcurrentHashMap<>();           // ageGroup → id → loc
    private final Map<String, Map<String, Instant>> online = new ConcurrentHashMap<>();    // ageGroup → id → lastSeen
    private final Map<String, Instant> boosts = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Instant>> left = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> right = new ConcurrentHashMap<>();

    @Override
    public void putLocation(String ageGroup, String id, LatLon pos, Instant expiresAt) {
        // Người dùng chỉ ở một nhóm tuổi; xoá ở nhóm kia phòng khi dữ liệu cũ còn sót.
        geo.forEach((g, m) -> {
            if (!g.equals(ageGroup)) {
                m.remove(id);
            }
        });
        geo.computeIfAbsent(ageGroup, k -> new ConcurrentHashMap<>()).put(id, new Loc(pos, expiresAt));
    }

    @Override
    public Optional<LatLon> location(String ageGroup, String id) {
        Loc l = geo.getOrDefault(ageGroup, Map.of()).get(id);
        return l == null ? Optional.empty() : Optional.of(l.pos());
    }

    @Override
    public List<Neighbor> nearby(String ageGroup, LatLon center, double radius, int limit) {
        return geo.getOrDefault(ageGroup, Map.of()).entrySet().stream()
                .map(e -> new Neighbor(e.getKey(), LocationPrivacy.distanceMeters(center, e.getValue().pos())))
                .filter(n -> n.distanceMeters() <= radius)
                .sorted(Comparator.comparingDouble(Neighbor::distanceMeters))
                .limit(limit).toList();
    }

    @Override
    public void touchOnline(String ageGroup, String id, Instant now) {
        online.computeIfAbsent(ageGroup, k -> new ConcurrentHashMap<>()).put(id, now);
    }

    @Override
    public List<String> online(String ageGroup, Instant since, int limit) {
        return online.getOrDefault(ageGroup, Map.of()).entrySet().stream()
                .filter(e -> e.getValue().isAfter(since))
                .sorted(Map.Entry.<String, Instant>comparingByValue().reversed())
                .limit(limit).map(Map.Entry::getKey).toList();
    }

    @Override
    public Map<String, Boolean> onlineStatus(Set<String> ids, Instant since) {
        Map<String, Boolean> out = new HashMap<>();
        for (String id : ids) {
            out.put(id, online.values().stream().anyMatch(m -> {
                Instant t = m.get(id);
                return t != null && t.isAfter(since);
            }));
        }
        return out;
    }

    @Override
    public void boost(String id, Instant until) {
        boosts.put(id, until);
    }

    @Override
    public Set<String> boosted(Instant now) {
        return boosts.entrySet().stream().filter(e -> e.getValue().isAfter(now))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    @Override
    public void recordLeft(String me, String target, Instant at) {
        left.computeIfAbsent(me, k -> new ConcurrentHashMap<>()).put(target, at);
    }

    @Override
    public Set<String> recentLeft(String me, Instant since) {
        return left.getOrDefault(me, Map.of()).entrySet().stream().filter(e -> e.getValue().isAfter(since))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    @Override
    public void recordRight(String me, String target, Instant at) {
        right.computeIfAbsent(me, k -> ConcurrentHashMap.newKeySet()).add(target);
    }

    @Override
    public Set<String> rights(String me) {
        return new HashSet<>(right.getOrDefault(me, Set.of()));
    }

    @Override
    public int purgeExpired(Instant now) {
        int n = 0;
        for (Map<String, Loc> m : geo.values()) {
            int before = m.size();
            m.values().removeIf(l -> !l.expires().isAfter(now));
            n += before - m.size();
        }
        for (Map<String, Instant> m : online.values()) {
            int before = m.size();
            m.values().removeIf(t -> t.isBefore(now.minusSeconds(3600)));
            n += before - m.size();
        }
        boosts.values().removeIf(t -> !t.isAfter(now));
        return n;
    }

    @Override
    public void removePresence(String id) {
        geo.values().forEach(m -> m.remove(id));
        online.values().forEach(m -> m.remove(id));
        boosts.remove(id);
    }

    @Override
    public void forget(String id) {
        geo.values().forEach(m -> m.remove(id));
        online.values().forEach(m -> m.remove(id));
        boosts.remove(id);
        left.remove(id);
        right.remove(id);
        left.values().forEach(m -> m.remove(id));
        right.values().forEach(s -> s.remove(id));
    }
}
