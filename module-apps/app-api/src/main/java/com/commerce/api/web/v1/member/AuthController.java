package com.commerce.api.web.v1.member;

import com.commerce.api.web.auth.Anonymous;
import com.commerce.api.web.v1.member.request.LoginRequest;
import com.commerce.api.web.v1.member.response.LoginResponse;
import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.service.MemberCredentialValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인·토큰 발급 엔드포인트다. */
@Tag(name = "인증", description = "로그인·토큰 발급")
@Anonymous
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberCredentialValidator memberCredentialValidator;
    private final JwtTokenCodec jwtTokenCodec;

    public AuthController(MemberCredentialValidator memberCredentialValidator, JwtTokenCodec jwtTokenCodec) {
        this.memberCredentialValidator = memberCredentialValidator;
        this.jwtTokenCodec = jwtTokenCodec;
    }

    @Operation(summary = "로그인", description = "이메일·비밀번호로 인증하고 액세스 토큰을 발급한다.")
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
        return LoginResponse.from(jwtTokenCodec.issue(
                member.id().toString(), Map.of("role", member.role().name())));
    }
}
