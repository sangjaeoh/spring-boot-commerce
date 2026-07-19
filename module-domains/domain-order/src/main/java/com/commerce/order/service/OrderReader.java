package com.commerce.order.service;

import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.Order;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.exception.OrderErrorCode;
import com.commerce.order.exception.OrderNotFoundException;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 조회를 담당한다. */
@Service
public class OrderReader {

    private final OrderRepository orderRepository;

    public OrderReader(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * 주문을 조회한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     */
    @Transactional(readOnly = true)
    public OrderInfo getOrder(UUID orderId) {
        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderInfo.from(order);
    }

    /**
     * 본인 주문을 조회한다. 미소유는 존재 누출 방지로 미존재로 취급한다.
     *
     * @throws OrderNotFoundException 본인 주문이 없으면
     */
    @Transactional(readOnly = true)
    public OrderInfo getOrder(UUID orderId, UUID memberId) {
        Order order = orderRepository
                .findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderInfo.from(order);
    }

    /** 회원에게 미배송 결제완료 주문(PAID이면서 아직 DELIVERED가 아닌 주문)이 있는지 본다. */
    @Transactional(readOnly = true)
    public boolean hasUndeliveredPaidOrder(UUID memberId) {
        return orderRepository.existsByMemberIdAndStatusAndFulfillmentStatusNot(
                memberId, OrderStatus.PAID, FulfillmentStatus.DELIVERED);
    }

    /** 회원의 주문 목록을 최신순 페이지로 조회한다. 없으면 빈 페이지다. 같은 생성 시각은 id로 결정적 순서를 둔다. */
    @Transactional(readOnly = true)
    public Page<OrderInfo> getOrdersByMember(UUID memberId, Pageable pageable) {
        // 컬렉션 페치(lines)와 LIMIT을 한 쿼리에 섞으면 Hibernate가 전체를 로드해 메모리에서 자르므로,
        // ID 페이지를 먼저 뜨고 그 ID들만 IN으로 라인을 페치한다.
        Page<UUID> idPage = orderRepository.findIdPageByMemberId(memberId, pageable);
        return toOrderPage(idPage, pageable);
    }

    /**
     * 결제·이행 축 상태로 주문 목록을 최신순 페이지로 조회한다. 없으면 빈 페이지다. 같은 생성 시각은 id로
     * 결정적 순서를 둔다.
     *
     * <p>소유 회원을 거르지 않으므로 관리자 가드 뒤에서만 부른다.
     */
    @Transactional(readOnly = true)
    public Page<OrderInfo> getOrdersByStatus(
            OrderStatus status, FulfillmentStatus fulfillmentStatus, Pageable pageable) {
        Page<UUID> idPage = orderRepository.findIdPageByStatusAndFulfillmentStatus(status, fulfillmentStatus, pageable);
        return toOrderPage(idPage, pageable);
    }

    /**
     * 기준 시각 이전에 생성돼 아직 PENDING인 주문을 조회한다. 없으면 빈 목록이다.
     *
     * <p>소유 회원을 거르지 않으므로 시스템 스윕에서만 부른다.
     */
    @Transactional(readOnly = true)
    public List<OrderInfo> findPendingBefore(Instant cutoff) {
        return orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff).stream()
                .map(OrderInfo::from)
                .toList();
    }

    /** ID 페이지로 주문을 페치해 총건수를 유지한 Info 페이지로 옮긴다. */
    private Page<OrderInfo> toOrderPage(Page<UUID> idPage, Pageable pageable) {
        List<OrderInfo> orders = orderRepository.findByIdInOrderByCreatedAtDescIdDesc(idPage.getContent()).stream()
                .map(OrderInfo::from)
                .toList();
        return new PageImpl<>(orders, pageable, idPage.getTotalElements());
    }
}
