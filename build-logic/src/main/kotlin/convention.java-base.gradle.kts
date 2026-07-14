import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
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
        // QueryDSL이 생성한 Q타입 등 @Generated 코드는 손으로 쓴 코드가 아니라 정적분석 대상에서 뺀다.
        disableWarningsInGeneratedCode.set(true)
        error("NullAway")
        option("NullAway:AnnotatedPackages", "com.commerce")
        option("NullAway:TreatGeneratedAsUnannotated", "true")
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
