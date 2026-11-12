package com.animallocket.common.config;

import com.animallocket.common.Headers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chain bảo mật mặc định chạy trong một ứng dụng Spring Boot thật (MockMvc + auto-config).
 * Lớp lồng {@code @Nested} được Surefire chạy qua JUnit Platform.
 */
class SecurityFilterChainTest {

    @RestController
    static class Endpoints {
        @GetMapping("/api/v1/thing")
        String thing() {
            return "ok";
        }

        @GetMapping("/internal/thing")
        String internal() {
            return "internal";
        }

        @GetMapping("/ws/info")
        String ws() {
            return "ws";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(Endpoints.class)
    static class App {
    }

    @Nested
    @SpringBootTest(classes = App.class, properties = {
            "animal.security.public-paths=/ws/**",
            "animal.security.cors.allowed-origins=https://app.example.test"})
    @AutoConfigureMockMvc
    class Enabled {
        @Autowired
        MockMvc mvc;
        @MockBean
        JwtDecoder jwtDecoder;

        @Test
        void apiRequiresJwt() throws Exception {
            mvc.perform(get("/api/v1/thing")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/thing").with(jwt())).andExpect(status().isOk());
        }

        @Test
        void internalUsesTokenInsteadOfJwt() throws Exception {
            mvc.perform(get("/internal/thing"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ONLY"));
            mvc.perform(get("/internal/thing").with(jwt()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/internal/thing").header(Headers.INTERNAL_TOKEN, InternalTokens.DEV_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        void configuredPublicPathsSkipJwt() throws Exception {
            mvc.perform(get("/ws/info")).andExpect(status().isOk());
        }

        @Test
        void corsAllowsOnlyConfiguredOrigins() throws Exception {
            mvc.perform(options("/api/v1/thing")
                            .header("Origin", "https://app.example.test")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.test"));
            mvc.perform(options("/api/v1/thing")
                            .header("Origin", "https://evil.example.test")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @SpringBootTest(classes = App.class, properties = "animal.security.enabled=false")
    @AutoConfigureMockMvc
    class Disabled {
        @Autowired
        MockMvc mvc;

        @Test
        void everythingButInternalIsOpen() throws Exception {
            mvc.perform(get("/api/v1/thing")).andExpect(status().isOk());
            mvc.perform(get("/internal/thing")).andExpect(status().isForbidden());
        }

        @Test
        void corsIsOffByDefault() throws Exception {
            mvc.perform(get("/api/v1/thing").header("Origin", "https://app.example.test"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }
}
