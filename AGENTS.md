# AGENTS.md — BookLoom

Operating manual, binding for every agent (Claude Code, Codex, anything else). `CLAUDE.md` imports this file and adds
nothing; never duplicate a rule there. Humans start at `docs/README.md`.

## What this is

A local-first, offline desktop app that translates whole books (EPUB, FB2, Markdown, TXT) with a locally-run LLM
(Ollama / LM Studio) or any OpenAI-compatible endpoint, preserving structure, IDs, images and fonts through a
canonical-equal round trip. Java 25 + JavaFX 26, Gradle Kotlin DSL + Guice, JUnit 5 + AssertJ + TestFX; SQLite/JDBI
(Flyway from the first release, ADR-0043). Full inventory: `gradle/libs.versions.toml`.

## Engineering principles — binding

- **KISS, YAGNI.** The smallest correct solution for today's requirement. No speculative abstraction, option,
  fallback or compatibility path; a clause nothing uses yet waits for its first user.
- **DRY, reuse first.** Before writing a class, widget, helper or test fixture, search for an existing one and extend
  it. One implementation per job; a second one is a defect (guard tests exist for several).
- **SOLID, pragmatically.** Small cohesive types, narrow ports in `:api`, constructor injection, depend on contracts.
  No god objects or wrappers that only forward. Methods ≤ 30 lines, classes ≤ ~400 (Checkstyle enforces).
- **Evidence over assumption.** Read the code and the specs before designing; test observable behaviour; never
  weaken a test to pass; never test what an external server does, only our code. Book-neutral sources: no names or
  text of a tested book in code, tests, prompts or fixtures.
- **Docs move with the code.** A change that alters behaviour updates its spec clause, the page under `docs/` that
  describes it, and this file's "Where it stands" in the same change.

## Where it stands — keep this section current

The `redesign-translation-pipeline` change is built, verified and accepted by the owner and archived on
2026-11-12 (`openspec/changes/archive/2026-11-12-redesign-translation-pipeline/`); the next change comes from
`docs/spec_baseline/implementation_plan/CHANGE_BACKLOG.md`. All eight modules carry real code; the whole workflow runs from the window and the command
line, on a stored project (SQLite `bookloom.db` in the data folder, a chunk's decisions and run record in one
transaction; an old database whose schema or record shapes changed is set aside as `bookloom.db.old-<time>` until the
first release, ADR-0043).

- **Document engine** (`:document`): four-format round trip, inline masking to `⟦gN⟧`, unmask behind a placeholder
  hard gate (multiset and order of paired placeholders), inspection, sentence splitting, auxiliary text units; verified
  on the owner's local corpus (235 books at the last sweep).
- **LLM layer** (`:llm`): chat-model port, pseudo model, gated and retried Ollama-native and OpenAI-compatible
  clients, detected engines, per-model profiles, bundled quant and language knowledge, per-engine timings (a missing
  figure is an en dash, never 0), verification, model discovery. Every call is bounded (output cap, per-kind timeout,
  one retry with a new seed, streamed Ollama replies with an idle gap, a stall watchdog); single-flight through one
  `InferenceGate`.
- **Pipeline** (`:pipeline`): a run has three stages. **Prepare** (style sheet, names scan), **Translate** (chunks
  close on the token budget and at chapter boundaries; JSON batches sized by the model profile; checks with levels
  BLOCK, REPAIR, NOTE; the acceptance rule: a failing draft gets **one repair** and the new text stays only through
  `NeverWorse`, else the segment is flagged; translation memory; one context builder plans every call from the book
  map), **Proofread** (final audit, term sweep, narrator inference, deferrals settled by code, a mark list; Fast stops
  there, Balanced reads each marked window once, Max adds a second round). Completed only after Proofread. The quality
  dial (Fast / Balanced / Max) sets only the fixing call and how far Proofread reads; the review mode (Unattended /
  Assisted / Manual) decides only when the run pauses (ADR-0046). Export is a separate action with no model call.
- **UI** (`:ui`, `:app`): five numbered steps (Import, Book Brief, Names & style, Translating, Export), Settings
  (Providers, Models, Generation, Automation, Appearance, Storage & logs), a Projects screen, English and Ukrainian
  bundles chosen by the OS, light and dark theme (token-only, WCAG AA), every operable control explains itself on
  hover. The command line (`:app:translate`, `scripts/e2e-fixture.sh`) is the headless proof tool.
- **Accepted known limits (2026-11-11)**, guarded by the review desk, written in `docs/known-limits.md` and backlog
  D20–D25: quality not proven better than the old pipeline (Q1); narrator gender slips; source-language words in ru to
  uk output; possessives and case forms stored as separate glossary rows.
