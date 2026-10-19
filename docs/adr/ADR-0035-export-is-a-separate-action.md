# ADR-0035 — Export is a separate action; a run ends when the translation ends

**Status:** accepted **Date:** 2026-09-26 **Deciders:** owner **Amends:** ADR-0033 (the job's stages)

## Context and problem statement

`add-translation-engine-and-cli` made export the last stage of the translation job, and `add-ui-translation-workspace`
therefore put the destination and the overwrite choice on the Book Brief: the job needed the path before it started
(FR-EXPORT-04, the `export` requirement "Decide where the translation is written before the run starts"). The
reference rendering has always drawn the save path on the Export screen, beside the side files and the final
consistency pass.

With review arriving, a finished run is no longer the end of the work: a person accepts, edits and retries segments
after the run and then exports. A stopped or paused run should still be exportable — "export is available at any
time" (FR-REVIEW-07, `01_Product/06_REVIEW_AND_EDITING.md#export-any-time`). The owner decided that the run
translates into memory and the Export screen owns where and how the book is written.

## Decision

- **The translation job has no export stage.** It prepares, translates and (on the Max dial) revises, then ends. Its
  report no longer claims a written file.
- **Export is its own job**, started from the Export screen (or by the command line after its translation job) with
  a destination, an overwrite choice, the side files wanted and whether to run the final consistency pass first. It
  reads the stored decisions (ADR-0034), writes from a fresh open of the source, and keeps the existing
  temporary-file → re-open → verify → atomic-replace chain.
- **Partial export is allowed.** A pending segment is written with its source text, a flagged one with its machine
  target; the report counts both, and the screen names them before and after writing.
- **The command line keeps its contract.** It checks the destination before translating, translates, then exports:
  same flags, same output name, same exit codes.

## Considered options

- **Keep export inside the run and move only the field.** Rejected: a person would have to open the last screen before
  starting, and an edit made after the run would never reach the file.
- **Write the book automatically at the end of the run and let Export re-write it.** Rejected: two writers for one
  file, and a finished run would silently create a file the person never asked for yet.

## Consequences

- **Positive:** review happens before writing; any state can be exported; the Book Brief is only about the translation.
- **Negative:** a person who forgets to press Export has no file; the finished-run notice points to Export.
- **Neutral:** DD-30 (same-format export) is untouched; FR-EXPORT-04, `08_UI_SCREENS_AND_STATES.md#screen-book-brief`
  and `#screen-export` are edited in the same change.

## What would falsify this decision

People regularly finish a run and lose the translation because nothing was written, or an export taken while a run is
still translating writes a book whose segments disagree with what the run later decides.
