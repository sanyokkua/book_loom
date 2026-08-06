# Tasks — bootstrap-app-launch-and-empty-window

Turns the eight-module skeleton into an application that starts. The decisions are `design.md` (D1–D9); the frozen
sources are `11_APP_ENVIRONMENT_AND_PATHS.md` (paths + startup order), `09_ERROR_HANDLING.md` (the envelope),
`10_DI_AND_LIFECYCLE.md` (launcher/injector/two-phase init), and `03_PACKAGING_JPACKAGE.md` (jpackage).

**Group order is a dependency chain, not a preference.** Seam F2 lands first because `:util`'s resolver returns
`Result`; the rule-8 **package re-scope** (3.1) lands before the bootstrap classes it must police, and before they are
placed in the package that re-scope defines; the version resource lands before the startup log line that prints it.

**Two paths in this change contradict the frozen spec on purpose, both under an existing ADR.** `:app:collectDist`
stages into `modules/app/build/dist/libs/`, not `app/build/dist/libs/` (**ADR-0021**, which supersedes all four
occurrences). The launch smoke uses `-Dglass.platform=Headless`, not
`-Dglass.platform=Monocle -Dmonocle.platform=Headless` (**ADR-0019** — `org.testfx:openjfx-monocle` has no release
past 21.0.2 and cannot run against JavaFX 26). Writing the spec's literal text in either place is a defect here.

**Test markers.** Unlike change 1, this change implements real frozen requirements, so a test that covers a
**business requirement** carries `// Covers: FR-*` plus a one-line EARS restatement (ADR-0016 R5, `testing.md`).
`skip_specs: true` means no delta spec ships — it does **not** mean those `FR-*` ids go uncited.

A **mechanical or infrastructure** test is simply written and carries **no marker**: a null guard, the boot smoke
(5.6), the packaging launch smoke (6.4), the stylesheet-attachment check (5.7). There is no requirement for them to
cite — the `Result`/`AppError` shape in particular has **no `FR-*` anywhere** in the frozen spec, because it is
architecture (DD-14, `09_ERROR_HANDLING.md`) rather than product behaviour. **Do not invent an id to fill the gap**;
`spec-authoring.md` names that as a defect, and a citation that resolves to nothing is worse than an absent one.

## 1. Seam F2 — the error envelope in `:api`

- [x] 1.1 Add the `Result<T>(@Nullable T data, @Nullable AppError error)` record with `isOk()`, `isErr()`, static
      `ok`/`err` factories, and `map`/`flatMap` that short-circuit on error. Every port method in the project returns
      this type, so its shape is fixed before anything can be written against a different one; it is a record with two
      nullable components rather than a sealed `Ok`/`Err` pair because the spec fixes that form.
      → `:api/ua.bookloom.api` · `modules/api/src/main/java/ua/bookloom/api/Result.java`,
      `09_ERROR_HANDLING.md#result-envelope`, design.md D5
- [x] 1.2 Add the `AppError(ErrorCode code, String title, String message, @Nullable String details, boolean
      retryable, @Nullable Throwable cause)` record. One error type serves the whole application — a per-module error
      type would make the UI's rendering logic a switch over eight shapes instead of one.
      → `:api/ua.bookloom.api` · `modules/api/src/main/java/ua/bookloom/api/AppError.java`,
      `09_ERROR_HANDLING.md#app-error`
- [x] 1.3 Fill the existing **empty** `ErrorCode` enum with all fifteen constants — `auth`, `timeout`, `rateLimited`,
      `unreachable`, `modelNotFound`, `discoveryFailed`, `modelUnavailable`, `upstream`, `missingCredential`,
      `contextWindow`, `emptyCompletion`, `cancelled`, `validation`, `internal`, `busy` — and replace its
      "constant set arrives with the error envelope in change `bootstrap-app-launch-and-empty-window`" scaffolding
      Javadoc with the real contract. Ten of these cannot be produced until change 10; shipping them anyway is what
      makes seam F2 a fixed target for the twenty-six changes that compile against it (design.md D5).
      → `:api/ua.bookloom.api` · `modules/api/src/main/java/ua/bookloom/api/ErrorCode.java`,
      `09_ERROR_HANDLING.md#error-code`
