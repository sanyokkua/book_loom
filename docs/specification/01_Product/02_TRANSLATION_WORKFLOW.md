**Status:** Final **Owner:** architect **Audience:** product, architect, engineering, QA, UX **Last Updated:**
2026-09-27 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/specification/01_Product/06_REVIEW_AND_EDITING.md`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`, `docs/specification/diagrams/pipeline.mermaid`

# Translation Workflow

This document defines the end-to-end user workflow, the three review modes, the single trust-threshold dial, and the
automatic-first behaviour. It is the user-facing counterpart to the pipeline described in `05_TRANSLATION_ALGORITHM.md`;
the high-level flow is drawn in `docs/specification/diagrams/pipeline.mermaid`.

## end-to-end-workflow {#end-to-end-workflow}

The primary journey is a linear path the user can complete with minimal input; the automatic pipeline does the rest.

| Step         | Screen        | User action                                                                                                                                                                                                                                                                                                                                                  | Requirements                           |
|--------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| 1. Open      | Import        | Drop or pick a book; confirm the detected-file card (format, declared language, unit and segment counts, title/author; cover and detected language are later).                                                                                                                                                                                                                                                                | FR-IMPORT-01..07                       |
| 2. Brief     | Book Brief    | Confirm source/target language; set genre, register, voice/era, audience; set policies and the faithful↔natural slider; pick the quality dial.                                                                                                                                                                                                               | FR-BRIEF-01..08                        |
| 3. Structure | Structure     | Review the detected reading order (a flat list of units in this build; confirming translate-vs-preserve is later).                                                                                                                                                                                                                                                            | FR-DOC-01, FR-DOC-08                   |
| 4. Glossary  | Names & style | Review the names/terms proposed by the **deterministic frequency-and-capitalization scan**, which seeds the glossary and grows it again at the end of each body unit; edit target renderings, type, gender; lock entries. The **LLM pre-scan is a button on this screen**, never automatic and never run at a run's start — the person presses it to add type and provisional gender the deterministic scan cannot infer (DD-46). | FR-GLOSS-01..05                        |
| 5. Translate | Translating   | Start the run; the pipeline processes the whole book automatically; the user may pause, resume, stop (a terminal `stopped` state; Resume starts a new run at the first PENDING segment and keeps flagged segments in the review panel), or leave.                                                                                                                                                                                                         | FR-ALGO-01, FR-RESUME-03, FR-RESUME-05 |
| 6. Review    | Review        | Optionally review the flagged queue side by side and accept/edit/revert/retry/skip.                                                                                                                                                                                                                                                                                 | FR-REVIEW-01..07                       |
| 7. Export    | Export        | Export is its own action, started from this screen, runnable at any time except while a run is actively translating; it chooses the save path, Browse, and the replace-if-exists switch, and confirms a finished export in an export-complete dialog — fixed to the original format, export is same-format-only (DD-30). The final consistency pass and side exports are offered here.                                                                                                                                            | FR-EXPORT-01..06                       |

Export is reachable at any time (subject only to pausing a running run), independent of remaining flagged segments
(FR-REVIEW-07).

## automatic-first-behaviour {#automatic-first-behaviour}

The pipeline is automatic-first (DD-15): once started, it drives the entire book to completion without human interaction
for the vast majority of segments (a ~99% accepted / ~1% flagged split is a non-gating aspiration, measured in
segments). Concretely:

- Before the run, the prep phase seeds the glossary via the deterministic frequency-and-capitalization scan (DD-46) so
  the Names & Style step opens pre-populated; the LLM pre-scan runs only from its own button, never at a run's start.
- Every chunk is drafted, deterministically checked, judged (when the dial enables the judge), and self-healed on
  failure, all without prompting the user.
- Failures flag only the **offending segments**: a segment that still fails after the repair budget N is FLAGGED;
  everything else is auto-accepted.
- The user can close the Book Brief and glossary steps quickly and rely on defaults; sensible defaults are provided for
  every brief field.
- The run is crash-safe and resumable (FR-RESUME-01, FR-RESUME-02), so leaving or losing the app does not lose progress;
  resume picks up at the **first PENDING segment** — FLAGGED segments are terminal for the run and wait in the review
  queue.

## review-modes {#review-modes}

The three modes are presets over the single trust-threshold dial and the pipeline; they do not change the underlying
algorithm. The review-mode dial is the **sole owner of τ** (DD-45, FR-REVIEW-02), resolved once at application launch
from `BOOKLOOM_REVIEW_MODE` (or the system property `bookloom.review.mode`) until Settings persist the choice.

| Mode       | Behaviour                                                                                                    | Trust threshold | Requirement          |
|------------|----------------------------------------------------------------------------------------------------------------|-----------------|-----------------------|
| Unattended | Never pauses for review (a provider error still pauses); flagged segments wait in the review panel.           | τ = 0.60        | FR-REVIEW-01, ADR-0036 |
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
  override of τ is not offered in this build.

The dial is the only quality/automation knob the user must understand; all other tuning lives in Settings → Generation
for advanced users.

## workflow-states-and-recovery {#workflow-states-and-recovery}

| Condition                   | Behaviour                                                                                                                                                                                                                                             |
|-----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Provider error mid-run      | Every review mode, including Unattended, auto-pauses with the provider-error state naming the failure; Retry now resumes the run and re-issues only the interrupted call; the run's counts are kept unchanged while paused (FR-INFER-05).           |
| User pause                  | Pause aborts the request in flight and the run halts at once; the interrupted call is redone on resume; progress is checkpointed for the rest of the session; resume continues from the first PENDING segment.                                       |
| User stop                   | Stop aborts the request in flight and ends the run in the terminal `stopped` state. Stopping is never surfaced as an error; every decision made before the stop is kept for the rest of the session, and Resume starts a new run at the first PENDING segment, with flagged segments still waiting in the review panel. Nothing survives closing the application. |
| Crash / quit                | Progress is kept in memory only until the application closes; a restart offers no resume in this build (persistence arrives in a later change).                                                                                                       |
| Source changed since import | The change is detected by hash **at export**, which is refused with `ErrorCode.validation` and writes nothing; resuming a run within the same session does not re-read the source file (FR-RESUME-04, EC-RESUME-*).                                   |
