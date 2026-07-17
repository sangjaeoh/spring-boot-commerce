package com.commerce.web.paging;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PaginationRequestTest {

    private final MockMvc mvc;

    PaginationRequestTest(WebApplicationContext context) {
        this.mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("1-based 페이지 번호는 0-based로 변환된다")
    void oneBasedPageConvertsToZeroBased() throws Exception {
        mvc.perform(get("/test/pagination").param("page", "3").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string("2:5"));
    }

    @Test
    @DisplayName("파라미터 생략 시 page=1·size=20으로 보정된다")
    void missingParamsFallBackToDefaults() throws Exception {
        mvc.perform(get("/test/pagination"))
                .andExpect(status().isOk())
                .andExpect(content().string("0:20"));
    }

    @Test
    @DisplayName("page=0은 400 VALIDATION_FAILED로 매핑된다")
    void zeroPageMapsToValidationFailure() throws Exception {
        mvc.perform(get("/test/pagination").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    @DisplayName("size=0은 400 VALIDATION_FAILED로 매핑된다")
    void zeroSizeMapsToValidationFailure() throws Exception {
        mvc.perform(get("/test/pagination").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("상한(100) 초과 size는 400 VALIDATION_FAILED로 매핑된다")
    void oversizedSizeMapsToValidationFailure() throws Exception {
        mvc.perform(get("/test/pagination").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }
}
