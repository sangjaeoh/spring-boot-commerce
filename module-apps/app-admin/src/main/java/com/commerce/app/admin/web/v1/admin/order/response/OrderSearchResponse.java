package com.commerce.app.admin.web.v1.admin.order.response;

import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.query.order.OrderSearchInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "관리자 주문 검색 결과 한 건")
public record OrderSearchResponse(
        @Schema(description = "주문 ID") UUID id,
        @Schema(description = "주문 번호") String orderNumber,
        @Schema(description = "주문 회원 ID") UUID memberId,
        @Schema(description = "주문 회원 이메일") String memberEmail,
        @Schema(description = "결제 축 주문 상태") OrderStatus status,
        @Schema(description = "이행 축 상태") FulfillmentStatus fulfillmentStatus,
        @Schema(description = "결제 금액(원 단위)") long payAmount,
        @Schema(description = "주문 시각") Instant orderedAt) {

    /** 검색 조회 모델에서 응답을 만든다. */
    public static OrderSearchResponse from(OrderSearchInfo info) {
        return new OrderSearchResponse(
                info.orderId(),
                info.orderNumber(),
                info.memberId(),
                info.memberEmail(),
                info.status(),
                info.fulfillmentStatus(),
                info.payAmount().amount(),
                info.orderedAt());
    }
}
