# Design

## Context

Motivation and scope are in `proposal.md`. This document holds only the decisions an implementer cannot derive alone.

- **The review.** `research/review-digest.md` measured the built app on whole books: a run can end "Finished" with
  paragraphs in the source language (F12), code rules in Prepare write wrong rows that 99 % of later uses follow (F5),
  about 40 % of a run's time changes nothing in the book (F13), the window's shortest path never runs the model's brief
  and names work (F11), and the evals cannot see translation quality (F7). Its section "What the evidence supports
  keeping" lists what this change does not rework.
- **The owner's points.** Recorded in `research/review-digest.md#owner-decisions` and in the five window points behind
  `research/ui-audit-shell-dialogs.md`, `research/ui-audit-workflow.md`, `research/ui-audit-settings.md` and
  `research/combo-box-behaviour.md`; the release pipeline's facts are in `research/ci-and-release.md`.
- **What exists.** Eight Gradle modules with downward edges only (`:api`, `:util`, `:document`, `:llm`,
  `:persistence`, `:pipeline`, `:ui`, `:app`). A run has three stages (Prepare, Translate, Proofread) and an export
  that makes no model call. The brief suggestion (`SetupAssistant`) and the names pass (`NamesPass`) exist but run
  only from buttons and from the eval harness. Storage is SQLite with JSON documents; a database whose schema number
  or stored-record fingerprint differs is set aside (ADR-0043). One workflow file, `.github/workflows/ci.yml`, runs the
  whole gate; no release is published. `docs/spec_baseline/` is a reference tree that 265 tracked files cite.
- **Constraints.** FX-free core, `Result<T>` at every port, one model call at a time through `InferenceGate`, no
  network call that is not user-triggered provider communication (ADR-0044 keeps its one exception), book-neutral
  tests and fixtures, and a layout that holds with the Linux runner's fonts.

## Goals / Non-Goals

**Goals:**

- A finished run holds a translation for every paragraph, or says how many it does not, before the file is written.
- Prepare writes only rows that the person or verifiable evidence supports; consistency over the whole book is held
  by code after Translate, with no model call.
- The default path is one drop and one press: the brief suggestion and the names pass run inside the run, and Start
  asks nothing but a missing target language.
- Every model call that changed nothing in the measured book is removed, and the saving is confirmed on a run with
  the tool that took the baseline.
- Quality claims are judged on a fixed blind panel of real paragraphs with a plain-ask control arm.
- The window is the same for every language, a model is chosen in one place, and the audit's High and Medium defects
  are closed.
- A pull request is verified in parallel jobs, a tag on `master` publishes five portable archives, and the repository
  holds no `docs/spec_baseline/` tree.

**Non-Goals:**

- No LanguageTool and no other language-specific external tool; no speaker or addressee line; the translate
  prompt's wording is not changed for quality (two things do change and neither is that: data slots leave a request
  with the removed context blocks, D12, and the proofread reply becomes a list of edits to save time, D11, which has
  its own measured step in D15); no new context block; no batch-size tuning; no profile, prompt, format or reader
  for a special model (TranslateGemma, Qwen, Hunyuan-MT); no test of MamayLM 27B or gemma4:31b.
- No language-specific option row in the window; no cross-provider model chooser.
- No database migration: stored shapes change and the database is set aside (ADR-0043).
- No signing, no notarisation, no installer type (`.dmg`, `.deb`, `.msi`) and no test suite in the release workflow.
- Not acted on, with the review's reasons (`research/review-digest.md#not-acted-on`): the weight and contradictions of
  the translate request (F6), non-spaced scripts and other alphabets (F9), sampling differences on the LM Studio path
  (F10), new models (F18), ти/ви tracking for English sources.
- Two defaults that `research/review-digest.md#f16--defaults-work-against-the-goal` lists stay as they are, each
  unchanged because it was not measured as harmful: the name policy Transliterate (the harm the review saw came from
  the letter refusal and the shared-word swap, which D8 removes; a translated speaking name is then kept under the
  same policy), and no automatic continue after a crash or quit (the review counted 1–2 clicks and measured nothing;
  the launch already reopens the last project and offers Continue). The behaviour when the lid is closed was not
  proven in a run by the review and is not changed either.
- Not done by the cleanup: splitting the 109 long legacy requirements of the main specs, rewriting `ADR-00NN` ids out
  of 438 files, renaming `QualityLoop` (D38, D41, D43).

## Decisions

Path prefixes used in every decision: `P/` = `modules/pipeline/src/main/java/ua/bookloom/pipeline/`, `PT/` =
`modules/pipeline/src/test/java/ua/bookloom/pipeline/`, `PR/` = `modules/pipeline/src/main/resources/ua/bookloom/pipeline/`,
`U/` = `modules/ui/src/main/java/ua/bookloom/ui/`, `UT/` = `modules/ui/src/test/java/ua/bookloom/ui/`,
`A/` = `modules/api/src/main/java/ua/bookloom/api/`, `L/` = `modules/llm/src/main/java/ua/bookloom/llm/`,
`C/` = `modules/app/src/main/java/ua/bookloom/app/cli/`, `S/` = `modules/persistence/src/main/java/ua/bookloom/persistence/`,
`MSG` = `modules/ui/src/main/resources/ua/bookloom/ui/i18n/messages_en.properties` (and `messages_uk.properties`). In
D23–D29 a path with no prefix (`state/…`, `screen/…`, `control/…`, `dialog/…`, `i18n/…`) is under `U/`, `theme.css` is
`modules/ui/src/main/resources/ua/bookloom/ui/theme.css`, and "the bundles" are the two `MSG` files. A class marked
"new" does not exist yet; every other class named was read on the working tree on 2026-11-14. Decisions are numbered in
the order of the task groups that build them: D1–D5 groups 1 and 2, D6–D9 group 3, D10–D15 group 4, D16–D19 group 5,
D20–D21 group 6, D22 group 7, D23–D28 group 8, D29 group 9, D30–D35 group 10, D36–D43 group 11, D44 group 12.

### D1 — A fixed real-book blind panel, built from the existing harness

**Decision.** Translation quality is measured on a fixed sample of real paragraphs: 100 per language pair (en→uk and
ru→uk), each drawn once with a stored seed from a pinned slice of an owner book. Two arms are produced for every
sample paragraph by the same model: `app` (the book slice run through the production chain, the paragraph taken from
the export) and `plain` (one plain chat ask per paragraph, no rules, no context). The two arms are written as two
ordinary eval-history runs, shown to readers as one shuffled unlabeled sheet, and scored paired.

**Why.** The suites score protocol and consistency, never whether the text is good; nine of fourteen arm decisions
were ties at n = 17–60, and a consistently wrong term scores 100 % (`research/review-digest.md#f7--the-evals-cannot-see-translation-quality`).
A score compares only inside one sheet (noise band 0.3), so both arms must be on the same sheet and judged in pairs
(`research/review-digest.md#method-and-limits`).

**Reuses.**
- `PT/eval/BookRunner.java` and `PT/eval/WholeBookStages.java` — the `app` arm is one book run; `PT/eval/ExportedBooks.java` reads the exported text by segment id.
- `PT/eval/DatasetManifest.java` — pins the data by SHA-256; its `ownerData` flag already exists.
- `PT/eval/EvalOutput.java`, `PT/eval/EvalProvenance.java`, `PT/eval/CaseRecord.java` — the version-2 history run with `cases` that carry `caseId`, `source` and `output`.
- `scripts/eval-score.py blind A B --out --key --seed` — writes the shuffled sheet and its key from two runs; `sign_test`, `bootstrap_interval`, `kendall_tau_b` in the same file do the statistics.
- `scripts/eval-matrix.sh` — one model resident at a time, the gradle lock.

**New (and nothing else).**
- Owner data, never in the repository: `.temporary_context/eval-artifacts/panel/<id>/book.<ext>` (the slice) and `sample.json` (`{"seed", "segmentIds": [100 ids]}`), for `<id>` = `panel-en-uk` and `panel-ru-uk`. The English slice is the five-chapter slice of the traced run (385 body paragraphs).
- In the repository: `modules/pipeline/src/test/resources/eval/datasets/panel-en-uk/manifest.json` and `…/panel-ru-uk/manifest.json` with `ownerData: true` and the two file hashes. `DatasetManifest.of(folder, dataRoot)` resolves the listed files under `BOOKLOOM_PANEL_DIR` instead of the `eval` resource folder; a hash that differs stops the run.
- `PT/eval/PlainAsk.java` — the control arm: one user message "Translate the following text from <source> into <target>. Reply with the translation only." plus the paragraph as plain text, at the draft's temperature, through the same `ChatModel`.
- `PT/eval/PanelRunner.java`, `PT/eval/PanelEvalTest.java` (suite `panel`, tag `promptEval`), `PT/eval/PanelRunnerOfflineTest.java` (scripted model, runs in `check`).
- `scripts/eval-score.py blind … --complete @ids.txt` — the complete-sample sheet ("What is counted" below).
- `scripts/eval-score.py panel --key key.json --scores reader1.json [reader2.json …]` — per reader and for the mean: better / same / worse at a 0.25 margin, mean difference with a 95 % bootstrap interval, share scored 2 or less, and the readers' rank agreement. A scores file is `{"reader": "<name>", "scores": {"<caseId>": {"X": 1–5, "Y": 1–5}}}`.
- `docs/evaluation/panel.md` — the procedure and the reader's rubric (1–5; 5 publishable, 2 needs rework).

**Readers.** Reading is not done inside the JVM. A sheet is read by two reader families, the two the review used
(`research/review-digest.md#method-and-limits`): an agent session of a Claude model and an agent session of an OpenAI
model, each given only `docs/evaluation/panel.md` and the sheet, never the key; and by the owner on a 20-paragraph
subsample (`eval-score.py blind … --only @subsample.txt`). The owner starts each reading session, or tells the agent
in the session to start it: a sheet holds real-book paragraphs, and the harness never sends book text anywhere by
itself. Each reader writes one scores file,
`.temporary_context/eval-artifacts/panel/scores/<sheet>-<reader>.json`. A reader that runs on this machine is started
only after the translating model is unloaded (one model resident at a time). Every measuring task names its sheet,
its readers and its scores files in the results page.

**Sample rule.** Body paragraphs of at least 12 words, 50 that hold direct speech and 50 that do not, drawn with the
stored seed; the draw is made once and the ids are frozen in `sample.json`. A measurement that needs paragraphs of one
kind only (the marked paragraphs of D22, the equal-score edits of D11) reads every such paragraph of the whole slice,
not only those among the 100.

**What is counted (the denominator).** `eval-score.py blind` as it is today drops a pair whose two texts are
identical or of which one is missing (`cmd_blind`), so a sheet made that way holds only the changed pairs and a mean
over it overstates a gain: ten paragraphs improved by one point among a hundred are +0.1 over the panel and +1.0 over
the changed-only sheet. Every measurement of this change therefore uses a complete-sample mode, `blind --complete
@ids.txt` (task 1.3), with these rules, the same for the baseline and for every later run:
- The required ids are fixed before the run: the 100 ids of `sample.json` for a panel sheet; for a sheet of one kind
  of paragraph (D11, D22), the list of every such paragraph, written by the run that found them.
- Every required id must be in both runs with an output. A missing case or a missing output stops the command with
  the ids named; the run is repeated, never scored short. The `app` arm always has an output, because it is the text
  of the export: a paragraph the export wrote in the source language is on the sheet as it is and is scored as it
  reads. A plain-ask or merge call that failed is repeated by the runner.
- A pair with identical texts is not shown to the readers; the key records it as a tie, and the scorer counts it as
  "same" with difference 0.
- `eval-score.py panel` refuses a scores file that lacks a score for a pair on the sheet.
- Every mean, every better / same / worse count and every "n paragraphs read" is over all required ids, ties
  included. The result page gives that number beside each figure.
The changed-only behaviour of `blind` without `--complete` stays for the suites that use it today.

**Preparation path.** Until group 6 the `app` arm prepares a book as `BookRunner` does today: it asks for the brief,
accepts it, runs the names pass with the model scan and rechecks the narrator before it starts the job. The baseline
(task 1.7) and the lean run (task 4.9) are both taken that way, so they compare like with like, and the results page
labels them "harness preparation". Task 6.4 deletes those manual steps: `BookRunner` starts the job on a new project
with defaults and the run's own Prepare stage does the work (no model scan), which is the path the window and the
command line take. Task 6.12 measures that path once on the panel against the lean run and labels the difference in
preparation; every later measurement (group 7) uses it.

**Validity gate (task 1.7).** The panel is trusted only if it separates a known-better arm from a known-worse one
outside the noise band: the `app` arm of gemma4:26b against the `app` arm of gemma4:e4b on `panel-en-uk` must differ
by more than 0.3 with both readers ranking them the same way.

