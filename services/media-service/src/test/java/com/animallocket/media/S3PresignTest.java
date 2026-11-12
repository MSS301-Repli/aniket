package com.animallocket.media;

import com.animallocket.media.storage.ObjectStorage;
import com.animallocket.media.storage.S3ObjectStorage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ký URL là phép tính cục bộ (có sẵn region) nên không cần kết nối tới R2. */
class S3PresignTest {
    private final S3ObjectStorage storage = new S3ObjectStorage(
            "https://acc123.r2.cloudflarestorage.com", "auto", "ak", "sk", "animal-photos");

    @Test
    void uploadIsPutWithContentTypeHeader() {
        ObjectStorage.PresignedPut put = storage.presignUpload("abc.jpg", "image/jpeg", Duration.ofMinutes(5));
        assertTrue(put.url().startsWith("https://acc123.r2.cloudflarestorage.com/animal-photos/abc.jpg?"), put.url());
        assertTrue(put.url().contains("X-Amz-Signature="));
        assertTrue(put.url().contains("X-Amz-Expires=300"));
        assertEquals("image/jpeg", put.headers().get("Content-Type"));
    }

    @Test
    void downloadIsSignedAndShortLived() {
        String url = storage.presignDownload("abc.jpg", Duration.ofMinutes(10));
        assertTrue(url.startsWith("https://acc123.r2.cloudflarestorage.com/animal-photos/abc.jpg?"), url);
        assertTrue(url.contains("X-Amz-Expires=600"));
    }
}
