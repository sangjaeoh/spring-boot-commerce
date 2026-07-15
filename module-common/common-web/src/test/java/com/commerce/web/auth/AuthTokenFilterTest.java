package com.commerce.web.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.auth.token.JwtTokenCodec;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthTokenFilterTest {

    private final MockMvc mvc;
    private final JwtTokenCodec codec;

    AuthTokenFilterTest(WebApplicationContext context, AuthTokenFilter filter, JwtTokenCodec codec) {
        // 컨텍스트가 등록한 실제 필터 빈을 실제 MVC 필터 체인에 태운다.
        this.mvc =
                MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
        this.codec = codec;
    }

    @Test
    @DisplayName("유효한 Bearer 토큰은 인증 주체를 요청에 부착한다")
    void validBearerTokenAttachesAuthUser() throws Exception {
        UUID memberId = UUID.randomUUID();
        String token = codec.issue(memberId);

        mvc.perform(get("/test/auth-user").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(memberId.toString()));
    }

    @Test
    @DisplayName("토큰이 없어도 요청을 거부하지 않고 미인증으로 통과시킨다")
    void missingTokenPassesThroughAnonymously() throws Exception {
        mvc.perform(get("/test/auth-user"))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }

    @Test
    @DisplayName("유효하지 않은 토큰도 거부하지 않고 미인증으로 통과시킨다")
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
