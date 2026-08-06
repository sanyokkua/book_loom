# Design — bootstrap-app-launch-and-empty-window

## Context

See `proposal.md` — Why. The decisions this change *consumes* are already settled and are not re-argued here:
paths-first startup (**ADR-0015**, DD-39), infrastructure-first ordering (**ADR-0017**), JavaFX 26's built-in headless
platform (**ADR-0019**), the `modules/` layout and the `modules/app/build/dist/libs/` staging path (**ADR-0021**), and
Lombok-on-services/records-for-data (**ADR-0014**, DD-05).

What shapes this design is a hard constraint the frozen spec states but does not resolve: **three things must happen
before Logback is configured, and all three can fail in a way the user has to be told about.** Resolving paths can
fail (no usable home — EC-ENV-7). Creating directories can fail (EC-ENV-2). Acquiring the single-instance lock can
fail by design (EC-ENV-3, the second launch). Each of those must surface a message, and none of them can be logged,
because the logger does not exist yet — and if one is created, Logback pins its file appender to a default directory
permanently (`11_APP_ENVIRONMENT_AND_PATHS.md#startup-order` step 5).

Three facts about the existing tree constrain the shape:

| Fact | Where | Consequence |
|---|---|---|
| `:util`'s `module-info` deliberately omits Guice **and** SLF4J | `modules/util/src/main/java/module-info.java` | The resolver cannot log and cannot be injected. It must be a pure function returning values, with `:app` deciding what to say about a failure. |
| ArchUnit rule 8 scopes "bootstrap path" to `ua.bookloom.util.paths..` **plus** classes in `ua.bookloom.app..` **named** `Launcher` or containing `Lock` | `ArchitectureRules.BOOTSTRAP_NAMED` | A logging-bootstrap class named anything else is invisible to the rule that exists to protect it. See D4. |
| `api-is-framework-free` bans `ch.qos.logback..` in `:api` outright | `ArchitectureRules` rule 6 | Logback types are confined to `:app`; nothing else may see them. |

Rule 8 has never had a subject — change 1 shipped it deliberately empty so this change "cannot introduce the
violation in the first place". This change is the first to give it real classes to police, which means it is also the
first chance to discover the rule's scope is narrower than its stated intent.

## Goals / Non-Goals

**Goals:**

- Prove the **runtime** integration Java 25 + JPMS + JavaFX 26 + Guice + jpackage, not just its compilation: a module
  graph that resolves, an injector that builds, a `Stage` that shows, and a packaged image that starts.
- Make every pre-logging failure **user-visible and non-silent**, using one mechanism rather than three.
- Land seam F2 (`Result`/`AppError`/`ErrorCode`) **complete**, so no later change reshapes it.
- Keep the resolver a **pure function over injected seams**, so all three operating systems and both environments are
  testable on one machine without touching the real environment.

**Non-Goals:**

- **No SQLite, no Flyway, no DAO.** Persistence is change 8. Phase 2 of two-phase init exists as an ordered step with
  nothing to open — see D6.
- **No theming system.** One minimal token set proves the Scene-level mechanism; the catalogue, light/dark swapping
  and the OS colour-scheme hook are change 6 (`add-theming-token-system`).
- **No screens, no navigation, no `ViewNames`, no FXML.** The window is blank on purpose; the component library is
  Stage B′ and screens are Stage D.
- **No About dialog.** DD-50 gives `AppVersion` two consumers; only the startup log line exists yet.
- **Not editing the frozen spec.** Two passages are read through *existing* ADRs (ADR-0021 for the staging path,
  ADR-0019 for the headless flags). Neither is a new divergence, so this change writes **no new ADR**.

## Decisions

### D1 — `Launcher` owns everything before the injector; the `Application` owns everything after

`Launcher` is a plain class with a `main` that does **not** extend `Application`. This is not stylistic: when the main
class extends `Application`, the JavaFX launcher takes over module-path/toolkit initialization in a way that is
fragile under jpackage, and `07_UI_ARCHITECTURE_JAVAFX.md#bootstrap` fixes the separation.

The split is by *what can fail before logging exists*:

