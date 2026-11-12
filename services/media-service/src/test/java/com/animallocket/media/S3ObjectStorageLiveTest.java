package com.animallocket.media;

import com.animallocket.media.storage.ObjectStorage;
import com.animallocket.media.storage.S3ObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chạy với R2 thật; tự bỏ qua khi không đặt {@code R2_ENDPOINT} (mọi môi trường CI/dev thông thường).
 * Cần R2_ENDPOINT, R2_ACCESS_KEY, R2_SECRET_KEY, R2_BUCKET. Ghi một object tạm rồi xoá.
 */
@EnabledIfEnvironmentVariable(named = "R2_ENDPOINT", matches = ".+")
class S3ObjectStorageLiveTest {
    private final S3ObjectStorage storage = new S3ObjectStorage(
            System.getenv("R2_ENDPOINT"), "auto", System.getenv("R2_ACCESS_KEY"),
            System.getenv("R2_SECRET_KEY"), System.getenv("R2_BUCKET"));
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void putStatGetDeleteRoundTrip() throws Exception {
        String key = "live-test-" + UUID.randomUUID() + ".png";
        byte[] body = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4, 5};
        try {
            ObjectStorage.PresignedPut put = storage.presignUpload(key, "image/png", Duration.ofMinutes(2));
            var putReq = HttpRequest.newBuilder(URI.create(put.url()))
                    .header("Content-Type", put.headers().get("Content-Type"))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var putResp = http.send(putReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, putResp.statusCode(), putResp.body());

            var info = storage.stat(key).orElseThrow();
            assertEquals(body.length, info.size());
            assertEquals("image/png", info.contentType());

            var get = http.send(HttpRequest.newBuilder(URI.create(storage.presignDownload(key, Duration.ofMinutes(2)))).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, get.statusCode());
            assertArrayEquals(body, get.body());
        } finally {
            storage.delete(key);
        }
        assertTrue(storage.stat(key).isEmpty(), "object phải bị xoá");
    }
}
