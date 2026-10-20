# Proposal: finalize-ui-and-persistence

## Why

The translation workflow works, but the window around it does not explain itself, and it forgets everything on
restart. The owner reviewed the last build screen by screen on 2026-10-19, and a four-sided final review followed.
They found:
- model work hidden behind a spinner or shown as raw prompts in a separate OS window;
- the per-segment results of a batch never reaching the rows;
- dialogs and tables that do not fit the window;
- no Projects screen, two of six Settings tabs, and no stored state.

The owner's observations, decisions 1–14 and every requirement ID are in
`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`. The target is
`docs/specification/mockups/ui-mockup.html`, described element by element in `docs/specification/mockups/README.md`.
This change makes BookLoom functionally finished: the final UI, the Projects screen, the full Settings, and the
application's state and settings stored on disk, so that a translation can be closed and continued after a restart.

## What Changes

- **Dialogs and progress (decisions 1–3, 10, 11; G1, G4, G5, G10; PR1–PR3).**
  - Every dialog is an in-window modal on one of two layers: model work below, questions above.
  - One progress dialog serves all model work (brief, names, file name, the last check before saving, a
    translation's calls). It shows its steps, its figures and the parsed input and output. Long work can continue in
    the background.
  - Progress and time left count every step of the whole operation, measured per kind of step. **BREAKING** for the
    old estimate: the 20-segment window is removed.
  - The separate "Model calls" OS window is removed.
- **Layout and visual system (G2, G3, G8, G9, G11, G12; L1–L10; theme B, G).**
  - Toasts move to the bottom-right; 12 px visible scrollbars.
  - Width classes; the toolbar as the one action bar; a pinned banner slot.
  - Two kinds of table; markers as an icon plus a word in one colour map.
  - "Show technical details", with plain words on the main screens (the Words table, decision 14).
- **Projects (new; G6, PN1–PN3, decisions 8, 9, 13).**
  - A list grouped by book, the first launch, create, open, continue, rename and remove.
  - Several translations of one book; automatic project names.
  - One silent look for a local AI program at first launch.
  - Workflow steps stay locked until a project is open.
- **Screens to the mockup.**
  - **Import (I1–I6):** a verdict line and a three-pane text browser.
  - **Brief (B1–B3):** read-only while translating; languages locked once started; "Fill in from the book".
  - **Names & style (N1–N5):** purpose lines, resizable tables, the names pass in the progress dialog with Results.
  - **Translating (T1–T5, C1):** a summary strip, and a live card with Simple and Detailed modes and per-segment
    markers for a batch.
  - **Review (T6–T12, decisions 5 and 12):** a large dialog that opens by itself, with protected formatting marks and
    one main button.
  - **Export (E1–E3):** figures before export, partial export, and asking before a file is replaced. **BREAKING**: the
    Replace switch is removed.
- **Settings (S1–S6, H1, decisions 6–7).**
  - Providers: add, edit, delete and save, with a summary card and the connection form (auth by environment-variable
    reference, two endpoint overrides, headers, custom models), checks, and a test dialog.
  - Models: per-model settings, with a three-way temperature, the context window, answer length and "Settings this
    model refused".
  - Generation, Appearance, Automation, and Storage & logs hold only knobs the code has.
  - Providers and Models save through a pinned footer; the other tabs apply at once.
  - A setting a model refuses is dropped, the request repeated once, and the refusal remembered (S6).
- **Stored state.**
  - Projects, books, briefs, both term lists, segment decisions, translation memory, summaries, run state, providers,
    per-model settings and preferences are kept in a local SQLite database.
  - The command line keeps working in memory.
  - Before the first release there are no schema migrations: a database from another schema version is put aside and
    a fresh one is created (owner, 2026-10-20). **BREAKING**: ADR-0034's in-memory-only state ends for the window.
- **Small pipeline and provider hooks the UI needs**, and nothing more (see `design.md` "Hooks"):
  - per-item batch results on the call;
  - the batch context actually sent, kept on the segment;
  - step announcements;
  - provider fields, header and key injection, and checking an unsaved provider;
  - per-model generation overrides;
  - refused settings;
  - id-based projects with names.

## Capabilities

### New Capabilities

- `local-storage`: keeping the application's state and settings on this computer. A translation can be closed and
  continued after a restart, a crash loses at most the work in flight, and a credential is never written down
  (FR-PERSIST, `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`).

### Modified Capabilities

- `app-shell`: the window, the two dialog layers, the progress dialog, the width classes, the banner slot, the title
  bar and toolbar, the steps locked without a project, the Projects screen, project names, the first-launch look for a
  local AI program, and the 16 run states.
- `book-import`: the one-line verdict, the "About this book" fold and the three-pane text browser.
- `book-brief`: the brief is read-only while translating and its languages lock once started; the project-name line
  and the "Same language pair" question; "Fill in from the book" in the progress dialog.
- `glossary`: resizable tables with a changed-row edge and the "Used" count; the names pass in the progress dialog with
  Results and Continue in background; recurring terms are stored; model actions are off while translating.
- `translation-pipeline` (UI hooks only): progress and time left from all steps; the live card's per-segment rows for
  a batch; step and per-item announcements; the review mode as a stored setting.
- `review-queue`: review as a large dialog that opens by itself, with protected marks, one main button, and the
  context the model actually saw.
- `export`: figures before export, partial export, asking before a file is replaced, and the last check in the
  progress dialog.
- `quality-gates`: a segment's findings are shown in the review dialog rather than an inline panel (wording only;
  no gate changes).
