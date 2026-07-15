package com.commerce.web.config;

import com.commerce.web.auth.AdminOnlyInterceptor;
import com.commerce.web.auth.AuthUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 인증 주체 아규먼트 리졸버와 관리자 가드 인터셉터를 MVC에 등록한다. */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthUserArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminOnlyInterceptor());
    }
}
