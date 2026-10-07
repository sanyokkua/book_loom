**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`), QA, UX **Last Updated:** 2026-09-27
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
| Title bar          | `HBox`                            | Product name, then the run status (only while a run exists, `#title-bar-run-status`), then the theme toggle                                          |
| Left navigation    | `ScrollPane` around a `VBox`      | Workflow (six numbered steps, `#workflow-navigation`) and Application groups; 236 px wide, scrolls vertically when the window is short               |
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

### Title bar run status {#title-bar-run-status}

While the current project has no run, the title bar shows only the product name and the theme toggle. While a run
exists — from Start until the project's book is replaced by a different import — the title bar shows, between the
product name and the theme toggle: the file name; the state text (`Progress 78%` while running, `Paused at 78%`,
`Stopped at 78%`, `Provider error`, `Finished`, or `Failed at 78%`); the run's quality dial and review mode joined by ` · ` (`Balanced · Unattended`; `#shell-run-mode`, with a hover
explanation, absent when the start names none); and the elapsed time and the time left. It
offers **Pause** while running, **Resume** while paused, stopped or in a provider error, and **no control** once the
run has finished or failed. The title bar's control does exactly what the Translating screen's control of the same
name does — pressing either one acts on the same run.

### Start and Resume {#start-and-resume}

**Start** begins a new run at the first pending segment. It is offered only by the Names & style screen's
`Start translation` action (`#screen-names-and-style`) and by the Translating screen's own start control
(`#screen-translating`), and only when the project has no run yet, its last run completed with pending segments left
(for example after an "Also translate" switch was turned on once the run had completed), or its last run failed.
While a run can be resumed instead, `Start translation` starts nothing — it only navigates to Translating, where
Resume is offered.

**Resume** continues the book's one run. From **Paused** or a **provider error** it continues in place, at the
segment it left off on. From **Stopped** it begins a new run at the first pending segment, because a stopped run
re-enters at the first pending segment rather than mid-chunk.

### Workflow navigation {#workflow-navigation}

The left navigation's **WORKFLOW** group numbers its six steps — **1 Import, 2 Book Brief, 3 Structure, 4 Names &
style, 5 Translating, 6 Export** — each showing a done mark once its work is complete, under an uppercase heading.
The **APPLICATION** group (also an uppercase heading) lists Settings and Projects. Only **Projects** is an inert
entry in this build: it is listed but has no screen yet, so "Continue" skips it. Every workflow step — Import, Book
Brief, Structure, Names & style, Translating, Export — has a live screen; the Review entry from the mockup is gone,
because review is a panel inside Translating, not a step of its own.

### Navigation footer {#navigation-footer}

The navigation column's footer reads the chosen provider's display name and model id, for example `Ollama · gemma4:26b`, or `No model chosen` when none is configured. It shows **no readiness dot** — readiness is not tracked in this build.

P6 reference: match the shell chrome, nav grouping, and theme toggle in the mockup, except for the departures named
below. The mockup's "Design reference" group (component library, dialogs and alerts, notifications) is a specimen
sheet for the designer and is not a navigation group of the application.

### Departures from the mockup {#shell-departures}

This build departs from the mockup on purpose, each for a stated reason:

- **The separate Review step and its navigation entry** — review is a panel inside Translating, not a screen or a
  step of its own (owner decision D-8, ADR-0036).
- **The toolbar's Pause and `Review (3)`** — the run control lives in the title bar; review lives on the Translating
  screen.
- **The title bar's always-shown Resume** — the run status, Resume included, appears only while a run exists.
- **"Back to projects"** — Projects is not built in this release.
- **The footer's readiness dot and the Book Brief's `ready` model badge** — both need a verification state the
  application does not keep.
- **The import card's `valid` badge** — nothing validates a book beyond opening it.
- **The import screen's "Source language override" dropdown** — the source language is chosen on the Book Brief, not
  on Import (ADR-0037).
- **The `primes system prompt` and `Slower · Max-quality` notes** — notes for the mockup's reader; the consistency
  pass runs on any quality dial setting chosen from Export.
