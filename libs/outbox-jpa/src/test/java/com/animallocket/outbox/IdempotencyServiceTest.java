package com.animallocket.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = OutboxTestApp.class)
class IdempotencyServiceTest {

    @Autowired
    IdempotencyService idempotency;
    @Autowired
    ProcessedEventRepository repository;
    @Autowired
    TransactionTemplate tx;
    @MockBean
    KafkaTemplate<String, String> kafka;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private Boolean firstTime(String id) {
        return tx.execute(s -> idempotency.firstTime(id));
    }

    @Test
    void onlyTheFirstDeliveryIsProcessed() {
        assertThat(firstTime("e1")).isTrue();
        assertThat(firstTime("e1")).isFalse();
        assertThat(firstTime("e2")).isTrue();
    }

    @Test
    void requiresTheBusinessTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> idempotency.firstTime("e1"));
    }

    @Test
    void failedProcessingRollsBackTheMarkerSoTheEventIsRetried() {
        tx.executeWithoutResult(s -> {
            assertThat(idempotency.firstTime("e1")).isTrue();
            s.setRollbackOnly(); // xử lý nghiệp vụ lỗi
        });
        assertThat(firstTime("e1")).isTrue();
    }

    @Test
    void concurrentDuplicateDeliveriesNeverBothSucceed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inside = new CountDownLatch(2);
        Callable<Boolean> consumer = () -> tx.execute(s -> {
            inside.countDown();
            try {
                inside.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return idempotency.firstTime("dup");
        });
        Future<Boolean> a = pool.submit(consumer);
        Future<Boolean> b = pool.submit(consumer);
        int successes = 0;
        for (Future<Boolean> f : new Future[]{a, b}) {
            try {
                if (Boolean.TRUE.equals(f.get())) {
                    successes++;
                }
            } catch (java.util.concurrent.ExecutionException e) {
                // Bên thua vi phạm khoá chính: Kafka sẽ giao lại và lần sau firstTime trả false.
            }
        }
        pool.shutdown();
        assertThat(successes).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void cleanupRemovesOnlyOldMarkers() throws Exception {
        firstTime("old");
        firstTime("recent");
        tx.executeWithoutResult(s -> {
            ProcessedEvent old = repository.findById("old").orElseThrow();
            setProcessedAt(old, Instant.now().minus(8, ChronoUnit.DAYS));
        });
        idempotency.cleanup();
        assertThat(repository.findAll()).extracting(ProcessedEvent::getEventId).containsExactly("recent");
    }

    private static void setProcessedAt(ProcessedEvent e, Instant at) {
        try {
            Field f = ProcessedEvent.class.getDeclaredField("processedAt");
            f.setAccessible(true);
            f.set(e, at);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
