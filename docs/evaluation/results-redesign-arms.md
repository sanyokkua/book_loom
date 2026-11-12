# Results: redesign arms

*The arms measured while the redesign was built.*

## Redesign arms {#redesign-arms}

The paired evals of the `redesign-translation-pipeline` change (design D24). Each arm's result is recorded here and in the
`Result` cell of its row in the change's `design.md`.

**Arm 1: judge calibration `cal-1` (task 1.9).** Judge `gemma4:26b-mlx` on Ollama, single-flight, rubric `bl-judge-1`
frozen with no tuning rounds (hash `d7b8b8ded24e`, file SHA-256
`d7b8b8ded24e4677f50c39e28a6cb69fda502c5cf659737aeb0b8117c8fcaf1b`).

- *Items.* 60 invented items: 30 single translations and 30 pairs, 25 ru->uk and 15 long, split 10 few-shot, 25 dev and
  25 test.
- *Labels.* Grader `agent` is a blind Claude agent. Grader `codex` is the majority of three Codex CLI models asked by
  hand (`gpt-6-luna` and `gpt-6.1-terra` at reasoning none, `gpt-6.1-sol` at low because it rejects none). A single
  item is agreed when `meaningError` matches and the scores are within 1 (the mean is kept); a pair is agreed when the
  winner matches. 59 of 60 items were agreed; 1 was left out (`cal-p29`).
- *Dev, reasoning off.* TPR 3/3 (Wilson lower bound 0.438), TNR 9/9 (0.701), position consistency 12/13 (0.923),
  Kendall tau-b 0.555 over 12, pairwise accuracy 10/13 (0.769).
- *Hidden test, reasoning off, run once.* 13 single and 11 pair items scored. TPR 4/4 (Wilson lower bound 0.510), TNR
  9/9 (0.701), position consistency 9/11 (0.818), tau-b 0.716 over 13, pairwise accuracy 11/11 (1.000), 1 item left out.
- *Reasoning on, dev.* Only 5 of 12 singles and 9 of 13 pairs gave a readable answer, and a run took 23 minutes against
  about 3. It fixes nothing net and loses items, so the judge's reasoning stays off.
- *Decision rule.* TPR and TNR Wilson lower bounds >= 0.8 and position consistency >= 0.7. It fails on the Wilson
  bounds (0.510 and 0.701) although both point rates are 1.0 and position consistency (0.818) passes. The owner kept the
  rule and accepted the fallback. Cause: the bound reaches 0.8 only with about 16 positives and 16 negatives all
  correct, and the test split has 13 singles (4 positives, 9 negatives), so the rule cannot pass at this split size.
- *Outcome.* The judge is a pairwise screen only: every case it marks discordant is read by the agent with a Codex
  second opinion by hand. Reports carry `judge.calibration.passed = false`, read from
  `modules/pipeline/src/test/resources/eval/judge/calibration.json`.


**Arm 2: batch format, JSON items against markers (task 2.2).** Translator `gemma4:e4b-mlx` on Ollama, Balanced, single-flight,
K = 3 (seeds 1000-1002), then `gemma4:26b-mlx` once with K = 1; `dual` was not run. The format was chosen by the eval option
that task 2.3 then removed with the property the job read. The arm's runner, judge screen and report classes (`ArmTwoRunner`,
`ArmTwoReport`, `ArmTwoEvalTest`, `ArmTwoJudgeEvalTest`) and its `--suite arm2` were removed in task 2.3 too; the result below
was recorded in commit dc00e4ab, and `ArmSegments` with `eval/arms/arm-2-segments.json` stay.

- *Segments.* The 60 ids of `modules/pipeline/src/test/resources/eval/arms/arm-2-segments.json` (written before the run, checked by
  `ArmSegmentsTest`): all 33 blocks of `mini-en` and all 17 of `mini-ru` (each needs a model call) and the first ten paragraphs of
  the `sequence` book's chapter 1 (`sequence/book.md:1` to `:10`, the Markdown importer's block numbers; block 0 is the heading).
  Each repeat runs the three books through the names pass, the job and the consistency pass (`--suite arm2`, `ArmTwoEvalTest`);
  every repeat's stored target, with its marks, is kept in the run's `cases.jsonl`.
- *Grading.* The judge (`gemma4:26b-mlx`, `bl-judge-1`, reasoning off; `ArmTwoJudgeEvalTest`) screened each paragraph and repeat
  pairwise with the order swapped. On e4b 39 of 60 paragraphs differed in some repeat; the judge preferred JSON in 22 and markers in
  18 paragraph-repeats and marked 29 paragraphs discordant. The agent read all 29 blind (`eval-score.py blind --all-repeats`, three
  repeats of each side, the format hidden). A paragraph is WRONG for a format when 2 of its 3 repeats have a meaning error, an
  omission, source-language residue or lost marks.
- *Result on e4b.* WRONG: JSON 8, markers 8 (both wrong: `m01`, `m06`, `m10`, `m23`, `sequence:1`, `:4`, `:9`; JSON only `m32`,
  a dagger for a stick; markers only `m21`, salt cod turned into a non-word). Two readings were close calls, one per side (JSON
  `sequence:5`, "the whole street" read as "all the streets"; markers `m31`, "came about the nets" read as "came for the nets"); counted
  either way the difference stays 0 or 1. No mark was lost in either format. Hard-gate failures in the first evaluation: JSON 3,
  markers 3 (the same paragraph, `sequence:9`, in every repeat); flagged 3 and 3.
- *Cost, reported and not weighed.* Per repeat over the three books: JSON 34 translation calls (0.57 per listed segment), 51 calls with
  the names and consistency passes (0.86), 204 s (3.4 s per segment); markers 34 (0.57), 51 (0.85), 179 s (3.0 s). The first repeat of
  each holds the model load.
- *26b, K = 1.* 22 paragraphs differed, the judge marked 4 discordant and 1 unreadable; read blind: JSON 0 WRONG, markers 3 (`m18` a
  garbled name, `m26` the meaning reversed, `sequence:9` the imp named "the faceless one"). Hard gates 1 and 1, flagged 0 and 0; JSON
  39 translation calls and 335 s, markers 35 and 318 s. It does not reverse the e4b result.
- *Decision rule.* Markers replace JSON only with at least 2 fewer WRONG paragraphs and no hard-gate count higher by 2 or more.
  Markers have 0 fewer (e4b) and 3 more (26b), so **JSON stays**; task 2.3 deletes the markers and `dual` code. Codex was not asked;
  the two close calls above are left for the owner to read.
- *Also seen.* Both formats wrote Russian words on e4b and 26b (`Мост`, `Трость`), which is none of the four WRONG classes; e4b's
  `m08` has it in 1 of 3 JSON repeats and 3 of 3 markers repeats.