```
Launcher.main                       (pre-logging, pre-injector)
  1. resolve isDev                  pure function over env + system properties
  2. resolve dataDir + logDir       pure function over os.name + home + env
  3. createDirectories              first failure that can reach a user (EC-ENV-2)
  4. tryLock(dataDir/bookloom.lock) second launch exits here (EC-ENV-3)
  5. configure Logback              publish logDir, THEN start the appender
  6. log "app started version=<v>"  the first logger in the process
  7. Application.launch(...)
                                    ─────────── from here on, logging works ───────────
BookLoomApplication.start           Guice.createInjector(six modules) → two-phase init → Stage
```

Steps 1–4 produce **no log line at all**, by construction rather than by discipline: the classes involved cannot
reach SLF4J (`:util` does not require it; the `:app` classes all sit in `ua.bookloom.app.bootstrap`, which is what
rule 8 polices after D4).

*Alternative considered:* resolve paths inside `Application.init()`, keeping `Launcher` a one-liner. Rejected — the
lock must be held *before* the injector is built (`10_DI_AND_LIFECYCLE.md#single-instance-lock`), and by the time
`init()` runs, the JavaFX launcher has already started a toolkit and a second instance has already paid for it.

### D2 — One pre-logging failure surface: `Platform.startup()` + a plain `Stage`, never Swing

Steps 3–4 above can each fail with nothing to log to and no `Stage` yet. All three cases (`EC-ENV-2` directory
creation, `EC-ENV-3` already running, `EC-ENV-7` no usable home) get **one** mechanism: boot only the FX toolkit with
`Platform.startup(Runnable)`, show a window on the FX Application Thread, then `Platform.exit()` and terminate with a
non-zero status for the failures / zero for the deliberate second-launch refusal.

**A plain `Stage` + `Scene`, not an `Alert` — corrected during implementation.** `Alert` and `Dialog` assume a running
`Application` context. Shown from a bare `Platform.startup()` runnable with no owner window, the stage appears with
the correct **title** and a completely blank body: no header, no message, and no button to dismiss it with. It fails
in the worst available shape — enough of a window to look deliberate, with none of the information that would tell the
user what happened. Verified by hand on macOS, and the reason this decision now names the control explicitly rather
than reaching for the obvious convenience API.

*Alternative considered:* `javax.swing.JOptionPane`. Rejected on two counts. It drags **`java.desktop`** into the
jlink image for one dialog, working directly against the size trim
(`--strip-debug --no-header-files --no-man-pages --compress zip-6`) the packaging scripts exist to apply; and it puts
a second UI toolkit in a project whose UI invariants (`javafx-ui.md`, `theming-tokens.md`) are written entirely about
the first one.

*Alternative considered:* launch the `Application` and let it show the dialog and exit. Rejected — it inverts D1 by
building a toolkit and an injector for a process whose only job is to say "already running", and it means a second
launch briefly touches the data directory the lock exists to protect.

**When the toolkit itself will not start**, the process exits with a **non-zero status and emits nothing at all** — no
`System.err` line, no crash file, no fallback dialog. This is deliberate. A terminal line is invisible to the user who
double-clicked a packaged app, and inventing a second failure channel to serve the developer case only would put a
write path in the one code region that has just proved it cannot write anywhere. The path is also narrower than it
looks: of the two ways to reach it, only **EC-ENV-7** (no usable home) is genuinely hostile to booting a toolkit;
**EC-ENV-2** (directory creation failed) has a working home, so the toolkit starts and the dialog shows normally.

**Consequence to accept:** `Platform.startup()` costs ~100–200 ms on the refusal path. That is paid only by a second
launch, which is a mistake being corrected, not a hot path.

### D3 — The resolver is a pure function; `:app` decides what a failure *means*

`AppPaths` becomes a record carrying `dataDir`, `logDir`, `lockFile`, `databaseFile` — resolved values, not lazy
lookups — produced by a resolver that takes `Function<String,String> getEnv` and `Function<String,String> getProperty`
as constructor parameters. Nothing in `ua.bookloom.util.paths` calls `System.getenv`/`System.getProperty` directly.
`Launcher` passes `System::getenv` and `System::getProperty`; every test passes a map.

