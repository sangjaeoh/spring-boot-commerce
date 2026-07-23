package com.commerce.common.web.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthUserArgumentResolverTest {

    private final MockMvc mvc;

    AuthUserArgumentResolverTest(WebApplicationContext context) {
        this.mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("시큐리티 컨텍스트에 인증 주체가 있으면 AuthUser 파라미터에 주입된다")
    void authenticatedPrincipalResolvesAuthUserParameter() throws Exception {
        UUID memberId = UUID.randomUUID();
        setPrincipal(new AuthUser(memberId, "BUYER"));

        mvc.perform(get("/test/principal"))
                .andExpect(status().isOk())
                .andExpect(content().string(memberId.toString()));
    }

    @Test
    @DisplayName("인증 주체가 없으면 AuthUser 파라미터 선언 자체가 401 UNAUTHENTICATED로 거부한다")
    void missingPrincipalRejectsWith401() throws Exception {
        mvc.perform(get("/test/principal"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private static void setPrincipal(AuthUser authUser) {
        PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                authUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
