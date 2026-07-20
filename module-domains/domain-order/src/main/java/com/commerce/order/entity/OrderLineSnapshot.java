package com.commerce.order.entity;

import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 주문 생성 입력의 라인 스냅샷이다. 파사드가 변형·상품을 해소해 채운다.
 *
 * @param optionLabel 옵션 표시. 옵션이 없으면 {@code null}
 */
public record OrderLineSnapshot(
        UUID variantId,
        UUID productId,
        String productName,
        @Nullable String optionLabel,
        Money unitPrice,
        int quantity) {}
