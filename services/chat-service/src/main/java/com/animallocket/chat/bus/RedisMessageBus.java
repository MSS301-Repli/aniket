package com.animallocket.chat.bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/** Kênh {@code chat:user:{publicId}}. Mỗi instance PSUBSCRIBE một lần và chỉ chuyển cho phiên WebSocket cục bộ. */
public class RedisMessageBus implements MessageBus {
    private static final Logger log = LoggerFactory.getLogger(RedisMessageBus.class);
    static final String PREFIX = "chat:user:";

    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer container;

    public RedisMessageBus(StringRedisTemplate redis, RedisMessageListenerContainer container) {
        this.redis = redis;
        this.container = container;
    }

    @Override
    public void publish(String publicId, String json) {
        redis.convertAndSend(PREFIX + publicId, json);
    }

    @Override
    public void subscribe(BiConsumer<String, String> deliverLocally) {
        MessageListener listener = (Message message, byte[] pattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                deliverLocally.accept(channel.substring(PREFIX.length()), body);
            } catch (RuntimeException e) {
                log.warn("Giao tin cục bộ lỗi: {}", e.toString());
            }
        };
        container.addMessageListener(listener, new PatternTopic(PREFIX + "*"));
    }
}
