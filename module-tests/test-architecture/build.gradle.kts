import org.gradle.api.attributes.LibraryElements

plugins {
    id("convention.java-common")
}

dependencies {
    // JUnit·Spring 타입 버전을 Spring Boot BOM으로 정렬한다(도메인 모듈과 동일 방식). 이 모듈은
    // 스프링을 실행하지 않고 archunit·junit만 쓰지만, 카탈로그 미고정 버전(junit)을 BOM이 관리한다.
    testImplementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )

    // 아키텍처 테스트는 컴파일된 클래스 그래프로 경계를 검증하므로 검사 대상 전 모듈이 classpath에 있어야 한다.
    // 새 모듈이 검사에서 누락되지 않도록 의존을 모듈 목록에서 파생시킨다(architecture.md 아키텍처 테스트 모듈).
    // 산출물(jar) 대신 컴파일된 클래스 디렉터리 변형을 요청한다 — app 모듈 산출물은 bootJar(fat jar)라
    // 클래스가 BOOT-INF/classes/로 재배치돼 ArchUnit의 com.commerce 스캔에 안 잡히기 때문. 라이브러리
    // 모듈엔 무해하다(동일 classes 변형 선택).
    rootProject.subprojects
        .filter { it.buildFile.exists() && it.path != project.path }
        .forEach { sub ->
            testImplementation(project(sub.path)) {
                attributes {
                    attribute(
                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        objects.named(LibraryElements::class.java, LibraryElements.CLASSES),
                    )
                }
            }
        }

    testImplementation(libs.archunit.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
