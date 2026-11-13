package com.animallocket.billing.repo;

import com.animallocket.billing.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findByActiveTrueOrderBySku();
}
