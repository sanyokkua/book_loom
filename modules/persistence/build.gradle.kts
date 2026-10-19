// :persistence — SQLite (WAL) via JDBI with Flyway migrations. FX-free. Implements the repository ports.
//
// sqlite-jdbc, Flyway and JDBI arrive with the storage change; this module currently needs only the module
// graph and Guice so its `opens … to com.google.guice` has a target.

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

    // The SLF4J facade, used through Lombok's `@Slf4j` by every in-memory adapter — the port boundary that first
    // classifies and logs a failure (`.claude/rules/logging.md`).
    implementation(libs.slf4j.api)

    testRuntimeOnly(libs.logback.classic)
}
