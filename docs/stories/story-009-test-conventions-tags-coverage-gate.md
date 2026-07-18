---
id: STORY-009
title: Establish test conventions, excluded tag sets, and the per-module coverage gate
status: ready
spec_clauses:
  - docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#coverage-gate
  - docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#test-types
  - docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#live-local
  - DD-35
modules:
  - build-logic
acceptance_criteria:
  - STORY-009-AC-1
  - STORY-009-AC-2
  - STORY-009-AC-3
  - STORY-009-AC-4
edge_cases: [ ]
depends_on:
  - STORY-001
  - STORY-004
adrs: [ ]
phase: PHASE_00_SCAFFOLD
owner: tester
estimate: M
---

# STORY-009 — Establish test conventions, excluded tag sets, and the per-module coverage gate

## Goal

Fix the testing substrate every later story assumes: JUnit 5 + AssertJ + Mockito + WireMock + TestFX/Monocle available
through `bookloom.test-conventions`, the three local-only tag sets (`liveLocal`, `promptEval`, `visual`) wired as
registered Gradle tasks and excluded from `test`/`check`/CI, and the JaCoCo coverage gate — exact 80% branch, per
module, only on modules with production code.

## In scope

- `bookloom.test-conventions`: test dependencies, Monocle headless properties for UI tests, JUnit platform config.
- Default `Test` task `excludeTags("liveLocal","promptEval","visual")`; registered `liveLocal`/`promptEval`/`visual`
  tasks including only their tag, not wired into `check`, each no-oping (all-skipped) when its env gate is absent.
- JaCoCo per-module branch threshold (80%) applied only to modules containing production sources; empty/stub modules and
  `:ui`/`:app` presentation excluded per the gate spec.

## Out of scope

- Any actual live/prompt-eval/visual test content (they arrive with their features, PHASE_05+).

## Spec inputs

- `02_QUALITY_GATES.md#coverage-gate` — the exact threshold semantics and exclusions.
- `06_TESTING_STRATEGY.md#test-types` / `#live-local` — the taxonomy and the tag/env-gating wiring this story
  implements.
- DD-35.

## Design constraints

- The excluded tags are the ONLY mechanism keeping local-only sets out of CI — no ad-hoc skips (testing rule).
- Coverage numbers are exact (80%), not "~80%"; the exclusion list is explicit configuration, not convention.

## Acceptance criteria

### STORY-009-AC-1

`./gradlew test` and `./gradlew check` execute zero tests tagged `liveLocal`, `promptEval`, or `visual`, while
`./gradlew liveLocal` (and peers) executes exactly its tag. (P1)

### STORY-009-AC-2

Given the env gate for a tagged set absent, running its task reports all tests skipped (not failed); given the gate
present (a fake endpoint variable), the set executes. (P5)

### STORY-009-AC-3

Given a module with production code below 80% branch coverage, `./gradlew check` fails its verification task; a module
with no production sources is not subject to the gate. (P5)

### STORY-009-AC-4

A TestFX + Monocle canary UI test runs headlessly (no display server) under `check` with the convention-supplied Monocle
properties. (P1)

## Test plan

- STORY-009-AC-1 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/TagWiringFunctionalTest.kt ·
  defaultExcludesAndTaskIncludesTags · `Proves: STORY-009-AC-1`
- STORY-009-AC-2 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/EnvGateFunctionalTest.kt ·
  absentGateSkipsPresentGateRuns · `Proves: STORY-009-AC-2`
- STORY-009-AC-3 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/CoverageGateFunctionalTest.kt ·
  under80FailsEmptyModuleExempt · `Proves: STORY-009-AC-3`
- STORY-009-AC-4 → ui · ui/src/test/java/ua/bookloom/ui/MonocleCanaryTest.java · headlessStageRenders ·
  `Proves: STORY-009-AC-4`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate.
