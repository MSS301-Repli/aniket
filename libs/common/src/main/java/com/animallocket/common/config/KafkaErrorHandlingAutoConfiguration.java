package com.animallocket.common.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Message xử lý lỗi được thử lại 3 lần (cách nhau 1 giây) rồi chuyển sang topic {@code <topic>.dlq}, thay vì bị
 * bỏ qua im lặng như mặc định của Spring Kafka. Message không phải event hợp lệ ({@code IllegalArgumentException}
 * từ {@code EventJson.read}/{@code payloadAs}) vào DLQ ngay, không thử lại vô ích.
 * Service tự khai báo {@link CommonErrorHandler} thì cấu hình này lùi lại.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass({KafkaTemplate.class, DefaultErrorHandler.class})
public class KafkaErrorHandlingAutoConfiguration {

    public static final String DLQ_SUFFIX = ".dlq";
    static final long RETRY_INTERVAL_MS = 1000L;
    static final long RETRIES = 3L;

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler animalKafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template, KafkaErrorHandlingAutoConfiguration::dlqDestination);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRIES));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    /** {@code <topic>.dlq}, partition -1 để producer tự chọn (topic DLQ có thể ít partition hơn topic gốc). */
    static TopicPartition dlqDestination(ConsumerRecord<?, ?> record, Exception ex) {
        return new TopicPartition(record.topic() + DLQ_SUFFIX, -1);
    }
}
