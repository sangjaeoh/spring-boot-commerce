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
include("module-domains:domain-member")
include("module-domains:domain-product")
include("module-apps:app-api")
