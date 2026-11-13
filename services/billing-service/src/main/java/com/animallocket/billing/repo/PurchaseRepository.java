package com.animallocket.billing.repo;

import com.animallocket.billing.domain.Purchase;
import com.animallocket.billing.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    Optional<Purchase> findByStoreAndStoreTransactionId(Store store, String storeTransactionId);

    /** Giữ bản ghi tài chính nhưng cắt liên kết danh tính khi người dùng xoá tài khoản. */
    @Modifying
    @Query("update Purchase p set p.publicId = 'deleted' where p.publicId = :publicId")
    int anonymize(String publicId);
}
