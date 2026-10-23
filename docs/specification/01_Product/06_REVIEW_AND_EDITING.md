**Status:** Final **Owner:** architect **Audience:** product, architect, engineering (`:ui`, `:pipeline`), QA, UX **Last
Updated:** 2026-10-19 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`,
`docs/specification/01_Product/07_SETTINGS.md`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/01_Product/12_PROMPT_CATALOG.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/specification/mockups/README.md` ("Dialog · Review")

# Review and Editing

This document specifies the segment status state machine, the review desk (the list of segments to review and the
side-by-side compare), the segment actions, and the export-any-time guarantee. It realizes `FR-REVIEW-*`. Review is
opt-in; the pipeline is automatic-first (DD-15).

**Status of the review UI.** The review desk is a **large in-window dialog** (decision 5, F1; `mockups/README.md`,
"Dialog · Review"). The dialog, its row markers, the single main button, the More menu, the inline note panel, the
shortcuts and the batch's sent sections under "What the model saw" are built (`ReviewDeskCard`, `ReviewViewModel`);
the protected-mark editor is planned (6.6b). The status machine and the actions below (`SegmentActions`) are built and do
not change.

## segment-status-state-machine {#segment-status-state-machine}

Every segment moves through a deterministic state machine (FR-REVIEW-06):

| From                  | Event                                                                                     | To       |
|-----------------------|---------------------------------------------------------------------------------------------|----------|
| —                     | Imported                                                                                  | PENDING  |
| PENDING               | `hardGatesPass ∧ noSoftCheckFailed ∧ noVerifiedBlockerLeft` (15d.6)                          | ACCEPTED |
| PENDING               | Still failing after repair budget N (QA re-entry rounds), or below τ with review required | FLAGGED  |
| FLAGGED               | User accepts as-is                                                                        | ACCEPTED |
| FLAGGED               | User edits and confirms                                                                   | REVISED  |
| FLAGGED               | User retries / retry-with-note and it now passes                                          | ACCEPTED |
| ACCEPTED / FLAGGED    | Backward revision re-renders the segment                                                  | REVISED  |
| REVISED (machine)     | Backward revision re-renders it again                                                     | REVISED  |
| REVISED (user-edited) | Backward-revision sweep — **only with user opt-in**                                       | REVISED  |
| REVISED               | User edits again                                                                          | REVISED  |

`τ` is owned by the **review-mode dial** (Unattended 0.60 / Assisted 0.75 / Manual 0.85); a manual Settings override
is not offered in this build. Neither the quality dial nor the review mode sets a confidence bar any more (15d.6): the review mode decides when the run pauses.

Rules: PENDING is the only initial state; ACCEPTED, FLAGGED, and REVISED are the terminal working states; a segment
never returns to PENDING once processed. Resume picks up at the **first PENDING** segment; FLAGGED is
**terminal-for-run** — surfaced in the review desk and excluded from auto-resume. Backward revision (FR-ALGO-D1) may
move an ACCEPTED or FLAGGED segment to REVISED, but **user-edited `REVISED` segments are protected**: the sweep
**proposes, never overwrites**, and re-sweeps a user-edited segment only with explicit user opt-in.

## flagged-queue {#flagged-queue}

The flagged queue is the review desk's left pane (planned dialog; today the panel's list). In the user's words it is
"the segments to review".

| ID           | Requirement                                                                                                                                                           |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-REVIEW-Q1 | The list shows the FLAGGED segments with locator (e.g. `ch5 · p12`), a **state marker** (to review · accepted · your edit · later) and a one-line "why · text" taken from the QA findings that caused the flag. A filter offers To review (n), Worth a second look (n) and All segments, all three always (All segments lists only decided segments and those kept as source, so it is safe in every review mode); ‹ › move between rows. |
| FR-REVIEW-Q2 | The queue reflects the segments the pipeline could not clear automatically (aspirationally a small residual, ~1% of **segments** — a non-normative goal, not a gate). |
| FR-REVIEW-Q3 | Selecting a row opens it in the side-by-side compare.                                                                                                                 |
| FR-REVIEW-Q4 | An empty list shows "Nothing needs your review".                                                                                                                      |
| FR-REVIEW-Q5 | The desk opens from "Review (n)" or the strip's "n to review", and **opens by itself** when a translation finishes with segments to review (it waits for a free slot; `mockups/README.md`, G1): only when a translating run reaches Completed, `automation.openReviewDesk` is on and segments are left to review — never for a project reopened already completed. |
| FR-REVIEW-Q6 | The header says how many are left ("4 of 6 left — if a segment reads well, press Accept. You can export now and come back later."). While a run translates the desk is **read-only**: the header says "read-only while translating" and a banner says "Pause the translation to edit, accept or try again." with a Pause button. While a retry is in flight on a run that is not translating the desk is read-only too, and the banner says "Trying again… the desk is available when the model answers." with no Pause. |

## side-by-side-compare {#side-by-side-compare}