**Arm 3a: the maker's samplers against BookLoom's own (task 6.10).** Default: BookLoom's temperature per call kind and nothing
else of its own (neutral `top_k`/`top_p`/`min_p`/repeat penalty only where the detected engine's defaults are not neutral).
Alternative: the row's vendor `top_k` and `top_p` sent explicitly with the same temperatures. The Gemma 4 rows held the model
card's values for the arm (top_p 0.95, top_k 64; research `03-models/families/gemma4.md` F16). The rows have no sampler fields
in `ModelProfile`, so the eval reached the wire through a test-only switch (`BOOKLOOM_EVAL_SAMPLING=vendor`, read in
`PromptEvalTest.chatModel`) that stored the vendor values as the model's Custom sampling choice with no temperature of its own;
the test log shows `Sampling … source=custom topK=64 topP=0.95` on every call of the vendor runs and none on the default runs.

- *Probe, 5 pairs at temperature 0.3, fixed seeds, invented sentences, each request sent twice.* Ollama `gemma4:e4b-mlx`
  (only temperature against temperature + 64/0.95): default against vendor differed in 1 of 5 seeds, default against itself in
  2 of 5, vendor against itself in 1 of 5. LM Studio `google/gemma-4-e4b` (neutral 0/1.0/0/1.0 against 64/0.95): 2 of 5,
  1 of 5 and 2 of 5. The MLX runtimes are not deterministic at a fixed seed, so the probes differ only inside their own noise;
  the mini runs were made anyway because the outputs did differ (`.temporary_context/arm3/arm3a-*.json`).
- *Why Ollama cannot show an effect.* `ollama show gemma4:e4b-mlx --parameters` and the 26b's both hold `top_k 64` and
  `top_p 0.95`, and BookLoom sends no sampler to Ollama by default, so on Ollama the two sides sample alike. The eval's LM Studio
  provider is saved without engine detection (engine `GENERIC`), so it got no neutral values either and LM Studio's own Gemma 4
  defaults (the same 64/0.95) applied; only the probe above compared neutral values with the vendor's on the wire.

| Grader pass rate, mean of K (per repeat); hard-grader failures | Default | Vendor |
|---|---|---|
| Ollama e4b, `mini-en`, K = 3 | 0.9697 (0.9675 / 0.974 / 0.9675); 0 | 0.9718 (0.9675 / 0.974 / 0.974); 0 |
| Ollama e4b, `mini-ru`, K = 3 | 0.9533 (all three); 0 | 0.9533 (all three); 0 |
| LM Studio e4b, `mini-en`, K = 3 | 0.9026 (0.9026 / 0.8766 / 0.9286); 4 (`numbers-kept-m30` ×3, stress mark ×1) | 0.9264 (0.9026 / 0.9416 / 0.9351); 3 (`numbers-kept-m30` ×3) |
| LM Studio e4b, `mini-ru`, K = 3 | 0.9502; 0 | 0.9533; 0 |
| Ollama 26b, `mini-en`, K = 1 | 0.974; 0 | 0.974; 0 |
| Ollama 26b, `mini-ru`, K = 1 | 0.9439; 2 (stress mark) | 0.9439; 2 (the same) |

- *Paired.* `eval-score.py diff --paired`: Ollama e4b `mini-en` 1:0 discordant (`dialogue-quote-form`, p = 1.0), `mini-ru`
  none; LM Studio e4b `mini-en` `every-usable` 8:2 for the vendor side (p = 0.11, a screen pass but no real effect) on two
  sides that sampled alike, `mini-ru` 1:0 (`dash-kept`); 26b none. Noise bands 0.0175 / 0 (Ollama e4b), 0.070 / 0.038
  (LM Studio e4b). 21 of 33 (`mini-en`) and 15 of 17 (`mini-ru`) first-repeat Ollama outputs were identical across the sides.
- *Cost, reported and not weighed.* Ollama e4b `mini-en` 23 against 22 calls, 134 against 123 s; 26b 25 against 23 calls,
  163 against 154 s. LM Studio e4b took about 2,040 s per `mini-en` repeat on both sides: the eval's `GENERIC` engine sends no
  thinking control, so Gemma 4 thought on every call there.
- *Decision rule.* The vendor set is adopted only if no hard grader is worse and token fidelity is within the noise band. Both
  hold, but the vendor set wins nothing beyond noise anywhere, and on the setups measured the two sides sampled alike; D24's
  tie rule keeps the simpler side. So **BookLoom's own sampling stays and the vendor values are deleted from the rows**
  (`VendorSampling`, `BuiltInRow.vendor`, the Gemma 4, Gemma 3 MamayLM, Qwen 2.5 and Qwen 3.5 values) with the test switch;
  no requirement changes. Not measured: a detected LM Studio, llama-server or Unsloth provider, where the default sends neutral
  values and the vendor set would really differ; only the 5-pair probe touched that case.
- *Runs* (`.temporary_context/eval-history/`): Ollama e4b `20261030-173138`/`-173656` (en), `-173842`/`-174023` (ru); LM Studio
  e4b `20261030-213907`/`-232331` (en), `-234658`/`20261031-001356` (ru); 26b `20261031-001644`/`-001925` (en),
  `-002046`/`-002204` (ru); default first in each pair.

**Arm 3b: batch start size on a small model (task 6.10).** Translator `gemma4:e4b-mlx` on Ollama, Balanced, single-flight,
the whole `sequence` book (328 segments, the "300-segment slice" of the task; it holds no more), K = 3 (seeds 1000-1002), the
two sides paired by seed. The start size, which is also the ceiling the size grows back to, is bound in the eval's job by the
test-only `BOOKLOOM_EVAL_BATCH_START` (`EvalBatchStart`, read by `SequenceJobs`); production still starts every model at 8
until group 10 wires the profile's `batchSize`. `gemma4:26b-mlx` was not run: a large model starts at 8 either way.

| Per repeat (seeds 1000 / 1001 / 1002) | Start 4 (default) | Start 8 (alternative) |
|---|---|---|
| Items that fell back to their own draft (all reasons) | 11 / 12 / 10 (mean 11.0) | 13 / 15 / 12 (mean 13.3) |
| of them for a lost or changed token (`[TOKENS]`) | 7 / 8 / 5 (6.7) | 4 / 4 / 4 (4.0) |
| of them for a lost mark (`[MARKERS]`) | 0 / 0 / 0 | 1 / 1 / 1 |
| of them for quote marks (`[QUOTES]`) | 2 / 1 / 2 | 4 / 8 / 4 |
| Segments failing a hard gate in their first evaluation | 11 / 9 / 10 (mean 10.0) | 14 / 13 / 11 (mean 12.7) |
| Flagged | 10 / 10 / 9 | 10 / 10 / 9 |
| Calls (per segment) | 155 / 152 / 153 (0.47) | 118 / 119 / 114 (0.36) |
| Seconds per segment | 3.91 / 3.96 / 3.83 | 3.79 / 3.48 / 3.50 |
| LTCR-1, first repeat | 93.2 % | 93.0 % |

- *Decision rule.* 8 only if token fidelity, fallbacks and hard-gate failures are all no worse beyond the noise band; seconds and
  calls are reported, not weighed. Token fidelity is better at 8 (4.0 token fallbacks against 6.7, though 8 adds one lost mark per
  repeat), but fallbacks are worse in every seed (+2, +3, +2) and first-round hard-gate failures are worse in every seed (+3, +4,
  +1); both means at 8 lie above the largest value any start-4 repeat gave (12 and 11), the spread that is the noise here. So
  **the rows keep 4** for small models (the batch-size scenario value stays 4) and D11 is unchanged. 8 saves 23 % of the calls
  and about 7 % of the time per segment.
- *Runs.* `eval-history/20261030-184427-sequence-gemma4_e4b-mlx-en-uk-balanced-unset` (start 4) and `20261030-194329-…`
  (start 8). The switch was deleted with the arm settled. The report holds the first repeat in full; the per-repeat counts above were read from the run's test log with the
  patterns `SequenceLogCapture` uses (`Batch item falls back …`, `Evaluated segment=… round=0 … hardGatesPass=false`), and the first
  repeat's counts match the report's.

**Arm 4a: what a model knows about a bare name (task 7.8).** The production name-knowledge stage (`NameKnowledgeCalls`, the
`name-knowledge` templates, batches of 40, no book text) asked about the 61 invented names of
`modules/pipeline/src/test/resources/eval/datasets/bare-names/names.json` (dataset `bare-names` v1, root hash `15f24644de82…`): 26
English-looking and 20 Russian-looking names with a gold gender, and 15 unisex ones (given names used for both, or a bare surname with
no gendered ending) with none. The English names go out in an en→uk frame and the Russian ones in a ru→uk frame, as a book of
each language asks them (`BareNamesEvalTest`, `BOOKLOOM_EVAL_SUITE=bare-names`, scored by `BareNamesScore`). Ollama, single-flight.

| Run | Answered | Confident (≥ 0.7) gendered | of them wrong | Unisex answered ≥ 0.7 | Rule |
|---|---|---|---|---|---|
| `gemma4:e4b-mlx`, K = 3 (seeds 1000–1002) | 61/61 each | 45 each | 0 (0 %) | 0/15 each | pass |
| `gemma4:26b-mlx`, K = 1 | 61/61 | 46 | 0 (0 %) | 0/15 | pass |

- *Decision rule.* A model class keeps its likely values in prompts only if at most 5 % of its confident answers are wrong and
  every unisex name is answered below 0.7. Both classes pass, so **no `NO_KNOWLEDGE_HINT` quirk is set** and the profile code is
  unchanged. Both models answered every unisex name "neuter" at 0.4–0.6 (the prompt allows no "unknown"); e4b also gave the
  gendered `Edrun Moss` "neuter" at 0.6, its one gendered name below the bar. e4b's three repeats answered identically.
- *Runs* (`.temporary_context/eval-history/`): `20261031-224420-bare-names-gemma4_e4b-mlx-arm4a`,
  `20261031-232345-bare-names-gemma4_26b-mlx-arm4a`; logs in `.temporary_context/arm4/logs/`.

**Arm 4b: the likely-gender hint on the character line (task 7.8).** Default: a character whose gender is only likely is listed
by name alone (`Pell`). Alternative: `Pell — male (likely)`, which no check enforces. The alternative reached the job's prompts
through a test-only `PromptVariants.likelyGenders` field (bound by `BOOKLOOM_EVAL_VARIANTS=likelyGenders=HINT`); the hint lines
were seen in the calls of every hint run and in none of the default runs. Translator on Ollama, Balanced, single-flight, the `mini`
suite (`mini-en` v2 root `febe1374fd3a…`, `mini-ru` v2 root `852efa31cc1e…`).

- *Characters with no evidence.* The mini books' scan keeps no such character (Pell is left out of `mini-en`'s glossary), so each
  run adds them after the names pass, as the person does with "+ Add", and runs the name-knowledge stage on them as "Review with
  model" does (`BOOKLOOM_EVAL_ADD_CHARACTERS`, `MiniRunner`): `Pell` in `mini-en`; `Игнат`, `Ивар` and `Але́ксей` in `mini-ru`.
  Adding them before the names pass was tried first and dropped: a glossary that is not empty is not scanned, so the book's own
  characters were lost. Every added character ended the names stage likely male, except Pell on 26b, likely neuter (the hint then
  read `Pell — neuter (likely)`). `Мирена` stayed likely in every `mini-ru` run and `Велемир` in the 26b one, so they were hinted too.
