package com.animallocket.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/** Số dư hiện tại của từng loại vật phẩm. Chỉ đổi qua câu UPDATE nguyên tử; mọi thay đổi đều có dòng trong sổ cái. */
@Entity
@Table(name = "wallet_balances")
@IdClass(WalletBalance.Key.class)
@Getter
@NoArgsConstructor
public class WalletBalance {
    public static class Key implements Serializable {
        public String publicId;
        public String item;

        public Key() {
        }

        public Key(String publicId, String item) {
            this.publicId = publicId;
            this.item = item;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(publicId, k.publicId) && Objects.equals(item, k.item);
        }

        @Override
        public int hashCode() {
            return Objects.hash(publicId, item);
        }
    }

    @Id
    @Column(name = "public_id", length = 20)
    private String publicId;
    @Id
    @Column(length = 40)
    private String item;
    @Column(nullable = false)
    private int balance;

    public WalletBalance(String publicId, String item, int balance) {
        this.publicId = publicId;
        this.item = item;
        this.balance = balance;
    }
}
