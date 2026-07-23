package com.commerce.domain.product.application.provided;

import com.commerce.domain.product.application.info.ProductInfo;
import com.commerce.domain.product.domain.exception.ProductNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 상품 조회를 담당하는 서비스다. */
public interface ProductReader {

    /**
     * 활성 상품을 조회한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     */
    ProductInfo getProduct(UUID productId);

    /**
     * 카탈로그 노출 상품을 조회한다. 노출은 판매중({@code ON_SALE})·미삭제 상품이다.
     *
     * <p>공개 상세 표면이 소비한다(카탈로그와 같은 상품 게이트). 존재하되 숨김({@code HIDDEN})·삭제인 상품은
     * 미존재와 같은 예외로 은닉해 존재를 누출하지 않는다.
     *
     * @throws ProductNotFoundException 노출 상품이 없으면(미존재·숨김·삭제 포함)
     */
    ProductInfo getExposedProduct(UUID productId);

    /** 주어진 ID들 중 활성 상품을 조회한다. */
    List<ProductInfo> getProducts(Collection<UUID> productIds);

    /**
     * 카탈로그 노출 상품 페이지를 최신 등록순으로 조회한다. 노출은 판매중·미삭제·{@code ACTIVE} 변형 1개 이상인
     * 상품이다. 키워드가 있으면 상품명 부분 일치(대소문자 무시)로, 카테고리가 있으면 소속 일치로 좁힌다.
     */
    Page<ProductInfo> getExposedPage(@Nullable String keyword, @Nullable UUID categoryId, Pageable pageable);

    /** 카탈로그 노출 상품 페이지를 대표가(ACTIVE 변형 최저가) 낮은순으로 조회한다. 키워드·카테고리 의미는 {@link #getExposedPage}와 같다. */
    Page<ProductInfo> getExposedPageOrderByPriceAsc(
            @Nullable String keyword, @Nullable UUID categoryId, Pageable pageable);

    /** 카탈로그 노출 상품 페이지를 대표가(ACTIVE 변형 최저가) 높은순으로 조회한다. 키워드·카테고리 의미는 {@link #getExposedPage}와 같다. */
    Page<ProductInfo> getExposedPageOrderByPriceDesc(
            @Nullable String keyword, @Nullable UUID categoryId, Pageable pageable);

    /**
     * 미삭제 상품 페이지를 최신 등록순으로 조회한다. 노출 여부·변형 유무와 무관하게 숨김({@code HIDDEN})을 포함한다.
     *
     * <p>관리자 상품 관리 표면이 소비한다(숨김 상품 발견). 카탈로그({@link #getExposedPage})와 달리 노출 필터가 없다.
     */
    Page<ProductInfo> getPage(Pageable pageable);
}
