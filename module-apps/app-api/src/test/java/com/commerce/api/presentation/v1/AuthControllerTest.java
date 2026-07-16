package com.commerce.api.presentation.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.presentation.v1.request.LoginRequest;
import com.commerce.api.presentation.v1.response.LoginResponse;
import com.commerce.auth.token.AuthRole;
import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.auth.token.TokenClaims;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import com.commerce.member.service.MemberRemover;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthControllerTest extends WebIntegrationTest {

    private static final String PASSWORD = "password-123!";

    // RateLimitConfig의 로그인 창당 한도와 같은 값. 초과 시도가 429로 거부되는지 검증한다.
    private static final int LOGIN_MAX_ATTEMPTS = 10;

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;
    private final MemberRemover memberRemover;
    private final JwtTokenCodec jwtTokenCodec;

    AuthControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            MemberModifier memberModifier,
            MemberRemover memberRemover,
            JwtTokenCodec jwtTokenCodec) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.memberModifier = memberModifier;
        this.memberRemover = memberRemover;
        this.jwtTokenCodec = jwtTokenCodec;
    }

    @Test
    @DisplayName("가입한 자격증명으로 로그인하면 회원 ID를 주체로, BUYER를 역할로 실은 Bearer 액세스 토큰이 발급된다")
    void loginIssuesTokenWithMemberIdSubject() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", PASSWORD);

        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = objectMapper.readValue(body, LoginResponse.class).accessToken();
        assertThat(accessToken.split("\\.")).hasSize(3);
        assertThat(jwtTokenCodec.verify(accessToken)).contains(new TokenClaims(memberId, AuthRole.BUYER));
    }

    @Test
    @DisplayName("잘못된 패스워드 로그인은 401 MEMBER_INVALID_CREDENTIALS로 거부된다")
    void loginRejectsWrongPassword() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        memberAppender.register(email, "테스터", PASSWORD);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("미존재 이메일 로그인은 잘못된 패스워드와 동일하게 401로 거부된다")
    void loginRejectsUnknownEmail() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("탈퇴 회원 로그인은 401로 거부된다")
    void loginRejectsWithdrawnMember() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", PASSWORD);
        memberRemover.delete(memberId, WithdrawalReason.NO_LONGER_USED);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("정지 회원 로그인은 허용된다 — 차단은 담기·주문 자격 게이트가 담당한다")
    void loginAllowsSuspendedMember() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", PASSWORD);
        memberModifier.suspend(memberId, SuspensionReason.POLICY_VIOLATION);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("공백 이메일·패스워드 로그인은 400 VALIDATION_FAILED로 거부된다")
    void loginRejectsBlankCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("  ", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("한 창 안에서 로그인 시도가 한도를 넘으면 429 TOO_MANY_LOGIN_ATTEMPTS로 거부된다")
    void loginRejectsAfterTooManyAttempts() throws Exception {
        // 다른 로그인 테스트가 공유하는 기본 클라이언트 IP를 오염시키지 않도록 전용 IP로 한도를 채운다.
        String clientIp = "203.0.113.9";
        String body = objectMapper.writeValueAsString(
                new LoginRequest("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD));

        for (int attempt = 0; attempt < LOGIN_MAX_ATTEMPTS; attempt++) {
            mvc.perform(loginFrom(clientIp, body)).andExpect(status().isUnauthorized());
        }

        mvc.perform(loginFrom(clientIp, body))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                // 필터 단계에서 거부된 응답에도 시큐리티 헤더가 실린다.
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    private static MockHttpServletRequestBuilder loginFrom(String clientIp, String body) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                });
    }

    @Test
    @DisplayName("로그인 응답에 nosniff·no-store 시큐리티 헤더가 실린다")
    void loginResponseCarriesSecurityHeaders() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        memberAppender.register(email, "테스터", PASSWORD);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
