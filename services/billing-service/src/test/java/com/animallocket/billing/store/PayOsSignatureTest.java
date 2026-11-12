package com.animallocket.billing.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayOsSignatureTest {
    static final ObjectMapper JSON = new ObjectMapper();

    /** Bộ dữ liệu mẫu CHÍNH THỨC của payOS (https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/). */
    static final String OFFICIAL_KEY = "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";
    static final String OFFICIAL_SIGNATURE = "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03";
    static final String OFFICIAL_DATA = """
            {"orderCode":123,"amount":3000,"description":"VQRIO123","accountNumber":"12345678","reference":"TF230204212323",
             "transactionDateTime":"2023-02-04 18:25:00","currency":"VND","paymentLinkId":"124c33293c43417ab7879e14c8d9eb18",
             "code":"00","desc":"Thành công","counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"",
             "counterAccountNumber":"","virtualAccountName":"","virtualAccountNumber":""}
            """;

    @Test
    void matchesPayOsOfficialTestVector() throws Exception {
        JsonNode data = JSON.readTree(OFFICIAL_DATA);
        assertEquals(OFFICIAL_SIGNATURE, PayOsSignature.forData(OFFICIAL_KEY, data));
    }

    @Test
    void fieldOrderInTheJsonDoesNotMatter() throws Exception {
        JsonNode reordered = JSON.readTree("""
                {"virtualAccountNumber":"","code":"00","orderCode":123,"amount":3000,"currency":"VND","desc":"Thành công",
                 "description":"VQRIO123","accountNumber":"12345678","reference":"TF230204212323",
                 "transactionDateTime":"2023-02-04 18:25:00","paymentLinkId":"124c33293c43417ab7879e14c8d9eb18",
                 "counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"","counterAccountNumber":"",
                 "virtualAccountName":""}""");
        assertEquals(OFFICIAL_SIGNATURE, PayOsSignature.forData(OFFICIAL_KEY, reordered));
    }

    @Test
    void nullBecomesEmptyString() throws Exception {
        JsonNode withNull = JSON.readTree("{\"a\":\"x\",\"b\":null}");
        JsonNode withEmpty = JSON.readTree("{\"a\":\"x\",\"b\":\"\"}");
        assertEquals(PayOsSignature.forData("k", withEmpty), PayOsSignature.forData("k", withNull));
    }

    @Test
    void anyTamperingChangesTheSignature() throws Exception {
        JsonNode tampered = JSON.readTree(OFFICIAL_DATA.replace("3000", "30000"));
        assertFalse(PayOsSignature.matches(OFFICIAL_SIGNATURE, PayOsSignature.forData(OFFICIAL_KEY, tampered)));
        assertFalse(PayOsSignature.matches(OFFICIAL_SIGNATURE,
                PayOsSignature.forData("another-key", JSON.readTree(OFFICIAL_DATA))));
    }

    @Test
    void matchesIgnoresCaseAndSurroundingSpacesButNotContent() {
        assertTrue(PayOsSignature.matches("abcdef", " ABCDEF "));
        assertFalse(PayOsSignature.matches("abcdef", "abcdee"));
        assertFalse(PayOsSignature.matches("abcdef", null));
        assertFalse(PayOsSignature.matches(null, "abcdef"));
    }

    @Test
    void paymentRequestSignatureUsesAlphabeticalFieldsWithoutUrlEncoding() {
        String expected = PayOsSignature.hmac("secret",
                "amount=79000&cancelUrl=http://x/c?a=b&c=d&description=AL12&orderCode=100000000101&returnUrl=http://x/r");
        assertEquals(expected, PayOsSignature.forPaymentRequest("secret", 79000, "http://x/c?a=b&c=d", "AL12",
                100000000101L, "http://x/r"));
    }
}
