package com.commerce.domain.order.application.required;

import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);

    /**
     * 이행축 상태를 읽어 취소·반품 개시를 가르는 쓰기 앞에서 사용한다(FOR UPDATE). {@code ship} 등 이행
     * 쓰기 측의 {@link #findByIdForShare(UUID)}와 짝을 이뤄, 별도 애그리거트가 된 이행축 상태를 두 축이
     * 상대 커밋 이후 값으로 일관되게 보도록 직렬화한다 — 그러지 않으면 취소 개시가 마커를 커밋한 뒤에도
     * 동시 출고가 그 마커를 못 보고 통과할 수 있다(반대 순서도 마찬가지).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    /**
     * 이행축 상태를 읽어 취소·반품 개시를 가르는 쓰기 앞에서 사용한다(FOR UPDATE). 본인 소유 검증이 필요한
     * 호출 경로용이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.memberId = :memberId")
    Optional<Order> findByIdAndMemberIdForUpdate(@Param("id") UUID id, @Param("memberId") UUID memberId);

    /**
     * 결제·취소 축 상태를 읽어 이행 전이(주로 {@code ship})를 가르는 쓰기 앞에서 사용한다(FOR SHARE).
     * {@link #findByIdForUpdate(UUID)}를 참조.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForShare(@Param("id") UUID id);

    /** 결제 완료·미배송(이행 행 없음도 미배송으로 취급) 주문이 있는지 본다. */
    @Query("""
            select count(o) > 0
            from Order o
            where o.memberId = :memberId
              and o.status = com.commerce.domain.order.domain.OrderStatus.PAID
              and not exists (
                  select 1 from Fulfillment f
                  where f.orderId = o.id and f.status = com.commerce.domain.order.domain.FulfillmentStatus.DELIVERED
              )
            """)
    boolean existsUndeliveredPaidByMemberId(@Param("memberId") UUID memberId);

    /** 결제 완료·배송 완료 주문에 해당 상품 라인이 있는지 확인한다. */
    // 파생 쿼리로는 라인·이행 조인 조건까지 이름이 비대해져 @Query를 쓴다(architecture.md 쿼리 선택 기준).
    // 애그리거트 간 참조는 ID만 두므로(FK 없음) 멀티 루트 JPQL로 Fulfillment를 조인한다.
    @Query("""
            select count(o) > 0
            from Order o join o.lines l, Fulfillment f
            where o.memberId = :memberId
              and l.productId = :productId
              and o.status = com.commerce.domain.order.domain.OrderStatus.PAID
              and f.orderId = o.id
              and f.status = com.commerce.domain.order.domain.FulfillmentStatus.DELIVERED
            """)
    boolean existsDeliveredLineByMemberIdAndProductId(
            @Param("memberId") UUID memberId, @Param("productId") UUID productId);

    /** 회원의 주문 ID 페이지를 최신순으로 조회한다. */
    @Query("""
            select o.id
            from Order o
            where o.memberId = :memberId
            order by o.createdAt desc, o.id desc
            """)
    Page<UUID> findIdPageByMemberId(@Param("memberId") UUID memberId, Pageable pageable);

    /**
     * 결제·이행 축 상태가 모두 일치하는 주문의 ID 페이지를 최신순으로 조회한다. 이행 행이 없으면
     * NOT_STARTED로 취급한다(PENDING 주문의 정상 상태).
     */
    @Query("""
            select o.id
            from Order o
            where o.status = :status
              and (
                  (:fulfillmentStatus = com.commerce.domain.order.domain.FulfillmentStatus.NOT_STARTED
                      and not exists (select 1 from Fulfillment f where f.orderId = o.id))
                  or exists (
                      select 1 from Fulfillment f
                      where f.orderId = o.id and f.status = :fulfillmentStatus
                  )
              )
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
