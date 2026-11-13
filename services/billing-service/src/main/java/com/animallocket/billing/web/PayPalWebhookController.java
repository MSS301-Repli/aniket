package com.animallocket.billing.web;

import com.animallocket.billing.store.PayPalClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook PayPal. Đường dẫn công khai (không có JWT, xem public-paths); {@link PayPalWebhookHandler} nhờ PayPal xác minh chữ ký
 * trước khi làm gì khác. 2xx = đã xử lý hoặc bỏ qua có chủ đích; 401 = chữ ký sai; 5xx = lỗi tạm thời để PayPal gửi lại.
 */
@RestController
public class PayPalWebhookController {
    private final PayPalWebhookHandler handler;

    public PayPalWebhookController(PayPalWebhookHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/webhooks/paypal")
    public Map<String, String> paypal(@RequestHeader HttpHeaders headers, @RequestBody JsonNode body) {
        var h = new PayPalClient.WebhookHeaders(
                headers.getFirst("PAYPAL-AUTH-ALGO"), headers.getFirst("PAYPAL-CERT-URL"),
                headers.getFirst("PAYPAL-TRANSMISSION-ID"), headers.getFirst("PAYPAL-TRANSMISSION-SIG"),
                headers.getFirst("PAYPAL-TRANSMISSION-TIME"));
        return Map.of("status", handler.handle(h, body));
    }
}