That single seam is what makes the OS matrix testable at all: `os.name=Windows 11` + `LOCALAPPDATA=C:\Users\x\AppData\Local`
is a two-entry map, not a CI runner. **Seven** of the nine `EC-ENV-*` cases become ordinary unit tests here —
**EC-ENV-1, 2, 4, 6, 7, 8, 9**. The other two are not the resolver's: **EC-ENV-3** (a second instance) is proved
against `Launcher` and the lock, and **EC-ENV-5** (`ATOMIC_MOVE` unsupported) describes the atomic file-replace that
exports use, which this change does not ship — writing a test for it would assert against code that does not exist.

**`isDev` defaults to dev**, and the default is the safety property, not a fallback: an un-stamped run — an IDE launch,
a `gradlew run`, a test — stays out of the production folder. Only an explicit `BOOKLOOM_ENV=prod`, the
`-Dbookloom.env=prod` stamp the packaging scripts inject, or the `jpackage.app-path` property moves it to production.

**Resolution failures are `Result<AppPaths>`, not exceptions.** The resolver returns `Result.err(AppError(validation,
…))` for EC-ENV-7 (no usable home) — which is why `:util` requires `:api` and why seam F2 has to land in the same
change as its first consumer rather than later.

*Alternative considered:* an `AppPaths` interface with per-OS implementations selected by a factory. Rejected as
ceremony over a decision table: the whole per-OS difference is three branches choosing two directory names, and three
implementing classes would each need their own test rather than sharing one parameterised set.

**EC-ENV-8 (network filesystem) is detected and warned about, not acted on.** The resolver reports the condition; the
`PRAGMA journal_mode=TRUNCATE` fallback it implies belongs to change 8, which is where a SQLite connection first
exists. Detecting now and acting later is the honest split — the alternative is a warning nobody can act on, or dead
code guarding a connection that does not exist.

**Who says it, and when.** "Warns" cannot mean the resolver logs, because the resolver runs at step 2 and logging
starts at step 5 — and `:util` cannot reach SLF4J at all. So the condition travels as **data on the resolved value**,
and `:app` emits it as its **own dedicated warning line** immediately after Logback is configured, naming the resolved
folder and stating that SQLite's WAL locking is unreliable there. It is not folded into `app started version=<v>`: a
warning buried as a field on a normal-looking startup line is read straight past, and this one has to survive being
skim-read in a support thread.

### D4 — Rule 8 identifies the pre-logging path by *package*, not by class name

This is the change's one silent trap, and it is a trap because the protection *looks* present.

`ArchitectureRules.BOOTSTRAP_NAMED` matches a class in `ua.bookloom.app..` only if its simple name **equals**
`Launcher` or **contains** `Lock`. A class called `LoggingBootstrap` — the natural name for the class that configures
Logback — matches neither. It would therefore be free to declare `private static final Logger log = ...`, which is
exactly the mistake rule 8 exists to prevent, in exactly the class most able to make it: the one whose entire job is
to run *before* logging is configured.

The fix is to **stop identifying these classes by name**. `BOOTSTRAP_NAMED` is deleted; `ON_BOOTSTRAP_PATH` becomes
`ua.bookloom.util.paths..` **or** a new `ua.bookloom.app.bootstrap..`, and `Launcher`, the lock acquirer and the
Logback bootstrap all move into that package. Membership stops being a naming convention the next author has to
infer and becomes a directory they must deliberately choose — and a directory whose name states the constraint.
Everything added there afterwards is covered with no predicate edit, ever.

*Alternative considered:* widen `BOOTSTRAP_NAMED` to also match `LoggingBootstrap`. Rejected, and this is the
distinction worth being precise about: it does not remove the naming coupling, it **relocates** it. The class's simple
name stays load-bearing — now in two places, the predicate and the fixture — and the *next* pre-logging class still
arrives unprotected until somebody remembers to edit the regex again. The failure it permits is the same one D4
exists to close.

*Alternative considered:* name the class `BootstrapLock…`-something to fit the existing predicate. Rejected — naming a
class around a regex is a comment written in the wrong medium, and it has the identical next-class problem.

**Three consequences follow, and each is a red build if it is missed.** (a) `RuleViolationFixtureTest` already proves
rule 8 against `ua.bookloom.app.archfixture.Launcher`, which is caught **by the name predicate**; once that predicate
is gone the rule cannot see it and the test asserting rejection fails — so the fixture moves under the new bootstrap
package in the same task. (b) Rule 8's `.as(...)` text enumerates "Launcher/lock acquisition in `ua.bookloom.app..`"
and must be rewritten to name the package, while keeping the two properties `RuleSuiteCompletenessTest` asserts: it
opens with the spec name plus the separator, and it states a *why*. (c) The main class in the packaging scripts
becomes `ua.bookloom.app.bootstrap.Launcher`. Re-scoping a predicate adds no `ArchRule` field, so the eight-rule count
is unaffected.

