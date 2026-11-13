package com.animallocket.billing.store;

import com.animallocket.billing.domain.Store;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * CHỈ dùng khi phát triển hoặc test: tin tưởng biên lai do client tự khai.
 * Token: {@code dev:<transactionId>[:<originalTransactionId>[:<expiresEpochSeconds>]]}.
 * Mặc định TẮT; bật bằng animal.billing.dev-store.enabled=true. Tuyệt đối không bật ở production.
 */
@Component
@ConditionalOnProperty(name = "animal.billing.dev-store.enabled", havingValue = "true")
public class DevStoreGateway implements StoreGateway {
    @Override
    public Store store() {
        return Store.DEV;
    }

    @Override
    public VerifiedPurchase verify(String sku, String token) throws StoreException {
        String[] p = token == null ? new String[0] : token.split(":");
        if (p.length < 2 || !p[0].equals("dev") || p[1].isBlank()) {
            throw new StoreException(StoreException.Kind.INVALID, "Token dev không hợp lệ");
        }
        String tx = p[1];
        String orig = p.length > 2 && !p[2].isBlank() ? p[2] : tx;
        Instant expires = null;
        if (p.length > 3) {
            try {
                expires = Instant.ofEpochSecond(Long.parseLong(p[3]));
            } catch (NumberFormatException e) {
                throw new StoreException(StoreException.Kind.INVALID, "Hạn không hợp lệ");
            }
        }
        return new VerifiedPurchase(tx, orig, sku, Instant.now(), expires, false);
    }
}