- **The per-node formatting pills on Structure** — formatting is reported for the whole book in the statistics card,
  not per node.
- **Toasts naming no requirement** — the mockup's toast text is illustrative; this document is the binding toast
  contract.
- **The preview-state switchers** — every screen shows its own live state, not a designer-only switcher between
  states.
- **The design-reference group** — a specimen sheet for the designer, not a navigation group of the application.
- **"EPUBCheck passed"** — the round-trip and resource-id checks this build runs are its own checks, not EPUBCheck's.

### Hover explanations {#hover-explanations}

Every button, toggle, field, combo, segmented choice and table header a person can operate explains itself in a
tooltip that appears after 400 ms, wraps at 320 px and stays long enough to read. The text is a `<label key>.tip`
catalogue entry in both languages, set once through `Tips`, and it is also the control's accessible help. The bubble is
painted with the title bar's colour pair, so it is dark on the light block and stays readable on the dark one. A
disabled control shows none, because JavaFX delivers it no pointer events. The Names & style subtitle lists the three
things to do: fill Target, set Type and Gender, lock what must never change.

## screen-projects {#screen-projects}

Purpose: list existing projects and start a new one.

| Control            | JavaFX control          | Behaviour                            |
|---------------------|--------------------------|----------------------------------------|
| Project list       | `TableView` / card list | Rows open a project; supports resume |
| New project action | `Button`                | Opens Import                         |

States: **populated** (project list) and **empty** (`#empty-state-projects`: "No projects yet" with a call to action).
Requirements: FR-NOTIF-05. P6 reference: Projects and Projects-empty.

## screen-import {#screen-import}

Purpose: open a book and confirm the detected-file card.

| Control                 | JavaFX control                       | Behaviour                                                                                                                                                       |
|--------------------------|----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Drop zone / file picker | drag-drop target + `FileChooser`      | FR-IMPORT-02                                                                                                                                                    |
| Cover                    | `ImageView` or a neutral placeholder  | Shows the book's cover image when the format carries one, else a neutral placeholder                                                                           |
| Detected file card       | card of key/value rows (`Label`s)     | Rows, in order: file; format with its version (e.g. `EPUB 2.0`); `Title · Author`; declared language written `English (en)`; chapters · `~words`; images · fonts; DRM `none` |
| Continue / Cancel        | `Button`s                              | `Continue to Book Brief` leads to the Book Brief; `Cancel` discards the import                                                                                  |

States (`#importState`), all decided by the inspection the application performs on open (ADR-0039):

| State                  | Trigger                                                                                                          | UI                                                                                                                              |
|--------------------------|---------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| idle                    | Nothing chosen yet                                                                                              | Drop zone                                                                                                                       |
| opening                 | A book has been handed to the port                                                                              | Progress line naming the file                                                                                                   |
| detected                | Supported, no DRM, no language disagreement                                                                     | Detected-file card + Continue                                                                                                   |
| language-mismatch       | An EPUB's own language declarations disagree (its package language against its content documents' majority), or the book declares a language the application does not recognize | Warning banner alongside the detected-file card                                                                                 |
| refused (DRM)           | DRM detected (EC-DRM-1)                                                                                         | Banner `This book is DRM-protected.`, naming the file and the encryption scheme when known (e.g. `ZIP encryption` for a zipped FB2); `Import blocked`; `Choose another file` |
| refused (unsupported)   | An unsupported or corrupt file type (FR-IMPORT-05)                                                              | Banner `Couldn't read this file.`, naming the detected type as `not supported`, plus the supported-formats hint                 |

There is no "Preview state" switcher on this screen — the states above are the screen's real behaviour, not a
designer-only selector — and no detected-from-text language row: the source language is chosen on the Book Brief, not
detected on Import (ADR-0037).

Requirements: FR-IMPORT-01..07. P6 reference: Import and each import state.

## screen-book-brief {#screen-book-brief}

Purpose: capture the Book Brief.

