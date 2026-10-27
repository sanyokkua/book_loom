# Proposal: finish the application UI and keep progress in SQLite

Status: proposed (owner review pending) · 2026-10-19 · evidence: the owner's annotated review
`.temporary_context/NewObservations/*.pdf` with the screenshots in `BookLoomLastBuild/` (owner-local), and the updated
mockup `docs/specification/mockups/ui-mockup.html` with its `README.md`.

## Why

The owner went through the last build screen by screen (Oct 19) and annotated it, along with the old mockup. The
translation workflow itself works, but the window around it does not yet explain what it is doing:

- **Model work is opaque or raw.** The brief suggestion, the names pass, the file-name suggestion and the export
  consistency pass each show a small busy card: a spinner, an elapsed clock and "Show model calls". That button opens a
  **separate OS window**, which shows prompts as raw text (`== System message ==`, `<Items>`, `⟦g0⟧`) and replies as raw
  JSON. The owner asked for the details in an **in-window dialog**, every time, **parsed**, with **exact progress**
  (which step, which is done, what is left).
- **A batch is invisible.** A draft call carries up to 8–16 segments, but the live panel shows one row in a fixed
  220 px box. The reply's per-item translations never reach the rows: a row reads "no target" or "Fell back" while the
  JSON beside it holds the answer.
- **Things do not fit.** The results dialog grows past the window, long terms are cut to "Ea…", and the Import page
  takes four screens to scroll. Scrollbars are thin and faint, and toasts sit bottom-centre over buttons.
- **Nothing is kept.** Settings, providers and projects vanish on restart, so the Projects step, the Storage tab and
  "resume" exist only in the mockup.

The next change finishes the UI on these points and adds SQLite persistence. No pipeline behaviour changes, except
where the UI needs data the pipeline already has (per-item batch results, step names).

## Owner decisions (2026-10-19)

1. **Every dialog is an in-window modal.** The scrim keeps focus, Esc cancels and Enter confirms. While a dialog is
   open, feedback goes into its footer rather than into a toast over its buttons. There are no OS
   windows apart from native file choosers; `ModelCallsWindow` is removed.
2. **One progress-dialog pattern for model work** ("same approach as other detail dialogs"):
   - a header with the step or item count, elapsed and remaining time, tok/s and a determinate bar;
   - a list of steps or items with their state;
   - details of the selected step, with the input and output shown parsed;
   - Cancel in the footer.

   It replaces the busy card plus the "Model calls" window for the brief suggestion, names, file name and the
   consistency pass. The provider tests get their own Body | Metrics | Response dialog from the owner's sketch (S2).
3. **No raw prompts.**
   - Prompts are shown as their parsed System and User sections.
   - Replies are shown as per-segment results.
   - Raw text stays reachable only as a secondary "Raw" view with Copy, in Detailed mode.
4. **The Translating live panel has two modes.**
   - **Simple** ("Option 2" of the sketch): the batch's segment list with a state marker on every row (waiting, in
     progress, translated, fell back, flagged), the selected segment's Input | Translation, and "Current text in
     progress".
   - **Detailed**: the same list, the Input as parsed System and User, the Output as the parsed reply, and a "Next in
     progress" preview.
   - An **Auto-show translated segment** switch selects each segment as it is translated, unless the person has picked
     one by hand.
5. **Review is a large in-window dialog**, opened by "Review flagged (n)". It **opens by itself** when a run finishes
   with flags.
6. **Settings keeps a separate Models tab**, as in the owner's GoText app. The provider holds the connection; the Models
   tab holds the parameters for each model. This deliberately overrides the review note "We have models inside provider
   now" and the red ✗ on the Models tab.
7. **Provider form like GoText**:
   - name, kind, and auth (None / Bearer / API key, with the key as an env-var reference);
   - base URL, plus a **models endpoint** and an **inference endpoint** override ("Need 2 endpoints");
   - custom headers, custom models (typed model ids), a diagnostics section, and Delete / Cancel / Save.
8. **Workflow steps are unavailable until a project is open.** Projects is the way in: it shows the list when there are
   projects, and the empty state when there are none.
