**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`), QA, UX **Last Updated:** 2026-07-18
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

The application shell has a title bar, a left navigation grouped into Workflow / App / Reference, a breadcrumb, a
toolbar-actions area, a content host, a modal host with scrim, and a toast host.

| Element            | JavaFX control                    | Notes                                                                                                                                                |
|--------------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Window / stage     | `Stage` + root `BorderPane`       | `.root` carries theme tokens                                                                                                                         |
| Left navigation    | `ListView` / custom nav list      | Workflow, App, Reference groups                                                                                                                      |
| Breadcrumb         | `Label`/`Breadcrumbs`-style bar   | Reflects current screen                                                                                                                              |
| Toolbar actions    | `ToolBar` / `HBox` of `Button`    | Context actions per screen                                                                                                                           |
| Theme toggle       | `ToggleButton`                    | **2-state light↔dark quick-toggle** only; the tri-state selector including `system` lives in Settings → Appearance (`07_SETTINGS.md#appearance-tab`) |
| Content host       | `StackPane`                       | Hosts the active screen                                                                                                                              |
| Modal host + scrim | `StackPane` overlay               | For `Dialog`/`Alert`                                                                                                                                 |
| Toast host         | ControlsFX `Notifications` region | ok/info/warn/err                                                                                                                                     |

P6 reference: match the shell chrome, nav grouping, and theme toggle in the mockup.

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

| Control                 | JavaFX control                         | Behaviour                                                      |
|-------------------------|----------------------------------------|----------------------------------------------------------------|
| Drop zone / file picker | drag-drop target + `FileChooser`       | FR-IMPORT-02                                                   |
| Detected file card      | card of `Label`s + `ImageView` (cover) | format, source language, counts, cover/metadata (FR-IMPORT-06) |

States (`#importState`):
| State | Trigger | UI | |---|---|---| | detected-ok | Supported, no DRM, language detected | Detected-file card +
Continue | | language-mismatch | Declared ≠ detected language (EC-LANG-1) | Warning banner + confirm control | |
DRM-blocked | DRM detected (EC-DRM-1) | Error banner; import refused | | unsupported | Unsupported/corrupt file
(FR-IMPORT-05) | Error banner; import refused |

Requirements: FR-IMPORT-01..07. P6 reference: Import and each import state.

## screen-book-brief {#screen-book-brief}

Purpose: capture the Book Brief.

| Section              | Control                                                                                  | JavaFX control                                  | Requirement              |
|----------------------|------------------------------------------------------------------------------------------|-------------------------------------------------|--------------------------|
| Languages            | source/target pickers                                                                    | `ComboBox`                                      | FR-BRIEF-01              |
| Tone & style         | genre, register, voice/era, audience; faithful↔natural                                   | `ComboBox`, `TextField`, `Slider`               | FR-BRIEF-02, FR-BRIEF-03 |
| Translation policies | name, foreign-passage (keep-as-is / translate / translate+note), footnote, unit policies | `SegmentedButton`/`ToggleGroup`, `ToggleSwitch` | FR-BRIEF-04..07          |
| Also translate       | ToC/navigation labels, image alt-text, book metadata title/author, frontmatter values    | four `ToggleSwitch`es                           | FR-BRIEF-04, FR-DOC      |
| Quality vs speed     | Fast/Balanced/Max                                                                        | `SegmentedButton`/`ToggleGroup`                 | FR-BRIEF-08              |

The **"Also translate" toggle group** controls which auxiliary text units the run translates (modelled as the synthetic
metadata unit — `NAV_LABEL`, `ALT`, `METADATA_TITLE`/`METADATA_AUTHOR`, `FRONTMATTER_VALUE`). Defaults: **ToC/navigation
labels on, image alt-text on, book metadata title/author on, frontmatter values off**.

P6 reference: Book Brief with its sections, including the "Also translate" group.

## screen-structure {#screen-structure}

Purpose: show the detected reading order and chapter tree; confirm translate-vs-preserve.

