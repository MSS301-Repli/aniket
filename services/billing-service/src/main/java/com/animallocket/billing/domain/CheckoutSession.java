package com.animallocket.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một lần thanh toán web. Số tiền và sản phẩm được chốt lúc tạo phiên; quyền lợi chỉ cấp cho {@code publicId} này
 * sau khi PayPal xác nhận đúng số tiền của đơn hàng đã gắn với phiên (không tin tham số nào trên URL quay về).
 */
@Entity
@Table(name = "checkout_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_checkout_token", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_checkout_user", columnList = "public_id"),
                @Index(name = "idx_checkout_capture", columnList = "paypal_capture_id"),
                @Index(name = "idx_checkout_order", columnList = "paypal_order_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class CheckoutSession {
    public enum Status { PENDING, APPROVING, COMPLETED, PAYMENT_HELD, CANCELED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** SHA-256 (hex) của mã dùng một lần nằm trên URL; không lưu mã gốc. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "public_id", nullable = false, length = 20)
    private String publicId;
    @Column(nullable = false, length = 60)
    private String sku;
    /** Số tiền USD (cent) chốt lúc tạo phiên cho PayPal; null nếu sản phẩm không bán qua PayPal. */
    @Column(name = "amount_cents")
    private Integer amountCents;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.PENDING;
    @Column(name = "paypal_order_id", length = 64)
    private String paypalOrderId;
    /** Id khoản thu PayPal, ghi khi đã cấp quyền; webhook hoàn tiền dùng nó để tìm lại phiên. */
    @Column(name = "paypal_capture_id", length = 100)
    private String paypalCaptureId;
    /** payOS: số tiền VND chốt lần đầu bấm payOS (các link sau dùng lại đúng số này). */
    @Column(name = "payos_amount")
    private Integer payosAmount;
    /** payOS: số link đã tạo. Mỗi link có orderCode riêng = base + id*100 + lần thứ n (xem CheckoutService). */
    @Column(name = "payos_attempts", nullable = false)
    private int payosAttempts;
    @Column(name = "payos_link_id", length = 64)
    private String payosLinkId;
    /** payOS: orderCode của link đã được trả tiền và đã cấp quyền. */
    @Column(name = "payos_paid_order_code")
    private Long payosPaidOrderCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