- *Scored segments*, fixed before the runs were read: every segment that names a character whose gender was likely in the run —
  `mini-en` m06, m11, m23, m33 (Pell, gold male from m33's "he"); `mini-ru` ch02.md:4, :6, :8, :10, :14, :16 (Игнат, Але́ксей and
  Ивар male, Мирена female). A gender-agreement error is a past verb, adjective or pronoun in the Ukrainian that agrees with one of
  them in the wrong gender. The agent read every repeat of both sides (`.temporary_context/arm4/arm4b-scored-segments.txt`).

| Gender-agreement errors (segment-repeats read) | Name only (default) | Hint |
|---|---|---|
| e4b, `mini-en`, K = 3 | 0 (12) | 0 (12) |
| e4b, `mini-ru`, K = 3 | 0 (18) | 0 (18) |
| 26b, `mini-en`, K = 1 | 0 (4) | 0 (4) |
| 26b, `mini-ru`, K = 1 | 0 (6) | 0 (6) |

- *Also reported.* Grader pass rate per repeat, default against hint: e4b `mini-en` 0.947 / 0.947 / 0.965 against 0.965 / 0.947 /
  0.965, `mini-ru` 0.962 ×3 on both sides; 26b `mini-en` 0.965 and 0.965, `mini-ru` 0.923 against 1.0 (a stress mark kept in
  ch01.md:15 on the default side, no character in it). Every miss is outside the scored segments. Calls and seconds (first
  repeat): e4b `mini-en` 24 and 108 s against 25 and 101 s, `mini-ru` 22 and 52 s against 21 and 37 s; 26b `mini-en` 28 and 170 s
  against 26 and 206 s, `mini-ru` 20 and 95 s against 20 and 72 s.
- *Decision rule.* The hint is kept only with fewer gender-agreement errors. It has 0 against 0 on both models, so **the name alone
  stays** (the likely value is still stored and shown); the `likelyGenders` field and its plumbing were deleted, no requirement
  changes. Why it changed nothing here: English past forms in the source already show the gender the agreement needs ("Pell…
  said he hoped"), and Russian past forms carry it, so the model never had to guess from the name; a source whose gender shows
  only through the name is not in the mini books.
- *Runs* (`.temporary_context/eval-history/`): e4b `20261031-230736`/`-231239` (en), `-231458`/`-231648` (ru); 26b
  `20261031-232647`/`-233021` (en), `-233204`/`-233327` (ru); default first in each pair. The aborted first batch (no likely
  character in `mini-en`) and the pilots are in `.temporary_context/arm4/void-runs/` and are not counted. `MiniReport` now writes
  every repeat's stored targets and characters (`repeats`), which these counts were read from.

**Arm 5a: the rules layout of the translate frame (task 11.8).** Default: the context builder's blocks 3-5 (book rules chosen by
the book's facts, the style sheet with the foreign-passage line, the language-rules section without the rules a book rule
states). Alternative: the batch prompt's numbered rules, bare style sheet and whole language-rules section as they read before
task 11.5. Translators `gemma4:e4b-mlx` (Balanced, K = 3, seeds 1000-1002) and `gemma4:26b-mlx` (once, K = 1), Ollama,
single-flight, paired by seed (`eval-score.py diff --paired`).

- *Mini books.* e4b `mini-en` 37 cases and `mini-ru` 26 graders: 0 discordant on every grader, no real effect (p = 1.0), noise
  band 0.0175 on `mini-en` and 0 on `mini-ru`. 26b `mini-en` 34 cases and `mini-ru` 18: 0 discordant, no real effect.
- *`sequence` (328 segments, e4b, summary lines only).* Builder against today: first-round hard-gate failures 14 against 15,
  LTCR-1 93.2 % against 92.8 % (today -0.4, under the 1.5 threshold), flagged 3.4 % on both, fallbacks 2.8 % against 2.4 %,
  3.27 against 3.46 s per segment (reported, not weighed).
- *Decision rule.* The builder is kept unless token fidelity, fallbacks or first-round hard gates are worse beyond noise, or
  LTCR-1 falls 1.5 points. None happened, so **the builder stays**; `TodayRules`, `today-rules.properties` and the
  `rulesLayout` field of `PromptVariants` were deleted. The generic `--variants` plumbing stays for arm 5c.
