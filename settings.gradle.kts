rootProject.name = "spring-boot-commerce"

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
include("module-common:common-messaging")
include("module-common:common-auth")
include("module-common:common-web")
include("module-domains:domain-shared")
include("module-domains:domain-member")
include("module-domains:domain-product")
include("module-domains:domain-stock")
include("module-domains:domain-cart")
include("module-domains:domain-coupon")
include("module-domains:domain-order")
include("module-domains:domain-payment")
include("module-domains:domain-wishlist")
include("module-external:external-payment")
include("module-infra:infra-messaging")
include("module-infra:infra-redis")
include("module-apps:app-api")
include("module-apps:app-migration")
include("module-tests:test-architecture")
