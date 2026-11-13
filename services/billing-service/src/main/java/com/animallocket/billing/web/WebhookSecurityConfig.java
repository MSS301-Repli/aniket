package com.animallocket.billing.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * /webhooks/** là đường dẫn công khai (store không có JWT của Keycloak) và tự xác thực trong handler.
 * Nhưng {@code BearerTokenAuthenticationFilter} của resource server vẫn cố giải mã MỌI header
 * {@code Authorization: Bearer ...}, kể cả trên đường dẫn permitAll, và trả 401 nếu không phải JWT của Keycloak.
 * Token OIDC của Google Pub/Sub chính là trường hợp đó, nên phải bỏ qua bearer ở các đường dẫn webhook;
 * chữ ký/audience/email của token đó được kiểm tra riêng bởi {@code OidcGooglePushAuth}.
 */
@Configuration
public class WebhookSecurityConfig {

    @Bean
    BearerTokenResolver webhookAwareBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return (HttpServletRequest request) -> request.getRequestURI().startsWith("/webhooks/") ? null : delegate.resolve(request);
    }
}
