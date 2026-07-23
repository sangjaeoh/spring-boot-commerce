package com.commerce.query.order;

import com.commerce.domain.order.domain.OrderStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 관리자 주문 검색을 담당하는 서비스다. */
public interface OrderSearchReader {

    /**
     * 활성 회원의 이메일(정확 일치)과 주문 상태로 필터한 주문 페이지를 최신 주문 우선으로 조회한다.
     *
     * <p>상태가 없으면 전 상태를 반환한다. 탈퇴 회원의 이메일은 빈 페이지다.
     */
    Page<OrderSearchInfo> getMemberOrderPage(String email, @Nullable OrderStatus status, Pageable pageable);
}