**Logback types are confined to that one class.** `logging.md` requires SLF4J-only in application code, and
programmatic configuration unavoidably needs `LoggerContext` and `RollingFileAppender`. One class, in
`ua.bookloom.app.bootstrap`, is the whole carve-out; `api-is-framework-free` already bans `ch.qos.logback..` from
`:api`, and no other module gains the dependency.

### D5 — `ErrorCode` lands complete; `details` is built, never concatenated

All **fifteen** constants ship now, and this change can produce exactly **two** of them — `validation` (the resolver
refusing an unusable home, EC-ENV-7) and `internal` (a boundary wrap). The other thirteen, `auth` and `rateLimited`
and `contextWindow` among them, describe failures of I/O that does not exist yet. The alternative — add constants as their producers appear — would make the enum's shape a moving
target for twenty-six changes, and seam F2's whole purpose is that every later change compiles against a fixed one.
Being unreachable is not a defect in an enum constant; it is what "contract" means.

`AppError.details` is produced by a **typed builder** over the allowlist (HTTP status, endpoint host without token,
model name, timeout, attempt count, QA finding names) — never by `e.getMessage()` concatenation. This is a structural
guarantee rather than a review rule: if the only way to get a `details` string is to hand typed fields to a builder,
a stack trace or an `Authorization` header has no syntax to arrive by.

`Result<T>` is a record with two `@Nullable` components and static `ok`/`err` factories; `map`/`flatMap` short-circuit
on error. It is **not** sealed and there is no `Ok`/`Err` subtype pair: `09_ERROR_HANDLING.md#result-envelope` fixes
the record shape, and callers branch on `isOk()`.

**Two implementation-time corrections to this decision, both discovered by the gates rather than by review.**

The safe-details builder is a **record**, not a builder object. The mutable fluent builder written first violated the
shipped `records-first` ArchUnit rule, which requires every non-enum top-level type in a `..api..` package to be a
record. Rather than weaken a rule that already existed, the allowlist became the record's **component set** — so it
is now enforced twice over, as the components and as the method set, and a sixteenth field cannot be added as an
implementation detail without changing what the test asserts. No ADR: this satisfies an existing rule more strictly
rather than deviating from anything.

`ErrorCode.busy` **also covers the process single-instance lock**, not only the inference gate. A second launch that
fails `tryLock()` needs a code, the two situations differ only in which exclusive resource is held, and no other
constant fits — `validation` would be actively wrong, since the user did nothing invalid. The alternative was a
sixteenth constant, which would break the fixity that is seam F2's entire value. The specification's inline note
describes `busy` narrowly, so this **is** a deviation and is recorded as **ADR-0022**.

### D6 — Two-phase init ships with a real Phase 1 and a deliberately empty Phase 2

`10_DI_AND_LIFECYCLE.md#two-phase-init` defines Phase 1 (construct services, no I/O) and Phase 2 (open resources, run
Flyway, backfill repositories). This change has nothing to open.

Phase 2 ships as a **named, ordered, empty step** rather than being omitted. The reason is that its *position* is the
load-bearing part: migrations must complete before any `JobHandle` exists, and the first scene must be built after
Phase 2 so settings reads have a database behind them. Change 8 fills the body; if the step were absent, change 8
would have to introduce the ordering and the SQLite work at once, which is precisely the "later stages reshape earlier
code" that `07_ROADMAP.md#forward-compatibility-seams` forbids.

**The position is asserted, not merely documented.** An empty step that nothing checks can be quietly reordered or
dropped by the change that fills it — which is the exact accident this decision exists to prevent, arriving by the
exact route it was meant to block. So a test pins the order: Phase 2 runs after Phase 1 and before the first scene is
built. Note that the boot smoke's "two-phase init completed" assertion does **not** cover this: it passes under any
ordering, including none.

The six Guice module classes are all passed to `Guice.createInjector` even though four bind nothing. An injector that
successfully assembles a graph spanning eight JPMS modules is the integration signal; assembling only the two that
have content would prove less.

