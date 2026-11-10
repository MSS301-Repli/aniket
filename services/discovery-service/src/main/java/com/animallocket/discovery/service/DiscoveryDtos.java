package com.animallocket.discovery.service;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public final class DiscoveryDtos {
    private DiscoveryDtos() {
    }

    public record LocationRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                                  @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon) {
    }

    public record PresenceResponse(Map<String, Boolean> online) {
    }
}
