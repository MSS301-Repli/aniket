package com.animallocket.discovery.client;

import com.animallocket.common.Headers;
import com.animallocket.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Configuration
class RemoteGatewayConfig {
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}

/** Gọi user-service. Lỗi/ngắt mạch → 503 rõ ràng (thao tác quẹt là fail closed; danh sách thẻ do controller chuyển thành fail soft). */
@Component
class RemoteUserGateway implements Gateways.UserGateway {
    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> breakers;

    RemoteUserGateway(RestClient.Builder lb, CircuitBreakerFactory<?, ?> breakers,
                      @Value("${animal.user-service-url:http://user-service}") String baseUrl,
                      @Value("${animal.internal-token:dev-internal-token}") String token) {
        this.client = lb.baseUrl(baseUrl).defaultHeader(Headers.INTERNAL_TOKEN, token).build();
        this.breakers = breakers;
    }

    @Override
    public Gateways.Profile profile(String id) {
        return call(() -> {
            try {
                return client.get().uri("/internal/users/{id}", id).retrieve().body(Gateways.Profile.class);
            } catch (HttpClientErrorException.NotFound e) {
                return null;
            }
        });
    }

    @Override
    public Map<String, Gateways.Profile> profiles(List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Gateways.Profile> list = call(() -> client.get()
                .uri(b -> b.path("/internal/users").queryParam("ids", String.join(",", ids)).build())
                .retrieve().body(new ParameterizedTypeReference<List<Gateways.Profile>>() {
                }));
        return list == null ? Map.of() : list.stream().collect(Collectors.toMap(Gateways.Profile::publicId, p -> p, (a, b) -> a));
    }

    @Override
    public Gateways.Exclusions exclusions(String id) {
        return call(() -> client.get().uri("/internal/exclusions/{id}", id).retrieve().body(Gateways.Exclusions.class));
    }

    @Override
    public String swipeRight(String from, String to) {
        record Res(String result) {
        }
        Res r = call(() -> client.post().uri("/internal/swipes/right").body(Map.of("from", from, "to", to))
                .retrieve().body(Res.class));
        return r == null ? "REQUESTED" : r.result();
    }

    private <T> T call(Supplier<T> s) {
        return breakers.create("user-service").run(s, t -> {
            if (t instanceof HttpClientErrorException h && h.getStatusCode().value() == 403) {
                throw ApiException.forbidden("Không thể kết nối với người dùng này");
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE",
                    "Tạm thời không thực hiện được, vui lòng thử lại");
        });
    }
}

@Component
class RemoteBillingGateway implements Gateways.BillingGateway {
    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> breakers;

    RemoteBillingGateway(RestClient.Builder lb, CircuitBreakerFactory<?, ?> breakers,
                         @Value("${animal.billing-service-url:http://billing-service}") String baseUrl,
                         @Value("${animal.internal-token:dev-internal-token}") String token) {
        this.client = lb.baseUrl(baseUrl).defaultHeader(Headers.INTERNAL_TOKEN, token).build();
        this.breakers = breakers;
    }

    @Override
    public boolean consumeBoost(String publicId, String idempotencyKey) {
        return breakers.create("billing-service").run(() -> {
            try {
                // billing-service hiện bỏ qua header này; gửi sẵn để sau này khử trùng lặp phía billing.
                client.post().uri("/internal/wallet/consume").header(Headers.IDEMPOTENCY_KEY, idempotencyKey)
                        .body(Map.of("publicId", publicId, "item", "BOOST"))
                        .retrieve().toBodilessEntity();
                return true;
            } catch (HttpClientErrorException e) {
                // 402/409 = không đủ số dư; lỗi 4xx khác là lỗi thật
                if (e.getStatusCode().value() == 402 || e.getStatusCode().value() == 409) {
                    return false;
                }
                throw e;
            }
        }, t -> {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_UNAVAILABLE",
                    "Tạm thời không dùng được Boost, vui lòng thử lại");
        });
    }
}
