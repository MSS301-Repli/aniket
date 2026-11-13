package com.animallocket.billing.store;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Gọi payOS REST. Đường dẫn, header và thuật toán ký lấy từ tài liệu chính thức (https://payos.vn/docs/api/) và SDK Java;
 * chưa chạy với tài khoản payOS thật, xem PAYOS.md.
 */
public class HttpPayOsClient implements PayOsClient {
    private static final Logger log = LoggerFactory.getLogger(HttpPayOsClient.class);
    private static final String CALL = "payos";
    private static final String OK = "00";

    private final RestClient http;
    private final PayOsProperties props;
    private final StoreCaller caller;

    public HttpPayOsClient(PayOsProperties props, RestClient http, StoreCaller caller) {
        this.props = props;
        this.http = http;
        this.caller = caller;
    }

    @Override
    public PaymentLink createPaymentLink(long orderCode, int amount, String description, String itemName,
                                         String returnUrl, String cancelUrl) throws StoreException {
        Map<String, Object> body = Map.of(
                "orderCode", orderCode,
                "amount", amount,
                "description", description,
                "items", List.of(Map.of("name", itemName, "quantity", 1, "price", amount)),
                "cancelUrl", cancelUrl,
                "returnUrl", returnUrl,
                "signature", PayOsSignature.forPaymentRequest(props.getChecksumKey(), amount, cancelUrl, description,
                        orderCode, returnUrl));
        JsonNode data = send(HttpMethod.POST, "/v2/payment-requests", body);
        String id = data.path("paymentLinkId").asText(null);
        String checkout = data.path("checkoutUrl").asText(null);
        if (id == null || id.isBlank() || checkout == null || checkout.isBlank()) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "payOS trả link thiếu paymentLinkId hoặc checkoutUrl");
        }
        return new PaymentLink(data.path("orderCode").asLong(orderCode), id, checkout, data.path("status").asText(""));
    }

    @Override
    public PaymentInfo getPaymentInfo(long orderCode) throws StoreException {
        JsonNode data = send(HttpMethod.GET, "/v2/payment-requests/" + orderCode, null);
        return new PaymentInfo(data.path("orderCode").asLong(orderCode), data.path("status").asText(""),
                data.path("amount").asLong(-1), data.path("amountPaid").asLong(0));
    }

    @Override
    public boolean verifyWebhookSignature(JsonNode data, String signature) {
        if (data == null || !data.isObject() || signature == null || signature.isBlank()) {
            return false;
        }
        return PayOsSignature.matches(PayOsSignature.forData(props.getChecksumKey(), data), signature);
    }

    /** Trả {@code data} của phản hồi; code khác "00" là payOS từ chối yêu cầu (INVALID, không retry). */
    private JsonNode send(HttpMethod method, String path, Object body) throws StoreException {
        return caller.call(CALL, () -> {
            try {
                RestClient.RequestBodySpec req = http.method(method).uri(props.getBaseUrl() + path)
                        .header("x-client-id", props.getClientId())
                        .header("x-api-key", props.getApiKey());
                if (body != null) {
                    req.contentType(MediaType.APPLICATION_JSON).body(body);
                }
                JsonNode res = req.retrieve().body(JsonNode.class);
                if (res == null) {
                    throw new StoreException(StoreException.Kind.UNAVAILABLE, "payOS trả nội dung rỗng");
                }
                if (!OK.equals(res.path("code").asText(""))) {
                    log.warn("payOS từ chối yêu cầu {} {}: code={} desc={}", method, path, res.path("code").asText(""),
                            res.path("desc").asText(""));
                    throw new StoreException(StoreException.Kind.INVALID,
                            "payOS từ chối (" + res.path("code").asText("?") + ": " + res.path("desc").asText("") + ")");
                }
                JsonNode data = res.path("data");
                if (!data.isObject()) {
                    throw new StoreException(StoreException.Kind.UNAVAILABLE, "payOS trả phản hồi thiếu data");
                }
                return data;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 429 || status >= 500 || status == 401 || status == 403) {
                    if (status == 401 || status == 403) {
                        log.error("payOS từ chối khoá API (HTTP {}). Kiểm tra PAYOS_CLIENT_ID / PAYOS_API_KEY.", status);
                    }
                    throw new StoreException(StoreException.Kind.UNAVAILABLE, "payOS lỗi tạm thời (" + status + ")", e);
                }
                log.warn("payOS từ chối yêu cầu {} {}: HTTP {}", method, path, status);
                throw new StoreException(StoreException.Kind.INVALID, "payOS từ chối yêu cầu (" + status + ")", e);
            } catch (ResourceAccessException e) {
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "Không kết nối được payOS", e);
            }
        });
    }
}
