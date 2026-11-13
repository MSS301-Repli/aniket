package com.animallocket.billing.web;

import com.animallocket.billing.service.BillingDtos;
import com.animallocket.billing.service.CheckoutService;
import com.animallocket.common.Headers;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API cho app (có JWT): xin liên kết thanh toán web. Trang web nằm ở {@link CheckoutPageController}. */
@RestController
public class CheckoutController {
    private final CheckoutService checkout;

    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @PostMapping("/api/v1/billing/checkout-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingDtos.CheckoutResponse create(@RequestHeader(Headers.PUBLIC_ID) String me,
                                               @Valid @RequestBody BillingDtos.CheckoutRequest req) {
        return checkout.create(me, req.sku());
    }
}
