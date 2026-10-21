# Persistence (SQLite)

Scope: `:persistence` (`ua.bookloom.persistence..`). Spec: `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`. Stack: `org.xerial:sqlite-jdbc` (WAL) + JDBI 3 (fluent API; Flyway from the first release, ADR-0043). FX-free. Implements the repository ports.

## Current status

`:persistence` has two adapter families behind the `:api` storage ports, each with its own Guice module in
`ua.bookloom.persistence`. `InMemoryPersistenceModule` binds the in-memory adapters
(`.../persistence/memory/`, ADR-0034); the command line (`TranslateLauncher`) and most tests install it, and it never
creates `bookloom.db`. `SqlitePersistenceModule(file, clock)` binds the SQLite adapters (`.../persistence/sqlite/`;
sqlite-jdbc in WAL mode, JDBI's fluent API, state kept as JSON documents in a few tables, real columns only where an
index, a uniqueness rule or a cascade needs them; ADR-0043) over one singleton `SqliteDatabase`; the window installs
it through `CoreModules(startup, persistenceModule)`, `AppLifecycle` phase 2 opens the database at
`AppPaths.databaseFile()` (an open failure is a startup error) and `BookLoomApplication.stop()` closes it. So settings,
providers, books and projects survive a restart in the window only. The abstract
`modules/persistence/src/test/java/ua/bookloom/persistence/contract/RepositoryContractTest.java` runs against both
adapter families. There is no schema migration before the first release (ADR-0043): one schema file, and a database
whose `PRAGMA user_version` differs from it, or that cannot be opened, is renamed aside and a fresh one created.

## MUST

- **MUST** access SQLite in **WAL** mode via `sqlite-jdbc`, through **JDBI DAOs** — no ORM (no Hibernate/JPA). SQL is explicit and reviewable. — Rationale: an embedded DB with hand-written SQL stays predictable and lightweight.
- **MUST** apply the full PRAGMA set **per connection** via a connection-init hook (`journal_mode=WAL`, `foreign_keys=ON`, `synchronous=NORMAL`, `busy_timeout=<N ms>`): these are connection-scoped (only `journal_mode=WAL` persists on the file), so a recycled or newly-opened connection must never run with `foreign_keys` off or without a busy timeout. — Rationale: pragma guarantees hold on every connection, not just the first.
- **MUST** confine all write transactions to a **single-writer** executor/serialized handle; readers may run concurrently on other pooled connections. — Rationale: SQLite's single-writer model stays explicit; contention becomes a bounded `busy_timeout` wait, not `SQLITE_BUSY`.
- **MUST** keep the schema, until the first release, in one schema file of `CREATE TABLE IF NOT EXISTS` statements whose number is held in `PRAGMA user_version`; a database with another number, or one that cannot be opened, is renamed `bookloom.db.old-<yyyyMMdd-HHmmss>` and a fresh one created (one WARN, no dialog, no data carried over; ADR-0043). From the first release, evolve the schema only through **additive Flyway migrations** named `V{ver}__desc.sql`, applied in order at startup, and never edit an applied migration. — Rationale: no installed base exists to migrate before release; afterwards history is forward-only and reproducible.
- **MUST** store preferences in a **generic typed KV `settings(key, value, type)` table** and domain data (providers, model settings, books, projects, project documents, segments, TM entries, deferrals) as JSON documents in a few tables, with real columns only where an index, a uniqueness rule or a cascade needs them (ADR-0043). — Rationale: one flexible settings store, documents that grow without a schema change.
- **MUST** perform **atomic writes** — checkpoint/segment updates commit as a transaction so a crash never leaves a half-written record; resume reads a consistent state. WAL + `synchronous=NORMAL` makes accepted work **process-crash-safe and forced-quit-safe**; on OS crash / power loss at most the last in-flight commit may be lost, never earlier accepted work — do not claim power-loss-proof durability (DD-20). — Rationale: honest, per-chunk crash-safety and clean resume.
- **MUST** store secrets **as references only** (an environment-variable name; an OS-keychain reference is still allowed later but is not built), never the secret value, mirroring `llm-provider-integration.md`. — Rationale: privacy invariant; the DB holds a pointer, not the key.
- **MUST** place the database in the **per-OS user data dir** (platform-appropriate app-data location), created if absent, and rely on the **single-instance lock** (`dataDir/bookloom.lock`) so two app instances never share one DB file — the lock is acquired **pre-injector by `:app`/`:util`** (`Launcher` + `FileChannel.tryLock()`), NOT by `:persistence`, which only guards its own DB connections; a second launch shows a "BookLoom is already running" dialog and exits (no IPC/focus). — Rationale: correct per-user location and no concurrent-writer corruption; the lock exists before any DB is touched.
- **MUST** implement the `:api` repository interfaces here and bind them in the persistence Guice module; callers hold ports, not `..Dao` concretes. — Rationale: layering; see `architecture-layering.md`.

## SHOULD

- **SHOULD** keep DAO row mappers mapping to records (records-first), not mutable beans. — Rationale: immutable read models.
- **SHOULD** write per-chunk checkpoints so `RESUME` can restart mid-book without re-translating accepted segments. — Rationale: supports crash-safe resume; see `threading-concurrency.md`.
- **SHOULD** use `:memory:` or a temp DB file for tests (no Testcontainers). — Rationale: SQLite is embedded; see `testing.md`.

## Reject if

- An ORM is introduced, or SQL is generated/reflected instead of explicit JDBI statements.
- Before the first release, a schema change is made anywhere but the one schema file (edit it in place and raise its `user_version`), or Flyway is added early; from the first release, a schema change is made outside a `V{ver}__desc.sql` Flyway migration, or an applied migration is edited.
- A secret value is written to the database instead of a reference.
- A multi-statement write is not transactional/atomic (crash could leave a partial record).
- The DB is placed outside the per-OS data dir, the DB is opened before the pre-injector `bookloom.lock` is held, or `:persistence` tries to own the single-instance lock itself.
- A pooled connection skips the per-connection PRAGMA re-application, `busy_timeout` is absent, or writes bypass the single-writer executor.
- Durability is documented/claimed as power-loss-proof under `synchronous=NORMAL`.
- `:pipeline`/`:ui` depends on a `..Dao` concrete instead of an `:api` repository port.
