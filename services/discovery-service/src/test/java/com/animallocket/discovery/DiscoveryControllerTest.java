package com.animallocket.discovery;

import com.animallocket.common.Headers;
import com.animallocket.common.web.ApiException;
import com.animallocket.discovery.client.Gateways;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DiscoveryControllerTest {
    static final String ME = "aaaaaaaaaaaaaaaaaaaa";
    static final String LONG_ID = "x".repeat(65);

    @Autowired MockMvc mvc;
    @MockBean Gateways.UserGateway users;
    @MockBean Gateways.BillingGateway billing;
    @MockBean JwtDecoder jwtDecoder;

    private void discoverableMe() {
        when(users.profile(ME)).thenReturn(new Gateways.Profile(ME, "me", "fox", "1:fox:0:none", "ADULT", true, true, false));
        when(users.exclusions(anyString())).thenReturn(new Gateways.Exclusions(List.of(), List.of()));
    }

    @Test
    void swipeRejectsOversizedTargetAndMissingDirection() throws Exception {
        mvc.perform(post("/api/v1/discovery/swipes").with(jwt()).header(Headers.PUBLIC_ID, ME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPublicId\":\"" + LONG_ID + "\",\"direction\":\"RIGHT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/discovery/swipes").with(jwt()).header(Headers.PUBLIC_ID, ME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPublicId\":\"bbbb\"}"))
                .andExpect(status().isBadRequest());
        verify(users, never()).swipeRight(anyString(), anyString());
    }

    @Test
    void locationRejectsOutOfRangeCoordinates() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/discovery/location")
                        .with(jwt()).header(Headers.PUBLIC_ID, ME)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":91,\"lon\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void presenceRejectsOversizedIds() throws Exception {
        mvc.perform(get("/api/v1/discovery/presence").with(jwt()).header(Headers.PUBLIC_ID, ME)
                        .param("ids", LONG_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(users, never()).exclusions(anyString());
    }

    @Test
    void boostRejectsMalformedIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/discovery/boost").with(jwt()).header(Headers.PUBLIC_ID, ME)
                        .header(Headers.IDEMPOTENCY_KEY, "bad key with spaces"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(billing, never()).consumeBoost(anyString(), anyString());
    }

    @Test
    void boostPassesTheKeyToBillingAndAnswersNoContent() throws Exception {
        discoverableMe();
        when(billing.consumeBoost(ME, "key-1")).thenReturn(true);
        mvc.perform(post("/api/v1/discovery/boost").with(jwt()).header(Headers.PUBLIC_ID, ME)
                        .header(Headers.IDEMPOTENCY_KEY, "key-1"))
                .andExpect(status().isNoContent());
        verify(billing).consumeBoost(ME, "key-1");
    }

    @Test
    void boostWithoutBalanceAnswersPaymentRequired() throws Exception {
        discoverableMe();
        when(billing.consumeBoost(anyString(), anyString())).thenReturn(false);
        mvc.perform(post("/api/v1/discovery/boost").with(jwt()).header(Headers.PUBLIC_ID, ME))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("NO_BOOST"));
    }

    @Test
    void cardsFailSoftWhenUserServiceIsDown() throws Exception {
        String other = "bbbbbbbbbbbbbbbbbbbb";
        discoverableMe();
        when(users.profile(other)).thenReturn(
                new Gateways.Profile(other, "b", "cat", "1:cat:0:none", "ADULT", true, true, false));
        when(users.profiles(anyList())).thenThrow(
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE", "down"));
        // ME đang online nên trở thành ứng viên của other, buộc discovery phải lấy hồ sơ từ user-service.
        mvc.perform(post("/api/v1/discovery/heartbeat").with(jwt()).header(Headers.PUBLIC_ID, ME))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/discovery/cards").with(jwt()).header(Headers.PUBLIC_ID, other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.cards").isEmpty());
    }

    @Test
    void cardsKeepBusinessErrorCodes() throws Exception {
        when(users.profile(ME)).thenReturn(new Gateways.Profile(ME, "me", "fox", "1:fox:0:none", "ADULT", false, true, false));
        mvc.perform(get("/api/v1/discovery/cards").with(jwt()).header(Headers.PUBLIC_ID, ME))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/v1/discovery/cards").header(Headers.PUBLIC_ID, ME))
                .andExpect(status().isUnauthorized());
    }
}
