package com.commerce.api.config;

import com.commerce.api.web.auth.Admin;
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
 * OpenAPI 문서(/v3/api-docs·swagger-ui)의 공통 설정이다.
 *
 * <p>엔드포인트·필드 설명은 컨트롤러·request/response에 명시하는 {@code @Operation}·{@code @ApiResponse}·
 * {@code @Schema}가 소유하고 빌드가 강제한다(architecture.md 빌드가 강제하는 불변식). 이 설정은 문서 어노테이션으로는
 * 표현하지 않는 두 공통 축만 코드에서 기계적으로 보정한다 — (1) 커스텀 리졸버가 토큰에서 주입하는 {@link AuthUser}
 * 파라미터가 요청 파라미터로 오인 문서화되는 것, (2) 인증 강제 표면({@link AuthUser} 파라미터 선언·{@link Admin})의
 * bearer 요구 표기. 둘 다 코드에서 도출하므로 엔드포인트가 늘어도 보정이 따라간다.
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

    /** 인증이 강제되는 오퍼레이션({@link AuthUser} 파라미터·{@link Admin})에 bearer 요구를 표기한다. */
    @Bean
    OperationCustomizer bearerRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            boolean adminOnly = handlerMethod.getBeanType().isAnnotationPresent(Admin.class);
            boolean authUserBound = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(parameter -> AuthUser.class.equals(parameter.getParameterType()));
            if (adminOnly || authUserBound) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
            }
            return operation;
        };
    }
}
