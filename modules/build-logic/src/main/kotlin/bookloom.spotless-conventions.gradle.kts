// Formatting conventions — Palantir Java Format at 120 columns.
//
// Formatting is mechanical and never argued about in review: `spotlessApply` runs in the pre-commit hook, so a
// file is fixed before anyone ever sees it, and `spotlessCheck` gates CI
// (docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#tools, DD-25).
//
// A separate plugin from `bookloom.java-conventions` per design decision D2: formatting is the one gate that
// must also apply to source sets a module adds later (arch-test, fixtures) without dragging the whole
// compiler/lint stack along with it.

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.diffplug.spotless")
}

// Precompiled script plugins do not get the generated `libs.*` accessors — those exist only in the main build's
// scripts. Reading the catalog by name keeps the version pinned in exactly one file all the same.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

spotless {
    java {
        // Every Java source set, present and future — `src/main/java`, `src/test/java`, and the shared
        // `arch-test` source set that arrives in task group 4. Generated sources under `build/` are excluded by
        // anchoring the target at `src/`.
        target("src/*/java/**/*.java")

        // Palantir Java Format is fixed at 120 columns; that is the column budget the spec names, and it is not
        // configurable per-project, which is precisely why it was chosen over a formatter that is.
        palantirJavaFormat(
            catalog.findVersion("palantirJavaFormat").orElseThrow().requiredVersion,
        )

        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
