**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md`, `docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md`,
`docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md`,
`docs/specification/03_NonFunctional/05_RELIABILITY_AND_RESUME.md`, `docs/adr/ADR-0009-durability.md`

# Data Model — SQLite

`:persistence` stores all durable state in a single SQLite database (WAL mode) accessed through JDBI 3 DAOs, with schema
evolved by Flyway. This document fixes the tables, the migration/DAO conventions, atomicity, the per-OS data dir, the
DB-connection guard, and secret references. The DDL block under [ddl-normative](#ddl-normative) is **normative for
`V1__baseline.sql`**: the checked-in baseline migration MUST match it column-for-column (later migrations extend it
additively). Acquisition of the single-instance lock is a pre-injector `:app`/`:util` concern on the resolved data dir,
not a `:persistence` DAO (`10_DI_AND_LIFECYCLE.md#single-instance-lock`,
`11_APP_ENVIRONMENT_AND_PATHS.md#lock-and-atomic`); the only lock this document owns is the DB-connection guard
described below.

## storage-conventions {#storage-conventions}

- **Engine:** `org.xerial:sqlite-jdbc`, `PRAGMA journal_mode=WAL`, `PRAGMA foreign_keys=ON`,
  `PRAGMA synchronous=NORMAL`, `PRAGMA busy_timeout=<N ms>`.
