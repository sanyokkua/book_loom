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
}
