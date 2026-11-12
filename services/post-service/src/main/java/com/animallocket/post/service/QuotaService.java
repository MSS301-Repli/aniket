package com.animallocket.post.service;

import com.animallocket.common.kv.KeyValueStore;
import com.animallocket.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Hạn mức ảnh/ngày theo gói, đếm bằng Redis theo ngày giờ Việt Nam. */
@Service
public class QuotaService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final KeyValueStore kv;
    private final int freeLimit;
    private final int proLimit;

    public QuotaService(KeyValueStore kv,
                        @Value("${animal.limits.photos-per-day.free:10}") int freeLimit,
                        @Value("${animal.limits.photos-per-day.pro:50}") int proLimit) {
        this.kv = kv;
        this.freeLimit = freeLimit;
        this.proLimit = proLimit;
    }

    public boolean isPro(String publicId) {
        return kv.get(proKey(publicId)).isPresent();
    }

    public void setPro(String publicId, Instant expiresAt) {
        Duration ttl = expiresAt == null ? Duration.ofDays(400) : Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            kv.delete(proKey(publicId));
        } else {
            kv.set(proKey(publicId), "1", ttl);
        }
    }

    public void clearPro(String publicId) {
        kv.delete(proKey(publicId));
    }

    /** Dùng một lượt; vượt hạn mức thì trả lại lượt và báo 429. */
    public void consume(String publicId) {
        int limit = isPro(publicId) ? proLimit : freeLimit;
        long used = kv.increment(quotaKey(publicId, LocalDate.now(ZONE)), Duration.ofHours(30));
        if (used > limit) {
            kv.decrement(quotaKey(publicId, LocalDate.now(ZONE)));
            throw ApiException.tooMany("PHOTO_LIMIT", "Bạn đã dùng hết " + limit + " ảnh hôm nay");
        }
    }

    /** Hoàn lượt khi ảnh bị từ chối, để lỗi của hệ thống không trừ vào hạn mức người dùng. */
    public void refund(String publicId, Instant createdAt) {
        kv.decrement(quotaKey(publicId, createdAt.atZone(ZONE).toLocalDate()));
    }

    private static String quotaKey(String id, LocalDate day) {
        return "quota:photo:" + id + ":" + DAY.format(day);
    }

    private static String proKey(String id) {
        return "pro:" + id;
    }
}
