plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.gradle.plugin)
    testImplementation("org.junit.jupiter:junit-jupiter:${libs.versions.junit.get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:${libs.versions.junit.get()}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
