# ADR-0036 — Review modes decide when the run pauses, as well as the trust threshold

**Status:** accepted **Date:** 2026-09-26 **Deciders:** owner **Extends:** DD-45

## Context and problem statement

The specification defines three review modes — Unattended, Assisted and Manual — purely as positions of the trust
threshold τ (DD-45, `01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`): each mode flags more or fewer segments, and
all three run without stopping. The owner wants the modes to change how the run behaves: the default runs to the end
and lets the person review afterwards; a semi-manual mode stops on each flagged segment or error so it can be fixed
at once; a manual mode stops after every segment for confirmation. The mode is a Settings value in the long run, but
Settings are not persisted yet, so for now it is always the default unless the application is launched with a flag
("as interface language now").

## Decision

- The three modes keep their specification names and gain pause behaviour:
  - **Unattended** ("Automatic" on screen) — never pauses for review; flagged segments wait in the review panel.
  - **Assisted** ("Assisted — pause on problems") — pauses on every flagged segment and on every provider error (an
    internal error ends the run instead, and a prompt too long for the model flags its segment).
  - **Manual** ("Manual — confirm every segment") — pauses after every segment, flagged or not; chunks hold one segment.
- Each mode still owns τ (DD-45): 0.60, 0.75 and 0.85 respectively. The specification gave no numbers; these are the
  starting values and live in one place.
- A new pause point, "on a flagged segment", joins the existing ones; a pause for review names the segment.
- While the run is paused the person may accept, edit, revert, retry or skip that segment; resuming continues with
  the next one and treats an edited target as the preceding context of every segment drafted after the resume. Drafts
  already made in the same chunk are kept, so with the judge on an edit reaches the next chunk.
- Review happens in a panel inside the Translating screen, not on a screen of its own; the mockup's separate Review
  step and its navigation entry are an approved departure.
- The mode is resolved once at launch from the environment variable `BOOKLOOM_REVIEW_MODE` or the system property
  `bookloom.review.mode` (the same order as the log level); an unknown value falls back to Unattended with one warning.
  The command line always runs Unattended.

## Considered options

- **Thresholds only, as specified.** Rejected by the owner: it offers no way to fix problems as they appear.
- **A mode picker on the Book Brief.** Rejected by the owner: the mode is a preference, not a property of a book, and
  belongs with Settings when they are persisted.

## Consequences

- **Positive:** the default stays automatic-first (ADR-0007); careful translators get the stop-and-fix flow.
- **Negative:** until Settings persistence, choosing a mode requires a launch flag. Accepted and documented.
- **Neutral:** FR-REVIEW-01, `02_TRANSLATION_WORKFLOW.md#review-modes` and `07_SETTINGS.md#automation-tab` are edited
  to describe the pause behaviour.

## What would falsify this decision

Pausing on a flagged segment loses work or blocks export, or people in Assisted mode spend most of the run paused on
segments they then accept unchanged — a sign the threshold, not the pause, is what needs adjusting.

## Amended in this change (2026-09-27)

The owner confirmed during the readiness review of `complete-translation-workflow` that review stays a panel inside
Translating, so a pause shows the segment beside the run it belongs to; the mockup's Review step is recorded as a
departure in the `app-shell` specification. The pause-and-edit sentence now states the chunk behaviour the design's D4a
fixes: a chunk's segments are all drafted, and judged, before its decisions, so a pause mid-chunk keeps those drafts.
