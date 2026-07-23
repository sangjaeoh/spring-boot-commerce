package com.commerce.product.application;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.product.application.provided.ProductImageAppender;
import com.commerce.product.application.required.ImageStore;
import com.commerce.product.application.required.ProductImageRepository;
import com.commerce.product.application.required.ProductRepository;
import com.commerce.product.domain.ProductImage;
import com.commerce.product.domain.exception.InvalidProductImageException;
import com.commerce.product.domain.exception.ProductErrorCode;
import com.commerce.product.domain.exception.ProductNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link ProductImageAppender}의 기본 구현이다. */
@Service
class DefaultProductImageAppender implements ProductImageAppender {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageStore imageStore;
    private final TransactionTemplate transactionTemplate;

    DefaultProductImageAppender(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            ImageStore imageStore,
            PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.imageStore = imageStore;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public UUID append(UUID productId, String contentType, byte[] content) {
        // 1. 상품 존재·입력 검증
        if (!productRepository.existsByIdAndDeletedAtIsNull(productId)) {
            throw new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        String extension = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw new InvalidProductImageException(ProductErrorCode.UNSUPPORTED_IMAGE_FORMAT);
        }
        if (content.length > MAX_IMAGE_BYTES) {
            throw new InvalidProductImageException(ProductErrorCode.IMAGE_TOO_LARGE);
        }
        // 2. 스토리지 보관 — 트랜잭션 밖 부작용
        String key = productId + "/" + UuidV7Generator.generate() + "." + extension;
        String url = imageStore.store(key, content, contentType);
        // 3. 메타 영속 — 정렬 순서는 현재 최댓값 + 1
        return transactionTemplate.execute(status -> {
            int nextSortOrder = productImageRepository
                    .findTopByProductIdOrderBySortOrderDesc(productId)
                    .map(image -> image.getSortOrder() + 1)
                    .orElse(0);
            return productImageRepository
                    .save(ProductImage.create(productId, key, url, nextSortOrder))
                    .getId();
        });
    }
}
