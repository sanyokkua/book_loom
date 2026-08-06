// `build-logic` is an included build, so it has its own settings and resolves its own dependencies.
// It reads the SAME version catalog as the main build, so a tool version is pinned in exactly one file.

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            // Two hops up: this build sits at `modules/build-logic/` (ADR-0021) while `gradle/` stays at
            // the repository root.
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
