package com.animallocket.billing.service;

import com.animallocket.billing.domain.CheckoutSession;
import com.animallocket.billing.domain.Product;
import com.animallocket.billing.domain.Store;
import com.animallocket.billing.repo.CheckoutSessionRepository;
import com.animallocket.billing.repo.ProductRepository;
import com.animallocket.billing.store.PayOsClient;
import com.animallocket.billing.store.PayOsProperties;
import com.animallocket.billing.store.PayPalClient;
import com.animallocket.billing.store.PayPalProperties;
import com.animallocket.billing.store.StoreException;
import com.animallocket.billing.store.VerifiedPurchase;
import com.animallocket.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Thanh toán web qua PayPal: app xin liên kết ({@link #create}), trình duyệt đi qua {@link #start} và {@link #complete}.
 * Giống {@link PurchaseFlow}, bean này cố ý KHÔNG @Transactional: gọi PayPal xong mới cấp quyền trong transaction ngắn
 * của {@link BillingService#grant}.
 *
 * <p>An toàn: quyền lợi chỉ cấp khi PayPal báo khoản thu COMPLETED đúng số tiền/đơn gắn với phiên; publicId lấy từ phiên
 * (do app đã đăng nhập tạo), không lấy từ URL. Capture và cấp quyền đều idempotent nên gọi lặp (F5, hai tab, lỗi giữa chừng)
 * không thu hoặc cấp hai lần.
 */
@Service
public class CheckoutService {
    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,64}");
    private static final String CURRENCY = "USD";
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum State { NOT_FOUND, EXPIRED, READY, COMPLETED, HELD, FAILED, UNAVAILABLE }

    /**
     * Thứ trang cần để hiển thị. {@code redirectUrl} chỉ có khi {@link #start}/{@link #startPayOs} cần đưa người dùng sang
     * PayPal/payOS. {@code amount}+{@code currency} là giá PayPal (USD, null nếu không bán qua PayPal); {@code payosAmount} là giá VND
     * đã định dạng (null nếu không bán qua payOS); {@code provider} cho biết kết quả thuộc PayPal hay payOS (null = PayPal).
     */
    public record Outcome(State state, String productLabel, String amount, String currency, String redirectUrl, String message,
                          String provider, String payosAmount) {
        Outcome(State state, String productLabel, String amount, String currency, String redirectUrl, String message) {
            this(state, productLabel, amount, currency, redirectUrl, message, null, null);
        }

        static Outcome of(State s, String message) {
            return new Outcome(s, null, null, null, null, message);
        }

        Outcome from(String provider) {
            return new Outcome(state, productLabel, amount, currency, redirectUrl, message, provider, payosAmount);
        }
    }

    private static final String PAYOS = "PAYOS";
    /** payOS giới hạn 9 ký tự cho mô tả với tài khoản ngân hàng không liên kết qua payOS. */
    private static final int PAYOS_DESCRIPTION_MAX = 9;
    private static final int PAYOS_MAX_ATTEMPTS = 99;

    private final CheckoutSessionRepository sessions;
    private final ProductRepository products;
    private final BillingService billing;
    private final PayPalProperties props;
    private final PayOsProperties payosProps;
    private final ObjectProvider<PayPalClient> paypal;
    private final ObjectProvider<PayOsClient> payos;

    public CheckoutService(CheckoutSessionRepository sessions, ProductRepository products, BillingService billing,
                           PayPalProperties props, PayOsProperties payosProps, ObjectProvider<PayPalClient> paypal,
                           ObjectProvider<PayOsClient> payos) {
        this.sessions = sessions;
        this.products = products;
        this.billing = billing;
        this.props = props;
        this.payosProps = payosProps;
        this.paypal = paypal;
        this.payos = payos;
    }

    public boolean paypalEnabled() {
        return paypal.getIfAvailable() != null;
    }

    public boolean payosEnabled() {
        return payos.getIfAvailable() != null;
    }

    // ---------------- app xin liên kết ----------------

    public BillingDtos.CheckoutResponse create(String me, String sku) {
        if (!paypalEnabled() && !payosEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_UNAVAILABLE", "Thanh toán web chưa được bật");
        }
        Product product = products.findById(sku).filter(Product::isActive)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PRODUCT", "Sản phẩm không tồn tại"));
        boolean viaPayPal = paypalEnabled() && purchasableOnline(product, product.getPriceUsdCents());
        boolean viaPayOs = payosEnabled() && purchasableOnline(product, product.getPriceVnd());
        if (!viaPayPal && !viaPayOs) {
            throw ApiException.badRequest("NOT_PURCHASABLE_ONLINE", "Sản phẩm này chưa bán qua web");
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        CheckoutSession s = new CheckoutSession();
        s.setTokenHash(hash(token));
        s.setPublicId(me);
        s.setSku(sku);
        s.setAmountCents(viaPayPal ? product.getPriceUsdCents() : null);
        s.setCurrency(CURRENCY);
        s.setExpiresAt(Instant.now().plus(props.getSessionTtl()));
        sessions.save(s);
        return new BillingDtos.CheckoutResponse(baseUrl() + "/pay/" + token, s.getExpiresAt());
    }

    private static boolean purchasableOnline(Product p, Integer price) {
        if (price == null || price <= 0) {
            return false;
        }
        return p.getType() != Product.Type.SUBSCRIPTION || (p.getPeriodDays() != null && p.getPeriodDays() > 0);
    }

    // ---------------- trình duyệt ----------------

    /** Dữ liệu cho trang tóm tắt đơn. */
    public Outcome view(String token) {
        Optional<CheckoutSession> found = find(token);
        if (found.isEmpty()) {
            return Outcome.of(State.NOT_FOUND, "Liên kết thanh toán không hợp lệ.");
        }
        CheckoutSession s = found.get();
        Outcome done = terminal(s);
        if (done != null) {
            return done;
        }
        return describe(s, State.READY, null);
    }

    /** Người dùng bấm "Thanh toán": tạo đơn PayPal và trả địa chỉ để chuyển sang duyệt. */
    public Outcome start(String token) {
        Optional<CheckoutSession> found = find(token);
        if (found.isEmpty()) {
            return Outcome.of(State.NOT_FOUND, "Liên kết thanh toán không hợp lệ.");
        }
        CheckoutSession s = found.get();
        Outcome done = terminal(s);
        if (done != null) {
            return done;
        }
        PayPalClient client = paypal.getIfAvailable();
        if (client == null) {
            return Outcome.of(State.UNAVAILABLE, "Thanh toán PayPal chưa được bật.");
        }
        Product product = products.findById(s.getSku()).orElse(null);
        if (product == null) {
            return Outcome.of(State.FAILED, "Sản phẩm không còn tồn tại.");
        }
        if (s.getAmountCents() == null) {
            return Outcome.of(State.FAILED, "Sản phẩm này không bán qua PayPal.");
        }
        try {
            PayPalClient.CreatedOrder order = client.createOrder("animal-order-" + s.getId(), String.valueOf(s.getId()),
                    "Animal Locket - " + s.getSku(), amount(s), s.getCurrency(),
                    baseUrl() + "/pay/" + token + "/return", baseUrl() + "/pay/" + token + "/cancel");
            if (!order.approveUrl().startsWith("https://")) {
                log.error("PayPal trả liên kết duyệt không phải HTTPS, từ chối chuyển hướng: {}", order.approveUrl());
                return Outcome.of(State.UNAVAILABLE, "Không bắt đầu được thanh toán, vui lòng thử lại.");
            }
            s.setPaypalOrderId(order.orderId());
            s.setStatus(CheckoutSession.Status.APPROVING);
            sessions.save(s);
            return new Outcome(State.READY, label(product), amount(s), s.getCurrency(), order.approveUrl(), null);
        } catch (StoreException e) {
            log.error("Không tạo được đơn PayPal cho phiên {}: {}", s.getId(), e.getMessage());
            return Outcome.of(e.kind() == StoreException.Kind.INVALID ? State.FAILED : State.UNAVAILABLE,
                    "Không bắt đầu được thanh toán, vui lòng thử lại.");
        }
    }

    /** Người dùng đóng PayPal giữa chừng: quay lại trang tóm tắt, vẫn thanh toán lại được đến khi hết hạn. */
    public Outcome cancel(String token) {
        return cancel(token, "PayPal");
    }

    /** @param providerName tên hiển thị trong thông báo ("PayPal" hoặc "VietQR") */
    public Outcome cancel(String token, String providerName) {
        Optional<CheckoutSession> found = find(token);
        if (found.isEmpty()) {
            return Outcome.of(State.NOT_FOUND, "Liên kết thanh toán không hợp lệ.");
        }
        CheckoutSession s = found.get();
        Outcome done = terminal(s);
        if (done != null) {
            return done;
        }
        if (s.getStatus() == CheckoutSession.Status.APPROVING) {
            s.setStatus(CheckoutSession.Status.PENDING);
            sessions.save(s);
        }
        return describe(s, State.READY, "Bạn đã huỷ thanh toán qua " + providerName + ". Chưa có khoản tiền nào bị trừ.");
    }

    /** PayPal đưa người dùng về: capture, kiểm tra, cấp quyền. Gọi lặp an toàn. */
    public Outcome complete(String token) {
        Optional<CheckoutSession> found = find(token);
        if (found.isEmpty()) {
            return Outcome.of(State.NOT_FOUND, "Liên kết thanh toán không hợp lệ.");
        }
        return settle(found.get(), false);
    }

    /**
     * Webhook PAYMENT.CAPTURE.COMPLETED: khoản thu đã xong ở PayPal nên chốt nốt phiên dù người dùng không quay về
     * (đóng tab, server sập giữa capture và cấp quyền, hoặc khoản PENDING vừa hoàn tất). Không bị chặn bởi hạn của link.
     *
     * @return null nếu không có phiên nào gắn với đơn này
     */
    public Outcome settleByOrderId(String orderId) {
        return sessions.findFirstByPaypalOrderId(orderId).map(s -> settle(s, true)).orElse(null);
    }

    /** Hoàn tiền toàn phần khi số tiền hoàn >= số đã thu; hoàn một phần thì giữ quyền lợi (xem PAYPAL.md). */
    public boolean isFullRefund(String captureId, String refundValue, String refundCurrency) {
        return sessions.findFirstByPaypalCaptureId(captureId).filter(s -> s.getAmountCents() != null).map(s -> {
            try {
                return s.getCurrency().equals(refundCurrency)
                        && new BigDecimal(refundValue).compareTo(BigDecimal.valueOf(s.getAmountCents(), 2)) >= 0;
            } catch (RuntimeException e) {
                return false;
            }
        }).orElse(false);
    }

    private Outcome settle(CheckoutSession s, boolean ignoreExpiry) {
        Outcome done = terminal(s, ignoreExpiry);
        // Khoản đang giữ (PAYMENT_HELD) vẫn phải thử lại: có thể PayPal vừa hoàn tất.
        if (done != null && s.getStatus() != CheckoutSession.Status.PAYMENT_HELD) {
            return done;
        }
        if (s.getPaypalOrderId() == null) {
            return describe(s, State.READY, "Bạn chưa hoàn tất thanh toán trên PayPal.");
        }
        PayPalClient client = paypal.getIfAvailable();
        if (client == null) {
            return Outcome.of(State.UNAVAILABLE, "Thanh toán PayPal chưa được bật.");
        }
        Product product = products.findById(s.getSku()).orElse(null);
        if (product == null) {
            log.error("Phiên {} đã thanh toán nhưng sản phẩm {} không còn: cần xử lý thủ công", s.getId(), s.getSku());
            return Outcome.of(State.FAILED, "Sản phẩm không còn tồn tại. Liên hệ hỗ trợ nếu bạn đã bị trừ tiền.");
        }
        PayPalClient.Capture cap;
        try {
            cap = client.capture(s.getPaypalOrderId(), "animal-capture-" + s.getId());
        } catch (StoreException e) {
            log.error("Capture PayPal lỗi cho phiên {}: {}", s.getId(), e.getMessage());
            // Người dùng có thể tải lại trang: capture idempotent nên không thu hai lần.
            return e.kind() == StoreException.Kind.INVALID
                    ? Outcome.of(State.FAILED, "PayPal không hoàn tất giao dịch. Chưa có khoản tiền nào bị trừ.")
                    : Outcome.of(State.UNAVAILABLE, "Chưa xác nhận được với PayPal. Hãy tải lại trang sau ít phút; bạn sẽ không bị trừ tiền hai lần.");
        }

        if ("PENDING".equals(cap.captureStatus())) {
            s.setStatus(CheckoutSession.Status.PAYMENT_HELD);
            sessions.save(s);
            return describe(s, State.HELD, "PayPal đang giữ khoản thanh toán để xem xét. Quyền lợi sẽ được cấp khi khoản này hoàn tất.");
        }
        String problem = mismatch(s, cap);
        if (problem != null) {
            // Có thể PayPal đã thu tiền: ghi lỗi để hoàn tiền thủ công, tuyệt đối không cấp quyền.
            log.error("Capture PayPal không khớp phiên {} ({}): order={} capture={}", s.getId(), problem,
                    cap.orderId(), cap.captureId());
            return Outcome.of(State.FAILED, "Không xác nhận được giao dịch. Nếu bạn đã bị trừ tiền, hãy liên hệ hỗ trợ kèm mã đơn "
                    + s.getPaypalOrderId() + ".");
        }

        Instant purchasedAt = cap.createdAt() != null ? cap.createdAt() : Instant.now();
        Instant expiresAt = product.getType() == Product.Type.SUBSCRIPTION
                ? purchasedAt.plus(Duration.ofDays(product.getPeriodDays())) : null;
        VerifiedPurchase vp = new VerifiedPurchase(cap.captureId(), cap.captureId(), s.getSku(), purchasedAt, expiresAt, false);
        try {
            grant(s.getPublicId(), Store.PAYPAL, product, vp);
        } catch (RuntimeException e) {
            log.error("Đã thu tiền nhưng chưa cấp được quyền cho phiên {} (capture {}): {}", s.getId(), cap.captureId(), e.toString());
            return Outcome.of(State.UNAVAILABLE, "Đã nhận thanh toán nhưng chưa cập nhật được quyền lợi. Hãy tải lại trang; bạn sẽ không bị trừ tiền lại.");
        }
        s.setPaypalCaptureId(cap.captureId());
        s.setStatus(CheckoutSession.Status.COMPLETED);
        sessions.save(s);
        return describe(s, State.COMPLETED, null);
    }

    private void grant(String publicId, Store store, Product product, VerifiedPurchase vp) {
        try {
            billing.grant(publicId, store, product, vp);
        } catch (DataIntegrityViolationException e) {
            // Hai yêu cầu cùng cấp một giao dịch: bên thua vi phạm khoá duy nhất. Thử lại sẽ thấy bản ghi đã có.
            billing.grant(publicId, store, product, vp);
        }
    }

    private static String mismatch(CheckoutSession s, PayPalClient.Capture cap) {
        if (!s.getPaypalOrderId().equals(cap.orderId())) {
            return "order id khác";
        }
        if (!"COMPLETED".equals(cap.orderStatus()) || !"COMPLETED".equals(cap.captureStatus())) {
            return "trạng thái " + cap.orderStatus() + "/" + cap.captureStatus();
        }
        if (cap.captureId() == null || cap.captureId().isBlank() || cap.captureId().length() > 100) {
            return "thiếu capture id";
        }
        if (s.getAmountCents() == null) {
            return "phiên không bán qua PayPal";
        }
        if (!s.getCurrency().equals(cap.currency())) {
            return "tiền tệ " + cap.currency();
        }
        try {
            if (new BigDecimal(cap.amountValue()).compareTo(BigDecimal.valueOf(s.getAmountCents(), 2)) != 0) {
                return "số tiền " + cap.amountValue();
            }
        } catch (RuntimeException e) {
            return "số tiền không đọc được";
        }
        if (cap.customId() != null && !cap.customId().equals(String.valueOf(s.getId()))) {
            return "custom_id khác";
        }
        return null;
    }

    // ---------------- payOS (VietQR, VND) ----------------
    //
    // Mỗi lần bấm tạo một link payOS với orderCode riêng = orderCodeBase + id phiên * 100 + lần thứ n (n ≤ 99), nên từ orderCode
    // luôn suy ra được phiên (webhook và trang quay về không cần bảng tra). Trạng thái thật luôn hỏi lại payOS (GET), chữ ký webhook
    // chỉ để biết thông báo đúng do payOS gửi; tham số trên returnUrl không được ký nên chỉ dùng làm gợi ý orderCode (đã kiểm tra thuộc phiên).

    /** Người dùng bấm "Thanh toán VietQR": tạo link payOS mới và trả địa chỉ trang thanh toán của payOS. */
    public Outcome startPayOs(String token) {
        Optional<CheckoutSession> found = find(token);
        if (found.isEmpty()) {
            return Outcome.of(State.NOT_FOUND, "Liên kết thanh toán không hợp lệ.");
        }
        CheckoutSession s = found.get();
        Outcome done = terminal(s);
        if (done != null) {
            return done;
        }
        PayOsClient client = payos.getIfAvailable();
        if (client == null) {
            return Outcome.of(State.UNAVAILABLE, "Thanh toán VietQR chưa được bật.").from(PAYOS);
        }
        Product product = products.findById(s.getSku()).orElse(null);
        if (product == null) {
            return Outcome.of(State.FAILED, "Sản phẩm không còn tồn tại.");
        }
        Integer vnd = s.getPayosAmount() != null ? s.getPayosAmount() : product.getPriceVnd();
        if (vnd == null || vnd <= 0) {
            return Outcome.of(State.FAILED, "Sản phẩm này không bán qua VietQR.").from(PAYOS);
        }
        if (s.getPayosAttempts() >= PAYOS_MAX_ATTEMPTS) {
            return Outcome.of(State.FAILED, "Đã thử quá nhiều lần. Hãy quay lại app và bắt đầu lại.").from(PAYOS);
        }
        int attempt = s.getPayosAttempts() + 1;
        long orderCode = orderCode(s.getId(), attempt);
        // Ghi số lần thử TRƯỚC khi gọi payOS: nếu phản hồi bị mất mà link vẫn được trả tiền thì webhook phải tìm lại được phiên.
        s.setPayosAmount(vnd);
        s.setPayosAttempts(attempt);
        sessions.save(s);
        String description = ("AL" + s.getId());
        description = description.substring(0, Math.min(PAYOS_DESCRIPTION_MAX, description.length()));
        try {
            PayOsClient.PaymentLink link = client.createPaymentLink(orderCode, vnd, description, label(product),
                    baseUrl() + "/pay/" + token + "/payos/return", baseUrl() + "/pay/" + token + "/payos/cancel");
            if (!link.checkoutUrl().startsWith("https://")) {
                log.error("payOS trả địa chỉ thanh toán không phải HTTPS, từ chối chuyển hướng: {}", link.checkoutUrl());
                return Outcome.of(State.UNAVAILABLE, "Không bắt đầu được thanh toán, vui lòng thử lại.").from(PAYOS);
            }
            s.setPayosLinkId(link.paymentLinkId());
            s.setStatus(CheckoutSession.Status.APPROVING);
            sessions.save(s);
            return new Outcome(State.READY, label(product), amount(s), s.getCurrency(), link.checkoutUrl(), null, PAYOS,
                    formatVnd(vnd));
        } catch (StoreException e) {
            log.error("Không tạo được link payOS cho phiên {}: {}", s.getId(), e.getMessage());
            return Outcome.of(e.kind() == StoreException.Kind.INVALID ? State.FAILED : State.UNAVAILABLE,
                    "Không bắt đầu được thanh toán, vui lòng thử lại.").from(PAYOS);
        }
    }

    /**
     * payOS đưa người dùng về (returnUrl): hỏi payOS trạng thái thật rồi cấp quyền nếu đã PAID. Gọi lặp an toàn.
     *
     * @param orderCodeHint orderCode trên URL quay về (không được ký): chỉ dùng nếu thuộc đúng phiên này, ngược lại dùng link mới nhất
     */
    public Outcome completePayOs(String token, Long orderCodeHint) {
        Optional<CheckoutSession> found = find(token);
        if (found.isEmpty()) {
            return Outcome.of(State.NOT_FOUND, "Liên kết thanh toán không hợp lệ.");
        }
        CheckoutSession s = found.get();
        long orderCode;
        if (orderCodeHint != null && sessionOfOrderCode(orderCodeHint).map(x -> x.getId().equals(s.getId())).orElse(false)) {
            orderCode = orderCodeHint;
        } else if (s.getPayosAttempts() > 0) {
            orderCode = orderCode(s.getId(), s.getPayosAttempts());
        } else {
            return describe(s, State.READY, "Bạn chưa bắt đầu thanh toán VietQR.").from(PAYOS);
        }
        return settlePayOs(s, orderCode, false).from(PAYOS);
    }

    /**
     * Webhook payOS: chốt phiên dù người dùng không quay về (đóng tab, mất mạng sau khi chuyển khoản). Không bị chặn bởi hạn của link.
     *
     * @return null nếu orderCode không thuộc phiên nào (ví dụ dữ liệu mẫu của confirm-webhook)
     */
    public Outcome settlePayOsByOrderCode(long orderCode) {
        return sessionOfOrderCode(orderCode).map(s -> settlePayOs(s, orderCode, true).from(PAYOS)).orElse(null);
    }

    private Outcome settlePayOs(CheckoutSession s, long orderCode, boolean ignoreExpiry) {
        PayOsClient client = payos.getIfAvailable();
        if (client == null) {
            return Outcome.of(State.UNAVAILABLE, "Thanh toán VietQR chưa được bật.");
        }
        if (s.getStatus() == CheckoutSession.Status.COMPLETED) {
            warnIfPaidTwice(client, s, orderCode);
            return describe(s, State.COMPLETED, null);
        }
        if (!ignoreExpiry && s.getExpiresAt().isBefore(Instant.now())) {
            return Outcome.of(State.EXPIRED, "Liên kết đã hết hạn. Hãy quay lại app và bắt đầu lại.");
        }
        Product product = products.findById(s.getSku()).orElse(null);
        if (product == null || s.getPayosAmount() == null) {
            log.error("Phiên {} có link payOS {} nhưng thiếu sản phẩm hoặc số tiền: cần xử lý thủ công", s.getId(), orderCode);
            return Outcome.of(State.FAILED, "Không xác nhận được giao dịch. Liên hệ hỗ trợ nếu bạn đã chuyển khoản.");
        }
        PayOsClient.PaymentInfo info;
        try {
            info = client.getPaymentInfo(orderCode);
        } catch (StoreException e) {
            log.error("Không hỏi được payOS về đơn {} (phiên {}): {}", orderCode, s.getId(), e.getMessage());
            return e.kind() == StoreException.Kind.INVALID
                    ? Outcome.of(State.FAILED, "Không xác nhận được giao dịch với payOS. Nếu bạn đã chuyển khoản, hãy liên hệ hỗ trợ kèm mã đơn " + orderCode + ".")
                    : Outcome.of(State.UNAVAILABLE, "Chưa xác nhận được với payOS. Hãy tải lại trang sau ít phút; bạn sẽ không bị trừ tiền hai lần.");
        }
        switch (info.status()) {
            case "PAID" -> {
                return grantPayOs(s, product, orderCode, info);
            }
            case "PROCESSING" -> {
                return describe(s, State.HELD, "Ngân hàng đang xử lý giao dịch. Hãy chờ vài giây rồi bấm kiểm tra lại.");
            }
            case "PENDING" -> {
                return describe(s, State.HELD,
                        "Chưa nhận được tiền. Nếu bạn vừa chuyển khoản, hãy chờ vài giây rồi bấm kiểm tra lại.");
            }
            default -> {
                // CANCELLED hoặc giá trị lạ: chưa thu tiền, cho phép thử lại bằng link mới.
                if (s.getStatus() == CheckoutSession.Status.APPROVING) {
                    s.setStatus(CheckoutSession.Status.PENDING);
                    sessions.save(s);
                }
                return describe(s, State.READY, "Link thanh toán VietQR không còn hiệu lực. Chưa có khoản tiền nào bị trừ.");
            }
        }
    }

    private Outcome grantPayOs(CheckoutSession s, Product product, long orderCode, PayOsClient.PaymentInfo info) {
        String problem = null;
        if (info.orderCode() != orderCode) {
            problem = "orderCode khác";
        } else if (info.amount() != s.getPayosAmount()) {
            problem = "số tiền đơn " + info.amount();
        } else if (info.amountPaid() < s.getPayosAmount()) {
            problem = "đã trả " + info.amountPaid() + " < " + s.getPayosAmount();
        } else if (product.getType() == Product.Type.SUBSCRIPTION && product.getPeriodDays() == null) {
            problem = "thuê bao thiếu số ngày";
        }
        if (problem != null) {
            // payOS báo đã trả nhưng không khớp: tuyệt đối không cấp quyền, ghi lỗi để xử lý thủ công.
            log.error("payOS không khớp phiên {} ({}): orderCode={}", s.getId(), problem, orderCode);
            return Outcome.of(State.FAILED, "Không xác nhận được giao dịch. Nếu bạn đã chuyển khoản, hãy liên hệ hỗ trợ kèm mã đơn "
                    + orderCode + ".");
        }
        Instant now = Instant.now();
        Instant expiresAt = product.getType() == Product.Type.SUBSCRIPTION
                ? now.plus(Duration.ofDays(product.getPeriodDays())) : null;
        String tx = "payos:" + orderCode;
        try {
            grant(s.getPublicId(), Store.PAYOS, product, new VerifiedPurchase(tx, tx, s.getSku(), now, expiresAt, false));
        } catch (RuntimeException e) {
            log.error("Đã nhận tiền payOS nhưng chưa cấp được quyền cho phiên {} (orderCode {}): {}", s.getId(), orderCode, e.toString());
            return Outcome.of(State.UNAVAILABLE, "Đã nhận thanh toán nhưng chưa cập nhật được quyền lợi. Hãy tải lại trang; bạn sẽ không bị trừ tiền lại.");
        }
        s.setPayosPaidOrderCode(orderCode);
        s.setStatus(CheckoutSession.Status.COMPLETED);
        sessions.save(s);
        return describe(s, State.COMPLETED, null);
    }

    /** Phiên đã xong (bằng PayPal hoặc link payOS khác) mà link này cũng bị trả tiền: người dùng trả hai lần, cần hoàn tiền thủ công. */
    private void warnIfPaidTwice(PayOsClient client, CheckoutSession s, long orderCode) {
        if (Objects.equals(s.getPayosPaidOrderCode(), orderCode)) {
            return;
        }
        try {
            if ("PAID".equals(client.getPaymentInfo(orderCode).status())) {
                log.error("THANH TOÁN TRÙNG: phiên {} đã hoàn tất nhưng link payOS {} cũng đã được trả tiền: hoàn tiền thủ công", s.getId(), orderCode);
            }
        } catch (StoreException e) {
            log.warn("Không kiểm tra được đơn payOS {} của phiên đã hoàn tất: {}", orderCode, e.getMessage());
        }
    }

    private long orderCode(long sessionId, int attempt) {
        return payosProps.getOrderCodeBase() + sessionId * 100 + attempt;
    }

    private Optional<CheckoutSession> sessionOfOrderCode(long orderCode) {
        long rel = orderCode - payosProps.getOrderCodeBase();
        long id = rel / 100;
        int attempt = (int) (rel % 100);
        if (rel < 101 || attempt < 1) {
            return Optional.empty();
        }
        return sessions.findById(id).filter(s -> attempt <= s.getPayosAttempts());
    }

    // ---------------- tiện ích ----------------

    /** Trạng thái đã kết thúc (hết hạn, hoàn tất, đang giữ) hoặc null nếu còn thanh toán được. */
    private Outcome terminal(CheckoutSession s) {
        return terminal(s, false);
    }

    private Outcome terminal(CheckoutSession s, boolean ignoreExpiry) {
        if (s.getStatus() == CheckoutSession.Status.COMPLETED) {
            return describe(s, State.COMPLETED, null);
        }
        if (!ignoreExpiry && s.getExpiresAt().isBefore(Instant.now())) {
            return Outcome.of(State.EXPIRED, "Liên kết đã hết hạn. Hãy quay lại app và bắt đầu lại.");
        }
        if (s.getStatus() == CheckoutSession.Status.PAYMENT_HELD) {
            return describe(s, State.HELD, "PayPal đang giữ khoản thanh toán để xem xét.");
        }
        return null;
    }

    private Outcome describe(CheckoutSession s, State state, String message) {
        Product p = products.findById(s.getSku()).orElse(null);
        Integer vnd = s.getPayosAmount() != null ? s.getPayosAmount() : p == null ? null : p.getPriceVnd();
        return new Outcome(state, p == null ? s.getSku() : label(p), amount(s), s.getCurrency(), null, message, null,
                vnd == null || vnd <= 0 ? null : formatVnd(vnd));
    }

    static String formatVnd(long amount) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(amount) + " ₫";
    }

    static String label(Product p) {
        return switch (p.getType()) {
            case SUBSCRIPTION -> "Animal Locket Pro (" + p.getPeriodDays() + " ngày)";
            case CONSUMABLE -> p.getQuantity() + " × " + switch (p.getItem()) {
                case "BOOST" -> "Boost";
                case "PRIORITY_INVITE" -> "Lời mời ưu tiên";
                default -> p.getItem();
            };
            case PACK -> "Gói con vật " + p.getItem();
        };
    }

    private static String amount(CheckoutSession s) {
        return s.getAmountCents() == null ? null : BigDecimal.valueOf(s.getAmountCents(), 2).toPlainString();
    }

    private String baseUrl() {
        String b = props.getPublicBaseUrl();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    private Optional<CheckoutSession> find(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            return Optional.empty();
        }
        return sessions.findByTokenHash(hash(token));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
