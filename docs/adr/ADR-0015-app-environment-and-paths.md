# ADR-0015 — Per-OS application paths resolved first, hand-rolled, with dev/prod separation

**Status:** accepted **Date:** 2026-07-18 **Deciders:** architect

## Context and problem statement

BookLoom keeps a SQLite database and rolling logs on the user's disk. Before it can open the database or write a log
line it must know *where* those live — and the correct location differs per OS and per convention (data vs logs vs
roaming vs local). Two forces make this more than a one-liner. First, it is a **startup ordering** problem: logging
needs the log directory and SQLite needs the data directory, so path resolution has to happen before either is
initialized — earlier than most of the app exists. Second, a **developer running the app from Gradle/IDE must not read
or corrupt the production database and logs**; dev and prod state must be physically separate.

The specification had per-OS directory strings scattered in the data-model doc but never defined the resolution
mechanism, the dev/prod split, or where this sits in startup — a genuine gap that must be filled before the persistence
work begins.

## Decision drivers

- **Correct per-OS locations.** Data (the large, machine-local WAL database) and logs must follow platform conventions —
  `%LOCALAPPDATA%` not roaming on Windows; `Application Support` + `Library/Logs` on macOS; XDG data + state on Linux.
- **Foundational ordering.** Resolution must run before logging and SQLite, as the very first startup step.
- **Dev/prod isolation.** A development build must never touch production data or logs.
- **Testability.** Resolving the right path for each OS/env must be unit-testable on one machine without mutating the
  real environment.
- **Minimal dependencies.** No native code or heavy library for a ~30-line concern; keep the jlink image small and the
  module graph clean.

## Considered options

- **Option A — Use `net.harawata:appdirs`.** A maintained library that returns per-OS dirs.
- **Option B — Hand-rolled resolver in `:util`** with injected env/property seams, plus a `-Dev` sibling folder selected
  by an `isDev` signal.
- **Option C — Hard-code paths / use `java.util.prefs`.** Rely on the Preferences API or fixed strings.

## Decision outcome

Chosen: **Option B.** A small resolver in `:util` switches on `os.name` and reads `user.home`, `APPDATA`/`LOCALAPPDATA`,
and `XDG_DATA_HOME`/`XDG_STATE_HOME` through **injected `getEnv`/`getProperty` seams**, validating and falling back per
the XDG spec. Production paths: Windows `%LOCALAPPDATA%\BookLoom`, macOS `~/Library/Application Support/BookLoom` +
`~/Library/Logs/BookLoom`, Linux `$XDG_DATA_HOME/bookloom` + `$XDG_STATE_HOME/bookloom/logs`. A **development build**
resolves to a `-Dev`/`-dev` sibling, selected by a pure `isDev` function (`BOOKLOOM_ENV` env → `-Dbookloom.env=prod`
jpackage build stamp → `jpackage.app-path` presence → default dev). Resolution → directory creation → single-instance
lock → logging → SQLite is the fixed startup order. Full detail:
`docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md`.

`appdirs` is rejected because it drags in **JNA** (a native `.dll`/`.so`) to save ~30 lines — complicating the
jlink/JPMS image and adding a native attack surface — and its Linux log location is off the XDG state-dir convention.
`java.util.prefs` stores key/values in the registry/plists, not filesystem directories for a DB, so it does not fit.

### Consequences

Positive:

- Correct, convention-following per-OS locations with full control and no native dependency.
- The resolver and the `isDev` decision are pure functions with injected inputs — every OS/env branch is unit-testable
  on one machine.
- Dev and prod state are physically separate; a dev run cannot corrupt production data, and both can run at once.
- Path resolution is pinned as the first startup step, so logging and SQLite always find their directories.

Negative:

- A little hand-written per-OS code to own and test (mitigated by the seams and a small test matrix).
- The `jpackage.app-path` fallback signal is undocumented; it is only a tertiary heuristic behind the explicit build
  stamp.

Neutral:

- Preferences still live in the SQLite KV table, so only data and logs need OS roots.
- If a future macOS App Store (sandboxed) build is ever made, real paths redirect into the container — noted but out of
  scope for the unsigned jpackage distribution.

## Pros and cons of the options

### Option A — appdirs

Pros: maintained, covers all three OSes. Cons: pulls JNA (native code) into a minimal offline app; off-spec Linux log
dir; version-segment path behavior to work around.

### Option B — hand-rolled + dev/prod (chosen)

Pros: no native dep; exact control; testable seams; clean dev/prod split. Cons: a little code to maintain.

### Option C — hard-code / java.util.prefs

Pros: trivial. Cons: wrong tool for a DB directory; not convention-correct; poor testability; no dev/prod split.

## Links

- Design decisions: DD-39 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-39-app-environment-and-paths`)
- Spec clauses: `docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md`,
  `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#storage-conventions`,
  `docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md`
- Stories: none yet
