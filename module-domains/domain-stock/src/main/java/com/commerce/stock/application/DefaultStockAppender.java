package com.commerce.stock.application;

import com.commerce.stock.application.provided.StockAppender;
import com.commerce.stock.application.required.StockRepository;
import com.commerce.stock.domain.Stock;
import com.commerce.stock.domain.exception.DuplicateStockException;
import com.commerce.stock.domain.exception.StockErrorCode;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link StockAppender}의 기본 구현이다. */
@Service
class DefaultStockAppender implements StockAppender {

    private final StockRepository stockRepository;

    DefaultStockAppender(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    @Override
    public UUID create(UUID variantId, int initialQuantity) {
        if (stockRepository.existsByVariantId(variantId)) {
            throw new DuplicateStockException(StockErrorCode.DUPLICATE_STOCK);
        }
        try {
            return stockRepository
                    .saveAndFlush(Stock.create(variantId, initialQuantity))
                    .getId();
        } catch (DataIntegrityViolationException e) {
            // 선검사와 저장 사이 동시 생성 경합 방어
            throw new DuplicateStockException(StockErrorCode.DUPLICATE_STOCK);
        }
    }
}
