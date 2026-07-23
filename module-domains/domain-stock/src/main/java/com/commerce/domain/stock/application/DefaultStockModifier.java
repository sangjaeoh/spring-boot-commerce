package com.commerce.domain.stock.application;

import com.commerce.domain.stock.application.provided.StockModifier;
import com.commerce.domain.stock.application.required.StockRepository;
import com.commerce.domain.stock.domain.Stock;
import com.commerce.domain.stock.domain.exception.StockErrorCode;
import com.commerce.domain.stock.domain.exception.StockNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link StockModifier}의 기본 구현이다. */
@Service
class DefaultStockModifier implements StockModifier {

    private final StockRepository stockRepository;

    DefaultStockModifier(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    @Override
    public void deduct(UUID variantId, int amount) {
        find(variantId).deduct(amount);
    }

    @Transactional
    @Override
    public void restore(UUID variantId, int amount) {
        find(variantId).restore(amount);
    }

    @Transactional
    @Override
    public void increase(UUID variantId, int amount) {
        find(variantId).increase(amount);
    }

    @Transactional
    @Override
    public void markSoldOut(UUID variantId) {
        find(variantId).markSoldOut();
    }

    @Transactional
    @Override
    public void markSellable(UUID variantId) {
        find(variantId).markSellable();
    }

    @Transactional
    @Override
    public void discontinue(UUID variantId) {
        find(variantId).discontinue();
    }

    /** 변형의 재고를 찾고 없으면 거부한다. */
    private Stock find(UUID variantId) {
        return stockRepository
                .findByVariantId(variantId)
                .orElseThrow(() -> new StockNotFoundException(StockErrorCode.STOCK_NOT_FOUND));
    }
}