- *Runs* (`.temporary_context/eval-history/20261105-*arm5a*`).

**Arm 5c: rules per book or per window (task 11.9).** Default: the book rules chosen once at run start from the whole story,
in the fixed system part (block 3). Alternative: the rules chosen for each call from its own items and map lines, sent as the
first block of the user message. Translators `gemma4:e4b-mlx` (Balanced, K = 3, seeds 1000-1002; eval jobs start batches at 8)
and `gemma4:26b-mlx` (once, K = 1), Ollama, single-flight, paired by seed.

- *Mini books.* e4b `mini-en` 34 cases and `mini-ru` (ru-uk) 18: paired sign test, no real effect; only `dialogue-quote-form`
  moved on en-uk (book 1 : window 0 discordant, p = 1.0). 26b once, en-uk and ru-uk: 0 discordant cases, no real effect.
- *`sequence` (328 segments, e4b, K = 1).* Per book against per window: gender slips 9 against 4, first-round hard-gate
  failures 13 against 11, flagged 10 against 11, batch fallbacks 6 against 7, LTCR-1 0.9265 against 0.9300, renderings per term
  2.2 against 2.0, name variants 1 against 0, 3.765 against 3.81 s per segment, prompt tokens 2.914e5 against 2.784e5, cached
  prompt tokens 1.408e5 against 1.259e5, prompt read 0.6864 against 0.6863 s per segment. One run a side, so no significance
  test: the gender-slip drop is a hint, not a win.
- *Batch size.* The `sequence` pair started batches at the model profile's 4 (`RunPacing.FROM_PROFILE`), the mini pairs at 8;
  the `sequence` figures are therefore not comparable with arm 5a's (batch 8).
- *Reading of the design.* "Per window" was built as my reading of the design's "the window's items": marks, digits and
  dialogue chosen from the focus items; agreement and inflection from the window's map lines, gated by the book-level facts.
  The owner may read it differently.
- *Decision rule.* Per window only on a quality win (sign test p < 0.05, or a screen confirmed on `sequence`) with no hard
  grader worse; a tie keeps per book (owner decisions D-9, D-28). Neither happened, so **per book stays**; the `rulesScope`
  field of `PromptVariants`, `RulesScope`, `WindowRules` and `RuleFacts.ofWindow` were deleted. The gender-slip drop (9 against
  4) is a hint for the owner, not a result. The eval-side prompt and cached-token sums, the profile forwarding in
  `StageProbe`/`SequenceFinishRecorder` and `RunPacing.FROM_PROFILE` stay.
- *Runs* (`.temporary_context/eval-history/20261105-*arm5c-*`).

**Arm 5b: key-term asks (task 12.9).** Default: the flat batch schema `{"items":[{"id","target"}]}`, no key-terms block, the
co-occurrence learner alone keeping recurring terms consistent. Alternative: the `terms` object in the schema, the `[Key terms`
block (block 11b) and `KeyTermAsks`, which stops asking after five asking batches that report no rendering. Both sides carry the
recently-present card and the earlier chapters' summaries in block 8, at Balanced, on `sequence` (328 segments, 8 chapters,
en-uk), batches starting at the model profile's size. `gemma4:e4b-mlx` K = 3 (seeds 1000-1002) and `gemma4:26b-mlx` once on
Ollama; then, once each, `gpt-oss:20b` on Ollama and `mamaylm-gemma-3-12b-it-v2.0` and `mistralai/ministral-3-14b-reasoning` on LM
Studio. Graded by LTCR-1, the share of a term's later occurrences that keep its first rendering.

| Model | Repeat | LTCR-1 flat / asks | Flagged | Batch fallbacks | First-round hard gates | s per segment |
|---|---|---|---|---|---|---|
| e4b | 1 | 92.6 / 94.3 | 11 / 11 | 7 / 6 | 14 / 13 | 3.93 / 4.07 |
| e4b | 2 | 94.6 / 93.9 | 11 / 12 | 7 / 8 | 16 / 13 | 3.95 / 4.12 |
| e4b | 3 | 95.4 / 91.8 | 11 / 12 | 10 / 8 | 13 / 15 | 3.87 / 4.13 |
| e4b | mean | 94.2 / 93.3 | | | | |
| 26b | 1 | 93.2 / 95.1 | 15 / 14 | 16 / 32 | 22 / 24 | 6.24 / 6.93 |
| gpt-oss 20b | 1 | 85.7 / 83.0 | 15 / 14 | 21 / 29 | 39 / 26 | 20.9 / 17.0 |
| mamaylm 12b | 1 | 88.3 / 92.6 | 25 / 26 | 30 / 33 | 21 / 18 | 15.3 / 16.7 |
| ministral 14b | 1 | 90.5 / 88.9 | 31 / 47 | 36 / 145 | 20 / 23 | 20.3 / 25.4 |

- *Reading.* On e4b, the target model, the asks stopped themselves in every repeat (the model reported no rendering in its
  first five asking batches, `keyTermAsksStopped` 1), so most of that run was flat too; the other models kept being asked. The
  asks were ahead on 26b (+1.9) and mamaylm (+4.3) and behind on e4b (-0.9 on the mean), gpt-oss (-2.7) and ministral (-1.6),
  a mean difference of about zero; ministral paid for them with 145 against 36 fallbacks and 25.4 against 20.3 s per segment.
  Every figure outside e4b is a single run, so the evidence is "no consistent gain", not "the asks are harmful".
- *Decision rule.* The asks return only if LTCR-1 falls 1.5 points or more without them on the target model e4b. It rose 0.9,
  so **the flat schema stays**. Deleted: `KeyTermAsks` and its tests, the `terms` object of `BatchSchema`, `ItemOutcome.terms`,
  `BatchReply.hasTerms`, the parser's terms reading (an extra `terms` field is still ignored), the batch stage's recording of
  verified reported pairs (`Lexicon.record`), the key-term list (`ChunkInputs.keyTermsIn`, `Lexicon.termsIn`) and its path
  through `TranslateContext`, `CallInputs`, the context builder and `WindowBlocks`, the `keyTerms` block slot and its
  `keyTerms.heading` words, and the `keyTermAsks` field of `PromptVariants` with its eval and report plumbing.
  `TermMappingVerifier` stays: the name checks use it through `AlternationGuard`.
- *Runs* (`.temporary_context/eval-history/20261106-*arm5b-{flat,asks}*`, logs `.temporary_context/arm5b-*.log`).

**Arm 6: chapter summaries (task 12.10).** Default: the Book Brief's switch, suggested from the story shape. Alternative: no
summaries. Balanced, en-uk, single-flight; e4b K = 3, 26b K = 1; `sequence` (8 chapters), then `mini-en` and `mini-ru`.
- *Result, `sequence`.* e4b LTCR-1 on 93.6/93.6/97.2 (mean 94.8) against off 94.0/92.6/92.8 (mean 93.1); repeat 0 flagged
  11 against 12, calls 261 against 251, 4.54 against 3.96 s per segment. 26b LTCR-1 93.4 against 92.1, flagged 16 against 14,
  calls 289 against 278, 6.02 against 5.65 s per segment. The summary gate kept 29 of 29 sentences over 7 chapters (the last
  chapter is never summarised), drop share 0 %, so the 20 % falsifier is not met.
