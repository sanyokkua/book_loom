---
phase: PHASE_10_UI_TRANSLATE_REVIEW
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
phase_clauses:
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export
  - FR-RESUME-03
  - FR-UI-02
  - FR-UI-04
  - FR-REVIEW-01
  - FR-REVIEW-02
  - FR-REVIEW-03
  - FR-REVIEW-04
  - FR-REVIEW-05
  - FR-REVIEW-06
  - FR-REVIEW-07
  - FR-GLOSS-02
  - FR-GLOSS-03
  - FR-GLOSS-04
  - FR-EXPORT-01
  - FR-EXPORT-02
  - FR-EXPORT-03
  - FR-EXPORT-04
  - FR-EXPORT-05
  - FR-EXPORT-06
  - DD-28
  - DD-30
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`, `docs/specification/01_Product/06_REVIEW_AND_EDITING.md`,
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`, `docs/specification/mockups/ui-mockup.html`

# PHASE_10 — Translate & Review UI

## Goal

Give the running pipeline a face: the Translating dashboard with its running / paused / stopped / provider-error states,
the Review queue with side-by-side compare and the flagged list, the Glossary (Names & Style) editor, and the Export
screen. The UI drives the PHASE_06–PHASE_08 engine through its ports and mirrors job/segment state (F8), reading target
text back into the same nodes on export (F1).

## In scope

- Translating dashboard: progress, per-chapter status, running / paused / stopped / provider-error states;
  pause/resume/stop controls wired to the engine (stopped is resumable, never an error — FR-RESUME-05).
- Review queue: flagged list, side-by-side source-vs-target compare (two `TextArea`, no inline diff),
  accept/edit/retry/retry-with-note/skip actions driving the segment state machine; export allowed at any time.
- Names & Style (Glossary) editor: editable table (source/target/type/gender), lock, add/import/export, wired to the
  PHASE_07 glossary engine.
- Export screen: a **read-only original-format label** (shows the source format EPUB/FB2/MD/TXT; **no format chooser** —
  export is always same-format, DD-30), validation, save path, export-complete dialog, optional
  glossary/bilingual/report side exports, final consistency-pass toggle.

## Out of scope

- Settings tabs and the provider dialog (PHASE_11).
- The backward-revision execution (PHASE_12) — the export final-consistency-pass toggle exposes the flag; the sweep
  itself is later.
- New engine logic — this phase renders and drives existing ports.

## Dependencies

PHASE_06 (running engine, segment states, checkpoints), PHASE_09 (UI shell, state mirror F8, theming), PHASE_07
(glossary engine the Names & Style editor drives), and PHASE_01 + PHASE_02 (format reassembly the Export screen writes
target text back through — F1). All five are hard dependencies (aligned with
`docs/implementation_plan/07_ROADMAP.md#phase-table`).

## Forward-compatibility

- **Consumes F1** — export reads target strings back into the same DOM/AST nodes and repackages.
- **Consumes F8** — dashboard and review bind to the observable state mirror.
- Surfaces the final-consistency-pass toggle that PHASE_12 executes.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                                                            | Target modules                                           | Cited spec clauses                                                                                                                                        |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Translating dashboard with running / paused / stopped / provider-error states + pause/resume/stop controls                                                | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.state`  | FR-RESUME-03, FR-UI-02, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery` |
| Review queue: flagged list + side-by-side compare (two TextArea, no diff)                                                                                 | `:ui/ua.bookloom.ui.screen`                              | FR-REVIEW-03, FR-REVIEW-04, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`                                                                        |
| Segment actions: accept/edit/retry/retry-with-note/skip driving the state machine                                                                         | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.dialog` | FR-REVIEW-05, FR-REVIEW-06, `01_Product/06_REVIEW_AND_EDITING.md#segment-actions`                                                                         |
| Three review modes (Unattended/Assisted/Manual) via the trust-threshold dial                                                                              | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.state`  | FR-REVIEW-01, FR-REVIEW-02, `01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`                                                                          |
| Names & Style glossary editor (table, lock, add/import/export)                                                                                            | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.dialog` | FR-GLOSS-02, FR-GLOSS-03, FR-GLOSS-04, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`                                                    |
| Export screen: read-only original-format label (no chooser, DD-30), validation, save path, export-complete dialog, side exports, final-consistency toggle | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.dialog` | FR-EXPORT-01..06, DD-30, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`                                                                           |
| Export any time regardless of remaining flagged segments                                                                                                  | `:ui/ua.bookloom.ui.screen`                              | FR-REVIEW-07, FR-EXPORT-04                                                                                                                                |
| Match each screen/state/dialog to the mockup (P6), light + dark                                                                                           | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.dialog` | FR-UI-04, DD-28, `docs/specification/mockups/ui-mockup.html`                                                                                              |

## Phase exit checklist

- [ ] The Translating dashboard shows progress and the running / paused / stopped / provider-error states;
  pause/resume/stop drive the engine (stopped is resumable, never an error).
- [ ] The Review queue lists flagged segments and shows side-by-side compare with no inline diff.
- [ ] accept/edit/retry/retry-with-note/skip drive the PENDING→ACCEPTED/FLAGGED→REVISED state machine correctly.
- [ ] The three review modes map to the single trust-threshold dial.
- [ ] The Glossary editor edits/locks/imports/exports entries against the engine glossary.
- [ ] Export writes target back into the same nodes, validates, chooses a save path, and confirms via the
  export-complete dialog; the format is shown as a **read-only original-format label** (no chooser, DD-30); side exports
  and the final-consistency toggle exist.
- [ ] Export is allowed at any time regardless of flagged segments.
- [ ] Each screen/state/dialog matches the mockup (P6) in both themes (TestFX/Monocle).
- [ ] Visual validation performed per `04_Build_and_Release/06_TESTING_STRATEGY.md#visual-validation` (token/geometry
  assertions; pinned snapshot diff and/or vision-model review for the translate/review/export surfaces).
- [ ] `./gradlew :ui:test` and `./gradlew traceCheck` green; module inventory updated.
