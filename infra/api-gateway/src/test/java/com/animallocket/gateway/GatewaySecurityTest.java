package com.animallocket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Context gateway khởi động không cần Config Server/Eureka/Keycloak, và JWT là bắt buộc trừ các path công khai. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "animal.internal-token=test-token"
})
@AutoConfigureWebTestClient
class GatewaySecurityTest {
    @Autowired WebTestClient web;

    @Test
    void apiRequiresJwt() {
        web.get().uri("/api/v1/feed").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void invalidJwtIsRejected() {
        web.get().uri("/api/v1/feed").header("Authorization", "Bearer not-a-jwt").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void publicPathsDoNotRequireJwt() {
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
        // Không có route trong test nên 404, quan trọng là không bị 401.
        web.post().uri("/webhooks/paypal").exchange().expectStatus().isNotFound();
        web.get().uri("/pay/abc").exchange().expectStatus().isNotFound();
    }
}
