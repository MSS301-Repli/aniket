package com.animallocket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

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
        // billing-service không chạy trong test nên không phải 2xx; quan trọng là không bị 401.
        web.post().uri("/webhooks/paypal").exchange().expectStatus().value(s -> assertNotEquals(401, s));
        web.get().uri("/pay/abc").exchange().expectStatus().value(s -> assertNotEquals(401, s));
    }

    @Test
    void internalApiIsBlockedEvenWithValidJwt() {
        web.mutateWith(mockJwt()).get().uri("/internal/identity/some-sub").exchange().expectStatus().isForbidden();
        web.mutateWith(mockJwt()).delete().uri("/internal/devices/tok").exchange().expectStatus().isForbidden();
        web.get().uri("/internal/users").exchange().expectStatus().is4xxClientError();
    }

    @Test
    void otherActuatorEndpointsRequireJwt() {
        web.get().uri("/actuator/info").exchange().expectStatus().isUnauthorized();
    }
}