- [x] 1.4 Add the safe-details builder that composes `AppError.details` from **typed** inputs only — HTTP status,
      endpoint host without token, model name, timeout value, attempt count, QA finding names. Make concatenating an
      exception message syntactically impossible rather than merely forbidden: if the only way to obtain a `details`
      string is to hand typed fields to a builder, an `Authorization` header has no route into the UI or a log file.
      This **half-covers `FR-NOTIF-04`** — the allowlist is built here, but surfacing it to the user arrives with the
      notification UI in Stage D, so the marker must say so rather than claim the whole requirement (same treatment as
      `FR-UI-09` in task 4.3).
      → `:api/ua.bookloom.api` · `09_ERROR_HANDLING.md#safe-details-allowlist`,
      `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, `.claude/rules/error-envelope.md`, FR-NOTIF-04
- [x] 1.5 Cover the envelope: `map`/`flatMap` short-circuit on an error and do not invoke the mapper; exactly one of
      `data`/`error` is non-null and constructing both or neither is rejected; `retryable` matches the code; and the
      safe-details builder emits every allowlisted field while offering no path for a raw `Throwable` message. The
      last of these is the privacy invariant in test form, so it is the one that must not be skipped. Marker rule: the
      safe-details test carries `// Covers: FR-NOTIF-04` with its half-coverage note (task 1.4); the `Result`/
      `AppError` shape tests carry **no** `FR-*` marker, because no such requirement exists — see the header.
      → `:api/ua.bookloom.api` · `modules/api/src/test/java/ua/bookloom/api/`, `testing.md`, FR-NOTIF-04

## 2. The app-paths resolver in `:util`

- [x] 2.1 Add the `isDev` decision as a **pure function** over an injected env map and system properties, in
      precedence order `BOOKLOOM_ENV=dev|prod` → `-Dbookloom.env=prod` → presence of `jpackage.app-path` → **default
      dev**. The default is the safety property, not a fallback: an un-stamped run (IDE, `gradlew run`, a test) must
      stay out of the production folder, so an unrecognised environment resolves to dev rather than to prod.
      → `:util/ua.bookloom.util.paths` · `11_APP_ENVIRONMENT_AND_PATHS.md#dev-vs-prod`, DD-39, ADR-0015
- [x] 2.2 Replace the empty `AppPaths` **interface** with a record carrying resolved `dataDir`, `logDir`, `lockFile`
      and `databaseFile` values, and add the resolver that produces it. Take `getEnv`/`getProperty` as injected
      `Function<String,String>` seams — never call `System.getenv`/`System.getProperty` from this package — because
      that seam is the only thing that makes three operating systems testable on one machine (design.md D3).
      → `:util/ua.bookloom.util.paths` · `modules/util/src/main/java/ua/bookloom/util/paths/`,
      `11_APP_ENVIRONMENT_AND_PATHS.md#resolution-mechanism`
- [x] 2.3 Implement the three per-OS branches: Windows data+logs under `%LOCALAPPDATA%\BookLoom\` (logs in a `logs\`
      child), macOS data under `~/Library/Application Support/BookLoom/` with logs under `~/Library/Logs/BookLoom/`,
      Linux data under `${XDG_DATA_HOME:-~/.local/share}/bookloom/` with logs under
      `${XDG_STATE_HOME:-~/.local/state}/bookloom/logs/`. Windows uses `%LOCALAPPDATA%` and never `%APPDATA%`: the WAL
      database is a large memory-mapped file, and a roaming profile would both corrupt it and bloat sync.
      → `:util/ua.bookloom.util.paths` · `11_APP_ENVIRONMENT_AND_PATHS.md#per-os-locations`
- [x] 2.4 Apply the `-Dev`/`-dev` suffix to the app-name segment when `isDev` is true, so a development run resolves
      to `BookLoom-Dev` / `bookloom-dev`. Because the whole folder differs, a dev and a production instance hold
      distinct single-instance locks and can run simultaneously — that is a property of this task, not a coincidence.
      → `:util/ua.bookloom.util.paths` · `11_APP_ENVIRONMENT_AND_PATHS.md#dev-vs-prod`, EC-ENV-4
- [x] 2.5 Honour the `BOOKLOOM_DATA_DIR` override when it is absolute and non-blank, taking it as the data dir
      verbatim and deriving `logs/` beneath it; ignore it when blank or relative and fall through to per-OS
      resolution. The packaging launch smoke depends on this to run against a temp directory instead of fighting a
      developer's real single-instance lock (task 6.4).
      → `:util/ua.bookloom.util.paths` · `11_APP_ENVIRONMENT_AND_PATHS.md#resolution-mechanism`, EC-ENV-9
