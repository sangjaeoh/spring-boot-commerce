package com.commerce.domain.member.application.info;

import com.commerce.domain.member.domain.Member;
import com.commerce.domain.member.domain.MemberRole;
import com.commerce.domain.member.domain.MemberStatus;
import com.commerce.domain.member.domain.SuspensionReason;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 회원 조회 경계 모델이다. */
public record MemberInfo(
        UUID id,
        String email,
        String name,
        MemberRole role,
        MemberStatus status,
        @Nullable SuspensionReason suspensionReason,
        @Nullable Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt) {

    /** 회원 엔티티에서 조회 모델을 만든다. */
    public static MemberInfo from(Member member) {
        return new MemberInfo(
                member.getId(),
                member.getEmail().value(),
                member.getName(),
                member.getRole(),
                member.getStatus(),
                member.getSuspensionReason(),
                member.getEmailVerifiedAt(),
                member.getCreatedAt(),
                member.getUpdatedAt());
    }
}
