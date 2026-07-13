plugins {
    id("convention.java-base")
}

val forbiddenDependencyGroups = setOf("org.projectlombok", "com.h2database")

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group in forbiddenDependencyGroups) {
            throw GradleException(
                "금지된 의존성: ${requested.group}:${requested.name} " +
                    "(Lombok/H2 금지 — docs/code-quality.md)",
            )
        }
    }
}
