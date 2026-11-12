package com.animallocket.billing.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Gọi PayPal REST (Orders v2). Chưa được chạy thử với PayPal thật; xem PAYPAL.md. */
public class HttpPayPalClient implements PayPalClient {
    private static final Logger log = LoggerFactory.getLogger(HttpPayPalClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CALL = "paypal";

    /** PayPal từ chối yêu cầu (4xx): lỗi của yêu cầu, không retry. {@code issue} là mã như ORDER_ALREADY_CAPTURED. */
    static final class Rejected extends StoreException {
        private final String issue;

        Rejected(String issue, String message) {
            super(Kind.INVALID, message);
            this.issue = issue;
        }

        String issue() {
            return issue;
        }
    }

    private final RestClient http;
    private final PayPalProperties props;
    private final StoreCaller caller;
    private final Clock clock;
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public HttpPayPalClient(PayPalProperties props, RestClient http, StoreCaller caller, Clock clock) {
        this.props = props;
        this.http = http;
        this.caller = caller;
        this.clock = clock;
    }

    @Override
    public CreatedOrder createOrder(String requestId, String customId, String description, String amountValue,
                                    String currency, String returnUrl, String cancelUrl) throws StoreException {
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "custom_id", customId,
                        "description", description,
                        "amount", Map.of("currency_code", currency, "value", amountValue))),
                "payment_source", Map.of("paypal", Map.of("experience_context", Map.of(
                        "brand_name", "Animal Locket",
                        "user_action", "PAY_NOW",
                        "shipping_preference", "NO_SHIPPING",
                        "return_url", returnUrl,
                        "cancel_url", cancelUrl))));
        JsonNode res = send("POST", "/v2/checkout/orders", body, requestId);
        String id = res.path("id").asText(null);
        String approve = null;
        for (JsonNode link : res.path("links")) {
            String rel = link.path("rel").asText("");
            if (rel.equals("payer-action") || rel.equals("approve")) {
                approve = link.path("href").asText(null);
                break;
            }
        }
        if (id == null || id.isBlank() || approve == null || approve.isBlank()) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "PayPal trả đơn hàng thiếu id hoặc liên kết duyệt");
        }
        return new CreatedOrder(id, approve);
    }

    @Override
    public Capture capture(String orderId, String requestId) throws StoreException {
        try {
            return parseCapture(send("POST", "/v2/checkout/orders/" + orderId + "/capture", Map.of(), requestId));
        } catch (Rejected e) {
            if ("ORDER_ALREADY_CAPTURED".equals(e.issue())) {
                return parseCapture(send("GET", "/v2/checkout/orders/" + orderId, null, null));
            }
            throw e;
        }
    }

    @Override
    public boolean verifyWebhook(WebhookHeaders h, JsonNode event) throws StoreException {
        if (h == null || !h.complete() || event == null || props.getWebhookId().isBlank()) {
            return false;
        }
        Map<String, Object> body = Map.of(
                "auth_algo", h.authAlgo(),
                "cert_url", h.certUrl(),
                "transmission_id", h.transmissionId(),
                "transmission_sig", h.transmissionSig(),
                "transmission_time", h.transmissionTime(),
                "webhook_id", props.getWebhookId(),
                "webhook_event", event);
        JsonNode res = send("POST", "/v1/notifications/verify-webhook-signature", body, null);
        return "SUCCESS".equals(res.path("verification_status").asText(""));
    }

    static Capture parseCapture(JsonNode order) {
        JsonNode unit = order.path("purchase_units").path(0);
        JsonNode cap = unit.path("payments").path("captures").path(0);
        String custom = cap.path("custom_id").asText(null);
        if (custom == null) {
            custom = unit.path("custom_id").asText(null);
        }
        Instant created;
        try {
            created = Instant.parse(cap.path("create_time").asText(""));
        } catch (RuntimeException e) {
            created = null;
        }
        return new Capture(order.path("id").asText(null), order.path("status").asText(null),
                cap.path("id").asText(null), cap.path("status").asText(null),
                cap.path("amount").path("value").asText(null), cap.path("amount").path("currency_code").asText(null),
                custom, created);
    }

    private JsonNode send(String method, String path, Object body, String requestId) throws StoreException {
        return caller.call(CALL, () -> {
            String token = token();
            try {
                RestClient.RequestBodySpec req = http.method(org.springframework.http.HttpMethod.valueOf(method))
                        .uri(props.apiBaseUrl() + path)
                        .headers(h -> {
                            h.setBearerAuth(token);
                            if (requestId != null) {
                                h.set("PayPal-Request-Id", requestId);
                            }
                        });
                if (body != null) {
                    req.contentType(MediaType.APPLICATION_JSON).body(body);
                }
                JsonNode res = req.retrieve().body(JsonNode.class);
                if (res == null) {
                    throw new StoreException(StoreException.Kind.UNAVAILABLE, "PayPal trả nội dung rỗng");
                }
                return res;
            } catch (RestClientResponseException e) {
                throw translate(e);
            } catch (ResourceAccessException e) {
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "Không kết nối được PayPal", e);
            }
        });
    }

    private StoreException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401) {
            invalidateToken();
            return new StoreException(StoreException.Kind.UNAVAILABLE, "PayPal từ chối access token, sẽ lấy token mới", e);
        }
        if (status == 429 || status >= 500) {
            return new StoreException(StoreException.Kind.UNAVAILABLE, "PayPal lỗi tạm thời (" + status + ")", e);
        }
        String issue = null;
        try {
            JsonNode err = JSON.readTree(e.getResponseBodyAsString());
            issue = err.path("details").path(0).path("issue").asText(null);
            if (issue == null) {
                issue = err.path("name").asText(null);
            }
        } catch (Exception ignored) {
            // nội dung lỗi không phải JSON: chỉ còn mã HTTP
        }
        log.warn("PayPal từ chối yêu cầu: HTTP {} issue={}", status, issue);
        return new Rejected(issue, "PayPal từ chối yêu cầu (" + status + (issue == null ? "" : ", " + issue) + ")");
    }

    private synchronized String token() throws StoreException {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(tokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }
        try {
            JsonNode res = http.post().uri(props.apiBaseUrl() + "/v1/oauth2/token")
                    .headers(h -> h.setBasicAuth(props.getClientId(), props.getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve().body(JsonNode.class);
            String t = res == null ? null : res.path("access_token").asText(null);
            if (t == null || t.isBlank()) {
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "PayPal không trả access token");
            }
            long ttl = Math.max(res.path("expires_in").asLong(300), 120);
            accessToken = t;
            tokenExpiresAt = now.plus(Duration.ofSeconds(ttl));
            return t;
        } catch (RestClientResponseException e) {
            log.error("Không lấy được access token PayPal (HTTP {}). Kiểm tra PAYPAL_CLIENT_ID/SECRET và PAYPAL_MODE.",
                    e.getStatusCode().value());
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "PayPal từ chối thông tin đăng nhập", e);
        } catch (ResourceAccessException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Không kết nối được PayPal", e);
        }
    }

    private synchronized void invalidateToken() {
        accessToken = null;
        tokenExpiresAt = Instant.EPOCH;
    }
}
