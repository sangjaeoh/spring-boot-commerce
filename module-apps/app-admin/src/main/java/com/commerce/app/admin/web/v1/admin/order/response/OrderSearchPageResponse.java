package com.commerce.app.admin.web.v1.admin.order.response;

import com.commerce.common.web.paging.PaginationResponse;
import com.commerce.query.order.OrderSearchInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "관리자 주문 검색 페이지")
public record OrderSearchPageResponse(
        @Schema(description = "검색 결과 주문 목록(최신 주문 우선)") List<OrderSearchResponse> orders,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public OrderSearchPageResponse {
        orders = List.copyOf(orders);
    }

    /** 검색 조회 페이지에서 응답을 만든다. */
    public static OrderSearchPageResponse from(Page<OrderSearchInfo> page) {
        return new OrderSearchPageResponse(
                page.getContent().stream().map(OrderSearchResponse::from).toList(), PaginationResponse.from(page));
    }
}
