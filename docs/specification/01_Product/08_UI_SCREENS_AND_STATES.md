**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`), QA, UX **Last Updated:** 2026-09-26
**Cross-references:** `docs/specification/mockups/ui-mockup.html`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`, `docs/specification/01_Product/09_THEMING.md`,
`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md`,
`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`

# UI Screens and States

This is the binding UI specification. `docs/specification/mockups/ui-mockup.html` is the visual source of truth (DD-28);
this document enumerates every screen, state, dialog, toast, banner, and empty state, and maps each control to a
concrete JavaFX control. Every UI story cites the mockup via the P6 visual-reference pattern. **Binding acceptance is
scoped:** the mockup's **structure, looked-up tokens, and geometry** are gated in CI (headless TestFX/Monocle —
`04_Build_and_Release/06_TESTING_STRATEGY.md#ui-conformance`); **pixel fidelity** is a **nightly/on-demand**
tolerant-diff check, not a merge gate (`#visual-validation`). This document does not restate pixel values — those live
in the mockup. It realizes `FR-UI-*`.

## shell-and-navigation {#shell-and-navigation}

The application shell has a title bar, a left navigation grouped into Workflow / Application, a breadcrumb, a
toolbar-actions area, a content host, a modal host with scrim, and a toast host.

| Element            | JavaFX control                    | Notes                                                                                                                                                |
|--------------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Window / stage     | `Stage` + root `BorderPane`       | `.root` carries theme tokens                                                                                                                         |
| Left navigation    | `ScrollPane` around a `VBox`      | Workflow and Application groups; 236 px wide, scrolls vertically when the window is short                                                            |
| Breadcrumb         | `Label`/`Breadcrumbs`-style bar   | Reflects current screen                                                                                                                              |
| Toolbar actions    | `ToolBar` / `HBox` of `Button`    | Context actions per screen                                                                                                                           |
| Theme toggle       | `ToggleButton`                    | **2-state light↔dark quick-toggle** only; the tri-state selector including `system` lives in Settings → Appearance (`07_SETTINGS.md#appearance-tab`) |
| Content host       | `ScrollPane` around a `StackPane` | Hosts the active screen; scrolls when the screen is taller or wider than the window, and fits width and height so a growing list still fills it       |
| Modal host + scrim | `StackPane` overlay               | For `Dialog`/`Alert`                                                                                                                                 |
| Toast host         | Native token-styled JavaFX nodes  | ok/info/warn/err, stacked in-shell                                                                                                                   |

Smallest window: the stage's minimum is 960 × 640 as the outer window, title bar and borders included (the window opens
at 1024 × 700); the content area inside it is smaller by that decoration — about 944 wide with 8 px borders and about 612
high with a 28 px title bar. At that size every screen fits the width of the content area (proved for 944 × 600) and
every part stays reachable, scrolling vertically where the screen is taller: the navigation column and the content host
scroll rather than clip, a count tile (`.stat`) keeps its 120 px minimum width and never shows an ellipsis for its caption, and a progress bar keeps its 9 px height (a progress bar's
own minimum height is zero, so the height is fixed in the stylesheet).

P6 reference: match the shell chrome, nav grouping, and theme toggle in the mockup, except that the mockup's
"Design reference" group (component library, dialogs and alerts, notifications) is a specimen sheet for the designer and
is not a navigation group of the application.

Projects, Names & style and Review are inert navigation entries in this build: they are listed but have no screen yet,
so "Continue" skips them to the next entry that has one.

## screen-projects {#screen-projects}

Purpose: list existing projects and start a new one.

| Control            | JavaFX control          | Behaviour                            |
|--------------------|-------------------------|--------------------------------------|
| Project list       | `TableView` / card list | Rows open a project; supports resume |
| New project action | `Button`                | Opens Import                         |

States: **populated** (project list) and **empty** (`#empty-state-projects`: "No projects yet" with a call to action).
Requirements: FR-NOTIF-05. P6 reference: Projects and Projects-empty.

## screen-import {#screen-import}

Purpose: open a book and confirm the detected-file card.

