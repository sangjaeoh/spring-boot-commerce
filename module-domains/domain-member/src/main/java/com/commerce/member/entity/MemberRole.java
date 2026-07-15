package com.commerce.member.entity;

/** 회원 역할이다. 관리자 오퍼레이션 가드의 판정 축이며, 가입 경로는 구매자만 만든다. */
public enum MemberRole {
    BUYER,
    ADMIN
}
