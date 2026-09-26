# Proposal

## Why

BookLoom translates a whole book end to end today, and only from a terminal. `./gradlew :app:translate
--args="'book.epub' --to uk --provider ollama --model gemma3:12b"` opens any of the four formats, masks its inline
markup, drives a real local model segment by segment, and writes a canonical-equal translated file beside the
source. The desktop application opens a 1024×700 window containing one `Label`.

The gap is entirely presentation. `:ui` holds four files — an `AppShellView` that builds a `StackPane` around a
placeholder label, a `Theme.stylesheet()` locator, a `UiModule` that binds nothing, and a `theme.css` carrying a
light-only five-token block that exists to prove the cascade works. There is no FXML in the repository, no
`ViewNames`, no `Navigator`, no `ResourceBundle` and no message file. Nobody who has not read `AGENTS.md` can
translate a book.

This change gives the working pipeline a face: the shell from `docs/specification/mockups/ui-mockup.html`, and
live screens for exactly what the command line already proves — open a book, choose the target language, choose
one of the two preconfigured local providers and a model, start the run, watch it, and find the written file. No
pipeline behaviour is added, extended or altered. Every region of the mockup the engine cannot yet answer is
built and disabled, so the shell looks finished and no screen claims a capability that does not exist.

## What Changes

**BREAKING:** none for any caller outside `:app`. The command line, the pipeline, the document engine and the
provider clients all keep their current behaviour and their observable signatures. The one `:api` addition is
purely additive. Two internal edits are worth naming rather than discovering: `AppModule`'s two executor
bindings move from `@Named` string constants to qualifier annotations (the constants go), and
`TranslateCommand`'s private destination-naming rule moves to `:util` so the book-brief screen calls the same
code rather than a second copy of it.

- **The full theme.** `theme.css` grows from five role tokens to the whole normative catalogue — 32 roles plus
  12 status roles, 44 in all — as one role set with a light and a dark value block swapped at `.root`, and the
  initial block follows the operating system's colour scheme. Forty-one are looked-up colours; the three
  elevation roles are drop-shadow effects on named style classes, because a looked-up colour cannot carry a
  box-shadow triple and silently renders nothing when asked to (D10). It stays one stylesheet
  (`01_Product/09_THEMING.md#token-catalog`, FR-THEME-1..10).
- **The shell.** Title bar with the two-state light↔dark quick-toggle and About; the left navigation with its
  Workflow / Application groups and the numbered workflow steps; breadcrumb; toolbar region;
  content host; modal host with scrim; toast host. Navigation is addressed through a `ViewNames` enum and a
  `Navigator`, and every controller is built by a Guice controller factory
  (`02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#navigation`, FR-UI-01..04).
- **A state mirror and a job runner.** One `@Singleton` observable mirror whose `publish*` methods wrap
  `Platform.runLater`, and a runner that executes the job on the existing injected daemon executor — reached
  through a new `@BackgroundExecutor` qualifier in `:util`, because its binding name is a string constant in
  `:app`, which `:ui` may not depend on — subscribes a `JobListener`, coalesces per-segment events onto a 100 ms
  cadence, and treats the `Result<JobReport>` the job returns as the authoritative terminal outcome
  (`#state-mirror`, FR-UI-02).
- **Two languages from the first screen.** English and Ukrainian resource bundles behind a typed message-key
  registry, ICU `MessageFormat` for plurals, and an injectable `Locale` provider that picks Ukrainian on a
  Ukrainian OS and English otherwise (FR-UI-06, FR-UI-08).
- **Settings.** All six tabs exist. **Providers** lists the two presets the provider registry already ships —
  `ollama` at `http://localhost:11434` and `lmstudio` at `http://localhost:1234/v1` — runs the three-stage
  verification against the selected one, reports each stage independently, and offers a model control that is
  filled by live discovery and still accepts a typed model id. **Appearance** carries the light / dark / system
  selector. Models, Generation, Automation and Storage & logs are present and disabled.