- *Result, mini.* `mini-en` (e4b K = 3, 26b K = 1): pass rate identical on both sides (93.0 % e4b, 94.7 % 26b), 0 flagged,
  calls e4b 37 against 37, 26b 35 against 33, gate 3 of 3 kept (a three-sentence sample, weak evidence for the share).
  `mini-ru` is one chapter, so nothing is summarised and the sides are identical.
- *Rule.* The noise band is about 1.8 points; the LTCR-1 gain of about 1.7 is inside it, so "on" is not worse beyond noise
  and the owner is not told of a loss. The cost is about 4 % more calls and 5-15 % more time (reported, not weighed). **The
  switch and its suggestion stay.**
- *Hand grade* (`.temporary_context/arm6-grading.md`, 139 of about 460 differing chapter 1-7 segment pairs read; no
  automated grader exists). e4b: meaning and name errors OFF 22 narrator-gender slips against ON 16 and OFF 49 wrong
  or dropped names against ON 11, but about 35 of OFF's come from one title garbled in the same 17-18 segments of two
  repeats, from chapter 0 on, before any summary exists; without it the sides sit inside the spread between repeats.
  26b: even. So ON is not shown worse. The summary sentences themselves could not be graded from the eval runs (the
  reports keep counts only).
- *Hand check* (`.temporary_context/hand-12b/`, e4b on an invented two-chapter book): two real chapter-1 summaries were
  three Ukrainian sentences, true to the chapter with the right referents, but with non-words and renderings that
  contradict the decided text, and one carried leaked `<Names>` markup that the gate kept and block 8 sent on. The gate
  now drops a sentence holding markup (`dropped-markup`); it still checks letters and names, not meaning or agreement
  with the glossary.
- *Runs* (`.temporary_context/eval-history/*arm6-{on,off}*`, logs `.temporary_context/arm6-*.log`).

**Arm 7a: the per-chunk reviewer (task 14.2).** Default OFF: no reviewer call, so a chunk is draft, checks, the one
repair, and then the Proofread stage. Alternative ON: today's reviewer at Balanced as built (it fixes in place, the code
verifies every edit, a refused edit gets a directed fix), on the same pipeline. The alternative reached the job through
the test-only `PromptVariants.reviewer` field (`BOOKLOOM_EVAL_VARIANTS=reviewer=ON`). Every ON run made `REVIEW` calls
and no OFF run did. Balanced, Ollama, single-flight, paired by seed. `gemma4:e4b-mlx` ran `mini-en` and `mini-ru` with
K = 3 (seeds 1000-1002) and `sequence` with K = 3. `gemma4:26b-mlx` ran both mini books once (K = 1).

| Per repeat | OFF | ON |
|---|---|---|
| e4b `mini-en` grader pass rate | 0.931 ×3 | 0.914, 0.948, 0.931 |
| e4b `mini-ru` / 26b `mini-en` / 26b `mini-ru` | 0.963 ×3 / 0.948 / 0.963 | the same |
| Proofread rewrites kept (every mini run) | 0 | 0 |
| `sequence` LTCR-1 (%) | 96.6, 95.7, 89.6 (mean 94.0) | 88.9, 97.1, 96.8 (mean 94.3) |
| `sequence` narrator-gender slips | 5, 9, 2 (16) | 0, 6, 2 (8) |
| `sequence` first-round hard gates / flagged / batch fallbacks | 14, 12, 15 / 12, 11, 12 / 4, 8, 9 | 14, 15, 15 / 12, 11, 11 / 6, 8, 8 |
| Calls, e4b `mini-en` / 26b `mini-en` | 32, 31, 32 / 30 | 46, 38, 43 / 43 |
| `sequence` calls and seconds per segment | 0.92-0.95, 5.1-5.4 | 1.06-1.09, 5.5-6.6 |

- *Graders* (`eval-score.py diff --paired`). e4b `mini-en`: `address-same` 2:0 for ON and `every-usable` 1:0 for OFF.
  The `address-same` count is one mini case, counted on its two blocks: ON's second repeat used the same form of
  address in m25 as in m26. p = 0.5, noise band 0.0345. The screen counter reads "moves >= 2 cases", but the cases come
  from one repeat and the screen is not confirmed on `sequence` (see below). Every other set is 0:0 on every grader.
- *Judge on the final text after Proofread.* `gemma4:26b-mlx`, `bl-judge-1`, reasoning off, the order swapped. It read
  every paragraph and repeat whose final text differs between the sides; the source paragraph's neighbours were given as
  context. Proofread kept no rewrite in any mini run, so `ProofreadJudgeEvalTest` (which grades kept rewrites) would
  have had nothing to read. The judge was therefore driven by a throwaway test with the same `JudgeRunner` calls; the
  items and verdicts are in `.temporary_context/arm7a/judge/`.

| Set | Paragraph-repeats that differ | OFF better | ON better | Tie / unread | Sign test p |
|---|---|---|---|---|---|
| e4b `mini-en`, K = 3 | 61 | 19 | 28 | 14 / 0 | 0.24 |
| e4b `mini-ru`, K = 3 | 4 | 0 | 2 | 2 / 0 | 0.50 |
| 26b `mini-en`, K = 1 | 19 | 5 | 5 | 9 / 0 | 1.00 |
| 26b `mini-ru`, K = 1 | 0 | — | — | — | — |
| e4b `sequence`, K = 3 | 697 | 216 | 223 | 255 / 3 | 0.78 |

  Pooled over the e4b mini books it is 19:30, p = 0.15. On `sequence`, every repeat is close to even (77:68, 72:82,
  67:73), and the first-person and third-person chapters are too (81:86 and 135:137). Most of these differences come
  from the MLX runtime's own nondeterminism at a fixed seed (arm 3a). In arm 7c, two sides whose prompts differed
  only in the proofread call already had 14-19 of 33 `mini-en` blocks differ. So the judge is comparing two noisy
  draws, and neither side is ahead.
- *The `gender` and `LTCR-1` lines of the matrix table.* The table's "5 against 0" slips and "96.6 % against 88.9 %"
  LTCR-1 describe the first repeat only. Over the three repeats LTCR-1 is 94.0 % against 94.3 %. To count the slips in
  every repeat, the production `GenderChecks` (`uk`, narrator male) was run on each repeat's stored outputs in the three
  first-person chapters: 16 slips on OFF against 8 on ON. Each slip is a feminine past verb after «я» in a paragraph
  where the narrating spirit acts. These are real slips, not false hits, and they are still in the final text: at
  Balanced the narrator inference stores the gender but re-renders nothing (that happens only at Max). By segment and
  repeat the slips do not line up with the reviewer. OFF has 7 segment-repeats with a slip where ON has none: the
  same two paragraphs in repeats 1 and 2, one paragraph in repeat 2, and one in repeats 2 and 3. ON has 5 where OFF has
  none: four paragraphs of repeat 2 (three of them short dialogue tags) and one paragraph of repeat 3. That is 7:5,
  p = 0.77. ON's lower total is a hint in the same direction as the 9 : 4 of arm 5c, not a result. The judge does not
  pick it up either: the paragraphs with a slip on either side come out 5:4 (OFF better : ON better), the rest tied
  or unread, so the judge does not grade narrator gender. `nameV` 2 against 3 is one name variant, first repeat only. Seen this way, the slips are
  noise in which side they fall on, but they are a real, recurring defect of e4b at Balanced, whatever the reviewer
  setting.