- **Figures** of the closing run: `docs/evaluation/results-redesign-closing-run.md#redesign-closing-run`. Local-only
  data (sample books, the corpus, eval inputs, outputs, history) lives in the git-ignored `.temporary_context/`,
  described by its `README.md`, never in the home folder or the repository.

## Commands

```bash
scripts/test-focused.sh                           # THE INNER LOOP — format + the tests the diff points at + fastTest of dependents
scripts/test-focused.sh --full-module             # the changed module's full check — once before each commit
./gradlew build                                   # compile + lint + test
./gradlew clean build check spotlessCheck         # THE GATE — exactly what pre-push and CI run (≈20 min from clean; 17m 45s–20m 17s measured)
./gradlew :ui:fastTest                            # a module's tests minus the `slow`-tagged end-to-end classes
python3 scripts/slowest-tests.py                  # where the last run's test time went
./gradlew :document:test --tests 'ua.bookloom.document.golden.*'   # one class or package
./gradlew :app:run                                # launch the app
./gradlew spotlessApply                           # fix formatting
BOOKLOOM_CORPUS_DIR=/path ./gradlew :document:corpus   # the owner's corpus sweep, local only
./gradlew liveLocal | promptEval | visual         # local-only tagged sets, never in check
./gradlew sonar -PsonarLocal                      # local SonarQube analysis (opt-in; after the gate)
scripts/ci-linux-ui.sh                            # the UI fit tests on Ubuntu in Docker, before pushing a screen change
scripts/package-macos.sh && scripts/launch-smoke.sh   # the packaged image and its launch check
scripts/e2e-fixture.sh epub                       # headless end-to-end proof on the invented fixture (needs a model)
scripts/eval-matrix.sh --suite mini               # prompt evals (local models, never in CI)
python3 scripts/sync-agent-files.py --check       # agent-file mirrors in sync (CI and pre-push run it)
```

Every script, with its flags and prerequisites: `docs/project/scripts.md`. CI and hooks: `docs/project/ci-and-hooks.md`.

## Definition of Done

Gate green **and** the change exercised in the running app (or by the one test that reproduces the user-visible
behaviour). Paste the gate's tail as evidence — "it passed" is not a result. The cadence: while coding run
`scripts/test-focused.sh`; run the changed module's full `check` once before a commit (`--full-module`); run the whole
gate at the end of a feature or step group (pre-push and CI still run it on every push). A red check anywhere is fixed before the
work is called done, never carried forward. At the end of each change group, after the gate, also run `./gradlew sonar -PsonarLocal` (opt-in, needs the local
SonarQube server; `docs/development/quality-and-ci.md#coverage`): the project stays at 0 open issues and the quality gate OK, so
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
`modules/<name>` (ADR-0021); Gradle names are unchanged. As-built history: `docs/spec_baseline/implementation_plan/01_MODULE_INVENTORY.md`.

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
- `docs/spec_baseline/specification/` is the baseline reference and is **editable**: when the code legitimately
  differs, fix the clause in the same change. `docs/spec_baseline/adr/` records decisions that are costly to reverse, each naming what would falsify it.
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

| What | Where |
|---|---|
| Start here (documentation index) | `docs/README.md` |
| Architecture and design | `docs/architecture/` (overview, document engine, UI, decision index) |
| How translation and the pipeline work | `docs/pipeline/` |
| Build, run, test, quality gate | `docs/development/` |
| Evals: procedures and measurements | `docs/evaluation/` (`README.md` first; `scripts/eval-matrix.sh`) |
| Scripts, CI, hooks, packaging, agent setup | `docs/project/` |
| Known limits and open work | `docs/known-limits.md` |
| **Baseline** the app was built from: original specification, ADRs, backlog and module inventory (editable reference; ADRs are decisions costly to reverse, each naming what would falsify it) | `docs/spec_baseline/{specification,adr,implementation_plan}`; new ADRs go in `docs/spec_baseline/adr/` |
| Live behavioural specs (the ledger of what is built) and changes | `openspec/specs/`, `openspec/changes/` |
| Rules, scoped by `paths:` | `.claude/rules/` |
| Skills (canonical) | `.agents/skills/`; Claude reads them through the `.claude/skills` symlink kept by `python3 scripts/sync-agent-files.py --apply` |

The `openspec-*` skills and `/opsx:*` commands are OpenSpec output: refresh with `OPENSPEC_TELEMETRY=0 openspec update`,
never hand-edit them (`docs/development/quality-and-ci.md#quality-gate`).
