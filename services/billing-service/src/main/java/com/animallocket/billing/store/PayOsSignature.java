package com.animallocket.billing.store;

import com.fasterxml.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * Chữ ký payOS: HMAC-SHA256 (hex thường) bằng checksum key. Đã đối chiếu với bộ dữ liệu mẫu chính thức của payOS
 * (xem PayOsSignatureTest). Nguồn: https://payos.vn/docs/api/ và https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/
 */
public final class PayOsSignature {
    private PayOsSignature() {
    }

    /** Yêu cầu tạo link: {@code amount=..&cancelUrl=..&description=..&orderCode=..&returnUrl=..} (đã sắp theo alphabet, không mã hoá URL). */
    public static String forPaymentRequest(String checksumKey, long amount, String cancelUrl, String description,
                                           long orderCode, String returnUrl) {
        return hmac(checksumKey, "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description
                + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl);
    }

    /**
     * Webhook: ký trên đối tượng {@code data} (không gồm code/desc/success): các cặp {@code key=value} sắp theo key alphabet,
     * nối bằng {@code &}; null → chuỗi rỗng; mảng/đối tượng → JSON; còn lại dạng chuỗi thường.
     */
    public static String forData(String checksumKey, JsonNode data) {
        TreeSet<String> keys = new TreeSet<>();
        for (Iterator<String> it = data.fieldNames(); it.hasNext(); ) {
            keys.add(it.next());
        }
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            JsonNode v = data.get(k);
            sb.append(k).append('=').append(v == null || v.isNull() ? "" : v.isContainerNode() ? v.toString() : v.asText());
        }
        return hmac(checksumKey, sb.toString());
    }

    /** So sánh thời gian không đổi để không lộ chữ ký đúng qua độ trễ. */
    public static boolean matches(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8),
                actualHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    static String hmac(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
