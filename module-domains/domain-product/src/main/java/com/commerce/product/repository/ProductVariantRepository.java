package com.commerce.product.repository;

import com.commerce.product.entity.ProductVariant;
import com.commerce.product.entity.ProductVariantStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductId(UUID productId);

    List<ProductVariant> findByProductIdIn(Collection<UUID> productIds);

    boolean existsByProductIdAndOptionSignatureAndStatusNot(
            UUID productId, String optionSignature, ProductVariantStatus status);
}
