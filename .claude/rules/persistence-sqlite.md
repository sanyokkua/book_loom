# Persistence (SQLite)

Scope: `:persistence` (`ua.bookloom.persistence..`). Spec: `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`. Stack: `org.xerial:sqlite-jdbc` (WAL) + Flyway + JDBI 3. FX-free. Implements the repository ports.

## MUST

- **MUST** access SQLite in **WAL** mode via `sqlite-jdbc`, through **JDBI DAOs** — no ORM (no Hibernate/JPA). SQL is explicit and reviewable. — Rationale: an embedded DB with hand-written SQL stays predictable and lightweight.
- **MUST** apply the full PRAGMA set **per connection** via a connection-init hook (`journal_mode=WAL`, `foreign_keys=ON`, `synchronous=NORMAL`, `busy_timeout=<N ms>`): these are connection-scoped (only `journal_mode=WAL` persists on the file), so a recycled or newly-opened connection must never run with `foreign_keys` off or without a busy timeout. — Rationale: pragma guarantees hold on every connection, not just the first.
- **MUST** confine all write transactions to a **single-writer** executor/serialized handle; readers may run concurrently on other pooled connections. — Rationale: SQLite's single-writer model stays explicit; contention becomes a bounded `busy_timeout` wait, not `SQLITE_BUSY`.
- **MUST** evolve schema only through **additive Flyway migrations** named `V{ver}__desc.sql`, applied in order at startup; never edit an applied migration. — Rationale: forward-only, reproducible schema history.
- **MUST** store preferences in a **generic typed KV `settings(key, value, type)` table** and domain data (project/segment/glossary/TM/summary) in dedicated entity tables. — Rationale: one flexible settings store, typed entities for domain data.
- **MUST** perform **atomic writes** — checkpoint/segment updates commit as a transaction so a crash never leaves a half-written record; resume reads a consistent state. WAL + `synchronous=NORMAL` makes accepted work **process-crash-safe and forced-quit-safe**; on OS crash / power loss at most the last in-flight commit may be lost, never earlier accepted work — do not claim power-loss-proof durability (DD-20). — Rationale: honest, per-chunk crash-safety and clean resume.
- **MUST** store secrets **as references only** (env-var name / OS-keychain handle), never the secret value, mirroring `llm-provider-integration.md`. — Rationale: privacy invariant; the DB holds a pointer, not the key.
- **MUST** place the database in the **per-OS user data dir** (platform-appropriate app-data location), created if absent, and rely on the **single-instance lock** (`dataDir/bookloom.lock`) so two app instances never share one DB file — the lock is acquired **pre-injector by `:app`/`:util`** (`Launcher` + `FileChannel.tryLock()`), NOT by `:persistence`, which only guards its own DB connections; a second launch shows a "BookLoom is already running" dialog and exits (no IPC/focus). — Rationale: correct per-user location and no concurrent-writer corruption; the lock exists before any DB is touched.
- **MUST** implement the `:api` repository interfaces here and bind them in the persistence Guice module; callers hold ports, not `..Dao` concretes. — Rationale: layering; see `architecture-layering.md`.

## SHOULD

- **SHOULD** keep DAO row mappers mapping to records (records-first), not mutable beans. — Rationale: immutable read models.
- **SHOULD** write per-chunk checkpoints so `RESUME` can restart mid-book without re-translating accepted segments. — Rationale: supports crash-safe resume; see `threading-concurrency.md`.
- **SHOULD** use `:memory:` or a temp DB file for tests (no Testcontainers). — Rationale: SQLite is embedded; see `testing.md`.

## Reject if

- An ORM is introduced, or SQL is generated/reflected instead of explicit JDBI statements.
- Schema changes are made outside a `V{ver}__desc.sql` Flyway migration, or an applied migration is edited.
- A secret value is written to the database instead of a reference.
- A multi-statement write is not transactional/atomic (crash could leave a partial record).
- The DB is placed outside the per-OS data dir, the DB is opened before the pre-injector `bookloom.lock` is held, or `:persistence` tries to own the single-instance lock itself.
- A pooled connection skips the per-connection PRAGMA re-application, `busy_timeout` is absent, or writes bypass the single-writer executor.
- Durability is documented/claimed as power-loss-proof under `synchronous=NORMAL`.
- `:pipeline`/`:ui` depends on a `..Dao` concrete instead of an `:api` repository port.
