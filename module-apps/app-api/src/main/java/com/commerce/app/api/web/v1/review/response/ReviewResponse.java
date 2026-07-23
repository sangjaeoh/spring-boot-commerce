package com.commerce.app.api.web.v1.review.response;

import com.commerce.domain.review.application.info.ReviewInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "리뷰 한 건")
public record ReviewResponse(
        @Schema(description = "리뷰 ID") UUID id,
        @Schema(description = "별점(1~5)") int rating,
        @Schema(description = "리뷰 본문") String content,
        @Schema(description = "작성 시각") Instant writtenAt) {

    /** 리뷰 조회 모델에서 응답을 만든다. */
    public static ReviewResponse from(ReviewInfo info) {
        return new ReviewResponse(info.id(), info.rating(), info.content(), info.writtenAt());
    }
}
