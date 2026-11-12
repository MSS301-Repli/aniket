package com.animallocket.user.service;

/** Cổng sang Keycloak để xoá hẳn danh tính đăng nhập (email...) khi người dùng xoá tài khoản. */
public interface IdentityProviderClient {
    void deleteUser(String keycloakSub);
}
