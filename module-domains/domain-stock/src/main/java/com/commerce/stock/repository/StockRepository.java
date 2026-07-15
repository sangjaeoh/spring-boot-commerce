package com.commerce.stock.repository;

import com.commerce.stock.entity.Stock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, UUID> {

    Optional<Stock> findByVariantId(UUID variantId);

    List<Stock> findByVariantIdIn(Collection<UUID> variantIds);

    boolean existsByVariantId(UUID variantId);
}
