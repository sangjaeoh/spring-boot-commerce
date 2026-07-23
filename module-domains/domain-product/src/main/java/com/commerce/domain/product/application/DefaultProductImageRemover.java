package com.commerce.domain.product.application;

import com.commerce.domain.product.application.provided.ProductImageRemover;
import com.commerce.domain.product.application.required.ImageStore;
import com.commerce.domain.product.application.required.ProductImageRepository;
import com.commerce.domain.product.domain.ProductImage;
import com.commerce.domain.product.domain.exception.ProductErrorCode;
import com.commerce.domain.product.domain.exception.ProductImageNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link ProductImageRemover}의 기본 구현이다. */
@Service
class DefaultProductImageRemover implements ProductImageRemover {

    private final ProductImageRepository productImageRepository;
    private final ImageStore imageStore;
    private final TransactionTemplate transactionTemplate;

    DefaultProductImageRemover(
            ProductImageRepository productImageRepository,
            ImageStore imageStore,
            PlatformTransactionManager transactionManager) {
        this.productImageRepository = productImageRepository;
        this.imageStore = imageStore;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void purge(UUID productId, UUID imageId) {
        // 1. 행 삭제 — 트랜잭션
        String storageKey = transactionTemplate.execute(status -> {
            ProductImage image = productImageRepository
                    .findByIdAndProductId(imageId, productId)
                    .orElseThrow(() -> new ProductImageNotFoundException(ProductErrorCode.IMAGE_NOT_FOUND));
            productImageRepository.delete(image);
            return image.getStorageKey();
        });
        // 2. 스토리지 제거 — 커밋 후 부작용
        if (storageKey != null) {
            imageStore.remove(storageKey);
        }
    }
}
