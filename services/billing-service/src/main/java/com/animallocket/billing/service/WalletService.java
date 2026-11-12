package com.animallocket.billing.service;

import com.animallocket.billing.domain.LedgerEntry;
import com.animallocket.billing.domain.WalletBalance;
import com.animallocket.billing.repo.LedgerRepository;
import com.animallocket.billing.repo.WalletBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

/**
 * Ví vật phẩm. Số dư đổi bằng câu UPDATE nguyên tử (không đọc-rồi-ghi), và mọi thay đổi đều có một dòng sổ cái
 * với khoá duy nhất (reason, ref, item) nên cùng một sự kiện không thể cộng/trừ hai lần.
 */
@Service
public class WalletService {
    private final WalletBalanceRepository balances;
    private final LedgerRepository ledger;

    public WalletService(WalletBalanceRepository balances, LedgerRepository ledger) {
        this.balances = balances;
        this.ledger = ledger;
    }

    /** @return false nếu (reason, ref, item) đã được ghi trước đó (xử lý lại cùng sự kiện). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean credit(String publicId, String item, int qty, String reason, String ref) {
        if (qty <= 0 || ledger.existsByReasonAndRefAndItem(reason, ref, item)) {
            return false;
        }
        ledger.save(new LedgerEntry(publicId, item, qty, reason, ref));
        if (balances.credit(publicId, item, qty) == 0) {
            balances.save(new WalletBalance(publicId, item, qty));
        }
        return true;
    }

    /**
     * Ghi một dòng sổ cái mà KHÔNG đổi số dư của ví này: dùng cho vật phẩm mà số dư do service khác giữ
     * (hiện là {@code AVATAR_SPIN}, số dư nằm ở user-service). Sổ cái vẫn là nơi chốt "việc này đã xảy ra chưa",
     * nhờ khoá duy nhất (reason, ref, item).
     *
     * @return true nếu đây là lần đầu ghi; false nếu (reason, ref, item) đã có
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordOnce(String publicId, String item, int qty, String reason, String ref) {
        if (qty <= 0 || ledger.existsByReasonAndRefAndItem(reason, ref, item)) {
            return false;
        }
        ledger.save(new LedgerEntry(publicId, item, qty, reason, ref));
        return true;
    }

    /**
     * Trừ n đơn vị. Idempotent theo (reason, ref): gọi lại với cùng ref coi như đã trừ.
     *
     * @return true nếu đã trừ (hoặc đã trừ từ trước); false nếu số dư không đủ
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean debit(String publicId, String item, int n, String reason, String ref) {
        if (ledger.existsByReasonAndRefAndItem(reason, ref, item)) {
            return true;
        }
        if (balances.debit(publicId, item, n) == 0) {
            return false;
        }
        ledger.save(new LedgerEntry(publicId, item, -n, reason, ref));
        return true;
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> balances(String publicId) {
        Map<String, Integer> out = new TreeMap<>();
        balances.findByPublicId(publicId).forEach(b -> out.put(b.getItem(), b.getBalance()));
        return out;
    }

    @Transactional
    public void wipe(String publicId) {
        balances.deleteByPublicId(publicId);
        ledger.deleteByPublicId(publicId);
    }
}
