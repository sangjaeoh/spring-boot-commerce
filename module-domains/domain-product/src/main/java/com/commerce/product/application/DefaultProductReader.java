package com.commerce.product.application;

import com.commerce.product.application.info.ProductInfo;
import com.commerce.product.application.provided.ProductReader;
import com.commerce.product.application.required.ProductRepository;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductErrorCode;
import com.commerce.product.domain.ProductNotFoundException;
import com.commerce.product.domain.ProductStatus;
import com.commerce.product.domain.ProductVariantStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProductReader}의 기본 구현이다. */
@Service
class DefaultProductReader implements ProductReader {

    private final ProductRepository productRepository;

    DefaultProductReader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public ProductInfo getProduct(UUID productId) {
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    @Override
    public ProductInfo getExposedProduct(UUID productId) {
        Product product = productRepository
                .findByIdAndStatusAndDeletedAtIsNull(productId, ProductStatus.ON_SALE)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductInfo> getProducts(Collection<UUID> productIds) {
        return productRepository.findByIdInAndDeletedAtIsNull(productIds).stream()
                .map(ProductInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ProductInfo> getExposedPage(@Nullable String keyword, @Nullable UUID categoryId, Pageable pageable) {
        return productRepository
                .findExposedPage(ProductStatus.ON_SALE, ProductVariantStatus.ACTIVE, keyword, categoryId, pageable)
                .map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ProductInfo> getExposedPageOrderByPriceAsc(
            @Nullable String keyword, @Nullable UUID categoryId, Pageable pageable) {
        return productRepository
                .findExposedPageOrderByPriceAsc(
                        ProductStatus.ON_SALE, ProductVariantStatus.ACTIVE, keyword, categoryId, pageable)
                .map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ProductInfo> getExposedPageOrderByPriceDesc(
            @Nullable String keyword, @Nullable UUID categoryId, Pageable pageable) {
        return productRepository
                .findExposedPageOrderByPriceDesc(
                        ProductStatus.ON_SALE, ProductVariantStatus.ACTIVE, keyword, categoryId, pageable)
                .map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ProductInfo> getPage(Pageable pageable) {
        return productRepository.findByDeletedAtIsNullOrderByIdDesc(pageable).map(ProductInfo::from);
    }
}
