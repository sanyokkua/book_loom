// Test conventions — one stack, one platform configuration, one exclusion mechanism, for all eight subprojects.
//
// Fixing the stack here means no later change re-argues which assertion library or mocking framework to use, and
// no module hand-rolls its own JUnit Platform wiring
// (docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#test-types).
//
// Owns design decision D5: `liveLocal`, `promptEval` and `visual` are kept out of `check` by the TASK GRAPH and by
// nothing else. See the exclusion block below for why that distinction is load-bearing.

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
}

// Precompiled script plugins do not get the generated `libs.*` accessors — those exist only in the main build's
// scripts. Reading the catalog by name keeps every version pinned in exactly one file all the same.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = catalog.findLibrary(alias).orElseThrow()

fun bundle(alias: String) = catalog.findBundle(alias).orElseThrow()

// --- The three local-only tag sets (design D5) -------------------------------------------------------------------
//
// `liveLocal` needs a real Ollama / LM Studio, `promptEval` needs a real model plus an embedding scorer, `visual`
// needs a pinned rendering environment. None of the three can run on a CI runner, and all three are excluded from
// coverage (`06_TESTING_STRATEGY.md#live-local`, `#prompt-evals`, `#visual-validation`, DD-35).
//
// The task name and the tag name are deliberately identical: `./gradlew liveLocal` runs tag `liveLocal`. That is
// what makes the mechanism inspectable — `./gradlew tasks` shows the whole partition.
val localOnlyTags = listOf("liveLocal", "promptEval", "visual")

dependencies {
    // The BOM pins every `org.junit.*` coordinate from one version, so `junit-jupiter` and
    // `junit-platform-launcher` below carry no version of their own and can never drift apart.
    "testImplementation"(platform(lib("junit-bom")))

    // JUnit 5 + AssertJ + Mockito 5 (core and the Jupiter extension) — the fixed stack of
    // `06_TESTING_STRATEGY.md#test-types`, declared once as a catalog bundle.
    "testImplementation"(bundle("testing"))

    // The LLM seam is tested at the HTTP wire with WireMock, never by mocking the `Provider` port
    // (`.claude/rules/testing.md`). It ships to every module rather than only `:llm` for the same reason the rest
    // of this block does: the stack is a project-level decision, not a per-module one.
    "testImplementation"(lib("wiremock"))

    // TestFX for the UI tiers. Verified against the published POM: `testfx-core` declares NO dependency on
    // `org.openjfx:*` — it expects JavaFX to already be present — so putting it on every module's test classpath
    // drags no per-OS classified artifact into the six FX-free modules and leaves their lock state intact
    // (design D7). Headless comes from JavaFX 26 itself, not from `openjfx-monocle`; see the system properties
    // below and ADR-0019.
    "testImplementation"(lib("testfx-core"))
    "testImplementation"(lib("testfx-junit5"))

    // The launcher is a runtime-only component of the platform; Gradle needs it on the test runtime classpath to
    // start the engine.
    "testRuntimeOnly"(lib("junit-platform-launcher"))
}

// --- Every Test task: the platform, the exclusions, and headless rendering ----------------------------------------
//
// `withType<Test>` rather than `tasks.test` on purpose: it also covers Test tasks a module registers itself —
// today that is `:app`'s `archTest`, which must be subject to exactly the same tag exclusions as `test`. A rule
// that only ever configured the default task would leave a second, unfiltered execution path open.
tasks.withType<Test>().configureEach {
    // The three tagged tasks are registered below and configure `includeTags` for their own tag. They must NOT
    // also receive the exclusions: both actions mutate the same `JUnitPlatformOptions`, exclusion beats inclusion
    // in JUnit's filter algebra, and `liveLocal` would then match nothing — forever, and silently.
    if (name !in localOnlyTags) {
        useJUnitPlatform {
            // THE exclusion mechanism (design D5). `@Disabled`, an `assumeTrue(System.getenv(...))` inside the
            // test body, or a `-PskipLive` property would all leave a live test *in* the merge gate's task graph:
            // it would look like an ordinary green test right up to the moment it hit a network timeout on a
            // runner with no Ollama. Excluding by tag means the class is never loaded.
            excludeTags(*localOnlyTags.toTypedArray())
        }
    }

    // JavaFX 26 ships a headless glass platform inside `javafx.graphics` on every OS, selected with this one
    // property. It replaces `-Dglass.platform=Monocle -Dmonocle.platform=Headless` wherever the frozen spec names
    // Monocle: `org.testfx:openjfx-monocle` has no release past 21.0.2 and patches JavaFX internals, so it cannot
    // be run against a JavaFX 26 runtime at all (ADR-0019).
    //
    // Deliberately NOT `testfx.headless=true`: that property makes TestFX try to install Monocle itself, which is
    // exactly the artifact this project does not have.
    systemProperty("glass.platform", "Headless")

    // The software rasteriser. Headless has no GPU surface to acquire, and on a CI runner the hardware pipeline
    // would fall back anyway — after logging a failure that reads like a real one.
    systemProperty("prism.order", "sw")

    // Any incidental AWT/Swing initialisation (font metrics, image IO) must not try to open a display either.
    systemProperty("java.awt.headless", "true")

    // A tag-partitioned suite legitimately matches zero tests: `:api` has no `visual` test and never will, and on
    // a machine with no local model EVERY `liveLocal` case is skipped. Gradle's default would turn that into a
    // build failure, which would make `./gradlew liveLocal` red on a fresh checkout — the precise outcome
    // `06_TESTING_STRATEGY.md#live-local` requires it not to have. The guard this gives up (a typo'd filter
    // running nothing) is covered far more tightly by the functional tests in `build-logic`, which assert the
    // exact set of tests each task runs.
    failOnNoDiscoveredTests = false

    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }
}

// --- The three local-only tasks (design D5) ----------------------------------------------------------------------
//
// Each includes exactly its own tag and is NOT wired into `check`. They run the same `test` source set as the
// default task — the partition is by tag, not by directory, so a `liveLocal` case sits next to the WireMock test
// of the same behaviour instead of in a parallel tree that drifts.
localOnlyTags.forEach { tag ->
    tasks.register<Test>(tag) {
        description = "Runs only the `$tag`-tagged tests. Local-only: never part of `check` or the merge gate."
        group = LifecycleBasePlugin.VERIFICATION_GROUP

        val testSourceSet = sourceSets["test"]
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath

        useJUnitPlatform {
            includeTags(tag)
        }

        // Deliberately no `dependsOn`/`finalizedBy` onto `check`, and no JaCoCo verification: these tests prove
        // behaviour against a real server or a pinned display, which is not the kind of evidence a merge gate can
        // reproduce (`06_TESTING_STRATEGY.md#coverage-traceability`).
    }
}