### D7 — One minimal token set, in `:ui`, attached at the `Scene`

The stylesheet lives in `:ui`, not `:app` — and this is **mandated, not preferred**: FR-THEME-5 states outright that
"Theme CSS lives in the `:ui` module resources as a **single stylesheet** declaring the role set once and the two
value blocks applied on `.root`". Two consequences follow that a "minimal" reading could easily miss. It must be
**one** file, so change 6 extends this stylesheet rather than adding a second beside it; and the file must already be
structured as *role set + value block on `.root`*, because that is the shape FR-THEME-2's light/dark swap needs.
`:app` attaches it to the `Scene` and does nothing else with styling.

"Minimal" is defined by what must be *proven*, not by a token count: looked-up colors declared on `.root`, referenced
by at least one selector, attached at `Scene` level, with **no** `node.setStyle(...)` anywhere and no hard-coded hex
outside the `.root` block. The four brand anchors (`charcoal #3a4a52`, `slate #b2babd`, `sand #e7d6c0`,
`cognac #a58075`) plus `bg`, `surface` and `text` are enough to demonstrate the pattern. Change 6 adds the remaining
roles and the dark block; it must not have to *restructure* what is here.

### D8 — Packaging: `Sync` into `modules/app/build/dist/libs/`, smoke via `-Dglass.platform=Headless`

`:app:collectDist` is a plain `Sync` of the app jar plus `runtimeClasspath` into **`modules/app/build/dist/libs/`**.
The frozen spec says `app/build/dist/libs/` at four places; **ADR-0021 supersedes all four**, and this is the change
that writes the path for the first time — which is why the layout move was done immediately before it, rather than
after.

The launch smoke uses **`-Dglass.platform=Headless`**, JavaFX 26's built-in headless platform. The frozen
`03_PACKAGING_JPACKAGE.md#verification` calls for `-Dglass.platform=Monocle -Dmonocle.platform=Headless`; **ADR-0019
supersedes that**, because `org.testfx:openjfx-monocle` has no release past 21.0.2 and cannot run against a JavaFX 26
runtime at all. Writing the spec's flags would produce a smoke that fails on every platform.

The smoke asserts a **positive startup signal** — injector built, two-phase init complete, primary `Stage` shown —
within a bounded timeout, against a temp data dir via `BOOKLOOM_DATA_DIR`. "Did not actually launch" is a hard
failure, never a skip (`02_QUALITY_GATES.md#jpackage-smoke`). Running it against the *developer's real* data directory
would have the smoke fight the single-instance lock of a running app, so the override is not optional.

**CI is not exercised** until the project is feature-complete (the standing decision recorded under change 1's task
7.7), so the packaging-matrix wiring ships as documented debt; the scripts themselves are run locally on this OS.

### D9 — Why `skip_specs`

Recorded here because `.openspec.yaml` cites it.

This change ships the **frame**: a window opens, a lock is refused, a version is logged, a package is produced. A user
can do nothing with the product — no import, parse, translate, review, or export exists. `openspec/specs/` is the
ledger of what BookLoom *does for its user* (ADR-0016); a blank window is the surface a later answer gets painted on,
not an answer.

Seam F2 makes the case sharpest: `Result`/`AppError`/`ErrorCode` is a **contract**, and this change introduces exactly
**zero** code paths that return a populated error, because there is no I/O yet that can fail. A requirement like "WHEN
a port fails, the system SHALL return `Result.err`" would describe a situation the software cannot enter until change
10. `spec-authoring.md` names inventing a requirement to satisfy validation as a defect, so the verification lives in
`tasks.md` — and, unusually for a `skip_specs` change, in a genuinely large test suite, because the resolver alone is
three operating systems × two environments × seven enumerated edge cases.

**The `FR-*` marker rule this change follows.** A test that covers a **business requirement** carries
`// Covers: FR-*` plus its one-line EARS restatement. A **mechanical or infrastructure** test — a null guard, the boot
smoke, the packaging launch smoke, the stylesheet-attachment check — is simply written, and carries no marker, because
there is no requirement for it to cite. That is not a loophole: the `Result`/`AppError` shape genuinely has **no**
`FR-*` anywhere in the frozen spec (it is DD-14 and `09_ERROR_HANDLING.md`, architecture rather than product
behaviour), and the alternative to saying so plainly is an invented id — which `spec-authoring.md` names as a defect
outright. An id is a citation; a citation that points nowhere is worse than none.

