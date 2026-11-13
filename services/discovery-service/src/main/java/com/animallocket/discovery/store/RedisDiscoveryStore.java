package com.animallocket.discovery.store;

import com.animallocket.discovery.geo.LocationPrivacy.LatLon;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cấu trúc khoá Redis:
 * <pre>
 * geo:{ageGroup}          GEO   member = publicId (vị trí đã làm nhiễu)
 * geo:exp:{ageGroup}      ZSET  member = publicId, score = thời điểm hết hạn (giây) — vì GEO không có TTL từng member
 * online:{ageGroup}       ZSET  member = publicId, score = lần heartbeat cuối (giây)
 * boost                   ZSET  member = publicId, score = thời điểm hết boost (giây)
 * swipe:left:{me}         ZSET  member = target, score = thời điểm quẹt (giây), key TTL 35 ngày
 * swipe:right:{me}        SET   member = target, key TTL 35 ngày
 * </pre>
 */
public class RedisDiscoveryStore implements DiscoveryStore {
    private static final String[] AGE_GROUPS = {"MINOR", "ADULT"};
    private static final Duration SWIPE_KEY_TTL = Duration.ofDays(35);

    private final StringRedisTemplate redis;

    public RedisDiscoveryStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void putLocation(String ageGroup, String id, LatLon pos, Instant expiresAt) {
        for (String g : AGE_GROUPS) {
            if (!g.equals(ageGroup)) {
                redis.opsForGeo().remove(geoKey(g), id);
                redis.opsForZSet().remove(expKey(g), id);
            }
        }
        redis.opsForGeo().add(geoKey(ageGroup), new Point(pos.lon(), pos.lat()), id);
        redis.opsForZSet().add(expKey(ageGroup), id, expiresAt.getEpochSecond());
    }

    @Override
    public Optional<LatLon> location(String ageGroup, String id) {
        List<Point> pts = redis.opsForGeo().position(geoKey(ageGroup), id);
        if (pts == null || pts.isEmpty() || pts.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(new LatLon(pts.get(0).getY(), pts.get(0).getX()));
    }

    @Override
    public List<Neighbor> nearby(String ageGroup, LatLon c, double radiusMeters, int limit) {
        var res = redis.opsForGeo().search(geoKey(ageGroup),
                GeoReference.fromCoordinate(c.lon(), c.lat()),
                new Distance(radiusMeters, RedisGeoCommands.DistanceUnit.METERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending().limit(limit));
        if (res == null) {
            return List.of();
        }
        return res.getContent().stream()
                .map(r -> new Neighbor(r.getContent().getName(), r.getDistance().getValue())).toList();
    }

    @Override
    public void touchOnline(String ageGroup, String id, Instant now) {
        redis.opsForZSet().add(onlineKey(ageGroup), id, now.getEpochSecond());
    }

    @Override
    public List<String> online(String ageGroup, Instant since, int limit) {
        Set<String> s = redis.opsForZSet().reverseRangeByScore(onlineKey(ageGroup), since.getEpochSecond() + 1,
                Double.POSITIVE_INFINITY, 0, limit);
        return s == null ? List.of() : List.copyOf(s);
    }

    @Override
    public Map<String, Boolean> onlineStatus(Set<String> ids, Instant since) {
        Map<String, Boolean> out = new HashMap<>();
        for (String id : ids) {
            boolean on = false;
            for (String g : AGE_GROUPS) {
                Double score = redis.opsForZSet().score(onlineKey(g), id);
                if (score != null && score > since.getEpochSecond()) {
                    on = true;
                }
            }
            out.put(id, on);
        }
        return out;
    }

    @Override
    public void boost(String id, Instant until) {
        redis.opsForZSet().add("boost", id, until.getEpochSecond());
    }

    @Override
    public Set<String> boosted(Instant now) {
        Set<String> s = redis.opsForZSet().rangeByScore("boost", now.getEpochSecond() + 1, Double.POSITIVE_INFINITY);
        return s == null ? Set.of() : new HashSet<>(s);
    }

    @Override
    public void recordLeft(String me, String target, Instant at) {
        String key = "swipe:left:" + me;
        redis.opsForZSet().add(key, target, at.getEpochSecond());
        redis.expire(key, SWIPE_KEY_TTL);
    }

    @Override
    public Set<String> recentLeft(String me, Instant since) {
        Set<String> s = redis.opsForZSet().rangeByScore("swipe:left:" + me, since.getEpochSecond() + 1, Double.POSITIVE_INFINITY);
        return s == null ? Set.of() : new HashSet<>(s);
    }

    @Override
    public void recordRight(String me, String target, Instant at) {
        String key = "swipe:right:" + me;
        redis.opsForSet().add(key, target);
        redis.expire(key, SWIPE_KEY_TTL);
    }

    @Override
    public Set<String> rights(String me) {
        Set<String> s = redis.opsForSet().members("swipe:right:" + me);
        return s == null ? Set.of() : new HashSet<>(s);
    }

    @Override
    public int purgeExpired(Instant now) {
        int n = 0;
        double nowS = now.getEpochSecond();
        for (String g : AGE_GROUPS) {
            Set<String> dead = redis.opsForZSet().rangeByScore(expKey(g), Double.NEGATIVE_INFINITY, nowS);
            if (dead != null && !dead.isEmpty()) {
                redis.opsForGeo().remove(geoKey(g), dead.toArray(new String[0]));
                redis.opsForZSet().remove(expKey(g), dead.toArray());
                n += dead.size();
            }
            Long old = redis.opsForZSet().removeRangeByScore(onlineKey(g), Double.NEGATIVE_INFINITY, nowS - 3600);
            n += old == null ? 0 : old.intValue();
        }
        redis.opsForZSet().removeRangeByScore("boost", Double.NEGATIVE_INFINITY, nowS);
        return n;
    }

    @Override
    public void removePresence(String id) {
        for (String g : AGE_GROUPS) {
            redis.opsForGeo().remove(geoKey(g), id);
            redis.opsForZSet().remove(expKey(g), id);
            redis.opsForZSet().remove(onlineKey(g), id);
        }
        redis.opsForZSet().remove("boost", id);
    }

    @Override
    public void forget(String id) {
        for (String g : AGE_GROUPS) {
            redis.opsForGeo().remove(geoKey(g), id);
            redis.opsForZSet().remove(expKey(g), id);
            redis.opsForZSet().remove(onlineKey(g), id);
        }
        redis.opsForZSet().remove("boost", id);
        redis.delete(List.of("swipe:left:" + id, "swipe:right:" + id));
    }

    private static String geoKey(String g) {
        return "geo:" + g;
    }

    private static String expKey(String g) {
        return "geo:exp:" + g;
    }

    private static String onlineKey(String g) {
        return "online:" + g;
    }
}
