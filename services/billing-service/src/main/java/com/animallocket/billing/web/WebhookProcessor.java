package com.animallocket.billing.web;

import com.animallocket.billing.domain.Store;
import com.animallocket.billing.service.BillingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khử trùng lặp VÀ xử lý webhook trong CÙNG MỘT transaction: nếu xử lý lỗi thì dấu "đã nhận" cũng rollback,
 * store gửi lại thì được xử lý lại (đây chính là hàng đợi retry). Mọi cuộc gọi mạng phải làm TRƯỚC khi vào đây.
 */
@Service
public class WebhookProcessor {
    private final BillingService billing;

    public WebhookProcessor(BillingService billing) {
        this.billing = billing;
    }

    /** @return false nếu thông báo này đã xử lý trước đó. */
    @Transactional
    public boolean process(String dedupeId, Store store, String type, Runnable action) {
        if (!billing.firstTimeWebhook(dedupeId, store, type)) {
            return false;
        }
        action.run();
        return true;
    }
}
