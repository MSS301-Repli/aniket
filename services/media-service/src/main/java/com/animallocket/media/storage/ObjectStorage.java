package com.animallocket.media.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Cổng sang kho đối tượng tương thích S3 (Cloudflare R2). Tách interface để test không cần kho thật. */
public interface ObjectStorage {

    /** Client gửi {@code PUT} thân là file tới {@code url} kèm đúng các {@code headers}. */
    record PresignedPut(String url, Map<String, String> headers) {
    }

    record ObjectInfo(String key, long size, Instant lastModified, String contentType) {
    }

    /** URL upload ngắn hạn. R2 không hỗ trợ presigned POST nên dùng PUT (không ràng buộc kích thước trong chữ ký). */
    PresignedPut presignUpload(String key, String contentType, Duration ttl);

    String presignDownload(String key, Duration ttl);

    Optional<ObjectInfo> stat(String key);

    void delete(String key);
}
