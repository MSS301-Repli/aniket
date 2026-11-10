package com.animallocket.notification;

import com.animallocket.common.Headers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class UserServiceDeviceDirectory implements DeviceDirectory {

    @Configuration
    static class Config {
        @Bean
        @LoadBalanced
        RestClient.Builder loadBalancedRestClientBuilder() {
            return RestClient.builder();
        }
    }

    private final RestClient client;

    public UserServiceDeviceDirectory(RestClient.Builder lbBuilder,
                                      @Value("${animal.user-service-url:http://user-service}") String baseUrl,
                                      @Value("${animal.internal-token:dev-internal-token}") String token) {
        this.client = lbBuilder.baseUrl(baseUrl).defaultHeader(Headers.INTERNAL_TOKEN, token).build();
    }

    @Override
    public List<String> tokens(String publicId) {
        List<String> r = client.get().uri("/internal/devices/{id}", publicId).retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {
                });
        return r == null ? List.of() : r;
    }

    @Override
    public List<String> friendIds(String publicId) {
        List<String> r = client.get().uri("/internal/friends/{id}", publicId).retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {
                });
        return r == null ? List.of() : r;
    }

    @Override
    public void removeToken(String token) {
        client.delete().uri("/internal/devices/{token}", token).retrieve().toBodilessEntity();
    }
}
