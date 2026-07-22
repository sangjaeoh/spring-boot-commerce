package com.commerce.api.web.v1.member;

import com.commerce.api.web.auth.Anonymous;
import com.commerce.api.web.v1.member.request.LoginRequest;
import com.commerce.api.web.v1.member.request.PasswordResetMailRequest;
import com.commerce.api.web.v1.member.request.PasswordResetRequest;
import com.commerce.api.web.v1.member.request.RefreshTokenRequest;
import com.commerce.api.web.v1.member.response.LoginResponse;
import com.commerce.api.web.v1.member.response.TokenRefreshResponse;
import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.auth.token.RefreshTokenStore;
import com.commerce.member.application.info.MemberInfo;
import com.commerce.member.application.provided.MemberCredentialValidator;
import com.commerce.member.application.provided.MemberReader;
import com.commerce.member.application.provided.PasswordResetProcessor;
import com.commerce.member.domain.MemberNotFoundException;
import com.commerce.web.exception.UnauthenticatedException;
import com.commerce.web.exception.WebErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로그인·토큰 발급·비밀번호 재설정 엔드포인트다. */
@Tag(name = "인증", description = "로그인·토큰 발급·비밀번호 재설정")
@Anonymous
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberCredentialValidator memberCredentialValidator;
    private final MemberReader memberReader;
    private final PasswordResetProcessor passwordResetProcessor;
    private final JwtTokenCodec jwtTokenCodec;
    private final RefreshTokenStore refreshTokenStore;
    private final Duration refreshTokenTtl;

    public AuthController(
            MemberCredentialValidator memberCredentialValidator,
            MemberReader memberReader,
            PasswordResetProcessor passwordResetProcessor,
            JwtTokenCodec jwtTokenCodec,
            RefreshTokenStore refreshTokenStore,
            @Value("${auth.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.memberCredentialValidator = memberCredentialValidator;
        this.memberReader = memberReader;
        this.passwordResetProcessor = passwordResetProcessor;
        this.jwtTokenCodec = jwtTokenCodec;
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Operation(summary = "로그인", description = "이메일·비밀번호로 인증하고 액세스·리프레시 토큰을 발급한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "인증됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "자격 증명 불일치",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "이미 인증된 주체의 재로그인",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        MemberInfo member = memberCredentialValidator.authenticate(request.email(), request.password());
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenStore.save(refreshToken, member.id().toString(), refreshTokenTtl);
        return LoginResponse.of(issueAccessToken(member), refreshToken);
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새 액세스 토큰을 발급한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "발급됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "무효한 리프레시 토큰(위조·만료·로그아웃·탈퇴)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String memberId = refreshTokenStore
                .findSubject(request.refreshToken())
                .orElseThrow(() -> new UnauthenticatedException(WebErrorCode.UNAUTHENTICATED));
        return TokenRefreshResponse.from(issueAccessToken(activeMember(memberId)));
    }

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 무효화한다. 이미 무효한 토큰도 성공으로 응답한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "로그아웃됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenStore.delete(request.refreshToken());
    }

    @Operation(summary = "비밀번호 재설정 메일 요청", description = "가입 이메일로 재설정 토큰 메일을 보낸다. 이메일 존재와 무관하게 성공으로 응답한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "요청 접수됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효 또는 이메일 형식 오류",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "이미 인증된 주체의 요청",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/password-reset-request")
    public void requestPasswordReset(@Valid @RequestBody PasswordResetMailRequest request) {
        passwordResetProcessor.requestReset(request.email());
    }

    @Operation(summary = "비밀번호 재설정", description = "재설정 토큰을 검증하고 새 비밀번호로 교체한다. 토큰은 1회용이다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "재설정됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효 또는 새 비밀번호 정책 위반",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "무효한 재설정 토큰(위조·만료·기사용)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "이미 인증된 주체의 요청",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/password-reset")
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        if (passwordResetProcessor.reset(request.token(), request.newPassword()).isEmpty()) {
            throw new UnauthenticatedException(WebErrorCode.UNAUTHENTICATED);
        }
    }

    /** 활성 회원을 조회하고, 탈퇴 등으로 없으면 갱신 거부(401)로 바꾼다. */
    private MemberInfo activeMember(String memberId) {
        try {
            return memberReader.getMember(UUID.fromString(memberId));
        } catch (MemberNotFoundException e) {
            throw new UnauthenticatedException(WebErrorCode.UNAUTHENTICATED);
        }
    }

    /** 회원 ID를 주체로, 역할 클레임을 실은 액세스 토큰을 발급한다. */
    private String issueAccessToken(MemberInfo member) {
        return jwtTokenCodec.issue(
                member.id().toString(), Map.of("role", member.role().name()));
    }
}
