# ADR-0009 — Persist state in SQLite (WAL) with Flyway migrations and JDBI access

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The application must durably store projects, the per-segment translation state machine, glossary and translation-memory
entries, user preferences, and crash-safe per-chunk checkpoints — all locally, with no server and no administration. A
long translation job must survive a crash or quit and resume exactly where it left off, which means every accepted or
flagged chunk has to be written durably before the pipeline proceeds. Preferences are heterogeneous (booleans, numbers,
strings, enums) and evolve over time. The store must live in a per-OS data directory, tolerate concurrent readers while
a job writes, and never corrupt on an unclean shutdown.

This ADR fixes the persistence technology and access approach.

## Decision drivers

- **Durable, crash-safe local storage.** Checkpoints must survive crashes so resume works; writes must be atomic.
- **Zero administration, embedded.** No external database process for a desktop app.
- **Concurrent read during write.** The UI reads while the pipeline writes.
- **Schema evolution.** The schema will change across releases and must migrate forward safely.
- **Heterogeneous, evolving preferences.** A flexible settings store without a migration for every new toggle.
- **Explicit, debuggable SQL** over hidden query generation, for a data model where correctness of the segment state
  machine matters.
- **Fits the license gate** (Apache-2.0/MIT/BSD).

## Considered options

- **Option A — JSON files on disk.** Serialize projects and settings to JSON documents.
- **Option B — SQLite with a full ORM.** SQLite storage accessed through an object-relational mapper.
- **Option C — SQLite (WAL) + Flyway migrations + JDBI**, with a generic typed `settings(key, value, type)` KV table for
  preferences and dedicated tables for domain data.

## Decision outcome

Chosen: **Option C — SQLite in WAL mode, with Flyway migrations and JDBI for SQL access.** SQLite is embedded,
zero-administration, and battle-tested for local durability; WAL mode gives concurrent readers alongside a writer, which
fits a UI reading while the pipeline writes. Flyway versions the schema so it evolves safely across releases. JDBI gives
explicit, debuggable SQL with lightweight record mapping — the right level of control for a data model where the
per-segment state machine (`PENDING → ACCEPTED/FLAGGED → REVISED`) and checkpoint semantics must be exactly right —
without the hidden query generation and lifecycle magic of a full ORM.

Preferences use a generic typed `settings(key, value, type)` KV table so new toggles do not each require a schema
migration, while domain data (projects, segments, glossary, translation memory) lives in dedicated typed tables. Writes
are atomic, the database lives in a per-OS data directory, and a single-instance lock prevents two app instances from
racing on the same store. Crash-safe per-chunk checkpoints are written durably before the pipeline advances, which is
what makes resume-on-launch reliable. With `synchronous=NORMAL` under WAL this durability is **process-crash-safe and
forced-quit-safe**; on an OS crash or power loss at most the last in-flight commit may be lost, never earlier accepted
work (DD-20).

### Consequences

Positive:

- Embedded, zero-admin, corruption-resistant local storage with real transactional durability.
- WAL allows the UI to read while a translation job writes.
- Flyway makes schema evolution explicit and repeatable across releases.
- JDBI keeps SQL visible and debuggable; the segment state machine and checkpoint logic are written in plain, reviewable
  queries.
- The typed KV settings table absorbs new preferences without per-toggle migrations.
- Testing needs no external services — a temp DB file or an in-memory database suffices.

Negative:

- Hand-written SQL and row-to-record mapping is more code than an ORM's derived queries.
- SQLite's concurrency model is single-writer; heavy write contention must be funnelled through disciplined
  transactions.
- Migrations must be authored and ordered carefully; a bad migration is a real hazard.

Neutral:

- The single-instance lock is required to keep two processes from sharing one database file.
- Per-OS data-directory resolution and atomic-write helpers are shared utilities the persistence module owns.

## Pros and cons of the options

### Option A — JSON files

Pros:

- Trivial to start; human-readable; no schema or driver.
- Easy to diff and inspect by hand.

Cons:

- No transactional durability; a crash mid-write can corrupt a file, breaking crash-safe checkpointing outright.
- No concurrent read/write story; the UI and pipeline would race on files.
- Querying, indexing, and relational integrity across projects/segments/glossary/TM are all hand-rolled.
- Schema evolution and partial updates become ad-hoc and error-prone at book scale.

### Option B — SQLite + full ORM

Pros:

- Less boilerplate for simple CRUD; derived queries and entity mapping out of the box.
- Familiar entity-centric programming model.

Cons:

- Hidden query generation and lazy-loading make the exact SQL — and its performance — non-obvious, which is risky for
  the state-machine and checkpoint paths.
- Heavier dependency and lifecycle (sessions, caches, dirty-checking) than a desktop app needs.
- ORM behaviour can fight SQLite's simplicity and WAL semantics; debugging pushes through an abstraction layer.

### Option C — SQLite (WAL) + Flyway + JDBI (chosen)

Pros:

- Embedded, durable, concurrent-read storage that fits crash-safe resume.
- Explicit, debuggable SQL with lightweight record mapping — right control level for the state machine.
- Flyway gives disciplined schema evolution; the typed KV table absorbs preference growth.
- No external services in tests; within the license gate.

Cons:

- More hand-written SQL than an ORM.
- Single-writer concurrency demands transaction discipline.
- Migrations must be authored and reviewed with care.

## Links

- Design decisions: DD-20 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-20-sqlite-persistence`)
- Spec clauses: `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`,
  `docs/specification/03_NonFunctional/05_RELIABILITY_AND_RESUME.md`, `docs/specification/01_Product/07_SETTINGS.md`
- Stories: none yet
