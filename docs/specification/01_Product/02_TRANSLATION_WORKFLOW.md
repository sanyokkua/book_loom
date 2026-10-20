**Status:** Final **Owner:** architect **Audience:** product, architect, engineering, QA, UX **Last Updated:**
2026-10-19 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/specification/01_Product/06_REVIEW_AND_EDITING.md`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`, `docs/specification/mockups/README.md`,
`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`, `docs/specification/diagrams/pipeline.mermaid`

# Translation Workflow

This document defines the end-to-end user workflow, the three review modes, the single trust-threshold dial, and the
automatic-first behaviour. It is the user-facing counterpart to the pipeline described in `05_TRANSLATION_ALGORITHM.md`;
the high-level flow is drawn in `docs/specification/diagrams/pipeline.mermaid`.

**Status convention.** Behaviour marked **planned — UI finalization + SQLite** is the target of the next change
(`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`; every screen is described in
`docs/specification/mockups/README.md`) and is not built today. Everything else describes the current build. In
particular, nothing is saved today: persistence is in memory (ADR-0034) and the SQLite store is planned.

## end-to-end-workflow {#end-to-end-workflow}

The primary journey is a linear path the user can complete with minimal input; the automatic pipeline does the rest.
The window has **five workflow steps** — Import, Book Brief, Names & style, Translating, Export — and **Review is a
dialog**, not a step (mockups/README.md, G7). Behind the steps sits **Projects** (planned): a project is one translation
of a book, and the steps are unavailable until a project is open (G6).

| Step               | Screen        | User action                                                                                                                                                                                                                                                                                                                                                  | Requirements                           |
|--------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| 0. Choose a project | Projects (planned — UI finalization + SQLite) | Start a new translation, or open, continue, rename or remove a stored one; the list is grouped by book. Workflow steps 1–5 stay greyed until a project is open. Today the window opens straight on Import and holds one current project. | FR-RESUME-01, FR-RESUME-02 |
| 1. Open            | Import        | Drop or pick a book; read the one-line verdict and the About this book fold (format, declared language, segment counts, title/author; cover and detected language are later); browse the book's structure (chapters, segments, text) on the same screen. | FR-IMPORT-01..07, FR-DOC-01, FR-DOC-08 |
| 2. Brief           | Book Brief    | Confirm source/target language; set genre, register, voice/era, audience; set policies and the faithful↔natural slider; pick the quality dial. "Fill in from the book" opens a progress dialog and writes nothing until the person applies it. | FR-BRIEF-01..08                        |
| 3. Names & style   | Names & style | Review the names/terms proposed by the **deterministic frequency-and-capitalization scan**, which seeds the glossary and grows it again at the end of each body unit; edit target renderings, type, gender; lock entries. The **LLM pre-scan is a button on this screen** (Scan · Review · Translate on a card, or Prepare names automatically), never automatic and never run at a run's start — the person presses it to add type and provisional gender the deterministic scan cannot infer (DD-46). The step is optional: left alone, BookLoom builds the glossary and recurring terms while it translates. | FR-GLOSS-01..05                        |
| 4. Translate       | Translating   | Start the run; the pipeline processes the whole book automatically; the user may **pause**, **stop** (a terminal `stopped` state), resume or continue, or leave (see Pause vs Stop below). | FR-ALGO-01, FR-RESUME-03, FR-RESUME-05 |
| (Review)           | Review dialog | Optionally review the segments that need it side by side and accept/edit/revert/retry/skip. Opened from Review (n) in the toolbar or the "n to review" button, and by itself when a translation finishes with segments to review (see below). | FR-REVIEW-01..07                       |
| 5. Export          | Export        | Export is its own action, started from this screen. It is disabled while the translation is running, pausing or stopping, and allowed once it is paused, stopped, failed or finished (a partial export keeps the original text in untranslated segments). It chooses the save path and asks before replacing an existing file, and confirms a finished export in an export-complete dialog — export is same-format-only (DD-30). The last check before saving (the consistency pass) and side exports are offered here. | FR-EXPORT-01..06                       |

Export is reachable whenever the run is not actively translating, independent of remaining segments that need review
(FR-REVIEW-07).

### Status of the redesign

- **Built today:** the five steps with Import holding the book's structure, the review desk as a panel on the
  Translating screen, the "replace the run?" prompt when a book is opened while a translation exists, and the
  Replace-if-exists switch on Export.
