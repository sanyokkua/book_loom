**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-09-26
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
still observes properties (the navigator's current view and content, the theme block) rather than owning state, and no
screen is built this way.

## observable-state-mirror {#state-mirror}

Core progress and job state are pushed into a `@Singleton` **state mirror** — an object of JavaFX observable
properties/collections that the UI binds to. The mirror exposes `publish*` methods that **wrap `Platform.runLater`**
internally, so the pipeline (running off the FX thread) calls plain methods and the mirror marshals every mutation onto
the FX Application Thread. Views bind to the mirror's properties; they never poll. This is the one bridge between the
core's worker threads and the scene graph (`08_THREADING_CONCURRENCY.md`).

### JobProgress snapshot {#jobprogress}

The Translating dashboard binds to the mirror's fixed, explicit observable surface, so its contract is stable and
testable. The engine reports counts only; the dashboard shows those counts and derives nothing else.

**The engine's events** (`ua.bookloom.api.pipeline`): `JobEvent` is sealed over `StageStarted`, `ModelCallStarted`,
`SegmentDecided`, `Paused`, `Resumed` and `Finished`. The point-in-time count snapshot they carry is
`JobProgress(JobStage stage, int section, int sections, int accepted, int flagged, int pending)`, where `JobStage` is
`TRANSLATE` or `EXPORT`; it rides on `StageStarted`, `SegmentDecided` and `Paused`. `ModelCallStarted(segmentId)` marks
that a model call started (a draft and each repair announce once each; the client's own retries of one call do not). The engine names no throughput, no ETA, no in-flight text and no judge score, so
none of those appears on the dashboard.

**The mirror's properties** (`StateMirror`, read-only, read on the FX thread):

| Property          | Meaning                                                                                               |
|-------------------|-------------------------------------------------------------------------------------------------------|
| `runState`        | `RunState`: `IDLE`, `RUNNING`, `PAUSING`, `PAUSED`, `STOPPING`, `STOPPED`, `COMPLETED`, `FAILED`      |
| `accepted`        | segments accepted so far                                                                              |
| `flagged`         | segments flagged so far                                                                               |
| `remaining`       | segments not yet decided (the engine's `pending`)                                                     |
| `total`           | accepted + flagged + remaining, derived once by `RunFigures`                                          |
| `progressFraction`| decided segments over the total, `0.0` when the total is zero; the section columns never move the bar |
| `waitingSeconds`  | how long one model request has been outstanding once past 10 s, else `NOT_WAITING` (-1)               |
| `failure`         | the run's `AppError`, when it ended on one                                                            |
| `report`          | the run's `JobReport`, when it returned one                                                           |
| `activityLog`     | unmodifiable `ObservableList<LogEntry>` of the newest 500 entries                                     |

Counts are locale-formatted for display (`10_I18N_AND_ACCESSIBILITY.md#locale-formatted-fields`).

**The publishers** (each wraps `Platform.runLater`, so engine threads call plain methods): `publishRunStarted()`,
`publishRunState(RunState)`, `publishWaitingSeconds(int)`, `publishProgress(JobProgress)`,
`publishLogEntries(List<LogEntry>)` and `publishOutcome(RunState, JobReport, AppError)`. A per-run `RunSession` receives
the engine events on the engine's thread and coalesces them into **one publish per 100 ms tick**, so a fast job never
floods the FX queue. `TranslationRunner` treats the `Result<JobReport>` the job **returns** as authoritative for the
terminal state — a run refused before it starts emits no `Finished` event — and publishes it last, so a late tick cannot
overwrite it. Toasts and banners are surfaced by the notification components, not the mirror
(`11_NOTIFICATIONS_AND_ERRORS.md`).

Views bind read-only to these; no other channel mutates the dashboard. This fixed surface is what `#ui-conformance`
/widget tests drive (`04_Build_and_Release/06_TESTING_STRATEGY.md`).

## navigation {#navigation}

- **`ViewNames`** enum — one constant per navigation entry (`PROJECTS`, `IMPORT`, `BOOK_BRIEF`, `STRUCTURE`, `NAMES_STYLE`,
  `TRANSLATING`, `REVIEW`, `EXPORT`, `SETTINGS`), each carrying its FXML path once it has a screen. `PROJECTS`,
  `NAMES_STYLE` and `REVIEW` carry none in this build: they are inert entries, and `Navigator.nextAvailableStep` skips
  them.
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
|----------------------------------|-----------------------------------|
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
- **Import** — states: idle, opening, detected, refused (DRM and unsupported, with the typed error code),
  `language-mismatch` (built, no trigger yet — nothing detects a source language); detected-file card with file,
  format, title/author/declared language when present, and unit and segment counts. No cover.
- **Book Brief** — source language (read-only, as declared by the book), target language, the destination path with
  overwrite, and the cards nothing reads yet, shown disabled: genre, register, voice/era, audience, name policy,
  foreign-passage policy (keep-as-is / translate / translate+note), footnote/unit policy, faithful↔natural slider,
  quality dial, and the **"Also translate"** toggle group (ToC/navigation labels [on], image alt-text [on], book
  metadata title/author [on], frontmatter values [off]). A no-book state exists.
- **Structure** — a read-only flat list of units (resource path, position, segment count), the segment total, Back and
  Continue. No translate-vs-preserve confirmation.
- **Names & Style (glossary)** — glossary `TableView`, lock, add/import/export. Inert in this build.
- **Translating** — states: `idle`, `running`, `pausing`, `paused`, `stopping`, `stopped` (terminal: nothing is written
  and the run cannot be resumed), `completed`, `failed`; provider-error, refused and missing-input are notices in the
  state banner, not states. The dashboard (`#jobprogress`) shows the accepted / flagged / remaining / total counts and a
  progress bar, a "Waiting for the model… m:ss" cue after 10 s on one request, and the activity log. There is no
  throughput, ETA or in-flight panel.
- **Review** — flagged list + side-by-side compare (two `TextArea`, no diff); actions Save-edit / Accept /
  Revert-to-machine-target / Retry / Retry-with-note / Skip, with dirty-edit tracking
  (`01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`). Inert in this build.
- **Export** — reports the finished file rather than triggering it: format, written path, accepted and flagged counts,
  and an action that shows the file in the system file manager. No save path and no Export button (the path is chosen on
  the Book Brief); the glossary/bilingual/report options and the final consistency toggle are shown disabled.
- **Settings** — `TabPane`: Providers / Models / Generation / Appearance / Automation / Storage & logs.

## dialogs-and-notifications {#dialogs-notifications}

- **Dialogs:** welcome, add/edit provider (with the Test connection/models/inference trio →
  `04_LLM_INTEGRATION.md#three-stage-verification`), the two **provider-binding** prompts (bound provider/model
  unavailable → confirm fallback; settings-differ-from-last-used → apply vs continue — DD-31,
  `01_Product/08_UI_SCREENS_AND_STATES.md#dialog-provider-binding`), add glossary term, retry-with-note, confirm-delete,
  unsaved-changes, error-with-details (expandable technical detail), export-complete (specified but not shipped — the
  Export screen reports the written file itself), about.
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
