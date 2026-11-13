package com.animallocket.billing.store;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình payOS (VietQR). Để trống clientId/apiKey/checksumKey = TẮT (fail closed).
 * payOS KHÔNG có sandbox: mọi giao dịch là tiền thật (https://payos.vn/docs/moi-truong-test).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "animal.billing.payos")
public class PayOsProperties {
    private String clientId = "";
    private String apiKey = "";
    /** Checksum key của kênh thanh toán: dùng ký yêu cầu tạo link và xác thực webhook. Không bao giờ gửi đi. */
    private String checksumKey = "";
    private String baseUrl = "https://api-merchant.payos.vn";
    /**
     * orderCode của link = base + id phiên * 100 + lần thứ n. Đặt base KHÁC nhau cho mỗi môi trường (dev, production)
     * nếu dùng chung một kênh payOS, vì payOS yêu cầu orderCode không trùng.
     */
    private long orderCodeBase = 100_000_000_000L;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(8);
}
