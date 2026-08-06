// Coverage conventions — the exact per-module JaCoCo branch gate (design D6).
//
// Applied ONLY by `:api :util :document :llm :pipeline :persistence`. `:ui` and `:app` deliberately do not apply
// it: JavaFX presentation code is covered behaviourally by the headless TestFX suites, not by a branch percentage
// (docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#coverage-gate,
// `06_TESTING_STRATEGY.md#coverage-traceability`).
//
// Opt-in by explicit `plugins { }` application rather than a project-name check inside a shared plugin, so which
// modules are gated is readable from the module's own build script — the place someone looks when they wonder why
// their coverage did or did not block a merge.

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
    jacoco
}

// Precompiled script plugins do not get the generated `libs.*` accessors — those exist only in the main build's
// scripts. Reading the catalog by name keeps the version pinned in exactly one file all the same.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

jacoco {
    toolVersion = catalog.findVersion("jacoco").orElseThrow().requiredVersion
}

// --- What counts as "production sources" (design D6) --------------------------------------------------------------
//
// `module-info.java` and `package-info.java` are declarations, not code: the first states the module graph, the
// second carries the JSpecify `@NullMarked` marker. Both compile to class files with zero methods and zero
// branches, so a module holding only those two is empty in every sense that matters to a coverage gate.
//
// This is the EXPLICIT exemption the design calls for, and it is not the same thing as JaCoCo happening to report
// nothing. A verification rule over zero branches evaluates `0/0`, which JaCoCo treats as an undefined ratio and
// skips — so an empty module would pass either way, and the two mechanisms would be indistinguishable in a green
// build. They are distinguishable here: this one makes the task SKIPPED, which the functional test in
// `build-logic` asserts on a fixture module that has a test (and therefore real execution data) but no production
// class at all.
val productionSources =
    sourceSets["main"].java.asFileTree.matching {
        exclude("module-info.java")
        exclude("**/package-info.java")
    }

// Resolved at execution time, not at configuration time: a module can acquire its first production class in the
// same build that runs the gate.
val hasProductionSources = provider { !productionSources.isEmpty }

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            // BUNDLE = the whole module, which is what "applied per-module" means in
            // `02_QUALITY_GATES.md#coverage-gate`. Anything coarser would let a well-covered module carry a
            // bare one; anything finer (CLASS/METHOD) would fail a data carrier for having no branches to cover.
            element = "BUNDLE"

            limit {
                // BRANCH, not LINE. Branch coverage is the one that notices an `if` whose false arm no test ever
                // takes — the defect a line-coverage number hides behind an executed line.
                counter = "BRANCH"
                value = "COVEREDRATIO"

                // Exactly 0.80, per `02_QUALITY_GATES.md#coverage-gate` ("an exact branch-coverage percentage,
                // not an approximate ~80%"). A BigDecimal because JaCoCo compares ratios exactly and a binary
                // double for 0.8 is not 0.8.
                minimum = "0.80".toBigDecimal()
            }
        }
    }

    // `setOnlyIf`, not `onlyIf`: this REPLACES the predicate Gradle's JaCoCo plugin installs, which skips the
    // task whenever no execution data file exists. That default leaves a hole exactly the size of the gate — a
    // module with branchy production code and NO test source set at all has no `test.exec`, so the verification
    // would be skipped and `check` would go green on 0% coverage. Untested code is the case a coverage gate
    // exists to catch; it must fail, not vanish.
    //
    // Replacing the predicate means the task can now run with no execution data, so the input has to tolerate a
    // file that is not there. JaCoCo then analyses the class files against zero recorded coverage, which is the
    // truthful answer for a module nobody tested.
    executionData.setFrom(executionData.filter { it.exists() })
    setOnlyIf("the module has production sources to measure") { hasProductionSources.get() }

    // Gradle's JaCoCo plugin wires this task `mustRunAfter` the test task, not `dependsOn` it. Inside `check`
    // that is enough, because `check` pulls both — but invoked on its own the verification would then judge
    // whatever execution data happened to be lying in `build/` from a previous run, or none at all.
    dependsOn(tasks.named("test"))
}

// A gate that is not on the `check` graph is a report, not a gate.
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// The HTML/XML report is diagnostic rather than gating: when the verification above fails, this is what says
// which branch was missed. Same `onlyIf`, for the same reason.
tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        html.required = true
        xml.required = true
    }

    onlyIf("the module has production sources to measure") { hasProductionSources.get() }
}
