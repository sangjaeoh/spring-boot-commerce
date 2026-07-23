package com.commerce.app.admin.web.v1.admin.review;

import com.commerce.app.admin.web.auth.Admin;
import com.commerce.app.admin.web.v1.admin.review.request.ReviewRemovalRequest;
import com.commerce.domain.review.application.provided.ReviewRemover;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 리뷰 모더레이션(관리자 제거) 엔드포인트다. */
@Tag(name = "리뷰 관리", description = "부적절 리뷰의 사유 기록 제거")
@Admin
@RestController
@RequestMapping("/api/v1/admin/reviews")
public class ReviewAdminController {

    private final ReviewRemover reviewRemover;

    public ReviewAdminController(ReviewRemover reviewRemover) {
        this.reviewRemover = reviewRemover;
    }

    @Operation(summary = "리뷰 제거", description = "부적절한 리뷰를 사유와 함께 제거한다. 제거된 리뷰는 공개 목록에서 빠지고 사유가 보존된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "제거됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "리뷰 없음 또는 이미 제거됨",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @Parameter(description = "리뷰 ID") @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewRemovalRequest request) {
        reviewRemover.removeByAdmin(reviewId, request.reason());
    }
}
