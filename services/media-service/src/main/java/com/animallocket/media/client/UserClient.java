package com.animallocket.media.client;

import com.animallocket.common.Headers;
import com.animallocket.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** Gọi đồng bộ sang user-service, có circuit breaker. Lỗi → fail closed (không cho đăng ảnh khi không biết con vật). */
@Component
public class UserClient {
    public record InternalUser(String publicId, String leadAnimal, String ageGroup, boolean active) {
    }

    public record Relationship(boolean friends, boolean blocked) {
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

    public UserClient(RestClient.Builder lbBuilder, CircuitBreakerFactory<?, ?> breakers,
                      @Value("${animal.user-service-url:http://user-service}") String baseUrl,
                      @Value("${animal.internal-token:dev-internal-token}") String token) {
        this.client = lbBuilder.baseUrl(baseUrl).defaultHeader(Headers.INTERNAL_TOKEN, token).build();
        this.breakers = breakers;
    }

    public Optional<InternalUser> user(String publicId) {
        return call(() -> {
            try {
                return Optional.ofNullable(client.get().uri("/internal/users/{id}", publicId)
                        .retrieve().body(InternalUser.class));
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                return Optional.<InternalUser>empty();
            }
        });
    }

    public Relationship relationship(String a, String b) {
        return call(() -> client.get().uri("/internal/relationship?a={a}&b={b}", a, b)
                .retrieve().body(Relationship.class));
    }

    private <T> T call(java.util.function.Supplier<T> supplier) {
        return breakers.create("user-service").run(supplier, t -> {
            if (t instanceof ApiException ae) {
                throw ae;
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE",
                    "Tạm thời không xác minh được người dùng, vui lòng thử lại");
        });
    }
}
