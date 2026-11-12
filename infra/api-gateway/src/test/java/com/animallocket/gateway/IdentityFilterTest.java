package com.animallocket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityFilterTest {
    final AtomicInteger userServiceCalls = new AtomicInteger();
    final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    final GatewayFilterChain chain = ex -> {
        forwarded.set(ex);
        return Mono.empty();
    };

    private IdentityFilter filter(ExchangeFunction userService) {
        return new IdentityFilter(WebClient.builder().exchangeFunction(userService), "internal-secret");
    }

    private ExchangeFunction ok(String publicId) {
        return req -> {
            userServiceCalls.incrementAndGet();
            assertEquals("internal-secret", req.headers().getFirst("X-Internal-Token"));
            assertTrue(req.url().getPath().startsWith("/internal/identity/"), req.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"publicId\":\"" + publicId + "\"}").build());
        };
    }

    private static JwtAuthenticationToken auth(String sub) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt);
    }

    private Mono<Void> run(IdentityFilter f, MockServerWebExchange ex, JwtAuthenticationToken auth) {
        Mono<Void> m = f.filter(ex, chain);
        return auth == null ? m : m.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    @Test
    void clientSuppliedPublicIdIsAlwaysStripped() {
        var ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/feed").header("X-Public-Id", "attacker-chosen-id"));
        StepVerifier.create(run(filter(ok("never-called")), ex, null)).verifyComplete();
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Public-Id"), "header giả mạo phải bị xoá");
        assertEquals(0, userServiceCalls.get());
    }

    @Test
    void authenticatedRequestsGetTheRealPublicIdEvenWhenClientSendsAnother() {
        var ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/feed").header("x-public-id", "attacker-chosen-id"));
        StepVerifier.create(run(filter(ok("realpublicid0000000x")), ex, auth("kc-sub-1"))).verifyComplete();
        var headers = forwarded.get().getRequest().getHeaders();
        assertEquals("realpublicid0000000x", headers.getFirst("X-Public-Id"));
        assertEquals(1, headers.get("X-Public-Id").size());
    }

    @Test
    void resolvedIdentitiesAreCached() {
        var f = filter(ok("cachedid000000000000"));
        for (int i = 0; i < 3; i++) {
            var ex = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
            StepVerifier.create(run(f, ex, auth("kc-sub-2"))).verifyComplete();
        }
        assertEquals(1, userServiceCalls.get(), "chỉ gọi user-service một lần cho cùng một sub");
    }

    @Test
    void usersWithoutProfileContinueWithoutHeaderAndAreNotCached() {
        ExchangeFunction notFound = req -> {
            userServiceCalls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        };
        var f = filter(notFound);
        for (int i = 0; i < 2; i++) {
            var ex = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/users/register"));
            StepVerifier.create(run(f, ex, auth("brand-new-sub"))).verifyComplete();
            assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Public-Id"));
        }
        assertEquals(2, userServiceCalls.get(), "404 không được cache: người dùng sắp đăng ký xong");
    }

    @Test
    void userServiceFailureFailsClosedWith503() {
        ExchangeFunction down = req -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
        var ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/feed"));
        StepVerifier.create(run(filter(down), ex, auth("sub-x"))).verifyComplete();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getResponse().getStatusCode());
        assertNull(forwarded.get(), "không được cho đi tiếp ẩn danh khi không xác định được người dùng");
    }

    @Test
    void connectionErrorAlsoFailsClosed() {
        ExchangeFunction broken = req -> Mono.error(new java.net.ConnectException("refused"));
        var ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/feed"));
        StepVerifier.create(run(filter(broken), ex, auth("sub-y"))).verifyComplete();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getResponse().getStatusCode());
        assertNull(forwarded.get());
    }

    @Test
    void blankInternalTokenFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new IdentityFilter(WebClient.builder(), " "));
        assertThrows(IllegalStateException.class, () -> new IdentityFilter(WebClient.builder(), null));
    }

    @Test
    void runsBeforeRouteFilters() {
        assertEquals(-1, filter(ok("x")).getOrder());
    }
}
