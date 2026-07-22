plugins {
    id("convention.java-common")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

restrictProjectDependencies("domain-module") { dependencyPath ->
    dependencyPath in
        setOf(
            ":module-domains:domain-shared",
            ":module-common:common-core",
            ":module-common:common-jpa",
            ":module-common:common-event",
        )
}

dependencies {
    "implementation"(
        platform(
            "org.springframework.boot:spring-boot-dependencies:${
                libsCatalog.findVersion("spring-boot").get().requiredVersion
            }",
        ),
    )
    "implementation"(libsCatalog.findLibrary("spring-boot-starter-data-jpa").get())
}
