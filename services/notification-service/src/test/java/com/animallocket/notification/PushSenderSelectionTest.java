package com.animallocket.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushSenderSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FcmPushSender.class, LoggingPushSender.class);

    @Test
    void loggingSenderIsUsedWhenFcmIsNotConfigured() {
        runner.run(ctx -> {
            assertEquals(1, ctx.getBeansOfType(PushSender.class).size());
            assertInstanceOf(LoggingPushSender.class, ctx.getBean(PushSender.class));
        });
    }

    @Test
    void loggingSenderStepsAsideWhenFcmIsConfigured() {
        runner.withPropertyValues("animal.push.fcm.credentials-file=/khong/ton/tai/fcm.json")
                .run(ctx -> {
                    // FCM được chọn nên đòi file khoá thật; LoggingPushSender không tham gia nên không có PushSender nào khác
                    assertTrue(ctx.getStartupFailure() != null);
                    assertTrue(ctx.getStartupFailure().getMessage().contains("FcmPushSender"));
                });
    }
}