- **Planned — UI finalization + SQLite:** the Projects screen and the locked steps (G6); a project name and several
  translations of one book (below); the review desk as a large in-window dialog that opens by itself; Export asking
  before it replaces a file; the partial-export rules below; progress that survives a restart.

## first-launch {#first-launch}

*Planned — UI finalization + SQLite (owner decision 13; mockups/README.md, "First launch and the AI model").* On the
first launch BookLoom looks **once, by itself** for Ollama (`127.0.0.1:11434`) and LM Studio (`127.0.0.1:1234`) on this
computer. If it finds one with models it creates the provider and selects the only model, or asks which one when there
are several. This is the one exception to "no network call the person did not start": it is local only, happens once,
needs no answer and degrades silently when nothing is found. The empty Projects screen then shows either the model
found, or **No AI model found** with Try again and a way to Settings. Every later check is user-triggered. The
exception is to be written into `AGENTS.md` and `.claude/rules/offline-and-privacy.md` with an ADR in the same change;
until then the offline invariant has no exception and nothing probes at launch.

## projects-and-names {#projects-and-names}

*Planned — UI finalization + SQLite (mockups/README.md, "Project names — one book, several translations (PN)").* A
project is one translation of a book. The same book can be translated into several languages, or into the same
language more than once (for example with another model or quality).

- The **automatic name** is `<book title> · <SRC> → <TGT>` (for example `Frankenstein · EN → UK`) and follows the
  languages while it is automatic; a typed name never changes.
- A **language pair is unique per book** (same content hash) unless the project is named. Choosing a used pair opens
  **Same language pair**: open the existing project, or make a second translation with a name of its own (required,
  unique, case-insensitive).
- **Translate into another language** starts a new project from an already-imported book: nothing is imported again,
  and the brief and the term lists are copied with their translations emptied.
- Project names label the Projects list, the title bar and side files, so exports of two projects with the same pair do
  not collide.

## automatic-first-behaviour {#automatic-first-behaviour}

The pipeline is automatic-first (DD-15): once started, it drives the entire book to completion without human interaction
for the vast majority of segments (a ~99% accepted / ~1% flagged split is a non-gating aspiration, measured in
segments). Concretely:

- Before the run, the prep phase seeds the glossary via the deterministic frequency-and-capitalization scan (DD-46) so
  the Names & Style step opens pre-populated; the LLM pre-scan runs only from its own button, never at a run's start.
- Every chunk is drafted, deterministically checked, judged (when the dial enables the judge), and self-healed on
  failure, all without prompting the user.
- Failures flag only the **offending segments** (shown to the person as "to review"): a segment that still fails after
  the repair budget N is FLAGGED; everything else is auto-accepted.
- The user can close the Book Brief and glossary steps quickly and rely on defaults; sensible defaults are provided for
  every brief field.
- The run is crash-safe and resumable (FR-RESUME-01, FR-RESUME-02) once progress is stored (planned — SQLite), so
  leaving or losing the app does not lose progress; resume picks up at the **first PENDING segment** — FLAGGED segments
  are terminal for the run and wait in the review desk.

## finishing-a-run {#finishing-a-run}

When a translation finishes (`COMPLETED`), the toolbar offers Review (n) and **Continue to Export**. If segments need
review, a banner on Translating says so ("You can export now and review later") and **the review desk opens by
itself**. It is not shown on top of another dialog or visible work: it waits until none is open (mockups/README.md, G1,
`openWhenFree`). When nothing needs review the desk does **not** open and the banner reads "Translation finished.
Nothing needs your review." Settings › Automation (planned) can turn the automatic opening off. Today the finished run
shows the review panel on the Translating screen and does not open a dialog (planned — UI finalization + SQLite).

## review-modes {#review-modes}

The three modes are presets over the single trust-threshold dial and the pipeline; they do not change the underlying
algorithm. The review-mode dial is the **sole owner of τ** (DD-45, FR-REVIEW-02), resolved once at application launch
from `BOOKLOOM_REVIEW_MODE` (or the system property `bookloom.review.mode`) until Settings persist the choice; the
planned Settings › Automation tab ("When to stop and ask you") will own the choice.

| Mode       | Behaviour                                                                                                    | Trust threshold | Requirement          |
|------------|----------------------------------------------------------------------------------------------------------------|-----------------|-----------------------|
| Unattended | Never pauses for review (a provider error still pauses); flagged segments wait in the review desk.            | τ = 0.60        | FR-REVIEW-01, ADR-0036 |
| Assisted   | Pauses on each flagged segment for a side-by-side pass before the run continues.                               | τ = 0.75        | FR-REVIEW-01, ADR-0036 |
| Manual     | Pauses after every decided segment — flagged or not — for confirmation (Accept & continue) before it goes on.  | τ = 0.85        | FR-REVIEW-01, ADR-0036 |

