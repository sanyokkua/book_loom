**Status:** Final **Owner:** architect **Audience:** product, architect, engineering, QA, UX **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
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
| 1. Open      | Import        | Drop or pick a book; confirm the detected-file card (format, source language, counts, cover).                                                                                                                                                                                                                                                                | FR-IMPORT-01..07                       |
| 2. Brief     | Book Brief    | Confirm source/target language; set genre, register, voice/era, audience; set policies and the faithful↔natural slider; pick the quality dial.                                                                                                                                                                                                               | FR-BRIEF-01..08                        |
| 3. Structure | Structure     | Review the detected reading order and chapter tree; confirm what will be translated vs preserved.                                                                                                                                                                                                                                                            | FR-DOC-01, FR-DOC-08                   |
| 4. Glossary  | Names & style | Review the names/terms proposed by the **glossary pre-scan** — an LLM call that suggests candidates with type and provisional gender (DD-46); edit target renderings, type, gender; lock entries. When the provider is offline or the call is disabled, the proposals come from the deterministic frequency + capitalization fallback with gender `unknown`. | FR-GLOSS-01..05                        |
| 5. Translate | Translating   | Start the run; the pipeline processes the whole book automatically; the user may pause, stop (into the resumable `stopped` state), resume, or leave.                                                                                                                                                                                                         | FR-ALGO-01, FR-RESUME-03, FR-RESUME-05 |
| 6. Review    | Review        | Optionally review the flagged queue side by side and accept/edit/retry/skip.                                                                                                                                                                                                                                                                                 | FR-REVIEW-01..07                       |
| 7. Export    | Export        | Choose the save path — the format is fixed to the original and shown as a read-only label (export is same-format-only, DD-30); optionally run the final consistency pass and side exports; confirm on completion.                                                                                                                                            | FR-EXPORT-01..06                       |

Export is reachable at any time from step 5 onward, independent of remaining flagged segments (FR-REVIEW-07).

## automatic-first-behaviour {#automatic-first-behaviour}

The pipeline is automatic-first (DD-15): once started, it drives the entire book to completion without human interaction
for the vast majority of segments (a ~99% accepted / ~1% flagged split is a non-gating aspiration, measured in
segments). Concretely:

- Before the run, the prep phase seeds the glossary via the LLM pre-scan (deterministic fallback offline, DD-46) so the
  Names & Style step opens pre-populated.
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
algorithm. The review-mode dial is the **sole owner of τ** (DD-45, FR-REVIEW-02).

| Mode       | Behaviour                                                                                    | Trust threshold effect                                 | Requirement  |
|------------|----------------------------------------------------------------------------------------------|--------------------------------------------------------|--------------|
| Unattended | Runs to completion and exports-ready with no expectation of review; only hard failures flag. | Low threshold for flagging (auto-accept aggressively). | FR-REVIEW-01 |
| Assisted   | Runs automatically but flags borderline segments for a quick side-by-side pass.              | Moderate threshold; borderline scores flag.            | FR-REVIEW-01 |
| Manual     | Runs automatically but presents more segments for confirmation before acceptance.            | High threshold; more segments routed to review.        | FR-REVIEW-01 |

## trust-threshold-dial {#trust-threshold-dial}

A single trust-threshold dial (FR-REVIEW-02) is the one control the user turns to trade automation for oversight. It
sets τ, the minimum deterministic confidence for automatic acceptance (DD-45):

- A segment is ACCEPTED automatically when the hard gates pass **and** its deterministic confidence ≥ τ **and** (the
  judge is off **or** the judge score ≥ τ_judge, which defaults to τ).
- A segment that misses the accept rule enters self-heal; if it still fails after N attempts, it is FLAGGED.
- The three review modes are named positions of this dial, and the dial is the **exclusive owner of τ** (DD-45). The
  quality dial (Fast/Balanced/Max) owns only mechanics — chunk size, preceding-target count, repair budget N, and
  whether the judge and backward revision run — and never sets τ (FR-ALGO-11).

The dial is the only quality/automation knob the user must understand; all other tuning lives in Settings → Generation
for advanced users (where a Manual τ override, highest precedence, is available).

## workflow-states-and-recovery {#workflow-states-and-recovery}

| Condition                   | Behaviour                                                                                                                                                                                                                                             |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Provider error mid-run      | Run pauses into a provider-error state with a banner; retryable errors are retried automatically first (FR-INFER-05).                                                                                                                                 |
| User pause                  | Run halts at a chunk boundary; progress is checkpointed; resume continues from the first PENDING segment.                                                                                                                                             |
| User stop                   | Stop transitions the run into the resumable `stopped` state at the next chunk boundary (FR-RESUME-05). Cancellation is never surfaced as an error; the run is checkpointed and can be resumed later from the first PENDING segment.                   |
| Crash / quit                | On relaunch or reopen, the application offers to resume from the last checkpoint (FR-RESUME-02); resume starts at the first PENDING segment — FLAGGED segments are terminal for the run, surfaced in the review queue, and excluded from auto-resume. |
| Source changed since import | The application warns before resuming against a changed source hash (FR-RESUME-04, EC-RESUME-*).                                                                                                                                                      |
