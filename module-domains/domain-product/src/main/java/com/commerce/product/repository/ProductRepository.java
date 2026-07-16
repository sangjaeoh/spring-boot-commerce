package com.commerce.product.repository;

import com.commerce.product.entity.Product;
import com.commerce.product.entity.ProductStatus;
import com.commerce.product.entity.ProductVariantStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Product> findByIdAndStatusAndDeletedAtIsNull(UUID id, ProductStatus status);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    List<Product> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    // UUIDv7이 시간순이라 id desc가 최신 등록순이다. 노출 필터(status·변형) 없이 미삭제 상품 전부(숨김 포함).
    Page<Product> findByDeletedAtIsNullOrderByIdDesc(Pageable pageable);

    @Query("""
            select p
            from Product p
            where p.status = :status
              and p.deletedAt is null
              and exists (
                select 1
                from ProductVariant v
                where v.productId = p.id and v.status = :variantStatus)
            order by p.id desc
            """)
    Page<Product> findExposedPage(
            @Param("status") ProductStatus status,
            @Param("variantStatus") ProductVariantStatus variantStatus,
            Pageable pageable);
}
