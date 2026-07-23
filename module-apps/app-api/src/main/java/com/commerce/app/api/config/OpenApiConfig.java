package com.commerce.app.api.config;

import com.commerce.common.web.auth.AuthUser;
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

/** OpenAPI 문서(/v3/api-docs·swagger-ui)의 공통 설정이다. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_JWT = "bearerAuth";

    static {
        // AuthUser는 커스텀 리졸버가 토큰에서 주입하므로 요청 파라미터로 오인 문서화되지 않게 제외한다.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthUser.class);
    }

    /** 문서 제목·버전과 bearer JWT 보안 스킴을 담은 문서 뼈대를 공급한다. */
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

    /** 인증이 강제되는 오퍼레이션({@link AuthUser} 파라미터)에 bearer 요구를 표기한다. */
    @Bean
    OperationCustomizer bearerRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            boolean authUserBound = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(parameter -> AuthUser.class.equals(parameter.getParameterType()));
            if (authUserBound) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
            }
            return operation;
        };
    }
}
