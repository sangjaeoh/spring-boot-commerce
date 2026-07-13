plugins {
    id("convention.java-common")
}

restrictProjectDependencies("common-module") { dependencyPath ->
    when (project.name) {
        "common-core" -> false
        "common-web" -> dependencyPath in setOf(":module-common:common-core", ":module-common:common-auth")
        else -> dependencyPath == ":module-common:common-core"
    }
}
