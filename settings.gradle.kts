rootProject.name = "spring-boot-commerce-practice"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("module-common:common-core")
include("module-common:common-jpa")
include("module-apps:app-api")
