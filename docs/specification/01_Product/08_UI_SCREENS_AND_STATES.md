**Status:** Final (built clauses) / Planned (clauses marked so) **Owner:** architect **Audience:** architect, engineering (`:ui`), QA, UX **Last Updated:** 2026-10-19
**Cross-references:** `docs/specification/mockups/ui-mockup.html`, `docs/specification/mockups/README.md`,
`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`, `docs/specification/01_Product/09_THEMING.md`,
`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md`,
`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`

# UI Screens and States

This is the binding UI specification. `docs/specification/mockups/ui-mockup.html` is the visual source of truth (DD-28)
and `docs/specification/mockups/README.md` (cited below as "the mockup README") describes every screen, dialog, global
pattern (G1–G12), layout rule (L1–L10), word and run state of the target UI, and **how an agent builds and checks the JavaFX UI against the mockup** (its section "For AI agents": render the mockup in a browser, capture the app in the same state and size, compare screenshots, never port mockup-only elements); this document enumerates every screen,
state, dialog, toast, banner, and empty state, and maps each control to a concrete JavaFX control. Every UI story cites
the mockup via the P6 visual-reference pattern. **Binding acceptance is
scoped:** the mockup's **structure, looked-up tokens, and geometry** are gated in CI (headless TestFX/Monocle —
`04_Build_and_Release/06_TESTING_STRATEGY.md#ui-conformance`); **pixel fidelity** is a **nightly/on-demand**
tolerant-diff check, not a merge gate (`#visual-validation`). This document does not restate pixel values — those live
in the mockup. It realizes `FR-UI-*`.

**Status convention.** The mockup README is the target of the next change, *finish the UI and keep progress in SQLite*
(proposal `2026-10-19-ui-finalization-and-persistence.md`). A clause marked **Planned — UI finalization + SQLite**
(short form **Planned**) is that target and is **not built**; a clause marked **Built today** describes the app as it
runs now and is replaced by the Planned clause beside it when the change ships; an unmarked clause is built and
unchanged by the change. Nothing is saved across a restart today (ADR-0034); everything that needs the project store is
Planned.

## shell-and-navigation {#shell-and-navigation}

The application shell has a title bar, a left navigation grouped into Workflow / Application, a breadcrumb, a
toolbar-actions area, a content host, a busy (work) layer, a modal host with scrim, and a toast host. **Planned:** a
pinned banner slot between the toolbar and the content (`#shell-banners`, L3).

| Element            | JavaFX control                    | Notes                                                                                                                                                |
|--------------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Window / stage     | `Stage` + root `BorderPane`       | `.root` carries theme tokens                                                                                                                         |
| Title bar          | `HBox`                            | Built today: product name, then the run status (only while a run exists, `#title-bar-run-status`), then the theme toggle. Planned: see `#title-bar-run-status` |
| Left navigation    | `ScrollPane` around a `VBox`      | Workflow (Projects, then five numbered steps, `#workflow-navigation`) and Application groups; 236 px wide (Planned: 200 px when the window is narrower than 1200 px, L9; no icon rail), scrolls vertically when the window is short |
| Breadcrumb         | `Label`/`Breadcrumbs`-style bar   | Reflects current screen; Planned: "Workflow / Step · step n of 5" (`#shell-breadcrumb`)                                                              |
| Toolbar actions    | `ToolBar` / `HBox` of `Button`    | The one action bar (L2): each screen's main and run actions, always visible (`StepFooter` in `#shell-actions`)                                       |
| Banner slot        | `VBox` (`#shell-banners`)         | **Planned.** Pinned above the scrolling content; holds the run banners and the hidden-work banner (L3)                                               |
| Theme toggle       | `ToggleButton`                    | **2-state light↔dark quick-toggle** only; the tri-state selector including `system` lives in Settings → Appearance (`07_SETTINGS.md#appearance-tab`) |
| Content host       | `ScrollPane` around a `StackPane` | Hosts the active screen; scrolls when the screen is taller or wider than the window, and fits width and height so a growing list still fills it       |
| Work layer + scrim | `StackPane` overlay (`BusyHost`)  | Holds the progress dialogs of model work (`#busy-card`, G4), below the modal host                                                                    |
| Modal host + scrim | `StackPane` overlay (`ModalHost`) | For `Dialog`/`Alert`; above the work layer (G1)                                                                                                      |
| Toast host         | Native token-styled JavaFX nodes  | ok/info/warn/err, stacked in-shell (`#shell-toast-host`); Planned: bottom-right (G2, see `#toasts`)                                                  |

Smallest window: the stage's minimum is 960 × 640 as the outer window, title bar and borders included (the window opens
at 1024 × 700); the content area inside it is smaller by that decoration — about 944 wide with 8 px borders and about 612
high with a 28 px title bar. At that size every screen fits the width of the content area (proved for 944 × 600) and
every part stays reachable, scrolling vertically where the screen is taller: the navigation column and the content host
scroll rather than clip, a count tile (`.stat`) keeps its 120 px minimum width and never shows an ellipsis for its caption, and a progress bar keeps its 9 px height (a progress bar's
own minimum height is zero, so the height is fixed in the stylesheet). The layout rules L1–L10 are in
`#layout-and-sizes`.

### Title bar run status {#title-bar-run-status}

**Built today.** While the current project has no run, the title bar shows only the product name and the theme toggle. While a run
exists — from Start until the project's book is replaced by a different import — the title bar shows, between the
product name and the theme toggle: the file name; the state text (`Progress 78%` while running, `Paused at 78%`,
`Stopped at 78%`, `Provider error`, `Finished`, or `Failed at 78%`); the run's quality dial and review mode joined by ` · ` (`Balanced · Unattended`; `#shell-run-mode`, with a hover
explanation, absent when the start names none); and the elapsed time and the time left. It
offers **Pause** while running, **Resume** while paused, stopped or in a provider error, and **no control** once the
run has finished or failed. The title bar's control does exactly what the Translating screen's control of the same
name does — pressing either one acts on the same run. In a narrow window (down to the 960 × 640 minimum, with the
model-work chip and every run part shown at once) nothing is cut or drawn over another: the file name shortens first
(ellipsis, down to about twenty characters), then the run parts are hidden whole in this order — the tokens-per-second
rate, the mode, the time left, the elapsed time — and only then does the file name shrink further; the state text, the
connection chip, the control, the model-work chip with its Stop, the product name, the theme toggle and About always
keep their words whole, and a wider window brings the hidden parts back (`TitleBarFitTest`).

**Planned.** The title bar reads, left to right: product name · the **activity chip** of hidden work (`ActivityChip`,
for example "▬ Names · 53 % · open") · while a project is open, the project name · the run state text from the run-state
table (`#run-states`, for example "51 % done · about 1 h 10 min left") · the quality dial and review mode · while
translating, elapsed time and speed (the title bar keeps "9.4 tok/s", tooltip "how fast the model writes") · the
**AI-model chip** (colour by G11) · Pause / Resume. Items give way by priority; the mockup README gives "window ≤ 1280
hides dial, elapsed and speed; ≤ 1060 hides the name", and `TitleBarFitTest` is extended (also under `uk`, L10). The
project's name shows in the title bar, not in the sidebar (G6).

### Background work and the progress dialog {#busy-card}

Every background operation is registered as an activity with a title, a start time, optional progress (a fraction, a
step, key/value details and a time left) and, when it can be stopped, a cancel. Work the window waits for is
**blocking**: opening and inspecting a book, preparing a run, the export and its consistency pass, the glossary's model
scan and review, the Book Brief's style suggestion, the file-name suggestion, the provider's inference test and a
review retry. The translation run itself, the model listing, the connection check and structure checks are not.

While a blocking activity runs with its dialog shown, the work layer (`#shell-busy-host`) fades in (160 ms) — after
400 ms, so work that ends at once never flashes it — over a scrim (`#shell-busy-scrim`) that swallows clicks, and no key
reaches the window behind it. Short work also disables the navigation column and the toolbar actions, and the navigator
refuses every navigation; long work (the names pass, the export) does not lock them, so leaving its screen asks "Model
work is running" (Stay · Continue in background). Tab stays in the dialog; it goes when the work ends. The layer sits
between the frame and the dialog layer, so an error dialog or a question that arrives meanwhile is shown above it, and
the progress dialog keeps its state underneath.

