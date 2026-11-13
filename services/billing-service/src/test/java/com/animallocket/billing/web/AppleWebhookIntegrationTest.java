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
import com.animallocket.billing.store.AppleTestPki;
import com.animallocket.common.EventTypes;
import com.animallocket.common.event.EventJson;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppleWebhookIntegrationTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BUNDLE = "com.animallocket.app";
    static final String MONTH = "animallocket.pro.month";
    static final String BOOST5 = "animallocket.boost.5";
    static AppleTestPki pki;
    static Path rootPem;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws Exception {
        pki = AppleTestPki.create();
        rootPem = pki.writeRootPem(Files.createTempFile("apple-root", ".pem"));
        r.add("animal.billing.apple.root-cert-files", () -> rootPem.toString());
        r.add("animal.billing.apple.bundle-id", () -> BUNDLE);
    }

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired PurchaseRepository purchases;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired WalletBalanceRepository balances;
    @Autowired LedgerRepository ledger;
    @Autowired WebhookEventRepository webhooks;
    @Autowired OutboxRepository outbox;
    @SpyBean BillingService billing;
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
        products.save(new Product(BOOST5, Product.Type.CONSUMABLE, "BOOST", 5, true));

        Subscription s = new Subscription();
        s.setPublicId(ALICE);
        s.setSku(MONTH);
        s.setStore(Store.APPLE);
        s.setOriginalTransactionId("1000");
        s.setExpiresAt(Instant.now().plus(Duration.ofDays(2)));
        subscriptions.save(s);
    }

    private String txJws(String txId, String orig, String sku, Instant expires) throws Exception {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("transactionId", txId);
        m.put("originalTransactionId", orig);
        m.put("bundleId", BUNDLE);
        m.put("productId", sku);
        if (expires != null) m.put("expiresDate", expires.toEpochMilli());
        return pki.sign(EventJson.mapper().writeValueAsString(m));
    }

    private String notification(AppleTestPki signer, String type, String uuid, String bundle, String txJws) throws Exception {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("bundleId", bundle);
        data.put("environment", "Production");
        if (txJws != null) data.put("signedTransactionInfo", txJws);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("notificationType", type);
        payload.put("notificationUUID", uuid);
        payload.put("version", "2.0");
        payload.put("data", data);
        String signed = signer.sign(EventJson.mapper().writeValueAsString(payload));
        return "{\"signedPayload\":\"" + signed + "\"}";
    }

    private String send(String body, int expectedStatus) throws Exception {
        return mvc.perform(post("/webhooks/apple").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private List<OutboxEvent> events(String type) {
        return outbox.findAll().stream().filter(e -> e.getType().equals(type)).toList();
    }

    private Subscription sub() {
        return subscriptions.findByStoreAndOriginalTransactionId(Store.APPLE, "1000").orElseThrow();
    }

    @Test
    void renewalExtendsTheSubscriptionAndEmitsActivation() throws Exception {
        Instant newExp = Instant.now().plus(Duration.ofDays(32));
        String body = notification(pki, "DID_RENEW", UUID.randomUUID().toString(), BUNDLE, txJws("2001", "1000", MONTH, newExp));
        assertTrue(send(body, 200).contains("processed"));

        assertEquals(newExp.toEpochMilli(), sub().getExpiresAt().toEpochMilli());
        assertEquals(1, events(EventTypes.SUBSCRIPTION_ACTIVATED).size());
        assertEquals(1, webhooks.count());
    }

    @Test
    void duplicateNotificationIsProcessedOnce() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String body = notification(pki, "DID_RENEW", uuid, BUNDLE, txJws("2001", "1000", MONTH, Instant.now().plus(Duration.ofDays(32))));
        assertTrue(send(body, 200).contains("processed"));
        assertTrue(send(body, 200).contains("duplicate"));
        assertEquals(1, events(EventTypes.SUBSCRIPTION_ACTIVATED).size());
        assertEquals(1, webhooks.count());
    }

    @Test
    void forgedSignatureIsRejectedAndChangesNothing() throws Exception {
        AppleTestPki attacker = AppleTestPki.create();
        Instant before = sub().getExpiresAt();
        String body = notification(attacker, "DID_RENEW", UUID.randomUUID().toString(), BUNDLE,
                attacker.sign("{\"originalTransactionId\":\"1000\",\"bundleId\":\"" + BUNDLE + "\",\"expiresDate\":9999999999999}"));
        mvc.perform(post("/webhooks/apple").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        assertEquals(before.toEpochMilli(), sub().getExpiresAt().toEpochMilli());
        assertEquals(0, webhooks.count());
        assertTrue(outbox.findAll().isEmpty());
    }

    @Test
    void validOuterButForgedInnerTransactionIsRejected() throws Exception {
        AppleTestPki attacker = AppleTestPki.create();
        String forgedTx = attacker.sign("{\"originalTransactionId\":\"1000\",\"bundleId\":\"" + BUNDLE + "\",\"expiresDate\":9999999999999}");
        send(notification(pki, "DID_RENEW", UUID.randomUUID().toString(), BUNDLE, forgedTx), 401);
        assertEquals(0, webhooks.count());
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        String signed = pki.sign("{\"notificationType\":\"EXPIRED\",\"notificationUUID\":\"u1\",\"data\":{\"bundleId\":\"" + BUNDLE + "\"}}");
        String[] p = signed.split("\\.");
        String forged = AppleTestPki.b64("{\"notificationType\":\"DID_RENEW\",\"notificationUUID\":\"u2\"}".getBytes());
        send("{\"signedPayload\":\"" + p[0] + "." + forged + "." + p[2] + "\"}", 401);
    }

    @Test
    void garbageBodiesAreRejected() throws Exception {
        send("{\"signedPayload\":\"not-a-jws\"}", 401);
        send("{}", 400);
        send("{\"signedPayload\":\"\"}", 400);
    }

    @Test
    void expiryEndsProAndEmitsExpired() throws Exception {
        send(notification(pki, "EXPIRED", UUID.randomUUID().toString(), BUNDLE, txJws("2000", "1000", MONTH, Instant.now())), 200);
        assertEquals(Subscription.Status.EXPIRED, sub().getStatus());
        assertEquals(1, events(EventTypes.SUBSCRIPTION_EXPIRED).size());
    }

    @Test
    void refundRevokesThePurchase() throws Exception {
        Purchase p = new Purchase();
        p.setStore(Store.APPLE);
        p.setStoreTransactionId("2000");
        p.setOriginalTransactionId("1000");
        p.setPublicId(ALICE);
        p.setSku(MONTH);
        purchases.save(p);

        send(notification(pki, "REFUND", UUID.randomUUID().toString(), BUNDLE, txJws("2000", "1000", MONTH, Instant.now())), 200);

        assertEquals(Purchase.Status.REFUNDED, purchases.findByStoreAndStoreTransactionId(Store.APPLE, "2000").orElseThrow().getStatus());
        assertEquals(Subscription.Status.EXPIRED, sub().getStatus());
        assertEquals(1, events(EventTypes.SUBSCRIPTION_EXPIRED).size());
    }

    @Test
    void refundOfUnknownPurchaseStillEndsTheSubscription() throws Exception {
        send(notification(pki, "REFUND", UUID.randomUUID().toString(), BUNDLE, txJws("7777", "1000", MONTH, Instant.now())), 200);
        assertEquals(Subscription.Status.EXPIRED, sub().getStatus());
    }

    @Test
    void uninterestingNotificationTypesAreAcknowledgedWithoutSideEffects() throws Exception {
        for (String type : List.of("DID_CHANGE_RENEWAL_STATUS", "DID_FAIL_TO_RENEW", "TEST", "PRICE_INCREASE", "SOMETHING_NEW")) {
            assertTrue(send(notification(pki, type, UUID.randomUUID().toString(), BUNDLE, null), 200).contains("ignored"), type);
        }
        assertEquals(0, webhooks.count());
        assertTrue(outbox.findAll().isEmpty());
    }

    @Test
    void notificationsForAnotherAppAreRejected() throws Exception {
        send(notification(pki, "DID_RENEW", UUID.randomUUID().toString(), "com.someone.else",
                txJws("2001", "1000", MONTH, Instant.now().plus(Duration.ofDays(30)))), 400);
        assertEquals(0, webhooks.count());
    }

    @Test
    void notificationForUnknownSubscriptionIsHarmlessButRecorded() throws Exception {
        send(notification(pki, "DID_RENEW", UUID.randomUUID().toString(), BUNDLE,
                txJws("3001", "does-not-exist", MONTH, Instant.now().plus(Duration.ofDays(30)))), 200);
        assertTrue(events(EventTypes.SUBSCRIPTION_ACTIVATED).isEmpty());
    }

    @Test
    void processingFailureReturns5xxAndRollsBackSoARetrySucceeds() throws Exception {
        Instant newExp = Instant.now().plus(Duration.ofDays(32));
        String body = notification(pki, "DID_RENEW", UUID.randomUUID().toString(), BUNDLE, txJws("2001", "1000", MONTH, newExp));
        Instant before = sub().getExpiresAt();

        doThrow(new IllegalStateException("db down")).doCallRealMethod()
                .when(billing).onSubscriptionUpdated(any(), any(), any());

        send(body, 500);
        assertEquals(0, webhooks.count(), "dấu 'đã nhận' phải rollback cùng lần xử lý lỗi");
        assertEquals(before.toEpochMilli(), sub().getExpiresAt().toEpochMilli());
        assertTrue(events(EventTypes.SUBSCRIPTION_ACTIVATED).isEmpty());

        // Apple gửi lại → xử lý thành công
        assertTrue(send(body, 200).contains("processed"));
        assertEquals(newExp.toEpochMilli(), sub().getExpiresAt().toEpochMilli());
        assertEquals(1, webhooks.count());
        doCallRealMethod().when(billing).onSubscriptionUpdated(any(), any(), any());
    }
}
