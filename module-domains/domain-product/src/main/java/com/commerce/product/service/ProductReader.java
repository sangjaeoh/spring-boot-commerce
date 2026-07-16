package com.commerce.product.service;

import com.commerce.product.entity.Product;
import com.commerce.product.entity.ProductStatus;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductNotFoundException;
import com.commerce.product.info.ProductInfo;
import com.commerce.product.repository.ProductRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 조회를 담당한다. */
@Service
public class ProductReader {

    private final ProductRepository productRepository;

    public ProductReader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 활성 상품을 조회한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     */
    @Transactional(readOnly = true)
    public ProductInfo getProduct(UUID productId) {
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return ProductInfo.from(product);
    }

    /**
     * 카탈로그 노출 상품을 조회한다. 노출은 판매중({@code ON_SALE})·미삭제 상품이다.
     *
     * <p>공개 상세 표면이 소비한다(카탈로그와 같은 상품 게이트). 존재하되 숨김({@code HIDDEN})·삭제인 상품은
     * 미존재와 같은 예외로 은닉해 존재를 누출하지 않는다.
     *
     * @throws ProductNotFoundException 노출 상품이 없으면(미존재·숨김·삭제 포함)
     */
    @Transactional(readOnly = true)
    public ProductInfo getExposedProduct(UUID productId) {
        Product product = productRepository
                .findByIdAndStatusAndDeletedAtIsNull(productId, ProductStatus.ON_SALE)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return ProductInfo.from(product);
    }

    /** 주어진 ID들 중 활성 상품을 조회한다. */
    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(Collection<UUID> productIds) {
        return productRepository.findByIdInAndDeletedAtIsNull(productIds).stream()
                .map(ProductInfo::from)
                .toList();
    }

    /** 카탈로그 노출 상품 페이지를 최신 등록순으로 조회한다. 노출은 판매중·미삭제·ACTIVE 변형 1개 이상인 상품이다. */
    @Transactional(readOnly = true)
    public Page<ProductInfo> getExposedPage(Pageable pageable) {
        return productRepository
                .findExposedPage(ProductStatus.ON_SALE, ProductVariantStatus.ACTIVE, pageable)
                .map(ProductInfo::from);
    }

    /**
     * 미삭제 상품 페이지를 최신 등록순으로 조회한다. 노출 여부·변형 유무와 무관하게 숨김({@code HIDDEN})을 포함한다.
     *
     * <p>관리자 상품 관리 표면이 소비한다(숨김 상품 발견). 카탈로그({@link #getExposedPage})와 달리 노출 필터가 없다.
     */
    @Transactional(readOnly = true)
    public Page<ProductInfo> getPage(Pageable pageable) {
        return productRepository.findByDeletedAtIsNullOrderByIdDesc(pageable).map(ProductInfo::from);
    }
}
