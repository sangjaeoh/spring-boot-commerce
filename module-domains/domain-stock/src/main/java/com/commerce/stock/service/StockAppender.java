package com.commerce.stock.service;

import com.commerce.stock.entity.Stock;
import com.commerce.stock.exception.DuplicateStockException;
import com.commerce.stock.exception.StockErrorCode;
import com.commerce.stock.repository.StockRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 재고 생성을 담당하는 서비스다. */
@Service
public class StockAppender {

    private final StockRepository stockRepository;

    public StockAppender(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * 변형에 초기 수량의 재고를 생성하고 새 재고 ID를 반환한다.
     *
     * @throws DuplicateStockException 이미 재고가 있는 변형이면
     */
    @Transactional
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
