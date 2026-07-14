package com.commerce.stock.service;

import com.commerce.stock.entity.Stock;
import com.commerce.stock.exception.StockErrorCode;
import com.commerce.stock.exception.StockNotFoundException;
import com.commerce.stock.info.StockInfo;
import com.commerce.stock.repository.StockRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 재고 조회를 담당한다. */
@Service
public class StockReader {

    private final StockRepository stockRepository;

    public StockReader(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * 변형의 재고를 조회한다.
     *
     * @throws StockNotFoundException 재고가 없으면
     */
    @Transactional(readOnly = true)
    public StockInfo getByVariantId(UUID variantId) {
        Stock stock = stockRepository
                .findByVariantId(variantId)
                .orElseThrow(() -> new StockNotFoundException(StockErrorCode.STOCK_NOT_FOUND));
        return StockInfo.from(stock);
    }
}
