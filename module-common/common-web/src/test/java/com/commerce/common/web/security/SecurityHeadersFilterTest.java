package com.commerce.common.web.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class SecurityHeadersFilterTest {

    private final MockMvc mvc;

    SecurityHeadersFilterTest(WebApplicationContext context, SecurityHeadersFilter filter) {
        this.mvc =
                MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
    }

    @Test
    @DisplayName("응답에 nosniff·no-store 시큐리티 헤더가 실린다")
    void attachesSecurityHeaders() throws Exception {
        mvc.perform(get("/test/echo"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
