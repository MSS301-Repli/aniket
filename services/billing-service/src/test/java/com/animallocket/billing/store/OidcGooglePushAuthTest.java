package com.animallocket.billing.store;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Kiểm tra bộ xác thực OIDC của Pub/Sub bằng cặp khoá RSA cục bộ (thay cho JWKS của Google). */
class OidcGooglePushAuthTest {
    static final String AUD = "https://billing.example.com/webhooks/google";
    static final String SA = "pubsub-push@animal-locket.iam.gserviceaccount.com";
    static KeyPair key;
    static KeyPair otherKey;
    static OidcGooglePushAuth auth;

    @BeforeAll
    static void init() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        key = g.generateKeyPair();
        otherKey = g.generateKeyPair();
        GoogleProperties props = new GoogleProperties();
        props.setPushAudience(AUD);
        props.setPushServiceAccount(SA);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) key.getPublic()).build();
        decoder.setJwtValidator(OidcGooglePushAuth.validator(props));
        auth = new OidcGooglePushAuth(decoder);
    }

    private static String token(KeyPair signWith, String iss, String aud, String email, Object verified, long expiresInMs) throws Exception {
        JWTClaimsSet.Builder c = new JWTClaimsSet.Builder().issuer(iss).audience(aud).subject("1234567890")
                .claim("email", email).claim("email_verified", verified)
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + expiresInMs));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), c.build());
        jwt.sign(new RSASSASigner(signWith.getPrivate()));
        return "Bearer " + jwt.serialize();
    }

    private static void rejected(String header) {
        assertThrows(SecurityException.class, () -> auth.authenticate(header));
    }

    @Test
    void acceptsAValidPubSubToken() throws Exception {
        assertDoesNotThrow(() -> auth.authenticate(token(key, "https://accounts.google.com", AUD, SA, true, 60_000)));
    }

    @Test
    void rejectsWrongAudienceEmailIssuerOrUnverifiedEmail() throws Exception {
        rejected(token(key, "https://accounts.google.com", "https://evil.example.com", SA, true, 60_000));
        rejected(token(key, "https://accounts.google.com", AUD, "attacker@evil.iam.gserviceaccount.com", true, 60_000));
        rejected(token(key, "https://accounts.google.com", AUD, SA, false, 60_000));
        rejected(token(key, "https://accounts.google.com", AUD, SA, null, 60_000));
        rejected(token(key, "https://evil.example.com", AUD, SA, true, 60_000));
    }

    @Test
    void rejectsExpiredAndForgedSignatures() throws Exception {
        rejected(token(key, "https://accounts.google.com", AUD, SA, true, -120_000));
        rejected(token(otherKey, "https://accounts.google.com", AUD, SA, true, 60_000));
    }

    @Test
    void rejectsMissingOrMalformedHeaders() {
        for (String h : new String[]{null, "", "Bearer", "Bearer ", "Basic abc", "abc", "Bearer not.a.jwt"}) {
            rejected(h);
        }
        // audience dạng danh sách có chứa audience đúng vẫn được chấp nhận
        List<String> ok = List.of(AUD);
        assertDoesNotThrow(() -> auth.authenticate(token(key, "https://accounts.google.com", ok.get(0), SA, true, 60_000)));
    }
}
