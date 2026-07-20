package com.commerce.api.web.v1.admin.member;

import com.commerce.api.web.auth.Admin;
import com.commerce.api.web.v1.admin.member.request.MemberSuspensionRequest;
import com.commerce.api.web.v1.member.response.MemberResponse;
import com.commerce.member.service.MemberModifier;
import com.commerce.member.service.MemberReader;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 회원 지정 조회·이메일 검색·관리(정지·해제)의 관리자 엔드포인트다. */
@Tag(name = "회원 관리", description = "회원 지정 조회·이메일 검색·정지·해제")
@Admin
@RestController
@RequestMapping("/api/v1/admin/members")
public class MemberAdminController {

    private final MemberReader memberReader;
    private final MemberModifier memberModifier;

    public MemberAdminController(MemberReader memberReader, MemberModifier memberModifier) {
        this.memberReader = memberReader;
        this.memberModifier = memberModifier;
    }

    @Operation(summary = "회원 조회", description = "회원 ID로 회원 상세를 계정 상태·정지 사유와 함께 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
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
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping("/{memberId}")
    public MemberResponse getMember(@Parameter(description = "회원 ID") @PathVariable UUID memberId) {
        return MemberResponse.from(memberReader.getMember(memberId));
    }

    @Operation(summary = "이메일로 회원 검색", description = "이메일 정확 일치로 활성 회원을 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
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
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping(params = "email")
    public MemberResponse searchByEmail(@Parameter(description = "검색할 이메일(정확 일치)") @RequestParam String email) {
        return MemberResponse.from(memberReader.getMemberByEmail(email));
    }

    @Operation(summary = "회원 정지", description = "회원을 정지하고 사유를 기록한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "정지됨"),
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
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{memberId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(
            @Parameter(description = "회원 ID") @PathVariable UUID memberId,
            @Valid @RequestBody MemberSuspensionRequest request) {
        memberModifier.suspend(memberId, request.reason());
    }

    @Operation(summary = "회원 정지 해제", description = "회원 정지를 해제하고 사유를 지운다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "해제됨"),
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
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{memberId}/reinstate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reinstate(@Parameter(description = "회원 ID") @PathVariable UUID memberId) {
        memberModifier.reinstate(memberId);
    }
}
