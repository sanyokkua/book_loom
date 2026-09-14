# AGENTS.md — BookLoom

Operating manual, binding for every agent (Claude Code, Codex, anything else). `CLAUDE.md` imports this file and adds
nothing; never duplicate a rule there. Humans start at `docs/DEVELOPMENT.md`.

## What this is

A local-first, offline desktop app that translates whole books (EPUB, FB2, Markdown, TXT) with a locally-run LLM
(Ollama / LM Studio) or any OpenAI-compatible endpoint, preserving structure, IDs, images and fonts through a
canonical-equal round trip. Java 25 + JavaFX 26, Gradle Kotlin DSL + Guice, JUnit 5 + AssertJ + TestFX; SQLite/JDBI/
Flyway planned. Full inventory: `gradle/libs.versions.toml`.

## Where it stands — keep this paragraph current

Real: `:api` (`Result`/`AppError`, document model, `DocumentPort`), `:util`, `:document` (four-format round trip,
inline masking to `⟦gN⟧`, unmask behind a placeholder-multiset hard gate — verified on the owner's 216-book local corpus) and
`:app` (boot, logging, single-instance lock, DI, an empty themed window). Empty: `:llm`, `:pipeline`, `:persistence`;
`:ui` is a placeholder. **No book has been translated yet.** Next: the walking skeleton — one EPUB through one local
model to a translated EPUB, started from the UI — before any breadth (theming, persistence, QA, glossary).

## Commands

```bash
./gradlew build                                   # compile + lint + test
./gradlew clean build check spotlessCheck         # THE GATE — exactly what pre-push and CI run (≈1–2 min warm)
./gradlew :document:test --tests 'ua.bookloom.document.golden.*'   # one class or package
./gradlew :app:run                                # launch the app
./gradlew spotlessApply                           # fix formatting
BOOKLOOM_CORPUS_DIR=/path ./gradlew :document:corpus   # 216-book sweep, local only
./gradlew liveLocal | promptEval | visual         # local-only tagged sets, never in check
```

## Definition of Done

Gate green **and** the change exercised in the running app (or by the one test that reproduces the user-visible
behaviour). Paste the gate's tail as evidence — "it passed" is not a result. A red check anywhere is fixed before the
work is called done, never carried forward. A run materially longer than the last baseline is hung: kill it and
diagnose; never run two gates at once.

## Modules

| Module | Holds | Layer |
|---|---|---|
| `:api` | contracts, records, `Result`/`AppError`/`ErrorCode`, ports | foundation (framework-free) |
| `:util` | paths, hashing | foundation |
| `:document` | parse / mask / unmask / reassemble, per format | service |
| `:llm` | provider port + Ollama-native and OpenAI-compatible clients | service |
| `:persistence` | SQLite + Flyway + JDBI | service |
| `:pipeline` | translation engine: chunking, QA, judge, repair | orchestration |
| `:ui` | JavaFX views, theming | presentation |
| `:app` | launcher, composition root, the `archTest` suite | presentation |

Edges point downward only; `:pipeline` sees the three services, services see only `:api`/`:util`. Physical layout is
`modules/<name>` (ADR-0021); Gradle names are unchanged. As-built history: `docs/implementation_plan/01_MODULE_INVENTORY.md`.

## Non-negotiables

| Rule | Enforced by |
|---|---|
| FX-free core — only `:ui`/`:app` see JavaFX | ArchUnit `fx-free-core` |
| Skeleton never regenerated; only text nodes change | per-format golden round-trip test |
| Records for data, Lombok only on services | ArchUnit `records-first` |
| Opening a book never needs the network; provider calls are user-triggered; anything else must be optional, safe, and degrade without an error | ArchUnit `no-http-in-core-except-llm` |
| `Result<T>` + typed `AppError` at every port; no exception crosses a module edge | advisory |
| Credentials are a reference (env-var/keychain), never a persisted secret | advisory |
| Single-flight inference through one `InferenceGate` | advisory |
| Scene graph touched only on the FX Application Thread | advisory |
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

`master` is protected: never developed on, never merged into by an agent — final review and merge belong to the owner.
Work on `feature/<slug>`; branch each task as `feature/<slug>--<task>` (the separator is `--`, because `feature/x/y`
cannot coexist with the ref `feature/x`). Commit on task branches, squash-merge one coherent commit into the parent,
delete the task branch. Never `--no-verify`, never force-push; if a hook is wrong, fix the hook and say so.

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