- `settings`: all six tabs; providers added, edited, deleted and stored; per-model settings; refused settings; the save
  model.
- `llm-provider`: the provider's name, sign-in, endpoint overrides and headers; checking an unsaved provider; the key
  checked before a call.
- `inference`: the three-way temperature and the output-length field per model; any refused optional setting is
  dropped, repeated once and remembered.
- `resume`: progress survives a restart; a project reopens "Ready to continue"; the AI-model questions on continue.
- `notifications`: toasts in the bottom-right corner, feedback in a dialog's own footer, and the plain words for run
  problems.
- `localization`: the app language is a stored choice that applies after a restart.
- `theming`: the stored theme, the six-meaning state colour map, underlined links, the selected-segment bar and
  visible scrollbars.

## Impact

- **Modules:**
  - `:ui`: most of the change.
  - `:persistence`: SQLite adapters next to the in-memory ones.
  - `:api`: new ports and record fields.
  - `:pipeline`: step events, batch items, projects.
  - `:llm`: provider fields, headers, the draft check, refused settings.
  - `:app`: wiring, boot, the window-vs-CLI storage choice.
  - `:document`: untouched. `:util`: untouched.
- **Dependencies (all Apache-2.0):** `org.xerial:sqlite-jdbc`, `org.jdbi:jdbi3-core`, and
  `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` (Jackson itself is already in the catalog). No Flyway yet.
- **Decisions:**
  - ADR-0043: state in SQLite as JSON documents; no migrations before release.
  - ADR-0044: the first-launch local look; amends ADR-0010.
  - ADR-0045: review is a dialog and the review mode a stored setting; supersedes parts of ADR-0036.
  - Amended: ADR-0009, ADR-0034, ADR-0010, ADR-0036.
- **Documents:**
  - `AGENTS.md`, plus `.claude/rules/offline-and-privacy.md`, `persistence-sqlite.md` and `testing.md`;
  - `openspec/config.yaml`'s stack line;
  - the specification clauses that lag (listed per group in `tasks.md`);
  - `CHANGE_BACKLOG.md`: this change goes before `redesign-translation-pipeline`.
- **Not in this change:**
  - pipeline polish, including more frequent model summaries and rereading tone mid-run →
    `redesign-translation-pipeline`;
  - keychain credentials, packaging, OS notifications, a local language detector (none, by owner decision) and schema
    migrations → the first release.
