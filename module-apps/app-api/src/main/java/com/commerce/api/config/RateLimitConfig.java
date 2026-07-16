package com.commerce.api.config;

import com.commerce.web.ratelimit.LoginRateLimitFilter;
import com.commerce.web.ratelimit.LoginRateLimitStore;
import java.time.Duration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 로그인 표면에만 레이트리밋 필터를 붙인다.
 *
 * <p>필터는 {@code /api/v1/auth/login}에만 등록해 로그인 무제한 시도를 막고 다른 엔드포인트는 건드리지 않는다.
 * 키 설계·창·한도·fail-closed 근거는 REQUIREMENTS.md 제약·전제가 소유한다.
 */
@Configuration
public class RateLimitConfig {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilter(LoginRateLimitStore store) {
        FilterRegistrationBean<LoginRateLimitFilter> registration =
                new FilterRegistrationBean<>(new LoginRateLimitFilter(store, MAX_ATTEMPTS, WINDOW));
        registration.addUrlPatterns(LOGIN_PATH);
        // 시큐리티 헤더 필터(가장 바깥) 바로 안에서 돌아, 인증·멱등 등 나머지 필터보다 먼저 초과 시도를 거부한다.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
