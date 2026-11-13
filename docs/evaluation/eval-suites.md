# Evaluation suites and procedures

*How each prompt-eval suite is run and read: prompt eval, real-run corpus, sequence eval, batch arms, coref probe, stage suites, replay, whole-book run, scoreboard, corpus eval, draft eval. Measurements recorded when each was added stay in the text as evidence.*

**The prompt eval** (`@Tag("promptEval")`, `PromptEvalTest`) sends 26 fixed English → Ukrainian cases, and one batch of
the fixture book's names given suggested targets (four rows, one per name), through the production prompt builders to a
real Ollama model (`BOOKLOOM_EVAL_MODEL`, default `gemma4:e4b-mlx`; `BOOKLOOM_EVAL_ONLY=<case-name prefix>`, such as
`suggest`, runs only those cases; skipped when `BOOKLOOM_EVAL_OLLAMA_URL` is unset) and measures each reply before any
repair: it parses, its tokens are intact with every pair around words (gate), it is in Cyrillic, it holds the case's
marker (a glossary rendering, a correct drop cap, a translated instruction, a name's uninflected dictionary form), and
the table lands in
`modules/pipeline/build/reports/promptEval/<model>.txt`; the task fails below parse 95% or gate 90%
or a defect threshold.

*How the eval builds a request (15e.1).* An eval sends what the app sends. Each case becomes an `EvalProject`
(test side): its glossary entries and recurring terms go into the in-memory stores, its segments form one unit with the
earlier pairs, summary and narrator the case states (`EvalContext`; a case that states none gets none), and the request
is built by the run's own `PromptRequests` (`ua.bookloom.pipeline.run`) — the same code the job calls for a chunk's
context, a batch's fit and previous pairs, the draft and its repair. A locked name is written out in the case text and hidden behind the run's token by
the real mask. Every model call goes through `JobModelCalls`, so the request is sized to the window and the reply cap
exactly as in a run; the window is the context length the provider reports for the model, limited to 16,384 tokens as a run limits it (16,384 when it reports none), and `BOOKLOOM_EVAL_WINDOW` overrides it; the chosen window is logged at INFO (`Eval window …`) and in the report line. The brief the requests carry is the defaults unless `BOOKLOOM_EVAL_PRESET` (`burning-chrome`: formal literary register, names translated, Balanced, cyberpunk genre — one model's frozen answer, kept so earlier reports can be measured again), `BOOKLOOM_EVAL_GOLD` (the owner's gold file for the book, [judgement evals](#judgement-evals): its brief's register, names, genre, voice, audience and narrator — prefer it for a new measurement), `BOOKLOOM_EVAL_BRIEF` (a JSON file with `register`, `names`, `genre`, `voiceEra`, `audience`, `dial`, `balance`) or `BOOKLOOM_EVAL_REGISTER` / `_NAMES` (`translate`, `transliterate`, `keep`) / `_GENRE` / `_DIAL` say otherwise The equality of the
two paths is held by `PromptRequestsEquivalenceTest`. Not covered by this path yet: the name prescan, the glossary
review and the chapter-summary call (`ChapterSummaryCall`, 12.4), which build their own frame (deferred from 15e.2 to 15e.13, which tunes those
prompts).

## The fast loop {#fast-loop}

Whole suites cost hours, so a prompt change is iterated in minutes and confirmed once. The loop, narrowest first:

1. **Probe** one case: `scripts/probe.sh --case <id>` sends it through the production builders (`ProbeTest` keeps it
   honest) and prints each message split by section, the request parameters (`num_ctx`, max output, temperature, seed,
   format, think), the raw reply, the parsed result with the verdict the suite scores, and per call the usage
   (`prompt`, `completion`, `promptEvalMs`, `generationMs`, `cachedPrompt`, `wallMs`). `--list [prompt|defects|realrun]`
   names the ids, `--repeat N` sends it N times, `--dry` prints the request and calls nothing, `--provider pseudo` is the
   offline model of the tests, `--model`/`--url`/`--window` default to `gemma4:e4b-mlx`, the provider's local address and
   16,384. Editing a `.prompt` file or an eval JSON needs no build: the probe reads the source resource directories first.
2. **Quick**: `scripts/eval-matrix.sh --quick` (`BOOKLOOM_EVAL_QUICK=1`) runs ten prompt cases (`QuickEval.PROMPT_CASES`,
   no defects corpus), the realrun cases marked `"quick": true`, one invented book for `names`, and `mini` in place of
   `sequence`. `--book-file <path>` runs one owner book of the names and gold suites.
3. **Mini**: `scripts/eval-matrix.sh --suite mini` (the invented 33-block book, end to end).
4. **One full run per suite** to confirm, on e4b (the default model; `--all-models` restores the list).
5. **26b once**, at the end of a change.

Keep the model and its prompt cache warm: start Ollama with `OLLAMA_KEEP_ALIVE=1h` in the *server's* environment (not
the client's), and keep the window constant (16,384) so a GGUF model is never reloaded. Every Gradle run and every model
call takes `scripts/gradle-lock.sh` (one at a time across agents; `BOOKLOOM_GRADLE_LOCK_DIR` moves the lock off the
owner's default path), so never wrap `probe.sh` or `eval-matrix.sh` in it.

Reading `cachedPrompt`: it is filled from Ollama's `prompt_eval_cached_count`. On Ollama's MLX runner `prompt_eval_count`
is the whole prompt and `prompt_eval_duration` only the part that was not cached, so `promptEvalMs` falls on a repeated
prompt while `prompt` does not; the share of the prompt served from the cache is `cachedPrompt / prompt`. A first probe
after a model load shows `cachedPrompt` of 0 or `-`; send the same case again (`--repeat 2`) to see the shared system
block reused.

**Real-run corpus (15e.2).** The defect classes of the 6 h 17 min GUI run (Bartimaeus 1, en → uk, 26b) as synthetic cases
under `modules/pipeline/src/test/resources/eval/realrun/` (no book text; failure shapes only), run as their own suite:

```bash
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx BOOKLOOM_EVAL_STABILITY=3 \
  BOOKLOOM_EVAL_SUITE=realrun ./gradlew :pipeline:promptEval          # e4b (BOOKLOOM_EVAL_ONLY=<case-id prefix> narrows it)
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:26b-mlx BOOKLOOM_EVAL_STABILITY=3 \
  BOOKLOOM_EVAL_SUITE=realrun ./gradlew :pipeline:promptEval          # the 26b class
scripts/eval-matrix.sh --suite realrun --models "ollama:gemma4:e4b-mlx ollama:gemma4:26b-mlx" --stability 3   # both, one table
```

| File | Kind (report group) | What it holds |
|---|---|---|
| `text.json` | `quotes` (10), `mixed-script` (6), `invented-word` (3), `russian-letters` (5), `narrator` (6), `short-line` (6) | one source with a clean and a defective Ukrainian candidate (`good`, `bad`), the production check that decides it (`QUOTES`, `SCRIPT`, `GENDER`, `LENGTH`, `NONE`), a glossary and the context (earlier pairs, summary, lexicon, narrator) |
| `batch.json` | `batch-terms`, `batch-context` | a batch over recurring lexicon terms (it asked for `terms` until arm 5b; the parser now ignores the field), with scripted replies of the shapes the run produced: correct, the `terms` JSON leaked into a target (`«terms»: {…}}, {`), a code fence in a target, a far-too-short target |
| `repair.json` | `repair-placeholder`, `repair-structural` | a Markdown source the real parser masks, the reply a run refuses and two scripted answers for the production document gate |
| `scan.json` | offline only | the key-term and name scans on synthetic lines (titles, `Great Hall`, `Old Bailey`) |

Every request of a model run is the run's own (draft, batch, repairs). Each label of a deterministic case is held to the production code by plain
offline tests (`RealRunCorpusTest`, `RealRunRunnerTest`; no model, part of `check`): the text checks, `GenderChecks`, the
draft evaluation's length check, `BatchReplyParser` with `ItemValidator`, the real document gate, `KeyTermScan`.
`deterministic: false` marks a case no production check can judge (the Russian-only letters `ы ъ э ё` until the alphabet
check of 15e.11; invented words, since no dictionary is bundled; an ASCII `"` inside « »; a gender slip behind `нічого не`),
so it counts for the model run only.

*Known failures.* `knownFailure: true` marks a case production gets wrong today, with `fixedBy` naming the task: the
generic words `great`, `hall` and `old` that `KeyTermScan` proposes (15e.11). They are
reported beside the rates (`+Nk` in the matrix, `known` in the table), never inside one, and the offline test fails when one
starts passing, so the fixing task drops the flag in the same change.

*Cases for the translation fixes (15i.E4).* They are red before 15i.T1–T7 and say so: `replies.json` holds the kept brand,
magazine title and identifier (`kept-run`; the first two known failures of 15i.T4) and the ти/Ви mix in one speech
(`address-mix`; a known failure of 15i.T6) as replies the draft judge must keep or send to a fix; `text.json` holds the
same kept runs as `kept-run` cases decided by the blocking Latin-run gate (`LATIN_RUN`); `injection.json` is a batch of a
plain sentence and two instructions to the translator, measured only by the model run (`injection/batch`, the detail
reads `obeyed=N`; offline tests prove the patterns). The cases that need a whole job, a consistency pass or a call of
their own (a stray `\u001e` at the start of a directed fix, a fix that wraps tokens in a tokenless segment, a gender that
is only suggested, a redraft that mends a mixed-script word and drops one dash, `data → data`) are tests wrapped in
`KnownRed.until("15i.Tn", id, () -> …)`: the assertions are the behaviour the task delivers, they must still fail while
the task is open, and the wrapper's message tells the fixing task to drop it. `eval/long-paragraphs.json` holds twenty
public-domain paragraphs of more than 180 words (title, author, chapter, Project Gutenberg number) for 15i.X3. The
sequence report adds LTCR-1 (DelTA, ICLR 2025), as implemented in `SequenceMeasure`: for each fixture term and name, the decided
segments with a target are read in order, and a segment counts only if its source names the term and its target carries at least one
listed rendering or spelling. The first such segment fixes the term's rendering (the first listed one when it carries several); each
later such segment counts once, per segment and not per occurrence, as kept when its target carries that rendering. A later segment
whose target carries no listed rendering (echoed, untranslated, flagged without a target, or an unlisted form) is skipped and counts for
neither side. The ratio is the kept count over the later segments, summed over all terms and names. It is -1, printed as `n/a`, when no term has a
later segment, because a run that repeated nothing is not 0 % consistent.

*Report.* Rows are grouped by kind and call (`quotes/draft`, `quotes/repair`, …); a rate is
the share of counted cases that came out as labelled. Extra metrics: `truncated` (calls whose reply stopped with
`finish=LENGTH`), `tooShort` and `leaked` (share of batch items whose target is far shorter than the source, or carries the
`terms` object, an `id` entry or a fence), and `stability` (rows whose `BOOKLOOM_EVAL_STABILITY` repeats agree).
`tooShort` and `leaked` also appear in the batch A/B (`--suite batch`), and every case-set report now ends with the share
of corpus cases right per kind (`byKind` in its JSON, a second table in the matrix). No threshold is set yet: 15e.4 records
the "before" numbers and the thresholds per model class from this suite.

**Sequence eval (15e.3).** Every case above stands alone; the defects of the 6 h run (a title drifting between renderings, a
name in two spellings, a narrator's gender slipping, English paragraphs exported as source) only show over many segments
with the context accumulating. The sequence suite runs a synthetic book through the *real batched job* — preparation, token-budgeted
batches, the repair path, the Proofread stage, the lexicon and its learner — against a real
model, unattended, then reads what the run decided and measures it with production checks only.

```bash
# e4b, the real run's brief (no narrator), then the same book with a first-person male narrator in the brief
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx BOOKLOOM_EVAL_SUITE=sequence \
  BOOKLOOM_EVAL_NARRATOR=unset ./gradlew :pipeline:promptEval
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx BOOKLOOM_EVAL_SUITE=sequence \
  BOOKLOOM_EVAL_NARRATOR=set ./gradlew :pipeline:promptEval
# 26b: the same two commands with BOOKLOOM_EVAL_MODEL=gemma4:26b-mlx (BOOKLOOM_EVAL_DIAL=FAST|BALANCED|MAX, default BALANCED; BOOKLOOM_EVAL_WINDOW as for the other suites)
scripts/eval-matrix.sh --suite sequence --narrator both --models "ollama:gemma4:e4b-mlx ollama:gemma4:26b-mlx"   # all four runs, one table
```

*Runtime.* About 15-25 minutes per narrator mode on e4b and 40-70 minutes on a 26B class model (about 330 segments, Balanced), so
the matrix timeout for this suite is 5400 s per run instead of 1500 s; `MODEL_TIMEOUT=<seconds>` overrides it. LM Studio:
`BOOKLOOM_EVAL_PROVIDER=lmstudio` with its `/v1` URL, or `--models "lmstudio:<id>"`.

*Fixture* (`modules/pipeline/src/test/resources/eval/sequence/`, no text from a real book). The 58-paragraph book of the first cut was
too easy: a 6 h run of 3,783 segments had 1.7 % of its segments fail a quote or script gate in the first round, 28 narrator
gender slips, 186 batch fallbacks in 722 batches and drifting terms, none of which 58 paragraphs can reproduce at those rates.
`book.md` is now *generated* by `SequenceBookGenerator` (test source, fixed seed, JDK only) from the original sentences and slots of
`SequenceBookLibrary` and `SequenceTopic`, and committed; `SequenceFixtureTest` checks that regenerating gives the same bytes.
After changing a pool, rewrite it with
`java -cp modules/pipeline/build/classes/java/test ua.bookloom.pipeline.eval.SequenceBookGenerator modules/pipeline/src/test/resources/eval/sequence/book.md`
(after `./gradlew :pipeline:testClasses`) and re-pin the counts of `SequenceEvalOfflineTest`. The book has 8 chapters, 328 paragraphs
(40 per chapter with the footnote line, 8 headings besides) and about 13,200 words; chapters 2, 5 and 7 are narrated in the first
person by a male spirit in the past tense, the other five in the third person. Densities of the committed file, pinned by the test:

| Feature | In the book |
|---|---|
| speech paragraphs (straight `"`, curly `“ ”`, nested `' '` / `‘ ’`, em-dash `—`, mixed) | about 145 (44 %); 16 mix straight and curly marks |
| long paragraphs of 80-140 words / short lines of 1-8 words | 46 (14 %) / 69 (21 %), 9 of them 40-60 characters like "And, a split second later, the explosion." |
| `Mr` / `Mrs` / `Ms` with surnames | 71 / 64 / 66 paragraphs |
| `master` / `imp` / `magician` / `boy` | 59 / 66 / 63 / 68 |
| `sir` / `pentacle` / `circle` | 27 / 29 / 32 |
| names with tempting spellings (Zerubin, Edwin, Pellbury, Ashdown, Whitlock, Harrowgate, Quill), the stammered `Z-Zerubin`, the hyphenated `Stoke-on-Marsh`, generic capitalised places (`Great Hall`, `Old Bailey`) | in narration and in speech |
| `[n]` footnote markers and footnote lines, `*emphasis*` / `**bold**`, Latin kept foreign, numbers and dates, three verses | 16 markers, 8 footnote lines, 14 Latin paragraphs |

`manifest.json` lists the chapters and their narrator, the glossary the run starts with (Edwin locked, Pellbury unlocked, Ashdown
and Zerubin with no target) and, per term and name, the renderings and spellings that are counted (`master`'s list leaves out
`пан`, which a `Mr` in the same paragraph would otherwise claim). Three paragraphs are fixed anchors for the scripted model of the offline test.

*Narrator modes* (`BOOKLOOM_EVAL_NARRATOR`, reports `<model>-sequence-<mode>.{txt,json}`, the JSON field `narrator`). `unset`, the default,
writes a brief with **no narrator**, which is what the real run had: the style sheet carries no first-person rule and the Ukrainian
gender check is silent, so the male narrator's «я була / могла» slips through. `set` writes a first-person male narrator into the brief
(rule in the style sheet, check on, one directed fix per slip). `genderSlips` counts slips in the three first-person chapters in both modes,
so the difference between the two rows is what the narrator field buys.

*Metrics* (one table per model and mode):

| Metric | How it is read |
|---|---|
| `renderings/term`, `dominantShare` | per fixture term, the known renderings (inflected, as regular expressions) found in the targets of the paragraphs that name the term; the mean count of distinct ones over the terms rendered at all (1.00 is ideal) and, per term and as a mean, the commonest rendering's share (100 % is ideal; the real run's `master` was 143 майстер to 7 господар) |
| `nameVariants` | per name, the spellings seen (`зерубін` and `зеробін`); the sum of those beyond the first |
| `genderSlips` | `GenderChecks` («я» + a past-tense word of the wrong gender) over the first-person chapters |
| `english` | targets still in the source language: `LanguageIdentityCheck` finding or a plain echo of the source |
| `flaggedNoTarget` | flagged segments with no stored target, which an export writes as the source (the 23-English-paragraphs bug, fixed by 15e.5) |
| `hardGateRound0` and `kinds` | segments whose first evaluation failed a hard gate (a blocking quote or script finding, a placeholder), read from the run's `Evaluated ... round=0 ... hardGatesPass=false` line; `kinds` counts the findings of their first repair round (`Round choice ... round=1`). The real run: 73 of 3,783 |
| `leakedProtocol` | final targets holding protocol text (`ProtocolLeak`: a `terms`/`items` key and colon, a brace holding an `id` key, a code fence; the real run: 4) |
| `termClaimedWrong` and `claimed` | lexicon terms whose learned rendering another term also claimed (`mr` and `mrs` both learning «пані»), with the shared renderings |
| `quote` / `ascii` / `mixed` | `TextChecks` blocking unbalanced-quote and mixed-script findings in the final targets (a soft "English quote marks" note is not counted); `ascii` counts targets that still hold a straight `"` (an eval-side count, the quote check does not read the kind) |
| `flag%`, `fallbk%` | flagged share; batch items that fell back to their own draft over batched items (reasons are `status/problems`, read from the run's own warning) |
| `calls`, `sec/seg`, `edits+`, `edits-` | finished model calls and wall seconds per segment; repair edits kept and refused |
| `learned`, `coverage`, `lexicon/t` | renderings the lexicon learned by co-occurrence, the share of those terms' occurrences carrying it (the `--report` lexicon section), the lexicon's own distinct renderings per term |

Offline (`check`, no model, under 20 s): `SequenceFixtureTest` (the committed book equals the generator's output; paragraph, word and density
counts; term and name counts; the narrator modes), `SequenceEvalOfflineTest` (the same code through the real job over `SequenceScriptedModel`,
which drifts `master`, `Mr` and `Zerubin` for the second half of the book, says «я була» once, echoes one paragraph in English and appends one
stray quote, and over hand-written runs for each defect class and the new metrics). No floor is asserted: 15e.4 records the "before" table
and the thresholds per model class from this suite.

Calibration on 2026-10-02 (one sample per case, temperature as in production):

| Prompts | Model | parse | gate | review separation | script | marker | injection |
|---|---|---|---|---|---|---|---|
| before (step 8g) | gemma4:e4b-mlx | 100% | 91% | 50% | 100% | 67% | 100% |
| after (step 9) | gemma4:e4b-mlx | 100% | 100% | 100% | 100% | 89% | 100% |
| after (step 9) | gemma4:e2b-mlx (floor) | 100% | 82% | 100% | 100% | 100% | 100% |
| after (step 10, with the 4 suggest rows) | gemma4:e4b-mlx | 100% | 100% | 100% | 100% | 92% | 100% |

**Model list.** `scripts/eval-models.txt` is the one list of models `scripts/eval-matrix.sh` runs (refreshed 2026-10-04 to what
`ollama ls` and `lms ls` show: gemma-4 e4b in every quant first, then e2b, 12b, 26b, gpt-oss, the two qwen3.8-27b builds, muse-glimmer
and the rest). `qwen2.5:1.5b` is kept only as a stub for API checks, never for translation. 

**Batch A/B (15d.8).** `scripts/eval-matrix.sh --suite batch [--batch-sizes 4,8] --models "ollama:<id> lmstudio:<id>"`
sends batches of consecutive draft cases through the JSON batch protocol. Without `--batch-sizes` the size is the job's: the first
batch has `BatchDrafter.DEFAULT_INITIAL_SIZE` cases and each reply goes to a production `BatchDrafter.record`, which halves the
size on a lost numbering, an omission or a merge (not on a reply that answered only the first item or was cut at the length cap, 15i.T3) and grows it back after a clean streak, never above the start (the table then has a row per size reached);
`--batch-sizes` fixes the old sweep. The draft cases include two invented long paragraphs (over 250 and over 400 characters,
with numbers that must survive). It prints, per model and size: `idValid` (items
answered under their id exactly once), `tokGate` (also kept their tokens in order), `omit`, `merge` and output tokens per
item. It measures only; the batch size is chosen by reading the table (the tagged-block protocol it once compared was
dropped after the first A/B, see `12_PROMPT_CATALOG.md#batch-draft`). A run's own draft calls per segment are
`modelCallsByKind.DRAFT.attempts` over `run.segments` in the report JSON (a batch call and a single-segment fallback each count one).

*History (removed in redesign task 2.3): batches now go out in one JSON format, so the switch, the eval option and the
`markers` and `dual` arms below no longer exist. The text and the measurements are kept as the record of the experiment.*

**Batch format arms (15i.X1, an experiment).** Three ways to send and read a batch sit behind one switch until the
measurement picks one and the other two are removed. `BOOKLOOM_BATCH_FORMAT` (else the property `bookloom.batch.format`) names
`json` (the default: numbered items, a JSON reply under the schema), `markers` (one passage whose items start at `⟦s1⟧ … ⟦s8⟧`,
a plain-text reply with the same markers split by code, no response format) or `dual` (the whole passage once as context, then the
numbered items, the same JSON reply). In a run it is read when the job starts; in the evals use
`scripts/eval-matrix.sh --batch-format json|markers|dual` (or `BOOKLOOM_EVAL_BATCH_FORMAT` for a direct `./gradlew :pipeline:promptEval`,
which the build passes on as the property) so the realrun, sequence, batch and replay suites send and read what that arm's job does; every
report's provenance says `batch_format=…`. Run all three arms on the same models and suites, one gradle at a time, and compare omissions
and missing ids, control-code and quote defects, the placeholder gate, WRONG on a fixed 60-segment sample, seconds and calls per segment;
adopt `markers` or `dual` only if WRONG falls by 2 or more or seconds per segment fall by 10 % with no key metric losing 2 or more (task
15i.X1). The result and the decision are recorded here when the arms are measured.

**15i.X1 batch format (measured 2026-10-13/14, tree 690ce998, relevant pairs off).** Suites through `scripts/eval-matrix.sh
--batch-format <arm>` (realrun `--stability 3`, sequence `--narrator unset`, replay of the Oct 10 trace, fast mode, 40 cases);
the 300-segment run is `:app:translate` on a public-domain novel (EPUB, en→uk, Balanced, names transliterated,
`--stop-after 300`). realrun counts only the draft, batch and repair groups (the reviewer groups do not depend on the format).

| model | arm | realrun draft | sequence flagged | seq. fallbacks | seq. calls/seg | seq. s/seg | LTCR-1 | replay pass | 300: flagged | 300: missing ids | 300: calls/seg | 300: s/seg |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| e4b | json | 67/73 | 9 | 17 (5.2 %) | 0.38 | 4.11 | 92.9 % | 78 % | 3 | 5 (one cut reply, re-asked) | 0.30 | 1.60 |
| e4b | markers | 66/73 | 7 | 10 (3.0 %) | 0.36 | 3.60 | 92.4 % | 83 % | 4 | 0 | 0.30 | 1.32 |
| e4b | dual | 65/73 | 10 | 19 (5.8 %) | 0.39 | 3.81 | 92.7 % | 78 % | 3 | 0 | 0.31 | 1.46 |
| 26b | json | 66/73 | 9 | 11 (3.1 %) | 0.44 | 5.05 | 74.7 % | 80 % | 4 | 5 (one cut reply, re-asked) | 0.32 | 1.96 |
| 26b | markers | 67/73 | 9 | 5 (1.5 %) | 0.38 | 4.97 | 85.7 % | 78 % | 5 | 0 (one merge suspect) | 0.35 | 2.30 |
| 26b | dual | not run | not run | not run | not run | not run | not run | not run | 5 | 0 | 0.32 | 2.24 |

In every arm and model: no leaked protocol, no control-code residue, no ASCII quotes and no unbalanced « » in the export,
no placeholder-gate failure in the export (one `TOKENS` item problem per markers run, repaired before acceptance), realrun
truncated 0. The `exit=1` of every 300-segment run is Gradle reporting the command's exit code 3, a partial export
by design under `--stop-after`; all six runs decided 300 segments and wrote their book.

**Decision: json stays the default; the decision is pending the owner.** Speed alone cannot adopt an arm: markers is
18 % faster on e4b in the 300-segment run (12 % in the sequence suite) but 17 % slower on 26b (sequence 2 % faster), so the
models disagree and the rule keeps json; dual is never 10 % faster on both. The Opus-graded WRONG on the fixed 60-segment
sample and the 26b dual suites were not run (the eval queue's budget ran out), so a quality gain of 2+ WRONG cannot be
ruled in or out. The three arms stay in the code behind `BOOKLOOM_BATCH_FORMAT` (default `json`) until that grading is done.
Rerun what is missing: `scripts/eval-matrix.sh --models ollama:gemma4:26b-mlx --batch-format dual --suite realrun --stability 3`
(then `--suite sequence --narrator unset` and the replay suite), and grade the six exports with the sample rule: `<p>` blocks
of 8+ source words translated in all six books, 60 picked evenly by position, letters shuffled per segment.

**Reviewer eval (15d.6) — removed.** The per-chunk reviewer, its corpus run (`catch`, `falseAlarmEdits`, `tokenBreaks`) and `ReviewerCall` were deleted in group 14 of `redesign-translation-pipeline` (arm 7a, `#redesign-arms`); the corpus case files keep their draft and repair cases, and `--langs all` runs each language's mini-corpus. Reports land in `modules/pipeline/build/reports/promptEval/<model>.txt` and `.json`.

**Coref probe (15i.E2).** 31 invented windows of two to five paragraphs (`src/test/resources/eval/coref/windows.json`), each
naming one target and listing every third-person pronoun with its gold referent (the target, another character, a place's
people, nothing), in six shapes: another character in the next sentence, a speaker's pronoun in quotes, a place's "her", a
first-person narrator, a pronoun with no referent, and plain. `CorefScorer` scores a `CorefReader` per window: wrong if any
vote names a pronoun that is not the target's (or fits the target's and another's), right if it has a vote and none is wrong,
else abstention; a quote not in the window is dropped. The gate is wrong votes at most 5 % of the windows that voted
(`CorefReport.wrongVoteRate`), per model: above it that model's window votes only break ties. The fixture and scorer are
tested in the normal gate. `scripts/eval-matrix.sh --suite coref` (15i.N4, `CorefEvalTest`) reads every window with the
production term reading (`TermReadingCorefReader`: one `term-reading` call per window, the target marked where it first
stands) and scores only the votes `TermReadingReplies` keeps; the report (`<model>-coref.json`) and the matrix table give
per model the windows right, the wrong votes, the abstentions and the wrong-vote rate.

**Stage suites (plan A4).** The model-call stages no other suite reaches are measured through their production classes on
small invented fixtures (`src/test/resources/eval/stages/*.json`, English and Russian to Ukrainian, no book text), selected
with `BOOKLOOM_EVAL_SUITE` and run like the others (`BOOKLOOM_EVAL_URL` required, `@Tag("promptEval")`, never in `check`):

| Suite | Production class | What a case must end with |
|---|---|---|
| `prescan` | `PreScan` (PRESCAN, then the verdict and suggestion calls) | a woman's one-word name kept as a female character, a place kept, a weekday and a language name left out |
| `terms` | `TermChoice` (TERM_CHOICE) and `TermReview` (REVIEW_TERMS) | everyday words (keep, black, screen) dropped, a jargon term kept; held names kept, a weekday removed |
| `setup` | `SetupAssistantImpl` (FILE_NAME; BOOK_KNOWLEDGE, BRIEF_SUGGESTION per window, BRIEF_SYNTHESIS) | a file name in the target script with its author part in it; the narrator the text is told in, the narrator's gender, the register and a genre of the case's class (`genreClass`, any one of whose words the genre holds) |
| `consistency` | `ConsistencyPass` (`RetryPass`, `RetryDraft`, `NeighbourRevision`, `RevisionCall`) | a planted mixed-script word, a left-untranslated sentence, a trailing `"}` and a dropped vocative repaired |
| `retry` | `RetryDraft.retry` (the review desk's Retry) | the same four planted defects repaired by one retry each |
| `replay` | the draft parsers and judges (`ReplyJudge`, the batch parser), over calls read from a real trace log | see "Replay from a trace log" below |

`consistency` and `retry` first run the real batched job over a small book with a model that answers the case file's planted
(defective) drafts, so the stored records, statuses and context snapshots are what a run leaves; only then is the real model
asked. Scoring is deterministic: each case's pass/fail, and per row the calls, failed calls, repeated identical requests and
answers the class read and refused (`ConsistencyChecks.refused`, undecided term batches). Reports are
`build/reports/promptEval/<model>-<suite>.json` and `.txt` (`{"suite":"prescan",...}`), and
`scripts/eval-matrix.sh --suite prescan|terms|setup|consistency|retry` prints one row per model. The offline proof is
`StageEvalRunnerTest` (a fake model answering like a good one, and a silent one), which also compares the recorded request with
the production prompt for the stage.

*Setup cases for the new prediction (15i.E3).* `eval/stages/setup.json` also holds eight invented title and author pairs
(`invented-01…08`), four public-domain classics (`classic-*`, published before 1929, with the year in the FB2 `<date>`) and a
book with no metadata (`setup-no-metadata`). A case carries `expectKnowledge` (`unknown` for an invented or title-less book,
`known` for a classic), `year`, `publisher` and `classic`; the knowledge answer is scored from 15i.B2 on, until then
`SetupScore` reads `knowledge.* n/a`. The per-field score (genre by the gold class, register, narrator, narrator's gender,
knowledge split into invented and classic) is `SetupScore`, proven offline by `SetupScoreTest` over `SetupScriptedModel`.

**Defect-class corpus (A5).** `eval/realrun/text.json` (`RunCase`; an optional `sourceLanguage` makes a case ru to uk, an optional
`marker` is a regex a model's draft must match where no production check decides) holds invented sentences for the classes
the October 8 run showed: a female character with a consonant-ending name whose verbs the draft makes masculine
(`female-noun-name`), slang read literally (`slang`), numbers that must not change, ASCII-quoted dialogue that must become
« » (`ascii-dialogue`), venue names declined twice (`venue-name`), a vocative at the start, middle and end
(`vocative-position`), reply closers after a draft (`closer-residue`, check `RESIDUE`) and a Latin tail on a Cyrillic word
(`mixed-script-tail`). `eval/realrun/replies.json` (`ReplyCase`) holds synthetic whole replies read by the draft reply parser
and judged by `ReplyJudge`; `DefectClassCorpusTest` holds each to what production does today. A defect production cannot see yet
(a masculine verb after a female name, a changed number) is a `knownFailure` naming the plan task that adds the check, and the
test fails once production gets it right. The control-character quote glitch (U+001C..U+001F where « » belong) and closers
after the JSON object are refused today; what they are expected to come to is the named constants
`ReplyExpectations.CONTROL_CHARS` and `.TRAILING_CLOSERS`, flipped to `ACCEPTED` when the mapping (plan B2) and the tail cut
(B3) land. No text of the corpus is copied from a book.

**Replay from a trace log (A6).** `BOOKLOOM_EVAL_SUITE=replay BOOKLOOM_EVAL_REPLAY_LOG=~/Library/Logs/BookLoom-Dev/bookloom-trace.log
./gradlew :pipeline:promptEval` (with `BOOKLOOM_EVAL_URL`, `_PROVIDER`, `_MODEL` as for the other suites; `scripts/eval-matrix.sh
--suite replay` passes the variables on) sends again the model calls a real run logged. `LogReplayCorpus` pairs each `Model call
going out` line with the next `HTTP request body` and `HTTP response body` lines of its thread and reads the `Decided segmentId=`
lines for how each segment ended; the log may be `.gz`. `BOOKLOOM_EVAL_REPLAY_MODE=raw` sends every logged request body as it
went out (a model, quant or server setting compared on the very prompts of the run); `fast` (the default) takes a stratified
selection of about `BOOKLOOM_EVAL_REPLAY_TARGET` (40) draft calls, by short/long (over 250 characters), single/batch and
flagged/repaired/accepted, always including every call that drafted a segment that ended flagged, and rebuilds each request with
the current prompts through `EvalProject` (a prompt change compared on the very paragraphs of the run; the glossary and context
are not in a log, so the rebuilt request carries none). `BOOKLOOM_EVAL_REPLAY_MAX_BYTES` reads only the first bytes of a log and
`_LIMIT` sends at most that many calls. A draft reply is scored by `DraftReplyParser` and `ReplyJudge`, a batch reply by the batch
parser and the judge on each item, any other call kind on its structure only; each row shows the logged reply's verdict beside the
new one. The calls stay in memory; the report (same files, provenance and history as the other stage suites, label = the mode)
holds counts and verdict words, never log text. `LogReplayCorpusTest` and `ReplayRunnerTest` prove the parser, the selection and
the runner on `eval/replay/sample-trace.txt`, an invented log in the real line format; `LogReplayRealLogTest` parses a real log
when `BOOKLOOM_EVAL_REPLAY_LOG` is set, without sending anything.

<a id="recall-suite"></a>**Detector recall over an exported book (15h.E2).** How many of the defects a person finds in a finished
book the code's own detectors catch, and how often they fire on a sound paragraph. No model is involved:

```bash
BOOKLOOM_EVAL_SUITE=recall BOOKLOOM_RECALL_DIR=$PWD/.temporary_context/eval-artifacts/recall ./gradlew :pipeline:promptEval --tests '*RecallEvalTest*'
BOOKLOOM_RECALL_DIR=$PWD/.temporary_context/eval-artifacts/recall scripts/eval-matrix.sh --suite recall     # the same, then the table
```

`BOOKLOOM_RECALL_DIR` must lie in the git-ignored `.temporary_context/` or outside the repository (the test refuses any
other path inside the checkout, since `segments.jsonl` holds book text), and must be absolute — Gradle runs the test in
the module directory; unset, the test is
skipped. The directory is one book (it holds `book.json`) or holds one sub-directory per book. A book directory holds:

| File | Who writes it | What it holds |
|---|---|---|
| `book.json` | the owner | `{"source": "Burning Chrome.epub", "exported": "Burning Chrome (uk).epub", "glossary": "glossary.csv", "sourceLanguage": "en", "targetLanguage": "uk", "narrator": {"person": "FIRST", "gender": "MALE"}}` — `source` and `exported` are required (paths relative to the directory); the glossary is the export's CSV side file; the languages default to `en` and `uk`, the narrator to unstated |
| `segments.jsonl` | the suite, on every run | one line per aligned segment: `{"id", "hash", "source", "target", "fired"}` — the display texts and the detectors that fired; **book text**, which is why the directory stays outside the repository. This is what a reading (a person, or an Opus reading the person spot-checks) labels |
| `gold.jsonl` | the reading | one line per segment that was read, no book text: `{"hash": "<64 hex>", "classes": ["dropped-sentence"], "expect": ["sentence-count", "audit:sentence-count"]}` |

*Gold line format.* `hash` is SHA-256, lower-case hex, of the NFC form of the source's display text, the unit separator U+001F and
the exported text's display text (`RecallGold.hash`; display text = `DisplayText.of`, tokens out, whitespace runs as one space,
trimmed), copied from `segments.jsonl`. `classes` are the defect classes the reading found, free words such as `dropped-sentence`,
`untranslated`, `wrong-gender`, `name-lost`, `wrong-meaning`; an empty list marks a segment read and found **clean** (needed for
precision). `expect` names the detectors that should have caught it, spelled as the report spells them: the run check's
`raisedBy` (`sentence-count`, `language-identity`, `quote-balance`, `number`, `vocative`, `name-swap`, `gender`, `length`, `echo`,
… — `qa.CheckName`) or the final audit's `audit:<check>`; leave it empty when no deterministic detector can see the defect. A
segment no line names is unread and counts nowhere. A line whose hash matches no segment (a new export of other text) is counted as
`staleGold`; a malformed or repeated line fails the run with its line number.

*What runs.* Both books are opened through `DocumentPort` and paired by segment id (`ExportedBooks.recordsOf`; a single-file book's
unit id is its file name, so an export under another name is paired unit by unit). Each pair goes through the run's own
evaluation, `QaEvaluator.evaluate` (refusal gate, every deterministic text check, the soft checks) with the glossary's pairs and the
segment's character sheet, then `FinalAudit.scan` reads the whole book as accepted, unreviewed records. The word validator is the
offline one, so `unknown-word` never fires.

*Report* (`build/reports/promptEval/recall.{txt,json}` and the history; counts and names only). Per detector: `fired` (read
segments it fired on), `tp` (of those, defective), precision = tp / fired, `expected` (segments whose gold expects it) and recall
= caught / expected. Per defect class: recall = the share of its segments **any** detector fired on; precision = of the read
segments one of the class's expected detectors fired on, the share of that class. Overall: `recall` (defective segments any
detector fired on) and `falseAlarm` (clean segments any detector fired on). A rate with an empty population prints `-` (`null` in
the JSON). `RecallSuiteTest` (in `check`) proves all of it on `eval/recall/mini/`, an invented eight-paragraph English → Ukrainian
book exported under another name. The gold for the three Oct 9 books is produced from the Opus readings and spot-checked by the owner (pending); it stays
in the owner's directory.

<a id="judgement-evals"></a>**The model's judgement of the book: stability and gold (15h.E3).** Most people accept the Book Brief and
the glossary's genders as suggested, so two suites measure them, both through the production `SetupAssistantImpl.suggestBrief`
and `TermReview.review` (`JudgementRunner`: the book imported with nothing stated, the gold's main characters held as a scan
leaves them — untyped, gender unknown — then the review with the code's pronoun and first-name seeding). Scored per field:
the brief's genre (by the gold's genre class), register, narrator and narrator gender, and each main character's type and
gender; voice and audience are free phrases and are not scored. *Agreement* is the share of a field's answers across seeds
that are the most common one (target 0.8 or more); *accuracy* is the share that match the gold.

```bash
scripts/eval-matrix.sh --suite stability                    # every model in scripts/eval-models.txt, seeds 1,2,3
BOOKLOOM_EVAL_BOOK=all BOOKLOOM_EVAL_SEEDS=1,2,3,4,5 scripts/eval-matrix.sh --suite stability --models "ollama:gemma4:e4b-mlx"
BOOKLOOM_CORPUS_DIR=/abs/books scripts/eval-matrix.sh --suite gold   # the owner's gold set, one seed
BOOKLOOM_EVAL_SUITE=stability BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx \
  ./gradlew :pipeline:promptEval --tests '*JudgementEvalTest*'    # one model by hand
```

`stability` reads one committed fixture book (`BOOKLOOM_EVAL_BOOK`: an id below or `all`; default `front-matter-converter`) once
per seed (`BOOKLOOM_EVAL_SEEDS`, comma-separated; each call is sent with that sampling seed). `gold` reads every
`<name>.gold.json` directly under `BOOKLOOM_CORPUS_DIR` (absolute path) and opens the book each names; it is skipped when the
directory is unset. Both read a book at the Fast dial unless `BOOKLOOM_EVAL_DIAL` names another (the 15i.B4 gate times
the suggestion at `BALANCED`). Both are `promptEval`-tagged (`JudgementEvalTest`), never in `check`. Reports:
`build/reports/promptEval/<model>-stability.{txt,json}` (or `-gold`) and the history; the matrix table prints each model's
lowest agreement and mean accuracy, then every `book:field` per model as agreement/accuracy and the agreement of all models'
answers pooled.

*Gold file format* (one per book, written by the owner after reading it; no book text):

```json
{
  "book": "Glass Harbour.epub",
  "sourceLanguage": "en",
  "targetLanguage": "uk",
  "brief": {
    "genre": "cyberpunk short stories",
    "genreClass": ["cyberpunk", "science fiction"],
    "register": "NEUTRAL",
    "voiceEra": "hard-boiled, 1980s street slang",
    "audience": "adult readers of science fiction",
    "names": "TRANSLITERATE",
    "narrator": "FIRST",
    "narratorGender": "MALE"
  },
  "characters": [
    {"term": "Lena", "type": "CHARACTER", "gender": "FEMALE"},
    {"term": "Danny Vosk", "type": "CHARACTER", "gender": "MALE"}
  ]
}
```

`book` is relative to the gold file's directory. `register` is `FORMAL_LITERARY`, `NEUTRAL` or `CASUAL`; `narrator` is `FIRST`,
`THIRD` or `UNSPECIFIED` (a book whose first-person narration alternates with third-person chapters is `FIRST` when at least a quarter of its judged chapters are first person, else `THIRD`: the book-brief spec's mixed-book rule, 15i.B4); `narratorGender` and a character's `gender` are `MALE`,
`FEMALE`, `NEUTER` or `UNKNOWN` (the book never shows it); `type` is `CHARACTER`, `PLACE`, `TERM`, `TITLE` or `OTHER`; `names`
is `TRANSLATE`, `TRANSLITERATE` or `KEEP_ORIGINAL`. `genreClass` holds lower-case words, any one of which a right genre contains
(empty: the genre is not scored; a committed gold's `genre` is one of the forty names the synthesis answers with, and its class never holds a generic word such as `fiction` alone, so every book can be scored); `genre`, `voiceEra`, `audience` and `names` are optional and only used when the same file is
given to `BOOKLOOM_EVAL_GOLD`, which sizes every other suite's requests for that book's real brief. List each main character
under the term the book uses most. Three to five books make the set.

*Committed cases* (`src/test/resources/eval/judgement/*.json`, invented text): `gender-late-or-object` (a "She" only two
sentences after the name; a man shown only by his/him), `straight-quoted-speech` (another man's "He" inside straight-quoted
speech next to a woman's name), `vocative-name` (a woman named mostly as a vocative before "he said"), `female-narrator-address`
(a first-person woman shown only by "ma'am" and her name), `front-matter-converter` (title page, copyright, twelve blurbs of
praise, dedication, contents and a converter's page before the story), `alternating-narrators`, and for the new prediction
(15i.E3) `letter-in-third-person` (a first-person letter inside a third-person novel: the quote misleads, the narrator detector
is right), `first-person-vocative-name` (a woman narrator named only by others' vocatives: the code verifies her gender only once
the name is a glossary character, `expect.verifiedGender` is `UNKNOWN` until then, 15i.T5) and `quoted-woman-not-the-narrator`
(a man's own quoted "Mind the step, Lucy" is a woman's name, not his; "sir" shows his gender). Each holds its chapters, its
gold, what the code must conclude with no model (`expect`: the narrator detector's person, the pronoun reader's gender per
name, where the brief's first sample starts and what no sample may hold) and the quotes a good model gives for the narrator's
gender. `JudgementBooksTest` (in `check`) holds the code to `expect` and runs each book through the runner with a fake model that
answers as the gold says (every field right); `JudgementSuitesTest` proves the agreement and accuracy scoring, the seed handling
and the gold directory. The praise page found a defect: twelve blurbs ("A quiet triumph." The Northern Review) counted as a
story chapter, so the brief read reviewers first; a story paragraph now needs two full sentences closed by their mark.

<a id="names-suite"></a>**The names suite: Names & style end to end against gold (15i.E1).** `NamesRunner` runs a book the way the
window does, through the production `GlossaryService` and `LexiconService` over the imported project's own stores (`SequenceJobs.names`):
glossary scan, name prescan, review, translate, then lexicon scan, model scan, review and suggest. It then scores the final glossary
and lexicon against the book's gold (`NamesScore`): the gender of every character with five or more mentions (accuracy, and the
*wrong but verified* count, a wrong gender not marked suggested), the type of every gold character, whether the written forms of one
person (one `cluster`) hold one gender, the distinct spellings per cluster, the junk a scan kept, the real names it lost, the
recurring terms kept and dropped, and the calls and minutes. A mention is a whole-word, case-sensitive occurrence of the gold term.

```bash
scripts/eval-matrix.sh --suite names                                    # the nine committed books, every model
BOOKLOOM_EVAL_BOOK=misattributed-she scripts/eval-matrix.sh --suite names --models "ollama:gemma4:e4b-mlx"
BOOKLOOM_CORPUS_DIR=/abs/books scripts/eval-matrix.sh --suite names     # the committed books and the owner's gold set
```

`BOOKLOOM_EVAL_BOOK` is a committed id or `all` (the default); with `BOOKLOOM_CORPUS_DIR` set every `<name>.gold.json` directly under
it runs too, its mentions counted in the book's story text. The report is `build/reports/promptEval/<model>-names.{txt,json}` and the
history; the matrix prints, per model and book, gender % over the eligible characters, wrong-but-verified, type %, the share of
person clusters with one gender, spellings per cluster (1 is ideal), junk kept, names lost, recurring terms right, calls and minutes.
It measures and asserts no floor. The books are read at `BOOKLOOM_EVAL_DIAL` (Balanced by default; before 15i's names gate the
suite read every book at Fast whatever its report said). Gender is scored over gold persons only (type `CHARACTER`, five or more
mentions): a place, a thing or a title carries no gender. A cluster's spellings are counted word by word: each source word of a
member whose rendering has as many words is paired with the rendering's word in its place, and the cluster counts the most
renderings one source word got. Each book's JSON also carries a `diagnostics` object (`NamesDiagnostics`) with the cases behind
the numbers: each wrong gender with its origin, the wrong types, the lost names, each cluster's renderings, the junk kept, the
recurring terms per gold decision, the other lexicon terms and the whole final glossary.

<a id="mini-suite"></a>**The mini suite: one small invented book that holds every defect seen so far (15j.J15).** `eval/datasets/mini-en/book.json` is a
33-block invented book in two chapters (a first-person male narrator, a numbered chapter heading, a double negation, a numbers line, a name that first appears in chapter 2, a proofread trap, a stress-marked seed in `gold-map.json`; `labels.json` and a hashed `manifest.json` sit beside it, and a label value with fewer than 8 cases is printed "not interpretable"; `MiniRunner` takes its pair and narrator from the book; a harbour town, no line from any real book) with one block per defect the owner's
runs showed: Ukrainian dialogue punctuation, an inflected title and an inflected multi-word term, a brand, a material and a drug, a
woman and a man whose pronouns prove their gender and a name with none, a multi-word place and a place whose first letter a model
mis-transliterates, a word that tempts a Russian letter, an injected instruction, a long paragraph, a ти/Ви mix, a footnote aside and a
code that must stay Latin. `MiniBook` writes it as an EPUB 3 for each run (nothing binary is committed) and `eval/datasets/mini-en/expectations.json`
says what each block must come to: a check of one segment or of a group (`NO_LATIN_EXCEPT`, `NO_RUSSIAN_LETTERS`, `MATCHES`, `ABSENT`,
`NOT_FLAGGED`, `NO_FINDING` by the finding's `raisedBy` name, `NARRATOR_MALE`, `MIN_RATIO`, `ADDRESS_SAME`, and the pair-neutral `NO_STRESS_MARK`, `HEADING_NUMBER`, `NEGATION_KEPT`, `NUMBERS_KEPT`, `PLACEHOLDERS`), the two checks every block
must pass, and what the glossary holds after the names pass (type, gender and whether it is settled, locked, target in Cyrillic, target
words, junk absent). Each expectation carries the defect it guards. `MiniRunner` runs it as the app runs a whole book: the autonomous
names pass (`NamesPass`), the batched translation job and the Proofread stage, over one imported project; `MiniScorer`
scores the result with production checks (`GenderChecks`) and plain patterns.

```bash
scripts/eval-matrix.sh --suite mini --models "ollama:gemma4:e4b-mlx"                       # Transliterate, the default
scripts/eval-matrix.sh --suite mini --models "ollama:gemma4:e4b-mlx" --names keep_original # the brand expectations flip
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_SUITE=mini ./gradlew :pipeline:promptEval   # one model, by hand
```

The book has 39 segments; a run covers the names pass, the translation and the Proofread stage at Balanced. Its runtime on e4b is measured by the orchestrator, not stated here. `--names` (env
`BOOKLOOM_EVAL_NAMES`) is the brief's name policy: under Transliterate and Translate a brand must come out in Cyrillic and unlocked, under Keep
original it must keep its Latin spelling and be locked; the material and the drug are translated under every policy. The report is
`build/reports/promptEval/<model>-mini-<POLICY>.{txt,json}` and the history; the `.txt` lists each expectation not met with what was found,
and the matrix prints the expectations met, the segments flagged, the calls and seconds, the missed ids and the met/total per defect.
It measures and asserts no floor. In `check`, `MiniEvalOfflineTest` runs the whole flow on the pseudo model (which upper-cases its source,
so only the expectations an echo can meet are met), and `MiniBookTest` holds the book to its expectation file.

**The whole-book run (15j.J16).** `BookRunner` takes any book file and decides what a typical user decides in the window: the predicted
Book Brief accepted (name policy and the rest at the defaults), the names pass, the Start narrator re-check, the translation at the dial,
then the export written twice (the two files keep the names `no_consistency_pass` and `with_consistency_pass` from before group 14; export makes no model call, so they are the same book), each with the glossary CSV and the report. Select the class, since
`PromptEvalTest` also runs whenever `BOOKLOOM_EVAL_URL` is set:

```bash
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_PROVIDER=ollama BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx BOOKLOOM_EVAL_DIAL=BALANCED \
BOOKLOOM_EVAL_BOOK_FILE=/path/book.epub BOOKLOOM_EVAL_OUT_DIR=$PWD/.temporary_context/eval-artifacts/runs/book1 BOOKLOOM_EVAL_SOURCE=en BOOKLOOM_EVAL_TARGET=uk \
  ./gradlew :pipeline:promptEval --tests '*BookRunEvalTest'
```

`BOOKLOOM_EVAL_OUT_DIR` is required and must lie outside the checkout (the test refuses otherwise: the exports hold book text). It receives
`<name>.no_consistency_pass.<ext>`, `<name>.with_consistency_pass.<ext>`, their `.glossary.csv` and `.report.md`, and `book-run.json`: calls per stage,
minutes, flagged segments, characters by gender (verified, suggested, none), and per export the Latin words left, words with a letter the
target does not use, segments waiting on a gender, and the segments each detector fires on. `BookRunnerOfflineTest` proves it in `check` on the pseudo model.

The names gold extends the gold file above with these keys (the older code ignores them): `characters[].cluster` (the id shared by
every written form of one person, such as "Dax" and "Dax Orlo"; absent for a name of its own), `junk[]` (terms a scan must not leave
in the glossary: a heading, a nationality, a distress call, half of a brand), `keep[]` (real names besides the characters that must
not be lost: a place, a brand, a person called Mark) and `recurringTerms[]` (`{"term", "keep"}`: the lower-case common words a person
keeps in the lexicon, or `keep: false` for the ones a scan wrongly lists). Junk is matched against a glossary term with the same
letter case, so `KESSEL OPTIK` is junk while `Kessel Optik` is the camera.

*Committed books* (`src/test/resources/eval/names/*.json`, invented text; each has its gold, the answers a scripted model gives where
they differ from the gold, and `knownFailure`/`fixedBy` when today's Scan, Review and Translate end wrong): `misattributed-she`
(a man followed five times by another person's "she"; clean since 15i.N1), `short-and-full-form`, `city-her`, `stray-he`,
`bartender-mark` (a man called Mark beside "a mark on the wall"; clean since 15i.N5), `brand-and-camera` (`Orrin & Vale` and
`KESSEL OPTIK`/`Kessel Optik`/`Kessels`; red until 15i.N3), `groups-nationalities-converter`, `technical-headings`, `mayday`.
`NamesRunnerTest` (in `check`) runs each through the runner with a scripted model: a book not marked a known failure must end clean,
a marked one must still end wrong, so the test fails the day a task fixes it and the flag is dropped. `NamesScoreTest` holds every
scoring rule to numbers written out by hand.

*How Review decides a read name* (15i.N5): Review and the names pass share one path. A strong pronoun case
(`PronounGender.Evidence.isStrong`) of a person is decided by the code with no call; of the other open entries, those the
dial's `NamesPlan` reads go through `term-reading` and `ReadingVerdict`, and the rest through the batched `review-terms`
call. `ReadingVerdict` turns the readings into a change by rule: a name needs two windows read as `name` (one window of one
at Fast) and takes the kind most name windows give (person → character, place → place, organisation or thing → term); a
title majority makes a title, which like a place, an organisation or a thing carries no gender. The gender pools the
verified votes of the whole cluster (`ReadingVotes`), each window and quote counted once. The votes only break ties,
because the coref round (15i.N4, 2026-10-13) found 10 % wrong votes on 26b and 20 % on e4b against a 5 % gate: the code's
pronoun tally decides wherever it is clear (three or more at 60 %), written verified only where the cluster's verified votes
agree with it and suggested otherwise, and with no clear tally votes at the thresholds below give only a suggestion, as do two
or more pieces of evidence, votes and pronouns together, all one gender's; no tiebreak call is made. The tally reads a name's own
uses (15i.N names gate): a use that ends a longer name (`Corin Vale` for `Vale`; a capitalised word opening the sentence
counts only when the book also writes it with a capital inside a sentence) is not read, a use after a gendered form of address
(`Mrs.`, `Miss`, `Aunt`, `Widow`; language file `femaleTitles`/`maleTitles`) counts that form instead of a pronoun unless the
surname is written after both genders' forms (a family name), and a name that opens with one has its gender. A mode in
which a model's votes decide, with a `term-tiebreak` call against a dominant tally, returns with the first model that passes
the coref gate; until then it is not built (removed as dead code in the 15i review). Removal needs two `word` uses whose quote stands in its own window, no `name`
use, a lower-case share of 0.2 or more in the scan text and an entry nobody typed or gave a gender. At Max a term read in
two or more calls (or mentioned 30 times) whose calls read it differently gets one `term-summary` call; its reading replaces
the windows' majority only when its quote stands in a window shown and it is one of the calls' readings. The Scan button
keeps for the reading a common word the book writes with a capital mid-sentence three times or more (`Mark`), and adds it
only when the dial's plan reads it; the run's own scans still drop it.

*The names pass* (15i.N8): `NamesPass` (`NamesPassImpl`, `ua.bookloom.pipeline.glossary`) runs the steps the Names & style
buttons run, in order, on private copies of both tables (`StagedGlossary`, `StagedLexicon`: the glossary's and the
lexicon's own rules over a copy, with the stored repositories asked only about removals made earlier): the deterministic
scan when the glossary is empty (`GlossaryServiceImpl.scan`), the names step (`TermReview.decide`: the verdicts of a
review without its suggestion step), the recurring terms (`LexiconServiceImpl` scan, review of the held terms, model
scan, so a term a review drops is not read again), the renderings (`NamesTranslation` for the glossary, with the readings
of the names step; `LexiconService.suggest` for the terms) and the lock (`LockRule`: a brand, an identifier or a name kept
as written is locked with its own spelling; a name read as a name, not a speaking one, with a checked rendering, is locked
where the target language does not decline names, `namesInflect=false` in its language file). The stored tables are
written once, at the end, through `remove`, `update` and `add` (`put` for the lexicon), so a Stop, a cancelled reply or a
failed call writes nothing; Stop is the calling thread's interrupt, which `StoppableModel` turns into a `cancelled` answer
before the next call goes out. `estimate` runs the scan on a copy and then `NamesPlan.of` over the open names: calls and
minutes (`NamesPlan.estimatedMinutes`: the dial's time budget over the 80-term plan, 3.75–7.5 s per call at Fast,
11.25–18.75 s at Balanced, 11.25–22.5 s at Max, rounded up), an upper bound because a character the pronouns decide is
counted. The lexicon remembers a removed term for the session as the glossary does (`LexiconRepository.wasRemoved`: set by
`remove`, cleared by `put` and `record`), which the text scan and the model scan honour. The context window of the pass's
own calls was then the detected one (`ContextBudget.windowFor`, since removed: group 6.9b gives every call to a model the
one window `ModelWindow` resolves); the recurring-term calls of the lexicon service used the default window, as the buttons
did. Owner-book target: 26b at Balanced within 25 minutes with no wrong verified gender and every gold
name present (run by the orchestrator).

*Book-neutral sources* (15i.E0): no name, quote or passage of a book under test may sit in `modules/*/src` — the committed
cases above, the tests and the prompts use invented material of the same shape. `BookNeutralSourcesTest` (`:app`) enforces it
from the owner's own list, which is never committed because it would name the books: it reads
`$BOOKLOOM_CORPUS_DIR/neutral-denylist.txt` (one case-sensitive word or phrase per line, `#` comments and blank lines skipped)
and fails with every `path:line: token` that holds one as a whole word in a text file (java, kt, properties, prompt, txt, json,
md, fxml, css, xml, csv). It is skipped by a JUnit assumption when the variable is unset or the file is absent, so CI and a
checkout without the corpus stay green.

<a id="15i-names-gate"></a>**15i names gate (2026-10-13/14).** Three rounds of the names suite at Balanced on the owner's four books ("owner
book 1–4": three novels and a short story) and the committed fixtures, 26b and e4b
(`gemma4:26b-mlx`, `gemma4:e4b-mlx`, Ollama). Before the rounds the measurement itself was fixed: the suite had read every
book at Fast while its report said Balanced; it now reads at the reported dial, scores gender over gold persons only, counts
spellings word by word and writes the cases behind each number (`diagnostics`). The owner-local gold was corrected where the
book proves it wrong (four rules, each entry with its quote in `GOLD-CORRECTIONS.md` beside the gold): a title form the scan
cannot make (`Mr.`, `Mrs.`, `Miss` + surname: the glossary holds the surname, the title carries the gender), a name the book
never writes alone, a place given a gender, and a surname whose lone uses are one titled woman's. Every number below is
rescored against that corrected gold (persons with five or more mentions), r0 included. Round r0 is the code before the gate
(1202c719); r1 adds the agreement rule, the title-aware tally, the ten-lone-uses scan rule and the seeded-type rule; r2 adds the
one-sided weak evidence, the first-name diminutives and the model scan's batch pre-filter; r3 replaces that pre-filter with
"read the 30 most frequent, narrow the rest", adds the shared-word agreement and the tolerant reader's raw tab. (e4b r1 already
ran with r2's pre-filter and diminutives; its terms column moved with them.)

| Round | Model | Gender, persons ≥5 (books 1 / 2 / 3 / 4) | Wrong verified | Type | Lost | Spellings (max) | Terms kept (1 / 2 / 3 / 4) | Term junk kept | Minutes (1 / 2 / 3 / 4) |
|---|---|---|---|---|---|---|---|---|---|
| r0 | 26b | 39/50 / 6/8 / 32/42 / 32/40 | 4 | 149/188 | 24 | 1 | 3/23 / 5/13 / 0/21 / 4/26 | 4 | 28.8 / 11.0 / 18.0 / 22.9 |
| r0 | e4b | 40/50 / 5/8 / 30/42 / 31/40 | 4 | 144/188 | 24 | 2 | 2/23 / 1/13 / 0/21 / 0/26 | 0 | 24.7 / 9.7 / 16.3 / 20.4 |
| r1 | 26b | 40/50 / 5/8 / 36/42 / 36/40 | 0 | 160/188 | 19 | 2 | 3/23 / 5/13 / 0/21 / 3/26 | 4 | 28.2 / 10.7 / 16.3 / 22.4 |
| r1 | e4b | 41/50 / 5/8 / 37/42 / 37/40 | 0 | 155/188 | 19 | 2 | 3/23 / 1/13 / 0/21 / 1/26 | 0 | 24.3 / 5.7 / 15.9 / 18.7 |
| r2 | 26b | 42/50 / 6/8 / 38/42 / 38/40 | 0 | 160/188 | 19 | 2 | 10/23 / 5/13 / 2/21 / 4/26 | 0 | 21.7 / 5.7 / 11.8 / 16.9 |
| r2 | e4b | 42/50 / 5/8 / 38/42 / 37/40 | 0 | 155/188 | 19 | 2 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 23.6 / 5.7 / 15.5 / 18.7 |
| r3 | 26b | 42/50 / 7/8 / 38/42 / 38/40 | 0 | 161/188 | 19 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 24.0 / 6.9 / 12.1 / 16.9 |
| r3 | e4b | 42/50 / 5/8 / 38/42 / 37/40 | 0 | 155/188 | 19 | 1 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 22.0 / 6.3 / 14.6 / 18.1 |

What moved, by case: the agreement rule took every wrong verified gender away (a demon written "it" with a neighbour's "he"
four times; a title and a family surname); the title-aware tally gave the bare surname of a married couple its husband's gender
instead of the wife's (429 uses, 195 after `Mr.`/`Mrs.`) and gave `Widow …`, `Mr. …` and family surnames their gender; the
ten-lone-uses rule brought back five first names and surnames (one written alone 34 times), all with the right gender; the
seeded-type rule turned seven places, spells and objects a "he" had made characters back into places and terms (type
+7 on owner book 1); the weak evidence and the diminutives gave seven minor characters a suggested gender (and two plural group words of a junk fixture one too); the batch
pre-filter, reading the 300 most frequent words, took owner book 1's recurring terms from 3 to 11 kept and cut 4–7 minutes per
book on 26b; the tolerant reader recovered a heroine whose reading reply held a tab; the shared-word rule made the spellings
1 (a person written with two first names, two anchors, had two renderings of the surname).

Gates after r3. N1 met (wrong verified 4 → 0 on 26b, 4 → 0 on e4b). N5 not met: gender 84 / 88 / 90 / 95 % on 26b (95 %
wanted), 84 / 63 / 90 / 93 % on e4b (90 % wanted); no wrong verified gender and the bartender kept, but the junk fixtures still
keep their junk (a plural group, a nationality and a converter's name read as persons; `KESSEL`'s plural); time within the
budget (owner book 1, the longest, 24 min on 26b). N6 met on 26b (spellings 1) and e4b has no literal name. N7 not met: on 26b
correct keep/drop decisions rose from 34 to 46 and "Mayday" is kept, but three terms the per-term reading kept are now dropped
by the batch choice (two on owner book 4, one on owner book 2); on e4b +1 only. N3's triage loses no gold name on the owner
books (every lost name is lost by the longer-name rule or a connector phrase, not by triage). What remains: persons the book
gives no pronoun or title (a narrator in the first person, pet names, minor surnames) stay unknown, since the reading's
pronoun answer is "none" and no other evidence exists; names written alone only two or three times are dropped as part of
their full names; the junk fixtures; reading more of the pool word by word (`READ_FIRST` 120 would keep the three dropped
terms at about 4 more minutes per book).

*Review fixes folded in after the rounds (2026-10-14, not re-measured; each tested):* a rule's lock (`GlossaryEntry.withRuleLock`)
keeps a suggested gender suggested — only the person's lock confirms it; a clear tally is verified only where verified votes agree
and none opposes it; a cluster's calls that show one window twice count its quote once; the cluster's verified gender upgrades a
member suggested the same gender; only a rendering the person chose binds a cluster's other spellings; the DECIDE vote mode, the
tiebreak call and its prompt were removed as unreachable; the names pass's estimate (`NamesPassEstimate`) counts every call it
sends (names reading, batched review, Max summaries, recurring terms, renderings and their re-asks), its store
(`NamesPassStore`) records the term deferrals a locked rendering change leaves on decided segments and puts both tables back when
a write fails half-way, and a promotion of a term the glossary already holds only takes it out of the lexicon. Of these, only
the agreement and pooling rules can move the gate numbers (verified to suggested only, so wrong-verified can only fall).

*Second iteration (2026-10-14), rounds r4–r7.* The same suite, the same corrected gold. The rows below are the reports'
own counts (`.temporary_context/eval-artifacts/eval_2026-10-12/raw/Ngate2-r*`): the suite writes 39 eligible persons for owner book 4
where the hand-rescored r0–r3 rows above say 40, so compare r4 with r3's own report (`raw/Ngate-r3`), not with the row.
r4 is HEAD after the review fixes; "rf120" is r4 with `LexiconReading`'s Balanced read-first at 120 instead of 30 (15i.N7);
r5 adds the scan rules below; r6 fixes what r5 broke (a couple's shared surname, the plural rule, a place's spelling,
two fragments); r7 lets a mention be a joined name of up to eight words and keeps the runs of an "of" name.

| Round | Model | Gender, persons ≥5 (books 1 / 2 / 3 / 4) | Wrong verified | Type | Lost | Spellings (max) | Terms kept (1 / 2 / 3 / 4) | Term junk kept | Minutes (1 / 2 / 3 / 4) | Fixture junk |
|---|---|---|---|---|---|---|---|---|---|---|
| r3 (own report) | 26b | 42/50 / 7/8 / 38/42 / 37/39 | 0 | 161/188 | 19 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 24.0 / 6.9 / 12.1 / 16.9 | 6 |
| r4 | 26b | 42/50 / 6/8 / 37/42 / 37/39 | 0 | 160/188 | 19 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 23.3 / 7.4 / 13.1 / 18.2 | 6 |
| r4 | e4b | 42/50 / 5/8 / 38/42 / 36/39 | 0 | 155/188 | 19 | 1 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 23.9 / 6.9 / 16.2 / 19.0 | 6 |
| rf120 | 26b | 42/50 / 6/8 / 37/42 / 37/39 | 0 | 160/188 | 19 | 1 | 11/23 / 5/13 / 3/21 / 5/26 | 4 | 29.9 / 10.9 / 18.8 / 23.6 | 6 |
| rf120 | e4b | 42/50 / 5/8 / 38/42 / 36/39 | 0 | 155/188 | 19 | 1 | 3/23 / 1/13 / 0/21 / 1/26 | 0 | 28.1 / 9.6 / 19.4 / 23.7 | 6 |
| r5 | 26b | 44/50 / 7/8 / 38/42 / 39/39 | 1 | 168/188 | 12 | 2 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 24.2 / 7.4 / 13.5 / 18.4 | 0 |
| r5 | e4b | 44/50 / 5/8 / 39/42 / 38/39 | 1 | 162/188 | 12 | 2 | 4/23 / 0/13 / 0/21 / 1/26 | 0 | 25.2 / 6.9 / 16.3 / 19.7 | 0 |
| r6 | 26b | 44/50 / 7/8 / 39/42 / 39/39 | 0 | 168/188 | 10 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 25.4 / 7.6 / 13.6 / 18.4 | 0 |
| r6 | e4b | 44/50 / 5/8 / 39/42 / 38/39 | 0 | 162/188 | 10 | 1 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 25.3 / 6.7 / 17.4 / 20.2 | 0 |
| r7 | 26b | 44/50 / 6/8 / 38/42 / 39/39 | 0 | 168/188 | 9 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 24.2 / 8.0 / 13.8 / 19.2 | 0 |
| r7 | e4b | 44/50 / 5/8 / 39/42 / 38/39 | 0 | 163/188 | 9 | 2 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 24.3 / 7.3 / 16.8 / 20.7 | 0 |

r4 against r3: nothing lost two or more. Two persons changed on 26b, both model noise: one heroine's reading reply could
not be read this time (gender and type unknown), and one minor character got no vote. e4b is r3 to the person.

**N7, read-first at 120 (rf120): not adopted.** On 26b it kept three more terms of craft (a painting, a cave, a widow)
and lost one (doctor), but kept two more ordinary words (monitor, school): the correct keep/drop decisions are level.
On e4b one more term is kept. Every book took 3.5–6.6 minutes longer and the longest owner book took 29.9 minutes on
26b, over Balanced's 25. The rule asked for 2+ more correct decisions on both models within the budget; neither holds,
so `READ_FIRST` stays at 30, and no second value was tried (120 was the only one the r3 notes pointed at, and it did
not move the count).

**The scan rules of r5–r7 (15i.N5 junk, 15i.N8 lost names), all code, no prompt change.**
- A capitalised word the book writes after a determiner in nine of ten mid-sentence uses (language file
  `determiners`) is not a name when it also stands between an indefinite article and a noun ("a Velarian porter": an
  adjective, `indefiniteArticles`) or when the subject pronoun after it is plural in two uses or more and more often
  than a gendered one ("the Guildsmen … They": a group, `pluralPronouns`) (`NounUses`). A word of a longer candidate is
  that name's short form and stays ("an Institute notebook" beside a longer institute's name). On the owner books the
  rule touches only words no gold lists (a river and a watch brand used as adjectives, "English").
- A plural-shaped word written after a determiner in half its uses or more ("the Velarians", "every Kessels") is the
  plural of a word: it folds into that word's candidate, or is left out when the word only stands inside a longer
  candidate (`PluralNouns`). The singular must be a candidate or a word of one, so a newspaper called "The …s" stays.
- A name written only in sections with no paragraph of two full sentences, when the book has such paragraphs, is a
  credit (`ProseSections`, `StoryMeasure.hasProse`; the triage, `CandidateTriage`). Sections are cut at units and
  headings; a story-chapter measure (20 sentences) dropped real names on the fixture book, whose sub-sections are short.
- A word inside a longer name is a name of its own when the book writes it truly alone (mid-sentence, no capital beside
  it) twice (`FrequencyScan.ALONE_MIN_STANDALONE`); the hygiene's fragment rule keeps a fragment written alone twice.
  This brought back the surnames and first names written alone two to five times beside their full names.
- Two runs joined by "of", with at most one determiner after it (`nameJoiners`), are one candidate ("Tower of Arlen",
  "Keeper of the Grey Gate"); neither run becomes its fragment, and a mention may be up to eight words long so such a
  name has windows (`MentionFinder`).
- A surname that a form of address of the other gender also carries ("Lord Venn" beside "Mira Venn") joins no full name:
  r5 had given a husband's surname his wife's gender, verified (`NameClusters`).
- One word of a place's or other name keeps one rendering across names of that group, up to the first lower-case word
  (`ClusterAgreement`; a place's first word had two renderings beside the place's full name in r5; a place named after a person
  keeps its own).

**Gates after r7.** N5's junk fixtures: all junk gone on both models in r5, r6 and r7 (the plural group word, both
nationality words, the converter's name and the brand's plural; the scan alone now proposes exactly the gold names of
those two books), the bartender kept, no wrong verified gender (r5's one, a couple's shared surname, is fixed in r6).
N5's gender: 88 / 88 / 93 / 100 % on 26b in r6, 88 / 75 / 90 / 100 % in r7, where two persons fell back to unknown by
model noise seen in r4 too (a reading reply that could not be read, a reading with no vote); e4b 88 / 63 / 93 / 97 %.
N6: spellings 1 on 26b; e4b's 2 in r7 is a joined place name the model rendered as a phrase. N8: lost names 19 → 9;
what is left: a first name written truly alone once (its other lone uses follow a capital or carry a possessive), a
place whose first word is a stop word ("Other …"), a shop written with a possessive inside it, a firm joined by "and",
two nicknames and a place written two or three times, a pet name written alone twice that the model pre-scan did not
propose, a place written with a lower-case word ("… garden"). Time: the longest owner book takes 24.2 minutes on 26b
in r7 (25.4 in r6, 24.0 in r3), inside Balanced's 25 with no margin: the names brought back are read too. Recurring
terms: unchanged by these rules.

**N5's gender gate on 26b is not met as written, and cannot be by evidence.** Every 26b miss in r6 is one of: a person
the book gives no evidence for (the code's pronoun tally is 0/0 and the reading gave no vote: four minor men of owner
book 1 who are only spoken to or of in speech, the first-person narrator of owner book 2, a woman and a man of owner
book 3 written in quoted testimony, and a couple's shared surname, unknown on purpose), a demon the gold calls neuter
whom the book's narration calls "he" four times, and a name still lost (written truly alone once). Leaving out the 8
persons with no evidence in the book, gender on 26b in r6 is 44/46 (96 %), 7/7, 39/39 and 39/39: every book at or over
95 %. On e4b the same count is 44/46, 5/7, 39/39, 38/39: owner book 2 misses two persons with evidence (one typed a
term, one with a vote against its tally). Proposed task text for N5's gate (owner decision): "characters with five or
more mentions *that the book gives a pronoun, a gendered form of address or a verified reading vote for* 95 % right on
26b, 90 % on e4b; the persons with none are listed by the suite and stay unknown (asked at Start)", with the suite
reporting that denominator. Open, not a code rule: an unreadable term-reading reply is not asked again, which r4 and
r7 show costs a person now and then.

*Decided 2026-10-15, rescored (15i.N5).* The owner took the proposed gate text: the gender gate counts the persons the book
gives a pronoun, a gendered form of address or a verified vote for; the others stay not specified, are marked
`GlossaryEntry.noGenderEvidence` (the names pass sets it on a person with a 0/0 tally and no vote; any later gender or the
person's edit clears it), read "not specified — no evidence in the book" in the Names & style gender cell, and the suite's
report and JSON give gender over evidence persons (`genderOverEvidence`, `noEvidence`) beside the old denominator. The r6 and
r7 reports predate the mark, so they are rescored without a model run: a person who got an entry of the right type and an
unknown gender is read as "no evidence" (an upper bound on that group: a person with a vote against its tally is in it).
Pooled over all fourteen books: r6 26b 147/149 (98.7 %, was 93.6 %), r6 e4b 144/147 (98.0 %, was 91.7 %), r7 26b 145/148 (98.0 %,
was 92.4 %), r7 e4b 144/147 (98.0 %, was 91.7 %). By owner book (1 / 2 / 3 / 4): r6 26b 44/46, 7/7, 39/39, 39/39; r7 26b
44/46, 6/7, 38/38, 39/39; e4b (r6 and r7) 44/46, 5/6, 39/39, 38/38. The pooled gate (95 % on 26b, 90 % on e4b) is met; read per
book it is met on 26b in r6 and missed in r7 and on e4b by owner book 2 alone (8 persons: one reading reply that could not be
read, one person typed a term), so N5 stays open until the owner says whether the gate is pooled or per book.

**Scoreboard: comparing prompt rounds (A7).** Every eval report's JSON starts with a `provenance` object (`EvalProvenance`:
ISO timestamp, short git SHA and dirty flag, a 12-hex hash of `modules/pipeline/src/main/resources/ua/bookloom/pipeline/prompt/**`,
model, `BOOKLOOM_EVAL_LABEL`, provider, window, dial (`BOOKLOOM_EVAL_DIAL`, Balanced when unset; the judgement suites record the dial they ran at, Fast when unset), brief preset and overrides, suite). Besides `build/reports/promptEval`
(overwritten by the next run) each report is kept as `report.txt` and `report.json` in
`.temporary_context/eval-history/<yyyyMMdd-HHmmss>-<suite>-<model[-label]>/` (git-ignored; `BOOKLOOM_EVAL_HISTORY_DIR`
moves it, `off` disables it; a name that exists gets `-2`). Only what the report holds is written. The JSON carries, where the
suite has the data: pass rates, defect counts (mixed script, source-language leftover, protocol or closer residue, control
characters, missing vocative, quote findings, gender slips, name variants), `wastedCallRate` (failed or repeated calls per call),
calls per segment or case and, for the sequence suite, tokens and seconds per call kind (`costByKind`).

```bash
BOOKLOOM_EVAL_LABEL=round7 BOOKLOOM_EVAL_SUITE=terms ./gradlew :pipeline:promptEval   # a run, kept in .temporary_context/eval-history
scripts/eval-score.py list                                                          # the kept runs
scripts/eval-score.py diff previous:terms:e4b latest:terms:e4b                      # or two directories or globs
scripts/eval-score.py diff A B --noise 3                                            # noise band, default 5
```

`diff` prints what changed first (SHA, prompt hash, model, window, brief), then each metric as A, B, delta and `+` (better),
`-` (worse), `~` (inside the noise band), `=` (same), `?` (no direction known). The band is 5 percentage points for rates and
5 % of A for counts and costs, because the documented run-to-run noise is prose, not machine-readable (about 1.5 points of
dominant share and 2 points of flagged in the sequence eval, see 15e.13). The fast loop for a prompt round: edit the prompt,
run the offline `StageEvalRunnerTest` and `scripts/test-focused.sh`, then one `:pipeline:promptEval` run with a label, then `diff`.
`python3 scripts/eval-score.py selftest` and `python3 -m unittest discover -s scripts/tests` check the script.

**Prompt round 6 — draft rules rewrite (plan 16a, Phase C): reverted, no gain.** Control = HEAD `bd629759` (prompt hash `95c7d4ac6dc2`),
candidate = the rewritten DRAFT/BATCH-DRAFT rules + end checklist, token rules moved into the conditional `[Immutable tokens]` block,
the style default dropped when a register line follows, and uk/ru quotes, dialogue and venue-name lines. LM Studio `google/gemma-4-e4b`,
`burning-chrome` preset, window 16384, three runs per arm (`.temporary_context/eval-history/*r6-control*`, `*r6-cand*`, `*r6-split*`). Run-to-run spread of
the control was zero on every group (the seed is fixed per call), so a range proves less than it looks: one case flipping is one sample.
Moved for the better: `ascii-dialogue/draft` 33 % → 67 % (0.67..1.0), replay-fast pass 90 % (90..92.5) → 95 %, `injection-hello` fixed, default
`falsePositive` 17.6 % → 5.9 %. Did not move: `venue-name/draft` 0/2 (the model now writes «Синього якоря», still the genitive), `slang/draft`
60 % (0.4..0.6), `numbers/draft` 67 %, batch `tooShort`. Regressed: `quotes/draft` 100 % → 89 % (`quotes-unclosed-2` unparsed on an all-ASCII source),
default `gate` 94.3 % → 91.4 % (`link-pair` put its token pair around nothing, `names-no-token` left "Vance" in Latin). The split without the token
move kept the ascii gain (100 %) but lost the replay gain and `quotes/draft` fell to 78 %. The candidate was also not shorter: the token rules
moved into the user message (+100 words) and the uk language lines grew by 88 words. The decision rule (two targets improved and no key metric
worse than the control spread) was not met, so the edit was reverted; the idea worth retrying is the quotes line alone with a worked ASCII-quote example
(round 8).

**Corpus eval (15d.1).** `scripts/eval-matrix.sh [--models "ollama:<id> lmstudio:<id>"] [--stability N] [--only corpus]` runs the
prompt eval plus 19 labelled defect cases, once sent to the per-chunk reviewer (removed in group 14; the "judge FN/FP" columns below are that history) (`src/test/resources/eval/defects.json`: garbled word, mixed script, unbalanced
« », English left in, idiom, gender slip, lexical drift, omission, meaning, short lines) over Ollama and LM Studio and prints
one table. `falseNegative` is the share of defective candidates the reviewer left alone (one minus the defect catch rate), `falsePositive` the share of clean ones
it would have changed (false-alarm edits), `tokenBreaks` the applied edits that changed a placeholder token (always 0), `stability` the share of cases whose `BOOKLOOM_EVAL_STABILITY` repeats agree. Thresholds per model class are in
`eval/thresholds.json`. Env: `BOOKLOOM_EVAL_URL`, `BOOKLOOM_EVAL_PROVIDER=lmstudio`. 2026-10-03, one sample, stability 1:

| Model | judge FN before → after | FP before → after | other |
|---|---|---|---|
| gemma4:e4b-mlx | 38% → 25% | 0% → 0% | all draft rates 100% except marker 94% |
| gemma4:e4b-mxfp8 | 38% (not re-run) | 0% | |
| google/gemma-4-e4b (LM Studio) | 50% → 25% | 0% → 0% | gate 96% |
| gemma4:12b-mxfp8 | 12% → 0% | 0% → 9% | |
| gemma4:12b-mlx | 25% (not re-run) | 0% | |
| gemma4:26b-mlx | 12% (not re-run) | 0% | judge separation 75% |
| google/gemma-4-26b-a4b-qat (LM Studio) | 0% (not re-run) | 0% | every rate 100% |
| gemma4:e2b-mlx (floor) | 25% → 38% | 18% → 18% | gate 84%, judge separation 75% |
| qwen3.8:27b-mlx | 25% (not re-run) | 0% | |
| qwen/qwen3-vl-4b (LM Studio, no longer installed) | 88% (not re-run) | 0% | judge accepts nearly everything |
| muse-glimmer:30b-nvfp4-dflash | 12% (not re-run) | 9% | script 48%, gate 84%, injection 50% |
| gpt-oss:20b (Ollama) | n/a | n/a | parse 6%: `emptyCompletion` — Ollama's `think:false` is ignored by gpt-oss, the cap is spent on reasoning (needs an `:llm` fix) |
| qwen/qwen3-4b-2507 (LM Studio, no longer installed) | n/a | n/a | run hung over 49 minutes and was killed |

**Draft (translation) eval, 2026-10-03.** The 26 draft/fix cases plus six new ones (balanced « », he/she agreement, idiom,
short line) run for every model. gpt-oss:20b was failing on Ollama's native endpoint (`think:false` is ignored, the cap
went to reasoning, `emptyCompletion`): the client now asks a gpt-oss model for `think:"low"` and adds 1024 tokens of
reasoning headroom to its cap (`OllamaClient.thinkControl`, `withReasoningHeadroom`; both gone since redesign task 6.7:
gpt-oss's `low` now comes from its profile row, and a model that always thinks gets the profile's reasoning reserve,
`max(2048, 4·S + 35·n)` tokens, instead of the flat 1024). The draft user prompt ends with a
reminder that the target is the target language, never a copy of the source and never "...".

| Model | parse | gate | script | marker | injection | note |
|---|---|---|---|---|---|---|
| gpt-oss:20b | 6% → 95% → 97% | 8% → 94% → 97% | 7% → 91% → 97% | 6% → 82% → 86% | 100% | fix, then prompt |
| muse-glimmer:30b | 100% | 84% → 87% | 54% → 74% | 68% → 77% | 50% | still copies source on some cases |
| gemma4:e4b-mlx | 100% | 100% | 100% | 96% | 100% | unchanged |
| gemma4:12b-mxfp8 | 100% | 100% | 100% | 100% | 100% | unchanged |
| gemma4:26b-mlx | 100% | 100% | 100% | 86% → 96% | 100% | judge separation 50% |
| gemma4:e2b-mlx (floor) | 97% | 87% | 97% | 73% | 100% | |
| google/gemma-4-e4b (LM Studio) | 100% | 97% → 94% | 100% → 97% | 91% → 86% | 100% → 50% | one sample; possible noise |

**New LM Studio models, 2026-10-04** (current prompts, one sample): `google/gemma-4-e2b` parse 97%, gate 94%, script 97%, marker 86%,
judge false-negative 38% / false-positive 9% (an e2b floor like the Ollama build). `qwen/qwen3.8-27b` parse, gate and script 100%, marker 86%,
judge false-negative 25% / false-positive 0%. One LM Studio model that was tried and excluded (weak Ukrainian drafts, parse 83%, obeys instructions hidden in the book text) also stalled on the
judge call: with the judge's JSON-schema `response_format` (with `maxItems`/`maxLength` limits) LM Studio never answered (100 s, model stuck in
`PROCESSINGPROMPT`), while the same prompt without a schema answered in 5 s and the simple draft schema worked. Keep the reviewer schema of
task 15d.6 flat and free of `maxItems`/`maxLength`, and consider dropping `response_format` for a model after a structured call timed out.

The other models (12b-mlx, e4b-mxfp8, qwen3.8:27b, gemma-4-26b-a4b-qat, qwen3-vl-4b) were measured before the reminder
and not re-run. Marker rates also moved because two over-strict markers were loosened. qwen3-4b-2507 is excluded from
the default matrix (it hung); `MODEL_TIMEOUT` (default 1500 s) now stops any model that stalls.

"After" is the judge prompt with a defect checklist (garbled word, mixed script, unbalanced quotes, name drift, gender
agreement, omission). The misses that remain on the 4B class — mixed script, unbalanced quotes, drift — are the
deterministic checks of 15d.2.

Before, the judge scored an untranslated English candidate 1.0, a pair was moved off its words and a locked name was
written out instead of its token; after, the one miss on e4b is the drop cap (`⟦g0⟧Т⟦g1⟧іч` — the source letter kept),
and e2b drops pairs, which the run's placeholder gate refuses and sends to repair. The suggestion rows read `Елеонора Венс`, `Гарроу Вейл`,
`Інститут Меридіанського зондування` and `Амулет`; the first prompt came back with `Вeнс` (a Latin `e`, now put back as
Cyrillic) and every place and thing as `neuter` (a suggested gender is now kept for characters only).
