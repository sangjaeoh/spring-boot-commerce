package com.commerce.product.application.provided;

import com.commerce.product.domain.exception.ProductImageNotFoundException;
import java.util.UUID;

/** 상품 이미지 삭제(물리)를 담당하는 서비스다. */
public interface ProductImageRemover {

    /**
     * 이미지 행을 지우고 스토리지에서 제거한다. 행 삭제 커밋 후 스토리지를 지우므로 스토리지 제거 실패는
     * 고아 파일만 남긴다.
     *
     * @throws ProductImageNotFoundException 상품에 속한 이미지가 없으면
     */
    void purge(UUID productId, UUID imageId);
}
