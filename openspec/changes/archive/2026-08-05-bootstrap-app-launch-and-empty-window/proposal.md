# bootstrap-app-launch-and-empty-window

## Why

Change 1 built a toolchain around eight modules that contain nothing. Every module holds one placeholder type whose
Javadoc names this change as the one that fills it: `:api`'s `ErrorCode` is an **empty enum** ("the constant set
arrives with the error envelope in change `bootstrap-app-launch-and-empty-window`"), and `:util`'s `AppPaths` is an
**empty interface** ("the resolution logic arrives with the app-environment change"). The quality gate is green over a
skeleton — `./gradlew :app:run` does not exist, no window opens, and nothing has ever been packaged.

That is the risk this change exists to retire. BookLoom's startup path stacks five technologies that are individually
routine and jointly the least-proven combination in the project: **Java 25 + JPMS + JavaFX 26 + Guice + jpackage**. A
JPMS `module-info` that compiles says nothing about whether the module graph *resolves at runtime*, whether Guice can
reflect into an exported package, or whether `jpackage` can jlink a runtime that starts. ADR-0017 puts infrastructure
first for exactly this reason: found now, each of those is a one-line fix inside one change; found in Stage D, each is
a refactor across eight modules and twenty screens.

The **order** inside the change is itself a decision, not a preference. Path resolution must run before logging,
because Logback binds its file appender at configuration time and a `static final Logger` that fires first pins it to
the wrong directory permanently (DD-39, ADR-0015). Change 1 already built the ArchUnit rule that forbids that mistake
(`bootstrap-no-static-logger`) — but a rule policing a bootstrap path that does not exist yet has never once been
tested against real code. This change is what gives it something to police.

Two seams also have to exist before anything can consume them. **Seam F2** — the `Result`/`AppError`/`ErrorCode`
envelope — is consumed by *every* remaining change (`07_ROADMAP.md#forward-compatibility-seams`), so it cannot be
retrofitted: a port that starts out throwing and is later converted is twenty-six changes of churn. The **app-paths
resolver** is the foundation persistence and logging both stand on, so it lands before either (`07_ROADMAP.md`,
"Foundations precede their users (no forced mocks)").

## What Changes

