package com.animallocket.common.config;

import com.animallocket.common.Headers;
import com.animallocket.common.web.ApiExceptionHandler;
import com.animallocket.common.web.InternalTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenConfigurationTest {
    private static final String STRONG = "0123456789abcdefghijklmnopqrstuvwxyz";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonAutoConfiguration.class));

    private static int status(InternalTokenFilter filter, String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/x");
        req.setServletPath("/internal/x");
        req.addHeader(Headers.INTERNAL_TOKEN, token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void prodWithoutTokenFailsToStart() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("bắt buộc ở profile prod"));
    }

    @Test
    void prodRejectsDevOrShortTokens() {
        runner.withPropertyValues("spring.profiles.active=prod", "animal.internal-token=dev-internal-token")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("spring.profiles.active=prod", "animal.internal-token=short-token")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void prodWithStrongTokenUsesIt() {
        runner.withPropertyValues("spring.profiles.active=prod", "animal.internal-token=" + STRONG).run(ctx -> {
            InternalTokenFilter filter = ctx.getBean(InternalTokenFilter.class);
            assertThat(status(filter, STRONG)).isEqualTo(200);
            assertThat(status(filter, InternalTokens.DEV_TOKEN)).isEqualTo(403);
        });
    }

    @Test
    void devFallsBackToDevToken() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ApiExceptionHandler.class);
            assertThat(status(ctx.getBean(InternalTokenFilter.class), InternalTokens.DEV_TOKEN)).isEqualTo(200);
        });
    }

    @Test
    void configuredTokenWinsOutsideProd() {
        runner.withPropertyValues("animal.internal-token=custom").run(ctx -> {
            InternalTokenFilter filter = ctx.getBean(InternalTokenFilter.class);
            assertThat(status(filter, "custom")).isEqualTo(200);
            assertThat(status(filter, InternalTokens.DEV_TOKEN)).isEqualTo(403);
        });
    }
}