- [x] 2.6 Create the data and log directories idempotently, and return
      `Result.err(AppError(validation, …))` — not a thrown exception — when `user.home` is blank with no usable
      fallback and no override is set. Returning the envelope is why `:util` depends on `:api` and why group 1 had to
      land first; the app must refuse to start rather than write to the working directory.
      → `:util/ua.bookloom.util.paths` · `11_APP_ENVIRONMENT_AND_PATHS.md#edge-cases`, EC-ENV-2, EC-ENV-7
- [x] 2.7 Detect a data dir on a network filesystem and carry it as a warning condition **on the resolved value**,
      **without** acting on it and **without logging it here**. The `PRAGMA journal_mode=TRUNCATE` fallback it implies
      needs a SQLite connection, which change 8 (`add-local-storage`) introduces; guarding a connection that does not
      exist would be dead code, and detecting it later would mean re-opening this file. It cannot be logged from this
      package for two independent reasons — the resolver runs at startup step 2 and logging starts at step 5, and
      `:util` does not `requires org.slf4j` at all — so the condition travels as data and `:app` emits it (task 3.7).
      → `:util/ua.bookloom.util.paths` · EC-ENV-8, `06_DATA_MODEL_SQLITE.md#storage-conventions`, design.md D3,
      task 3.7
- [x] 2.8 Cover the resolver across all three operating systems and both environments by feeding the injected seams a
      map — `os.name`, `user.home`, `LOCALAPPDATA`, `XDG_DATA_HOME`, `XDG_STATE_HOME`, `BOOKLOOM_DATA_DIR`,
      `BOOKLOOM_ENV` — with one test per **resolver-owned** edge case: **EC-ENV-1, 2, 4, 6, 7, 8, 9** — seven, not
      nine — including the blank/relative `XDG_*` fallback (EC-ENV-1) and the `%LOCALAPPDATA%`-unset Windows chain
      (EC-ENV-6). The two absentees are deliberate and must not be written here: **EC-ENV-3** (a second instance) is a
      lock concern proved against `Launcher` in task 3.9, and **EC-ENV-5** (`ATOMIC_MOVE` unsupported) describes the
      atomic file-replace that exports use — this change ships no atomic-write helper, so a test for it would assert
      against code that does not exist. Mark each `// Covers: FR-PERSIST-03` / `FR-PERSIST-06` with its one-line EARS
      restatement. No test may mutate the real environment.
      → `:util/ua.bookloom.util.paths` · `modules/util/src/test/java/ua/bookloom/util/paths/`, `testing.md`,
      FR-PERSIST-03, FR-PERSIST-06

## 3. Bootstrap in `:app` — the rule first, then `Launcher`, lock and logging

- [x] 3.1 **Re-scope ArchUnit rule 8 from class names to a package, before writing any bootstrap class.**
      `ON_BOOTSTRAP_PATH` currently matches `ua.bookloom.util.paths..` plus any `ua.bookloom.app..` class whose simple
      name equals `Launcher` or contains `Lock`, so the logging-bootstrap class — the one whose entire job is to run
      before logging exists — is invisible to the rule that protects it. **Delete `BOOTSTRAP_NAMED`** and make the
      predicate `ua.bookloom.util.paths..` or a new `ua.bookloom.app.bootstrap..`, so membership is a package a
      developer deliberately chooses rather than a naming convention the next author has to guess. Doing this first
      means the rule polices the classes as they are written rather than after. Three things move with it, each a red
      build if skipped: **(a)** the existing violation fixture
      `modules/app/src/archTest/java/ua/bookloom/app/archfixture/Launcher.java` is caught **by the name predicate** —
      once that is gone the rule cannot see it and
      `RuleViolationFixtureTest.bootstrapNoStaticLogger_staticLoggerFieldInAppLauncher_isRejected` fails asserting a
      rejection that no longer happens, so the fixture moves under the new bootstrap package in this task;
      **(b)** rule 8's `.as(...)` text enumerates "Launcher/lock acquisition in `ua.bookloom.app..`" and must name the
      package instead, keeping the two properties `RuleSuiteCompletenessTest` asserts — it opens with the spec name
      plus the name separator, and it states a *why*; **(c)** re-scoping a predicate adds no `ArchRule` field, so the
      eight-rule count is unchanged.
      → `:app/ua.bookloom.archtest` · `modules/app/src/archTest/java/ua/bookloom/archtest/ArchitectureRules.java`,
      `modules/app/src/archTest/java/ua/bookloom/app/archfixture/`,
      `02_MODULES_AND_LAYERING.md#archunit-rules`, design.md D4
