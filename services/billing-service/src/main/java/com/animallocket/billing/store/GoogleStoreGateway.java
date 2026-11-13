package com.animallocket.billing.store;

import com.animallocket.billing.domain.Product;
import com.animallocket.billing.domain.Store;
import com.animallocket.billing.repo.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Xác thực mua hàng Google Play bằng cách hỏi lại Google (client chỉ gửi {@code purchaseToken}).
 *
 * <h3>Ý nghĩa của originalTransactionId</h3>
 * Là SHA-256 (hex, 64 ký tự) của <b>purchaseToken gốc</b> của chuỗi thuê bao. Khi người dùng đăng ký lại hoặc đổi gói,
 * Google cấp token mới có {@code linkedPurchaseToken} trỏ về token cũ; ta lần theo chuỗi liên kết (tối đa 5 bước)
 * tới token không còn liên kết và băm nó. Nhờ vậy cả luồng verify lẫn webhook RTDN đều tính ra CÙNG một khoá cho
 * cùng một thuê bao. Phải băm vì token dài hơn cột {@code original_transaction_id VARCHAR(100)}.
 * {@code transactionId} là {@code latestOrderId} (đổi theo mỗi kỳ gia hạn).
 */
public class GoogleStoreGateway implements StoreGateway, GoogleSubscriptionResolver {
    private static final Logger log = LoggerFactory.getLogger(GoogleStoreGateway.class);
    /** purchaseToken chỉ gồm chữ, số, . - _ ; chặn mọi ký tự khác để không chèn được vào đường dẫn. */
    static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9._\\-]{1,2048}$");
    private static final int MAX_CHAIN_HOPS = 5;

    private final GoogleProperties props;
    private final GooglePlayClient client;
    private final ProductRepository products;
    private final Clock clock;

    public GoogleStoreGateway(GoogleProperties props, GooglePlayClient client, ProductRepository products, Clock clock) {
        this.props = props;
        this.client = client;
        this.products = products;
        this.clock = clock;
    }

    @Override
    public Store store() {
        return Store.GOOGLE;
    }

    @Override
    public VerifiedPurchase verify(String sku, String token) throws StoreException {
        requireToken(token);
        Product product = products.findById(sku)
                .orElseThrow(() -> new StoreException(StoreException.Kind.INVALID, "Sản phẩm không có trong danh mục"));
        return product.getType() == Product.Type.SUBSCRIPTION ? verifySubscription(sku, token) : verifyOneTime(sku, token);
    }

    private VerifiedPurchase verifySubscription(String sku, String token) throws StoreException {
        GooglePlayClient.SubscriptionInfo info = client.getSubscription(token);
        Resolved r = map(info, sku::equals, rootKey(token, info));
        if (props.isServerAcknowledge() && r.entitled() && !r.acknowledged()) {
            acknowledgeQuietly(() -> client.acknowledgeSubscription(sku, token));
        }
        return new VerifiedPurchase(r.latestOrderId(), r.originalTransactionId(), sku, clock.instant(), r.expiresAt(), false);
    }

    private VerifiedPurchase verifyOneTime(String sku, String token) throws StoreException {
        GooglePlayClient.ProductInfo p = client.getProduct(sku, token);
        if (p.productId() != null && !sku.equals(p.productId())) {
            throw new StoreException(StoreException.Kind.INVALID, "productId không khớp sản phẩm yêu cầu");
        }
        if (p.orderId() == null || p.orderId().isBlank()) {
            throw new StoreException(StoreException.Kind.INVALID, "Giao dịch không có orderId (có thể đang chờ hoặc là đơn thử)");
        }
        switch (p.purchaseState()) {
            case 0 -> { /* đã mua */ }
            case 2 -> throw new StoreException(StoreException.Kind.UNAVAILABLE, "Giao dịch đang chờ thanh toán");
            case 1 -> {
                return new VerifiedPurchase(p.orderId(), p.orderId(), sku,
                        p.purchaseTime() == null ? clock.instant() : p.purchaseTime(), null, true);
            }
            default -> throw new StoreException(StoreException.Kind.INVALID, "purchaseState không hợp lệ");
        }
        if (props.isServerAcknowledge() && !p.acknowledged()) {
            acknowledgeQuietly(() -> client.acknowledgeProduct(sku, token));
        }
        return new VerifiedPurchase(p.orderId(), p.orderId(), sku,
                p.purchaseTime() == null ? clock.instant() : p.purchaseTime(), null, false);
    }

    /**
     * Dùng cho webhook RTDN: hỏi lại Google trạng thái thuê bao của token (không tin nội dung thông báo).
     * Chỉ xét line item có productId thuộc danh mục thuê bao đang bán.
     */
    @Override
    public Resolved resolveSubscription(String token) throws StoreException {
        requireToken(token);
        GooglePlayClient.SubscriptionInfo info = client.getSubscription(token);
        Predicate<String> known = sku -> products.findById(sku)
                .filter(p -> p.isActive() && p.getType() == Product.Type.SUBSCRIPTION).isPresent();
        return map(info, known, rootKey(token, info));
    }

    /** Ánh xạ trạng thái Google → quyền lợi. Fail closed: trạng thái lạ không bao giờ cấp quyền. */
    Resolved map(GooglePlayClient.SubscriptionInfo info, Predicate<String> skuFilter, String originalKey) throws StoreException {
        Instant now = clock.instant();
        Optional<GooglePlayClient.LineItem> item = info.lineItems().stream()
                .filter(li -> li.productId() != null && li.expiryTime() != null && skuFilter.test(li.productId()))
                .max(Comparator.comparing(GooglePlayClient.LineItem::expiryTime));
        if (item.isEmpty()) {
            throw new StoreException(StoreException.Kind.INVALID, "Thuê bao không thuộc sản phẩm yêu cầu");
        }
        if (info.latestOrderId() == null || info.latestOrderId().isBlank()) {
            throw new StoreException(StoreException.Kind.INVALID, "Thuê bao không có latestOrderId");
        }
        Instant expiry = item.get().expiryTime();
        String state = info.state() == null ? "" : info.state();
        boolean entitled;
        switch (state) {
            // CANCELED: người dùng đã huỷ nhưng vẫn được dùng đến hết kỳ đã trả tiền.
            case "SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED" ->
                    entitled = expiry.isAfter(now);
            case "SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_EXPIRED" -> entitled = false;
            case "SUBSCRIPTION_STATE_PENDING" ->
                    throw new StoreException(StoreException.Kind.UNAVAILABLE, "Thuê bao đang chờ thanh toán");
            default -> throw new StoreException(StoreException.Kind.INVALID, "Trạng thái thuê bao không hợp lệ: " + state);
        }
        Instant effectiveExpiry = entitled ? expiry : (expiry.isBefore(now) ? expiry : now);
        return new Resolved(originalKey, info.latestOrderId(), item.get().productId(), entitled, effectiveExpiry, info.acknowledged());
    }

    /** Lần theo linkedPurchaseToken tới token gốc rồi băm: khoá ổn định cho cả một chuỗi thuê bao. */
    String rootKey(String token, GooglePlayClient.SubscriptionInfo first) throws StoreException {
        String root = token;
        GooglePlayClient.SubscriptionInfo cur = first;
        for (int hop = 0; hop < MAX_CHAIN_HOPS && cur.linkedPurchaseToken() != null; hop++) {
            String linked = cur.linkedPurchaseToken();
            if (!TOKEN.matcher(linked).matches()) {
                break;
            }
            root = linked;
            try {
                cur = client.getSubscription(linked);
            } catch (StoreException e) {
                if (e.kind() == StoreException.Kind.INVALID) {
                    break; // token cũ đã bị Google xoá: coi token liên kết cuối cùng là gốc
                }
                throw e;
            }
        }
        return hash(root);
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void requireToken(String token) throws StoreException {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new StoreException(StoreException.Kind.INVALID, "purchaseToken không hợp lệ");
        }
    }

    @FunctionalInterface
    private interface Ack {
        void run() throws StoreException;
    }

    private void acknowledgeQuietly(Ack ack) {
        try {
            ack.run();
        } catch (StoreException e) {
            log.warn("Server không acknowledge được giao dịch Google (client cần tự acknowledge): {}", e.getMessage());
        }
    }
}
