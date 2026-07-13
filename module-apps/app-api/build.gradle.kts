plugins {
    id("convention.app-module")
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit.junit5)
}