9. **One book, several translations** (owner request, 2026-10-19, after the review).
   - A project is one translation of a book. Its automatic name is `<book title> · SRC → TGT` and follows the
     languages.
   - A language pair is unique per book (same content hash). A second project with the same pair asks the person for a
     name of its own, for example to compare models or settings.
   - Projects are grouped by book, and "Translate into another language" starts a new project from an already-imported
     book.
   - The full rules are in the mockup README, "Project names (PN)".
10. **Progress counts all the work.** Every bar and every time-left counts all steps of the whole operation: all tasks
    and their items, the run's review passes and repairs included. Time left is measured per step kind over the whole
    operation, not from the last few segments. The rule is README G10.

11. **Long work can continue in the background** (final review). The names pass and the export pass can be hidden into
    the title bar's activity chip. The screen they write to is read-only until they end, and a toast with an action
    says when they finish. Short work stays in its dialog.
12. **The review editor uses protected text marks `⟦1⟧`** (final review). A `TextArea` with a `TextFormatter` deletes a
    mark whole and lists missing ones; Accept and Save stay off until every mark is there. No RichTextFX; chips stay in
    read-only panes. This replaces T12's "chips in the editors".
13. **BookLoom looks for the AI model by itself at first launch** (final review): once, on `127.0.0.1` only (Ollama and
    LM Studio ports), silently degrading. This is a deliberate exception to "no network call the person did not start",
    to be written into AGENTS.md and `offline-and-privacy.md` with an ADR in the next change.
14. **"Segment" stays the user-facing unit** (final review). Chunk, call, tokens and attempts move out of Simple views;
    "segment" is explained once in a tooltip.

## What the owner asked for, by screen

IDs are cited by the mockup README. Quotes are verbatim, sic included. Colour: red = defect or redesign, blue = layout
fix, yellow = content note, black = question or sketch.

### Global (G)
| ID | Observation | Ask |
|---|---|---|
| G1 | "it should be dialog popup", "And it shuld be dialog, not a separate window!" (Brief, Names, Export) | Every popup is an in-window modal of at most about 90 % of the window, with a scrolling body and a fixed header and footer |
| G2 | "Should be moved", "Move to right" (Settings, Import, Translating) | Toasts stack in the bottom-right corner, newest at the bottom, and never cover the page's buttons |
| G3 | "Scroll bar should be bigger", "scroll bar size increase" | Wider (≈12 px) scrollbars with a visible thumb, shown whenever content overflows |
| G4 | "display exact progress of the work", "User should see progress in real time and understand what is current status and what is left" | The progress-dialog pattern (decision 2) |
| G5 | "No RAW prompts, we need their parsed representation", "Req/Resp shows what was sent/Received from LLM in user friendly way (Parsed and sanitized data)" | The parsed renderer (decision 3) |
| G6 | "Until project opened Should be unavailable", "Show when there are projects", "Show when no projects" | Decision 8 |
| G7 | The old mockup's eight steps against the app's five (Structure and Review queue were steps) | The mockup follows the five-step app, with Structure folded into Import and Review moved into a dialog |
| G8 | Accent row "delete", "No color" | Remove fixed, non-actionable rows |
| G9 | "Table should have visual division, borders", "User should be able to resize each column…", "Term is not readable" | Tables have cell borders, resizable columns, and a full term that is never cut by a chip |

### Settings (S)
| ID | Observation | Ask |
|---|---|---|
| S1 | The red ✗ on the Models tab, "Should be removed" | Overridden by decision 6: the Models tab stays and is rebuilt |
| S2 | The inference-test busy card, "Expand", and the sketch "Inference Test" | A large dialog showing the request URL, the request body (JSON, scrolling), Time, TPS and Status, and the response, with Cancel and Ok. Test connection and Test models report the URL, status and time the same way |
| S3 | The Add provider dialog, "Need 2 endpoints 1 - Models 2 - Inference" | Decision 7 |
| S4 | Generation and Automation, "Adopt to app logic" | Only real settings (see the knob table below), with no fictional judge model or τ |
| S5 | The Accent row | Removed (G8) |

