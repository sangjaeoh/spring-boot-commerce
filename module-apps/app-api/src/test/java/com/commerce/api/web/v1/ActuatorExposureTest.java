package com.commerce.api.web.v1;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관측 최소셋의 노출을 검증하는 테스트다 — 메트릭 엔드포인트(커넥션 풀 포함)와 요청 상관관계 ID 헤더.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ActuatorExposureTest extends WebIntegrationTest {

    private final MockMvc mvc;

    ActuatorExposureTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("메트릭 엔드포인트가 노출되고 커넥션 풀 게이지를 포함한다")
    void metricsEndpointExposedWithConnectionPool() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics/hikaricp.connections")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("모든 응답에 요청 상관관계 ID(X-Request-Id)가 실린다")
    void responseCarriesRequestId() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(header().exists("X-Request-Id"));
    }
}
