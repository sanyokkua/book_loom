---
id: STORY-011
title: Lefthook git hooks — staged-file format/lint on commit, local CI mirror on push
status: ready
spec_clauses:
  - docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#lefthook-stages
  - docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#what-fails-where
  - docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-25-quality-tooling
modules:
  - tooling
acceptance_criteria:
  - STORY-011-AC-1
  - STORY-011-AC-2
  - STORY-011-AC-3
edge_cases: [ ]
depends_on:
  - STORY-004
  - STORY-005
  - STORY-009
adrs:
  - ADR-0014
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: S
---

# STORY-011 — Lefthook git hooks

## Goal

Install and configure Lefthook so that every commit auto-formats/lints what is staged and every push runs the same
quality gate CI runs, catching failures before they reach the remote.

## In scope

- `lefthook.yml` at the repository root defining the `pre-commit` and `pre-push` stages exactly as specified in
  `02_QUALITY_GATES.md#lefthook-stages`.
- Pre-commit: Spotless apply on staged Java files (re-staging formatted output), Checkstyle on staged files; fast — no
  test execution at commit time.
- Pre-push: the local mirror of the CI quality gate — `./gradlew clean build check spotlessCheck`
  (compile, all default-tag tests, static analysis, coverage, ArchUnit, format check).
- A documented, scripted install step (`lefthook install`) wired into the standard developer setup path so hooks are
  active after checkout without manual ceremony.
- Bypass documentation: `git push --no-verify` escape hatch noted in the hook config comments.

## Out of scope

- CI workflow definitions (STORY-012) — hooks mirror CI, they do not replace it.
- Any new quality tool: hooks only invoke gates introduced by STORY-004/005/009.
- Windows-specific shell wrappers beyond what Lefthook provides natively.

## Spec inputs

- `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#lefthook-stages` — the normative stage table: what runs
  at commit vs push.
- `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#what-fails-where` — the fails-at-commit / fails-at-push /
  fails-in-CI matrix the hook config must reproduce.
- `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-25-quality-tooling` — decision that gates are enforced
  locally via Lefthook and mirrored in CI.

## Design constraints

- Hooks must invoke the **same Gradle tasks** as CI — no hook-only variants, so a green push implies a green CI quality
  job for the same tree.
- Pre-commit must operate on **staged files only** and re-stage Spotless output, so a partial stage is never mixed with
  unrelated formatting churn.
- Hook scripts must be POSIX-portable where shell is used (macOS/Linux) and rely on Lefthook's runner on Windows.
- No secrets, no network access in hooks.

## Acceptance criteria

### STORY-011-AC-1

**Given** the repository is freshly cloned and the documented setup step has run, **when** `git commit` is invoked with
a staged, mis-formatted Java file, **then** the pre-commit hook formats the file via Spotless, re-stages it, and the
resulting commit contains only correctly formatted content (verified by `./gradlew spotlessCheck` passing on the
committed tree).

### STORY-011-AC-2

**Given** a working tree containing a change that fails the quality gate (e.g. a Checkstyle violation or a failing unit
test), **when** `git push` is invoked, **then** the pre-push hook fails with a non-zero exit before any ref is pushed,
and its output names the failing Gradle task.

### STORY-011-AC-3

**Given** the `lefthook.yml` configuration, **when** it is compared against `02_QUALITY_GATES.md#lefthook-stages`,
**then** every stage listed there is present with the specified command, no extra blocking stage exists, and the
pre-push command is exactly the project-wide clean gate (`./gradlew clean build check spotlessCheck`).

## Test plan

| Tier           | Test                                                                                                                      | Proves                 |
|----------------|---------------------------------------------------------------------------------------------------------------------------|------------------------|
| scripted check | `tooling/hooks-test/commit_formats_staged.sh` — stages a mis-formatted file, commits, asserts formatted content committed | Proves: STORY-011-AC-1 |
| scripted check | `tooling/hooks-test/push_blocks_on_gate_failure.sh` — introduces a Checkstyle violation, asserts push fails pre-transfer  | Proves: STORY-011-AC-2 |
| review check   | config-to-spec table comparison recorded in the PR description                                                            | Proves: STORY-011-AC-3 |

Hook behaviour tests run locally (they need a git repo + hooks installed); they are scripted and repeatable but are not
part of the Gradle `check` graph.

## Definition of done

- All acceptance criteria demonstrably pass.
- `lefthook.yml` and any helper scripts are committed; setup documentation updated.
- The implicit clean-gate AC holds: `./gradlew clean build check spotlessCheck` passes project-wide with zero warnings
  treated as errors — no "pre-existing issue" exemptions.
- Traceability: each test carries its `// Proves:` marker (scripted checks carry the marker in a header comment) and
  `./gradlew traceCheck` passes.