| ID           | Requirement                                                                                          |
|--------------|--------------------------------------------------------------------------------------------------------|
| FR-REVIEW-C1 | The compare shows source and target in two panes side by side, with no inline diff.                   |
| FR-REVIEW-C2 | The target pane is editable; the source pane is read-only.                                           |
| FR-REVIEW-C3 | The QA findings for the segment are visible alongside the panes (tab **What may be wrong**: the plain reason plus "If it reads well, press Accept."; the rule code and ratio only with the "Technical details" switch). |
| FR-REVIEW-C4 | In the target editor formatting marks are **protected text marks** `⟦1⟧` (decision 12): deleting removes a whole mark, never half; cut and paste can move one; a paste keeps only marks missing from the editor. A strip below says which marks keep bold, links and similar, or "⟦1⟧ is missing — paste it back where the formatting ends." The main button is off while a mark is missing or doubled. The source pane shows the marks as chips. The editor is a `TextArea`; no rich-text library is added. Planned — UI finalization + SQLite. |
| FR-REVIEW-C5 | The editors and the tabs share the dialog's height through a draggable divider (vertical `SplitPane`), so the editors grow with the dialog. Planned. |
| FR-REVIEW-C6 | Tab **What the model saw** shows the stored context snapshot of the segment's draft, parsed into System and User sections (T8). For a batch-drafted segment it must be the context the batch actually sent; today the stored snapshot is the segment's own context package, so storing the batch's sent sections is a small pipeline change. Planned. |
| FR-REVIEW-C7 | Tab **Suggestion** shows Now \| Suggested by the check, with "Use the suggestion". |

## segment-actions {#segment-actions}

The footer has **Close**, a **More ▾** menu and **one main button**.

- The main button reads **✓ Accept & next** and turns into **✓ Save my edit & next** once the text is edited (T7). It
  accepts or saves, then moves to the next segment.
- **More ▾** lists, each with what it does: Leave for later · Try again · Try again with a note… · Undo my edit.
- **Try again with a note** opens an inline panel inside the desk, not a second dialog; so do the questions of Undo my
  edit and of typing that is not saved (`ModalHost` shows one card at a time).
- Shortcuts: Ctrl/⌘+Enter runs the main button (plain Enter does nothing in the desk); Ctrl+Alt+↓/↑ moves between
  segments (Alt+↑/↓ belongs to the editor on macOS).
- Feedback is a footer line (`mockups/README.md`, G2).
- Every action is unavailable while the project's run is RUNNING (the desk is read-only) or a retry is in flight.

| Action          | Effect                                                                                                                                                                  | Resulting status                                  |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| Accept (main button, text unedited) | Keep the machine target as-is. Acts only on a FLAGGED or an ACCEPTED segment (a confirmation on the latter); refused on a PENDING or REVISED one, and on a segment with no machine target. | ACCEPTED                                        |
| Save edit (main button, text edited) | Persist the person's edited target, placeholders included, keeping the machine target apart so a later revert restores it exactly. Refused for a PENDING segment, and when the edit breaks the placeholders beyond what the gate can repair (a token only lost or repeated is put back by position). Withdraws a backward-revision proposal waiting on the segment. | REVISED                                         |
| Undo my edit (Revert) | Clear the saved edit and restore the stored machine target. Acts only on a REVISED segment that has a machine target; withdraws a waiting proposal.                  | ACCEPTED                                        |
| Try again (Retry) | Re-run the segment through the pipeline, **reconstructing the original chunk context** (same brief, glossary, preceding-target window, and TM the segment first saw); allowed whenever no run of the project is running, refused with `ErrorCode.busy` while one is. | ACCEPTED if it now passes, else remains FLAGGED/ACCEPTED (never downgraded) |
| Try again with a note | Re-run as above, reconstructing the original chunk context and injecting the user's free-text note into the draft/directed-fix `{{userNote}}` slot.                | ACCEPTED if it now passes, else remains FLAGGED/ACCEPTED (never downgraded) |
| Leave for later | Writes nothing: the row is marked "later" for this application run only and the desk moves to the next segment of the filter shown. The segment stays to review. | unchanged                                       |
| Apply a proposal | Take the backward-revision proposal waiting on an edited segment as the person's own edit. Acts only on a REVISED segment with a proposal waiting.                      | REVISED                                         |

A refused action changes nothing and answers `validation` (`SegmentActions`); the desk shows the reason in place.

| ID           | Requirement                                                                                                                                                                                                                      |
|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-REVIEW-A1 | Retry and retry-with-note route through the same InferenceGate and QA gate as the automatic pipeline, reconstructing the segment's original chunk context (single-segment call).                                                 |
| FR-REVIEW-A2 | Retry-with-note captures a free-text instruction injected into the `{{userNote}}` slot of the draft/directed-fix templates (see `08_UI_SCREENS_AND_STATES.md`, `12_PROMPT_CATALOG.md#draft-translation`). The note is entered in an inline panel of the review desk. |
| FR-REVIEW-A3 | Every action persists atomically for the rest of the session so review progress survives navigation; actions are stored in SQLite, so they also survive closing the application; the "later" marks are not stored. |

## export-any-time {#export-any-time}

| ID           | Requirement                                                                                                                       |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------|
| FR-REVIEW-X1 | Export is available at any time, regardless of how many segments remain FLAGGED (FR-REVIEW-07). While a run translates the Export screen asks to pause or stop first and says the rest keeps the original text (`mockups/README.md`, Step 5). |
| FR-REVIEW-X2 | On export with remaining flags, the last accepted/machine target is used for flagged segments; the user is informed of the count. |
