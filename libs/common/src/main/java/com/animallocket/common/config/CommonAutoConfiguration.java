package com.animallocket.common.config;

import com.animallocket.common.Headers;
import com.animallocket.common.web.ApiExceptionHandler;
import com.animallocket.common.web.InternalTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Cấu hình chung cho mọi service servlet: xử lý lỗi RFC 7807, bảo vệ {@code /internal/**},
 * và kiểm tra lại JWT (gateway đã kiểm tra một lần, service kiểm tra lần nữa).
 *
 * <ul>
 *   <li>{@code animal.internal-token}: khoá gọi nội bộ (bắt buộc ở prod, xem {@link InternalTokens}).</li>
 *   <li>{@code animal.security.enabled=false}: tắt kiểm tra JWT (chỉ dùng cho test/dev).</li>
 *   <li>{@code animal.security.public-paths}: đường dẫn không cần JWT, phân tách bằng dấu phẩy (ví dụ {@code /ws/**}).</li>
 *   <li>{@code animal.security.cors.allowed-origins}: origin được phép gọi trực tiếp; để trống = tắt CORS
 *       (mặc định, vì client đi qua gateway cùng origin hoặc là app di động).</li>
 * </ul>
 * Service tự khai báo {@link SecurityFilterChain} riêng thì chain mặc định này lùi lại.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(ApiExceptionHandler.class)
public class CommonAutoConfiguration {

    static final String[] ALWAYS_PUBLIC = {"/actuator/health/**", "/actuator/prometheus", "/actuator/info"};

    @Bean
    InternalTokenFilter internalTokenFilter(Environment env) {
        boolean prod = env.acceptsProfiles(Profiles.of("prod"));
        return new InternalTokenFilter(InternalTokens.resolve(env.getProperty(InternalTokens.PROPERTY), prod));
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain animalSecurityFilterChain(
            HttpSecurity http,
            InternalTokenFilter internalTokenFilter,
            @Value("${animal.security.enabled:true}") boolean enabled,
            @Value("${animal.security.public-paths:}") String[] publicPaths,
            @Value("${animal.security.cors.allowed-origins:}") String[] allowedOrigins) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // API stateless dùng Bearer token, không cookie
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(internalTokenFilter, BasicAuthenticationFilter.class);

        List<String> origins = nonBlank(allowedOrigins);
        if (origins.isEmpty()) {
            http.cors(AbstractHttpConfigurer::disable);
        } else {
            http.cors(c -> c.configurationSource(corsSource(origins)));
        }

        if (!enabled) {
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }
        List<String> extraPublic = nonBlank(publicPaths);
        http.authorizeHttpRequests(a -> {
                    a.requestMatchers(ALWAYS_PUBLIC).permitAll();
                    a.requestMatchers("/internal", "/internal/**").permitAll(); // đã có InternalTokenFilter
                    a.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    if (!extraPublic.isEmpty()) {
                        a.requestMatchers(extraPublic.toArray(String[]::new)).permitAll();
                    }
                    a.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }

    static UrlBasedCorsConfigurationSource corsSource(List<String> origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, Headers.IDEMPOTENCY_KEY));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private static List<String> nonBlank(String[] values) {
        return Arrays.stream(values).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