- *Decision rule.* The reviewer stays only on p < 0.05 for ON by the sign test on discordant paragraphs of the final
  text, or with at least 2 mini cases confirmed on `sequence`. Neither holds: the best p is 0.15 (pooled e4b mini), the
  one grader that moved is a single case, and `sequence` is even on the judge, on LTCR-1, on hard gates and on flags.
  So **the reviewer goes (OFF)**, and task 14.7 deletes it as planned. The reviewer costs 14-45 % more calls and 3-30 %
  more time (reported, not weighed). On the `sequence` first repeat it applied 18 edits and refused 36 (40 `REVIEW`
  calls and 2 directed fixes).
- *For the owner.* Narrator slips at Balanced are not fixed by either side. If they matter, the Max re-render, or a
  Balanced re-render of the slips the code finds, is the lever, not the reviewer.
- *Runs* (`.temporary_context/eval-history/20261108-*arm7a-{OFF,ON}*`; the script, the matrix logs and the judge files are
  in `.temporary_context/arm7a/`).

**Arm 7b: Max under the new dial (task 14.3).** New Max is Balanced plus Proofread's second round, which re-reads every
story paragraph; each rewrite is kept only through the never-worse gate. It is compared with new Balanced and with
today's Max from the `79ffede6` baseline, which still had the reviewer, the backward revision and the export pass.
Reviewer OFF on every new run (`BOOKLOOM_EVAL_VARIANTS=reviewer=OFF`, arm 7a). Ollama, single-flight. `sequence` on
`gemma4:e4b-mlx` with K = 3 (seeds 1000-1002), and `mini-en` and `mini-ru` on `gemma4:26b-mlx` once. New Balanced is
not run again: it reuses arm 7a's OFF runs, which have the same `src/main` (`f6c39eed`, reviewer OFF). The baseline's
Max is one stored run (K = 1, seed 1000), so its outputs were judged against each new repeat. The "58 meaning errors
per 50 paragraphs" in the task came from a blind hand grade of an owner book (`#15j-results`); it cannot be measured
again on `sequence`. So the comparison here is the judge's preference, not an error count. The eval harness now
stores the proofread verdicts of every `sequence` repeat (their outcome, their round and the text a kept rewrite
replaced), names each mini verdict's round, and logs the deferrals a mini run leaves open.

| `sequence`, e4b, per repeat | new Max | new Balanced (arm 7a OFF) | baseline Max (`79ffede6`, K = 1) |
|---|---|---|---|
| LTCR-1 (%) | 96.1, 96.1, 97.0 | 96.6, 95.7, 89.6 | 94.6 |
| First-round hard gates / flagged | 12, 14, 12 / 11, 11, 11 | 14, 12, 15 / 12, 11, 12 | 13 / 8 |
| Narrator slips (production `GenderChecks`, first-person chapters) | 7, 0, 6 (13) | 5, 9, 2 (16) | 0 |
| Calls (per segment) | 461, 466, 461 (1.41-1.42) | 302, 312, 306 (0.92-0.95) | 601 (1.83) |
| Seconds per segment | 7.05, 7.21, 7.15 | 5.37, 5.37, 5.11 | 6.58 |
| Proofread round 1: unchanged / rejected / kept | 12/12/0, 11/10/0, 13/13/1 | — | — |
| Proofread round 2: unchanged / rejected / kept | 122/87/5, 122/82/4, 109/85/6 | — | — |

- *Gate (design D25 row 14).* The judge (`gemma4:26b-mlx`, `bl-judge-1`, reasoning off, order swapped) compared each
  of the 15 round-2 rewrites that were kept with the text it replaced: **12 better, 0 worse, 3 tied** (p = 0.0005). I
  read them as well. Six put a Latin-letter name into Cyrillic (`Zerubin` → `Зерубін`). Seven fix the male narrator's
  feminine past forms (`була рада` → `був радий`). Two make a name's spelling match the rest of the book (`Ешдаун` →
  `Ашдаун`); those two are ties, and the third tie is one of the narrator fixes. Two rewrites are mixed. One also swaps `нескінченно` for the Latin phrase written in
  Cyrillic. The other adds `кишеньковий` and drops `на ланцюжку`. Neither is clearly worse. That is well above 3 better
  per worse. Open deferrals whose fact has arrived on `mini-ru`: **0** (0 open in all), on 26b once and on e4b K = 3
  at Max. **The gate holds, so `NeverWorse` and the round-2 prompt are not tightened.**
- *New Max against new Balanced* (every paragraph and repeat whose final text differs, same judge): 187 : 174 for Max,
  258 tied, 5 unread (p = 0.53); by repeat 59:62, 60:52, 68:60. A tie: round 2 kept only 4-6 rewrites per repeat, so
  most differences are the MLX runtime's own drift at a fixed seed (arm 3a).
- *New Max against the baseline's Max:* 228 : 265 for the baseline, 238 tied, 7 unread (p = 0.105); by repeat 76:98
  (repeat 0, same seed, p = 0.11), 79:83, 73:84. This is not significant. The three new repeats are also judged against
  one baseline draw, so they are not independent. The baseline's lead is a hint, not a result.
- *Narrator gender.* The baseline's Max has 0 slips, because its backward revision (`GenderRevision`) re-rendered them.
  New Max has 13 over three repeats, close to Balanced's 16. Round 2 fixed some of them (the seven kept rewrites
  above), but it left paragraphs 49-50, 71, 255 and 272. Its rewrites of those were refused or never asked. In the run's
  log the refusals read `gender-scope`, `new-block:glossary` and `no-gain`; over the whole run, `no-gain` caused 253 of
  the roughly 300 refusals. So the gate is strict rather than loose. But the Proofread stage does not yet do everything
  the deleted gender pass did. Task 14.4 deletes `GenderRevision`, and this loss is the owner's to weigh; it is not part
  of the D25 gate.
- *26b once, Max against Balanced* (Balanced is arm 7a's OFF run). `mini-en`: grader pass rate 0.948 on both sides,
  0 discordant; 81 against 30 calls; 282 against 166 s. Proofread read 31 segments: round 2 left 26 unchanged and
  refused 5 (`no-gain`); it kept none. The judge on the 6 blocks that differ: 3 : 1 for Max, 2 tied. `mini-ru`: 0.963
  on both sides, 0 discordant, no block differs; 50 against 25 calls; 133 against 104 s; round 2 left all 15 it read
  unchanged. e4b `mini-ru` at Max, K = 3: 0.963 in every repeat; all 15 left unchanged by round 2.
- *Time* (reported, not weighed): new Max takes 1.35 times new Balanced's time per segment on `sequence` (7.14 against
  5.28 s, the means), and 1.08 times the baseline's Max (6.58 s). It makes about 1.5 times Balanced's calls and about
  0.77 times the baseline Max's calls. Nothing flips: Max always re-reads the whole book.
- *Runs* (`.temporary_context/eval-history/2026110{8,9}-*arm7b-MAX*`; the scripts, the matrix logs, the judge items and
  verdicts, and the narrator-slip counts are in `.temporary_context/arm7b/`; the judge and the slip count were driven by
  throwaway tests that were not kept).

**Arm 7c: the proofread prompt (task 13.9).** Default P1: block 12 of a proofread call states the target language's own
instruction and rules (`proofreadInstruction`, `proofreadRules` in `uk.properties`; the English default for a target with
none). Alternative P2: the English instruction of `generic.properties` for every target, the target's rules kept, every other
block the same. The alternative reached the job through the test-only `PromptVariants.proofreadPrompt` field
(`BOOKLOOM_EVAL_VARIANTS=proofreadPrompt=P2`); the calls of every P2 run held `You proofread a translation` and none
`Ти коректор`, and the P1 runs the reverse. Max (round 1 on marks, then round 2 over every story paragraph, so every
paragraph is read), the `mini` suite (`mini-en` 33 blocks, `mini-ru` 17), Ollama, single-flight, paired by seed
(`eval-score.py diff --paired`); `gemma4:e4b-mlx` K = 3 (seeds 1000-1002), then `gemma4:26b-mlx` once (K = 1).

