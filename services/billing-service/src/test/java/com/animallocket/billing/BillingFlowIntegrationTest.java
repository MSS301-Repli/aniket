package com.animallocket.billing;

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
import com.animallocket.billing.service.BillingMaintenance;
import com.animallocket.billing.service.BillingService;
import com.animallocket.common.EventTypes;
import com.animallocket.common.Headers;
import com.animallocket.common.event.EventJson;
import com.animallocket.common.event.Events;
import com.animallocket.outbox.OutboxEvent;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BillingFlowIntegrationTest {
    static final String ALICE = "aaaaaaaaaaaaaaaaaaaa";
    static final String BOB = "bbbbbbbbbbbbbbbbbbbb";
    static final String MONTH = "animallocket.pro.month";
    static final String BOOST5 = "animallocket.boost.5";

    @Autowired MockMvc mvc;
    @Autowired BillingService billing;
    @Autowired BillingMaintenance maintenance;
    @Autowired ProductRepository products;
    @Autowired PurchaseRepository purchases;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired WalletBalanceRepository balances;
    @Autowired LedgerRepository ledger;
    @Autowired WebhookEventRepository webhooks;
    @Autowired OutboxRepository outbox;
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
        products.save(new Product("animallocket.pack.safari", Product.Type.PACK, "SAFARI", 1, true));
        products.save(new Product("animallocket.old", Product.Type.CONSUMABLE, "BOOST", 1, false));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String me) {
        return b.header(Headers.PUBLIC_ID, me).with(jwt()).contentType(MediaType.APPLICATION_JSON);
    }

    private String verifyBody(String store, String sku, String token) {
        return "{\"store\":\"" + store + "\",\"sku\":\"" + sku + "\",\"token\":\"" + token + "\"}";
    }

    private String devSub(String tx, String orig, Instant exp) {
        return "dev:" + tx + ":" + orig + ":" + exp.getEpochSecond();
    }

    private List<OutboxEvent> events(String type) {
        return outbox.findAll().stream().filter(e -> e.getType().equals(type)).toList();
    }

    @Test
    void catalogListsOnlyActiveProducts() throws Exception {
        mvc.perform(as(get("/api/v1/billing/products"), ALICE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void subscriptionPurchaseGrantsProAndEmitsEventOnce() throws Exception {
        Instant exp = Instant.now().plus(Duration.ofDays(30));
        String body = verifyBody("DEV", MONTH, devSub("t1", "o1", exp));

        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("PRO"));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(body)) // gửi lại (khôi phục giao dịch)
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("PRO"));

        assertEquals(1, purchases.count());
        assertEquals(1, subscriptions.count());
        List<OutboxEvent> activated = events(EventTypes.SUBSCRIPTION_ACTIVATED);
        assertEquals(1, activated.size(), "gửi lại cùng giao dịch không được phát thêm event");
        var p = EventJson.read(activated.get(0).getPayload()).payloadAs(Events.SubscriptionChanged.class);
        assertEquals(ALICE, p.publicId());
        assertEquals("PRO", p.plan());
        mvc.perform(as(get("/api/v1/billing/me"), ALICE)).andExpect(jsonPath("$.plan").value("PRO"));
        mvc.perform(as(get("/api/v1/billing/me"), BOB)).andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    void sameReceiptCannotBeClaimedByAnotherAccount() throws Exception {
        String body = verifyBody("DEV", BOOST5, "dev:tx-shared");
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(body)).andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), BOB).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECEIPT_ALREADY_USED"));
        mvc.perform(as(get("/api/v1/billing/me"), BOB)).andExpect(jsonPath("$.wallet.BOOST").doesNotExist());
    }

    @Test
    void sameSubscriptionChainCannotBeSharedAcrossAccounts() throws Exception {
        Instant exp = Instant.now().plus(Duration.ofDays(30));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", MONTH, devSub("t1", "orig", exp))))
                .andExpect(status().isOk());
        // Bob gửi giao dịch gia hạn khác nhưng cùng chuỗi originalTransactionId của Alice.
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), BOB).content(verifyBody("DEV", MONTH, devSub("t2", "orig", exp))))
                .andExpect(status().isConflict());
        mvc.perform(as(get("/api/v1/billing/me"), BOB)).andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    void consumablePurchaseCreditsWalletAndLedger() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", BOOST5, "dev:tx-b1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.wallet.BOOST").value(5));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", BOOST5, "dev:tx-b1")))
                .andExpect(jsonPath("$.wallet.BOOST").value(5)); // không cộng lần hai
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", BOOST5, "dev:tx-b2")))
                .andExpect(jsonPath("$.wallet.BOOST").value(10));
        assertEquals(2, ledger.findByPublicIdOrderByIdDesc(ALICE).size());
        assertEquals(2, events(EventTypes.CONSUMABLE_GRANTED).size());
    }

    /** docs/19 §8: một lượt quay avatar cho MỖI kỳ thuê bao, không phải mỗi lần phát SUBSCRIPTION_ACTIVATED. */
    @Test
    void subscriptionGrantsOneAvatarSpinPerPeriod() throws Exception {
        Instant exp = Instant.now().plus(Duration.ofDays(30));
        String body = verifyBody("DEV", MONTH, devSub("t1", "o1", exp));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(body)).andExpect(status().isOk());

        assertEquals(1, spinGrants().size(), "mua mới: đúng một lượt");

        // Khôi phục giao dịch (client gửi lại cùng biên lai) không được cấp thêm.
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(body)).andExpect(status().isOk());
        assertEquals(1, spinGrants().size(), "gửi lại cùng biên lai không cấp thêm lượt");

        // Store gửi lại webhook của ĐÚNG kỳ đó. Biên lai DEV chỉ có độ phân giải giây còn exp ở đây có cả mili,
        // nên đây cũng là phép thử cho việc khoá sổ cái chốt theo giây.
        assertTrue(billing.onSubscriptionUpdated(Store.DEV, "o1", exp));
        assertEquals(1, spinGrants().size(), "webhook lặp lại của cùng kỳ không cấp thêm lượt");

        // Gia hạn thật: hạn mới → kỳ mới → thêm một lượt.
        assertTrue(billing.onSubscriptionUpdated(Store.DEV, "o1", exp.plus(Duration.ofDays(30))));
        assertEquals(2, spinGrants().size(), "mỗi kỳ gia hạn là một lượt");

        // Số dư ví KHÔNG đổi: lượt quay do user-service giữ, billing chỉ ghi sổ cái.
        assertTrue(balances.findByPublicId(ALICE).isEmpty(), "AVATAR_SPIN không được cộng vào ví của billing");
        assertEquals(2, ledger.findByPublicIdOrderByIdDesc(ALICE).size());
        var payload = EventJson.read(spinGrants().get(0).getPayload()).payloadAs(Events.ConsumableGranted.class);
        assertEquals("AVATAR_SPIN", payload.item());
        assertEquals(1, payload.quantity());
        assertEquals(ALICE, payload.publicId());
    }

    @Test
    void expiredSubscriptionGrantsNoAvatarSpin() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE)
                        .content(verifyBody("DEV", MONTH, devSub("t-old", "o-old", Instant.now().minusSeconds(60)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("FREE"));
        assertTrue(spinGrants().isEmpty(), "kỳ đã hết hạn lúc xác thực thì không tặng lượt quay");
    }

    @Test
    void consumableAndPackPurchasesGrantNoAvatarSpin() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", BOOST5, "dev:tx-b")))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE)
                .content(verifyBody("DEV", "animallocket.pack.safari", "dev:tx-p"))).andExpect(status().isOk());
        assertTrue(spinGrants().isEmpty(), "lượt quay chỉ đi kèm thuê bao");
    }

    /** Các event CONSUMABLE_GRANTED mang vật phẩm AVATAR_SPIN. */
    private List<OutboxEvent> spinGrants() {
        return events(EventTypes.CONSUMABLE_GRANTED).stream()
                .filter(e -> "AVATAR_SPIN".equals(
                        EventJson.read(e.getPayload()).payloadAs(Events.ConsumableGranted.class).item()))
                .toList();
    }

    @Test
    void packPurchaseEmitsAnimalPackGrant() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE)
                .content(verifyBody("DEV", "animallocket.pack.safari", "dev:tx-pack"))).andExpect(status().isOk());
        var e = events(EventTypes.CONSUMABLE_GRANTED).get(0);
        assertEquals("ANIMAL_PACK:SAFARI", EventJson.read(e.getPayload()).payloadAs(Events.ConsumableGranted.class).item());
    }

    @Test
    void consumeIsAtomicAndNeverGoesNegative() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", BOOST5, "dev:tx-c")));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            fs.add(pool.submit(() -> {
                try {
                    billing.consume(ALICE, "BOOST");
                    ok.incrementAndGet();
                } catch (com.animallocket.common.web.ApiException e) {
                    assertEquals("INSUFFICIENT_BALANCE", e.code());
                }
            }));
        }
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();
        assertEquals(5, ok.get(), "chỉ đúng 5 lần trừ thành công dù 12 request đồng thời");
        assertEquals(0, balances.findByPublicId(ALICE).get(0).getBalance());
    }

    @Test
    void internalConsumeEndpoint() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", BOOST5, "dev:tx-i")));
        String req = "{\"publicId\":\"" + ALICE + "\",\"item\":\"BOOST\"}";
        mvc.perform(post("/internal/wallet/consume").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content(req)).andExpect(status().isNoContent());
        mvc.perform(post("/internal/wallet/consume").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/wallet/consume").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"publicId\":\"" + BOB + "\",\"item\":\"BOOST\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
        mvc.perform(post("/internal/wallet/consume").header(Headers.INTERNAL_TOKEN, "dev-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"publicId\":\"" + ALICE + "\",\"item\":\"PRO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidAndUnverifiableReceiptsGrantNothing() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", MONTH, "garbage")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RECEIPT"));
        // Apple/Google chưa cấu hình ở môi trường test: fail closed bằng 503, không cấp quyền.
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("APPLE", MONTH, "12345")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("STORE_UNAVAILABLE"));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", "no.such.sku", "dev:x")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", "animallocket.old", "dev:x")))
                .andExpect(status().isBadRequest());
        assertEquals(0, purchases.count());
        assertTrue(outbox.findAll().isEmpty());
    }

    @Test
    void expiredReceiptDoesNotGrantPro() throws Exception {
        String body = verifyBody("DEV", MONTH, devSub("t-old", "o-old", Instant.now().minusSeconds(60)));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("FREE"));
        assertTrue(events(EventTypes.SUBSCRIPTION_ACTIVATED).isEmpty());
    }

    @Test
    void renewalExtendsAndEndingRevokesPro() throws Exception {
        Instant exp = Instant.now().plus(Duration.ofDays(1));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", MONTH, devSub("t1", "o1", exp))));

        Instant newExp = Instant.now().plus(Duration.ofDays(31));
        assertTrue(billing.onSubscriptionUpdated(Store.DEV, "o1", newExp));
        Subscription s = subscriptions.findByStoreAndOriginalTransactionId(Store.DEV, "o1").orElseThrow();
        assertTrue(s.getExpiresAt().isAfter(exp.plus(Duration.ofDays(20))));
        assertFalse(billing.onSubscriptionUpdated(Store.DEV, "unknown", newExp), "không có thuê bao khớp thì bỏ qua");

        assertTrue(billing.onSubscriptionEnded(Store.DEV, "o1"));
        mvc.perform(as(get("/api/v1/billing/me"), ALICE)).andExpect(jsonPath("$.plan").value("FREE"));
        assertEquals(1, events(EventTypes.SUBSCRIPTION_EXPIRED).size());
    }

    @Test
    void endingOneOfTwoSubscriptionsKeepsPro() throws Exception {
        Instant exp = Instant.now().plus(Duration.ofDays(10));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", MONTH, devSub("a1", "oa", exp))));
        // thuê bao thứ hai từ một chuỗi khác (mô phỏng store khác) – dùng cùng DEV nhưng original khác
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", MONTH, devSub("b1", "ob", exp.plus(Duration.ofDays(5))))));

        billing.onSubscriptionEnded(Store.DEV, "oa");
        mvc.perform(as(get("/api/v1/billing/me"), ALICE)).andExpect(jsonPath("$.plan").value("PRO"));
        assertTrue(events(EventTypes.SUBSCRIPTION_EXPIRED).isEmpty(), "vẫn còn thuê bao khác nên không được phát EXPIRED");
    }

    @Test
    void refundRevokesConsumablesUpToRemainingBalance() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", BOOST5, "dev:tx-r")));
        billing.consume(ALICE, "BOOST");
        billing.consume(ALICE, "BOOST");   // còn 3, đã dùng 2

        assertTrue(billing.onRefund(Store.DEV, "tx-r"));
        assertEquals(0, balances.findByPublicId(ALICE).get(0).getBalance(), "thu hồi phần còn lại, phần đã dùng không lấy lại được");
        assertEquals(Purchase.Status.REFUNDED, purchases.findByStoreAndStoreTransactionId(Store.DEV, "tx-r").orElseThrow().getStatus());
        assertTrue(billing.onRefund(Store.DEV, "tx-r"), "hoàn tiền lặp lại vẫn an toàn");
        assertFalse(billing.onRefund(Store.DEV, "never-existed"));
    }

    @Test
    void refundOfSubscriptionEndsPro() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE)
                .content(verifyBody("DEV", MONTH, devSub("t9", "o9", Instant.now().plus(Duration.ofDays(30))))));
        assertTrue(billing.onRefund(Store.DEV, "t9"));
        mvc.perform(as(get("/api/v1/billing/me"), ALICE)).andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    void lapsedSubscriptionsAreDowngradedAfterGracePeriod() throws Exception {
        Instant exp = Instant.now().plusSeconds(1);
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", MONTH, devSub("t5", "o5", exp))));
        Thread.sleep(1200);
        assertEquals(0, billing.expireDue(Duration.ofHours(1)), "trong thời gian ân hạn thì giữ nguyên");
        assertEquals(1, billing.expireDue(Duration.ZERO));
        assertEquals(Subscription.Status.EXPIRED, subscriptions.findByStoreAndOriginalTransactionId(Store.DEV, "o5").orElseThrow().getStatus());
        assertEquals(1, events(EventTypes.SUBSCRIPTION_EXPIRED).size());
        maintenance.expire(); // không ném lỗi khi không còn gì để hạ cấp
    }

    @Test
    void webhookDeduplication() {
        assertTrue(billing.firstTimeWebhook("n-1", Store.DEV, "DID_RENEW"));
        assertFalse(billing.firstTimeWebhook("n-1", Store.DEV, "DID_RENEW"));
        assertTrue(billing.firstTimeWebhook("n-2", Store.DEV, "DID_RENEW"));
    }

    @Test
    void deletingAccountWipesWalletAndAnonymizesPurchases() throws Exception {
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE).content(verifyBody("DEV", BOOST5, "dev:tx-d")));
        mvc.perform(as(post("/api/v1/billing/purchases/verify"), ALICE)
                .content(verifyBody("DEV", MONTH, devSub("t-d", "o-d", Instant.now().plus(Duration.ofDays(5))))));

        billing.onUserDeleted(ALICE);

        assertTrue(balances.findByPublicId(ALICE).isEmpty());
        assertTrue(ledger.findByPublicIdOrderByIdDesc(ALICE).isEmpty());
        assertTrue(subscriptions.findByPublicId(ALICE).isEmpty());
        assertTrue(purchases.findAll().stream().allMatch(p -> p.getPublicId().equals("deleted")), "giữ bản ghi tài chính nhưng bỏ liên kết danh tính");
        assertEquals(2, purchases.count());
    }

    @Test
    void endpointsRequireJwtAndProfile() throws Exception {
        mvc.perform(get("/api/v1/billing/me").header(Headers.PUBLIC_ID, ALICE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/billing/me").with(jwt())).andExpect(status().isForbidden());
    }
}
