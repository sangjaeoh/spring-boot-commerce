package com.commerce.product.application;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.product.application.required.ImageStore;
import com.commerce.product.application.required.ProductImageRepository;
import com.commerce.product.application.required.ProductRepository;
import com.commerce.product.domain.InvalidProductImageException;
import com.commerce.product.domain.ProductErrorCode;
import com.commerce.product.domain.ProductImage;
import com.commerce.product.domain.ProductNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 상품 이미지 업로드를 담당하는 서비스다. */
@Service
public class ProductImageAppender {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageStore imageStore;
    private final TransactionTemplate transactionTemplate;

    public ProductImageAppender(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            ImageStore imageStore,
            PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.imageStore = imageStore;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 이미지를 검증·보관하고 메타를 영속한 뒤 새 이미지 ID를 반환한다. 정렬 순서는 업로드 순(max+1)이다.
     *
     * <p>스토리지 보관은 롤백되지 않는 부작용이라 트랜잭션 밖에서 수행하고 메타 영속만 트랜잭션으로 감싼다.
     * 영속 실패 시 고아 파일이 남는 트레이드오프를 수용한다.
     *
     * @throws ProductNotFoundException 미삭제 상품이 없으면
     * @throws InvalidProductImageException 지원하지 않는 형식이거나 5MB를 초과하면
     */
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
