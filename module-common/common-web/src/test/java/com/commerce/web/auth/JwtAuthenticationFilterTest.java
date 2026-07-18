package com.commerce.web.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.auth.token.AuthRole;
import com.commerce.auth.token.JwtTokenCodec;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class JwtAuthenticationFilterTest {

    private final MockMvc mvc;
    private final JwtTokenCodec codec;

    JwtAuthenticationFilterTest(WebApplicationContext context, JwtTokenCodec codec) {
        // 하네스에는 커스텀 시큐리티 체인이 없으므로(오토컨피그 배제) 필터를 직접 만들어 MVC 필터 체인에 태운다.
        this.mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new JwtAuthenticationFilter(codec))
                .build();
        this.codec = codec;
    }

    // 하네스에는 컨텍스트를 비우는 시큐리티 체인이 없으므로 필터가 세팅한 컨텍스트를 테스트 간 직접 비운다.
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰은 인증 주체를 시큐리티 컨텍스트에 싣는다")
    void validBearerTokenPopulatesSecurityContext() throws Exception {
        UUID memberId = UUID.randomUUID();
        String token = codec.issue(memberId, AuthRole.BUYER);

        mvc.perform(get("/test/auth-user").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(memberId.toString()));
    }

    @Test
    @DisplayName("토큰이 없으면 컨텍스트를 비운 채 익명으로 통과시킨다")
    void missingTokenPassesThroughAnonymously() throws Exception {
        mvc.perform(get("/test/auth-user"))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }

    @Test
    @DisplayName("유효하지 않은 토큰도 거부하지 않고 익명으로 통과시킨다")
    void invalidTokenPassesThroughAnonymously() throws Exception {
        mvc.perform(get("/test/auth-user").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }

    @Test
    @DisplayName("Bearer 스킴이 아닌 Authorization 헤더는 무시한다")
    void nonBearerSchemeIgnored() throws Exception {
        mvc.perform(get("/test/auth-user").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwdw=="))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }
}
