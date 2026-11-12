package com.animallocket.user.dto;

import com.animallocket.user.domain.Animal;
import com.animallocket.user.domain.Report;
import com.animallocket.user.domain.UserAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Mọi DTO trả cho người khác chỉ chứa publicId/alias/con vật. Không bao giờ có sub, email, ngày sinh. */
public final class Dtos {
    private Dtos() {
    }

    public record RegisterRequest(@Size(min = 3, max = 20) String alias, @NotNull @Past LocalDate birthDate) {
    }

    public record UpdateProfileRequest(@Size(min = 3, max = 20) String alias, Boolean discoverable) {
    }

    public record LeadAnimalRequest(@NotBlank String animalCode) {
    }

    /** {@code avatar} = "v:animal:palette:accessory", duy nhất toàn hệ thống (docs/19-avatar-mechanism.md). */
    public record MeProfile(String publicId, String alias, String leadAnimal, String avatar, String ageGroup,
                            boolean discoverable, String plan, Instant createdAt) {
    }

    public record PublicProfile(String publicId, String alias, String leadAnimal, String avatar) {
        public static PublicProfile of(UserAccount u) {
            return new PublicProfile(u.getPublicId(), u.getAlias(), u.getLeadAnimal(), u.avatarCode());
        }
    }

    public record AvatarView(String avatar, String source, Instant acquiredAt) {
    }

    public record AnimalView(String code, String name, Animal.Rarity rarity, String packCode) {
        public static AnimalView of(Animal a) {
            return new AnimalView(a.getCode(), a.getName(), a.getRarity(), a.getPackCode());
        }
    }

    public record SpinResult(AnimalView animal, Instant nextSpinAt) {
    }

    public record InviteRequest(@NotBlank String toPublicId) {
    }

    public record FriendRequestView(Long id, PublicProfile user, Instant createdAt) {
    }

    public record ConnectResult(String result) {
    }

    public record ReportRequest(@NotNull Report.TargetType targetType, @NotBlank String targetPublicId,
                                @Size(max = 64) String targetRef, @NotBlank @Size(max = 30) String reason,
                                @Size(max = 500) String details) {
    }

    public record DeviceRequest(@NotBlank @Size(max = 255) String token, @NotBlank @Size(max = 10) String platform) {
    }

    // ---- nội bộ ----
    public record IdentityResponse(String publicId) {
    }

    public record InternalUser(String publicId, String alias, String leadAnimal, String avatar, String ageGroup,
                               boolean discoverable, boolean active, boolean pro) {
    }

    public record SwipeRequest(@NotBlank String from, @NotBlank String to) {
    }

    public record Relationship(boolean friends, boolean blocked) {
    }

    public record Exclusions(List<String> friends, List<String> blockedEitherWay) {
    }
}
