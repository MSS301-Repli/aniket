package com.animallocket.user.service;

import com.animallocket.common.web.ApiException;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Luật đặt alias: độ dài, ký tự cho phép, từ cấm, và bộ sinh alias gợi ý. */
public final class AliasPolicy {
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N}_.\\- ]{3,20}$");
    /** Danh sách tối thiểu; production nên nạp từ cấu hình/dịch vụ kiểm duyệt. */
    private static final List<String> BANNED = List.of("admin", "moderator", "support", "animallocket", "staff");
    private static final List<String> ADJECTIVES = List.of("Vui", "Lém", "Hiền", "Nhanh", "Ngố", "Xinh",
            "Bự", "Tí hon", "Mơ màng", "Lười", "Cool", "Dễ thương");
    private static final SecureRandom RANDOM = new SecureRandom();

    private AliasPolicy() {
    }

    public static String validate(String raw) {
        String alias = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (!ALLOWED.matcher(alias).matches()) {
            throw ApiException.badRequest("INVALID_ALIAS", "Alias dài 3–20 ký tự, chỉ gồm chữ, số, khoảng trắng, _ . -");
        }
        String folded = fold(alias);
        for (String b : BANNED) {
            if (folded.contains(b)) {
                throw ApiException.badRequest("INVALID_ALIAS", "Alias không được phép");
            }
        }
        return alias;
    }

    public static String generate(String animalName) {
        String adj = ADJECTIVES.get(RANDOM.nextInt(ADJECTIVES.size()));
        String base = animalName + " " + adj;
        String alias = base + " " + (100 + RANDOM.nextInt(900));
        return alias.length() <= 20 ? alias : alias.substring(0, 20).strip();
    }

    static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
