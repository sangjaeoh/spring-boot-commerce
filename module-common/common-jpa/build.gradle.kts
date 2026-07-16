plugins {
    id("convention.common-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(project(":module-common:common-core"))
    implementation(libs.spring.boot.starter.data.jpa)

    // SchemaFlywayFactory가 Flyway API로 컴파일되게 한다. 런타임 flyway는 이 팩토리의 소비자
    // (app-migration)가 제공하므로 compileOnly로 두어 도메인 런타임에 flyway를 전파하지 않는다.
    // 버전은 위 implementation platform BOM이 compileClasspath까지 해석한다(compileClasspath ⊇ implementation).
    compileOnly(libs.flyway.core)
}
