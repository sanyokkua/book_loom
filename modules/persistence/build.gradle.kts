// :persistence — SQLite (WAL) via JDBI; one schema file, no Flyway before release (ADR-0043). FX-free. Implements
// the repository ports. Jackson (databind + jsr310) stores state as JSON documents.

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
    implementation(libs.sqlite.jdbc)
    implementation(libs.jdbi3.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jsr310)

    // The SLF4J facade, used through Lombok's `@Slf4j` by every in-memory adapter — the port boundary that first
    // classifies and logs a failure (`.claude/rules/logging.md`).
    implementation(libs.slf4j.api)

    testRuntimeOnly(libs.logback.classic)
}

// JDBI's classes carry Error Prone's `@GuardedBy`, which this module does not (and need not) have on its compile
// classpath; javac reports that as a classfile warning, and the build treats warnings as errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