The work layer holds the **progress dialog** (`control/ProgressDialog`, root id `#busy-card`, XL size), one pattern for
all model work (mockup README, G4); no second OS window exists except the native file choosers (G1;
`control/ModelCallsWindow` is removed). Each activity keeps its dialog and `state/ProgressDialogModel` until it ends,
and the tracker's changes reach them at most ten times a second. The progress dialog is used by: Fill in the brief from
the book, Prepare names automatically and the Scan / Review / Translate card actions, Suggest a file name, Export · last
check before saving, and All calls (the translation's model calls). Its parts:

| Part | Content | Behaviour |
|---|---|---|
| Header | Title, purpose line, determinate bar | The bar counts every step of every task of the operation (G10); its tooltip says "n of N steps" |
| Figures line | "51 % · 26 of about 49 steps · Left about 1 min"; Detailed also shows elapsed, speed and task figures | Task and item counters live in the list rows ("25 of 30 requests"), not in the figures line |
| Simple / Detailed | Simple: one plain sentence for the selected step and Input \| Output in plain words. Detailed: adds the info grid (attempt, a clock against the timeout, first token, tokens, speed), the call kind code and the parsed prompt (G5) | Opens in Detailed when **Show technical details** is on (G12) |
| Follow current step | Switch | On: the list selects the step the work is on. Only a person's click or key turns it off |
| Left list | Steps or items in groups, each with a state marker (G11) and one result line | Finished steps stay selectable with their data; a step not started says so |
| Footer | Left: **Continue in background** (long work only) and one line saying what Cancel does ("Cancel writes nothing."). Right: Cancel, then the result action | The export pass also offers **Write the file now** |

**Continue in background** is offered only for long work — the names pass and the export pass; short work (brief
suggestion, file name, tests) cannot be hidden. Hiding collapses the dialog into the title bar's activity chip; a click
on the chip reopens the same dialog with its state. While work is hidden, the screen it writes to is **read-only**
behind a banner ("Names is running in the background — this screen is read-only until it ends"), and other model work is
refused with a toast offering "Show progress". When it finishes, the chip goes and a toast with **Review the results**
appears; nothing pops up by itself. A press of Cancel disables the button until the work ends; work that cannot be
stopped shows no Cancel and says so. Esc hides long work, the export's last check included, and cancels short work;
only the export's Cancel button asks first ("Cancel the export? No file is written.").

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

**Planned wording** (mockup README, Run states): the toolbar labels are **Start translation**, **Continue translation**,
**Pause**, **Resume**, **Stop** and **Try again now** (the one retry verb); the table in `#run-states` says which is
offered in each state. Pause is a break inside this translation (segments in flight are translated again on resume);
Stop ends translating for now. Both keep everything, and Stop is a secondary (not red) button.

### Workflow navigation {#workflow-navigation}

The left navigation's **WORKFLOW** group numbers its five steps — **1 Import, 2 Book Brief, 3 Names &
style, 4 Translating, 5 Export** — each showing a done mark (number, or ✓ when done) once its work is complete, under an
uppercase heading. The **APPLICATION** group (also an uppercase heading) lists Settings; **Planned:** the WORKFLOW group
also lists **Projects** above the steps (the mockup README, Window chrome), and Projects gets a screen
(`#screen-projects`). **Built today:** Projects is an inert entry — it is listed but has no screen yet, so "Continue"
skips it. Every workflow step — Import, Book Brief, Names & style, Translating, Export — has a live screen; Review is
not a step (G7): **built today** it is a panel inside Translating, **Planned** it is a dialog (`#screen-review`). The
navigation footer is described in `#navigation-footer`.

**Step gating.** A step opens only when what it needs exists. Import and Settings are always open. Book Brief,
Names & style, Translating and Export are **locked** while no book is open (`Open a book first`); Names &
style and Translating are also locked while the source or target language is unset or the two are the same (`Choose the
languages in Book Brief`). Export needs only a book, because a stopped or running book can be exported at any time. A locked entry is
drawn muted (`nav-item-locked`) but stays clickable: the click is refused and an information toast gives the reason, and
its hover text states the same reason. Every route to a screen — the column, Back and Continue — goes through the same
refusal. While Continue on Book Brief cannot be pressed, a line beside it names what is missing, because a disabled button
shows no hover text. Book Brief is marked done once Continue was pressed with usable languages, or a run exists;
Translating is also marked done for a stopped or failed run with no segment left undecided.
**Planned (G6):** without an open project, steps 1–5 are greyed and unclickable, with a tooltip and the hint "Steps
unlock when a project is open"; Projects and Settings stay available (`NavColumn` + `StepLocks` / `StepAvailability`).

### Navigation footer {#navigation-footer}

The navigation column's footer reads the chosen provider's display name and model id, for example `Ollama · gemma4:26b`, or `No model chosen` when none is configured. It shows **no readiness dot** — readiness is not tracked in this build. **Planned:** the footer (`#nav-footer`) reads "AI program · model" with the tooltip "the one network destination"; the readiness shows in the title bar's AI-model chip (colour by G11) and in the Providers summary card.

P6 reference: match the shell chrome, nav grouping, and theme toggle in the mockup, except for the departures named
below. The mockup's "Mockup reference" group (Component library, Dialogs, Notifications and Run states pages) and its
dashed preview bars and mockup-only buttons are specimens for the designer and are not part of the application.

### Departures from the mockup {#shell-departures}

The mockup README is the target; until the next change ships, the build differs from it in these places (each closes
when the Planned clause ships), and differs on purpose in the rest:

- **Built today, closed by the change:** Projects has no screen; the work layer shows a small busy card with a separate
  Model calls window; review is a panel inside Translating (not a dialog); run controls also sit in the title bar and the
  Translating screen; toasts are anchored bottom-centre; nothing is saved (no project store, no remembered settings);
  the Settings tabs hold a subset of the mockup's contents (`#screen-settings`).
- **The Structure step** — the mockup's older draft drew the book's structure as a step of its own; since 15i.U3 the
  Import screen shows the reading-order tree, its segments and the book's facts, so the workflow has five steps and
  Book Brief leads straight to Names & style (G7).
- **The per-node formatting pills on the structure tree** — formatting is reported for the whole book in the statistics card,
  not per node.
- **The import card's `valid` badge, "EPUBCheck passed"** — nothing validates a book beyond opening it; the round-trip and
  resource-id checks this build runs are its own checks, not EPUBCheck's.
- **The import screen's "Source language override" dropdown** — the source language is chosen on the Book Brief, not
  on Import (ADR-0037).
- **Toasts naming no requirement** — the mockup's toast text is illustrative; this document and
  `11_NOTIFICATIONS_AND_ERRORS.md` are the binding toast contract.
- **The preview-state switchers** — every screen shows its own live state, not a designer-only switcher between
  states.

### Hover explanations {#hover-explanations}

Every button, toggle, field, combo, segmented choice and table header a person can operate explains itself in a
tooltip that appears after 400 ms, wraps at 320 px and stays long enough to read. The text is a `<label key>.tip`
catalogue entry in both languages, set once through `Tips`, and it is also the control's accessible help. The bubble is
painted with the title bar's colour pair, so it is dark on the light block and stays readable on the dark one. A
disabled control shows none, because JavaFX delivers it no pointer events. The Names & style subtitle lists the three
things to do: fill Target, set Type and Gender, lock what must never change. **Planned (L8):** one-line text is cut with an
ellipsis and gets a tooltip only when it is cut (an `Overrun` helper on `Labeled`); long text wraps in detail panes and
dialogs; nothing important is hidden behind a hover.

## layout-and-sizes {#layout-and-sizes}

**Planned — UI finalization + SQLite.** The window minimum is 960 × 640 (`AppShellView`). JavaFX has no media queries,
so the rules are Java (mockup README, "Layout and sizes"):

| ID | Rule | JavaFX |
|---|---|---|
| **L1** | Width classes from the **content width**, not the window: compact < 1040 px · regular 1040–1439 · wide ≥ 1440, with 16 px hysteresis (at least the scrollbar width). Each region still decides its own two-column switch from its own width, as `TwoColumns` does; the classes drive CSS only | One `LayoutClass` helper: a width listener on the content host sets pseudo-classes `compact` / `regular` / `wide`; no logging in the listener |
| **L2** | The toolbar is the one action bar: Import → Continue; Brief → Back · Continue; Names → Back · Start translation (or Go to Translating); Translating → Back · Pause / Resume · Stop · Review (n), or Start / Continue / Try again now by run state; Export → Back · ⬇ Export book, then Open folder · Open book; Projects → + New translation. In compact the Back label shortens to "‹ Back", the full label as tooltip | `StepFooter.claim()` mounts buttons in `#shell-actions` |
| **L3** | A pinned banner slot between the toolbar and the scrolling content holds the run banners (provider unreachable, stuck, gave up, model missing, cannot start, welcome back, finished) and the hidden-work banner. Run banners show on every screen while they apply, with "Go to Translating" | `VBox #shell-banners` in `AppShellView` above the `ScrollPane` |
| **L4** | Content caps: form and list screens (Projects, Import, Brief, Export, Settings) at 1320 px; prose and hints at 680 px; Translating and Names & style use the full width. Input widths by kind: number 120, name 360, URL or path 640, model 480 | `maxWidth` on a centred content column; `prefColumnCount` / `maxWidth` per field |
| **L5** | Screens that fill the window: Translating (the live panel), Import (the text browser, min 360 px high), Names & style (the two tables); they grow to the window height instead of scrolling inside a scroll | A `fills-viewport` style class opts out of the `minHeight = USE_PREF_SIZE` that `AppShellView.showContent` applies |
| **L6** | Never stacks, list \| detail splits: the live panel (list 240 px … 34 %), the review desk and progress dialogs (list 220 px … 30 %), Settings › Providers (list 240, or 200 in compact). In compact, Detailed Input \| Output stacks inside the detail pane | `SplitPane` with divider positions and minimum sizes |
| **L7** | Stacks in compact: Names & style's two cards; the Import browser (Chapters \| Segments on top, Text below, 200 px); two-column card rows (Brief, Export); four tiles go 2 × 2 | Region width rules (`TwoColumns`), `GridPane` constraints |
| **L8** | Truncation: one-line text is cut with an ellipsis and gets a tooltip only when it is cut; long text wraps in detail panes and dialogs | `Overrun` helper on `Labeled` |
| **L9** | Nav width 236 px, or 200 px when the window is narrower than 1200 px; no icon rail | `NavColumn` pref width |
| **L10** | Ukrainian labels are about 26 % longer: every fit test (title bar, toolbar, dialog footers, segmented controls in narrow panes) also runs under `uk` | `LayoutFitTest` extending `TitleBarFitTest`'s pattern, at 960×640 and 1920×1080, in `en` and `uk` |

Built today, the same intent is met screen by screen: `TwoColumns` switches by its own width (Import 760 px, Book Brief
700 px, Names & style 1040 px), and the 960 × 640 fit above holds.

## global-patterns {#global-patterns}

**Planned — UI finalization + SQLite.** These apply to every screen (mockup README, G1–G12); the sections below cite
them by number.

- **G1 — Dialogs live in the window, on two layers.** The work layer (`BusyHost`) holds the progress dialogs (G4);
  `ModalHost`, above it, holds questions, confirmations, errors, the review desk, the provider tests and the forms. A
  question opened during work appears **above** the progress dialog. One card per layer: a dialog the person did not ask
  for (the review desk opening at the end of a run) **waits** until no dialog is open and no work is visible
  (`openWhenFree`, a `showWhenFree` queue in `ModalHost`). Focus is trapped in the card (Tab wraps); **Esc** closes a
  dialog (on a progress dialog it hides long work, asks first before losing an export, and closes short work); **Enter**
  (or Ctrl/⌘+Enter inside a text area) runs the footer's main button; only About closes on a click outside. Sizes:
  `.dialog` 480 px (a confirmation), `.wide` 720 px (a form or result), `.xl` min(1180, 92 % of the window) wide with a
  fixed min(820, 90 %) height so the footer never moves (progress dialogs, the review desk, the tests). Header (icon,
  title, one-line purpose, ✕) and footer (safe action first, main action last) are fixed; only the body scrolls. No
  second OS window except the native file choosers.
- **G2 — Toasts bottom-right**, newest at the bottom, at most 4; success and info stay 5 s, warning, error and toasts
  with an action 12 s; each has ✕; a toast may carry one action ("Review the results"); while a dialog or visible work
  is open its feedback is a line in its own footer, not a toast (`notify/ToastStack`, 16 px inset, 360 px wide).
- **G3 — Visible scrollbars:** 12 px, a `muted-2` thumb on a `surface-2` track, shown whenever content overflows, never
  overlay bars; `ContrastTest` checks the thumb at 3:1.
- **G4 — The progress dialog** (`#busy-card`).
- **G5 — Parsed prompts and replies, in Detailed views only:** each message a card headed by its role (SYSTEM, USER)
  with part count, tokens and Copy; each prompt section collapsible; rule lists as bullets; name and term slots as a
  Term \| Rendering \| Note grid; preceding pairs as Source \| Translation; items as Id \| Segment \| Text; in detail
  panes `⟦gN⟧` marks are chips, in one-line rows they are left out (the full text with marks `⟦1⟧` is the tooltip), in
  editors they are protected text marks `⟦1⟧`; a JSON reply is an Id \| Segment \| Translation returned \| Result grid;
  "Raw messages, exactly as sent" and "Raw reply" are collapsed blocks with Copy (`PromptView`, `ReplyView`).
- **G6 — Workflow steps need an open project** (`#workflow-navigation`).
- **G7 — Five steps:** Import (with the book's text browser) · Book Brief · Names & style · Translating · Export. Review
  is a dialog.
- **G8 — No fixed, non-actionable rows:** no Accent row and no decorative text; each screen has at most one purpose
  line.
- **G9 — Two kinds of table.** Editable or sortable lists (Glossary, Recurring terms, Projects search results) are
  `TableView`s with every column resizable (`CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN`, minimum widths such as Term
  140, Kind 96, Translation 170, Gender 110, Locked 64, action 36), grid lines, a header that stays visible, **fixed
  row heights** (46 px; long lists stay virtualised), and a cell's key text never cut by something else in the cell; a
  changed row is marked by its **left edge**, never by a chip in the term cell, and the full text is in the tooltip.
  Read-only grids in dialogs (findings, reply items, breakdowns, Previous \| Translated) are bordered, non-resizable
  `GridPane`s whose text wraps.
- **G10 — Progress and time left count all the work** (`#progress-counting`).
- **G11 — State markers and one colour map.** Every state is an icon + word (Ikonli Feather); one colour per meaning
  everywhere (markers, chips, badges, the title-bar chip): grey = waiting, queued, idle; blue = working; green = done;
  amber = needs you or slow; red = failed or unreachable; sand = kept as is, skipped. "Waiting" means queued only; the
  model at work is "translating…" or "answering". Links are always underlined and use `--info-fg` (4.5:1 on every
  surface in both themes); the selected segment of a segmented control gets a 2 px `--text-strong` bar besides the fill.
- **G12 — Simple first, technical details on request.** Main screens use plain words (`#words`); chunks, calls, tokens,
  attempts, rule codes and wire names appear only in Detailed views, in the Advanced folds, and when Settings ›
  Appearance › **Show technical details** is on. That switch is off by default and remembered; it makes every Simple /
  Detailed view open in Detailed, opens the Settings folds, and shows the technical lines. There is no third mode.

### Progress and time left {#progress-counting}

**Planned.** The unit is a **step**, counted over the whole operation (3 tasks × 3 items are 9 steps). For a translation
run: a step per segment draft, one per check the quality dial plans for each translated segment (Fast 0, Balanced 1,
Max 2), plus Max's chapter summaries and backward revision; a kept-as-is segment is a draft step done at once. For a
dialog: each step's items (requests, or 1 for a code step). Unknown counts are estimated and marked "about" until
known; work found on the way (a fix after a failed check, a re-ask, a resend after a timeout) joins the total when it
starts, and the bar never moves back. **Time left** is the sum over step kinds of steps left × the measured time per
step of that kind, excluding pauses; a kind not run yet borrows the draft time scaled by its size; it is shown after 5
steps or 3 %, rounded, as "about …", and moves at most 10 % a minute. The bar and the title bar show the share of the
work ("51 % done"); text lines count segments ("74 of 142 segments done"); the two are never printed side by side as two
percentages; the bar's tooltip says "140 of 276 steps, checks included". **ⓘ How progress is counted** opens the
breakdown per kind of step (planned, done, measured time per step). Estimates before Start appear only from a measured
speed (the inference test or an earlier run) and are marked "estimate"; without one the screen shows relative cost
(Balanced ≈ 1.5× Fast, Max ≈ 3×). **Built:** `state/ProgressLedger` counts every step of every kind and measures the time per kind
over the whole operation without pauses (it replaced `RunFigures.percent` and the 20-segment window of `RunClock`);
`StartEstimates` decides the estimate before Start, and its wiring to the quality card is group 6.3.

### Words {#words}

**Planned.** Segment stays the unit of text; every segment count's tooltip says "A segment is a paragraph, heading or
caption — one piece of the book's text." Main screens use the right-hand words of the mockup README's Words table:
"segments 5–8" for chunk/batch/call, "AI model" for provider in sentences (banners name the program, "Ollama"), "to
review" for flagged (buttons "Review (6)"), "worth a second look" for suspicious, "translated / fixed automatically /
left" for auto-accepted / repaired & accepted / remaining, "translated again alone" for fell back, "translation /
translating" for run, **"Try again now"** for Retry / Send again / Retry now, "Skip these 4 segments" (always the count),
"✦ Fill in from the book" for Suggest with model, "Text in other languages", "samples", "Spell in Ukrainian letters"
(by target language), "Picture descriptions", "Table of contents and chapter titles", "Copyright and edition notes",
"Last check before saving". The **Glossary**, **Recurring terms**, **Scan · Review · Translate** and **Locked** keep the
owner's names (15i.U5). Ukrainian follows the same rules: one idea per sentence, ICU plurals for every count, no
gendered past tense for "you". "Stay paused" is gone (the toolbar's Pause does it).

## screen-projects {#screen-projects}

Purpose: list existing projects and start a new one. **Built today:** the screen does not exist (the navigation entry is
inert). **Planned — UI finalization + SQLite** (new `screen/ProjectsView`, `ProjectsViewModel` over a `ProjectStore` port
in `:api`, SQLite adapter in `:persistence`):

| Control            | JavaFX control          | Behaviour                            |
|---------------------|--------------------------|----------------------------------------|
| Purpose line       | `Label`                 | "A project is one translation of a book. One book can have several — into other languages, or the same language with another AI model. Progress is saved as you go." |
| Search and filter  | `TextField`, segmented All / In progress / Finished | Searches books and projects; no hits: "No project matches 'xyz'." with [Clear search] |
| Project list       | `TableView` / card list, **grouped by book** | A book header (cover, title, file · format · segments, "n translations", **＋ Translate into another language**) and one row per project: name (a "named" chip if named, "open" for the open one), languages · AI model · quality, a bar with % and last opened, a status chip coloured by G11 (Translating, Exported, Not started, Cannot reach LM Studio), **Continue** or **Open**, ⋯ (Rename…, Show book file, Remove project…) |
| ＋ New translation | `Button` in the toolbar (list) or the empty state | Creates a project and opens Import |

Continue / Open opens the project at its step; a translation that was going waits paused (Ready to continue,
`#run-states`). If the AI model is missing or changed, the continue dialogs appear (`#dialogs-other`).

States: **populated** (grouped list), **search without a match**, and **empty / first launch** (`#empty-state-projects`):
"Translate a book on your own computer" with **How it works** in four steps (choose a book · check the languages · let
it translate · save the translated book; "your original file is never changed"), the **AI-model line** (found ✓ —
"AI model: Ollama · gemma4:e4b — ready ✓ (found on this computer when BookLoom first opened)" — or **No AI model
found**: "Ollama is not open. Open the Ollama app, then press Try again." with [Try again] and "I use another
program…", which opens Settings), and **＋ New translation**.

**First launch and the AI model (owner decision 13, planned).** On the first launch BookLoom looks **once, by itself**
for Ollama (`127.0.0.1:11434`) and LM Studio (`127.0.0.1:1234`) on this computer; if it finds one with models it creates
the provider and selects the only model, or asks which one when there are several. This is the one exception to "no
network call the person did not start": local only, once, no answer needed, degrading silently; every later check is
user-triggered (Settings tests, "Try again"). The exception is written in AGENTS.md, `.claude/rules/offline-and-privacy.md`,
`03_PRIVACY_AND_OFFLINE.md` (NFR-OFFLINE-05), DD-01 and ADR-0044.

Requirements: FR-NOTIF-05. P6 reference: Projects and Projects-empty.

### Project names {#project-names}

**Planned (PN).** A project is one translation of a book; the same book can be translated into several languages, or
into the same language more than once (another model, quality or thinking setting).

1. **Automatic name** `<book title> · <SRC> → <TGT>`, for example `Frankenstein · EN → UK`: the title is the metadata
   title before ":", or the file name; under "Find out from the text" the detected language is used, and until there is
   one the source code is left out; it ends in `→ …` before a target is chosen.
2. **It follows the languages** while it is automatic (`nameOrigin` AUTO); a toast says when it changes. A typed name
   (USER) never changes.
3. **A language pair is unique per book** (same `Project.contentHash`) **unless the project is named.** Choosing a used
   pair (in the Brief, or in Translate into another language) opens **Same language pair**: the existing project with its
   AI model, quality and progress; **Open that project**; **Make a second <language> translation**, which needs a name
   of its own (the field starts empty, with suggestion chips `· 26b`, `· Max quality`, `· thinking low`, `· formal`,
   `· v2`). The primary button stays disabled until the name is filled in and unused; the error shows only after the
   first edit and the placeholder never equals an existing name.
4. **Names are unique** (trimmed, case-insensitive). A named project returns to its automatic name only when no other
   project of the book has the same pair.
5. **Where it shows:** Projects (with an "open" chip on the open one), the title bar, the remove and continue dialogs,
   and the label of side files, so exports of two projects with the same pair never collide (`earth-gravity.uk.md` and
   `earth-gravity.uk.26b-max.md`).

Dialogs: **Translate into another language** (Translate into, with a used language marked "already in n projects";
From, the book's source; project name; **Start from**: the brief · Glossary and Recurring terms with translations
emptied · optionally the latest project's AI model; "Create and open the brief"; nothing is imported again — the book
is reused by `contentHash`); **Same language pair**; **Rename…** (chips, uniqueness error, "Use the automatic name
again"); **Remove project…** (deletes this project's saved work only — never the book, the exported files or the
book's other projects). `Project` gains `name` and `nameOrigin`; a `ProjectNames` service in `:pipeline` builds the name
and reports a conflict as a typed result.

## screen-import {#screen-import}

Purpose: open a book, confirm what was detected and read the book's structure before going on.

| Control                 | JavaFX control                       | Behaviour                                                                                                                                                       |
|--------------------------|----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Drop zone / file picker | drag-drop target (`GridPane`) + `FileChooser` | FR-IMPORT-02. Browse files… is the Open action. The full box while no book is open, a refused file, a DRM-blocked book or a cancelled import is shown; once a book is open the box is one line (style class `dropzone-compact`: glyph, title, "or" and Browse on one row) and still accepts drops |
| Cover                    | `ImageView` or a neutral placeholder  | Shows the book's cover image when the format carries one, else a neutral placeholder                                                                           |
| Book details card        | card of key/value rows (`Label`s)     | Rows, in order: file; format with its version (e.g. `EPUB 2.0`); `Title · Author`; declared language written `English (en)`; chapters · `~words`; DRM `none` (a card exists only for a readable book, and the inspection names an encryption scheme only for a protected one) |
| Book facts card          | one card: a `GridPane` of figures, the run total and the check rows | Titled `Book facts`. Translatable segments; images (kept as-is); code blocks with their "preserved" note; embedded fonts; verse lines; footnotes · tables; inline formatting; then the round-trip and identifier checks and, when a segment is larger than one chunk, the warning that it will be split. The checks run once the book is open, off the FX thread, and never hold Continue back. Each figure is shown by exactly one card: the words and chapters are on the details card, the segment total is the tree's own count |
| Structure card           | `TreeView` (virtualized)              | The reading-order tree, nested and titled from the book's own navigation (its ToC/nav document); a count pill per node shows its segment count; a node with no title reads `Untitled`. Rows have one fixed height (34 px): a long title ends in an ellipsis and is its own hover text. Read-only: nothing offers to include, exclude, rename or reorder a part. Picking a node lists its unit's segments; a grouping node with no unit lists its descendants' units; the first node with a unit is picked on opening |
| Segments card            | search `TextField`, kind `ComboBox`, `ListView` (virtualized, fixed 34 px rows), read-only reading pane | The picked part's source segments in reading order with no model call, so it works before a model is chosen; the list above and the picked segment's text below. Header `N segments · M chunks (planned)`. A row shows: locator (`ch1 · p02`), kind chip, the text on one ellipsised line, the token estimate, a `kept as is` chip for a segment a run copies unchanged (numeral, scene break, lone letter) or a `kept as source` chip for an auxiliary kind the brief's *Also translate* switches leave untranslated, and a planned-chunk badge `n/m` (tint alternates per chunk; the first row of each chunk has a rule above it). The chunk is the plan `ProjectService.plan` computes (same packing, current brief and dial), not a promise: the real run drafts in token-budgeted batches that may differ at the edges. A search field keeps rows whose text or locator contains the typed text (case-insensitive); a kind choice keeps one kind; `Showing K of N` appears while a filter hides rows. Picking a row opens the whole text in the reading pane with locator, kind, tokens and chunk, each formatting token as a chip. States: nothing picked, loading, listing failed (`The segments could not be listed.`), part without segments, filter without a match. The listing is asked again when a brief save finishes |
| Continue / Cancel        | `Button`s                              | `Continue to Book Brief` leads to the Book Brief; `Cancel` discards the import                                                                                  |

Layout of a detected book, top to bottom: the compact drop zone; the details card beside the facts card; the structure
card beside the segments card; the footer. Each pair is a `TwoColumns` control (`ui.control`): side by side while the
content is at least 760 px wide, else stacked with the first card above the second, in the same order for Tab and
for a reader. Since 15i.U3 there is no Structure step: this screen is where the structure is read, and its Continue
leads to the Book Brief, whose Continue leads to Names & style.

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

**Planned — UI finalization + SQLite (I1–I4).** The screen is reshaped around a one-line verdict and a text browser
that fills the height (L5), capped at 1320 px (L4):

| Element | Behaviour | Code |
|---|---|---|
| Header row | Title and a one-row drop zone: "Drop another book here or Choose a file…". Dropping a book while one is open asks **Open another book?** (`#dialogs-other`), whose primary is **Open as a new project**; "Replace and delete this translation" is the named destructive choice | `ImportController`, `ImportGuard`, `dialog/ReplaceRunDialog` |
| **Verdict** (one line) | ✓ "BookLoom can translate this book: 5 chapters, about 4,100 words. Your file is never changed." · **Reading…** (a bar "chapter 3 of 5", Cancel) · **Already in projects** (names them, Show them) · **Two language marks** ("The file names two languages: its details say English, its chapters are marked Ukrainian. BookLoom does not read the text to decide — check “Translate from” in the Book Brief." — a comparison of the file's own declarations, never a text analysis) · **DRM** ("locked by the shop it came from") · **Damaged** ("Try downloading it again", Details) · **Unsupported** ("It reads EPUB, FB2, Markdown and plain text"); each error offers Choose another file… | `ImportState` variants |
| **About this book** (fold, closed) | Summary line: format · chapters · words · images · cover. Open: cover (or "No cover · N/A", I2), file, title · author, the declared language, DRM; segments to translate, kept as they are, images · footnotes · tables "kept in place", formatting "kept exactly", the check "read and written back with nothing lost". The technical line (round trip, ids, code blocks, fonts) shows only with Show technical details | `ImportCardView`, `ImportBookView`, `StructureChecks*` |
| **The book's text** (I3) | Three panes in one `SplitPane` (minimum widths 160 / 240 / 220): **Chapters** (tree, count pills) → **Segments** of the chapter (search the text or a place like "ch1 · p05", kind filter I4, one-line rows with the kind chip or "kept as is"; marks left out, full text in the tooltip) → **Text** of the segment (location and kind; tokens and chunk only with technical details; marks as chips). In compact, Text moves under the other two (200 px). Search with no hits: "Nothing matches 'xyz'." | `StructureBrowser` + `StructureSegmentsPane` (virtualised) |

Requirements: FR-IMPORT-01..07. P6 reference: Import and each import state.

## screen-book-brief {#screen-book-brief}

Purpose: capture the Book Brief. **Planned purpose line:** "We filled this in from the book. Check the two languages —
everything else is optional and can be changed later."

| Column and section    | Control                                                                                                    | JavaFX control                                            | Requirement              |
|-----------------------|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|--------------------------|
| Left: Languages       | source (led by **Auto — the model identifies it**) and target, both editable and searchable over the same 34 languages; the model's "Looks like English — Use it" proposal under an Auto source | two searchable `ComboBox`es, a `Label` and a `Button`        | FR-BRIEF-01              |
| Left: Translation policies | name, foreign-passage (keep-as-is / translate / translate+note), footnote, unit policies, faithful↔natural slider | `SegmentedButton`/`ToggleGroup`, `ToggleSwitch`, `Slider`      | FR-BRIEF-04..07          |
| Left: Also translate  | ToC/navigation labels (also governs page titles), image alt-text, book metadata title/author (also governs the description), frontmatter values | four `ToggleSwitch`es                                          | FR-BRIEF-04, FR-DOC      |
| Left: Quality vs speed | Fast/Balanced/Max, three hint lines, and the model row with a `change` link                                   | `SegmentedButton`/`ToggleGroup`, `Label`s, `Hyperlink`         | FR-BRIEF-08              |
| Right: Tone & style (predicted) | the Suggest button, then genre (searchable free-text field over the forty genres), register, voice/era, audience, narrator and narrator gender, each with its basis line | `Button`, searchable `TextField`/`ComboBox`, `ComboBox`s, `Label`s | FR-BRIEF-02, FR-BRIEF-03 |

The screen has two columns (`TwoColumns`, breakpoint 700 px of content width): the **left** holds what the person
decides and the model never touches, the **right** holds what the model predicts. The columns sit side by side from
700 px and stack below that, left above right (at the 960 px minimum window), and Tab walks the left
column, the right column, then the footer in both layouts. Each predicted field shows its basis under it when the
suggestion wrote it (built wording; planned: "samples", see mockups/README.md Words): "3 of 4 passages" (a plural form of the count of passages read), "“quote” · 2 of 3 passages",
"From what the model knows of this book" or "Counted over the whole book"; a field nothing supported shows the
"? Uncertain" chip beside "1 of 4 passages" and keeps the value it had.

**Auto.** The source list begins with **Auto — the model identifies it**, which is chosen for a book that declares no
language the application can name and which the person can choose over a declared one; the target list has no Auto
entry. Under Auto the screen says the model will identify the language, `Continue` needs only a target language, and
after Suggest the language most passages read as is offered as "Looks like English" beside a **Use it** button, which
sets the source language and nothing else; the proposal is never applied without the click and is shown only while the
source is Auto.

Both the source and the target language are editable and searchable over the same 34-language list (the source also
holds Auto, first); the source is
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
screen (**Planned:** the review mode is Settings › Automation, `#screen-settings`). Opened with no book, the screen shows a **no-book state** ("No book is open" with an action that leads to
Import) instead of the form.

**Planned — UI finalization + SQLite (B1–B3, I).** The cards keep their content and take the words of `#words`:

| Element | Behaviour | Code |
|---|---|---|
| While translating | Read-only, with the banner "Pause the translation to change the brief. Changes apply to segments not translated yet." [Pause] (the engine rereads the brief's switches only at resume, and tone and style are not proven to be rederived) | `BriefView` |
| Languages | **Translate from** (Find out from the text · a language · Other) with "The book says English" · **Translate into** · **Project name** line ("automatic — follows the languages", Rename…). A used pair opens Same language pair (`#project-names`). **Locked once translating has started** ("To translate into another language, use Translate into another language on Projects") | `BriefLanguagesCard` |
| Tone & style | Genre and Who reads it (each with "Suggested from the book · why ›", which opens the brief dialog), Register, Voice and period notes, Who tells the story, Narrator's gender (asked at Start for a first-person story); **✦ Fill in from the book** | `BriefToneCard`, `BriefEvidenceRow`, `NarratorNotice`, `StyleSuggestion` |
| How to translate | Names of people and places (Translate · Spell in Ukrainian letters · Keep as written), Text in other languages, Footnotes, Units, Close to the original ↔ natural | `BriefPoliciesCard` |
| Also translate | Table of contents and chapter titles · Picture descriptions · The book's title and author · Copyright and edition notes (off) | `BriefAlsoCard` |
| Quality and speed | Fast · Balanced · Max quality; one line on what the choice does and its relative cost; "For this book on this computer: about 2 h 30 min (estimate from the model test)" **only when measured** (G10); the AI-model line with "change" | `BriefQualityCard` |

**Fill in the brief from the book** (progress dialog, short, B1–B3; `#busy-card`): header "50 % · 3 of 6 steps · Left about
30 s"; group **What it decides** (Genre, Register, Who tells the story, Who reads it · voice, each with its value so far
and its agreement, "3 of 4 samples agree"; selecting one shows each sample's answer with its **verified quote**, and a
value without a quote is not used); group **Steps**: (1) what the model knows about the book — skipped when the title is
the file name, (2) read sample 1…K (K = 3 / 4 / 5 for Fast / Balanced / Max) with the location range, (3) decide genre,
readers and voice; Simple detail: a plain sentence, then Sent (the sample) \| Answer (a Field \| Value \| Quote grid);
Detailed adds the info grid, the code (`book-knowledge`, `brief-suggestion` or `brief-synthesis`) and the parsed prompt.
Footer: "Nothing is written to the brief until you apply it." Cancel · **Apply to the brief** (when all steps have ended).
Not hideable; Esc cancels.

P6 reference: Book Brief with its sections, including the "Also translate" group.

## screen-structure {#screen-structure}

Folded into **Import** (`#screen-import`) in 15i.U3: there is no Structure screen, step or navigation entry. The
reading-order tree, the segment browser, the book's facts and the round-trip, resource-id and oversized-segment checks
are the Import screen's Structure, Segments and Book facts cards, with the same behaviour and element ids
(`structure-*`). A failed check never blocks Continue. The departure from the mockup is listed under
`#shell-departures`.

## screen-names-and-style {#screen-names-and-style}

Purpose: review and edit the glossary and the book's recurring terms before or during a run. **Planned purpose line:**
"Decide how names and repeated words are translated. Optional — skip it and BookLoom builds both while it translates."

The screen holds **two equal cards**, the Glossary and the Recurring terms, each built from the same widget (15i.U5):
the same toolbar in the same order, the same table look (46 px rows) and the same row cross in the last column. They
sit side by side when the screen's content is at least 1040 px wide and stack, Glossary above Recurring terms, when it
is narrower (one child order either way, so Tab reads the same); the tables fill the height (L5, L7).

| Control              | JavaFX control                       | Behaviour                                                                                          |
|------------------------|-----------------------------------------|--------------------------------------------------------------------------------------------------|
| Prepare names automatically | `Button` (`names-style-auto`, secondary), above both cards, with a muted line under it (`names-style-auto-subtitle`) | One press runs the whole names pass (15i.N8) with the chosen model: scan, decide the names, scan and read the recurring terms, translate both, lock by rule. The line states the plan's estimate for the brief's dial ("about 15–25 min on Balanced"), asked of the port again when the screen opens and when a change of the dial has been saved, and blank when the port cannot say. Off while any model action runs, and with no model chosen it reports so in the banner and calls nothing. Built today the shared busy card shows it and that card's Cancel is the Stop; **Planned:** it runs in the progress dialog (`#names-progress-dialog`), whose Cancel is the Stop, and a stopped or failed pass changes neither table. Its results view is "Automatic names — results" with a "Names (n)" section and a "Recurring terms (n)" section, a Revert on each row and one Undo all across both; "Show changed rows" turns on both cards' changed filters |
| Confirm suggested genders | `Button` (`names-style-confirm-genders`, ghost), beside Prepare names automatically | Shown only while a character's gender is a suggestion (marked "suggested"); one press turns every suggested character gender into the person's (15j.J3), kept in the results dialog with Undo all, which puts the suggested marks back. Off while any model action runs. The export notice and the Start question name suggested genders "suggested — confirm", apart from unknown ones |
| Glossary table         | `TableView`                             | Columns: Term, Type (Planned: Kind), Target (Planned: Translation), Gender, Locked and an unnamed last column holding the row's ✕; Term, Type, Gender and Locked sort by header, ties by term. An empty target shows the placeholder "No target" in its field; Gender reads "—" for a row that is not a character, "(suggested)" follows only a gender the book's pronouns or the first-name list suggested (Planned: evidence "he ×6", or "(likely)"); a row the last model operation changed is marked (built today: a "Changed" chip in its Term cell; **Planned (N3):** the row's left edge, with the tooltip "Changed by the last automatic run", G9). Start translation asks "N entries have no target — the model will choose" (listing them) when any entry has no target |
| Recurring terms table  | `TableView`                             | Columns: Term, Target (the rendering the run keeps: the person's, else the learned or most used one), an unnamed "→ Glossary" column that moves the term to the glossary, and an unnamed last column holding the ✕. Built today there is no Used column: a learned rendering is the Target. **Planned (mockup README):** Term · Translation · Used ("13 times") · → Glossary. Same placeholder and changed-row mark |
| Search field           | `TextField`                             | Each card's own: shows only rows whose term or target contains the text; Escape clears it         |
| + Add                  | `Button`                                | Glossary: opens the add-glossary-term dialog (FR-GLOSS-04; refuses locking a term that has no target). Recurring terms: opens a small card for one word or title; a term the glossary or the list holds is refused in the banner above the cards |
| Scan · Review · Translate | `Button`s                            | Each card's model actions, one model action at a time across both cards; Scan adds, Review removes what is not worth keeping, Translate fills the missing targets. **Planned:** each runs the progress dialog with two steps (the action's requests, then write); not hideable |
| Stop                   | `Button`, shown while a model action runs | Interrupts the action; nothing is written; a waiting line names the request and attempt; shown on both cards |
| Accept all             | `Button`, Glossary only                 | Shown only while a row holds a suggested target                                                   |
| Changed in last run    | `ToggleButton`                          | Per card, shown only while some row is marked; keeps the marked rows, so the results dialog's "Show changed" has a way back |
| Import · Export        | `Button`s + `FileChooser` (`CsvFileChooser`) | Per card, `glossary.csv` and `recurring-terms.csv`. Glossary import refuses locking a row that has no target. When a card is too narrow both fold, before anything else, into one three-dot menu (`<card>-more`; Planned: ⋯ holds Import CSV, Export CSV, Show only changed) |
| `Start translation`    | `Button`                                | Starts a new run at the first pending segment when Start is offered (`#start-and-resume`)          |
| Info banner            | `Label`                                 | `Skip this and the app builds names on the fly as it translates.`                                  |

The toolbar is one line at 960 and 1024 px in English and Ukrainian, also while Stop is shown: Import and Export fold
into the menu first and the search field narrows after that, so no caption is ever cut.

A **deterministic scan** runs automatically when the screen opens, proposing entries from the book's own text; these
proposals start **unlocked** (design.md D10). Locking is allowed only for an entry that has a target — the table, Add
term, and CSV import each refuse a lock with no target with `A locked term needs a target.` The info banner is true
either way: a run never depends on this screen — it adds the names it meets at each body unit's end as it translates
(design.md D5). With no book open the screen shows a **no-book state**: it says no book is open, routes to Import,
and shows no table, no scan, and no `Start translation`.

**Planned — UI finalization + SQLite (N1–N5, I).**

- **While translating:** the screen stays editable, with the banner "Translating is going on. Changes apply to the next
  segments translated." (the glossary is read again for every group of segments). While the names pass runs in the
  background, the screen is read-only (G4).
- **Glossary card:** title with count; purpose line "Names of people and places — written the same way every time. Set
  the gender so names take the right grammatical form." (N1); toolbar: search · + Add · **Scan** · **Review** ·
  **Translate** · ⋯ (Import CSV, Export CSV, Show only changed), each with a plain tooltip; **Locked** tooltip "always use
  exactly this spelling". **Empty:** "No names yet. Scan the book for them, or add one." with [Scan the book] [+ Add].
- **Recurring terms card:** purpose line "Words the book repeats — kept the same all through the book. Learned while
  translating, or set here." and **How it works** (shortened). **Empty:** "No recurring terms yet. They are found when
  you prepare names, or while translating." A search that hides every row reads "No name matches the search".

### Prepare names automatically — progress and results {#names-progress-dialog}

**Planned (G4, long; N4, N5).** The steps are the `NamesPassImpl` stages, in order:

| # | Step | Code |
|---|---|---|
| 1 | Find candidates in the book | `scan` |
| 2 | Decide the names | `names`: `term-reading`, `review-terms` |
| 3 | Find repeated words | `terms-scan` |
| 4 | Clean up repeated words | `terms-review` |
| 5 | Read repeated words in context | `terms-read` |
| 6 | Suggest translations for names | `names-translate` |
| 7 | Suggest translations for repeated words | `terms-translate` |
| 8 | Write the results | — |

Each row has its count ("25 of 30 requests", "about 4 requests"). Figures: "53 % · 26 of about 49 steps · Left about 1
min". Detail: a plain sentence; What the model reads (candidates with counts and quotes) \| What it says; Detailed adds
the info grid, the stage code and the parsed prompt; a finished step shows its result, one not started says so.
Footer: Continue in background · "Cancel writes nothing." · Cancel; Esc hides. The warm card on the screen says "About 1
min. You can undo it all afterwards." **Results** (the same card when done; wide; fits the window): subtitle "5 added ·
0 removed · 1 changed · 49 steps · 1 min 48 s"; tabs **Changes** (Entry \| Change \| Before \| After, grouped) and
**Steps**; footer Undo all · Show changed rows · Done (`ChangeResultsDialog` becomes the Results view; `ChangeResults`
and Undo all stay).

Requirements: FR-GLOSS-01..05. P6 reference: Names & style / Glossary — proposed.

## screen-translating {#screen-translating}

Purpose: drive and monitor the automatic run, and review flagged segments without leaving the screen. The dashboard
binds to the state mirror's observable surface, built from the events the engine emits
(`07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`); it never polls.

Subtitle: `A stopped run resumes where it left off — until the application closes.` (Built today; **Planned:** progress
is saved, so the subtitle changes with the project store.)

| Control               | JavaFX control            | Behaviour                                                                                                                                       |
|-------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Progress line           | `Label` + `ProgressBar`      | Percentage · `Chapter k of n` (the body-unit position; the auxiliary unit is never counted as a chapter) · chunk `k/K`                          |
| Rate / time             | `Label`s                     | Time left and tokens per second, from the recent drafts                                                                                        |
| Count tiles             | four stat tiles              | auto-accepted / repaired / flagged / remaining — `remaining` leaves out segments kept as source by choice. The flagged tile shows the review desk's count whenever the run is paused or ended (so Accept on a paused run lowers it and it equals `Review flagged (n)`); while the run decides it shows the run's own running count, so after Resume it reads that figure until the next pause or end |
| Model calls (live)      | two call blocks              | Block 1, `Current call` (`Last call` once the run has ended): the model call in flight, else the newest finished. Block 2, `Previous call`: the one before it; a new call moves the current one here. A block shows a header (prompt name, kind, state chip — waiting / answered / failed / cancelled — `attempt a of b`, a clock from the moment the attempt went out, the timeout, the tokens once answered), every segment the call carried (locator, the source as sent, the target so far, chips for what became of it: adopted, fell back, accepted, flagged, judge score, path) in a fixed-height scroll area, the model's reply as received (collapsed, with Copy) and a collapsed `Prompt context` section (with Copy) holding the filled parts of the prompt in the order they were sent — the system message's first, the fixed instruction text left out. The reply and the prompt are each one read-only text, filled only while the section is open; the clock, timeout and token figures change without re-laying out the screen, and a new call swaps the two blocks' roles rather than rebuilding them. Before the first call the card says nothing was sent yet |
| Activity log            | `ListView` (monospace)       | Tagged entries — `ok`, `fix`, `mem`, `sum`, `retry`, `err`, `info` — naming segments by locator (e.g. `ch7 · p42`); bounded to the last 500       |
| Controls                | `Button`s                    | Start, Pause, Resume, Stop, `Review flagged (n)`; which are offered follows the state table below                                               |

The start toast reads `Translation started` / `Keep the application open — progress is kept only until it closes.`
(Built today.)

**Built today** (`#runState`, 7 states):

| State           | Shows                                                                                                                                                                        | Offers                                                            |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| idle              | A ready card: book, model, review mode, quality dial, pending count; a missing model or missing brief field is named in place                                              | Start (its own)                                                       |
| running           | Progress advancing, the live panel, the activity log                                                                                                                        | Pause, Stop, `Review flagged (n)`                                     |
| paused            | Banner `Paused. Progress is kept until the application closes. Resume any time — it continues at chunk 41/66.`                                                             | Resume, Stop, `Review flagged (n)`                                    |
| stopped           | Banner `Run stopped. Progress is kept until the application closes. Resume any time — it re-enters at the first pending segment; flagged segments wait in the review panel.` | Resume, `Review flagged (n)`                                          |
| provider error    | Auto-paused, naming the failure's error code                                                                                                                                | Resume, `Open provider settings`                                      |
| completed         | Auto-accepted, repaired-and-accepted, flagged, kept-as-is (numbers, symbols) and kept-as-source counts                                                                      | Continue to Export, `Review flagged (n)`; Start only while pending segments remain (e.g. after an "Also translate" switch is turned on once the run has completed) |
| failed            | A blocking dialog: `Decided segments are kept until the application closes.`, then the outcome so far                                                                      | Start (begins a new run at the first pending segment)                 |

### The 16 run states {#run-states}

**Planned — UI finalization + SQLite (A).** Every surface follows the row of the current state: the title bar, the
AI-model chip (colour by G11), the toolbar (L2), the pinned banner (L3), the live panel's line and the markers of the
current call's segments. `RunState` (8 values) plus the recovery and call details (`RunNotice`, `RunInterventions`,
`PauseNotice`, `ActivityTracker`) already exist; this table is the mapping the conformance test checks.

| State | Code | Title bar · chip | Toolbar | Banner | Live line · current-call rows | Review · Export · awake |
|---|---|---|---|---|---|---|
| Ready | IDLE | "Ready" · AI model ready (grey) | **Start translation** | — | "Nothing translated yet." · waiting | off · off · no |
| Ready to continue | IDLE + stored progress | "Ready · 51 % done" | **Continue translation** · Review (6) | "Welcome back — 74 of 142 segments are done." | "Continues at Chapter 2, segment 5." · waiting | editable · partial · no |
| Cannot start yet | IDLE + `MissingInput` | "Ready" · No AI model (red) | Start (disabled, tooltip names what is missing) | "Cannot start yet — no AI model is chosen." [Open settings] | — | off · off · no |
| Translating | RUNNING | "51 % done · about 1 h 10 min left" · Model answering (blue) | **Pause** · Stop · Review (6) | — | "Now translating: Chapter 2, segments 5–8" · translating… | read-only ("Pause to edit") · disabled · yes |
| Pausing | PAUSING | "Pausing…" | Pause disabled · Stop | — | "Pausing… segments 5–8 are translated again when you resume." · translating… | read-only · disabled · yes |
| Paused | PAUSED | "Paused at 51 %" · Model idle (grey) | **Resume** · Stop · Review (6) | — | "Paused. Segments 5–8 are translated again when you resume." · **waiting** | editable · partial · no |
| Waiting for the AI model | PAUSED + recovery waiting | "Waiting for Ollama" · Cannot reach Ollama (red) | Pause ("stop trying by itself") · Stop | "BookLoom cannot reach Ollama — Is the Ollama app open? BookLoom tries again by itself in 0:12." [Try again now] [Skip these 4 segments] [Open settings] | "Waiting for Ollama. Segments 5–8 are sent again when it answers." · waiting | editable · partial · yes |
| Paused after an error | PAUSED, recovery held | "Paused at 51 %" · Cannot reach Ollama (red) | **Resume** · Stop | "Paused. Ollama could not be reached. Open Ollama, then press Resume." | as Paused | editable · partial · no |
| Stopped trying | PAUSED, recovery gave up (12 h) | "Paused at 51 %" · Ollama: no answer for 12 h (red) | **Try again now** · Stop | "Ollama has not answered for 12 hours — BookLoom stopped trying by itself." | as Paused | editable · partial · no |
| Model missing | PAUSED + model unloaded | "Paused at 51 %" · Model missing (red) | **Try again now** · Stop | "The AI model gemma4:e4b is not available — check that it is installed." [Choose another model…] | as Paused | editable · partial · no |
| Stuck call | RUNNING + call past its usual time | as Translating · No answer for 3:40 (amber) | Pause · Stop · Review (6) | "The model is taking longer than usual (3:40). BookLoom tries again by itself at 6:00. You do not need to do anything." | "The model is taking longer than usual with segments 5–8." · translating… · one clock "3:40 of 6:00" | as Translating |
| Stopping | STOPPING | "Stopping…" | all disabled | — | "Stopping… segments 5–8 are translated next time." | read-only · disabled · yes |
| Stopped | STOPPED | "Stopped at 51 %" · Model idle | **Continue translation** · Review (6) | — | "Stopped. Continues at Chapter 2, segment 5." · waiting | editable · partial · no |
| Finished, to review | COMPLETED | "Finished" · Model idle | Review (6) · **Continue to Export** | "Translation finished. 6 segments need your review. You can export now and review later." [Review 6 segments] — on Translating only; the desk opens by itself | "All segments translated." · no chapter, no chunk | editable · on · no |
| Finished, nothing to review | COMPLETED, 0 flagged | "Finished" | **Continue to Export** | "Translation finished. Nothing needs your review." | as above | "Nothing needs your review" · on · no; the desk does **not** open |
| Failed | FAILED | "Did not finish" · Error (red) | **Continue translation** · Review (n) | "The translation stopped because of an error. Finished segments are saved." [Show details] | "Stopped by an error at Chapter 2, segment 5." | editable · partial · no |

The Stop dialog says: "74 of 142 segments are done and saved. The 4 being translated now are translated next time. For
a short break, use Pause." ("Stay paused" and "Retry now / Send again" are gone; **Try again now** is the one retry
verb.)

### Changes while translating {#changes-while-translating}

**Planned (I).**

- **Names & style:** editable, with the banner "Changes apply to the next segments translated." (`#screen-names-and-style`).
- **Book Brief:** read-only while translating; the languages are locked once translating has started
  (`#screen-book-brief`).
- **Provider or model change** during a translation asks first, in the resume dialog's words (`#dialog-provider-binding`).
- **Export:** disabled while translating, pausing or stopping, with "Pause or stop the translation to export"; allowed
  when paused, stopped or failed, with the banner "74 of 142 segments are translated. The other segments keep the
  original text"; the last check is off and disabled for a partial export ("Runs when the whole book is translated")
  (`#screen-export`).
- **A screen another job writes to** (the names pass or the export pass hidden in the background) is read-only (G4).

### Translating screen — planned layout {#translating-planned}

**Planned (T1–T12, A, C1).** The toolbar and banner follow `#run-states` (L2, L3). The screen fills the window (L5):

| Element | Behaviour | Code |
|---|---|---|
| Help line | Once per project, closable: "BookLoom keeps the computer awake while it translates. Closing a laptop lid still puts it to sleep — after sleep, translating goes on by itself. Nothing finished is lost." If keep-awake is off: "The computer may sleep while translating — turn on Keep awake in Settings." | new |
| **Summary strip** (C1) | Big: "about 1 h 10 min left" (Finished: "Finished in 2 h 25 min"; Ready: the measured estimate). The bar over all steps (tooltip "51 % of all the work — 140 of 276 steps, checks included"). Facts: **74 of 142 segments done** · 36 translated · 12 fixed automatically · 6 to review · 20 kept as is · Chapter 2 of 5 · elapsed. A **"6 to review"** button opens the review desk. **ⓘ How progress is counted** (`#progress-counting`) | `TranslatingFigures` → a new strip class |
| **Live card** (fills the height, L5) | Tabs **Live** · **Activity log**. Live: Simple / Detailed · **Show each segment when translated** (Auto-show) · Follow again (after a manual pick) · All calls… | `control/LiveCallPanel` (rebuilt) |
| Call line | Simple: the plain line of the run state ("Now translating: Chapter 2, segments 5–8"). Detailed adds "Call 42 · Draft · 4 segments · ch2 · p05–p08 · attempt 1 of 2 · 0:21 of 3:00". One clock: the stuck state shows "3:40 of 6:00" | `LiveCallView` header |
| **List \| detail split** (never stacks, L6) | Left: the chunk's segments in reading order, one line each (marks left out, tooltip with the full text), each with a G11 marker: waiting · translating… · translated · to review · translated again alone · kept as is. Markers follow the run state: when paused, the current call's rows are "waiting" | per-row state from the call state + `SegmentOutcomeNote` + `SegmentDrafted` |
| Simple detail | The segment's marker and location (a "to review" one names why), a bordered **Source \| Translation** grid ("Being translated now…" / "Not translated yet." when empty, T4), and **Being translated now**: the sources of the call in flight | — |
| Detailed detail | The info grid (call, attempt, clock, first token, tokens in · cached · out, speed), **Sent to the model** (parsed, G5) \| **The model's reply** (grid by id); stacked in compact; Next call line | `PromptView`, `ReplyView`; per-item reply on `CallSnapshot` |
| Show each segment when translated | On: when a reply arrives, the first newly translated segment is selected, unless the person clicked one (then "Follow again"). Only a click or key turns following off | `LiveCallPanel` |
| Scene summary (T5) | Always under the detail: the current summary and where it comes from ("Updated every 20 segments. On Max quality the model also writes one at the end of each chapter.") | `RollingSummaryKeeper` via `MemoryUpdated` |
| Activity log tab | All · Problems only. Plain lines ("Segments 1–4 of chapter 2 translated in one reply · 3 accepted · 1 to review"); debug lines only with technical details | `control/TaggedLog`, `ActivityLogFeed` |
| All calls… | The progress dialog listing the translation's calls, newest first (last 50 kept); Simple shows each reply matched to its segments, Detailed the full call (replaces `ModelCallsWindow`) | `ProgressDialog` |

The **review panel** lives inside this screen today (`#screen-review`); planned, it is a dialog opened from here.

### Review panel (built today) / Review dialog (planned) {#screen-review}

Purpose: review flagged segments (and, on request, every segment) without leaving the Translating screen.

**Built today** — a panel inside Translating, no navigation entry of its own:

| Control            | JavaFX control              | Behaviour                                                                                                                        |
|-----------------------|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| Filter chips           | `ToggleGroup` of chips          | `all`, `names`, `omissions`, `foreign · kept`, and `All segments` — the one list that also shows segments kept as source, each marked `kept as source` |
| Flagged list           | `ListView`/`TableView`          | Items by locator (e.g. `ch5 · p12`) with a main-finding badge: the highest-severity finding, ties broken `name` > `wrong lang?` > `omission` > `low score` |
| Compare panes          | two `TextArea`                  | Source (read-only) and target (editable)                                                                                          |
| Readable view          | two `TextFlow`s (`#review-source-preview`, `#review-target-preview`) | Under the panes: each `⟦gN⟧` token as a chip (`g0`), and the words a finding quotes marked in the target; shown only when the text holds a token or a found quote; the target follows the editor |
| Findings / context     | `Label`s/chips                  | The segment's QA findings, each with a kind badge (`name`, `wrong lang?`, `omission`) when its kind maps to one, and a line of surrounding context |
| Actions                | `Button`s                       | Save edit / Accept / Revert to machine target / Retry / Retry with note / Skip                                                    |

**Planned — Review dialog (decision 5, F1; T6–T12).** The panel moves into an `.xl` `ModalCard` (`ReviewPanel`,
`ReviewListPane`, `ReviewComparePane`, `FindingRows`, `ReadableView`, `ContextSection`):

- **Opens** from "Review (n)" or the summary strip's "n to review", and **by itself** when a translation finishes with
  segments to review (it waits for a free slot, G1; the desk does not open when nothing needs review, and says "Nothing
  needs your review").
- **Header:** "4 of 6 left — if a segment reads well, press Accept. You can export now and come back later." While
  translating the desk is read-only ("read-only while translating") with the banner "Pause the translation to edit,
  accept or try again." [Pause].
- **Left pane:** a filter (To review (n) · Worth a second look (n) · All segments) and ‹ › to move; rows show the
  location, a **state marker** (to review · accepted · your edit · later) and a one-line "why · text".
- **Editors:** Original (read-only, chips) \| Translation (the editor), sharing the height with the tabs through a
  draggable vertical `SplitPane`. In the editor formatting marks are **protected text marks `⟦1⟧`** (decision 12; a
  `TextFormatter` adjusts changes to whole marks): deleting removes a whole mark, never half; cut and paste can move
  one; a paste keeps only marks missing from the editor. A strip says "Formatting marks ⟦0⟧ ⟦1⟧ keep bold, links and
  similar…", or "⟦1⟧ is missing — paste it back where the formatting ends." The main button stays off while a mark is
  missing or doubled. RichTextFX is not added.
- **Tabs:** **What may be wrong** (the plain reason plus "If it reads well, press Accept."; the rule code and ratio
  only with Show technical details); **What the model saw** (the stored snapshot, parsed, T8; for a batch-drafted
  segment the batch's sent context); **Suggestion** (Now \| Suggested by the check, with "Use the suggestion").
- **Footer:** **More ▾** (Leave for later · Try again · Try again with a note… · Undo my edit, each with what it does);
  the **one main button** **✓ Accept & next**, which turns into **✓ Save my edit & next** once the text is edited (T7).
  Ctrl/⌘+Enter runs the main button; Ctrl+Alt+↓/↑ moves between segments. **Try again with a note** opens an inline
  panel inside the desk, not a second dialog. Feedback is a footer line (G2).

**Target editor.** The editable pane shows, in order of preference: the saved human edit, else the machine target,
else the source (when the segment has neither) — all in **masked** form: `⟦gN⟧` tokens stay visible and a locked
name shows as its locked rendering. Saving an edit that deletes or reorders a token is refused, naming the reason; a
revised segment can be edited again.

**Edit vs accept.** Built today, typing in the target pane disables **Accept** until the edit is either **Save**d or
**Revert**ed, so Accept never silently discards an unsaved edit. **Planned:** the one main button is Save my edit & next
once the text is edited, so the same guarantee holds with a single button. Accept (`SegmentActions`) moves a `FLAGGED`
segment to `ACCEPTED` and confirms an already-`ACCEPTED` segment; it is refused on `PENDING` or `REVISED`, and on a
segment with no machine target (`There is no machine translation to accept — edit it or retry.`). Save edit (REVISED)
keeps its behaviour.

**Retry.** Retry (with or without a note) is allowed whenever no run of the project is currently running; it is
refused as busy while one is. While a retry is in flight, Resume and the other review actions are disabled.

Requirements: FR-ALGO-01, FR-RESUME-03, FR-REVIEW-01..07. P6 reference: Translating, each run state, and Review queue
(side-by-side).

## screen-export {#screen-export}

Purpose: choose the destination and export the translated book.

| Control                   | JavaFX control                | Behaviour                                                                                                                                |
|------------------------------|----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| Save to / Browse             | `TextField` + `FileChooser`      | Output path, defaulted from the source name and target language, with the shared naming rule and an "already exists" warning when overwrite is off; **Suggest name** asks the model for the author and title in the target language, and when the author was kept in the source alphabet a hint under the field says so ("The author's name was kept in Latin — edit it if you want it translated"), gone once the name is edited; while the field is empty a note under it says Export book waits for a path ("Choose where to write the book…") |
| Overwrite                    | `ToggleSwitch`                    | Built today: allows writing over an existing file at the chosen path. **Planned:** no Replace switch — if the file exists, BookLoom asks before replacing it |
| Format                       | read-only `Label`                 | The book's original format (EPUB/FB2/MD/TXT) — export is same-format only; there is **no format chooser** (DD-30, FR-EXPORT-01)          |
| Also export (side files)     | three `CheckBox`es (live)         | Glossary (CSV) / bilingual copy / quality report (FR-EXPORT-05) — each produces its file when checked                                    |
| Final consistency pass       | `ToggleSwitch` (live)             | FR-EXPORT-06 — runs the consistency pass on export at any quality dial setting. **Planned wording:** "Last check before saving" — "Check the uncertain places again — Recommended. Rereads the segments that needed fixing next to their neighbours. Adds about 3 min for this book."; off and disabled for a partial export |
| Checks                       | banner(s)                         | The round-trip and resource checks carried over from the Import screen, re-shown here before writing                                             |
| Counts                       | stat tiles                        | Accepted, flagged, pending, and kept-as-source, named apart                                                                              |
| `Export book`                | `Button`                          | Writes the book; **unavailable while a run of this project is translating**                                                              |
| Open folder                  | `Button`                          | Shows the written file in the system file manager: `open -R` on macOS, `explorer.exe /select,` on Windows (an exit code of 1 still counts as success), `xdg-open` on its folder on Linux |

The partial-export statement names **pending segments** (not yet decided), **segments kept as source by choice** and
**segments kept as is** (a chapter number, a scene break — nothing to translate, decided with no model call) apart, so a
partial export never describes every remaining segment as the same kind of gap. The
**export-complete dialog** (`#dialog-export-complete`) is the export's **only** success notice — no `ok` toast
duplicates it.

**Planned — UI finalization + SQLite (E1–E3, I).**

| Element | Behaviour | Code |
|---|---|---|
| Toolbar | Back · **⬇ Export book** (disabled while translating), then Open folder · Open book (L2) | `ExportView`, `StepFooter` |
| Banner | Translating: "Pause or stop the translation to export. You can export what is done so far; the rest keeps the original text." [Pause]. Paused or stopped: "74 of 142 segments are translated. The other segments keep the original text." | new |
| **Tiles** (E1) | From the project **before** any export: **74 of 142** segments done · translated without changes · reviewed by you · "checked when written" (— until written, then ✓ "opens, nothing missing") | a pre-export source; `ExportResult.reset()` blanks today |
| **Title and author** (E1) | Original (read-only) and translated (editable); when there is none, one line says why ("The book names no author"). Markdown takes the title from its first heading or front matter | `ExportTitleCard`, `BookTitleViewModel.read` |
| **Where to save** | "Books ▸ earth-gravity.uk.md (in your home folder)" with the full path as tooltip · Change… · ✦ Suggest a name; the same format as the original; if the file exists, BookLoom asks before replacing it | `ExportDestinationCard`, `ExportPathRules`, `ExportOccupancy` |
| Also save | Glossary (CSV) · Bilingual copy · Quality report | `ExportSideFilesColumn` |
| **Last check before saving** | As the table row above | `ExportSideFilesColumn` |

States: **populated** (a book is open, ready to export) and **empty** ("No book is open", with a hint to Import;
nothing to reveal).

### Suggest a file name — progress dialog {#dialog-progress-file-name}

**Planned (G4, short).** One step: Sent (title, target, format) \| Answer (the name and how it was made). Footer: "Cancel
keeps the current name." · Cancel · **Use this name**.

### Export · last check before saving — progress dialog {#dialog-progress-export}

**Planned (G4, long; E2, E3).** Groups: **Steps** — Check the translation is complete · Fix he/she forms that became
known (`CONSISTENCY_RETRY`) · Translate unsure segments again (`RETRY_DOUBTED`); **Reread next to the neighbours · 6 of
18** (one item per segment: unchanged · kept · fixed · reading… · waiting); **Finish** — Write the file. Figures: "48 % ·
12 of 25 steps · Left about 2 min". Item detail: a plain sentence, then a bordered **Before \| After** grid (the segment
before, this segment, the segment after; a fix shows the old text struck through above the new one; "unchanged" is said
in words); Detailed adds the parsed prompt and the raw reply note. Footer: left, Continue in background and "Cancel
writes no file."; right, **Write the file now** (asks: "6 of 18 reread, 1 fixed; the other 12 stay as they are") and
**Cancel**, which asks "Cancel the export? No file is written."; Esc hides. On finish **Export complete** opens
(`#dialog-export-complete`). `ExportProgress.Step` is VALIDATING → CONSISTENCY_RETRY → RETRY_DOUBTED →
CONSISTENCY_NEIGHBOUR → WRITING.

Requirements: FR-EXPORT-01..06. Export always writes the book back in its original format; converting to another format
is out of scope (DD-30). P6 reference: Translated book ready / Export.

## screen-settings {#screen-settings}

Purpose: host the six settings tabs.

| Control | JavaFX control             | Behaviour                                                                  |
|---------|------------------------------|------------------------------------------------------------------------------|
| Tabs    | `TabPane` (`#settingsTab`)   | Providers / Models / Generation / Appearance / Automation / Storage & logs |

Each tab's fields, defaults, and ranges are specified in `07_SETTINGS.md`. **Built today:** only the Providers tab is
live; Models, Generation, Automation and Storage are not built, and nothing is remembered across a restart.
**Planned — UI finalization + SQLite (S1–S5, H1, decisions 6–7):** all six tabs, with the contents below. Subtitle:
"Your books stay on this computer. BookLoom sends your text only to the AI model you choose here." Keys are never saved, only
environment-variable names.

**How changes are saved (H1).** **Providers** and **Models** save explicitly through a pinned form footer: "● Unsaved
changes" · Cancel · Save; leaving them with changes (another provider, another tab, navigation, closing the window)
asks **Unsaved changes** — Keep editing · Discard · Save (`LeaveGuard` gains a dirty-form check). The other tabs
**apply at once** and say when: "Changes apply at once", "to the segments not translated yet", or "after BookLoom
restarts".

- **Providers.** List \| form side by side at every width (list 240, or 200 in compact; the form column capped at 860
  px; its footer pinned). Detail header: the provider's name, "current" or **Use this one**, **Delete…** (confirm).
  **Summary card:** "Your AI model: Ollama · gemma4:e4b — ready ✓ · Answers in about 1 second · 7 models available" ·
  Test · Change model. **Connection details** (fold; opens by itself when there is a problem, for a new provider, or
  with Show technical details): the GoText form — Name · Kind (Ollama / OpenAI-compatible) · Sign-in (None · Bearer
  token · API-key header + header name; key from environment variable, "set" / "not set") · Address · **Models
  endpoint** and **Inference endpoint** overrides (the inference one disabled for Ollama, with the reason) · Connect and
  request timeouts · Model (list + ↻, or typed) · Use custom headers (values may be `${env:NAME}`) · Use custom models
  (chips) · **Checks** in plain words ("Ollama answers (41 ms)", "7 models found — the chosen one is there", "The model
  replied in 1.2 s") with **Details**. `ProviderConfig` gains `displayName`, `auth`, `credentialRef`, `modelsPath`,
  `chatPath`, `headers`; header injection is in `:llm` `HttpExchange`; `ProviderVerifierImpl` takes an unsaved config.
  The test dialog is `#dialog-test-provider`.
- **Models** (decision 6). Model (a list across providers, ↻ Refresh), with "Each model keeps its own settings. All are
  optional — off means BookLoom picks the value per task." Found out from the program: how much it can read at once
  (and how much is loaded now) and whether it gives structured answers. **Use one temperature** (slider; off = the
  per-task defaults of `ChatRequests`); **Use context window** (number; filled in from what is loaded; `num_ctx` for
  Ollama in grey); **Limit answer length** (off = `OutputLimit` per request); **Advanced**: the field that carries the
  answer length (`max_completion_tokens` / `max_tokens`; not used for Ollama). Pinned footer (H1).
- **Generation** (applies to segments not translated yet): what the quality choice does (a read-only grid from
  `DialParameters`); Thinking before answering: Off / Low / On (`ReasoningPlan`); Sampling: Program defaults / Tuned by
  BookLoom (`SamplingMode`); Time limits: fixed, `RequestTimeouts`.
- **Appearance:** Theme; Language of the app ("Applies after BookLoom restarts." [Restart now]); **Show technical
  details** (G12). There is no Accent row and no Reduce-motion switch in the mockup README's tab.
- **Automation:** When to stop and ask you (Never — translate unattended · When something needs review · After every
  segment = `ReviewMode` UNATTENDED / ASSISTED / MANUAL); Default quality; Open the review desk when a translation
  finishes with segments to review; Run the last check before saving by default; Keep the computer awake (with the lid
  note); Reopen the last project. There is no OS-notification switch.
- **Storage & logs:** Your projects (saved in, size, saved as it goes, Open folder, Make a backup…); Logs (folder, kept
  10 launches, Detailed diagnostic log, Open log folder, Save a diagnostic bundle…). Paths are shown short with the full
  path as tooltip.

P6 reference: Settings with each tab.

## dialogs {#dialogs}

All dialogs use `Dialog`/`Alert` (or `ModalCard`) in the modal host with scrim; progress dialogs sit on the work layer
(`#busy-card`). Layers, keys and sizes are in `#global-patterns` (G1).

### dialog-welcome {#dialog-welcome}

"Welcome to BookLoom" first-run dialog. Control: `Dialog`. P6 reference: welcome dialog. (The mockup README shows the
first launch as the empty Projects screen, `#screen-projects`; whether the dialog stays is open.)

### dialog-add-edit-provider {#dialog-add-edit-provider}

Add/edit provider with endpoint, kind, auth, and credential-reference fields, plus the Test connection / models /
inference trio (FR-PROV-06). The kind selects the client implementation (Ollama → native client; everything else →
OpenAI-compatible). Model fields come from live discovery when the provider offers it, and always allow **manual
model-ID entry** (a free-text field used when the server has no discovery endpoint — DD-38, FR-MODEL-02). Two model
slots only: translator and judge/helper (no embedding). Controls: `Dialog` with `TextField`/`ComboBox`/editable
`ComboBox` and three test `Button`s each showing a per-stage result. **Planned:** the form lives in Settings ›
Providers (the Connection details fold, `#screen-settings`); the tests open `#dialog-test-provider`. P6 reference: Add
provider dialog.

### dialog-test-provider {#dialog-test-provider}

**Planned (S2).** Test connection / models / inference: a verdict banner first ("The model replied correctly.
Translation can start."); the request line (method and full URL, Copy as curl with the key hidden); three columns —
**Body** (request JSON) \| **Status / Time / First token / Speed / Tokens / Structured** \| **Response** (JSON), each
pane scrolling inside its column so the banner never overlaps; footer: switch between the three tests · Close. Built
today the Providers tab shows the three tests' measured values inline.

### dialog-provider-binding {#dialog-provider-binding}

Resume-time provider/model prompts protecting per-project consistency (DD-31, FR-PROV-09, FR-PROV-10). Two variants,
both `Alert` (confirmation): (a) **bound provider/model unavailable** — the project's bound provider or model could not
be verified; offer to fall back to the current default (only on confirm) or cancel; (b) **settings differ from the
project's last-used** — the current settings default differs from what this project last used; offer *apply the new
provider/model to this project* or *continue with the previously-used one* (default: continue). **Planned wording
(needs the project store):** **AI model not answering** — Try again · Use another AI model… · Not now; **A different
model is chosen** — Use the earlier model · Continue with the new one. P6 reference: Provider/model binding prompt.

### dialog-add-glossary-term {#dialog-add-glossary-term}

**Ships in this build.** Add a glossary term: source, target, type, gender, lock. The dialog refuses a duplicate
source term, and refuses locking a term that has no target (`A locked term needs a target.`). Control: `Dialog`
(`dialog/AddTermDialog`; `AddRecurringTermDialog` for one recurring word or title).
FR-GLOSS-04. P6 reference: Add glossary term.

### dialog-retry-with-note {#dialog-retry-with-note}

**Ships in this build** as a dialog: retry a flagged segment with a free-text instruction, plus a checkbox offering
**lower temperature for this retry**. Control: `Dialog` with a `TextArea` note field. **Planned:** "Try again with a
note…" is an **inline panel inside the review desk**, not a second dialog (F1, `#screen-review`). FR-REVIEW-A2. P6
reference: Retry with note.

### dialog-confirm-irreversible {#dialog-confirm-irreversible}

**Ships in this build.** A yes/no card (`#confirm-card`) asked before something that cannot be undone, in the modal host:
**Stop the run** (from the Translating screen's Stop and the banner's Stop; decided segments are kept; **Planned**
title **Stop translating?**, buttons Keep translating · Stop, text in `#run-states`), **Discard your
edit** (the review panel's Revert, only when the segment holds the person's own edit), **Discard this project** (Import's
Cancel, only when the project holds work: a run began or a later step is done) and **Quit BookLoom** (closing the
window while a run or any activity is under way; confirming stops the work and closes). The safe answer is
the default: **Cancel** (`#confirm-cancel`) is the default button and takes the focus, Escape and a click outside do
nothing but close the card, and only the confirming button (`#confirm-yes`, worded for the action and in the danger
look) runs the action. Review's Skip discards nothing and asks nothing. A card whose answer something waits on (the
replace-run question) is not closed by Escape. Control: `Dialog`. P6 reference: Confirm dialogs.

### dialog-confirm-delete {#dialog-confirm-delete}

Confirm deleting a provider (or, Planned, removing a project: `Remove project…` deletes this project's saved work only).
Control: `Alert` (confirmation). P6 reference: Delete provider?.

### dialog-unsaved-changes {#dialog-unsaved-changes}

Warn on navigating away from unsaved edits. Control: `Alert` (confirmation). **Planned:** in Settings › Providers and
Models only — Keep editing · Discard · Save (H1). P6 reference: Unsaved changes.

### dialog-error-with-details {#dialog-error-with-details}

Error dialog with an expandable technical-details section surfacing the typed `AppError` safe details (FR-NOTIF-03,
FR-NOTIF-04): a plain message, "Details for support", Copy details (`notify/ErrorDialog`). Control: `Alert` (error)
with expandable content. P6 reference: Translation failed.

### dialog-export-complete {#dialog-export-complete}

**Ships in this build.** Shown once the run's export stage finishes writing the book: the written path, the format,
and an `Open folder` action that shows the file in the system file manager. It is the export's **only** success
notice — no `ok` toast duplicates it. **Planned content:** Saved as (wraps), Check, Size, Also saved, Last check "18
segments reread · 1 fixed"; Close · Open folder · Open book. Control: `Dialog` (`dialog/ExportCompleteDialog`). P6
reference: Export complete.

### dialog-about {#dialog-about}

About dialog (product name, version, MIT license). The version comes from the build-generated version resource
(`AppVersion` reader — DD-50, FR-UI-09): the tag-injected full version in release artifacts, **`dev`** in any
non-release build. Control: `Dialog`; the only dialog that closes on a click outside. P6 reference: BookLoom (about).

### Other dialogs {#dialogs-other}

**Planned** unless a built dialog is named (mockup README, "Other dialogs"):

| Dialog | When | Code |
|---|---|---|
| Add to the glossary | + Add (built) | `dialog/AddTermDialog`, `AddRecurringTermDialog` |
| Who tells the story? | Start, first-person story with an unknown narrator gender (Enter = not stated) (built) | `dialog/NarratorDialog` |
| Characters without a gender | Start; its count equals its rows (built) | `dialog/CharacterGendersDialog` |
| Stop translating? | Stop — Keep translating · Stop | `ConfirmDialog` |
| Unsaved changes | Leaving Providers or Models with changes — Keep editing · Discard · Save | new |
| Cancel the export? / Write the file now? | Export dialog footer — they open above it (G1) | `ConfirmDialog` |
| Delete provider / Remove project | Settings / Projects | `ConfirmDialog` |
| Open another book? | Dropping a book while a translation exists — Open as a new project (primary) · Replace and delete this translation · Cancel | `ReplaceRunDialog` |
| Model work is running | Leaving during long work — Stay · Continue in background | `LeaveDialog`, `LeaveGuard` |
| Error with details | Unreadable file and other hard failures | `notify/ErrorDialog` |
| AI model not answering / A different model is chosen | Continuing a project — Try again · Use another AI model… · Not now; Use the earlier model · Continue with the new one | new (persistence) |
| Same language pair · Rename · Translate into another language | Project names (`#project-names`) | new |
| About | Title bar (built) | `AboutDialog` |

## toasts {#toasts}

Toasts (native token-styled JavaFX nodes stacked in the shell) come in four types — ok / info / warn / err — specified
with their triggers in `11_NOTIFICATIONS_AND_ERRORS.md#toasts`. FR-NOTIF-01. P6 reference: Notifications.
**Planned (G2):** bottom-right, 16 px inset, 360 px wide, newest at the bottom, at most 4; success and info stay 5 s,
warning, error and toasts with an action 12 s; each has ✕; one optional action (`ToastAction`, "Review the results");
while a dialog or visible work is open, feedback is a line in its footer, not a toast.

## banners {#banners}

Persistent banners in three severities — info / warn / err — e.g. language-mismatch (warn), DRM-blocked (err),
provider-error (err). FR-NOTIF-02. Specified in `11_NOTIFICATIONS_AND_ERRORS.md#banners`. **Planned (L3):** run banners
(provider unreachable, stuck, gave up, model missing, cannot start, welcome back, finished) and the hidden-work banner
sit in the pinned slot `#shell-banners` and show on every screen while they apply, with "Go to Translating"; their
wording is the Banner column of `#run-states`.

## empty-states {#empty-states}

Every list screen has an empty state (FR-NOTIF-05): Projects (first launch, `#screen-projects`; "No project matches
'xyz'." for a search with no hits), the review desk ("Nothing needs your review"; built today the Translating screen's
review panel says "Nothing flagged"), Providers (no providers configured), the Import text browser ("Nothing matches
'xyz'."), and the Names & style glossary and Recurring terms tables ("No names yet. Scan the book for them, or add one."
and "No recurring terms yet. They are found when you prepare names, or while translating."; "No name matches the search"
while a search hides every row). P6 reference: each empty state in the mockup.

## control-mapping-summary {#control-mapping-summary}

| Mockup widget      | JavaFX control                    |
|---------------------|--------------------------------------|
| Segmented picker   | `SegmentedButton` / `ToggleGroup` (+ the 2 px selected bar, G11) |
| Side-by-side panes | two `TextArea`                    |
| Data table (editable) | `TableView`, `FLEX_LAST_COLUMN`, fixed cell size |
| Read-only grid     | bordered `GridPane`               |
| List \| detail split | `SplitPane` with divider positions and minimum sizes |
| Chapter tree       | `TreeView`                        |
| Prompt section     | `TitledPane`                      |
| Formatting marks   | detail panes: `TextFlow` chips; editors: `TextArea` + `TextFormatter` |
| State marker       | `Label` + Ikonli icon + word      |
| Toggle             | `ToggleSwitch`                    |
| Tabs               | `TabPane`                         |
| Dialog / alert     | `Dialog` / `Alert` / `ModalCard` on `ModalHost`; progress dialogs on the work layer |
| Toast              | Native token-styled nodes (`ToastStack` + `ToastAction`) |
| Ticking figures    | `FigureLabel` (no re-layout per tick) |
| Width classes      | `LayoutClass` pseudo-classes (no CSS media queries in JavaFX) |
| Icon               | Ikonli                            |

No new library is needed (JavaFX 26, ControlsFX, Ikonli Feather); nothing needs a second `Stage`.
