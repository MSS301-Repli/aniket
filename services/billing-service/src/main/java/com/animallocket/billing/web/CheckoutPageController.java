package com.animallocket.billing.web;

import com.animallocket.billing.service.CheckoutService;
import com.animallocket.billing.service.CheckoutService.Outcome;
import com.animallocket.billing.store.PayPalProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Trang thanh toán web (HTML). Công khai về mặt JWT: mã trên URL (dùng một lần, hết hạn, gắn với một người dùng) là thứ
 * duy nhất xác định phiên. Mọi quyết định cấp quyền nằm ở {@link CheckoutService}, trang chỉ hiển thị kết quả.
 */
@Controller
public class CheckoutPageController {
    private final CheckoutService checkout;
    private final PayPalProperties props;

    public CheckoutPageController(CheckoutService checkout, PayPalProperties props) {
        this.checkout = checkout;
        this.props = props;
    }

    @GetMapping("/pay/{token}")
    public String page(@PathVariable String token, Model model, HttpServletResponse res) {
        return render(checkout.view(token), token, model, res);
    }

    /** Bấm nút thanh toán: chuyển sang PayPal (địa chỉ tuyệt đối trên paypal.com do {@link CheckoutService} đã kiểm tra HTTPS). */
    @PostMapping("/pay/{token}/start")
    public String start(@PathVariable String token, Model model, HttpServletResponse res) {
        Outcome o = checkout.start(token);
        if (o.redirectUrl() != null) {
            noLeak(res);
            return "redirect:" + o.redirectUrl();
        }
        return render(o, token, model, res);
    }

    /** PayPal đưa người dùng về sau khi duyệt. */
    @GetMapping("/pay/{token}/return")
    public String back(@PathVariable String token, Model model, HttpServletResponse res) {
        return render(checkout.complete(token), token, model, res);
    }

    @GetMapping("/pay/{token}/cancel")
    public String cancel(@PathVariable String token, Model model, HttpServletResponse res) {
        return render(checkout.cancel(token), token, model, res);
    }

    // ---- VietQR / payOS ----

    /** Bấm "Chuyển khoản VietQR": tạo link payOS rồi chuyển sang trang thanh toán của payOS (HTTPS do service đã kiểm tra). */
    @PostMapping("/pay/{token}/payos")
    public String startPayOs(@PathVariable String token, Model model, HttpServletResponse res) {
        Outcome o = checkout.startPayOs(token);
        if (o.redirectUrl() != null) {
            noLeak(res);
            return "redirect:" + o.redirectUrl();
        }
        return render(o, token, model, res);
    }

    /** payOS đưa người dùng về. Tham số trên URL không được ký: chỉ orderCode được dùng làm gợi ý và service kiểm tra lại. */
    @GetMapping("/pay/{token}/payos/return")
    public String backPayOs(@PathVariable String token, @RequestParam(value = "orderCode", required = false) String orderCode,
                            Model model, HttpServletResponse res) {
        Long hint = null;
        if (orderCode != null && orderCode.matches("\\d{1,18}")) {
            hint = Long.parseLong(orderCode);
        }
        return render(checkout.completePayOs(token, hint), token, model, res);
    }

    @GetMapping("/pay/{token}/payos/cancel")
    public String cancelPayOs(@PathVariable String token, Model model, HttpServletResponse res) {
        return render(checkout.cancel(token, "VietQR"), token, model, res);
    }

    private String render(Outcome o, String token, Model model, HttpServletResponse res) {
        noLeak(res);
        res.setStatus(switch (o.state()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND.value();
            case EXPIRED -> HttpStatus.GONE.value();
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE.value();
            case FAILED -> HttpStatus.UNPROCESSABLE_ENTITY.value();
            default -> HttpStatus.OK.value();
        });
        // Thuộc tính phẳng để template không phụ thuộc cách SpEL đọc record.
        model.addAttribute("status", o.state().name());
        model.addAttribute("productLabel", o.productLabel());
        model.addAttribute("amount", o.amount());
        model.addAttribute("currency", o.currency());
        model.addAttribute("message", o.message());
        model.addAttribute("provider", o.provider());
        model.addAttribute("payosAmount", o.payosAmount());
        // Chỉ hiện phương thức đã bật VÀ có giá cho sản phẩm này.
        model.addAttribute("paypalEnabled", checkout.paypalEnabled() && o.amount() != null);
        model.addAttribute("payosEnabled", checkout.payosEnabled() && o.payosAmount() != null);
        model.addAttribute("token", token);
        model.addAttribute("appReturnUrl", props.getAppReturnUrl());
        return o.state() == CheckoutService.State.READY ? "pay/checkout" : "pay/result";
    }

    /** Mã nằm trên URL: không cache và không gửi Referer sang trang khác. */
    private static void noLeak(HttpServletResponse res) {
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        res.setHeader("Referrer-Policy", "no-referrer");
    }
}
