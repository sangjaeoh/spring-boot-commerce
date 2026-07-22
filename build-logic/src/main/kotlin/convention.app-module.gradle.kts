plugins {
    id("convention.java-common")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

restrictProjectDependencies("app-module") { dependencyPath ->
    listOf(":module-domains:", ":module-infra:", ":module-common:", ":module-events:")
        .any { dependencyPath.startsWith(it) }
}

tasks.named<Jar>("jar") {
    enabled = false
}
