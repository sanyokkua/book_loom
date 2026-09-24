// :llm — provider abstraction, inference, the single-flight gate, retry. FX-free.
//
// The HTTP client is `java.net.http` from the JDK, so there is no third-party transport dependency to declare —
// which is exactly why "only :llm can open a socket" is checkable by the `no-http-in-core-except-llm` rule.

plugins {
    id("bookloom.java-conventions")
    id("bookloom.spotless-conventions")
    id("bookloom.test-conventions")
    id("bookloom.coverage-conventions")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":util"))
    implementation(libs.guice)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(libs.logback.classic)
}
