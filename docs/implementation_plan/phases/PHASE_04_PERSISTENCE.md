---
phase: PHASE_04_PERSISTENCE
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
phase_clauses:
  - docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#ddl-normative
  - docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#tables
  - docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#settings-serialization
  - FR-PERSIST-01
  - FR-PERSIST-02
  - FR-PERSIST-03
  - FR-PERSIST-04
  - FR-PERSIST-05
  - FR-SETTINGS-01
  - FR-RESUME-01
  - FR-RESUME-02
  - FR-RESUME-04
  - DD-11
  - DD-20
  - DD-27
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/03_NonFunctional/05_RELIABILITY_AND_RESUME.md`,
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`

# PHASE_04 — Persistence

## Goal

Provide durable local state: the SQLite (WAL) schema managed by Flyway, JDBI DAOs implementing the repository ports, the
typed `settings(key,value,type)` KV store, crash-safe per-chunk checkpoints, secret references, and atomic writes —
under the per-OS data dir resolved in PHASE_00 (the process single-instance lock is also PHASE_00's; this phase owns
only the DB-connection guard). This establishes seams F6 (checkpoint) and F7 (settings KV) that later long-running and
preference-driven features rely on.

## In scope

- SQLite connection with WAL; Flyway migration baseline for projects, segments, glossary, TM, and rolling-summary
  tables. The `projects` table carries the **per-project provider/model binding** (`provider_id`, `translator_model`,
  `judge_model`, `last_used_json`) per DD-31; the `providers` table has `supports_discovery` and two model slots (no
  embedding).
- JDBI DAOs implementing `ProjectRepository`, `SegmentRepository`, `GlossaryRepository`, `TmRepository`,
  `SettingsRepository`.
- Typed `settings(key,value,type)` KV store with a read/write API (seam F7), including the `ui.language` key (DD-34).
- The **app-paths resolver is consumed here** (PHASE_00, DD-39): SQLite opens the DB under the resolved per-OS **data
  dir** — dev builds under the `-Dev` sibling — so persistence never guesses its own location.
- Per-chunk crash-safe checkpoint writes + a resume reader (seam F6); atomic durable writes.
- Secret references (env-var name / OS keychain entry), never the secret value.
- Per-OS application data directory (consumed from the PHASE_00 `:util.paths` resolver) and the **DB-connection guard**.
  The **process single-instance lock is owned pre-injector by `:app`/`:util` (PHASE_00)**, not by `:persistence`; this
  module only guards its own database connection (`docs/implementation_plan/01_MODULE_INVENTORY.md#lock-ownership`).

## Out of scope

- The pipeline that produces checkpoints (PHASE_06) — here the checkpoint read/write API and schema exist and are
  unit-tested with synthetic data.
- Provider credential capture UI (PHASE_11) — the secret-reference storage exists; entry is later.
- Settings screens (PHASE_11) — the KV store exists; the tabs are later.

## Dependencies

PHASE_00 (module skeleton, `Result`/`AppError`, build/trace tooling).

## Forward-compatibility

- **Establishes F6** — atomic per-chunk checkpoint writes + resume reader, making any later long-running stage
  crash-safe by default (consumed by PHASE_06, PHASE_12).
- **Establishes F7** — the generic typed settings KV so any later feature persists a preference without a schema change
  (consumed by PHASE_09–PHASE_11).
- Consumes F2 (Result/AppError) for repository results.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                                        | Target modules                                                                           | Cited spec clauses                                                                           |
|---------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| SQLite WAL bootstrap + Flyway baseline migration for core tables                                                                      | `:persistence/ua.bookloom.persistence`, `:persistence/ua.bookloom.persistence.migration` | FR-PERSIST-01, `02_Architecture/06_DATA_MODEL_SQLITE.md#ddl-normative`, DD-20                |
| JDBI DAOs implementing project/segment/glossary/TM repository ports                                                                   | `:persistence/ua.bookloom.persistence.dao`, `:api/ua.bookloom.api.persistence`           | FR-PERSIST-01, `02_Architecture/06_DATA_MODEL_SQLITE.md#tables`                              |
| Typed `settings(key,value,type)` KV store + read/write API                                                                            | `:persistence/ua.bookloom.persistence.settings`                                          | FR-SETTINGS-01, DD-20 (seam F7)                                                              |
| Per-chunk crash-safe checkpoint writer + resume reader                                                                                | `:persistence/ua.bookloom.persistence.checkpoint`                                        | FR-RESUME-01, FR-RESUME-02, DD-27 (seam F6)                                                  |
| Atomic durable writes                                                                                                                 | `:persistence/ua.bookloom.persistence`, `:util/ua.bookloom.util.io`                      | FR-PERSIST-02                                                                                |
| Secret references (env-var name / OS keychain), never the value                                                                       | `:persistence/ua.bookloom.persistence.secret`                                            | FR-PERSIST-05, FR-PROV-05, DD-11                                                             |
| Open the DB under the per-OS data dir **consumed from the PHASE_00 `:util.paths` resolver** (persistence never resolves paths itself) | `:persistence/ua.bookloom.persistence`                                                   | FR-PERSIST-03, DD-39, `02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#resolution-mechanism` |
| DB-connection guard (process single-instance lock is owned pre-injector by `:app`/`:util`, PHASE_00)                                  | `:persistence/ua.bookloom.persistence.lock`                                              | FR-PERSIST-04, `docs/implementation_plan/01_MODULE_INVENTORY.md#lock-ownership`              |
| Changed-source detection by hash before resume                                                                                        | `:persistence/ua.bookloom.persistence.checkpoint`                                        | FR-RESUME-04, EC-RESUME-*                                                                    |

## Phase exit checklist

- [ ] SQLite (WAL) schema created and versioned by Flyway; migrations replay clean on an empty DB.
- [ ] JDBI DAOs implement every repository port and return `Result`/`AppError`.
- [ ] Typed settings KV round-trips typed values (F7 established).
- [ ] Per-chunk checkpoints write atomically and a resume reader restores without redoing completed segments (F6
  established).
- [ ] Secret references stored as env-var name / keychain entry; no secret value ever persisted (FindSecBugs + test).
- [ ] Data lives under the per-OS app data dir; the DB-connection guard is in place (the process single-instance lock
  that prevents a second instance is owned pre-injector by `:app`/`:util`, PHASE_00).
- [ ] Tests use a temp DB file or `:memory:` (no Testcontainers); FX-free (ArchUnit green).
- [ ] `./gradlew test` and `./gradlew traceCheck` green; module inventory updated.
