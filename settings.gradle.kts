// BookLoom — root settings.
//
// Fixes the module set for the whole project: eight Gradle subprojects that are also JPMS modules
// (`ua.bookloom.<module>`), plus the `build-logic` included build that carries every shared convention.
// A ninth subproject requires an ADR (docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md).

pluginManagement {
    // `build-logic` is an included build, not a subproject: its precompiled script plugins
    // (`bookloom.*-conventions`) must be resolvable as plugins by the main build.
    includeBuild("modules/build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // No subproject may declare its own repositories — resolution is centralised here so the
    // committed lockfiles describe one graph, not eight.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
    }
}

rootProject.name = "bookloom"

include(
    ":api",
    ":util",
    ":document",
    ":llm",
    ":pipeline",
    ":persistence",
    ":ui",
    ":app",
)

// The eight code directories live under `modules/` (ADR-0021), but their Gradle project paths stay
// `:api` … `:app`. Gradle would otherwise derive each directory from the project name — `include(":api")`
// means `rootDir/api` — which is now wrong for all eight, so each is repointed explicitly.
//
// This is deliberately NOT `include(":modules:api")`: that form derives the path from the directory and
// would rename every project to `:modules:api`, invalidating every `project(":api")` dependency, every
// `./gradlew :app:run` invocation, and the `:module/package` citation convention the whole corpus uses
// (docs/implementation_plan/01_MODULE_INVENTORY.md#how-to-cite-a-module).
listOf("api", "util", "document", "llm", "pipeline", "persistence", "ui", "app").forEach { module ->
    project(":$module").projectDir = file("modules/$module")
}
