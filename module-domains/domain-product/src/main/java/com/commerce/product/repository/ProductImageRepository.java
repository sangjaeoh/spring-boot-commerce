package com.commerce.product.repository;

import com.commerce.product.entity.ProductImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(UUID productId);

    List<ProductImage> findByProductIdInOrderBySortOrderAscIdAsc(Collection<UUID> productIds);

    Optional<ProductImage> findByIdAndProductId(UUID id, UUID productId);

    Optional<ProductImage> findTopByProductIdOrderBySortOrderDesc(UUID productId);
}
