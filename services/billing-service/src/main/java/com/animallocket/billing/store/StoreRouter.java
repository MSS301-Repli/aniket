package com.animallocket.billing.store;

import com.animallocket.billing.domain.Store;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Chọn gateway theo store. Store không có gateway (chưa cấu hình hoặc tắt) thì từ chối, không bao giờ cấp quyền. */
@Component
public class StoreRouter {
    private final Map<Store, StoreGateway> gateways = new EnumMap<>(Store.class);

    public StoreRouter(List<StoreGateway> all) {
        all.forEach(g -> gateways.put(g.store(), g));
    }

    public VerifiedPurchase verify(Store store, String sku, String token) throws StoreException {
        StoreGateway g = gateways.get(store);
        if (g == null) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Store " + store + " chưa được bật");
        }
        return g.verify(sku, token);
    }

    public boolean supports(Store store) {
        return gateways.containsKey(store);
    }
}