- **Model discovery becomes reachable.** Listing a provider's models is fully built and fully specified inside
  `:llm`, but no port exposes it: the verifier calls it and discards the list. One additive `:api` port —
  `ModelCatalog.listModels(providerId)` — makes the existing listing answerable to a caller. No discovery
  behaviour changes.
- **A place for failures to appear.** An error dialog carrying a failure's title, its message and its safe
  details behind an expander, and a four-severity transient-message surface coloured from the status roles —
  together with the assignment from each of the fifteen typed error codes to the surface it belongs on, so a
  stop a person chose, a destination that already exists and a model server that is not running are three
  visibly different events (FR-NOTIF-01..04).
- **Import, Book Brief, Structure.** A drop zone and a file chooser open a book through the document port; the
  detected-file card reports what the parsed document actually carries; an unsupported or DRM-protected file
  surfaces its typed error as the refusing state. Book Brief keeps its Languages card live and gains the
  destination path and the overwrite choice; every other card on it is built and disabled. Structure is a
  read-only tree of the book's units with their segment counts.
- **Translating and Export.** The dashboard binds to the mirror — progress, the four counters, the activity log,
  Pause / Resume / Stop — across six states: running, pausing, paused, stopping, stopped and provider-error. The
  two pending states exist because a pause or a stop aborts the model request in flight and then reaches a safe
  point, which is a visible moment rather than an instant. A stop is presented as a neutral outcome rather than a failure, and
  is terminal in this build. Export reports the written path, the counts and an open-folder action.
- **Three navigation entries with no screen behind them** — Projects, Names & style and Review queue, all in the
  workflow group — are listed and **inert**: shown in place and visibly unavailable, activating one changes
  nothing, and none of them has a screen, a placeholder or an FXML file. Listing them is what keeps the shell
  matching the mockup and the workflow numbering unbroken; building a placeholder nobody can navigate to would be
  a file with no reader. The mockup's Design-reference group (Component library, Dialogs & alerts, Notifications)
  is not listed: those are specimen sheets for the designer, not planned product screens, so the navigation has
  two groups.

**Assumptions taken, each reversible:**

1. **This change is larger than the one-page, ten-task norm** (`AGENTS.md#how-work-is-planned`) and says so rather
   than pretending otherwise. The slice has no value in halves: a shell with no screens cannot be exercised in the
   running app, which is half the Definition of Done. It is ten task groups, each finishable in one session.
2. **Nothing is persisted.** The chosen provider, model, target language and theme live for the session;
   `:persistence` is empty and `add-local-storage` owns SQLite. Theme falls back to the OS colour scheme on each
   launch and language to the OS locale, so persisting `ui.language` (FR-UI-06) is deferred with it.
3. **FR-UI-06's in-app language switch is deferred whole, not half-built.** The displayed language is chosen
   from the operating system and nothing in this build changes it. A switch whose answer does not survive a
   restart is a control that appears to remember and does not, and both bundles ship complete here, so adding it
   later is a control plus a stored value rather than a re-translation. Recorded in `localization`'s "Choose the
   language from the operating system on first launch" and in the implemented-requirements index.
4. **A stopped run is terminal.** `07_UI_ARCHITECTURE_JAVAFX.md#screens` marks the stopped state "(resumable)".
   Resuming a cancelled run means knowing which segments were already decided, which needs storage that does not
   exist; offering the control anyway would silently restart from zero. The clause is corrected in this change
   and the state is specified as terminal in `resume`.
5. **Both bundles ship now rather than English alone.** `07_ROADMAP.md#execution-notes` sequences localization
   before the screens precisely so no label is retrofitted; retrofitting means editing every view and every screen
   test a second time. Ukrainian ships as a working draft; `complete-ukrainian-localization` polishes it.
6. **ControlsFX and Ikonli, not AtlantaFX.** The first two supply the toggle switch, the segmented picker, the
   toast overlay and the icons that the mockup's control mapping names by hand. AtlantaFX is declined: `theme.css`
   already owns every colour, and a second base control theme would compete with the palette-token assertion that
   makes the mockup binding.
