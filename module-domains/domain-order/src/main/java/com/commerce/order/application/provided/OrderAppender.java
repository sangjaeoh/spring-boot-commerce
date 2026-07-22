package com.commerce.order.application.provided;

import com.commerce.order.domain.Address;
import com.commerce.order.domain.InvalidOrderException;
import com.commerce.order.domain.OrderLineSnapshot;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 생성을 담당하는 서비스다. */
public interface OrderAppender {

    /**
     * 주문을 생성하고 새 주문 ID를 반환한다.
     *
     * @throws InvalidOrderException 라인이 없거나 할인 불변식을 어기면
     */
    UUID place(
            UUID memberId,
            List<OrderLineSnapshot> lineSnapshots,
            Address shippingAddress,
            Money discountAmount,
            Money shippingFee,
            @Nullable UUID issuedCouponId);
}
