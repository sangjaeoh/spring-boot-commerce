package com.commerce.domain.product.application.provided;

import com.commerce.domain.product.domain.exception.InvalidProductImageException;
import com.commerce.domain.product.domain.exception.ProductNotFoundException;
import java.util.UUID;

/** 상품 이미지 업로드를 담당하는 서비스다. */
public interface ProductImageAppender {

    /**
     * 이미지를 검증·보관하고 메타를 영속한 뒤 새 이미지 ID를 반환한다. 정렬 순서는 업로드 순(max+1)이다.
     *
     * <p>스토리지 보관은 롤백되지 않는 부작용이라 트랜잭션 밖에서 수행하고 메타 영속만 트랜잭션으로 감싼다.
     * 영속 실패 시 고아 파일이 남는 트레이드오프를 수용한다.
     *
     * @throws ProductNotFoundException 미삭제 상품이 없으면
     * @throws InvalidProductImageException 지원하지 않는 형식이거나 5MB를 초과하면
     */
    UUID append(UUID productId, String contentType, byte[] content);
}
