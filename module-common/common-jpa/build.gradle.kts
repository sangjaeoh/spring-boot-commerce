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
    compileOnly(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    compileOnly(libs.flyway.core)

    // BaseTimeEntity(@MappedSuperclass)의 Q타입(QBaseTimeEntity)을 생성해 이를 상속하는
    // 도메인 엔티티의 QueryDSL 코드가 다운스트림 모듈에서 컴파일되게 한다.
    implementation(libs.querydsl.jpa)
    annotationProcessor(variantOf(libs.querydsl.apt) { classifier("jpa") })
}
