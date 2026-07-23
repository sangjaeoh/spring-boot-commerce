plugins {
    id("convention.common-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(libs.spring.boot.starter.data.jpa)

    // BaseTimeEntity의 Q타입(QBaseTimeEntity)을 생성해 jar에 포함한다 — apt는 바이너리(의존성 jar) 상위타입의
    // Q타입을 생성하지 않으므로, 도메인 엔티티 Q타입이 참조하는 상위 Q타입은 소유 모듈이 배포해야 한다.
    implementation(libs.querydsl.core)
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:${libs.versions.querydsl.get()}:jakarta")

    // SchemaFlywayFactory가 Flyway API로 컴파일되게 한다. 런타임 flyway는 이 팩토리의 소비자
    // (app-migration)가 제공하므로 compileOnly로 두어 도메인 런타임에 flyway를 전파하지 않는다.
    // 버전은 위 implementation platform BOM이 compileClasspath까지 해석한다(compileClasspath ⊇ implementation).
    compileOnly(libs.flyway.core)
}