**Rejected.** A reader client inside the harness (a new HTTP path and book text leaving the machine without the
person's action). Replaying logged requests (`--suite replay`): a logged request cannot show a change of the request.
Reference translations with an overlap metric: none exist and the review showed overlap does not follow quality.

**Risks.** Readers are a model and one person; the human subsample is 20 paragraphs. The panel is two slices; a change
that only helps other books is not seen.

### D2 — One baseline run, measured by the tool that measures the result

**Decision.** Before any pipeline task of groups 2–7, the English panel slice is run once per model (gemma4:e4b-mlx
and gemma4:26b-mlx, Balanced, TRACE log) with `scripts/eval-matrix.sh --suite book`, and `book-run.json` gains a
`byKind` table (calls, seconds, prompt and output tokens per call kind), the count of segments written in the source
language, and the count of proofread items by mark reason. The same file is produced again by task 4.9. The TRACE
logs of the two runs are kept: they hold the requests of the segments that ended with no translation, which the
replay of D4 needs.

**Why.** The saving this change claims (about a third of run time) is an estimate from one traced run
(`research/review-digest.md#time-breakdown-of-the-traced-run`); it must be compared with a baseline taken by the same
tool on the same slice and machine.

**Reuses.** `PT/eval/StageProbe.java` keeps every call with its request's call kind and usage; `P/CallKindTotals.java`
sums them. `PT/eval/BookRunSummary.java` is the file's record.

**Changes.** `BookRunner` writes one export (the two-export "consistency pass" comparison is dead: export makes no
model call); `BookRunSummary` loses `noConsistencyPass`/`withConsistencyPass` and gains `written`, `byKind`,
`sourceLanguageSegments`, `proofreadReasons`. `PT/eval/StageEvalTest.java` drops `consistency` from its suite matcher.
The suites `cal`, `bare-names` and `proofread-judge` are listed in the header of `scripts/eval-matrix.sh` and in
`docs/evaluation/eval-suites.md`. The baseline figures go to `docs/evaluation/results-finish-release.md#baseline`
(numbers only, no book text).

**Rejected.** Reusing the review's numbers as the baseline: they were taken by hand from a log, on another day.

### D3 — A cut single draft: read it if it is whole, else ask once more

**Decision.** When a single draft's reply ends cut off by length and holds text, the reply is first read by the batch
reader with the runaway recovery of D14; if that gives item 1 a target, the target is used and no call is made.
Otherwise the same request is sent once more with the retry seed and twice the output cap. A second cut reply flags the
segment as today. Batches, repairs and proofread windows get no such retry.

**Why.** 16 of 1,166 drafts were cut on gemma4:e4b; the cut drafts of the traced run were runaway replies, not long
translations, and a cut single draft was flagged with no retry and exported in the source language
(`research/review-digest.md#f3--the-output-cap-cuts-replies-and-a-cut-single-draft-is-not-retried`).

**Reuses.** `ChatRequest.forRetry(int seed, Integer cap)` in `A/llm/ChatRequest.java` (built for the timed-out retry
of `L/GatedChatModel.java`, which lowers the cap; here the cap is raised); `ChatRequests.RETRY_SEED` (8191);
`JobModelCalls.sized` in `P/run/JobModelCalls.java` already limits any cap to half of the window. The one-re-ask
pattern of `DraftReplies.Sent` (`reasked`, `echoAsked`).

**Changes.** `P/DraftReplies.java`: `Sent` gains `cutRetried`; `decideResponse` handles a non-blank reply whose finish
is `LENGTH` before `DraftOutcomes.unfinished`. `P/SegmentTranslator.java` `send` builds the retry request with
`forRetry(RETRY_SEED, 2 × cap)`. The retry is announced as a `StepKind.RE_ASK` step, like the example-copy re-ask.

**Rejected.** A lower cap, as the timeout retry uses: it would cut a long paragraph again. Retrying every cut call:
a cut batch already drafts its lost items alone, and a proofread reply is now tens of tokens (D11).

**Risks.** A paragraph that truly needs more than twice its cap is cut twice and flagged; where the last draft is
built (D4) it then gets one, which fails the same way. Such a paragraph is far above the 1.5× output ratio and was not
seen in the runs.

### D4 — The last draft: one more single draft at the end of Translate

**Decision.** The step is measured before it is built, as the merge of D22 is.

*The replay (task 2.2).* A harness-only arm (`PT/eval/LastDraftReplay`, tagged `promptEval`, no production class)
takes the recorded cases: every segment that ended FLAGGED with no machine target in the two baseline runs of task
1.7 and in the review's traced runs kept under `.temporary_context/REVIEW_2026_11_12/` (their TRACE logs hold each
segment's single-draft request). For each case it sends the request the last draft would send — the logged translate
request for that one item (the JSON batch of one, same system part, same window and name rows) with the last-draft
seed — to the model the case came from, and decides the reply with the production checks and acceptance rule, with no
repair. A case is **recovered** when its reply is accepted, or is a translation by the rule below. The arm also counts,
for the same cases, how many hold a stored refused reply that export already writes (the candidate rule of
`P/export/EffectiveTargets`), so the gain of the extra call over no call is on the page.

*Gate.* The last draft is built only if at least half of the cases are recovered. The counts (cases, accepted, kept
as a translation, not recovered, already written by export) go to
`docs/evaluation/results-finish-release.md#last-draft` with one line, PASS or FAIL.

*If it passes (tasks 2.3–2.5).* When no segment is pending, and before the consistency pass and the final audit,
`ChunkRunner.run` makes one more single draft for each FLAGGED segment with no machine target, no edit, no review by
the person and no last draft yet. The request is the run's own translate request for one item with the new seed
`ChatRequests.LAST_DRAFT_SEED` (16381) — not `RETRY_SEED`, which the cut retry of D3 and the example-copy re-ask
(`SegmentTranslator` `send`) already used on the same request. The draft is decided by the normal checks and
acceptance rule, with no repair. Accepted: the segment is ACCEPTED. Not accepted, but a translation: the text becomes
the FLAGGED segment's machine target. Not a translation: nothing changes. In every case the segment's path becomes
the new `SegmentPath.LAST_DRAFT`, which is the record that the draft was made.

*If it fails.* Nothing of the step is built: no `LastDrafts`, no `SegmentPath.LAST_DRAFT`, no `LAST_DRAFT_SEED`, no
`awaitingLastDraft`. Task 2.2 deletes the replay arm, removes the three last-draft requirements and the last-draft
clauses from this change's deltas, and tasks 2.3–2.5 are closed as "not built: the replay failed". The run then keeps
what D3 and D14 recover, export keeps writing a fit refused reply, and every paragraph that still has no translation
is counted and named before export (D5). That honest count is the outcome of the group in this branch.

**Why.** A finished novel left 24, 5 and 1 paragraphs in the source language; the blocking check on most was the
sentence count, and the fix costs 5–24 calls per novel
(`research/review-digest.md#f12--finished-is-shown-for-a-book-with-untranslated-paragraphs`). A flagged draft that
fails any gate other than quote balance, script purity or the alphabet keeps no machine target today
(`P/heal/MachineTarget.java`, `TOLERATED`), so export writes the source unless the stored refused reply passes every
blocking check. Whether one more draft with another seed does better was never measured: of 20 single re-drafts the
review read, about 6 were better, 9 the same and 5 worse. Hence the replay first.

**Why at the end of Translate, not in Proofread.** The Proofread stage reads only segments that hold a text
(`MarkList.isListed` in `P/proofread/MarkList.java`), and the consistency pass, the final audit and the mark list must
see the text the last draft left.

**Why the JSON batch of one, not a plain-text reply.** The paragraphs concerned were refused for their content, not for
the reply's wrapper. A plain reply needs a second reply contract in the system part, which breaks the byte-identical
prefix every call shares, and a new parser path; it scored no better (26b 3.03 against 3.24, e4b 1.94 against 2.23,
`research/review-digest.md#model-table`). The wrapper's two failure classes are closed by D3 and D14.

**What counts as a translation.** The text restores through the placeholder gate and fails none of
`language-identity`, `refusal`, `locked-term`, `kept-run`, `protocol-leak`, `echo`, `script`, `repetition`. A failed
`sentence-count`, `length`, `vocative`, `number`, `identifier`, `latin-run`, `name-swap`, `name-spelling` or
`glossary` check leaves a translation a reader can read; it stays FLAGGED and is written.

**Reuses.** `SegmentTranslator.translate(segment, context, mask)` and `ChunkDrafting`'s single-draft path
(`draftAlone`), `RoutedCalls` for pause, stop and provider errors, `ChunkDeciding` and `OutcomeRecords` for the
decision and its record, `sinks.pending().flush()` for the one commit; for the replay, the logged-request reader of
the existing `replay` suite (`PT/eval/StageEvalTest`).

**Changes (built only after a PASS).** New `P/run/LastDrafts.java`: it selects the candidates itself from the stored
records, so `P/run/WorkList.java` (400 lines, at the file limit) gains nothing, and runs them as one chunk;
`P/run/ChunkRunner.java` calls it after the loop; `P/heal/MachineTarget.java` gains `ofLastDraft` with the wider rule;
`A/project/SegmentPath.java` gains `LAST_DRAFT`; `P/prompt/ChatRequests.java` gains `LAST_DRAFT_SEED`.
`U/control/DecisionWords.java` shows `LAST_DRAFT` with the words of `DRAFT`.

**Continue (built only after a PASS).** Candidates are Translate work: `ReviewCounts` gains `awaitingLastDraft`
(FLAGGED, no machine target, no user target, not reviewed, path other than `LAST_DRAFT`), and
`U/state/PendingCount.java` adds it to the pending count, so a run stopped inside the pass offers Continue translation
and the next run makes only the drafts still missing (`specs/resume/spec.md`, "Continue the stage that has work").

**Rejected.** Building the step without the replay: "a second draft is clean often enough" was 6 better against 5
worse, a tie. Choosing the better of the refused reply and the last draft: only the last refused reply is stored, in
masked form, and comparing needs a second evaluation path; export's existing rule already writes a fit refused reply
when no machine target exists. A boolean on `SegmentRecord`: the path enum already says how a segment reached its
text, and an exhaustive `switch` finds every place that must handle the new value.

**Risks.** A translation with a missing sentence is written into the book. It is FLAGGED, counted on the Export screen
as "written with its machine translation", and listed on the review desk. The replay re-sends logged requests, so it
measures the seed's effect on the same context and not what a later, better context would give; a real run can only
do the same or better.

### D5 — One number for "no translation", shown before export

**Decision.** "Paragraphs with no translation" is one number, computed by the code that decides what export writes:
`P/export/EffectiveTargets.apply` over the open book and the stored segments. It is the size of its `noTarget` list
plus its `sourceFallbacks` list — the paragraphs export would write in the source language for want of a usable
translation:

| Case | Counted | What export writes |
|---|---|---|
| FLAGGED, no machine target, no edit, no refused reply that restores and passes the blocking checks | yes | the source |
| A stored target (machine or the person's) whose formatting marks cannot be restored | yes | the source |
| FLAGGED, no machine target, a refused reply that restores and passes the blocking checks (`candidates`) | no | that reply |
| FLAGGED with a machine target (a kept last draft included) | no | the machine target |
| Kept as source by the brief's "Also translate" choices; kept verbatim (nothing to translate); a kept foreign passage | no | the source, on purpose |
| PENDING in an unfinished run | no (the partial-book statement names them) | the source |

The Finished title, the finished banner, the run-finished message and one Export warning all show this number.

**Why.** The run state, the title and the banner spoke only of segments to review; the count appeared after the file
was written (`research/review-digest.md#f12--finished-is-shown-for-a-book-with-untranslated-paragraphs`). A count read
off the stored statuses would disagree with the book both ways: export writes a fit refused reply for some FLAGGED
segments with no target, and falls back to the source for a stored target whose marks cannot be restored.

**Reuses.** `EffectiveTargets.apply`, already called by `P/export/ExportJobImpl.writeStored`; the port method
`ExportService.openWork(projectId)` (`P/export/ExportServiceImpl`, `P/export/OpenWork`), which reads the stored
records and the open book before anything is written and already feeds the Export screen's banners through
`U/state/ExportStatement` and `U/state/ExportFigures`.

**Changes.** `A/pipeline/ExportOpenWork` gains `untranslated`. `ExportServiceImpl` and `ExportJobImpl` call
`EffectiveTargets.apply` through one shared private-to-the-package helper (same arguments: the open book, the stored
records, the kept kinds, the document unmask, the two languages), so the count and the write are one code path;
`OpenWork.of` sets `untranslated`. `ReviewCounts.sourceFallback` and `ReviewCounting.isSourceFallback` are deleted:
the one warning replaces `export-banner-source-fallback` in `U/screen/ExportBanners.java`.
`ReviewCounts.flaggedWithoutTarget` stays for the partial-export line (`U/state/PartialExportStatement`) and is
counted only where `userTarget` is null (today an edited segment is counted). `U/state/RunStateInputs.java` gains
`untranslated`, which `U/state/RunStateSource.java` takes from the Export view model's figures
(`ExportFigures.open().untranslated()`); `U/state/ExportViewModel` refreshes its statement when a run ends, when a
review-desk action is stored and when a project opens, and task 2.6 adds whichever of the three is missing.
`U/state/RunStateView.java`, `U/state/RunBanners.java` and the toast use the new keys
`run.title.finishedUntranslated`, `run.banner.finishedUntranslated`, `toast.runFinishedUntranslated`,
`export.banner.noTranslation` in both bundles; `export.banner.source_fallback` is deleted. No new run state:
`RunRow.FINISHED_TO_REVIEW` covers it — a FLAGGED segment is a segment to review, and a stored target that cannot be
restored is listed by the export report.

**Rejected.** A second count over the stored statuses (`flaggedWithoutTarget`) for the banner: it is the approximate
definition that disagreed with export. A seventeenth run state: the toolbar and the chip do not differ. Blocking
Export: a partial book is allowed, never a surprise.

**Tests that hold the rule.** In `:pipeline`: the number `openWork` gives equals the number of paragraphs the export
of the same stored book writes in the source language for want of a translation (`ExportReport`), for a book that
holds one case of every row of the table; a FLAGGED segment whose refused reply is fit is not counted and is written
translated; a stored target with a lost mark is counted and written in the source.

**Risks.** `openWork` now applies the targets of the whole book on each read (unmasking every decided segment). It
runs off the FX thread already; task 2.6 records its time on a 3,700-segment book and, above one second, the read is
made once per stored change instead of once per screen visit.

### D6 — One gender rule: the person or evidence the code verifies

**Decision.** Only what the review measured as harmful is removed: a character's gender is no longer produced from
a pronoun tally, from how the name declines or from a first-name list. Everything else about gender keeps its
behaviour. After this change a character's gender has these sources:

| Source | Origin written | Sent and enforced | Change |
|---|---|---|---|
| The person (typed, imported, confirmed) | `Origin.USER` | yes | none |
| The batched review's cited windows (`TermReviewReplies.matches`: most of the distinct cited windows show the claimed pronoun) | `Origin.BOOK` | yes | the tally can no longer overrule the claim |
| Five or more gendered past-tense verbs right after the name, 90 % agreeing (the existing strong-evidence rule, `PronounGender.STRONG_EVIDENCE`, `STRONG_SHARE`, now counted over the verbs only) | `Origin.BOOK` | yes | pronouns leave the count |
| The readings' verified quotes alone — two or more, 75 % agreeing (three at 80 % under Auto), `ReadingVotes.MIN_VERIFIED_VOTES`, `VERIFIED_SHARE` | `Origin.LIKELY` | no (shown as "likely") | none: this is what verified votes with no clear tally give today |
| The model's answer for the bare name (`NameKnowledgeCalls`) | `Origin.LIKELY` | no | none |
| A pronoun tally; a declension guess; a first-name list | — | — | removed |

No gender is written with `Origin.RULE` any more. Verified votes are **not** promoted to `Origin.BOOK`, and nothing
changes in how the narrator's gender is suggested, stored, shown or used (the "likely" narrator gender,
`Narrator.GenderOrigin.SUGGESTED`, stays as it is); only the question at Start goes (D21).

**Why.** `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`: a tally of 3 "she" to 1 "he" marked a man female
while the model had voted nothing; the value went into 13 requests and raised 2 false notices. The tally reads the
first pronoun after a mention, which often belongs to a neighbour. A row is followed 99 % of the time, so a wrong
value is worse than none. The review's option is "(b) No gender from a pronoun tally alone": it removes a source and
asks for no promotion. The coreference probe measured 10 % wrong votes on the large model and 20 % on the small one,
so votes alone stay a suggestion the person may confirm.

**Reuses.** `P/glossary/QuoteVote`, `ReadingCounts.votes` (verified votes), `PronounReader` and `NameWindows` (the
per-window pronoun the code gives a name: still needed to verify a cited window), `PastTenseReader`, `NameEvidence`
and `PronounTally` (they build the `PronounGender.Evidence` whose verbs and windows are still read), `ClusterGender`
(one gender per person), `P/checks/UkrainianCharacterAgreement` and `UkrainianGenderCheck` (both read only enforced
origins through `Origin.isEnforced()` and `P/context/RunFrames`; they stay unchanged), the "Confirm suggested
genders" button of Names & style (`U/screen/AutoNamesBar`), which is how a likely gender becomes the person's.

**Changes.**
- `P/glossary/ReadingVotes.breakingTies`: the tally branch (`TermReviewReplies.favouredBy`) goes; `majority && enough`
  returns `Origin.LIKELY` as it does today; the one-sided hint (`oneSided`) counts the verified votes only; anything
  else is `NONE` / `NO_EVIDENCE`.
- `P/glossary/TermReviewReplies.genderCall`: the `favouredBy` overrule goes; `favouredBy`, `FAVOURED_MIN`,
  `FAVOURED_PERCENT` are deleted.
- `P/glossary/PronounGender`: `seedHeld` and `seeded` are deleted with `retypes` and `isPersonLike`;
  `Evidence.isStrong` counts `femaleVerbs` and `maleVerbs` only and is read where it is read today, by the review
  (`TermReview` decides such an entry by code and `ReviewCommit` writes `Origin.BOOK`); what `CharacterSheet`, `NameWindows`, `LemmaMentions`,
  `NameClusters` read stays.
- Class verdicts, decided by reading the callers on 2026-11-14: `P/glossary/DeclensionGender` is deleted (its readers
  are the two seeding callers, `ScanProposals` and `PrepStage`); `TitledGender` is deleted (its one reader is
  `PronounGender.seeded`); `PronounTally` and `NameEvidence` stay (they build `Evidence`); `NameTally` stays
  (`FrequencyScan` and `NounUses` read it).
- `P/glossary/GivenNames`: `seedHeld` and `seeded` go; the lists stay for `NameClusters` (joining `Mrs Venn` to a
  full name), `StopWords` and `P/setup/NarratorGenderEvidence`.
- The pronoun tally leaves the `review-terms` and `term-reading` requests (`ReviewBatches`, `TermReading.Subject.tally`,
  the two prompt files' slot). This removes a data slot; no instruction is reworded.
- `P/run/PrepStage.scanIfEmpty`, `P/glossary/ScanProposals`, `ReviewCommit`, `PreScan` line 223: the seeding calls go.
- `A/project/GlossaryEntry`: `genderSeedTried` is deleted (see D9).

**Rejected.**
- Keeping the tally as a tie-breaker: a tally cannot tell whose pronoun it is; that is the defect.
- Promoting verified votes to `Origin.BOOK` so that the default path enforces a gender: a new behaviour with no
  measurement, and the probe's wrong-vote rate argues against it. A character whose gender nothing verifies stays
  "likely" or unknown; the model then reads the gender from the paragraph, as it does for every unlisted name.
- Deleting the `LIKELY` bare-name answer (`NameKnowledgeCalls`): it is never sent or enforced, costs one call per 40
  names and gives the person a starting value; it cannot make a wrong row act.
- Any change to the narrator's gender: the review's narrator finding was the forced question at Start, which D21
  removes; the stored "likely" value is not sent as a fact and was not measured as harmful.

**Risks.** Fewer characters carry an enforced gender on the default path than before (the tally used to verify some).
Task 3.10 counts, on the names gate, the characters with an enforced gender before and after and the wrong ones
among them; the pass rule is 0 wrong enforced genders.

### D7 — The recurring-term list is removed whole

**Decision.** The lexicon goes entirely: the store, the service, the scans, the model calls, the learning, the card,
the CSV, the prompt rows. Nothing replaces it except glossary rows of kind title (D16) and rows the person adds by
hand.

**Why.** `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call` (about 30 of 37 rows were common words; two rows overwritten with wrong values mid-run),
`research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable` (term rows: no visible effect),
`research/review-digest.md#time-breakdown-of-the-traced-run` (204 s, 10 % of the run, 58 of 60 reading calls empty; plus 48 false Proofread
items).

**Goes** (each with its tests):
- `:api`: `LexiconService`, `LexiconRepository`, `LexiconEntry` with `RenderingOrigin` (its only reader),
  `CallKind.TERM_CHOICE`, the term stages of `NamesPassStage` (`TERMS_SCAN`, `TERMS_REVIEW`, `TERMS_READ`,
  `TERMS_TRANSLATE`), the term half of `NamesPassReport`.
- `:persistence`: `InMemoryLexiconRepository`, `SqliteLexiconRepository`, `LexiconDocument`, the lexicon value of
  `DocKind`, the bindings in both modules, the `LexiconDocument` root of `StoredShapes`.
- `P/lexicon/`: `Lexicon`, `LexiconServiceImpl`, `LexiconCsv`, `LexiconFiles`, `LexiconDecisions`,
  `LexiconForms`, `LexiconSuggestions`, `LexiconUsage`, and `FormGroups` (its readers are `KeyTermScan`, deleted
  here, and one Javadoc link in `CooccurrenceLearner`, which is reworded).
- `P/glossary/`: `KeyTermScan`, `LexiconReading`, `LexiconReadingReplies`, `LexiconReadingSchema`, `TermChoice`,
  `TermChoiceSchema`, `StagedLexicon`, the term steps of `NamesPassImpl`, `NamesPassSteps`, `NamesPassStore`,
  `NamesPassEstimate`, `NamesPlan`. `CommonWords` stays: `SuggestTargets` reads it.
- `P/prompt/PromptName.LEXICON_READING`, `TERM_CHOICE`; the files `lexicon-reading.user.prompt`,
  `term-choice.user.prompt`.
- `P/run/`: the lexicon half of `TermLearning` (keep `learnSpellings` and what it needs), `PrepStage.scanKeyTermsIfEmpty`,
  the lexicon of `RunStores`, `ChunkInputs`, `PromptRequests`, `ChapterSummaries`.
- `P/context/`: the term rows of `WindowMap`, `BookMap`, `TranslatePlanner`, `RunFrames`.
- Readers in `P/checks/NameMissingCheck`, `NameSwapCheck`, `P/qa/NameVetoes`, `P/proofread/MarkDetectors`,
  `MarkList`, `ProofreadTallies`, `P/audit/NameVariants`, `P/review/ReviewQueries`, `RetryDraft`,
  `P/export/ExportParts`, `P/project/ProjectCopies`, `P/memory/SummaryGate`, `P/RenderingConsistency`,
  `TranslationJobImpl` (`logConsistency`), `C/TranslateCommand`, `C/RunReport`.
- `:ui`: `RecurringTermsCard`, `RecurringTermsCells`, `RecurringTerms`, `RecurringUsage`, `LexiconActions`,
  `AddRecurringTermDialog`, the lexicon parts of `NamesStyleCards`, `NamesStyleViewModel`, `NamesServices`,
  `GlossaryModelRuns`, `ModelAnswers`, `ChangeLog`, `ChangeRows`, `ChangeReverts`, `ChangeMarks`, `UiModule`, the
  step words of the four term stages (`U/control/StepWords`, `MessageKey.PROGRESS_NAMES_STEP_TERMS_*`), every
  `namesStyle.recurring.*`, `dialog.addRecurring.*` and `progress.names.step.terms*` message key and
  `results.section.terms` in both bundles, the conformance case `#recurring-card`
  (`UT/NamesStyleConformanceCases`), and the test doubles `UT/ScriptedLexiconService` and its bindings in
  `UT/UiTestInjector` and `UT/ReviewPortsModule`.
- Other readers of the removed call kind and stages, found by the compiler: `L/http/RequestTimeouts`,
  `L/profile/ReasoningChoice`, `U/control/CallHeadings`, `U/state/StartEstimates`, `P/RunSummaryLogger`.

**Eval code and documents that name the list — one verdict each** (`grep -rl "Lexicon\|distinctRenderings\|recurring"
modules/pipeline/src/test/java/ua/bookloom/pipeline/eval modules/pipeline/src/test/resources/eval scripts docs`):
- `PT/eval/TermsEvalRunner`: loses its term-choice rows (`TermChoice`) and keeps its glossary-review rows
  (`TermReview`); the suite keeps the name `terms`.
- `PT/eval/NamesRunner`, `NamesScore`, `NamesDiagnostics` with `NamesRunnerTest`, `NamesScoreTest`,
  `NamesWiringTest`: lose the recurring-term columns (`recurringKept`, `recurringDropped`, `recurringJunkKept`,
  `recurringPromoted`, shown as `termsOK` and `promo`); the fixtures `eval/names/*.json` lose their `recurringTerms`
  lists.
- `PT/eval/SequenceEval`, `SequenceRun`, `SequenceMeasure` (400 lines; it only loses lines) with
  `SequenceEvalOfflineTest`: lose the lexicon figures (`learned`, `coverage`, `lexiconDistinct`, `termClaimedWrong`)
  and keep the renderings per fixture term, which are counted from the targets.
- `PT/eval/EvalProject`, `EvalBook`: stop loading recurring terms into a store; a fixture's `terms` list stays as the
  data the sequence measure counts renderings for.
- `eval/baseline/sequence-*.json`: the removed fields are deleted from the stored baselines, the other values
  untouched; `eval/realrun/batch.json` loses `lexicon` and `expectTerms`.
- `scripts/eval-matrix.sh`: the `terms` suite's header text (lines 47–49, 63–68) and the two print lines (346–363)
  lose the recurring-term words and columns.
- `docs/evaluation/eval-suites.md`, `docs/architecture/overview.md`, `docs/development/running.md`,
  `docs/development/testing.md`, `docs/pipeline/context-glossary-memory.md`, `docs/pipeline/translation-pipeline.md`:
  corrected to the state without the list. `docs/evaluation/results-prompt-rounds.md`, `results-quality-rounds.md`
  and `results-redesign-arms.md` are records of past runs and are not edited.

**Stays.** In `P/lexicon/`: `CooccurrenceLearner` (the engine behind a name's most used spelling) with the three
classes it compiles against, `Vocabulary`, `TrackedTerm` and `BaseForms`; `AlternationGuard` with
`TermMappingVerifier`, which it calls; `TermMatch`; `TargetWords`. They are used by the name checks, the per-chunk
spelling hint and the consistency pass, and keep their package: moving them is churn with no behaviour. The key
function `LexiconEntry.keyOf` (compose, strip, lower-case) has callers that stay — `CooccurrenceLearner`,
`TermMappingVerifier`, `P/run/TermLearning`, `P/context/WindowMap`, `P/glossary/NamesPassImpl`, `NamesPassStore` —
so it moves, unchanged, to `TermMatch.keyOf` before `LexiconEntry` is deleted (it is not the fold of
`P/index/Occurrences.keyOf`, which also maps apostrophes). `TermLearning.learnSpellings` keeps
`CooccurrenceLearner.Learned`; its use of `LexiconEntry.Learned` goes with the lexicon half. `TERM_READING` and
`TERM_SUMMARY` stay: they read names (21 calls, 140 s in the traced run).

**Rejected.** Keeping the list for words that are not common vocabulary: the app carries no frequency reference to
tell them apart (the `KeyTermScan` Javadoc says so), and the person's hand-added word works as a glossary row.

**Risks.** A wide deletion (about 80 main files and 98 test files name the lexicon). Tasks 3.4–3.8 cut it in an
order that keeps every module compiling after each task: stop the writers (with the window's step list), remove the
readers in the run, remove the window's card and view-model parts, remove the service and its `:api` contract, then
the store.

### D8 — Names are not refused for their letters or rewritten from other names

**Decision.** `SuggestionReplies.Standing.LETTERS` and the opening-letter judgement of
`P/glossary/TransliterationCheck` (`Problem.OPENING`) are deleted; `TargetValidator` asks the check only for a lost
word. `RefusedAgain.settle` (which existed to rescue a place refused twice for its letters) is deleted and
`SuggestTargets` keeps the retry's own kept suggestions; `warnLeftWithoutTarget` stays where it is and the class is
renamed `LeftWithoutTarget`, because `SuggestTargets` is 399 lines and cannot take it. `ClusterAgreement.withSharedWords`, `sharedWordsOf`, `learnWords` and `heldWords` are deleted;
`align` returns the aligned list. The refusals that remain are those code can prove: `COPY`, `LOST_WORD`,
`ALPHABET`, `OBLIQUE`, `IDENTIFIER`.

**Why.** `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`: the model's translated renderings of a place and of a speaking nickname were refused with "it does
not begin the way the name sounds", then the shared-word rule built a hybrid; the two rows reached 28 of 385
paragraphs (7 %). The name-translation request works when its inputs are right.

**Reuses.** `ClusterAgreement.align` for one person's spellings (full name and short name) — kept.
**Rejected.** A list of words exempt from the letter rule: it is the rule, not its exceptions, that is wrong for a
name with a meaning. **Risks.** A mis-sounded name (`Карло` for `Arlo`) is no longer caught; the book-agreement pin
(D17) and the person's table are the remedy, and the names gate of task 3.10 counts such cases: its pass rule is that
the rows a reader calls wrong are no more than before the change.

### D9 — Stored shapes that change

The database is set aside and started fresh (ADR-0043); no migration. `S/sqlite/StoredShapes` fingerprints the
roots, so every change below alters the fingerprint by itself. `db/schema.sql` is not edited by this change and its
`PRAGMA user_version` stays 2: the recurring terms and the chapter summaries have no table of their own — both are
rows of `project_docs`, told apart by `S/sqlite/DocKind` (`LEXICON`, `CHAPTER_SUMMARY`) — so removing them removes a
`DocKind` value and a root, not a table.

| Root (`StoredShapes.ROOTS`) | Change | Decision |
|---|---|---|
| `LexiconDocument` | root removed; `DocKind.LEXICON` removed | D7 |
| `ChapterSummary` | root removed; `DocKind.CHAPTER_SUMMARY`, `SummaryRepository` and both adapters removed | D13 |
| `GlossaryDocument` → `GlossaryEntry` | `genderSeedTried` and `meaningChoice` removed | D6, D21 |
| `Project` | gains `PrepareState prepare` (`briefDone`, `namesDone`) | D20 |
| `Project` → `BookBrief` | gains `dialByModel` | D21 |
| `RunRecord` | gains `RenderingReport` (null until a run's Proofread stage ended) | D19 |
| `ModelSettings` (measured seconds per `CallKind`) | `CallKind.TERM_CHOICE` removed; `MERGE` added only if 7.1 passes | D7, D22 |
| settings table (not a shape) | `brief.lastTarget` added; `run.defaultDial` absent by default | D21 |
| `Project` → `BookBrief` | `apostrophe`, `quoteStyle`, `defaultAddress`, `conventions` removed with `Address`, `AddressForm`, `QuoteStyle` | D26 |
| `Project` → `BookBrief`, `BriefSuggestion` | `storyShape`, `chapterSummaries` removed with `StoryShape` | D13 |
| `SegmentRecord` → `SegmentPath` | gains `LAST_DRAFT` only if the replay of D4 passes | D4 |
| `SegmentRecord` → `ContextSnapshot` | the summary lines removed | D13 |
| `ModelSettings` (figures and refusals per `CallKind`) | the chapter-summary kind removed | D13 |

### D10 — The Proofread stage reads only reader-visible marks

**Decision.** A segment is listed for the Proofread stage only for: FLAGGED with a text; a REPAIR-level finding the
one repair left; an arrived fact its text disagrees with; an audit doubt or a NOTE finding of a check; no form of a
pinned or locked name its source holds (a title row is not such a name); a word against an enforced gender. Not for: the LOW `normalised` finding
(typography and quote repairs code already made), the LOW `placeholder` finding ("Markup auto-restored"), a recurring
term written otherwise.

**Why.** Of 129 items sent in the traced run, 59 were sent only because code had normalised an apostrophe or an
ellipsis and 48 for a common word off a list; 46 of 67 windows held nothing else and cost 242 s of the stage's 382 s
(`research/review-digest.md#time-breakdown-of-the-traced-run`,
`research/review-digest.md#f2--almost-nothing-improves-a-poor-first-draft`).

**Changes.** `P/proofread/MarkList.java` `doubtful` skips a LOW finding raised by `CheckName.TYPOGRAPHY` or
`CheckName.PLACEHOLDER`; `P/proofread/MarkDetectors.java` loses whatever task 3.5 left of `offRendering`, `keptRendering`, `namesOf` and the
lexicon half of `MapNow`, and its missing-name-form detector skips title rows. `MarkList.openMarks` follows, so the Export screen's "still carry a proofread mark" count
and the review desk fall with it.

**Rejected.** Dropping the "name form missing" mark for every row: the mark finds a wrong name. Its 17 false items of
the traced run were all the titles `mr`/`mrs`, and a translation may rightly drop a title, so the mark skips title rows
(`StopWords.isTitle`, moved there by task 3.5); a title's other rendering is put right by the consistency pass (D16,
D17). Fast as the default dial for every model: the default follows the model's size instead (D21).

**Risks.** A typography repair that changed meaning is no longer re-read. The normaliser changes punctuation and case
only (`research/review-digest.md#what-the-evidence-supports-keeping`).

### D11 — A proofread reply is a list of edits, each judged alone

**Decision.** The reply is `{"edits":[{"id","find","replace"}]}`; `{"edits":[]}` means nothing to change. An edit is
applied only when `id` is a focus id and `find` occurs exactly once in the segment's current masked text. Edits of one
segment are applied in order, each judged by `ProofreadJudge` against the text the kept edits left. At an equal score
a candidate within the rewrite cap is kept for a marked paragraph too (the equal-score rule; it stays only if the
measurement below keeps it).

**What kind of change this is.** It changes the form of the proofread reply to save time (finding F13: the echo and
the wrapper cost about 100 s and three windows were lost at the cap). It is not a rewording of the translate prompt
for quality, which the Non-Goals exclude: the system part and the draft and repair requests are untouched, and of
`proofread.user.prompt` only the two lines that state the reply's shape change. Because it is a change of form with
its own effect, it is measured as its own step, apart from the narrower mark list of D10 (D15).

**The equal-score rule's test.** Task 4.9 reads blind, on the panel slices, every proofread edit that was kept at an
equal score (before against after). The rule stays only if the edits judged better outnumber those judged worse. A
tie, or no such edit at all, removes the rule: `NeverWorse.byScore` goes back to "cleared or lower" for a marked
paragraph, with its tests and the `quality-gates` requirement's equal-score clause.

**Why.** The model never answered "unchanged" (0 of 67), wrote 58 paragraphs back identical, 3 windows were lost whole
at the cap, and the wrapper and echo cost about 100 s; 53 of 60 refusals were "no gain", and 3 of 5 compared by hand
refused a real fix (`research/review-digest.md#f13--about-40--of-the-run-time-buys-nothing-in-the-exported-book`,
`research/review-digest.md#f2--almost-nothing-improves-a-poor-first-draft`).

**Reuses.** `P/proofread/ProofreadJudge.java`, `P/proofread/WindowVerdicts.java`, `P/proofread/RevisionWriter.java`
and `P/qa/NeverWorse.java` unchanged in what they judge: they still get a current text and a candidate text.
`TextGuards.REWRITE_CAP` and `isSmallRewrite` in `NeverWorse` (today used only for Max's unmarked paragraphs).
`JsonReplies.tolerant` for reading. No edit-verifying code of the old reviewer is left in the tree; nothing is revived.

**Changes.** `P/prompt/ProofreadSchema.java` (`edits` required); the two reply lines of
`modules/pipeline/src/main/resources/ua/bookloom/pipeline/prompt/proofread.user.prompt`; `P/proofread/ProofreadCall.java`
(`Answer.changes` becomes the ordered edits per id; applying is a new package-private `P/proofread/EditApplier.java`);
`P/proofread/WindowReader.java` loops over a segment's edits; `P/qa/NeverWorse.java` `byScore` uses `isSmallRewrite`
for every focus; `L/pseudo/PseudoChatModel.java` answers `{"edits":[]}`; the proofread prompt's version in
`P/proofread/WindowFingerprint.java` rises, so stored verdicts of the old shape are read again.
The output cap stays as it is (`OutputLimit.forBatch` over the items read): it is now an upper bound never reached.

**Rejected.** Keeping `{"unchanged":true}` beside the list: two ways to say one thing, and the model never used it.
Short ids in the reply (est. −22 s): an edit list names an id only for a change. Salvaging edits from a cut reply: a
reply of tens of tokens is not cut.

**Risks.** A small model may copy `find` inexactly; the edit is dropped, which is today's outcome for a refused
rewrite. The equal-score rule can keep a neutral or bad edit; its test above decides whether it ships.

### D12 — What a translate request carries

**Decision.** A draft, repair and proofread request keeps: the fixed system part, the glossary rows the window names
(pinned, locked, suggested), the previous accepted pairs, the translation-memory lines, the next source, the items,
and a reminder of at most two lines (formatting marks, narrator). It loses: the `[Earlier chapters]` block, the
`[Recently present]` card, the `[Established renderings of recurring terms]` rows, and the reminder's dialogue line.

**Why.** Full context against name rows only: +0.14 on 26b, −0.01 on e4b, inside the 0.3 band; name rows make the
model use the given rendering in 60–62 of 62 cases against 50–52; the card's names were used 2 times of 284; the
dialogue reminder applied to 0 of 405 items; removing the blocks saves 10 % of draft time on e4b and up to 24 % on
26b (`research/review-digest.md#context-block-replay`,
`research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`). Previous pairs and the
next source stay: cheap, and not measured as harmful.

**Changes.** Deleted: `P/context/EarlierChapters.java`, `P/context/EarlierSummary.java`,
`P/context/RecentlyPresentCard.java`, `P/context/LexiconFilter.java`, the slot `BlockSlots.SCENE`, the keys
`earlier.*`, `card.*` and `map.lexicon` of `blocks.properties`, the lexicon section of `P/context/MapLines.java` and
`P/context/WindowBlocks.java`, the dialogue line of `P/context/Reminder.java` and its wording in the language files
(`reminders().dialogue()` of `P/context/RunFrame.java`), the summary share of `P/context/WindowBudgets.java` and
`P/context/ContextBudget.java`. `P/context/ContextBuilder.java` plans twelve blocks. The book rule line about
dialogue in the system part (`label.dialogue`) stays: it was not measured.

**Rejected.** Dropping previous pairs and the next source too: a judgment call with no measurement behind it. Sending
name rows only for names in the items (38 % of rows name only a neighbour): a rewording of the window rule with no
measured gain.

**Risks.** The measurement is one English slice on e4b and 26b; task 4.9 repeats it on both panel slices with the
win rule of the review's experiment E1: panel score within 0.15 of the baseline and no rise in name-form variants,
narrator-gender slips or flagged segments.

### D13 — Chapter summaries are removed, with their switch and the story shape

**Decision.** No chapter-summary call is made and no summary is stored, shown or logged. The Book Brief loses the
"Chapter summaries" switch and the "Story shape" field; the brief suggestion no longer asks for a story shape.

**Why.** 27 calls, 137 s, 6.8 % of the traced run (4.6 % of a whole book), 13 of them re-asks; the gate kept 36 of 81
sentences and what is stored is partly wrong; nothing but the removed `[Earlier chapters]` block reads a summary; the
story shape only sets the switch's default (`BookBrief.summariesOn`, `A/project/BookBrief.java`)
(`research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`).

**Changes (deleted).** Pipeline: `P/memory/ChapterSummaryCall.java`, `P/memory/SummaryGate.java`,
`P/memory/SummaryVocabulary.java`, `P/memory/ChapterText.java`, `P/run/ChapterSummaries.java`,
`P/run/SummariesSwitch.java`, `P/proofread/SummarySweep.java`, `P/prompt/ChapterSummarySchema.java`,
`chapter-summary.user.prompt`, `languages/summary-words/`, `OutputLimit.forChapterSummary`,
`PromptName.CHAPTER_SUMMARY`, `P/setup/StoryShapeDecision.java` and the `storyShape` field of
`brief-synthesis.user.prompt` and `P/setup/BriefSynthesisSchema.java`, the summary steps of the step plan, the
chapter standings that exist only for summaries in `P/project/ChapterStandings.java` and `ChapterTallies.java`.
API: `A/persistence/SummaryRepository.java`, `A/project/ChapterSummary.java`, `A/project/StoryShape.java`,
`A/pipeline/ChapterSummaryKept.java`, the summary variant of `A/pipeline/MemoryUpdated.java`, `CallKind`'s
chapter-summary constant, the `storyShape` and `chapterSummaries` components of `BookBrief` and `BriefSuggestion`, the
summary lines of `A/project/ContextSnapshot.java`. Persistence: `InMemorySummaryRepository`, `SqliteSummaryRepository`,
`DocKind.CHAPTER_SUMMARY` and the `ChapterSummary` root of `StoredShapes` (the summaries are rows of `project_docs`;
the schema file is not edited, D9). UI: `U/screen/ChapterSummariesSwitch.java`,
`U/screen/BriefStoryShapeField.java`, `U/state/BriefSummaries.java`, the story-shape parts of
`U/state/BriefDecisions.java`, `U/state/SuggestionMerge.java`, `U/state/StyleSuggestion.java` and
`U/screen/BriefToneCard.java`, the message keys `brief.tone.storyShape*`, `brief.decides.storyShape` and the
chapter-summaries keys in both bundles, plus the `sum` activity kind (`U/state/ActivityLogFeed.java`), the live card's
summary row (`U/state/LiveRow.java`, `U/state/ChunkRows.java`) and their message keys. Evals: `EvalChapterSummaries`,
`SummaryGateTally`, `ChapterSummaryAcceptanceTest`, the `summaryGate` field of the mini and sequence reports, the
`--summaries` flag of `scripts/eval-matrix.sh`. Other readers of the removed call kind, step kind and event, found by
the compiler: `L/http/RequestTimeouts`, `L/profile/ReasoningChoice`, `U/control/CallHeadings`,
`U/state/StartEstimates`, `U/state/LiveCallState`, `U/state/RunSession` (397 lines; it only loses lines),
`U/state/LogKind`, `P/RunSummaryLogger`, `P/context/FrameKind`, `P/run/RunSteps`. Chapter boundaries stay (`P/project/ChapterBoundaries.java`): a batch
still ends at a chapter.

**Rejected.** Summaries off by default with the code kept: a switch for a feature with no measured effect and a gate
that stores wrong text is dead weight the next change has to carry. Keeping the story shape "for later": nothing
reads it.

**Risks.** A wide deletion across five modules; the stored-shape fingerprint changes, which this change already does
(ADR-0043). Requirements in `app-shell`, `book-brief`, `local-storage`, `inference` and `review-queue` name
summaries; their deltas are in `specs/`.

### D14 — The runaway reply is recovered in the parser

**Decision.** Keep the enforced JSON reply. `RawEntries.find` accepts, as the end of an entry, a closing quote
followed only by white space up to the end of the text. The brackets the model wrote inside the string are then cut
by the existing `ReplyClosers.strip`.

**Why.** All runaway replies have one shape: the last item ends in `?»` or `!»`, the model writes `}]}` inside the
string, closes the string and fills the rest with white space; the entry is complete, and the finder drops it only
because no `,` or `}` follows the quote. It recovers 3 of 3 with no call
(`research/review-digest.md#f4--replies-run-away-and-the-cause-is-known`).

**Why not numbered lines.** Numbered lines had 0 runaways and 0 lost items in 198 + 28 calls and use 5–8 % fewer
output tokens, and quality is equal across formats (`research/review-digest.md#reply-format-and-runaway-replay`).
But they replace the reply contract of every translate-family call: the reply sentence of the system part and the
task block (prompt wording, which the owner excluded), `BatchSchema`, `BatchReplyParser`, `BatchEntries`,
`TranslateDraftPrompts.envelopeOf`, the format re-ask, the structured-answer rungs in `:llm`, the pseudo model, every
golden prompt and every scripted eval reply; a paragraph with a line break inside it breaks the format. The recovery
is one pattern in one class, and the residual cost of a runaway in the app is a few seconds of white space up to a
cap of about 250 tokens.

**Reuses.** `P/batch/RawEntries.java` (pattern `CLOSE`), `P/batch/BatchEntries.java` `salvaged`,
`P/checks/ReplyClosers.java` through `P/batch/ItemValidator.java`, `BatchDrafter.outcomeOf` (a cut reply with no
missing id is a clean reply).

**Changes.** `RawEntries.CLOSE` becomes `"\s*(?:[,}]|\z)`. `P/DraftReplies.java` reads a cut single draft through
`prompts.parse` before the retry of D3; for that, `TranslateDraftPrompts.envelopeOf` (`P/TranslateDraftPrompts.java`)
passes a reply that starts with `{` and is not valid JSON as a whole on to the batch reader when the call's finish is
`LENGTH`, instead of answering "was not valid JSON".

**Rejected.** A stop on repeated white space in the stream reader of `:llm`: LM Studio's path does not stream, the
interplay with Ollama's `format` is unknown, and the item is already saved. Unenforced JSON: the worst arm (36 items
lost). Sampler penalties: the cause is the grammar, not repetition.

**Risks.** A reply cut exactly after a closing quote that was not the end of the paragraph would be read as complete.
A JSON string can only be closed by the model, so the text before the quote is what the model chose to end with; the
item still passes the length band and the sentence-count check.

### D15 — The saving is confirmed on a run

**Decision.** The lean run is measured in two steps on the tool and slice of the baseline (D2), on the same machine,
idle and cool, and recorded in `docs/evaluation/results-finish-release.md#lean-run`.

- **Step 1 — the mark list alone (task 4.3).** Taken when only D14 and D10 are built: the old reply form, the narrower
  mark list. It records proofread items listed, windows read, proofread calls, seconds and output tokens, and the
  kept changes.
- **Step 2 — the whole lean run (task 4.9).** Taken after D11, D12 and D13 are built. Against step 1 it shows what the
  edit reply alone changed (proofread output tokens per item, windows lost at the cap, changes kept and refused);
  against the baseline it shows the whole saving, and it repeats the panel of D1.

**Expected (estimates from one traced run).** No chapter-summary calls (−137 s of 2,018 s); Proofread windows with no
reader-visible mark gone (−242 s, step 1) and proofread output at least 5 times smaller per item (step 2); no re-draft
after a runaway reply (−28 s); draft prompt time down by the removed blocks. Together about a quarter of the model
time of the slice from this part alone, and about a third together with group 3's removal of the common-word term
calls, which is built before this run (`research/review-digest.md#time-breakdown-of-the-traced-run`).

**Pass rule.** Step 1: 0 proofread items listed for a `normalised`, auto-restored or recurring-term reason. Step 2:
model seconds of the slice at Balanced at least 20 % below the baseline on both models; 0 chapter-summary calls; 0
`length` finishes that end as a flag after a runaway shape; proofread output tokens per listed item at least 5 times
below step 1; no window lost at the cap; the kept proofread changes of step 2 are no fewer than those of step 1 on
the items both steps listed; where the last draft is built (D4), 0 FLAGGED segments with no translation that never had
a last draft; panel `app` arm within 0.15 of the baseline `app` arm on both slices. A missed time figure is reported
with the measured value, not tuned around. A missed quality figure for D12 is reported to the owner with the figures,
and a follow-up change puts the removed blocks back one at a time on the panel. A missed figure for the edit reply
(output not 5 times smaller, a window lost, or fewer kept changes than step 1) is reported to the owner with both
steps' figures before group 5 starts. The equal-score rule is decided by its own test (D11): better must outnumber
worse; a tie or no case removes it.

### D16 — Titles are glossary rows

**Decision.** A title is a glossary row of `TermType.TITLE`, lower case, no gender, found by the deterministic scan
from the source language's bundled list (`P/…/resources/…/glossary/titles/<lang>.txt`, read by `StopWords.titles`)
at 3 uses or more (`KeyTermScan.TITLE_MIN_COUNT` moves with the code). Its target is the pair file's
`titleRenderings` (`prompt/languages/pairs/en-uk.properties`), **pinned** with `Origin.RULE`; with no pair data the
target is empty and D17 fills it from the book. `KeyTermScan.isTitle` moves to `StopWords.isTitle`; the name
checks keep skipping titles while drafts are decided (`NameLossCheck`, `NameSwapCheck`, `VocativeCheck`).

**Why.** `research/review-digest.md#consistency-numbers`: Mr 83/12, Mrs 75/30, sir 49/44 on the small model; locked title rows were sent in 0
of 14 requests because `P/context/WindowMap.lockedLines` lists a locked row only when its word is behind a token.
A pinned row is listed by its source word and may decline.

**Reuses.** The existing title list and pair data; `P/prompt/TitleRenderings` (the reader of `titleRenderings`:
`forPair(source, target).render(term)`); `P/glossary/SettledSuggestions`, which already gives every glossary row of
`TermType.TITLE` the pair's rendering with no model call and is called by `SuggestTargets` — unchanged, so a title row
the person adds by hand is rendered the same way; `FrequencyScan`'s reads; the pinned-row path of `WindowMap`.
**Changes.** New `P/glossary/TitleScan` (the title half of `KeyTermScan`; it asks `TitleRenderings` for the target at
scan time and pins it), called from two of the three public entry points of `FrequencyScan` — `newTerms` (the run's
Prepare, called by `PrepStage`) and `proposals` (the Scan button, called by `GlossaryServiceImpl`) — so `PrepStage` and
`GlossaryServiceImpl` (396 lines) gain no line. The third, `found` (the per-unit path of `P/run/DecisionFollowUp`),
proposes no title: both other paths count over the whole book's index, so a title that reaches three uses only in a
late chapter is already found at Prepare. Nothing moves into
`SuggestTargets`.
**Rejected.** A language-neutral "word before a name" rule: capitalised words before names include `Old`, `Poor`,
`Dear`; untested, and the list is already data any language may add. Blocking a draft that writes another title
rendering: «містер» and «пан» are both valid Ukrainian; the fix is the code sweep, not a repair call.
**Risks.** A title that is also a name part (`Major Tom` the song) — the scan's existing removal memory and the
person's delete cover it.

### D17 — The consistency pass: agreement, pinning and the sweep

**Decision.** A consistency pass runs when the Translate stage has ended, at every dial, with no model call. It is
built from named parts, each with one job, so no class does five things:

| Part | Job |
|---|---|
| `P/proofread/RenderingTally` (new) | the one counting rule: per row, the decided body paragraphs that name it and the count of each rendering found in their stored targets |
| `P/proofread/RenderingAgreement` (new, pure) | from a tally: which rows agree, which are pinned or filled, which paragraphs hold a minority rendering |
| `P/proofread/TermSweep`, `CodeSweep` (existing) | replace a minority rendering form by form and store through `RevisionWriter` |
| `P/proofread/DialogueSweep` (new) over `P/typography/DialogueStyle` (new, pure) | one dialogue style per book (D18) |
| `P/proofread/ConsistencyPass` (new) | the orchestrator only: calls the parts in order, honours a stop between them, returns the report |

**Where it runs.** In `P/proofread/ProofreadStage.run`, after the flush of the Translate stage and before
`RunAudit.run`: flush → `ConsistencyPass` → flush → `RunAudit.run` → `StageStarted(PROOFREAD)` → `codePart`. The
audit lists second spellings as doubts (`P/audit/NameVariants`), so it must read the text the pass left. The term
sweep that `codePart` runs today moves into the pass and runs once: it then carries out the deferrals the person's
glossary edits recorded and the ones the pass registers. The pass's time belongs to the end of the Translate stage,
as the audit's does, and it raises no new kind of event. A stop is honoured between its steps, as `codePart` does
with `isStopped`. A storage error fails the run, as it does in every neighbouring step. A continued run runs the
pass again; with every row already agreed and swept it changes nothing.

**Steps.**
1. **Tally.** For each character, place or title row, over the decided body paragraphs whose source names the row
   (`TermMatch.occursIn`), group the renderings found in the target: for a row with a target, its forms
   (`PinnedForms`) and its near spellings. `NameSpelling.fold` (as `P/audit/NameVariants` uses it) only *discovers*
   which target words belong to the row: it collapses doubled letters and the language's voicing pairs (Ukrainian
   `зс`), so `Бренз` and `Бренс` fold to the same letters and are both found for the row `Brenz`. It never merges
   them: each distinct base spelling is its own rendering group, and only the inflected forms of one base spelling
   (`Бренз`, `Бренза`, `Брензу`) are counted together. These groups are the one input of everything after — the
   agreement of step 2, the choice of the minority of step 3, the sweep, and the report's state (D19); for a one-word row
   without one, the candidates of `CooccurrenceLearner` — a new method `candidates(term)` returns every rendering
   candidate the learner holds for a tracked name or title with its support, of which the existing `established` is
   the gated leader. The learner only proposes which words to count; the count is the tally's, the same for both
   kinds of row: the paragraphs that name the row and hold a form of the rendering.
2. **Agree (the pinning rule).** "Agrees" here is the rule for pinning and filling only; what the report calls
   "agreed" is a different, stricter thing (D19). A row agrees when it is named in ≥ 5 paragraphs and one rendering holds ≥ 80 % of those that hold any.
   A suggested row that agrees becomes pinned with `Origin.BOOK`, its target the agreed rendering (also when that
   differs from the suggestion). A one-word row with no target named in ≥ 10 paragraphs gets the leading rendering
   (the one in most paragraphs): pinned when it agrees, suggested otherwise — 15 against 11 over 26 paragraphs gives a
   suggestion, not a pin. Rows the person pinned or locked are never changed.
3. **Sweep.** For each paragraph that holds a minority rendering of a pinned row, register a `DeferralReason.TERM`
   deferral whose previous rendering is the minority word (`DeferralRegister`), then run the term sweep: it replaces
   form by form through `PinnedForms.renamings`, restores through the document gate, stores through `RevisionWriter`
   (a person-edited paragraph gets a proposal). `TermSweep.notSwept` needs no change: the rows are pinned by then.
4. Dialogue style (D18), then the report, counted again from the stored texts (D19).

**Why.** `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `research/review-digest.md#consistency-numbers`. Right rows sit at 98–100 %; the undecided ones at 52–58 % and 75 %; 85 %
and 87 % are the cases a reader would call decided. 80 % separates them. Five paragraphs is one more than the
names scan's own floor of 3 plus a rival. Ten uses for an empty row is the review's option (d).
**Confirmed by** task 5.8 (the report on two whole books; wrong pins counted by reading).

**Reuses.** `TermSweep`, `CodeSweep`, `PinnedForms`, `RevisionWriter`, `DeferralRegister`, `NameSpelling`,
`CooccurrenceLearner`, `TermMatch`, the atomic `GlossaryRepository.update`. `TermLearning.learnSpellings` (the
per-chunk first-spelling hint) stays as it is: it gives later chunks a hint during the run; the pass decides after.
**Rejected.** One class for the whole pass: tally, agreement, sweep, dialogue and report are five jobs and would pass
the 400-line file limit. Running the pass before the audit and leaving `codePart`'s sweep in place: the sweep would
run twice. Pinning during the run: agreement cannot be judged on the first chapters, and a mid-run pin would
block drafts on a spelling the book may yet abandon. Replacing a minority rendering of a term, material or product
row: common nouns agree with adjectives and verbs, so a word swap can break grammar; those rows are only reported.
One more suggestion call for an empty row (review option d): it costs a call; the book's own spelling costs none.
ти/ви tracking for English sources: an untested bet (`research/review-digest.md#owner-decisions`); not built.
**Risks.** A wrong majority is made uniform (the report shows every pinned row with its counts, and the person's
target wins on the next run or retry). A form table that mis-declines a rendering: the gate and `RevisionWriter`
keep the old text when the swap fails.

### D18 — One dialogue style per book

**Decision.** `P/typography/DialogueStyle` (new, pure functions) classifies a target paragraph as dash speech
(starts with `— `), quoted speech (holds a primary quote pair of `P/checks/QuoteConventions` and its source holds
quoted speech or a leading dash) or none, and converts between the two for four shapes only: one speech; speech +
attribution; speech split by its attribution; the hybrid `«— …»`. `P/proofread/DialogueSweep` (new; the part of the
consistency pass that stores, D17) counts the two styles over the decided body paragraphs, takes the larger (on a tie or under 10 speech paragraphs: dash where
`typography-rules.properties` sets `dialogue-dash`, quotes elsewhere), converts the other style's paragraphs and
stores each through `RevisionWriter` with a new `RevisionWriter.Change.DIALOGUE_STYLE`, only when (a) the sequence
of letters, digits and `⟦gN⟧` tokens is identical before and after, (b) `QuoteBalanceCheck` raises nothing new, and
(c) no person edited the paragraph. The per-draft `TypographyNormalizer` is unchanged ("a dash dialogue stays a dash
dialogue" stays true for a draft).

**Why.** `research/review-digest.md#consistency-numbers`: dash 897, quotes 529, 161 hybrids, both inside 27 of 39 chapters, in one book.
**Reuses.** `QuoteConventions`, `QuoteBalanceCheck`, `DialogueDash` (dash spacing), `RevisionWriter`.
**Rejected.** A stored brief option for the style: the window is the same for every language (`research/review-digest.md#owner-decisions`).
A prompt line asking for one style: the dialogue reminder is being removed as irrelevant, and prompts are not
reworded. Converting every shape: narration around speech needs to know where speech ends; guessing would touch
words. **Risks.** Dash → quotes is ambiguous when an attribution could be speech; the four shapes and the
letter-equality check bound it, and what is left is counted in the report. Task 5.8 reads 40 converted paragraphs.

### D19 — The rendering report

**Decision.** `P/RenderingConsistency` (a figure over the lexicon) is deleted with the lexicon and replaced by
`A/pipeline/RenderingReport` (record: rows `{term, kind, paragraphs, renderings[{text,count}], state AGREED|SPLIT|NONE}`,
`dashParagraphs`, `quoteParagraphs`, `fixedParagraphs`, `notFixedParagraphs`), built by `RenderingTally.report()` for
every row named in ≥ 3 decided body paragraphs.

**A row's state in the report is not the pinning rule.** D17 pins a row at 80 % of at least 5 paragraphs. The report
describes what a reader would see: a row is `SPLIT` while the stored texts hold more than one rendering group for
it, `AGREED` when they hold exactly one, `NONE` when they hold none. A rendering group is the tally's group (D17, step 1): one base spelling with its
inflected forms (`PinnedForms`). Two base spellings that the fold makes equal — `Бренз` and `Бренс` — are two groups,
so a `Бренс` the sweep could not replace keeps the row `SPLIT`. So 19 `Гейл` and 1 `Хейл` pins the name and, if the sweep could not replace the one paragraph or a person
edits it back, still reads `SPLIT` with "19 · 1"; the summary's "the same all through" counts `AGREED` rows only.

**Freshness.** A report is always a recount of the stored texts at a stated moment; the tally the pass took before
its sweep is never reused for it.

| Moment | Who counts | Where it goes |
|---|---|---|
| The consistency pass ends (after its sweep and its dialogue rewrite) | `ConsistencyPass`, a second `RenderingTally` | the pass's INFO line and its result |
| The Proofread stage ends (after its last stored change: a proofread edit, a merge) | `ProofreadStage`, a `RenderingTally` | `RunRecord.renderingReport` and `JobReport`; this is the run's snapshot, printed by `C/RunReport` and read by the eval harness as its names metric |
| The Export screen reads its figures (a project opens, a run ends, a review-desk action is stored, a book is exported) | `ExportService.openWork`, a `RenderingTally` over the stored records | `ExportOpenWork.renderings`, shown in the fold; the stored snapshot is not shown there |

So a row the sweep corrected reads agreed, a row left split keeps its counts, and a paragraph the person edits after
the run is in the next reading of the Export screen. `fixedParagraphs` and `notFixedParagraphs` are the pass's own
counts and are carried into the snapshot; the Export recount shows names and speech only.

**Why.** `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book` option (a), first in the review's order of value; `research/review-digest.md#f7--the-evals-cannot-see-translation-quality`:
a consistently wrong term scored 100 % on the old figure.
**Reuses.** `RenderingTally` (the one counting rule); `RunRecord`; `ExportService.openWork` and `ExportOpenWork` (the
read the Export screen already makes before anything is written, D5); `ExportReportLines` (the Export screen's line
builder).
**Rejected.** A separate audit class: the tally is the count. Showing the stored snapshot on Export: a review-desk
edit after the run would leave it stale, with nothing to invalidate it. Showing it on Names & style: the
person decides on Export whether the book is ready; one link from a split row to the glossary is enough.
**Risks.** `RunRecord` grows by a few hundred rows; it is one JSON document per run. The Export recount reads every
decided body paragraph on each refresh; it shares the read and the time budget of D5.

### D20 — Prepare steps inside the run

**Decision.** `TranslationJobImpl.translate` runs, after `PrepStage.prepare` and inside `JobStage.PREP`, a new
`P/run/PrepModelSteps` with two steps, each atomic:
- **Brief**: when `Project.prepare().briefDone()` is false and the brief is untouched (no genre, voice/era,
  audience; `Register.NEUTRAL`; `Narrator.unspecified()`; `likely` empty), call `SetupAssistant.suggestBrief(projectId,
  model, detectedContext, emit)`, merge the answer into the brief with `BriefMerge.apply` (below) and save the project
  with `briefDone` set, in one `ProjectRepository.save`. Otherwise mark the step done with no call.
- **Names**: when `Project.prepare().namesDone()` is false, call `NamesPass.run(projectId, model, detectedContext,
  emit)` with the model scan off (a new `NamesPass.Options(boolean modelScan)`; the window button passes `true`),
  then `SetupAssistant.recheckNarrator`. The pass's result and the mark are one write (below).
Both steps run through the job's own model (so `InferenceGate`, cancellation and the call ledger apply) and announce
themselves with the existing `StageStarted`, `StepsPlanned`, `StepChanged`, `ModelCallStarted`/`Finished` events.
Outcomes: success → result and mark in one write; `cancelled` → the job stops or pauses, nothing written;
a retryable `ErrorCode` (`unreachable`, `timeout`, `rateLimited`, `busy`) → the job's existing error pause and
unattended recovery, step not marked; any other error, a refused or failed write of the step's result included →
WARN, the step is marked done by a plain project save (a failure of that save is one more WARN), and the run goes on
with what the code scan left (partial-result rule).

A failed result write therefore has two moments, and they do not contradict each other. (1) The write itself
(`commitNames`, or the brief step's save) is all or nothing: refused, it stores no row, no deferral and no mark, and a
database closed right then holds the step as not finished. (2) The job then records the failed attempt: one separate
`ProjectRepository.save` sets the step's mark and nothing else, so the glossary and the brief stay as they were, the
step is not tried again by a continue, and the person can still run the names pass from Names & style. Task 6.1
proves (1) on both adapters and across a reopen; task 6.3 proves (2) as the job's final state.
`RunRequest` gains `modelPrepare`: the canonical constructor takes it, and the existing shorter constructors set
it false, so every test that builds a request directly keeps the run it had; the window (`U/state/RunStarter`) and
the command line (`C/TranslateCommand`) pass true, as do the eval harness's `BookRunner` (task 6.4) and the new
tests; `C/RunOptionsParser` maps `--code-prepare` to false;
`--review-names` and `C/NameReview` are deleted.

**One rule for applying a suggestion.** The production rule is `U/state/SuggestionMerge.withSuggestion` (with
`likelyAfter`, `registerFor`, `narratorFor`): it keeps what the person wrote where the model said nothing, never
replaces a narrator the person chose, and computes the "likely" marks. The eval tests hold a mirror of it,
`PT/eval/BriefAcceptance`, which sets no likely mark. `:pipeline` cannot see `:ui`, and `:ui` sees only the root
package of `:pipeline`, so the rule moves to `:api`: a static utility `A/pipeline/BriefMerge.apply(BookBrief,
BriefSuggestion)`, a pure function over two `:api` records, beside `BriefSuggestion.alignedTo`. `SuggestionMerge` and
`BriefAcceptance` are deleted; `U/state/StyleSuggestion` (the "Apply to the brief" button), `PrepModelSteps` and the
eval harness call `BriefMerge.apply`. No port method is added for it. The run writes the merged brief without an
Apply press: pressing Start is the person's consent, every field the run fills is marked likely with its evidence,
and each stays editable. The Apply rule of the dialog is unchanged (`book-brief`, "Write nothing to the brief until
the person applies the suggestion").

**The run translates with what Prepare accepted.** `PrepStage.prepare` builds the style sheet from the brief read at
Start, and `RunWiring.stages` builds the run frame (`frameOf`) and `RunSettings` from `run.project().brief()`, the
`RunStart.Started` copy. So after `PrepModelSteps` and after the quality is resolved by the model (D21),
`TranslationJobImpl.translate` reads the project again with `ProjectRepository.find` and builds the style sheet
(`StyleSheet.from`), the frame and the settings from that stored project; `PrepStage.Prepared` gets the new style
sheet (a new `Prepared.withStyleSheet`) and `RunWiring.stages` takes the reloaded project. Without this the suggestion would be
saved and the run would still translate with the register and narrator of before. The acceptance test asserts it on
the first real draft request.

**One write for the names step.** `NamesPassStore` today writes through separate repository calls (`glossary.remove`,
`update`, `add`, `deferrals.add`) and restores by best effort after a failure; a crash between two of them, or
between the last and the mark, leaves partial glossary changes with no mark. The existing all-or-nothing port,
`A/persistence/CheckpointPort` (it commits a chunk), gains `Result<Integer> commitNames(NamesCommit commit)`.
`A/project/NamesCommit` (new record): `projectId`, `removedEntryIds`, `changed`, `added` (glossary entries),
`deferrals`. The commit applies the removals, changes and additions as `GlossaryRepository.remove`, `update` and `add`
do, adds the deferrals and sets `Project.prepare().namesDone()`, all or nothing; an entry id of `changed` or
`removedEntryIds` that the project does not hold gives `ErrorCode.validation` and nothing is written. Both adapters
implement it: `S/memory/InMemoryCheckpoint` (applies to a copy and swaps it in) and `S/sqlite/SqliteCheckpoint` (one
`db.write`, one transaction). `NamesPassStore` builds the commit — `TermChanges.defer` is split into the pure builder
of the deferral list and the add — and calls `commitNames` for the run's step and for the Names & style button alike;
its own `restore` goes (`GlossaryRestore` stays: `GlossaryCsvFiles` reads it). Proof: the checkpoint contract test over both
adapters, and a SQLite test on a temp file that closes and reopens the database after a refused commit (glossary as
before, no deferral, `namesDone` false) and after a good one.

**The window follows what the run wrote.** The job writes the brief (the suggestion, the dial of D21) while the
window holds its own copy in `U/state/CurrentProject`; the next save from the window would write the old brief over
it. The precedent is `U/state/StoredNarratorFollower`, which takes the narrator the run inferred when the run ends.
It is widened to the whole brief and renamed `StoredBriefFollower`: when the run leaves the Prepare stage (the first
`StageStarted` of the Translate stage reaches the state mirror) and when the run ends, it reads the stored project's
brief off the FX thread (`ProjectService.open`) and replaces the window's copy with `CurrentProject.replaceBrief`.
The Book Brief is read-only while a translation is at work, so the stored brief is the newer one; the follower still
takes nothing when another book is open. The Book Brief then shows the run's fields with their likely marks, and a
later edit saves on top of them.

While the names step runs, Names & style is read-only exactly as under a hidden names pass (the `app-shell`
requirement "Make the screen that hidden work writes to read-only"; task 6.6).

**Why.** `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`: the window's shortest path and the command line never ran these steps; every measured run that had
them came from the harness (`BookRunner` lines 101–104). Traced cost: brief 6 calls 48 s, names 104 calls 377 s, of
which 204 s go with D7.
**Reuses.** `SetupAssistantImpl`, `NamesPassImpl`, `StoppableModel` (no call after an interrupt), `JobBoundaries`
and `UnattendedRecovery` for the retryable case, `MemoryEvents.namesAdded`, `CheckpointPort` and its two adapters,
`StoredNarratorFollower`.
**Rejected.** Promoting the test mirror `BriefAcceptance`: it writes no likely marks, so the Book Brief could not show
them. A port method `SetupAssistant.accept`: a pure function over two records needs no service. A new storage port
for the names commit: the checkpoint port already is the "all or none" boundary. A generic transaction over several
repository calls: a framework for one use. Persisting a half-run pass for resume: the pass stages a private copy and
writes once; storing the staging is a new store for a six-minute step. Deriving "names prepared" from the glossary's
content: the per-chunk spelling hint writes targets during the run, so the content cannot tell. Running the model
name scan inside the run: the glossary requirement "Run the model scan only when the person asks for it" stays true
and the run stays shorter. A new kind of event for Prepare progress: the existing ones carry it.
**Risks.** Pausing during a step throws the step's calls away (up to about 3 minutes after D7). The first
start-to-first-paragraph time grows by the two steps; the Translating screen must show the step (events) — task 6.10
checks it in the running app.

### D21 — Defaults instead of questions

**Decision.**
- **Start cards.** `U/dialog/NarratorDialog`, `CharacterGendersDialog`, `MeaningNameDialog`, `GenderRow`,
  `U/state/NarratorQuestion`, `UnknownGenders`, `MeaningQuestions`, `NarratorHints.needsGender`,
  `P/glossary/MeaningQuestions`, `GlossaryService.meaningQuestions`/`answerMeaning`, `A/project/MeaningChoice` and
  `GlossaryEntry.meaningChoice` are deleted with their message keys. The questions stay answerable where the data
  lives: "Who narrates?" on the Book Brief, the Gender and Translation columns on Names & style.
- **Target language.** New `AppSetting.LAST_TARGET_LANGUAGE` (`brief.lastTarget`), written by `P/run/RunStart` when
  a run starts; `P/project/NewBookBrief.of` pre-fills it unless it equals the source. With no target, Start asks in
  the small dialog of D23 (`U/dialog/TargetLanguageDialog`); the run that then starts stores the answer as the last used
  target.
- **Quality.** `AppSetting.DEFAULT_DIAL` has no default: it is declared with a null default value, as
  `AppSetting.LANGUAGE` and `LAST_PROJECT` are (the record's `defaultValue` is nullable), and an absent key means "by
  the model". `BookBrief` gains `dialByModel`; `NewBookBrief` sets it when the setting is absent. At run start, when
  it is set and `WorkList` has no decided segment, `TranslationJobImpl` sets
  `dial = QualityDial.forTier(model.profile().tier())` — `FAST` for `ModelProfile.Tier.SMALL`, `BALANCED` for `LARGE`
  — and saves the brief, before the run's frame and settings are built from the stored project (D20); the window
  takes the saved dial through `StoredBriefFollower` (D20). `Tier` is the existing class
  (`L/profile/IdentityPattern.LARGE_FROM_B = 14.0`; an unknown model is `SMALL`). Choosing a dial on the Book Brief
  clears the flag.
- **Recommendation.** `U/state/ModelRecommendation`: every text names the recommended model `gemma4:26b` (new constant
  `LARGE_NAME`); its pull tag stays the data value `LARGE` (today `gemma4:26b-a4b`) and is shown only inside the
  `ollama pull` command; `OPT_IN`, `OPT_IN_FROM_GB`, `optInTag`
  and `optIn()` are deleted; `LARGE_FROM_GB = 24` stays. The texts name what the small model costs.
- **Special models.** None: no profile row, prompt, format or reader is added for TranslateGemma, Qwen or
  Hunyuan-MT (`inference` "Build the same requests for every model").
- **One action from Import.** The Import toolbar offers Start translation (`specs/book-import/spec.md`), wired to the same
  START command as Names & style, and like it (`NamesStyleView.startRun`) it opens the Translating screen, so a refused
  start or a provider error is shown there by its banner. With no AI model it is disabled with the tooltip that names
  the model and Import shows no banner (D23). With no target language it is disabled with the Book Brief tooltip until
  task 8.4 makes every Start ask for the language.
- **Readers of the removed Start plumbing**, beyond the classes above: `U/screen/TranslatingTools`,
  `U/screen/NamesDialogs`, `P/glossary/NamesTranslation`, `GlossaryService.unknownGenders` with
  `A/pipeline/UnknownGender` and `P/glossary/CharacterMentions` (they exist only for the gender card).

**Why.** `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work` (1 drop + 7 clicks + 1 forced decision + up to three cards), `research/review-digest.md#f16--defaults-work-against-the-goal`,
`research/review-digest.md#f2--almost-nothing-improves-a-poor-first-draft` (Proofread on a small model: a quarter of the run, 1 good and 2 bad kept changes), `research/review-digest.md#owner-decisions`.
**Rejected.** Target from the OS language: the app language is not the translation's language for a person who reads
English UI and translates into another tongue; "last used" is observed, the OS language is a guess. One folded card
with "start anyway": still a stop on the default path. A middle model tier and the language pair as inputs of the
recommendation: the window is the same for every language. Resolving the dial at import: the model may be chosen
after the book.
**Risks.** A person who relied on the Start question no longer sees it; the Book Brief notice
(`U/state/NarratorNotice`) is rewired to say the narrator's gender is not stated and where to set it.

### D22 — The merge at Max is an experiment first

**Decision.** The merge is measured in the eval harness before any production class exists, and it ships only if it
passes the gate below. This section is the one statement of that gate; every other artifact refers to it.

**The arms (task 7.1).** Both are whole runs of the two panel slices through `PT/eval/BookRunner`, at Max, on
gemma4:26b, on the same idle machine. Arm A is the app as built. Arm B is the same run where every paragraph that
carries a proofread mark after Translate gets, instead of its proofread window, two more single-paragraph drafts by
the app's own draft request (`P/DraftRequests`, temperature 0.2 and 0.4, two new seeds) and one merge call
(temperature 0.2). The merged text is kept when it passes the hard gates and adds no BLOCK or REPAIR finding.

**The merge request.** The system part is the run's own fixed system part, so the shared prefix is unchanged. The
user part (`merge.user.prompt`) holds the paragraph's name rows, the source, the three drafts numbered 1–3 and this
task text: "Below are one source paragraph and three translations of it, numbered 1 to 3. Write one translation that
takes the best of the three. Keep every sentence of the source, keep every formatting mark exactly as it is, and use
the listed names as given." The reply is the JSON batch of one that a single draft uses
(`{"items":[{"id","target"}]}`, `BatchSchema`), read by the same reader, so there is no second reply contract. The
output cap is a single draft's.

**The sample and the readers.** Every paragraph that carries a mark after Translate in arm A, in both slices — not
only those among the 100 sample paragraphs of D1 — is put on one blind sheet with its arm A text and its arm B text
and read by the two reader families of D1. The sheet is a complete-sample sheet (D1, "What is counted"): the
list of marked paragraphs is the required ids, a paragraph whose merge was refused keeps its arm A text and is a tie
counted as "same" with difference 0, and every figure — the 60 of rule (a) included — is over the whole list. When the two slices give fewer than 60 marked paragraphs, the two runs are
extended over the following chapters of the same two books until 60 are read or the books end.

**The gate.** The merge is kept only if all three hold:
- (a) at least 60 marked paragraphs were read;
- (b) on them the mean gain (arm B minus arm A, mean of the two reader families) is at least +0.3, and the paragraphs
  judged better are at least twice those judged worse (margin 0.25, as `eval-score.py panel` counts them);
- (c) the whole-run time at Max — the wall-clock time of the whole run of arm B against arm A, both slices together —
  grows by at most 15 %.

Fewer than 60 marked paragraphs, or any condition not met, is a fail. A clear gain that costs more than 15 % is a
fail too, reported to the owner with the numbers. The result page holds, for both arms, the count of marked
paragraphs and their share of the slices, the mean gain with its 95 % interval per reader family, better / same /
worse, the gain averaged over all paragraphs of the slices (so the owner's "1–2 %" reading can be made), the
whole-run times, and one line: PASS or FAIL.

**If it passes.** Tasks 7.2–7.5 build it in `ProofreadStage` as the pass that replaces the proofread window for marked
paragraphs at Max on `Tier.LARGE`: new `P/proofread/MergePass`, `PromptName.MERGE` (`merge.user.prompt`, the text
measured), `CallKind.MERGE` with its English and Ukrainian words, a record note. On a small model a Max run makes no
merge call and reads its marked paragraphs in proofread windows. Task 7.4 changes two texts of the `book-brief`
requirement "Choose the quality dial and show what it turns on": the Max hint names the merge, and the sentence that
the quality settings are the same for every model becomes "the same for every model, except that Max merges marked
paragraphs only on a large model". Task 7.6 removes the conditional wording ("WHERE the merge at Max is built") from
the deltas.

**If it fails.** Task 7.6 deletes the harness arm, the three merge requirements and the merge clause of "Map the
quality dial to the run's mechanics" in `specs/translation-pipeline/spec.md`, the merge clause and scenario of the
`inference` temperature requirement and the merge branch of the `book-brief` dial requirement, and records the
numbers in `docs/evaluation/results-finish-release.md#merge-at-max` under "tried and rejected". The Book Brief's two
texts stay as they are today. Tasks 7.2–7.5 are closed as "not built: 7.1 failed".

**Why.** `research/review-digest.md#merge-results-on-gemma426b`: +0.25…+0.45 on 26b across readings, a fair expectation of +0.3 with the bad
share halved; nothing on other models (+0.04, +0.10, −0.6); never measured inside the app or with one prompt.
The review's best variant used three different prompts; the owner forbids new prompt variants and special formats,
so the app's variant differs by seed and temperature only (two repeats of the same pipeline already differ in about
two thirds of paragraphs), and its gain is unknown until measured. The owner's rule
(`research/review-digest.md#owner-decisions`) is "only if an in-app test shows a real gain; a gain of 1–2 % for 15 %
or more extra time is rejected". +0.3 is also the sheet's noise band, which is why (b) asks for twice as many better
as worse paragraphs and (a) for 60 of them: a mean alone on a handful of paragraphs would be decided by chance.
**Rejected.** Three prompts (lean, TranslateGemma header): excluded by the owner. Hot drafts (0.8): measured worse
(3.03 against 3.29) and three times the time. Merging every paragraph: four calls per paragraph is about four times
the run. Requiring the merge to clear its mark: the "no-gain" veto refused 3 real fixes of 5 compared (`research/review-digest.md#f2--almost-nothing-improves-a-poor-first-draft`).
Measuring time as model seconds: the person waits for the wall clock.
**Risks.** The measured gain may be a selection effect (about 14 variants were tried); that is why the gate is in the
app's harness on fresh paragraphs and removal is planned. After D10 a mark is rare (22 of 129 listed items had a
reader-visible reason on 385 paragraphs of the traced run), so reaching 60 may need the extension above; if the two
books cannot give 60, the experiment fails by rule (a) and is removed.

### D23 — Where the cannot-start notice shows, and the question at Start

**Decision.** The "Cannot start yet" banner is shown only on Names & style and Translating. It is never raised for a
missing target language: a brief without one raises no notice, `Start translation` stays enabled, and a press opens a
new small dialog, "Translate into which language?", that stores the target in the brief and goes on with the start.
Translating is unlocked in the navigation while only the target language is missing; Names & style stays locked.

**Rule for the other missing inputs.**

| Missing | Before a press | At a press | Where the banner shows |
|---|---|---|---|
| Book | nothing (no project is open, so no run banner exists and the five steps are locked) | unreachable | nowhere |
| Target language | nothing; Book Brief marks its target box "Required…"; Names & style is locked with its reason | the dialog | never |
| AI model | title-bar chip `No AI model` and the red navigation footer, on every screen; Start disabled with the tooltip naming it | unreachable (Start is disabled) | Names & style, Translating |
| A refused start or a failed preparation (`RunNotice.Refused`, `RunNotice.ProviderError`) | — | the banner in its own words | Names & style, Translating |

**Why.** `research/ui-audit-shell-dialogs.md#defects` D-06: the banner is 120 pt tall on screens where nothing can be
done about it. Code: `state/MissingInputNotice.briefChanged` raises the notice as soon as a brief has no target;
`control/RunBanner.refresh` pins it on every screen; `state/StepAvailability.reasonFor` locks both screens that hold
Start, so today a Start press without a target language is impossible and the banner is the only signal.

**Reuses.** `state/RunStateView.BannerView` (its `translatingOnly` flag becomes a place: everywhere, Translating only,
start screens only); `control/RunBanner.refresh`; `dialog/FormCard`, `dialog/ModalCard.Size.DIALOG`;
`control/SearchableCombo.parsed` with `i18n/LanguageNames` exactly as `dialog/TranslateIntoDialog.languageBox` builds
it; `state/BookBriefViewModel.setTargetLanguage` (it replaces the brief synchronously through
`CurrentProject.replaceBrief`, renames an automatic project name and saves).

**Changes.** `state/RunStateView` (`BannerView.where`), `state/RunBanners` (`cannotStartBanner` → start screens; the
`TARGET_LANGUAGE` arm of `fixFor` and `RunStateView.GO_BRIEF` are deleted), `control/RunBanner` (`DESTINATIONS`,
`commands`, `actionsOf` lose `GO_BRIEF`), `state/MissingInputNotice.briefChanged` (only withdraws),
`state/RunToolbar.start` (no `noTarget` branch), `state/StepAvailability.reasonFor` (Translating: locked only for no
book or for two equal languages), new `dialog/TargetLanguageDialog` and `dialog/TargetLanguagePrompt` (the interface
the Translating view calls to ask, so a test answers with a recording double, as `dialog/TranslateIntoPrompt` is for
the other dialog), the language box
moved from `TranslateIntoDialog.languageBox` into a package-private `dialog/LanguageBoxes.targets(...)` used by both
dialogs, `screen/TranslatingView.runCommands` (the START command asks first), `screen/TranslatingDialogs`. Bundle keys
removed: `run.action.goBrief`, `run.action.goBrief.tip`; and the `target` arm of the two `select` messages
`run.banner.cannotStart` and `translating.missingText` (both keys stay: their `book`, `model` and `other` arms are
still used). `RunNotice.Input.TARGET_LANGUAGE` stays: `Input.firstMissing` still answers it, and
`MissingInputNotice.refuseIfMissing` then returns empty without publishing a notice, so a start that reaches the view
model without a target (a path that skipped the dialog) starts nothing. Keys added:
`dialog.targetLanguage.title`, `.body`, `.start`, `.start.tip`, `.used`.

**Rejected.**
- Reuse `dialog/TranslateIntoDialog` as is — it creates a *new project* from a `BookGroup` (name field, "Start from"
  lines, "Create and open the brief"); it cannot set the open brief's target.
- A mode flag on `TranslateIntoDialog` — two different jobs in one card, half of its nodes hidden per mode.
- Keep Translating locked — then no Start exists to press and the owner's "a popup at Start" is unreachable.
- Hide the banner per screen in `RunBanner` by a list of screens — the place belongs to the banner's row, where
  `translatingOnly` already lives.

**Risks.** The chain of Start questions (`dialog/NarratorDialog.startAfterAsking`) is deleted by task 6.7 (D21) before
this group, so the target question is the only thing a Start may ask. The Import toolbar's Start (task 6.10) uses the
same START command, opens the Translating screen and asks the same question; with no AI model it is disabled with the
tooltip, and Import still shows no banner. A language the same book already uses is
not confirmed in this dialog (the name question lives on the Book Brief, `state/BriefPairGuard`); the dialog says so.

### D24 — About this book opens with the book

**Decision.** The fold is open whenever a book is published to Import; the person's own collapse is kept only until
another book is opened, not stored. The flag lives in a new small singleton `state/AboutFoldState` (one boolean
property; it listens to `CurrentProject.book()` and opens on every change of book): `state/ImportViewModel` is 400
lines, at the file limit, and is not edited. The text browser keeps a minimum height of 320 px so an open fold cannot squeeze it; the page scrolls.

**Why.** Owner's point 2. The Import view is rebuilt on every visit, so without the boolean a fold the person closed
would re-open each time they return to the step.

**Reuses / changes.** `screen/AboutBookFold` (`setExpanded` bound to the flag both ways), new
`state/AboutFoldState`, `screen/ImportBookView` (the browser's minimum height). `AboutBookFoldTest.fold_bookOpened_isClosedWithTheOneLineSummary`
is replaced; the three `fold_openedOn…` tests drop their click that opens the fold.

**Rejected.** Storing the collapse per project — a setting nobody asked for. Always open with no collapse memory —
the fold would re-open on every return to Import.

### D25 — One searchable choice box, built to the research contract

**Decision.** `control/SearchableCombo` is rebuilt to the 14 rules of
`research/combo-box-behaviour.md#c-proposed-contract-and-the-smallest-way-to-build-it` by its step list C.2: select-all
on focus (deferred with `Platform.runLater`), its own key and mouse filters on the popup list so a choice is committed
from the gesture and the cell's **item**, never from a selection index, a highlight held in the popup list's focus
model, and Enter / Escape / Down handled in a filter added in the constructor. The provider form's model entry
(`screen/ModelField`) is rebuilt on the same control in its free-text mode, so the app has one type-to-filter box.

**Why.** Research A.2 (typed letters inserted into the shown name), A.3 (a click on the row at the committed entry's
index is a no-op), A.4 (Down commits, Enter with no match wipes the text, an `IndexOutOfBoundsException` on the FX
thread). `ModelField` has the same defect class: `showOffered()` and `showChoices()` narrow the list by the editor
text, which is the chosen model's own id, so the open list holds one row while the card says seven are available
(`research/ui-audit-settings.md#cross-tab-inconsistencies`, last bullet); it also commits from `setOnAction`, and
`SettingsModelScreenTest.modelControl_nameClickedInOpenList_choosesItWithNoFailureOnTheFxThread` already guards the
same exception.

**Reuses.** `control/SearchableCombo` (three modes, `committedProperty`, `invalidText`), `control/TextMatch`,
`state/ModelNameFilter`, `screen/ModelChoiceCell` (quant chip and cloud label), `control/ModelFilterField` (kept above
the picker: the settings spec requires it).

**Where the code goes.** `control/SearchableCombo` (266 lines) keeps the text, the three modes and the commit. The
popup's part — the cell that commits its item on release, the key and mouse filters on the popup list, the highlight
in the list's focus model — is a new package-private `control/ChoicePopup`, so neither file nears the 400-line limit.

**Changes.** `SearchableCombo` gains: `setChoices(List<T>)` (the backing list becomes an `ObservableList`, for a model
list that arrives later), `setExtraFilter(Predicate<T>)` (the "Filter models by name" field narrows the same list),
`setRowFactory(Supplier<ListCell<T>>)` (the caller's cell; the control adds its press/release filters to whatever cell
it gets). `theme.css` gains one rule, `.combo-box-popup .list-cell:focused`, with existing tokens. `ModelField` keeps
only its wiring: the editor text still goes to `SettingsViewModel.setModelText` on every key (a Test button must be
enabled as soon as something is typed), a commit calls `commitModel()`.

**Rejected.**
- The narrower patch of research C.2 (keep selection-driven commits, defer `clearFilter`) — leaves Down committing
  and adds an asynchronous step every test must wait for.
- ControlsFX `SearchableComboBox` or `TextFields.bindAutoCompletion` — research C.3: neither has the three modes, both
  bring foreign styling.
- Patch `ModelField` in place — a second hand-written filtering combo with the same faults stays.

**Risks.** The filter order (popup list sees keys before the combo; a constructor filter runs before the skin's) is
observed, not documented API: tests 6–13 of the research pin it. The headless robot marks its mouse events as
synthesized, so the row-click tests are parameterised over the robot click and a fired non-synthesized press/release
pair (research D.1). One hand check on macOS with a real mouse is part of task 8.2. Input-method composition and the
space key with the list open get one test each (research A.4 item 7).

### D26 — The Book Brief has no language-specific rows

**Decision.** The five rows (apostrophe, quotation marks, address between strangers, address in a family, hard g) and
everything that stores or carries their answers are deleted. The fixed internal rule for each is **what a brief that
stored no answer does today**:

| Key | Today with nothing stored (becomes the only behaviour) | Where it lives |
|---|---|---|
| Apostrophe | the typography pass writes the language's apostrophe | `pipeline: typography/TypographyRules.forLanguage` (`apostrophe` property) |
| Quotation marks | the language's quote pairs | `pipeline: typography/QuoteConventions.ownLine` |
| Address | the language's `rule.address` / `address` line in every system message; the ти/ви mix check | `prompt/languages/uk.properties`, `ru.properties`, `prompt/AddressForms`, `checks/AddressMixCheck` |
| Hard g | the suggestion call's `names.convention` line (H as Г); no refusal; г and ґ fold to one letter when forms are compared | `prompt/languages/uk.properties`, `glossary/TargetConventions.fold` |

So no style-sheet line is added for them (an undeclared key "says nothing" today, `prompt/StyleSheet.addPolicyKeys`),
the normaliser runs with the language's own rules, and the glossary no longer refuses a target for its hard g — that
refusal fired only for a brief that stored a choice (`TargetConventions.refusal` skips a convention with
`chosen == null`).

**Why.** Owner decision, `research/review-digest.md#owner-decisions`: the window is the same for every language;
language files keep internal defaults. Enforcing г by refusing ґ would be a new behaviour nobody measured, which the
same decisions exclude.

**Order (three tasks, the build green after each).** Users first, the contract last: (1) `:ui` — the rows, their state
class and bundle keys, the re-check banner; (2) `:pipeline` — everything that reads the keys from the brief;
(3) `:api` + `:persistence` — the record components and types. The reverse order (`:api` first) breaks every module
at once; one vertical task would touch about 45 files in four modules, more than one session.

**Deleted.**
- `:ui`: `screen/BriefKeyRows`, `state/BriefPolicyKeys` (with its nested `Edit` interface: its one other user,
  `state/BriefSummaries`, is deleted by task 4.8), `BookBriefViewModel.policyKeys()` and `rechecked()`, `BriefSaver.rechecked()`, the
  `brief-recheck-banner` in `screen/BriefView`; keys `brief.policy.apostrophe*`, `brief.policy.quotes*`,
  `brief.policy.address.*`, `brief.convention.*`, `brief.recheck.banner`; tests `BookBriefPolicyKeysScreenTest`,
  `BriefRecheckBannerTest`, `BookBriefViewModelRecheckTest` and `BriefRebuildKeysTest` (its remaining cases — the
  likely set — move into `BookBriefViewModelLikelyTest`; the story-shape and chapter-summaries cases are deleted by
  task 4.8). The merge rule's own test moved to `:api` with the rule in task 6.2 (`BriefMergeTest`); its case that
  the four key components are kept is deleted in task 8.7 with the components,
  `state/BriefKeysFixtures`; the conformance case at `ConformanceCases.java:221-231` and the `briefChoices` override in
  `ConformanceTestBase`.
- `:pipeline`: `prompt/BriefKeys`, `LanguageRules.briefChoices`, `run/BriefKeyRecheck` (and its use in
  `project/ProjectServiceImpl`), `typography/TypographyChoices` (with `TypographyRules.with`, the `choices` parameter
  of `TypographyGate.around` and `TypographyNormalizer.normalise`, the field in `prompt/CallFrame`),
  `StyleSheet.addPolicyKeys` and the four `isAllDefault` lines, `StylePhrases.apostrophe/quoteStyle/address/convention`
  with the `apostrophe`, `quoteStyle`, `address`, `convention`, `convention.hardG` lines of `style-phrases.properties`,
  the four brief components of `context/BookMap`, `TargetConventions.refusal` with the `chosen` parameter through
  `TargetValidator.of`, `SuggestionAcceptance`, `ReviewFlags`, `SuggestionCheck`, `GlossaryModelScans.Book`,
  `TargetValidator.Reason.CONVENTION`; the keys `briefApostrophes`, `briefQuotes` of `uk.properties` and
  `ru.properties` (`conventions`, `convention.hardG.choices`, `convention.hardG.default`, `informalAddress`,
  `formalAddress` stay: `TargetConventions.fold` and `AddressForms` read them — `TargetConventions.of` reads the
  convention keys from the language file directly instead of through `BriefChoices`); tests `BriefKeysTest`,
  `BriefKeyRecheckTest`, and the brief-key cases of `StyleSheetTest`, `TypographyNormalizerTest`, `TypographyGateTest`,
  `TargetValidatorTest`.
- `:api`: `BookBrief` components `apostrophe`, `quoteStyle`, `defaultAddress`, `conventions` with their `with…`
  methods (the private `keys(...)` and `withKeysOf` keep the likely set; story shape and chapter summaries
  left with task 4.8),
  `BriefField.APOSTROPHE`, `QUOTE_STYLE`, `DEFAULT_ADDRESS`, `CONVENTIONS`, `pipeline/BriefChoices`,
  `LanguageSupport.briefChoices`, `project/Address`, `AddressForm`, `QuoteStyle`, `TargetIssue.CONVENTION`,
  `ProjectChange.Saved.rechecked`; tests `BriefChoicesTest`, the key cases of `BookBriefTest`, `BookBriefKeysOfTest`.
- `:persistence`: nothing in main code — `StoredShapes` fingerprints the record shapes by itself, so the stored
  fingerprint changes and the database is set aside (ADR-0043); `JsonCodecTest` and `StoredShapesTest` are updated.

**Other language-dependent things found on the window (kept, with the reason).**
- Book Brief, the note "translation rules for this language are not tested yet" (`state/BriefLanguageFlags.targetUntested`):
  a status line, not an option; the widgets are the same. Kept.
- The provider form's "Declared languages … BookLoom tested it for English → Ukrainian" line: a fact about the model
  and the open pair, not an option. Kept.
- Names & style, the Forms column (empty for languages without forms): the column is always present; polish task 9.7
  only lets it shrink.
- No other row or control is shown for some languages only (`screen/`, `dialog/`, `control/` read no language tag
  except through `LanguageNames` and the two flags above).

**Rejected.** Writing each language's default into the style sheet as a line — duplicates the language's own rules
in the same system message, and changes every prompt, which this change may not do. Refusing ґ by default — see Why.

### D27 — The Models tab edits the current model; the facts move to the provider form

**Decision.** Settings › Models shows a read-only title `{model} · {provider}` for the current selection
(`SettingsViewModel.selection()`), then the sections Temperature, Thinking, Sampling, Settings this model refused,
limits and Advanced, with the pinned footer. It has no chooser, no filter, no Refresh, no roster. With no model it
shows one line and a link to Providers. The facts (identity, quant sentence, declared languages, licence note,
thinking hint, facts date, the verdict, "Found out from the program", the structured-answers lines) move as one block
into the provider form's Model row, under the picker, and describe the model the picker holds for a **saved**
provider. The Model row (label "Model"; filter, picker with Refresh, hints, facts) becomes a row of
`screen/FormGrid` inside Connection details.

**Why.** Owner's point 5 and `research/ui-audit-settings.md#defects` S-09, S-16.

**Reuses.** `state/ModelForm` (draft, baseline, save, refused, Send again — unchanged), `screen/ModelsFormView` minus
its three fact nodes, `screen/ModelAdviceSection` (it already takes a selection supplier; it is fed
`SettingsViewModel.testedSubject()`), `state/ModelReportRows`, `state/ModelFacts`, `screen/SettingsTabGuard.guardModel`.

**Changes.**
- New `state/ModelFactsSource`: the report reading now inside `ModelForm` (`askReport`, `reported`, the `facts`
  property, the epoch guard) moves here with `follow(Optional<ModelSelection>)`. `ModelForm` holds one instance for
  its model (the Thinking and limits sections read `facts`); the provider form holds a second one for the picker's
  model. One class, two instances — no shared mutable state between the tabs.
- New `screen/ModelFactsBlock`: the `ModelAdviceSection` lines, then verdict, the "Found out from the program" banner
  and the structured lines taken out of `ModelsFormView`, all in the `hint` class with 4 px spacing.
- `ModelForm`: follows `SettingsViewModel.selectedProviderId()` and `model()` instead of `ModelRoster`; a change of
  the current model while the draft is dirty cannot happen from another Settings tab (the leave guard asked first);
  a change from outside Settings (the first-launch pick, `SettingsViewModel.adopt`) discards the draft, logged at INFO.
- `screen/ModelsController` and `models.fxml`: the chooser row, `#models-unanswered`, `#models-explain` (the sentence
  stays, as the line under the title), `#models-empty` become the title, the explanation and the no-model line with
  its link; `addFilter`, `narrow`, `filterTyped`, `moveChooser`, `chosen` are deleted.
- `screen/ProviderFormView.buildFold`, `screen/ModelField` (see D25), `screen/ProviderConnectionSection` (the grid
  the Model row joins).
- Deleted: `state/ModelRoster` (and its refresh in `screen/SettingsTabEntries`), `screen/ModelEntryCell`,
  `ModelsController`'s converter; tests `ModelRosterTest`, `SettingsModelsFilterTest`; `ModelFormSelectTest`,
  `SettingsModelsScreenTest`, `SettingsModelsLeaveScreenTest`, `SettingsModelsReportScreenTest`,
  `ModelsAdviceLinesTest` are rewritten for the new places. Bundle keys removed: `settings.models.model`,
  `settings.models.model.tip`, `settings.models.refresh`, `settings.models.refresh.tip`, `settings.models.unanswered`,
  `settings.models.listing`, `settings.models.empty`; added: `settings.models.title`, `settings.models.none`,
  `settings.models.none.link`, `settings.models.none.link.tip`, `settings.form.model`.

**Rejected.** Keeping the facts on Models under the title — the owner crossed them out there, and they describe a
choice made elsewhere. Showing the facts for an unsaved provider's typed model — the report needs a stored provider
(`TestSubject`), and typing must not start requests (`llm-provider`: facts are read on select, test or detect).

**Risks.** `ModelEntry` is still the form's subject type; it is built from the selection and
`SettingsViewModel.configOf` (kind, engine) instead of from a roster row. Settings of models that no provider lists
any more can no longer be opened; they stay stored and unused, which is harmless.

### D28 — Conformance cases and catalogue keys touched by removed widgets

The mockup file moves (D37); these cases change whatever its path is.

- `ConformanceCases.java:221-231` (the Ukrainian brief rows: `#brief-policy-apostrophe-label`,
  `#brief-policy-convention-hardG-label`) — deleted (task 8.5), with the `briefChoices` override in
  `ConformanceTestBase.java:242-258`.
- `SettingsConformanceCases.java:94-105` (Models card): the part `#models-model` is deleted (task 8.10); the parts
  `#models-found` and `#models-identity-line` move to the Providers form case with their node ids unchanged
  (task 8.9), and the Models case gains `#models-title` (text, the strong-text role).
- `ImportConformanceCases.java:32-33` (`#import-about`): unchanged parts; the fold is now open in that state, so the
  case's expected bounds are re-read, not its colours (task 8.3).
- `ScreenConformanceCoverageTest`'s checklist loses `BOOK_BRIEF_POLICY_KEYS` (task 8.5); `SETTINGS_MODELS` and
  `SETTINGS_MODELS_DIRTY` stay.
- `ThemeTokenCatalogueTest`: no token is added or removed by groups 8 and 9 (task 9.14 changes which token a rule
  uses, not the catalogue).
- `i18n/VocabularyTest`, `MessageBundleParityTest`, `MessageKeyRegistryTest`, `TipKeysTest`: every task that removes
  or adds a key does so in `MessageKey`, `messages_en` and `messages_uk` together, so these stay green task by task.

### D29 — Polish: triage of the 90 audit items

Kept: every High and Medium item, 16 Low items that share a file with a kept fix. Each kept item is closed by the task
named. "Hand" means the audit marked it "check by hand": the task reproduces it first and closes it without a change
if it does not reproduce.

| Task | Closes | Shared cause |
|---|---|---|
| 8.8 (group 8) | S-09 | the model picker outside the form grid |
| 9.1 | W-01, D-01, W-34 | `control/HowCountedPane` carries no fold class; focus effect on the arrow |
| 9.2 | W-02, W-03, D-07, W-04 (hand) | `control/BannerSlot` outside the scroll gutter; no gap under it |
| 9.3 | W-33, D-05, S-17, D-06, D-15 | `control/Banner` glyph label, `TOP_LEFT`, title height; `TranslatingView` sleep note |
| 9.4 | D-02, D-03, D-04, S-18 (a, b, d), D-28 (hand), D-17, D-18, W-36 (Add term part) | button kinds chosen per dialog |
| 9.5 | D-11, S-18 (c), D-09, S-04, D-10, S-03 | `.dialog-h` padding; XL bodies that do not grow; fixed figures width |
| 9.6 | D-08, D-16, W-05 (results), W-06 (results), W-36 (progress, results parts) | progress panes without padding; results rows |
| 9.7 | W-05, W-06, W-07, W-08 | `screen/TermTableCard`, `GlossaryCells`, `GlossaryTable` |
| 9.8 | W-11, W-12, W-15, W-17 (hand) | `control/LiveRowList` width, `LiveRowDetail` alignment, empty-cell borders |
| 9.9 | W-13, W-18, W-19, W-20, W-21 | `control/PromptView` wrap width; `control/ComparePanes` label row and hint |
| 9.10 | W-22, W-23, W-24, D-13 | `screen/ExportResult` tiles and double line; `ExportTitleCard`; `ExportCompleteDialog` paths |
| 9.11 | W-27, W-31, W-32, Book Brief locked languages in dark (hand: measure) | switch column; tree indent; fold rows |
| 9.12 | S-01, S-05, S-23, S-02, S-07 | `control/EnumSegments.show`; per-tab `maxWidth`; `GenerationQualityTable.WIDTHS` |
| 9.13 | S-06, S-08, S-16 (Thinking, Sampling text), S-24, S-10, S-21, S-15 (the three row actions it restyles) | switch text widths, `FormGrid` label alignment, summary card tone, three looks for a row action |
| 9.14 | dark: `Delete…` border, unselected radio (hand: measure), info banner tone; S-14 | `-color-err-bd` used as an edge |
| 9.15 | D-19 | title-bar phase pill looks like the model chip, actions look like text |

**Moot because of group 8 or an earlier group.**
- S-16, the fact lines part, and the Ukrainian fact line touching the scroll bar: the facts move (task 8.9) and get
  one style there.
- S-09: done inside task 8.8.
- D-06, the cannot-start case on four screens: removed by task 8.4; the spacing fix stays in 9.3.
- D-25 (Start questions): the dialogs are deleted by task 6.7 (D21).
- W-28 (empty right column on Book Brief): removing five rows (task 8.5) shortens the left column; not re-laid out.

**Depends on group 3**: task 9.7. Group 3 removes the Recurring terms card (D7); `TermTableCard` still draws the
Glossary, so the fixes are made once, on that one table.
**Depends on groups 1–7** (run-state wording and the Export screen's sentences change there): tasks 9.8 (W-17 only)
and 9.10 (W-24 only).

**Low items left out** (no neighbouring fix makes them free; none hides information):
W-09, W-10 (table inside page scroll, empty space under few rows — "note only" in the audit), W-14 (Copy button row),
W-16 (chip position on "This run"), W-25 (two form styles in "Where to save"), W-26 and D-21 (text glyphs in buttons —
needs an icon decision per button), W-29 (slider legend), W-30 ("change" link), W-35 (Projects bar x), W-37 (back-quotes
in the first-launch hint — that sentence is rewritten with the recommended model by task 6.11),
S-11, S-25 (scroll-bar gutters inside cards), S-12 (Details link position), S-13 (four filled Test buttons — the audit
calls it the owner's taste), S-19 (test dialog's stage switch, hand), S-20 (Storage cards' heights, path breaks),
S-22 and D-27 (focus ring after a synthetic click, hand), D-12 (dialog position — consistent, "possibly intended"),
D-14 (2 pt nav label offset), D-20 (state word after reopening — run-state logic, not polish), D-22 (lock glyph —
"only if the owner wants it"), D-23 (toast position — "none needed"), D-24 (About rhythm), D-26 (Translate into
"Start from" lines), and the dark-theme Low notes: scrollbar thumb brightness, a tooltip cut by the window edge, the
off-switch thumb, the scrim strength.

**How a polish fix is checked.** A TestFX assertion where geometry or a style class can be asserted (bounds of two
nodes, a class on a node, a looked-up colour through `Contrast`), added to the existing class for that screen
(`*FitTest`, `ContrastTest`, `DialogFitTest`, `ThemeControlStatesTest`). Text is measured with the platform font, so
an assertion compares two nodes of the same run (equal x, equal height, "no more than three lines") and never a pixel
constant taken from a macOS shot; every task that touches a screen, a dialog or a word runs `scripts/ci-linux-ui.sh`.
Where only the eye can judge (W-33 icon look, D-19), the check is a before/after pair captured as the audit was:
`BOOKLOOM_DATA_DIR=<fresh folder> ./gradlew :app:run`, window 1440 × 900 pt, `screencapture -l <window id>`, files
under `.temporary_context/ui-audit-2026-11-14/after/` with the audit's shot number.

### D30 — Four workflow files: build, verify, release and one reusable packaging matrix

- **Decision.** `.github/workflows/ci.yml` is deleted and replaced by `build.yml` (every branch push: compile only),
  `verify.yml` (pull request to `master`: the whole verification in parallel jobs), `release.yml` (the four tag forms:
  build, pack, publish) and `package.yml` (`workflow_call`, the five-leg packaging matrix, called by `verify.yml` and
  `release.yml`).
- **Why.** The owner's trigger list; `research/ci-and-release.md#e-proposed-minimal-file-set-description-only`. The
  matrix today is 110 lines (`ci.yml:196-306`, checked); two copies would drift, so the reusable file removes real
  duplication.
- **Jobs.**
  - `build.yml`: `on: push: branches: ['**']` (a branch filter alone never matches a tag push). Jobs `detect` and
    `build` (D33). `build` runs `scripts/ci-verify.sh build`. Timeout 15 min.
  - `verify.yml`: `on: pull_request: branches: [master]`. Jobs `static`, `test-core`, `test-ui`, `package`
    (`uses: ./.github/workflows/package.yml`, version `dev`, retention 14 days) and `verified`
    (`needs` all four, `if: always()`, fails unless every needed result is `success`). All four start together;
    nothing waits for `static`. Timeouts 20 / 30 / 30 / 30 min.
  - `static` steps: strict lock check (both builds, without `--no-daemon`), `scripts/ci-verify.sh static`,
    `scripts/ci-verify.sh --check-parity`, `python3 scripts/sync-agent-files.py --check`,
    `python3 -m unittest discover -s scripts/tests`, `python3 scripts/check-docs.py` (D40), `./gradlew checkLicense`.
  - `test-core` and `test-ui`: Xvfb as today (`ci.yml:103-118`), then `scripts/ci-verify.sh test-core` /
    `test-ui`; test reports uploaded on failure as today.
  - Concurrency `${{ github.workflow }}-${{ github.ref }}`, cancel in progress, for `build.yml` and `verify.yml`;
    `release.yml` never cancels. Workflow permission `contents: read` everywhere; only `detect` adds
    `pull-requests: read` and only `publish` has `contents: write`. No secret is used.
  - Every action stays pinned by commit SHA with its tag in a comment (`ci.yml:70-76`). New pins, read from the
    GitHub API on 2026-11-14: `gradle/actions/setup-gradle` v6.4.1 `8da46b539cd8fe6ab27ca75128919a18d757604c`,
    `actions/cache` v6.1.0 `55cc8345863c7cc4c66a329aec7e433d2d1c52a9`, `actions/download-artifact` v8.0.2
    `9000827ccba6bdab643e8b6fd33ac0654aef8333`. The task re-reads each SHA before it writes it.
- **Reuses.** The Xvfb recipe, the matrix rows, the action pins and the failure-report uploads of `ci.yml`.
- **Rejected.** One job with the gate command (30 min; keeps the old identity rule, loses the speed).
  `paths-ignore` for document-only changes: a skipped workflow leaves the required `verified` check pending, and the
  document check of D40 must run on exactly those changes. `workflow_run` chaining: it shares no compiled output.
  Skipping the branch build while a pull request is open: the owner accepted the duplicate (decision 13).
- **Risks.** A direct commit to `master` is only compiled, never tested (owner decision 3); the documents say so.
  The estimate of 15–18 min wall clock is unmeasured; task 10.9 measures it.

### D31 — The gate after the split: one script holds the job commands, and a parity check proves they add up

- **Decision.** The pre-push hook stays one command, `./gradlew clean build check spotlessCheck`
  (`.lefthook/pre-push/quality-gate.sh`, unchanged). The rule "CI runs the pre-push command character for character"
  becomes "the pull-request jobs together run exactly the tasks of the gate command". `scripts/ci-verify.sh` is the
  one place that holds the commands:

  | Mode | Command |
  |---|---|
  | `build` | `./gradlew assemble testClasses :app:archTestClasses --build-cache` |
  | `static` | `./gradlew build check spotlessCheck -x test -x jacocoTestCoverageVerification --build-cache` |
  | `test-core` | `./gradlew test jacocoTestCoverageVerification -x :ui:test --build-cache` |
  | `test-ui` | `./gradlew :ui:test --build-cache` |
  | `--check-parity` | dry-runs the gate command and the three verify modes; fails unless the gate's task set, without the `clean` tasks, equals the union of the three |

- **Why.** `research/ci-and-release.md#open-decisions-for-the-owner` decision 1. A written mapping in a comment goes
  stale silently; `--dry-run` task lists compare in under a minute and fail the `static` job when a new task joins
  `check` and no job runs it. `-x test` must travel with `-x jacocoTestCoverageVerification`: the coverage task
  depends on `test` and fails a module at 0 % without execution data
  (`modules/build-logic/src/main/kotlin/bookloom.coverage-conventions.gradle.kts`, checked; applied by `:api`,
  `:util`, `:document`, `:llm`, `:persistence`, `:pipeline`, not by `:ui` or `:app`).
- **Changes.** New `scripts/ci-verify.sh`; the header comments of the workflows and `lefthook.yml`; the "one rule"
  section of `docs/project/ci-and-hooks.md`.
- **Rejected.** Changing the hook to the split commands (three Gradle starts locally for no gain). Dropping `clean`
  from the hook (it is what makes the local gate independent of incremental state).
- **Risks.** `:app:archTest` runs in `static` without the tests before it (`shouldRunAfter` only, checked); it reads
  compiled classes, so the order does not matter.

### D32 — Caching: dependencies through setup-gradle `basic`, the build cache in its own entry, tests never cached

- **Decision.** Every job uses `gradle/actions/setup-gradle` with `cache-provider: basic` and drops setup-java's
  `cache: gradle`. The Gradle build cache is switched on only by `--build-cache` on the CI command lines (no
  `gradle.properties`), lives in `.gradle/build-cache` (set in `settings.gradle.kts`, so it stays out of the
  dependency entry), and is saved and restored by an `actions/cache` step with key
  `build-cache-${{ github.sha }}-${{ github.job }}` and restore key `build-cache-`. `build`, `static`, `test-core`
  and `test-ui` use it; the packaging legs do not. Every `Test` task is marked never cacheable
  (`outputs.doNotCacheIf` in `bookloom.test-conventions.gradle.kts`), so a test result is never loaded from the
  build cache and each pull-request commit runs every test on its fresh runner.
- **What `doNotCacheIf` does and does not do.** It stops the build cache from storing or serving a `Test` task's
  outputs. It does not switch off Gradle's ordinary up-to-date check: a second local `:ui:test` with unchanged inputs
  and its outputs still on disk reports `UP-TO-DATE`, and that local behaviour is kept. The guarantee to prove is the
  CI one — a runner has no test outputs, only a restored build cache. The check: run `scripts/ci-verify.sh test-ui`
  once, delete the test outputs (`modules/ui/build/test-results`, `modules/ui/build/reports/tests`,
  `modules/ui/build/jacoco`) while keeping `.gradle/build-cache`, run it again, and require `:ui:test` to execute
  (its line carries neither `FROM-CACHE` nor `UP-TO-DATE`).
- **Why.** `research/ci-and-release.md#b-do-not-rebuild-in-the-pr-what-the-branch-already-built` and decision 6. One
  fact the research did not have (setup-gradle documentation, read 2026-11-14): the `basic` provider caches
  `~/.gradle/caches` and `~/.gradle/wrapper` under a key that is a hash of the build files, with no restore keys. An
  entry is therefore written once per build-file state and never updated, so compile outputs for changed sources
  would never be saved. The separate entry keyed by commit is what makes "compilation is not repeated" true under
  `basic`. Undeclared test inputs (the platform font the fit tests measure with) are the reason tests are never
  loaded from a cache.
- **Promise, in the documents' words.** A pull-request job can load, for every module whose inputs are unchanged,
  what an earlier job of the same kind stored: compile outputs from the branch push's `build` job (which runs no
  analysis task), and analysis outputs only from an earlier `static` job of the same pull request; tests always
  run. A restored cache entry alone proves nothing, so task 10.9 reads the task outcomes (`FROM-CACHE` on compile
  and analysis tasks, none on a `Test` task) beside the "Cache restored from key" line. Whether a pull-request run can restore the entry written by the branch
  push (GitHub's cache scope) is confirmed in task 10.9 from the "Cache restored from key" line. If it cannot, the
  documents say that the first run of a pull request compiles once per job and later pushes reuse it; no workflow
  change follows.
- **Changes.** `settings.gradle.kts` (`buildCache { local { directory = … } }`),
  `modules/build-logic/src/main/kotlin/bookloom.test-conventions.gradle.kts`, the workflows,
  `docs/development/testing.md` (the "two switches stay off" paragraph), `docs/development/getting-started.md:36`.
- **Rejected.** `enhanced` caching: a closed-source library (owner default: `basic` first). Cached test results on
  fix-up pushes: a stale green `:ui` result after a runner image update. `org.gradle.parallel` and the configuration
  cache: the known Spotless failure, and near-zero gain on a fresh runner.
- **Risks.** One entry per commit and job counts against the 10 GB repository limit; GitHub evicts the least
  recently used. Local builds are unchanged: without `--build-cache` the directory setting does nothing.

### D33 — A push to master builds only when the commit did not come from a merged pull request

- **Decision.** `build.yml` has a `detect` job. On `refs/heads/master` it runs
  `gh api "repos/$GITHUB_REPOSITORY/commits/$GITHUB_SHA/pulls" --jq 'map(select(.merged_at != null)) | length'` and
  sets `from_pr=true` when the number is above 0. On any other branch, and when the call fails, `from_pr=false`.
  `build` has `needs: detect` and `if: needs.detect.outputs.from_pr != 'true'`.
- **Why.** `research/ci-and-release.md#a3-push-to-master-nothing-after-a-merged-pr-a-build-after-a-direct-commit`:
  the endpoint was checked on this repository (squash commit `8d507357` → PR 4; `79ffede6`, whose title has no
  `(#N)`, → PR 3; a direct commit → empty list). A commit-message test is wrong in both directions.
- **Honest wording.** After a merged pull request a run still appears, with `build` skipped and one job of about 10
  seconds. "No additional build" is true; "no run" is not possible.
- **Rejected.** `pull_request: closed` (says nothing to a `push` run). A retry loop: added only if task 10.6's check
  shows the association missing at push time.
- **Risks.** The `merged_at` filter matters: an open pull request that contains the commit is also returned.

### D34 — Release: a tag on master builds five archives and publishes them; no tests, one launch check

- **Decision.** `release.yml` starts on a push of a tag matching `v[0-9]+.[0-9]+.[0-9]+`,
  `v[0-9]+.[0-9]+.[0-9]+-alpha[0-9]+`, `…-beta[0-9]+` or `…-RC[0-9]+`. Jobs:
  1. `check-tag`: `scripts/release-version.sh "$GITHUB_REF_NAME"` repeats the check with the regular expression
     `^v[1-9][0-9]*\.[0-9]+\.[0-9]+(-(alpha|beta|RC)[0-9]+)?$` (so `v0.*` is refused), prints `full` (the tag without
     `v`) and `prerelease` (`true` when the tag holds `-`); then, with `fetch-depth: 0`,
     `git merge-base --is-ancestor "$GITHUB_SHA" origin/master` must succeed.
  2. `package`: `package.yml` with `version: <full>`, retention 1 day. Each leg runs
     `./gradlew :app:collectDist -PappVersion=<full>`, the packaging script with `APP_VERSION=<full>`, and on the four
     POSIX legs `scripts/launch-smoke.sh` with `SMOKE_EXPECT_VERSION=<full>`.
  3. `publish` (`contents: write`): downloads the five archives, checks the five expected names, writes `SHA256SUMS`,
     `gh release create "$GITHUB_REF_NAME" --draft --verify-tag --generate-notes [--prerelease]` with a notes file
     holding the first-launch steps, uploads the six files, then `gh release edit --draft=false`.
- **"No tests".** The release compiles and packs; it runs no `test`, `check` or analysis task. The launch smoke stays
  on the POSIX legs: it starts the built image once and reads four log lines (`scripts/launch-smoke.sh:82-87`,
  checked). It is a launch check of the file about to be published, not a test suite, and it is the only proof that
  file starts. The Windows archive is published unlaunched, as today; `docs/known-limits.md` says so.
- **Dry run.** Two more triggers put the same workflow into dry-run mode: `workflow_dispatch` with an input `version`
  (default `1.0.0-RC0`), and `pull_request` to `master` limited by `paths` to `.github/workflows/release.yml`,
  `.github/workflows/package.yml`, `scripts/package-*`, `scripts/jpackage-common.sh`, `scripts/launch-smoke.sh` and
  `scripts/release-version.sh`. In dry-run mode `check-tag` validates `v<version>` with the same script and skips the
  ancestor check, `package` runs unchanged, and `publish` checks the names, writes `SHA256SUMS`, uploads the six files
  as one workflow artifact `release-dry-run` and prints the `gh release create` line instead of running it. The
  publishing steps carry `if: github.event_name == 'push'`.
- **Why.** `research/ci-and-release.md#a4-release-on-a-semver-tag-on-master`, decisions 8, 9 and 12. A release runs a
  few times a year; without the pull-request trigger a change to a packaging script would first fail on a real tag.
  The dry run needs no tag, so nothing misleading is ever published. The first real pre-release, `v1.0.0-alpha1`, is
  an owner action after the merge.
- **Reuses.** `gh` on the runner with the built-in token; no release action.
- **Rejected.** Testing on a throwaway tag (a tag push is irreversible in effect: a release page and a notification).
  A third-party release action (one more pin for nothing). Making the dry-run workflow a required check (it is
  path-filtered; `verified` stays the only required check).
- **Risks.** Two pre-releases of one version carry the same OS-level number `1.0.0`; harmless for portable archives.

### D35 — Packaging scripts: five portable archives, one name pattern, no installer types

- **Decision.**
  - Output per leg is exactly one file: `BookLoom-<full>-macos-aarch64.tar.gz`, `…-macos-x86_64.tar.gz`,
    `…-linux-x86_64.tar.gz`, `…-linux-aarch64.tar.gz`, `…-windows-x86_64.zip`.
  - `scripts/jpackage-common.sh` gains `arch_word` (`arm64` → `aarch64`, everything else as `uname -m` prints it);
    `package-macos.sh` and `package-linux.sh` use it.
  - The `.dmg` step (`package-macos.sh`, last block) and the `.deb` step (`package-linux.sh:37-50`) are removed.
    Nothing reads those files: the release must not hold installer types, and the pull-request artifacts were never
    downloaded by a script. `fakeroot` leaves the prerequisites table.
  - `package-windows.bat` zips `build\package\BookLoom\` with `%SystemRoot%\System32\tar.exe -a -c -f` (the bsdtar
    Windows ships; a `tar` found first on `PATH` can be GNU tar, which cannot write zip). `package.yml` expands the
    zip and checks `BookLoom\BookLoom.exe`.
  - A version whose first number is `0` is refused by `jpackage-common.sh` and the `.bat` with a message naming
    jpackage's rule; today it silently becomes the placeholder `1.0.0` (`jpackage-common.sh`, `numeric_version`,
    checked). `dev` keeps the placeholder.
  - `scripts/launch-smoke.sh` reads `SMOKE_EXPECT_VERSION`; when set, the `app started version=` line must hold
    exactly that version. An environment variable, like the existing `SMOKE_TIMEOUT_SECONDS`, because the one
    positional argument is already the image path.
  - `scripts/launch-smoke-readonly-db.sh`, which nothing runs today, becomes a step of `package.yml` on the POSIX
    legs when the caller passes `extra-smoke: true`; `verify.yml` does, `release.yml` does not.
  - `package.yml` also lists the Linux archive and requires `BookLoom/lib/BookLoom.png` (the icon as a file,
    decision 7), requires `BookLoom.app/Contents/Resources/BookLoom.icns` on the two macOS legs and checks that its
    bytes equal the committed `BookLoom.icns` — the workflow holds no icon path of its own: it compares with the path
    `scripts/package-macos.sh --print-icon` prints (the script's own `ICON` variable, which it passes to `jpackage --icon`; the mode prints and exits before any build), so
    the move of task 11.2 needs no workflow edit — (jpackage falls back to its default icon without an error when the
    `--icon` path is wrong, which the move of D36 can cause), and prints `codesign -dv` of the macOS bundle so the
    README can state the ad-hoc signature as observed. The Windows executable's icon is embedded in `BookLoom.exe`
    and cannot be compared on the runner by a file check. Two things cover it: every packaging script refuses to
    run, with a message naming the path, when its icon file does not exist (`jpackage-common.sh` for the two POSIX
    scripts and `package-windows.bat`; task 11.2, which moves the files) — a missing file is the failure a move
    causes — and the owner checklist holds the look: the Explorer icon of `BookLoom.exe` and the Finder icon of
    `BookLoom.app` are each seen once (task 12.4).
- **Why.** `research/ci-and-release.md#d-packaging-facts`: the Windows leg uploads nothing today, macOS files are
  named `arm64` while the matrix says `aarch64`, and jpackage refuses a first number of zero on macOS.
- **Rejected.** A switch that keeps `.dmg` and `.deb` for local use (nothing uses it; it can return when an
  installer is wanted). PowerShell `Compress-Archive` (slow on a 150 MB image and a second shell in a `.bat`).
- **Risks.** The `.bat` cannot be run on the owner's machine; the Windows leg of the pull request is its proof.

### D36 — One icon source under `modules/app`; the window gets its icon

- **Decision.** The folder `docs/spec_baseline/specification/assets/icon/` is split by who reads the file:
  - `modules/app/src/main/resources/ua/bookloom/app/icon/bookloom-<n>.png` for n = 16, 32, 48, 64, 128, 256, 512 —
    the former `dist/linux/hicolor/<n>x<n>/apps/bookloom.png`. The window loads 16–256; Linux packaging passes
    `bookloom-512.png` to `jpackage --icon`.
  - `modules/app/src/packaging/icon/`: `BookLoom-source.png`, `appicon.png`, `BookLoom.icns`, `BookLoom.ico`,
    `process_icon.py`, `generate_platform_icons.py`, `README.md`. These are read only by packaging and by the
    generator; in the resources folder they would add 5.7 MB to `app.jar` that no code loads.
  - `dist/linux/BookLoom.png` is deleted: it is byte-identical to the 512 px hicolor file (same SHA-1, checked).
    `generate_platform_icons.py` writes the new layout and puts the macOS iconset under `modules/app/build/`.
    `.gitignore:6-8` (the `dist/` exception) is removed.
  - New `ua.bookloom.app.WindowIcons` (`:app`, static utility) with `apply(Stage)`: it adds the six images to
    `stage.getIcons()`. Called by `BookLoomApplication.start` and by `bootstrap/StartupFailureDialog` for its stage.
- **Why.** `research/ci-and-release.md#d-packaging-facts` (Icons): the packaging input lives in a document tree, a
  document move already broke the Windows script once, and no code calls `Stage.getIcons()` (checked: no hit in
  `modules/`).
- **Reuses.** The generated files as they are; `scripts/jpackage-common.sh` `ICON_DIR` and
  `scripts/package-windows.bat` `ICON`, repointed.
- **Rejected.** Generating icons in the Gradle build (needs Python and Pillow on every machine and runner). Icons in
  `:ui` resources (the stage is created in `:app`, and packaging belongs to `:app`). Icons on every dialog (owned
  dialogs have no task-bar entry; not asked for).
- **Risks.** macOS ignores stage icons (the Dock shows the bundle's `.icns`), so the window icon is visible only on
  Linux and Windows; the test asserts the stage's icon list, and the owner checklist holds the look on a real
  Linux or Windows machine.

### D37 — The mockup moves beside the test that reads it and stops being binding

- **Decision.** `docs/spec_baseline/specification/mockups/ui-mockup.html` moves to
  `modules/ui/src/test/mockup/ui-mockup.html` with a new ten-line `README.md` (what it is, that only
  `ThemeTokenCatalogueTest` reads it, that it no longer describes the window after this change). The 89 KB
  `mockups/README.md` is deleted: its screen descriptions are the requirements of `openspec/specs/` (265 `Source`
  lines cite it, checked). The sentence "the mockup is the binding visual reference" leaves `openspec/config.yaml`,
  `.claude/rules/javafx-ui.md`, `.claude/rules/theming-tokens.md` and the `javafx-ui-designer` and `testing-javafx`
  skills; they name `openspec/specs/` for behaviour and `theme.css` for tokens.
- **Why.** Groups 8–9 of this change remove rows and a chooser the mockup still shows; a binding reference that is
  wrong on purpose misleads. The owner allows the file to stay for now.
- **Changes.** `modules/ui/build.gradle.kts:41`, the comment in
  `modules/ui/src/main/resources/ua/bookloom/ui/theme.css`, the files named above.
- **Rejected.** Cutting the token block out into a small fixture and deleting the mockup (the owner wants it kept
  for now). `src/test/resources` (223 KB copied to the test classpath on every build; the test takes a path).

### D38 — `docs/spec_baseline` is folded by a ledger, and ids stay as labels with one place to resolve them

- **Decision.** The ledger is checked against a frozen list of the tree as it was before any file of it moved.
  Task 11.2, the first task that moves a file out of the tree, first writes
  `openspec/changes/finish-app-for-release/fold-manifest.txt`: the output of `git ls-files docs/spec_baseline` at that
  moment, one path per line (118 files on 2026-11-14; the acceptance is the list, not the number). Task 11.5 writes
  `openspec/changes/finish-app-for-release/fold-ledger.md`: one row for each path of the manifest with a verdict —
  `moved <path>`, `folded <path#anchor>`, `covered <path>` or `dropped <reason>` — the files tasks 11.2 and 11.4
  already moved included, with their new paths. The check is set equality: the paths of the ledger's rows and the
  lines of the manifest are the same set. The later tasks carry the ledger out. Verdicts by folder:

  | Source | Verdict |
  |---|---|
  | `adr/ADR-0001…0047` | folded into `docs/architecture/decisions.md`: one section `### ADR-00NN — <title>` per live decision under the seven existing theme headings, holding the decision, the reason and the falsifier, with amendments merged into the amended decision (0043 into 0009 and 0034, 0044 and 0047 into 0010, 0045 into 0036, 0046 into 0012 and 0035). An amendment keeps its own id as a heading: `### ADR-0043 — <title>`, `ADR-0044`, `ADR-0045`, `ADR-0046` and `ADR-0047` are each a short section of one or two lines that says what it amended and links to the sections that now hold its text, because 65 maintained files cite these five ids and each id must resolve by itself. Superseded or process-only records (0007, 0016, 0017, 0018, 0023, 0038) become rows of a closing table "Retired decisions" (id, what it said, what replaced it), under the heading `## Retired decisions`. So every one of the 47 ids is either a `### ADR-00NN — …` heading or a row of that table. `README.md`, `template.md`: dropped |
  | `specification/00_Foundation` | `04_DESIGN_DECISIONS.md` → a table "Design decisions (DD)" in `decisions.md` (id, one line, where it is described now); the rest dropped (vision is the root `README.md`; index and stages are history) |
  | `specification/01_Product` | dropped as covered by `openspec/specs/` (it is the source of 600+ `Source` lines); provider facts not in `docs/pipeline/llm-providers.md` and format rules not in `docs/architecture/document-engine.md` are folded there |
  | `specification/02_Architecture` | folded into `docs/architecture/overview.md` (layering and the ArchUnit rule list, error envelope, paths and startup order, threading, DI), `document-engine.md`, `ui.md`; the SQLite data model is dropped (the schema file is the truth) |
  | `specification/03_NonFunctional` | privacy/offline and accessibility text not already in `.claude/rules/offline-and-privacy.md`, `overview.md` or `ui.md` is folded there; performance, reliability and quality attributes dropped as covered by `openspec/specs/resume` and `docs/known-limits.md` |
  | `specification/04_Build_and_Release` | folded into `docs/development/quality-and-ci.md`, `testing.md`, `docs/project/ci-and-hooks.md`, `release-and-packaging.md` (which gets an "Icons" section); `04_CI_CD.md` is replaced by the pipeline of D30 |
  | `specification/05_Dependencies` | the dependency policy and the licence gate → two sections of `docs/development/quality-and-ci.md` |
  | `specification/diagrams/*.mermaid` | dropped after their nodes are compared with the mermaid blocks already in `docs/pipeline/translation-pipeline.md`; a missing stage is added to the page |
  | `specification/INDEX.md`, `IMPLEMENTED_REQUIREMENTS.md` | dropped (`openspec/specs/` is the ledger of what is built) |
  | `specification/mockups`, `assets/icon` | moved (D37, D36) |
  | `implementation_plan/01_MODULE_INVENTORY.md` | the module and package map not already in `docs/project/repository-map.md` is folded there; the rest dropped |
  | `implementation_plan/notes-corpus-verification.md` | moved to `docs/evaluation/corpus-verification.md` (three Javadoc comments cite its measurements) |
  | `implementation_plan/CHANGE_BACKLOG.md` | open rows D4, D6, D9, D11, D13, D15, D16, D18 → a section "Document engine" of `docs/known-limits.md`; D20–D25 are already there in words and keep their text until task 11.17 removes what groups 1–9 fixed; everything else dropped |
  | `implementation_plan/04_ADR_FORMAT.md`, `07_ROADMAP.md` | dropped |

- **Ids.** `ADR-00NN` is cited in 438 files outside the tree (378 under `modules/`), `DD-NN` in 82, `FR-`/`NFR-`/`EC-`
  ids in 52 outside `openspec/specs` (checked with `git grep`). They are not rewritten. An ADR or DD id resolves to
  its section or row in `decisions.md`. An FR, NFR or EC id is a label of the retired specification: the live
  requirement is found by searching `openspec/specs/` for the id, which its `Source` line still carries.
  `docs/README.md` states this in three lines and names the last commit that holds the tree.
- **Why.** The owner's rule: no ADR files, valid text into the other documents, the rest deleted. A ledger makes
  "nothing valid was lost" checkable file by file; without it the deletion is a judgement nobody can review.
- **Rejected.** Rewriting the ids out of 438 files (large diff, no reader gains). Keeping a frozen copy (the owner
  said delete; git history is the copy). One document per ADR theme (seven small pages instead of one searchable
  one).
- **Risks.** `decisions.md` grows from 87 lines to roughly 500. That is the cost of one searchable place.

### D39 — Source lines and authoring rules after the reference tree is gone

- **Decision.**
  - **Main specs.** A one-off script, kept in the scratch folder and not committed, rewrites only the lines that
    start with `**Source**:` in `openspec/specs/*/spec.md` (818 lines, 712 with a `docs/spec_baseline` path, all 818
    with "In plain words", checked).
  - **Policy: every rewritten line cites a document that survives.** The script replaces
    `docs/spec_baseline/adr/ADR-NNNN-<slug>.md[#anchor]` by `docs/architecture/decisions.md#adr-NNNN-<slug of the new
    heading>`, reading the slug from the headings task 11.6 wrote (the five amendment ids have their own headings,
    D38), or by `docs/architecture/decisions.md#retired-decisions` for one of the six retired ids; a line that cites a `DD-NN` id gets `docs/architecture/decisions.md#design-decisions-dd`; and every
    other `docs/spec_baseline/…` path, every short form of one (`01_Product/…md#…`, `` `#fr-doc` ``) and the brackets
    around it are replaced by one pointer, `docs/README.md#retired-specification-ids` — the section that says an
    `FR-`, `NFR-` or `EC-` id is a label of the retired specification and how it is resolved (D38). A line that already cites an archived change by its path (`openspec/changes/archive/…`) keeps it. About a hundred
    lines hold no `docs/spec_baseline` path at all — only ids (`FR-QA-01, ADR-0038; tasks 15e.5`), a task label, a
    bare file name (`08_UI_SCREENS_AND_STATES.md#…`, `additional-work.md`), a bare `design.md#…` of an archived change,
    or a quoted requirement. One rule covers them all, applied last: a bare `design.md#<anchor>` or
    `additional-work.md` is given the path of the one archived change under `openspec/changes/archive/` whose file
    holds that anchor (or that file); then every line that still holds no path with an anchor resolving to an
    existing file and heading gains `docs/README.md#retired-specification-ids`. The
    script keeps the ids, quoted requirement names and the gloss. So no line is left with ids and no live anchor,
    which the rule R2 below would forbid. Task 11.11 writes the section "Retired specification ids" of
    `docs/README.md` before it runs the script.
  - **Verification, by script.** `git diff -U0 openspec/specs | grep '^[-+][^-+]' | grep -v '^[-+]\*\*Source\*\*:'`
    prints nothing; the count of `**Source**:` lines is still 818 and each still holds "In plain words:";
    `git grep -n 'spec_baseline\|0[0-5]_[A-Z][A-Za-z_]*/' -- openspec/specs` prints nothing; a check in the one-off
    script lists every `**Source**:` line that holds no path with an anchor resolving to an existing file and heading
    (`docs/…#…` or `openspec/changes/archive/…`) and must list none;
    `OPENSPEC_TELEMETRY=0 openspec validate --specs` reports 17 passed.
  - **Rule R2**, in `openspec/config.yaml`, `.claude/rules/spec-authoring.md` and the `openspec-change-authoring`
    skill (whose example today contradicts the rule): a `Source` line cites where the requirement comes from — a
    `research/` or `design.md` anchor of its own change, a `docs/` page, or a decision in
    `docs/architecture/decisions.md` — then the gloss; a requirement written before this change cites at least
    `docs/README.md#retired-specification-ids` beside its ids. "16 capabilities" becomes 17 with the real list;
    "`openspec/specs/` starts EMPTY" and the "what is ultimately intended" sentence go.
  - **Decisions.** The `adr-authoring` skill is deleted. A decision that is costly to reverse is written in the
    change's `design.md` and, in the same change, added to `docs/architecture/decisions.md` as the next `ADR-00NN`
    section with its falsifier. Numbers stay permanent. The rules that said "write an ADR under
    `docs/spec_baseline/adr/`" say this instead.
  - **Size rule.** "At most ~10 tasks" becomes "about 10 tasks per group; a change the owner keeps whole is split
    into numbered groups, each a mergeable unit, the last being the gate and the hand run" in `AGENTS.md`,
    `spec-authoring.md` and `config.yaml` — what the last two changes did.
- **Why.** Dead paths in 712 lines are worse than no path; the gloss is what a reader uses. Archived changes are not
  edited (project rule), so the dead prefix remains only under `openspec/changes/archive/`.
- **Rejected.** Hand-editing 712 lines. Dropping the ids from the lines (they are the search key of D38).
- **Risks.** `openspec validate --specs --strict` is not a usable check: it fails today on 11 of 17 specs with 109
  "requirement text is very long" warnings (run on 2026-11-14). The non-strict run is the check; see D41.

### D40 — A document check script is the acceptance test of the cleanup and a step of the `static` job

- **Decision.** New `scripts/check-docs.py` (standard library only) fails when (1) a maintained tracked file contains
  `spec_baseline` — maintained means every tracked file except `openspec/changes/**` (active and archived changes
  are planning history: this change's own design, tasks, research, fold manifest and fold ledger name the tree on
  purpose) and except the checker's own source and tests (`scripts/check-docs.py`, `scripts/tests/test_check_docs.py`,
  which must spell the prefix they look for); (2) a relative Markdown link in `README.md`, `AGENTS.md`,
  `docs/**`, `.claude/**` or `.agents/**` points at a missing file, or at a missing heading of a Markdown file;
  (3) an `ADR-00NN` id cited in a tracked file outside `openspec/changes/**` has no section or retired row in
  `docs/architecture/decisions.md`; (4) a path in backticks that starts with `docs/`, `scripts/`, `modules/`,
  `config/`, `tooling/` or `.github/` in `AGENTS.md`, `.claude/rules/*.md` or `docs/project/*.md` does not exist.
  Each rule keeps its own scope as written here; the exclusion of rule (1) is the same in the script, in its tests
  and in every command a task uses to confirm it (`git grep -l spec_baseline -- . ':!openspec/changes'
  ':!scripts/check-docs.py' ':!scripts/tests/test_check_docs.py'`). The script is written in task 11.1 and becomes a
  step of the `static` job in task 11.12, when the repository passes it. Tests: `scripts/tests/test_check_docs.py`.
- **Why.** 265 files cite the tree; `AGENTS.md` already cites a `docs/proposals/` file that does not exist
  (checked). Only a mechanical check keeps the documents true after this change too.
- **Rejected.** A Gradle task (documents are not a build input). A pre-push step (the hook's identity with the gate
  command is kept exact; the `static` job is where CI-only gates live).

### D41 — Spec housekeeping left by the last change: what is done here and what is not

- **Decision.** Done: the scenario titles that still name the old flow are renamed (checked today: only
  `openspec/specs/export/spec.md` "The report holds no section of consistency notes" still matches the follow-up's
  search; `document-round-trip` "A reviewer's edit reaches both labels" means a person and is renamed "A person's
  edit reaches both labels"); the two requirements "Keep a segment with nothing to translate as it is" and "Report
  tokens per second from the recent drafts" are reread and reworded where they name the old flow. Already done by
  the last change and only confirmed: the three `## Purpose` paragraphs name the three stages and the export without
  a model call (read 2026-11-14). A renamed scenario that also appears in a MODIFIED block of this change's deltas is
  renamed there in the same task, because OpenSpec refuses a MODIFIED block that drops an original scenario name. The
  plain-words glosses of three unchanged `translation-pipeline` requirements that still name chapter summaries ("End a
  model call that outlives its ceiling", "End a batch at a chapter boundary", "Define a chapter by one rule for every
  format") are reworded in the same task. The scenario titles this change leaves stale (listed at the end of this
  document) are renamed in the main specs after the change is archived, as a workflow follow-up.
- **Not done.** Splitting long legacy requirements. There are 109 of them in 11 specs; each split needs new headings,
  scenarios moved between them and a reading of the behaviour — not mechanical. `docs/known-limits.md` records the
  number and that `--strict` validation of the main specs fails on it.
- **Why.** `openspec/changes/archive/2026-11-12-redesign-translation-pipeline/tasks.md` "Workflow follow-up".

### D42 — Scripts and clutter: a verdict for each

| Item | Verdict | Reason |
|---|---|---|
| `scripts/launch-smoke-readonly-db.sh` | wired into `package.yml` for pull requests (D35) | it proves a startup path only the packaged image has |
| `scripts/model-knowledge-check.py` | kept | the maintenance check for the bundled CSVs in `modules/llm/src/main/resources/ua/bookloom/llm/knowledge/`; `docs/development/testing.md:17` documents it |
| `scripts/segment-histogram.py`, `scripts/tests/test_segment_histogram.py` | deleted | a one-time measurement of group 15d; only its own test and two document lines name it |
| `scripts/tests/` | run by the `static` job (`python3 -m unittest discover -s scripts/tests`) | four test files that nothing runs today |
| `scripts/eval-matrix.sh:15,53,55,62,76`, `scripts/eval-score.py:2` | comments repointed to `docs/evaluation/eval-suites.md` | they cite sections of a page that is now a link list |
| `docs/DEVELOPMENT.md` | deleted | a 16-line link page; `docs/README.md` is the index; only `docs/README.md` and the two scripts cite it (checked) |
| `docs/screens/*.png` | retaken after groups 8–9 | they show removed rows and the removed chooser |
| `lefthook.yml` | stale comments fixed ("release tooling arriving in change 27", the `ci.yml` identity text, "license and SCA gates") | |
| `.claude/rules/gradle-build-and-quality.md` | body checked against `modules/build-logic` and the five archives of D35 | its `paths:` already say `modules/build-logic/**` (checked) |

The private agent memory index (`~/.claude/projects/-Users-ok-Development-GitHub-book-loom/memory/MEMORY.md`) lies
outside the repository: task 11.17 lists the entries that describe finished groups and the owner removes or confirms
them.

### D43 — Static-analysis leftovers: a verdict for each

| Item | Verdict |
|---|---|
| Suppression of `BannedLoggerFactoryCall` for `BookLoomApplication.java` (`config/checkstyle/suppressions.xml:45-49`) | removed; the class uses `@Slf4j` |
| `java.util.Date` in `ui/state/LastOpened.java:43` and `ui/screen/ModelRefusedSection.java:118` | both call one new method `Messages.icuDay(LocalDate)` in `ua.bookloom.ui.i18n.Messages`; `java.util.Date` then appears in one place, with a comment that ICU's `{0,date}` argument needs it |
| 31 main files at 395–400 lines | no action unless a task touches one. Where this change makes one grow, the decision names where the new code goes: `WorkList` (400) — the candidates are selected in `LastDrafts` (D4); `SuggestTargets` (399) — nothing moves in (D8, D16); `GlossaryServiceImpl` (396) — the title scan is called inside `FrequencyScan` (D16); `ImportViewModel` (400) — the fold flag is `AboutFoldState` (D24); `SearchableCombo` — the popup part is `ChoicePopup` (D25); `CooccurrenceLearner` (393) — the candidate listing is `LearnedCandidates` (task 5.2); `ProviderTestView` (395) — the figures pane's builder moves out if the edit of task 9.5 would pass the limit; the consistency pass is five parts (D17); `BookBrief` (399) only loses components. Any other touched file that would pass 400 lines is split by job in the same task, and the task's commit names the new class |
| `pipeline/heal/QualityLoop.java` | not renamed: 31 files name it (9 main, 22 test, checked), 11 test files carry the name, and groups 1–7 edit the same package; `docs/known-limits.md` gets no entry — a name is not a limit |
| `docs/development/testing.md:114` test count | replaced, in task 12.1, by the count of that task's gate run |
| Local Sonar | scan at the close of every group (tasks preamble) and in task 12.2; 0 open issues, quality gate OK |

### D44 — The final group: gate, Sonar, hand run from a fresh folder, release dry run, owner checklist

- **Decision.** Group 12 runs, in order: the gate from clean and the strict lock check; the Sonar scan
  (`export SONAR_HOST_URL=http://localhost:9000`, `export SONAR_TOKEN=…`, `./gradlew sonar -PsonarLocal`,
  `SONAR_PROJECT_KEY=local:bookloom sonar-local report`, from `docs/development/quality-and-ci.md:19-44`; the owner
  starts the server); the window walked by hand on a real book with `BOOKLOOM_DATA_DIR` set to an empty folder, once
  in English and once in Ukrainian, recording clicks, decisions asked, wall time, flagged count and the number of
  paragraphs left in the source language (must be 0) in `hand-run.md` of the change; the pull request's `verified`
  check and the dry-run `release.yml` run green, with the five names and `SHA256SUMS` in the `release-dry-run`
  artifact; `openspec validate finish-app-for-release --strict`; `owner-checklist.md` in the change.
- **Why.** The project rule (final group = gate and hand run) and the Definition of Done in `AGENTS.md`.
- **Rejected.** A release dry run on a tag (D34).

## Risks / Trade-offs

- [A wrong majority is made uniform by the consistency pass] → the rendering report shows every pinned row with its
  counts, the person's target wins on the next run or retry, and task 5.8 counts wrong pins on two whole books before
  the thresholds (80 %, 5 paragraphs, 10 for an empty row) are kept (D17).
- [Fewer characters carry an enforced gender, because a pronoun tally no longer verifies one] → verified votes stay
  "likely" and one press confirms them; task 3.10 counts enforced genders before and after on the names gate, with
  0 wrong ones as its pass rule (D6).
- [Removing the context blocks lowers quality on books unlike the two panel slices] → task 4.9 repeats the panel and
  the name-form, narrator-gender and flag counts; a missed figure is reported to the owner with the numbers (D12, D15).
- [The last draft is built on a guess that a second draft does better] → task 2.2 replays the recorded cases first
  and the step is built only if it recovers at least half of them; a kept translation with a missing sentence stays
  FLAGGED, is counted on the Export screen and listed on the review desk (D4, D5).
- [The equal-score rule keeps a neutral or bad proofread edit] → task 4.9 reads the edits kept at an equal score
  blind and removes the rule unless better outnumbers worse; a tie or no case removes it (D11).
- [The time before the first paragraph grows by the two Prepare steps, and a pause inside a step throws its calls
  away] → the steps announce themselves through the run's events, and a step writes once, in one all-or-nothing
  write, so a repeat is safe (D20).
- [The run saves a suggested brief and then translates with the old one, or the window overwrites it] → the run
  builds its style sheet, frame and settings from the project as stored after Prepare, the acceptance test reads the
  first draft request, and the window takes the stored brief when the run leaves Prepare (D20).
- [The merge at Max is a selection effect of the review's fourteen variants] → it is measured in the app's harness
  first and removed in this change unless it passes the gate of D22.
- [The wide deletions (recurring terms, chapter summaries, brief keys, Start cards) break the build half-way] → each
  is cut writers first, readers next, the contract last, and every task lists the readers in every module, both
  bundles and their tests, so each task ends with every module compiling and green (D7, D13, D26).
- [The combo box's filter order is observed behaviour of the toolkit, not documented API] → research tests 6–13 pin
  it, row clicks are tested with the robot and with a fired press and release, and one hand check on macOS is part of
  task 8.2 (D25).
- [A direct commit to `master` is compiled and never tested; the Windows archive is published unlaunched] → the
  owner's ruleset forbids direct pushes, and `docs/known-limits.md` states both (D30, D34).
- [The cache claim and the pull-request wall time are estimates] → task 10.9 reads both from the first runs and
  corrects the documents (D30, D32).
- [Folding `docs/spec_baseline` loses valid text] → the fold ledger gives one verdict per file of the frozen manifest and
  `scripts/check-docs.py` fails on a dead path, link or ADR id (D38, D40).
- [The panel's readers are two model families and one person on 20 paragraphs] → the validity gate of task 1.7 must
  separate a known-better arm from a known-worse one before any later claim rests on the panel (D1).

## Migration Plan

**Stored data.** No migration is written (ADR-0043). The stored shapes listed in D9 change; the schema file and its
`PRAGMA user_version` do not (D9). At the first launch of a build that holds any of these changes
the application renames `bookloom.db` to `bookloom.db.old-<yyyyMMdd-HHmmss>`, creates a fresh database, writes one WARN
line and raises one warning message; providers and settings are entered again, and the first-launch look for local
models runs once more (ADR-0044). The command line stores nothing and is unaffected. Rolling back to an older build
sets the new file aside in the same way.

**Build order.** The preamble of `tasks.md` holds the one statement of the order: the groups run serially in number
order, and only group 10 may run beside groups 1–9.

**Release order.** (1) The pull request of this change runs `verify.yml` and the dry run of `release.yml` (tasks
10.9 and 12.4). (2) The owner applies the repository settings listed below. (3) The owner merges with a squash; the
`build` run on `master` shows its build job skipped. (4) The owner starts the dry run once more on `master`, then
pushes the tag `v1.0.0-alpha1`; `release.yml` builds, launches the four POSIX archives, and publishes five archives
with `SHA256SUMS` as a pre-release. A failed release run publishes nothing: the release is created as a draft and
made public only after the six files are uploaded. A bad tag is deleted by the owner with its draft.

## Owner actions outside the repository

Not tasks; nothing in the repository can do them.

1. Repository settings → allow squash merging only (decision 10). The lookup of D33 is then the tested case.
2. A ruleset for `master`: require a pull request and the status check `verified`; no direct pushes (decision 11).
   Do not require any other check: `release.yml` is path-filtered on pull requests.
3. A ruleset for tags `v*`: only the owner may create or delete them.
4. After the merge: look at the `build` run on `master` — `build` must show as skipped (D33).
5. After the merge: `gh workflow run release.yml -f version=1.0.0-alpha1` (dry run), then push the tag
   `v1.0.0-alpha1` as the first real pre-release.
6. Prune the private agent memory entries listed by task 11.17.

## Decisions taken by default that the owner may overrule

One line each: the question, then the default this change builds.

1. The recommended model's name and pull tag — default: every text says `gemma4:26b`; the `ollama pull` command shows
   the tag the app holds today, `gemma4:26b-a4b`; gemma4:31b is no longer offered (D21).
2. Start with no target language — default: a new small dialog "Translate into which language?", not the existing
   "Translate into another language" dialog; a language pair the book already has is not started there (D23).
3. Which steps open without a target language — default: Translating opens, Names & style stays locked (D23).
4. The "Cannot start yet" banner — default: only on Names & style and Translating, never on Import although Import
   offers Start; a missing model disables Start on Import with a tooltip (D21, D23).
5. Start translation on the Import toolbar — default: offered as the main action beside Continue; it opens the
   Translating screen (D21).
6. The target language of a new book — default: the last one used, never the operating system's language (D21).
7. The quality of a new book — default: "By the AI model": Fast on a small model (under 14 billion parameters, and
   every unknown model), Balanced otherwise, chosen at the first start; the person's choice is never changed (D21).
8. A Prepare step that fails — default: the run goes on with the code scan's result and the step is not tried again;
   an unreachable or timed-out provider is waited for (D20).
9. A Prepare step that is paused or interrupted — default: it runs again from its first call; nothing partial is
   stored (D20).
10. The brief the run suggests — default: written without an Apply press, every filled field marked likely and
    editable; pressing Start is the consent (D20).
11. The model name scan — default: never sent from a run; only from the Names & style button (D20).
12. Names & style during the run's names step — default: read-only, as under a hidden names pass (D20).
13. Consistency thresholds — default: a row agrees at 5 paragraphs and 80 %; an empty one-word row is filled at 10
    paragraphs; only character, place and title rows are swept; task 5.8 confirms them (D17).
14. The dialogue style of a book — default: the style most of its paragraphs use; on a tie or under 10 speech
    paragraphs the dash where the language's typography file sets `dialogue-dash`, quotation marks elsewhere (D18).
15. Where the rendering report is shown — default: a closed fold on the Export screen, counted again from the stored
    texts each time the screen reads its figures, with a link from a split row to Names & style (D19).
16. Chapter summaries — default: removed whole, with the Book Brief switch and the Story shape field, not switched
    off (D13).
17. The context of a translate request — default: name rows, previous pairs, the next paragraph and a reminder of at
    most two lines; the earlier-chapters block, the recently-present card, recurring-term rows and the dialogue
    reminder are removed; task 4.9 decides whether this stands (D12).
18. The reply format — default: the enforced JSON reply stays, with the runaway reply recovered in the parser;
    numbered lines are not adopted (D14).
19. The last draft — default: built only if a replay of the recorded cases recovers at least half of them (task
    2.2); then one more single draft per paragraph with no translation, and a real translation that still fails a
    check such as the sentence count is kept, flagged and exported. Otherwise not built (D4).
20. What "no translation" counts — default: the paragraphs export would write in the source language for want of a
    usable translation; a refused reply export will write is not counted, a translation whose formatting marks
    cannot be restored is (D5).
21. A proofread edit at an equal score — default: a small edit is kept for a marked paragraph only if task 4.9 finds
    more such edits better than worse; a tie or no case removes the rule (D11).
22. The proofread reply as a list of edits — default: built as a change of reply form for time, measured as its own
    step; a missed figure is reported before group 5 (D11, D15).
23. The missing-name-form mark and titles — default: a title row is skipped by the mark, because a translation may
    rightly drop a title and all 17 false items of the traced run were titles (D10).
24. The merge at Max — default: drafts differ by seed and temperature only (0.2 and 0.4, merge at 0.2), on a large
    model only, for marked paragraphs only; removed in this change unless task 7.1 passes the gate of D22 (at least
    60 marked paragraphs read, a mean gain of at least +0.3 with twice as many better as worse, at most 15 % more
    whole-run time). The gate is stricter than the owner's sentence in one case: a clear gain that costs more than
    15 % fails and is reported with its numbers (D22).
25. The hard g — default: no suggestion is refused for writing ґ or г; the two letters count as one when forms are
    compared (D26).
26. The "About this book" fold — default: open with every book; the person's collapse is remembered until another
    book is opened and is not stored (D24).
27. The model facts — default: shown under the provider's model picker for a saved provider only (D27).
28. Polish scope — default: every High and Medium audit item and 16 Low items; the Low items listed in D29 are left.
29. Where measurements are recorded — default: one page, `docs/evaluation/results-finish-release.md`, for groups 1–7,
    because `docs/DEVELOPMENT.md` is deleted (D2, D42).
30. Who reads a blind sheet — default: an agent session of a Claude model and one of an OpenAI model, started by the
    owner, and the owner on 20 paragraphs; book text leaves the machine only by that action (D1).
31. A direct commit to `master` — default: compiled, not tested; a branch push and its pull request both build
    (D30, D33).
32. The release — default: no test suite; one launch check per POSIX archive; the Windows archive published
    unlaunched; a tag `v0.*` refused; `.dmg` and `.deb` outputs removed from the packaging scripts (D34, D35).
33. The mockup — default: kept, moved to `modules/ui/src/test/mockup/`, no longer the binding visual reference (D37).
34. Decision ids — default: `ADR-00NN`, `DD-NN`, `FR-`, `NFR-` and `EC-` ids stay in code and specs as labels and
    resolve in `docs/architecture/decisions.md` and `docs/README.md`; no separate ADR file is written from now on
    (D38, D39).
35. Long legacy requirements — default: not split; `openspec validate --specs --strict` fails on 109 of them today,
    so the non-strict run is the check for the main specs and the strict run for this change (D39, D41).
36. Stale scenario titles — default: left as listed below until the change is archived, then renamed in the main
    specs.

## Scenario titles left stale by the validator

OpenSpec refuses a MODIFIED block that drops or renames a scenario of the main spec. Where a requirement changed and
its scenarios could keep their place, the title was kept and the body rewritten; the titles below no longer describe
their body. Where a scenario had to go, the requirement was restated instead (REMOVED, then ADDED under a new
heading), and no stale title was left. The titles are renamed in the main specs after archive (D41).

- `app-shell` — "Keep list and detail side by side and stack cards where they do not fit": scenario "Names & style is side by side when wide"
- `app-shell` — "Keep list and detail side by side and stack cards where they do not fit": scenario "Names & style stacks below wide"
- `app-shell` — "Pin the run's banners above the content on every screen": scenario "A missing target language leads to the Book Brief"
- `app-shell` — "Show all model work in one progress dialog": scenario "Chapter summaries and proofread windows are steps"
- `app-shell` — "Show the live card's Live and Activity log tabs": scenario "Proofread and summary entries are in the Activity log"
- `app-shell` — "Translate a book into another language without importing it again": scenario "Recurring terms are copied with empty translations"
- `book-brief` — "Carry every brief choice into the run": scenario "A policy key reaches the model"
- `book-brief` — "Check the narrator's pending quotes again after the names": scenario "A book Start would not ask about is not re-checked"
- `book-brief` — "Check the narrator's pending quotes again after the names": scenario "A failed or empty re-check asks as before"
- `book-brief` — "Choose the quality dial and show what it turns on": scenario "Balanced is the default and says what it does"
- `book-brief` — "Judge the narration when the run starts when the import could not": scenario "A first-person Markdown book is asked about"
- `book-brief` — "Judge the narration when the run starts when the import could not": scenario "A first-person Markdown book with an Auto source is asked about"
- `book-brief` — "Show what the suggestion decides with its agreement and verified quote": scenario "The story shape shows its two quotes"
- `glossary` — "Check a suggested target against the target language's rules": scenario "A declared convention is followed"
- `inference` — "The pseudo model answers every call kind with the text in capitals": scenario "A chapter summary is the text in capitals"
- `local-storage` — "Keep the application's work across a restart": scenario "The translation memory and the summary survive a restart"
- `local-storage` — "Remove a project's saved work and nothing else": scenario "Chapter summaries and verdicts go with the project"
- `local-storage` — "Store each distinct fixed part of a run's prompts once per project": scenario "A segment with no chapter summary stores no key"
- `quality-gates` — "Normalise apostrophes, ellipses and spacing": scenario "The person chose the straight apostrophe"
- `quality-gates` — "Write quote marks in the target language's convention": scenario "The person chose another pair"
- `review-queue` — "Retry a segment with a context rebuilt from the current glossary and settings": scenario "A retry uses the current recurring-term rendering"
- `settings` — "Recommend a model by the computer's memory where models are chosen": scenario "A 48 GB computer"
- `settings` — "Show the licence note of a model with a restricted licence": scenario "The note on the Models tab"
- `settings` — "Show the thinking hint for a download that cannot turn thinking off": scenario "The hint on the Models tab"
- `translation-pipeline` — "Draft consecutive segments in token-budgeted batches": scenario "A learned rendering belongs to one term"
- `translation-pipeline` — "Draft consecutive segments in token-budgeted batches": scenario "A stored chunk teaches the lexicon"
- `translation-pipeline` — "Draft consecutive segments in token-budgeted batches": scenario "Reported terms are verified before they count"
- `translation-pipeline` — "Draft consecutive segments in token-budgeted batches": scenario "The dictionary form and the common word"
- `translation-pipeline` — "Draft consecutive segments in token-budgeted batches": scenario "The first decision wins and a name gets its spelling"
- `translation-pipeline` — "End every translate-family call with a short reminder": scenario "Marks and dialogue in the batch"
- `translation-pipeline` — "Map the quality dial to the run's mechanics": scenario "Every model gets the same dial"
- `translation-pipeline` — "Write each injected map item as one line of its target, type and settled gender": scenario "A name the person chose to gloss carries its meaning at its first use"
- `translation-pipeline` — "Write each injected map item as one line of its target, type and settled gender": scenario "A rule's gender is sent but not enforced"
