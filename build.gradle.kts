// Root build script.
//
// Deliberately thin: it carries no `allprojects { }` / `subprojects { }` configuration. Every shared
// concern (toolchain, JPMS args, formatting, lint, tests) is a convention plugin in `build-logic`
// applied explicitly by each subproject, so configuration is visible where it takes effect
// (docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#convention-plugins).

import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.TextReportRenderer

plugins {
    base
    alias(libs.plugins.license.report)
}

// The git tag is the single source of truth for the app version; local and CI snapshot builds get `dev`
// (01_BUILD_AND_TOOLING.md#version-injection, DD-50). The generated version *resource* arrives with :app
// in a later change; this only establishes the property.
version = providers.gradleProperty("appVersion").orElse("dev").get()

// The root project applies no `bookloom.*` convention plugin (it is not a Java project), so it opts into
// dependency locking on its own. It declares no dependencies today, so no `gradle.lockfile` is written here —
// the declaration exists so that anything added at root level later is locked from its first resolution rather
// than from whenever someone remembers (01_BUILD_AND_TOOLING.md#dependency-locking).
dependencyLocking {
    lockAllConfigurations()
}

// --- License gate --------------------------------------------------------------------------------------------
//
// BookLoom ships under MIT (05_Dependencies/03_LICENSING.md#app-license). That claim is only true if every
// dependency reaching a user is permissively licensed, so it is machine-checked rather than asserted: `checkLicense`
// fails the build on any resolved artifact whose license is not in `config/license/allowed-licenses.json`.
//
// It asks "may we distribute this?", nothing more; a known-vulnerability scan was deliberately dropped (2026-09-11):
// a single-user offline app is not worth a 30-minute NVD sync on every CI run (03_LICENSING.md#license-gate-tool).
licenseReport {
    // The root project plus the eight subprojects. Root carries no dependency graph of its own, but the plugin
    // resolves the report's output directory through the project that owns this extension, so it must be in the
    // list — dropping it fails with `Cannot get property 'absoluteOutputDir' on null object`.
    projects = arrayOf(project) + subprojects.toTypedArray()

    // `runtimeClasspath` ONLY, and this line is the whole reason the gate can be strict.
    //
    // Checkstyle and SpotBugs are LGPL-2.1 — a license this project bans outright. They pass not because the
    // allowlist was widened to admit them, but because they are never resolved here: they live on the
    // `checkstyle`/`spotbugs` configurations, which are developer tooling and reach no user.
    // 03_LICENSING.md#build-tool-exception draws exactly this line — "the gate's hard ban applies to runtime and
    // bundled dependencies (anything reachable in the jlink image)" — so the correct instrument is the
    // configuration filter, not an exception entry. Widening the allowlist to Apache/MIT/BSD + LGPL would also
    // have admitted LGPL code into the shipped image, which is the thing being prevented.
    //
    // Test-scope licenses (JUnit EPL-2.0, TestFX EUPL-1.1) fall outside for the same structural reason: they are
    // on `testRuntimeClasspath` and are not shipped.
    configurations = arrayOf("runtimeClasspath")

    // The policy file is data, not code: the allowlist is reviewable on its own, and widening it is a visible diff
    // rather than a build-script edit buried among plugin configuration.
    allowedLicensesFile = file("config/license/allowed-licenses.json")

    // A single license is spelled a dozen ways across POMs ("Apache 2", "The Apache Software License, Version
    // 2.0", "Apache-2.0", a bare URL). The normalizer folds those spellings onto canonical names so the allowlist
    // can enumerate licenses rather than every string upstream has ever used to write one down.
    filters = arrayOf(LicenseBundleNormalizer())

    // Typed as `Array<ReportRenderer>` on purpose. The plugin appends its own JsonReportRenderer at execution
    // time, so an array whose inferred element type is narrower than `ReportRenderer` fails with an
    // ArrayStoreException-shaped cast error — which would happen the moment this list dropped to a single entry.
    renderers =
        arrayOf<ReportRenderer>(
            // The attribution artifact required by 03_LICENSING.md#notices. Generated rather than committed:
            // a hand-maintained notices file drifts from the graph silently, and this one is regenerated from
            // the same resolution the gate checks. Packaging (change 27) consumes it from here.
            TextReportRenderer("THIRD-PARTY-NOTICES"),
            InventoryHtmlReportRenderer(),
        )
}
