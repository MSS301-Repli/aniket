package com.animallocket.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Xoá user trong Keycloak qua Admin API, dùng client credentials của một service account. */
@Component
@ConditionalOnExpression("!'${keycloak.admin.url:}'.isEmpty()")
public class KeycloakAdminClient implements IdentityProviderClient {
    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private final RestClient http = RestClient.create();
    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminClient(@Value("${keycloak.admin.url}") String baseUrl,
                               @Value("${keycloak.admin.realm:animal-locket}") String realm,
                               @Value("${keycloak.admin.client-id:user-service-admin}") String clientId,
                               @Value("${keycloak.admin.client-secret:}") String clientSecret) {
        this.baseUrl = baseUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void deleteUser(String sub) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<String, Object> token = http.post()
                .uri(baseUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        String access = (String) token.get("access_token");
        try {
            http.delete().uri(baseUrl + "/admin/realms/" + realm + "/users/" + sub)
                    .headers(h -> h.setBearerAuth(access)).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            log.info("User đã không còn trong Keycloak");
        }
    }
}
