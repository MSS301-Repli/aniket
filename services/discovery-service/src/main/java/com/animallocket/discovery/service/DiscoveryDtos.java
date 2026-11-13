package com.animallocket.discovery.service;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class DiscoveryDtos {
    private DiscoveryDtos() {
    }

    public record LocationRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                                  @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon) {
    }

    /**
     * Thẻ gợi ý: chỉ alias, con vật, avatar, mức khoảng cách. Không toạ độ, không ảnh thật, không thông tin định danh.
     * {@code avatar} = "v:animal:palette:accessory" (docs/19-avatar-mechanism.md), client tự vẽ.
     */
    public record Card(String publicId, String alias, String leadAnimal, String avatar, String distanceBand,
                       boolean online, boolean boosted) {
    }

    public record CardsResponse(List<Card> cards, boolean degraded, int swipesLeftToday) {
    }

    public enum Direction { LEFT, RIGHT }

    public record SwipeRequest(@NotBlank @Size(max = 64) String targetPublicId, @NotNull Direction direction) {
    }

    public record SwipeResponse(String result, int swipesLeftToday) {
    }

    public record PresenceResponse(Map<String, Boolean> online) {
    }
}