| Per repeat | P1 | P2 |
|---|---|---|
| e4b `mini-en` verdicts (UNCHANGED / REJECTED / KEPT) | 22/9/0, 20/11/0, 24/7/0 | 21/10/0, 20/11/0, 24/7/0 |
| e4b `mini-ru` verdicts | 15/0/0 ×3 | 15/0/0 ×3 |
| 26b `mini-en` verdicts | 25/6/0 | 24/7/0 |
| 26b `mini-ru` verdicts | 15/0/0 | 15/0/0 |
| e4b grader pass rate `mini-en` / `mini-ru` | 0.931, 0.931, 0.914 / 0.963 ×3 | 0.931 ×3 / 0.963 ×3 |
| 26b grader pass rate `mini-en` / `mini-ru` | 0.948 / 0.963 | 0.948 / 0.963 |
| `proofread-never-worse` (hard) | passed in every run | passed in every run |
| Calls, seconds (e4b `mini-en`, first repeat) | 91, 285 s | 92, 256 s |

- *Graders.* Every grader 0:0 discordant except e4b `mini-en` `every-usable` 1:0 for P2 (one segment flagged by its draft in
  P1's third repeat, before any proofread call; p = 1.0); noise band 0.0172 on `mini-en`, 0 on `mini-ru`; the screen rule
  fails (no 2 cases moved) and there is no real effect. 26b: 0 discordant on both books.
- *Judge.* The judge screen (`ProofreadJudgeEvalTest`, `gemma4:26b-mlx`, `bl-judge-1`, pairwise with the order swapped)
  compares the two sides' final text of every changed paragraph, a block whose rewrite either side kept, and each side's kept
  rewrites against the text they replaced. Neither prompt got a rewrite kept on either model, so there were 0 changed
  paragraphs and the judge was asked nothing. The draft text differs between the sides in 14-19 of 33 `mini-en` blocks per e4b
  repeat (9 on 26b) and in 0-1 of 17 `mini-ru` blocks; that is the MLX runtime's own nondeterminism at a fixed seed (arm 3a),
  not the proofread prompt, and it is not what the arm grades.
- *What the stage did.* `mini-ru` was read without a single proposed change by either prompt. On `mini-en` both prompts
  proposed rewrites in 6-11 paragraphs per repeat (4-9 of the block ids shared by the two sides in a repeat), and the never-worse gate
  refused every one, on 26b each with `no-gain` (no check got better); the e4b reports predate the stored reason.
- *Decision rule.* P2 only on a quality win (sign test p < 0.05 on discordant cases, or the screen rule confirmed on
  `sequence`) with no hard grader worse; a tie keeps P1. A tie on measured data, so **P1 stays**: the `proofreadPrompt` field
  and the P2 branch of `ProofreadInstruction` were deleted (the English default stays for targets with no instruction). Not
  measured: a book where the proofread call's rewrites pass the gate; on the mini books no rewrite was kept, so the arm says
  that the two prompts are not told apart there, not that they would edit alike where an edit is kept.
- *Since this arm.* Each mini repeat writes the Proofread stage's verdicts (`repeats[].proofread`: id, outcome, reason, text
  before and after), and a run whose stage read any segment reports the hard `proofread-never-worse` grader
  (`ProofreadChanges`: the failing expectations of the block counted on the verdict's earlier text and on the text the job
  left, before the consistency pass).
- *Runs* (`.temporary_context/eval-history/20261107-*arm7c-P{1,2}*`; scripts, logs and the judge files in
  `.temporary_context/arm7c/`).

**Arm 8: quants of e4b (task 9.9).** Translator `gemma4:e4b` on Ollama, Balanced, single-flight, K = 3 (seeds 1000-1002), commit
`6f6f819d` for all four, `mini-ru` (17 segments) and `mini-en` (33). Quants: NVFP4 `gemma4:e4b-mlx` (the default and the baseline of
every pair), MXFP8 `gemma4:e4b-mxfp8`, Q8_0 `gemma4:e4b-it-q8_0` and the QAT build `gemma4:e4b-it-qat`; the owner pulled the last three.
The run is `.temporary_context/arm8/run.sh` (`scripts/eval-matrix.sh --suite mini --repeats 3 --models ...`), paired with
`eval-score.py diff --paired`. The run did not use `--baseline-of`, so it is no baseline reproduction. No judge runner is left after
arm 2, so the changed outputs were read by the agent (all repeats); the judge `gemma4:26b-mlx` was not called.

- *`mini-ru`, graders.* All four quants: 318 of 321 grader results, the same single miss (`every-usable`, one flagged segment,
  `ch02.md:14`), 0 hard failures, 0 Russian letters, 0 stress marks. Against NVFP4 every grader is discordant 0:0 for MXFP8, Q8_0 and
  QAT (p = 1.00); noise band 0.
- *`mini-ru`, outputs read.* MXFP8 and Q8_0 differ from NVFP4 in 4 of 17 segments per repeat and are not worse: NVFP4 cut a sentence
  ("А я просто повз,") in 2 of 3 repeats, which both fuller quants completed ("повз проходив"). QAT differs in 4-6 of 17: its names
  pass chose the spelling "Мирена" where the others chose "Мірена", and it kept that spelling in all 9 segment-repeats that name
  her, consistent with its own glossary (a valid Ukrainian spelling, not a defect); it wrote "мимо" (a Russianism for "повз") in 3 of
  51 segment-repeats against 1 of 51 for NVFP4 and 0 for MXFP8 and Q8_0, which is inside the noise at this size.
- *`mini-en`, reported.* Grader results of 462: NVFP4 455, MXFP8 450, Q8_0 448, QAT 456; 0 hard failures and 0 flagged for all.
  `address-same` is lost in 2 repeats by MXFP8 and by Q8_0 (b:a 0:2, p = 0.50) and `brand-written-dravik` and
  `brand-written-quorrel` once by Q8_0, which trips the screen rule but not a real effect (no p < 0.05). 28-31 of 33 outputs
  differ in every pair, as expected for different weights.
- *Cost, reported and not weighed.* Run seconds as reported, `mini-ru` / `mini-en`: NVFP4 83.8 / 125.7, MXFP8 95.3 / 150.8,
  Q8_0 100.2 / 167.8, QAT 82.0 / 122.5 (translation calls: `mini-ru` 11 for all four; `mini-en` 14, 12, 13 and 12).
- *Decision rule.* The advice changes only if a quant is worse beyond noise on `mini-ru`. None is (no grader moved, the outputs read
  equal or better), so **the advice stays**: the NVFP4 cell of `quant-tiers.csv` keeps its `# deliberate:` line and no
  `QuantAdvisorTest` row changes. Kept for later: K = 3 on 17 short segments cannot see a small loss, and MXFP8 and Q8_0 cost
  14-33 % more time here.

