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

    // UUIDv7이 시간순이라 id desc가 최신 등록순이다.
    Page<Product> findByDeletedAtIsNullOrderByIdDesc(Pageable pageable);

    /**
     * 주어진 상품 상태이면서 미삭제이고 주어진 상태의 변형을 1개 이상 가진 상품 페이지를 최신 등록순으로
     * 조회한다.
     *
     * <p>이 노출 술어를 쿼리가 들고 있어 페이지 크기·총건수가 노출 집합과 일치한다.
     */
    // UUIDv7이 시간순이라 id desc가 최신 등록순이다.
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
