package com.commerce.web.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.auth.token.AuthRole;
import com.commerce.auth.token.JwtTokenCodec;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminOnlyInterceptorTest {

    private final MockMvc mvc;
    private final JwtTokenCodec codec;

    AdminOnlyInterceptorTest(WebApplicationContext context, AuthTokenFilter filter, JwtTokenCodec codec) {
        // 컨텍스트가 등록한 실제 필터 빈을 실제 MVC 필터 체인에 태운다.
        this.mvc =
                MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
        this.codec = codec;
    }

    @Test
    @DisplayName("관리자 토큰은 @AdminOnly 핸들러를 통과한다")
    void adminTokenPassesAdminOnlyHandler() throws Exception {
        String token = codec.issue(UUID.randomUUID(), AuthRole.ADMIN);

        mvc.perform(get("/test/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-ok"));
    }

    @Test
    @DisplayName("구매자 토큰의 @AdminOnly 요청은 403 FORBIDDEN으로 거부한다")
    void buyerTokenRejectedWith403() throws Exception {
        String token = codec.issue(UUID.randomUUID(), AuthRole.BUYER);

        mvc.perform(get("/test/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("미인증 @AdminOnly 요청은 401 UNAUTHENTICATED로 거부한다")
    void unauthenticatedRejectedWith401() throws Exception {
        mvc.perform(get("/test/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("@AdminOnly가 없는 핸들러는 미인증 요청도 가드하지 않는다")
    void nonAdminHandlerUnaffected() throws Exception {
        mvc.perform(get("/test/echo")).andExpect(status().isOk());
    }
}
