package com.commerce.web.idempotency;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.web.auth.AuthUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class IdempotencyFilterTest {

    private static final String HEADER = "Idempotency-Key";

    private final MockMvc mvc;

    @MockitoSpyBean
    private IdempotencyStore store;

    IdempotencyFilterTest(WebApplicationContext context, IdempotencyFilter filter) {
        // 컨텍스트가 등록한 실제 필터 빈을 실제 MVC 필터 체인에 태운다.
        this.mvc =
                MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 problem+json 409로 거부된다")
    void duplicateKeyRejected() throws Exception {
        mvc.perform(post("/test/echo").header(HEADER, "dup-1")).andExpect(status().isOk());
        mvc.perform(post("/test/echo").header(HEADER, "dup-1"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("서로 다른 키는 각각 통과한다")
    void distinctKeysPass() throws Exception {
        mvc.perform(post("/test/echo").header(HEADER, "distinct-a")).andExpect(status().isOk());
        mvc.perform(post("/test/echo").header(HEADER, "distinct-b")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("키 없는 요청은 멱등 검사를 건너뛴다")
    void requestWithoutKeyBypasses() throws Exception {
        mvc.perform(post("/test/echo")).andExpect(status().isOk());
        mvc.perform(post("/test/echo")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("공백뿐인 키는 멱등 검사를 건너뛴다")
    void blankKeyBypasses() throws Exception {
        mvc.perform(post("/test/echo").header(HEADER, "   ")).andExpect(status().isOk());
        mvc.perform(post("/test/echo").header(HEADER, "   ")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("키 저장소 장애 시 하류로 통과시키지 않고 problem+json 503으로 거부한다(fail-closed)")
    void storeOutageFailsClosed() throws Exception {
        doThrow(new IllegalStateException("키 저장소 장애 주입")).when(store).tryBegin(anyString());

        // 장애가 통과(2xx)로 강등되지 않고 레이트리밋과 같은 503 problem+json으로 거부된다.
        mvc.perform(post("/test/echo").header(HEADER, "outage-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503));

        // 장애가 걷히면 같은 키가 정상 통과한다 — 실패한 시도가 키를 잠그지 않았다.
        org.mockito.Mockito.reset(store);
        mvc.perform(post("/test/echo").header(HEADER, "outage-1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("safe 메서드(GET)는 키가 있어도 멱등 검사를 건너뛴다")
    void safeMethodBypasses() throws Exception {
        mvc.perform(get("/test/echo").header(HEADER, "get-1")).andExpect(status().isOk());
        mvc.perform(get("/test/echo").header(HEADER, "get-1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증 주체가 다르면 같은 키도 각각 통과한다")
    void sameKeyDifferentPrincipalsPass() throws Exception {
        performAs(UUID.randomUUID(), "member-scope-1").andExpect(status().isOk());
        performAs(UUID.randomUUID(), "member-scope-1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 인증 주체의 같은 키 재요청은 409로 거부된다")
    void sameKeySamePrincipalRejected() throws Exception {
        UUID memberId = UUID.randomUUID();

        performAs(memberId, "member-scope-2").andExpect(status().isOk());
        performAs(memberId, "member-scope-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
    }

    /** 회원을 인증 주체로 실은 시큐리티 컨텍스트에서 키를 실어 요청한다. MockMvc는 호출 스레드에서 동기 실행된다. */
    private ResultActions performAs(UUID memberId, String key) throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PreAuthenticatedAuthenticationToken(
                new AuthUser(memberId, "BUYER"), null, List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
        SecurityContextHolder.setContext(context);
        try {
            return mvc.perform(post("/test/echo").header(HEADER, key));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
