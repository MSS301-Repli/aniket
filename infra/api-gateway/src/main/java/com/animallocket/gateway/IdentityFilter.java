package com.animallocket.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Biến JWT (sub Keycloak) thành header {@code X-Public-Id} cho các service phía sau.
 *
 * <ul>
 *   <li>Luôn xoá {@code X-Public-Id} do client gửi lên (chống giả mạo danh tính).</li>
 *   <li>Tra cứu sub → publicId ở user-service, cache ngắn trong bộ nhớ (Caffeine).</li>
 *   <li>Chưa có hồ sơ (404): không gắn header, các endpoint cần hồ sơ sẽ trả PROFILE_REQUIRED.</li>
 *   <li>user-service lỗi và chưa có cache: fail closed với 503, không cho đi tiếp ẩn danh.</li>
 * </ul>
 */
@Component
public class IdentityFilter implements GlobalFilter, Ordered {
    static final String PUBLIC_ID = "X-Public-Id";
    private static final Logger log = LoggerFactory.getLogger(IdentityFilter.class);

    private final WebClient client;
    private final String internalToken;
    private final Cache<String, String> cache = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterWrite(Duration.ofMinutes(5)).build();

    public IdentityFilter(WebClient.Builder lbBuilder, @Value("${animal.internal-token:}") String internalToken) {
        // config-repo phục vụ ${INTERNAL_TOKEN:} nên giá trị có thể rỗng: chặn ngay lúc khởi động thay vì gửi token rỗng.
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("animal.internal-token (INTERNAL_TOKEN) chưa được đặt");
        }
        this.client = lbBuilder.baseUrl("http://user-service").build();
        this.internalToken = internalToken;
    }

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange base = exchange.mutate().request(rewrite(exchange.getRequest(), null)).build();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(Authentication::getPrincipal)
                .cast(Jwt.class)
                .map(Jwt::getSubject)
                .flatMap(sub -> resolve(sub)
                        .map(pid -> base.mutate().request(rewrite(base.getRequest(), pid)).build())
                        .defaultIfEmpty(base))
                .defaultIfEmpty(base)
                .flatMap(chain::filter)
                .onErrorResume(IdentityUnavailable.class, e -> unavailable(exchange));
    }

    /**
     * Trả request với {@code X-Public-Id} bị xoá và (nếu pid != null) gắn lại giá trị mới.
     * Không dùng {@code mutate().headers(h -> h.remove(..))}: ở spring-web 6.1.x header của request Netty là
     * {@code ReadOnlyHttpHeaders} và {@code remove} ném UnsupportedOperationException (mọi request có JWT sẽ 500).
     */
    static ServerHttpRequest rewrite(ServerHttpRequest request, String pid) {
        HttpHeaders copy = new HttpHeaders();
        request.getHeaders().forEach((name, values) -> {
            if (!PUBLIC_ID.equalsIgnoreCase(name)) {
                copy.addAll(name, values);
            }
        });
        if (pid != null) {
            copy.set(PUBLIC_ID, pid);
        }
        return new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                return copy;
            }
        };
    }

    /** Trả publicId, hoặc rỗng nếu người dùng chưa có hồ sơ. Không cache "chưa có hồ sơ" vì người dùng sắp đăng ký xong. */
    Mono<String> resolve(String sub) {
        String cached = cache.getIfPresent(sub);
        if (cached != null) {
            return Mono.just(cached);
        }
        return client.get().uri("/internal/identity/{sub}", sub)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .bodyToMono(IdentityResponse.class)
                .timeout(Duration.ofSeconds(2))
                .map(IdentityResponse::publicId)
                .doOnNext(pid -> cache.put(sub, pid))
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                .onErrorMap(e -> !(e instanceof IdentityUnavailable), e -> {
                    log.warn("Không tra được danh tính: {}", e.toString());
                    return new IdentityUnavailable(e);
                });
    }

    private Mono<Void> unavailable(ServerWebExchange exchange) {
        var res = exchange.getResponse();
        res.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        res.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = "{\"status\":503,\"code\":\"IDENTITY_UNAVAILABLE\",\"detail\":\"Tạm thời không xác định được người dùng\"}"
                .getBytes(StandardCharsets.UTF_8);
        return res.writeWith(Mono.just(res.bufferFactory().wrap(body)));
    }

    record IdentityResponse(String publicId) {
    }

    static class IdentityUnavailable extends RuntimeException {
        IdentityUnavailable(Throwable cause) {
            super(cause);
        }
    }
}
