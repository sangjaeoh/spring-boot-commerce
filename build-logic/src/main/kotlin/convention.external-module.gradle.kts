import org.gradle.api.provider.Property

plugins {
    id("convention.java-common")
}

interface ExternalModuleExtension {
    val targetDomain: Property<String>
}

val externalModule = extensions.create("externalModule", ExternalModuleExtension::class.java)

// targetDomain은 이 스크립트가 적용된 뒤 소비 모듈의 build.gradle.kts가 설정하므로,
// 전체 스크립트 평가가 끝난 뒤(afterEvaluate)에만 최종 값을 알 수 있다.
afterEvaluate {
    val targetDomainPath = externalModule.targetDomain.orNull?.let { ":module-domains:$it" }
    val ownerDescription = "external-module(구현 대상 도메인 " +
        (externalModule.targetDomain.orNull
            ?: "미설정 — externalModule { targetDomain.set(\"domain-xxx\") }로 지정") +
        "과 module-common만 허용)"
    restrictProjectDependencies(ownerDescription) { dependencyPath ->
        dependencyPath.startsWith(":module-common:") || dependencyPath == targetDomainPath
    }
}
