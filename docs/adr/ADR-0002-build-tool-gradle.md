# ADR-0002 — Build with Gradle (Kotlin DSL) rather than Maven

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The project is a multi-module JVM application with a JavaFX UI that must be delivered as native per-OS packages
(app-images, `.dmg`, `.deb`; Windows as a portable zip — DD-24). The build has to: manage several subprojects with an
inward dependency direction, centralize dependency versions and locks, apply a shared set of quality-gate plugins
(formatter, linters, boundary tests, coverage), resolve the platform-specific JavaFX artifacts correctly, and stage the
runnable jar set that the committed jpackage scripts package into `.dmg`, portable zips, and `.deb` on a per-OS CI
matrix (DD-24).

The build tool choice determines how much of that is first-class versus hand-rolled. This ADR fixes the build system.

## Decision drivers

- **First-class JavaFX support.** JavaFX artifacts are platform-classified; the build must select the right variant per
  OS without manual classifier juggling.
- **Native packaging plugin ecosystem.** jlink + jpackage orchestration should come from a maintained plugin, not
  bespoke scripting.
- **Multi-module ergonomics.** Convention plugins should let all subprojects share build logic without copy-paste.
- **Centralized, lockable dependencies.** A version catalog plus committed dependency locks for reproducibility.
- **Build performance.** Incremental builds and a build cache matter for a growing multi-module codebase and CI.
- **Type-safe, refactorable build code.** Build logic should be checked by the IDE and the compiler.
- **Reasonable fallback.** The decision should acknowledge that the alternative is viable, not strawmanned.

## Considered options

- **Option A — Gradle with the Kotlin DSL**, a wrapper, `build-logic/` convention plugins, a `gradle/libs.versions.toml`
  version catalog, and committed dependency locks.
- **Option B — Maven** with a parent POM, profiles, and the corresponding JavaFX and jpackage plugins.

## Decision outcome

Chosen: **Option A — Gradle (Kotlin DSL)**, because the JavaFX-plus-jpackage delivery story is materially better
supported in the Gradle ecosystem: the variant-aware `org.openjfx.javafxplugin` resolves the correct per-OS JavaFX
artifacts automatically, and packaging stays a thin, transparent layer — a plain `Sync` task stages the jars and
committed scripts drive the `jpackage` CLI directly (DD-24), so no packaging plugin is needed. The Kotlin DSL gives
type-safe, IDE-checked build code; `build-logic/` convention plugins let every subproject share the quality-gate
configuration; the version catalog plus dependency locking give centralized, reproducible dependency management; and the
build cache and incremental build speed up the multi-module build.

Maven remains a viable fallback and is not dismissed — see the honest note below — but Gradle's plugin ecosystem and
convention-plugin model fit this JavaFX/jpackage application better.

### Consequences

Positive:

- Correct per-OS JavaFX artifact resolution with no manual classifier handling.
- Declarative jlink/jpackage tasks from a maintained plugin, driven by the CI OS matrix (see
  `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md`).
- Shared build logic lives once in `build-logic/`; subprojects stay thin.
- Type-safe build scripts; refactors and version-catalog references are IDE-checked.
- Build cache and incremental builds shorten local and CI cycles.

Negative:

- Gradle's flexibility is also complexity; convention plugins and the configuration model have a real learning curve.
- The Kotlin DSL adds Kotlin compilation to the build path and to contributor onboarding.
- Gradle version upgrades occasionally require plugin and script adjustments.

Neutral:

- Contributors must use the committed wrapper for reproducible builds.
- Build behaviour is governed by convention plugins rather than per-module scripts, centralizing both power and
  responsibility.

## Pros and cons of the options

### Option A — Gradle (Kotlin DSL)

Pros:

- Variant-aware JavaFX plugin resolves platform artifacts automatically.
- Maintained jlink/jpackage plugin turns native packaging into declarative tasks.
- Convention plugins in `build-logic/` remove per-module duplication.
- Version catalog + dependency locking give centralized, reproducible dependencies.
- Build cache and incremental builds improve throughput.
- Type-safe, IDE-navigable build code.

Cons:

- Higher conceptual complexity than Maven's fixed lifecycle.
- Kotlin DSL adds a compilation step and a second language to the repo.
- Plugin/version upgrades can ripple into build scripts.

### Option B — Maven

Pros:

- Simple, well-understood, convention-over-configuration lifecycle.
- Ubiquitous, stable, and low-surprise; XML POMs are easy to diff and review.
- Fully capable of building this application, including JavaFX and jpackage via community plugins — a genuine, workable
  fallback.

Cons:

- JavaFX platform-variant handling and jpackage orchestration are less ergonomic and lean more on manual configuration.
- No first-class shared-convention mechanism equivalent to Gradle convention plugins; cross-module reuse relies on
  parent-POM inheritance, which is coarser.
- No built-in build cache; incremental performance on a growing multi-module build is weaker.
- Build logic is declarative XML, which is harder to extend for custom packaging steps.

## Links

- Design decisions: DD-03 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-03-gradle-kotlin-dsl`)
- Spec clauses: `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md`,
  `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md`,
  `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md`,
  `docs/specification/04_Build_and_Release/04_CI_CD.md`
- Stories: none yet
