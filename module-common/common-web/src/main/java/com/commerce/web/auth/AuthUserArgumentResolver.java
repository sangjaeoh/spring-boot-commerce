package com.commerce.web.auth;

import com.commerce.web.exception.UnauthenticatedException;
import com.commerce.web.exception.WebErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link AuthUser} 컨트롤러 파라미터를 시큐리티 컨텍스트의 인증 주체로 해석하는 리졸버다. */
public final class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthUser.class.equals(parameter.getParameterType());
    }

    /**
     * 시큐리티 컨텍스트의 인증 주체를 해석해 반환한다. 파라미터 선언 자체가 인증 강제다.
     *
     * @throws UnauthenticatedException 컨텍스트에 인증 주체가 없으면(미인증 요청)
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw new UnauthenticatedException(WebErrorCode.UNAUTHENTICATED);
    }
}
