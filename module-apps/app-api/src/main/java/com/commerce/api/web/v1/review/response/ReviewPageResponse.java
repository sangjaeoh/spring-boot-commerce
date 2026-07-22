package com.commerce.api.web.v1.review.response;

import com.commerce.review.application.info.ReviewInfo;
import com.commerce.web.paging.PaginationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "상품 리뷰 페이지")
public record ReviewPageResponse(
        @Schema(description = "리뷰 목록(최신 리뷰 우선)") List<ReviewResponse> reviews,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public ReviewPageResponse {
        reviews = List.copyOf(reviews);
    }

    /** 리뷰 조회 페이지에서 응답을 만든다. */
    public static ReviewPageResponse from(Page<ReviewInfo> page) {
        return new ReviewPageResponse(
                page.getContent().stream().map(ReviewResponse::from).toList(), PaginationResponse.from(page));
    }
}