| Control                 | JavaFX control                   | Behaviour                                                                                                                                                                                                             |
|-------------------------|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Drop zone / file picker | drag-drop target + `FileChooser` | FR-IMPORT-02                                                                                                                                                                                                          |
| Detected file card      | card of key/value rows (`Label`s) | Rows: file, format, title, author and declared language (each only when the book declares it), units ("Divided into") and segments ("Text to translate") (FR-IMPORT-06). No cover, no detected source language, no chapter count yet |

States (`#importState`):

| State             | Trigger                                                                       | UI                                                                                                    |
|-------------------|-------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| idle              | Nothing chosen yet                                                            | Drop zone                                                                                             |
| opening           | A book has been handed to the port                                            | Progress line naming the file                                                                         |
| detected          | Supported, no DRM                                                             | Detected-file card + Continue                                                                         |
| refused           | DRM detected (EC-DRM-1) or unsupported/corrupt file (FR-IMPORT-05)            | Error banner carrying the typed error code as a chip; the import is refused                           |
| language-mismatch | Declared ≠ detected language (EC-LANG-1)                                      | Warning banner. **Built but has no trigger yet:** nothing detects a source language, so `open` never produces it |

Requirements: FR-IMPORT-01..07. P6 reference: Import and each import state.

## screen-book-brief {#screen-book-brief}

Purpose: capture the Book Brief.

| Section              | Control                                                                                  | JavaFX control                                  | Requirement              |
|----------------------|------------------------------------------------------------------------------------------|-------------------------------------------------|--------------------------|
| Languages            | source (read-only display) and target (editable) pickers                                 | `ComboBox` (source disabled)                    | FR-BRIEF-01              |
| Destination          | output path (default computed from the source name and target language), Browse, overwrite switch, "already exists" warning | `TextField` + `FileChooser`, `ToggleSwitch`, warning banner | FR-EXPORT-04             |
| Tone & style         | genre, register, voice/era, audience; faithful↔natural                                   | `ComboBox`, `TextField`, `Slider`               | FR-BRIEF-02, FR-BRIEF-03 |
| Translation policies | name, foreign-passage (keep-as-is / translate / translate+note), footnote, unit policies | `SegmentedButton`/`ToggleGroup`, `ToggleSwitch` | FR-BRIEF-04..07          |
| Also translate       | ToC/navigation labels, image alt-text, book metadata title/author, frontmatter values    | four `ToggleSwitch`es                           | FR-BRIEF-04, FR-DOC      |
| Quality vs speed     | Fast/Balanced/Max                                                                        | `SegmentedButton`/`ToggleGroup`                 | FR-BRIEF-08              |

The **"Also translate" toggle group** controls which auxiliary text units the run translates (modelled as the synthetic
metadata unit — `NAV_LABEL`, `ALT`, `METADATA_TITLE`/`METADATA_AUTHOR`, `FRONTMATTER_VALUE`). Defaults: **ToC/navigation
labels on, image alt-text on, book metadata title/author on, frontmatter values off**.

In this build the source language is a read-only display: it shows the language the book declares, or "The book does
not declare one", because nothing edits or detects it yet. The target language is chosen from a fixed list. The
**destination path** and the **overwrite** switch live on this screen, not on Export: the save path is one of the four
components of the translation request and must exist before a run starts (FR-EXPORT-04); a warning shows while the path
already exists and overwriting is off, and Continue is unavailable until the request is valid. The Tone & style,
Translation policies, Also translate and Quality vs speed cards are drawn but **disabled and tagged "not used yet"**,
because nothing reads them. Opened with no book, the screen shows a **no-book state** ("No book is open" with an action
that leads to Import) instead of the form.

P6 reference: Book Brief with its sections, including the "Also translate" group.

## screen-structure {#screen-structure}

Purpose: show the parsed reading order, read-only.

| Control                | JavaFX control                | Behaviour                                                                                                                     |
|------------------------|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| Reading order          | `TreeView` (flat, virtualized) | One row per unit in reading order: its resource path, its position and its segment count. A unit carries no title, so none is shown |
| Total                  | `Label`                       | Total segments in the book                                                                                                    |
| Back / Continue        | `Button`s                     | Back returns to the Book Brief; Continue goes to the next **available** step (`Navigator.nextAvailableStep`), i.e. Translating, not the inert Names & style |

