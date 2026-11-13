package com.animallocket.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FcmPushSenderTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FcmPushSender.class);

    @Test
    void notCreatedWhenCredentialsFileIsNotConfigured() {
        runner.run(ctx -> assertEquals(0, ctx.getBeansOfType(PushSender.class).size()));
    }

    @Test
    void enabledWhenCredentialsFileIsSet() {
        // Có đường dẫn khoá thì bean được bật; file không tồn tại làm khởi động thất bại rõ ràng thay vì chạy im lặng.
        runner.withPropertyValues("animal.push.fcm.credentials-file=/khong/ton/tai/fcm.json")
                .run(ctx -> {
                    assertTrue(ctx.getStartupFailure() != null);
                    assertTrue(rootCause(ctx.getStartupFailure()) instanceof java.io.FileNotFoundException);
                });
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
