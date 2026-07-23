package com.commerce.query.order;

import com.commerce.domain.member.domain.Email;
import com.commerce.domain.member.domain.Member;
import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderSearchReader}의 기본 구현이다. */
@Service
class DefaultOrderSearchReader implements OrderSearchReader {

    // UUIDv7 ID가 생성 시각 순서라 ID 내림차순이 최신 주문 우선 정렬을 겸한다. 활성 회원 필터는
    // 소프트삭제 조회 기본(삭제 미포함)이자 부분 유니크 인덱스(ux_member_email_active)의 사용 조건이다.
    private static final String CONTENT_JPQL = """
            select o, m from Order o, Member m
            where m.id = o.memberId and m.email = :email and m.deletedAt is null %s
            order by o.id desc
            """;

    private static final String COUNT_JPQL = """
            select count(o) from Order o, Member m
            where m.id = o.memberId and m.email = :email and m.deletedAt is null %s
            """;

    private static final String STATUS_CONDITION = "and o.status = :status";

    private final EntityManager entityManager;

    DefaultOrderSearchReader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderSearchInfo> getMemberOrderPage(String email, @Nullable OrderStatus status, Pageable pageable) {
        Email emailValue = Email.of(email);
        String condition = status == null ? "" : STATUS_CONDITION;

        TypedQuery<Object[]> contentQuery = entityManager
                .createQuery(CONTENT_JPQL.formatted(condition), Object[].class)
                .setParameter("email", emailValue)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());
        TypedQuery<Long> countQuery = entityManager
                .createQuery(COUNT_JPQL.formatted(condition), Long.class)
                .setParameter("email", emailValue);
        if (status != null) {
            contentQuery.setParameter("status", status);
            countQuery.setParameter("status", status);
        }

        List<OrderSearchInfo> content = contentQuery.getResultList().stream()
                .map(row -> toInfo((Order) row[0], (Member) row[1]))
                .toList();
        return new PageImpl<>(content, pageable, countQuery.getSingleResult());
    }

    /** 조인 행을 경계 모델로 변환한다. */
    private static OrderSearchInfo toInfo(Order order, Member member) {
        return new OrderSearchInfo(
                order.getId(),
                order.getOrderNumber(),
                member.getId(),
                member.getEmail().value(),
                order.getStatus(),
                order.getFulfillmentStatus(),
                order.getPayAmount(),
                order.getCreatedAt());
    }
}
