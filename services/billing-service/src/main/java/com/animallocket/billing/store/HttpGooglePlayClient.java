package com.animallocket.billing.store;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Gọi Google Play Developer API (androidpublisher v3) bằng access token của service account. */
public class HttpGooglePlayClient implements GooglePlayClient {
    private static final Logger log = LoggerFactory.getLogger(HttpGooglePlayClient.class);

    /** Lấy access token OAuth2 cho scope androidpublisher. */
    @FunctionalInterface
    public interface AccessTokenProvider {
        String accessToken() throws IOException;
    }

    private final GoogleProperties props;
    private final RestClient http;
    private final AccessTokenProvider tokens;
    private final StoreCaller caller;

    public HttpGooglePlayClient(GoogleProperties props, RestClient http, AccessTokenProvider tokens, StoreCaller caller) {
        this.props = props;
        this.http = http;
        this.tokens = tokens;
        this.caller = caller;
    }

    @Override
    public SubscriptionInfo getSubscription(String token) throws StoreException {
        JsonNode n = caller.call("store-google", () -> get(
                "/androidpublisher/v3/applications/{pkg}/purchases/subscriptionsv2/tokens/{token}", props.getPackageName(), token));
        try {
            List<LineItem> items = new ArrayList<>();
            for (JsonNode li : n.path("lineItems")) {
                items.add(new LineItem(text(li, "productId"), time(li, "expiryTime")));
            }
            return new SubscriptionInfo(text(n, "subscriptionState"), text(n, "latestOrderId"), text(n, "linkedPurchaseToken"),
                    "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(text(n, "acknowledgementState")), items);
        } catch (RuntimeException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Phản hồi subscriptionsv2 không đọc được", e);
        }
    }

    @Override
    public ProductInfo getProduct(String sku, String token) throws StoreException {
        JsonNode n = caller.call("store-google", () -> get(
                "/androidpublisher/v3/applications/{pkg}/purchases/products/{sku}/tokens/{token}", props.getPackageName(), sku, token));
        try {
            String millis = text(n, "purchaseTimeMillis");
            return new ProductInfo(text(n, "orderId"), n.path("purchaseState").asInt(-1), text(n, "productId"),
                    millis == null ? null : Instant.ofEpochMilli(Long.parseLong(millis)), n.path("acknowledgementState").asInt(0) == 1);
        } catch (RuntimeException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Phản hồi products không đọc được", e);
        }
    }

    @Override
    public void acknowledgeSubscription(String sku, String token) throws StoreException {
        caller.call("store-google", () -> post(
                "/androidpublisher/v3/applications/{pkg}/purchases/subscriptions/{sku}/tokens/{token}:acknowledge",
                props.getPackageName(), sku, token));
    }

    @Override
    public void acknowledgeProduct(String sku, String token) throws StoreException {
        caller.call("store-google", () -> post(
                "/androidpublisher/v3/applications/{pkg}/purchases/products/{sku}/tokens/{token}:acknowledge",
                props.getPackageName(), sku, token));
    }

    private JsonNode get(String path, Object... vars) throws StoreException {
        return exchange(() -> http.get().uri(props.getApiBaseUrl() + path, vars)
                .header("Authorization", "Bearer " + bearer()).retrieve().body(JsonNode.class));
    }

    private JsonNode post(String path, Object... vars) throws StoreException {
        exchange(() -> http.post().uri(props.getApiBaseUrl() + path, vars)
                .header("Authorization", "Bearer " + bearer()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("{}").retrieve().toBodilessEntity());
        return null;
    }

    private String bearer() {
        try {
            return tokens.accessToken();
        } catch (IOException e) {
            throw new TokenFailure(e);
        }
    }

    private static final class TokenFailure extends RuntimeException {
        TokenFailure(Throwable c) {
            super(c);
        }
    }

    private <T> JsonNode exchange(Supplier<T> call) throws StoreException {
        try {
            Object r = call.get();
            return r instanceof JsonNode j ? j : null;
        } catch (TokenFailure e) {
            log.error("Không lấy được access token Google: {}", e.getCause().toString());
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Không lấy được access token của Google", e);
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 404 || code == 410 || code == 400) {
                throw new StoreException(StoreException.Kind.INVALID, "Google không nhận token này (HTTP " + code + ")");
            }
            if (code == 401 || code == 403) {
                log.error("Google từ chối service account (HTTP {}): kiểm tra quyền API trong Play Console", code);
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "Google từ chối thông tin xác thực của server");
            }
            if (code == 429) {
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "Google giới hạn tốc độ");
            }
            throw new StoreException(StoreException.Kind.INVALID, "Google từ chối yêu cầu (HTTP " + code + ")");
        } catch (HttpServerErrorException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Google lỗi (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (ResourceAccessException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Không kết nối được tới Google", e);
        } catch (RestClientException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Phản hồi của Google không đọc được", e);
        }
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Instant time(JsonNode n, String f) {
        String t = text(n, f);
        return t == null ? null : OffsetDateTime.parse(t).toInstant();
    }
}
