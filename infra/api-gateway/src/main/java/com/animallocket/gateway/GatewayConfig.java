package com.animallocket.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableWebFluxSecurity
public class GatewayConfig {
    static final String INTERNAL = "/internal/**";

    /**
     * Gateway kiểm tra JWT một lần ở biên. Webhook của store không có JWT (billing tự xác thực chữ ký).
     * {@code animal.security.enabled=false} chỉ dành cho test cục bộ.
     */
    @Bean
    SecurityWebFilterChain securityChain(ServerHttpSecurity http,
                                         @Value("${animal.security.enabled:true}") boolean enabled) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        if (!enabled) {
            return http.authorizeExchange(a -> a.pathMatchers(INTERNAL).denyAll().anyExchange().permitAll()).build();
        }
        return http.authorizeExchange(a -> a
                        // API nội bộ service-to-service không bao giờ được gọi từ ngoài, kể cả khi có JWT.
                        .pathMatchers(INTERNAL).denyAll()
                        .pathMatchers("/actuator/health/**", "/actuator/prometheus", "/fallback/**").permitAll()
                        .pathMatchers("/webhooks/**").permitAll()
                        // Trang thanh toán web: không có JWT, billing-service xác thực bằng mã dùng một lần trên URL.
                        .pathMatchers("/pay/**").permitAll()
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> {
                }))
                .build();
    }

    /** WebClient gọi service khác qua Eureka (lb://), dùng cho IdentityFilter. */
    @Bean
    @LoadBalanced
    WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