### Import (I)
| ID | Observation | Ask |
|---|---|---|
| I1 | "Should be available only in scope of opened project" | Import is reached from an open project (G6) |
| I2 | "N/A" "if no image" | The cover placeholder says "No cover" when the book has none |
| I3 | Reading order, Segments (empty and filled) and the wide two-column layout, "Should be combined in to single widget"; sketch Chapters → Segments → Text | One three-pane structure browser: a Chapters tree with count pills, then the Segments of the picked chapter, then the picked segment's full Text. Each pane scrolls on its own |
| I4 | "Mockup doesnt show search, but it should be added as it is on real widgets" | The browser keeps the text/locator search, the kind filter, and the row chips (kind, kept as is, tokens, chunk) |

### Book Brief (B)
| ID | Observation | Ask |
|---|---|---|
| B1 | The Style suggestion card, "Should always be detailed one" | "Suggest with model" opens the detailed dialog straight away; no "Show model calls" step |
| B2 | Sketch "Define Book Characteristics" | Header "Overall progress: step n/N · time" with a bar. Steps (book knowledge, sample windows, synthesis, mapped to the fields Genre / Register / Narrator / Audience / Voice) each show done (with the value), in progress or waiting. The detail pane shows the state ("Waiting for LLM response"), Request \| Response, "Segments to process 10/50" and other information. Cancel |
| B3 | "Popup can give details on the each step", "User can see info about finished steps" | Finished steps stay selectable and keep their request and response |

### Names & style (N)
| ID | Observation | Ask |
|---|---|---|
| N1 | "Both tables should explain their purpose", "???" on the Glossary header | Both cards get a short purpose line; the long Recurring-terms paragraph moves behind "More" |
| N2 | "User should be able to resize each column…" | Resizable columns in both tables (G9) |
| N3 | "Term is not readable" (the Changed chip cuts "Earth" to "Ea…") | "Changed" becomes a row marker or a column of its own; the term keeps its width and a tooltip |
| N4 | "It should be dialog not the separate window", "The detailed dialog should be user friendly… exact progress of the work" | The names pass uses the progress dialog. Its steps are the real `NamesPassImpl` stages: scan, names, terms-scan, terms-review, terms-read, names-translate, terms-translate, write. Scan / Review / Translate on a card run a one-step version |
| N5 | "Result" arrow; "Fails to be displayed when more details clicked, should fit to display, should scroll, should take more horizontal space" | When the pass ends, the same dialog switches to **Results**: Added / Removed / Changed, Undo all, Show changed rows. It is wide, fits the window, and the call list is a tab rather than an expander that grows |

### Translating (T)
| ID | Observation | Ask |
|---|---|---|
| T1 | "The widget should be more user friendly", "Idea to have 2 modes…" | Decision 4 |
| T2 | "Progress: seg12/100, t" | The panel header and the progress card show "segment n of N", time elapsed and time left, alongside chapter and chunk |
| T3 | "Table should have visual division, borders" | Bordered Input \| Output tables (G9) |
| T4 | "Current after translation replaces Previous when LLM retured response…" | In Simple mode the selection moves to the newly translated segment (the auto-show switch); a row that is still waiting shows no empty output column |
| T5 | "Also, where is paragraphs summary?" | A "Scene summary" line is always present in the panel. It says when a summary exists and, when none exists yet, why — code-built every 20 accepted segments and at each chapter end, plus a model-written one on Max at chapter end (drafts read only the model-written one) |
| T6 | "Can dialog be a good idea?" (next to the empty review area) | Decision 5 |
| T7 | "What's diff between Accept vs Save edit" | The buttons explain themselves. "Accept machine text" approves the draft as it is; "Save my edit" stores the person's text, needs a change, and marks the segment Revised |
| T8 | "it should show the same data which was used during translation attempt", "Idea again show the System/User promts parsed. show what will go to LLM" | The review dialog's Context tab shows the stored snapshot, parsed into System and User sections. For a batch-drafted segment it must be the batch context actually sent (today it is the segment's own package, see Code facts) |
| T9 | "When finished, show automatically flagged" | Decision 5 |
| T10 | Toast over the Accept row, "Move to right" | G2 |
| T11 | (not annotated) "omission · medium" plus an "omission" chip | One label per finding: kind chip, severity, "raised by" |
| T12 | (not annotated) `⟦g0⟧` tokens in the edit boxes | Tokens show as chips in the editors too, with a "Readable / Raw" switch |