## Risks / Trade-offs

- **The rule-8 naming gap (D4) is not noticed, and a static logger ships on the bootstrap path** → The symptom is
  brutal to diagnose: logging silently writes to the *wrong directory*, the app works, every test passes, and the
  defect is discovered by a user who cannot find their log file. Mitigated by re-scoping the rule to
  `ua.bookloom.app.bootstrap..` **and** keeping a violation fixture in that package, so `RuleViolationFixtureTest`
  fails if the scope is ever narrowed back. This is the one risk in the change that the standing gate does not catch
  on its own.
- **The rule-8 re-scope is done by halves and the archTest suite goes red** → Deleting the name predicate without
  moving `ua.bookloom.app.archfixture.Launcher` under the new package leaves a fixture the rule can no longer see,
  and `RuleViolationFixtureTest.bootstrapNoStaticLogger_staticLoggerFieldInAppLauncher_isRejected` fails asserting a
  rejection that no longer happens. This is not a subtle risk — it is a loud, immediate failure — but it is easy to
  read as "the re-scope broke something" rather than "the re-scope is half-applied", so task 3.1 names it.
- **The JPMS graph compiles but does not resolve at runtime** → The classic split: `requires` satisfies `javac` while
  a missing `opens … to com.google.guice` fails only when Guice reflects, and only at startup. Mitigated by the boot
  smoke being an actual `Application` launch rather than an injector unit test — and by change 1 having already
  written the `opens ua.bookloom.app to com.google.guice` clause for exactly this reason.
- **Logback's `logback-classic` is an automatic-module surprise under JPMS** → It ships a proper module descriptor,
  but the binding is discovered via `ServiceLoader`; a `requires` that satisfies compilation can still leave the
  binding unresolved at runtime, giving the "SLF4J: No providers were found" no-op logger. The startup log line
  asserted by the smoke is what catches it: a no-op logger produces no line.
- **`Platform.startup()` on the refusal path conflicts with an FX toolkit already started** → It throws
  `IllegalStateException` if called twice. Only reachable if D1's ordering is violated (`Application.launch` before
  the lock check), so the ordering is the mitigation; the smoke covers the normal path and a dedicated test covers
  the second-launch path.
- **The dev-default `isDev` hides a packaging mistake** → An image built without `--java-options "-Dbookloom.env=prod"`
  silently uses `BookLoom-Dev`, and everything works — it is simply the wrong folder. Mitigated by the packaging smoke
  asserting the startup log's resolved data dir, not merely that the app started.
- **Two ADR-superseded paths are written from ADRs rather than from the spec** → `modules/app/build/dist/libs/`
  (ADR-0021) and `-Dglass.platform=Headless` (ADR-0019). Anyone checking this change against the frozen spec alone
  will read both as defects. Recorded here and in the task text so the divergence is cited at the point of use.

## Migration Plan

Nothing is deployed and there is no data to migrate. "Migration" is the build-out order, and it is a dependency chain:

1. **Seam F2 first** (`:api`) — `Result`/`AppError`/`ErrorCode` and the safe-details builder. `:util`'s resolver
   returns `Result`, so this cannot come second.
2. **The resolver** (`:util`) — `isDev`, per-OS resolution, directory creation, the seven resolver-owned `EC-ENV-*`
   cases (1, 2, 4, 6, 7, 8, 9).
3. **Bootstrap** (`:app`) — the rule-8 package re-scope and the fixture move **first**, then `Launcher`, the lock and
   the programmatic Logback configuration into `ua.bookloom.app.bootstrap`. The rule leads so it polices the classes
   as they are written, not afterwards.
4. **Version resource** — the `generateVersionResource` task and `AppVersion`, so step 3's startup line has a value.
5. **The window** (`:ui` + `:app`) — the minimal token stylesheet, the `Application` subclass, the injector and
   two-phase init.
6. **Packaging** — `collectDist`, the four scripts, and the launch smoke.
7. **Green gate**, then the lock gates (a dependency was added, so lockfiles are regenerated and must verify).

**Rollback** is `git revert`. Nothing persists state, no schema exists, and the only external artifact is a
locally-built package that can be deleted.
