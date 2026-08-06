// :document — parse/mask/reassemble EPUB, FB2, Markdown and TXT. FX-free.
//
// `implementation` rather than `api` throughout: nothing here is part of an exported surface another module
// compiles against, so no transitive leaks downstream (05_Dependencies/02_DEPENDENCY_POLICY.md#narrowest-scope).

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
}