| Section              | Control                                                                                                    | JavaFX control                                            | Requirement              |
|-----------------------|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|--------------------------|
| Languages             | source and target, both editable and searchable over the same 34 languages                                   | two searchable `ComboBox`es                                   | FR-BRIEF-01              |
| Tone & style          | genre (searchable free-text field over the forty genres), register, voice/era, audience                       | searchable `TextField`/`ComboBox`, `ComboBox`s                | FR-BRIEF-02, FR-BRIEF-03 |
| Translation policies  | name, foreign-passage (keep-as-is / translate / translate+note), footnote, unit policies, faithful↔natural slider | `SegmentedButton`/`ToggleGroup`, `ToggleSwitch`, `Slider`      | FR-BRIEF-04..07          |
| Also translate        | ToC/navigation labels (also governs page titles), image alt-text, book metadata title/author (also governs the description), frontmatter values | four `ToggleSwitch`es                                          | FR-BRIEF-04, FR-DOC      |
| Quality vs speed      | Fast/Balanced/Max, three hint lines, and the model row with a `change` link                                    | `SegmentedButton`/`ToggleGroup`, `Label`s, `Hyperlink`         | FR-BRIEF-08              |

Both the source and the target language are editable and searchable over the same 34-language list; the source is
preselected from the normalized declaration on the detected-file card or, when an EPUB's content documents disagree,
from their majority. Choosing the same language for both is refused with `The source and target languages are the
same.` There is no destination card on this screen — the save path and overwrite switch live on the Export screen
(`#screen-export`).

Every card on this screen is **live**: Tone & style, Translation policies, Also translate and Quality vs speed all
feed the run — none are drawn disabled. The faithful↔natural slider defaults to **55**. The **"Also translate"
toggle group** controls which auxiliary text units the run translates: **ToC/navigation labels** (on by default;
also governs the translation of page titles), **image alt-text** (on), **book metadata title/author** (on; also
governs the translation of the description), and **frontmatter values** (off). The quality dial shows its three hint
lines and a model row naming the chosen provider and model with a `change` link — there is no readiness badge.
Segmented-button labels **wrap** rather than truncate at narrow widths. There is no review-mode control on this
screen. Opened with no book, the screen shows a **no-book state** ("No book is open" with an action that leads to
Import) instead of the form.

P6 reference: Book Brief with its sections, including the "Also translate" group.

## screen-structure {#screen-structure}

Purpose: show the parsed reading order and the book's structural health, read-only.

| Control                     | JavaFX control            | Behaviour                                                                                                                        |
|-------------------------------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| Structure tree                | `TreeView` (virtualized)     | Nested, titled from the book's own navigation (its ToC/nav document); a count pill per node shows its segment count; a node with no title reads `Untitled` |
| Statistics card                | `Label`s / stat tiles        | Eight rows beside the tree: total segments, chapters/sections, words, images, fonts, footnotes, cross-references, auxiliary units |
| Round-trip / resource checks   | banner(s)                    | Background checks run beside the structure; a failed round-trip shows `Round-trip check failed — structure not preserved`; missing resource ids show `IDs missing after the round trip:` followed by their list. A failed check never blocks Continue |
| Oversized-segment warning      | banner                       | Warns when a segment exceeds the chunking limit                                                                                  |
| Back / Continue                | `Button`s                    | Back returns to the Book Brief; Continue leads to Names & style                                                                  |

With no book open the screen shows the same no-book state as the Book Brief.

Requirements: FR-DOC-01, FR-DOC-08. P6 reference: Structure found / Reading order.

## screen-names-and-style {#screen-names-and-style}

Purpose: review and edit the glossary before or during a run.

