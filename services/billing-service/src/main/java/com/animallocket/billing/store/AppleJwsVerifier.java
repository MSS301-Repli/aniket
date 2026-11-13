package com.animallocket.billing.store;

import com.animallocket.common.event.EventJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Xác thực JWS (ES256) do Apple ký: giao dịch StoreKit và App Store Server Notifications V2.
 *
 * <p>Điều kiện để chấp nhận (thiếu một điều kiện là từ chối):
 * <ol>
 *   <li>Đã cấu hình ít nhất một Apple Root CA; nếu không → UNAVAILABLE (fail closed).</li>
 *   <li>Header {@code alg} đúng bằng {@code ES256} (từ chối none/HS256/RS256...).</li>
 *   <li>Header có {@code x5c}; chuỗi chứng chỉ (leaf → intermediate [→ root]) hợp lệ theo PKIX và kết thúc
 *       tại một root đã cấu hình, còn hạn tại thời điểm hiện tại.</li>
 *   <li>Leaf mang OID đánh dấu của Apple (mặc định bắt buộc, tắt được bằng cấu hình).</li>
 *   <li>Chữ ký trên {@code header.payload} hợp lệ với khoá công khai của leaf.</li>
 * </ol>
 * Không kiểm tra thu hồi chứng chỉ (OCSP/CRL).
 */
public class AppleJwsVerifier {
    public static final String APPLE_LEAF_OID = "1.2.840.113635.100.6.11.1";
    private static final int MAX_JWS_LENGTH = 64 * 1024;

    private final List<X509Certificate> roots;
    private final Set<TrustAnchor> anchors = new HashSet<>();
    private final boolean requireAppleOid;
    private final Clock clock;

    public AppleJwsVerifier(List<X509Certificate> roots, boolean requireAppleOid, Clock clock) {
        this.roots = List.copyOf(roots);
        this.requireAppleOid = requireAppleOid;
        this.clock = clock;
        roots.forEach(r -> anchors.add(new TrustAnchor(r, null)));
    }

    /** Đọc các file PEM hoặc DER; mỗi file có thể chứa nhiều chứng chỉ. */
    public static List<X509Certificate> loadRoots(List<String> files) throws IOException, GeneralSecurityException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> out = new ArrayList<>();
        for (String f : files) {
            if (f == null || f.isBlank()) {
                continue;
            }
            try (InputStream in = new FileInputStream(f.trim())) {
                cf.generateCertificates(in).forEach(c -> out.add((X509Certificate) c));
            }
        }
        return out;
    }

    public JsonNode verify(String jws) throws StoreException {
        if (anchors.isEmpty()) {
            throw new StoreException(StoreException.Kind.UNAVAILABLE, "Chưa cấu hình Apple Root CA");
        }
        if (jws == null || jws.isEmpty() || jws.length() > MAX_JWS_LENGTH) {
            throw invalid("JWS rỗng hoặc quá lớn");
        }
        String[] parts = jws.split("\\.", -1);
        if (parts.length != 3) {
            throw invalid("JWS phải có 3 phần");
        }
        try {
            JsonNode header = EventJson.mapper().readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode alg = header.get("alg");
            if (alg == null || !alg.isTextual() || !"ES256".equals(alg.asText())) {
                throw invalid("alg phải là ES256");
            }
            List<X509Certificate> chain = parseChain(header.get("x5c"));
            X509Certificate leaf = chain.get(0);
            validateChain(chain);
            if (requireAppleOid && !hasExtension(leaf, APPLE_LEAF_OID)) {
                throw invalid("Leaf không có OID của Apple");
            }
            verifySignature(leaf.getPublicKey(), parts);
            return EventJson.mapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
        } catch (StoreException e) {
            throw e;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw invalid("JWS không hợp lệ: " + e.getClass().getSimpleName());
        }
    }

    private List<X509Certificate> parseChain(JsonNode x5c) throws GeneralSecurityException, StoreException {
        if (x5c == null || !x5c.isArray() || x5c.isEmpty() || x5c.size() > 5) {
            throw invalid("Thiếu hoặc sai x5c");
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> chain = new ArrayList<>();
        for (JsonNode n : x5c) {
            if (!n.isTextual()) {
                throw invalid("x5c phải là chuỗi base64");
            }
            byte[] der = Base64.getDecoder().decode(n.asText());
            chain.add((X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(der)));
        }
        return chain;
    }

    private void validateChain(List<X509Certificate> chain) throws GeneralSecurityException, StoreException {
        // Apple đính kèm cả root ở cuối x5c; PKIX không cho phép trust anchor nằm trong path nên cắt nó đi
        // (chỉ khi nó đúng là root đã cấu hình, nên không thể "tự đưa root của kẻ tấn công vào").
        List<X509Certificate> path = new ArrayList<>(chain);
        while (path.size() > 1 && isConfiguredRoot(path.get(path.size() - 1))) {
            path.remove(path.size() - 1);
        }
        CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(path);
        PKIXParameters params = new PKIXParameters(anchors);
        params.setRevocationEnabled(false);
        params.setDate(Date.from(clock.instant()));
        try {
            CertPathValidator.getInstance("PKIX").validate(certPath, params);
        } catch (java.security.cert.CertPathValidatorException e) {
            throw invalid("Chuỗi chứng chỉ không hợp lệ: " + e.getReason());
        }
    }

    private boolean isConfiguredRoot(X509Certificate c) {
        try {
            for (X509Certificate r : roots) {
                if (Arrays.equals(r.getEncoded(), c.getEncoded())) {
                    return true;
                }
            }
        } catch (java.security.cert.CertificateEncodingException e) {
            return false;
        }
        return false;
    }

    private static boolean hasExtension(X509Certificate c, String oid) {
        Set<String> critical = c.getCriticalExtensionOIDs();
        Set<String> nonCritical = c.getNonCriticalExtensionOIDs();
        return (critical != null && critical.contains(oid)) || (nonCritical != null && nonCritical.contains(oid));
    }

    private static void verifySignature(PublicKey key, String[] parts) throws GeneralSecurityException, StoreException {
        if (!(key instanceof ECPublicKey)) {
            throw invalid("Khoá của leaf không phải EC");
        }
        byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
        if (sig.length != 64) {
            throw invalid("Chữ ký ES256 phải dài 64 byte");
        }
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(key);
        s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!s.verify(sig)) {
            throw invalid("Chữ ký sai");
        }
    }

    private static StoreException invalid(String msg) {
        return new StoreException(StoreException.Kind.INVALID, msg);
    }
}