## trust-threshold-dial {#trust-threshold-dial}

A single trust-threshold dial (FR-REVIEW-02) is the one control the user turns to trade automation for oversight. It
sets τ, the minimum deterministic confidence for automatic acceptance (DD-45):

- A segment is ACCEPTED automatically when the hard gates pass **and** no soft check failed outright **and** its
  no verified reviewer blocker is left (15d.6: there is no confidence threshold and no judge score).
- A segment that misses the accept rule enters self-heal; if it still fails after N attempts, it is FLAGGED.
- The three review modes are named positions of this dial (τ 0.60 / 0.75 / 0.85), and the dial is the **exclusive
  owner of τ** (DD-45). The quality dial (Fast/Balanced/Max) owns only mechanics — chunk size, preceding-target count,
  repair budget N, and whether the judge and backward revision run — and never sets τ (FR-ALGO-11). A manual Settings
  override of τ is not offered.

The dial is the only quality/automation knob the user must understand; all other tuning lives in Settings → Generation
for advanced users.

## pause-vs-stop {#pause-vs-stop}

*Wording and states: mockups/README.md, "Run states (A)"; the eight `RunState` values already exist.*

- **Pause** is a break inside this translation. The request in flight is aborted and the segments in flight are
  translated again when the person resumes. The toolbar then offers **Resume**.
- **Stop** ends translating for now, for example to close the project (terminal `stopped` state). It is never surfaced
  as an error, and Stop is a secondary (not red) button. The toolbar then offers **Continue translation**, which starts
  a new run at the first PENDING segment. The Stop dialog says how many segments are done and saved and that the ones
  being translated now are translated next time.
- Both keep every decision made so far, and segments that need review keep waiting in the review desk. Today those
  decisions are kept for the rest of the session only; saving them across a restart is planned (SQLite).
- **Try again now** is the one retry verb; "Retry now", "Send again" and "Stay paused" are gone.

## changes-while-translating {#changes-while-translating}

*Planned — UI finalization + SQLite (mockups/README.md, "Changes while translating (I)").*

- **Names & style** stays editable; changes apply to the next segments translated.
- **Book Brief** is read-only while translating ("Pause the translation to change the brief"), and its languages are
  locked once translating has started.
- **Export** is disabled while translating, pausing or stopping. When paused, stopped or failed it is allowed as a
  **partial export**: the translated segments are written and the others keep the original text, the banner says how
  many segments are translated, and the last check before saving is off and disabled ("Runs when the whole book is
  translated").
- A hidden long job (the names pass, the export pass) makes the screen it writes to read-only until it ends.

## workflow-states-and-recovery {#workflow-states-and-recovery}

| Condition                   | Behaviour                                                                                                                                                                                                                                             |
|-----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Provider error mid-run      | Every review mode, including Unattended, pauses with the provider-error state naming the failure; BookLoom tries again by itself, and **Try again now** resumes the run and re-issues only the interrupted call; the run's counts are kept unchanged while paused (FR-INFER-05). When it gives up, the run stays paused with a banner and Try again now. |
| User pause                  | Pause aborts the request in flight and the run halts at once; the interrupted call is redone on resume; progress is checkpointed; resume continues from the first PENDING segment.                                                                   |
| User stop                   | Stop aborts the request in flight and ends the run in the terminal `stopped` state. Stopping is never surfaced as an error; every decision made before the stop is kept, and Continue translation starts a new run at the first PENDING segment, with segments that need review still waiting in the review desk. |
| Crash / quit                | **Today:** progress is kept in memory only until the application closes; a restart offers no resume and nothing survives closing the application. **Planned — SQLite:** each project's book, brief, glossary, segment records, summary and run state are stored as the run goes, so a restart (or a crash) loses at most the work in flight; reopening the project from Projects shows it paused and ready to continue ("Welcome back"). If the AI model is missing or changed, the continue dialogs ask first. Secrets are never stored, only references. |
| Source changed since import | The change is detected by hash **at export**, which is refused with `ErrorCode.validation` and writes nothing; resuming a run within the same session does not re-read the source file (FR-RESUME-04, EC-RESUME-*).                                   |
