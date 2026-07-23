package com.commerce.domain.stock.application;

import com.commerce.domain.stock.application.info.StockInfo;
import com.commerce.domain.stock.application.provided.StockReader;
import com.commerce.domain.stock.application.required.StockRepository;
import com.commerce.domain.stock.domain.Stock;
import com.commerce.domain.stock.domain.exception.StockErrorCode;
import com.commerce.domain.stock.domain.exception.StockNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link StockReader}의 기본 구현이다. */
@Service
class DefaultStockReader implements StockReader {

    private final StockRepository stockRepository;

    DefaultStockReader(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public StockInfo getByVariantId(UUID variantId) {
        Stock stock = stockRepository
                .findByVariantId(variantId)
                .orElseThrow(() -> new StockNotFoundException(StockErrorCode.STOCK_NOT_FOUND));
        return StockInfo.from(stock);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existsByVariantId(UUID variantId) {
        return stockRepository.existsByVariantId(variantId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<StockInfo> getByVariantIds(Collection<UUID> variantIds) {
        return stockRepository.findByVariantIdIn(variantIds).stream()
                .map(StockInfo::from)
                .toList();
    }
}
