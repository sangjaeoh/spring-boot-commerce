package com.commerce.member.entity;

/** 회원의 계정 상태다. */
public enum MemberStatus {
    /** 정상. 가입 직후 진입하고 정지 해제가 되돌린다. */
    ACTIVE,
    /** 관리자 정지. ACTIVE에서만 진입한다. */
    SUSPENDED
}
