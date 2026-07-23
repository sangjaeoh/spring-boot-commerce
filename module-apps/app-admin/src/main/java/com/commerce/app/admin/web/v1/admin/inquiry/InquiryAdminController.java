package com.commerce.app.admin.web.v1.admin.inquiry;

import com.commerce.app.admin.web.auth.Admin;
import com.commerce.app.admin.web.v1.admin.inquiry.request.InquiryAnswerRequest;
import com.commerce.domain.inquiry.application.provided.InquiryModifier;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 문의 운영(답변 등록) 엔드포인트다. */
@Tag(name = "문의 관리", description = "상품 문의 답변 등록")
@Admin
@RestController
@RequestMapping("/api/v1/admin/inquiries")
public class InquiryAdminController {

    private final InquiryModifier inquiryModifier;

    public InquiryAdminController(InquiryModifier inquiryModifier) {
        this.inquiryModifier = inquiryModifier;
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
