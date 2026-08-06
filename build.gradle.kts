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
    alias(libs.plugins.owasp.dependencycheck)
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
// This gate is NOT the OWASP dependency-check SCA gate. This one asks "may we distribute this?"; that one asks
// "does this have a known CVE?". Both run in CI and either can fail the merge independently
// (03_LICENSING.md#license-gate-tool).
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

// --- SCA gate (OWASP dependency-check) -----------------------------------------------------------------------
//
// The second of the two dependency gates, and deliberately the one that is NOT wired into `check`. The license
// gate above asks "may we distribute this?" and is answerable from the POMs already on disk; this one asks "does
// this have a known CVE?" and is answerable only against the NVD feed — a network round trip, on every run, whose
// result changes without the dependency graph changing at all. Bolting that onto `./gradlew check` would make the
// pre-push hook slow, network-dependent, and non-deterministic — the three properties a local gate must not have.
// So it stays a CI-only step (`.github/workflows/ci.yml`, task 7.3), invoked by name as `dependencyCheckAggregate`.
//
// `dependencyCheckAggregate` rather than `dependencyCheckAnalyze`: the root project has no dependency graph of its
// own, so the analysing variant would scan nothing and pass — a green gate over an empty set. The aggregate variant
// walks the subprojects.
dependencyCheck {
    // Only what ships. `runtimeClasspath` is the same scope the license gate uses and for the same reason: a CVE in
    // Checkstyle or in the WireMock test server is not a vulnerability in the distributed application, and treating
    // it as one trains people to suppress findings — which is how a real one gets suppressed alongside it.
    scanConfigurations = listOf("runtimeClasspath")

    // CVSS >= 7.0 is "High" on the CVSS v3 scale. `02_DEPENDENCY_POLICY.md#owasp-dependency-check` fixes the
    // behaviour ("fails the build above the configured severity threshold") and leaves the number to
    // configuration; this is that configuration. High-and-above blocks the merge, Medium and below is reported in
    // the artifact and triaged rather than blocking. A flagged dependency is upgraded, replaced, or risk-accepted
    // with justification via `config/owasp/suppressions.xml` before merge.
    failBuildOnCVSS = 7.0f

    // HTML for a human opening the CI artifact, JSON for anything that later wants to diff findings across runs.
    formats = listOf("HTML", "JSON")

    // Documented risk acceptances live in a reviewable data file, exactly like the license allowlist — never as a
    // threshold quietly raised until the build goes green.
    suppressionFile = file("config/owasp/suppressions.xml").absolutePath

    analyzers {
        // This is a pure-JVM project. Every analyzer below scans an ecosystem that is not present, and each one
        // costs either startup time or an extra remote call for a guaranteed-empty result.
        assemblyEnabled = false
        msbuildEnabled = false
        nuspecEnabled = false
        nugetconfEnabled = false
        nodeEnabled = false
        nodeAuditEnabled = false
        retirejs { enabled = false }

        // Sonatype OSS Index is a SECOND vulnerability service, with its own rate limit and its own outage
        // profile. Enabling it would mean the merge gate could go red because a service this project has no
        // account with was unavailable. NVD is the source `02_DEPENDENCY_POLICY.md` names; one source, one
        // failure mode.
        ossIndex { enabled = false }
    }

    nvd {
        // The optional `NVD_API_KEY` repository secret (`04_CI_CD.md#no-secrets` — the ONLY optional secret in the
        // pipeline, granting no publish or signing capability). Read from the environment so the value never
        // reaches a build script, a lockfile, or a log.
        //
        // Absent is the SUPPORTED path, not a degraded one that fails: this repository has no such secret, and a
        // fork must stay buildable without one. What changes is throughput, not outcome — NVD rate-limits
        // unauthenticated callers far harder, so the inter-request delay goes up and the first sync takes
        // materially longer. CI caches the resulting data directory across runs so that cost is paid once rather
        // than every PR (`04_CI_CD.md#no-secrets`: "CI can run against a cached/mirrored feed, keeping forks
        // buildable without it").
        val key = providers.environmentVariable("NVD_API_KEY").orNull
        if (!key.isNullOrBlank()) {
            apiKey = key
            delay = 1000
        } else {
            delay = 8000
        }

        // A transient 503 from NVD mid-sync must not read as "your dependencies are fine" or as a red merge gate.
        maxRetryCount = 20
    }
}

// An included build's verification tasks are NOT reachable from the root build's `check` by default. Without
// this edge the `build-logic` functional tests — the ones proving Spotless, NullAway, Checkstyle and
// FindSecBugs each actually fail red on a seeded canary — would never run in `./gradlew clean build check
// spotlessCheck`, and therefore never run in the pre-push hook or CI either. A test that never executes proves
// nothing at all, which is the exact failure mode those tests exist to rule out.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}