### Progress and time left (PR) — owner request after the review
| ID | Ask | Mockup |
|---|---|---|
| PR1 | "Progress bar and time should spread across all the tasks": 3 tasks × 3 items = 9 steps, and the bar counts those 9 | Every bar is steps done ÷ all steps of the operation; dialogs show "Overall n of N steps" before the current task (README G10) |
| PR2 | "2 h passed, 52 % translated, remaining 10 h" is wrong: half done in 2 h means about 2 h left | Time left = steps left of each kind × time per step of that kind, measured over the whole operation; rounded, smoothed, never from the last few segments only |
| PR3 | For a book, review and other extra rounds per segment are part of the progress | The run's steps are drafts + planned review passes + Max's summaries and backward revision + repairs added on the way; the card shows "step n of N" and the segments decided, with a breakdown per kind |

### Project names (PN) — owner request after the review
| ID | Ask | Mockup |
|---|---|---|
| PN1 | The same book in different languages: the project name carries the language combination | Automatic name `<title> · SRC → TGT`, following the languages until renamed |
| PN2 | The same book into the same language (another model, reasoning, quality): the person enters a name | The Same language pair dialog: open the existing project, or name this one (required, unique, with suggestion chips) |
| PN3 | Find the book's translations together | Projects grouped by book; "＋ Translate into another language" reuses the book and copies the brief and terms |

### Export (E)
| ID | Observation | Ask |
|---|---|---|
| E1 | "Why is it empty?" (stat tiles, Title and author card) | The tiles show project figures before any export (translated, auto-accepted, reviewed, "not exported yet"). The Title card shows its rows whenever the book has a title or author, and explains why they cannot be edited when that is the case |
| E2 | "Should be a dialog popup, not window" | File-name suggestion and the consistency pass use the progress dialog |
| E3 | Sketch "Same appoach as other detail dialogs (Rendered prompts)": Items list, Input/Output, Previous \| Translated, Current System/User | Each consistency item shows the paragraph before and after (Previous \| Translated). `{"unchanged":true}` reads "Unchanged — kept as it was". Every call keeps its input; the empty "Previous call" box is a bug |

## Final review (2026-10-19)

After the owner's notes were applied, four fresh-context reviewers checked the mockup:
- **R1** — UX design, 76 findings;
- **R2** — JavaFX feasibility, 29;
- **R3** — a first-time non-technical user, 47;
- **R4** — window sizes 960 → 1920, 29.

They worked from captures of every state at four window sizes and from the live mockup. Their findings were grouped
into nine themes with options. Three independent reviewers scored the options on six criteria (expert UX, novice UX,
scaling, JavaFX effort, consistency, conflict with decisions), and the owner decided 11–14 above. The reports are
owner-local in `.temporary_context/NewObservations/analysis/final-review/`.