**Task 3.4: LM Studio and llama-server error rows.** The fixtures under `modules/llm/src/test/resources/errors/lmstudio/`
and `errors/llamacpp/` are named `<name>.<status>.json|sse` and hold only the server's own sentences. None of them was
recorded from a running server in this task, so every status is, for now, taken from the research folder or chosen by
decision. `terminated.400.json` and `terminated.200.json` are `{"error":"terminated"}` by decision (the research
inferred the reply, it never reproduced it; the recording attempt of 2026-10-29 — three long chats with `lms unload` during the answer — got `400 {"error":"Model unloaded."}` every time and
never `terminated`, so the `terminated` status stays by decision; the 400 `Model unloaded.` reply is the observed one), once as a 400 and once as a 200 so the row is proven to read the words whatever the status.
`structured-unavailable.501.json` (the MLX engine's code wording) and `llamacpp/schema-not-applied.500.json` (the
engine's source wording) are constructed. The rest follow the bodies the research lists for LM Studio and llama-server.
`terminated` and `engine-unreachable` carry the code `upstream`; since task 3.5 their row's rule gets them one resend
after a state check (the provider's reachability probe), and a second failure goes to the run as an outage.
Two Ollama rows are still engine-blind until group 5 adds the engine mask to `ErrorRow`: `ollama-host-forbidden`
(an empty `403`) and `ollama-model-not-pulled` (`model … not found` in an error field) match those words from any
server, because task 3.3 proves them on both dialects, `/v1` included; they are not gated on `ProviderKind`. The
context row ignores deadline, timeout and cancel wording (Go's `context deadline exceeded` is an outage), and
`ollama-cloud-unreachable` needs cloud words, so a proxy's plain `502 Bad Gateway` page stays `upstream`/`unclassified`.

**Reference book `article-gravity` (task 1.11).** One invented science article (about 2,350 words of prose) in English, Ukrainian,
Russian and French, each as Markdown, EPUB and FB2 (`modules/pipeline/src/test/resources/eval/datasets/article-gravity/`, with its own
README). It holds what a translation must keep: code in four languages, a LaTeX block, two Mermaid diagrams, an ASCII diagram, tables
with code cells, raw HTML, GitHub alerts, a local and a remote image, DOI/ISBN/URL references, and four foreign-language quotations
(Latin, German, French, Ukrainian) that stay in their language with a gloss. The English was written by an Opus agent; the
Ukrainian, Russian and French were translated by Opus agents. Each translation was reviewed by three Codex models
(`gpt-6-luna` and `gpt-6.1-terra` at reasoning `none`, `gpt-6.1-sol` at `low`, the lowest it accepts) on invented text only, by hand. An editor applied a finding when two
reviewers agreed or it was clearly right and logged every decision (applied/rejected, who, why). Baseline files carry `gitDirty: true` (the tree had uncommitted test and doc work); `src/main` did not change between the measured commit and the group end. Result: Ukrainian 14 of 37 findings
applied, Russian 7 of 17, French 6 of 11 (plus one own fix). The English source was then reviewed once (Sol) and
corrected (two arithmetic fixes, 8.73 and 0.0275 %, wording, unverifiable DOIs/ISBN replaced by fictional ones) and the same edits were
carried into all three translations. Fixed decisions: HTML comments and the metadata footer stay English in every language, contents
anchors follow each language's headings, code and identifiers are untouched. Segment counts through the real importer: Markdown 152,
EPUB 169, FB2 207, identical in the four languages. The EPUB/FB2 files are built by `scripts/md-to-books.py` (standard library only,
byte-reproducible). Models that reject a reasoning level: `none` for Sol, `minimal` for Luna.

**Dataset version 2 (redesign 7.4).** `mini-en` and `mini-ru` gained narration inside existing blocks (`mini-en` m09, m12, m21,
m31; `mini-ru` ch01.md:3, ch01.md:7, ch02.md:10, ch02.md:12; ids, counts and cases unchanged) so a small book reaches the
code's evidence bars (`MiniBookGendersTest`). The baselines below were measured on version 1 and name its root hashes
(`d712187a…`, `f2f9fce5…`); `eval-score.py diff --paired` lists the changed root hash under what changed, so a comparison
with a version-2 run is not like for like until they are measured again.

**Baselines of `79ffede6` (task 1.10).** Recorded on 2026-10-28/29 with the `redesign-translation-pipeline` harness (v2) on Ollama, one run at a
time, seeds 1000.., and committed under `modules/pipeline/src/test/resources/eval/baseline/` (aggregates, per-case results, noise band,
provenance with `baselineOf` `79ffede6`, outputs of invented text). Aggregate = mean pass rate of the suite's graders (article: flagged rate,
lower is better). Time is the first repeat.

| File | Model | Dial | K | Aggregate | Noise band | Flagged | Calls | Seconds |
|---|---|---|---|---|---|---|---|---|
| mini-en-uk-gemma4_e4b-mlx-fast | e4b | Fast | 3 | 0.9123 | 0.0 | 0/33 | 12 | 81 |
| mini-en-uk-gemma4_e4b-mlx-balanced | e4b | Balanced | 3 | 0.9240 | 0.0175 | 0/33 | 25 | 133 |
| mini-en-uk-gemma4_e4b-mlx-max | e4b | Max | 3 | 0.8947 | 0.0 | 0/33 | 105 | 283 |
| mini-ru-uk-gemma4_e4b-mlx-fast | e4b | Fast | 3 | 0.8077 | 0.0 | 1/17 | 12 | 41 |
| mini-ru-uk-gemma4_e4b-mlx-balanced | e4b | Balanced | 3 | 0.8077 | 0.0 | 1/17 | 19 | 78 |
| mini-ru-uk-gemma4_e4b-mlx-max | e4b | Max | 3 | 0.8205 | 0.0385 | 0/17 | 60 | 116 |
| mini-en-uk-gemma4_26b-mlx-balanced | 26b | Balanced | 1 | 0.9298 | none | 0/33 | 27 | 178 |
| mini-ru-uk-gemma4_26b-mlx-balanced | 26b | Balanced | 1 | 0.7692 | none | 0/17 | 17 | 73 |
| sequence-en-uk-gemma4_e4b-mlx-balanced | e4b | Balanced | 3 | 0.9736 | 0.0030 | 9/328 | 118 | 1198 |
| sequence-en-uk-gemma4_e4b-mlx-max | e4b | Max | 1 | 0.9756 | none | 8/328 | 601 | 2157 |
| article-gravity-en-uk / -ru / -fr (e4b, Balanced) | e4b | Balanced | 1 | flagged 3.9 % / 5.3 % / 2.0 % | none | 6 / 8 / 3 of 152 | 53 / 56 / 54 | 312 / 294 / 285 |

All three article runs kept every protected span (score 1.0). Findings: (1) a noise band of 0.0 is real for most e4b cells (three seeds gave
the same pass rate), but it does not mean the replies are identical: in the check-2 rerun below 15 of 48 replies differed in text and the
pass rate did not move. The reviewer call keeps a fixed seed (15), so repeats differ only in the draft seeds, which also narrows the band. A band of 0.0 therefore means "the graders did not move", not "the model is deterministic". (2) Max is not better than
Balanced on `mini` (0.8947 against 0.9240 on en-uk) and costs 4 to 10 times the calls. (3) The `sequence` Balanced and Max aggregates
differ by 0.002, inside the band.

Reproduction checks of task 1.10: (1) `git diff 79ffede6` of `PromptRequestsEquivalenceTest`, `PromptRequestsBatchFormatEquivalenceTest`,
`DraftPromptGoldenTest` and `SelfHealPromptGoldenTest` prints nothing. (2) `mini-ru` at Balanced, K = 1, seed 1000, run again: the first
request of all 17 segments is byte-identical to the baseline's recorded request (later requests carry earlier replies, so they follow
the replies), and the pass rate is 0.8077, the baseline's.
