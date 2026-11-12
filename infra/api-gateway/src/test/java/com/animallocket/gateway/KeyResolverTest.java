package com.animallocket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void fallsBackToConnectionAddressWithoutHeader() {
        assertEquals("ip:10.0.0.5", key(MockServerHttpRequest.get("/x").remoteAddress(new InetSocketAddress("10.0.0.5", 1))));
    }
}
