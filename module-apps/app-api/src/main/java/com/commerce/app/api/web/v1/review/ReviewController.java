package com.commerce.app.api.web.v1.review;

import com.commerce.app.api.facade.ReviewWriteFacade;
import com.commerce.app.api.web.auth.Authenticated;
import com.commerce.app.api.web.v1.review.request.ReviewRequest;
import com.commerce.app.api.web.v1.review.response.ReviewCreationResponse;
import com.commerce.app.api.web.v1.review.response.ReviewPageResponse;
import com.commerce.common.web.auth.AuthUser;
import com.commerce.common.web.paging.PaginationRequest;
import com.commerce.domain.review.application.provided.ReviewModifier;
import com.commerce.domain.review.application.provided.ReviewReader;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 상품 리뷰 작성·수정·삭제·목록 엔드포인트다. 목록은 공개, 쓰기는 로그인 주체 전용이다. */
@Tag(name = "상품 리뷰", description = "구매확정 상품 리뷰 작성·수정·삭제·상품별 목록 조회")
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewWriteFacade reviewWriteFacade;
    private final ReviewReader reviewReader;
    private final ReviewModifier reviewModifier;
    private final ReviewRemover reviewRemover;

    public ReviewController(
            ReviewWriteFacade reviewWriteFacade,
            ReviewReader reviewReader,
            ReviewModifier reviewModifier,
            ReviewRemover reviewRemover) {
        this.reviewWriteFacade = reviewWriteFacade;
        this.reviewReader = reviewReader;
        this.reviewModifier = reviewModifier;
        this.reviewRemover = reviewRemover;
    }

    @Operation(summary = "리뷰 작성", description = "구매확정(배송 완료)한 상품에 별점·본문 리뷰를 쓴다. 상품당 한 건이다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "작성됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효 또는 별점·본문 범위 위반",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "구매확정 이력 없음 또는 이미 작성한 상품",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @Authenticated
    @PostMapping("/products/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewCreationResponse write(
            AuthUser authUser,
            @Parameter(description = "리뷰 대상 상품 ID") @PathVariable UUID productId,
            @Valid @RequestBody ReviewRequest request) {
        return ReviewCreationResponse.from(
                reviewWriteFacade.write(authUser.memberId(), productId, request.rating(), request.content()));
    }

    @Operation(summary = "상품 리뷰 목록", description = "상품의 리뷰를 최신 리뷰 우선 페이지로 조회한다. 인증 없이 열람할 수 있다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "400",
                description = "페이지 파라미터 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    // 공개 엔드포인트라 마커를 두지 않는다 — @Anonymous는 익명 전용이라 인증 열람자를 403으로 거부한다.
    @GetMapping("/products/{productId}/reviews")
    public ReviewPageResponse getProductReviews(
            @Parameter(description = "상품 ID") @PathVariable UUID productId,
            @Valid @ParameterObject PaginationRequest pagination) {
        return ReviewPageResponse.from(
                reviewReader.getProductPage(productId, PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    @Operation(summary = "리뷰 수정", description = "본인 리뷰의 별점·본문을 고쳐 쓴다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "수정됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효 또는 별점·본문 범위 위반",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "본인 소유 리뷰 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @Authenticated
    @PatchMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revise(
            AuthUser authUser,
            @Parameter(description = "리뷰 ID") @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewRequest request) {
        reviewModifier.revise(reviewId, authUser.memberId(), request.rating(), request.content());
    }

    @Operation(summary = "리뷰 삭제", description = "본인 리뷰를 삭제한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "본인 소유 리뷰 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @Authenticated
    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(AuthUser authUser, @Parameter(description = "리뷰 ID") @PathVariable UUID reviewId) {
        reviewRemover.remove(reviewId, authUser.memberId());
    }
}
