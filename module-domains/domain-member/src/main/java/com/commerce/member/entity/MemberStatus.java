package com.commerce.member.entity;

/** 회원의 계정 상태다. 탈퇴는 이 축이 아니라 {@code deletedAt}으로 표현한다. */
public enum MemberStatus {
    ACTIVE,
    SUSPENDED
}
