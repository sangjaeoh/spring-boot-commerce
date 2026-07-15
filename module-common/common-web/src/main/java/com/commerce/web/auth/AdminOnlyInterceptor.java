package com.commerce.web.auth;

import com.commerce.auth.token.AuthRole;
import com.commerce.web.exception.ForbiddenException;
import com.commerce.web.exception.UnauthenticatedException;
import com.commerce.web.exception.WebErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link AdminOnly}가 붙은 핸들러를 관리자 토큰으로만 통과시키는 인터셉터다.
 *
 * <p>{@link AuthTokenFilter}가 부착한 인증 주체의 역할 클레임만 검사한다(회원 저장소 미조회). 부착된
 * 주체가 없으면 401, 주체의 역할이 관리자가 아니면 403으로 매핑되는 예외를 던진다.
 */
public final class AdminOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod) || !isAdminOnly(handlerMethod)) {
            return true;
        }
        if (!(request.getAttribute(AuthUser.ATTRIBUTE) instanceof AuthUser user)) {
            throw new UnauthenticatedException(WebErrorCode.UNAUTHENTICATED);
        }
        if (user.role() != AuthRole.ADMIN) {
            throw new ForbiddenException(WebErrorCode.FORBIDDEN);
        }
        return true;
    }

    private static boolean isAdminOnly(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(AdminOnly.class)
                || handlerMethod.getBeanType().isAnnotationPresent(AdminOnly.class);
    }
}
