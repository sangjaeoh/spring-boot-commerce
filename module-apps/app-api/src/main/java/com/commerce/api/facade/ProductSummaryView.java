package com.commerce.api.facade;

import com.commerce.core.money.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 카탈로그 목록의 상품별 합성 뷰다. 대표가(fromPrice)는 ACTIVE 변형 최저가, 품절(soldOut)은 ACTIVE 변형이
 * 있으나 주문가능이 하나도 없을 때다.
 */
public record ProductSummaryView(
        UUID id, String name, @Nullable Money fromPrice, boolean soldOut) {}