| Control              | JavaFX control                       | Behaviour                                                                                          |
|------------------------|-----------------------------------------|--------------------------------------------------------------------------------------------------|
| Glossary table         | `TableView`                             | Columns: Source term, Type, Target, Gender, Locked, Check; Source term, Type, Gender, Locked and Check sort by header, ties by term. Check shows a "No target" chip for an empty target and a "Likely junk" chip for a term the junk rules mark, and sorts the likeliest junk first. Start translation asks "N entries have no target — the model will choose" (listing them) when any entry has no target |
| Search field           | `TextField`                             | Shows only rows whose term or target contains the text; Escape clears it                           |
| Model-scan button      | `Button`                                | Runs a model-assisted name scan in addition to the deterministic scan                             |
| Review with model      | `Button`                                | Asks the model which open rows are names; removes the rest and fills an unset type or gender       |
| Stop                   | `Button`, shown while a model action runs | Interrupts the model scan or review; nothing is written; a waiting line names the request and attempt |
| Add term               | `Button` → add-glossary-term dialog     | FR-GLOSS-04; refuses locking a term that has no target                                             |
| CSV import / export    | `Button`s + `FileChooser`               | Import refuses locking a row that has no target; export writes the current table                  |
| `Start translation`    | `Button`                                | Starts a new run at the first pending segment when Start is offered (`#start-and-resume`)          |
| Info banner            | `Label`                                 | `Skip this and the app builds names on the fly as it translates.`                                  |

A **deterministic scan** runs automatically when the screen opens, proposing entries from the book's own text; these
proposals start **unlocked** (design.md D10). Locking is allowed only for an entry that has a target — the table, Add
term, and CSV import each refuse a lock with no target with `A locked term needs a target.` The info banner is true
either way: a run never depends on this screen — it adds the names it meets at each body unit's end as it translates
(design.md D5). With no book open the screen shows a **no-book state**: it says no book is open, routes to Import,
and shows no table, no scan, and no `Start translation`.

Requirements: FR-GLOSS-01..05. P6 reference: Names & style / Glossary — proposed.

## screen-translating {#screen-translating}

Purpose: drive and monitor the automatic run, and review flagged segments without leaving the screen. The dashboard
binds to the state mirror's observable surface, built from the events the engine emits
(`07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`); it never polls.

Subtitle: `A stopped run resumes where it left off — until the application closes.`

| Control               | JavaFX control            | Behaviour                                                                                                                                       |
|-------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Progress line           | `Label` + `ProgressBar`      | Percentage · `Chapter k of n` (the body-unit position; the auxiliary unit is never counted as a chapter) · chunk `k/K`                          |
| Rate / time             | `Label`s                     | Time left and tokens per second, from the recent drafts                                                                                        |
| Count tiles             | four stat tiles              | auto-accepted / repaired / flagged / remaining — `remaining` leaves out segments kept as source by choice                                       |
| Current chunk (live)    | two-row panel                | Row 1: the segment decided last, with its judge and path badges. Row 2: the segment in progress — `waiting for the model…` until its draft arrives, then marked `awaiting judge` while the judge runs |
| Activity log            | `ListView` (monospace)       | Tagged entries — `ok`, `fix`, `mem`, `sum`, `retry`, `err`, `info` — naming segments by locator (e.g. `ch7 · p42`); bounded to the last 500       |
| Controls                | `Button`s                    | Start, Pause, Resume, Stop, `Review flagged (n)`; which are offered follows the state table below                                               |

The start toast reads `Translation started` / `Keep the application open — progress is kept only until it closes.`

States (`#runState`):

| State           | Shows                                                                                                                                                                        | Offers                                                            |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| idle              | A ready card: book, model, review mode, quality dial, pending count; a missing model or missing brief field is named in place                                              | Start (its own)                                                       |
| running           | Progress advancing, the live panel, the activity log                                                                                                                        | Pause, Stop, `Review flagged (n)`                                     |
| paused            | Banner `Paused. Progress is kept until the application closes. Resume any time — it continues at chunk 41/66.`                                                             | Resume, Stop, `Review flagged (n)`                                    |
| stopped           | Banner `Run stopped. Progress is kept until the application closes. Resume any time — it re-enters at the first pending segment; flagged segments wait in the review panel.` | Resume, `Review flagged (n)`                                          |
| provider error    | Auto-paused, naming the failure's error code                                                                                                                                | `Retry now` (= Resume), `Open provider settings`, `Stay paused`       |
| completed         | Auto-accepted, repaired-and-accepted, flagged, kept-as-is (numbers, symbols) and kept-as-source counts                                                                      | Continue to Export, `Review flagged (n)`; Start only while pending segments remain (e.g. after an "Also translate" switch is turned on once the run has completed) |
| failed            | A blocking dialog: `Decided segments are kept until the application closes.`, then the outcome so far                                                                      | Start (begins a new run at the first pending segment)                 |

