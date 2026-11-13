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
import com.animallocket.billing.store.PayOsClient;
import com.animallocket.billing.store.PayOsProperties;
import com.animallocket.billing.store.PayPalClient;
import com.animallocket.billing.store.StoreException;
import com.animallocket.common.Headers;
import com.animallocket.outbox.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Luồng VietQR/payOS: xin liên kết → /pay → payOS → trang quay về hoặc webhook → cấp quyền. payOS được giả lập. */
@SpringBootTest
@AutoConfigureMockMvc
class PayOsCheckoutIntegrationTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String MONTH = "animallocket.pro.month";
    static final String BOOST5 = "animallocket.boost.5";
    static final String PAYPAL_ONLY = "animallocket.paypalonly";
    static final String PAY_URL = "https://pay.payos.vn/web/link-1";

    @Autowired MockMvc mvc;
    @Autowired BillingService billing;
    @Autowired PayOsProperties payosProps;
    @Autowired ProductRepository products;
    @Autowired PurchaseRepository purchases;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired WalletBalanceRepository balances;
    @Autowired LedgerRepository ledger;
    @Autowired WebhookEventRepository webhooks;
    @Autowired CheckoutSessionRepository sessions;
    @Autowired OutboxRepository outbox;
    @MockBean PayOsClient payos;
    @MockBean PayPalClient paypal;
    @MockBean JwtDecoder jwtDecoder;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        reset(payos, paypal);
        sessions.deleteAll();
        outbox.deleteAll();
        ledger.deleteAll();
        balances.deleteAll();
        subscriptions.deleteAll();
        purchases.deleteAll();
        webhooks.deleteAll();
        products.deleteAll();
        products.save(new Product(MONTH, Product.Type.SUBSCRIPTION, "PRO", 1, true, 299, 30, 79000));
        products.save(new Product(BOOST5, Product.Type.CONSUMABLE, "BOOST", 5, true, 399, null, 99000));
        products.save(new Product(PAYPAL_ONLY, Product.Type.CONSUMABLE, "BOOST", 1, true, 99, null));
        when(payos.createPaymentLink(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> new PayOsClient.PaymentLink(inv.<Long>getArgument(0), "link-" + inv.<Long>getArgument(0),
                        PAY_URL, "PENDING"));
        when(payos.verifyWebhookSignature(any(), anyString())).thenReturn(true);
    }

    // ---------------- helpers ----------------

    private String createSession(String sku) throws Exception {
        String res = mvc.perform(post("/api/v1/billing/checkout-sessions").header(Headers.PUBLIC_ID, ALICE).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"" + sku + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return res.replaceAll(".*/pay/([A-Za-z0-9_-]+).*", "$1");
    }

    private CheckoutSession session() {
        return sessions.findAll().get(0);
    }

    private long orderCode(int attempt) {
        return payosProps.getOrderCodeBase() + session().getId() * 100 + attempt;
    }

    private String startPayOs(String token) throws Exception {
        mvc.perform(post("/pay/" + token + "/payos")).andExpect(status().isFound()).andExpect(header().string("Location", PAY_URL));
        return token;
    }

    private void stubInfo(long orderCode, String status, long amount, long paid) throws Exception {
        // doReturn: dựng lại stub không gọi phương thức thật (nếu stub trước đó là thenThrow thì when(...) sẽ ném lỗi).
        doReturn(new PayOsClient.PaymentInfo(orderCode, status, amount, paid)).when(payos).getPaymentInfo(orderCode);
    }

    private String body(String path) throws Exception {
        return mvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private ResultActions webhook(long orderCode, boolean success, String code) throws Exception {
        String payload = """
                {"code":"00","desc":"success","success":%s,"data":{"orderCode":%d,"amount":79000,"description":"AL1",
                 "accountNumber":"1","reference":"TF1","transactionDateTime":"2026-11-07 17:50:00","currency":"VND",
                 "paymentLinkId":"link-1","code":"%s","desc":"Thành công"},"signature":"sig"}
                """.formatted(success, orderCode, code);
        return mvc.perform(post("/webhooks/payos").contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    // ---------------- trang và bắt đầu thanh toán ----------------

    @Test
    void pageShowsBothMethodsWithTheirOwnPrices() throws Exception {
        String token = createSession(MONTH);
        String html = body("/pay/" + token);
        assertTrue(html.contains("2.99 USD"), html);
        assertTrue(html.contains("79.000 ₫"), html);
        assertTrue(html.contains("/pay/" + token + "/start"), html);
        assertTrue(html.contains("/pay/" + token + "/payos"), html);
    }

    @Test
    void startCreatesALinkWithADerivedOrderCodeAndShortDescription() throws Exception {
        String token = createSession(MONTH);
        startPayOs(token);

        long id = session().getId();
        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> returnUrl = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cancelUrl = ArgumentCaptor.forClass(String.class);
        verify(payos).createPaymentLink(eq(payosProps.getOrderCodeBase() + id * 100 + 1), eq(79000), description.capture(),
                anyString(), returnUrl.capture(), cancelUrl.capture());
        assertEquals("AL" + id, description.getValue());
        assertTrue(description.getValue().length() <= 9);
        assertTrue(returnUrl.getValue().endsWith("/pay/" + token + "/payos/return"), returnUrl.getValue());
        assertTrue(cancelUrl.getValue().endsWith("/pay/" + token + "/payos/cancel"), cancelUrl.getValue());
        assertEquals(1, session().getPayosAttempts());
        assertEquals(79000, session().getPayosAmount());
        assertEquals(CheckoutSession.Status.APPROVING, session().getStatus());
        assertEquals("FREE", billing.me(ALICE).plan()); // chưa trả tiền thì chưa có quyền
    }

    @Test
    void eachStartUsesANewOrderCode() throws Exception {
        String token = createSession(MONTH);
        startPayOs(token);
        startPayOs(token);
        verify(payos).createPaymentLink(eq(orderCode(1)), anyInt(), anyString(), anyString(), anyString(), anyString());
        verify(payos).createPaymentLink(eq(orderCode(2)), anyInt(), anyString(), anyString(), anyString(), anyString());
        assertEquals(2, session().getPayosAttempts());
    }

    @Test
    void productWithoutVndPriceCannotUsePayOs() throws Exception {
        String token = createSession(PAYPAL_ONLY);
        String html = body("/pay/" + token);
        assertTrue(!html.contains("/payos"), html);
        mvc.perform(post("/pay/" + token + "/payos")).andExpect(status().isUnprocessableEntity());
        verify(payos, never()).createPaymentLink(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void nonHttpsCheckoutUrlIsNeverFollowed() throws Exception {
        String token = createSession(MONTH);
        when(payos.createPaymentLink(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PayOsClient.PaymentLink(1, "x", "http://evil.example/steal", "PENDING"));
        mvc.perform(post("/pay/" + token + "/payos")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void payOsOutageOnStartKeepsSessionUsable() throws Exception {
        String token = createSession(MONTH);
        when(payos.createPaymentLink(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new StoreException(StoreException.Kind.UNAVAILABLE, "down"));
        mvc.perform(post("/pay/" + token + "/payos")).andExpect(status().isServiceUnavailable());
        assertEquals(CheckoutSession.Status.PENDING, session().getStatus());
        // Lần sau dùng orderCode mới (đã ghi trước khi gọi payOS) nên không đụng link có thể đã tạo dở.
        doReturn(new PayOsClient.PaymentLink(orderCode(2), "l", PAY_URL, "PENDING")).when(payos)
                .createPaymentLink(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyString());
        mvc.perform(post("/pay/" + token + "/payos")).andExpect(status().isFound());
        verify(payos).createPaymentLink(eq(orderCode(2)), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    // ---------------- trang quay về ----------------

    @Test
    void paidReturnGrantsProAndIsIdempotent() throws Exception {
        String token = startPayOs(createSession(MONTH));
        stubInfo(orderCode(1), "PAID", 79000, 79000);
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/pay/" + token + "/payos/return?code=00&id=link-1&cancel=false&status=PAID&orderCode=" + orderCode(1)))
                    .andExpect(status().isOk());
        }
        assertEquals("PRO", billing.me(ALICE).plan());
        assertEquals(1, purchases.count());
        var purchase = purchases.findByStoreAndStoreTransactionId(Store.PAYOS, "payos:" + orderCode(1)).orElseThrow();
        assertEquals(ALICE, purchase.getPublicId());
        assertEquals(CheckoutSession.Status.COMPLETED, session().getStatus());
        assertEquals(orderCode(1), session().getPayosPaidOrderCode());
        var expires = billing.me(ALICE).planExpiresAt();
        assertTrue(expires.isAfter(Instant.now().plusSeconds(29L * 86400)) && expires.isBefore(Instant.now().plusSeconds(31L * 86400)));
        String page = body("/pay/" + token + "/payos/return");
        assertTrue(page.contains("Thanh toán thành công") && page.contains("79.000 ₫"), page);
    }

    @Test
    void consumableIsCreditedOnce() throws Exception {
        String token = startPayOs(createSession(BOOST5));
        stubInfo(orderCode(1), "PAID", 99000, 99000);
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isOk());
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isOk());
        assertEquals(5, billing.me(ALICE).wallet().get("BOOST"));
        assertEquals(1, purchases.count());
    }

    @Test
    void pendingAndProcessingGrantNothing() throws Exception {
        String token = startPayOs(createSession(MONTH));
        stubInfo(orderCode(1), "PENDING", 79000, 0);
        String pending = body("/pay/" + token + "/payos/return");
        assertTrue(pending.contains("Chưa nhận được tiền"), pending);
        assertTrue(pending.contains("/pay/" + token + "/payos/return"), pending);
        stubInfo(orderCode(1), "PROCESSING", 79000, 0);
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isOk());
        assertEquals("FREE", billing.me(ALICE).plan());
        assertEquals(0, purchases.count());
    }

    @Test
    void cancelledLinkAllowsTryingAgain() throws Exception {
        String token = startPayOs(createSession(MONTH));
        stubInfo(orderCode(1), "CANCELLED", 79000, 0);
        mvc.perform(get("/pay/" + token + "/payos/return?cancel=true&status=CANCELLED")).andExpect(status().isOk());
        assertEquals(CheckoutSession.Status.PENDING, session().getStatus());
        startPayOs(token);
        assertEquals(2, session().getPayosAttempts());
    }

    @Test
    void cancelUrlReturnsToTheCheckoutPage() throws Exception {
        String token = startPayOs(createSession(MONTH));
        String html = body("/pay/" + token + "/payos/cancel?code=00&cancel=true&status=CANCELLED");
        assertTrue(html.contains("Xác nhận thanh toán") && html.contains("VietQR"), html);
        assertEquals(CheckoutSession.Status.PENDING, session().getStatus());
        verify(payos, never()).getPaymentInfo(anyLong());
    }

    @Test
    void wrongAmountOrUnderpaymentGrantsNothing() throws Exception {
        String token = startPayOs(createSession(MONTH));
        stubInfo(orderCode(1), "PAID", 1000, 1000);
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isUnprocessableEntity());
        stubInfo(orderCode(1), "PAID", 79000, 50000);
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isUnprocessableEntity());
        when(payos.getPaymentInfo(orderCode(1)))
                .thenReturn(new PayOsClient.PaymentInfo(orderCode(1) + 1, "PAID", 79000, 79000));
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isUnprocessableEntity());
        assertEquals(0, purchases.count());
        assertEquals("FREE", billing.me(ALICE).plan());
    }

    @Test
    void payOsOutageOnReturnIsRetryable() throws Exception {
        String token = startPayOs(createSession(MONTH));
        when(payos.getPaymentInfo(orderCode(1))).thenThrow(new StoreException(StoreException.Kind.UNAVAILABLE, "down"));
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isServiceUnavailable());
        assertEquals(0, purchases.count());
        stubInfo(orderCode(1), "PAID", 79000, 79000);
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isOk());
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void returnBeforeStartingShowsTheCheckoutAgain() throws Exception {
        String token = createSession(MONTH);
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isOk());
        verify(payos, never()).getPaymentInfo(anyLong());
    }

    @Test
    void orderCodeOnTheUrlOfAnotherSessionIsNotTrusted() throws Exception {
        String tokenA = startPayOs(createSession(MONTH));
        long codeA = orderCode(1);
        String tokenB = startPayOs(createSession(MONTH));
        long idB = sessions.findAll().stream().filter(s -> s.getId() != session().getId()).findFirst().orElseThrow().getId();
        long codeB = payosProps.getOrderCodeBase() + idB * 100 + 1;
        stubInfo(codeA, "PAID", 79000, 79000);
        stubInfo(codeB, "PENDING", 79000, 0);
        // B quay về nhưng khai orderCode của A (đã trả): không được lấy quyền lợi cho B.
        mvc.perform(get("/pay/" + tokenB + "/payos/return?orderCode=" + codeA)).andExpect(status().isOk());
        verify(payos, never()).getPaymentInfo(codeA);
        assertEquals("FREE", billing.me(ALICE).plan());
        assertEquals(0, purchases.count());
        assertTrue(tokenA != null);
    }

    @Test
    void expiredSessionCannotBeCompletedFromTheBrowser() throws Exception {
        String token = startPayOs(createSession(MONTH));
        CheckoutSession s = session();
        s.setExpiresAt(Instant.now().minusSeconds(5));
        sessions.save(s);
        mvc.perform(get("/pay/" + token + "/payos/return")).andExpect(status().isGone());
        mvc.perform(post("/pay/" + token + "/payos")).andExpect(status().isGone());
        verify(payos, never()).getPaymentInfo(anyLong());
    }

    // ---------------- webhook ----------------

    @Test
    void webhookWithPaidOrderGrantsEvenIfTheUserNeverReturnedAndTheLinkExpired() throws Exception {
        String token = startPayOs(createSession(MONTH));
        CheckoutSession s = session();
        s.setExpiresAt(Instant.now().minusSeconds(60));
        sessions.save(s);
        stubInfo(orderCode(1), "PAID", 79000, 79000);

        webhook(orderCode(1), true, "00").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));

        assertEquals("PRO", billing.me(ALICE).plan());
        // payOS gửi lại: không cấp lần hai
        webhook(orderCode(1), true, "00").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
        assertEquals(1, purchases.count());
        // Phiên đã hoàn tất thì trang quay về luôn báo thành công, kể cả khi link đã hết hạn.
        String page = body("/pay/" + token + "/payos/return");
        assertTrue(page.contains("Thanh toán thành công"), page);
    }

    @Test
    void webhookForAnEarlierAttemptStillFindsTheSession() throws Exception {
        String token = startPayOs(createSession(MONTH));
        startPayOs(token); // lần 2: người dùng quét QR của lần 1 mới trả tiền
        stubInfo(orderCode(1), "PAID", 79000, 79000);
        webhook(orderCode(1), true, "00").andExpect(jsonPath("$.status").value("processed"));
        assertEquals("PRO", billing.me(ALICE).plan());
        assertEquals(1, purchases.count());
    }

    @Test
    void webhookWithBadSignatureIsRejected() throws Exception {
        startPayOs(createSession(MONTH));
        when(payos.verifyWebhookSignature(any(), anyString())).thenReturn(false);
        webhook(orderCode(1), true, "00").andExpect(status().isUnauthorized());
        verify(payos, never()).getPaymentInfo(anyLong());
        assertEquals("FREE", billing.me(ALICE).plan());
    }

    @Test
    void webhookWithoutDataIsRejected() throws Exception {
        mvc.perform(post("/webhooks/payos").contentType(MediaType.APPLICATION_JSON).content("{\"success\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void failedPaymentWebhookIsIgnored() throws Exception {
        startPayOs(createSession(MONTH));
        webhook(orderCode(1), false, "01").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
        verify(payos, never()).getPaymentInfo(anyLong());
    }

    @Test
    void confirmWebhookSampleAndUnknownOrdersAreIgnoredWith200() throws Exception {
        // payOS gửi dữ liệu mẫu (orderCode 123) khi đăng ký webhook; phải trả 2xx thì mới đăng ký được.
        webhook(123, true, "00").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
        webhook(payosProps.getOrderCodeBase() + 99999 * 100 + 1, true, "00").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));
        verify(payos, never()).getPaymentInfo(anyLong());
    }

    @Test
    void webhookIsNotTrustedForTheAmount() throws Exception {
        startPayOs(createSession(MONTH));
        // Webhook nói 79000 nhưng payOS (nguồn thật) báo đơn chỉ trả 1000: không cấp.
        stubInfo(orderCode(1), "PAID", 79000, 1000);
        webhook(orderCode(1), true, "00").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));
        assertEquals("FREE", billing.me(ALICE).plan());
        assertEquals(0, purchases.count());
    }

    @Test
    void payOsOutageDuringWebhookAsksForRetry() throws Exception {
        startPayOs(createSession(MONTH));
        when(payos.getPaymentInfo(orderCode(1))).thenThrow(new StoreException(StoreException.Kind.UNAVAILABLE, "down"));
        webhook(orderCode(1), true, "00").andExpect(status().isServiceUnavailable());
        stubInfo(orderCode(1), "PAID", 79000, 79000);
        webhook(orderCode(1), true, "00").andExpect(status().isOk());
        assertEquals("PRO", billing.me(ALICE).plan());
    }

    @Test
    void webhookPendingStatusIsIgnoredWithoutGranting() throws Exception {
        startPayOs(createSession(MONTH));
        stubInfo(orderCode(1), "PENDING", 79000, 0);
        webhook(orderCode(1), true, "00").andExpect(jsonPath("$.status").value("ignored"));
        assertEquals("FREE", billing.me(ALICE).plan());
    }
}
