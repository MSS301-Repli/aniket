package com.animallocket.billing.store;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Cấu hình Google Play. Mặc định rỗng: chưa cấu hình thì tích hợp Google TẮT (fail closed). */
@Getter
@Setter
@ConfigurationProperties(prefix = "animal.billing.google")
public class GoogleProperties {
    /** Package name (applicationId) của app Android. */
    private String packageName = "";
    /** File JSON của service account đã được cấp quyền API trong Play Console. */
    private String serviceAccountFile = "";
    /** Audience cấu hình trong Pub/Sub push subscription (thường là URL webhook). */
    private String pushAudience = "";
    /** Email service account dùng để ký OIDC token của push subscription. */
    private String pushServiceAccount = "";
    private String certsUri = "https://www.googleapis.com/oauth2/v3/certs";
    private String apiBaseUrl = "https://androidpublisher.googleapis.com";
    /**
     * Server tự acknowledge sau khi xác thực. Mặc định TẮT vì acknowledge diễn ra TRƯỚC khi ghi quyền lợi
     * vào DB; cách chuẩn là client gọi acknowledge/consume sau khi nhận 200 từ /purchases/verify.
     */
    private boolean serverAcknowledge = false;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
}
