package com.animallocket.billing.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook payOS. Đường dẫn công khai (không có JWT, xem public-paths); {@link PayOsWebhookHandler} tự xác thực chữ ký.
 * 2xx = đã xử lý hoặc bỏ qua có chủ đích; 401 = chữ ký sai; 5xx = lỗi tạm thời để payOS gửi lại.
 */
@RestController
public class PayOsWebhookController {
    private final PayOsWebhookHandler handler;

    public PayOsWebhookController(PayOsWebhookHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/webhooks/payos")
    public Map<String, String> payos(@RequestBody JsonNode body) {
        return Map.of("status", handler.handle(body));
    }
}
