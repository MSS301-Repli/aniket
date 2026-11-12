package com.animallocket.outbox;

import com.animallocket.common.event.EventJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = OutboxTestApp.class)
class OutboxFlowTest {

    record Payload(String publicId) {
    }

    @Autowired
    OutboxService outbox;
    @Autowired
    OutboxRepository repository;
    @Autowired
    TransactionTemplate tx;
    @MockBean
    KafkaTemplate<String, String> kafka;

    OutboxRelay relay;
    final List<String> sentKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        sentKeys.clear();
        relay = new OutboxRelay(repository, kafka);
        succeedSends();
    }

    private void succeedSends() {
        doAnswer(inv -> {
            sentKeys.add(inv.getArgument(1));
            return CompletableFuture.completedFuture((SendResult<String, String>) null);
        }).when(kafka).send(anyString(), anyString(), anyString());
    }

    private void publish(String key) {
        sleepPastClockTick(); // createdAt quyết định thứ tự relay: tránh hai event cùng một tick đồng hồ
        tx.executeWithoutResult(s -> outbox.publish("user.events", "USER_DELETED", key, new Payload(key)));
    }

    private List<OutboxEvent> rows() {
        return repository.findAll().stream().sorted(Comparator.comparing(OutboxEvent::getCreatedAt)).toList();
    }

    @Test
    void publishRequiresAnExistingTransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> outbox.publish("user.events", "USER_DELETED", "k", new Payload("k")));
    }

    @Test
    void publishStoresTheEnvelopeAndRollsBackWithTheBusinessTransaction() {
        publish("a");
        OutboxEvent row = rows().get(0);
        var env = EventJson.read(row.getPayload());
        assertThat(env.eventId()).isEqualTo(row.getId());
        assertThat(env.producer()).isEqualTo("outbox-test");
        assertThat(env.payloadAs(Payload.class).publicId()).isEqualTo("a");
        assertThat(row.getPublishedAt()).isNull();

        tx.executeWithoutResult(s -> {
            outbox.publish("user.events", "USER_DELETED", "b", new Payload("b"));
            s.setRollbackOnly();
        });
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void relaySendsInCreationOrderAndMarksPublished() {
        publish("a");
        publish("b");
        publish("c");
        tx.executeWithoutResult(s -> relay.relay());
        assertThat(sentKeys).containsExactly("a", "b", "c");
        assertThat(rows()).allSatisfy(r -> assertThat(r.getPublishedAt()).isNotNull());
        verify(kafka).send(eq("user.events"), eq("a"), anyString());

        sentKeys.clear();
        tx.executeWithoutResult(s -> relay.relay());
        assertThat(sentKeys).as("không gửi lại event đã đẩy").isEmpty();
    }

    @Test
    void relayStopsAtFirstFailureToKeepOrder() {
        publish("a");
        publish("b");
        doAnswer(inv -> CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .when(kafka).send(anyString(), anyString(), anyString());
        tx.executeWithoutResult(s -> relay.relay());
        verify(kafka, never()).send(anyString(), eq("b"), anyString());
        assertThat(rows()).allSatisfy(r -> assertThat(r.getPublishedAt()).isNull());

        succeedSends();
        tx.executeWithoutResult(s -> relay.relay());
        assertThat(sentKeys).containsExactly("a", "b");
    }

    @Test
    void cleanupRemovesOnlyOldPublishedEvents() {
        publish("old");
        publish("pending");
        tx.executeWithoutResult(s -> relay.relay());
        publish("fresh-pending");
        // Lùi thời điểm đẩy của "old" về 2 ngày trước.
        tx.executeWithoutResult(s -> repository.findAll().stream()
                .filter(r -> r.getKey().equals("old")).findFirst().ifPresent(r -> setPublishedAt(r,
                        Instant.now().minus(2, ChronoUnit.DAYS))));
        tx.executeWithoutResult(s -> relay.cleanup());
        assertThat(rows()).extracting(OutboxEvent::getKey).containsExactly("pending", "fresh-pending");
    }

    private static void sleepPastClockTick() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void setPublishedAt(OutboxEvent e, Instant at) {
        try {
            var f = OutboxEvent.class.getDeclaredField("publishedAt");
            f.setAccessible(true);
            f.set(e, at);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
