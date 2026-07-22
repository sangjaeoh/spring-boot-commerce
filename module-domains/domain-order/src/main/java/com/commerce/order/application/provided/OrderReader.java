package com.commerce.order.application.provided;

import com.commerce.order.application.info.OrderInfo;
import com.commerce.order.domain.FulfillmentStatus;
import com.commerce.order.domain.OrderNotFoundException;
import com.commerce.order.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 주문 조회를 담당하는 서비스다. */
public interface OrderReader {

    /**
     * 주문을 조회한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     */
    OrderInfo getOrder(UUID orderId);

    /**
     * 본인 주문을 조회한다. 미소유는 존재 누출 방지로 미존재로 취급한다.
     *
     * @throws OrderNotFoundException 본인 주문이 없으면
     */
    OrderInfo getOrder(UUID orderId, UUID memberId);

    /** 회원에게 미배송 결제완료 주문(PAID이면서 아직 DELIVERED가 아닌 주문)이 있는지 본다. */
    boolean hasUndeliveredPaidOrder(UUID memberId);

    /** 회원이 결제 완료·배송 완료 주문으로 상품을 받은 적이 있는지 본다. */
    boolean hasDeliveredProduct(UUID memberId, UUID productId);

    /** 회원의 주문 목록을 최신순 페이지로 조회한다. 없으면 빈 페이지다. 같은 생성 시각은 id로 결정적 순서를 둔다. */
    Page<OrderInfo> getOrdersByMember(UUID memberId, Pageable pageable);

    /**
     * 결제·이행 축 상태로 주문 목록을 최신순 페이지로 조회한다. 없으면 빈 페이지다. 같은 생성 시각은 id로
     * 결정적 순서를 둔다.
     *
     * <p>소유 회원을 거르지 않으므로 관리자 가드 뒤에서만 부른다.
     */
    Page<OrderInfo> getOrdersByStatus(OrderStatus status, FulfillmentStatus fulfillmentStatus, Pageable pageable);

    /**
     * 기준 시각 이전에 생성돼 아직 PENDING인 주문을 조회한다. 없으면 빈 목록이다.
     *
     * <p>소유 회원을 거르지 않으므로 시스템 스윕에서만 부른다.
     */
    List<OrderInfo> findPendingBefore(Instant cutoff);
}
