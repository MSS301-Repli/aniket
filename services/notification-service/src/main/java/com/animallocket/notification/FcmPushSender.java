package com.animallocket.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

/** Gửi push qua FCM (Android và iOS thông qua APNs do FCM chuyển tiếp). Khoá dịch vụ lấy từ file, không đưa vào Git. */
@Component
@ConditionalOnExpression("!'${animal.push.fcm.credentials-file:}'.isEmpty()")
public class FcmPushSender implements PushSender {
    private final FirebaseMessaging messaging;

    public FcmPushSender(@Value("${animal.push.fcm.credentials-file}") String credentialsFile) throws IOException {
        try (var in = new FileInputStream(credentialsFile)) {
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in)).build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
            this.messaging = FirebaseMessaging.getInstance(app);
        }
    }

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        try {
            messaging.send(Message.builder().setToken(deviceToken)
                    .setNotification(com.google.firebase.messaging.Notification.builder().setTitle(title).setBody(body).build())
                    .putAllData(data).build());
            return Result.OK;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT
                    || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
                return Result.INVALID_TOKEN;
            }
            return Result.TRANSIENT_ERROR;
        }
    }
}