The **review panel** lives inside this screen (`#screen-review`) rather than on a separate screen or nav entry.

### Review panel {#screen-review}

Purpose: review flagged segments (and, on request, every segment) without leaving the Translating screen.

| Control            | JavaFX control              | Behaviour                                                                                                                        |
|-----------------------|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| Filter chips           | `ToggleGroup` of chips          | `all`, `names`, `omissions`, `foreign · kept`, and `All segments` — the one list that also shows segments kept as source, each marked `kept as source` |
| Flagged list           | `ListView`/`TableView`          | Items by locator (e.g. `ch5 · p12`) with a main-finding badge: the highest-severity finding, ties broken `name` > `wrong lang?` > `omission` > `low score` |
| Compare panes          | two `TextArea`                  | Source (read-only) and target (editable)                                                                                          |
| Readable view          | two `TextFlow`s (`#review-source-preview`, `#review-target-preview`) | Under the panes: each `⟦gN⟧` token as a chip (`g0`), and the words a finding quotes marked in the target; shown only when the text holds a token or a found quote; the target follows the editor |
| Findings / context     | `Label`s/chips                  | The segment's QA findings, each with a kind badge (`name`, `wrong lang?`, `omission`) when its kind maps to one, and a line of surrounding context |
| Actions                | `Button`s                       | Save edit / Accept / Revert to machine target / Retry / Retry with note / Skip                                                    |

**Target editor.** The editable pane shows, in order of preference: the saved human edit, else the machine target,
else the source (when the segment has neither) — all in **masked** form: `⟦gN⟧` tokens stay visible and a locked
name shows as its locked rendering. Saving an edit that deletes or reorders a token is refused, naming the reason; a
revised segment can be edited again.

**Edit vs accept.** Typing in the target pane disables **Accept** until the edit is either **Save**d or
**Revert**ed — editing disables Accept until Save edit or Revert, so Accept never silently discards an unsaved edit.
Accept moves a `FLAGGED` segment to `ACCEPTED` and confirms an already-`ACCEPTED` segment; it is refused on `PENDING`
or `REVISED`, and on a segment with no machine target (`There is no machine translation to accept — edit it or
retry.`).

**Retry.** Retry (with or without a note) is allowed whenever no run of the project is currently running; it is
refused as busy while one is. While a retry is in flight, Resume and the other review actions are disabled.

This panel has **no navigation entry of its own** — it is reached only from the Translating screen.

Requirements: FR-ALGO-01, FR-RESUME-03, FR-REVIEW-01..07. P6 reference: Translating, each run state, and Review queue
(side-by-side).

## screen-export {#screen-export}

Purpose: choose the destination and export the translated book.

| Control                   | JavaFX control                | Behaviour                                                                                                                                |
|------------------------------|----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| Save to / Browse             | `TextField` + `FileChooser`      | Output path, defaulted from the source name and target language, with the shared naming rule and an "already exists" warning when overwrite is off |
| Overwrite                    | `ToggleSwitch`                    | Allows writing over an existing file at the chosen path                                                                                  |
| Format                       | read-only `Label`                 | The book's original format (EPUB/FB2/MD/TXT) — export is same-format only; there is **no format chooser** (DD-30, FR-EXPORT-01)          |
| Also export (side files)     | three `CheckBox`es (live)         | Glossary / bilingual copy / quality report (FR-EXPORT-05) — each produces its file when checked                                          |
| Final consistency pass       | `ToggleSwitch` (live)             | FR-EXPORT-06 — runs the consistency pass on export at any quality dial setting                                                           |
| Checks                       | banner(s)                         | The round-trip and resource checks carried over from Structure, re-shown here before writing                                             |
| Counts                       | stat tiles                        | Accepted, flagged, pending, and kept-as-source, named apart                                                                              |
| `Export book`                | `Button`                          | Writes the book; **unavailable while a run of this project is translating**                                                              |
| Open folder                  | `Button`                          | Shows the written file in the system file manager: `open -R` on macOS, `explorer.exe /select,` on Windows (an exit code of 1 still counts as success), `xdg-open` on its folder on Linux |

