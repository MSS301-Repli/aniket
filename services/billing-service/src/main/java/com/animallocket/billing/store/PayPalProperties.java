package com.animallocket.billing.store;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình PayPal (thanh toán web). Mặc định để trống = tắt (fail closed).
 * {@code mode} mặc định {@code sandbox}; chỉ đặt {@code live} trên server triển khai thật, khoá live không bao giờ vào repo.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "animal.billing.paypal")
public class PayPalProperties {
    /** sandbox | live. Giá trị khác làm service không khởi động (tránh gọi nhầm môi trường). */
    private String mode = "sandbox";
    private String clientId = "";
    private String clientSecret = "";
    /** Id webhook (developer.paypal.com → app → Webhooks). Trống = endpoint /webhooks/paypal từ chối (503). */
    private String webhookId = "";
    /** Địa chỉ công khai (qua gateway) mà trình duyệt dùng để quay lại từ PayPal. Production phải là HTTPS. */
    private String publicBaseUrl = "http://localhost:8080";
    /** Liên kết mở lại app sau khi thanh toán (deep link); để trống thì trang kết quả không có nút này. */
    /** Scheme riêng, KHÔNG dùng com.animallocket.app: scheme đó đã thuộc về flutter_appauth (đăng nhập) và sẽ bị nó chặn. */
    private String appReturnUrl = "animallocket://checkout";
    /** Thời gian sống của mã trên URL thanh toán. */
    private Duration sessionTtl = Duration.ofMinutes(30);
    private String sandboxUrl = "https://api-m.sandbox.paypal.com";
    private String liveUrl = "https://api-m.paypal.com";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(8);

    public boolean isLive() {
        return "live".equalsIgnoreCase(mode);
    }

    public String apiBaseUrl() {
        if (isLive()) {
            return liveUrl;
        }
        if ("sandbox".equalsIgnoreCase(mode)) {
            return sandboxUrl;
        }
        throw new IllegalStateException("animal.billing.paypal.mode phải là sandbox hoặc live, nhận được: " + mode);
    }
}
