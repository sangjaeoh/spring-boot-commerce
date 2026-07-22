package com.commerce.api.facade.view;

import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 카탈로그 목록의 상품별 합성 뷰다.
 *
 * @param fromPrice ACTIVE 변형 최저가. ACTIVE 변형이 없으면 null.
 * @param soldOut ACTIVE 변형이 있으나 주문가능이 하나도 없음. ACTIVE 변형이 없으면 false.
 * @param imageUrl 대표 이미지 URL. 이미지가 없으면 null.
 */
public record ProductSummaryView(
        UUID id,
        String name,
        @Nullable Money fromPrice,
        boolean soldOut,
        @Nullable String imageUrl) {}
