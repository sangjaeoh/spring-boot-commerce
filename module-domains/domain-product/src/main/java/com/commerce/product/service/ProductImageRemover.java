package com.commerce.product.service;

import com.commerce.product.entity.ProductImage;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductImageNotFoundException;
import com.commerce.product.port.ImageStore;
import com.commerce.product.repository.ProductImageRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 상품 이미지 삭제(물리)를 담당하는 서비스다. */
@Service
public class ProductImageRemover {

    private final ProductImageRepository productImageRepository;
    private final ImageStore imageStore;
    private final TransactionTemplate transactionTemplate;

    public ProductImageRemover(
            ProductImageRepository productImageRepository,
            ImageStore imageStore,
            PlatformTransactionManager transactionManager) {
        this.productImageRepository = productImageRepository;
        this.imageStore = imageStore;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 이미지 행을 지우고 스토리지에서 제거한다. 행 삭제 커밋 후 스토리지를 지우므로 스토리지 제거 실패는
     * 고아 파일만 남긴다.
     *
     * @throws ProductImageNotFoundException 상품에 속한 이미지가 없으면
     */
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
