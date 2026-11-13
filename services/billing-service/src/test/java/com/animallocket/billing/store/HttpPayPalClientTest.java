package com.animallocket.billing.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Chạy với HTTP giả: định dạng JSON thật của PayPal có thể khác chi tiết, xem PAYPAL.md (chưa chạy với sandbox thật). */
class HttpPayPalClientTest {
    static final String BASE = "https://api-m.sandbox.paypal.com";
    /** Giá trị giả chỉ dùng trong test; header Basic được tính lại để không có chuỗi base64 cố định trong mã. */
    static final String CLIENT_ID = "cid";
    static final String CLIENT_SECRET = "sec";
    static final String TOKEN_JSON = "{\"access_token\":\"A21.test\",\"token_type\":\"Bearer\",\"expires_in\":32400}";

    MockRestServiceServer server;
    HttpPayPalClient client;

    @BeforeEach
    void setUp() {
        PayPalProperties props = new PayPalProperties();
        props.setClientId(CLIENT_ID);
        props.setClientSecret(CLIENT_SECRET);
        RestClient.Builder b = RestClient.builder();
        server = MockRestServiceServer.bindTo(b).build();
        client = new HttpPayPalClient(props, b.build(),
                new StoreCaller(2, Duration.ofMillis(1), 50, 50, Duration.ofSeconds(30)), Clock.systemUTC());
    }

    private void expectToken() {
        server.expect(once(), requestTo(BASE + "/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8))))
                .andExpect(content().string("grant_type=client_credentials"))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
    }

    private static final String CAPTURED = """
            {"id":"ORD1","status":"COMPLETED","purchase_units":[{"custom_id":"42","payments":{"captures":[
              {"id":"CAP1","status":"COMPLETED","custom_id":"42","amount":{"currency_code":"USD","value":"2.99"},
               "create_time":"2026-07-01T10:20:30Z"}]}}]}
            """;

    @Test
    void createsOrderAndFindsPayerActionLink() throws Exception {
        expectToken();
        server.expect(once(), requestTo(BASE + "/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer A21.test"))
                .andExpect(header("PayPal-Request-Id", "req-1"))
                .andExpect(jsonPath("$.intent").value("CAPTURE"))
                .andExpect(jsonPath("$.purchase_units[0].amount.value").value("2.99"))
                .andExpect(jsonPath("$.purchase_units[0].amount.currency_code").value("USD"))
                .andExpect(jsonPath("$.purchase_units[0].custom_id").value("42"))
                .andExpect(jsonPath("$.payment_source.paypal.experience_context.return_url").value("https://x/pay/t/return"))
                .andRespond(withSuccess("""
                        {"id":"ORD1","status":"PAYER_ACTION_REQUIRED","links":[
                          {"href":"https://api-m.sandbox.paypal.com/v2/checkout/orders/ORD1","rel":"self","method":"GET"},
                          {"href":"https://www.sandbox.paypal.com/checkoutnow?token=ORD1","rel":"payer-action","method":"GET"}]}
                        """, MediaType.APPLICATION_JSON));
        var order = client.createOrder("req-1", "42", "Animal Locket - pro", "2.99", "USD",
                "https://x/pay/t/return", "https://x/pay/t/cancel");
        assertEquals("ORD1", order.orderId());
        assertEquals("https://www.sandbox.paypal.com/checkoutnow?token=ORD1", order.approveUrl());
        server.verify();
    }

    @Test
    void orderWithoutApproveLinkIsUnavailable() {
        expectToken();
        server.expect(once(), requestTo(BASE + "/v2/checkout/orders"))
                .andRespond(withSuccess("{\"id\":\"ORD1\",\"links\":[]}", MediaType.APPLICATION_JSON));
        var e = assertThrows(StoreException.class, () -> client.createOrder("r", "42", "d", "2.99", "USD", "u", "c"));
        assertEquals(StoreException.Kind.UNAVAILABLE, e.kind());
    }

    @Test
    void parsesCapture() throws Exception {
        expectToken();
        server.expect(once(), requestTo(BASE + "/v2/checkout/orders/ORD1/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "cap-1"))
                .andRespond(withSuccess(CAPTURED, MediaType.APPLICATION_JSON));
        var cap = client.capture("ORD1", "cap-1");
        assertEquals("ORD1", cap.orderId());
        assertEquals("COMPLETED", cap.orderStatus());
        assertEquals("CAP1", cap.captureId());
        assertEquals("COMPLETED", cap.captureStatus());
        assertEquals("2.99", cap.amountValue());
        assertEquals("USD", cap.currency());
        assertEquals("42", cap.customId());
        assertEquals(Instant.parse("2026-07-01T10:20:30Z"), cap.createdAt());
        server.verify();
    }

    @Test
    void alreadyCapturedFallsBackToReadingTheOrder() throws Exception {
        expectToken();
        server.expect(once(), requestTo(BASE + "/v2/checkout/orders/ORD1/capture"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"UNPROCESSABLE_ENTITY\",\"details\":[{\"issue\":\"ORDER_ALREADY_CAPTURED\"}]}"));
        server.expect(once(), requestTo(BASE + "/v2/checkout/orders/ORD1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(CAPTURED, MediaType.APPLICATION_JSON));
        var cap = client.capture("ORD1", "cap-1");
        assertEquals("CAP1", cap.captureId());
        server.verify();
    }

    @Test
    void otherRejectionIsInvalidAndNotRetried() {
        expectToken();
        server.expect(once(), requestTo(BASE + "/v2/checkout/orders/ORD1/capture"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"UNPROCESSABLE_ENTITY\",\"details\":[{\"issue\":\"ORDER_NOT_APPROVED\"}]}"));
        var e = assertThrows(StoreException.class, () -> client.capture("ORD1", "cap-1"));
        assertEquals(StoreException.Kind.INVALID, e.kind());
        assertTrue(e.getMessage().contains("ORDER_NOT_APPROVED"));
        server.verify(); // chỉ một lần gọi capture: INVALID không retry
    }

    @Test
    void serverErrorIsRetriedThenSucceeds() throws Exception {
        expectToken(); // access token được cache nên chỉ lấy một lần
        server.expect(once(), requestTo(BASE + "/v2/checkout/orders/ORD1/capture")).andRespond(withServerError());
        server.expect(once(), requestTo(BASE + "/v2/checkout/orders/ORD1/capture"))
                .andRespond(withSuccess(CAPTURED, MediaType.APPLICATION_JSON));
        assertEquals("CAP1", client.capture("ORD1", "cap-1").captureId());
        server.verify();
    }

    @Test
    void badCredentialsFailClosedAsUnavailable() {
        server.expect(manyTimes(), requestTo(BASE + "/v1/oauth2/token")) // StoreCaller thử lại nên có thể gọi nhiều lần
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));
        var e = assertThrows(StoreException.class, () -> client.capture("ORD1", "cap-1"));
        assertEquals(StoreException.Kind.UNAVAILABLE, e.kind());
    }

    @Test
    void unknownModeIsRejected() {
        PayPalProperties p = new PayPalProperties();
        p.setMode("production");
        assertThrows(IllegalStateException.class, p::apiBaseUrl);
        p.setMode("LIVE");
        assertEquals("https://api-m.paypal.com", p.apiBaseUrl());
    }
}
