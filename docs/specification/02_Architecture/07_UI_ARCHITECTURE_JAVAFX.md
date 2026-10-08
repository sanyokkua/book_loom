**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-09-27
**Cross-references:** `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`,
`docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md`,
`docs/specification/mockups/ui-mockup.html`

# UI Architecture — JavaFX

`:ui` (with `:app`) is the only module that `requires javafx.*`. It presents an MVVM front-end over the FX-free core,
driven by an observable state mirror. The mockup at `docs/specification/mockups/ui-mockup.html` is the visual reference
(pattern P6): every screen, state, and dialog below maps to it. Binding acceptance is scoped — **structure, tokens, and
geometry gated in CI; pixel fidelity nightly** (`04_Build_and_Release/06_TESTING_STRATEGY.md`).

## bootstrap {#bootstrap}

Startup is a launcher → application → injector chain (detail in `10_DI_AND_LIFECYCLE.md`):

1. **`Launcher`** (in `:app`) — a plain `main` that does **not** extend `Application` (so the module path/JavaFX runtime
   initializes cleanly under jpackage). It acquires the single-instance lock, then launches the `Application`.
2. **`Application` subclass** — builds the Guice injector (composition root), performs two-phase init, then loads the
   first view.
3. **Controller factory** — `FXMLLoader.setControllerFactory(injector::getInstance)` so every FXML controller is
   Guice-constructed with its viewmodel and ports injected.

## mvvm {#mvvm}

Three layers inside `:ui`:

- **View** — FXML + a thin controller. The controller wires FX nodes to the viewmodel's observable properties and
  forwards user gestures. Controllers hold node references but no business logic.
- **ViewModel** — `@Singleton`-or-per-view classes exposing JavaFX `Property`/`ObservableList` state and command
  methods. **A ViewModel holds no `javafx.scene` node references** (no `Button`, `TableView`, `Scene`); it deals only in
  properties, observable collections, and calls to `:api` ports / `:pipeline`. This keeps viewmodels unit-testable
  without a scene graph.
- **Model** — the FX-free core reached through ports (`TranslationEngine`, repositories) and the observable state
  mirror.

