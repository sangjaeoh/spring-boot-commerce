package com.commerce.api.web.v1.member.response;

import com.commerce.member.entity.MemberStatus;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.info.MemberInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 회원 상세 응답이다. 계정 상태와 정지 사유(정지 회원만)를 싣는다. */
public record MemberResponse(
        UUID id,
        String email,
        String name,
        MemberStatus status,
        @Nullable SuspensionReason suspensionReason,
        Instant createdAt,
        Instant updatedAt) {

    public static MemberResponse from(MemberInfo member) {
        return new MemberResponse(
                member.id(),
                member.email(),
                member.name(),
                member.status(),
                member.suspensionReason(),
                member.createdAt(),
                member.updatedAt());
    }
}
