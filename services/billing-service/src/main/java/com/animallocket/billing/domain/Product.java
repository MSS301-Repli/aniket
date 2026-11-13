package com.animallocket.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Danh mục sản phẩm. {@code sku} trùng với product id đăng ký trên App Store / Google Play. */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor
public class Product {
    public enum Type { SUBSCRIPTION, CONSUMABLE, PACK }

    @Id
    @Column(length = 60)
    private String sku;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private Type type;
    /** SUBSCRIPTION: "PRO". CONSUMABLE: "BOOST" | "PRIORITY_INVITE". PACK: mã gói con vật ("SAFARI"). */
    @Column(nullable = false, length = 40)
    private String item;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private boolean active;
    /** Giá bán qua web (cent USD). null = không bán qua web. Thanh toán trong app dùng giá của store, không dùng cột này. */
    @Column(name = "price_usd_cents")
    private Integer priceUsdCents;
    /** Thời hạn (ngày) của thuê bao trả trước qua web; chỉ dùng với SUBSCRIPTION. */
    @Column(name = "period_days")
    private Integer periodDays;

    /** Giá bán qua payOS (VND, số nguyên). null = không bán qua payOS. */
    @Column(name = "price_vnd")
    private Integer priceVnd;

    public Product(String sku, Type type, String item, int quantity, boolean active, Integer priceUsdCents,
                   Integer periodDays, Integer priceVnd) {
        this(sku, type, item, quantity, active, priceUsdCents, periodDays);
        this.priceVnd = priceVnd;
    }

    public Product(String sku, Type type, String item, int quantity, boolean active) {
        this.sku = sku;
        this.type = type;
        this.item = item;
        this.quantity = quantity;
        this.active = active;
    }

    public Product(String sku, Type type, String item, int quantity, boolean active, Integer priceUsdCents, Integer periodDays) {
        this(sku, type, item, quantity, active);
        this.priceUsdCents = priceUsdCents;
        this.periodDays = periodDays;
    }
}
