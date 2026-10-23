**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-10-21
**Cross-references:** `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md`, `docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md`,
`docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md`,
`docs/specification/03_NonFunctional/05_RELIABILITY_AND_RESUME.md`, `docs/adr/ADR-0009-persistence-sqlite.md`,
`docs/adr/ADR-0043-state-in-sqlite-as-json-documents.md` (JSON-document tables, no migrations before the first release)

# Data Model — SQLite

`:persistence` stores all durable state in a single SQLite database (WAL mode) accessed through JDBI 3 (fluent API, no
ORM). State is kept as **JSON documents** in a few tables; a real column exists only where an index, a uniqueness rule
or a cascade needs it (ADR-0043). **Before the first release there are no migrations and no Flyway:** one schema file,
`db/schema.sql` (`CREATE … IF NOT EXISTS` statements ending in `PRAGMA user_version = N`), creates the tables, and a
database whose `user_version` differs, whose stored-record fingerprint differs ([record-shapes](#record-shapes)), that
cannot be opened, or that fails `PRAGMA quick_check` is renamed aside and a fresh one created
([corruption-and-recovery](#corruption-and-recovery)). Flyway with additive `V{ver}__{desc}.sql`
migrations arrives with the first release; until then no statement here about migrations applies.

## storage-conventions {#storage-conventions}

- **Engine:** `org.xerial:sqlite-jdbc` through `SQLiteDataSource` and a `SQLiteConfig`; no pool library.
- **Per-connection pragmas:** `journal_mode=WAL`, `foreign_keys=ON`, `synchronous=NORMAL`, `busy_timeout=5000`. They
  are connection-scoped (only `journal_mode=WAL` persists on the file), so the data source applies the full set to
  every connection it opens, and no connection can run with foreign keys off or without a busy timeout.
- **Schema:** `src/main/resources/db/schema.sql` in `:persistence`, applied at startup (`AppLifecycle` phase 2).
- **Single writer:** `WriteExecutor` is one daemon thread; every write runs on it as **one JDBI transaction** and the
  caller blocks on the result without being interruptible (an interrupt is kept and set again once the answer is in,
  so a committed write is never reported lost); a write asked for from inside a write is refused with an `internal`
  error instead of waiting for its own thread. Reads use their own connections. Lock contention becomes queueing, not `SQLITE_BUSY`.
- **Atomic writes:** a checkpoint (segments, TM entries, deferrals, glossary additions, run record) is one write
  transaction. WAL + `synchronous=NORMAL` is **process-crash-safe and forced-quit-safe**; on OS crash or power loss at
  most the last in-flight commit may be lost, never earlier accepted work (`05_RELIABILITY_AND_RESUME.md`, DD-20,
  ADR-0009). Durability is never claimed to survive power loss.
- **Backup:** `StorageInfo.backupTo` runs `VACUUM INTO ?` (the destination is a bound parameter) on the single writer
  thread, outside a transaction because SQLite refuses `VACUUM` inside one; a call from inside a write is refused. The
  destination must be a new file in an existing folder (`validation` otherwise, nothing replaced); the copy is a
  consistent snapshot, opens as a database and holds no key. A write submitted while the copy runs waits on the writer thread and runs after it; a failed copy removes the half-written file.
- **One document, one writer:** the glossary and the lexicon are one JSON document per project; every change to them is
  a read-modify-write inside one write transaction, so a person's edit and a run's addition never overwrite each other.
- **Errors:** a storage failure is `ErrorCode.internal` with the safe details `storage <operation>`; no SQL, path or
  row content is put in `details`.
- **FX-thread check:** a read or write on a thread named "JavaFX Application Thread" logs one WARN with the stack per
  call site; with the system property `bookloom.storage.failOnFxThread=true` (set for every test task) it throws.
- **Data dir:** per-OS — Windows `%LOCALAPPDATA%\BookLoom`, macOS `~/Library/Application Support/BookLoom`, Linux
  `${XDG_DATA_HOME:-~/.local/share}/bookloom`; logs go to the per-OS log dir. Dev builds resolve under a `-Dev`/`-dev`
  sibling. Resolution and the `BOOKLOOM_DATA_DIR` override are in `11_APP_ENVIRONMENT_AND_PATHS.md` (DD-39, ADR-0015).
- **Single-instance lock:** `FileChannel.tryLock()` on `bookloom.lock` in the data dir, owned by the pre-injector
  launcher (`:app`/`:util`), **not** by `:persistence`, which assumes it is held before the database is opened
  (`10_DI_AND_LIFECYCLE.md#single-instance-lock`). A second launch shows "BookLoom is already running" and exits.
- **Network-filesystem caution:** if the data dir is on SMB/NFS, WAL locking is unreliable; startup warns
  (`11_APP_ENVIRONMENT_AND_PATHS.md#edge-cases`).

## tables {#tables}

| Table | Key and columns | Holds |
|---|---|---|
| `meta` | `key` PK, `value` | facts about the file itself: `record_shapes`, the fingerprint of the stored records ([record-shapes](#record-shapes)) |
| `settings` | `key` PK, `value`, `type` | preferences ([settings-serialization](#settings-serialization)) |
| `providers` | `id` PK, `json` | `ProviderConfig`; the credential is an env-var name reference only |
| `model_settings` | PK (`provider_id`, `model_id`), `json` | `ModelSettings`, refusals and the measured speed included |
| `books` | `content_hash` PK, `source_path`, `format`, `title`, `json` | the book, stored once; `title` is the book's title (the base of an automatic project name); `source_path` is written when the book is first stored and changed only by `relocateBook`, so saving a project never moves it |
| `projects` | `id` PK, `content_hash` → `books`, `name`, `name_key`, `name_origin`, `source_lang`, `target_lang`, `created_at`, `last_opened_at`, `brief_json`; `UNIQUE (content_hash, name_key)`; index (`content_hash`, `source_lang`, `target_lang`) | the project; a name is unique within its book, ignoring letter case |
| `project_docs` | PK (`project_id`, `kind`), `json` | glossary (with tombstones), lexicon (with removals), summary, latest run, last export |
| `segments` | PK (`project_id`, `segment_id`), `ord`, `unit_id`, `status`, `json`; index (`project_id`, `status`) | `SegmentRecord` |
| `tm_entries` | PK (`project_id`, `source_hash`, `context_key`), `json` | `TmEntry` |
| `deferrals` | PK (`project_id`, `segment_id`, `reason`, `waiting_on`), `json` | `Deferral` |

- **Cascade:** every child table (`project_docs`, `segments`, `tm_entries`, `deferrals`) has
  `FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE`, so deleting a project removes all of it.
- **The name key:** `name_key` is `strip().toLowerCase(Locale.ROOT)` computed in Java, because SQLite's `lower()` folds
  only ASCII and Cyrillic must fold too.
- **Document-level hash:** `books.content_hash` is taken over the whole imported source at import time; it is distinct
  from the per-segment `source_hash` and detects on resume that the file changed (`EC-RESUME-*`).
- **Run state:** the current run state lives in the project's latest-run document (`project_docs`, kind run), not in a
  column of its own.
- **Provider binding:** a project's provider and model are kept in its documents and re-verified on resume (DD-31,
  `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#per-project-binding`); deleting a provider never deletes a project.

The exact statements are `modules/persistence/src/main/resources/db/schema.sql`; that file, not a copy here, is the
schema.

## settings-serialization {#settings-serialization}

The `settings` KV store is typed by its `type` column, and `value` is always a **canonical, locale-independent** string
encoding decided solely by `type`. A single **codec keyed off `type`** owns both directions (write and parse); no call
site formats a settings value ad hoc.

| `type`   | Canonical encoding of `value`                                                      |
|----------|--------------------------------------------------------------------------------------|
| `BOOL`   | the literal string `true` or `false` (lowercase).                                  |
| `INT`    | `Long.toString(v)` under `Locale.ROOT` (no grouping separators, no locale digits). |
| `DOUBLE` | `Double.toString(v)` under `Locale.ROOT` (`.` decimal point, no grouping).         |
| `STRING` | the raw UTF-8 string.                                                              |
| `JSON`   | a UTF-8 JSON object, serialized canonically (stable field order).                  |

Rules:

- `value` is **`NOT NULL`**. A setting that is "unset" is represented by the **absence of the row**, never by a null or
  empty `value`; the codec never emits null.
- All numeric encodings use `Locale.ROOT` so a value written under a Ukrainian or German OS locale reads back
  identically — a `DOUBLE` is never written with a comma decimal separator.
- Reading a row whose `value` does not parse under its declared `type` is a typed error (`ErrorCode.validation`),
  surfaced at startup rather than silently coerced.

## secret-references {#secret-references}

The `providers` document holds only a credential reference: the bare name of an environment variable, which
`ProviderConfigRules` requires to match `[A-Za-z_][A-Za-z0-9_]*` — anything else (a key pasted into the field) is
refused with `ErrorCode.validation` before it is stored, and the refusal never echoes the value (a `KEYCHAIN:<id>`
form, an OS-keychain entry, is allowed later and not built; ADR-0043). The plaintext secret is resolved at request time by `:llm` (credential resolution is planned, not built) and is never written to any table,
log, or export (`04_LLM_INTEGRATION.md#credentials-as-reference`). A row whose reference resolves to nothing yields
`ErrorCode.missingCredential`.

## corruption-and-recovery {#corruption-and-recovery}

The database is opened defensively at startup:

- **Checks on open.** A file that is absent or empty is simply created. Any other file must open, pass
  `PRAGMA quick_check`, carry the build's `PRAGMA user_version` and hold the build's stored-record fingerprint in
  `meta` ([record-shapes](#record-shapes)).
- **Set aside and start fresh.** A file that fails any check is **renamed** `bookloom.db.old-<yyyyMMdd-HHmmss>` (time
  from an injectable `Clock`; the `-wal` and `-shm` files of the old database are moved with it, as
  `bookloom.db.old-<stamp>-wal` and `-shm`, so they cannot corrupt the new one and the old file stays readable) and a
  fresh database is created. One WARN line names the old file; no dialog is shown and no data is carried
  over (ADR-0043). The renamed file stays for diagnosis.
- **After the first release.** Flyway migrations are forward-only and transactional; a failed one rolls back
  completely, and an older app opening a newer schema refuses to run with a clear typed error.

A failure to create even the fresh file is an `internal` `AppError` surfaced before the first scene
(`09_ERROR_HANDLING.md`).

## record-shapes {#record-shapes}

The JSON codec reads strictly (an unknown property fails the read), so a document written before an `:api` record
gained, lost, renamed or retyped a component cannot be read back. Before the first release the owner's decision is to
**start fresh** rather than migrate documents: `StoredShapes` fingerprints the JSON shape of every stored type
(`Project` with its brief, `SegmentRecord`, the glossary and lexicon documents, `RollingSummary`, `Deferral`,
`RunRecord`, `TmEntry`, `ExportRecord`, `ProviderConfig`, `ModelSettings`) as the SHA-256 of a text that lists each
record's components sorted by name with their types, nested records inline and enums by their constant names. The
class name of a record is not part of it, so moving or renaming a record changes nothing. The schema step writes the
fingerprint into `meta` (`record_shapes`); a file holding another one does not fit and is set aside like a file of
another `user_version`. A record change therefore needs no hand-raised number: the next start sets the old file aside
and creates an empty one.

## resume-support {#resume-support}

Per-chunk atomic checkpoints make a run resumable: on relaunch the engine reads `segments.status` for the project and
continues from the **first `PENDING` segment**. `ACCEPTED`/`REVISED` are terminal and kept; **`FLAGGED` is
terminal-for-run** — surfaced in the review queue and **excluded from auto-resume**. Because writes are transactional
and WAL-durable, a crash mid-chunk loses at most the last in-flight commit, never earlier accepted work
(`05_RELIABILITY_AND_RESUME.md`, including the `EC-RESUME-*` edge cases). Before continuing, resume re-binds the
project's own provider/model (not the settings default, and never `current_provider`), preflight-verifies it, and
prompts on unavailability (including a null/`ON DELETE SET NULL`-cleared `provider_id`) or a settings/last-used mismatch
(DD-31, `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#per-project-binding`).
