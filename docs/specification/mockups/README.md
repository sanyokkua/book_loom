# UI Mockup — Source of Truth

`ui-mockup.html` is the **binding visual and interaction source of truth** for the application UI. It is a
self-contained, clickable prototype: open it in any browser. Where the written specification and the mockup diverge
on a *visual or interaction* detail, **the mockup wins** and the spec clause is corrected. Where they diverge on
*behaviour or data*, the functional spec (`../01_Product/`) wins.

**Where this revision comes from.**
- The owner's annotated review of the last build (2026-10-19).
- A final review from four sides: UX design, JavaFX feasibility, a first-time non-technical user, and window sizes from
  960×640 to 1920×1080. Its findings went through option reviews and the owner's decisions.

It is the target for the next change, **finish the UI and keep progress in SQLite**. The proposal
`../../proposals/2026-10-19-ui-finalization-and-persistence.md` holds the observations verbatim, every decision, the
research and the code facts. This README describes every screen and dialog: what each part does, its states, and
where it lives (or will live) in the code.

## How to read the mockup

- **Mockup preview bars.** The dashed bars switch demo states: run state, inspection verdict, project open or not,
  model found or not, provider kind and auth, empty lists. They are not app UI.
- **Requirement badges.** Small blue badges such as `T4` or `L2` name the requirement an element answers. The IDs are
  in the proposal.
- **Mockup-only buttons.** "Mockup: finish →" and "Mockup: reply arrives" exist only to show later states.
- **Reference pages.** The sidebar group "Mockup reference" holds the Component library, Dialogs, Notifications and
  **Run states** pages. They are not shipped.
- **Code paths.** They are relative to `modules/ui/src/main/java/ua/bookloom/ui/` unless they start with a module name.
  **New** marks what the next change builds.
- **The sample-data fixture.** The `D` object at the top of the script holds the sample figures, and every figure on
  every screen comes from it. `console.assert` lines check the sums (decided = kept + translated + fixed + to review;
  steps done ≤ total; review desk rows = to review).

## For AI agents: building the JavaFX UI from this mockup

The mockup is HTML, CSS and JavaScript; the app is JavaFX. **Nothing is copied across.** The mockup shows what the
window must look like and how it behaves; the JavaFX code is written separately, and the two are compared **by
eye, from screenshots**. Follow these steps for every screen, state or dialog you build or change.

1. **Render the mockup in a browser.**
   - Use the built-in browser (`mcp__Claude_Browser__*`).
   - A `file://` URL may not load in a new tab. Serve the folder instead:
     `python3 -m http.server 8799 --bind 127.0.0.1 --directory docs/specification/mockups`, then open
     `http://127.0.0.1:8799/ui-mockup.html`. Stop the server when done.
   - Set the window size with `resize_window`: **960×640** (the minimum), **1280×800** and **1920×1080**.
   - Reach the state you need with the page's own functions in `javascript_tool`, or with the dashed demo bars:
     - `go('translating')`, `setRun('stuck')`, `setSet('models')`;
     - `openDialog('review')`, `openWork('exportRun')`, `openNames('auto')`;
     - `setTheme('dark')`, `ST.liveMode = 'detailed'; rerender()`.
   - The **Run states** page lists every run state; its first column opens the app in that state.
   - Take a screenshot of each state at each size, in light and dark.
