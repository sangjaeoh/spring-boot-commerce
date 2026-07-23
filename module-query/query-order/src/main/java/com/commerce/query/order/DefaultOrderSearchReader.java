package com.commerce.query.order;

import static com.commerce.domain.member.domain.QMember.member;
import static com.commerce.domain.order.domain.QOrder.order;

import com.commerce.domain.member.domain.Email;
import com.commerce.domain.order.domain.OrderStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
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

    private final JPAQueryFactory queryFactory;

    DefaultOrderSearchReader(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderSearchInfo> getMemberOrderPage(String email, @Nullable OrderStatus status, Pageable pageable) {
        Email emailValue = Email.of(email);

        // UUIDv7 ID가 생성 시각 순서라 ID 내림차순이 최신 주문 우선 정렬을 겸한다. 활성 회원 필터는
        // 소프트삭제 조회 기본(삭제 미포함)이자 부분 유니크 인덱스(ux_member_email_active)의 사용 조건이다.
        List<OrderSearchInfo> content = queryFactory
                .select(Projections.constructor(
                        OrderSearchInfo.class,
                        order.id,
                        order.orderNumber,
                        member.id,
                        // 이메일 등치 필터로 모든 행의 이메일이 입력값과 같다 — 컨버터 타입(Email) 경로 대신 상수로 채운다.
                        Expressions.constant(email),
                        order.status,
                        order.fulfillmentStatus,
                        order.payAmount,
                        order.createdAt))
                .from(order)
                .join(member)
                .on(member.id.eq(order.memberId))
                .where(member.email.eq(emailValue), member.deletedAt.isNull(), statusEq(status))
                .orderBy(order.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(order.count())
                .from(order)
                .join(member)
                .on(member.id.eq(order.memberId))
                .where(member.email.eq(emailValue), member.deletedAt.isNull(), statusEq(status))
                .fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /** 상태 조건이 있으면 등치 식을, 없으면 null을 반환한다 — where는 null 조건을 무시한다. */
    private static @Nullable BooleanExpression statusEq(@Nullable OrderStatus status) {
        return status == null ? null : order.status.eq(status);
    }
}