- [x] 3.2 Add `ch.qos.logback:logback-classic` to `gradle/libs.versions.toml` and declare it on `:app` only. It is
      this change's **single** new runtime dependency; its **EPL-1.0** licence is already allowlisted by the exact
      module pattern `ch\.qos\.logback:.*` in `config/license/allowed-licenses.json`, so the licence policy needs no
      edit — only regenerated lock state (task 7.1).
      → repo root · `gradle/libs.versions.toml`, `modules/app/build.gradle.kts`,
      `01_BUILD_AND_TOOLING.md#version-catalog`, `05_Dependencies/03_LICENSING.md`
- [x] 3.3 Add `requires org.slf4j` and `requires ch.qos.logback.classic` to `:app`'s `module-info.java`. Confine
      every Logback type in the project to the one bootstrap class: `logging.md` requires SLF4J-only in application
      code, and programmatic configuration unavoidably needs `LoggerContext` and `RollingFileAppender`, so the
      carve-out is exactly one class and is stated as such in its Javadoc.
      → `:app/ua.bookloom.app` · `modules/app/src/main/java/module-info.java`, `.claude/rules/logging.md`, design.md D4
- [x] 3.4 Add `Launcher` in the **new `ua.bookloom.app.bootstrap` package** — a plain class with `main` that does
      **not** extend `Application` — performing steps 1–4 of the startup order: resolve `isDev`, resolve the
      directories, create them, then acquire the single-instance lock. It must not extend `Application` because the
      JavaFX launcher would otherwise own module-path and toolkit initialization, which is fragile under jpackage, and
      because the lock must be held before the injector exists. The package is not cosmetic: after task 3.1 it *is*
      how rule 8 recognises the pre-logging path, so a bootstrap class placed in `ua.bookloom.app` instead is
      unprotected and silently free to hold a static logger.
      → `:app/ua.bookloom.app.bootstrap` ·
      `modules/app/src/main/java/ua/bookloom/app/bootstrap/Launcher.java`,
      `07_UI_ARCHITECTURE_JAVAFX.md#bootstrap`, `11_APP_ENVIRONMENT_AND_PATHS.md#startup-order`, design.md D1, D4
- [x] 3.5 Acquire the lock with `FileChannel.tryLock()` on `dataDir/bookloom.lock` and hold it for process lifetime,
      releasing it as the last step of shutdown. This is an `:app`/`:util` concern acquired **pre-injector** — never a
      `:persistence` DAO — because it must be held before anything can open the database it exists to protect. The
      acquirer lives in `ua.bookloom.app.bootstrap` with `Launcher`, for the rule-8 reason in task 3.1.
      → `:app/ua.bookloom.app.bootstrap` · `10_DI_AND_LIFECYCLE.md#single-instance-lock`,
      `11_APP_ENVIRONMENT_AND_PATHS.md#lock-and-atomic`, FR-PERSIST-04