The list is flat: the document model has no chapter tree yet. There is **no translate-vs-preserve confirmation** in this
build. With no book open the screen shows the same no-book state as the Book Brief.

Requirements: FR-DOC-01, FR-DOC-08. P6 reference: Structure found / Reading order.

## screen-names-and-style {#screen-names-and-style}

Purpose: review and edit the auto-proposed glossary.

| Control        | JavaFX control                      | Behaviour                                         |
|----------------|-------------------------------------|---------------------------------------------------|
| Glossary table | `TableView`                         | source term, target rendering, type, gender, lock |
| Lock toggle    | `ToggleSwitch`/checkbox column      | FR-GLOSS-03                                       |
| Add term       | `Button` → add-glossary-term dialog | FR-GLOSS-04                                       |

Requirements: FR-GLOSS-01..05. P6 reference: Names & style / Glossary — proposed.

## screen-translating {#screen-translating}

Purpose: drive and monitor the automatic run. The dashboard binds to the state mirror's observable surface, built from
the counts the engine emits (`07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`); it never polls.

| Control        | JavaFX control          | Behaviour                                                                                                                                                                                      |
|----------------|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| State banner   | icon + title + text     | Says where the run is; shows "Waiting for the model… m:ss" once one request has been outstanding for 10 s; a provider failure shows the error code and an "Open provider settings" button      |
| Progress       | `ProgressBar` + `Label` | Four count tiles — accepted / flagged / remaining / total — and "N of M segments processed" beside a bar. No "repaired" count and no chapter/chunk index: the engine emits neither             |
| Activity log   | `ListView`              | Appended entries for each accepted segment, each segment error and the run's milestones (stage start, pause, resume, finish); the vocabulary holds more kinds that no engine event produces yet. Bounded to the last 500, bundle-keyed/localized (`11_NOTIFICATIONS_AND_ERRORS.md#activity-log`)                                              |
| Controls       | `Button`s               | Start, Pause, Resume, Stop, New run (FR-RESUME-03); which are offered follows the state table below                                                                                            |

There is no throughput/ETA row and no in-flight source/target panel: the engine reports no rate and no in-flight text,
so the screen has nothing to show for them.

States (`#runState`, from `RunState`):

| State     | UI                                                                                                             | Controls offered            |
|-----------|----------------------------------------------------------------------------------------------------------------|-----------------------------|
| idle      | "Ready to translate"                                                                                           | Start                       |
| running   | Progress advancing; "Waiting for the model…" replaces the banner text after 10 s of one request                 | Pause, Stop                 |
| pausing   | A pause is pending; the request in flight is being cancelled and will be repeated on resume                     | Stop (Pause disabled)       |
| paused    | The engine is waiting                                                                                          | Resume, Stop                |
| stopping  | A stop is pending; the request in flight is being cancelled                                                     | nothing (Stop disabled)     |
| stopped   | Neutral info banner: a stopped run cannot be resumed; start a new run to translate from the beginning          | New run                     |
| completed | Every segment processed and the book written                                                                    | New run                     |
| failed    | The run ended before every segment was processed                                                               | New run                     |

A provider error, a refused start and a missing input (no book, no model) are **notices** (`RunNotice`) worded in the
same banner in place of the plain state, not states of their own; only a provider error offers "Open provider
settings" (see `11_NOTIFICATIONS_AND_ERRORS.md`).

**Pause / Stop behaviour.** Pause and Stop abort the model request that is in flight: no request is sent to the provider
after the button is pressed. A paused run that is resumed translates the interrupted segment again from the start. The
"Waiting for the model…" cue appears when one request has been outstanding for 10 seconds and is cleared by the next
decision, a pause or the end of the run. A pause pressed while the book is being written is ignored (the engine honours
none during export).

**Stop is terminal.** Stop ends the run in `stopped` — a neutral `info` state, **not** a failure: cancellation is never
an error dialog or `err` toast (`11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`). A stopped run **writes nothing**
and cannot be resumed; only a new run, which begins with the first segment, is offered. Resuming a stopped run needs
local storage (persistence) and arrives with it.

