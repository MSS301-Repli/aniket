package com.animallocket.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Dùng khi chưa cấu hình Keycloak Admin (dev/test). Production phải cấu hình keycloak.admin.url. */
@Component
@ConditionalOnExpression("'${keycloak.admin.url:}'.isEmpty()")
public class NoopIdentityProviderClient implements IdentityProviderClient {
    private static final Logger log = LoggerFactory.getLogger(NoopIdentityProviderClient.class);

    @Override
    public void deleteUser(String keycloakSub) {
        log.warn("keycloak.admin.url chưa cấu hình: bỏ qua xoá user trong Keycloak");
    }
}