| Theme | Chosen | What it means | README |
|---|---|---|---|
| A — run state and sample data | fix | One row per run state (16 states, code mapping) drives the title bar, chip, toolbar, banner, live line and row markers. One sample-data fixture feeds every figure, with self-checks. Pause and Stop are worded by what they do. "Try again now" is the one retry verb | Run states |
| B — layout and pinned actions | B1 | The toolbar is the one action bar (Pause / Stop / Review, Export book). A pinned banner slot shows run banners on every screen. Width classes are compact / regular / wide from the content width with 16 px hysteresis. Content caps 1320 px, inputs sized by kind. A fills-viewport opt-out in `AppShellView` | L1–L10 |
| C — Translating | C1 | A summary strip (time left, segments done, to-review button, breakdown) plus a full-height live card with Live and Activity log tabs. The list \| detail split never stacks | Step 4 |
| D — plain language | D1 (owner kept "segment") | A Words table. "Show technical details" opens Detailed everywhere. Plain banners and findings. Estimates only from measurements. A true sleep line. First-launch model check (decision 13) | Words, G12, First launch |
| E — progress dialog | E1 on the work layer | Built in the `BusyHost` layer below ModalHost, so questions open above it. Continue in background for long work (decision 11). Simple = plain sentence + Input \| Output. A fixed xl height. Cancel says what it does | G1, G4 |
| F — review desk | F1 merged | Rows carry state markers. Editors and tabs sit in a vertical split. Protected marks (decision 12). One main button, Accept & next, that turns into Save my edit & next. A More menu. Try again with a note is an inline panel. Shortcuts Ctrl+Enter and Ctrl+Alt+↑/↓ | Review dialog |
| G — visual system | fix | Two kinds of table. Markers are icon + word. One colour map. Links underlined in `--info-fg` (4.6:1 or better). The selected segment bar in `--text-strong`. Overrun tooltips. Follow turned off only by a person | G9, G11, L8 |
| H — Settings | H1 | Providers and Models save through a pinned footer with an unsaved marker and a leave guard; the other tabs apply at once and say when. A summary card plus a "Connection details" fold around the unchanged GoText form. Plain check results | Settings |
| I — missing and mid-run states | fix | Empty states (no names, no review, search no hits, reading the book, no AI model found). The Brief is read-only while translating and its languages are locked once started. Export is disabled while translating, allowed (partial) when paused, and the last check is off for a partial export. Name validation shows after the first edit | Changes while translating |

**Code impact added by the final review** (on top of "Code facts" above):
- `ModalHost` gets a `showWhenFree` queue and an Enter/Esc policy.
- `BusyHost` becomes the work layer for `ProgressDialog`, and `ActivityChip` shows hidden work.
- A `LayoutClass` helper and an `Overrun` helper.
- A `#shell-banners` slot.
- A `fills-viewport` opt-out in `AppShellView.showContent`.
- A summary-strip class for Translating.
- `TermTables` moves to `FLEX_LAST_COLUMN` with minimum widths.
- A `TextFormatter`-based protected editor.
- `LeaveGuard` gains a dirty-form check.
- `LayoutFitTest` runs in `en` and `uk`.
- `ContrastTest` covers links and the selected bar.
- A first-launch local probe, under decision 13.

## Owner notes after the final review (2026-10-20)
| ID | Note | Mockup and documents |
|---|---|---|
| I5 | "We do not have … dependencies that can help to check the real book lang" — the "text reads as Ukrainian" warning is impossible | The check is real but reads no text: it compares the file's package language with its chapters' language tags (`ImportStates` → `LanguageWarning.Mismatch`). Reworded to say exactly that ("The file names two languages…"); "Translate from" says the language comes from the file or from the model (ADR-0042) |
| I6 | Return "Drop another book here or" to the centre of the widget | The drop zone is again a full-width row with its content centred, under the title |
| S6 | Newer OpenAI and Anthropic models no longer accept `temperature`; the app needs "no temperature, app suggested, user selected" and must survive rejected or unknown parameters | Settings › Models: Temperature = Let BookLoom choose · My value · Don't send; "Settings this model refused" lists learned refusals with Send again. Every refused optional setting is dropped, the request is repeated once, and the refusal is remembered per provider + model and logged once (README "Refused settings (S6)", FR-INFER-09e). Code: extend `CapabilityRejections` |
| AG | Instructions for AI agents on using the mockup | README "For AI agents": render in a browser at fixed sizes, capture the app in the same state and size, compare screenshots, never port mockup-only elements, translate the technique not the markup, behaviour from the README, prove with tests. Pointers in `08_UI_SCREENS_AND_STATES.md` and `07_UI_ARCHITECTURE_JAVAFX.md` |

## Research that shaped the mockup

The full notes are in the owner-local copy at `.temporary_context/NewObservations/analysis/09-ux-research.md`, beside the per-PDF readings `v2-*.md` (copied from this
session).

