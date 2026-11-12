package com.animallocket.billing.service;

import com.animallocket.billing.domain.Product;
import com.animallocket.billing.domain.Store;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class BillingDtos {
    private BillingDtos() {
    }

    /** token = transactionId (Apple) hoặc purchaseToken (Google). Server luôn tự hỏi lại store, không tin client. */
    public record VerifyRequest(@NotNull Store store, @NotBlank @Size(max = 100) String sku,
                                @NotBlank @Size(max = 4096) String token) {
    }

    public record MeResponse(String plan, Instant planExpiresAt, Map<String, Integer> wallet) {
    }

    /**
     * Giá bán qua web (thanh toán trên trình duyệt): {@code priceUsdCents} cho PayPal, {@code priceVnd} cho VietQR, null = không bán qua
     * phương thức đó. {@code periodDays} chỉ có với thuê bao. Chỉ để hiển thị; số tiền thật do server chốt lúc tạo phiên thanh toán.
     */
    public record ProductView(String sku, Product.Type type, String item, int quantity, Integer priceUsdCents,
                              Integer priceVnd, Integer periodDays) {
    }

    /** App xin một liên kết thanh toán web cho sản phẩm {@code sku}. */
    public record CheckoutRequest(@NotBlank @Size(max = 100) String sku) {
    }

    /** {@code url} mở bằng trình duyệt; mã trên URL dùng một lần và hết hạn lúc {@code expiresAt}. */
    public record CheckoutResponse(String url, Instant expiresAt) {
    }

    public record ConsumeRequest(@NotBlank String publicId, @NotBlank String item) {
    }
}
