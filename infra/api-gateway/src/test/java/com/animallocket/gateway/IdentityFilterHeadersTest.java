package com.animallocket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Hồi quy: header request của Netty là ReadOnlyHttpHeaders; xoá trực tiếp ném UnsupportedOperationException (500). */
class IdentityFilterHeadersTest {

    private static ServerHttpRequest readOnly(String... headerPairs) {
        MockServerHttpRequest.BaseBuilder<?> b = MockServerHttpRequest.post("/api/v1/users/register");
        for (int i = 0; i < headerPairs.length; i += 2) {
            b.header(headerPairs[i], headerPairs[i + 1]);
        }
        ServerHttpRequest inner = b.build();
        HttpHeaders ro = HttpHeaders.readOnlyHttpHeaders(inner.getHeaders());
        return new ServerHttpRequestDecorator(inner) {
            @Override
            public HttpHeaders getHeaders() {
                return ro;
            }
        };
    }

    @Test
    void stripsClientSuppliedPublicIdFromReadOnlyHeaders() {
        ServerHttpRequest out = IdentityFilter.rewrite(readOnly("X-Public-Id", "forged", "X-Other", "keep"), null);
        assertNull(out.getHeaders().getFirst("X-Public-Id"));
        assertEquals("keep", out.getHeaders().getFirst("X-Other"));
    }

    @Test
    void replacesPublicIdWithResolvedOneRegardlessOfCase() {
        ServerHttpRequest out = IdentityFilter.rewrite(readOnly("x-public-id", "forged", "X-PUBLIC-ID", "forged2"),
                "abcdefghijklmnopqrst");
        assertEquals(List.of("abcdefghijklmnopqrst"), out.getHeaders().get("X-Public-Id"));
    }

    @Test
    void keepsMultiValuedHeadersAndOriginalRequest() {
        ServerHttpRequest in = readOnly("Accept", "a/b", "Accept", "c/d", "Authorization", "Bearer t");
        ServerHttpRequest out = IdentityFilter.rewrite(in, "pid");
        assertEquals(List.of("a/b", "c/d"), out.getHeaders().get("Accept"));
        assertEquals("Bearer t", out.getHeaders().getFirst("Authorization"));
        assertEquals(in.getURI(), out.getURI());
        assertEquals(in.getMethod(), out.getMethod());
    }

    @Test
    void noPublicIdWhenNoneResolved() {
        assertFalse(IdentityFilter.rewrite(readOnly(), null).getHeaders().containsKey("X-Public-Id"));
    }
}
