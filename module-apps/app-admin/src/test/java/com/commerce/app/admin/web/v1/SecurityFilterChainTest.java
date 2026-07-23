package com.commerce.app.admin.web.v1;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 어드민 앱 무상태 JWT 시큐리티 필터 체인의 인가 매트릭스를 한자리에서 고정하는 테스트다 — 인프라 경로 무토큰
 * 통과, 어드민 URL 무토큰 401·구매자 403·관리자 통과, 그 밖의 경로 전부 거부. 401 본문은 진입점의
 * problem+json 계약(코드·content-type·시큐리티 헤더 파리티)을 함께 검증한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityFilterChainTest extends WebIntegrationTest {

    private final MockMvc mvc;

    SecurityFilterChainTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("인프라 경로(actuator)는 토큰 없이 통과한다")
    void infraPathsAllowedWithoutToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("어드민 URL의 무토큰 요청은 401 UNAUTHENTICATED problem+json으로 거부되고 시큐리티 헤더가 실린다")
    void adminPathWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/v1/admin/stocks")
                        .param("variantIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("어드민 URL의 구매자 토큰 요청은 403 FORBIDDEN으로 거부된다")
    void adminPathRejectsBuyerTokenWith403() throws Exception {
        mvc.perform(get("/api/v1/admin/stocks")
                        .param("variantIds", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("어드민 URL의 관리자 토큰 요청은 인가를 통과한다")
    void adminPathAllowsAdminToken() throws Exception {
        mvc.perform(get("/api/v1/admin/stocks")
                        .param("variantIds", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("어드민 네임스페이스 밖 경로는 관리자 토큰이어도 전부 거부된다")
    void nonAdminPathsAreDenied() throws Exception {
        mvc.perform(get("/api/v1/products").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isForbidden());
    }
}
