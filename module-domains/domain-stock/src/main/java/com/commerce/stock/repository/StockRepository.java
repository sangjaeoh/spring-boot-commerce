package com.commerce.stock.repository;

import com.commerce.stock.entity.Stock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, UUID> {

    Optional<Stock> findByVariantId(UUID variantId);

    boolean existsByVariantId(UUID variantId);
}
