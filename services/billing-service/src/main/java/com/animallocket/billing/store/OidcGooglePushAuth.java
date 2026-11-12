package com.animallocket.billing.store;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

/**
 * Pub/Sub push gắn một OIDC token do Google ký trong header Authorization. Chấp nhận khi:
 * chữ ký khớp JWKS của Google, issuer = https://accounts.google.com, chưa hết hạn,
 * audience khớp cấu hình, email đúng service account của subscription và email_verified = true.
 * Lưu ý: JwtDecoder này CỐ Ý không phải Spring bean để không xung đột với JwtDecoder của resource server.
 */
public class OidcGooglePushAuth implements GooglePushAuth {
    static final String ISSUER = "https://accounts.google.com";
    private final JwtDecoder decoder;

    public OidcGooglePushAuth(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    public static OidcGooglePushAuth create(GoogleProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.getCertsUri()).build();
        decoder.setJwtValidator(validator(props));
        return new OidcGooglePushAuth(decoder);
    }

    public static OAuth2TokenValidator<Jwt> validator(GoogleProperties props) {
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean aud = jwt.getAudience() != null && jwt.getAudience().contains(props.getPushAudience());
            boolean email = props.getPushServiceAccount().equals(jwt.getClaimAsString("email"));
            boolean verified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
            return aud && email && verified ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience/email không khớp", null));
        };
        return new DelegatingOAuth2TokenValidator<>(List.of(JwtValidators.createDefaultWithIssuer(ISSUER), claims));
    }

    @Override
    public void authenticate(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() <= 7) {
            throw new SecurityException("Thiếu bearer token");
        }
        try {
            decoder.decode(header.substring(7).trim());
        } catch (JwtException e) {
            throw new SecurityException("Token push không hợp lệ", e);
        }
    }
}
