---
id: STORY-007
title: Inject the app version from the build into a generated resource
status: ready
spec_clauses:
  - docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#version-injection
  - FR-UI-09
  - DD-50
modules:
  - :app/ua.bookloom.app
  - build-logic
acceptance_criteria:
  - STORY-007-AC-1
  - STORY-007-AC-2
  - STORY-007-AC-3
edge_cases: [ ]
depends_on:
  - STORY-001
adrs: [ ]
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: S
---

# STORY-007 — Inject the app version from the build into a generated resource

## Goal

Make the git tag the single source of the app version with no hand-maintained constant: a `-PappVersion` Gradle property
(default `dev`) drives a `generateVersionResource` task that writes `version.properties` onto the `:app` classpath, and
a small `AppVersion` reader exposes it for the About dialog and the single startup log line — so `dev` in a running
build proves it is not a release artifact.

## In scope

- Root `version` from `providers.gradleProperty("appVersion").orElse("dev")`.
- `generateVersionResource` wired into `:app` `processResources`, version declared as a task input (up-to-date checks
  work).
- The `AppVersion` reader (classpath resource, `dev` fallback); the startup log line `app started version=<v>` emitted
  once from the launcher path.

## Out of scope

- The About dialog rendering the value (PHASE_09/11); jpackage `--app-version` wiring (STORY-013); the release workflow
  passing the tag (PHASE_13).

## Spec inputs

- `01_BUILD_AND_TOOLING.md#version-injection` — the full mechanism, full-vs-numeric split, `dev` semantics.
- FR-UI-09, DD-50.

## Design constraints

- Exactly two consumers ever (About + startup log line); no second stored copy (DD-50).
- The resource approach (not jar-manifest `Implementation-Version`) so the value is identical in `gradlew run`, tests,
  and the packaged image.

## Acceptance criteria

### STORY-007-AC-1

Given a build invoked with `-PappVersion=1.2.0-rc1`, when the resource is read at runtime, then `AppVersion` reports
`1.2.0-rc1`; given no property, it reports `dev`. (P1)

### STORY-007-AC-2

The `generateVersionResource` task declares the version as an input: rebuilding with the same value is up-to-date;
changing the value regenerates the resource. (P3)

### STORY-007-AC-3

On startup the launcher logs exactly one `app started version=<v>` line containing the injected value. (P1)

## Test plan

- STORY-007-AC-1 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/VersionResourceFunctionalTest.kt ·
  propertyInjectsAndDefaultIsDev · `Proves: STORY-007-AC-1`
- STORY-007-AC-2 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/VersionResourceFunctionalTest.kt ·
  upToDateAndRegeneration · `Proves: STORY-007-AC-2`
- STORY-007-AC-3 → integration · app/src/test/java/ua/bookloom/app/AppVersionStartupLogTest.java ·
  singleStartupVersionLine · `Proves: STORY-007-AC-3`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate.
