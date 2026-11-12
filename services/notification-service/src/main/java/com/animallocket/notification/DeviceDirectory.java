package com.animallocket.notification;

import java.util.List;

/** Nguồn dữ liệu từ user-service: token thiết bị và danh sách bạn. */
public interface DeviceDirectory {
    List<String> tokens(String publicId);

    List<String> friendIds(String publicId);

    void removeToken(String token);
}
