package com.commerce.api.web.v1.member;

import com.commerce.api.facade.MemberWithdrawalFacade;
import com.commerce.api.web.auth.Anonymous;
import com.commerce.api.web.auth.Authenticated;
import com.commerce.api.web.v1.member.request.EmailVerificationRequest;
import com.commerce.api.web.v1.member.request.MemberPasswordReplacementRequest;
import com.commerce.api.web.v1.member.request.MemberRegistrationRequest;
import com.commerce.api.web.v1.member.request.MemberRenameRequest;
import com.commerce.api.web.v1.member.request.MemberWithdrawalRequest;
import com.commerce.api.web.v1.member.response.MemberRegistrationResponse;
import com.commerce.api.web.v1.member.response.MemberResponse;
import com.commerce.member.application.provided.MemberModifier;
import com.commerce.member.application.provided.MemberReader;
import com.commerce.member.application.provided.MemberRegistrationProcessor;
import com.commerce.web.auth.AuthUser;
import com.commerce.web.exception.UnauthenticatedException;
import com.commerce.web.exception.WebErrorCode;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 회원 가입·이메일 인증·본인 조회·이름 변경·비밀번호 변경·탈퇴 엔드포인트다. */
@Tag(name = "회원", description = "회원 가입·이메일 인증·본인 조회·이름 변경·비밀번호 변경·탈퇴")
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberRegistrationProcessor memberRegistrationProcessor;
    private final MemberReader memberReader;
    private final MemberModifier memberModifier;
    private final MemberWithdrawalFacade memberWithdrawalFacade;

    public MemberController(
            MemberRegistrationProcessor memberRegistrationProcessor,
            MemberReader memberReader,
            MemberModifier memberModifier,
            MemberWithdrawalFacade memberWithdrawalFacade) {
        this.memberRegistrationProcessor = memberRegistrationProcessor;
        this.memberReader = memberReader;
        this.memberModifier = memberModifier;
        this.memberWithdrawalFacade = memberWithdrawalFacade;
    }

    @Operation(summary = "회원 가입", description = "회원을 가입시키고 가입된 회원 ID를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "가입됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "이미 인증된 주체의 가입",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이메일 중복",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @Anonymous
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberRegistrationResponse register(@Valid @RequestBody MemberRegistrationRequest request) {
        UUID memberId = memberRegistrationProcessor.register(request.email(), request.name(), request.password());
        return MemberRegistrationResponse.from(memberId);
    }

    @Operation(summary = "이메일 인증", description = "가입 메일로 받은 토큰을 검증하고 이메일 소유 인증을 기록한다. 토큰은 1회용이다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "인증됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "무효한 인증 토큰(위조·만료·기사용)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
        if (memberRegistrationProcessor.verifyEmail(request.token()).isEmpty()) {
            throw new UnauthenticatedException(WebErrorCode.UNAUTHENTICATED);
        }
    }

    @Operation(summary = "본인 조회", description = "토큰 주체의 회원 상세를 계정 상태·정지 사유와 함께 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @Authenticated
    @GetMapping("/me")
    public MemberResponse getMe(AuthUser authUser) {
        return MemberResponse.from(memberReader.getMember(authUser.memberId()));
    }

    @Operation(summary = "본인 이름 변경", description = "본인 표시 이름을 바꾼다. 이메일은 불변이다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "변경됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @Authenticated
    @PatchMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(AuthUser authUser, @Valid @RequestBody MemberRenameRequest request) {
        memberModifier.rename(authUser.memberId(), request.name());
    }

    @Operation(summary = "본인 비밀번호 변경", description = "현재 비밀번호를 대조하고 새 비밀번호로 교체한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "변경됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효 또는 현재 비밀번호 불일치·새 비밀번호 정책 위반",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @Authenticated
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replacePassword(AuthUser authUser, @Valid @RequestBody MemberPasswordReplacementRequest request) {
        memberModifier.replacePassword(authUser.memberId(), request.currentPassword(), request.newPassword());
    }

    @Operation(summary = "본인 탈퇴", description = "본인을 탈퇴(논리삭제) 처리한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "탈퇴됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "미배송 결제 주문이 있어 탈퇴 불가",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @Authenticated
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(AuthUser authUser, @Valid @RequestBody MemberWithdrawalRequest request) {
        memberWithdrawalFacade.withdraw(authUser.memberId(), request.reason());
    }
}
