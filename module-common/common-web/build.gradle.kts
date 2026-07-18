plugins {
    id("convention.common-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(project(":module-common:common-core"))

    // 인증 필터가 토큰 검증 원자재(JwtTokenCodec)를 소비한다(web→auth 단방향, docs/architecture.md).
    implementation(project(":module-common:common-auth"))

    // 슬림 스프링 웹 프레임워크만 의존한다(임베디드 서버 스타터 회피). @RestControllerAdvice·
    // ProblemDetail·OncePerRequestFilter·MethodArgumentNotValidException을 제공한다.
    implementation(libs.spring.webmvc)

    // 낙관락 충돌(ObjectOptimisticLockingFailureException) → 409 매핑용(entity-persistence.md).
    implementation(libs.spring.orm)

    // 요청 상관관계 ID 필터가 MDC를 배선한다. 구현(logback)은 실행 앱의 로깅 스타터가 런타임 제공한다.
    implementation(libs.slf4j.api)

    // 서블릿 API는 실행 앱(서블릿 컨테이너)이 런타임에 제공하므로 compileOnly.
    compileOnly(libs.jakarta.servlet.api)

    // 공용 페이지 파라미터·메타 DTO(PaginationRequest·PaginationResponse)의 Bean Validation 제약·
    // @Schema 명세·Page 시그니처. 런타임은 실행 앱이 제공한다(validation 스타터·springdoc·data 스타터)므로 compileOnly.
    compileOnly(libs.jakarta.validation.api)
    compileOnly(libs.swagger.annotations.jakarta)
    compileOnly(libs.spring.data.commons)

    // Spring Security(무상태 JWT) 전환 준비 — 인증 필터·엔트리포인트가 컴파일 시 참조하는 원자재.
    // 런타임(Boot 오토컨피그 포함)은 실행 앱의 시큐리티 스타터가 제공하므로 compileOnly(servlet·validation 관례와 동일).
    compileOnly(libs.spring.security.web)
    compileOnly(libs.spring.security.core)

    // JWT 엔트리포인트·핸들러의 ProblemDetail JSON 직렬화(Jackson 3). 런타임은 실행 앱 제공이라 compileOnly.
    compileOnly(libs.jackson.databind)

    // 웹 계층 하네스: 최소 부트 웹 컨텍스트(@SpringBootTest)로 핸들러·필터를 실제 필터 체인에 태운다.
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.boot.starter.test)
    // security를 클래스패스에 올린 채로도 common-web 테스트가 green임을 증명한다.
    // 기본 보안체인이 /test/* 를 잠그지 않도록 시큐리티 오토컨피그를 TestWebApplication에서 배제한다.
    testImplementation(libs.spring.boot.starter.security)
    // PaginationResponse 단위 테스트가 PageImpl로 Page를 조립한다(compileOnly는 테스트 클래스패스에 안 실린다).
    testImplementation(libs.spring.data.commons)
    testRuntimeOnly(libs.junit.platform.launcher)
}
