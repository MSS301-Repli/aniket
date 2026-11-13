package com.animallocket.billing.store;

import com.animallocket.billing.domain.Store;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Không cấu hình gì (cấu hình mặc định) thì Apple/Google phải TẮT hoàn toàn và mọi đường vào đều fail closed. */
@SpringBootTest
@AutoConfigureMockMvc
class StoreRouterConfigTest {
    @Autowired StoreRouter router;
    @Autowired ApplicationContext ctx;
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void unconfiguredStoresAreNotRegistered() {
        assertFalse(router.supports(Store.APPLE));
        assertFalse(router.supports(Store.GOOGLE));
        assertTrue(router.supports(Store.DEV), "DEV chỉ có trong test vì dev-store.enabled=true");
        for (Class<?> c : new Class<?>[]{AppleStoreGateway.class, GoogleStoreGateway.class, GooglePlayClient.class,
                AppleJwsVerifier.class, GooglePushAuth.class}) {
            assertEquals(0, ctx.getBeanNamesForType(c).length, c.getSimpleName() + " không được tồn tại khi chưa cấu hình");
        }
    }

    @Test
    void verifyThroughTheRouterFailsClosed() {
        for (Store s : new Store[]{Store.APPLE, Store.GOOGLE}) {
            StoreException e = assertThrows(StoreException.class, () -> router.verify(s, "animallocket.pro.month", "12345"));
            assertEquals(StoreException.Kind.UNAVAILABLE, e.kind());
        }
    }

    @Test
    void webhooksRefuseToProcessAnythingWhenNotConfigured() throws Exception {
        mvc.perform(post("/webhooks/apple").contentType(MediaType.APPLICATION_JSON).content("{\"signedPayload\":\"a.b.c\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("WEBHOOK_NOT_CONFIGURED"));
        mvc.perform(post("/webhooks/google").header("Authorization", "Bearer anything")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":{\"messageId\":\"1\",\"data\":\"e30=\"}}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("WEBHOOK_NOT_CONFIGURED"));
    }
}
