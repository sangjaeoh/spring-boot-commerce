package com.commerce.admin.web.v1.admin.member.response;

import com.commerce.member.entity.MemberStatus;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.info.MemberInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// app-api 본인/공개 슬라이스의 동명 응답과 같은 형상의 어드민 소유 사본이다(와이어 계약 동일).
@Schema(description = "회원 상세 응답")
public record MemberResponse(
        @Schema(description = "회원 ID") UUID id,
        @Schema(description = "이메일") String email,
        @Schema(description = "이름") String name,
        @Schema(description = "계정 상태") MemberStatus status,

        @Schema(description = "정지 사유(정지 회원만)", nullable = true) @Nullable
        SuspensionReason suspensionReason,

        @Schema(description = "이메일 인증 시각. 미인증이면 없음", nullable = true) @Nullable
        Instant emailVerifiedAt,

        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "수정 시각") Instant updatedAt) {

    /** 회원 조회 모델에서 응답을 만든다. */
    public static MemberResponse from(MemberInfo member) {
        return new MemberResponse(
                member.id(),
                member.email(),
                member.name(),
                member.status(),
                member.suspensionReason(),
                member.emailVerifiedAt(),
                member.createdAt(),
                member.updatedAt());
    }
}
