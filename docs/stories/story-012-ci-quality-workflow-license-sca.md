---
id: STORY-012
title: CI quality workflow — PR gate with license and vulnerability gates
status: ready
spec_clauses:
  - docs/specification/04_Build_and_Release/04_CI_CD.md#quality-job
  - docs/specification/04_Build_and_Release/04_CI_CD.md#no-secrets
  - docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#ci-gates
  - docs/specification/05_Dependencies/03_LICENSING.md#license-gate-tool
  - docs/specification/05_Dependencies/02_DEPENDENCY_POLICY.md#owasp-dependency-check
  - docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-25-quality-tooling
modules:
  - ci
  - tooling
acceptance_criteria:
  - STORY-012-AC-1
  - STORY-012-AC-2
  - STORY-012-AC-3
  - STORY-012-AC-4
  - STORY-012-AC-5
edge_cases: [ ]
depends_on:
  - STORY-004
  - STORY-005
  - STORY-009
  - STORY-010
adrs:
  - ADR-0014
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: L
---

# STORY-012 — CI quality workflow with license and SCA gates

## Goal

Stand up the `ci.yml` GitHub Actions workflow — the PR merge gate — running the full quality job headlessly (Xvfb), plus
the license-compliance gate and the OWASP dependency-check SCA gate, so that no change can merge without passing the
same checks developers run locally.

## In scope

- `.github/workflows/ci.yml` triggered on pull requests targeting `main` (and pushes to PR branches) — the PR merge gate
  of the three-workflow layout in `04_CI_CD.md`.
- The **quality job** as specified in `04_CI_CD.md#quality-job`: checkout, Temurin 25 toolchain setup, Gradle cache,
  then `./gradlew clean build check spotlessCheck` under Xvfb so TestFX/Monocle UI tests run headlessly.
- `./gradlew trace traceCheck` in the quality job (traceability gate from STORY-010).
- The **license gate**: `com.github.jk1.dependency-license-report` with the allowlist policy file (Apache-2.0 / MIT /
  BSD family + the documented EPL-1.0/ICU/JDOM exceptions) failing the build on any non-allowlisted license, and
  `THIRD-PARTY-NOTICES` generation (`03_LICENSING.md#license-gate-tool`).
- The **SCA gate**: OWASP dependency-check over the resolved runtime graph with the documented severity threshold;
  optional `NVD_API_KEY` secret consumed when present, gracefully degraded (longer NVD sync) when absent
  (`02_DEPENDENCY_POLICY.md#owasp-dependency-check`).
- Concurrency group cancelling superseded runs of the same PR; timeout guards on every job.
- Dependency-lock verification: the build runs with locking in strict mode, so a drifted
  `*.lockfile` fails the job (STORY-001's locking is thereby CI-enforced).

## Out of scope

- `build.yml` (main-branch snapshot artifacts) and `release.yml` (tag-driven releases) — the packaging story (STORY-013)
  provides their input; the workflows themselves are PHASE_13 scope refined from `04_CI_CD.md`.
- Any new quality tool or rule — CI invokes gates introduced by earlier stories only.
- Code signing, artifact notarization (explicitly out of project scope).

## Spec inputs

- `docs/specification/04_Build_and_Release/04_CI_CD.md#quality-job` — normative job steps, runner (`ubuntu-24.04`), Xvfb
  wrapper, cache keys.
- `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#ci-gates` — the gate list CI must enforce and
  `#what-fails-where` for the CI column.
- `docs/specification/05_Dependencies/03_LICENSING.md#license-gate-tool` — allowlist mechanics, exceptions, notices
  file.
- `docs/specification/05_Dependencies/02_DEPENDENCY_POLICY.md#owasp-dependency-check` — SCA tool, threshold, NVD key
  handling.
- `docs/specification/04_Build_and_Release/04_CI_CD.md#no-secrets` — only `GITHUB_TOKEN` required;
  `NVD_API_KEY` optional.

## Design constraints

- CI must run **exactly the same Gradle entry points** as the local pre-push hook (`clean build check spotlessCheck`)
  plus CI-only additions (license gate, SCA, trace) — never a weaker variant.
- UI tests run under **Xvfb with Monocle** as configured by STORY-009; no test may require a real display.
- Only `GITHUB_TOKEN` is required; `NVD_API_KEY` is optional and its absence must not fail the workflow.
- `liveLocal`, `promptEval`, and `visual` tagged tests are **excluded** — CI runs default-tag tests only (DD-35 via
  STORY-009's task wiring).
- Workflow YAML must pin action versions (no floating `@main`).

## Acceptance criteria

### STORY-012-AC-1

**Given** a pull request whose tree passes all local gates, **when** `ci.yml` runs, **then** the quality job completes
green: Gradle build + `check` + `spotlessCheck` under Xvfb,
`trace traceCheck`, license gate, and dependency-check all pass, and the job summary lists each gate's outcome.

### STORY-012-AC-2

**Given** a pull request introducing a dependency with a non-allowlisted license (e.g. GPL-2.0), **when** the license
gate runs, **then** the workflow fails naming the offending coordinate and license, and the
`THIRD-PARTY-NOTICES` file is not silently regenerated around it.

### STORY-012-AC-3

**Given** a pull request that changes a dependency version without regenerating lockfiles, **when** the quality job
resolves the configuration, **then** the build fails with a lock-state mismatch before any test executes.

### STORY-012-AC-4

**Given** the repository has no `NVD_API_KEY` secret configured, **when** the SCA step runs, **then** dependency-check
still executes and gates on the documented severity threshold, and the workflow log records that it ran keyless.

### STORY-012-AC-5

**Given** a UI widget test (TestFX/Monocle, default tag) that fails, **when** `ci.yml` runs on that tree, **then** the
quality job fails in the `check` step with the test failure attributed in the Gradle report artifact uploaded by the
workflow.

## Test plan

| Tier         | Test                                                                                        | Proves                 |
|--------------|---------------------------------------------------------------------------------------------|------------------------|
| workflow run | green-path PR against the scaffold tree (recorded run link in PR)                           | Proves: STORY-012-AC-1 |
| workflow run | canary branch adding a GPL-licensed test-only coordinate; assert red + message              | Proves: STORY-012-AC-2 |
| workflow run | canary branch bumping a version without `--write-locks`; assert lock failure                | Proves: STORY-012-AC-3 |
| workflow run | inspect step log of green-path run for keyless-mode line                                    | Proves: STORY-012-AC-4 |
| workflow run | canary branch with a deliberately failing Monocle widget test; assert red + report artifact | Proves: STORY-012-AC-5 |

Canary branches are temporary verification branches, deleted after the run links are recorded in the PR; they are the
standard way to prove a gate actually fails red.

## Definition of done

- All acceptance criteria demonstrably pass with recorded workflow-run evidence.
- `ci.yml`, the license policy file, dependency-check config, and notices generation are committed; action versions
  pinned.
- The implicit clean-gate AC holds: `./gradlew clean build check spotlessCheck` passes project-wide with zero warnings
  treated as errors — no "pre-existing issue" exemptions.
- Traceability: `./gradlew traceCheck` passes with the new story's `Proves:` markers indexed (workflow-run evidence is
  linked from the story file, as workflow YAML carries no code markers).
