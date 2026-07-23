package com.commerce.app.api.web.v1.order.response;

import com.commerce.common.web.paging.PaginationResponse;
import com.commerce.domain.order.application.info.OrderInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "본인 주문 목록 페이지 응답")
public record OrderPageResponse(
        @Schema(description = "현재 페이지 주문 목록") List<OrderResponse> orders,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public OrderPageResponse {
        orders = List.copyOf(orders);
    }

    /** 주문 조회 페이지에서 응답을 만든다. */
    public static OrderPageResponse from(Page<OrderInfo> page) {
        return new OrderPageResponse(
                page.getContent().stream().map(OrderResponse::from).toList(), PaginationResponse.from(page));
    }
}
