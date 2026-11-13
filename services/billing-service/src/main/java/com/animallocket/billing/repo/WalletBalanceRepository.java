package com.animallocket.billing.repo;

import com.animallocket.billing.domain.WalletBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WalletBalanceRepository extends JpaRepository<WalletBalance, WalletBalance.Key> {
    List<WalletBalance> findByPublicId(String publicId);

    /** Trừ nguyên tử: chỉ thành công (trả 1) khi số dư đủ. Không bao giờ âm, kể cả khi hai request đồng thời. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WalletBalance w set w.balance = w.balance - :n where w.publicId = :id and w.item = :item and w.balance >= :n")
    int debit(String id, String item, int n);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WalletBalance w set w.balance = w.balance + :n where w.publicId = :id and w.item = :item")
    int credit(String id, String item, int n);

    void deleteByPublicId(String publicId);
}
