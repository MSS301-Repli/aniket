package com.animallocket.common.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaErrorHandlingAutoConfigurationTest {

    private final ApplicationContextRunner kafka = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class, KafkaErrorHandlingAutoConfiguration.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092");

    @Test
    void consumersGetADeadLetterErrorHandlerByDefault() {
        kafka.run(ctx -> assertThat(ctx.getBean(CommonErrorHandler.class)).isInstanceOf(DefaultErrorHandler.class));
    }

    @Test
    void servicesCanOverrideTheErrorHandler() {
        CommonErrorHandler custom = new DefaultErrorHandler();
        kafka.withBean("custom", CommonErrorHandler.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(CommonErrorHandler.class)).isSameAs(custom));
    }

    @Test
    void absentWithoutKafkaOnClasspath() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaErrorHandlingAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(KafkaTemplate.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CommonErrorHandler.class));
    }

    @Test
    void failedRecordsGoToTheTopicDlq() {
        var rec = new ConsumerRecord<>("media.events", 3, 42L, "k", "v");
        var tp = KafkaErrorHandlingAutoConfiguration.dlqDestination(rec, new RuntimeException("boom"));
        assertThat(tp.topic()).isEqualTo("media.events.dlq");
        assertThat(tp.partition()).isEqualTo(-1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidEventsSkipRetriesAndArePublishedToDlqImmediately() {
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(template.isTransactional()).thenReturn(false);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        DefaultErrorHandler handler = new KafkaErrorHandlingAutoConfiguration().animalKafkaErrorHandler(template);

        var rec = new ConsumerRecord<Object, Object>("user.events", 0, 7L, "k", "{not json");
        var error = new ListenerExecutionFailedException("listener", new IllegalArgumentException("Event JSON không hợp lệ"));
        boolean handled = handler.handleOne(error, rec, mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(handled).isTrue();
        verify(template, timeout(2000)).send(org.mockito.ArgumentMatchers.<ProducerRecord<Object, Object>>argThat(
                pr -> pr.topic().equals("user.events.dlq")));
    }

    @Test
    void transientErrorsAreRetriedBeforeDlq() {
        DefaultErrorHandler handler = new KafkaErrorHandlingAutoConfiguration()
                .animalKafkaErrorHandler(mockTemplate());
        var rec = new ConsumerRecord<Object, Object>("user.events", 0, 7L, "k", "v");
        var error = new ListenerExecutionFailedException("listener", new IllegalStateException("db down"));
        // Lần lỗi đầu tiên: chưa recover, để container thử lại (seek về offset cũ).
        assertThat(handler.handleOne(error, rec, mock(Consumer.class), mock(MessageListenerContainer.class))).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<Object, Object> mockTemplate() {
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        return template;
    }
}