7. **A failure's own title and message stay in the language the port wrote them in.** Every visible word this
   change writes comes from the catalogue, but an `AppError`'s `title` and `message` are built in `:document`,
   `:llm` and `:pipeline` — modules that have no bundle and, under `fx-free-core` and `dependency-direction`,
   cannot acquire one. They are therefore treated exactly as `localization` already treats a file path or a model
   identifier: **data a port supplies, substituted into a catalogue-owned frame**, not text the interface wrote.
   A Ukrainian interface will show an English failure sentence inside a Ukrainian dialog until the error
   catalogue itself is translated, which is a change to the three service modules rather than to the window.
   Recorded in `localization`'s "Draw every visible word from a catalogue".
8. **A pause or a stop must not wait for the provider.** A hand run against LM Studio showed a Pause ignored for two
   minutes while a retry was sent, and a Stop that took effect only when the request returned. So the engine aborts
   the request in flight and sends none after the button was pressed (task 9.5, `translation-pipeline`); the
   `pausing` and `stopping` states cover the moment that takes. This supersedes the earlier position, drawn from
   `docs/next_features.md` §14, that a stop requested during a model call waits for that call to return.

## Capabilities

Requirements are filed under the capability whose FR area owns them (`openspec/config.yaml`), not under the
screen that displays them — so that after archiving, "what does `export` guarantee?" is answerable from
`openspec/specs/export/`. Twelve capabilities take a delta: seven new, five already in the ledger.

### New Capabilities

- `theming`: the visual token system — one catalogue of 44 role names declared on `.root`, a light and a dark
  value for every one of them, three of them expressed as an elevation effect rather than a colour, the
  operating system's colour scheme choosing the initial block, the in-window quick toggle and the three-way
  selector in Settings, and the rule that a control never names a colour.
- `app-shell`: the window and everything structural in it — the title bar, the grouped and numbered left
  navigation, the breadcrumb, the content host, the modal and toast hosts, movement between screens, which
  entries are reachable and which are visibly unavailable, the build version in the dialog and in the log, which
  real control stands behind each drawn widget, how far the reference rendering binds, and the rule that no port
  is ever called on the thread that draws the window.
- `settings`: how a person chooses what translates their book — the two local providers that exist from first
  launch, checking one of them in three independent stages and seeing each stage's own verdict, choosing a model
  from what the server offers or typing one the server does not list, and choosing the interface theme.
- `localization`: the interface in two languages — every visible string drawn from a bundle through a typed key
  rather than written into a view, plural forms rendered by ICU patterns, and the language chosen from the
  operating system alone.
- `book-import`: opening a book and reporting what the parse found in it — the fields the parsed document
  actually carries, the rows that are absent when the book declares nothing, the refusing states for a protected
  or unreadable file, and the language-mismatch state that is built but unreachable.
- `book-brief`: the instruction given before a run — the target language, the uneditable source language the
  book declares, and the rest of the brief shown and unavailable because nothing downstream reads it.
- `notifications`: how a failure reaches the person — the blocking dialog carrying a failure's title, message and
  expandable safe details, the four transient severities drawn from the status roles, and the assignment from
  each of the fifteen typed error codes to the surface it appears on.

### Modified Capabilities

- `document-round-trip`: gains the read-only structure tree — the units in reading order with their segment
  counts and the total — identified by resource path and position, because a parsed unit carries no title.
- `translation-pipeline`: gains the dashboard's obligations — which four counts are reported and that no rate or
  estimate is, and that the result the run returns is the authoritative outcome while its events are progress
  only.
- `resume`: gains the screen's side of pause, resume and stop — a stop reported as a neutral outcome and
  terminal in this build, and the requested-but-not-yet-effective interval between pressing pause or stop and
  the engine aborting the request in flight and reporting the run paused or stopped.