- **Progress:**
  - A determinate bar counted in segments, never in calls, so batches do not make it jump.
  - The phase in words, elapsed time, and time left as a rounded range shown only after enough samples.
  - Pause, Stop and Skip always visible.
  - No modal for the main run.

  Sources: [NN/g progress indicators](https://www.nngroup.com/articles/progress-indicators/),
  [Microsoft progress controls](https://learn.microsoft.com/en-us/windows/apps/design/controls/progress-controls),
  [Carbon loading](https://carbondesignsystem.com/patterns/loading-pattern/).
- **CAT tools:**
  - Source and target side by side, with a small set of statuses shown as colour plus a word.
  - QA findings tiered by severity.
  - Only the terms present in a segment are listed.

  Sources: [Crowdin editor](https://support.crowdin.com/online-editor/),
  [Weblate](https://docs.weblate.org/en/latest/user/translating.html),
  [OmegaT panes](https://omegat.sourceforge.io/manual-snapshot/en/chapter.panes.html).
- **LLM call inspection:**
  - A compact card per call: kind, segments, attempt, state, wall time, time to first token, prompt, cached and output
    tokens, tok/s and stop reason.
  - Messages grouped by role, with a Rendered \| Raw switch and Copy.
  - Batch items as id-to-result chips linked to the segment rows.

  Sources: [Ollama usage fields](https://docs.ollama.com/api/usage),
  [Langfuse trace view](https://langfuse.com/changelog/2025-03-19-new-trace-view).
- **Provider forms:**
  - Kind, base URL, auth by reference, extra headers, and endpoint overrides.
  - A discovered model list with Refresh, plus a typed id.
  - "Provider default" toggles, so a parameter that is off is really omitted.
  - A warning when an OpenAI-compatible URL lacks `/v1`.

  Sources: [Open WebUI](https://docs.openwebui.com/getting-started/quick-start/connect-a-provider/starting-with-openai),
  [Jan](https://jan.ai/docs/desktop/remote-models/custom-endpoint),
  [Cline](https://docs.cline.bot/provider-config/openai-compatible).
- **Dialogs and scrollbars:**
  - At most about 90 % of the window, a fixed header and footer, the safe button as the Esc target.
  - No nested modals.
  - Scrollbars visible whenever content overflows, with a contrasting thumb.

  Sources: [NN/g modal vs non-modal](https://www.nngroup.com/articles/modal-nonmodal-dialog/),
  [Fluent dialogs](https://learn.microsoft.com/en-us/windows/apps/design/controls/dialogs-and-flyouts/dialogs),
  [NN/g scrollbars](https://www.nngroup.com/articles/scrolling-and-scrollbars/).

  The research suggests a separate window for reference content such as call logs. The owner's decision 1 overrides
  this: the dialog is large and resizable within the window instead.

## Code facts the change builds on (checked 2026-10-19)

- **Dialogs.**
  - `ui/ModalHost` (scrim, focus trap, Esc) hosts every `dialog/ModalCard`.
  - `ui/BusyHost` is the busy card, and its `#busy-show-calls` opens `control/ModelCallsWindow`, the only `new Stage`
    in `:ui` apart from the startup-failure stage and the file choosers.
  - The progress dialog replaces `BusyHost`'s card and `ModelCallsWindow`, and is fed by the same `state/CallFeed` /
    `ActivityTracker.Handle.calls(...)`.
- **Toasts.** `notify/ToastStack` (`#shell-toast-host`) is anchored bottom-centre; moving it is a layout change.
- **Scrollbars.** The `theme.css` scroll-bar baseline (about lines 225–262) sets no width, so Modena's thin default
  applies.
- **Batch display.**
  - `CallSnapshots` puts **all** of a batch's segments on the first `CallSnapshot`, and `LiveCallView.showRows` builds a
    row for each one inside a ScrollPane fixed at `SEGMENTS_HEIGHT = 220` px. That is why only one row shows.
  - The parsed reply (`BatchReplyParser`, ids 1..n, `ItemStatus` OK / MISSING / DUPLICATE / EXTRA / MERGED_SUSPECT) is
    **not** put on the snapshot. A row's target appears only when that segment's own turn emits `SegmentDrafted`.
  - **Needed:** add the per-item result (id → target, status) to `CallSnapshot`. This is a `:api` record change plus
    `CallSnapshots.answered` — a small pipeline-side change the UI requires.
  - **Needed:** per-row states `queued / in progress / translated / fell back / flagged`, derived from the call state
    plus `SegmentOutcomeNote`.
  - `LiveCallState` keeps only the current and previous call. The Detailed mode's call list needs a bounded history
    (for example the last 50 calls).
- **Why the time left goes wrong.** `RunClock.timeLeft(pending)` multiplies the average of the **last 20** segment times
  by **all pending segments**, so one slow stretch is projected over the rest of the book. Meanwhile the percentage
  counts segments equally, including the instant kept-as-is ones. Review passes and Max's work after the last segment
  are in neither figure. The fix is the step ledger and the per-kind estimate in README G10.
- **Progress figures.** "Chapter n of N · chunk n/N" and the percentage come from `RunCounts` and `RunFigures`; there is
  no "segment n of N" string yet (decided + 1 of total is available). The ETA (`RunClock`, average of the last 20
  segments, after 5 decisions) and tok/s (`ThroughputMeter`) already exist.
- **Accept and Save edit.**
  - Accept means FLAGGED → ACCEPTED with the machine target, and is off while the editor is dirty.
  - Save edit means REVISED with the person's target, and needs a change (`SegmentActions`, `ReviewAvailability`).
  - Only the labels and help text change.
- **Review context.** `ContextSection` shows the stored `SegmentRecord.context`. For a batch-adopted segment that is
  its own `ContextPackage`, not the `BatchContext` that was sent, so T8 needs the batch's sent sections stored with the
  record (pipeline-side, small).
- **Duplicate finding label.** `FindingRows.plainRow` prints "{kind} · {severity}" and then a `FindingBadge` with the
  same kind.
- **Running summary.**
  - `RollingSummaryKeeper` refreshes a code-built summary every 20 accepted segments and at each unit's end.
  - The model-written one exists only on Max at the unit's end, and drafts see a summary only when it is model-written.
  - T5 is a display decision: show what exists and say why when there is nothing.
- **Export tiles.** `ExportResult.reset()` shows "—" on purpose until an `ExportOutcome` arrives. E1 needs a separate
  "before export" figure source; the run's `RunFigures` already holds the counts.
- **Title card.** `BookTitleViewModel.read` looks up `aux:title` / `aux:creator:0`. Markdown uses `aux:fm:*` and TXT
  has no metadata, so the card is empty for those. E1 needs a per-format title lookup and an explanation line.
- **Steps for the progress dialogs.**
  - **Brief:** 1 `book-knowledge` (when the title is not the file name), then K `brief-suggestion` windows (3 / 4 / 5
    for Fast / Balanced / Max), then 1 `brief-synthesis`.
  - **Names:** scan → names → terms-scan → terms-review → terms-read → names-translate → terms-translate → write.
  - **Export:** validating → consistency-retry → retry-doubted → consistency-neighbour → writing.
  - **Prompt sections:** `PromptSection(slot, heading, origin, lines)` already exists. The parsed renderer is a `:ui`
    view over it; `items` lines are `<s id="N">…</s>`, and pairs are `Source:` / `Translation:`.

## Provider and model settings: what exists and what this change adds

| Setting | Today | This change |
|---|---|---|
| Provider list, current badge | Read-only, two presets (`InMemoryProviderConfigs`) | Add, edit, delete, save; stored in SQLite |
| Name | i18n map for the two presets | `ProviderConfig.displayName` |
| Kind | `ProviderKind` OLLAMA / OPENAI_COMPATIBLE, not editable | Selectable: Ollama native or OpenAI-compatible (LM Studio, llama.cpp, vLLM, gateways) |
| Auth + key reference | Missing (`ErrorCode.missingCredential` is unused) | None / Bearer / API-key header; env-var name only; checked before a call |
| Base URL | In the record and `--base-url` | Editable; a warning when an OpenAI-compatible URL lacks `/v1` |
| Models / inference endpoints | Hard-coded (`/api/tags`, `/models`, `/chat/completions`) | Two override paths; inference is disabled for Ollama |
| Custom headers | Missing | A key/value table, values allowed as env-var references |
| Custom models | Exists (the model box is editable) | "Use custom models" list |
| Timeouts | Record fields; only `--timeout` sets them | Connect and request timeouts in the form |
| Diagnostics | Three tests with chips | The same three, each opening the inference-test dialog with URL, status, time and body |
| Test an unsaved form | Missing (tests resolve a registered id) | The verifier takes the form's config |
| Per-model temperature | Constants per call kind (`ChatRequests`) | Opt-in override (off = the per-kind defaults) |
| Context window | Fixed 16,384 (`TokenBudget.EFFECTIVE_CONTEXT`); detected value used only for budgeting | Opt-in override, pre-filled from `ModelCapabilities.contextLength`, shown as "detected" |
| Max output tokens | Computed per call (`OutputLimit`) | Opt-in cap per provider + model |
| `max_tokens` vs `max_completion_tokens` | Only `max_tokens` | A choice per model, disabled for Ollama |
| Reasoning, sampling | Env vars `BOOKLOOM_REASONING`, `BOOKLOOM_SAMPLING` | Generation tab: reasoning off / low / on; sampling server / tuned |

The Generation and Automation tabs carry only knobs the code has:
- the quality dial's effects (read-only table from `DialParameters`);
- review mode;
- reasoning and sampling;
- per-kind timeouts (read-only);
- the default "Also translate" and consistency-pass choices;
- resume on launch, which comes with persistence.

## Persistence (the second half of the change)

- **Projects:** each project's book, brief, glossary, recurring terms, segment records, translation memory, summary and
  run state are stored. The Projects screen lists them with progress and last opened time, and lets you Open, Resume
  and Remove (the project only, never the book file).
- **Project names:** `Project` gains `name` and `nameOrigin` (AUTO / USER). The store keeps names unique
  (case-insensitive) and finds projects by `(contentHash, source, target)` for the pair check. "Translate into another
  language" reuses the stored book by its content hash and copies the brief and the term lists, with targets emptied.
- **Settings:** providers, the per-model parameters, theme, UI language and review mode survive a restart. Secrets are
  never stored, only references.
- **Storage & logs tab:** the database location and size, Open folder, a backup copy, the log folder, the detailed-log
  switch, Save diagnostic bundle.
- **Resume:** reopening a project whose provider or model is missing or changed shows the resume-binding dialog pair
  that already exists in the mockup.
- The rules in `.claude/rules/persistence-sqlite.md` apply: WAL, JDBI, a single writer, atomic per-chunk
  checkpoints. There is no Flyway and no migration before the first release: one schema file, and a database with
  another version is renamed aside (ADR-0043).

## Open questions for the owner

1. **Settings subtitle.** "Everything is local. Nothing leaves your machine." The first reading said the ✗ crossed it
   out; the zoomed check shows the ✗ is on the Models tab. The mockup keeps a reworded line ("…The only network calls go to the provider you configure."). Say if it should go.
2. **Projects toolbar.** The blue loop "Show when there are projects" also takes in the search and filter row. The
   mockup hides search and filter in the empty state and keeps "+ New translation" in both states.
3. **Inference test "Ok".** The mockup treats it as Close; it does not accept or change the model.
4. **Running summary (T5).** The mockup shows the summary that exists and where it comes from. Writing a model summary more often than
   at chapter end would be a pipeline change, which belongs to `redesign-translation-pipeline` rather than this one.
5. **Copying into a new language.** "Translate into another language" copies the brief and the term lists (terms,
   types, genders) by default, with the targets emptied. Say if the recurring terms should not be copied, or if the
   translation memory of another language should never be offered.
6. **Sleep sentence.** "After sleep, translating goes on by itself" must be verified once with a real laptop sleep on
   e4b before it ships.
7. **Brief while translating.** The mockup makes it read-only until a pause. Say if some fields (tone, voice) should
   stay editable. That needs proof first that the engine rereads them mid-run.
