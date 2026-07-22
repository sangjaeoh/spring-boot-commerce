package com.commerce.product.application.required;

import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductStatus;
import com.commerce.product.domain.ProductVariantStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
     * 조회한다. 키워드가 있으면 상품명 부분 일치(대소문자 무시)로, 카테고리가 있으면 소속 일치로 좁힌다.
     * 페이지 크기·총건수는 이 노출 집합 기준이다.
     */
    // 노출 술어를 파사드 후필터가 아니라 쿼리에 둬야 페이지 크기·총건수가 노출 집합과 어긋나지 않는다.
    // UUIDv7이 시간순이라 id desc가 최신 등록순이다.
    // null 키워드는 PostgreSQL이 파라미터 타입을 추론하지 못하므로(bytea 오추론) cast로 문자열을 고정한다.
    @Query("""
            select p
            from Product p
            where p.status = :status
              and p.deletedAt is null
              and (:keyword is null or lower(p.name) like lower(concat('%', cast(:keyword as string), '%')))
              and (:categoryId is null or p.categoryId = :categoryId)
              and exists (
                select 1
                from ProductVariant v
                where v.productId = p.id and v.status = :variantStatus)
            order by p.id desc
            """)
    Page<Product> findExposedPage(
            @Param("status") ProductStatus status,
            @Param("variantStatus") ProductVariantStatus variantStatus,
            @Param("keyword") @Nullable String keyword,
            @Param("categoryId") @Nullable UUID categoryId,
            Pageable pageable);

    // group by 쿼리는 Spring Data의 count 파생이 어긋나므로 노출 집합 count를 명시한다.
    String EXPOSED_COUNT_QUERY = """
            select count(p)
            from Product p
            where p.status = :status
              and p.deletedAt is null
              and (:keyword is null or lower(p.name) like lower(concat('%', cast(:keyword as string), '%')))
              and (:categoryId is null or p.categoryId = :categoryId)
              and exists (
                select 1
                from ProductVariant v
                where v.productId = p.id and v.status = :variantStatus)
            """;

    /**
     * 노출 상품 페이지를 대표가(주어진 상태 변형의 최저가) 낮은순으로 조회한다. 키워드가 있으면 상품명 부분
     * 일치(대소문자 무시)로, 카테고리가 있으면 소속 일치로 좁힌다. 대표가가 같으면 최신 등록순이다.
     */
    @Query(value = """
                    select p
                    from Product p
                    join ProductVariant v on v.productId = p.id and v.status = :variantStatus
                    where p.status = :status
                      and p.deletedAt is null
                      and (:keyword is null or lower(p.name) like lower(concat('%', cast(:keyword as string), '%')))
                      and (:categoryId is null or p.categoryId = :categoryId)
                    group by p
                    order by min(v.price) asc, p.id desc
                    """, countQuery = EXPOSED_COUNT_QUERY)
    Page<Product> findExposedPageOrderByPriceAsc(
            @Param("status") ProductStatus status,
            @Param("variantStatus") ProductVariantStatus variantStatus,
            @Param("keyword") @Nullable String keyword,
            @Param("categoryId") @Nullable UUID categoryId,
            Pageable pageable);

    /**
     * 노출 상품 페이지를 대표가(주어진 상태 변형의 최저가) 높은순으로 조회한다. 키워드가 있으면 상품명 부분
     * 일치(대소문자 무시)로, 카테고리가 있으면 소속 일치로 좁힌다. 대표가가 같으면 최신 등록순이다.
     */
    @Query(value = """
                    select p
                    from Product p
                    join ProductVariant v on v.productId = p.id and v.status = :variantStatus
                    where p.status = :status
                      and p.deletedAt is null
                      and (:keyword is null or lower(p.name) like lower(concat('%', cast(:keyword as string), '%')))
                      and (:categoryId is null or p.categoryId = :categoryId)
                    group by p
                    order by min(v.price) desc, p.id desc
                    """, countQuery = EXPOSED_COUNT_QUERY)
    Page<Product> findExposedPageOrderByPriceDesc(
            @Param("status") ProductStatus status,
            @Param("variantStatus") ProductVariantStatus variantStatus,
            @Param("keyword") @Nullable String keyword,
            @Param("categoryId") @Nullable UUID categoryId,
            Pageable pageable);
}
