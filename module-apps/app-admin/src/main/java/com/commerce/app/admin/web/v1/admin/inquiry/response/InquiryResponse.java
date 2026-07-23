package com.commerce.app.admin.web.v1.admin.inquiry.response;

import com.commerce.domain.inquiry.application.info.InquiryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "관리자용 문의 한 건. 비밀글도 전문이 실린다.")
public record InquiryResponse(
        @Schema(description = "문의 ID") UUID id,
        @Schema(description = "작성 회원 ID") UUID memberId,
        @Schema(description = "문의 대상 상품 ID") UUID productId,
        @Schema(description = "문의 본문") String content,
        @Schema(description = "비밀글 여부") boolean secret,

        @Schema(description = "관리자 답변 — 미답변이면 없다") @Nullable String answer,

        @Schema(description = "작성 시각") Instant writtenAt) {

    /** 문의 조회 모델에서 응답을 만든다. */
    public static InquiryResponse from(InquiryInfo info) {
        return new InquiryResponse(
                info.id(),
                info.memberId(),
                info.productId(),
                info.content(),
                info.secret(),
                info.answer(),
                info.writtenAt());
    }
}
