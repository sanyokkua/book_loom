// :pipeline — the translation engine. The only core module that sees :document, :llm and :persistence together,
// which is what makes it the single orchestration seam :ui talks to.

plugins {
    id("bookloom.java-conventions")
    id("bookloom.spotless-conventions")
    id("bookloom.test-conventions")
    id("bookloom.coverage-conventions")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":util"))
    implementation(project(":document"))
    implementation(project(":llm"))
    implementation(project(":persistence"))
    implementation(libs.guice)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(libs.logback.classic)
}
