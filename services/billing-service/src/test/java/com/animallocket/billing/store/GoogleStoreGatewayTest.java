package com.animallocket.billing.store;

import com.animallocket.billing.domain.Product;
import com.animallocket.billing.domain.Store;
import com.animallocket.billing.repo.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleStoreGatewayTest {
    static final String MONTH = "animallocket.pro.month";
    static final String BOOST = "animallocket.boost.5";
    static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    static class FakeClient implements GooglePlayClient {
        final Map<String, SubscriptionInfo> subs = new HashMap<>();
        final Map<String, ProductInfo> products = new HashMap<>();
        final List<String> acks = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        StoreException failWith;

        public SubscriptionInfo getSubscription(String token) throws StoreException {
            calls.add(token);
            if (failWith != null) throw failWith;
            SubscriptionInfo s = subs.get(token);
            if (s == null) throw new StoreException(StoreException.Kind.INVALID, "not found");
            return s;
        }

        public ProductInfo getProduct(String sku, String token) throws StoreException {
            if (failWith != null) throw failWith;
            ProductInfo p = products.get(token);
            if (p == null) throw new StoreException(StoreException.Kind.INVALID, "not found");
            return p;
        }

        public void acknowledgeSubscription(String sku, String token) {
            acks.add("sub:" + token);
        }

        public void acknowledgeProduct(String sku, String token) {
            acks.add("prod:" + token);
        }
    }

    FakeClient client = new FakeClient();
    GoogleProperties props = new GoogleProperties();
    ProductRepository products = mock(ProductRepository.class);
    GoogleStoreGateway gateway;

    @BeforeEach
    void setUp() {
        props.setPackageName("com.animallocket.app");
        when(products.findById(anyString())).thenReturn(Optional.empty());
        when(products.findById(MONTH)).thenReturn(Optional.of(new Product(MONTH, Product.Type.SUBSCRIPTION, "PRO", 1, true)));
        when(products.findById("animallocket.pro.year")).thenReturn(Optional.of(new Product("animallocket.pro.year", Product.Type.SUBSCRIPTION, "PRO", 1, true)));
        when(products.findById(BOOST)).thenReturn(Optional.of(new Product(BOOST, Product.Type.CONSUMABLE, "BOOST", 5, true)));
        gateway = new GoogleStoreGateway(props, client, products, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static GooglePlayClient.SubscriptionInfo sub(String state, String order, String linked, Instant expiry, String sku, boolean ack) {
        return new GooglePlayClient.SubscriptionInfo(state, order, linked, ack, List.of(new GooglePlayClient.LineItem(sku, expiry)));
    }

    private StoreException.Kind kind(String sku, String token) {
        return assertThrows(StoreException.class, () -> gateway.verify(sku, token)).kind();
    }

    @Test
    void activeSubscriptionIsEntitledUntilExpiry() throws Exception {
        Instant exp = NOW.plus(Duration.ofDays(30));
        client.subs.put("tokA", sub("SUBSCRIPTION_STATE_ACTIVE", "GPA.1111-2222-3333-44444", null, exp, MONTH, true));
        VerifiedPurchase vp = gateway.verify(MONTH, "tokA");
        assertEquals("GPA.1111-2222-3333-44444", vp.transactionId());
        assertEquals(GoogleStoreGateway.hash("tokA"), vp.originalTransactionId());
        assertEquals(64, vp.originalTransactionId().length(), "vừa cột VARCHAR(100)");
        assertEquals(exp, vp.expiresAt());
        assertFalse(vp.revoked());
        assertEquals(Store.GOOGLE, gateway.store());
    }

    @Test
    void gracePeriodAndCanceledButNotExpiredStayEntitled() throws Exception {
        Instant exp = NOW.plus(Duration.ofDays(3));
        for (String state : List.of("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED")) {
            client.subs.put("t-" + state, sub(state, "GPA.1", null, exp, MONTH, true));
            assertEquals(exp, gateway.verify(MONTH, "t-" + state).expiresAt(), state);
        }
    }

    @Test
    void expiredHeldAndPausedAreNeverEntitled() throws Exception {
        Instant future = NOW.plus(Duration.ofDays(10)); // dù hạn còn xa
        for (String state : List.of("SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_EXPIRED")) {
            client.subs.put("t-" + state, sub(state, "GPA.1", null, future, MONTH, true));
            VerifiedPurchase vp = gateway.verify(MONTH, "t-" + state);
            assertFalse(vp.expiresAt().isAfter(NOW), state + " không được còn hạn");
        }
        // canceled nhưng đã quá hạn
        client.subs.put("old", sub("SUBSCRIPTION_STATE_CANCELED", "GPA.2", null, NOW.minus(Duration.ofDays(2)), MONTH, true));
        assertFalse(gateway.verify(MONTH, "old").expiresAt().isAfter(NOW));
    }

    @Test
    void pendingIsRetryableAndUnknownStatesAreRejected() {
        client.subs.put("p", sub("SUBSCRIPTION_STATE_PENDING", "GPA.1", null, NOW.plus(Duration.ofDays(1)), MONTH, true));
        assertEquals(StoreException.Kind.UNAVAILABLE, kind(MONTH, "p"));
        client.subs.put("c", sub("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", "GPA.1", null, NOW.plus(Duration.ofDays(1)), MONTH, true));
        assertEquals(StoreException.Kind.INVALID, kind(MONTH, "c"));
        client.subs.put("u", sub("SOMETHING_NEW", "GPA.1", null, NOW.plus(Duration.ofDays(1)), MONTH, true));
        assertEquals(StoreException.Kind.INVALID, kind(MONTH, "u"));
        client.subs.put("n", sub(null, "GPA.1", null, NOW.plus(Duration.ofDays(1)), MONTH, true));
        assertEquals(StoreException.Kind.INVALID, kind(MONTH, "n"));
    }

    @Test
    void rejectsSkuMismatchMissingOrderAndUnknownProduct() {
        client.subs.put("t", sub("SUBSCRIPTION_STATE_ACTIVE", "GPA.1", null, NOW.plus(Duration.ofDays(1)), "animallocket.pro.year", true));
        assertEquals(StoreException.Kind.INVALID, kind(MONTH, "t"), "token của gói khác");
        client.subs.put("t2", sub("SUBSCRIPTION_STATE_ACTIVE", null, null, NOW.plus(Duration.ofDays(1)), MONTH, true));
        assertEquals(StoreException.Kind.INVALID, kind(MONTH, "t2"));
        assertEquals(StoreException.Kind.INVALID, kind("not.in.catalog", "t"));
        assertEquals(StoreException.Kind.INVALID, kind(MONTH, "missing-token"));
    }

    @Test
    void rejectsMalformedTokensBeforeAnyApiCall() {
        for (String bad : new String[]{null, "", "a b", "../x", "tok/en", "tok?x=1", "x".repeat(3000)}) {
            assertEquals(StoreException.Kind.INVALID, kind(MONTH, bad));
        }
        assertTrue(client.calls.isEmpty());
    }

    @Test
    void googleOutageIsUnavailable() {
        client.failWith = new StoreException(StoreException.Kind.UNAVAILABLE, "503");
        assertEquals(StoreException.Kind.UNAVAILABLE, kind(MONTH, "t"));
    }

    @Test
    void resubscribingKeepsTheSameOriginalKeyByFollowingLinkedTokens() throws Exception {
        Instant exp = NOW.plus(Duration.ofDays(30));
        client.subs.put("T1", sub("SUBSCRIPTION_STATE_EXPIRED", "GPA.1", null, NOW.minus(Duration.ofDays(40)), MONTH, true));
        client.subs.put("T2", sub("SUBSCRIPTION_STATE_EXPIRED", "GPA.2", "T1", NOW.minus(Duration.ofDays(5)), MONTH, true));
        client.subs.put("T3", sub("SUBSCRIPTION_STATE_ACTIVE", "GPA.3", "T2", exp, MONTH, true));

        String viaT3 = gateway.verify(MONTH, "T3").originalTransactionId();
        assertEquals(GoogleStoreGateway.hash("T1"), viaT3, "khoá gốc = token đầu chuỗi");
        assertEquals(viaT3, gateway.verify(MONTH, "T2").originalTransactionId());
        assertEquals(viaT3, gateway.verify(MONTH, "T1").originalTransactionId());
        // và webhook RTDN (gửi token mới nhất) tính ra cùng khoá
        assertEquals(viaT3, gateway.resolveSubscription("T3").originalTransactionId());
    }

    @Test
    void purgedLinkedTokenIsTreatedAsRoot() throws Exception {
        client.subs.put("T9", sub("SUBSCRIPTION_STATE_ACTIVE", "GPA.9", "GONE", NOW.plus(Duration.ofDays(5)), MONTH, true));
        assertEquals(GoogleStoreGateway.hash("GONE"), gateway.verify(MONTH, "T9").originalTransactionId());
    }

    @Test
    void chainFollowingIsBounded() throws Exception {
        for (int i = 0; i < 20; i++) {
            client.subs.put("L" + i, sub("SUBSCRIPTION_STATE_ACTIVE", "GPA." + i, "L" + (i + 1), NOW.plus(Duration.ofDays(5)), MONTH, true));
        }
        gateway.verify(MONTH, "L0");
        assertTrue(client.calls.size() <= 6, "chỉ lần theo tối đa 5 liên kết, gọi " + client.calls.size());
    }

    @Test
    void differentSubscriptionsGetDifferentKeys() throws Exception {
        client.subs.put("X", sub("SUBSCRIPTION_STATE_ACTIVE", "GPA.1", null, NOW.plus(Duration.ofDays(5)), MONTH, true));
        client.subs.put("Y", sub("SUBSCRIPTION_STATE_ACTIVE", "GPA.2", null, NOW.plus(Duration.ofDays(5)), MONTH, true));
        assertNotEquals(gateway.verify(MONTH, "X").originalTransactionId(), gateway.verify(MONTH, "Y").originalTransactionId());
    }

    @Test
    void webhookResolverUsesOnlyCatalogSubscriptionsAndReportsState() throws Exception {
        client.subs.put("W", new GooglePlayClient.SubscriptionInfo("SUBSCRIPTION_STATE_ACTIVE", "GPA.5", null, true, List.of(
                new GooglePlayClient.LineItem("some.other.product", NOW.plus(Duration.ofDays(99))),
                new GooglePlayClient.LineItem(MONTH, NOW.plus(Duration.ofDays(7))))));
        var r = gateway.resolveSubscription("W");
        assertTrue(r.entitled());
        assertEquals(MONTH, r.productId());
        assertEquals(NOW.plus(Duration.ofDays(7)), r.expiresAt());

        client.subs.put("V", sub("SUBSCRIPTION_STATE_ON_HOLD", "GPA.6", null, NOW.plus(Duration.ofDays(7)), MONTH, true));
        assertFalse(gateway.resolveSubscription("V").entitled());

        client.subs.put("Z", sub("SUBSCRIPTION_STATE_ACTIVE", "GPA.7", null, NOW.plus(Duration.ofDays(7)), "unknown.product", true));
        assertEquals(StoreException.Kind.INVALID, assertThrows(StoreException.class, () -> gateway.resolveSubscription("Z")).kind());
    }

    // ---- mua lẻ ----

    @Test
    void purchasedOneTimeProductIsGranted() throws Exception {
        client.products.put("tokP", new GooglePlayClient.ProductInfo("GPA.9999", 0, BOOST, NOW, true));
        VerifiedPurchase vp = gateway.verify(BOOST, "tokP");
        assertEquals("GPA.9999", vp.transactionId());
        assertEquals("GPA.9999", vp.originalTransactionId());
        assertNull(vp.expiresAt());
        assertFalse(vp.revoked());
    }

    @Test
    void canceledPendingAndMismatchedOneTimePurchases() {
        client.products.put("c", new GooglePlayClient.ProductInfo("GPA.1", 1, BOOST, NOW, true));
        assertTrue(assertThrowsNone(() -> gateway.verify(BOOST, "c")).revoked());
        client.products.put("p", new GooglePlayClient.ProductInfo("GPA.2", 2, BOOST, NOW, false));
        assertEquals(StoreException.Kind.UNAVAILABLE, kind(BOOST, "p"));
        client.products.put("m", new GooglePlayClient.ProductInfo("GPA.3", 0, "other.sku", NOW, true));
        assertEquals(StoreException.Kind.INVALID, kind(BOOST, "m"));
        client.products.put("n", new GooglePlayClient.ProductInfo(null, 0, BOOST, NOW, true));
        assertEquals(StoreException.Kind.INVALID, kind(BOOST, "n"), "không có orderId (đơn thử/giả) không được cấp");
        client.products.put("s", new GooglePlayClient.ProductInfo("GPA.4", 7, BOOST, NOW, true));
        assertEquals(StoreException.Kind.INVALID, kind(BOOST, "s"));
    }

    @Test
    void serverAcknowledgeIsOptInAndBestEffort() throws Exception {
        client.products.put("a", new GooglePlayClient.ProductInfo("GPA.1", 0, BOOST, NOW, false));
        client.subs.put("b", sub("SUBSCRIPTION_STATE_ACTIVE", "GPA.2", null, NOW.plus(Duration.ofDays(1)), MONTH, false));
        gateway.verify(BOOST, "a");
        gateway.verify(MONTH, "b");
        assertTrue(client.acks.isEmpty(), "mặc định server không acknowledge");

        props.setServerAcknowledge(true);
        gateway.verify(BOOST, "a");
        gateway.verify(MONTH, "b");
        assertEquals(List.of("prod:a", "sub:b"), client.acks);
    }

    private VerifiedPurchase assertThrowsNone(ThrowingSupplier s) {
        try {
            return s.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        VerifiedPurchase get() throws Exception;
    }
}
