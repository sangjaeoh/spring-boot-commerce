package com.commerce.app.api.web.v1.review.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "리뷰 작성 결과")
public record ReviewCreationResponse(
        @Schema(description = "작성된 리뷰 ID(문자열)") String reviewId) {

    /** 작성된 리뷰 ID에서 응답을 만든다. */
    public static ReviewCreationResponse from(UUID reviewId) {
        return new ReviewCreationResponse(reviewId.toString());
    }
}
