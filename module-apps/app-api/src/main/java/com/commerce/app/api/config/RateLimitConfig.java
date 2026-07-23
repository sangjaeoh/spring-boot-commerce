package com.commerce.app.api.config;

import com.commerce.common.web.ratelimit.LoginRateLimitFilter;
import com.commerce.common.web.ratelimit.LoginRateLimitStore;
import com.commerce.common.web.ratelimit.RateLimitScope;
import java.time.Duration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** 로그인·가입 표면의 레이트리밋 필터를 배선하는 설정이다. */
@Configuration
public class RateLimitConfig {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String SIGNUP_PATH = "/api/v1/members";
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private static final RateLimitScope LOGIN_SCOPE = new RateLimitScope(
            "login:",
            "TOO_MANY_LOGIN_ATTEMPTS",
            "로그인 시도가 너무 많다. 잠시 후 다시 시도해 주세요.",
            "일시적으로 로그인을 처리할 수 없다. 잠시 후 다시 시도해 주세요.");

    private static final RateLimitScope SIGNUP_SCOPE = new RateLimitScope(
            "signup:",
            "TOO_MANY_SIGNUP_ATTEMPTS",
            "가입 시도가 너무 많다. 잠시 후 다시 시도해 주세요.",
            "일시적으로 가입을 처리할 수 없다. 잠시 후 다시 시도해 주세요.");

    /** 로그인 경로에만 걸리는 레이트리밋 필터 등록을 공급한다. */
    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilter(LoginRateLimitStore store) {
        return register(store, LOGIN_SCOPE, LOGIN_PATH);
    }

    /** 가입 경로에만 걸리는 레이트리밋 필터 등록을 공급한다. 로그인과 카운터를 공유하지 않는다. */
    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> signupRateLimitFilter(LoginRateLimitStore store) {
        return register(store, SIGNUP_SCOPE, SIGNUP_PATH);
    }

    /** 한 경로에 필터를 등록하고 실행 순서를 정한다. */
    private static FilterRegistrationBean<LoginRateLimitFilter> register(
            LoginRateLimitStore store, RateLimitScope scope, String path) {
        FilterRegistrationBean<LoginRateLimitFilter> registration =
                new FilterRegistrationBean<>(new LoginRateLimitFilter(store, scope, MAX_ATTEMPTS, WINDOW));
        registration.addUrlPatterns(path);
        // 시큐리티 헤더 필터(가장 바깥) 바로 안에서 돌아, 인증·멱등 등 나머지 필터보다 먼저 초과 시도를 거부한다.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
