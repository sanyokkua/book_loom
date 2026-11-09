# AGENTS.md — BookLoom

Operating manual, binding for every agent (Claude Code, Codex, anything else). `CLAUDE.md` imports this file and adds
nothing; never duplicate a rule there. Humans start at `docs/DEVELOPMENT.md`.

## What this is

A local-first, offline desktop app that translates whole books (EPUB, FB2, Markdown, TXT) with a locally-run LLM
(Ollama / LM Studio) or any OpenAI-compatible endpoint, preserving structure, IDs, images and fonts through a
canonical-equal round trip. Java 25 + JavaFX 26, Gradle Kotlin DSL + Guice, JUnit 5 + AssertJ + TestFX; SQLite/JDBI
(Flyway from the first release, ADR-0043). Full inventory: `gradle/libs.versions.toml`.

## Where it stands — keep this paragraph current

Real: `:api` (`Result`/`AppError`, document model, `DocumentPort`, project and brief records, storage and service
ports), `:util` (paths, hashing, the language catalogue), `:document` (four-format round trip, inline masking to
`⟦gN⟧`, unmask behind a placeholder hard gate that checks the multiset and the order of paired placeholders, book
inspection, sentence splitting, auxiliary text units — verified on the owner's local corpus, 235 books at the last
sweep), `:llm` (chat-model contract, factory, pseudo model, gated/retried Ollama-native and OpenAI-compatible clients,
server engines and per-model profiles, token usage, verification, model discovery), `:persistence` (in-memory adapters
for the command line and tests, SQLite adapters for the window, behind the storage ports), `:pipeline` (the translation
engine and the pausable, interruptible job; prompts, the context builder, chunk packing, the stored-project service,
the glossary and names pass, translation memory, chapter summaries, the deferral register, the quality gates below,
export), `:ui` (the shell, the workflow screens, the state mirror, English and Ukrainian bundles chosen by the OS,
light/dark theme) and `:app` (boot, logging, single-instance lock, DI, the window, the command line).

