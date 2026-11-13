package com.animallocket.notification;

import java.util.Map;

public interface PushSender {
    enum Result { OK, INVALID_TOKEN, TRANSIENT_ERROR }

    Result send(String deviceToken, String title, String body, Map<String, String> data);
}
