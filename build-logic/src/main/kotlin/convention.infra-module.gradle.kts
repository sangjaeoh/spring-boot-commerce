plugins {
    id("convention.java-common")
}

restrictProjectDependencies("infra-module") { dependencyPath ->
    dependencyPath.startsWith(":module-common:")
}
