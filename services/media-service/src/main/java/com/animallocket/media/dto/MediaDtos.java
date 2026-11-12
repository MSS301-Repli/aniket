package com.animallocket.media.dto;

import com.animallocket.media.domain.MediaJob;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public final class MediaDtos {
    private MediaDtos() {
    }

    public record UploadRequest(@NotNull MediaJob.Context context, @Size(max = 64) String refId,
                                @NotBlank String contentType, @Min(1) @Max(50_000_000) long sizeBytes) {
    }

    /** Client gửi {@code PUT} thân là file tới {@code uploadUrl} kèm đúng các {@code headers} (Content-Type). */
    public record UploadTicket(String mediaId, String uploadUrl, Map<String, String> headers, int expiresInSeconds) {
    }

    public record MediaStatus(String mediaId, String status, String method, String rejectReason) {
        public static MediaStatus of(MediaJob j) {
            return new MediaStatus(j.getId(), j.getStatus().name(), j.getMethod(), j.getRejectReason());
        }
    }

    public record ViewUrl(String url, int expiresInSeconds) {
    }

    public record InternalMedia(String mediaId, String ownerPublicId, String context, String status,
                                String publicKey) {
    }
}
