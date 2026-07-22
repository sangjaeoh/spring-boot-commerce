package com.commerce.web.exception;

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
class ProblemDetailHandlerTest {

    private final MockMvc mvc;

    ProblemDetailHandlerTest(WebApplicationContext context) {
        this.mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("BaseException은 ErrorCode의 status·code로 problem+json 매핑된다")
    void baseExceptionMapsToErrorCodeStatus() throws Exception {
        mvc.perform(post("/test/base"))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("TEST_SAMPLE"));
    }

    @Test
    @DisplayName("@Valid 요청 DTO 위반은 400과 필드 오류로 승격된다")
    void validationFailureMapsToBadRequest() throws Exception {
        mvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("@RequestParam 제약 위반은 400 VALIDATION_FAILED로 매핑된다")
    void paramConstraintViolationMapsToBadRequest() throws Exception {
        mvc.perform(get("/test/param").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    @DisplayName("잘못된 본문(JSON 파싱 실패)은 400으로 매핑되고 500 폴백에 삼켜지지 않는다")
    void malformedBodyMapsToBadRequest() throws Exception {
        mvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("낙관락 충돌은 409로 매핑된다")
    void optimisticLockMapsToConflict() throws Exception {
        mvc.perform(get("/test/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("매핑되지 않은 예외(IllegalArgumentException 등)는 500으로 매핑된다")
    void unexpectedExceptionMapsToServerError() throws Exception {
        mvc.perform(get("/test/iae"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("표준 스프링 MVC 4xx(405 등)는 500 폴백에 삼켜지지 않는다")
    void frameworkClientErrorsAreNotSwallowed() throws Exception {
        mvc.perform(post("/test/iae")).andExpect(status().isMethodNotAllowed());
    }
}
