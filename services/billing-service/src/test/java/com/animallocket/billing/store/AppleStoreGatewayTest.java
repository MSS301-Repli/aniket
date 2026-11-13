package com.animallocket.billing.store;

import com.animallocket.billing.domain.Store;
import com.animallocket.common.event.EventJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class AppleStoreGatewayTest {
    static final String BUNDLE = "com.animallocket.app";
    static final String PROD = "https://api.storekit.itunes.apple.com";
    static final String SANDBOX = "https://api.storekit-sandbox.itunes.apple.com";
    static final String MONTH = "animallocket.pro.month";

    @TempDir Path dir;
    AppleTestPki pki;
    KeyPair apiKey;
    AppleProperties props;
    MockRestServiceServer server;
    AppleStoreGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        pki = AppleTestPki.create();
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        apiKey = g.generateKeyPair();
        Path p8 = dir.resolve("AuthKey_TEST.p8");
        Files.writeString(p8, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(apiKey.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");

        props = new AppleProperties();
        props.setBundleId(BUNDLE);
        props.setIssuerId("issuer-123");
        props.setKeyId("KEY123");
        props.setPrivateKeyFile(p8.toString());
        build(props);
    }

    private void build(AppleProperties p) {
        RestClient.Builder b = RestClient.builder();
        server = MockRestServiceServer.bindTo(b).build();
        gateway = new AppleStoreGateway(p, new AppleJwsVerifier(List.of(pki.root), true, Clock.systemUTC()), b.build(),
                new StoreCaller(3, Duration.ofMillis(1), 50, 50, Duration.ofSeconds(30)), Clock.systemUTC());
    }

    private String tx(Map<String, Object> overrides) throws Exception {
        Map<String, Object> m = new java.util.LinkedHashMap<>(Map.of(
                "transactionId", "2000", "originalTransactionId", "1000", "bundleId", BUNDLE,
                "productId", MONTH, "environment", "Production",
                "purchaseDate", Instant.now().toEpochMilli(),
                "expiresDate", Instant.now().plus(Duration.ofDays(30)).toEpochMilli()));
        m.putAll(overrides);
        m.values().removeIf(v -> v == null);
        return pki.sign(EventJson.mapper().writeValueAsString(m));
    }

    private static String envelope(String signed) {
        return "{\"signedTransactionInfo\":\"" + signed + "\"}";
    }

    private void respondOk(String base, String id, String signed) {
        server.expect(once(), requestTo(base + "/inApps/v1/transactions/" + id)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(envelope(signed), MediaType.APPLICATION_JSON));
    }

    @Test
    void verifiesASubscriptionAgainstAppleAndMapsEveryField() throws Exception {
        respondOk(PROD, "2000", tx(Map.of()));
        VerifiedPurchase vp = gateway.verify(MONTH, "2000");
        assertEquals("2000", vp.transactionId());
        assertEquals("1000", vp.originalTransactionId());
        assertEquals(MONTH, vp.sku());
        assertFalse(vp.revoked());
        assertTrue(vp.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(29))));
        assertEquals(Store.APPLE, gateway.store());
        server.verify();
    }

    @Test
    void oneTimePurchaseHasNoExpiry() throws Exception {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("productId", "animallocket.boost.5");
        m.put("originalTransactionId", "2000");
        m.put("expiresDate", null);
        respondOk(PROD, "2000", tx(m));
        VerifiedPurchase vp = gateway.verify("animallocket.boost.5", "2000");
        assertNull(vp.expiresAt());
        assertEquals("2000", vp.originalTransactionId());
    }

    @Test
    void sendsAWellFormedSignedApiToken() throws Exception {
        server.expect(once(), requestTo(PROD + "/inApps/v1/transactions/2000"))
                .andExpect(header("Authorization", startsWith("Bearer ")))
                .andExpect(request -> {
                    String jwt = request.getHeaders().getFirst("Authorization").substring(7);
                    String[] p = jwt.split("\\.");
                    JsonNode h = EventJson.mapper().readTree(Base64.getUrlDecoder().decode(p[0]));
                    JsonNode body = EventJson.mapper().readTree(Base64.getUrlDecoder().decode(p[1]));
                    assertEquals("ES256", h.get("alg").asText());
                    assertEquals("KEY123", h.get("kid").asText());
                    assertEquals("issuer-123", body.get("iss").asText());
                    assertEquals("appstoreconnect-v1", body.get("aud").asText());
                    assertEquals(BUNDLE, body.get("bid").asText());
                    assertTrue(body.get("exp").asLong() - body.get("iat").asLong() <= 600, "Apple yêu cầu exp ≤ 60 phút");
                    try {
                        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
                        s.initVerify(apiKey.getPublic());
                        s.update((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
                        assertTrue(s.verify(Base64.getUrlDecoder().decode(p[2])), "chữ ký JWT gọi Apple sai");
                    } catch (java.security.GeneralSecurityException e) {
                        throw new AssertionError(e);
                    }
                })
                .andRespond(withSuccess(envelope(tx(Map.of())), MediaType.APPLICATION_JSON));
        gateway.verify(MONTH, "2000");
        server.verify();
    }

    @Test
    void rejectsSkuMismatchBundleMismatchAndTransactionMismatch() throws Exception {
        respondOk(PROD, "2000", tx(Map.of("productId", "animallocket.pro.year")));
        assertEquals(StoreException.Kind.INVALID, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());

        build(props);
        respondOk(PROD, "2000", tx(Map.of("bundleId", "com.evil.app")));
        assertEquals(StoreException.Kind.INVALID, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());

        build(props);
        respondOk(PROD, "2000", tx(Map.of("transactionId", "9999")));
        assertEquals(StoreException.Kind.INVALID, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());
    }

    @Test
    void revokedTransactionsAreFlagged() throws Exception {
        respondOk(PROD, "2000", tx(Map.of("revocationDate", Instant.now().toEpochMilli())));
        assertTrue(gateway.verify(MONTH, "2000").revoked());
    }

    @Test
    void rejectsResponsesNotSignedByAppleChain() throws Exception {
        AppleTestPki attacker = AppleTestPki.create();
        Map<String, Object> m = Map.of("transactionId", "2000", "originalTransactionId", "1000", "bundleId", BUNDLE, "productId", MONTH);
        respondOk(PROD, "2000", attacker.sign(EventJson.mapper().writeValueAsString(m)));
        assertEquals(StoreException.Kind.INVALID, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());
    }

    @Test
    void rejectsBadTransactionIdsWithoutCallingApple() {
        for (String bad : new String[]{null, "", "abc", "12 34", "../etc", "1;2", "9".repeat(40)}) {
            assertEquals(StoreException.Kind.INVALID, assertThrows(StoreException.class, () -> gateway.verify(MONTH, bad)).kind());
        }
        server.verify(); // không có request nào
    }

    @Test
    void serverErrorsAreUnavailableAndRetried() {
        server.expect(times(3), requestTo(PROD + "/inApps/v1/transactions/2000")).andRespond(withServerError());
        assertEquals(StoreException.Kind.UNAVAILABLE, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());
        server.verify();
    }

    @Test
    void transientErrorThenSuccessStillGrants() throws Exception {
        server.expect(once(), requestTo(PROD + "/inApps/v1/transactions/2000")).andRespond(withServerError());
        respondOk(PROD, "2000", tx(Map.of()));
        assertEquals("2000", gateway.verify(MONTH, "2000").transactionId());
    }

    @Test
    void rejectedCredentialsAndRateLimitAreUnavailableNotInvalid() {
        server.expect(times(3), requestTo(PROD + "/inApps/v1/transactions/2000")).andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));
        assertEquals(StoreException.Kind.UNAVAILABLE, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());

        build(props);
        server.expect(times(3), requestTo(PROD + "/inApps/v1/transactions/2000")).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        assertEquals(StoreException.Kind.UNAVAILABLE, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());
    }

    @Test
    void notFoundIsInvalidAndNotRetried() {
        server.expect(once(), requestTo(PROD + "/inApps/v1/transactions/2000")).andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        assertEquals(StoreException.Kind.INVALID, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());
        server.verify();
    }

    @Test
    void sandboxFallbackOnlyWhenEnabled() throws Exception {
        // tắt fallback: 404 ở production là kết thúc, không thử sandbox
        server.expect(once(), requestTo(PROD + "/inApps/v1/transactions/2000")).andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000"));
        server.verify();

        props.setAllowSandboxFallback(true);
        build(props);
        server.expect(once(), requestTo(PROD + "/inApps/v1/transactions/2000")).andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        respondOk(SANDBOX, "2000", tx(Map.of("environment", "Sandbox")));
        assertEquals("2000", gateway.verify(MONTH, "2000").transactionId());
        server.verify();
    }

    @Test
    void sandboxTransactionsAreRejectedInProductionWithoutFallback() throws Exception {
        respondOk(PROD, "2000", tx(Map.of("environment", "Sandbox")));
        assertEquals(StoreException.Kind.INVALID, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());
    }

    @Test
    void sandboxEnvironmentOnlyTalksToSandbox() throws Exception {
        props.setEnvironment("SANDBOX");
        build(props);
        respondOk(SANDBOX, "2000", tx(Map.of("environment", "Sandbox")));
        assertEquals("2000", gateway.verify(MONTH, "2000").transactionId());
    }

    @Test
    void unreadablePrivateKeyFailsClosed() {
        props.setPrivateKeyFile(dir.resolve("missing.p8").toString());
        build(props);
        assertEquals(StoreException.Kind.UNAVAILABLE, assertThrows(StoreException.class, () -> gateway.verify(MONTH, "2000")).kind());
    }
}
