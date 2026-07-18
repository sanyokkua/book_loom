---
id: STORY-008
title: Build the launcher, Guice composition root, two-phase init, and single-instance lock
status: ready
spec_clauses:
  - docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md#composition-root
  - docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md#two-phase-init
  - docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md#single-instance-lock
  - docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#startup-order
  - docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#lock-and-atomic
  - FR-PERSIST-04
  - DD-04
  - DD-39
modules:
  - :app/ua.bookloom.app
  - :ui/ua.bookloom.ui
acceptance_criteria:
  - STORY-008-AC-1
  - STORY-008-AC-2
  - STORY-008-AC-3
  - STORY-008-AC-4
  - STORY-008-AC-5
edge_cases:
  - EC-ENV-3
  - EC-ENV-4
depends_on:
  - STORY-002
  - STORY-003
  - STORY-006
  - STORY-007
adrs:
  - ADR-0015
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: L
---

# STORY-008 — Build the launcher, Guice composition root, two-phase init, and single-instance lock

## Goal

An empty-but-correct application launches: a plain `Launcher` (not extending `Application`) runs the fixed startup
order — resolve `isDev` and paths, create directories, acquire the `bookloom.lock` single-instance lock, initialize
logging, then hand off to the JavaFX `Application` that builds the single Guice injector (`:app` composition root,
constructor injection only) with two-phase init (Phase 1 constructs services; Phase 2 opens resources — a stub here,
since no DB exists yet) — and exits cleanly. A second launch shows the "BookLoom is already running" dialog and exits
without touching anything.

## In scope

- `Launcher` + `Application` subclass; the Phase 0 startup order exactly as specified.
- The Guice composition root assembling one module per subproject (empty modules acceptable at this phase);
  `FXMLLoader.setControllerFactory` seam prepared.
- `FileChannel.tryLock()` on `bookloom.lock` in the resolved data dir; the already-running dialog + exit path (no IPC,
  no focus).
- Graceful shutdown skeleton with bounded await budgets; the startup version log line (from STORY-007) emitted at the
  right point.

## Out of scope

- SQLite/Flyway opening in Phase 2 (PHASE_04 backfills the stub); real screens/navigation (PHASE_09).

## Spec inputs

- `10_DI_AND_LIFECYCLE.md#composition-root` / `#two-phase-init` — injector assembly, phase split, side-effect-free
  construction.
- `10_DI_AND_LIFECYCLE.md#single-instance-lock` + `11_APP_ENVIRONMENT_AND_PATHS.md#lock-and-atomic` — pre-injector lock,
  dialog+exit semantics.
- `11_APP_ENVIRONMENT_AND_PATHS.md#startup-order` — the seven-step order this story realizes (steps 1–5 + stubs).
- FR-PERSIST-04, DD-04, DD-39, ADR-0015.

## Design constraints

- Constructor injection only; no field/setter injection; no static holders (DD-04).
- The lock is acquired pre-injector by `:app`/`:util` — never by `:persistence`.
- No class on the bootstrap path declares a static `Logger` (STORY-005 rule 8).
- Only `:ui`/`:app` touch `javafx.*` (DD-06); dev builds resolve `-Dev` paths end to end (EC-ENV-4).

## Acceptance criteria

### STORY-008-AC-1

Given a clean environment, when the app starts headlessly (Monocle), then the observed order is: paths resolved →
directories created → lock acquired → logging initialized → injector built → Phase 2 stub completed → primary Stage
shown — and shutdown releases the lock and exits code 0. (P1)

### STORY-008-AC-2

From launch, on a second concurrent launch, the second process shows the "BookLoom is already running" dialog and exits
without creating, writing, or locking anything in the data dir; the first instance is unaffected. (EC-ENV-3) (P2)

### STORY-008-AC-3

Given `isDev` true, when the app starts, then every path it touches (data, logs, lock) is under the `-Dev`/`-dev`
sibling, and a prod-stamped start touches only the production folders — the two can run simultaneously. (EC-ENV-4) (P1)

### STORY-008-AC-4

Calling injector construction (Phase 1) performs no I/O: no file, directory, or network touch occurs until Phase 2
runs — verified by constructing the injector in a sandboxed test without any resolved directories. (P3)

### STORY-008-AC-5

The Guice graph binds one module per subproject and controllers resolve through the injector-backed controller factory;
no other module constructs an injector (source scan). (P3)

## Test plan

- STORY-008-AC-1 → integration · app/src/test/java/ua/bookloom/app/StartupOrderTest.java ·
  startupOrderObservedAndCleanExit · `Proves: STORY-008-AC-1`
- STORY-008-AC-2 → integration · app/src/test/java/ua/bookloom/app/SingleInstanceLockTest.java ·
  secondLaunchDialogAndExit · `Proves: STORY-008-AC-2` · `Proves: EC-ENV-3`
- STORY-008-AC-3 → integration · app/src/test/java/ua/bookloom/app/DevProdIsolationTest.java ·
  devAndProdFoldersDisjoint · `Proves: STORY-008-AC-3` · `Proves: EC-ENV-4`
- STORY-008-AC-4 → unit · app/src/test/java/ua/bookloom/app/TwoPhaseInitTest.java · phaseOneIsPure ·
  `Proves: STORY-008-AC-4`
- STORY-008-AC-5 → architecture · arch-test/src/test/java/ua/bookloom/arch/CompositionRootTest.java ·
  singleInjectorInAppOnly · `Proves: STORY-008-AC-5`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate. The app-boot
smoke of the testing strategy is satisfied by AC-1's test.
