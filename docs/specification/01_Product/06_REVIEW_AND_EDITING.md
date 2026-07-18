**Status:** Final **Owner:** architect **Audience:** product, architect, engineering (`:ui`, `:pipeline`), QA, UX **Last
Updated:** 2026-07-18 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`,
`docs/specification/01_Product/07_SETTINGS.md`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/01_Product/12_PROMPT_CATALOG.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`

# Review and Editing

This document specifies the segment status state machine, the flagged queue, the side-by-side compare, the segment
actions, and the export-any-time guarantee. It realizes `FR-REVIEW-*`. Review is opt-in; the pipeline is automatic-first
(DD-15).

## segment-status-state-machine {#segment-status-state-machine}

Every segment moves through a deterministic state machine (FR-REVIEW-06):

| From                  | Event                                                                                     | To       |
|-----------------------|-------------------------------------------------------------------------------------------|----------|
| —                     | Imported                                                                                  | PENDING  |
| PENDING               | `hardGatesPass ∧ confidence ≥ τ ∧ (judgeOff ∨ judgeScore ≥ τ_judge)`                      | ACCEPTED |
| PENDING               | Still failing after repair budget N (QA re-entry rounds), or below τ with review required | FLAGGED  |
| FLAGGED               | User accepts as-is                                                                        | ACCEPTED |
| FLAGGED               | User edits and confirms                                                                   | REVISED  |
| FLAGGED               | User retries / retry-with-note and it now passes                                          | ACCEPTED |
| ACCEPTED / FLAGGED    | Backward revision re-renders the segment                                                  | REVISED  |
| REVISED (machine)     | Backward revision re-renders it again                                                     | REVISED  |
| REVISED (user-edited) | Backward-revision sweep — **only with user opt-in**                                       | REVISED  |
| REVISED               | User edits again                                                                          | REVISED  |

`τ` is owned by the **review-mode dial** (Unattended/Assisted/Manual), with an advanced Manual-settings override; the
quality dial does not set it, and `τ_judge` defaults to `τ` (DD-45).

Rules: PENDING is the only initial state; ACCEPTED, FLAGGED, and REVISED are the terminal working states; a segment
never returns to PENDING once processed. Resume picks up at the **first PENDING** segment; FLAGGED is
**terminal-for-run** — surfaced in the review queue and excluded from auto-resume. Backward revision (FR-ALGO-D1) may
move an ACCEPTED or FLAGGED segment to REVISED, but **user-edited `REVISED` segments are protected**: the sweep
**proposes, never overwrites**, and re-sweeps a user-edited segment only with explicit user opt-in.

## flagged-queue {#flagged-queue}

| ID           | Requirement                                                                                                                                                           |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-REVIEW-Q1 | The flagged queue lists all FLAGGED segments with locator (e.g. `ch5 · p12`), the QA findings that caused the flag, and a preview.                                    |
| FR-REVIEW-Q2 | The queue reflects the segments the pipeline could not clear automatically (aspirationally a small residual, ~1% of **segments** — a non-normative goal, not a gate). |
| FR-REVIEW-Q3 | Selecting a queue item opens it in the side-by-side compare.                                                                                                          |
| FR-REVIEW-Q4 | An empty queue shows a "Nothing flagged" empty state.                                                                                                                 |

## side-by-side-compare {#side-by-side-compare}

| ID           | Requirement                                                                                          |
|--------------|------------------------------------------------------------------------------------------------------|
| FR-REVIEW-C1 | The compare shows source and target in two panes side by side (two `TextArea`), with no inline diff. |
| FR-REVIEW-C2 | The target pane is editable; the source pane is read-only.                                           |
| FR-REVIEW-C3 | The QA findings for the segment are visible alongside the panes.                                     |

## segment-actions {#segment-actions}

| Action          | Effect                                                                                                                                                                | Resulting status                                |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| Accept          | Keep the machine target as-is.                                                                                                                                        | ACCEPTED                                        |
| Edit            | Apply the user's edited target.                                                                                                                                       | REVISED                                         |
| Retry           | Re-run the segment through the pipeline, **reconstructing the original chunk context** (same brief, glossary, preceding-target window, and TM the segment first saw). | ACCEPTED if it now passes, else remains FLAGGED |
| Retry with note | Re-run as above, reconstructing the original chunk context and injecting the user's free-text note into the draft/directed-fix `{{userNote}}` slot.                   | ACCEPTED if it now passes, else remains FLAGGED |
| Skip            | Leave flagged and move to the next item.                                                                                                                              | FLAGGED (unchanged)                             |

| ID           | Requirement                                                                                                                                                                                                                      |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-REVIEW-A1 | Retry and retry-with-note route through the same InferenceGate and QA gate as the automatic pipeline, reconstructing the segment's original chunk context (single-segment call).                                                 |
| FR-REVIEW-A2 | Retry-with-note captures a free-text instruction injected into the `{{userNote}}` slot of the draft/directed-fix templates (see `08_UI_SCREENS_AND_STATES.md#dialog-retry-with-note`, `12_PROMPT_CATALOG.md#draft-translation`). |
| FR-REVIEW-A3 | Every action persists atomically so review progress survives a crash.                                                                                                                                                            |

## export-any-time {#export-any-time}

| ID           | Requirement                                                                                                                       |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------|
| FR-REVIEW-X1 | Export is available at any time, regardless of how many segments remain FLAGGED (FR-REVIEW-07).                                   |
| FR-REVIEW-X2 | On export with remaining flags, the last accepted/machine target is used for flagged segments; the user is informed of the count. |
