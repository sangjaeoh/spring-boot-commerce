package com.commerce.app.admin.web.v1.admin.inquiry;

import com.commerce.app.admin.web.auth.Admin;
import com.commerce.app.admin.web.v1.admin.inquiry.request.InquiryAnswerRequest;
import com.commerce.app.admin.web.v1.admin.inquiry.response.InquiryPageResponse;
import com.commerce.common.web.paging.PaginationRequest;
import com.commerce.domain.inquiry.application.provided.InquiryModifier;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 문의 운영(답변 등록·목록 조회) 엔드포인트다. */
@Tag(name = "문의 관리", description = "상품 문의 답변 등록·답변 여부별 목록 조회")
@Admin
@RestController
@RequestMapping("/api/v1/admin/inquiries")
public class InquiryAdminController {

    private final InquiryModifier inquiryModifier;
    private final InquiryReader inquiryReader;

    public InquiryAdminController(InquiryModifier inquiryModifier, InquiryReader inquiryReader) {
        this.inquiryModifier = inquiryModifier;
        this.inquiryReader = inquiryReader;
    }

    @Operation(summary = "문의 목록 조회", description = "답변 여부로 필터한 문의 목록을 최신순 페이지로 조회한다. 비밀글도 전문이 실린다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문의 목록 페이지"),
        @ApiResponse(
                responseCode = "400",
                description = "답변 필터·페이지 파라미터 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public InquiryPageResponse getInquiries(
            @Parameter(description = "답변 완료 필터 — 기본은 미답변 목록") @RequestParam(defaultValue = "false") boolean answered,
            @Valid @ParameterObject PaginationRequest pagination) {
        return InquiryPageResponse.from(inquiryReader.getAnswerStatusPage(
                answered, PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    @Operation(summary = "답변 등록", description = "문의에 답변을 단다. 이미 답변이 있으면 덮어쓴다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "등록됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효 또는 본문 범위 위반",
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
                description = "문의 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{inquiryId}/answer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void answer(
            @Parameter(description = "문의 ID") @PathVariable UUID inquiryId,
            @Valid @RequestBody InquiryAnswerRequest request) {
        inquiryModifier.answer(inquiryId, request.content());
    }
}
