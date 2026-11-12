package com.animallocket.media.service;

import com.animallocket.common.EventTypes;
import com.animallocket.common.Topics;
import com.animallocket.common.event.Events;
import com.animallocket.common.web.ApiException;
import com.animallocket.media.client.UserClient;
import com.animallocket.media.domain.MediaJob;
import com.animallocket.media.domain.OwnerAnimal;
import com.animallocket.media.dto.MediaDtos;
import com.animallocket.media.repo.MediaJobRepository;
import com.animallocket.media.repo.OwnerAnimalRepository;
import com.animallocket.media.storage.ObjectStorage;
import com.animallocket.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/heic", ".heic");
    static final Duration UPLOAD_TTL = Duration.ofMinutes(5);
    static final Duration VIEW_TTL = Duration.ofMinutes(10);

    private final MediaJobRepository jobs;
    private final OwnerAnimalRepository ownerAnimals;
    private final ObjectStorage storage;
    private final UserClient users;
    private final OutboxService outbox;
    private final long maxBytes;
    private final int maxUploadsPerHour;

    public MediaService(MediaJobRepository jobs, OwnerAnimalRepository ownerAnimals, ObjectStorage storage,
                        UserClient users, OutboxService outbox,
                        @Value("${animal.media.max-bytes:10485760}") long maxBytes,
                        @Value("${animal.media.max-uploads-per-hour:60}") int maxUploadsPerHour) {
        this.jobs = jobs;
        this.ownerAnimals = ownerAnimals;
        this.storage = storage;
        this.users = users;
        this.outbox = outbox;
        this.maxBytes = maxBytes;
        this.maxUploadsPerHour = maxUploadsPerHour;
    }

    @Transactional
    public MediaDtos.UploadTicket requestUpload(String owner, MediaDtos.UploadRequest req) {
        String ext = ALLOWED.get(req.contentType());
        if (ext == null) {
            throw ApiException.badRequest("UNSUPPORTED_MEDIA_TYPE", "Chỉ hỗ trợ JPEG, PNG, WEBP, HEIC");
        }
        if (req.sizeBytes() > maxBytes) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Ảnh tối đa " + (maxBytes / 1024 / 1024) + " MB");
        }
        long recent = jobs.countByOwnerPublicIdAndCreatedAtGreaterThanEqual(owner, Instant.now().minus(Duration.ofHours(1)));
        if (recent >= maxUploadsPerHour) {
            throw ApiException.tooMany("UPLOAD_LIMIT", "Bạn tải ảnh quá nhanh, vui lòng thử lại sau");
        }
        MediaJob job = new MediaJob();
        job.setId(UUID.randomUUID().toString());
        job.setOwnerPublicId(owner);
        job.setContext(req.context());
        job.setRefId(req.refId());
        job.setRawKey(job.getId() + ext);
        jobs.save(job);
        ObjectStorage.PresignedPut put = storage.presignUpload(job.getRawKey(), req.contentType(), UPLOAD_TTL);
        return new MediaDtos.UploadTicket(job.getId(), put.url(), put.headers(), (int) UPLOAD_TTL.toSeconds());
    }

    /** Client báo đã upload xong. Kiểm tra object có thật, đúng loại và cỡ rồi đánh dấu READY và phát PHOTO_READY. Idempotent. */
    @Transactional
    public MediaDtos.MediaStatus complete(String owner, String mediaId) {
        MediaJob job = ownedJob(owner, mediaId);
        if (job.getStatus() != MediaJob.Status.PENDING_UPLOAD) {
            return MediaDtos.MediaStatus.of(job);
        }
        var info = storage.stat(job.getRawKey())
                .orElseThrow(() -> ApiException.conflict("NOT_UPLOADED", "Chưa thấy ảnh được tải lên"));
        if (info.size() > maxBytes) {
            storage.delete(job.getRawKey());
            reject(job, "FILE_TOO_LARGE");
            return MediaDtos.MediaStatus.of(job);
        }
        // Presigned PUT không ràng buộc Content-Type trong chữ ký nên kiểm tra lại ở đây.
        if (info.contentType() == null || !ALLOWED.containsKey(info.contentType())) {
            storage.delete(job.getRawKey());
            reject(job, "UNSUPPORTED_MEDIA_TYPE");
            return MediaDtos.MediaStatus.of(job);
        }
        String publicKey = job.getRawKey();
        job.setStatus(MediaJob.Status.READY);
        job.setPublicKey(publicKey);
        job.setMethod("NONE");
        job.setFacesDetected(0);
        job.touch();
        outbox.publish(Topics.MEDIA, EventTypes.PHOTO_READY, job.getId(),
                new Events.PhotoReady(job.getId(), owner, publicKey, "NONE", 0,
                        job.getContext().name(), job.getRefId()));
        return MediaDtos.MediaStatus.of(job);
    }

    @Transactional(readOnly = true)
    public MediaDtos.MediaStatus status(String owner, String mediaId) {
        return MediaDtos.MediaStatus.of(ownedJob(owner, mediaId));
    }

    /** URL xem ảnh đã ẩn mặt: chỉ chủ ảnh hoặc bạn bè (không bị chặn). Ngắn hạn. */
    @Transactional(readOnly = true)
    public MediaDtos.ViewUrl viewUrl(String viewer, String mediaId) {
        MediaJob job = jobs.findById(mediaId).orElseThrow(() -> ApiException.notFound("Ảnh"));
        if (job.getStatus() != MediaJob.Status.READY || job.getPublicKey() == null) {
            throw ApiException.notFound("Ảnh");
        }
        if (!job.getOwnerPublicId().equals(viewer)) {
            var rel = users.relationship(viewer, job.getOwnerPublicId());
            if (!rel.friends() || rel.blocked()) {
                throw ApiException.notFound("Ảnh"); // không tiết lộ ảnh tồn tại
            }
        }
        return new MediaDtos.ViewUrl(storage.presignDownload(job.getPublicKey(), VIEW_TTL), (int) VIEW_TTL.toSeconds());
    }

    @Transactional(readOnly = true)
    public MediaDtos.InternalMedia internal(String mediaId) {
        MediaJob j = jobs.findById(mediaId).orElseThrow(() -> ApiException.notFound("Ảnh"));
        return new MediaDtos.InternalMedia(j.getId(), j.getOwnerPublicId(), j.getContext().name(),
                j.getStatus().name(), j.getPublicKey());
    }

    // ---------- event người dùng ----------

    @Transactional
    public void setOwnerAnimal(String publicId, String animal) {
        ownerAnimals.save(new OwnerAnimal(publicId, animal));
    }

    @Transactional
    public void onUserDeleted(String publicId) {
        for (MediaJob j : jobs.findByOwnerPublicId(publicId)) {
            storage.delete(j.getRawKey());
            jobs.delete(j);
        }
        ownerAnimals.deleteById(publicId);
    }

    // ---------- dọn dẹp định kỳ ----------

    /** Xoá job mà client xin upload nhưng không bao giờ báo hoàn tất (quá 1 giờ). */
    @Transactional
    public int expireAbandoned() {
        int n = 0;
        for (MediaJob j : jobs.findByStatusInAndUpdatedAtBefore(EnumSet.of(MediaJob.Status.PENDING_UPLOAD),
                Instant.now().minus(Duration.ofHours(1)))) {
            storage.delete(j.getRawKey());
            jobs.delete(j);
            n++;
        }
        return n;
    }

    private void reject(MediaJob job, String reason) {
        job.setStatus(MediaJob.Status.REJECTED);
        job.setRejectReason(reason);
        job.touch();
        outbox.publish(Topics.MEDIA, EventTypes.PHOTO_REJECTED, job.getId(),
                new Events.PhotoRejected(job.getId(), job.getOwnerPublicId(), reason,
                        job.getContext().name(), job.getRefId()));
    }

    private MediaJob ownedJob(String owner, String mediaId) {
        MediaJob job = jobs.findById(mediaId).orElseThrow(() -> ApiException.notFound("Ảnh"));
        if (!job.getOwnerPublicId().equals(owner)) {
            throw ApiException.notFound("Ảnh");
        }
        return job;
    }
}