**Shell chrome exception.** The View layer is FXML + a controller for every screen. The shell chrome — title bar,
navigation column, content host, modal host and toast host (`AppShellView`) — and the dialog cards shown in its modal
host (the About card, the error-with-details dialog) are built in code. The chrome's navigation entries are generated
from the `ViewNames` enum, and a dialog card is short static content assembled with the shell and shown in the shell's
own host, so an FXML file for either would be an empty container or a controller with no state of its own. The chrome
still observes properties (the navigator's current view and content, the theme block, the run status) rather than
owning state, and no screen is built this way.

## observable-state-mirror {#state-mirror}

Core progress and job state are pushed into a `@Singleton` **state mirror** — an object of JavaFX observable
properties/collections that the UI binds to. The mirror exposes `publish*` methods that **wrap `Platform.runLater`**
internally, so the pipeline (running off the FX thread) calls plain methods and the mirror marshals every mutation onto
the FX Application Thread. Views bind to the mirror's properties; they never poll. This is the one bridge between the
core's worker threads and the scene graph (`08_THREADING_CONCURRENCY.md`).

### JobProgress snapshot {#jobprogress}

The Translating dashboard binds to the mirror's fixed, explicit observable surface, so its contract is stable and
testable.

**The engine's events** (`ua.bookloom.api.pipeline`, design.md D5): `JobEvent` is sealed over `SegmentStarted`,
`SegmentDrafted`, `ModelCallStarted`, `ModelCallFinished`, `SegmentDecided`, `MemoryUpdated`, `Paused`, `Resumed` and
`Finished`.

- `SegmentStarted(segmentId, locator, displaySource, ChunkPosition(section, sections, chunk, chunks))` announces a
  segment's text as its translation starts.
- `SegmentDrafted(segmentId, displayTarget, confidence)` announces the drafted text once the model returns it.
- `ModelCallStarted(segmentId?, CallKind, segmentIds, attempt, maxAttempts, timeout?, RequestSummary(messageChars,
  contextWindow?, maxOutputTokens?)?)` and `ModelCallFinished(segmentId?, CallKind, elapsed, usage?, outputChars,
  usageEstimated, segmentIds, attempt, failure?)` bracket each **attempt** of a model call by its kind (draft, repair,
  judge, name scan, summary): the provider client reports every request it sends through a `CallAttemptListener`
  (`:api`), so a retry after a timeout is announced as attempt 2 with its own clock; a chunk's judge call names every
  segment it judges in `segmentIds`; a failed attempt finishes with its `ErrorCode`; `usageEstimated` marks a token
  count the client estimated rather than one the provider reported.
- `ContextAssembled(segmentId, ContextSnapshot)` announces, in display text, what a draft is sent with besides its
  source (preceding translations, rolling summary, glossary names, memory hits, recurring terms, character sheet);
  the review desk shows it collapsed, in prompt order (style sheet, summary, glossary names, locked names, suggested
  renderings, recurring terms, memory hits, characters, preceding translations).
- `CallSnapshotUpdated(CallSnapshot)` announces one model call as a person inspects it, under one `callId` per call:
  `WAITING` when its first attempt goes out (and again for a later attempt), then `ANSWERED` with the reply and usage,
  or `FAILED`/`CANCELLED`; each `SegmentOutcomeNote` noted afterwards (a batch item `ADOPTED` or `FELL_BACK` with its
  reason, a segment `ACCEPTED` or `FLAGGED` on the draft call that last drafted it) sends it again. It carries the
  segments the call is about (`CallSegment(id, locator, displaySource)`) and the prompt's filled parts in the order
  they were sent (`PromptSection(slot, heading, origin SYSTEM|USER, lines)`, the system style sheet first). Only the
  run's described calls are shown — the drafts (single and batch, with their repairs), the reviewer, the directed fix
  and the backward revision and consistency calls; the name scans and the summary are not.
- `RoundStarted(segmentId, round, rounds, judgeScore?, blockingFinding?)` announces each repair round a segment
  enters, for the log.
- `SegmentDecided` carries a `SegmentDetail(judgeScore?, path, findingKinds, displayTarget?)` and the point-in-time `JobProgress`
  snapshot, announcing a segment as decided.
- `MemoryUpdated(kind, label)` is sent only when a name scan (preparation or a body unit's end) adds at least one
  glossary entry (label `+n`), on each memory reuse (labelled with the segment's locator), and on each summary
  refresh (labelled with its version).
- `Paused` names the segment the run paused on — for a pause on a provider error, the segment whose step failed (a
  chunk's judge call by its first segment) with `pauses` and `pausesBeforeFlagging`, so the banner can say what Retry
  now and Skip segment will do.

The count snapshot is `JobProgress(JobStage stage, int section, int sections, int chunk, int chunks, int
autoAccepted, int repairedAccepted, int accepted, int flagged, int pending)`, where `JobStage` is `PREP | TRANSLATE |
REVISE`; it rides on `SegmentDecided` and `Paused`. `section, sections` is the 1-based position and count of **body**
units — the auxiliary unit is never counted, and `section = sections` while it runs; the screen labels this column
"Chapter" (no separate chapter field exists — the screen supplies the label). `chunk, chunks` is the position and
count within the current section's chunking. `pending` leaves out segments kept as source by choice.

**The mirror's properties** (`StateMirror`, read-only, read on the FX thread):

| Property             | Meaning                                                                                                          |
|------------------------|---------------------------------------------------------------------------------------------------------------|
| `runState`            | `RunState`: `IDLE`, `RUNNING`, `PAUSED`, `STOPPED`, `PROVIDER_ERROR`, `COMPLETED`, `FAILED`                     |
| `currentFile`         | the file name the run is translating                                                                            |
| `section`, `sections` | the 1-based body-unit position and count (`#jobprogress`)                                                       |
| `chunk`, `chunks`     | the position and count within the current section's chunking                                                    |
| `autoAccepted`        | segments accepted without a repair                                                                              |
| `repairedAccepted`    | segments accepted after a repair                                                                                |
| `accepted`            | segments accepted so far (auto-accepted + repaired-accepted)                                                    |
| `flagged`             | segments flagged so far                                                                                          |
| `remaining`           | segments not yet decided, leaving out segments kept as source by choice (the engine's `pending`)                |
| `total`               | accepted + flagged + remaining, derived once by `RunFigures`                                                    |
| `progressFraction`    | decided segments over the total, `0.0` when the total is zero                                                   |
| `tokensPerSecond`     | derived from the recent drafts' `ModelCallFinished` usage                                                       |
| `timeLeft`, `elapsed` | the run's estimated time left and time spent                                                                    |
| `liveCalls`           | the model call in flight (else the newest finished) and the one before it, with the targets and decisions of their segments and a waiting call's clock in whole seconds, for the "Model calls (live)" panel; published at most once per cadence tick, and only when it changed |
| `reviewQueue`         | the flagged (and, on the "All segments" filter, kept-as-source) segments the review panel lists                 |
| `waitingSeconds`      | how long one model request has been outstanding once past 10 s, else `NOT_WAITING` (-1)                         |
| `providerErrorNotice` | the failure's error code, while the run is auto-paused on a provider error                                      |
| `failure`             | the run's `AppError`, when it ended on one                                                                       |
| `report`              | the run's `JobReport`, when it returned one                                                                      |
| `activityLog`         | unmodifiable `ObservableList<LogEntry>` of the newest 500 entries, each tagged `ok`/`fix`/`mem`/`sum`/`retry`/`err`/`info` |

Counts are locale-formatted for display (`10_I18N_AND_ACCESSIBILITY.md#locale-formatted-fields`).

**The publishers** (each wraps `Platform.runLater`, so engine threads call plain methods): `publishRunStarted()`,
`publishRunState(RunState)`, `publishWaitingSeconds(int)`, `publishProgress(JobProgress)`,
`publishLivePanel(SegmentStarted, SegmentDrafted?)`, `publishLogEntries(List<LogEntry>)` and
`publishOutcome(RunState, JobReport, AppError)`. A per-run `RunSession` receives the engine events on the engine's
thread and coalesces them into **one publish per 100 ms tick**, so a fast job never floods the FX queue.
`TranslationRunner` treats the `Result<JobReport>` the job **returns** as authoritative for the terminal state — a
run refused before it starts emits no `Finished` event — and publishes it last, so a late tick cannot overwrite it.
Toasts and banners are surfaced by the notification components, not the mirror
(`11_NOTIFICATIONS_AND_ERRORS.md`).

Views bind read-only to these; no other channel mutates the dashboard. This fixed surface is what `#ui-conformance`
/widget tests drive (`04_Build_and_Release/06_TESTING_STRATEGY.md`).

## navigation {#navigation}

- **`ViewNames`** enum — one constant per navigation entry (`PROJECTS`, `IMPORT`, `BOOK_BRIEF`, `STRUCTURE`,
  `NAMES_STYLE`, `TRANSLATING`, `EXPORT`, `SETTINGS`), each carrying its FXML path once it has a screen. Only
  `PROJECTS` carries none in this build: it is the one inert entry, and `Navigator.nextAvailableStep` skips it. The
  review panel is reached from inside `TRANSLATING`, not through its own `ViewNames` constant.
- A `Navigator` (`@Singleton`) swaps the root content region by `ViewNames`, using the controller factory to construct
  the target view. Back/forward and deep-linking to a screen state (e.g. Import → language-mismatch) are driven by view
  state, not separate FXML.

## theming {#theming}

- **Token-only CSS** applied at the **Scene** level: **one set of token *roles*** on `.root` (surface, border, text,
  primary/Cognac, nav- *, title-*, status ok/warn/err/info, focus, …), with **two value blocks — light and dark —
  swapped at `.root`**; JavaFX 26 reads the OS `prefers-color-scheme` to pick the block. `theme.css` is the **single
  stylesheet** and the only source of colour. The full role catalogue with light + dark values is in
  `01_Product/09_THEMING.md#token-catalog`.
- **AtlantaFX is not used** and is on no classpath. Controls come from JavaFX itself plus **ControlsFX** (toggle
  switch, segmented picker) and **Ikonli** (icons). A second base theme that also styles `.root` and every control would
  be a second source of colour and a resolution order to reason about, and would compete with the role-lookup
  conformance check, which reads a role off `.root` (FR-THEME-5;
  `openspec/changes/archive/2026-09-26-add-ui-translation-workspace/design.md`, D7).
- The **accent is fixed to Cognac** in v1 (not user-selectable — `09_THEMING.md` FR-THEME-4).
- Controls reference role tokens (`-color-primary`, `-color-surface`, …), never hard-coded hex. Switching theme swaps
  the value block only, not the roles.

## controls-mapping {#controls-mapping}

Every mockup widget maps to a real JavaFX/ControlsFX control:

| Mockup element                   | Control                           |
|-------------------------------------|---------------------------------------|
| segmented pickers                | `SegmentedButton` / `ToggleGroup` |
| side-by-side panes               | two `TextArea`                    |
| tables (glossary, flagged queue) | `TableView` (virtualized)         |
| chapter/structure tree           | `TreeView`                        |
| toggles                          | `ToggleSwitch` (ControlsFX)       |
| tabbed settings                  | `TabPane`                         |
| dialogs                          | `Dialog` / `Alert`                |
| toasts                           | Native token-styled nodes         |
| icons                            | Ikonli                            |

## screens {#screens}

Enumerated against the mockup (each is a P6 visual-reference acceptance target); the per-screen detail and what this
build does and does not do is in `01_Product/08_UI_SCREENS_AND_STATES.md`:

- **Projects** (+ empty state) — project list, new/import entry. Inert in this build (no screen yet).
- **Import** — states: idle, opening, detected, `language-mismatch` (an EPUB's own language declarations disagree, or
  the book declares a language the application does not recognize), refused (DRM and unsupported, each with the typed
  error code). Detected-file card with file, format and version, title/author, declared language, chapter/word
  counts, image/font counts, and DRM status, plus a cover thumbnail or a neutral placeholder.
- **Book Brief** — source and target language, both editable and searchable over the same 34 languages; every card
  live (Tone & style, Translation policies, Also translate, Quality vs speed). No destination card — the save path
  lives on Export.
- **Structure** — a nested, titled tree (resource path, position, segment count per node), the statistics card, the
  background round-trip/resource-id checks, the oversized-segment warning, Back and Continue.
- **Names & Style (glossary)** — glossary `TableView`, lock (target-only), add/model-scan/CSV import/export,
  `Start translation`. Live in this build.
- **Translating** — states: `idle`, `running`, `paused`, `stopped` (resumable — it re-enters at the first pending
  segment), `provider error` (auto-paused), `completed`, `failed`. The dashboard (`#jobprogress`) shows the progress
  line, time left, tokens per second, the four count tiles, the two-block live call panel (current and previous call), and the tagged activity log. The
  review panel lives inside this screen (`01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`).
- **Export** — chooses the destination and exports: save path with overwrite, format, the three side-file boxes, the
  final consistency-pass switch, the carried-over checks, the accepted/flagged/pending/kept-as-source counts, and
  `Export book` (unavailable while a run of the project is translating). The export-complete dialog is the only
  success notice.
- **Settings** — `TabPane`: Providers / Models / Generation / Appearance / Automation / Storage & logs.

## dialogs-and-notifications {#dialogs-notifications}

- **Dialogs:** welcome, add/edit provider (with the Test connection/models/inference trio →
  `04_LLM_INTEGRATION.md#three-stage-verification`), the two **provider-binding** prompts (bound provider/model
  unavailable → confirm fallback; settings-differ-from-last-used → apply vs continue — DD-31,
  `01_Product/08_UI_SCREENS_AND_STATES.md#dialog-provider-binding`), add glossary term, retry-with-note,
  confirm-delete, unsaved-changes, error-with-details (expandable technical detail), export-complete (shipped in
  this build — the export's only success notice, `01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`),
  about.
- **Notifications:** toasts (native token-styled nodes stacked in-shell) `ok/info/warn/err`; banners `info/warn/err`;
  empty states per screen. Errors surface as a dialog (with expandable typed `AppError.details`) plus a toast, per
  `09_ERROR_HANDLING.md#ui-surfacing`.

## i18n {#i18n}

UI strings come from `ResourceBundle`s keyed by locale; the Appearance tab is specified to select the app language, but
that switch is **deferred** in this build: it needs local storage for `ui.language`, so the language follows the
operating system's locale (Ukrainian OS → `uk`, otherwise English). No user text is
concatenated into layout; all labels are addressed through a **typed message-key registry** (no bare string literals),
and plural/gender-sensitive strings render via **ICU4J `MessageFormat`** (DD-48). The OS locale used for first-start
detection is read through an **injectable `Locale` provider** so the rule is unit-testable. Details in
`01_Product/10_I18N_AND_ACCESSIBILITY.md`.