Requirements: FR-ALGO-01, FR-RESUME-03. P6 reference: Translating and each run state.

## screen-review {#screen-review}

Purpose: review flagged segments side by side.

| Control      | JavaFX control         | Behaviour                                                                      |
|--------------|------------------------|--------------------------------------------------------------------------------|
| Flagged list | `ListView`/`TableView` | e.g. "Flagged (3)", items like "ch5 · p12"                                     |
| Source pane  | read-only `TextArea`   | FR-REVIEW-C1                                                                   |
| Target pane  | editable `TextArea`    | FR-REVIEW-C2; typing marks the pane **dirty** and enables **Save edit**        |
| QA findings  | `Label`s/chips         | FR-REVIEW-C3                                                                   |
| Actions      | `Button`s              | Save edit / Accept / Revert to machine target / Retry / Retry-with-note / Skip |

**Edit vs accept (no silent discard).** The target pane starts holding the machine target. Typing enables **Save edit**;
saving records the human revision (state `REVISED`). **Accept** keeps the **machine target only when the pane is not
dirty** — if there are unsaved edits, Accept does not silently discard them (the user must Save edit first, or
explicitly revert). **Revert to machine target** discards the current pane edits and restores the machine target; from a
saved `REVISED` segment this is the `REVISED → (revert) → ACCEPTED` path — revert clears the revision and accepts the
machine target. No edit is ever dropped without an explicit Save-edit or Revert.

States: **flagged-populated** and **empty** ("Nothing flagged" empty state). Requirements: FR-REVIEW-01..07. P6
reference: Review queue (side-by-side) and Nothing-flagged.

## screen-export {#screen-export}

Purpose: report the translated book the run wrote. The screen **reports a finished file; it does not trigger one** —
export is the run's final stage (DD-30, FR-EXPORT-01), so there is no save-path field and no Export button here (the
path is chosen on the Book Brief, `#screen-book-brief`).

| Control                | JavaFX control                      | Behaviour                                                                                                                              |
|------------------------|-------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| Format                 | read-only `Label`                   | The book's original format (EPUB/FB2/MD/TXT) — export is same-format only; there is **no format chooser** (DD-30, FR-EXPORT-01)         |
| Written to             | `Label`                             | The path the run wrote                                                                                                                 |
| Counts                 | count tiles                         | Accepted and flagged                                                                                                                   |
| Open folder            | `Button`                            | Shows the written file in the system file manager: `open -R` on macOS, `explorer.exe /select,` on Windows, `xdg-open` on its folder on Linux |
| Also export            | disabled `CheckBox`es               | Glossary / bilingual copy / quality report (FR-EXPORT-05) — shown, tagged "not used yet", not produced by anything yet                  |
| Final consistency pass | disabled `ToggleSwitch`             | FR-EXPORT-06 — shown but disabled; nothing runs it yet                                                                                 |

States: **populated** (a run has written a book) and **empty** ("No translated book has been produced yet", with a hint
to finish a run; nothing to reveal). No export-complete dialog is shown.

Requirements: FR-EXPORT-01..06. Export always writes the book back in its original format; converting to another format
is out of scope (DD-30). P6 reference: Translated book ready / Export.

## screen-settings {#screen-settings}

Purpose: host the six settings tabs.

| Control | JavaFX control             | Behaviour                                                                  |
|---------|----------------------------|----------------------------------------------------------------------------|
| Tabs    | `TabPane` (`#settingsTab`) | Providers / Models / Generation / Appearance / Automation / Storage & logs |

Each tab's fields, defaults, and ranges are specified in `07_SETTINGS.md`. The Providers tab hosts the add/edit-provider
dialog trigger. P6 reference: Settings with each tab.

## dialogs {#dialogs}

All dialogs use `Dialog`/`Alert` in the modal host with scrim.

### dialog-welcome {#dialog-welcome}

"Welcome to BookLoom" first-run dialog. Control: `Dialog`. P6 reference: welcome dialog.

### dialog-add-edit-provider {#dialog-add-edit-provider}

