package com.animallocket.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NotificationTest {
    @Autowired LoggingPushSender loggingSender;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean DeviceDirectory contextDirectory;

    @Test
    void contextLoadsWithLoggingSenderWhenFcmNotConfigured() {
        assertTrue(loggingSender != null);
    }

    @Test
    void loggingSenderOnlyReportsOk() {
        assertEquals(PushSender.Result.OK, loggingSender.send("device-token-123456", "t", "b", Map.of()));
    }
}
