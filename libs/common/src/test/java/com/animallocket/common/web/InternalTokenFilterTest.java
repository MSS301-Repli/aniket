package com.animallocket.common.web;

import com.animallocket.common.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalTokenFilterTest {
    private static final String TOKEN = "s3cret-token-for-tests-only-0123456789";
    private final InternalTokenFilter filter = new InternalTokenFilter(TOKEN);

    private MockHttpServletResponse call(String path, String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setServletPath(path);
        if (token != null) {
            req.addHeader(Headers.INTERNAL_TOKEN, token);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        if (chain.getRequest() != null) {
            res.setHeader("X-Passed", "yes");
        }
        return res;
    }

    @Test
    void internalPathWithCorrectTokenPasses() throws Exception {
        assertThat(call("/internal/users/abc", TOKEN).getHeader("X-Passed")).isEqualTo("yes");
    }

    @Test
    void internalPathWithoutOrWrongTokenIsForbidden() throws Exception {
        for (String bad : new String[]{null, "", "wrong", TOKEN + "x", TOKEN.substring(1), TOKEN.toUpperCase()}) {
            MockHttpServletResponse res = call("/internal/users/abc", bad);
            assertThat(res.getStatus()).as("token %s", bad).isEqualTo(403);
            assertThat(res.getHeader("X-Passed")).isNull();
            assertThat(res.getContentType()).startsWith("application/problem+json");
            assertThat(res.getContentAsString()).contains("INTERNAL_ONLY").doesNotContain(TOKEN);
        }
    }

    @Test
    void bareInternalPathIsAlsoProtected() throws Exception {
        assertThat(call("/internal", null).getStatus()).isEqualTo(403);
    }

    @Test
    void publicPathsAreNotFiltered() throws Exception {
        assertThat(call("/api/v1/users/me", null).getHeader("X-Passed")).isEqualTo("yes");
        assertThat(call("/internalish", null).getHeader("X-Passed")).isEqualTo("yes");
        assertThat(call("/api/internal/x", null).getHeader("X-Passed")).isEqualTo("yes");
    }

    @Test
    void blankConfiguredTokenIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InternalTokenFilter(""));
        assertThrows(IllegalArgumentException.class, () -> new InternalTokenFilter(null));
    }
}
