package com.animallocket.billing.web;

import com.animallocket.billing.service.BillingDtos;
import com.animallocket.billing.service.BillingService;
import com.animallocket.billing.service.PurchaseFlow;
import com.animallocket.billing.service.WalletService;
import com.animallocket.common.Headers;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class BillingController {
    private final BillingService billing;
    private final PurchaseFlow flow;
    private final WalletService wallet;

    public BillingController(BillingService billing, PurchaseFlow flow, WalletService wallet) {
        this.billing = billing;
        this.flow = flow;
        this.wallet = wallet;
    }

    @GetMapping("/api/v1/billing/products")
    public List<BillingDtos.ProductView> products() {
        return billing.catalog();
    }

    @GetMapping("/api/v1/billing/me")
    public BillingDtos.MeResponse me(@RequestHeader(Headers.PUBLIC_ID) String me) {
        return billing.me(me);
    }

    /** Client gửi giao dịch vừa mua (hoặc "khôi phục giao dịch"); chỉ kết thúc giao dịch với store sau khi nhận 200. */
    @PostMapping("/api/v1/billing/purchases/verify")
    public BillingDtos.MeResponse verify(@RequestHeader(Headers.PUBLIC_ID) String me,
                                         @Valid @RequestBody BillingDtos.VerifyRequest req) {
        return flow.verifyAndGrant(me, req);
    }

    // ---- nội bộ (discovery dùng Boost) ----

    @PostMapping("/internal/wallet/consume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@Valid @RequestBody BillingDtos.ConsumeRequest req) {
        billing.consume(req.publicId(), req.item());
    }

    @GetMapping("/internal/wallet/{publicId}")
    public Map<String, Integer> balances(@PathVariable String publicId) {
        return wallet.balances(publicId);
    }
}
