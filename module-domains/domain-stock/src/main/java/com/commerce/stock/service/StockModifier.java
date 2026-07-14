package com.commerce.stock.service;

import com.commerce.stock.entity.Stock;
import com.commerce.stock.exception.StockErrorCode;
import com.commerce.stock.exception.StockNotFoundException;
import com.commerce.stock.exception.StockShortageException;
import com.commerce.stock.repository.StockRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 재고 수량 차감·복원·입고와 상태 전이를 담당한다. */
@Service
public class StockModifier {

    private final StockRepository stockRepository;

    public StockModifier(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * 재고를 차감한다.
     *
     * @throws StockShortageException 가용 수량이 부족하면
     */
    @Transactional
    public void deduct(UUID variantId, int amount) {
        find(variantId).deduct(amount);
    }

    /** 재고 수량을 되돌린다. */
    @Transactional
    public void restore(UUID variantId, int amount) {
        find(variantId).restore(amount);
    }

    /** 재고를 재입고한다. */
    @Transactional
    public void increase(UUID variantId, int amount) {
        find(variantId).increase(amount);
    }

    /** 재고를 수동 품절로 둔다. */
    @Transactional
    public void markSoldOut(UUID variantId) {
        find(variantId).markSoldOut();
    }

    /** 재고를 판매 가능으로 되돌린다. */
    @Transactional
    public void markSellable(UUID variantId) {
        find(variantId).markSellable();
    }

    /** 재고를 단종한다. */
    @Transactional
    public void discontinue(UUID variantId) {
        find(variantId).discontinue();
    }

    private Stock find(UUID variantId) {
        return stockRepository
                .findByVariantId(variantId)
                .orElseThrow(() -> new StockNotFoundException(StockErrorCode.STOCK_NOT_FOUND));
    }
}
