package com.commerce.app.api.web.v1;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.app.api.web.v1.member.request.LoginRequest;
import com.commerce.app.api.web.v1.member.request.MemberRegistrationRequest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 무상태 JWT 시큐리티 필터 체인의 인가 매트릭스를 한자리에서 고정하는 테스트다 — 공개 경로 무토큰 통과, 인증 경로 무토큰
 * 401, POST /members 공개 vs GET /members/me 인증. 401 본문은 진입점의 problem+json 계약(코드·content-type·
 * 시큐리티 헤더 파리티)을 함께 검증한다. 어드민 URL 게이트는 app-admin의 동명 테스트가 소유한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityFilterChainTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;

    SecurityFilterChainTest(MockMvc mvc, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("공개 경로(상품 카탈로그·actuator)는 토큰 없이 통과한다")
    void publicPathsAllowedWithoutToken() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증 경로의 무토큰 요청은 401 UNAUTHENTICATED problem+json으로 거부되고 시큐리티 헤더가 실린다")
    void authenticatedPathWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("이미 인증된 주체의 로그인(POST /auth/login)은 403 FORBIDDEN으로 거부된다(@Anonymous 익명 전용)")
    void loginRejectsAuthenticatedSubjectWith403() throws Exception {
        LoginRequest request = new LoginRequest("user-" + UUID.randomUUID() + "@example.com", "password-123!");
        mvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("이미 인증된 주체의 회원 가입(POST /members)은 403 FORBIDDEN으로 거부된다(@Anonymous 메서드 레벨)")
    void signupRejectsAuthenticatedSubjectWith403() throws Exception {
        MemberRegistrationRequest request =
                new MemberRegistrationRequest("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        mvc.perform(post("/api/v1/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/v1/members는 공개이고 GET /api/v1/members/me는 인증을 요구한다(HttpMethod 스코프 분리)")
    void signupIsPublicWhileGetMeIsAuthenticated() throws Exception {
        MemberRegistrationRequest request =
                new MemberRegistrationRequest("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        mvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
