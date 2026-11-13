package com.animallocket.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bảng route: mỗi path công khai tới đúng service, /internal/** và path lạ không có route. */
@SpringBootTest
@ActiveProfiles("test")
class RouteTableTest {
    @Autowired RouteLocator locator;

    private List<Route> routes() {
        return locator.getRoutes().collectList().block();
    }

    private List<String> matching(String method, String path) {
        return Flux.fromIterable(routes())
                .filterWhen(r -> Mono.from(r.getPredicate().apply(
                        MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path)))))
                .map(Route::getId)
                .collectList().block();
    }

    @Test
    void exactlyTheExpectedRoutesExist() {
        Set<String> ids = routes().stream().map(Route::getId).collect(Collectors.toSet());
        assertEquals(Set.of("user-service", "post-service", "media-service", "discovery-service",
                "chat-ws", "chat-service", "billing-service"), ids);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/users/register, user-service",
            "GET, /api/v1/users/me/animals, user-service",
            "GET, /api/v1/friends, user-service",
            "POST, /api/v1/friends/requests/1/accept, user-service",
            "PUT, /api/v1/blocks/abc, user-service",
            "POST, /api/v1/reports, user-service",
            "GET, /api/v1/animals/avatar-lib/3, user-service",
            "PUT, /api/v1/devices, user-service",
            "POST, /api/v1/posts, post-service",
            "DELETE, /api/v1/posts/1/comments/2, post-service",
            "GET, /api/v1/feed, post-service",
            "POST, /api/v1/media/uploads, media-service",
            "GET, /api/v1/discovery/cards, discovery-service",
            "GET, /ws/chat, chat-ws",
            "GET, /api/v1/chat/conversations, chat-service",
            "GET, /api/v1/billing/entitlements, billing-service",
            "POST, /webhooks/paypal, billing-service",
            "GET, /pay/abc123, billing-service",
    })
    void publicPathsRouteToTheOwningService(String method, String path, String routeId) {
        assertEquals(List.of(routeId), matching(method, path), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/identity/sub", "/internal/media/1", "/api/v1/internal/x", "/api/v1/userss", "/actuator/env", "/"})
    void internalAndUnknownPathsHaveNoRoute(String path) {
        assertEquals(List.of(), matching("GET", path), path);
    }

    @Test
    void chatWebSocketGoesThroughLoadBalancer() {
        Route ws = routes().stream().filter(r -> r.getId().equals("chat-ws")).findFirst().orElseThrow();
        assertEquals("lb:ws://chat-service", ws.getUri().toString());
    }

    @Test
    void discoveryHasItsOwnCircuitBreakerAndRateLimitOnTopOfDefaults() {
        int defaults = routes().stream().filter(r -> r.getId().equals("post-service")).findFirst().orElseThrow().getFilters().size();
        Route discovery = routes().stream().filter(r -> r.getId().equals("discovery-service")).findFirst().orElseThrow();
        assertTrue(defaults >= 2, "default-filters: CircuitBreaker + RequestRateLimiter");
        assertEquals(defaults + 2, discovery.getFilters().size());
    }
}
