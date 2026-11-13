package com.animallocket.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Dùng khi chưa cấu hình FCM (dev): chỉ ghi log, không gửi gì ra ngoài. */
@Component
@ConditionalOnExpression("'${animal.push.fcm.credentials-file:}'.isEmpty()")
public class LoggingPushSender implements PushSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        log.info("[PUSH giả lập] token=…{} title='{}' body='{}' data={}",
                deviceToken.substring(Math.max(0, deviceToken.length() - 6)), title, body, data);
        return Result.OK;
    }
}
