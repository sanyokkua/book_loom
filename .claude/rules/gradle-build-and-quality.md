# Gradle Build & Quality

Scope: build scripts and quality config — `**/build.gradle.kts`, `settings.gradle.kts`, `build-logic/**`, `gradle/libs.versions.toml`, `gradle/**.lockfile`, `lefthook.yml`, `.editorconfig`, `.gitattributes`. Spec: `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md`, `.../02_QUALITY_GATES.md`.

## MUST

- **MUST** write all build logic in **Gradle Kotlin DSL** and factor shared config into `build-logic/` convention plugins; subproject scripts only apply conventions and declare deps. — Rationale: one place defines the toolchain, lint, and packaging.
- **MUST** invoke Gradle only through the committed wrapper (`./gradlew`); never a system `gradle`. The wrapper JAR/props are committed. — Rationale: reproducible builds pinned to one Gradle version.
- **MUST** declare every dependency version in the version catalog `gradle/libs.versions.toml` and reference it via `libs.*` accessors — no inline version strings in scripts. — Rationale: single source of truth for versions.
- **MUST** enable Gradle **dependency locking** and commit the lockfiles; a resolution change must regenerate and commit the lock. — Rationale: the dependency graph is pinned and auditable.
- **MUST** apply **Spotless + Palantir Java Format (120-col)**: `spotlessApply` runs in the pre-commit hook (stage_fixed), `spotlessCheck` gates CI. — Rationale: formatting is automated and never argued about in review.
- **MUST** run **Error Prone + NullAway** at compile time with JSpecify `@NullMarked` per package; CI fails on SECURITY and CORRECTNESS findings. — Rationale: null and correctness bugs caught before merge.
- **MUST** run **Checkstyle**, **SpotBugs + FindSecBugs**, and the **ArchUnit** boundary tests as part of `check`. — Rationale: style, security, and layering are all gated.
- **MUST** stage checks via **Lefthook**: pre-commit (<10s: `spotlessApply`, gitleaks, file-size guard), commit-msg (Conventional Commits), pre-push (<60s: unit tests excluding UI/TestFX). Heavy checks (full lint, headless TestFX, coverage, SCA) run in CI only. — Rationale: fast local loop, thorough CI.
- **MUST** leave `./gradlew clean build check spotlessCheck` **green across the whole project** at the end of every story — build, format, lint, ArchUnit, and tests all passing, zero findings. **No "pre-existing failure" exemption:** a mechanical check that is red anywhere (even in untouched code) is fixed before the story is done, never carried forward or waved through. — Rationale: the gate is binary; a green build is a standing invariant, not a per-file courtesy (`06_DEFINITION_OF_DONE.md`).

## SHOULD

- **SHOULD** enforce the license gate in CI via **`com.github.jk1.dependency-license-report`** with an allowed-license policy file: allowlist Apache-2.0/MIT/BSD plus the recorded exceptions **EPL-1.0** (Logback), the **ICU License** (ICU4J), and the **JDOM License** (JDOM2); GPL/LGPL/AGPL/SSPL banned (`docs/specification/05_Dependencies/03_LICENSING.md`). This gate is distinct from OWASP dependency-check (SCA). — Rationale: keeps the distributable license-clean with a concrete, machine-checked policy.
- **SHOULD** keep packaging script-driven: `./gradlew :app:collectDist` stages the app jar + runtime classpath, and the committed `scripts/package-<os>` scripts drive the plain `jpackage` CLI per OS (no `org.beryx.jlink` or other packaging plugin; Windows = portable app-image zip, never an installer), built per-OS in the CI matrix. — Rationale: jpackage cannot cross-compile; scripts keep flags transparent and locally reproducible (DD-24, `03_PACKAGING_JPACKAGE.md`).
- **SHOULD** keep `.editorconfig` and `.gitattributes` (LF) authoritative for line endings/whitespace so Spotless and Git agree. — Rationale: no CRLF churn across platforms.

## Reject if

- Build logic is written in Groovy, or shared config is copy-pasted instead of a convention plugin.
- A dependency version is hard-coded in a script instead of `libs.versions.toml`.
- Dependency lockfiles are missing or stale after a resolution change.
- A commit lands unformatted (`spotlessCheck` would fail) or CI lacks `spotlessCheck`.
- Error Prone+NullAway, Checkstyle, SpotBugs+FindSecBugs, or ArchUnit is disabled/skipped anywhere.
- A story is called done while any mechanical check is red, on the excuse that the failure was "pre-existing" or "not my code".
- A GPL/LGPL/AGPL/SSPL dependency is introduced, or a dependency outside the recorded allowlist (Apache-2.0/MIT/BSD + EPL-1.0/ICU/JDOM exceptions) lands without a licensing-doc update.
- A build invokes a system `gradle` instead of `./gradlew`.
