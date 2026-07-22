import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.LibraryElements

plugins {
    id("convention.java-common")
}

// 실행 앱별 임베드 도메인 목록을 빌드 시점에 산출한다 — 소비 커버리지 불변식 테스트가 리소스로 읽는다.
// 임베드 = 앱 main 런타임 구성(api·implementation·runtimeOnly)에 선언된 domain-{name} 프로젝트 의존.
val appEmbeddingsDir = layout.buildDirectory.dir("generated/app-embeddings")
val generateAppDomainEmbeddings by tasks.registering {
    outputs.dir(appEmbeddingsDir)
    doLast {
        val mainConfigurations = setOf("api", "implementation", "runtimeOnly")
        val lines = rootProject.subprojects
            .filter { it.path.startsWith(":module-apps:") }
            .sortedBy { it.name }
            .map { app ->
                val domains = app.configurations
                    .filter { it.name in mainConfigurations }
                    .flatMap { it.dependencies }
                    .filterIsInstance<ProjectDependency>()
                    .map { it.path }
                    .filter { it.startsWith(":module-domains:") }
                    .map { it.removePrefix(":module-domains:") }
                    .toSortedSet()
                "${app.name}=${domains.joinToString(",")}"
            }
        appEmbeddingsDir.get().file("app-domain-embeddings.properties").asFile.apply {
            parentFile.mkdirs()
            writeText(lines.joinToString("\n") + "\n")
        }
    }
}

sourceSets.test {
    resources.srcDir(appEmbeddingsDir)
}

tasks.named("processTestResources") {
    dependsOn(generateAppDomainEmbeddings)
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
