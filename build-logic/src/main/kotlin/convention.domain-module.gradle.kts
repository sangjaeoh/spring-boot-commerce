plugins {
    id("convention.java-common")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

restrictProjectDependencies("domain-module") { dependencyPath ->
    dependencyPath in
        setOf(
            ":module-common:common-core",
            ":module-common:common-jpa",
            ":module-common:common-messaging",
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
    "implementation"(libsCatalog.findLibrary("querydsl-jpa").get())
    "annotationProcessor"(
        variantOf(libsCatalog.findLibrary("querydsl-apt").get()) { classifier("jpa") },
    )
}
