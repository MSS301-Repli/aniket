package com.animallocket.billing.service;

import com.animallocket.billing.domain.Product;
import com.animallocket.billing.repo.ProductRepository;
import com.animallocket.billing.store.StoreException;
import com.animallocket.billing.store.StoreRouter;
import com.animallocket.billing.store.VerifiedPurchase;
import com.animallocket.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Luồng mua: hỏi store TRƯỚC (không giữ transaction/kết nối DB trong lúc chờ mạng), rồi mới cấp quyền
 * trong một transaction ngắn ở {@link BillingService#grant}. Bean này cố ý KHÔNG @Transactional.
 */
@Service
public class PurchaseFlow {
    private static final Logger log = LoggerFactory.getLogger(PurchaseFlow.class);

    private final ProductRepository products;
    private final StoreRouter stores;
    private final BillingService billing;

    public PurchaseFlow(ProductRepository products, StoreRouter stores, BillingService billing) {
        this.products = products;
        this.stores = stores;
        this.billing = billing;
    }

    public BillingDtos.MeResponse verifyAndGrant(String me, BillingDtos.VerifyRequest req) {
        Product product = products.findById(req.sku()).filter(Product::isActive)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PRODUCT", "Sản phẩm không tồn tại"));
        VerifiedPurchase vp;
        try {
            vp = stores.verify(req.store(), req.sku(), req.token());
        } catch (StoreException e) {
            if (e.kind() == StoreException.Kind.INVALID) {
                log.warn("Biên lai không hợp lệ ({}): {}", req.store(), e.getMessage());
                throw ApiException.badRequest("INVALID_RECEIPT", "Biên lai không hợp lệ");
            }
            log.error("Không xác thực được với {}: {}", req.store(), e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORE_UNAVAILABLE",
                    "Chưa xác nhận được giao dịch với cửa hàng, vui lòng thử lại (bạn sẽ không bị mất quyền lợi)");
        }
        if (!vp.sku().equals(req.sku())) {
            throw ApiException.badRequest("SKU_MISMATCH", "Biên lai không thuộc sản phẩm này");
        }
        if (vp.revoked()) {
            throw ApiException.badRequest("RECEIPT_REVOKED", "Giao dịch đã bị hoàn tiền hoặc thu hồi");
        }
        return billing.grant(me, req.store(), product, vp);
    }
}
