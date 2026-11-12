package com.animallocket.billing.web;

import com.animallocket.billing.domain.Product;
import com.animallocket.billing.domain.Purchase;
import com.animallocket.billing.domain.Store;
import com.animallocket.billing.domain.Subscription;
import com.animallocket.billing.repo.LedgerRepository;
import com.animallocket.billing.repo.ProductRepository;
import com.animallocket.billing.repo.PurchaseRepository;
import com.animallocket.billing.repo.SubscriptionRepository;
import com.animallocket.billing.repo.WalletBalanceRepository;
import com.animallocket.billing.repo.WebhookEventRepository;
import com.animallocket.billing.service.BillingService;
import com.animallocket.billing.store.GooglePushAuth;
import com.animallocket.billing.store.GoogleSubscriptionResolver;
import com.animallocket.billing.store.StoreException;
import com.animallocket.common.EventTypes;
import com.animallocket.outbox.OutboxEvent;
import com.animallocket.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "animal.billing.google.package-name=com.animallocket.app")
@AutoConfigureMockMvc
class GoogleWebhookIntegrationTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String PKG = "com.animallocket.app";
    static final String MONTH = "animallocket.pro.month";
    static final String ORIG = "hash-of-root-token";

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired PurchaseRepository purchases;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired WalletBalanceRepository balances;
    @Autowired LedgerRepository ledger;
    @Autowired WebhookEventRepository webhooks;
    @Autowired OutboxRepository outbox;
    @SpyBean BillingService billing;
    @MockBean GooglePushAuth pushAuth;
    @MockBean GoogleSubscriptionResolver resolver;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        ledger.deleteAll();
        balances.deleteAll();
        subscriptions.deleteAll();
        purchases.deleteAll();
        webhooks.deleteAll();
        products.deleteAll();
        products.save(new Product(MONTH, Product.Type.SUBSCRIPTION, "PRO", 1, true));

        Subscription s = new Subscription();
        s.setPublicId(ALICE);
        s.setSku(MONTH);
        s.setStore(Store.GOOGLE);
        s.setOriginalTransactionId(ORIG);
        s.setExpiresAt(Instant.now().plus(Duration.ofDays(2)));
        subscriptions.save(s);

        reset(pushAuth, resolver);
        doNothing().when(pushAuth).authenticate("Bearer good");
        doThrow(new SecurityException("thiếu token")).when(pushAuth).authenticate(isNull());
        doThrow(new SecurityException("sai token")).when(pushAuth).authenticate("Bearer bad");
    }

    private static String message(String messageId, String noteJson) {
        String data = Base64.getEncoder().encodeToString(noteJson.getBytes(StandardCharsets.UTF_8));
        return "{\"message\":{\"data\":\"" + data + "\",\"messageId\":\"" + messageId + "\"},\"subscription\":\"projects/p/subscriptions/s\"}";
    }

    private static String subNote(String pkg, int type, String token) {
        return "{\"version\":\"1.0\",\"packageName\":\"" + pkg + "\",\"eventTimeMillis\":\"1\",\"subscriptionNotification\":"
                + "{\"version\":\"1.0\",\"notificationType\":" + type + ",\"purchaseToken\":\"" + token + "\",\"subscriptionId\":\"" + MONTH + "\"}}";
    }

    private MockHttpServletRequestBuilder req(String auth, String body) {
        MockHttpServletRequestBuilder b = post("/webhooks/google").contentType(MediaType.APPLICATION_JSON).content(body);
        return auth == null ? b : b.header("Authorization", auth);
    }

    private Subscription sub() {
        return subscriptions.findByStoreAndOriginalTransactionId(Store.GOOGLE, ORIG).orElseThrow();
    }

    private List<OutboxEvent> events(String type) {
        return outbox.findAll().stream().filter(e -> e.getType().equals(type)).toList();
    }

    @Test
    void missingOrBadBearerIsRejectedAndChangesNothing() throws Exception {
        String body = message("m1", subNote(PKG, 2, "tok"));
        mvc.perform(req(null, body)).andExpect(status().isUnauthorized());
        mvc.perform(req("Bearer bad", body)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        assertEquals(0, webhooks.count());
        assertTrue(outbox.findAll().isEmpty());
    }

    @Test
    void stateComesFromGoogleNotFromTheNotificationContents() throws Exception {
        Instant newExp = Instant.now().plus(Duration.ofDays(31));
        // Thông báo tự nhận là "EXPIRED" (13) nhưng Google xác nhận vẫn còn hạn → phải gia hạn.
        when(resolver.resolveSubscription("tok")).thenReturn(
                new GoogleSubscriptionResolver.Resolved(ORIG, "GPA.2", MONTH, true, newExp, true));
        mvc.perform(req("Bearer good", message("m1", subNote(PKG, 13, "tok")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"));
        assertEquals(newExp.toEpochMilli(), sub().getExpiresAt().toEpochMilli());
        assertEquals(1, events(EventTypes.SUBSCRIPTION_ACTIVATED).size());

        // Ngược lại: thông báo tự nhận là "RENEWED" (2) nhưng Google nói không còn quyền lợi → phải hạ cấp.
        when(resolver.resolveSubscription("tok2")).thenReturn(
                new GoogleSubscriptionResolver.Resolved(ORIG, "GPA.3", MONTH, false, Instant.now().minusSeconds(5), true));
        mvc.perform(req("Bearer good", message("m2", subNote(PKG, 2, "tok2")))).andExpect(status().isOk());
        assertEquals(Subscription.Status.EXPIRED, sub().getStatus());
        assertEquals(1, events(EventTypes.SUBSCRIPTION_EXPIRED).size());
    }

    @Test
    void duplicateMessageIdIsProcessedOnce() throws Exception {
        when(resolver.resolveSubscription("tok")).thenReturn(
                new GoogleSubscriptionResolver.Resolved(ORIG, "GPA.2", MONTH, true, Instant.now().plus(Duration.ofDays(31)), true));
        String body = message("same-id", subNote(PKG, 2, "tok"));
        mvc.perform(req("Bearer good", body)).andExpect(jsonPath("$.status").value("processed"));
        mvc.perform(req("Bearer good", body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));
        assertEquals(1, events(EventTypes.SUBSCRIPTION_ACTIVATED).size());
    }

    @Test
    void googleOutageReturns503WithoutRecordingSoPubSubRetries() throws Exception {
        AtomicInteger n = new AtomicInteger();
        when(resolver.resolveSubscription("tok")).thenAnswer(inv -> {
            if (n.incrementAndGet() == 1) {
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "Google down");
            }
            return new GoogleSubscriptionResolver.Resolved(ORIG, "GPA.2", MONTH, true, Instant.now().plus(Duration.ofDays(31)), true);
        });
        String body = message("m-retry", subNote(PKG, 2, "tok"));
        mvc.perform(req("Bearer good", body)).andExpect(status().isServiceUnavailable());
        assertEquals(0, webhooks.count());
        mvc.perform(req("Bearer good", body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
        assertEquals(1, webhooks.count());
    }

    @Test
    void unknownTokenIsAcknowledgedAndIgnored() throws Exception {
        when(resolver.resolveSubscription(anyString())).thenThrow(new StoreException(StoreException.Kind.INVALID, "token đã bị xoá"));
        mvc.perform(req("Bearer good", message("m1", subNote(PKG, 2, "gone")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));
        assertEquals(0, webhooks.count());
    }

    @Test
    void testNotificationAndOtherPackagesAreNoOps() throws Exception {
        mvc.perform(req("Bearer good", message("t1", "{\"version\":\"1.0\",\"packageName\":\"" + PKG + "\",\"testNotification\":{\"version\":\"1.0\"}}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
        mvc.perform(req("Bearer good", message("t2", subNote("com.someone.else", 2, "tok"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
        assertEquals(0, webhooks.count());
        assertTrue(outbox.findAll().isEmpty());
    }

    @Test
    void malformedMessagesAreBadRequests() throws Exception {
        mvc.perform(req("Bearer good", "{}")).andExpect(status().isBadRequest());
        mvc.perform(req("Bearer good", "{\"message\":{\"messageId\":\"x\"}}")).andExpect(status().isBadRequest());
        mvc.perform(req("Bearer good", "{\"message\":{\"messageId\":\"x\",\"data\":\"%%%\"}}")).andExpect(status().isBadRequest());
        mvc.perform(req("Bearer good", "{\"message\":{\"messageId\":\"../x\",\"data\":\"e30=\"}}")).andExpect(status().isBadRequest());
    }

    @Test
    void voidedSubscriptionOrderRevokesThePurchaseAndEndsPro() throws Exception {
        Purchase p = new Purchase();
        p.setStore(Store.GOOGLE);
        p.setStoreTransactionId("GPA.1111");
        p.setOriginalTransactionId(ORIG);
        p.setPublicId(ALICE);
        p.setSku(MONTH);
        purchases.save(p);
        when(resolver.resolveSubscription("tok")).thenReturn(
                new GoogleSubscriptionResolver.Resolved(ORIG, "GPA.1111", MONTH, true, Instant.now().plus(Duration.ofDays(10)), true));

        String note = "{\"version\":\"1.0\",\"packageName\":\"" + PKG + "\",\"voidedPurchaseNotification\":"
                + "{\"purchaseToken\":\"tok\",\"orderId\":\"GPA.1111\",\"productType\":1,\"refundType\":1}}";
        mvc.perform(req("Bearer good", message("v1", note))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));

        assertEquals(Purchase.Status.REFUNDED, purchases.findByStoreAndStoreTransactionId(Store.GOOGLE, "GPA.1111").orElseThrow().getStatus());
        assertEquals(Subscription.Status.EXPIRED, sub().getStatus(), "đơn bị hoàn tiền thì hết Pro dù Google báo còn hạn");
    }

    @Test
    void voidedOrderWithoutLocalPurchaseStillEndsTheSubscriptionViaTheChainKey() throws Exception {
        when(resolver.resolveSubscription("tok")).thenReturn(
                new GoogleSubscriptionResolver.Resolved(ORIG, "GPA.77", MONTH, true, Instant.now().plus(Duration.ofDays(10)), true));
        String note = "{\"version\":\"1.0\",\"packageName\":\"" + PKG + "\",\"voidedPurchaseNotification\":"
                + "{\"purchaseToken\":\"tok\",\"orderId\":\"GPA.77\",\"productType\":1,\"refundType\":2}}";
        mvc.perform(req("Bearer good", message("v2", note))).andExpect(status().isOk());
        assertEquals(Subscription.Status.EXPIRED, sub().getStatus());
    }

    @Test
    void processingFailureReturns5xxAndRollsBackSoARetrySucceeds() throws Exception {
        Instant newExp = Instant.now().plus(Duration.ofDays(31));
        when(resolver.resolveSubscription("tok")).thenReturn(
                new GoogleSubscriptionResolver.Resolved(ORIG, "GPA.2", MONTH, true, newExp, true));
        String body = message("m-fail", subNote(PKG, 2, "tok"));
        Instant before = sub().getExpiresAt();
        doThrow(new IllegalStateException("db down")).doCallRealMethod()
                .when(billing).onSubscriptionUpdated(any(), any(), any());

        mvc.perform(req("Bearer good", body)).andExpect(status().is5xxServerError());
        assertEquals(0, webhooks.count(), "dấu đã nhận phải rollback cùng lần xử lý lỗi");
        assertEquals(before.toEpochMilli(), sub().getExpiresAt().toEpochMilli());

        mvc.perform(req("Bearer good", body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
        assertEquals(newExp.toEpochMilli(), sub().getExpiresAt().toEpochMilli());
        assertEquals(1, webhooks.count());
        doCallRealMethod().when(billing).onSubscriptionUpdated(any(), any(), any());
    }
}
