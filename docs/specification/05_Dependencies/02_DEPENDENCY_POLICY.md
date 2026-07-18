**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/05_Dependencies/01_DEPENDENCIES.md`,
`docs/specification/05_Dependencies/03_LICENSING.md`, `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md`,
`docs/specification/04_Build_and_Release/04_CI_CD.md`

# Dependency Policy

How dependencies are declared, pinned, updated, and audited. The goals are reproducibility, a small permissive-only
surface, and no vulnerable or over-scoped libraries.

## exact-versions {#exact-versions}

- Every dependency is pinned to an **exact version** — no dynamic (`+`, `latest.release`) or open ranges. A build
  resolves the same versions on every machine.
- Versions live only in `gradle/libs.versions.toml`; subprojects reference `libs.<alias>` and never hard-code a version.

## version-catalog {#version-catalog}

The Gradle **version catalog** is the single source of truth: `[versions]` (numbers), `[libraries]` (coordinates →
version ref), `[bundles]` (grouped sets, e.g. a `testing` bundle), `[plugins]` (Gradle plugins incl. openjfx; packaging
is script-driven jpackage, no packaging plugin — DD-24). Adding or bumping a dependency is a catalog edit, reviewed like
code.

## dependency-locking {#dependency-locking}

Gradle **dependency locking** is enabled for all configurations and the lockfiles are **committed**. Resolution is thus
fully reproducible; an unexpected transitive change fails the build until the lock is regenerated deliberately
(`./gradlew --write-locks`) and reviewed. CI verifies against the committed locks.

## renovate {#renovate}

**Renovate** proposes dependency and catalog updates as pull requests on a schedule. Updates flow through the normal
quality job (`04_Build_and_Release/04_CI_CD.md`) — a bump is merged only if formatting, lint, ArchUnit, tests, coverage,
SCA, and the license gate all pass. No auto-merge of majors; security patches are prioritized.

## owasp-dependency-check {#owasp-dependency-check}

**OWASP dependency-check** runs in CI as software-composition analysis: it scans the resolved graph against
known-vulnerability databases and **fails the build** above the configured severity threshold. A flagged dependency must
be upgraded, replaced, or explicitly risk-accepted with justification before merge.

## narrowest-scope {#narrowest-scope}

- A dependency is declared in **only** the module that uses it, and at the **narrowest configuration** —
  `testImplementation` for test-only libs, `implementation` (not `api`) unless a type is genuinely part of a module's
  exported surface, `compileOnly` for annotations (JSpecify, Error Prone).
- This keeps the JPMS `requires` graph minimal (`02_Architecture/02_MODULES_AND_LAYERING.md`), prevents leaking a
  transitive dependency onto downstream modules, and keeps the jlink runtime image small.
- No dependency is added to the root/shared config "for convenience."

## adding-a-dependency {#adding-a-dependency}

A new dependency requires: (1) a permissive license passing the gate (`03_LICENSING.md`), (2) a catalog entry with an
exact pin, (3) declaration in the single owning module at the narrowest scope, (4) a regenerated committed lockfile, (5)
green SCA. Introducing it in code without these steps fails CI.
