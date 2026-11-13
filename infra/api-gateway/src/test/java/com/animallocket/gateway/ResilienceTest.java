package com.animallocket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Service phía sau không có instance (không chạy Eureka) => circuit breaker chuyển sang fallback.
 * Không có Redis: RedisRateLimiter fail open nên request vẫn đi tiếp. Tắt JWT để IdentityFilter không gọi user-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "animal.security.enabled=false",
        "GATEWAY_CONNECT_TIMEOUT_MS=1500",
        "GATEWAY_RESPONSE_TIMEOUT=3s"
})
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class ResilienceTest {
    @Autowired WebTestClient web;
    @Autowired HttpClientProperties httpClient;

    @Test
    void unavailableServiceReturnsProblem503() {
        web.get().uri("/api/v1/posts/mine").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void discoveryFailsSoftWithEmptyCards() {
        web.get().uri("/api/v1/discovery/cards").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.degraded").isEqualTo(true).jsonPath("$.cards").isEmpty();
    }

    @Test
    void timeoutsAreReadFromConfig() {
        assertEquals(1500, httpClient.getConnectTimeout());
        assertEquals(Duration.ofSeconds(3), httpClient.getResponseTimeout());
    }
}