- `export`: gains where the destination is chosen — on the book brief, before the run, with the default path
  rule — and the completion report that replaces the drawn save-path field and Export button.
- `llm-provider`: model discovery is already specified and built, but only inside the module — no caller can ask
  for the list. This adds the obligation that discovery is answerable through a port by provider id, and that an
  unregistered id is refused before any request is sent. The discovery behaviour itself is unchanged and is not
  restated.

`inference`, `quality-gates`, `review-queue`, `glossary` and `local-storage` get no delta.

## Impact

- **Modules:**
  - `:api` — one additive port in `ua.bookloom.api.llm` answering a provider id with the models it offers, and
    two named constants for the `"title"` and `"author"` keys `:document` already writes into `Document.metadata`,
    so the import screen reads them by name rather than by string literal. No existing type changes.
  - `:util` — the destination-naming rule, lifted out of `TranslateCommand`'s private `destination` method so the
    command line and the book-brief screen compute the same path from the same code. `:util` already depends on
    `:api` for `BookFormat.matchedSuffix`, and both `:ui` and `:app` already require `:util`.
  - `:llm` — that port's implementation, delegating to the `ProviderClient` the existing client factory already
    builds and caches. No client, mapper, retry, gate or verification behaviour changes.
  - `:ui` — effectively the whole change: the token catalogue, the shell, navigation, the controller factory, the
    state mirror and job runner, the message bundles and key registry, and the seven screens.
  - `:app` — the application class builds the real shell instead of the placeholder node, installs the new `:ui`
    bindings, and passes the injector to the controller factory.
  - `:document`, `:pipeline`, `:persistence` — untouched.
- **Dependencies:** ControlsFX (BSD-3-Clause) and Ikonli (Apache-2.0) enter `gradle/libs.versions.toml` and
  `:ui` only. Both licence families are already accepted by `config/license/allowed-licenses.json` without an
  entry; whether the exact string each POM declares matches one of those entries is checked when the artifacts
  first resolve, and a variant spelling is a normal outcome rather than evidence of a mis-pin. **No `:ui`
  lockfile diff is expected**: `bookloom.javafx-conventions` deactivates dependency locking on `:ui`'s and
  `:app`'s `compileClasspath`, `runtimeClasspath`, `testCompileClasspath` and `testRuntimeClasspath` for the
  JavaFX per-OS classifier carve-out, so the committed `modules/ui/gradle.lockfile` records only
  `annotationProcessor`, `checkstyle` and `spotbugs` state and never named Guice either
  (`AGENTS.md#what-will-bite-you`). Nothing new reaches `:api` (ArchUnit `api-is-framework-free`) or any core
  module (`fx-free-core`).
- **Network:** unchanged in kind. The only outbound traffic remains user-triggered provider communication with
  the configured endpoint — now also from the window, where pressing a verification button or opening a model
  list is the trigger. No background call, no scheduled call, no telemetry
  (`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`).
- **Decision records:** no new ADR. ADR-0019 (JavaFX 26's built-in headless platform, so every UI test runs with
  no display), ADR-0021 (the module layout), ADR-0012 (per-project provider binding, whose absence here is a
  stated non-goal) and ADR-0033 (the bound chat model and the pausable job) already settle the shape; the choices
  inside it are recorded in `design.md`.
