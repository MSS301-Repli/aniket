package com.animallocket.billing.store;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình App Store. Mọi giá trị mặc định đều rỗng: chưa cấu hình thì tích hợp Apple TẮT (fail closed).
 * {@code root-cert-files} viết dạng chuỗi phân tách bằng dấu phẩy (ví dụ biến môi trường APPLE_ROOT_CERT_FILES).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "animal.billing.apple")
public class AppleProperties {
    /** Bundle id của app iOS; payload của Apple phải khớp. */
    private String bundleId = "";
    /** Issuer ID trong App Store Connect → Users and Access → Integrations → In-App Purchase. */
    private String issuerId = "";
    /** Key ID của khoá API (.p8). */
    private String keyId = "";
    /** Đường dẫn file .p8 (PKCS#8 PEM) của khoá API In-App Purchase. */
    private String privateKeyFile = "";
    /** Các file PEM/DER chứa Apple Root CA (G3). Chuỗi chứng chỉ trong JWS phải kết thúc ở một trong số này. */
    private List<String> rootCertFiles = new ArrayList<>();
    /** PRODUCTION hoặc SANDBOX: môi trường gọi App Store Server API. */
    private String environment = "PRODUCTION";
    /** Chỉ bật khi App Review/TestFlight cần: 404 ở production thì thử sandbox. Tắt ở production thật. */
    private boolean allowSandboxFallback = false;
    /** Leaf cert của Apple có OID 1.2.840.113635.100.6.11.1; mặc định bắt buộc. */
    private boolean requireAppleOid = true;
    private String productionUrl = "https://api.storekit.itunes.apple.com";
    private String sandboxUrl = "https://api.storekit-sandbox.itunes.apple.com";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);

    public boolean isSandbox() {
        return "SANDBOX".equalsIgnoreCase(environment);
    }
}
