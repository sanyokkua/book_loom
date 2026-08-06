// :util — small stateless helpers. Depends on :api and nothing else.
//
// Deliberately without Guice: `ua.bookloom.util.paths` resolves the data/log directories BEFORE the injector
// is built and before logging is configured (DD-39, ADR-0015).

plugins {
    id("bookloom.java-conventions")
    id("bookloom.spotless-conventions")
    id("bookloom.test-conventions")
    id("bookloom.coverage-conventions")
}

dependencies {
    implementation(project(":api"))
}
