package com.animallocket.notification;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.LongConsumer;

/**
 * Gửi một thông báo tới mọi thiết bị của người nhận. Push là fail-soft: lỗi tạm thời được retry
 * có giới hạn, token chết bị gỡ, hết lượt thì bỏ qua và không bao giờ chặn nghiệp vụ.
 */
@Component
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final DeviceDirectory directory;
    private final PushSender sender;
    private final MeterRegistry metrics;
    private final int maxAttempts;
    private final long backoffMs;
    private LongConsumer sleeper = NotificationDispatcher::sleepQuietly;

    public NotificationDispatcher(DeviceDirectory directory, PushSender sender, MeterRegistry metrics,
                                  @Value("${animal.push.max-attempts:3}") int maxAttempts,
                                  @Value("${animal.push.backoff-ms:500}") long backoffMs) {
        this.directory = directory;
        this.sender = sender;
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    void setSleeper(LongConsumer sleeper) {
        this.sleeper = sleeper;
    }

    public void dispatch(Notification n) {
        List<String> tokens;
        try {
            tokens = directory.tokens(n.recipientPublicId());
        } catch (Exception e) {
            log.warn("Không lấy được thiết bị của {}: {}", n.recipientPublicId(), e.toString());
            metrics.counter("push.skipped", "reason", "directory_error").increment();
            return;
        }
        for (String token : tokens) {
            sendWithRetry(token, n);
        }
    }

    private void sendWithRetry(String token, Notification n) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            PushSender.Result r;
            try {
                r = sender.send(token, n.title(), n.body(), n.data());
            } catch (Exception e) {
                log.warn("Gửi push lỗi: {}", e.toString());
                r = PushSender.Result.TRANSIENT_ERROR;
            }
            switch (r) {
                case OK -> {
                    metrics.counter("push.sent", "type", n.type()).increment();
                    return;
                }
                case INVALID_TOKEN -> {
                    metrics.counter("push.invalid_token").increment();
                    try {
                        directory.removeToken(token);
                    } catch (Exception e) {
                        log.warn("Không gỡ được token chết: {}", e.toString());
                    }
                    return;
                }
                case TRANSIENT_ERROR -> {
                    if (attempt < maxAttempts) {
                        sleeper.accept(backoffMs * attempt);
                    }
                }
            }
        }
        metrics.counter("push.dropped", "type", n.type()).increment();
        log.warn("Bỏ qua push {} sau {} lần thử", n.type(), maxAttempts);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
