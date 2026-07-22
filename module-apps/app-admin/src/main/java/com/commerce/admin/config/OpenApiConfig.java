package com.commerce.admin.config;

import com.commerce.admin.web.auth.Admin;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 문서(/v3/api-docs·swagger-ui)의 공통 설정이다. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_JWT = "bearerAuth";

    /** 문서 제목·버전과 bearer JWT 보안 스킴을 담은 문서 뼈대를 공급한다. */
    @Bean
    OpenAPI commerceAdminOpenApi() {
        return new OpenAPI()
                .info(new Info().title("commerce admin API").version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_JWT,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    /** 관리자 전용 오퍼레이션({@link Admin} 컨트롤러)에 bearer 요구를 표기한다. */
    @Bean
    OperationCustomizer bearerRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            if (handlerMethod.getBeanType().isAnnotationPresent(Admin.class)) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
            }
            return operation;
        };
    }
}
