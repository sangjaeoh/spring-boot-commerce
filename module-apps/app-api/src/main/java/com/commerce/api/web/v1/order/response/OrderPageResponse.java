package com.commerce.api.web.v1.order.response;

import com.commerce.order.info.OrderInfo;
import com.commerce.web.paging.PaginationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** 본인 주문 목록 페이지 응답이다. */
@Schema(description = "본인 주문 목록 페이지 응답")
public record OrderPageResponse(
        @Schema(description = "현재 페이지 주문 목록") List<OrderResponse> orders,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public OrderPageResponse {
        orders = List.copyOf(orders);
    }

    public static OrderPageResponse from(Page<OrderInfo> page) {
        return new OrderPageResponse(
                page.getContent().stream().map(OrderResponse::from).toList(), PaginationResponse.from(page));
    }
}