- **Per-connection pragma re-application:** the PRAGMAs above are **connection-scoped**, not database-scoped (only
  `journal_mode=WAL` persists on the file). Every connection handed out by the pool MUST re-apply the full set —
  `foreign_keys=ON`, `busy_timeout`, `synchronous=NORMAL`, and `journal_mode=WAL` — through a **connection-init hook**
  (e.g. the pool's `connectionInitSql` / an init callback) so a recycled or newly-opened connection can never run with
  `foreign_keys` off or without a busy timeout.
- **Migrations:** Flyway, file convention `V{ver}__{desc}.sql` (e.g. `V1__baseline.sql`, `V2__add_tm_context_key.sql`)
  on the classpath of `:persistence`. Migrations run in two-phase init after services construct
  (`10_DI_AND_LIFECYCLE.md`). Never edit an applied migration; add a new one. `V1__baseline.sql` MUST match
  the [normative DDL](#ddl-normative) exactly.
- **DAOs:** JDBI 3 SQL-object DAOs, one per aggregate, bound to repository ports in `:api`. Records map columns via row
  mappers; no ORM.
- **Atomic writes:** each checkpoint (segment accept, status change, TM/summary update) is a single transaction. WAL +
  `synchronous=NORMAL` gives per-chunk durability that is **process-crash-safe and forced-quit-safe**; on OS crash /
  power loss at most the last in-flight commit may be lost, never earlier accepted work (`05_RELIABILITY_AND_RESUME.md`,
  DD-20, ADR-0009).
- **Single-writer discipline:** all write transactions are confined to a **single-writer executor** (a serialized
  handle / one connection that owns writes); readers may run concurrently on other pooled connections. This keeps
  SQLite's single-writer model explicit and turns lock contention into a bounded `busy_timeout` wait rather than an
  immediate `SQLITE_BUSY`. See `08_THREADING_CONCURRENCY.md` for how this executor relates to the worker pool.
- **Data dir:** per-OS — Windows `%LOCALAPPDATA%\BookLoom`, macOS `~/Library/Application Support/BookLoom`, Linux
  `${XDG_DATA_HOME:-~/.local/share}/bookloom`; logs go to the per-OS log dir (macOS `~/Library/Logs/BookLoom`, Linux
  `${XDG_STATE_HOME:-~/.local/state}/bookloom/logs`, Windows `%LOCALAPPDATA%\BookLoom\logs`). Dev builds resolve under a
  `-Dev`/`-dev` sibling so they never collide with production. Resolution, dev/prod separation, the `BOOKLOOM_DATA_DIR`
  override, and startup order are specified in `11_APP_ENVIRONMENT_AND_PATHS.md` (DD-39, ADR-0015) — this must be
  established before SQLite is opened.
- **Single-instance lock:** an exclusively-held lock file (`FileChannel.tryLock()` on `bookloom.lock`) in the data dir.
  Its acquisition is owned by the pre-injector launcher (`:app`/`:util`), **not** by this persistence layer
  (`10_DI_AND_LIFECYCLE.md#single-instance-lock`). A second launch detects the held lock, **shows a "BookLoom is already
  running" dialog, and exits** — it does not focus or signal the first window (no IPC). `:persistence` only assumes the
  lock has already been acquired for the resolved data dir before it opens the database.
- **Network-filesystem caution:** the data dir is expected to be local. If it resolves onto a network filesystem
  (SMB/NFS), SQLite's WAL locking is unreliable; startup **warns**, and the connection SHOULD fall back to
  `PRAGMA journal_mode=TRUNCATE` (see `11_APP_ENVIRONMENT_AND_PATHS.md#edge-cases`).

## tables {#tables}

### projects {#projects}

One row per imported book/translation project. Each project carries its **own provider/model binding** (`provider_id`,
`translator_model`, `judge_model`) copied from the settings defaults at creation and reused on every resume, plus a
`last_used_json` snapshot that resume verifies and prompts against (DD-31,
`01_Product/04_LLM_PROVIDERS_AND_MODELS.md#per-project-binding`). Settings changes never rewrite an existing project's
binding without user confirmation. `provider_id` is a foreign key with **`ON DELETE SET NULL`**: deleting a provider
does not delete its projects — it nulls the binding, and the next resume sees a null/missing binding and takes the **"
provider unavailable → prompt"** path (`05_RELIABILITY_AND_RESUME.md#resume-on-launch`, DD-31) rather than silently
falling back to a default. The project also stores a **document-level content hash** (`content_hash`) taken over the
whole imported source at import time; it is distinct from the per-segment `source_hash` and is used on resume to detect
that the underlying file changed since the skeleton was built (`EC-RESUME-*` in `05_RELIABILITY_AND_RESUME.md`).

### units {#units}

Spine units per project (skeleton lives on disk / blob; DB holds order + metadata).

### segments {#segments}

The ordered segment list with status and translation (mirrors `03_DOCUMENT_MODEL.md#data-model`). The primary key is the
composite string **`{unitId}:{ord}`** (kept for stable, human-readable IDs and deterministic TM/context keys);
`UNIQUE(unit_id, ord)` enforces spine ordering. `kind` is the segment classifier enum:
`PARAGRAPH | HEADING | VERSE_LINE | …` plus the synthetic-metadata kinds
`METADATA_TITLE | METADATA_AUTHOR | FRONTMATTER_VALUE | ALT | NAV_LABEL` (DD-47, `03_DOCUMENT_MODEL.md`), which anchor
to OPF/frontmatter/attribute/nav nodes rather than spine prose. `prev_key`/`next_key` are the **TM context
neighbours** — the `source_hash`-domain keys of the immediately preceding and following segments used to build
`tm.context_key`; both are **null at spine ends** (first segment has no `prev_key`, last has no `next_key`), which the
context hash represents with the `⟦BOS⟧`/`⟦EOS⟧` sentinels described under [tm](#tm).

### glossary {#glossary}

The name/term dictionary — term, type, gender, locked flag, target rendering. `gender` accepts
`masculine | feminine | neuter | unknown` (the pre-scan/deterministic fallback yields `unknown` until the user or a
backward-revision pass fills it in, DD-46).

### tm {#tm}

Context-keyed translation memory, keyed **`(project_id, source_hash, context_key)`**. `source_hash` is computed over the
**unmasked, NFC-normalized** source text (so masking placeholders never perturb a TM hit and canonically-equal source
always collides — see D1 canonical-equal wording). `context_key = hash(prev ⊕ next)` over the neighbour source hashes,
using the sentinels `⟦BOS⟧` (no previous neighbour) and `⟦EOS⟧` (no next neighbour) so segments at spine ends key
deterministically.

### summaries {#summaries}

Rolling bilingual summaries per chapter/unit, plus **rolling-summary trigger bookkeeping**: the marker that records
where the last summary was taken (e.g. the last summarized unit/segment and token count since) so a resumed run knows
whether a fresh summary is due at the next chapter boundary rather than re-summarizing or skipping.

### deferrals {#deferrals}

Segment **deferred-resolution** entries: when a segment cannot be finalized in the current pass because it depends on a
not-yet-resolved decision (e.g. a glossary gender still `unknown`, a name whose rendering a later passage will
disambiguate), a deferral row records the segment, the reason/kind, and the resolution it is waiting on. The pipeline
drains these when the dependency resolves (backward revision) and the rows are cleared on resolution; they are separate
from `FLAGGED`, which is a terminal-for-run review state.

### providers {#providers}

Provider configs with **credential references only** (never secrets). `kind` selects the client implementation
(`OLLAMA` → native client; `OPENAI_COMPATIBLE` → OpenAI-compatible client); `supports_discovery` gates live model
listing vs manual model-ID entry (DD-38). Two model slots only — `translator_model`, `judge_model` (no embedding slot).
`effective_context` (nullable INTEGER) holds the manually-entered **"effective context (tokens)"** value used by the
token-budget heuristic when discovery cannot supply it (DD-44, `04_LLM_INTEGRATION.md`); null means "unknown → use the
conservative default". `supports_structured_output` (nullable) is a **capability cache** recording whether the
provider/model accepts a structured-output/JSON-schema request; null means "not yet probed".

### app_state {#app_state}

Small singleton-ish table for runtime pointers, e.g. `current_provider`. `current_provider` is the **new-project default
only** — the provider a freshly-created project copies its binding from. It is **never consulted at resume**: resume
precedence is *project binding (`projects.provider_id` + models) > the resume "provider unavailable → prompt" path when
that binding is null/unavailable*; `current_provider` sits below both and is only read when seeding a new project's
binding (DD-31).

### settings {#settings}

Generic typed KV store: `(key, value, type)`. Holds the UI language under key `ui.language` (STRING, `en` | `uk`) — set
from the OS locale on first start and thereafter from the user's choice (DD-34,
`01_Product/10_I18N_AND_ACCESSIBILITY.md`). `value` is **`NOT NULL`**; a settings row always carries a concrete
serialized value (absence is modeled by not having the row, not by a null value). Serialization is canonical and
locale-independent — see [settings-serialization](#settings-serialization).

## ddl-normative {#ddl-normative}

The block below is **normative for `V1__baseline.sql`** — the checked-in baseline migration MUST match it
column-for-column, constraint-for-constraint. It is no longer a sketch: any change to a column, type, default, or
foreign-key action is a schema change that requires a new additive migration, never an edit here or to the applied
baseline.

```sql
-- V1__baseline.sql (NORMATIVE — the baseline migration must match this exactly)
CREATE TABLE projects (
  id            TEXT PRIMARY KEY,
  title         TEXT NOT NULL,
  format        TEXT NOT NULL,             -- EPUB|FB2|MD|TXT
  source_lang   TEXT,                      -- detected
  target_lang   TEXT,
  brief_json    TEXT,                      -- Book Brief snapshot
  content_hash  TEXT,                      -- document-level hash of the whole imported source (distinct from segments.source_hash)
  quality_dial  TEXT NOT NULL DEFAULT 'BALANCED',
  status        TEXT NOT NULL DEFAULT 'IMPORTED',
  -- Per-project provider/model binding (DD-31): copied from the settings defaults at creation,
  -- reused on every resume. last_used_* is the snapshot verified/prompted against on resume.
  provider_id      TEXT REFERENCES providers(id) ON DELETE SET NULL, -- null binding on resume => "provider unavailable → prompt"
  translator_model TEXT,                    -- the project's bound translator model id
  judge_model      TEXT,                    -- the project's bound judge/helper model id
  last_used_json   TEXT,                    -- {providerId, kind, baseUrl, translatorModel, judgeModel}
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);

CREATE TABLE units (
  id          TEXT PRIMARY KEY,
  project_id  TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  ord         INTEGER NOT NULL,
  href        TEXT,
  media_type  TEXT,
  UNIQUE(project_id, ord)
);

CREATE TABLE segments (
  id           TEXT PRIMARY KEY,           -- {unitId}:{ord}  (composite string PK, stable & human-readable)
  project_id   TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  unit_id      TEXT NOT NULL REFERENCES units(id) ON DELETE CASCADE,
  ord          INTEGER NOT NULL,
  kind         TEXT NOT NULL,              -- PARAGRAPH|HEADING|VERSE_LINE|...|METADATA_TITLE|METADATA_AUTHOR|FRONTMATTER_VALUE|ALT|NAV_LABEL
  source_inner TEXT NOT NULL,
  masked       TEXT NOT NULL,
  placeholders TEXT NOT NULL,              -- JSON map gN -> fragment
  source_hash  TEXT NOT NULL,              -- over UNMASKED, NFC-normalized source_inner
  prev_key     TEXT,                       -- TM context neighbour: preceding segment's source_hash-domain key; NULL at spine start (⟦BOS⟧)
  next_key     TEXT,                       -- TM context neighbour: following segment's source_hash-domain key; NULL at spine end (⟦EOS⟧)
  target_inner TEXT,
  status       TEXT NOT NULL DEFAULT 'PENDING', -- PENDING|ACCEPTED|FLAGGED|REVISED
  confidence   REAL,
  updated_at   TEXT NOT NULL,
  UNIQUE(unit_id, ord)
);
CREATE INDEX ix_seg_project_status ON segments(project_id, status);

CREATE TABLE glossary (
  id          TEXT PRIMARY KEY,
  project_id  TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  term        TEXT NOT NULL,
  type        TEXT,                        -- person|place|org|term
  gender      TEXT,                        -- masculine|feminine|neuter|unknown  (unknown until user/backward-revision fills it, DD-46)
  target      TEXT,                        -- chosen rendering
  locked      INTEGER NOT NULL DEFAULT 0,
  UNIQUE(project_id, term)
);

CREATE TABLE tm (
  id           TEXT PRIMARY KEY,
  project_id   TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  source_hash  TEXT NOT NULL,              -- over UNMASKED, NFC-normalized source
  context_key  TEXT NOT NULL,              -- hash(prev ⊕ next) of neighbour source hashes, ⟦BOS⟧/⟦EOS⟧ at spine ends
  source_inner TEXT NOT NULL,
  target_inner TEXT NOT NULL,
  UNIQUE(project_id, source_hash, context_key)
);
CREATE INDEX ix_tm_lookup ON tm(project_id, source_hash);

CREATE TABLE summaries (
  id            TEXT PRIMARY KEY,
  project_id    TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  unit_id       TEXT,
  summary_src   TEXT,
  summary_tgt   TEXT,
  -- rolling-summary trigger bookkeeping: where the last summary was taken so a resumed
  -- run knows whether a fresh summary is due at the next chapter boundary.
  last_summarized_key TEXT,                 -- last summarized unit/segment key
  tokens_since  INTEGER,                    -- token count accumulated since the last summary
  updated_at    TEXT NOT NULL
);

CREATE TABLE deferrals (
  id            TEXT PRIMARY KEY,
  project_id    TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  segment_id    TEXT NOT NULL REFERENCES segments(id) ON DELETE CASCADE,
  reason        TEXT NOT NULL,              -- e.g. GENDER_UNKNOWN|NAME_UNRESOLVED
  waiting_on    TEXT,                       -- the glossary term / decision the segment is blocked on
  created_at    TEXT NOT NULL,
  UNIQUE(project_id, segment_id, reason)
);
CREATE INDEX ix_deferral_project ON deferrals(project_id);

CREATE TABLE providers (
  id            TEXT PRIMARY KEY,
  display_name  TEXT NOT NULL,
  kind          TEXT NOT NULL,             -- OLLAMA (native client) | OPENAI_COMPATIBLE (LM Studio, llama.cpp, custom, …)
  base_url      TEXT NOT NULL,
  auth_scheme   TEXT NOT NULL,             -- NONE|BEARER|API_KEY_HEADER
  credential_ref TEXT,                     -- ENV:NAME or KEYCHAIN:id  (NEVER the secret)
  supports_discovery INTEGER NOT NULL DEFAULT 1, -- 0 => manual model-ID entry only (DD-38)
  translator_model TEXT,
  judge_model      TEXT,
  num_ctx       INTEGER,
  effective_context INTEGER,               -- manual "effective context (tokens)"; NULL => conservative default (DD-44)
  supports_structured_output INTEGER       -- capability cache (JSON-schema/structured output); NULL => not yet probed
);

CREATE TABLE app_state (
  k  TEXT PRIMARY KEY,                      -- e.g. 'current_provider' (new-project default only; never consulted at resume)
  v  TEXT
);

CREATE TABLE settings (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL,                      -- canonical serialized value (Locale.ROOT); absence = missing row, never NULL
  type  TEXT NOT NULL                       -- STRING|INT|BOOL|DOUBLE|JSON
);
```

## settings-serialization {#settings-serialization}

The `settings` KV store is typed by its `type` column, and `value` is always a **canonical, locale-independent** string
encoding decided solely by `type`. A single **codec keyed off `type`** owns both directions (write and parse); no call
site formats a settings value ad hoc.

| `type`   | Canonical encoding of `value`                                                      |
|----------|------------------------------------------------------------------------------------|
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

`providers.credential_ref` holds only a reference: `ENV:<VAR_NAME>` (an environment-variable name) or `KEYCHAIN:<id>`
(an OS-keychain entry). The plaintext secret is resolved at request time by `:llm` and is never written to any table,
log, or export (`04_LLM_INTEGRATION.md#credentials-as-reference`). A row whose reference resolves to nothing yields
`ErrorCode.missingCredential`.

## corruption-and-recovery {#corruption-and-recovery}

The database is opened defensively at startup:

- **Integrity check on open.** Before the app accepts work, startup runs `PRAGMA integrity_check` (or catches an opening
  `SQLITE_CORRUPT`). A corrupt or unreadable database becomes a **typed startup error**, never a silent partial open.
- **Quarantine and start fresh.** On detected corruption BookLoom **quarantines the corrupt `bookloom.db`** (renames it
  aside, e.g. `bookloom.db.corrupt-<timestamp>`, keeping the file for diagnosis) and starts fresh with a newly-created
  database rather than crashing on every launch. The user is told what happened and where the old file is.
- **Cross-release migration guardrails.** Flyway migrations are forward-only and **transactional**: a failed migration
  **rolls back completely** — the schema is never left partially migrated. An **older app opening a newer schema**
  (schema version ahead of what this build knows) **refuses to run with a clear typed error** rather than mangling data.
  An **optional pre-migration backup** of `bookloom.db` may be taken before applying migrations so a bad upgrade is
  recoverable.

Corruption/refusal errors map to the error model in `09_ERROR_HANDLING.md` (a startup-scoped `internal`/`validation`
-class `AppError`); the app surfaces them before the first scene rather than mid-run.

## resume-support {#resume-support}

Per-chunk atomic checkpoints make a run resumable: on relaunch the engine reads `segments.status` for the project and
continues from the **first `PENDING` segment**. `ACCEPTED`/`REVISED` are terminal and kept; **`FLAGGED` is
terminal-for-run** — surfaced in the review queue and **excluded from auto-resume**. Because writes are transactional
and WAL-durable, a crash mid-chunk loses at most the last in-flight commit, never earlier accepted work
(`05_RELIABILITY_AND_RESUME.md`, including the `EC-RESUME-*` edge cases). Before continuing, resume re-binds the
project's own provider/model (not the settings default, and never `current_provider`), preflight-verifies it, and
prompts on unavailability (including a null/`ON DELETE SET NULL`-cleared `provider_id`) or a settings/last-used mismatch
(DD-31, `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#per-project-binding`).
