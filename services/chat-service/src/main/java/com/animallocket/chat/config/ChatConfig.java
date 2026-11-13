package com.animallocket.chat.config;

import com.animallocket.chat.bus.LocalMessageBus;
import com.animallocket.chat.bus.MessageBus;
import com.animallocket.chat.bus.RedisMessageBus;
import com.animallocket.chat.service.ChatService;
import com.animallocket.chat.service.GlyphSettings;
import com.animallocket.chat.ws.ChatWebSocketHandler;
import com.animallocket.chat.ws.PublicIdHandshakeInterceptor;
import com.animallocket.chat.ws.SessionRegistry;
import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.kv.KeyValueStore;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Set;

@Configuration
@EnableWebSocket
public class ChatConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;

    public ChatConfig(ChatWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Mobile không gửi Origin; nếu có web client sau này, đặt animal.chat.allowed-origins.
        registry.addHandler(handler, "/ws/chat").addInterceptors(new PublicIdHandshakeInterceptor())
                .setAllowedOriginPatterns("*");
    }

    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean c = new ServletServerContainerFactoryBean();
        c.setMaxTextMessageBufferSize(32 * 1024);
        c.setMaxSessionIdleTimeout(120_000L);
        return c;
    }

    @Configuration
    static class Beans {
        @Bean
        @ConditionalOnMissingBean(KeyValueStore.class)
        KeyValueStore fallbackKeyValueStore() {
            return new InMemoryKeyValueStore();
        }

        /** Palette là chuỗi phân cách bằng dấu phẩy (YAML list không bind được qua @Value). */
        @Bean
        GlyphSettings glyphSettings(@Value("${animal.chat.glyph.write-enabled:true}") boolean writeEnabled,
                                    @Value("${animal.chat.glyph.palette:}") String palette,
                                    @Value("${animal.chat.glyph.max-active:100}") int maxActive,
                                    @Value("${animal.chat.glyph.max-creates-per-day:20}") int maxCreatesPerDay) {
            Set<String> colors = palette.isBlank() ? Set.copyOf(GlyphSettings.DEFAULT_PALETTE)
                    : Set.of(palette.split(","));
            return new GlyphSettings(writeEnabled, colors, maxActive, maxCreatesPerDay);
        }

        @Bean
        SessionRegistry sessionRegistry(KeyValueStore kv) {
            return new SessionRegistry(kv);
        }

        @Bean
        ChatWebSocketHandler chatWebSocketHandler(SessionRegistry registry, @org.springframework.context.annotation.Lazy ChatService chat) {
            return new ChatWebSocketHandler(registry, chat);
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "animal.kv.type", havingValue = "memory")
    static class Local {
        @Bean
        MessageBus localMessageBus() {
            return new LocalMessageBus();
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "animal.kv.type", havingValue = "redis", matchIfMissing = true)
    static class Redis {
        @Bean
        RedisMessageListenerContainer redisListenerContainer(RedisConnectionFactory cf) {
            RedisMessageListenerContainer c = new RedisMessageListenerContainer();
            c.setConnectionFactory(cf);
            return c;
        }

        @Bean
        MessageBus redisMessageBus(StringRedisTemplate redis, RedisMessageListenerContainer container) {
            return new RedisMessageBus(redis, container);
        }
    }

    /** Nối bus với registry: tin tới instance này sẽ được chuyển cho các phiên WebSocket cục bộ. */
    @Configuration
    static class Wiring {
        private final MessageBus bus;
        private final SessionRegistry registry;

        Wiring(MessageBus bus, SessionRegistry registry) {
            this.bus = bus;
            this.registry = registry;
        }

        @PostConstruct
        void wire() {
            bus.subscribe(registry::deliver);
        }
    }

    @SuppressWarnings("unused")
    private static final String UNUSED = Value.class.getName();
}
