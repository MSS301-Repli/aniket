package com.animallocket.billing.web;

import com.animallocket.billing.domain.CheckoutSession;
import com.animallocket.billing.domain.Product;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Luồng thanh toán web: app xin liên kết → trang /pay → PayPal → capture → cấp quyền. PayPal được giả lập. */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutFlowIntegrationTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";
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
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
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
        products.save(new Product("animallocket.noprice", Product.Type.CONSUMABLE, "BOOST", 1, true));
        products.save(new Product("animallocket.nodays", Product.Type.SUBSCRIPTION, "PRO", 1, true, 299, null));
        products.save(new Product("animallocket.old", Product.Type.CONSUMABLE, "BOOST", 1, false, 99, null));
    }

    // ---------------- helpers ----------------

    /** App (đã đăng nhập) xin liên kết thanh toán; trả mã trên URL. */
    private String createSession(String me, String sku) throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/v1/billing/checkout-sessions")
                        .header(Headers.PUBLIC_ID, me).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        String url = json.readTree(res.getContentAsString()).path("url").asText();
        assertTrue(url.startsWith("http://localhost:8080/pay/"), url);
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private String body(String path) throws Exception {
        return mvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void stubOrder() throws Exception {
        when(paypal.createOrder(anyString(), anyString(), anyString(), anyString(), eq("USD"), anyString(), anyString()))
                .thenReturn(new PayPalClient.CreatedOrder("ORD1", APPROVE));
    }

    private PayPalClient.Capture captured(String amount, String customId) {
        return new PayPalClient.Capture("ORD1", "COMPLETED", "CAP1", "COMPLETED", amount, "USD", customId, Instant.now());
    }

    /** Đi đến bước đã duyệt trên PayPal (đã có đơn), sẵn sàng cho /return. */
    private String startedSession(String me, String sku) throws Exception {
        stubOrder();
        String token = createSession(me, sku);
        mvc.perform(post("/pay/" + token + "/start")).andExpect(status().isFound());
        return token;
    }

    // ---------------- tạo phiên ----------------

    @Test
    void createRejectsUnknownUnpricedAndInactiveProducts() throws Exception {
        for (String sku : List.of("nope", "animallocket.noprice", "animallocket.nodays", "animallocket.old")) {
            mvc.perform(post("/api/v1/billing/checkout-sessions").header(Headers.PUBLIC_ID, ALICE).with(jwt())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"" + sku + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, sessions.count());
    }

    @Test
    void createNeedsLoginAndProfile() throws Exception {
        mvc.perform(post("/api/v1/billing/checkout-sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + MONTH + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/billing/checkout-sessions").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + MONTH + "\"}"))
                .andExpect(status().isForbidden());
        assertEquals(0, sessions.count());
    }

    @Test
    void tokenIsStoredOnlyAsHash() throws Exception {
        String token = createSession(ALICE, MONTH);
        CheckoutSession s = sessions.findAll().get(0);
        assertEquals(64, s.getTokenHash().length());
        assertTrue(!s.getTokenHash().contains(token));
        assertEquals(ALICE, s.getPublicId());
        assertEquals(299, s.getAmountCents());
    }

    // ---------------- trang web ----------------

    @Test
    void pageShowsOrderWithoutLoginAndDoesNotLeakToken() throws Exception {
        String token = createSession(ALICE, MONTH);
        mvc.perform(get("/pay/" + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        String html = body("/pay/" + token);
        assertTrue(html.contains("Animal Locket Pro (30 ngày)"), html);
        assertTrue(html.contains("2.99 USD"), html);
        assertTrue(html.contains("/pay/" + token + "/start"), html);
    }

    @Test
    void unknownOrMalformedTokensAre404() throws Exception {
        mvc.perform(get("/pay/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).andExpect(status().isNotFound());
        mvc.perform(get("/pay/short")).andExpect(status().isNotFound());
        mvc.perform(get("/pay/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/return")).andExpect(status().isNotFound());
        mvc.perform(post("/pay/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/start")).andExpect(status().isNotFound());
        verify(paypal, never()).capture(anyString(), anyString());
    }

    @Test
    void expiredSessionCannotStartOrCapture() throws Exception {
        String token = createSession(ALICE, MONTH);
        CheckoutSession s = sessions.findAll().get(0);
        s.setExpiresAt(Instant.now().minusSeconds(5));
        sessions.save(s);
        mvc.perform(get("/pay/" + token)).andExpect(status().isGone());
        mvc.perform(post("/pay/" + token + "/start")).andExpect(status().isGone());
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isGone());
        verify(paypal, never()).createOrder(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paypal, never()).capture(anyString(), anyString());
    }

    // ---------------- thanh toán thành công ----------------

    @Test
    void happyPathGrantsProToTheSessionOwnerOnly() throws Exception {
        stubOrder();
        String token = createSession(ALICE, MONTH);

        mvc.perform(post("/pay/" + token + "/start"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APPROVE));
        assertEquals(CheckoutSession.Status.APPROVING, sessions.findAll().get(0).getStatus());
        assertEquals("FREE", billing.me(ALICE).plan()); // chưa capture thì chưa có quyền

        long id = sessions.findAll().get(0).getId();
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("2.99", String.valueOf(id)));
        mvc.perform(get("/pay/" + token + "/return?token=ORD1&PayerID=XYZ")).andExpect(status().isOk());

        assertEquals("PRO", billing.me(ALICE).plan());
        assertEquals("FREE", billing.me(BOB).plan());
        var purchase = purchases.findByStoreAndStoreTransactionId(Store.PAYPAL, "CAP1").orElseThrow();
        assertEquals(ALICE, purchase.getPublicId());
        assertEquals(MONTH, purchase.getSku());
        assertEquals(CheckoutSession.Status.COMPLETED, sessions.findAll().get(0).getStatus());
        var expires = billing.me(ALICE).planExpiresAt();
        assertTrue(expires.isAfter(Instant.now().plusSeconds(29L * 86400)), expires.toString());
        assertTrue(expires.isBefore(Instant.now().plusSeconds(31L * 86400)), expires.toString());

        String page = body("/pay/" + token + "/return");
        assertTrue(page.contains("Thanh toán thành công"), page);
        assertTrue(page.contains("animallocket://checkout?status=success"), page);
    }

    @Test
    void reloadingReturnPageDoesNotCaptureOrGrantTwice() throws Exception {
        String token = startedSession(ALICE, BOOST5);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("3.99", null));
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isOk());
        }
        verify(paypal, times(1)).capture(eq("ORD1"), anyString());
        assertEquals(5, billing.me(ALICE).wallet().get("BOOST"));
        assertEquals(1, purchases.count());
    }

    @Test
    void concurrentReturnsGrantOnce() throws Exception {
        String token = startedSession(ALICE, BOOST5);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("3.99", null));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> mvc.perform(get("/pay/" + token + "/return"))
                        .andReturn().getResponse().getStatus()));
            }
            for (Future<Integer> f : results) {
                assertTrue(f.get() == 200 || f.get() == 503, "status " + f.get());
            }
        } finally {
            pool.shutdownNow();
        }
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isOk());
        assertEquals(1, purchases.count());
        assertEquals(5, billing.me(ALICE).wallet().get("BOOST"));
    }

    // ---------------- fail closed ----------------

    @Test
    void amountMismatchGrantsNothing() throws Exception {
        String token = startedSession(ALICE, MONTH);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("0.01", null));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isUnprocessableEntity());
        assertEquals("FREE", billing.me(ALICE).plan());
        assertEquals(0, purchases.count());
        assertEquals(CheckoutSession.Status.APPROVING, sessions.findAll().get(0).getStatus());
    }

    @Test
    void customIdOfAnotherSessionGrantsNothing() throws Exception {
        String token = startedSession(ALICE, MONTH);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("2.99", "999999"));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isUnprocessableEntity());
        assertEquals(0, purchases.count());
    }

    @Test
    void wrongOrderIdOrCurrencyOrStatusGrantsNothing() throws Exception {
        String token = startedSession(ALICE, MONTH);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(
                new PayPalClient.Capture("OTHER", "COMPLETED", "CAP1", "COMPLETED", "2.99", "USD", null, Instant.now()));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isUnprocessableEntity());
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(
                new PayPalClient.Capture("ORD1", "COMPLETED", "CAP1", "COMPLETED", "2.99", "EUR", null, Instant.now()));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isUnprocessableEntity());
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(
                new PayPalClient.Capture("ORD1", "APPROVED", "CAP1", "DECLINED", "2.99", "USD", null, Instant.now()));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isUnprocessableEntity());
        assertEquals(0, purchases.count());
        assertEquals("FREE", billing.me(ALICE).plan());
    }

    @Test
    void pendingCaptureIsHeldWithoutGranting() throws Exception {
        String token = startedSession(ALICE, MONTH);
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(
                new PayPalClient.Capture("ORD1", "COMPLETED", "CAP1", "PENDING", "2.99", "USD", null, Instant.now()));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isOk());
        assertEquals("FREE", billing.me(ALICE).plan());
        assertEquals(CheckoutSession.Status.PAYMENT_HELD, sessions.findAll().get(0).getStatus());

        // Khi PayPal hoàn tất khoản thu, bấm "Kiểm tra lại" sẽ cấp quyền.
        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("2.99", null));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isOk());
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void paypalOutageOnCaptureIsRetryableAndGrantsNothing() throws Exception {
        String token = startedSession(ALICE, MONTH);
        when(paypal.capture(eq("ORD1"), anyString()))
                .thenThrow(new StoreException(StoreException.Kind.UNAVAILABLE, "down"));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isServiceUnavailable());
        assertEquals(0, purchases.count());

        when(paypal.capture(eq("ORD1"), anyString())).thenReturn(captured("2.99", null));
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isOk());
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void returnBeforeStartingShowsCheckoutAgain() throws Exception {
        String token = createSession(ALICE, MONTH);
        mvc.perform(get("/pay/" + token + "/return")).andExpect(status().isOk());
        verify(paypal, never()).capture(anyString(), anyString());
        assertEquals(0, purchases.count());
    }

    @Test
    void paypalOutageOnStartKeepsSessionUsable() throws Exception {
        String token = createSession(ALICE, MONTH);
        when(paypal.createOrder(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new StoreException(StoreException.Kind.UNAVAILABLE, "down"));
        mvc.perform(post("/pay/" + token + "/start")).andExpect(status().isServiceUnavailable());
        assertEquals(CheckoutSession.Status.PENDING, sessions.findAll().get(0).getStatus());
    }

    @Test
    void nonHttpsApproveLinkIsNeverFollowed() throws Exception {
        String token = createSession(ALICE, MONTH);
        when(paypal.createOrder(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PayPalClient.CreatedOrder("ORD1", "http://evil.example/steal"));
        mvc.perform(post("/pay/" + token + "/start")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void cancelReturnsToCheckoutAndAllowsRetry() throws Exception {
        String token = startedSession(ALICE, MONTH);
        mvc.perform(get("/pay/" + token + "/cancel")).andExpect(status().isOk());
        assertEquals(CheckoutSession.Status.PENDING, sessions.findAll().get(0).getStatus());
        mvc.perform(post("/pay/" + token + "/start")).andExpect(status().isFound());
        verify(paypal, never()).capture(anyString(), anyString());
    }
}
