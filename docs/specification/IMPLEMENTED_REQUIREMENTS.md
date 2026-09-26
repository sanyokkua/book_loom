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
| FR-UI-06 | partial | `add-ui-translation-workspace` | `localization` | The language follows the operating system. The in-app switch and the persisted `ui.language` setting are deferred to local storage. Also cited by `settings`. |
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
| FR-RESUME-05 | partial | `add-ui-translation-workspace` | `resume` | Stop is terminal and writes nothing; resuming a stopped run arrives with storage. Also cited by `translation-pipeline`. |
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

- The in-app language switch and the persisted `ui.language` setting (FR-UI-06 stays partial until then).
- Source-language detection (the import screen shows the declared language only).
- Resuming a run across a restart, and resuming a stopped run (FR-RESUME-05 stays partial); both need saved progress from
  local storage.
- Side files: the glossary, the bilingual copy and the quality report (FR-EXPORT-05, FR-EXPORT-06 stay shown-disabled).
- Adding and editing a provider (the settings screen offers the two built-in local providers only).
