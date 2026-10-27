# ADR-0043 — Keep the application's state in SQLite as JSON documents, with no schema migrations before the first release

**Status:** accepted 2026-10-20 **Date:** 2026-10-20 **Deciders:** owner (plan approval for
`finalize-ui-and-persistence`)
**Amends:** ADR-0009 · **Supersedes:** ADR-0034 for the desktop window (ADR-0034 still holds for the command line and
the tests)

## Context and problem statement

ADR-0034 put the working state of a translation behind storage ports with in-memory adapters, so nothing survives a
restart. The Projects screen, the stored providers and settings, and "continue after a restart"
(`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`, "Persistence") need that state on disk.

ADR-0009 chose SQLite with Flyway and JDBI, typed tables for domain data, and a typed key/value table for preferences.
The schema in `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md` has fallen behind the records the code
carries today: segment records with findings and context snapshots, glossary tombstones, the recurring-term lexicon,
runs, project names and per-model settings. A column-for-column table set would need a DAO mapper and a schema edit for
every record field.

The owner asked for "the simplest schema, key/value, not overengineered" (2026-10-20). The owner also ruled that
**breaking changes are acceptable until the real release**, so no time is spent on migrations.

## Decision

- **SQLite (sqlite-jdbc, WAL) with JDBI's fluent API.** No ORM and no SQL objects.
- **JSON documents in a few tables.**
  - Everything the code only reads and writes whole is a JSON column, serialised with Jackson in `:persistence`. Mix-ins
    describe the sealed types, so `:api` stays framework-free.
  - Real columns exist only where an index, a uniqueness rule or a cascade needs them (project name key per book,
    content hash, language pair, segment status and order), plus the few plain fields the Projects list shows without
    reading a JSON document (book title and format, project name and origin, timestamps).
  - The tables are `settings(key, value, type)`, `providers`, `model_settings`, `books`, `projects`, `project_docs`,
    `segments`, `tm_entries` and `deferrals`.
- **No migrations before the first release.**
  - One schema file creates every table with `IF NOT EXISTS`, and `PRAGMA user_version` holds its number.
  - A database with another number, or one that cannot be opened, is renamed `bookloom.db.old-<yyyyMMdd-HHmmss>` and a fresh one
    is created. That produces one WARN line, no dialog, one warning message naming the renamed file, and no data
    carried over. A file that is locked or cannot be written is never renamed: the start stops with a message.
  - The schema file is edited in place until release. Flyway, with additive migrations as ADR-0009 describes, arrives
    with the first release.
- **One writer.**
  - A single-thread executor runs every write as one transaction. Readers use other connections.
  - Every connection applies `foreign_keys=ON`, `synchronous=NORMAL` and `busy_timeout`.
  - A chunk's decisions, memory entries, deferrals, glossary additions and run record commit together. This is
    ADR-0034's commit rule, now as a real transaction.
- **Window on disk, command line in memory.**
  - The desktop window binds the SQLite adapters.
  - The command line (`scripts/e2e-fixture.sh`, the evaluation tools) keeps the in-memory adapters, so a test or
    evaluation run never fills the Projects list (owner, 2026-10-20).
  - The repository contract tests run against both adapters.
- **Credentials stay references.** A provider stores an environment-variable name, never a key. A custom header whose
  name looks like a credential must take its value from an environment variable.

## Considered options

- **Typed entity tables per record, with Flyway from the start (ADR-0009 as written).** Rejected for now: a mapper and
  a migration for every field, while the records are still changing every week.
- **One key/value table for everything.** Rejected: name uniqueness, the lookup of a book's language pair, and
  segment-status counts would all move into Java scans.
- **JSON files in the data folder.** Rejected again for the reasons in ADR-0009: no atomic multi-record commit and no
  single-writer guarantee.

## Consequences

- **Positive:**
  - A record field can be added without a schema change.
  - Persistence is a second adapter, not a redesign.
  - Projects, settings and run state survive a restart.
- **Negative:**
  - The JSON is not convenient to query by hand.
  - Editing one glossary term rewrites that project's glossary document (tens of kilobytes) in one transaction.
  - Until release, a schema change discards the developer's local database.
- **Neutral:** the in-memory adapters stay in `src/main` as the command line's storage.

## What would falsify this decision

- A screen needs a query the document layout cannot answer without loading every project's documents, for example
  sorting or filtering projects by a field held only inside JSON and taking longer than 200 ms with 200 projects.
- Or a whole-book run's checkpoint writes take more than 10 % of the run's time.

Either result means the hot fields belong in columns.

## Links

- Design decisions: DD-20 (durability).
- Spec clauses: `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`, `01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`.
- Change: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/`.
