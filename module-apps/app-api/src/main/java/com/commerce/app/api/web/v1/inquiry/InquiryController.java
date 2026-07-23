package com.commerce.app.api.web.v1.inquiry;

import com.commerce.app.api.facade.InquiryWriteFacade;
import com.commerce.app.api.web.auth.Authenticated;
import com.commerce.app.api.web.v1.inquiry.request.InquiryRequest;
import com.commerce.app.api.web.v1.inquiry.response.InquiryCreationResponse;
import com.commerce.app.api.web.v1.inquiry.response.InquiryPageResponse;
import com.commerce.common.web.auth.AuthUser;
import com.commerce.common.web.paging.PaginationRequest;
import com.commerce.domain.inquiry.application.provided.InquiryReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 상품 문의 작성·상품별 목록 엔드포인트다. 목록은 공개, 작성은 로그인 주체 전용이다. */
@Tag(name = "상품 문의", description = "상품 문의 작성·상품별 목록 조회")
@RestController
@RequestMapping("/api/v1/products/{productId}/inquiries")
public class InquiryController {

    private final InquiryWriteFacade inquiryWriteFacade;
    private final InquiryReader inquiryReader;

    public InquiryController(InquiryWriteFacade inquiryWriteFacade, InquiryReader inquiryReader) {
        this.inquiryWriteFacade = inquiryWriteFacade;
        this.inquiryReader = inquiryReader;
    }

    @Operation(summary = "문의 작성", description = "상품에 문의를 남긴다. 비밀글이면 작성자·관리자만 내용을 열람한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "작성됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효 또는 본문 범위 위반",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @Authenticated
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InquiryCreationResponse write(
            AuthUser authUser,
            @Parameter(description = "문의 대상 상품 ID") @PathVariable UUID productId,
            @Valid @RequestBody InquiryRequest request) {
        return InquiryCreationResponse.from(
                inquiryWriteFacade.write(authUser.memberId(), productId, request.content(), request.secret()));
    }

    @Operation(
            summary = "상품 문의 목록",
            description = "상품의 문의를 최신 문의 우선 페이지로 조회한다. 인증 없이 열람할 수 있고," + " 비밀글 본문·답변은 작성자·관리자에게만 실린다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "400",
                description = "페이지 파라미터 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    // 공개 엔드포인트라 마커를 두지 않는다 — @Anonymous는 익명 전용이라 인증 열람자를 403으로 거부한다.
    @GetMapping
    public InquiryPageResponse getProductInquiries(
            @Parameter(description = "상품 ID") @PathVariable UUID productId,
            @Valid @ParameterObject PaginationRequest pagination) {
        return InquiryPageResponse.of(
                inquiryReader.getProductPage(productId, PageRequest.of(pagination.zeroBasedPage(), pagination.size())),
                currentViewer());
    }

    /** 시큐리티 컨텍스트에서 열람자를 선택적으로 읽는다. 공개 엔드포인트라 미인증이면 없다. */
    private static @Nullable AuthUser currentViewer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser;
        }
        return null;
    }
}
