package com.commerce.web.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AuthUserArgumentResolverTest {

    private final MockMvc mvc;
    private final JwtTokenCodec codec;

    AuthUserArgumentResolverTest(WebApplicationContext context, AuthTokenFilter filter, JwtTokenCodec codec) {
        // 컨텍스트가 등록한 실제 필터 빈을 실제 MVC 필터 체인에 태운다.
        this.mvc =
                MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
        this.codec = codec;
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이 있으면 AuthUser 파라미터에 인증 주체가 주입된다")
    void validTokenResolvesAuthUserParameter() throws Exception {
        UUID memberId = UUID.randomUUID();
        String token = codec.issue(memberId);

        mvc.perform(get("/test/principal").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(memberId.toString()));
    }

    @Test
    @DisplayName("토큰이 없으면 AuthUser 파라미터 선언 자체가 401 UNAUTHENTICATED로 거부한다")
    void missingTokenRejectsWith401() throws Exception {
        mvc.perform(get("/test/principal"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("유효하지 않은 토큰도 미인증이라 401로 거부한다")
    void invalidTokenRejectsWith401() throws Exception {
        mvc.perform(get("/test/principal").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
