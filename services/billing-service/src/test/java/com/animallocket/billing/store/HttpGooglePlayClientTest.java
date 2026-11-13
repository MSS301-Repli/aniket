package com.animallocket.billing.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpGooglePlayClientTest {
    static final String BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.animallocket.app";

    MockRestServiceServer server;
    HttpGooglePlayClient client;
    HttpGooglePlayClient.AccessTokenProvider tokens = () -> "ya29.test-access";

    @BeforeEach
    void setUp() {
        GoogleProperties props = new GoogleProperties();
        props.setPackageName("com.animallocket.app");
        RestClient.Builder b = RestClient.builder();
        server = MockRestServiceServer.bindTo(b).build();
        client = new HttpGooglePlayClient(props, b.build(), () -> tokens.accessToken(),
                new StoreCaller(2, Duration.ofMillis(1), 50, 50, Duration.ofSeconds(30)));
    }

    @Test
    void readsSubscriptionsV2() throws Exception {
        server.expect(once(), requestTo(BASE + "/purchases/subscriptionsv2/tokens/tok.en-1_x"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer ya29.test-access"))
                .andRespond(withSuccess("""
                        {"kind":"androidpublisher#subscriptionPurchaseV2","subscriptionState":"SUBSCRIPTION_STATE_ACTIVE",
                         "latestOrderId":"GPA.1234-5678","linkedPurchaseToken":"old-token",
                         "acknowledgementState":"ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
                         "lineItems":[{"productId":"animallocket.pro.month","expiryTime":"2026-07-01T10:20:30.123Z"}]}
                        """, MediaType.APPLICATION_JSON));
        var s = client.getSubscription("tok.en-1_x");
        assertEquals("SUBSCRIPTION_STATE_ACTIVE", s.state());
        assertEquals("GPA.1234-5678", s.latestOrderId());
        assertEquals("old-token", s.linkedPurchaseToken());
        assertTrue(s.acknowledged());
        assertEquals("animallocket.pro.month", s.lineItems().get(0).productId());
        assertEquals(Instant.parse("2026-07-01T10:20:30.123Z"), s.lineItems().get(0).expiryTime());
        server.verify();
    }

    @Test
    void readsOneTimeProducts() throws Exception {
        server.expect(once(), requestTo(BASE + "/purchases/products/animallocket.boost.5/tokens/tokP"))
                .andRespond(withSuccess("""
                        {"purchaseTimeMillis":"1780000000000","purchaseState":0,"consumptionState":0,
                         "orderId":"GPA.9","acknowledgementState":1,"productId":"animallocket.boost.5"}
                        """, MediaType.APPLICATION_JSON));
        var p = client.getProduct("animallocket.boost.5", "tokP");
        assertEquals("GPA.9", p.orderId());
        assertEquals(0, p.purchaseState());
        assertEquals(Instant.ofEpochMilli(1780000000000L), p.purchaseTime());
        assertTrue(p.acknowledged());
    }

    @Test
    void sendsAcknowledgeCalls() throws Exception {
        server.expect(once(), requestTo(BASE + "/purchases/subscriptions/animallocket.pro.month/tokens/tokS:acknowledge"))
                .andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
        server.expect(once(), requestTo(BASE + "/purchases/products/animallocket.boost.5/tokens/tokP:acknowledge"))
                .andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
        client.acknowledgeSubscription("animallocket.pro.month", "tokS");
        client.acknowledgeProduct("animallocket.boost.5", "tokP");
        server.verify();
    }

    @Test
    void goneAndNotFoundAreInvalidNotRetried() {
        server.expect(once(), requestTo(BASE + "/purchases/subscriptionsv2/tokens/t1")).andRespond(withStatus(HttpStatus.GONE));
        assertEquals(StoreException.Kind.INVALID, assertThrows(StoreException.class, () -> client.getSubscription("t1")).kind());
        server.verify();
    }

    @Test
    void serverErrorsAndForbiddenAreUnavailableAndServerErrorsAreRetried() {
        server.expect(times(2), requestTo(BASE + "/purchases/subscriptionsv2/tokens/t2")).andRespond(withServerError());
        assertEquals(StoreException.Kind.UNAVAILABLE, assertThrows(StoreException.class, () -> client.getSubscription("t2")).kind());
        server.verify();

        server.reset();
        server.expect(times(2), requestTo(BASE + "/purchases/subscriptionsv2/tokens/t3")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertEquals(StoreException.Kind.UNAVAILABLE, assertThrows(StoreException.class, () -> client.getSubscription("t3")).kind());
    }

    @Test
    void failureToGetAccessTokenIsUnavailable() {
        tokens = () -> {
            throw new IOException("metadata server unreachable");
        };
        assertEquals(StoreException.Kind.UNAVAILABLE, assertThrows(StoreException.class, () -> client.getSubscription("t4")).kind());
    }

    @Test
    void garbageResponseNeverGrants() {
        server.expect(once(), requestTo(BASE + "/purchases/subscriptionsv2/tokens/t5"))
                .andRespond(withSuccess("{\"lineItems\":[{\"productId\":\"x\",\"expiryTime\":\"not-a-date\"}]}", MediaType.APPLICATION_JSON));
        StoreException e = assertThrows(StoreException.class, () -> client.getSubscription("t5"));
        assertEquals(StoreException.Kind.UNAVAILABLE, e.kind());
        assertFalse(e.getMessage().isEmpty());
    }
}
