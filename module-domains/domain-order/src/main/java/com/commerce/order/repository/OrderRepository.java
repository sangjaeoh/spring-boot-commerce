package com.commerce.order.repository;

import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.Order;
import com.commerce.order.entity.OrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);

    boolean existsByMemberIdAndStatusAndFulfillmentStatusNot(
            UUID memberId, OrderStatus status, FulfillmentStatus fulfillmentStatus);

    /** 회원의 주문 ID 페이지를 최신순으로 조회한다. */
    @Query("""
            select o.id
            from Order o
            where o.memberId = :memberId
            order by o.createdAt desc, o.id desc
            """)
    Page<UUID> findIdPageByMemberId(@Param("memberId") UUID memberId, Pageable pageable);

    /** 결제·이행 축 상태가 모두 일치하는 주문의 ID 페이지를 최신순으로 조회한다. */
    @Query("""
            select o.id
            from Order o
            where o.status = :status and o.fulfillmentStatus = :fulfillmentStatus
            order by o.createdAt desc, o.id desc
            """)
    Page<UUID> findIdPageByStatusAndFulfillmentStatus(
            @Param("status") OrderStatus status,
            @Param("fulfillmentStatus") FulfillmentStatus fulfillmentStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    List<Order> findByIdInOrderByCreatedAtDescIdDesc(Collection<UUID> ids);

    @EntityGraph(attributePaths = "lines")
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant createdAt);
}
