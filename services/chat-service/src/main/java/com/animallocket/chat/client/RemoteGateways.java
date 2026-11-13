package com.animallocket.chat.client;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Headers;
import com.animallocket.common.Topics;
import com.animallocket.common.event.EventEnvelope;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

@Configuration
class RemoteGatewayConfig {
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}

/** Quan hệ bạn bè: không xác minh được thì fail closed (không cho gửi tin). */
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
    public Gateways.Relationship relationship(String a, String b) {
        return breakers.create("user-service").run(
                () -> client.get().uri("/internal/relationship?a={a}&b={b}", a, b).retrieve().body(Gateways.Relationship.class),
                t -> {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE",
                            "Tạm thời không gửi được tin nhắn, vui lòng thử lại");
                });
    }
}

@Component
class RemoteMediaGateway implements Gateways.MediaGateway {
    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> breakers;

    RemoteMediaGateway(RestClient.Builder lb, CircuitBreakerFactory<?, ?> breakers,
                       @Value("${animal.media-service-url:http://media-service}") String baseUrl,
                       @Value("${animal.internal-token:dev-internal-token}") String token) {
        this.client = lb.baseUrl(baseUrl).defaultHeader(Headers.INTERNAL_TOKEN, token).build();
        this.breakers = breakers;
    }

    @Override
    public Gateways.MediaInfo media(String mediaId) {
        Supplier<Gateways.MediaInfo> call = () -> {
            try {
                return client.get().uri("/internal/media/{id}", mediaId).retrieve().body(Gateways.MediaInfo.class);
            } catch (HttpClientErrorException.NotFound e) {
                return null;
            }
        };
        return breakers.create("media-service").run(call, t -> {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_SERVICE_UNAVAILABLE",
                    "Tạm thời không gửi được ảnh, vui lòng thử lại");
        });
    }
}

@Component
class KafkaEventPublisher implements Gateways.EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private final KafkaTemplate<String, String> kafka;

    KafkaEventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void messageSent(String conversationId, String sender, String recipient) {
        try {
            EventEnvelope e = EventEnvelope.of(EventTypes.MESSAGE_SENT, "chat-service", recipient,
                    new Events.MessageSent(conversationId, sender, recipient));
            kafka.send(Topics.CHAT, recipient, EventJson.write(e));
        } catch (RuntimeException ex) {
            log.warn("Không phát được MESSAGE_SENT: {}", ex.toString());
        }
    }
}
