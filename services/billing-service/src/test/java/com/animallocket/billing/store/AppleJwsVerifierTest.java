package com.animallocket.billing.store;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppleJwsVerifierTest {
    static final String PAYLOAD = "{\"transactionId\":\"2000\",\"productId\":\"animallocket.pro.month\"}";

    private AppleJwsVerifier verifier(AppleTestPki pki, boolean requireOid) {
        return new AppleJwsVerifier(List.of(pki.root), requireOid, Clock.systemUTC());
    }

    private static StoreException.Kind kindOf(AppleJwsVerifier v, String jws) {
        return assertThrows(StoreException.class, () -> v.verify(jws)).kind();
    }

    @Test
    void acceptsAValidChainAndReturnsThePayload() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        JsonNode p = verifier(pki, true).verify(pki.sign(PAYLOAD));
        assertEquals("2000", p.get("transactionId").asText());
    }

    @Test
    void acceptsChainWithoutTheRootBeingIncluded() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        String jws = AppleTestPki.sign(PAYLOAD, "ES256", List.of(pki.leaf, pki.inter), pki.leafKey.getPrivate());
        assertEquals("2000", verifier(pki, true).verify(jws).get("transactionId").asText());
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        String[] parts = pki.sign(PAYLOAD).split("\\.");
        String forged = AppleTestPki.b64("{\"transactionId\":\"9999\",\"productId\":\"animallocket.pro.year\"}".getBytes());
        assertEquals(StoreException.Kind.INVALID, kindOf(verifier(pki, true), parts[0] + "." + forged + "." + parts[2]));
    }

    @Test
    void rejectsSignatureMadeByAnotherKeyEvenWithALegitChain() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair other = g.generateKeyPair();
        String jws = AppleTestPki.sign(PAYLOAD, "ES256", List.of(pki.leaf, pki.inter, pki.root), other.getPrivate());
        assertEquals(StoreException.Kind.INVALID, kindOf(verifier(pki, true), jws));
    }

    @Test
    void rejectsAnyAlgorithmOtherThanES256() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        AppleJwsVerifier v = verifier(pki, true);
        for (String alg : new String[]{"none", "HS256", "RS256", "ES384", "es256", ""}) {
            String jws = AppleTestPki.sign(PAYLOAD, alg, List.of(pki.leaf, pki.inter, pki.root), pki.leafKey.getPrivate());
            assertEquals(StoreException.Kind.INVALID, kindOf(v, jws), "alg=" + alg);
        }
    }

    @Test
    void rejectsChainThatDoesNotEndAtAConfiguredRoot() throws Exception {
        AppleTestPki trusted = AppleTestPki.create();
        AppleTestPki attacker = AppleTestPki.create();
        // Kẻ tấn công tự dựng chuỗi hoàn chỉnh và hợp lệ về mặt kỹ thuật, nhưng root không nằm trong cấu hình.
        assertEquals(StoreException.Kind.INVALID, kindOf(verifier(trusted, true), attacker.sign(PAYLOAD)));
    }

    @Test
    void rejectsAttackerLeafPaddedWithTheRealRoot() throws Exception {
        AppleTestPki trusted = AppleTestPki.create();
        AppleTestPki attacker = AppleTestPki.create();
        String jws = AppleTestPki.sign(PAYLOAD, "ES256", List.of(attacker.leaf, trusted.root), attacker.leafKey.getPrivate());
        assertEquals(StoreException.Kind.INVALID, kindOf(verifier(trusted, true), jws));
    }

    @Test
    void rejectsExpiredAndNotYetValidLeaf() throws Exception {
        AppleTestPki expired = AppleTestPki.create(true, Instant.now().minus(Duration.ofDays(30)), Instant.now().minus(Duration.ofDays(1)));
        assertEquals(StoreException.Kind.INVALID, kindOf(verifier(expired, true), expired.sign(PAYLOAD)));
        AppleTestPki future = AppleTestPki.create(true, Instant.now().plus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(30)));
        assertEquals(StoreException.Kind.INVALID, kindOf(verifier(future, true), future.sign(PAYLOAD)));
    }

    @Test
    void usesTheInjectedClockForValidity() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        Clock farFuture = Clock.fixed(Instant.now().plus(Duration.ofDays(400)), java.time.ZoneOffset.UTC);
        AppleJwsVerifier late = new AppleJwsVerifier(List.of(pki.root), true, farFuture);
        assertEquals(StoreException.Kind.INVALID, kindOf(late, pki.sign(PAYLOAD)));
    }

    @Test
    void rejectsMissingOrBrokenX5c() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        AppleJwsVerifier v = verifier(pki, true);
        assertEquals(StoreException.Kind.INVALID, kindOf(v, AppleTestPki.sign(PAYLOAD, "ES256", null, pki.leafKey.getPrivate())));
        assertEquals(StoreException.Kind.INVALID, kindOf(v, AppleTestPki.sign(PAYLOAD, "ES256", List.of(), pki.leafKey.getPrivate())));
    }

    @Test
    void rejectsChainWithMissingIntermediate() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        String jws = AppleTestPki.sign(PAYLOAD, "ES256", List.of(pki.leaf, pki.root), pki.leafKey.getPrivate());
        assertEquals(StoreException.Kind.INVALID, kindOf(verifier(pki, true), jws));
    }

    @Test
    void failsClosedWhenNoRootIsConfigured() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        AppleJwsVerifier none = new AppleJwsVerifier(List.of(), true, Clock.systemUTC());
        assertEquals(StoreException.Kind.UNAVAILABLE, kindOf(none, pki.sign(PAYLOAD)));
    }

    @Test
    void appleOidIsRequiredUnlessDisabled() throws Exception {
        AppleTestPki noOid = AppleTestPki.create(false, Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(30)));
        assertEquals(StoreException.Kind.INVALID, kindOf(verifier(noOid, true), noOid.sign(PAYLOAD)));
        assertEquals("2000", verifier(noOid, false).verify(noOid.sign(PAYLOAD)).get("transactionId").asText());
        // có OID thì cả hai chế độ đều chấp nhận
        AppleTestPki withOid = AppleTestPki.create();
        verifier(withOid, true).verify(withOid.sign(PAYLOAD));
        verifier(withOid, false).verify(withOid.sign(PAYLOAD));
    }

    @Test
    void rejectsMalformedInput() throws Exception {
        AppleJwsVerifier v = verifier(AppleTestPki.create(), true);
        for (String bad : new String[]{null, "", "a", "a.b", "a.b.c.d", "....", "###.###.###", "x".repeat(70_000)}) {
            assertEquals(StoreException.Kind.INVALID, kindOf(v, bad), String.valueOf(bad == null ? null : bad.length()));
        }
    }

    @Test
    void rejectsWrongLengthSignature() throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        String[] parts = pki.sign(PAYLOAD).split("\\.");
        String shortSig = AppleTestPki.b64(new byte[32]);
        assertEquals(StoreException.Kind.INVALID, kindOf(verifier(pki, true), parts[0] + "." + parts[1] + "." + shortSig));
    }

    @Test
    void loadsRootsFromPemFile(@TempDir Path dir) throws Exception {
        AppleTestPki pki = AppleTestPki.create();
        Path pem = pki.writeRootPem(dir.resolve("root.pem"));
        var roots = AppleJwsVerifier.loadRoots(List.of(pem.toString(), " "));
        assertEquals(1, roots.size());
        new AppleJwsVerifier(roots, true, Clock.systemUTC()).verify(pki.sign(PAYLOAD));
    }
}
