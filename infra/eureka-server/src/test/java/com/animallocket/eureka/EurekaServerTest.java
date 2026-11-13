package com.animallocket.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.user.name=eureka",
        "spring.security.user.password=test-pass"
})
class EurekaServerTest {
    @Autowired TestRestTemplate rest;

    @Test
    void registryApiRequiresCredentials() {
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity("/eureka/apps", String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                rest.withBasicAuth("eureka", "wrong").getForEntity("/eureka/apps", String.class).getStatusCode());
        assertEquals(HttpStatus.OK,
                rest.withBasicAuth("eureka", "test-pass").getForEntity("/eureka/apps", String.class).getStatusCode());
    }

    @Test
    void healthIsPublicButOtherActuatorsAreNot() {
        assertEquals(HttpStatus.OK, rest.getForEntity("/actuator/health", String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity("/actuator/info", String.class).getStatusCode());
    }
}