A book of any of the four formats goes through the whole pipeline from the window or the command line, with the pseudo
model or a configured real provider, as a run on the stored project that starts at its first pending segment and stores
each decision there. A run has three stages: **Prepare** (the brief's style sheet, a names scan of an empty glossary),
**Translate** and **Proofread**, and is Completed only after Proofread. Translate packs each unit into chunks that close
on the token budget (and at a chapter boundary), drafts them in JSON batches sized by the model profile, resolves
protected spans and control codes by code, runs the deterministic checks (each finding has a level, BLOCK, REPAIR or
NOTE) and decides every segment by the acceptance rule: a draft that fails gets **one repair** call (Fast: a re-draft
only for lost formatting marks) and the new text stays only through the never-worse gate (`NeverWorse`), else the
segment is flagged. Each draft carries its context package and every record its snapshot; repeated passages reuse the
translation memory; new names and deferrals are kept as the run goes; each finished chapter gets a stored summary.
**Proofread** runs after the last chunk at every dial: a final audit, a term sweep, the narrator inference and the
deferrals settled by code, then a mark list; Fast stops there and lists the marks on the review desk, Balanced reads each
marked window in one minimal-edit call and Max adds a second round over every story paragraph, each rewrite kept only
through the never-worse gate (a person's edit gets a proposal). There is no judge, no per-chunk reviewer, no directed
fix, no backward revision and no consistency pass (groups 10, 13 and 14 of `redesign-translation-pipeline` removed
them; ADR-0046). The quality dial (Fast / Balanced / Max) sets only the fixing call and how far Proofread reads; the
review mode (Unattended / Assisted / Manual, a setting and a launch flag) decides only when the run pauses.

Export is a separate action and makes no model call: it validates each segment, writes the book and the chosen side
files (glossary CSV, bilingual HTML, quality report), asks before replacing an existing file and reports the stored
Proofread summary; a stopped or partial run can be exported, and what it did not translate stays in the source. The
command line refuses an existing destination before any model call, runs a book unattended with the window's outage
recovery, writes what a stopped run translated and is the headless proof tool (`scripts/e2e-fixture.sh`).

Every model call is bounded (output cap, per-kind timeout, one retry with a new seed and a lower cap, the Ollama reply
read as a stream with an idle gap); a step that pauses twice is flagged and the run goes on, a paused run can skip the
failing segment, and a resume continues at the call that failed. Invisible text is no segment, and a segment with no
letter, a Roman numeral, one character or only a locked name is kept verbatim with no call. Per-model profiles (built-in
rows with evidence, the facts read only when the person acts, the person's overrides) decide the reasoning field, system
role, samplers, structured rung and window of each call; model advice (quant, declared languages) is shown and never
blocks Start.

The window opens a book on the stored project (one current project, the book's content matched against the stored
projects so a known book is never imported twice, the import states chosen from the inspection's verdict), numbers five
workflow steps (Import, Book Brief, Names & style, Translating, Export) with a provider footer, and shows the run and a
connection chip in its title bar. Book Brief (languages, who narrates, name policy, quality, the "Also translate"
switches; read-only while a translation is at work, languages locked once a segment is decided); Names & style (the
glossary with one entry per name and its forms, the Recurring terms card, Scan · Review · Translate · Stop, CSV, an
autonomous names pass with Undo all); Translating (summary strip, live card with Simple and Detailed rows and the row's
call, prompt and reply, the activity log, the provider-error banners with Skip segment and Retry now, the Proofread stage
chip and `window` rows, and the review desk with accept, edit, revert, skip, apply a proposal, Changed by proofread, Undo
proofread and Try again with the context rebuilt from the current glossary and settings); Export (Save as, side files,
tiles from the project and the stored last export, banners for a translating, partial or empty book, the
export-complete dialog). Settings has Providers (tests the form as typed in three stages), Models (per-model settings and
profile overrides), Generation (shows what each quality setting does), Automation, Appearance and Storage & logs; the
Projects screen lists, opens, continues, renames and removes the stored projects. Saving is SQLite (WAL, JDBI, state as
JSON documents) at `bookloom.db` in the data folder, a chunk's decisions and run record are one transaction, and a file
whose schema or stored record shapes changed is set aside as `bookloom.db.old-<time>` before release. The theme is
token-only and holds WCAG AA in both themes, every operable control explains itself on hover, transitions stop under
reduced motion, and the first-launch look is the one silent local model listing of ADR-0044.

Measurements and evals live in `docs/DEVELOPMENT.md` (`docs/DEVELOPMENT.md#redesign-arms`, `docs/DEVELOPMENT.md#15d-results`, the prompt-eval and matrix
sections) and `scripts/eval-matrix.sh`; the history of the groups that were not planned up front, 15a–15j, is in
`openspec/changes/archive/2026-10-19-complete-translation-workflow/additional-work.md` (IDs unchanged). The archived
changes `complete-translation-workflow` and `finalize-ui-and-persistence` have every task and the owner's hand checks
done and their specs folded into `openspec/specs/`; `redesign-translation-pipeline` is under way (groups 1–13 built; group 14 is at its last step).
Local-only data (sample books, the corpus, eval inputs, outputs and history) lives in the git-ignored
`.temporary_context/`, described by its `README.md` — never in the home folder.

## Commands

```bash
scripts/test-focused.sh                           # THE INNER LOOP — format + the tests the diff points at + fastTest of dependents
scripts/test-focused.sh --full-module             # the changed module's full check — once before each commit
./gradlew build                                   # compile + lint + test
./gradlew clean build check spotlessCheck         # THE GATE — exactly what pre-push and CI run (≈4 min; ~2 is :ui TestFX)
./gradlew :ui:fastTest                            # a module's tests minus the `slow`-tagged end-to-end classes
python3 scripts/slowest-tests.py                  # where the last run's test time went
./gradlew :document:test --tests 'ua.bookloom.document.golden.*'   # one class or package
./gradlew :app:run                                # launch the app
./gradlew spotlessApply                           # fix formatting
BOOKLOOM_CORPUS_DIR=/path ./gradlew :document:corpus   # the owner's corpus sweep, local only
./gradlew liveLocal | promptEval | visual         # local-only tagged sets, never in check
```

## Definition of Done

Gate green **and** the change exercised in the running app (or by the one test that reproduces the user-visible
behaviour). Paste the gate's tail as evidence — "it passed" is not a result. The cadence: while coding run
`scripts/test-focused.sh`; run the changed module's full `check` once before a commit (`--full-module`); run the whole
gate at the end of a feature or step group (pre-push and CI still run it on every push). A red check anywhere is fixed before the
work is called done, never carried forward. At the end of each change group, after the gate, also run `./gradlew sonar -PsonarLocal` (opt-in, needs the local
SonarQube server; `docs/DEVELOPMENT.md#coverage`): the project stays at 0 open issues and the quality gate OK, so
new findings are fixed in the same group, not left for a later sweep. A run materially longer than the last baseline is hung: kill it and
diagnose; never run two gates at once.

## Modules

| Module | Holds | Layer |
|---|---|---|
| `:api` | contracts, records, `Result`/`AppError`/`ErrorCode`, ports | foundation (framework-free) |
| `:util` | paths, hashing, language catalogue | foundation |
| `:document` | parse / mask / unmask / reassemble, per format | service |
| `:llm` | provider port + Ollama-native and OpenAI-compatible clients | service |
| `:persistence` | in-memory adapters (command line, tests; ADR-0034) and SQLite + JDBI adapters (window; ADR-0043; Flyway from the first release) behind the `:api` storage ports | service |
| `:pipeline` | translation engine: prompts, context builder, checks, one repair, Proofread stage | orchestration |
| `:ui` | JavaFX views, theming | presentation |
| `:app` | launcher, composition root, the `archTest` suite | presentation |

Edges point downward only; `:pipeline` sees the three services, services see only `:api`/`:util`. Physical layout is
`modules/<name>` (ADR-0021); Gradle names are unchanged. As-built history: `docs/implementation_plan/01_MODULE_INVENTORY.md`.

## Non-negotiables

| Rule | Enforced by |
|---|---|
| FX-free core — only `:ui`/`:app` see JavaFX | ArchUnit `fx-free-core` |
| Skeleton never regenerated; only text nodes and the attribute values DD-47 lists (image alt text) change — plus, in the EPUB package, the language rewrite and the sort keys of a changed title or creator (a title's `opf:file-as`, `calibre:title_sort` and file-as refine are removed; a creator's `file-as` is rewritten from the translated name when it has two words or more in an alphabetic script and the source key was surname-first, else removed) | per-format golden round-trip test |
| Records for data, Lombok only on services | ArchUnit `records-first` |
| Opening a book never needs the network; provider calls are user-triggered; anything else must be optional, safe, and degrade without an error; plus one exception (ADR-0044): on the very first launch only (a fresh database counts), after the window is shown, one silent model listing of exactly http://127.0.0.1:11434 (Ollama /api/tags) and http://127.0.0.1:1234/v1 (LM Studio /models), no other address, no prompt or book text, nothing answering is not an error, never repeated by itself, desktop window only, only :llm holds HTTP code; every later check is user-triggered | ArchUnit `no-http-in-core-except-llm` |
| `Result<T>` + typed `AppError` at every port; no exception crosses a module edge | advisory |
| Credentials are a reference (env-var name; keychain not built), never a persisted secret | advisory |
| Single-flight inference through one `InferenceGate` | advisory |
| Scene graph touched only on the FX Application Thread | advisory |
| Diagnostic logging at every level — INFO lifecycle, DEBUG each working method's parameters and each branch taken, TRACE values and book text; `BOOKLOOM_LOG_LEVEL` raises it; never a secret (`.claude/rules/logging.md`) | advisory |
| Never mock the boundary a test exists to prove; mock only I/O and non-determinism (no Mockito today) | advisory |

## How work is planned

- A change is small: a brief of at most one page (an OpenSpec proposal under `openspec/` when the owner wants one —
  `/opsx:*` is available, not required), at most ~10 tasks, the acceptance test seen **red** before the implementation,
  then green, then the app run by hand. No task ledger is maintained after a change ships; a checkbox is a claim, the
  test is the evidence.
- `docs/specification/` is the reference and is **editable**: when the code legitimately differs, fix the clause in the
  same change. `docs/adr/` records decisions that are costly to reverse, each naming what would falsify it.
- Sub-agents in `.claude/agents/` exist for read-heavy work (mapping, searching, diagnosing a red gate). The decision
  about the next step is never delegated.
- Stop and ask only to: resolve an ambiguous spec (propose a default), report a gate red twice for the same cause, or
  do anything irreversible — merge to `master`, tag, publish, delete outside the repo.

## Git

`master` is protected: never develop on or merge into it — final review and merge belong to the owner. Work on
`feature/<slug>`; branch each task as `feature/<slug>--<task>` (the `--` separator avoids Git's file/directory ref
conflict). Commit each task on its task branch. After marking the task complete in `tasks.md` and committing it,
squash-merge the task branch into its parent `feature/<slug>` branch, then delete the task branch; a checked-off,
committed task is not complete until this merge succeeds. If no separate task branch was created, skip this merge
step. Never `--no-verify`, never force-push; if a hook is wrong, fix the hook and say so.

## What will bite you

- The window's text is measured with the platform's font, so a fit test that passes on macOS can fail on the Ubuntu
  runner (run 40489525598: 199 failures, then the 30-minute ceiling). Layouts must hold on both without a bundled
  font: a wrapping label gets `Wrapped.wholeLines`, a button or chip that must stay whole gets
  `setMinWidth(Region.USE_PREF_SIZE)`, and a row that can run out of room wraps (`FlowPane`) instead of squeezing.
  Before pushing a change to a screen, a dialog or a UI word run `scripts/ci-linux-ui.sh` (Docker/Colima), and push
  after each step group, never 100+ commits at once.
- A `gradle/libs.versions.toml` pin that no `constraints {}` block references does nothing — grep for the `libs.`
  accessor; no hit means decoration.
- `:app` runtime deps produce no lockfile diff — correct, not broken (JavaFX per-OS classifier carve-out). Verify an
  `:app` dependency's version in the catalog.
- `modules/build-logic` is an included build with its own lock state: after changing its dependencies run
  `./gradlew -p modules/build-logic resolveAndLockAll --write-locks`.
- lefthook silently skips pre-push *jobs* when the push-file list is empty; the gate is a `scripts:` entry for that
  reason. Keep it so.

## Where things live

Spec `docs/specification/` · decisions `docs/adr/` · backlog `docs/implementation_plan/CHANGE_BACKLOG.md` · OpenSpec
`openspec/` · rules `paths:`-scoped in `.claude/rules/` · skills canonical in `.agents/skills/`, which Codex reads
directly and Claude reads through the `.claude/skills` symlink kept by `python3 scripts/sync-agent-files.py --apply` ·
the `openspec-*` skills and `/opsx:*` commands are OpenSpec output: refresh with `OPENSPEC_TELEMETRY=0 openspec update`,
never hand-edit them (`docs/DEVELOPMENT.md#quality-gate`).
