package com.animallocket.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm tra infra/config-repo thật: mọi service nhận được file chung + file riêng, và không có bí mật mặc định. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.security.user.name=config",
        "spring.security.user.password=test-pass",
        // Surefire chạy trong thư mục module (infra/config-server).
        "spring.cloud.config.server.native.search-locations=file:../config-repo"
})
class ConfigRepoTest {
    static final Path REPO = Path.of("../config-repo");
    static final List<String> SERVICES = List.of("user-service", "media-service", "post-service", "notification-service",
            "discovery-service", "chat-service", "billing-service", "api-gateway");
    /** ${VAR_CHỨA_PASSWORD/SECRET/TOKEN/KEY:giá-trị-không-rỗng} = bí mật có mặc định. */
    static final Pattern SECRET_WITH_DEFAULT =
            Pattern.compile("\\$\\{[A-Z0-9_]*(PASSWORD|SECRET|TOKEN|ACCESS_KEY|SECRET_KEY|API_KEY)[A-Z0-9_]*:[^}]+}");

    @Autowired TestRestTemplate rest;

    @Test
    void everyServiceGetsSharedAndOwnFile() {
        for (String service : SERVICES) {
            assertTrue(Files.exists(REPO.resolve(service + ".yml")), "thiếu " + service + ".yml");
            ResponseEntity<String> r = rest.withBasicAuth("config", "test-pass")
                    .getForEntity("/" + service + "/default", String.class);
            assertEquals(HttpStatus.OK, r.getStatusCode());
            assertTrue(r.getBody().contains("config-repo/" + service + ".yml"), r.getBody());
            assertTrue(r.getBody().contains("config-repo/application.yml"), r.getBody());
        }
    }

    @Test
    void noSecretHasADefaultValue() throws IOException {
        try (Stream<Path> files = Files.list(REPO)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                var m = SECRET_WITH_DEFAULT.matcher(Files.readString(f));
                boolean found = m.find();
                assertFalse(found, () -> f.getFileName() + " có bí mật mặc định: " + m.group());
            }
        }
    }

    @Test
    void detectorCatchesDefaultedSecrets() {
        assertTrue(SECRET_WITH_DEFAULT.matcher("token: ${INTERNAL_TOKEN:dev-internal-token}").find());
        assertTrue(SECRET_WITH_DEFAULT.matcher("k: ${R2_SECRET_KEY:changeme}").find());
        assertFalse(SECRET_WITH_DEFAULT.matcher("token: ${INTERNAL_TOKEN}").find());
    }
}
