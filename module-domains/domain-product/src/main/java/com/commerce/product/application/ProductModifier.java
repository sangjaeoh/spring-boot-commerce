package com.commerce.product.application;

import com.commerce.product.application.required.CategoryRepository;
import com.commerce.product.application.required.ProductRepository;
import com.commerce.product.domain.CategoryNotFoundException;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductErrorCode;
import com.commerce.product.domain.ProductNotFoundException;
import com.commerce.product.domain.ProductStatusException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 노출 전환·상품명·설명·분류 변경을 담당하는 서비스다. */
@Service
public class ProductModifier {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductModifier(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * 카테고리를 지정한다. null이면 미분류로 해제한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     * @throws CategoryNotFoundException 지정한 활성 카테고리가 없으면
     */
    @Transactional
    public void assignCategory(UUID productId, @Nullable UUID categoryId) {
        if (categoryId != null && !categoryRepository.existsByIdAndDeletedAtIsNull(categoryId)) {
            throw new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        find(productId).assignCategory(categoryId);
    }

    /**
     * 상품을 노출한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     * @throws ProductStatusException 숨김 상태가 아니면
     */
    @Transactional
    public void show(UUID productId) {
        find(productId).show();
    }

    /**
     * 상품을 숨긴다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     * @throws ProductStatusException 노출 상태가 아니면
     */
    @Transactional
    public void hide(UUID productId) {
        find(productId).hide();
    }

    /**
     * 상품명을 바꾼다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     */
    @Transactional
    public void rename(UUID productId, String newName) {
        find(productId).rename(newName);
    }

    /**
     * 상세 설명을 바꾼다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     */
    @Transactional
    public void changeDescription(UUID productId, @Nullable String newDescription) {
        find(productId).changeDescription(newDescription);
    }

    /** 활성 상품을 찾고 없으면 거부한다. */
    private Product find(UUID productId) {
        return productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
