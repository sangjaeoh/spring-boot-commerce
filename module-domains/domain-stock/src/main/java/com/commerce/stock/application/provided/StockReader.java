package com.commerce.stock.application.provided;

import com.commerce.stock.application.info.StockInfo;
import com.commerce.stock.domain.exception.StockNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 재고 조회를 담당하는 서비스다. */
public interface StockReader {

    /**
     * 변형의 재고를 조회한다.
     *
     * @throws StockNotFoundException 재고가 없으면
     */
    StockInfo getByVariantId(UUID variantId);

    /** 변형의 재고 행 존재 여부를 반환한다. */
    boolean existsByVariantId(UUID variantId);

    /** 주어진 변형들의 재고 목록을 조회한다. 재고 행이 없는 변형은 결과에 없다. */
    List<StockInfo> getByVariantIds(Collection<UUID> variantIds);
}
