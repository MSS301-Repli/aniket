package com.animallocket.billing.web;

import com.animallocket.billing.domain.CheckoutSession;
import com.animallocket.billing.domain.Product;
import com.animallocket.billing.domain.Purchase;
import com.animallocket.billing.domain.Store;
import com.animallocket.billing.repo.CheckoutSessionRepository;
import com.animallocket.billing.repo.LedgerRepository;
import com.animallocket.billing.repo.ProductRepository;
import com.animallocket.billing.repo.PurchaseRepository;
import com.animallocket.billing.repo.SubscriptionRepository;
import com.animallocket.billing.repo.WalletBalanceRepository;
import com.animallocket.billing.repo.WebhookEventRepository;
import com.animallocket.billing.service.BillingService;
import com.animallocket.billing.store.PayPalClient;
import com.animallocket.billing.store.StoreException;
import com.animallocket.common.Headers;
import com.animallocket.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Webhook PayPal: hoàn tiền, chargeback, chốt nốt phiên. Chữ ký và PayPal được giả lập. */
@SpringBootTest
@AutoConfigureMockMvc
class PayPalWebhookIntegrationTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String MONTH = "animallocket.pro.month";
    static final String BOOST5 = "animallocket.boost.5";
    static final String APPROVE = "https://www.sandbox.paypal.com/checkoutnow?token=ORD1";

    @Autowired MockMvc mvc;
    @Autowired BillingService billing;
    @Autowired ProductRepository products;
    @Autowired PurchaseRepository purchases;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired WalletBalanceRepository balances;
    @Autowired LedgerRepository ledger;
    @Autowired WebhookEventRepository webhooks;
    @Autowired CheckoutSessionRepository sessions;
    @Autowired OutboxRepository outbox;
    @MockBean PayPalClient paypal;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws Exception {
        reset(paypal);
        sessions.deleteAll();
        outbox.deleteAll();
        ledger.deleteAll();
        balances.deleteAll();
        subscriptions.deleteAll();
        purchases.deleteAll();
        webhooks.deleteAll();
        products.deleteAll();
        products.save(new Product(MONTH, Product.Type.SUBSCRIPTION, "PRO", 1, true, 299, 30));
        products.save(new Product(BOOST5, Product.Type.CONSUMABLE, "BOOST", 5, true, 399, null));
        when(paypal.verifyWebhook(any(), any())).thenReturn(true);
    }

    // ---------------- helpers ----------------

    private PayPalClient.Capture captured(String amount) {
        return new PayPalClient.Capture("ORD1", "COMPLETED", "CAP1", "COMPLETED", amount, "USD", null, Instant.now());
    }

    /** Đi hết luồng web: phiên đã duyệt trên PayPal (có đơn ORD1), chưa capture. */
    private String startedSession(String sku) throws Exception {
        when(paypal.createOrder(anyString(), anyString(), anyString(), anyString(), eq("USD"), anyString(), anyString()))
                .thenReturn(new PayPalClient.CreatedOrder("ORD1", APPROVE));
        String url = mvc.perform(post("/api/v1/billing/checkout-sessions").header(Headers.PUBLIC_ID, ALICE).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"" + sku + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = url.replaceAll(".*/pay/([A-Za-z0-9_-]+).*", "$1");
        mvc.perform(post("/pay/" + token + "/start")).andExpect(status().isFound());
        return token;
    }

    /** Đã thanh toán xong bằng redirect (capture CAP1 giá {@code amount}). */
    private String paid(String sku, String amount) throws Exception {
        String token = startedSession(sku);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured(amount));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isOk());
        return token;
    }

    private ResultActions webhook(String json) throws Exception {
        return mvc.perform(withHeaders(post("/webhooks/paypal")).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static MockHttpServletRequestBuilder withHeaders(MockHttpServletRequestBuilder b) {
        return b.header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
                .header("PAYPAL-CERT-URL", "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-1")
                .header("PAYPAL-TRANSMISSION-ID", "tx-1")
                .header("PAYPAL-TRANSMISSION-SIG", "sig")
                .header("PAYPAL-TRANSMISSION-TIME", "2026-07-01T10:20:30Z");
    }

    private static String refundEvent(String id, String amount) {
        return """
                {"id":"%s","event_type":"PAYMENT.CAPTURE.REFUNDED","resource":{"id":"REF1","status":"COMPLETED",
                 "amount":{"currency_code":"USD","value":"%s"},
                 "links":[{"href":"https://api.sandbox.paypal.com/v2/payments/refunds/REF1","rel":"self"},
                          {"href":"https://api.sandbox.paypal.com/v2/payments/captures/CAP1","rel":"up"}]}}
                """.formatted(id, amount);
    }

    private static String reversedEvent(String id) {
        return """
                {"id":"%s","event_type":"PAYMENT.CAPTURE.REVERSED","resource":{"id":"CAP1","status":"REVERSED",
                 "amount":{"currency_code":"USD","value":"2.99"}}}
                """.formatted(id);
    }

    private static String completedEvent(String id, String orderId) {
        return """
                {"id":"%s","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"CAP1","status":"COMPLETED",
                 "supplementary_data":{"related_ids":{"order_id":"%s"}}}}
                """.formatted(id, orderId);
    }

    // ---------------- xác thực ----------------

    @Test
    void invalidSignatureIsRejectedAndChangesNothing() throws Exception {
        paid(MONTH, "2.99");
        when(paypal.verifyWebhook(any(), any())).thenReturn(false);
        webhook(refundEvent("WH-1", "2.99")).andExpect(status().isUnauthorized());
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void missingSignatureHeadersAreRejectedWithoutCallingPayPal() throws Exception {
        paid(MONTH, "2.99");
        mvc.perform(post("/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(refundEvent("WH-1", "2.99")))
                .andExpect(status().isUnauthorized());
        verify(paypal, never()).verifyWebhook(any(), any());
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void paypalOutageWhileVerifyingAsksForRetry() throws Exception {
        paid(MONTH, "2.99");
        when(paypal.verifyWebhook(any(), any())).thenThrow(new StoreException(StoreException.Kind.UNAVAILABLE, "down"));
        webhook(refundEvent("WH-1", "2.99")).andExpect(status().isServiceUnavailable());
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void unknownEventTypeIsIgnored() throws Exception {
        webhook("{\"id\":\"WH-9\",\"event_type\":\"BILLING.SUBSCRIPTION.CREATED\",\"resource\":{}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
    }

    // ---------------- hoàn tiền / chargeback ----------------

    @Test
    void fullRefundRevokesPro() throws Exception {
        paid(MONTH, "2.99");
        assertEquals("PRO", billing.me(ALICE).plan());
        webhook(refundEvent("WH-1", "2.99")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
        assertEquals("FREE", billing.me(ALICE).plan());
        assertEquals(Purchase.Status.REFUNDED,
                purchases.findByStoreAndStoreTransactionId(Store.PAYPAL, "CAP1").orElseThrow().getStatus());
    }

    @Test
    void fullRefundRemovesUnusedConsumables() throws Exception {
        paid(BOOST5, "3.99");
        assertEquals(5, billing.me(ALICE).wallet().get("BOOST"));
        webhook(refundEvent("WH-1", "3.99")).andExpect(status().isOk());
        assertEquals(0, billing.me(ALICE).wallet().getOrDefault("BOOST", 0));
    }

    @Test
    void partialRefundKeepsEntitlements() throws Exception {
        paid(MONTH, "2.99");
        webhook(refundEvent("WH-1", "1.00")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void chargebackRevokesPro() throws Exception {
        paid(MONTH, "2.99");
        webhook(reversedEvent("WH-2")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
        assertEquals("FREE", billing.me(ALICE).plan());
    }

    @Test
    void redeliveredEventIsDeduplicated() throws Exception {
        paid(MONTH, "2.99");
        webhook(refundEvent("WH-1", "2.99")).andExpect(jsonPath("$.status").value("processed"));
        webhook(refundEvent("WH-1", "2.99")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));
        assertEquals("FREE", billing.me(ALICE).plan());
    }

    @Test
    void refundOfUnknownCaptureChangesNothing() throws Exception {
        paid(MONTH, "2.99");
        webhook(refundEvent("WH-1", "2.99").replace("captures/CAP1", "captures/OTHER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    // ---------------- chốt nốt phiên ----------------

    @Test
    void completedEventSettlesASessionTheUserNeverReturnedTo() throws Exception {
        String token = startedSession(MONTH);
        // Người dùng đóng tab, link hết hạn; PayPal đã thu tiền nên webhook vẫn phải cấp quyền.
        CheckoutSession s = sessions.findAll().get(0);
        s.setExpiresAt(Instant.now().minusSeconds(60));
        sessions.save(s);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("2.99"));

        webhook(completedEvent("WH-3", "ORD1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));

        assertEquals("PRO", billing.me(ALICE).plan());
        assertEquals(CheckoutSession.Status.COMPLETED, sessions.findAll().get(0).getStatus());
        assertEquals("CAP1", sessions.findAll().get(0).getPaypalCaptureId());
        // trang cũ vẫn báo thành công, không thu hay cấp lần hai
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isOk());
        assertEquals(1, purchases.count());
    }

    @Test
    void completedEventFinishesAHeldPayment() throws Exception {
        String token = startedSession(MONTH);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(
                new PayPalClient.Capture("ORD1", "COMPLETED", "CAP1", "PENDING", "2.99", "USD", null, Instant.now()));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isOk());
        assertEquals("FREE", billing.me(ALICE).plan());

        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("2.99"));
        webhook(completedEvent("WH-4", "ORD1")).andExpect(jsonPath("$.status").value("processed"));
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void completedEventForUnknownOrderIsIgnoredWithoutCapturing() throws Exception {
        webhook(completedEvent("WH-5", "NOPE")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
        verify(paypal, never()).capture(anyString(), anyString());
    }

    @Test
    void completedEventDuringPayPalOutageAsksForRetry() throws Exception {
        startedSession(MONTH);
        when(paypal.capture(eq("ORD1"), anyString())).thenThrow(new StoreException(StoreException.Kind.UNAVAILABLE, "down"));
        webhook(completedEvent("WH-6", "ORD1")).andExpect(status().isServiceUnavailable());
        assertEquals("FREE", billing.me(ALICE).plan());

        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("2.99"));
        webhook(completedEvent("WH-6", "ORD1")).andExpect(status().isOk());
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void completedEventWithWrongAmountGrantsNothing() throws Exception {
        startedSession(MONTH);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("0.01"));
        webhook(completedEvent("WH-7", "ORD1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
        assertEquals("FREE", billing.me(ALICE).plan());
        assertEquals(0, purchases.count());
    }
}