Add/edit provider with endpoint, kind, auth, and credential-reference fields, plus the Test connection / models /
inference trio (FR-PROV-06). The kind selects the client implementation (Ollama → native client; everything else →
OpenAI-compatible). Model fields come from live discovery when the provider offers it, and always allow **manual
model-ID entry** (a free-text field used when the server has no discovery endpoint — DD-38, FR-MODEL-02). Two model
slots only: translator and judge/helper (no embedding). Controls: `Dialog` with `TextField`/`ComboBox`/editable
`ComboBox` and three test `Button`s each showing a per-stage result. P6 reference: Add provider dialog.

### dialog-provider-binding {#dialog-provider-binding}

Resume-time provider/model prompts protecting per-project consistency (DD-31, FR-PROV-09, FR-PROV-10). Two variants,
both `Alert` (confirmation): (a) **bound provider/model unavailable** — the project's bound provider or model could not
be verified; offer to fall back to the current default (only on confirm) or cancel; (b) **settings differ from the
project's last-used** — the current settings default differs from what this project last used; offer *apply the new
provider/model to this project* or *continue with the previously-used one* (default: continue). P6 reference:
Provider/model binding prompt.

### dialog-add-glossary-term {#dialog-add-glossary-term}

Add a glossary term (source, target, type, gender, lock). Control: `Dialog`. FR-GLOSS-04. P6 reference: Add glossary
term.

### dialog-retry-with-note {#dialog-retry-with-note}

Retry a flagged segment with a free-text instruction. Control: `Dialog` with a `TextArea` note field. FR-REVIEW-A2. P6
reference: Retry with note.

### dialog-confirm-delete {#dialog-confirm-delete}

Confirm deleting a provider (or similar destructive action). Control: `Alert` (confirmation). P6 reference: Delete
provider?.

### dialog-unsaved-changes {#dialog-unsaved-changes}

Warn on navigating away from unsaved edits. Control: `Alert` (confirmation). P6 reference: Unsaved changes.

### dialog-error-with-details {#dialog-error-with-details}

Error dialog with an expandable technical-details section surfacing the typed `AppError` safe details (FR-NOTIF-03,
FR-NOTIF-04). Control: `Alert` (error) with expandable content. P6 reference: Translation failed.

### dialog-export-complete {#dialog-export-complete}

**Not shipped.** The Export screen itself reports the written path and offers the action that shows the file in the
system file manager (`#screen-export`), so no separate confirmation dialog is shown. P6 reference: Export complete (the
mockup's dialog is a specimen only).

### dialog-about {#dialog-about}

About dialog (product name, version, MIT license). The version comes from the build-generated version resource
(`AppVersion` reader — DD-50, FR-UI-09): the tag-injected full version in release artifacts, **`dev`** in any
non-release build. Control: `Dialog`. P6 reference: BookLoom (about).

## toasts {#toasts}

Toasts (native token-styled JavaFX nodes stacked in the shell) come in four types — ok / info / warn / err — specified
with their triggers in `11_NOTIFICATIONS_AND_ERRORS.md#toasts`. FR-NOTIF-01. P6 reference: Notifications.

## banners {#banners}

Persistent banners in three severities — info / warn / err — e.g. language-mismatch (warn), DRM-blocked (err),
provider-error (err). FR-NOTIF-02. Specified in `11_NOTIFICATIONS_AND_ERRORS.md#banners`.

## empty-states {#empty-states}

Every list screen has an empty state (FR-NOTIF-05): Projects ("No projects yet"), Review ("Nothing flagged"), Providers
(no providers configured). P6 reference: each empty state in the mockup.

## control-mapping-summary {#control-mapping-summary}

| Mockup widget      | JavaFX control                    |
|--------------------|-----------------------------------|
| Segmented picker   | `SegmentedButton` / `ToggleGroup` |
| Side-by-side panes | two `TextArea`                    |
| Data table         | `TableView`                       |
| Chapter tree       | `TreeView`                        |
| Toggle             | `ToggleSwitch`                    |
| Tabs               | `TabPane`                         |
| Dialog / alert     | `Dialog` / `Alert`                |
| Toast              | Native token-styled nodes         |
| Icon               | Ikonli                            |