- **Docs corrected in this change** — each because the shipped behaviour legitimately outgrew the clause
  (`.claude/rules/spec-authoring.md`), all of them in the final task group:
  - `01_Product/08_UI_SCREENS_AND_STATES.md#screen-export` — the screen reports a finished file rather than
    triggering one, because export is a stage of the run and not a separate operation, so the save-path field and
    the Export button go.
  - `#screen-import` — the detected-file card states the fields the parsed document actually carries, and the
    language-mismatch state has no trigger until source-language detection is built.
  - `#screen-book-brief` — the destination path and the overwrite choice live here, because the run needs them
    before it starts.
  - `#screen-structure` — the screen is read-only; "confirm translate-vs-preserve" describes a choice nothing
    downstream reads, and rows are identified by resource path because a parsed unit carries no title.
  - `02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress` — the dashboard reports the counts the engine
    emits, not a throughput or an estimate derived from elapsed time.
  - `#screens` — the same four corrections in its own enumeration (the export save path and Export button, the
    import cover card, the throughput/ETA dashboard) plus "stopped (resumable)", which is terminal in this build,
    **and the two new run states** `pausing` and `stopping`.
  - `01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating` — the same section, in five places the `#screens`
    enumeration does not reach: the Throughput/ETA `Label`s row and the in-flight live panel both go (the engine
    reports neither); "user-stopped and **resumable** … resume in place or leave to Projects" becomes terminal;
    the counts row names the four the engine actually emits, dropping "repaired", which it does not; and the
    states table gains `pausing` and `stopping`. Every state a document enumerates needs a screen/state test
    (`.claude/rules/testing.md`), so an un-extended enumeration puts the specification and the suite out of step
    on the day this ships.
  - `01_Product/07_SETTINGS.md#appearance-tab` — the Language (UI) row is marked deferred with its reason, since
    this build offers no control that changes the displayed language.
  - `01_Product/01_FUNCTIONAL_REQUIREMENTS.md` — **the table every correction above derives from**, at
    `#fr-import` (FR-IMPORT-06's cover, chapter count and detected source language), `#fr-resume` (FR-RESUME-05's
    resumable stopped state), `#fr-export` (FR-EXPORT-04's save path on the export screen) and `#fr-brief`
    (FR-BRIEF-01's editable source language). Correcting the screens document and leaving the requirement it was
    derived from asserting the opposite is the drift `.claude/rules/spec-authoring.md`'s same-change rule exists
    to prevent.
  - `#theming` — no AtlantaFX, and why.
  - `#mvvm` — the shell chrome is built in code rather than as FXML plus a controller, with its reason.
  - `.claude/rules/javafx-ui.md` — a narrow carve-out for the shell chrome from the FXML-plus-controller MUST
    (D2), and the `Task`/`Service` MUST reconciled with a job the engine runs synchronously (D3).
  - `.claude/rules/threading-concurrency.md` — the same `Task`/`Service` reconciliation.
  - `.claude/rules/theming-tokens.md` — its MUST requires every catalogued role, "shadow" included, to be a
    looked-up colour carrying "a light and a dark **hex**". Three of them hold a shadow triple and cannot be
    either (D10). The rule gains the same carve-out the requirement does, in the task that writes the stylesheet
    — otherwise the first task of this change produces output its own Read list rejects.
  - `docs/next_features.md` §14 — which of its items this change answered from the screen side and which it did
    not.
  - `docs/specification/IMPLEMENTED_REQUIREMENTS.md` — **new**: one hand-maintained row per implemented FR id
    giving the change that implemented it and the `openspec/specs/` capability that now owns it. Prose only: a
    pointer a human maintains and reads, never the id-coverage machinery ADR-0032 retired.
  - `docs/implementation_plan/01_MODULE_INVENTORY.md`, `docs/implementation_plan/CHANGE_BACKLOG.md#where-this-stands`,
    `AGENTS.md` "Where it stands" and `docs/DEVELOPMENT.md` are refreshed.
- **Non-goals**, every one of them a region of the mockup that is built and disabled rather than omitted: the
  projects list and any project concept; the glossary screen and its table; the review queue and side-by-side
  editing; the Book Brief's tone, register, narrative voice, name policy, foreign-passage policy, footnote and
  unit policies, faithful↔natural balance, quality dial and the four "also translate" switches; the export
  screen's glossary, bilingual and report side files and its final-consistency pass; the Models, Generation,
  Automation and Storage & logs settings tabs; adding, editing or deleting a provider; any credential or
  authentication field; per-project provider binding and its two resume-time prompts; the welcome dialog;
  persistence of anything at all; resuming a run across a restart; and the toast and banner vocabulary beyond what
  the five live screens need.
