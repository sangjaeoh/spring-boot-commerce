plugins {
    id("convention.common-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(project(":module-common:common-core"))

    // 슬림 스프링 웹 프레임워크만 의존한다(임베디드 서버 스타터 회피). @RestControllerAdvice·
    // ProblemDetail·OncePerRequestFilter·MethodArgumentNotValidException을 제공한다.
    implementation(libs.spring.webmvc)

    // 낙관락 충돌(ObjectOptimisticLockingFailureException) → 409 매핑용(entity-persistence.md).
    implementation(libs.spring.orm)

    // 서블릿 API는 실행 앱(서블릿 컨테이너)이 런타임에 제공하므로 compileOnly.
    compileOnly(libs.jakarta.servlet.api)

    // 웹 계층 하네스: 최소 부트 웹 컨텍스트(@SpringBootTest)로 핸들러·필터를 실제 필터 체인에 태운다.
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
