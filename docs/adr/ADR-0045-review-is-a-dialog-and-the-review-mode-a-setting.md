# ADR-0045 — Review is an in-window dialog, and the review mode is a stored setting

**Status:** accepted 2026-10-20 **Date:** 2026-10-20 **Deciders:** owner (decisions 5 and 12 of the 2026-10-19 review)
**Supersedes:** ADR-0036 in part (where review lives and how the mode is chosen; its pause rules stand)

## Context and problem statement

ADR-0036 put the review of flagged segments in a panel inside the Translating screen, and resolved the review mode
(`UNATTENDED`, `ASSISTED`, `MANUAL`) once at launch from a flag or an environment variable. The owner's review found two
problems:
- the panel competes with the live card for height;
- a person cannot find or change the mode.

The owner decided that review is a large in-window dialog that opens by itself when a translation finishes with
segments to review (decision 5), and that the review editor keeps formatting marks whole (decision 12). Settings now
persist (ADR-0043), so the mode can be a setting.

## Decision

- **Review is an extra-large in-window dialog.** It opens from "Review (n)" and from the summary strip.
  - When a translation finishes with segments to review, it also opens by itself, waiting until no other dialog or
    visible work is shown. An Automation setting turns that off.
  - It holds the segment list, the editors and the tabs. Accept, Save my edit, Try again and Revert keep their existing
    behaviour.
- **The editor is a plain text area with protected marks `⟦n⟧`.** A mark is deleted, cut or pasted only whole, and the
  main button stays off while a mark is missing or doubled. No rich-text library is added.
- **The review mode is the Automation setting "When to stop and ask you",** read at the start of each run.
  - The launch flag and the environment variable still override it, for the command line and the evaluations.
  - ADR-0036's pause rules (which segments pause a run in which mode) are unchanged.

## Considered options

- **Keep the panel and make it collapsible.** Rejected: the live card needs the height, and the owner asked for a
  dialog.
- **A rich-text editor with inline chips.** Rejected: a new library, and a heavier control than a review editor needs.

## Consequences

- **Positive:** the Translating screen keeps one full-height live card, and review gets the space it needs.
- **Negative:** while the desk is open, the live card is behind the scrim. The desk shows "read-only while translating"
  and offers Pause.
- **Neutral:** the existing review actions and their tests move with the panel's parts into the dialog.

## What would falsify this decision

A person regularly reviews while a run is still translating. The desk then needs to sit beside the live card, not
above it.

## Links

- Design decisions: DD-45.
- Spec clauses: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md`, `docs/specification/mockups/README.md`
  ("Dialog · Review").
- Change: `openspec/changes/finalize-ui-and-persistence/`.