- [x] 3.6 Surface every pre-logging failure through **one** mechanism: `Platform.startup(Runnable)` to boot only the
      FX toolkit, a plain `Stage` carrying a `Scene` with the message and a dismiss button, then `Platform.exit()`.
      **Not an `Alert`**: `Alert`/`Dialog` assume a running `Application` context and, shown this early with no owner
      window, render a correctly-titled window with a completely blank body — no message and no way to dismiss it.
      Use it for the second launch ("BookLoom is already
      running", exit 0), for a directory-creation failure, and for an unresolvable home (both non-zero). Do **not**
      use `javax.swing.JOptionPane`: it drags `java.desktop` into the jlink image for one dialog, defeating the size
      trim the packaging scripts exist to apply, and puts a second toolkit in a project whose UI rules are written
      about the first. The second launch must not focus, signal, or raise the first window — there is no IPC.
      **If the FX toolkit itself will not start**, exit with a non-zero status and emit **nothing** — no `System.err`
      line, no crash file, no fallback dialog. A terminal line is invisible to someone who double-clicked a packaged
      app, and a second failure channel would be a write path in the one code region that has just proved it cannot
      write anywhere. Only EC-ENV-7 (no usable home) is genuinely hostile to booting a toolkit; EC-ENV-2 has a working
      home, so the dialog shows normally there.
      → `:app/ua.bookloom.app.bootstrap` · `10_DI_AND_LIFECYCLE.md#single-instance-lock`, EC-ENV-2, EC-ENV-3,
      EC-ENV-7, design.md D2
- [x] 3.7 Configure Logback **programmatically** in one class in `ua.bookloom.app.bootstrap`: publish the resolved log
      directory, then build the rolling file appender (time+size policy) in the per-OS log dir, and only then take the
      first logger. No `logback.xml` file appender may exist — an XML appender binds to a default path at class-load
      time, before the log directory is known, and the resulting misdirection is silent. This class is the project's
      **only** carve-out for Logback types (task 3.3), and it is the class rule 8 most needs to see — which is the
      whole reason task 3.1 comes first.
      → `:app/ua.bookloom.app.bootstrap` · `10_DI_AND_LIFECYCLE.md#logging-bootstrap-order`,
      `11_APP_ENVIRONMENT_AND_PATHS.md#startup-order` (step 5), `.claude/rules/logging.md`, design.md D4
- [x] 3.8 Emit the EC-ENV-8 network-filesystem warning as its **own dedicated `warn` line**, immediately after logging
      is configured and before the startup version line — naming the resolved data dir and stating that SQLite's WAL
      locking is unreliable on a network share. Task 2.7 detects the condition and carries it as data because it
      cannot log; this task is the other half, and without it the detection falls on the floor. It is **not** folded
      into `app started version=<v>`: a warning buried as a field on a normal-looking startup line is read straight
      past, and this one has to survive being skim-read in a support thread.
      → `:app/ua.bookloom.app.bootstrap` · EC-ENV-8, `06_DATA_MODEL_SQLITE.md#storage-conventions`,
      `11_APP_ENVIRONMENT_AND_PATHS.md#startup-order`, task 2.7, design.md D3
- [x] 3.9 Cover the bootstrap: a second `Launcher` against the same data dir is refused while the first holds the
      lock and the database is never touched (`// Covers: FR-PERSIST-04`, **EC-ENV-3** — this is where EC-ENV-3 is
      proved, not in the resolver suite of task 2.8); a dev-stamped and a prod-stamped run resolve to different lock
      files and both succeed (EC-ENV-4); the configured appender writes into the resolved log dir rather than a
      default one; and a resolved value flagged as a network filesystem produces the dedicated warning line of task
      3.8. The appender assertion is what would otherwise fail silently in production and never in a test.
      → `:app/ua.bookloom.app.bootstrap` · `modules/app/src/test/java/ua/bookloom/app/bootstrap/`, FR-PERSIST-04,
      EC-ENV-3, EC-ENV-4, EC-ENV-8

## 4. The build-generated version resource

- [x] 4.1 Add a `generateVersionResource` task in `:app`, wired into `processResources` with the version declared as
      a **task input** so up-to-date checks work, writing `ua/bookloom/app/version.properties` containing
      `version=<value>` from the root build's existing
      `version = providers.gradleProperty("appVersion").orElse("dev")`. A classpath resource is used rather than the
      jar manifest's `Implementation-Version` because the manifest value is null when running from classes, which is
      how `gradlew run` and every test execute.
      → `:app/ua.bookloom.app` · `modules/app/build.gradle.kts`, `01_BUILD_AND_TOOLING.md#version-injection`, DD-50
- [x] 4.2 Add the `AppVersion` reader that loads the resource and falls back to `dev` when it is absent. Keep exactly
      one reader and let it cache nothing a second time: DD-50 permits precisely two surfaces, and a second copy of
      the string is how a version drifts from the tag that produced it.
      → `:app/ua.bookloom.app` · `modules/app/src/main/java/ua/bookloom/app/AppVersion.java`, DD-50
- [x] 4.3 Emit the single startup log line `app started version=<v>` after logging is configured. This change ships
      **one** of DD-50's two surfaces; the About dialog is the other and arrives with the UI in Stage D, so
      `FR-UI-09` is only half-covered here and the test marker must say so rather than claim the whole requirement.
      → `:app/ua.bookloom.app` · `01_BUILD_AND_TOOLING.md#version-injection`, FR-UI-09
- [x] 4.4 Cover the version path: a build with no `-PappVersion` reports `dev` (which is the proof an artifact did
      not come from the release pipeline), `-PappVersion=1.2.0-rc1` round-trips the **full** string including the
      suffix, and the reader falls back to `dev` when the resource is missing entirely.
      → `:app/ua.bookloom.app` · `modules/app/src/test/java/ua/bookloom/app/`, FR-UI-09, EC-REL-7

## 5. The injector and the blank themed window

- [x] 5.1 Add the minimal token stylesheet in `:ui`, declaring looked-up colors on `.root` — the four brand anchors
      `charcoal #3a4a52`, `slate #b2babd`, `sand #e7d6c0`, `cognac #a58075` plus `bg`, `surface` and `text` — and
      referencing them from at least one selector. FR-THEME-5 **mandates** the location and the count — theme CSS
      lives in the `:ui` module resources as a **single** stylesheet declaring the role set once — so change 6
      (`add-theming-token-system`) must extend this file, never add a second beside it. Structure it as *role set +
      value block on `.root`* from the start, because that is the shape FR-THEME-2's light/dark swap needs.
      → `:ui/ua.bookloom.ui` · `modules/ui/src/main/resources/`, `01_Product/09_THEMING.md#token-catalog`,
      FR-THEME-5, `.claude/rules/theming-tokens.md`, design.md D7
- [x] 5.2 Add the JavaFX `Application` subclass that builds the injector with all six Guice module classes —
      `AppModule`, `PersistenceModule`, `LlmModule`, `DocumentModule`, `PipelineModule`, `UiModule` — even though
      four of them bind nothing yet. An injector that assembles a graph spanning eight JPMS modules is the
      integration signal this change exists to produce; wiring only the two with content would prove less.
      → `:app/ua.bookloom.app` · `10_DI_AND_LIFECYCLE.md#composition-root`, `07_UI_ARCHITECTURE_JAVAFX.md#bootstrap`
- [x] 5.3 Give `AppModule` its real bindings — the injected daemon `ExecutorService`, a virtual-thread executor, and
      the resolved `AppPaths` — replacing the empty `AbstractModule` scaffolding. Constructor injection only; no
      field or setter injection and no static holder, so every dependency is explicit and substitutable in a test.
      → `:app/ua.bookloom.app` · `modules/app/src/main/java/ua/bookloom/app/AppModule.java`,
      `10_DI_AND_LIFECYCLE.md#guice-modules`, `.claude/rules/java-coding-style.md`
- [x] 5.4 Implement two-phase init with **Phase 2 present and deliberately empty**: Phase 1 constructs services with
      no I/O, Phase 2 is the named, ordered step where resources will open. Its *position* is the load-bearing part —
      migrations must complete before any `JobHandle` exists and the first scene must be built after Phase 2 — so
      change 8 fills the body rather than introducing the ordering and SQLite at the same time. **Pin that position
      with a test**: assert Phase 2 runs after Phase 1 and before the first scene is built. An empty step that nothing
      asserts can be silently reordered or dropped by the very change that fills it, which is the exact accident this
      decision exists to prevent; and task 5.6's "two-phase init completed" assertion does not cover it, because it
      passes under any ordering.
      → `:app/ua.bookloom.app` · `10_DI_AND_LIFECYCLE.md#two-phase-init`, design.md D6, task 5.6
- [x] 5.5 Show a blank `Stage` with the stylesheet attached at **`Scene`** level so tokens cascade from `.root`, and
      wire `./gradlew :app:run`. No `node.setStyle(...)` and no hard-coded hex outside the `.root` block anywhere —
      the mechanism this task proves is the one every later screen depends on.
      → `:app/ua.bookloom.app` · `.claude/rules/javafx-ui.md`, `.claude/rules/theming-tokens.md`, FR-THEME-1
- [x] 5.6 Add the boot smoke: launch the real `Application` headlessly against a temp data dir set through
      `BOOKLOOM_DATA_DIR`, and assert the injector built, two-phase init completed, and the primary `Stage` was
      shown. Assert the **startup log line was actually emitted** — a `requires ch.qos.logback.classic` that
      satisfies `javac` can still leave the SLF4J binding unresolved at runtime, and a no-op logger produces no line,
      which is the only cheap signal that the JPMS service binding really resolved. This is an **infrastructure** test
      and carries no `FR-*` marker (see the header). Note it does **not** prove the two-phase ordering — that is task
      5.4's assertion, because "init completed" is true in any order.
      → `:app/ua.bookloom.app` · `modules/app/src/test/java/ua/bookloom/app/`, `06_TESTING_STRATEGY.md`,
      design.md "Risks"
- [x] 5.7 Add the TestFX widget check that the `Scene` carries the stylesheet and that a node resolves a looked-up
      colour to the expected brand hex, using JavaFX 26's built-in headless platform. Assert the resolved colour
      rather than the presence of a stylesheet URL: a stylesheet that fails to parse still attaches.
      → `:ui/ua.bookloom.ui` · `modules/ui/src/test/java/ua/bookloom/ui/`, `.claude/rules/testing.md`, ADR-0019

## 6. Packaging and the launch smoke

- [x] 6.1 Add `:app:collectDist` as a plain `Sync` task staging the application jar plus its whole
      `runtimeClasspath` — including the per-OS JavaFX platform jars — into **`modules/app/build/dist/libs/`**. The
      frozen spec says `app/build/dist/libs/` in four places and **ADR-0021 supersedes all four**; this change is the
      first to write the path, which is precisely why the layout move was sequenced immediately before it. No
      `org.beryx.jlink` or other packaging plugin.
      → `:app/ua.bookloom.app` · `modules/app/build.gradle.kts`, `03_PACKAGING_JPACKAGE.md#approach`, ADR-0021, DD-24
- [x] 6.2 Add `scripts/jpackage-common.sh` holding the shared configuration — app name `BookLoom`, main class
      **`ua.bookloom.app.bootstrap.Launcher`** (the bootstrap package of task 3.1/3.4, not `ua.bookloom.app`), main
      jar auto-discovered in `modules/app/build/dist/libs/`, version from
      `APP_VERSION` falling back to the Gradle project version, the per-OS `--icon` from
      `docs/specification/assets/icon/dist/`, the jlink trim
      `--strip-debug --no-header-files --no-man-pages --compress zip-6`, and a prerequisite check that fails fast when
      `dist/libs` is missing. Include `--java-options "-Dbookloom.env=prod"`: without that stamp a launched image
      resolves **dev** paths and everything appears to work while writing to the wrong folder (DD-39).
      → repo root · `scripts/jpackage-common.sh`, `03_PACKAGING_JPACKAGE.md#approach`, DD-39
- [x] 6.3 Add the three per-OS drivers — `scripts/package-macos.sh` (`.app` app-image + `.dmg`, tarred with `tar -czf`
      so the execute bit survives), `scripts/package-linux.sh` (app-image + `.deb`, warning and skipping the `.deb`
      when `fakeroot` is absent rather than failing), and `scripts/package-windows.bat` (**app-image only** — there
      is deliberately no `.msi`/WiX installer; Windows ships as a portable zip).
      → repo root · `scripts/`, `03_PACKAGING_JPACKAGE.md#per-os-matrix`, EC-REL-3, EC-REL-6
- [x] 6.4 Add the launch smoke that starts the packaged image and asserts a positive startup signal within a bounded
      timeout, using **`-Dglass.platform=Headless`** — JavaFX 26's built-in headless platform. The frozen
      `03_PACKAGING_JPACKAGE.md#verification` names `-Dglass.platform=Monocle -Dmonocle.platform=Headless`;
      **ADR-0019 supersedes it**, because `org.testfx:openjfx-monocle` has no release past 21.0.2 and cannot run
      against a JavaFX 26 runtime, so the spec's literal flags would produce a smoke that fails everywhere. Point it
      at a temp data dir via `BOOKLOOM_DATA_DIR` so it does not fight a running app's lock, and assert the resolved
      data dir in the log is the **production** folder — that is what catches a missing `-Dbookloom.env=prod` stamp.
      "Did not actually launch" is a hard failure, never a skip.
      → repo root · `03_PACKAGING_JPACKAGE.md#verification`, `02_QUALITY_GATES.md#jpackage-smoke`, ADR-0019, EC-REL-5
- [x] 6.5 Wire the packaging matrix into CI (`build.yml`/the packaging job per `04_CI_CD.md#packaging-matrix`),
      selecting **Liberica 25 "Full" (`jdk+fx`)** on the Windows and Linux-aarch64 legs and Temurin elsewhere,
      because a plain JDK on those two lacks the JavaFX jmods `jpackage` needs. Record in the change that **CI is not
      exercised until the project is feature-complete** (the standing decision from change 1's task 7.7), so this
      ships as documented debt rather than as a verified claim; the scripts themselves are run locally on this OS.
      → repo root · `.github/workflows/`, `03_PACKAGING_JPACKAGE.md#jdk-and-javafx-runtime`, EC-REL-4

## 7. Green gate

- [x] 7.1 Regenerate and commit all lock state — `./gradlew resolveAndLockAll --write-locks` and the same for
      `-p modules/build-logic` — because `logback-classic` and its transitive `logback-core` are new coordinates. A
      dependency added without regenerated locks makes the committed lock state describe a graph that no longer
      exists.
      → repo root · `01_BUILD_AND_TOOLING.md#dependency-locking`, task 3.2
- [x] 7.2 Run `./gradlew :build-logic:clean` **before** the gate. `build-logic` is an included build, so the root
      `clean` does not reach into it and its functional tests — including the lock-state assertions that task 7.1
      just changed the inputs to — can report `UP-TO-DATE` from a stale cache.
      → repo root · `01_MODULE_INVENTORY.md#as-built-baseline`
- [x] 7.3 Run `./gradlew clean build check spotlessCheck` and confirm it is green across the whole project — build,
      Spotless, Error Prone/NullAway, Checkstyle, SpotBugs, the eight ArchUnit rules (now with real bootstrap classes
      to police for the first time), the `build-logic` functional suite, and every test. No "pre-existing failure"
      exemption. Report the real actionable-task count; it will **not** match change 1's 91, because this change adds
      source, tests and tasks — a number that reproduces exactly would mean the new work was not compiled.
      → whole project · `06_DEFINITION_OF_DONE.md#per-change-checklist`,
      `.claude/rules/gradle-build-and-quality.md`
- [x] 7.4 Run `./gradlew -PstrictLocks verifyLocks` and `./gradlew -p modules/build-logic -PstrictLocks verifyLocks`,
      then `./gradlew checkLicense`. The lock gates are deliberately outside `check` (they need the network); the
      licence gate must be run explicitly here because this is the first change since the toolchain landed to add a
      dependency at all, and `logback-classic` is **EPL-1.0** — an allowlisted exception rather than one of the
      default Apache/MIT/BSD family, so it is exactly the case the gate exists to adjudicate.
      → whole project · `01_BUILD_AND_TOOLING.md#dependency-locking`, `05_Dependencies/03_LICENSING.md`
- [x] 7.5 Verify the app actually runs, by hand and not only in a test: `./gradlew :app:run` opens a blank themed
      window; a second `:app:run` while the first is open shows "BookLoom is already running" and exits; and the log
      file appears under the resolved **dev** log directory with the `app started version=dev` line in it. A boot
      smoke can pass against a headless toolkit while the real window is broken.
      → whole project · `11_APP_ENVIRONMENT_AND_PATHS.md#startup-order`, EC-ENV-3
- [x] 7.6 Build a package locally with the matching `scripts/package-<os>` driver and run the launch smoke against
      the produced image, confirming it starts and that its log reports the **production** data dir. This is the only
      verification in group 7 the standing gate does not provide — `check` never invokes jpackage — and it is where a
      missing `--java-options "-Dbookloom.env=prod"` stamp or a JPMS binding that resolves only from the classpath
      would first show up.
      → whole project · `03_PACKAGING_JPACKAGE.md#verification`, task 6.4, EC-REL-5
- [x] 7.7 Update `01_MODULE_INVENTORY.md#as-built-baseline`, whose table still describes `ErrorCode` and `AppPaths`
      as *placeholder types* and still places `Launcher` in `:app/ua.bookloom.app`. Record what those packages now
      genuinely contain, correct the `Launcher` row, and add a row for the new **`:app/ua.bookloom.app.bootstrap`**
      package created by task 3.1 — noting that membership in it is what ArchUnit rule 8 polices, so it is a
      constraint and not merely a folder. The inventory is the citation target every later change resolves against, so
      a stale row there is a broken citation in every proposal that follows.
      → `docs/implementation_plan/` · `01_MODULE_INVENTORY.md#as-built-baseline`, `#adding-a-module-note`
- [x] 7.8 Confirm the offline invariant holds now that the app opens a window: no update check, no telemetry, no
      analytics ping, and no asset fetched at runtime — the stylesheet, icons and every resource are bundled. The
      `no-http-in-core-except-llm` ArchUnit rule keeps `java.net.http` out of every module but `:llm`, which this
      change does not touch, so the structural half is already gated; state the behavioural half explicitly because
      this is the first change that could have violated it.
      → whole project · `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, DD-01, NFR-PRIV-01, seam F9
- [x] 7.9 Run `openspec validate bootstrap-app-launch-and-empty-window --strict`, confirm it is clean, then archive
      with `openspec archive`. Because `skip_specs: true` is set, nothing folds into `openspec/specs/` — correct,
      since this change ships the frame rather than a user-observable capability (design.md D9).
      → `openspec/` · ADR-0016
