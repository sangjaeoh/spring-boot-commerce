package com.commerce.stock.application;

import com.commerce.stock.application.required.StockRepository;
import com.commerce.stock.domain.Stock;
import com.commerce.stock.domain.StockErrorCode;
import com.commerce.stock.domain.StockNotFoundException;
import com.commerce.stock.domain.StockShortageException;
import com.commerce.stock.domain.StockStatusException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 재고 수량 차감·복원·입고와 상태 전이를 담당하는 서비스다. */
@Service
public class StockModifier {

    private final StockRepository stockRepository;

    public StockModifier(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * 재고를 차감한다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockShortageException 가용 수량이 부족하면
     */
    @Transactional
    public void deduct(UUID variantId, int amount) {
        find(variantId).deduct(amount);
    }

    /**
     * 재고 수량을 되돌린다.
     *
     * @throws StockNotFoundException 재고가 없으면
     */
    @Transactional
    public void restore(UUID variantId, int amount) {
        find(variantId).restore(amount);
    }

    /**
     * 재고를 재입고한다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockStatusException 단종 재고면
     */
    @Transactional
    public void increase(UUID variantId, int amount) {
        find(variantId).increase(amount);
    }

    /**
     * 재고를 수동 품절로 둔다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockStatusException 판매 가능 상태가 아니면
     */
    @Transactional
    public void markSoldOut(UUID variantId) {
        find(variantId).markSoldOut();
    }

    /**
     * 재고를 판매 가능으로 되돌린다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockStatusException 품절 상태가 아니면
     */
    @Transactional
    public void markSellable(UUID variantId) {
        find(variantId).markSellable();
    }

    /**
     * 재고를 단종한다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockStatusException 이미 단종된 재고면
     */
    @Transactional
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
