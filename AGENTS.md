# AGENTS.md — BookLoom

Operating manual, binding for every agent (Claude Code, Codex, anything else). `CLAUDE.md` imports this file and adds
nothing; never duplicate a rule there. Humans start at `docs/DEVELOPMENT.md`.

## What this is

A local-first, offline desktop app that translates whole books (EPUB, FB2, Markdown, TXT) with a locally-run LLM
(Ollama / LM Studio) or any OpenAI-compatible endpoint, preserving structure, IDs, images and fonts through a
canonical-equal round trip. Java 25 + JavaFX 26, Gradle Kotlin DSL + Guice, JUnit 5 + AssertJ + TestFX; SQLite/JDBI/
Flyway planned. Full inventory: `gradle/libs.versions.toml`.

## Where it stands — keep this paragraph current

Real: `:api` (`Result`/`AppError`, document model, `DocumentPort`, project and brief records, storage and service
ports), `:util` (paths, hashing, the language catalogue), `:document` (four-format round trip, inline masking to
`⟦gN⟧`, unmask behind a placeholder hard gate that checks the multiset and the order of paired placeholders, book
inspection, sentence splitting, auxiliary text units — verified on the owner's local corpus, 235 books at the last
sweep), `:llm` (chat-model contract, factory, pseudo model, gated/retried Ollama-native and OpenAI-compatible clients,
token usage, verification, model discovery), `:persistence` (in-memory adapters behind the storage ports), `:pipeline`
(translation engine, pausable and interruptible job, checked export; prompt templates, style sheet, quality dial, chunk
packing, oversized-segment splitting, the stored-project service; the quality gates — deterministic checks, refusal
gate, confidence, per-chunk judge, directed fix, reflect → improve, polish, acceptance rule and quality loop; open language tags, the deterministic and model name scans, protected spans,
translation memory, rolling summary, context assembly, the deferral register, the glossary service with CSV), `:ui`
(the shell, six screens, the state mirror, English and Ukrainian bundles chosen by the OS, light/dark theme) and `:app`
(boot, logging, single-instance lock, DI, the window). A book of any of the four formats goes through the whole
pipeline from the window or the command line, with the pseudo model or a configured real provider — as a run on the
stored project that starts at its first pending segment and stores each decision there: preparation derives the brief's
style sheet and scans an empty glossary for names, each unit is packed into chunks, and every segment is drafted,
judged when the dial enables the judge, and decided by the acceptance rule and the quality loop, so an untranslated
echo is repaired or flagged. The "Also translate" switches decide what is translated, a segment drafted in pieces is repaired by redrafting
its pieces, locked names and kept foreign runs are protected behind tokens, each draft carries its context package
and every record its snapshot, repeated passages reuse the translation memory, the rolling summary, new names and
deferrals are kept as the run goes, and it pauses for review as the review mode says, announcing each segment and
model call as events. The review desk acts on segments (accept, edit, revert, skip, apply a proposal, retry with the
context the first draft saw), Max revises backwards after the last segment, export is checked per segment and writes
the chosen side files, the command line refuses an existing destination before any model call and cancels cleanly, the
review mode is a launch flag, and one whole-book test runs the parts together at the HTTP seam.

Real-book hardening (group 15b, after a 3,700-segment Ollama run): every model call is bounded — the judge capped by its
pairs, per-kind timeouts, a timed-out call retried once with a new seed and a lower cap, the Ollama reply read as a
stream with an idle gap — and a judge that cannot answer flags its segments (`judge-unavailable`) instead of pausing; a
step that pauses twice is flagged and the run goes on, a paused run can skip the failing segment, rounds that make no
progress stop early, and a resume continues at the call that failed. Invisible text is no segment, and a segment with no
letter, a Roman numeral, one character or only a locked name is kept verbatim with no call. The glossary scan drops
common words (lower-case share, stop-word lists, aliases), "Review with model" removes non-names and fills types, and the
table sorts and searches.

The window opens a book on the stored project (one current project, the import states chosen from the inspection's
verdict, a replace-run prompt, start and resume without importing again), shows the run and a connection chip in its
title bar, numbers six workflow steps with a provider footer, and its Book Brief, Structure, Names & style, Translating
and Export screens are live: the glossary table with scans, model review, suggested targets by the name policy and
CSV; the seven run states with a live panel showing each model call (kind, segment, attempt, its clock against the timeout), the round tracker and the
context sent to the model, a timed activity log with an errors-only view, the provider-error and stuck-call banners with
Skip segment and Retry now, and the review panel with retry and proposals; the Export screen writes the book (Save to,
side files, consistency pass, result tiles and checks, a neutral line for the file just written, the export-complete
dialog), and the Settings Providers tab tests a provider three ways with measured values. The theme is token-only in
both light and dark, every operable control explains itself on hover, wheel scrolling glides, the long lists have fixed
row heights, and the time left is the average of the last 20 timed segments. Not built: saving (nothing survives a
restart, no remembered settings, no SQLite), the other Settings tabs (Models, Generation, Automation, Storage) and the
Projects screen. Next: group 16 (the gate) of `openspec/changes/complete-translation-workflow`.

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
work is called done, never carried forward. A run materially longer than the last baseline is hung: kill it and
diagnose; never run two gates at once.

## Modules

| Module | Holds | Layer |
|---|---|---|
| `:api` | contracts, records, `Result`/`AppError`/`ErrorCode`, ports | foundation (framework-free) |
| `:util` | paths, hashing, language catalogue | foundation |
| `:document` | parse / mask / unmask / reassemble, per format | service |
| `:llm` | provider port + Ollama-native and OpenAI-compatible clients | service |
| `:persistence` | in-memory adapters behind the `:api` storage ports (ADR-0034); SQLite + Flyway + JDBI planned | service |
| `:pipeline` | translation engine: prompts, chunking, QA, judge, self-heal | orchestration |
| `:ui` | JavaFX views, theming | presentation |
| `:app` | launcher, composition root, the `archTest` suite | presentation |

Edges point downward only; `:pipeline` sees the three services, services see only `:api`/`:util`. Physical layout is
`modules/<name>` (ADR-0021); Gradle names are unchanged. As-built history: `docs/implementation_plan/01_MODULE_INVENTORY.md`.

## Non-negotiables

| Rule | Enforced by |
|---|---|
| FX-free core — only `:ui`/`:app` see JavaFX | ArchUnit `fx-free-core` |
| Skeleton never regenerated; only text nodes and the attribute values DD-47 lists (image alt text) change | per-format golden round-trip test |
| Records for data, Lombok only on services | ArchUnit `records-first` |
| Opening a book never needs the network; provider calls are user-triggered; anything else must be optional, safe, and degrade without an error | ArchUnit `no-http-in-core-except-llm` |
| `Result<T>` + typed `AppError` at every port; no exception crosses a module edge | advisory |
| Credentials are a reference (env-var/keychain), never a persisted secret | advisory |
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
