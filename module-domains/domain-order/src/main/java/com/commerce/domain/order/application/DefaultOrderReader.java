package com.commerce.domain.order.application;

import com.commerce.domain.order.application.info.OrderInfo;
import com.commerce.domain.order.application.provided.OrderReader;
import com.commerce.domain.order.application.required.FulfillmentRepository;
import com.commerce.domain.order.application.required.OrderRepository;
import com.commerce.domain.order.domain.Fulfillment;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.order.domain.exception.OrderNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderReader}의 기본 구현이다. */
@Service
class DefaultOrderReader implements OrderReader {

    private final OrderRepository orderRepository;
    private final FulfillmentRepository fulfillmentRepository;

    DefaultOrderReader(OrderRepository orderRepository, FulfillmentRepository fulfillmentRepository) {
        this.orderRepository = orderRepository;
        this.fulfillmentRepository = fulfillmentRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public OrderInfo getOrder(UUID orderId) {
        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderInfo.of(order, fulfillmentRepository.findByOrderId(orderId).orElse(null));
    }

    @Transactional(readOnly = true)
    @Override
    public OrderInfo getOrder(UUID orderId, UUID memberId) {
        Order order = orderRepository
                .findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderInfo.of(order, fulfillmentRepository.findByOrderId(orderId).orElse(null));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasUndeliveredPaidOrder(UUID memberId) {
        return orderRepository.existsUndeliveredPaidByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasDeliveredProduct(UUID memberId, UUID productId) {
        return orderRepository.existsDeliveredLineByMemberIdAndProductId(memberId, productId);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderInfo> getOrdersByMember(UUID memberId, Pageable pageable) {
        // 컬렉션 페치(lines)와 LIMIT을 한 쿼리에 섞으면 Hibernate가 전체를 로드해 메모리에서 자르므로,
        // ID 페이지를 먼저 뜨고 그 ID들만 IN으로 라인을 페치한다.
        Page<UUID> idPage = orderRepository.findIdPageByMemberId(memberId, pageable);
        return toOrderPage(idPage, pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderInfo> getOrdersByStatus(
            OrderStatus status, FulfillmentStatus fulfillmentStatus, Pageable pageable) {
        Page<UUID> idPage = orderRepository.findIdPageByStatusAndFulfillmentStatus(status, fulfillmentStatus, pageable);
        return toOrderPage(idPage, pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderInfo> findPendingBefore(Instant cutoff) {
        // PENDING 주문은 이행 행이 없다 — 벌크 조회 없이 매번 NOT_STARTED로 합성한다.
        return orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff).stream()
                .map(order -> OrderInfo.of(order, null))
                .toList();
    }

    /** ID 페이지로 주문을 페치해 이행을 벌크 장식하고, 총건수를 유지한 Info 페이지로 옮긴다. */
    private Page<OrderInfo> toOrderPage(Page<UUID> idPage, Pageable pageable) {
        List<Order> orders = orderRepository.findByIdInOrderByCreatedAtDescIdDesc(idPage.getContent());
        Map<UUID, Fulfillment> fulfillmentsByOrderId =
                fulfillmentRepository.findByOrderIdIn(idPage.getContent()).stream()
                        .collect(Collectors.toMap(Fulfillment::getOrderId, Function.identity()));
        List<OrderInfo> infos = orders.stream()
                .map(order -> OrderInfo.of(order, fulfillmentsByOrderId.get(order.getId())))
                .toList();
        return new PageImpl<>(infos, pageable, idPage.getTotalElements());
    }
}
