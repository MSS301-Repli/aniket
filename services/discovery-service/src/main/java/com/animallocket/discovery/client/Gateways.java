package com.animallocket.discovery.client;

import java.util.List;
import java.util.Map;

/** Các cổng sang service khác. Tách interface để test không cần mạng. */
public final class Gateways {
    private Gateways() {
    }

    public record Profile(String publicId, String alias, String leadAnimal, String avatar, String ageGroup,
                          boolean discoverable, boolean active, boolean pro) {
    }

    public record Exclusions(List<String> friends, List<String> blockedEitherWay) {
    }

    public interface UserGateway {
        /** Trả null nếu không tồn tại. */
        Profile profile(String publicId);

        Map<String, Profile> profiles(List<String> publicIds);

        Exclusions exclusions(String publicId);

        /** Kết quả: MATCHED, REQUESTED, ALREADY_FRIENDS, ALREADY_REQUESTED, BLOCKED. */
        String swipeRight(String from, String to);
    }

    public interface BillingGateway {
        /** Trừ một Boost khỏi ví. false nếu không đủ số dư. */
        boolean consumeBoost(String publicId);
    }
}
