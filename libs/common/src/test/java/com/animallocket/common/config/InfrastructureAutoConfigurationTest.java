package com.animallocket.common.config;

import com.animallocket.common.Headers;
import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.kv.KeyValueStore;
import com.animallocket.common.web.ApiException;
import com.animallocket.common.web.ApiExceptionHandler;
import com.animallocket.common.web.InternalTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hoàn tất G2: một ứng dụng giống service thật nạp TOÀN BỘ auto-config của libs/common qua
 * {@code AutoConfiguration.imports} (không liệt kê tay), kiểm tra các phần phối hợp đúng với nhau.
 * Cấu hình theo hướng dẫn test của service: kv memory, Kafka listener không tự chạy.
 */
@SpringBootTest(classes = InfrastructureAutoConfigurationTest.App.class, properties = {
        "spring.application.name=infra-test",
        "animal.kv.type=memory",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false",
        "management.endpoints.web.exposure.include=health,info,prometheus",
        "animal.resilience.timeout=300ms"})
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
class InfrastructureAutoConfigurationTest {

    @SpringBootApplication
    @Import(Endpoints.class)
    static class App {
    }

    @RestController
    static class Endpoints {
        @GetMapping("/api/v1/me")
        String me(@RequestHeader(Headers.PUBLIC_ID) String me) {
            return me;
        }

        @GetMapping("/api/v1/missing")
        String missing() {
            throw ApiException.notFound("Bài đăng");
        }
    }

    @Autowired
    ApplicationContext ctx;
    @Autowired
    MockMvc mvc;
    @MockBean
    JwtDecoder jwtDecoder;

    @Test
    void everyCommonBuildingBlockIsWired() {
        assertThat(ctx.getBean(KeyValueStore.class)).isInstanceOf(InMemoryKeyValueStore.class);
        assertThat(ctx.getBean(CommonErrorHandler.class)).isInstanceOf(DefaultErrorHandler.class);
        assertThat(ctx.getBeansOfType(SecurityFilterChain.class)).containsKey("animalSecurityFilterChain");
        assertThat(ctx.getBean(InternalTokenFilter.class)).isNotNull();
        assertThat(ctx.getBean(ApiExceptionHandler.class)).isNotNull();
        assertThat(ctx.containsBean("animalDefaultCircuitBreakerCustomizer")).isTrue();
    }

    @Test
    void circuitBreakerUsesTheSharedDefaults() {
        CircuitBreakerFactory<?, ?> factory = ctx.getBean(CircuitBreakerFactory.class);
        String r = factory.create("downstream").run(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "late";
        }, t -> "fallback");
        assertThat(r).isEqualTo("fallback");
    }

    @Test
    void healthAndPrometheusArePublicForProbesAndScraping() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    void securityAndErrorHandlingWorkTogether() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        mvc.perform(get("/api/v1/me").with(jwt()).header(Headers.PUBLIC_ID, "abc"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/missing").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/internal/anything"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTERNAL_ONLY"));
    }
}
