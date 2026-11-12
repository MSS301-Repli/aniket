package com.animallocket.media.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Một bucket riêng tư trên Cloudflare R2 (endpoint {@code https://<account-id>.r2.cloudflarestorage.com}, region
 * {@code auto}). Chỉ dùng client S3 (minio-java); không tạo bucket (token R2 chỉ có quyền trên object).
 * Ảnh không công khai: xem qua presigned GET ngắn hạn.
 */
@Component
public class S3ObjectStorage implements ObjectStorage {
    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final MinioClient client;
    private final String bucket;

    public S3ObjectStorage(@Value("${storage.endpoint}") String endpoint,
                           @Value("${storage.region:auto}") String region,
                           @Value("${storage.access-key}") String accessKey,
                           @Value("${storage.secret-key}") String secretKey,
                           @Value("${storage.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).region(region).build();
        this.bucket = bucket;
    }

    @Override
    public PresignedPut presignUpload(String key, String contentType, Duration ttl) {
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT)
                    .bucket(bucket).object(key).expiry((int) ttl.toSeconds(), TimeUnit.SECONDS).build());
            return new PresignedPut(url, Map.of("Content-Type", contentType));
        } catch (Exception e) {
            throw new IllegalStateException("Không ký được URL upload", e);
        }
    }

    @Override
    public String presignDownload(String key, Duration ttl) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                    .bucket(bucket).object(key).expiry((int) ttl.toSeconds(), TimeUnit.SECONDS).build());
        } catch (Exception e) {
            throw new IllegalStateException("Không ký được URL tải ảnh", e);
        }
    }

    @Override
    public Optional<ObjectInfo> stat(String key) {
        try {
            var s = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return Optional.of(new ObjectInfo(key, s.size(), s.lastModified().toInstant(), s.contentType()));
        } catch (ErrorResponseException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi đọc kho ảnh", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            log.warn("Không xoá được {}/{}: {}", bucket, key, e.toString());
        }
    }
}
