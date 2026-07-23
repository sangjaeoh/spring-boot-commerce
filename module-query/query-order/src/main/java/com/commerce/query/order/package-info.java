/**
 * 관리자 주문 검색 query 모듈이다.
 *
 * <p>회원 이메일 축(member)과 주문 상태 축(order)이 걸쳐 파사드 합성이 성립하지 않는 크로스 도메인
 * 조회를 소유한다. 모듈 밖은 provided 인터페이스와 Info record만 본다. 도메인 상태를 변경하지 않는다.
 */
@NullMarked
package com.commerce.query.order;

import org.jspecify.annotations.NullMarked;
