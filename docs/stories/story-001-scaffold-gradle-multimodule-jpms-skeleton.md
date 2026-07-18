---
id: STORY-001
title: Scaffold the Gradle multi-module build with the eight JPMS modules
status: ready
spec_clauses:
  - docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#build-system
  - docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#project-layout
  - docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#version-catalog
  - docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#dependency-locking
  - docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#dependency-direction
  - DD-03
  - DD-06
modules:
  - :api/ua.bookloom.api
  - :util/ua.bookloom.util.text
  - :app/ua.bookloom.app
  - build-logic
acceptance_criteria:
  - STORY-001-AC-1
  - STORY-001-AC-2
  - STORY-001-AC-3
  - STORY-001-AC-4
  - STORY-001-AC-5
edge_cases: [ ]
depends_on: [ ]
adrs:
  - ADR-0002
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: M
---

# STORY-001 — Scaffold the Gradle multi-module build with the eight JPMS modules

## Goal

Create the buildable skeleton every later story stands on: a Gradle (Kotlin DSL) multi-project with the committed
wrapper, a version catalog, dependency locking, a `build-logic` included build with a minimal java-conventions plugin
(JDK 25 toolchain), and the eight subprojects `:api :util :document :llm :pipeline :persistence :ui :app` as JPMS
modules whose `module-info.java` files declare only the allowed dependency edges.

## In scope

- `settings.gradle.kts` including the eight subprojects + `build-logic`; committed `gradlew` wrapper pinned by version +
  checksum.
- `gradle/libs.versions.toml` as the single source of dependency coordinates; Gradle dependency locking enabled with
  lockfiles committed.
- `build-logic` with a minimal `bookloom.java-conventions` precompiled plugin: JDK 25 toolchain, JPMS compilation.
- One `module-info.java` per subproject (`ua.bookloom.<module>`) with only the edges from the layering spec; a
  placeholder public type per module so each compiles.
- The JavaFX convention applied only to `:ui`/`:app` (openjfx plugin declaring `controls`, `fxml`, `graphics`).

## Out of scope

- Lint/format/static-analysis wiring (STORY-004), ArchUnit (STORY-005), test conventions (STORY-009), CI (STORY-012).
- Any production logic in any module.

## Spec inputs

- `01_BUILD_AND_TOOLING.md#build-system` — Kotlin DSL, wrapper, toolchain 25, JPMS decisions.
- `01_BUILD_AND_TOOLING.md#project-layout` — the exact directory layout to create.
- `01_BUILD_AND_TOOLING.md#version-catalog` / `#dependency-locking` — catalog is single source; locks committed
  (per-platform lock state note for JavaFX).
- `02_MODULES_AND_LAYERING.md#dependency-direction` — the only allowed module edges.
- DD-03 (Gradle Kotlin DSL), DD-06 (JPMS, FX-free core), ADR-0002.

## Design constraints

- Only `:ui` and `:app` may require `javafx.*` (DD-06); all other modules stay FX-free by construction.
- No inline dependency versions — every coordinate through `libs.<alias>` (DD-03).
- Service `module-info.java` files include `requires com.google.guice;` + `opens <impl pkg> to com.google.guice;` per
  `02_MODULES_AND_LAYERING.md` (wired now so STORY-008 needs no module-info churn).
- JavaFX platform-classified configurations follow the per-platform lock-state rule
  (`01_BUILD_AND_TOOLING.md#dependency-locking`).

## Acceptance criteria

### STORY-001-AC-1

Given a clean checkout, when `./gradlew build` runs via the committed wrapper, then all eight subprojects compile as
JPMS modules `ua.bookloom.<module>` and the build is green. (P1)

### STORY-001-AC-2

Calling the compiler on a source file that imports across a forbidden module edge (e.g. `:document` importing
`ua.bookloom.persistence`) yields a compile failure, because `module-info.java` declares no such `requires`. (P5)

### STORY-001-AC-3

Every dependency coordinate in every build script resolves through the version catalog — a repo scan finds no hard-coded
`group:artifact:version` string in `*.gradle.kts` outside the catalog. (P3)

### STORY-001-AC-4

Gradle dependency locking is active: lockfiles are committed, and resolving with a version absent from the lock state
fails the build until locks are rewritten deliberately. (P1)

### STORY-001-AC-5

The JavaFX convention is applied only to `:ui` and `:app`: compiling a `javafx.*` import in `:api`/`:util`/`:document`/
`:llm`/`:pipeline`/`:persistence` fails (no such module on their path). (P5)

## Test plan

- STORY-001-AC-1 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/ScaffoldBuildFunctionalTest.kt ·
  buildsAllEightModules · `Proves: STORY-001-AC-1`
- STORY-001-AC-2 → architecture · arch-test (compile-fixture)
  build-logic/src/test/kotlin/ua/bookloom/buildlogic/ForbiddenEdgeCompileTest.kt · forbiddenEdgeFailsCompilation ·
  `Proves: STORY-001-AC-2`
- STORY-001-AC-3 → unit · build-logic/src/test/kotlin/ua/bookloom/buildlogic/CatalogDisciplineTest.kt ·
  noInlineVersionStrings · `Proves: STORY-001-AC-3`
- STORY-001-AC-4 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/DependencyLockingFunctionalTest.kt ·
  unlockedResolutionFails · `Proves: STORY-001-AC-4`
- STORY-001-AC-5 → architecture · build-logic/src/test/kotlin/ua/bookloom/buildlogic/JavafxScopeCompileTest.kt ·
  javafxImportFailsInCoreModules · `Proves: STORY-001-AC-5`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate
(`./gradlew clean build check spotlessCheck` green, no pre-existing-failure exemption). Module inventory confirmed
current (this story creates the module skeleton the inventory already describes).