The partial-export statement names **pending segments** (not yet decided), **segments kept as source by choice** and
**segments kept as is** (a chapter number, a scene break — nothing to translate, decided with no model call) apart, so a
partial export never describes every remaining segment as the same kind of gap. The
**export-complete dialog** (`#dialog-export-complete`) is the export's **only** success notice — no `ok` toast
duplicates it.

States: **populated** (a book is open, ready to export) and **empty** ("No book is open", with a hint to Import;
nothing to reveal).

Requirements: FR-EXPORT-01..06. Export always writes the book back in its original format; converting to another format
is out of scope (DD-30). P6 reference: Translated book ready / Export.

## screen-settings {#screen-settings}

Purpose: host the six settings tabs.

| Control | JavaFX control             | Behaviour                                                                  |
|---------|------------------------------|------------------------------------------------------------------------------|
| Tabs    | `TabPane` (`#settingsTab`)   | Providers / Models / Generation / Appearance / Automation / Storage & logs |

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

**Ships in this build.** Add a glossary term: source, target, type, gender, lock. The dialog refuses a duplicate
source term, and refuses locking a term that has no target (`A locked term needs a target.`). Control: `Dialog`.
FR-GLOSS-04. P6 reference: Add glossary term.

### dialog-retry-with-note {#dialog-retry-with-note}

**Ships in this build.** Retry a flagged segment with a free-text instruction, plus a checkbox offering **lower
temperature for this retry**. Control: `Dialog` with a `TextArea` note field. FR-REVIEW-A2. P6 reference: Retry with
note.

### dialog-confirm-delete {#dialog-confirm-delete}

Confirm deleting a provider (or similar destructive action). Control: `Alert` (confirmation). P6 reference: Delete
provider?.

### dialog-unsaved-changes {#dialog-unsaved-changes}

Warn on navigating away from unsaved edits. Control: `Alert` (confirmation). P6 reference: Unsaved changes.

### dialog-error-with-details {#dialog-error-with-details}

Error dialog with an expandable technical-details section surfacing the typed `AppError` safe details (FR-NOTIF-03,
FR-NOTIF-04). Control: `Alert` (error) with expandable content. P6 reference: Translation failed.

### dialog-export-complete {#dialog-export-complete}

**Ships in this build.** Shown once the run's export stage finishes writing the book: the written path, the format,
and an `Open folder` action that shows the file in the system file manager. It is the export's **only** success
notice — no `ok` toast duplicates it. Control: `Dialog`. P6 reference: Export complete.

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

Every list screen has an empty state (FR-NOTIF-05): Projects ("No projects yet"), the Translating screen's review
panel ("Nothing flagged"), Providers (no providers configured), and the Names & style glossary and Recurring terms tables ("No names yet — run Model scan or add a term"; "No name matches the search" while a search hides every row). P6 reference: each empty state in the mockup.

## control-mapping-summary {#control-mapping-summary}

| Mockup widget      | JavaFX control                    |
|---------------------|--------------------------------------|
| Segmented picker   | `SegmentedButton` / `ToggleGroup` |
| Side-by-side panes | two `TextArea`                    |
| Data table         | `TableView`                       |
| Chapter tree       | `TreeView`                        |
| Toggle             | `ToggleSwitch`                    |
| Tabs               | `TabPane`                         |
| Dialog / alert     | `Dialog` / `Alert`                |
| Toast              | Native token-styled nodes         |
| Icon               | Ikonli                            |
