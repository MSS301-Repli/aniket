package com.animallocket.billing.store;

import com.animallocket.common.event.EventJson;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Dựng chuỗi root → intermediate → leaf thật (giống cấu trúc của Apple) và ký JWS ES256 để test, không cần mạng. */
public final class AppleTestPki {
    private static final AtomicLong SERIAL = new AtomicLong(1000);

    public final KeyPair rootKey;
    public final KeyPair interKey;
    public final KeyPair leafKey;
    public final X509Certificate root;
    public final X509Certificate inter;
    public final X509Certificate leaf;

    private AppleTestPki(KeyPair rootKey, KeyPair interKey, KeyPair leafKey,
                         X509Certificate root, X509Certificate inter, X509Certificate leaf) {
        this.rootKey = rootKey;
        this.interKey = interKey;
        this.leafKey = leafKey;
        this.root = root;
        this.inter = inter;
        this.leaf = leaf;
    }

    public static AppleTestPki create() throws Exception {
        return create(true, Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(30)));
    }

    public static AppleTestPki create(boolean leafHasAppleOid, Instant leafNotBefore, Instant leafNotAfter) throws Exception {
        KeyPair rk = ec(), ik = ec(), lk = ec();
        Instant from = Instant.now().minus(Duration.ofDays(2));
        Instant to = Instant.now().plus(Duration.ofDays(3650));
        X500Name rootName = new X500Name("CN=Test Apple Root CA - G3, O=Test");
        X500Name interName = new X500Name("CN=Test Apple WWDR Intermediate, O=Test");
        X500Name leafName = new X500Name("CN=Test Prod ECC Mac App Store and iTunes Store Receipt Signing, O=Test");

        X509Certificate root = build(rootName, rootName, rk.getPublic(), rk.getPrivate(), from, to, true, false);
        X509Certificate inter = build(rootName, interName, ik.getPublic(), rk.getPrivate(), from, to, true, false);
        X509Certificate leaf = build(interName, leafName, lk.getPublic(), ik.getPrivate(), leafNotBefore, leafNotAfter, false, leafHasAppleOid);
        return new AppleTestPki(rk, ik, lk, root, inter, leaf);
    }

    private static KeyPair ec() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    private static X509Certificate build(X500Name issuer, X500Name subject, java.security.PublicKey pub, PrivateKey signKey,
                                         Instant from, Instant to, boolean ca, boolean appleOid) throws Exception {
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(SERIAL.incrementAndGet()),
                Date.from(from), Date.from(to), subject, pub);
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        b.addExtension(Extension.keyUsage, true, new KeyUsage(ca ? KeyUsage.keyCertSign | KeyUsage.cRLSign : KeyUsage.digitalSignature));
        if (appleOid) {
            b.addExtension(new ASN1ObjectIdentifier(AppleJwsVerifier.APPLE_LEAF_OID), false, DERNull.INSTANCE);
        }
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(signKey);
        return new JcaX509CertificateConverter().getCertificate(b.build(signer));
    }

    /** JWS chuẩn của Apple: x5c = [leaf, intermediate, root], ký bằng khoá của leaf. */
    public String sign(String payloadJson) throws Exception {
        return sign(payloadJson, "ES256", List.of(leaf, inter, root), leafKey.getPrivate());
    }

    public static String sign(String payloadJson, String alg, List<X509Certificate> x5c, PrivateKey key) throws Exception {
        ObjectNode header = EventJson.mapper().createObjectNode();
        header.put("alg", alg);
        if (x5c != null) {
            ArrayNode arr = header.putArray("x5c");
            for (X509Certificate c : x5c) {
                arr.add(Base64.getEncoder().encodeToString(c.getEncoded()));
            }
        }
        String input = b64(EventJson.mapper().writeValueAsBytes(header)) + "." + b64(payloadJson.getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(key);
        s.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + b64(s.sign());
    }

    public static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public Path writeRootPem(Path file) throws Exception {
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(root.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
        Files.writeString(file, pem);
        return file;
    }
}