| Control                      | JavaFX control | Behaviour                      |
|------------------------------|----------------|--------------------------------|
| Reading order / chapter tree | `TreeView`     | Reflects spine/structure order |
| Segment counts               | `Label`s       | Per chapter/unit               |

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

Purpose: drive and monitor the automatic run. The dashboard binds to the fixed `JobProgress` observable surface
(`07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`); it never polls.

| Control                | JavaFX control             | Behaviour                                                                                                                                       |
|------------------------|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Progress               | `ProgressBar` + `Label`    | Bound to `JobProgress`: auto-accepted / repaired / flagged / remaining counts, chapter/chunk index                                              |
| In-flight panel (live) | read-only `TextArea`/panel | Live in-flight source, target, and judge-score (whole target, not token streaming — D8)                                                         |
| Throughput / ETA       | `Label`s                   | tok/s and ETA from `JobProgress` (locale-formatted)                                                                                             |
| Activity log           | `ListView`                 | Appended `ok/fix/mem/sum/retry/err/info` entries, bounded to the last N, bundle-keyed/localized (`11_NOTIFICATIONS_AND_ERRORS.md#activity-log`) |
| Pause / Resume         | `Button`                   | FR-RESUME-03                                                                                                                                    |
| Stop                   | `Button`                   | Stops the run at the next chunk boundary into the resumable `stopped` state                                                                     |

States (`#runState`):
| State | UI | |---|---| | running | Progress advancing; pause and stop available | | paused | Halted at chunk boundary;
resume available | | stopped | User-stopped and **resumable**; the run is checkpointed, no error is shown; offers
Resume / leave-to-Projects | | provider-error | Provider-error banner; retry/settings actions (see
`11_NOTIFICATIONS_AND_ERRORS.md`) |

**Stop / cancel behaviour.** Stop halts at the next chunk boundary and moves the run to `stopped` — a resumable,
checkpointed state, **not** a failure: cancellation is surfaced as a neutral `info` state and never as an error dialog
or `err` toast (`11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`). **Post-stop navigation:** the user may resume in
place (which re-enters at the **first PENDING** segment; FLAGGED segments are terminal-for-run and go to the review
queue rather than auto-resuming) or leave to Projects, where the project shows a resume affordance. The stop/cancel
functional requirement itself is owned elsewhere (FR-RESUME — noted for the lead).

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

Purpose: export the translated book.

| Control                | JavaFX control              | Behaviour                                                                                                                             |
|------------------------|-----------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| Format                 | read-only `Label`           | The book's original format (EPUB/FB2/MD/TXT) — export is same-format only; there is **no format chooser** (DD-30, FR-EXPORT-01)       |
| Save path              | `TextField` + `FileChooser` | FR-EXPORT-04 (default extension = the original format)                                                                                |
| Also export            | `ToggleSwitch`es            | optional auxiliary side files — glossary / bilingual review / quality report (FR-EXPORT-05); these never replace the same-format book |
| Final consistency pass | `ToggleSwitch`              | FR-EXPORT-06                                                                                                                          |
| Export                 | `Button`                    | Triggers export → export-complete dialog                                                                                              |

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

Confirm successful export with the output path and open-folder action. Control: `Dialog`. FR-EXPORT-04. P6 reference:
Export complete.

### dialog-about {#dialog-about}

About dialog (product name, version, MIT license). The version comes from the build-generated version resource
(`AppVersion` reader — DD-50, FR-UI-09): the tag-injected full version in release artifacts, **`dev`** in any
non-release build. Control: `Dialog`. P6 reference: BookLoom (about).

## toasts {#toasts}

Toasts (ControlsFX `Notifications`) come in four types — ok / info / warn / err — specified with their triggers in
`11_NOTIFICATIONS_AND_ERRORS.md#toasts`. FR-NOTIF-01. P6 reference: Notifications.

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
| Toast              | ControlsFX `Notifications`        |
| Icon               | Ikonli                            |
