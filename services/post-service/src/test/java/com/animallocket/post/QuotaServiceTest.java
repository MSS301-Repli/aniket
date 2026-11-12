package com.animallocket.post;

import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.web.ApiException;
import com.animallocket.post.service.QuotaService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaServiceTest {
    final QuotaService quota = new QuotaService(new InMemoryKeyValueStore(), 2, 5);

    @Test
    void freeUsersAreLimitedPerDay() {
        quota.consume("u1");
        quota.consume("u1");
        ApiException e = assertThrows(ApiException.class, () -> quota.consume("u1"));
        assertEquals("PHOTO_LIMIT", e.code());
        assertEquals(429, e.status().value());
        quota.consume("u2"); // người khác không bị ảnh hưởng
    }

    @Test
    void failedAttemptDoesNotConsumeQuota() {
        quota.consume("u1");
        quota.consume("u1");
        assertThrows(ApiException.class, () -> quota.consume("u1"));
        assertThrows(ApiException.class, () -> quota.consume("u1"));
        quota.refund("u1", Instant.now());
        quota.consume("u1"); // sau khi hoàn một lượt thì dùng lại được
    }

    @Test
    void rejectedPhotoIsRefunded() {
        quota.consume("u1");
        quota.consume("u1");
        quota.refund("u1", Instant.now());
        quota.consume("u1");
        assertThrows(ApiException.class, () -> quota.consume("u1"));
    }

    @Test
    void proUsersGetHigherLimit() {
        assertFalse(quota.isPro("u1"));
        quota.setPro("u1", Instant.now().plusSeconds(3600));
        assertTrue(quota.isPro("u1"));
        for (int i = 0; i < 5; i++) {
            quota.consume("u1");
        }
        assertThrows(ApiException.class, () -> quota.consume("u1"));
        quota.clearPro("u1");
        assertFalse(quota.isPro("u1"));
    }

    @Test
    void expiredProIsIgnored() {
        quota.setPro("u1", Instant.now().minusSeconds(5));
        assertFalse(quota.isPro("u1"));
    }
}
