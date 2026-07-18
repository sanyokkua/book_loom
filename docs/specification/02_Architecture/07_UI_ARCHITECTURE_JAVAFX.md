**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
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

## observable-state-mirror {#state-mirror}

Core progress and job state are pushed into a `@Singleton` **state mirror** — an object of JavaFX observable
properties/collections that the UI binds to. The mirror exposes `publish*` methods that **wrap `Platform.runLater`**
internally, so the pipeline (running off the FX thread) calls plain methods and the mirror marshals every mutation onto
the FX Application Thread. Views bind to the mirror's properties; they never poll. This is the one bridge between the
core's worker threads and the scene graph (`08_THREADING_CONCURRENCY.md`).

### JobProgress snapshot {#jobprogress}

The Translating dashboard binds to a fixed, explicit observable surface so its contract is stable and testable. The
pipeline reports progress as an immutable **`JobProgress`** snapshot; the mirror exposes each field as an observable
property/collection:

| Field             | Meaning                                                                  |
|-------------------|--------------------------------------------------------------------------|
| `autoAccepted`    | count of segments auto-accepted this run                                 |
| `repaired`        | count accepted after a directed repair round                             |
| `flagged`         | count flagged for review                                                 |
| `remaining`       | count not yet processed                                                  |
| `chapterIndex`    | current chapter index                                                    |
| `chunkIndex`      | current chunk index within the chapter                                   |
| `inFlightSource`  | live in-flight source text (current chunk)                               |
| `inFlightTarget`  | live in-flight target text (streamed-in whole, not token streaming — D8) |
| `judgeScore`      | live judge score for the in-flight chunk (or absent when judge off)      |
| `tokensPerSecond` | current throughput (tok/s)                                               |
| `eta`             | estimated time remaining (`java.time.Duration`)                          |

Counters (`autoAccepted/repaired/flagged/remaining`) and rates are locale-formatted for display
(`10_I18N_AND_ACCESSIBILITY.md#locale-formatted-fields`).

The mirror's `publish*` methods (each wrapping `Platform.runLater`) are the exact surface the pipeline calls:

- `publishJobProgress(JobProgress)` — replace the whole progress snapshot (counts, indices, tok/s, ETA).
- `publishInFlight(source, target, judgeScore)` — update the live in-flight source/target/judge-score panel.
- `publishActivity(ActivityEntry)` — **append** one localized, bundle-keyed entry to the activity log
  (`11_NOTIFICATIONS_AND_ERRORS.md#activity-log`); the log is a bounded observable list (last N).
- `publishRunState(RunState)` — set the run state (`running`/`paused`/`stopped`/`provider-error`).
- `publishToast(kind, key, args)` and `publishBanner(...)` — transient/persistent surfacing
  (`11_NOTIFICATIONS_AND_ERRORS.md`).

Views bind read-only to these; no other channel mutates the dashboard. This fixed surface is what `#ui-conformance`
/widget tests drive (`04_Build_and_Release/06_TESTING_STRATEGY.md`).

## navigation {#navigation}

- **`ViewNames`** enum — one constant per screen (`PROJECTS`, `IMPORT`, `BOOK_BRIEF`, `STRUCTURE`, `NAMES_STYLE`,
  `TRANSLATING`, `REVIEW`, `EXPORT`, `SETTINGS`), each carrying its FXML path.
- A `Navigator` (`@Singleton`) swaps the root content region by `ViewNames`, using the controller factory to construct
  the target view. Back/forward and deep-linking to a screen state (e.g. Import → language-mismatch) are driven by view
  state, not separate FXML.

## theming {#theming}

- **Token-only CSS** applied at the **Scene** level: **one set of token *roles*** on `.root` (surface, border, text,
  primary/Cognac, nav- *, title-*, status ok/warn/err/info, focus, …), with **two value blocks — light and dark —
  swapped at `.root`**; JavaFX 25 reads the OS `prefers-color-scheme` to pick the block. AtlantaFX provides the base
  control theme; app tokens override brand colors. The full role catalogue with light + dark values is in
  `01_Product/09_THEMING.md#token-catalog`.
- The **accent is fixed to Cognac** in v1 (not user-selectable — `09_THEMING.md` FR-THEME-4).
- Controls reference role tokens (`-color-accent`, `-color-bg-surface`, …), never hard-coded hex. Switching theme swaps
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
| toasts                           | ControlsFX `Notifications`        |
| icons                            | Ikonli                            |

## screens {#screens}

Enumerated against the mockup (each is a P6 visual-reference acceptance target):

- **Projects** (+ empty state) — project list, new/import entry.
- **Import** — states: `detected-ok`, `language-mismatch`, `DRM-blocked`, `unsupported`; detected-file card with
  cover/metadata.
- **Book Brief** — languages, genre, register, voice/era, audience, name policy, foreign-passage policy (keep-as-is /
  translate / translate+note), footnote/unit policy, faithful↔natural slider, quality dial, and the **"Also translate"
  toggle group** (ToC/navigation labels [on], image alt-text [on], book metadata title/author [on], frontmatter
  values [off]).
- **Structure** — chapter `TreeView`, segment counts.
- **Names & Style (glossary)** — glossary `TableView`, lock, add/import/export.
- **Translating** — states: `running`, `paused`, `stopped` (resumable), `provider-error`; the `JobProgress` dashboard
  (`#jobprogress`) — progress, live counters, in-flight source/target/judge-score, tok/s, ETA, activity log.
- **Review** — flagged list + side-by-side compare (two `TextArea`, no diff); actions Save-edit / Accept /
  Revert-to-machine-target / Retry / Retry-with-note / Skip, with dirty-edit tracking
  (`01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`).
- **Export** — format pick, validation, save path, optional glossary/bilingual/report, final consistency toggle.
- **Settings** — `TabPane`: Providers / Models / Generation / Appearance / Automation / Storage & logs.

## dialogs-and-notifications {#dialogs-notifications}

- **Dialogs:** welcome, add/edit provider (with the Test connection/models/inference trio →
  `04_LLM_INTEGRATION.md#three-stage-verification`), the two **provider-binding** prompts (bound provider/model
  unavailable → confirm fallback; settings-differ-from-last-used → apply vs continue — DD-31,
  `01_Product/08_UI_SCREENS_AND_STATES.md#dialog-provider-binding`), add glossary term, retry-with-note, confirm-delete,
  unsaved-changes, error-with-details (expandable technical detail), export-complete, about.
- **Notifications:** toasts `ok/info/warn/err`; banners `info/warn/err`; empty states per screen. Errors surface as a
  dialog (with expandable typed `AppError.details`) plus a toast, per `09_ERROR_HANDLING.md#ui-surfacing`.

## i18n {#i18n}

UI strings come from `ResourceBundle`s keyed by locale; the Appearance tab selects app language. No user text is
concatenated into layout; all labels are addressed through a **typed message-key registry** (no bare string literals),
and plural/gender-sensitive strings render via **ICU4J `MessageFormat`** (DD-48). The OS locale used for first-start
detection is read through an **injectable `Locale` provider** so the rule is unit-testable. Details in
`01_Product/10_I18N_AND_ACCESSIBILITY.md`.
