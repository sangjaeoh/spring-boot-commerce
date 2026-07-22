package com.commerce.product.application.required;

import com.commerce.product.domain.ProductVariant;
import com.commerce.product.domain.ProductVariantStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductId(UUID productId);

    List<ProductVariant> findByProductIdIn(Collection<UUID> productIds);

    boolean existsByProductIdAndOptionSignatureAndStatusNot(
            UUID productId, String optionSignature, ProductVariantStatus status);

    Optional<ProductVariant> findByProductIdAndOptionSignatureAndStatusNot(
            UUID productId, String optionSignature, ProductVariantStatus status);
}
