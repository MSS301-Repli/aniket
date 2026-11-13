package com.animallocket.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chọn khoá nội bộ từ {@code animal.internal-token}.
 * <ul>
 *   <li>Profile {@code prod}: bắt buộc có khoá ≥ 32 ký tự và khác khoá dev, nếu không service KHÔNG khởi động.</li>
 *   <li>Profile khác (dev, test): thiếu khoá thì dùng {@value #DEV_TOKEN} và cảnh báo.</li>
 * </ul>
 */
public final class InternalTokens {
    private static final Logger log = LoggerFactory.getLogger(InternalTokens.class);

    public static final String PROPERTY = "animal.internal-token";
    public static final String DEV_TOKEN = "dev-internal-token";
    public static final int MIN_PROD_LENGTH = 32;

    private InternalTokens() {
    }

    public static String resolve(String configured, boolean prod) {
        boolean blank = configured == null || configured.isBlank();
        if (prod) {
            if (blank) {
                throw new IllegalStateException(PROPERTY + " (INTERNAL_TOKEN) bắt buộc ở profile prod");
            }
            if (configured.equals(DEV_TOKEN) || configured.length() < MIN_PROD_LENGTH) {
                throw new IllegalStateException(PROPERTY + " ở profile prod phải là chuỗi ngẫu nhiên ≥ "
                        + MIN_PROD_LENGTH + " ký tự, không dùng khoá dev");
            }
            return configured;
        }
        if (blank) {
            log.warn("{} chưa đặt: dùng khoá dev '{}'. KHÔNG dùng cấu hình này ngoài máy dev.", PROPERTY, DEV_TOKEN);
            return DEV_TOKEN;
        }
        return configured;
    }
}
