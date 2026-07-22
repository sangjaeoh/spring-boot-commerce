plugins {
    id("convention.app-module")
}

dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    implementation(project(":module-common:common-messaging"))
    implementation(project(":module-domains:domain-order"))
    implementation(project(":module-domains:domain-payment"))
    implementation(project(":module-domains:domain-stock"))
    implementation(project(":module-domains:domain-coupon"))
    implementation(project(":module-domains:domain-cart"))
    // 웹훅 수신 엔드포인트(PG 결제 확정 통지)가 유일한 HTTP 표면이다.
    implementation(libs.spring.boot.starter.web)
    // 무상태 거부 기본 체인(SecurityConfig). common-web ProblemDetail 핸들러도 security 클래스를 참조한다.
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    // 스케줄 스윕의 @SchedulerLock·@EnableSchedulerLock. LockProvider 구현은 infra-redis가 배선한다.
    implementation(libs.shedlock.spring)
    // 웹훅 컨트롤러·request의 OpenAPI 명세(@Operation·@Schema). springdoc UI는 이 앱에 두지 않으므로
    // 실행 앱인 이 모듈이 어노테이션 런타임을 직접 공급한다.
    implementation(libs.swagger.annotations.jakarta)

    // 스테레오타입 빈만 조립한다(코드 참조 없음): ProblemDetail 핸들러·보안헤더/요청ID/멱등 필터·결제
    // 어댑터(PG 상태 조회)·아웃박스 발행 transport와 저장소 포트 구현·ShedLock LockProvider·JDBC 드라이버.
    runtimeOnly(project(":module-common:common-web"))
    runtimeOnly(project(":module-external:external-payment"))
    runtimeOnly(project(":module-infra:infra-messaging"))
    runtimeOnly(project(":module-infra:infra-redis"))
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.database.postgresql)
    // 통합 테스트 픽스처(회원·상품·재고 시딩)가 도메인 서비스를 직접 호출한다. main 코드는 참조하지 않는다.
    testImplementation(project(":module-domains:domain-shared"))
    testImplementation(project(":module-domains:domain-member"))
    testImplementation(project(":module-domains:domain-product"))
    // 픽스처가 올린 domain-product의 이미지 서비스가 ImageStore 포트 구현을 요구한다(테스트 컨텍스트 조립용).
    testRuntimeOnly(project(":module-external:external-storage"))
    testRuntimeOnly(libs.junit.platform.launcher)
}
