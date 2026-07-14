package com.commerce.web.idempotency;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class IdempotencyFilterTest {

    private static final String HEADER = "Idempotency-Key";

    private final MockMvc mvc;

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
    @DisplayName("safe 메서드(GET)는 키가 있어도 멱등 검사를 건너뛴다")
    void safeMethodBypasses() throws Exception {
        mvc.perform(get("/test/echo").header(HEADER, "get-1")).andExpect(status().isOk());
        mvc.perform(get("/test/echo").header(HEADER, "get-1")).andExpect(status().isOk());
    }
}
