**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md` (DD-39),
`docs/adr/ADR-0015-app-environment-and-paths.md`, `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`,
`docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md`,
`docs/specification/03_NonFunctional/05_RELIABILITY_AND_RESUME.md`

# Application Environment & Paths

How BookLoom resolves the per-OS folders that hold the SQLite database and the logs, how a **development** build is kept
separate from a **production** build so they never collide, and the exact startup order in which this happens. This is
**foundational**: logging needs the log directory and SQLite needs the data directory, so path resolution must run
before either. Owned by **PHASE_00** (`docs/implementation_plan/phases/PHASE_00_SCAFFOLD.md`); recorded in **DD-39 /
ADR-0015**.

## Table of Contents

1. [Directory kinds](#directory-kinds)
2. [Per-OS locations](#per-os-locations)
3. [Dev vs production separation](#dev-vs-prod)
4. [Resolution mechanism](#resolution-mechanism)
5. [Startup order](#startup-order)
6. [Single-instance lock & atomic writes](#lock-and-atomic)
7. [Edge cases (ENV)](#edge-cases)

## 1. Directory kinds {#directory-kinds}

BookLoom distinguishes three roles and places each per platform convention:

- **Data** — the SQLite database (`bookloom.db`, durable, user-owned). Large, machine-local, memory-mapped, WAL-locked.
- **Logs** — rolling Logback files (diagnostic, machine-local, non-critical).
- **Lock** — the single-instance lock file, placed inside the data directory.

There is no separate user-config store — preferences live in the SQLite typed KV `settings` table
(`06_DATA_MODEL_SQLITE.md#settings`), so only data and logs need OS roots.

## 2. Per-OS locations {#per-os-locations}

Production paths (app name segment `BookLoom` / lowercase `bookloom` on Linux):

| OS          | Data (SQLite DB)                             | Logs                                               |
|-------------|----------------------------------------------|----------------------------------------------------|
| **Windows** | `%LOCALAPPDATA%\BookLoom\`                   | `%LOCALAPPDATA%\BookLoom\logs\`                    |
| **macOS**   | `~/Library/Application Support/BookLoom/`    | `~/Library/Logs/BookLoom/`                         |
| **Linux**   | `${XDG_DATA_HOME:-~/.local/share}/bookloom/` | `${XDG_STATE_HOME:-~/.local/state}/bookloom/logs/` |

Rationale for the Windows choice: the SQLite WAL database is a large, machine-local, memory-mapped file — it belongs in
**`%LOCALAPPDATA%`** (local), never `%APPDATA%` (roaming), which would risk profile-sync corruption and bandwidth bloat.
On Linux, logs follow the XDG **state** dir (`XDG_STATE_HOME`), not the data dir, per the Base Directory spec. Blank or
non-absolute `XDG_*` values are ignored and the `~/.local/...` fallback is used.

## 3. Dev vs production separation {#dev-vs-prod}

A development run must **never** read or write the production database or logs. BookLoom selects the app-name segment by
an `isDev` signal:

- **Production:** `BookLoom` (Windows/macOS) / `bookloom` (Linux).
- **Development:** `BookLoom-Dev` / `bookloom-dev`.

Because the folder differs, a dev instance and a production instance can even run at the same time without their
single-instance locks colliding.

**`isDev` detection** (first match wins; default is dev — the safe choice, since an un-stamped run stays out of
production folders):

1. Env var override `BOOKLOOM_ENV` = `dev` | `prod` (highest precedence; for QA).
2. Build stamp `-Dbookloom.env=prod`, injected into the jpackage launcher via `--java-options`. Gradle/IDE runs do not
   set it → dev.
3. Presence of the (undocumented but reliable) `jpackage.app-path` system property → treated as packaged/production, as
   a fallback signal only.
4. Otherwise → **dev**.

The decision is a single pure function over an injected env map + system properties, so every branch is unit-testable
without mutating the real environment.

## 4. Resolution mechanism {#resolution-mechanism}

Paths are resolved by a small hand-rolled resolver in `:util` (namely `ua.bookloom.util.paths`; no third-party dir
library — avoids the JNA native dependency that `appdirs` drags in, keeps the jlink image small, and gives a clean test
seam). It:

- honours an explicit **`BOOKLOOM_DATA_DIR` override** first: if set to an absolute, non-blank path it becomes the data
  dir verbatim (bypassing the per-OS logic — useful for portable installs, tests, and putting the DB on a chosen
  volume); the log dir is then resolved as a `logs/` child of it unless a separate log override applies;
- otherwise switches on `os.name`; reads `user.home`, `APPDATA`/`LOCALAPPDATA`, and `XDG_DATA_HOME`/`XDG_STATE_HOME`
  through **injected `getEnv`/`getProperty` seams** (not `System.*` directly) so tests can simulate all three OSes and
  both environments on one machine;
- validates `BOOKLOOM_DATA_DIR`/`XDG_*`/`%…%` values (absolute, non-blank) before use, else falls back;
- exposes `dataDir()`, `logDir()`, `lockFile()`, and `databaseFile()` as `Path`s; and
- is consumed by the persistence layer (DB), the logging setup (log dir), and the launcher (lock — acquired pre-injector
  by `:app`/`:util`, `10_DI_AND_LIFECYCLE.md#single-instance-lock`).

