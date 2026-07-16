package com.commerce.web.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("헤더가 없으면 UUID를 발급해 MDC에 싣고 응답 헤더로 되돌리며, 종료 후 MDC를 정리한다")
    void issuesIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(seenInChain.get()).isNotBlank();
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(seenInChain.get());
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("형식에 맞는 클라이언트 헤더는 그대로 수용해 상관관계를 잇는다")
    void acceptsWellFormedClientHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader(RequestIdFilter.HEADER, "client-abc.123_XYZ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(seenInChain.get()).isEqualTo("client-abc.123_XYZ");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-abc.123_XYZ");
    }

    @Test
    @DisplayName("허용 밖 문자·과길이 헤더는 신뢰하지 않고 새 ID로 대체한다(로그 인젝션 방어)")
    void replacesMalformedClientHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader(RequestIdFilter.HEADER, "evil\nid=injected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(seenInChain.get()).isNotEqualTo("evil\nid=injected");
        assertThat(seenInChain.get()).matches("[A-Za-z0-9-]+");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(seenInChain.get());
    }
}
