package com.animallocket.common;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * publicId: chuỗi ngẫu nhiên 20 ký tự [a-z2-7] (32^20 ≈ 2^100), không suy ra được từ sub Keycloak
 * hay bất kỳ thông tin nào khác của người dùng.
 */
public final class PublicIds {
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz234567".toCharArray();
    public static final int LENGTH = 20;
    private static final Pattern VALID = Pattern.compile("^[a-z2-7]{" + LENGTH + "}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicIds() {
    }

    public static String generate() {
        char[] out = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }
}
