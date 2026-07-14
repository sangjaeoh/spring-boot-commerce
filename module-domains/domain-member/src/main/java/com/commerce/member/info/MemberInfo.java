package com.commerce.member.info;

import com.commerce.member.entity.Member;
import com.commerce.member.entity.MemberStatus;
import com.commerce.member.entity.SuspensionReason;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 회원 조회 경계 모델이다. 활성(미탈퇴) 회원만 나타내며 정지 회원·정지 사유를 포함한다.
 *
 * @param suspensionReason {@code SUSPENDED}일 때만 존재
 */
public record MemberInfo(
        UUID id,
        String email,
        String name,
        MemberStatus status,
        @Nullable SuspensionReason suspensionReason,
        Instant createdAt,
        Instant updatedAt) {

    public static MemberInfo from(Member member) {
        return new MemberInfo(
                member.getId(),
                member.getEmail().value(),
                member.getName(),
                member.getStatus(),
                member.getSuspensionReason(),
                member.getCreatedAt(),
                member.getUpdatedAt());
    }
}
