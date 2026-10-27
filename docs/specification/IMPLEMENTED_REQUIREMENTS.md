# Implemented requirements

A hand-kept pointer from a requirement id to the state it is in and the place its behaviour is written down. It answers
one question — "is `FR-X` built, and where do I read what it does?" — and nothing else.

Where each thing lives:

- The requirement text is in this specification (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md` and the area documents).
- The behaviour as built is in `openspec/specs/<capability>/spec.md`.
- The proof is the test that exercises that behaviour. This file is not evidence and is not checked by any tool.

This file is maintained by hand and read by people. It is **not** a revival of the retired traceability machinery:
`docs/traceability.yaml`, a `trace`/`traceCheck` Gradle task, `// Covers:` or `Proves:` test markers and a
requirement-id coverage script were retired by ADR-0032 (`docs/adr/ADR-0032-tests-are-the-evidence.md`) and none of them
may return. Nothing parses this file, and no build step or test compares it with the code.

## Status vocabulary

| Status | Meaning |
|---|---|
| implemented | Built and reachable in the running application. |
| partial | Built in part; the row says in a few words what is missing. |
| shown-disabled | Drawn on the screen as the reference shows it, but unavailable, and nothing in the application reads it. |
| deferred | Not built by the change named on the row; the row says where it goes instead. |

Ids are spelled as the specification and the change's spec deltas spell them (`FR-THEME-1`, not `FR-THEME-01`). An id
cited by several capabilities has one row, under the capability whose requirement governs it; the others are named in
Notes.

Every capability named in the rows below is a folder under `openspec/specs/`. The change was archived on 2026-09-26
(`openspec/changes/archive/2026-09-26-add-ui-translation-workspace/`) and its delta specs were merged, so the seven
capabilities it introduced (`app-shell`, `book-brief`, `book-import`, `localization`, `notifications`, `settings`,
`theming`) now sit beside the six that already existed (`document-round-trip`, `export`, `inference`, `llm-provider`,
`resume`, `translation-pipeline`).

## Requirements implemented by `add-ui-translation-workspace`

| Requirement id | Status | Change | Owning capability (`openspec/specs/<capability>/`) | Notes |
|---|---|---|---|---|
| FR-UI-01 | implemented | `add-ui-translation-workspace` | `app-shell` | One window, fixed regions, numbered and grouped navigation. |
| FR-UI-02 | implemented | `add-ui-translation-workspace` | `app-shell` | |
| FR-UI-03 | implemented | `add-ui-translation-workspace` | `app-shell` | |
| FR-UI-04 | implemented | `add-ui-translation-workspace` | `app-shell` | |
| FR-UI-09 | implemented | `add-ui-translation-workspace` | `app-shell` | Build version shown in the window and written to the log. |
| FR-THEME-1 | implemented | `add-ui-translation-workspace` | `theming` | Also cited by `notifications`. |
| FR-THEME-2 | implemented | `add-ui-translation-workspace` | `theming` | |
| FR-THEME-3 | implemented | `add-ui-translation-workspace` | `theming` | |
| FR-THEME-4 | implemented | `add-ui-translation-workspace` | `settings` | Accent fixed to Cognac and shown read-only in the Appearance area. |
| FR-THEME-6 | implemented | `add-ui-translation-workspace` | `theming` | |
| FR-THEME-7 | implemented | `add-ui-translation-workspace` | `theming` | |
| FR-THEME-8 | implemented | `add-ui-translation-workspace` | `theming` | |
| FR-THEME-9 | implemented | `add-ui-translation-workspace` | `theming` | |
| FR-THEME-10 | implemented | `add-ui-translation-workspace` | `theming` | Four status roles in three shades; also cited by `notifications`. |
| FR-SETTINGS-02 | implemented | `add-ui-translation-workspace` | `settings` | Every settings area is shown; only the working ones are enabled. |
| FR-SETTINGS-04 | implemented | `add-ui-translation-workspace` | `settings` | Theme is changeable in two places (see `theming`); language row: see FR-UI-06. |
| FR-PROV-01 | implemented | `add-ui-translation-workspace` | `settings` | Two local providers offered from first launch. |
| FR-PROV-02 | implemented | `add-ui-translation-workspace` | `settings` | |
| FR-PROV-04 | partial | `add-ui-translation-workspace` | `settings` | Nothing chosen on the settings screen is remembered beyond the session; storage is not built. |
| FR-PROV-06 | implemented | `add-ui-translation-workspace` | `settings` | Provider check in three stages, each reported. |
| FR-PROV-07 | implemented | `add-ui-translation-workspace` | `settings` | |
| FR-MODEL-01 | implemented | `add-ui-translation-workspace` | `llm-provider` | The model-list port; also cited by `settings`. |
| FR-MODEL-02 | implemented | `add-ui-translation-workspace` | `llm-provider` | Also cited by `settings`. |
| FR-MODEL-03 | implemented | `add-ui-translation-workspace` | `settings` | Choose from the list or name a model the list lacks. |
| FR-MODEL-04 | partial | `add-ui-translation-workspace` | `settings` | The chosen model is not remembered beyond the session. |
| FR-UI-06 | implemented | `add-ui-translation-workspace` | `localization` | Settings › Appearance stores `ui.language`; it is read first at the next launch and applies after a restart (Restart now). Also cited by `settings`. |
| FR-UI-08 | implemented | `add-ui-translation-workspace` | `localization` | Also cited by `translation-pipeline` (activity-log messages). |
| FR-I18N-1 | implemented | `add-ui-translation-workspace` | `localization` | |
| FR-I18N-2 | implemented | `add-ui-translation-workspace` | `localization` | |
| FR-I18N-4 | implemented | `add-ui-translation-workspace` | `localization` | |
| FR-I18N-6 | implemented | `add-ui-translation-workspace` | `localization` | |
| FR-I18N-8 | implemented | `add-ui-translation-workspace` | `localization` | |
| FR-I18N-9 | implemented | `add-ui-translation-workspace` | `localization` | English and Ukrainian catalogues complete against each other. |
| FR-NOTIF-01 | implemented | `add-ui-translation-workspace` | `notifications` | |
| FR-NOTIF-02 | implemented | `add-ui-translation-workspace` | `notifications` | |
| FR-NOTIF-03a | implemented | `add-ui-translation-workspace` | `notifications` | |
| FR-NOTIF-03b | implemented | `add-ui-translation-workspace` | `notifications` | |
| FR-NOTIF-03c | implemented | `add-ui-translation-workspace` | `notifications` | |
| FR-NOTIF-04a | implemented | `add-ui-translation-workspace` | `notifications` | |
| FR-NOTIF-04b | implemented | `add-ui-translation-workspace` | `notifications` | |
| FR-NOTIF-04c | implemented | `add-ui-translation-workspace` | `notifications` | A provider failure is the run's own state. |
| FR-A11Y-8 | implemented | `add-ui-translation-workspace` | `notifications` | Advisory, not a gate. Severity is never carried by colour alone. |
| FR-A11Y-6 | implemented | `add-ui-translation-workspace` | `translation-pipeline` | Advisory, not a gate. Cited by the activity-log requirement. |
| FR-ALGO-01 | implemented | `add-ui-translation-workspace` | `translation-pipeline` | Run start and progress, per-request events, slow-model notice. |
| FR-NOTIF-6a | implemented | `add-ui-translation-workspace` | `translation-pipeline` | Activity-log entry kinds. |
| FR-NOTIF-6b | implemented | `add-ui-translation-workspace` | `translation-pipeline` | Activity-log status roles. |
| FR-RESUME-03 | implemented | `add-ui-translation-workspace` | `resume` | Pause and resume from the screen; also cited by `translation-pipeline`. |
| FR-RESUME-05 | implemented | `add-ui-translation-workspace` | `resume` | Stop is terminal; its decisions are kept in the project's database and a new run starts at the first pending segment, also after a restart (`RestartContinueE2eTest`). Also cited by `translation-pipeline`. |
| FR-EXPORT-01 | implemented | `add-ui-translation-workspace` | `export` | The screen reports the finished file and shows it in the system file manager. |
| FR-EXPORT-04 | implemented | `add-ui-translation-workspace` | `export` | The path is chosen on the Book Brief before the run starts; the export screen reports the file. |
| FR-EXPORT-05 | shown-disabled | `add-ui-translation-workspace` | `export` | Auxiliary offering drawn on the export screen, unavailable. |
| FR-EXPORT-06 | shown-disabled | `add-ui-translation-workspace` | `export` | Auxiliary offering drawn on the export screen, unavailable. |
| FR-IMPORT-02 | implemented | `add-ui-translation-workspace` | `book-import` | File picker or drop; the found facts are reported. |
| FR-IMPORT-03 | partial | `add-ui-translation-workspace` | `book-import` | The language-mismatch state is built, but nothing triggers it. |
| FR-IMPORT-06 | partial | `add-ui-translation-workspace` | `book-import` | No cover image; only the declared language is shown, never a detected one. |
| EC-LANG-1 | partial | `add-ui-translation-workspace` | `book-import` | Same state as FR-IMPORT-03: built, not reachable. |
| FR-BRIEF-01 | partial | `add-ui-translation-workspace` | `book-brief` | The target language is editable; the source language is shown read-only. |
| FR-BRIEF-02 | shown-disabled | `add-ui-translation-workspace` | `book-brief` | Nothing reads it when a run is assembled. |
| FR-BRIEF-03 | shown-disabled | `add-ui-translation-workspace` | `book-brief` | |
| FR-BRIEF-04 | shown-disabled | `add-ui-translation-workspace` | `book-brief` | |
| FR-BRIEF-05 | shown-disabled | `add-ui-translation-workspace` | `book-brief` | |
| FR-BRIEF-06 | shown-disabled | `add-ui-translation-workspace` | `book-brief` | |
| FR-BRIEF-07 | shown-disabled | `add-ui-translation-workspace` | `book-brief` | |
| FR-BRIEF-08 | shown-disabled | `add-ui-translation-workspace` | `book-brief` | |
| FR-DOC-01 | implemented | `add-ui-translation-workspace` | `document-round-trip` | The structure screen shows the parsed skeleton; also cited by `book-brief` (empty state). |

## Deferred by this change

These were left out on purpose and are ordered in `docs/implementation_plan/CHANGE_BACKLOG.md`:

- Source-language detection (the import screen shows the declared language only).
- Side files: the glossary, the bilingual copy and the quality report (FR-EXPORT-05, FR-EXPORT-06 stay shown-disabled).
- Adding and editing a provider (the settings screen offers the two built-in local providers only).

## Requirements implemented by `complete-translation-workflow`

Built and verified by the scripted checks at the change's final commit and by the owner's hand checks (tasks 16.3 and
16.5). The change was archived on 2026-10-19 and its delta specs are merged into `openspec/specs/`. Three of the capabilities below — `glossary`, `quality-gates` and
`review-queue` — are new; the others extend capabilities that already sit under `openspec/specs/`. The work that was not planned up front (groups 15a–15j) is in `additional-work.md`, beside the change.

| Requirement id | Status | Change | Owning capability (`openspec/specs/<capability>/`) | Notes |
|---|---|---|---|---|
| FR-A11Y-6 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-A11Y-8 | implemented | `complete-translation-workflow` | `notifications` |  |
| FR-ALGO-01 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-02 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-03 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-04 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-06 | implemented | `complete-translation-workflow` | `quality-gates` | Also cited by `translation-pipeline`. |
| FR-ALGO-07 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-08 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-10 | implemented | `complete-translation-workflow` | `export` | Also cited by `translation-pipeline`. |
| FR-ALGO-11 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-12 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-B2 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-C10 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-C11 | implemented | `complete-translation-workflow` | `quality-gates` | Also cited by `translation-pipeline`. |
| FR-ALGO-C12 | implemented | `complete-translation-workflow` | `quality-gates` | Also cited by `translation-pipeline`. |
| FR-ALGO-C13 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-C2 | implemented | `complete-translation-workflow` | `document-round-trip` | Also cited by `translation-pipeline`. |
| FR-ALGO-C2b | implemented | `complete-translation-workflow` | `document-round-trip` | Also cited by `translation-pipeline`. |
| FR-ALGO-C4 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-C4b | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-C5 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-C7 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-C8 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-ALGO-C9 | implemented | `complete-translation-workflow` | `translation-pipeline` | Also cited by `glossary`. |
| FR-ALGO-D1 | implemented | `complete-translation-workflow` | `translation-pipeline` | Also cited by `glossary`, `review-queue`. |
| FR-ALGO-D3 | implemented | `complete-translation-workflow` | `translation-pipeline` | Also cited by `review-queue`. |
| FR-BRIEF-01 | implemented | `complete-translation-workflow` | `book-brief` | Also cited by `app-shell`, `translation-pipeline`. |
| FR-BRIEF-02 | implemented | `complete-translation-workflow` | `book-brief` | Also cited by `translation-pipeline`. |
| FR-BRIEF-03 | implemented | `complete-translation-workflow` | `book-brief` |  |
| FR-BRIEF-04 | implemented | `complete-translation-workflow` | `book-brief` |  |
| FR-BRIEF-05 | implemented | `complete-translation-workflow` | `book-brief` |  |
| FR-BRIEF-06 | implemented | `complete-translation-workflow` | `book-brief` |  |
| FR-BRIEF-07 | implemented | `complete-translation-workflow` | `book-brief` | Also cited by `translation-pipeline`. |
| FR-BRIEF-08 | implemented | `complete-translation-workflow` | `book-brief` | Also cited by `export`, `translation-pipeline`. |
| FR-BRIEF-09 | implemented | `complete-translation-workflow` | `export` | Also cited by `book-brief`, `translation-pipeline`. |
| FR-DOC-01 | implemented | `complete-translation-workflow` | `document-round-trip` | Also cited by `book-brief`. |
| FR-DOC-02 | implemented | `complete-translation-workflow` | `document-round-trip` |  |
| FR-DOC-03 | implemented | `complete-translation-workflow` | `document-round-trip` | Also cited by `translation-pipeline`. |
| FR-DOC-04 | implemented | `complete-translation-workflow` | `document-round-trip` |  |
| FR-DOC-05 | implemented | `complete-translation-workflow` | `document-round-trip` | The placeholder gate checks the multiset and the order of paired placeholders (ADR-0040). Also cited by `translation-pipeline`. |
| FR-DOC-06 | implemented | `complete-translation-workflow` | `export` |  |
| FR-DOC-07 | implemented | `complete-translation-workflow` | `document-round-trip` |  |
| FR-DOC-08 | implemented | `complete-translation-workflow` | `document-round-trip` |  |
| FR-DOC-09 | implemented | `complete-translation-workflow` | `document-round-trip` |  |
| FR-DOC-10 | implemented | `complete-translation-workflow` | `document-round-trip` |  |
| FR-DOC-11 | implemented | `complete-translation-workflow` | `document-round-trip` | Auxiliary text is produced for every format (ADR-0041). Also cited by `book-brief`, `translation-pipeline`. |
| FR-DOC-EPUB | implemented | `complete-translation-workflow` | `document-round-trip` |  |
| FR-DOC-MD | implemented | `complete-translation-workflow` | `document-round-trip` |  |
| FR-DOC-TXT | implemented | `complete-translation-workflow` | `document-round-trip` |  |
| FR-EXPORT-01 | implemented | `complete-translation-workflow` | `export` | Also cited by `translation-pipeline`. |
| FR-EXPORT-02 | implemented | `complete-translation-workflow` | `export` |  |
| FR-EXPORT-03 | implemented | `complete-translation-workflow` | `export` |  |
| FR-EXPORT-04 | implemented | `complete-translation-workflow` | `export` | Export is its own action with Save to on the Export screen (ADR-0035). Also cited by `book-brief`. |
| FR-EXPORT-05 | implemented | `complete-translation-workflow` | `export` | Glossary CSV, bilingual HTML and quality report side files are written. |
| FR-EXPORT-06 | implemented | `complete-translation-workflow` | `export` | The consistency pass runs on request at export, and after the last segment on Max. |
| FR-GLOSS-01 | implemented | `complete-translation-workflow` | `glossary` | Also cited by `translation-pipeline`. |
| FR-GLOSS-02 | implemented | `complete-translation-workflow` | `glossary` |  |
| FR-GLOSS-03 | implemented | `complete-translation-workflow` | `glossary` | Also cited by `quality-gates`. |
| FR-GLOSS-04 | implemented | `complete-translation-workflow` | `glossary` | Also cited by `export`. |
| FR-GLOSS-05 | implemented | `complete-translation-workflow` | `glossary` |  |
| FR-IMPORT-01 | implemented | `complete-translation-workflow` | `book-import` |  |
| FR-IMPORT-02 | implemented | `complete-translation-workflow` | `book-import` |  |
| FR-IMPORT-03 | implemented | `complete-translation-workflow` | `book-import` | The source language comes from the metadata evidence, or Auto (ADR-0042, ADR-0037). Also cited by `book-brief`, `document-round-trip` and `translation-pipeline`. |
| FR-IMPORT-04 | implemented | `complete-translation-workflow` | `book-import` | Also cited by `document-round-trip`. |
| FR-IMPORT-05 | implemented | `complete-translation-workflow` | `book-import` | Refusal kinds are data (ADR-0039). Also cited by `document-round-trip` and `notifications`. |
| FR-IMPORT-06 | implemented | `complete-translation-workflow` | `book-import` |  |
| FR-IMPORT-07 | implemented | `complete-translation-workflow` | `document-round-trip` | Also cited by `book-import`. |
| FR-INFER-01 | implemented | `complete-translation-workflow` | `llm-provider` | Also cited by `inference`, `translation-pipeline`. |
| FR-INFER-02 | implemented | `complete-translation-workflow` | `llm-provider` |  |
| FR-INFER-03 | implemented | `complete-translation-workflow` | `llm-provider` | Also cited by `inference`. |
| FR-INFER-04a | implemented | `complete-translation-workflow` | `llm-provider` |  |
| FR-INFER-05 | implemented | `complete-translation-workflow` | `llm-provider` |  |
| FR-INFER-06 | implemented | `complete-translation-workflow` | `review-queue` | Also cited by `translation-pipeline`. |
| FR-INFER-08 | implemented | `complete-translation-workflow` | `llm-provider` | Also cited by `translation-pipeline`. |
| FR-INFER-09 | implemented | `complete-translation-workflow` | `llm-provider` | Also cited by `inference`, `translation-pipeline`. |
| FR-MODEL-01 | implemented | `complete-translation-workflow` | `settings` | Also cited by `app-shell`. |
| FR-MODEL-02 | implemented | `complete-translation-workflow` | `settings` |  |
| FR-MODEL-03 | implemented | `complete-translation-workflow` | `settings` |  |
| FR-MODEL-05 | implemented | `complete-translation-workflow` | `llm-provider` |  |
| FR-MODEL-06 | implemented | `complete-translation-workflow` | `llm-provider` |  |
| FR-NOTIF-01 | implemented | `complete-translation-workflow` | `notifications` |  |
| FR-NOTIF-02 | implemented | `complete-translation-workflow` | `notifications` |  |
| FR-NOTIF-04a | implemented | `complete-translation-workflow` | `notifications` |  |
| FR-NOTIF-04c | implemented | `complete-translation-workflow` | `notifications` |  |
| FR-NOTIF-05 | implemented | `complete-translation-workflow` | `export` |  |
| FR-NOTIF-6a | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-NOTIF-6b | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-NOTIF-6c | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-PROV-01 | implemented | `complete-translation-workflow` | `settings` |  |
| FR-PROV-04 | partial | `complete-translation-workflow` | `settings` | Nothing chosen on the Providers tab is remembered beyond the session; storage is not built. |
| FR-PROV-06 | implemented | `complete-translation-workflow` | `llm-provider` | Also cited by `settings`. |
| FR-PROV-07 | implemented | `complete-translation-workflow` | `llm-provider` | Also cited by `settings`. |
| FR-QA-01 | implemented | `complete-translation-workflow` | `quality-gates` | Also cited by `document-round-trip`, `translation-pipeline`. |
| FR-QA-02 | implemented | `complete-translation-workflow` | `quality-gates` |  |
| FR-QA-03 | implemented | `complete-translation-workflow` | `quality-gates` |  |
| FR-QA-04 | implemented | `complete-translation-workflow` | `quality-gates` | Also cited by `document-round-trip`. |
| FR-QA-05 | implemented | `complete-translation-workflow` | `quality-gates` |  |
| FR-QA-07 | implemented | `complete-translation-workflow` | `quality-gates` |  |
| FR-RESUME-02 | implemented | `complete-translation-workflow` | `resume` | Also after a restart over SQLite; a stored running or paused run is shown as interrupted (`RestartContinueE2eTest`). |
| FR-RESUME-03 | implemented | `complete-translation-workflow` | `resume` | Also cited by `translation-pipeline`. |
| FR-RESUME-05 | implemented | `complete-translation-workflow` | `resume` | A stopped run resumes from its first pending segment, also after a restart over SQLite (`RestartContinueE2eTest`). Also cited by `translation-pipeline`. |
| FR-REVIEW-01 | implemented | `complete-translation-workflow` | `review-queue` | Also cited by `resume`, `translation-pipeline`. |
| FR-REVIEW-02 | implemented | `complete-translation-workflow` | `quality-gates` |  |
| FR-REVIEW-03 | implemented | `complete-translation-workflow` | `review-queue` |  |
| FR-REVIEW-04 | implemented | `complete-translation-workflow` | `review-queue` |  |
| FR-REVIEW-05 | implemented | `complete-translation-workflow` | `review-queue` | Review is a panel inside Translating (D-8). Also cited by `resume`. |
| FR-REVIEW-06 | implemented | `complete-translation-workflow` | `review-queue` |  |
| FR-REVIEW-07 | implemented | `complete-translation-workflow` | `export` | Also cited by `review-queue`. |
| FR-REVIEW-08 | implemented | `complete-translation-workflow` | `review-queue` |  |
| FR-REVIEW-09 | implemented | `complete-translation-workflow` | `review-queue` |  |
| FR-REVIEW-10 | implemented | `complete-translation-workflow` | `export` | Also cited by `review-queue`, `translation-pipeline`. |
| FR-REVIEW-A1 | implemented | `complete-translation-workflow` | `review-queue` |  |
| FR-REVIEW-A2 | implemented | `complete-translation-workflow` | `review-queue` |  |
| FR-REVIEW-A3 | implemented | `complete-translation-workflow` | `review-queue` |  |
| FR-REVIEW-Q4 | implemented | `complete-translation-workflow` | `review-queue` |  |
| FR-REVIEW-X1 | implemented | `complete-translation-workflow` | `export` | Also cited by `review-queue`. |
| FR-REVIEW-X2 | implemented | `complete-translation-workflow` | `export` | Also cited by `review-queue`. |
| FR-SETTINGS-02 | implemented | `complete-translation-workflow` | `settings` |  |
| FR-SETTINGS-03 | implemented | `finalize-ui-and-persistence` | `settings` | Settings › Generation: quality table, Thinking, Sampling and the fixed time limits (finalize 7.3). |
| FR-SETTINGS-04 | implemented | `complete-translation-workflow` | `settings` | Theme and technical details stored; language after a restart (finalize 7.3). |
| FR-SETTINGS-05 | implemented | `finalize-ui-and-persistence` | `settings` | Settings › Automation: when to stop and ask (next run start), default quality for new books, review desk, last-check default, keep awake, reopen last project (finalize 7.4). |
| FR-SETTINGS-06 | implemented | `finalize-ui-and-persistence` | `settings` | Settings › Storage & logs: database folder and size, Open folder, Make a backup…, log folder, detailed-log switch (after a restart), Open log folder, diagnostic bundle (finalize 7.4). |
| FR-THEME-10 | implemented | `complete-translation-workflow` | `notifications` |  |
| FR-THEME-4 | implemented | `complete-translation-workflow` | `settings` |  |
| FR-TRANS-05 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| FR-UI-01 | implemented | `complete-translation-workflow` | `app-shell` | Six numbered workflow steps with a provider and model footer; review is a panel inside Translating (D-8). Projects is the one inert entry. |
| FR-UI-03 | implemented | `complete-translation-workflow` | `app-shell` |  |
| FR-UI-04 | implemented | `complete-translation-workflow` | `app-shell` |  |
| FR-UI-08 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| NFR-PRIV-04 | implemented | `complete-translation-workflow` | `translation-pipeline` | Book text, prompts and replies are logged at TRACE only. Also cited by `app-shell`. The detailed log is switched on by Settings (marker `detailed-log.on`) or `BOOKLOOM_TRACE_FILE`; a backup is written only to a file the person picks and holds no key (finalize 7.4). |
| NFR-PRIV-06 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |
| NFR-PRIV-07 | implemented | `complete-translation-workflow` | `translation-pipeline` |  |

### Owner decisions this change built

These are decisions the owner took for this change; each is built and none is a requirement id of its own.

- **D-2 — Markdown frontmatter.** Only the text values of Markdown frontmatter become segments. A key is never a
  segment, and neither is `lang` or `language`, a number, a boolean (YAML `yes` and `on` included), a null, a date, a
  URL, a list or a map. On export an existing top-level `lang` value is replaced by the target language tag, written
  in the quotes it already had, and a `lang` key is never added to a file that had none.
- **D-4 — Text the source encoding cannot hold.** A TXT or Markdown export whose translation the source's own text
  encoding cannot represent is written whole as UTF-8 instead of being refused, with a byte-order mark exactly when the
  source had one (ADR-0029). EPUB keeps writing numeric character references for such characters and FB2 keeps its
  existing switch to UTF-8.
- **D-5 — Names grow as the run goes.** At the end of each body unit a deterministic re-scan adds the new capitalised
  candidates it finds to the glossary as unlocked entries with no target. It never changes an entry the person has
  kept and never brings back one the person removed.
- **D-6 — Foreign passages come from metadata only.** Under the Keep policy, an inline element whose own `lang` or
  `xml:lang` differs from the book's source language is kept verbatim as one protected token. The application does not
  guess that a passage is foreign from its letters.

## Deferred by this change

These were left out on purpose and are ordered in `docs/implementation_plan/CHANGE_BACKLOG.md`:

- Saving anything across a restart (SQLite persistence): resume after a restart, remembered settings and brief, the
  in-app language switch, the Projects screen, and add / edit / delete provider with credentials.
- Settings tabs Models, Generation, Automation and Storage.
- The translation pipeline redesign, the next change `redesign-translation-pipeline`
  (`docs/proposals/2026-10-16-translation-pipeline-redesign.md`).
- Numeral masking and an automatic model pre-scan of names (decided against, D-7); parallel cloud lanes (local inference
  is single-flight).

## Requirements implemented by `finalize-ui-and-persistence`

The Projects screen, the full Settings, SQLite storage, continue after a restart, the first-launch look and the words in English and Ukrainian. Rows for FR-SETTINGS-03, -05 and -06 stand in the table above. The `local-storage` capability is new: its spec is `openspec/specs/local-storage/spec.md`; the change itself is archived in `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/`.

| Requirement id | Status | Change | Owning capability (`openspec/specs/<capability>/`) | Notes |
|---|---|---|---|---|
| FR-THEME-4 | implemented | `finalize-ui-and-persistence` | `settings` | Accent fixed; the theme choice is stored (Settings › Appearance). |
| FR-PROV-01 | implemented | `finalize-ui-and-persistence` | `settings` | Two local providers offered from first launch; one silent look at the two local addresses on the very first launch (ADR-0044). |
| FR-PROV-04 | implemented | `finalize-ui-and-persistence` | `settings` | Providers are stored in SQLite and survive a restart (was partial). |
| FR-MODEL-04 | implemented | `finalize-ui-and-persistence` | `settings` | The chosen model and its per-model settings are stored (was partial). |
| FR-EXPORT-04 | implemented | `finalize-ui-and-persistence` | `export` | Export screen with the Save as path, early write, tiles from the project and a replace question. |
| FR-REVIEW-05 | implemented | `finalize-ui-and-persistence` | `review-queue` | Review desk as an extra-large dialog with row markers, one main button and More; protected formatting marks in the editor. |
| FR-UI-06 | implemented | `finalize-ui-and-persistence` | `localization` | Language stored as `ui.language`, applied after a restart with Restart now. |
| FR-UI-15 | implemented | `finalize-ui-and-persistence` | `app-shell` | Projects screen grouped by book, with open, continue, rename, remove and translate-into-another-language. |
| FR-PERSIST-01 | implemented | `finalize-ui-and-persistence` | `local-storage` | Projects, segments, glossary and translation memory in SQLite (WAL) from one schema file. |
| FR-PERSIST-02 | implemented | `finalize-ui-and-persistence` | `local-storage` | A chunk's decisions and run record are one transaction. |
| FR-PERSIST-03 | implemented | `finalize-ui-and-persistence` | `local-storage` | Database `bookloom.db` under the per-OS data folder. |
| FR-PERSIST-04 | implemented | `finalize-ui-and-persistence` | `local-storage` | Single-instance lock held before the database opens. |
| FR-PERSIST-05 | implemented | `finalize-ui-and-persistence` | `local-storage` | Secrets stay references; the database holds none. |
| FR-PERSIST-06 | implemented | `finalize-ui-and-persistence` | `local-storage` | Development builds use the separate `-Dev` folders. |
| FR-PERSIST-07 | implemented | `finalize-ui-and-persistence` | `local-storage` | Settings KV serialised with `Locale.ROOT`. |
| FR-PERSIST-08 | implemented | `finalize-ui-and-persistence` | `local-storage` | `busy_timeout` and pragmas per connection; one writer (`WriteExecutor`). |
| FR-PERSIST-09 | implemented | `finalize-ui-and-persistence` | `local-storage` | The provider sits in the project's document, so deleting a provider keeps projects. |
| FR-PERSIST-10 | implemented | `finalize-ui-and-persistence` | `local-storage` | A file that cannot be opened is set aside as `bookloom.db.old-<time>`. |
| FR-PERSIST-11 | implemented | `finalize-ui-and-persistence` | `local-storage` | A changed schema number or record-shape fingerprint sets the file aside (ADR-0043). |
| FR-SETTINGS-01 | implemented | `finalize-ui-and-persistence` | `settings` | Settings, providers and per-model settings in their own SQLite stores. |
| FR-SETTINGS-02 | implemented | `finalize-ui-and-persistence` | `settings` | Providers and Models tabs live. |
| FR-SETTINGS-04 | implemented | `finalize-ui-and-persistence` | `settings` | Theme and technical details stored; language applies after a restart. |
| FR-SETTINGS-07 | implemented | `finalize-ui-and-persistence` | `settings` | Every setting validated against its range. |
| FR-SETTINGS-08 | implemented | `finalize-ui-and-persistence` | `settings` | Providers and Models change nothing until Save; pinned footer and leave guard. |
| FR-RESUME-02 | implemented | `finalize-ui-and-persistence` | `resume` | Reopen the last project at launch; a run left running or paused shows as interrupted and continues at its first pending segment. |
| FR-RESUME-03 | implemented | `finalize-ui-and-persistence` | `resume` | Pause and resume of a running job; reopen-last-project switch in Settings › Automation. |
| FR-RESUME-05 | implemented | `finalize-ui-and-persistence` | `resume` | Stop is terminal; a new run starts at the first pending segment, also after a restart. |
