package com.commerce.api.config;

import com.commerce.web.auth.AdminOnly;
import com.commerce.web.auth.AuthUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서(/v3/api-docs·swagger-ui)의 최소 보정 설정이다.
 *
 * <p>자동 생성을 우선하고 컨트롤러에 문서 어노테이션을 두지 않는다. 보정은 자동 생성이 명백히 틀리는
 * 지점만 한다 — (1) 커스텀 리졸버가 토큰에서 주입하는 {@link AuthUser} 파라미터가 요청 파라미터로
 * 오인 문서화되는 것, (2) 인증 강제 표면({@link AuthUser} 파라미터 선언·{@link AdminOnly})이 공개
 * 엔드포인트로 보이는 것. 둘 다 코드에서 기계적으로 도출하므로 엔드포인트가 늘어도 보정이 따라간다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_JWT = "bearerAuth";

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthUser.class);
    }

    @Bean
    OpenAPI commerceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("commerce API").version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_JWT,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    /** 인증이 강제되는 오퍼레이션({@link AuthUser} 파라미터·{@link AdminOnly})에 bearer 요구를 표기한다. */
    @Bean
    OperationCustomizer bearerRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            boolean adminOnly = handlerMethod.hasMethodAnnotation(AdminOnly.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(AdminOnly.class);
            boolean authUserBound = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(parameter -> AuthUser.class.equals(parameter.getParameterType()));
            if (adminOnly || authUserBound) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
            }
            return operation;
        };
    }
}
