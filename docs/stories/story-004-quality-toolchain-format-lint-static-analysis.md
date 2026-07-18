---
id: STORY-004
title: Wire the quality toolchain — Spotless, Lombok, Error Prone/NullAway, Checkstyle, SpotBugs
status: ready
spec_clauses:
  - docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#tools
  - docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#null-safety
  - docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#error-prone-lombok
  - DD-25
  - DD-05
modules:
  - build-logic
  - :api/ua.bookloom.api
acceptance_criteria:
  - STORY-004-AC-1
  - STORY-004-AC-2
  - STORY-004-AC-3
  - STORY-004-AC-4
edge_cases: [ ]
depends_on:
  - STORY-001
adrs:
  - ADR-0014
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: M
---

# STORY-004 — Wire the quality toolchain — Spotless, Lombok, Error Prone/NullAway, Checkstyle, SpotBugs

## Goal

Make the mechanical gate real: Spotless with Palantir Java Format (120-col), Lombok as the sole annotation processor
running ahead of the Error Prone javac plugin (`net.ltgt.errorprone`, `-XDcompilePolicy=simple`), NullAway with JSpecify
`@NullMarked`, Checkstyle, and SpotBugs + FindSecBugs — all wired into `./gradlew check` by the convention plugins so
every later story compiles under the full analysis pipeline from day one.

## In scope

- `bookloom.spotless-conventions` (Palantir, 120-col; `spotlessApply`/`spotlessCheck`).
- `bookloom.java-conventions` additions: Lombok (sole APT, `lombok.config` with `addNullAnnotations`),
  `net.ltgt.errorprone` + NullAway (JSpecify `@NullMarked` per package), Checkstyle, SpotBugs + FindSecBugs — all bound
  into `check`.
- A `@NullMarked` `package-info.java` convention seeded in existing packages.

## Out of scope

- ArchUnit rules (STORY-005), JaCoCo coverage gate (STORY-009), Lefthook (STORY-011), CI (STORY-012).

## Spec inputs

- `02_QUALITY_GATES.md#tools` — the tool set and what binds into `check`.
- `02_QUALITY_GATES.md#null-safety` — NullAway + JSpecify posture.
- `01_BUILD_AND_TOOLING.md#error-prone-lombok` — the exact compiler-pipeline wiring (Lombok desugars before the Error
  Prone plugin; no processor-ordering myth).
- DD-25, DD-05/ADR-0014 (Lombok on services only; records for carriers).

## Design constraints

- Lombok annotations permitted only on service/component classes (`@RequiredArgsConstructor`/`@Slf4j`/`@Builder`); never
  `@Data`/`@Value` on carriers (DD-05).
- Error Prone SECURITY/CORRECTNESS categories fail the build; formatting is never argued — `spotlessApply` fixes it.
- All configuration lives in `build-logic` convention plugins, not per-subproject scripts (DD-03).

## Acceptance criteria

### STORY-004-AC-1

Given an unformatted source file, `./gradlew spotlessCheck` fails and `./gradlew spotlessApply` reformats it to Palantir
120-col so the check passes. (P1)

### STORY-004-AC-2

Given a class with a NullAway-detectable null defect in a `@NullMarked` package, `./gradlew check` fails with a NullAway
finding; given a Lombok `@RequiredArgsConstructor` service in the same package, the generated constructor compiles clean
under NullAway. (P5)

### STORY-004-AC-3

Given seeded canary violations for Checkstyle and for a SpotBugs/FindSecBugs security pattern, `./gradlew check` fails
on each; removing the canaries restores green. (P5)

### STORY-004-AC-4

`./gradlew check` on the clean scaffold runs Spotless, Error Prone + NullAway, Checkstyle, and SpotBugs + FindSecBugs
(all tasks present in the task graph) and is green. (P1)

## Test plan

- STORY-004-AC-1 → integration ·
  build-logic/src/test/kotlin/ua/bookloom/buildlogic/SpotlessConventionFunctionalTest.kt ·
  unformattedFailsThenApplyFixes · `Proves: STORY-004-AC-1`
- STORY-004-AC-2 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/NullAwayLombokFunctionalTest.kt ·
  nullDefectFailsAndLombokServiceCompiles · `Proves: STORY-004-AC-2`
- STORY-004-AC-3 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/LintCanaryFunctionalTest.kt ·
  checkstyleAndFindSecBugsCanariesFail · `Proves: STORY-004-AC-3`
- STORY-004-AC-4 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/CheckTaskGraphFunctionalTest.kt ·
  checkRunsAllQualityTools · `Proves: STORY-004-AC-4`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate.
