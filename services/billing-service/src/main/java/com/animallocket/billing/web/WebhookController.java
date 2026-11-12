package com.animallocket.billing.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook của store. Các đường dẫn này KHÔNG qua JWT của người dùng (cấu hình public-paths) nên mỗi handler tự xác
 * thực chữ ký/token của store trước khi làm bất cứ điều gì. Trả 2xx khi đã xử lý (hoặc trùng/bỏ qua có chủ đích);
 * 401 khi xác thực sai; 5xx khi lỗi tạm thời để store gửi lại.
 */
@RestController
public class WebhookController {
    public record AppleBody(@NotBlank String signedPayload) {
    }

    private final AppleWebhookHandler apple;
    private final GoogleWebhookHandler google;

    public WebhookController(AppleWebhookHandler apple, GoogleWebhookHandler google) {
        this.apple = apple;
        this.google = google;
    }

    @PostMapping("/webhooks/apple")
    public Map<String, String> apple(@Valid @RequestBody AppleBody body) {
        return Map.of("status", apple.handle(body.signedPayload()));
    }

    @PostMapping("/webhooks/google")
    public Map<String, String> google(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @RequestBody JsonNode body) {
        return Map.of("status", google.handle(authorization, body));
    }
}
