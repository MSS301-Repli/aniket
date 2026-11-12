package com.animallocket.billing.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Chạy với HTTP giả theo tài liệu payOS; chưa chạy với payOS thật (xem PAYOS.md). */
class HttpPayOsClientTest {
    static final String BASE = "https://api-merchant.payos.vn";
    static final String KEY = "test-checksum-key";

    MockRestServiceServer server;
    HttpPayOsClient client;

    @BeforeEach
    void setUp() {
        PayOsProperties props = new PayOsProperties();
        props.setClientId("cid");
        props.setApiKey("akey");
        props.setChecksumKey(KEY);
        RestClient.Builder b = RestClient.builder();
        server = MockRestServiceServer.bindTo(b).build();
        client = new HttpPayOsClient(props, b.build(), new StoreCaller(2, Duration.ofMillis(1), 50, 50, Duration.ofSeconds(30)));
    }

    @Test
    void createsSignedPaymentLink() throws Exception {
        String sig = PayOsSignature.forPaymentRequest(KEY, 79000, "https://x/pay/t/payos/cancel", "AL12", 100000000101L,
                "https://x/pay/t/payos/return");
        server.expect(once(), requestTo(BASE + "/v2/payment-requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-client-id", "cid"))
                .andExpect(header("x-api-key", "akey"))
                .andExpect(jsonPath("$.orderCode").value(100000000101L))
                .andExpect(jsonPath("$.amount").value(79000))
                .andExpect(jsonPath("$.description").value("AL12"))
                .andExpect(jsonPath("$.returnUrl").value("https://x/pay/t/payos/return"))
                .andExpect(jsonPath("$.cancelUrl").value("https://x/pay/t/payos/cancel"))
                .andExpect(jsonPath("$.items[0].price").value(79000))
                .andExpect(jsonPath("$.signature").value(sig))
                .andRespond(withSuccess("""
                        {"code":"00","desc":"success","data":{"bin":"970422","accountNumber":"123","accountName":"X",
                         "amount":79000,"description":"AL12","orderCode":100000000101,"currency":"VND",
                         "paymentLinkId":"124c33293c934a85be5b7f8761a27a07","status":"PENDING",
                         "checkoutUrl":"https://pay.payos.vn/web/124c33293c934a85be5b7f8761a27a07","qrCode":"000201..."},
                         "signature":"abc"}
                        """, MediaType.APPLICATION_JSON));
        var link = client.createPaymentLink(100000000101L, 79000, "AL12", "Pro", "https://x/pay/t/payos/return",
                "https://x/pay/t/payos/cancel");
        assertEquals(100000000101L, link.orderCode());
        assertEquals("124c33293c934a85be5b7f8761a27a07", link.paymentLinkId());
        assertEquals("https://pay.payos.vn/web/124c33293c934a85be5b7f8761a27a07", link.checkoutUrl());
        server.verify();
    }

    @Test
    void nonOkCodeIsInvalidAndNotRetried() {
        server.expect(once(), requestTo(BASE + "/v2/payment-requests"))
                .andRespond(withSuccess("{\"code\":\"231\",\"desc\":\"Đơn thanh toán đã tồn tại\",\"data\":null}",
                        MediaType.APPLICATION_JSON));
        var e = assertThrows(StoreException.class, () -> client.createPaymentLink(1, 1000, "d", "n", "r", "c"));
        assertEquals(StoreException.Kind.INVALID, e.kind());
        server.verify();
    }

    @Test
    void linkWithoutCheckoutUrlIsUnavailable() {
        server.expect(requestTo(BASE + "/v2/payment-requests")).andRespond(withSuccess(
                "{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"paymentLinkId\":\"x\"}}", MediaType.APPLICATION_JSON));
        var e = assertThrows(StoreException.class, () -> client.createPaymentLink(1, 1000, "d", "n", "r", "c"));
        assertEquals(StoreException.Kind.UNAVAILABLE, e.kind());
    }

    @Test
    void readsPaymentInfo() throws Exception {
        server.expect(once(), requestTo(BASE + "/v2/payment-requests/100000000101"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-client-id", "cid"))
                .andExpect(header("x-api-key", "akey"))
                .andRespond(withSuccess("""
                        {"code":"00","desc":"success","data":{"id":"124c","orderCode":100000000101,"amount":79000,
                         "amountPaid":79000,"amountRemaining":0,"status":"PAID","createdAt":"2026-11-07T10:30:00.000Z",
                         "transactions":[]},"signature":"abc"}
                        """, MediaType.APPLICATION_JSON));
        var info = client.getPaymentInfo(100000000101L);
        assertEquals(100000000101L, info.orderCode());
        assertEquals("PAID", info.status());
        assertEquals(79000, info.amount());
        assertEquals(79000, info.amountPaid());
        server.verify();
    }

    @Test
    void serverErrorIsRetriedThenSucceeds() throws Exception {
        server.expect(once(), requestTo(BASE + "/v2/payment-requests/5")).andRespond(withServerError());
        server.expect(once(), requestTo(BASE + "/v2/payment-requests/5")).andRespond(withSuccess(
                "{\"code\":\"00\",\"desc\":\"success\",\"data\":{\"orderCode\":5,\"amount\":1,\"amountPaid\":0,\"status\":\"PENDING\"}}",
                MediaType.APPLICATION_JSON));
        assertEquals("PENDING", client.getPaymentInfo(5).status());
        server.verify();
    }

    @Test
    void badApiKeyFailsClosedAsUnavailable() {
        server.expect(manyTimes(), requestTo(BASE + "/v2/payment-requests/5")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        var e = assertThrows(StoreException.class, () -> client.getPaymentInfo(5));
        assertEquals(StoreException.Kind.UNAVAILABLE, e.kind());
    }

    @Test
    void unknownOrderIsInvalid() {
        server.expect(once(), requestTo(BASE + "/v2/payment-requests/5")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        var e = assertThrows(StoreException.class, () -> client.getPaymentInfo(5));
        assertEquals(StoreException.Kind.INVALID, e.kind());
    }

    @Test
    void verifiesWebhookSignatureWithTheChecksumKey() throws Exception {
        var data = new ObjectMapper().readTree(PayOsSignatureTest.OFFICIAL_DATA);
        String good = PayOsSignature.forData(KEY, data);
        assertTrue(client.verifyWebhookSignature(data, good));
        assertFalse(client.verifyWebhookSignature(data, PayOsSignatureTest.OFFICIAL_SIGNATURE)); // ký bằng khoá khác
        assertFalse(client.verifyWebhookSignature(data, ""));
        assertFalse(client.verifyWebhookSignature(data, null));
        assertFalse(client.verifyWebhookSignature(null, good));
    }
}
