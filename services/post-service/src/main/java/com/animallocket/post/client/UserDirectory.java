package com.animallocket.post.client;

import com.animallocket.common.Headers;
import com.animallocket.common.kv.KeyValueStore;
import com.animallocket.common.web.ApiException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cổng sang user-service: danh sách bạn (cache Redis 45 giây + bản dự phòng 1 giờ) và hồ sơ hiển thị.
 * Chính sách chịu lỗi: user-service chết thì dùng cache; không có cache thì lỗi 503 (feed sẽ ẩn bài).
 */
@Component
public class UserDirectory {
    private static final Logger log = LoggerFactory.getLogger(UserDirectory.class);
    private static final Duration FRESH = Duration.ofSeconds(45);
    private static final Duration STALE = Duration.ofHours(1);

    public record Profile(String publicId, String alias, String leadAnimal, String avatar) {
    }

    @Configuration
    static class Config {
        @Bean
        @LoadBalanced
        RestClient.Builder loadBalancedRestClientBuilder() {
            return RestClient.builder();
        }
    }

    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> breakers;
    private final KeyValueStore kv;
    private final Cache<String, Profile> profileCache = Caffeine.newBuilder()
            .maximumSize(50_000).expireAfterWrite(Duration.ofMinutes(5)).build();

    public UserDirectory(RestClient.Builder lbBuilder, CircuitBreakerFactory<?, ?> breakers, KeyValueStore kv,
                         @Value("${animal.user-service-url:http://user-service}") String baseUrl,
                         @Value("${animal.internal-token:dev-internal-token}") String token) {
        this.client = lbBuilder.baseUrl(baseUrl).defaultHeader(Headers.INTERNAL_TOKEN, token).build();
        this.breakers = breakers;
        this.kv = kv;
    }

    public Set<String> friendIds(String me) {
        Optional<String> fresh = kv.get(freshKey(me));
        if (fresh.isPresent()) {
            return decode(fresh.get());
        }
        try {
            List<String> ids = breakers.create("user-service").run(() ->
                    client.get().uri("/internal/friends/{id}", me).retrieve()
                            .body(new ParameterizedTypeReference<List<String>>() {
                            }), t -> {
                throw new IllegalStateException(t);
            });
            Set<String> set = new HashSet<>(ids == null ? List.of() : ids);
            String enc = String.join(",", set);
            kv.set(freshKey(me), enc, FRESH);
            kv.set(staleKey(me), enc, STALE);
            return set;
        } catch (RuntimeException e) {
            Optional<String> stale = kv.get(staleKey(me));
            if (stale.isPresent()) {
                log.warn("user-service lỗi, dùng danh sách bạn cũ của {}: {}", me, e.toString());
                return decode(stale.get());
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FRIENDS_UNAVAILABLE",
                    "Tạm thời không tải được danh sách bạn bè");
        }
    }

    public boolean areFriends(String a, String b) {
        return friendIds(a).contains(b);
    }

    public void invalidateFriends(String... ids) {
        for (String id : ids) {
            kv.delete(freshKey(id), staleKey(id));
        }
    }

    /** Fail-soft: không lấy được hồ sơ thì trả map thiếu, UI vẫn hiển thị bài. */
    public Map<String, Profile> profiles(Set<String> ids) {
        Map<String, Profile> out = new HashMap<>();
        Set<String> missing = new HashSet<>();
        for (String id : ids) {
            Profile p = profileCache.getIfPresent(id);
            if (p != null) {
                out.put(id, p);
            } else {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return out;
        }
        try {
            List<Profile> fetched = client.get().uri(b -> b.path("/internal/users").queryParam("ids", String.join(",", missing)).build())
                    .retrieve().body(new ParameterizedTypeReference<List<Profile>>() {
                    });
            if (fetched != null) {
                for (Profile p : fetched) {
                    profileCache.put(p.publicId(), p);
                    out.put(p.publicId(), p);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Không lấy được hồ sơ hiển thị: {}", e.toString());
        }
        return out;
    }

    private static Set<String> decode(String csv) {
        return csv.isBlank() ? new HashSet<>() : Arrays.stream(csv.split(",")).collect(Collectors.toSet());
    }

    private static String freshKey(String id) {
        return "friends:" + id;
    }

    private static String staleKey(String id) {
        return "friends:stale:" + id;
    }
}
