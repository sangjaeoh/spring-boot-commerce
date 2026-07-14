plugins {
    id("convention.common-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(project(":module-common:common-core"))
    implementation(libs.spring.boot.starter.data.jpa)

    // BaseTimeEntity(@MappedSuperclass)의 Q타입(QBaseTimeEntity)을 생성해 이를 상속하는
    // 도메인 엔티티의 QueryDSL 코드가 다운스트림 모듈에서 컴파일되게 한다.
    implementation(libs.querydsl.jpa)
    annotationProcessor(variantOf(libs.querydsl.apt) { classifier("jpa") })
}
