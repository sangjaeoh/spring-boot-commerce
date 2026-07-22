package com.commerce.stock.application.provided;

import com.commerce.stock.domain.DuplicateStockException;
import java.util.UUID;

/** 재고 생성을 담당하는 서비스다. */
public interface StockAppender {

    /**
     * 변형에 초기 수량의 재고를 생성하고 새 재고 ID를 반환한다.
     *
     * @throws DuplicateStockException 이미 재고가 있는 변형이면
     */
    UUID create(UUID variantId, int initialQuantity);
}
