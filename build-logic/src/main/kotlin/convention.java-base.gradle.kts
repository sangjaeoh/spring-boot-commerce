import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        // Java 버전 정본: gradle/libs.versions.toml [versions] java. 카탈로그를 읽는 이 플러그인이 실소비처다.
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion.toInt())
    }
}

spotless {
    java {
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        importOrder()
        removeUnusedImports()
    }
}

dependencies {
    "implementation"(libs.findLibrary("jspecify").get())
    "compileOnly"(libs.findLibrary("errorprone-annotations").get())
    "errorprone"(libs.findLibrary("errorprone-core").get())
    "errorprone"(libs.findLibrary("nullaway").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        error("NullAway")
        option("NullAway:AnnotatedPackages", "com.commerce")
        option(
            "NullAway:ExcludedFieldAnnotations",
            listOf(
                    "jakarta.persistence.Id",
                    "jakarta.persistence.Column",
                    "jakarta.persistence.Enumerated",
                    "jakarta.persistence.Convert",
                    "jakarta.persistence.Embedded",
                    "jakarta.persistence.ManyToOne",
                    "jakarta.persistence.OneToOne",
                    "jakarta.persistence.OneToMany",
                    "jakarta.persistence.JoinColumn",
                    "jakarta.persistence.Version",
                )
                .joinToString(","),
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
