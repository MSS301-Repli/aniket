package com.animallocket.billing.store;

import com.animallocket.billing.domain.Store;
import com.animallocket.common.event.EventJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Xác thực giao dịch với App Store Server API: client chỉ gửi {@code transactionId}; server tự hỏi Apple
 * (kèm JWT ES256 ký bằng khoá API .p8), rồi kiểm tra chữ ký JWS của Apple trên kết quả.
 * Không tin bất kỳ trường nào do client khai.
 */
public class AppleStoreGateway implements StoreGateway {
    private static final Logger log = LoggerFactory.getLogger(AppleStoreGateway.class);
    /** transactionId của Apple là số; chặn mọi ký tự khác để không chèn được vào đường dẫn. */
    private static final Pattern TX_ID = Pattern.compile("^[0-9]{1,30}$");
    private static final String CALL = "store-apple";

    /** Giao dịch không tồn tại ở môi trường này. INVALID nên không bị retry và không tính vào ngắt mạch. */
    static final class TransactionNotFound extends StoreException {
        TransactionNotFound() {
            super(Kind.INVALID, "Apple không tìm thấy giao dịch");
        }
    }

    private final AppleProperties props;
    private final AppleJwsVerifier verifier;
    private final RestClient http;
    private final StoreCaller caller;
    private final Clock clock;
    private final PrivateKey apiKey;
    private final String keyProblem;

    public AppleStoreGateway(AppleProperties props, AppleJwsVerifier verifier, RestClient http,
                             StoreCaller caller, Clock clock) {
        this.props = props;
        this.verifier = verifier;
        this.http = http;
        this.caller = caller;
        this.clock = clock;
        PrivateKey key = null;
        String problem = null;
        try {
            key = loadPrivateKey(props.getPrivateKeyFile());
        } catch (Exception e) {
            problem = "Không đọc được khoá API của Apple: " + e.getClass().getSimpleName();
            log.error("{} ({}). Tích hợp Apple sẽ từ chối mọi giao dịch cho đến khi sửa cấu hình.", problem, props.getPrivateKeyFile());
        }
        this.apiKey = key;
        this.keyProblem = problem;
    }

    @Override
    public Store store() {
        return Store.APPLE;
    }

    @Override
    public VerifiedPurchase verify(String sku, String token) throws StoreException {
        if (token == null || !TX_ID.matcher(token).matches()) {
            throw new StoreException(StoreException.Kind.INVALID, "transactionId không hợp lệ");
        }
        if (apiKey == null) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, keyProblem);
        }
        String signed = fetchSignedTransaction(token);
        JsonNode tx = verifier.verify(signed);

        if (!props.getBundleId().equals(text(tx, "bundleId"))) {
            throw new StoreException(StoreException.Kind.INVALID, "bundleId không khớp ứng dụng");
        }
        if (!sku.equals(text(tx, "productId"))) {
            throw new StoreException(StoreException.Kind.INVALID, "productId không khớp sản phẩm yêu cầu");
        }
        String txId = text(tx, "transactionId");
        if (!token.equals(txId)) {
            throw new StoreException(StoreException.Kind.INVALID, "transactionId trong phản hồi không khớp");
        }
        if ("Sandbox".equalsIgnoreCase(text(tx, "environment")) && !props.isSandbox() && !props.isAllowSandboxFallback()) {
            throw new StoreException(StoreException.Kind.INVALID, "Giao dịch sandbox không được chấp nhận ở production");
        }
        String orig = text(tx, "originalTransactionId");
        return new VerifiedPurchase(txId, orig == null ? txId : orig, sku,
                millis(tx, "purchaseDate"), millis(tx, "expiresDate"), tx.hasNonNull("revocationDate"));
    }

    private String fetchSignedTransaction(String transactionId) throws StoreException {
        List<String> bases = props.isSandbox()
                ? List.of(props.getSandboxUrl())
                : props.isAllowSandboxFallback()
                ? List.of(props.getProductionUrl(), props.getSandboxUrl())
                : List.of(props.getProductionUrl());
        for (int i = 0; i < bases.size(); i++) {
            String base = bases.get(i);
            try {
                return caller.call(CALL, () -> getTransaction(base, transactionId));
            } catch (TransactionNotFound e) {
                if (i == bases.size() - 1) {
                    throw e;
                }
                log.info("Giao dịch {} không có ở {}, thử sandbox", transactionId, base);
            }
        }
        throw new StoreException(StoreException.Kind.INVALID, "Apple không tìm thấy giao dịch");
    }

    private String getTransaction(String base, String transactionId) throws StoreException {
        String jwt = apiJwt();
        try {
            JsonNode body = http.get().uri(base + "/inApps/v1/transactions/{id}", transactionId)
                    .header("Authorization", "Bearer " + jwt)
                    .retrieve().body(JsonNode.class);
            String signed = body == null ? null : text(body, "signedTransactionInfo");
            if (signed == null) {
                throw new StoreException(StoreException.Kind.INVALID, "Phản hồi của Apple thiếu signedTransactionInfo");
            }
            return signed;
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 404) {
                throw new TransactionNotFound();
            }
            if (code == 401 || code == 403) {
                log.error("Apple từ chối khoá API (HTTP {}): kiểm tra issuer-id/key-id/.p8", code);
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "Apple từ chối thông tin xác thực của server");
            }
            if (code == 429) {
                throw new StoreException(StoreException.Kind.UNAVAILABLE, "Apple giới hạn tốc độ");
            }
            throw new StoreException(StoreException.Kind.INVALID, "Apple từ chối yêu cầu (HTTP " + code + ")");
        } catch (HttpServerErrorException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Apple lỗi (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (ResourceAccessException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Không kết nối được tới Apple", e);
        } catch (RestClientException e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Phản hồi của Apple không đọc được", e);
        }
    }

    /** JWT ES256 gọi App Store Server API, sống 5 phút (Apple cho tối đa 60 phút). */
    String apiJwt() throws StoreException {
        try {
            long now = clock.instant().getEpochSecond();
            ObjectNode header = EventJson.mapper().createObjectNode();
            header.put("alg", "ES256").put("kid", props.getKeyId()).put("typ", "JWT");
            ObjectNode payload = EventJson.mapper().createObjectNode();
            payload.put("iss", props.getIssuerId()).put("iat", now).put("exp", now + 300)
                    .put("aud", "appstoreconnect-v1").put("bid", props.getBundleId());
            String input = b64(EventJson.mapper().writeValueAsBytes(header)) + "."
                    + b64(EventJson.mapper().writeValueAsBytes(payload));
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initSign(apiKey);
            s.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + b64(s.sign());
        } catch (Exception e) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Không ký được JWT gọi Apple", e);
        }
    }

    static PrivateKey loadPrivateKey(String file) throws Exception {
        String pem = Files.readString(Path.of(file), StandardCharsets.UTF_8)
                .replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Instant millis(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : Instant.ofEpochMilli(v.asLong());
    }
}
