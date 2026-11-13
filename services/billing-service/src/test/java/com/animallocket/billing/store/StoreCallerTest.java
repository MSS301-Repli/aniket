package com.animallocket.billing.store;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoreCallerTest {

    @Test
    void retriesOnlyUnavailableErrors() {
        StoreCaller caller = new StoreCaller(3, Duration.ofMillis(1), 20, 10, Duration.ofSeconds(30));
        AtomicInteger unavailable = new AtomicInteger();
        StoreException e = assertThrows(StoreException.class, () -> caller.call("a", () -> {
            unavailable.incrementAndGet();
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "down");
        }));
        assertEquals(StoreException.Kind.UNAVAILABLE, e.kind());
        assertEquals(3, unavailable.get());

        AtomicInteger invalid = new AtomicInteger();
        assertThrows(StoreException.class, () -> caller.call("b", () -> {
            invalid.incrementAndGet();
            throw new StoreException(StoreException.Kind.INVALID, "bad receipt");
        }));
        assertEquals(1, invalid.get(), "biên lai sai không được retry");
    }

    @Test
    void recoversWhenALaterAttemptSucceeds() throws Exception {
        StoreCaller caller = new StoreCaller(3, Duration.ofMillis(1), 20, 10, Duration.ofSeconds(30));
        AtomicInteger n = new AtomicInteger();
        String r = caller.call("c", () -> {
            if (n.incrementAndGet() < 3) {
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "blip");
            }
            return "ok";
        });
        assertEquals("ok", r);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingTheStore() {
        StoreCaller caller = new StoreCaller(1, Duration.ofMillis(1), 4, 4, Duration.ofMinutes(5));
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            assertThrows(StoreException.class, () -> caller.call("d", () -> {
                calls.incrementAndGet();
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "down");
            }));
        }
        assertEquals(CircuitBreaker.State.OPEN, caller.state("d"));
        StoreException e = assertThrows(StoreException.class, () -> caller.call("d", () -> {
            calls.incrementAndGet();
            return "never";
        }));
        assertEquals(StoreException.Kind.UNAVAILABLE, e.kind());
        assertEquals(4, calls.get(), "mạch mở thì không gọi store nữa");
    }

    @Test
    void invalidReceiptsNeverTripTheCircuit() {
        StoreCaller caller = new StoreCaller(1, Duration.ofMillis(1), 4, 4, Duration.ofMinutes(5));
        for (int i = 0; i < 20; i++) {
            assertThrows(StoreException.class, () -> caller.call("e", () -> {
                throw new StoreException(StoreException.Kind.INVALID, "bad");
            }));
        }
        assertEquals(CircuitBreaker.State.CLOSED, caller.state("e"));
    }

    @Test
    void unexpectedRuntimeErrorsBecomeUnavailable() {
        StoreCaller caller = new StoreCaller(1, Duration.ofMillis(1), 20, 10, Duration.ofSeconds(30));
        StoreException e = assertThrows(StoreException.class, () -> caller.call("f", () -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(StoreException.Kind.UNAVAILABLE, e.kind());
    }
}
