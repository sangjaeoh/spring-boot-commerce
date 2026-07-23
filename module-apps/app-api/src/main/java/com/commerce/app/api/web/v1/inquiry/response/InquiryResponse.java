package com.commerce.app.api.web.v1.inquiry.response;

import com.commerce.domain.inquiry.application.info.InquiryInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "문의 한 건. 열람 권한 없는 비밀글은 본문·답변이 실리지 않는다.")
public record InquiryResponse(
        @Schema(description = "문의 ID") UUID id,
        @Schema(description = "비밀글 여부") boolean secret,

        @Schema(description = "문의 본문 — 열람 권한 없는 비밀글이면 없다") @Nullable
        String content,

        @Schema(description = "관리자 답변 — 미답변이거나 열람 권한 없는 비밀글이면 없다") @Nullable
        String answer,

        @Schema(description = "작성 시각") Instant writtenAt) {

    /** 문의 조회 모델에서 응답을 만든다. 열람 불가면 본문·답변을 마스킹한다. */
    public static InquiryResponse of(InquiryInfo info, boolean readable) {
        return new InquiryResponse(
                info.id(),
                info.secret(),
                readable ? info.content() : null,
                readable ? info.answer() : null,
                info.writtenAt());
    }
}
