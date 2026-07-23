package com.commerce.domain.review.domain;

/** 리뷰 삭제 주체. 본인 삭제(MEMBER)는 재작성을 막지 않고, 관리자 삭제(ADMIN)는 막는다. */
public enum DeletedBy {
    MEMBER,
    ADMIN
}
