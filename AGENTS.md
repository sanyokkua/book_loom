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
token usage, verification, model discovery), `:persistence` (in-memory adapters for the command line and tests, SQLite adapters for the window, behind the storage ports), `:pipeline`
(translation engine, pausable and interruptible job, checked export; prompt templates, style sheet, quality dial, chunk
packing, oversized-segment splitting, the stored-project service; the quality gates — deterministic checks, refusal
gate, confidence, the per-chunk reviewer with verified edits, directed fix, acceptance rule and quality loop; open language tags, the deterministic and model name scans, protected spans,
translation memory, rolling summary, context assembly, the deferral register, the glossary service with CSV), `:ui`
(the shell, the workflow screens, the state mirror, English and Ukrainian bundles chosen by the OS, light/dark theme) and `:app`
(boot, logging, single-instance lock, DI, the window). A book of any of the four formats goes through the whole
pipeline from the window or the command line, with the pseudo model or a configured real provider — as a run on the
stored project that starts at its first pending segment and stores each decision there: preparation derives the brief's
style sheet and scans an empty glossary for names, each unit is packed into chunks, and every segment is drafted,
reviewed when the dial enables the reviewer (it fixes in place, and the code verifies every edit), and decided by the acceptance rule and the quality loop, so an untranslated
echo is repaired or flagged. The "Also translate" switches decide what is translated, a segment drafted in pieces is repaired by redrafting
its pieces, locked names and kept foreign runs are protected behind tokens, each draft carries its context package
and every record its snapshot, repeated passages reuse the translation memory, the rolling summary, new names and
deferrals are kept as the run goes, and it pauses for review as the review mode says, announcing each segment and
model call as events. The review desk acts on segments (accept, edit, revert, skip, apply a proposal, retry with the
context the first draft saw), Max revises backwards after the last segment, export is checked per segment and writes
the chosen side files, the command line refuses an existing destination before any model call, runs a book unattended with the window's outage recovery, writes what a stopped run translated and is the headless proof tool (`scripts/e2e-fixture.sh`), the
review mode is a launch flag, and one whole-book test runs the parts together at the HTTP seam.

Real-book hardening (group 15b, after a 3,700-segment Ollama run): every model call is bounded — its output capped (`maxOutputTokens`, `num_predict`/`max_tokens`), the reviewer capped by its
pairs, per-kind timeouts, a timed-out call retried once with a new seed and a lower cap, the Ollama reply read as a
stream with an idle gap — and a reviewer that cannot answer flags its segments (`reviewer-unavailable`) instead of pausing; a
step that pauses twice is flagged and the run goes on, a paused run can skip the failing segment, rounds that make no
progress stop early, and a resume continues at the call that failed. Invisible text is no segment, and a segment with no
letter, a Roman numeral, one character or only a locked name is kept verbatim with no call. The glossary scan drops
common words (lower-case share, stop-word lists, aliases), "Review with model" removes non-names and fills types, and the
table sorts and searches.

