package com.animallocket.billing.store;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bọc mọi lời gọi tới store bằng Retry (backoff luỹ thừa) bên trong CircuitBreaker.
 * Chỉ lỗi {@link StoreException.Kind#UNAVAILABLE} mới được retry và tính vào ngắt mạch; lỗi INVALID
 * (biên lai sai) trả thẳng cho người gọi. Mạch mở → UNAVAILABLE ngay, không gọi store.
 */
public class StoreCaller {
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration firstWait;
    private final int windowSize;
    private final int minCalls;
    private final Duration openFor;

    public StoreCaller() {
        this(3, Duration.ofMillis(500), 20, 10, Duration.ofSeconds(30));
    }

    public StoreCaller(int maxAttempts, Duration firstWait, int windowSize, int minCalls, Duration openFor) {
        this.maxAttempts = maxAttempts;
        this.firstWait = firstWait;
        this.windowSize = windowSize;
        this.minCalls = minCalls;
        this.openFor = openFor;
    }

    public <T> T call(String name, CheckedSupplier<T> supplier) throws StoreException {
        Retry retry = retries.computeIfAbsent(name, n -> Retry.of(n, RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(firstWait, 2.0))
                .retryOnException(StoreCaller::isUnavailable)
                .build()));
        CircuitBreaker breaker = breakers.computeIfAbsent(name, n -> CircuitBreaker.of(n, CircuitBreakerConfig.custom()
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minCalls)
                .failureRateThreshold(50)
                .waitDurationInOpenState(openFor)
                .recordException(StoreCaller::isUnavailable)
                .build()));
        CheckedSupplier<T> decorated = CircuitBreaker.decorateCheckedSupplier(breaker,
                Retry.decorateCheckedSupplier(retry, supplier));
        try {
            return decorated.get();
        } catch (StoreException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Cửa hàng đang quá tải, đã ngắt mạch tạm thời", e);
        } catch (Throwable t) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Lỗi khi gọi cửa hàng", t);
        }
    }

    public CircuitBreaker.State state(String name) {
        CircuitBreaker b = breakers.get(name);
        return b == null ? CircuitBreaker.State.CLOSED : b.getState();
    }

    private static boolean isUnavailable(Throwable t) {
        return t instanceof StoreException se && se.kind() == StoreException.Kind.UNAVAILABLE;
    }
}
