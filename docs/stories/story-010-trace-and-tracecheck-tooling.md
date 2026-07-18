---
id: STORY-010
title: Implement the trace and traceCheck Gradle tooling
status: ready
spec_clauses:
  - docs/implementation_plan/03_TRACEABILITY.md#gradle-tasks
  - docs/implementation_plan/03_TRACEABILITY.md#check-table
  - docs/implementation_plan/03_TRACEABILITY.md#proves-convention
modules:
  - build-logic
  - tooling
acceptance_criteria:
  - STORY-010-AC-1
  - STORY-010-AC-2
  - STORY-010-AC-3
  - STORY-010-AC-4
edge_cases: [ ]
depends_on:
  - STORY-001
  - STORY-009
adrs: [ ]
phase: PHASE_00_SCAFFOLD
owner: tester
estimate: M
---

# STORY-010 — Implement the trace and traceCheck Gradle tooling

## Goal

Make traceability executable: `./gradlew trace` regenerates `docs/traceability.yaml` by scanning test sources for
`// Proves:` markers (JavaParser, mapped to enclosing test-method FQNs) against a parsed index of requirement IDs and
the module inventory, writing a canonical fingerprint; `./gradlew traceCheck` validates the per-story link checks, the
phase-exit orphan-clause check driven by the `phase_clauses:` manifests, story-front-matter integrity (including
`phase:` resolution, check #13), and fingerprint freshness.

## In scope

- The `trace` task: `// Proves:` scanner over all test source sets; FR/DD/EC-ID index parsed from the spec tables;
  module-inventory index; canonical sorted fingerprint; generated-file header ("never hand-edit").
- The `traceCheck` task: every check in the traceability check table, including dependency-acyclicity,
  AC-heading/front-matter equality, clause resolution via the spec index, module-path existence, `phase:` file
  resolution, and the phase-exit orphan-clause check reading `phase_clauses:`.
- Failure messages that name the story/clause/test at fault.

## Out of scope

- Any story content itself; CI invocation (STORY-012 wires `traceCheck` into the quality job).

## Spec inputs

- `03_TRACEABILITY.md#gradle-tasks` — generator design (JavaParser, FR index, fingerprint).
- `03_TRACEABILITY.md#check-table` — the complete check list `traceCheck` must implement.
- `03_TRACEABILITY.md#proves-convention` — the marker grammar (`// Proves: STORY-NNN-AC-N`, `// Proves: EC-AREA-N`).

## Design constraints

- `docs/traceability.yaml` is generated-only; `trace` output is deterministic (sorted, canonical) so diffs are
  meaningful.
- The tooling lives in `build-logic`/`tooling`, not in a production module; it must not require the app to compile
  beyond test sources.

## Acceptance criteria

### STORY-010-AC-1

Given a test whose first line is `// Proves: STORY-010-AC-1`, when `./gradlew trace` runs, then `docs/traceability.yaml`
maps that AC to the test's method FQN and carries a fresh canonical fingerprint. (P1)

### STORY-010-AC-2

Given a story front-matter whose `acceptance_criteria` list disagrees with its body headings, or whose `phase:` does not
resolve to a phase file, `./gradlew traceCheck` fails naming the story and the violated check. (P5)

### STORY-010-AC-3

Given a phase whose stories are all `done` while a `phase_clauses:` manifest entry is cited by no non-superseded story,
`traceCheck` fails the phase-exit orphan-clause check; with every clause cited, it passes. (P5)

### STORY-010-AC-4

Given a stale `docs/traceability.yaml` (tests changed since generation), `traceCheck` fails on fingerprint freshness
until `trace` is re-run. (P1)

## Test plan

- STORY-010-AC-1 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/TraceGeneratorFunctionalTest.kt ·
  provesMarkerMappedAndFingerprinted · `Proves: STORY-010-AC-1`
- STORY-010-AC-2 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/TraceCheckStoryIntegrityTest.kt ·
  acMismatchAndBadPhaseFail · `Proves: STORY-010-AC-2`
- STORY-010-AC-3 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/TraceCheckOrphanClauseTest.kt ·
  orphanManifestClauseFailsPhaseExit · `Proves: STORY-010-AC-3`
- STORY-010-AC-4 → integration · build-logic/src/test/kotlin/ua/bookloom/buildlogic/TraceCheckFreshnessTest.kt ·
  staleFingerprintFails · `Proves: STORY-010-AC-4`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate. From this
story on, `trace`/`traceCheck` run in every later story's DoD.
