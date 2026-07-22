package com.commerce.stock.application.provided;

import com.commerce.stock.domain.StockNotFoundException;
import com.commerce.stock.domain.StockShortageException;
import com.commerce.stock.domain.StockStatusException;
import java.util.UUID;

/** 재고 수량 차감·복원·입고와 상태 전이를 담당하는 서비스다. */
public interface StockModifier {

    /**
     * 재고를 차감한다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockShortageException 가용 수량이 부족하면
     */
    void deduct(UUID variantId, int amount);

    /**
     * 재고 수량을 되돌린다.
     *
     * @throws StockNotFoundException 재고가 없으면
     */
    void restore(UUID variantId, int amount);

    /**
     * 재고를 재입고한다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockStatusException 단종 재고면
     */
    void increase(UUID variantId, int amount);

    /**
     * 재고를 수동 품절로 둔다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockStatusException 판매 가능 상태가 아니면
     */
    void markSoldOut(UUID variantId);

    /**
     * 재고를 판매 가능으로 되돌린다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockStatusException 품절 상태가 아니면
     */
    void markSellable(UUID variantId);

    /**
     * 재고를 단종한다.
     *
     * @throws StockNotFoundException 재고가 없으면
     * @throws StockStatusException 이미 단종된 재고면
     */
    void discontinue(UUID variantId);
}