## 5. Startup order {#startup-order}

Path resolution is a hard prerequisite for logging and persistence, so it runs first (`10_DI_AND_LIFECYCLE.md`):

1. Resolve `isDev` (pure function over env + system properties).
2. Resolve `dataDir` + `logDir` (pure function over os/home/env).
3. `Files.createDirectories(dataDir, logDir)` — idempotent first-run creation.
4. Acquire the single-instance lock (`dataDir/bookloom.lock`) — fail fast before touching the DB.
5. Initialize logging: publish the log dir (e.g. set the `LOG_DIR` property or configure Logback programmatically)
   **before the first `LoggerFactory.getLogger(...)` call**, then start the rolling file appender.
6. Open SQLite (`dataDir/bookloom.db`, WAL) and run Flyway.
7. Launch the JavaFX UI.

No class-level static logger in the bootstrap path may fire before step 5, or Logback pins to a wrong/default path.

## 6. Single-instance lock & atomic writes {#lock-and-atomic}

- **Lock:** `FileChannel.tryLock()` on `dataDir/bookloom.lock`, acquired by the pre-injector launcher (`:app`/`:util`)
  and held for process lifetime; the OS releases it on exit/crash. A second launch that fails `tryLock()` **shows a "
  BookLoom is already running" dialog and exits** — it does not focus, signal, or raise the first window, and there is
  no IPC (`10_DI_AND_LIFECYCLE.md#single-instance-lock`).
- **Atomic writes** (for any file BookLoom writes itself outside SQLite's own WAL durability — e.g. exports): write to a
  temp file **in the same directory**, then `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`, catching
  `AtomicMoveNotSupportedException` and falling back to a plain replace. The live SQLite DB manages its own durability
  via WAL and is not written this way.
- **Log rollover:** use a time+size rolling policy; the dev/prod folder split guarantees two instances never write the
  same log file (which on Windows would fail rollover on the file lock).

## 7. Edge cases (ENV) {#edge-cases}

- **EC-ENV-1** — `XDG_DATA_HOME`/`XDG_STATE_HOME` is blank or relative → ignored; the `~/.local/...` fallback is used
  (per XDG spec).
- **EC-ENV-2** — the data or log directory does not exist on first run → created idempotently before use (step 3); a
  creation failure surfaces as a typed startup error.
- **EC-ENV-3** — a second instance starts → the single-instance lock is already held; the new process **shows a "
  BookLoom is already running" dialog and exits** without touching the DB. It does **not** focus, signal, or raise the
  first window, and there is no IPC.
- **EC-ENV-4** — a dev build runs → all paths resolve under the `-Dev`/`-dev` sibling folder; production data is
  untouched even if both run simultaneously.
- **EC-ENV-5** — `ATOMIC_MOVE` is unsupported by the filesystem → a logged non-atomic replace fallback is used.
- **EC-ENV-6** — on Windows, `%LOCALAPPDATA%` (and `%APPDATA%`) is unset or blank → fall back to
  `%USERPROFILE%\AppData\Local`, and if that too is unavailable to `user.home`\`AppData\Local`; resolution never
  dead-ends on an unset Windows env var.
- **EC-ENV-7** — `user.home` is blank/undefined (no usable home on any OS) → a **typed fatal startup error** (there is
  nowhere safe to place the data dir); the app refuses to start rather than writing to an arbitrary or working-directory
  location. `BOOKLOOM_DATA_DIR` (EC-ENV-9) is the sanctioned escape hatch.
- **EC-ENV-8** — the resolved data dir is on a **network filesystem** (SMB/NFS/mapped drive) → startup **warns**; SQLite
  WAL locking is unreliable on network shares, so the connection SHOULD fall back to `PRAGMA journal_mode=TRUNCATE`
  (`06_DATA_MODEL_SQLITE.md#storage-conventions`). Local storage remains the supported configuration.
- **EC-ENV-9** — `BOOKLOOM_DATA_DIR` is set → if absolute and non-blank it overrides the per-OS data dir verbatim (§4);
  a blank or relative value is ignored and normal per-OS resolution applies.