The window opens a book on the stored project (one current project, the import states chosen from the inspection's
verdict, an "Open another book?" question whenever a project is open, the book's content matched against the stored projects so a known book is named and never imported again, one verdict line with a closed "About this book" fold and a three-pane text browser, start and resume without importing again), shows the run and a connection chip in its
title bar, numbers five workflow steps (Import holds the book's structure since 15i.U3) with a provider footer, and its Book Brief, Structure, Names & style, Translating
and Export screens are live: the glossary table with scans, model review, suggested targets by the name policy and
CSV; the Translating screen as a summary strip (time left, bar, segments done, counts, "n to review") over a full-height live card (finalize 6.5: the chunk's rows with their markers beside the selected row's detail, Simple and Detailed with the row's call, prompt and reply, follow the newest translation, the scene summary, All calls… over the last 50 calls, the sleep help line, and an Activity log tab with seven kinds and Problems only), the provider-error and stuck-call banners with
Skip segment and Retry now, and the review desk — an extra-large dialog that also opens by itself when a translating run completes with segments to review, with row markers, one main button that turns into Save my edit, More ▾ (Leave for later, Try again, an inline Try again with a note, Undo my edit) and the batch's sent sections under "What the model saw"; the Export screen writes the book (Save as as a
short path with Change… and ✦ Suggest a name used only on request, side files, the last check — locked off for a partial
book without touching the saved setting — tiles from the project and from the stored last export, banners for a
translating, partial or empty book, ⬇ Export book in the toolbar with its reason, a question naming each existing file
before it is replaced, the last-check dialog with the reread items next to their neighbours, Write the file now and a
Cancel that asks, and the export-complete dialog), and the Settings Providers tab tests a provider three ways with measured values. The theme is token-only in
both light and dark, every operable control explains itself on hover, wheel scrolling glides, the long lists have fixed
row heights, and the time left is the steps left times the measured seconds per kind of step over the whole run, pauses left out, shown as "about …" after 5 steps or 3 % (a progress ledger of all steps, finalize 5.4). The Recurring terms card under the glossary lists the book's repeated common words and titles with the rendering the run keeps for each (the person's, else the most used one the text proves), and the run asks the model in each batch which rendering it used and shows later calls the established one, and also learns a term's rendering from the decided segments by co-occurrence with no model call, so a model that ignores that field still keeps the book consistent (15d.9; titles and polysemous words are left to the glossary). The Book Brief says who narrates (person and gender); the style sheet tells every call, a gender sheet lists the characters a segment names, and a Ukrainian check holds the narrator's own words (outside quotes and dialogue) to the narrator's gender, earning one directed fix as a soft finding (15d.10). The Book Brief is read-only while a translation is at work, locks its languages once a segment is decided, shows the project's stored name with Rename…, asks Same language pair for a used pair, and fills in from the book through a dialog that holds the answer until Apply (finalize 6.3). Names & style has a purpose line, an empty glossary that offers Scan the book and + Add, a Used column of counts on the recurring terms, a banner while a translation is at work, a read-only screen under a hidden pass, the names pass in the long dialog as eight stages with their request counts, and a Results view (Changes and Steps tabs, Undo all) in `ProgressDialog` that a hidden pass offers through a message (finalize 6.4). Saving is built in the window (finalize group 3): projects, segments, glossary, lexicon, translation memory, summary, deferrals, runs, settings, per-model settings and providers are kept in SQLite (WAL, JDBI, state as JSON documents) at `bookloom.db` in the data folder, a chunk's decisions and run record are one transaction, and a file whose schema or stored record shapes changed is set aside as `bookloom.db.old-<time>` before release; the command line keeps nothing. Not built: remembered-settings screens beyond the Providers tab, the other Settings tabs (Models, Generation, Automation, Storage) and the
Projects screen. Measurements and evals (15d.0–15d.1): per-call-kind tokens and times in the run summary, `scripts/segment-histogram.py`, `--stop-after`, a defect corpus, per-class thresholds and
`scripts/eval-matrix.sh` over `scripts/eval-models.txt`; gpt-oss works on Ollama's native endpoint. Group 15d (translation-quality engineering) is built: deterministic text checks and a typography normalizer run before any model reads a draft, the body-only glossary scan drops junk, a context budget sizes every call from the detected window, a per-language rules map is injected per language pair, drafts go out in token-budgeted JSON batches (Balanced and Fast up to 8 segments, only a failing id is drafted again alone), the reviewer fixes in place and the code verifies every edit, the repair path keeps the best candidate, a final audit lists suspicious accepted segments, and the review desk shows evidence, chips and edit diffs. Measured on Bartimaeus 1 with `gemma4:e4b-mlx` (`docs/DEVELOPMENT.md#15d-results`): the whole book in 3 h 40 min against 8 h 18 min, 0.36 calls per segment against 1.29, 2.4 % flagged against 4.5 %. Consistency (15e.13): a learned rendering stays while the book supports it, a target-language stop word is never one, and a glossary name with no target gets the spelling the book used as a suggestion (`docs/DEVELOPMENT.md#15e-after3`). Open: terms the model splits from the first occurrence (`magician`) stay split, and the speed goal of about 1.6 s per segment on e4b is not met (3.0 s; generation is the floor). No word dictionary is bundled or planned (owner decision). Quality round 2 (15e.5–15e.12, 15e.14): a usable draft is kept and its quote marks repaired by code instead of exporting English; leaked protocol text is rejected; a cut reviewer reply is salvaged and re-asked; compact lines and the name-missing check are precise; counts are honest; a target-alphabet check catches Russian letters; the lexicon is more precise. A first-person narration is detected from the source, "Who narrates?" is asked once at Start (Enter means not stated), the Book Brief shows a notice, and first-name seeding suggests genders marked "(suggested)" (never retypes, once per entry). The prompt eval builds its requests through the same `PromptRequests` as the job, and `--suite sequence` measures consistency across chapters. Open: 26b with the narrator detected is not measured (15e.14), speed (15e.15). Group 15f (after the owner's review of a 26b Burning Chrome run) is built: a dropped sentence and a lost vocative name are blocking text checks, the reviewer may not re-inflect a glossary name, the consistency pass checks risky paragraphs against their neighbours with the model, the window is 16k, the model suggests the file name, the Book Brief's style and the recurring terms, the toolbar holds Back and Continue, every brief option explains itself and the title bar shows tokens per second. Open: narrator-gender inference, the missing-id re-ask, a dialogue normaliser, scrolling on macOS (`openspec/changes/archive/2026-10-19-complete-translation-workflow/additional-work.md` 15f). The export's consistency pass (15g F18–F19) first drafts every flagged or audit-doubted segment again and keeps the new text only when it is accepted, better and loses nothing (quotes, dashes, sentences, words, names, no new Latin run), then checks the flagged, repaired and doubted paragraphs (every paragraph at Max) against the previous and next paragraph shown as source and translation with names, recurring terms, characters and the summary; refused answers are counted by rule, and its progress dialog shows each call inside the window (finalize 5.3 replaced the "Model calls" window of 15j.J6). Group 15g's production polish (H21–H24) is built: every screen holds WCAG AA in both themes (status words in their own `-fg` roles, 3:1 input and switch edges, a 12px text floor, all enforced by `ContrastTest`), keyboard focus is a slate-blue ring outside the control and Tab walks every workflow screen without a trap, short transitions live in `Motion` and stop under reduced motion (OS or `BOOKLOOM_REDUCE_MOTION`, set for every test task), the title bar gives way by priority at 960 px, and machine codes (call kinds, outcome reasons, refusal rules) read as EN/UK words. Glossary gender (B6): a CSV import never turns a known type or gender into an unknown one, a character's gender is suggested from the pronouns that follow the name in the book (language file `femalePronouns`/`malePronouns`, shown on the Characters line as `she ×4`), a woman's name followed by a masculine Ukrainian verb or declined like a man's earns one directed fix, a glossary name swapped for another is a blocking `name-swap`, a changed number is a soft `number` finding, and contractions, half-names joined by a connector and stop words are not proposed as names. Group 15h (quality round 4 and UI round 2, after the Oct 9 e4b and 26b runs) is built in code: control codes are resolved on every path including batches; gender and type are decided from pronoun evidence across the book (windows, verified citations); refused reviewer edits that disprove themselves are low notes; name-variant, lost-name, Latin-run, foreign-word and identifier checks; glossary aliases (plural/typo) share the base target; title/author segments composed; a missing-ids batch re-ask; the consistency pass answers `unchanged` and remembers checked and doubted paragraphs; the brief is suggested from two samples of three windows with verified evidence and the narrator's gender only from verifiable evidence; the detector-recall, stability and gold evals; time to first token per call kind; the Translating screen no longer re-lays out per tick, trackpad gestures survive replaced nodes, the call panel shows every call's input and reply, Names & style has one Scan · Review · Translate · Stop bar and a results dialog with revert, the Title card lives on Export, and unknown character genders are asked at Start. The narrator's gender is taken in order and never from a model alone (15i.T5): the brief's verified quote, a recheck of its pending quotes against the glossary at Start, the Start question, then the run's own count of «я + past verb» forms in the decided translations (8 forms, 80 %), stored as inferred with its slips re-rendered at Max. Names & style is two equal cards (15i.U5), the Glossary and the Recurring terms, each built from one `TermTableCard` (search, + Add, Scan, Review, Translate, Import, Export; Import and Export fold into a three-dot menu when narrow), with CSV for both and a file dialog behind `CsvFileChooser`. Group 15i (the model's judgement of the book, after the Oct 10 eval) is built: a book index (postings, windows, the story's script), an Auto source language (ADR-0042; a prompt never names a language nobody chose), a predicted brief with verified evidence, an autonomous names pass ("Prepare names automatically": triage, per-window readings decided by code, clusters with one gender and one spelling, recurring terms, lock rules, staged and written once, with Undo all), translation hardening (a suggested gender is "likely" and never enforced, repairs never worsen a clean draft, a first-item-only batch reply is no size signal, brands and identity renderings, the narrator's gender in order, the ти/Ви mix check, keep-awake, continue or start over on re-import) and five workflow steps with two-column screens. Experiments: relevant earlier pairs (T8) and long paragraphs as sentence groups (X3) missed their gates (T8 stays off, X3 was removed); the injection example (T6) was reverted; the batch format (X1) stays JSON. Measured in `docs/DEVELOPMENT.md` (`#15i-baselines`, `#15i-translation-rounds`, `#15i-names-gate`, `#15i-x3`). Group 15j (after the owner's Oct 11–16 runs) is built: brands follow the name policy, materials are translated, identifiers are kept; a clear pronoun tally verifies a gender and a suggested gender is confirmed with Undo; multi-word places keep every word; Russian letters block and are repaired; name checks read inflected titles through one alternation guard; a dialogue sentence-count check; glossary hygiene and an untargeted list; Max's export pass skips what the backward revision checked and never sends navigation or front matter; the reviewer sees only its pairs' names; answered calls show their target; the Model calls window (since replaced by the progress dialog); an always-on per-run log and a 100 MB × 4 evidence log; and the mini eval book with the fast eval loop (`scripts/probe.sh`, `--quick`, `scripts/gradle-lock.sh`, `--suite book`). The rest of 15h–15i is closed too: Ollama's cached prompt tokens are read (15h.C5; the measured reorder of the token rules cost token fidelity on e4b and was reverted, prompt order moves to the redesign), lower-case words stay recurring terms (15i.N7), and a firm joined by "and" and a stop-word-head name stay one candidate, the lost classes that remain being written down with their reasons (15i.N8). The packaged image's launch smoke has a fourth signal, `resolve-all passed` (16.2): it builds the injector in the production stage and loads every screen and port, on the classpath, so a missing `opens` is not what it sees. The groups that were not planned up front, 15a–15j, live in `openspec/changes/archive/2026-10-19-complete-translation-workflow/additional-work.md` (IDs unchanged); the pipeline redesign is a separate proposal (`docs/proposals/2026-10-16-translation-pipeline-redesign.md`, the next change `redesign-translation-pipeline`, with a "Moved here" list). `complete-translation-workflow` was archived on 2026-10-19 with every task, the owner's hand checks included, done; its specs are folded into `openspec/specs/`. Local-only data (sample books, the corpus, eval inputs, outputs and history) lives in the git-ignored `.temporary_context/`, described by its `README.md` — never in the home folder. The review editor keeps each formatting mark whole (shown as `⟦N⟧`), holds Save while one is missing or doubled, and a saved edit with a broken mark is refused with `validation` (finalize 6.6b). Settings › Providers is built (finalize 7.1a): the provider list, the form, the summary card, the Connection details fold and a pinned Save footer with a leave guard that asks Keep editing, Discard or Save naming the provider. Settings › Providers tests the form as typed, saved or not, in three stages (a grid of Test buttons, result sentences and Details) with a test dialog that shows the request, the figures and the reply and copies the request as curl with the key shown as ••••, stores the inference test's seconds per segment with its source (test or an earlier translation) on the model so the estimate before Start names where it was measured, and Use this one makes a provider current, asking first when the open book already has decided segments (finalize 7.1b; a run's own speed is written at 7.4). Next: the rest of `finalize-ui-and-persistence` (7.2 on, then groups 8–9), then the redesign change.

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
| `:persistence` | in-memory adapters (command line, tests; ADR-0034) and SQLite + JDBI adapters (window; ADR-0043) behind the `:api` storage ports | service |
| `:pipeline` | translation engine: prompts, chunking, QA, reviewer, self-heal | orchestration |
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
