package com.animallocket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class KeyResolverTest {
    final GatewayConfig config = new GatewayConfig();

    String key(MockServerHttpRequest.BaseBuilder<?> b) {
        return config.userOrIpKeyResolver().resolve(MockServerWebExchange.from(b.build())).block();
    }

    @Test
    void authenticatedUsersAreKeyedByPublicId() {
        assertEquals("u:abc", key(MockServerHttpRequest.get("/x").header("X-Public-Id", "abc")));
    }

    @Test
    void blankPublicIdIsIgnored() {
        assertEquals("ip:10.0.0.5", key(MockServerHttpRequest.get("/x").header("X-Public-Id", " ")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 1))));
    }

    @Test
    void anonymousClientsBehindTraefikGetOneBucketEach() {
        // Cùng IP kết nối (Traefik) nhưng khác X-Forwarded-For => khác bucket, không gộp mọi người làm một.
        var proxy = new InetSocketAddress("10.0.0.5", 1234);
        String a = key(MockServerHttpRequest.get("/x").remoteAddress(proxy).header("X-Forwarded-For", "203.0.113.7"));
        String b = key(MockServerHttpRequest.get("/x").remoteAddress(proxy).header("X-Forwarded-For", "198.51.100.9"));
        assertEquals("ip:203.0.113.7", a);
        assertNotEquals(a, b);
    }

    @Test
    void spoofedForwardedChainOnlyTrustsTheLastHop() {
        // Kẻ tấn công gửi sẵn "X-Forwarded-For: 1.2.3.4"; Traefik nối IP thật vào cuối => chỉ tin phần tử cuối.
        var proxy = new InetSocketAddress("10.0.0.5", 1234);
        String k = key(MockServerHttpRequest.get("/x").remoteAddress(proxy).header("X-Forwarded-For", "1.2.3.4, 203.0.113.7"));
        assertEquals("ip:203.0.113.7", k);
    }

    @Test
    void unknownWhenNoAddressAtAll() {
        assertEquals("ip:unknown", key(MockServerHttpRequest.get("/x")));
    }

    @Test
    void fallsBackToConnectionAddressWithoutHeader() {
        assertEquals("ip:10.0.0.5", key(MockServerHttpRequest.get("/x").remoteAddress(new InetSocketAddress("10.0.0.5", 1))));
    }
}
