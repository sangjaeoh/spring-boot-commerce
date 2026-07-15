package com.commerce.api.presentation.v1.response;

import com.commerce.order.info.OrderInfo;
import java.util.List;
import org.springframework.data.domain.Page;

/** 본인 주문 목록 페이지 응답이다. */
public record OrderPageResponse(List<OrderResponse> orders, int page, int size, long totalElements, int totalPages) {

    public OrderPageResponse {
        orders = List.copyOf(orders);
    }

    public static OrderPageResponse from(Page<OrderInfo> page) {
        return new OrderPageResponse(
                page.getContent().stream().map(OrderResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
