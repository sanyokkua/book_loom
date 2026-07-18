---
id: STORY-003
title: Implement the app-paths resolver with dev/prod separation
status: ready
spec_clauses:
  - docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#resolution-mechanism
  - docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#per-os-locations
  - docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#dev-vs-prod
  - docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#edge-cases
  - FR-PERSIST-03
  - FR-PERSIST-06
  - DD-39
modules:
  - :util/ua.bookloom.util.paths
acceptance_criteria:
  - STORY-003-AC-1
  - STORY-003-AC-2
  - STORY-003-AC-3
  - STORY-003-AC-4
  - STORY-003-AC-5
  - STORY-003-AC-6
edge_cases:
  - EC-ENV-1
  - EC-ENV-2
  - EC-ENV-6
  - EC-ENV-7
  - EC-ENV-9
depends_on:
  - STORY-001
  - STORY-002
adrs:
  - ADR-0015
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: L
---

# STORY-003 — Implement the app-paths resolver with dev/prod separation

## Goal

Give the application its foundational answer to "where do my database and logs live": a hand-rolled resolver in
`:util.paths` that returns the correct per-OS data and log directories through injected env/property seams, separates
development from production via the `isDev` signal (a dev build never touches production folders), honours the
`BOOKLOOM_DATA_DIR` override, and creates directories idempotently on first run — all before logging or SQLite exist.

## In scope

- `AppPaths` resolver exposing `dataDir()`, `logDir()`, `lockFile()`, `databaseFile()` as `Path`s.
- Injected `getEnv`/`getProperty` seams (no direct `System.*`) so every OS/env branch is unit-testable on one machine.
- Per-OS resolution: Windows `%LOCALAPPDATA%\BookLoom`, macOS `Application Support` + `Library/Logs`, Linux XDG data +
  state — with validation and fallbacks.
- `isDev` as a pure function with the specified precedence (`BOOKLOOM_ENV` → `-Dbookloom.env=prod` stamp →
  `jpackage.app-path` → default dev) selecting the `-Dev`/`-dev` sibling.
- `BOOKLOOM_DATA_DIR` absolute-path override; idempotent `createDirectories`; typed startup error (via the STORY-002
  envelope) on creation failure.

## Out of scope

- Acquiring the single-instance lock and the startup ordering (STORY-008).
- Logging configuration that consumes `logDir()` (STORY-006).

## Spec inputs

- `11_APP_ENVIRONMENT_AND_PATHS.md#resolution-mechanism` — seams, validation, exposed accessors.
- `#per-os-locations` — the exact per-OS table incl. the LOCALAPPDATA and XDG-state rationale.
- `#dev-vs-prod` — the `isDev` precedence and `-Dev` sibling naming.
- `#edge-cases` — EC-ENV behaviours this story must prove.
- FR-PERSIST-03/06, DD-39, ADR-0015.

## Design constraints

- Pure functions over injected inputs; no static state; no logging from inside the resolver (it runs before logging
  exists — `10_DI_AND_LIFECYCLE.md#logging-bootstrap-order`).
- Failure surfaces as the typed envelope (DD-14), not exceptions across the boundary.
- `:util` depends only on `:api` (layering).

## Acceptance criteria

### STORY-003-AC-1

Given simulated Windows, macOS, and Linux environments (via the injected seams), when paths resolve, then data and log
dirs match the per-OS table exactly for both prod (`BookLoom`/`bookloom`) and dev (`BookLoom-Dev`/`bookloom-dev`). (P1)

### STORY-003-AC-2

Given conflicting `isDev` signals, when the signal resolves, then precedence is `BOOKLOOM_ENV` over the build stamp over
`jpackage.app-path`, and an un-stamped run defaults to dev. (P1)

### STORY-003-AC-3

Given `BOOKLOOM_DATA_DIR` set to an absolute path, when paths resolve, then it becomes the data dir verbatim with logs
under its `logs/` child; a blank or relative value is ignored with fallback to per-OS logic. (EC-ENV-9) (P1)

### STORY-003-AC-4

Given a blank or relative `XDG_DATA_HOME`/`XDG_STATE_HOME`, when Linux paths resolve, then the value is ignored and the
`~/.local/...` fallback is used. (EC-ENV-1) (P5)

### STORY-003-AC-5

Given `%LOCALAPPDATA%`/`%APPDATA%` unset on Windows, then the resolver falls back through `%USERPROFILE%\AppData\Local`
to `user.home`; given a blank `user.home`, resolution yields a typed fatal startup error, and a failed
`createDirectories` yields a typed startup error with no partial state. (EC-ENV-6, EC-ENV-7, EC-ENV-2) (P5)

### STORY-003-AC-6

Calling `createDirectories` twice for the same resolved paths is idempotent — the second call succeeds without error and
changes nothing. (P1)

## Test plan

- STORY-003-AC-1 → unit · util/src/test/java/ua/bookloom/util/paths/AppPathsResolverTest.java ·
  resolvesPerOsProdAndDevPaths · `Proves: STORY-003-AC-1`
- STORY-003-AC-2 → unit · util/src/test/java/ua/bookloom/util/paths/IsDevSignalTest.java ·
  precedenceEnvThenStampThenJpackageThenDev · `Proves: STORY-003-AC-2`
- STORY-003-AC-3 → unit · util/src/test/java/ua/bookloom/util/paths/DataDirOverrideTest.java ·
  absoluteOverrideWinsBlankIgnored · `Proves: STORY-003-AC-3` · `Proves: EC-ENV-9`
- STORY-003-AC-4 → unit · util/src/test/java/ua/bookloom/util/paths/XdgFallbackTest.java · blankOrRelativeXdgFallsBack ·
  `Proves: STORY-003-AC-4` · `Proves: EC-ENV-1`
- STORY-003-AC-5 → unit · util/src/test/java/ua/bookloom/util/paths/WindowsFallbackAndFatalsTest.java ·
  localAppDataFallbackChainAndTypedFatals · `Proves: STORY-003-AC-5` · `Proves: EC-ENV-6` · `Proves: EC-ENV-7` ·
  `Proves: EC-ENV-2`
- STORY-003-AC-6 → unit · util/src/test/java/ua/bookloom/util/paths/FirstRunCreationTest.java ·
  createDirectoriesIsIdempotent · `Proves: STORY-003-AC-6`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate. All listed
EC-ENV edge cases carry proving tests.