2. **Render the app in the same state and size.**
   - Run it (`./gradlew :app:run`, or the `scripts/e2e-fixture.sh` path for a run with the pseudo model).
   - Bring it to the same state, with the same window size and the same theme.
   - Capture the **app window** with computer use, or `screencapture` on macOS (see the project memory "Driving the
     JavaFX app": computer use may not see the java process). Headless TestFX snapshots are fine for repeat checks.
3. **Compare the two screenshots side by side** and list every difference. "Close enough" means:
   - the same regions, in the same order and hierarchy;
   - the same controls, with the same words from the bundles (EN first; UK must fit too);
   - the same pinned items: toolbar actions, banner slot, dialog footers;
   - the same states and markers;
   - colours from the same token roles;
   - sizes and spacing within about 4 px or 10 %.

   It does **not** mean pixel-equal: fonts, antialiasing and control chrome differ. Fix the app and compare again
   until the list holds only those accepted differences. Put the screenshots pair and the list in the change's
   evidence.
4. **Never bring mockup-only things into the app.** These exist only to explain or demonstrate:
   - the sidebar group **"Mockup reference"** (Component library, Dialogs, Notifications, Run states);
   - the dashed **"Mockup preview"** bars and every switch or select inside them;
   - the small blue **requirement badges** (`G4`, `T2`…);
   - the **"Mockup: finish →"** and **"Mockup: reply arrives"** buttons;
   - the ⋮ resize glyph in table headers (the app resizes column edges);
   - the **sample figures** of the fixture `D` and the `console.assert` lines. The app shows its own data from the view
     models.
5. **Translate the technique, not the markup.** Map each widget to its JavaFX control (see "JavaFX implementability"):
   - CSS colours → `theme.css` token roles. Never a hex value in Java or FXML.
   - `@media` widths → the `LayoutClass` helper (L1).
   - `details` → `TitledPane`.
   - Flex and grid rows → `HBox` / `VBox` / `GridPane` / `SplitPane`.
   - Hover titles → `Tooltip`s, and every operable control has one.
   - Inline token chips → `TextFlow`, in detail panes only.
   - Text → `MessageKey` entries in `messages_en` and `messages_uk`. The mockup's English is the source wording, and
     the Ukrainian is written for it.
6. **Behaviour comes from this README**, not from the mockup's script. The script fakes data and timing.
   - Where the mockup and the README disagree on a *visual* detail, the mockup wins.
   - On *behaviour or data*, the README and `../01_Product/` win.
   - If the implementation must differ for a real reason (a JavaFX limit, a measurement), change the mockup and this
     README in the same change and say why.
7. **Prove it with tests too.** Use the conformance tests by node id, `LayoutFitTest` at 960×640 and 1920×1080 in `en`
   and `uk`, and `ContrastTest` for any new colour pairing. A screenshot comparison is the visual check; the tests keep
   it from drifting.

## What CI checks against this file

Only `ThemeTokenCatalogueTest` reads the mockup (the path is passed as the `bookloom.mockup` system property in
`modules/ui/build.gradle.kts`). It takes the **first** `:root[data-theme="light"] { … }` block and requires exactly
**53 token names** that equal `theme.css`'s catalogue minus `toggle-off`, `toggle-on` and `toggle-thumb`. This
revision keeps that block byte-identical. To add a token, also change `theme.css` (light and dark), the test's expected
sets and `../01_Product/09_THEMING.md`.

Everything else (layout, ids, content) is free to change; the conformance tests use the app's own node ids.

---

## Global patterns

### G1 — Dialogs live in the window, on two layers
- **Two layers.**
  - **The work layer** (today's `BusyHost`) holds the progress dialogs of model work (G4).
  - **ModalHost**, above it, holds everything else: questions, confirmations, errors, the review desk, the tests and the
    forms.
  - A question opened during work ("Cancel the export?", an error) appears **above** the progress dialog, which keeps
    its state underneath (JX-G3).
- **One card per layer.**
  - A dialog the person did not ask for, such as the review desk opening at the end of a run, **waits** until no dialog
    is open and no work is visible (`ModalHost.showWhenFree`; `openWhenFree` in the mockup script).
  - A dialog that replaces another returns to it: Retry with a note was such a case, and is now an inline panel (F1).
- **Keys.**
  - Focus is trapped in the card (Tab wraps around).
  - **Esc** closes a dialog. On a progress dialog it *hides* long work (E1), the export's last check included (only
    its Cancel button asks "Cancel the export?"), and cancels short work.
  - **Enter** (or Ctrl/⌘+Enter inside a text area) runs the footer's main button.
  - Only About closes on a click outside.
- **Sizes.**
  - `.dialog` is 480 px (a confirmation) and `.wide` is 720 px (a form or result).
  - `.xl` is min(1180, 92 % of the window) wide and has a **fixed** min(820, 90 %) height, so its footer never moves
    as the content changes (JX-G1). It is used for progress dialogs, the review desk and the tests.
  - The header (icon, title, one-line purpose, ✕) and the footer (the safe action first, the main action last) are
    fixed; only the body scrolls.
- **No second OS window** except the native file choosers.
- **Code.**
  - `ModalHost` (scrim, focus trap, Esc, focus return) and `dialog/ModalCard` stay.
  - Add a size variant with a fixed xl height, a `ScrollPane` body, and an Enter/Esc policy in the key filter: default
    button, `returnTo`, ask-first.
  - `BusyHost` becomes the work layer; its small card and `#busy-show-calls` go.
  - `control/ModelCallsWindow` (`new Stage`) is removed.
  - A small `showWhenFree` queue in `ModalHost`.

### G2 — Toasts in the bottom-right corner
- Toasts stack bottom-right, newest at the bottom, at most 4.
- Success and info stay 5 s; warning, error and toasts with an action stay 12 s. Each has ✕.
- A toast may carry **one action** ("Review the results").
- **While a dialog or visible work is open, its feedback is a line in its own footer**, not a toast, so nothing lands
  on its buttons.
- **Code.** `notify/ToastStack` (`#shell-toast-host`) is anchored `BOTTOM_RIGHT` with a 16 px inset and a 360 px width;
  `ToastOccasion` is the closed list of occasions; `DialogFeedback` routes a message to the open dialog's footer.

### G3 — Visible scrollbars
- 12 px wide, with a `muted-2` thumb on a `surface-2` track, shown whenever content overflows. They are never overlay
  bars that hide themselves.
- **Code.** In `theme.css` set `.scroll-bar:vertical { -fx-pref-width: 12 }` and the horizontal height, the thumb
  `-color-muted-2` and the track `-color-surface-2`. The nav column uses `nav-fg-muted` on `nav-bg-2`.
  - JavaFX has no minimum thumb length, so on very long lists the thumb is about 18 px (JX note). That is accepted.
  - `ContrastTest` checks the thumb at 3:1.

### G4 — The progress dialog: one pattern for model work (E1)
It is used by:
- Fill in the brief from the book;
- Prepare names automatically and the Scan / Review / Translate card actions;
- Suggest a file name;
- Export · last check before saving;
- All model calls of the run.

| Part | Content | Behaviour |
|---|---|---|
| Header | Title, purpose line, determinate bar | The bar counts every step of every task of the operation (G10). Its tooltip says "n of N steps" |
| Figures line | **"51 % · 26 of about 49 steps · Left about 1 min"**; in Detailed also elapsed, speed and task figures | Task and item counters live in the list rows ("25 requests so far"), not in the figures line |
| Simple / Detailed | Simple: one plain sentence for the selected step (`.say`) and Input \| Output in plain words. Detailed: adds the info grid (attempt, a clock against the timeout, first token, tokens, speed), the call kind code and the parsed prompt (G5) | Opens in Detailed when **Show technical details** is on (G12) |
| Follow current step | Switch | On: the list selects the step the work is on. **Only a person's click or key turns it off** (never a selection made by the code, JX-T3) |
| Left list | Steps or items in groups, each with a marker (G11) and one result line, cut with a tooltip | Finished steps stay selectable with their data; a step not started says so |
| Footer | Left: **Continue in background** (long work only) and one line saying what Cancel does ("Cancel writes nothing."). Right: Cancel, then the result action | The export pass also offers **Write the file now** |

**Continue in background (owner decision 11).**
- Only long work can be hidden: the names pass and the export pass. Short work (brief suggestion, file name, tests)
  cannot.
- Hiding collapses the dialog into the title bar's **activity chip** ("▬ Names · 53 % · open"). Clicking the chip
  reopens the same dialog with its state.
- While it is hidden, **the screen it writes to is read-only** behind a banner ("Names is running in the background —
  this screen is read-only until it ends").
- Other model work is refused with a toast that offers "Show progress".
- When it finishes, the chip goes and a toast with **Review the results** appears. Nothing pops up by itself.
- **Code.**
  - New `control/ProgressDialog` (a card on the work layer) and a `ProgressDialogModel` (steps, figures, selection,
    follow, mode).
  - It is fed by `state/CallFeed` / `ActivityTracker.Handle.calls(...)` plus a **step source** per task, with the step
    names from the pipeline:
    - brief: `book-knowledge`, K × `brief-suggestion` (K = 3/4/5 by dial), `brief-synthesis`;
    - names: the `NamesPassImpl` stages;
    - export: `ExportProgress.Step`.
  - `ActivityChip` exists and shows hidden work.
  - The consistency pass on Max can list thousands of items: use a virtualised list of small row records, look up the
    detail by segment id, and keep full prompts only for the last 50 calls (JX-E1).
  - Clocks and figures tick in their own layout roots (`FigureLabel`), so a tick never re-lays out the dialog. Engine
    publications are coalesced to about 10 a second.

### G5 — Parsed prompts and replies, in Detailed views only
- **Messages.**
  - Each message is a card headed by its role (**SYSTEM**, **USER**), with its part count (ICU plural), tokens and Copy.
  - Inside, each `PromptSection` is a collapsible block.
- **Slots.**
  - Rule lists (`styleSheet`, `languageRules`) are bullet lists.
  - Name and term slots are a Term \| Rendering \| Note grid.
  - `precedingPairs` / `relevantPairs` are a Source \| Translation grid.
  - `items` are an Id \| Segment \| Text grid from the `<s id="N">` lines.
- **Formatting marks.**
  - In **detail panes**, `⟦gN⟧` are chips (`TextFlow`).
  - In **one-line rows** (lists, tables) the marks are left out, and the full text with marks `⟦1⟧` is the tooltip: a
    `TextFlow` cannot be cut with an ellipsis (JX-T2).
  - In **editors** they are protected text marks `⟦1⟧` (F1).
- **Replies.** A JSON reply becomes an **Id \| Segment \| Translation returned \| Result** grid. Each batch id is
  mapped back to its segment. `{"unchanged":true}` reads "unchanged".
- **Raw.** "Raw messages, exactly as sent" and "Raw reply" are collapsed blocks with Copy, for diagnostics.
- **Code.**
  - `control/PromptContextPane` and `ReplyPane` are replaced by a `PromptView` (`TitledPane` per section) and a
    `ReplyView`.
  - `CallSnapshot` gains the parsed batch items (`BatchReplyParser` result: id → target, `ItemStatus`) through
    `CallSnapshots.answered`. This is a small `:api` change.

### G6 — Workflow steps need an open project
Without an open project, steps 1–5 are greyed and unclickable, with a tooltip and the hint "Steps unlock when a project
is open". Projects and Settings stay available. The open project's name shows in the title bar, not in the sidebar.
- **Code.** `NavColumn` + `StepLocks` / `StepAvailability` add the "no project" rule. `ViewNames.PROJECTS` gets a
  screen.

### G7 — Five steps
Import (with the book's text browser) · Book Brief · Names & style · Translating · Export. Review is a dialog. This
matches `ViewNames`.

### G8 — No fixed, non-actionable rows
There is no Accent row and no decorative text; each screen has at most one purpose line.

### G9 — Two kinds of table (JX-G4)
- **Editable or sortable lists** (Glossary, Recurring terms, Projects search results) are `TableView`s:
  - every column resizable (`CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN`, with minimum widths such as Term 140, Kind
    96, Translation 170, Gender 110, Locked 64, action 36);
  - grid lines and a header that stays visible;
  - **fixed row heights** (46 px, as today; long lists must stay virtualised);
  - a cell's key text is never cut by something else in the cell. A changed row is marked by its **left edge**, never
    by a chip in the term cell. The full text is in the tooltip.
- **Read-only grids in dialogs** (findings, reply items, breakdowns, Previous \| Translated) are bordered `GridPane`s
  that are **not** resizable. Text in them wraps.
- In the mockup the ⋮ grip only illustrates resizing.

### G10 — Progress and time left count all the work (PR)
1. **The unit is a step, counted over the whole operation.** All tasks and their items are counted: 3 tasks × 3 items
   are 9 steps.
   - **Translation run:** a step per segment draft, one per check the quality dial plans for each translated segment
     (Fast 0, Balanced 1, Max 2), plus Max's chapter summaries and backward revision.
   - A kept-as-is segment is a draft step done at once.
   - **Dialogs:** each step's items (requests, or 1 for a code step).
2. **Unknown counts are estimated** and marked "about" until known.
3. **Work found on the way** (a fix after a failed check, a re-ask, a resend after a timeout) joins the total when it
   starts. The bar never moves back; it holds until the work catches up.
4. **Time left** = Σ over step kinds (steps left × time per step of that kind), measured over the **whole** operation
   and excluding pauses.
   - A kind not run yet borrows the draft time scaled by its size (a check ≈ 0.6 × a draft).
   - It is shown after 5 steps or 3 %, rounded, as "about …", and moves at most 10 % a minute.
   - The owner's sanity check: 52 % in 2 h → about 2 h left.
5. **What the screens show.**
   - **The bar** and the title bar show the share of the work: "51 % done".
   - **Text lines** count segments: "74 of 142 segments done".
   - The two are never printed side by side as two percentages (GL-3); the bar's tooltip says "140 of 276 steps,
     checks included".
   - **ⓘ How progress is counted** opens the breakdown per kind of step: planned, done, measured time per step.
   - **Estimates before Start** appear only from a measured speed (the inference test or an earlier run), always marked
     "estimate". Without one, the screen shows relative cost (Balanced ≈ 1.5× Fast, Max ≈ 3×).

**Why the old estimate was wrong (replaced by 5.4).**
- The former `state/RunClock.timeLeft(pending)` multiplied the average of the **last 20** segments by **all pending segments**, so
  one slow stretch is projected over the rest of the book.
- The former `RunFigures.percent` counted segments equally, including instant kept-as-is ones.
- Checks and Max's work after the last segment are in neither figure.
- That gives "2 h passed, 52 %, 10 h left".

**Code.** `state/ProgressLedger` replaced the segment percentage of `RunFigures` and the window of
`RunClock`. The pipeline announces planned and added steps: `DialParameters`; the repair, re-ask and timeout paths;
`NamesPassImpl`; `BriefSuggester`; `ExportProgress.Step`.

### G11 — State markers and one colour map
- Every state is an **icon + word** (Ikonli Feather in the app). The mockup's ○ ◐ ✓ ⚑ ✕ = glyphs are placeholders:
  ◐ and ⚑ are missing from common Windows and Linux fonts (JX-G5).
- One colour per meaning, everywhere (markers, chips, badges, the title-bar chip):

  | Colour | Meaning | Examples |
  |---|---|---|
  | **grey** | waiting, queued, idle | "waiting", "Model idle" |
  | **blue** | working | "translating…", "reading…", "Model answering" |
  | **green** | done | "translated", "accepted", "done" |
  | **amber** | needs you or slow | "to review", "No answer for 3:40" |
  | **red** | failed or unreachable | "Cannot reach Ollama", "Model missing" |
  | **sand** | kept as is | "kept as is", "skipped" |

- "Waiting" means **queued only**. The model at work is "translating…" or "answering"; "thinking" is reserved for a
  reasoning phase in Detailed.
- **Links** are always underlined and use `--info-fg`, which passes 4.5:1 on every surface in both themes (lowest
  4.60 on selected rows). No new token is needed, and `ContrastTest` covers the thin margins.
- **The selected segment** of a segmented control gets a 2 px `--text-strong` bar besides the fill: the fill alone is
  1.24:1. A `--primary` bar would fail (2.86:1).

### G12 — Simple first, technical details on request
- Main screens use plain words (see **Words**).
- Chunks, calls, tokens, attempts, rule codes and wire names appear **only** in Detailed views, in the Advanced folds,
  and when **Settings › Appearance › Show technical details** is on.
- That switch is off by default and remembered. It makes every Simple / Detailed view open in Detailed, opens the
  Settings folds, and shows the technical lines (rule codes in the review desk, debug lines in the log).
- There is no third mode.

---

## Layout and sizes (L)

The window minimum is **960×640** (`AppShellView`). The mockup's CSS mirrors the rules below; in the app they are Java
(JavaFX has no media queries, JX-G8).

| ID | Rule | JavaFX |
|---|---|---|
| **L1** | **Width classes from the content width**, not the window: **compact** < 1040 px · **regular** 1040–1439 · **wide** ≥ 1440, with **16 px hysteresis** (at least the scrollbar width, so the wider scrollbar cannot make a layout flip). Each region still decides its own two-column switch from its own width, as `TwoColumns` does; the classes drive CSS only | One `LayoutClass` helper: a width listener on the content host sets pseudo-classes `compact` / `regular` / `wide`. No logging in the listener |
| **L2** | **The toolbar is the one action bar.** Each screen's main and run actions sit there, always visible: Import → Continue; Brief → Back · Continue; Names → Back · Start translation (or Go to Translating); **Translating → Back · Pause / Resume · Stop · Review (n)**, or Start / Continue / Try again now, by run state; **Export → Back · ⬇ Export book**, then Open folder · Open book; Projects → + New translation. In compact the Back label shortens to "‹ Back", with the full label as tooltip | `StepFooter.claim()` already mounts buttons in `#shell-actions` |
| **L3** | **A pinned banner slot** between the toolbar and the scrolling content holds the run banners (provider unreachable, stuck, gave up, model missing, cannot start, welcome back, finished) and the hidden-work banner. Run banners show on **every** screen while they apply, with "Go to Translating" | A `VBox #shell-banners` in `AppShellView` above the `ScrollPane` |
| **L4** | **Content caps**: form and list screens (Projects, Import, Brief, Export, Settings) at **1320 px**; prose and hints at **680 px**; Translating and Names & style use the full width. **Input widths by kind**: number 120, name 360, URL or path 640, model 480 | `maxWidth` on a centred content column; `prefColumnCount` / `maxWidth` per field |
| **L5** | **Screens that fill the window**: Translating (the live card, min 460 px high), Import (the text browser, min 360 px high), Names & style (the two tables), Settings (so the form footer stays in view at 960×640). Below those minimums the page scrolls. They grow to the window height instead of scrolling in a scroll | A `fills-viewport` style-class opt-out from the `minHeight = USE_PREF_SIZE` that `AppShellView.showContent` applies to every screen today |
| **L6** | **Never stacks: list \| detail splits.** The live panel (list 240 px … 34 %), the review desk and progress dialogs (list 220 px … 30 %), and Settings › Providers (list 240, or 200 in compact) stay side by side at every width. In compact, Detailed Input \| Output stacks inside the detail pane | `SplitPane` with divider positions and min sizes |
| **L7** | **Stacks in compact**: Names & style's two cards (stacked below wide, since six columns need about 640 px); the Import browser (Chapters \| Segments on top, Text below, 200 px); two-column card rows (Brief, Export); four tiles go 2 × 2 | Region width rules (`TwoColumns`), `GridPane` constraints |
| **L8** | **Truncation**: one-line text is cut with an ellipsis **and** gets a tooltip only when it is cut (one overrun helper, R4-G4). Long text wraps in detail panes and dialogs. Nothing important is hidden behind a hover | A small `Overrun` helper on `Labeled` |
| **L9** | **Nav width**: 236 px, or 200 px when the window is narrower than 1200 px. No icon rail | `NavColumn` pref width |
| **L10** | **Ukrainian** labels are about 26 % longer: every fit test (title bar, toolbar, dialog footers, segmented controls in narrow panes) also runs under `uk` (JX-I2) | `LayoutFitTest` (grown from the former title-bar fit test) |

---

## Words (W) — user-facing vocabulary

The owner kept **"segment"** as the unit of text (decision 14). Segment is explained once, in the tooltip of every
segment count: "A segment is a paragraph, heading or caption — one piece of the book's text." All other words follow
this table. Ukrainian follows the same rules: one idea per sentence, ICU plurals for every count, no gendered past
tense for "you".

| Avoid on main screens | Use | Kept for Detailed / Advanced |
|---|---|---|
| chunk, batch, call, item | "segments 5–8", "Now translating: Chapter 2, segments 5–8" | "Call 42 · Draft · 4 segments · attempt 1 of 2 · chunk 3 of 9" |
| tokens, tok/s, LLM, JSON, endpoint | — (the title bar keeps "9.4 tok/s" with the tooltip "how fast the model writes") | Detailed, Settings › Connection details |
| provider (in sentences) | "AI model"; banners name the program ("Ollama") | Settings tab "Providers", Connection details |
| flagged | "to review" (counts, chips, buttons "Review (6)"), "need your review" (sentences) | rule codes in the review desk's technical line |
| suspicious | "worth a second look" | — |
| auto-accepted / repaired & accepted / remaining | "translated" / "fixed automatically" / "left" | the breakdown keeps "accepted", "repaired" |
| fell back | "translated again alone" | tooltip: "the batch reply lacked this id" |
| omission · ratio 0.29 | "may be missing words — if it reads well, press Accept" | `omission · length ratio 0.29, expected 0.35–3.6` |
| run (noun) | "translation", "translating" ("Stop translating?") | Projects, Detailed |
| Retry / Send again / Retry now | **"Try again now"** (the one retry verb for a run); **"Try again"** is a check the person starts: the Projects AI-model line (repeats the look) and the continue dialogs (re-asks the one model) | — |
| Skip segment | "Skip these 4 segments" (always the count) | — |
| Stay paused | — (the toolbar's Pause does it) | — |
| Suggest with model | "✦ Fill in from the book" | — |
| Foreign-language passages | "Text in other languages" | — |
| passages (brief samples) | "samples" ("3 of 4 samples agree") | — |
| Transliterate | "Spell in Ukrainian letters" (by target language) | — |
| Image alt-text, navigation labels, front matter | "Picture descriptions", "Table of contents and chapter titles", "Copyright and edition notes" | — |
| consistency pass, canonical-equal, round trip | "Last check before saving", "checked: opens, nothing missing" | technical line on Import |
| BOOKLOOM_* and wire names in hints | the plain label first; the wire name in grey (`num_ctx`) only in Advanced | — |

The **Glossary**, **Recurring terms**, **Scan · Review · Translate** and **Locked** keep the owner's names (15i.U5).
Their tooltips say what they do in plain words ("Locked — always use exactly this spelling").

---

## Run states (A)

The mockup's **Run states** page lists every state, and its rows open the app in that state (the `RUN` table in the
script). Every surface follows its row: the title bar, the AI-model chip (colour by G11), the toolbar (L2), the pinned
banner (L3), the live panel's line and the markers of the current call's segments.

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

**Pause vs Stop.**
- **Pause** is a break inside this translation: the segments in flight are translated again on resume.
- **Stop** ends translating for now, for example to close the project; Continue translation picks up later.
- Both keep everything, and Stop is a secondary (not red) button.
- The Stop dialog says: "74 of 142 segments are done and saved. The 4 being translated now are translated next time.
  For a short break, use Pause."

**Code.** `RunState` (8 values) plus the recovery and call details (`RunNotice`, `RunInterventions`, `PauseNotice`,
`ActivityTracker`) already exist. The README table is the mapping the conformance test should check.

---

## First launch and the AI model (owner decision 13)
- **On the first launch**, BookLoom looks **once, by itself** for Ollama (`127.0.0.1:11434`) and LM Studio
  (`127.0.0.1:1234`) on this computer. If it finds one with models, it creates the provider and selects the only model,
  or asks which one when there are several.
- This is the **one exception** to "no network call the person did not start". It is local only, happens once, needs
  no answer, and degrades silently.
  - AGENTS.md's non-negotiable and `.claude/rules/offline-and-privacy.md` need the exception written into them (an
    ADR) in the next change.
  - After that, every check is user-triggered (Settings tests, "Try again").
- **Empty Projects** shows the result:
  - "AI model: Ollama · gemma4:e4b — ready ✓ (found on this computer when BookLoom first opened)";
  - or **No AI model found**: "Ollama is not open. Open the Ollama app, then press Try again." [Try again] and "I use
    another program…" (opens Settings).
- **Settings › Providers** opens with the same **summary card** ("Your AI model: Ollama · gemma4:e4b — ready ✓
  [Test] [Change model]").
  - The owner's GoText form stays unchanged inside the **Connection details** fold (decision 7: the fold is a
    container, not a removal).
  - The fold opens by itself when there is a problem, for a new provider, or with Show technical details.

## Changes while translating (I)
- **Names & style**: editable. Banner: "Changes apply to the next segments translated." The glossary is read again for
  every group of segments.
- **Book Brief**: **read-only while translating (and while waiting for the AI program)**, with "Pause the translation to change the brief. Changes apply to
  segments not translated yet." [Pause].
  - The engine rereads the brief's switches only at resume, and it is not proven that tone and style are rederived.
  - **The languages are locked** once translating has started ("To translate into another language, use Translate into
    another language on Projects").
- **Provider or model change** during a translation asks first, in the resume dialog's words.
- **Export**:
  - **disabled** while translating, pausing or stopping, with "Pause or stop the translation to export";
  - **allowed** when paused, stopped or failed, with the banner "74 of 142 segments are translated. The other segments
    keep the original text";
  - the **last check is off and disabled for a partial export** ("Runs when the whole book is translated").
- **A screen another job writes to** (names pass, export pass hidden in the background) is read-only (G4).
- **One model request at a time** (single-flight inference, the `InferenceGate` rule). While translating, the
  model actions elsewhere are disabled with "Pause the translation first — BookLoom sends one model request at a time":
  Prepare names, Scan / Review / Translate, Fill in from the book. While the names pass runs (also when it is hidden),
  Start translation waits with "Wait until the names pass ends".
- **Export with nothing translated yet** (Ready, Cannot start): the tiles show 0, Export book is disabled, and the
  banner says "Nothing is translated yet."
- **Changing the AI model during a book** (Settings › Use this one, Change model) asks **Use LM Studio for the rest of
  this book?** — "Using the same AI model keeps the style even. Segments already done keep their translation." 

---

## Window chrome

| Element | Behaviour | Code |
|---|---|---|
| **Title bar** | Product name · the **activity chip** of hidden work · while a project is open: the project name · the run state text from the Run states table ("51 % done · about 1 h 10 min left") · dial · review mode · while translating, elapsed and speed · the **AI-model chip** · Pause / Resume. Items give way by priority (window ≤ 1280 hides dial, elapsed and speed; ≤ 1060 hides the name) | `AppShellView` `#shell-title-bar`, `ActivityChip`, `control/RunStatusBar`, `ConnectionStatus`, `LayoutFitTest` |
| **Sidebar** | Brand · Workflow: Projects, steps 1–5 (number, or ✓ when done; locked without a project, G6) · Application: Settings · footer: AI program · model, with the tooltip "the one network destination"; its dot and text follow the same source as the AI-model chip (red "No AI model found", "Ollama · Cannot reach Ollama") | `NavColumn`, `ViewNames`, `#nav-footer` |
| **Toolbar** | Breadcrumb "Workflow / Step · step n of 5" and the step's actions (L2) | `#shell-breadcrumb`, `StepFooter` in `#shell-actions` |
| **Banner slot** | L3 | new `#shell-banners` |

---

## Project names — one book, several translations (PN)
A project is one translation of a book. The same book can be translated into several languages, or into the same
language more than once, for example with another model, quality or thinking setting.

1. **Automatic name** `<book title> · <SRC> → <TGT>`, for example `Frankenstein · EN → UK`.
   - The title is the metadata title before ":", or the file name.
   - Under "Find out from the text", the detected language is used; until there is one, the source code is left out.
   - It ends in `→ …` before a target is chosen.
2. **It follows the languages** while it is automatic (`nameOrigin` AUTO); a toast says when it changes. A typed name
   (USER) never changes.
3. **A language pair is unique per book** (same `Project.contentHash`) **unless the project is named.**
   - Choosing a used pair in the Brief opens **Same language pair** (Translate into another language asks for the name in its own form and opens it only when the pair turns out to be used):
     - the existing project, with its AI model, quality and progress;
     - **Open that project**;
     - **Make a second <language> translation**, which needs a name of its own. The field starts empty, with
       suggestion chips (`· 26b`, `· Max quality`, `· thinking low`, `· formal`, `· v2`).
   - The primary button stays disabled until the name is filled in and unused. The error shows only after the first
     edit (DL-1), and the placeholder never equals an existing name.
4. **Names are unique within one book** (trimmed, case-insensitive; two editions of one title may both be
   "Earth Gravity · EN → UK"). A named project returns to its automatic name only when no other
   project of the book has the same pair.
5. **Where it shows**: Projects (with an "open" chip on the open one), the title bar, the remove and continue dialogs,
   and the label of side files.
   - Exports of two projects with the same pair never collide: `earth-gravity.uk.md` and
     `earth-gravity.uk.26b-max.md`.
- **Code (new).** `Project` gains `name` and `nameOrigin`. The store has a unique index on (book, lower-cased name) and a
  lookup by `(contentHash, source, target)`. A `ProjectNames` service in `:pipeline` builds the name and reports a
  conflict as a typed result.

---

## Projects (new screen) — G6, P, PN, D

| Element | Behaviour | Code |
|---|---|---|
| Purpose line | "A project is one translation of a book. One book can have several — into other languages, or the same language with another AI model. Progress is saved as you go." | new `screen/ProjectsView` |
| **List** (when there are projects) | Search (books and projects) · All / In progress / Finished. **Grouped by book**: a book header (cover, title, file · format · segments, "n translations", **＋ Translate into another language**) and one row per project: name (a "named" chip if named, "open" for the open one), languages · AI model · quality, a bar with % and last opened, a status chip coloured by G11 (Translating, Finished, Exported, Not started, and "Stopped by an error" for a run that failed — the stores keep no failure code, so the chip names no cause), **Continue** or **Open**, ⋯ (Rename…, Show book file, Remove project…). Search with no hits: "No project matches 'xyz'. [Clear search]" | new `ProjectsViewModel` over `ProjectService.list()` (`ProjectSummary`: counts, latest run, last export; quality from the brief) |
| **First launch / empty** | "Translate a book on your own computer" with **How it works** in four steps (choose a book · check the languages · let it translate · save the translated book; "your original file is never changed"), the **AI-model line** (found ✓ or not found, see First launch), and **＋ New translation** | — |
| ＋ New translation | Toolbar (list) or the empty state; creates a project and opens Import | — |
| Translate into another language | Dialog: Translate into (a used language is marked "already in n projects"), From (the book's source), project name (automatic, or required for a used pair, with chips), **Start from**: the brief · Glossary and Recurring terms (entries, kinds, genders; translations emptied), as fixed text; no AI model is copied. "Create and open the brief"; nothing is imported again | new; the book is reused by `contentHash` |
| Rename… / Remove project… | Rename dialog (chips, uniqueness error, "Use the automatic name again"); Remove deletes this project's saved work only — never the book, the exported files or the book's other projects | new |
| Continue / Open | Opens the project at its step; a translation that was going waits paused (Ready to continue). If the AI model is missing or changed, the continue dialogs appear | `CurrentProject`, `ImportGuard`; `bindUnavailable`, `bindChanged` |

---

## Step 1 · Import — I1–I4

| Element | Behaviour | Code |
|---|---|---|
| Header row | Title, the formats line, and a full-width drop zone with its content **centred**: "⬇ Drop another book here or Choose a file…". Dropping a book while one is open asks **Open another book?**, whose primary is **Open as a new project**; "Replace and delete this translation" is the named destructive choice | `ImportController`, `ImportGuard`, `dialog/OpenAnotherBookDialog` (ids `open-another-*`) |
| **Verdict** (one line) | ✓ "BookLoom can translate this book: 5 chapters, about 4,100 words. Your file is never changed." · **Reading…** (an indeterminate bar, Cancel — the parsers report no chapter count, so the mockup's "chapter 3 of 5" is not built) · **Already in projects** (names them, Show them; no project is created) · **Two language marks** ("The file names two languages: its details say English, its chapters are marked Ukrainian. BookLoom does not read the text to decide — check “Translate from” in the Book Brief." BookLoom has no language detector: this compares the package language (`dc:language`) with the chapters' `xml:lang` / `lang`, `ImportStates` → `LanguageWarning.Mismatch`) · **DRM** ("locked by the shop it came from") · **Damaged** ("Try downloading it again", Details) · **Unsupported** ("It reads EPUB, FB2, Markdown and plain text"); each error offers Choose another file… | `ImportVerdicts` → `ImportVerdictView` (ids `import-verdict`, `-text`, `-bar`, `-cancel`, `-show`, `-choose`, `-details`) |
| **About this book** (fold, closed) | The summary line shows format · chapters · words · images · cover. Open: cover (or "No cover · N/A", **I2**), file, title · author, the language the book declares, DRM; segments to translate, kept as they are, images · footnotes · tables "kept in place", formatting "kept exactly", the check "read and written back with nothing lost". The technical line (round trip, ids, code blocks, fonts) shows only with Show technical details | `AboutBookFold` (ids `import-about`, `import-about-summary`, `import-cover-none`), `ImportCardView`, `ImportBookView`, `StructureChecks*` |
| **The book's text** (fills the height, **I3**) | Three panes: **Chapters** (tree, count pills) → **Segments** of the chapter (search the text or a place like "ch1 · p05", kind filter **I4**, one-line rows with the kind chip or "kept as is"; marks left out, full text in the tooltip) → **Text** of the segment (location and kind; tokens and chunk only with technical details; marks as chips). In compact, Text moves under the other two. Search with no hits: "Nothing matches 'xyz'." | `StructureBrowser` + `StructureSegmentsPane` (virtualised) as one `SplitPane` (`StructureTextBrowser`, id `import-browser`); minimum widths 160 / 240 / 220 |

---

## Step 2 · Book Brief — B1–B3, I

| Element | Behaviour | Code |
|---|---|---|
| Purpose line | "We filled this in from the book. Check the two languages — everything else is optional and can be changed later." | `BriefView` |
| While translating | Read-only, with the banner "Pause the translation to change the brief." [Pause] (I) | new |
| Languages | **Translate from** (Find out from the text (the model reads a sample) · a language · Other) with "The book's details say English. “Find out from the text” lets the model name it while filling in the brief." — the language comes from the file's marks or from the model (ADR-0042), never from a local detector · **Translate into** · **Project name** line ("automatic — follows the languages", Rename…). A used pair opens Same language pair. Locked once translating has started | `BriefLanguagesCard` |
| Tone & style | Genre and Who reads it (each with "Suggested from the book · why ›", which opens the brief dialog), Register, Voice and period notes, Who tells the story (Unknown · “I” · “He / she”, short so a `SegmentedButton` fits in `uk`), Narrator's gender (asked at Start for a first-person story); **✦ Fill in from the book** | `BriefToneCard`, `BriefEvidenceRow`, `NarratorNotice`, `StyleSuggestion` |
| How to translate | Names of people and places (Translate · Spell in Ukrainian letters · Keep as written), Text in other languages, Footnotes, Units, Close to the original ↔ natural | `BriefPoliciesCard` |
| Also translate | Table of contents and chapter titles · Picture descriptions · The book's title and author · Copyright and edition notes (off) | `BriefAlsoCard` |
| Quality and speed | Fast · Balanced · Max quality; one line on what the choice does and its relative cost; "For this book on this computer: about 2 h 30 min (estimate from the model test)" **only when measured** (G10.5); the AI-model line with "change" | `BriefQualityCard` |

### Dialog · Fill in the brief from the book (G4, short) — B1–B3
- **Header.** "50 % · 3 of 6 steps · Left about 30 s".
- **Group "What it decides".** Genre, Register, Who tells the story, Who reads it · voice. Each shows its value so far
  and its agreement ("3 of 4 samples agree"). Selecting one shows each sample's answer with its **verified quote**; a
  value without a quote is not used.
- **Group "Steps".**
  1. What the model knows about the book — not listed when the title is the file name.
  2. Read sample 1…K (K = 3 / 4 / 5 for Fast / Balanced / Max), with the location range.
  3. Decide genre, readers and voice.
- **Detail.**
  - Simple: a plain sentence, then Sent (the sample) \| Answer (a Field \| Value \| Quote grid).
  - Detailed: the info grid, the code (`book-knowledge`, `brief-suggestion` or `brief-synthesis`) and the parsed
    prompt.
- **Footer.** "Nothing is written to the brief until you apply it." Cancel · **Apply to the brief** (when all steps
  have ended). Not hideable; Esc cancels.
- **Code.** `setup/BriefSuggester` announces step boundaries; `BriefSaver` applies.

---

## Step 3 · Names & style — N1–N5, I

| Element | Behaviour | Code |
|---|---|---|
| Purpose line | "Decide how names and repeated words are translated. Optional — skip it and BookLoom builds both while it translates." | `NamesStyleView` |
| While translating | Banner "Translating is going on. Changes apply to the next segments translated." While the names pass runs in the background, the screen is read-only (G4) | new |
| Prepare names automatically | Warm card: what it does, "About 1 min. You can undo it all afterwards." → the progress dialog | `AutoNamesBar`, `AutoEstimate` |
| **Glossary** card | Title with count; purpose line "Names of people and places — written the same way every time. Set the gender so names take the right grammatical form." (**N1**); toolbar: search · + Add · **Scan** · **Review** · **Translate** · ⋯ (Import CSV, Export CSV, Show only changed), each with a plain tooltip; the table (G9): Term · Kind · Translation · Gender (with evidence "he ×6", or "(likely)") · **Locked** (tooltip: "always use exactly this spelling") · ×. **Empty**: "No names yet. Scan the book for them, or add one." [Scan the book] [+ Add] | `GlossaryTable`, `TermTableCard`, `TermTables`, `ModelActionBar` |
| **Recurring terms** card | Purpose line "Words the book repeats — kept the same all through the book. Learned while translating, or set here." and **How it works** (the old long paragraph, shortened); Term · Translation · **Used** ("13 times"; new — how often the book uses it) · → Glossary. **Empty**: "No recurring terms yet. They are found when you prepare names, or while translating." | `RecurringTermsCard` |
| Changed rows (**N3**) | Left edge plus the tooltip "Changed by the last automatic run" | `TermTables` row style |
| Layout | Two cards side by side only when **wide** (≥ 1440 px of content); stacked otherwise, so the glossary's columns keep their minimum widths; tables fill the height (no 340 px cap) | L5, L7 |

### Dialog · Prepare names automatically → Results (G4, long) — N4, N5
- **Steps** (the `NamesPassImpl` stages, in order):

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

  Each row has its request count ("25 requests so far" while it runs, "6 requests" when done, "no request" when it sent none, "Not started" before it begins): no step knows its total up front, so a count never reads "of N".
- **Figures.** "53 % · 26 of about 49 steps · Left about 1 min".
- **Detail.** A plain sentence; What the model reads (candidates with counts and quotes) \| What it says. Detailed adds
  the info grid, the stage code and the parsed prompt. A finished step shows its result; one not started says so.
- **Footer.** Continue in background · "Cancel writes nothing." · Cancel. Esc hides.
- **Results** (same card when done; wide; fits the window):
  - Subtitle "5 added · 0 removed · 1 changed · 49 steps · 1 min 48 s".
  - Tabs **Changes** (Entry \| Change \| Before \| After, grouped) and **Steps**.
  - Footer: Undo all · Show changed rows · Done.
- **Scan / Review / Translate** on a card run the same dialog with two steps (the action's requests, then write). They
  are not hideable.
- **Code.** `ProgressDialog.results(ResultsView, Messages)` is the Results view (it replaced `ChangeResultsDialog`); `ChangeResults` and Undo all stay, and `ProgressDialog.calls(...)` is the Calls view 6.5 uses.

---

## Step 4 · Translating — T1–T12, A, C1

| Element | Behaviour | Code |
|---|---|---|
| Toolbar and banner | By the Run states table (L2, L3) | `TranslatingViewModel.controls()`, `RunNotice` |
| Help line | Once per project, closable: "BookLoom keeps the computer awake while it translates. Closing a laptop lid still puts it to sleep — after sleep, translating goes on by itself. Nothing finished is lost." (to be verified once with a real sleep). If keep-awake is off: "The computer may sleep while translating — turn on Keep awake in Settings." | new |
| **Summary strip** (C1) | Big: **"about 1 h 10 min left"** (Finished: "Finished in 2 h 25 min"; Ready: the measured estimate). The bar over all steps (tooltip "51 % of all the work — 140 of 276 steps, checks included"). Facts: **74 of 142 segments done** · 36 translated · 12 fixed automatically · 6 to review · 20 kept as is · Chapter 2 of 5 · elapsed. A **"6 to review"** button opens the desk. **ⓘ How progress is counted** opens the breakdown (G10) | `TranslatingSummaryStrip`; the ledger is new |
| **Live card** (fills the height, L5) | Tabs **Live** · **Activity log**. Live: Simple / Detailed · **Show each segment when translated** (Auto-show) · Follow again (after a manual pick) · All calls… | `control/LiveCallPanel` (rebuilt) |
| Call line | Simple: the plain line of the run state ("Now translating: Chapter 2, segments 5–8"). Detailed adds "Call 42 · Draft · 4 segments · ch2 · p05–p08 · attempt 1 of 2 · 0:21 of 3:00". **One clock**: the stuck state shows "3:40 of 6:00" | `LiveCallView` header |
| **List \| detail split** (never stacks, L6) | Left: the chunk's segments in reading order, one line each (marks left out, tooltip with the full text), each with a G11 marker: waiting · translating… · translated · to review · translated again alone · kept as is. Markers follow the run state: when paused, the current call's rows are "waiting" | new: per-row state from the call state + `SegmentOutcomeNote` + `SegmentDrafted`; `LiveCallState` needs the chunk's rows; remove `SEGMENTS_HEIGHT` |
| Simple detail | The segment's marker and location (a "to review" one names why), a bordered **Source \| Translation** grid ("Being translated now…" / "Not translated yet." when empty, **T4**), and **Being translated now**: the sources of the call in flight | — |
| Detailed detail | The call line (call, kind, segments, range, attempt, clock), the tokens, **Sent to the model** (parsed, G5) \| **The model's reply** (grid by id); no first-token figure and no next-call line (no source for either) | `PromptView`, `ReplyView`; per-item reply on `CallSnapshot` |
| Show each segment when translated | On: when a reply arrives, the first newly translated segment is selected, unless the person clicked one (then "Follow again"). Only a click or key turns following off (JX-T3) | `LiveCallPanel` |
| Scene summary (**T5**) | Always under the detail: the current summary (its target-language text, else its source text; "No scene summary yet." while there is none) and where it comes from, one wording on every quality ("Updated after every 20 accepted segments and at the end of each chapter. On Max quality the model writes the one at the end of each chapter.") | `RollingSummaryKeeper` via `MemoryUpdated` |
| Activity log tab | All · Problems only. Plain lines ("Segments 1–4 of chapter 2 translated in one reply · 3 accepted · 1 to review"); debug lines only with technical details | `control/TaggedLog`, `ActivityLogFeed` |
| All calls… | The progress dialog listing the translation's calls, newest first (last 50 kept); Simple shows each reply matched to its segments, Detailed the full call | replaces `ModelCallsWindow` |

### Dialog · Review (decision 5, F1) — T6–T12
- **Opens** from "Review (n)" or the strip's "n to review", and **by itself** when a translation finishes with segments
  to review (it waits for a free slot, G1). With nothing to review it says "Nothing needs your review".
- **Header.** "4 of 6 left — if a segment reads well, press Accept. You can export now and come back later."
  - While translating: "read-only while translating" plus the banner "Pause the translation to edit, accept or try
    again." [Pause].
- **Left pane.**
  - A filter (To review (n) · Worth a second look (n) · All segments) and ‹ › to move.
  - Rows: location, a **state marker** (to review · accepted · your edit · later) and a one-line "why · text". "Later" is the desk's own
    mark for this session: Leave for later writes nothing, so the segment stays FLAGGED and still counts as to review.
- **Editors.** Original (read-only, chips) \| Translation (the editor).
  - The editor is a `TextArea` where formatting marks are **protected text marks `⟦1⟧`** (decision 12). Deleting
    removes a whole mark, never half; cut and paste can move one; a paste keeps only marks missing from the editor.
  - A strip below says "Formatting marks ⟦0⟧ ⟦1⟧ keep bold, links and similar…", or **"⟦1⟧ is missing — paste it back
    where the formatting ends."**
  - The main button stays off while a mark is missing or doubled.
  - The editors and the tabs share the height through a draggable divider (vertical `SplitPane`), so the editors grow
    with the dialog.
- **Tabs.**
  - **What may be wrong**: the plain reason plus "If it reads well, press Accept." The rule code and ratio appear with
    the "Technical details" switch.
  - **What the model saw**: the stored snapshot, parsed (**T8**). For a batch-drafted segment it must be the batch's
    sent context (a small pipeline change).
  - **Suggestion**: Now \| Suggested by the check, with "Use the suggestion".
- **Footer.**
  - **More ▾**: Leave for later · Try again · Try again with a note… · Undo my edit, each with what it does.
  - The **one main button**: **✓ Accept & next** turns into **✓ Save my edit & next** once the text is edited (**T7**).
  - Shortcuts: Ctrl/⌘+Enter runs the main button and Ctrl+Alt+↓/↑ moves between segments. Alt+↑/↓ belongs to the
    editor on macOS.
  - **Try again with a note** opens an inline panel inside the desk, not a second dialog.
  - Feedback is a footer line (G2).
- **Code.**
  - `ReviewPanel`, `ReviewListPane`, `ReviewComparePane`, `FindingRows`, `ReadableView` and `ContextSection` move into
    an xl `ModalCard`.
  - Accept (FLAGGED → ACCEPTED with the machine text) and Save edit (REVISED) keep their `SegmentActions` behaviour.
  - The protected editor is a `TextFormatter` that adjusts changes to whole marks and lets whole-text loads through.
    Undo and redo skip the filter, which is safe because every change it recorded covered whole marks.
  - The duplicate finding label (`FindingRows.plainRow` + `FindingBadge`) goes away. RichTextFX is **not** added.

---

## Step 5 · Export — E1–E3, I

| Element | Behaviour | Code |
|---|---|---|
| Toolbar | Back · **⬇ Export book** (disabled while translating), then Open folder · Open book (L2) | `ExportView`, `StepFooter` |
| Banner | Translating: "Pause or stop the translation to export. You can export what is done so far; the rest keeps the original text." [Pause]. Paused or stopped: "74 of 142 segments are translated. The other segments keep the original text." | new |
| **Tiles** (E1) | From the project **before** any export: **74 of 142** segments done · translated without changes · reviewed by you · "checked when written" (— until written, then ✓ "opens, nothing missing") | add a pre-export source; `ExportResult.reset()` blanks today |
| **Title and author** (E1) | Original (read-only) and translated (editable); when there is none, one line says why ("The book names no author"). Markdown takes the title from its first heading or front matter | `ExportTitleCard`, `BookTitleViewModel.read` (add `aux:fm:*` and the reason) |
| **Where to save** | "Books ▸ earth-gravity.uk.md (in your home folder)" with the full path as tooltip · Change… · ✦ Suggest a name; the same format as the original; **if the file exists, BookLoom asks before replacing it** (no Replace switch that is on by default) | `ExportDestinationCard`, `ExportPathRules`, `ExportOccupancy` |
| Also save | Glossary (CSV) · Bilingual copy · Quality report | `ExportSideFilesColumn` |
| **Last check before saving** | "Check the uncertain places again — Recommended. Rereads the segments that needed fixing next to their neighbours. Adds about 3 min for this book." Off and disabled for a partial export | `ExportSideFilesColumn` |

### Dialog · Suggest a file name (G4, short)
- One step: Sent (title, target, format) \| Answer (the name and how it was made).
- Footer: "Cancel keeps the current name." · Cancel · **Use this name**.

### Dialog · Export · last check before saving (G4, long) — E2, E3
- **Groups.**
  - Steps: Check the translation is complete · Fix he/she forms that became known (`CONSISTENCY_RETRY`) · Translate
    unsure segments again (`RETRY_DOUBTED`).
  - **Reread next to the neighbours · 6 of 18** (one item per segment: unchanged · kept · fixed · reading… · waiting).
  - Finish: Write the file.
- **Figures.** "48 % · 12 of 25 steps · Left about 2 min".
- **Item detail.** A plain sentence, then a bordered **Before \| After** grid: the segment before, this segment, the
  segment after. A fix shows the old text struck through above the new one; "unchanged" is said in words. Detailed adds
  the parsed prompt and the raw reply note.
- **Footer.**
  - Left: Continue in background and "Cancel writes no file."
  - Right: **Write the file now** (asks: "6 of 18 reread, 1 fixed; the other 12 stay as they are") and **Cancel**,
    which asks "Cancel the export? No file is written."
  - Esc hides.
  - On finish, **Export complete** opens: Saved as (wraps), Check, Size, Also saved, Last check "18 segments reread · 1
    fixed"; Close · Open folder · Open book.
- **Code.** `ExportProgress.Step` (VALIDATING → CONSISTENCY_RETRY → RETRY_DOUBTED → CONSISTENCY_NEIGHBOUR → WRITING),
  `ExportCalls`, `ConsistencyPass`, `dialog/ExportCompleteDialog`.

---

## Settings — S1–S5, H1, decisions 6–7

Six tabs: **Providers · Models · Generation · Appearance · Automation · Storage & logs**. Subtitle: "Your books stay on
this computer. BookLoom sends your text only to the AI model you choose here."
- **How changes are saved (H1).**
  - **Providers** and **Models** save explicitly through a pinned form footer: "● Unsaved changes" · Cancel · Save.
    Leaving them with changes (another provider, another tab, navigation, closing the window) asks: Keep editing ·
    Discard · Save.
  - The other tabs **apply at once** and say when: "Changes apply at once", "to the segments not translated yet", or
    "after BookLoom restarts".
- Keys are never saved, only environment-variable names.

### Providers
| Element | Behaviour | Code |
|---|---|---|
| List \| form | Side by side at every width (list 240, or 200 in compact); the form column is capped at 860 px; its footer is pinned at the bottom of the card | `SettingsViewModel`, `ProviderRow`; `LeaveGuard` gains a dirty-form check |
| Detail header | The provider's name, "current" or **Use this one**, **Delete…** (confirm) | — |
| **Summary card** | "Your AI model: Ollama · gemma4:e4b — ready ✓ · Answers in about 1 second · 7 models available" · Test · Change model | new; from `ProviderTestRunner` results |
| **Connection details** (fold) | The GoText form, unchanged: Name · Kind (Ollama / OpenAI-compatible) · Sign-in (None · Bearer token · API-key header + header name; **key from environment variable**, "set" / "not set") · Address · **Models endpoint** and **Inference endpoint** overrides (the inference one disabled for Ollama, with the reason) · Connect and request timeouts (number widths) · Model (list + ↻, or typed) · Use custom headers (Header \| Value, values may be `${env:NAME}`) · Use custom models (chips) · **Checks** in plain words ("Ollama answers (41 ms)", "7 models found — the chosen one is there", "The model replied in 1.2 s") with **Details** | new `ProviderConfig` fields (`displayName`, `auth`, `credentialRef`, `modelsPath`, `chatPath`, `headers`); header injection in `:llm` `HttpExchange`; `ProviderVerifierImpl` takes an unsaved config |

#### Dialog · Test connection / models / inference (S2)
- A verdict banner first ("The model replied correctly. Translation can start.").
- The request line (method and full URL, Copy as curl with the key hidden).
- Three columns: **Body** (request JSON) \| **Status / Time / First token / Speed / Tokens / Structured** \|
  **Response** (JSON). Each pane scrolls inside its column, so the banner never overlaps.
- Footer: switch between the three tests · Close.

### Models (decision 6)
- **Model** (a list across providers, ↻ Refresh), with "Each model keeps its own settings. All are optional — off
  means BookLoom picks the value per task."
- **Found out from the program**: how much it can read at once (and how much is loaded now), and whether it gives
  structured answers.
- **Temperature** (S6): **Let BookLoom choose** (default; each task's tuned value from `ChatRequests`: 0.0 checks,
  0.1–0.2 translating) · **My value** (a slider; one value for every request) · **Don't send** (the parameter is
  left out entirely, for models that refuse it, such as some newer OpenAI and Anthropic models). The request DTOs already
  omit a null temperature, so "Don't send" maps to null.
- **Settings this model refused** (S6): a list learned automatically: the setting, when it was refused and what
  BookLoom did instead. Each row has **Send again** (or **Reset**). With nothing refused it says so.
- **Use context window** (number; filled in from what is loaded; `num_ctx` for Ollama in grey).
- **Limit answer length** (off = `OutputLimit` per request).
- **Advanced**: the field that carries the answer length (`max_completion_tokens` / `max_tokens`; not used for Ollama).
- Pinned footer (H1).

### Refused settings (S6)
Providers differ in what they accept. Newer OpenAI and Anthropic models refuse `temperature`; some servers refuse
`top_p`, `seed`, `max_tokens` (wanting `max_completion_tokens`), `reasoning_effort`, `response_format` or `num_ctx`.
BookLoom handles a refusal the same way for every optional setting:
1. **Recognise it.** The reply is an HTTP 400 (or 422) whose body names a setting BookLoom **sent**, with words like
   unsupported, unknown, unrecognized, invalid, "not supported" or "only the default".
   - `llm/provider/CapabilityRejections` already does this for reasoning and structured output. Extend it to
     temperature, the sampling settings (`top_p`, `top_k`, `min_p`, repeat penalty), `seed`, the output-length field and
     `num_ctx`.
   - A setting is recognised only if it was in the request, and nothing from the body is kept beyond its name.
2. **Drop it and repeat once.** The same request is sent again without that setting, or with
   `max_completion_tokens` instead of `max_tokens`. It does not count as a failed attempt.
3. **Remember it per provider + model** (planned SQLite; in memory until then). Later requests leave it out. The
   Models tab lists it under "Settings this model refused", where **Send again** forgets it.
4. **Say it once.** The activity log gets one warn line ("The model refused the setting “temperature” — BookLoom
   stopped sending it and repeated the request"). The inference test's Details show it. No toast during a run.
5. **A refusal BookLoom cannot place** (a 400 that names no setting it sent) stays an ordinary error with Details. It
   is never guessed.

### Generation · Appearance · Automation · Storage & logs
- **Generation** (applies to segments not translated yet):
  - What the quality choice does: a read-only grid from `DialParameters`.
  - Thinking before answering: Off / Low / On (`ReasoningPlan`).
  - Sampling: Program defaults / Tuned by BookLoom (`SamplingMode`).
  - Time limits: fixed, `RequestTimeouts`.
- **Appearance**:
  - Theme.
  - Language of the app ("Applies after BookLoom restarts." [Restart now]; a live switch would rebuild every view,
    JX-S1).
  - **Show technical details** (G12).
- **Automation**:
  - When to stop and ask you (Never — translate unattended · When something needs review · After every segment =
    `ReviewMode` UNATTENDED / ASSISTED / MANUAL).
  - Default quality.
  - Open the review desk when a translation finishes with segments to review.
  - Run the last check before saving by default.
  - Keep the computer awake (with the lid note).
  - Reopen the last project.
  - There is no OS-notification switch.
- **Storage & logs**:
  - Your projects: saved in, size, saved as it goes, Open folder, Make a backup….
  - Logs: folder, kept 10 launches, Detailed diagnostic log, Open log folder, Save a diagnostic bundle….
  - Paths are shown short with the full path as tooltip.

---

## Other dialogs

| Dialog | When | Code |
|---|---|---|
| Add to the glossary | + Add | `dialog/AddTermDialog`, `AddRecurringTermDialog` |
| Who tells the story? | Start, first-person story with an unknown narrator gender (Enter = not stated) | `dialog/NarratorDialog` |
| Characters without a gender | Start; its count equals its rows | `dialog/CharacterGendersDialog` |
| Stop translating? | Stop — Keep translating · Stop | `ConfirmDialog` |
| Unsaved changes | Leaving Providers or Models with changes — Keep editing · Discard · Save | new |
| Cancel the export? / Write the file now? | Export dialog footer — they open above it (G1) | `ConfirmDialog` |
| Delete provider / Remove project | Settings / Projects | `ConfirmDialog` |
| Open another book? | Dropping a book while a translation exists — Open as a new project (primary) · Replace and delete this translation · Cancel | `OpenAnotherBookDialog` |
| Model work is running | Leaving during long work — Stay · Continue in background | `LeaveDialog`, `LeaveGuard` |
| Error with details | Unreadable file and other hard failures — plain message, "Details for support", Copy details | `notify/ErrorDialog` |
| AI model not answering / A different model is chosen | Continuing a project — Try again (primary) · Use another AI model… · Not now; Use the earlier model (primary) · Continue with the new one; Escape is Not now / starts nothing | `ContinueDialog` |
| Same language pair · Rename · Translate into another language | PN | `dialog/SamePairDialog`, `RenameDialog`, `TranslateIntoDialog` |
| Find the book | Continue on a project whose book file is missing or changed — Find the book… (primary; only a file with the same content is accepted, otherwise "This is a different book") · Cancel; `DLG.findBook` | `dialog/FindBookDialog` |
| About | Title bar | `AboutDialog` |

---

## JavaFX implementability

Everything is buildable with JavaFX 26, ControlsFX 11.2.5 and Ikonli Feather, all already dependencies. **No new
library** is needed; RichTextFX is not added (decision 12).

| Mockup widget | JavaFX |
|---|---|
| Segmented pickers | `SegmentedButton` / `ToggleGroup` (+ the 2 px selected bar) |
| Editable tables | `TableView`, `FLEX_LAST_COLUMN`, fixed cell size, grid-line CSS |
| Read-only grids | `GridPane` with grid lines |
| List \| detail splits | `SplitPane` with divider positions and minimum sizes |
| Chapter tree | `TreeView` |
| Prompt sections | `TitledPane` |
| Formatting marks | detail panes: `TextFlow` chips; editors: `TextArea` + `TextFormatter` |
| State markers | `Label` + Ikonli icon + word |
| Switches | `ToggleSwitch` |
| Dialogs | `ModalCard` on `ModalHost`; progress dialogs on the work layer |
| Toasts | `ToastStack` + `ToastAction` |
| Ticking figures | `FigureLabel` (no re-layout per tick) |
| Width classes | `LayoutClass` pseudo-classes (no CSS media queries in JavaFX) |
| Fit checks | `LayoutFitTest` at 960×640 and 1920×1080, in `en` and `uk` |

Reused as they are: `PriorityRow`, `TwoColumns`, `TaggedLog`, `StructureSegmentsPane`, `PlaceholderFlow`, `TextBody`,
`GlossaryCells`, `SmoothScroll`. Nothing in the mockup needs a second `Stage`.

**Tests that change** (R2):
- Retired: `ModelCallsWindowTest`, `BusyHostCallsWindowTest`, most of `ChangeResultsDialogTest` and `BusyHostTest`.
- Rewritten: the LiveCallPanel, PromptContextPane / ContextSection, ReviewPanel, Structure / Import layout, TermTable,
  `RunFigures` / `RunClock`, Toasts and Settings tests.
- New:
  - `ModalCard` sizes;
  - `ModalHost` key and queue rules;
  - progress-dialog follow and hide;
  - the step ledger and estimate;
  - the protected editor;
  - per-item batch rows;
  - `LayoutFitTest`;
  - contrast for links and the selected bar;
  - the run-state table mapping.