- **`:util` gains the app-paths resolver.** `AppPaths` stops being an empty interface and resolves the per-OS data and
  log directories — Windows `%LOCALAPPDATA%\BookLoom\`, macOS `~/Library/Application Support/BookLoom/` +
  `~/Library/Logs/BookLoom/`, Linux `$XDG_DATA_HOME/bookloom/` + `$XDG_STATE_HOME/bookloom/logs/` — plus `lockFile()`
  and `databaseFile()`. Resolution reads the environment through **injected `getEnv`/`getProperty` seams**, never
  `System.*` directly, so one machine's tests can simulate all three operating systems. A `BOOKLOOM_DATA_DIR` override
  wins outright when absolute and non-blank.
- **Dev and production get separate folders.** An `isDev` signal (`BOOKLOOM_ENV` env → `-Dbookloom.env=prod` build
  stamp → `jpackage.app-path` presence → **default dev**) selects a `BookLoom-Dev`/`bookloom-dev` sibling, so a
  development run can never read or write the production database — and the two can run simultaneously, because their
  single-instance locks are in different directories.
- **`:api` gains the error envelope — seam F2.** `Result<T>(data, error)` with `isOk`/`isErr`/`map`/`flatMap`, the
  `AppError(code, title, message, details, retryable, cause)` record, the **fifteen-constant** `ErrorCode` enum, and a
  **safe-details builder** that composes `details` from typed inputs (HTTP status, endpoint host, model name, timeout,
  attempt count, QA finding names) rather than by concatenating exception messages — so a secret has no path into the
  UI or the logs.
- **Logback is configured programmatically, after the log dir is known.** No `logback.xml` file appender: a static XML
  appender binds to a default path at class-load time. The bootstrap publishes the resolved log directory, *then*
  starts the rolling file appender, *then* takes its first logger.
- **`:app` gains `Launcher` → `Application` → injector, and a new `ua.bookloom.app.bootstrap` package.** A plain
  `main` that does **not** extend `Application` (so the module path initializes cleanly under jpackage) resolves
  paths, creates the directories, acquires the single-instance lock, and only then launches the JavaFX
  `Application`, which builds the Guice injector from the six module classes and runs two-phase init. Everything on
  the pre-logging path — `Launcher`, the lock acquirer, the Logback bootstrap — lives in `ua.bookloom.app.bootstrap`,
  because that package **is** how ArchUnit rule 8 now identifies the pre-logging path (see below).
- **ArchUnit rule 8 is re-scoped from class names to a package.** `bootstrap-no-static-logger` currently finds its
  subjects by simple name (`Launcher`, or anything containing `Lock`), which would leave the Logback bootstrap class —
  the one whose entire job is to run before logging exists — invisible to the rule protecting it. The name predicate
  is **removed** and replaced by `ua.bookloom.util.paths..` + `ua.bookloom.app.bootstrap..`, so membership is a
  package a developer deliberately chooses rather than a naming convention the next author has to guess.
- **A second launch is refused, without IPC.** `FileChannel.tryLock()` on `dataDir/bookloom.lock` is acquired
  **pre-injector**; a failed acquire shows a "BookLoom is already running" dialog and exits. It does not focus,
  signal, or raise the first window, and no `:persistence` code is involved.
- **The version becomes build-generated (DD-50).** A `generateVersionResource` task wired into `:app`'s
  `processResources` writes `ua/bookloom/app/version.properties` from the `appVersion` Gradle property (default
  `dev` — already set in the root build), and a small `AppVersion` reader surfaces it. In *this* change it has exactly
  **one** consumer, the startup log line `app started version=<v>`; the About dialog is the second, and arrives with
  the UI in Stage D.
- **A blank themed window opens.** A minimal `Scene`-level stylesheet defines the token set on `.root` and the window
  references tokens only — no inline `setStyle`, no hard-coded hex in a component selector. This is the *mechanism*,
  not the catalogue: the full token system with light/dark swapping is change 6 (`add-theming-token-system`).
- **`:app:collectDist` and the packaging scripts arrive.** A plain `Sync` task stages the app jar plus its whole
  `runtimeClasspath` into **`modules/app/build/dist/libs/`** — the path ADR-0021 supersedes the frozen spec's
  `app/build/dist/libs/` to, at all four of its occurrences. `scripts/jpackage-common.sh` plus `package-macos.sh`,
  `package-linux.sh`, and `package-windows.bat` drive the plain `jpackage` CLI.
- **A launch smoke asserts the app actually starts.** Both in-JVM (injector built, two-phase init complete, Stage
  shown against a temp data dir) and from the packaged image. Per **ADR-0019** the headless mechanism is JavaFX 26's
  built-in `-Dglass.platform=Headless`, **not** the `-Dglass.platform=Monocle -Dmonocle.platform=Headless` pair the
  frozen packaging spec still names — `org.testfx:openjfx-monocle` has no release past 21.0.2 and cannot run against
  a JavaFX 26 runtime at all.
- **Logback joins the version catalog.** `ch.qos.logback:logback-classic` is the change's only new runtime
  dependency; `slf4j-api` is already pinned. Its **EPL-1.0** licence is already allowlisted by exact module pattern
  (`ch\.qos\.logback:.*` in `config/license/allowed-licenses.json`), so the licence gate needs no policy edit — only
  regenerated lockfiles.

**BREAKING:** nothing. There is no released artifact and no persisted data to migrate; this change only adds.

## Capabilities

**New Capabilities:** none.
**Modified Capabilities:** none.

This change sets `skip_specs: true` in `.openspec.yaml`, the same call change 1 made and for the same reason.

What ships is the **frame**, not a behaviour. A user can launch the app, see a blank themed window, be told the app is
already running if it is, and read a version string in a log file. They cannot import a book, parse one, translate,
review, or export anything — none of those capabilities exist, and none is created here. `openspec/specs/` answers
"what does BookLoom do for its user" (ADR-0016); a window with nothing in it is not an answer to that question, it is
the surface a later answer will be painted on.

The error envelope makes the point sharpest. Seam F2 is a **contract** — `Result`, `AppError`, and fifteen
`ErrorCode` constants — and this change introduces exactly **zero** code paths that return a populated one, because
there is no I/O yet to fail. `auth`, `rateLimited`, and `contextWindow` describe provider failures that change 10
will first be able to produce. Writing "WHEN a port fails, the system SHALL return `Result.err`" today would be a
requirement about a situation the software cannot yet enter.

`spec-authoring.md` names inventing a requirement to satisfy validation as a defect outright, so the verification that
would otherwise live in a spec lives in `tasks.md`, where each task carries its own concrete check — and, unusually
for a `skip_specs` change, in a **substantial test suite**: the resolver alone is a pure function over an injected
environment with three operating systems, two environments, and **seven** enumerated edge cases —
**EC-ENV-1, 2, 4, 6, 7, 8, 9** — to cover. It is seven and not nine because two of the nine belong elsewhere:
**EC-ENV-3** (a second instance) is a lock concern proved against `Launcher`, not against the resolver, and
**EC-ENV-5** (`ATOMIC_MOVE` unsupported) describes the atomic file-replace used for exports, which this change does
not ship — a test for it would exercise nothing.

## Impact

- **`:api/ua.bookloom.api`** — `Result<T>` and `AppError` are **new** records; `ErrorCode` is the existing empty enum
  **filled** with its fifteen constants; a safe-details builder is new. Still framework-free: the
  `api-is-framework-free` ArchUnit rule already forbids Guice, Jackson, JavaFX, JDBI, and parser libraries here, and
  nothing in this list needs any of them.
- **`:util/ua.bookloom.util.paths`** — `AppPaths` gains its resolution contract and an implementation, plus the
  `isDev` decision function and directory creation. This package sits on the **pre-logging** path, so no class in it
  may declare a `static` SLF4J `Logger` (ArchUnit `bootstrap-no-static-logger`, which change 1 built and this change
  is the first to actually exercise).
- **`:app/ua.bookloom.app`** — the `Application` subclass, `AppVersion`, and the real `AppModule` bindings (it is
  currently an empty `AbstractModule`). `:app` is the composition root, so it is the only place an injector is
  constructed.
- **`:app/ua.bookloom.app.bootstrap`** — a **new** package holding everything that runs before logging: `Launcher`,
  the single-instance lock acquisition, and the programmatic Logback bootstrap. Its existence is load-bearing rather
  than tidy: after the rule-8 re-scope, this package name is the definition of "pre-logging path", so a class placed
  here is protected automatically and a class placed outside it is not. The **archTest** side moves with it — the
  existing `Launcher` violation fixture is caught today by the name predicate, and once that predicate is gone the
  fixture must sit under this package or `RuleViolationFixtureTest` goes red.
- **`:ui/ua.bookloom.ui`** — the minimal token stylesheet and the blank window's root node. Deliberately thin: the
  component library is Stage B′ and the screens are Stage D.
- **Untouched modules:** `:document`, `:llm`, `:pipeline`, `:persistence` keep their placeholder types. Their Guice
  modules are constructed by the composition root but bind nothing yet, which is what makes the injector's success a
  real signal rather than a trivial one. **No SQLite, no Flyway, no DAO** — persistence is change 8; two-phase init
  ships with its Phase 2 present but opening nothing.
- **Dependencies added:** exactly one — `ch.qos.logback:logback-classic` (EPL-1.0, already allowlisted). All eleven
  lockfiles are regenerated; the SCA and licence gates see one new coordinate and its transitive `logback-core`.
- **Build:** the root `version = providers.gradleProperty("appVersion").orElse("dev")` already exists; the
  `generateVersionResource` task, the `collectDist` `Sync` task, and `./gradlew :app:run` are new.
- **Files added outside the modules:** `scripts/jpackage-common.sh`, `scripts/package-macos.sh`,
  `scripts/package-linux.sh`, `scripts/package-windows.bat`.
- **Offline invariant (DD-01, seam F9):** unaffected and worth stating explicitly, because this is the first change
  that opens a window. It makes **no** network call — no update check, no telemetry, no asset fetch. The `no-http`
  ArchUnit rule keeps `java.net.http` out of every module but `:llm`, which this change does not touch.
- **Frozen spec:** unedited. Two passages are read through existing ADRs rather than changed — the
  `app/build/dist/libs/` staging path via **ADR-0021**, and the Monocle headless flags in
  `03_PACKAGING_JPACKAGE.md#verification` via **ADR-0019**. Both supersessions predate this change and neither is a
  new divergence.
- **ADR written:** **ADR-0022** — `ErrorCode.busy` covers the process single-instance lock as well as the inference
  gate, keeping the enum at fifteen constants. The spec's inline note describes `busy` narrowly, so reusing it is a
  genuine deviation and gets an ADR rather than a code comment. The one other implementation-time correction —
  `SafeDetails` becoming a record because the shipped `records-first` rule rejected a mutable builder — needs none:
  it satisfies an existing rule more strictly rather than departing from anything (design.md D5).
- **ADRs consumed:** ADR-0015 (paths-first startup), ADR-0017 (why this is Stage A), ADR-0019 (JavaFX 26 headless),
  ADR-0021 (the `modules/` layout and the staging path), ADR-0014 (Lombok on services, records for data),
  ADR-0009 (SQLite — referenced by the two-phase-init shape, not implemented here).
