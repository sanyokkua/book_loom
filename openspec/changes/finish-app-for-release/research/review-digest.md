# Review digest — translation quality, autonomy, consistency and speed

Digest of the read-only review of 2026-11-12/13 (two rounds), written on 2026-11-14.
The review was pinned to commit `9bf17a04`. This digest was re-checked against `master` at `8d507357`, which has the
same content. The review folder is git-ignored, so this file repeats every number a requirement or task may cite.

Rules of this file:

- Numbers are copied from the review files. Where two review files disagree, both values are given and marked
  **(sources differ)**.
- "Review file" names in brackets are the files of the review folder: `SUMMARY`, `FINDINGS`, `WHAT-IS-GOOD`,
  `EVALS-VERDICT`, `09` (chat versus app), `09c` (whole traced run), `09d` (model sweep and prompt play),
  `12` (speed), `13` (request structure), `14` (autonomy and consistency), `15` (other projects and models),
  `16` (context and format replay).
- Code paths are relative to the repository root. A line number is given only where it was checked on the working
  tree on 2026-11-14. "Lines not re-checked" means the file exists and the line numbers are the review's.
- **Corrected** marks a place where the code check of 2026-11-14 disagrees with the review text. The corrected
  statement is the one to use.
- Short path prefixes used below:
  - `P/` = `modules/pipeline/src/main/java/ua/bookloom/pipeline/`
  - `U/` = `modules/ui/src/main/java/ua/bookloom/ui/`
  - `A/` = `modules/api/src/main/java/ua/bookloom/api/`
  - `L/` = `modules/llm/src/main/java/ua/bookloom/llm/`
  - `C/` = `modules/app/src/main/java/ua/bookloom/app/cli/`
  - `MSG` = `modules/ui/src/main/resources/ua/bookloom/ui/i18n/messages_en.properties`

## Method and limits

- **Hard sample ("the experiment").** 34 real paragraphs from three books (12 + 10 + 12; two English sources, one
  Russian, all into Ukrainian): 28 the app translated badly, 6 it translated well. One seed, no book context,
  window 16,384, output cap 3,000, temperature 0.2, thinking off (`09`, `09d`).
- **Blind sheets.** Candidates shuffled and unlabeled; two reader families (Claude sub-agents, Codex gpt-6.1-sol);
  score 1–5 (5 publishable, 2 needs rework); "bad" = mean of 2 or less; pairs compared with a 0.25 margin (`09`).
- **Noise band about 0.3.** A score compares only inside one sheet (the same 26b texts scored 2.75–3.34 on four
  sheets), so each sheet carries two anchors. Readers agree on ranks; the Claude reader scores 0.1–0.4 higher;
  r = 0.91 with the strong anchor, 0.65–0.80 among local arms (`09`, `09d`).
- **The traced run.** Five chapters of one English novel (385 body paragraphs, 399 segments), English → Ukrainian,
  `gemma4:e4b-mlx` on Ollama, Balanced, Unattended, batch 4, TRACE log: 353 calls, 2,018 s. It ran in the eval
  harness with in-memory storage, not in the window (`09c`, `12`).
- **The replay.** 14 real draft requests of the traced run (56 paragraphs, 47 read) on 26b and e4b, one thing
  changed at a time (`16`). **Whole-book counts** are regular-expression counts over three finished exports (`14`).
- **Limits.** Readers were models; no human reading. One seed. The traced run and the replay are one English slice
  on e4b. The click path was read from code, not walked. Time savings are estimates from one run (`SUMMARY`).

## F1 — The model sets the quality ceiling

**What happens.** The same model scores the same with the app's request and with a plain chat ask. A strong
outside model scores about 4.5 of 5 with the app's rules. So the app's fixed rules and reply protocol are not what
makes the text weak. The model is. Class: Likely (strong).

**Evidence.**

- gemma4:e4b: app request 2.2, bare ask 2.1 at the same temperature. gemma4:26b: 2.8 against 2.5 (the bare ask ran
  at default temperature). Strong outside model: 4.54 with the app's request and 4.54 with a bare ask
  (`FINDINGS` F1, `09`, `WHAT-IS-GOOD`).
- In whole real runs, unbiased rows are bad in 42 % on e4b and 4 % on 26b (`FINDINGS` F1).
- Traced run: 65 paragraphs read; 19 good, 20 acceptable, 26 bad (40 %, margin roughly ± 12 points). 23 of the 26
  bad ones were the model's first draft in a correct context. None came from batching, a cut reply or code
  (`09c` §7).
- gemma4:26b is first on every sheet. The app's request is equal to or better than a lean prompt for eleven of
  twelve models (`FINDINGS` F1). The model count is given as "thirteen local models" in `SUMMARY`, "twelve" in
  `FINDINGS` F1 and "ten" in the first sweep table of `09d` (two 27b-class models and one translation-only model
  were added later) **(sources differ in what they count)**.
- Rewording one call does not help on 26b: lean prompt 2.49, Ukrainian instructions 2.46, two examples 2.53,
  against 2.75 for the app's request (`09d` §2).
- Full model table: see [Model table](#model-table).

**Code today.**

- The recommendation by memory: `U/state/ModelRecommendation.java` lines 19–35 — under 24 GB `gemma4:e4b`, from
  24 GB `gemma4:26b-a4b`, from 36 GB also offers `gemma4:31b`. The language pair is not an input.
- The default batch size of a model with no profile row is 2: `A/llm/ModelProfile.java` line 42.

**Fix options the review recorded.**

- (a) Recommend and default to the largest model the computer can hold, in plain words in the UI.
- (b) For Russian → Ukrainian at 12b size recommend MamayLM over plain Gemma; do not offer qwen3.5, qwen3.8, gpt-oss
  or Ministral for Ukrainian; build no special request format for TranslateGemma.
- (c) Spend the Proofread time on a larger draft model.
- See [Owner decisions](#owner-decisions): gemma4:26b stays the recommended model; no special model support.

**How to prove a fix.** The blind panel of F7, per model, with a human reader on a subsample.

## F2 — Almost nothing improves a poor first draft

**What happens.** A draft that is valid but poor reaches the book unchanged. The checks see only a small part of
what a reader grades wrong. Proofread and Repair spend time and change almost nothing; part of what they change
gets worse. Class: Confirmed.

**Evidence.**

- Share of paragraphs exported as the first draft: 98.7 % (`SUMMARY`, `09c` closing table); 97–99 %
  (`FINDINGS` F2).
- The checks catch 2–5 % of what a reader grades wrong; recall 0.13, for wrong meaning 0.06
  (`FINDINGS` F2, `EVALS-VERDICT`).
- Traced run, Proofread: 67 calls, 381 s (`09c`) or 382 s (`12`) **(sources differ by rounding)**. 113 segments
  marked, 129 items sent in 67 windows, 64 windows read (122 items): unchanged 59, rejected 60, kept 3 (`09c` §1).
  Of the 3 kept changes 1 was better and 2 were worse. Both worse ones were ordered by the app's own false notes
  (`09c` §6).
- Why the 129 items were sent: 59 only because code had normalised an apostrophe or an ellipsis; 48 "recurring term
  off its rendering" from the common-word list; 17 "name form missing" (mr/mrs); 4 "gender now known"; 4
  terminal-mark; 1 flagged (`09c` §6).
- The model never answered "unchanged": 0 of 67 (`12` P1), 0 of 64 (`13` S3). Items written back identical: 58
  (`09c`, `13` S8); 55 (`12` P1) **(sources differ)**.
- Rejections: 60 — no-gain 53, placeholder 3, new quote imbalance 2, quotes 1, gender-scope 1. Of 5 rejections
  compared by hand, 3 refused a real fix (a meaning error, a Russian word, a female narrator) (`09c` §6).
- Repair: 8 attempts, 2 kept (both mixed-alphabet words), 6 rejected. 5 of the 8 answered a defect that does not
  exist: 4 × a four-dot ending read as a full stop, 1 × a name seen inside a plant name. In 5 of 6 rejected
  repairs the model returned the draft word for word (`09c` §6).
- Cost: about a quarter of the run time for about 1 % of the text (`FINDINGS` F2).
- What a second pass can give on gemma4:26b: see [Merge results on gemma4:26b](#merge-results-on-gemma426b). On
  MamayLM, Lapa and e4b every second pass is level or worse; the same merge gives qwen3.8:27b +0.10 and
  TranslateGemma 27b +0.04 (`09d` §4, §6).

**Code today.**

- Proofread stage: `P/proofread/ProofreadStage.java`. At Fast it makes no model call (comments at lines 38 and 181).
- The "no-gain" veto and the false notes (terminal mark on a four-dot ending, a name inside a common noun) were not
  located in code for this digest. The review does not name their classes. **Not verified.**
- The default dial is Balanced: `A/settings/AppSetting.java` line 52 and `A/project/BookBrief.java` line 180.

**Fix options the review recorded.**

- (a) Drop Proofread reading on small models (Fast as the honest default).
- (b) On a 26b-class model, try "several drafts, then merge" as the second pass, accepted only if it repeats on a
  fresh sample. See [Owner decisions](#owner-decisions) for the limits on this option.
- (c) Keep the checks that work (mixed alphabets, source-language leftovers, placeholders); remove the false ones.
- (d) Do not send items to Proofread for typography normalisation alone.
- (e) "No-gain" must not veto a change the checks cannot score.

**How to prove a fix.** Blind panel, draft against draft + pass, better / same / worse, on fresh paragraphs. For
(c) and (d): the count of Proofread items and Repair calls triggered by a false or typography-only reason → 0.

## F3 — The output cap cuts replies, and a cut single draft is not retried

**What happens.** A reply that reaches the output limit is cut. A single-paragraph draft cut this way is flagged at
once, with no retry. A Proofread window cut this way is lost whole. Class: Confirmed.

**Evidence.**

- Share of calls cut: 16 of 1,166 e4b drafts; 4.7 % of proofread windows (`FINDINGS` F3).
- Traced run: 6 of 353 replies cut — 3 draft batches and 3 Proofread windows. The 3 drafts were runaway replies
  (F4), not long translations. Their last items were drafted again alone; no text was lost. The 3 Proofread
  windows were lost whole; their caps were 273, 210 and 132 tokens (`09c` §1). Cost of the 3 lost windows: 16.9 s
  (`12` P7).
- Most cut drafts are batches whose lost item is drafted again alone, so the share of paragraphs harmed is well
  under 1 % (`FINDINGS` F3).

**Code today.**

- The cap of every call is clamped in `P/prompt/ChatRequests.java`, `build`, lines 96–99.
- A single draft whose finish reason is not STOP goes to `DraftOutcomes.unfinished`
  (`P/DraftReplies.java` lines 87–90), which flags it at once (`P/DraftOutcomes.java` lines 130–137).
- **Corrected.** The review says the Proofread cap "is sized for changed items". On master the Proofread output
  limit is `OutputLimit.forBatch` over the sources of **all items read** (`P/proofread/ProofreadCall.java`
  lines 175–178; `P/prompt/OutputLimit.java` `forBatch`, lines 97–115: each item's allowance plus 16 envelope
  tokens). Why three windows were still cut at 132–273 tokens was not traced for this digest; the clamp by the
  plan's `maxOutputTokens` in `ChatRequests.build` is one place to look. **Not verified.**

**Fix options the review recorded.**

- (a) On a `length` finish retry once with a larger cap and a new seed.
- (b) Size the Proofread cap for all items of the window (already so in code, see the correction), or ask for
  changed items only and enforce it in the schema. See also F13 (replies as edits).

**How to prove a fix.** Count of `length` finishes that end as a flag or a lost window → 0 in a whole run.

## F3b — A paragraph with no accepted target is exported in the source language

**What happens.** When a paragraph has no accepted target and no usable refused reply, export writes the source
text. The reader then finds a paragraph in the other language inside the book. Class: Confirmed (one case read;
the whole-book size is in F12).

**Evidence.**

- One segment of the Russian novel is exported entirely in Russian. That run had no cut drafts, so this is its own
  path, not F3 (`FINDINGS` F3b).

**Code today.**

- Export fallbacks: `P/export/ExportJobImpl.java` (uses `Fallbacks`; lines not re-checked).
- **Corrected.** The review's option "mark it in the report" is already built for the time after export:
  `U/state/ExportReportLines.java` has `sourceFallbacks` (lines 105–108, reason `BROKEN_FORMATTING`) and
  `noTargetFallbacks` (lines 118–120, reason `NO_TARGET`), and `U/dialog/ExportCompleteDialog.java` shows both
  lines and the "written in the source language" count (lines 159–169; message `export.complete.pending`,
  `MSG` line 1152). What is missing is any mention **before** export: the run state, the title and the finished
  banner do not say it (see F12).

**Fix options the review recorded.**

- One last single-paragraph draft before export.
- Never write source text into the book silently.

**How to prove a fix.** Count of source-language paragraphs in the export of a finished run → 0, or each one named
to the person before export.

## F4 — Replies run away, and the cause is known

**What happens.** With the enforced JSON reply, some replies never end. When the last item of a batch ends in a
closing quote mark after `?` or `!`, the model writes the closing brackets without the closing quote. The grammar
takes the brackets as text and blocks the end of the reply. White space follows up to the cap. The last item of
the batch is lost and drafted again alone. Class: Confirmed.

**Evidence.**

- Round 1, hard sample, one paragraph per call: about one reply in 34 runs to the limit on gemma4:e4b, gemma4:12b,
  qwen3.5:9b, Lapa and MamayLM; none on 26b. With a batch of 4 on e4b, 4 of 34 paragraphs were lost this way and 1
  item was omitted (`FINDINGS` F4, `09d` §1).
- Traced run: all 3 runaway drafts have the same token sequence. Rate: 3 of 44 replies whose last item ends with a
  separate closing-quote token (7 %); 0 of 97 others. After the missing quote the model wrote 72, 57 and 79
  white-space tokens to the cap (`13` S2). Time: 8.8 + 9.3 + 10.1 = 28.1 s (`12` P7).
- Replay on e4b of all real requests whose last item ends in a quote mark: 66 requests × 3 seeds. `13` counts 44
  such replies in the run, `16` replays 66 such requests **(sources differ; the counting rule differs)**. Results
  are in [Reply format and runaway replay](#reply-format-and-runaway-replay).
- The same runaway was seen once on 26b: 1 in 56 calls, in the `core` arm (`16`). Round 1 had seen none on 26b.
- Refused batch items drafted again alone: 20 in the traced run (reasons: TOKENS 10, QUOTES 4, MISSING 2,
  TOO_SHORT 2, SENTENCES 1, LEAKED 1). The single re-draft was better in about 6, the same in about 9 and worse
  in 5 (`09c` §1, §4). 14 of the 20 were refused for a lost formatting mark or an unclosed quote. Cost 61 s
  (`12` P6).
- Formatting marks: 74 of 405 items hold marks; the mark sequence is wrong in 13 replies of marked items (about
  15 %), all emphasis pairs (`13` S4).

**Code today.**

- Batch salvage of complete entries: `P/batch/BatchEntries.java`, `salvaged`, lines 73–87.
- The entry finder needs a closing quote followed by `,` or `}`: `P/batch/RawEntries.java`, pattern `CLOSE` at
  line 25, used in `find` (loop from line 41). A quote followed only by white space is not an entry, so the
  runaway's last item is dropped.
- Single-draft path: a cut reply is flagged at once (see F3).
- The Ollama request sends no stop sequence (`L/dto/OllamaChatRequest.java`; lines not re-checked) (`13` S9).

**Fix options the review recorded.**

- (a) Recover it in the parser: an entry that ends in a closing quote mark followed only by white space is
  complete. Recovers 3 of 3 with no call (`13` S2).
- (b) Stop the reply on a run of white space (a stop sequence, or a guard in the stream reader).
- (c) Move translate replies to numbered or tagged lines (F15).
- (d) When a batch item is refused only for a formatting mark, compare the single re-draft with it and keep the
  better; or restore a lost mark or closing quote by code (est. −40 s, `12` P6).
- Sampler penalties are not the fix: the cause is the grammar, not repetition.

**How to prove a fix.** Review experiment E4: the quote-ending requests of the traced run, several seeds, arms
"today" and "parser fix". Win: 0 lost items, 0 wasted tail tokens. Plus the runaway count and fallback outcomes of
a whole run.

## F5 — Wrong facts from Prepare go into every later call

**What happens.** Name rows are followed almost always. So a right row gives a consistent name, and a wrong row
gives a consistent mistake. Several code rules in Prepare make wrong rows: they refuse a translated name, swap a
shared word, set a gender from a pronoun count, and fill the recurring-term list with common words. Class:
Confirmed.

**Evidence (traced run, `09c`, unless stated).**

- What works: name rows are injected in 100 % of the draft calls that contain the name. The export follows them
  with 0 stem variants for the 12 main names. Every name used 3 times or more has a row.
- Name refusal: the model's translated renderings of a place name and of a speaking nickname were refused with "it
  does not begin the way the name sounds". A shared-word rule then built «Вежа оф Лондон». The refused nickname
  became «Ігл-бік». These rows reach 28 of 385 exported paragraphs (7 + 10 + 14 = 31, 28 distinct), about 7 %
  (`09c` §3, §8).
- Gender by pronoun count: a man was marked female. The model voted male 0, female 0; the code's tally was
  female 3, male 1, because "she" in the windows is a woman standing beside him. The wrong gender went into 8
  draft and 5 Proofread requests; the model ignored it in all 14 paragraphs; it caused 2 false "name-gender"
  notices. Earlier runs made two common nouns ("Government", "Son") male characters (`09c` §3, `FINDINGS` F5).
- Narrator: the code logged `person=MIXED firstPersonShare=0.257` and stored FIRST. Gender came out "MALE kept as
  likely" with 0 of 4 passages agreeing; it flipped between two runs of one book. 4 exported paragraphs have a
  female narrator (`09c` §2, `FINDINGS` F5).
- Recurring terms: 37 terms in the list, about 7 clean (`09c` §3); "about 30 of 37 rows are common words" (`09c`
  closing table); "about 20 of 34 sent rows" (`FINDINGS` F5); 34 distinct rows sent (`13` S5) **(sources differ in
  what they count)**. The rows go into 133 of 141 draft calls. Two rows were overwritten mid-run with wrong values
  by the co-occurrence learner; one of them led to 1 paragraph made worse by Proofread (`09c` §4, §8).
- Locked title rows (`mr`, `mrs`) were never sent: 0 of 14 calls (`09c`, `14`); 0 of 13 (`FINDINGS` F5)
  **(sources differ)**. Result: містер/місіс in 21 paragraphs against пан/пані in 5. Proofread then asked 14 times
  for пан/пані and its own veto refused all 14.
- One thing in two or three rows (object / place / "object of place"; first name / surname). In the whole-book
  glossaries 127–128 rows describe roughly 85 things (`14` B4).
- The name-translation request works when its input rows are right (`WHAT-IS-GOOD`, `09b`).

**Code today.**

- Name refusal: `P/glossary/SuggestionReplies.java`, enum `Standing`, constant `LETTERS` with the message "it
  does not begin the way the name sounds" at line 153.
- Shared-word swap: `P/glossary/ClusterAgreement.java`, lines 139–145 (`sharedWordsOf`, then `retargeted`).
- **Corrected.** The review's anchor `NarratorDetector.java:371` is not about gender. Lines 367–372 of
  `P/narrator/NarratorDetector.java` are `personOf`, which returns FIRST, THIRD or MIXED (narrative person).
- **Corrected.** Gender from a pronoun count is `P/glossary/PronounGender.java` and gender from name endings is
  `P/glossary/DeclensionGender.java`. Both are called from `P/run/PrepStage.java`, `scanIfEmpty`
  (`PronounGender.seedHeld` line 139, `PronounGender.seeded` line 146, `DeclensionGender.seeded` line 147). The
  names pass also uses the tally: `P/glossary/TermReviewReplies.java`, `favouredBy`, lines 234–243 (three or more
  pronouns making 60 % or more), and `P/glossary/PreScan.java` line 223. The traced case came from the names pass
  of the test harness.
- Locked rows are listed only for a term masked into a token: `P/context/WindowMap.java`, `lockedLines`,
  lines 197–211 (the trace line "its token is not in the window" is at line 209).
- Recurring terms: `P/glossary/KeyTermScan.java`, `P/glossary/LexiconReading.java`,
  `P/lexicon/CooccurrenceLearner.java`, `P/glossary/CommonWords.java` (lines not re-checked).
- Name scan threshold of 3 uses: `P/glossary/FrequencyScan.java`, `PROPOSAL_MIN_COUNT = 3`, line 52.

**Fix options the review recorded.**

- (a) Allow an "unknown" narrator gender; decide from first-person past-tense forms where the source marks them,
  else ask once.
- (b) No gender from a pronoun tally alone.
- (c) Drop the "begins the way the name sounds" refusal for names that have a meaning, and the shared-word swap.
- (d) Translate names with the largest available model (one call per book) and show the list for a short check.
- (e) Drop the recurring-term list or keep only words that are not common vocabulary; never overwrite a row
  mid-run.
- (f) Send locked title rows by source word, not by mask token.
- (g) Merge case forms and possessives before the model sees them.

**How to prove a fix.** A Prepare gold for two real books: names, types, genders, targets, narrator (F7). Plus,
on the traced slice: paragraphs carrying a wrong Prepare row → 0 of 385 (was 28), and locked title rows sent in
every call that holds the word (was 0 of 14).

## F7 — The evals cannot see translation quality

**What happens.** The eval suites score protocol and consistency. None scores whether the text is a good
translation. They also give the pipeline an easier context than a real book does. So the numbers improved while
the books did not. Class: Confirmed.

**Evidence (`EVALS-VERDICT`).**

- No reference translations and no adequacy score other than a local judge of the same model family. That judge
  failed calibration (true-positive lower bound 0.51).
- The recall suite is honest: the shipped checks see 0.13 of hand-labelled defects (wrong meaning 0.06).
- The mini and sequence suites run with `BookBrief.defaults` plus the dataset's narrator; the model-predicted brief
  is never in an arm number.
- About nine of fourteen arm decisions were ties or nulls at n = 17–60. Two repeats of the same pipeline differ in
  about two thirds of paragraphs.
- A consistently wrong term scores 100 % on the consistency metric.
- A reporting error in the committed closing document: new mini aggregates given as 0.9240 / 0.8077 "unchanged";
  the stored reports say 0.9310 (en) and 0.9630 (ru).
- `scripts/eval-models.txt`: 7 of 13 lines name models that are not installed; 6 installed models are missing.
- Evidence runs were logged at DEBUG, so no bad paragraph can be traced to its first draft.

**Code today.**

- Eval harness: `modules/pipeline/src/test/java/ua/bookloom/pipeline/eval/` (for example `BookRunner.java`).
- The review's blind-sheet scripts live in the git-ignored review folder (`experiments/chat_vs_app/`: `sheets.py`,
  `READER.md`, `codex_reader.py`); they are not in the repository.

**Fix options the review recorded.**

- Add a fixed real-book panel of about 100 paragraphs per pair, read blind by two reader families, with a
  human-read subsample; judge every pipeline change on it by paired better / same / worse.
- Add a permanent plain-ask control arm (bare ask, same model) in the Java harness.
- Add a Prepare gold for two real books and score the names pass against it.
- Use the whole-book rendering report of F14 as the names metric (`SUMMARY` item 7).
- Fix: run mini and sequence with the model-predicted brief; re-record the baseline; correct the closing figures;
  keep TRACE logs for every evidence run; refresh `eval-models.txt`.
- Keep: production-built requests, the statistics script, the fast probe loop, the recall suite.

**How to prove a fix.** The panel separates a known-better arm from a known-worse one outside the 0.3 noise band
(for example the strong anchor against e4b), and two readers agree on ranks.

## F8 — Context blocks other than name rows add nothing measurable

**What happens.** Real requests were replayed with context blocks removed. A reader sees no difference in the
paragraphs. Name rows are the one block with a proven effect: they make the model use the given rendering. Chapter
summaries, the "recently present" line, common-word term rows and the dialogue reminder cost time and show no
effect. Class: Likely (measured in round 2).

**Evidence.**

- Full context against none: +0.15 on 26b, +0.04 on e4b; noise band 0.3. Name rows only, or name rows + previous
  pairs + look-ahead, score the same as the full request (`16`). Table: [Context-block replay](#context-block-replay).
- With name rows the model uses the given rendering in 60–62 of 62 cases; without them in 50–52 (`16`).
- Traced run: name rows followed in 337 of 341 item uses (99 %). 62 % of the rows sent name something in an item;
  28 % only in a previous pair; 10 % only in the next item (so 38 % could go) (`13` S5).
- Term rows: common words; the rendering appears in 396 of 513 uses (77 %), and the 117 misses are wrong-sense rows
  rightly ignored (`13` S5).
- Chapter summaries: reply wording overlaps the summary block 7.2 % against 7.0 % for a random other reply. The
  block is 11.6 % of draft prompt tokens and is in 121 of 141 draft calls (`13` S5, `09c` §5).
- "Recently present": 2 of 284 names appear in the reply (`13` S5).
- Dialogue reminder: in 117 draft calls, relevant to 0 of 405 items (no item has a dash-line source)
  (`13` S5, `09c` §4). `FINDINGS` F6 gives "124 of 149 requests" (drafts plus repairs) **(sources differ in what
  they count)**. All 4 unbalanced-quote items of the run are in calls with it (4 of 339 against 0 of 55); the
  strong model wrote the hybrid «— …» in 3 of 34 paragraphs with the app's request and 0 of 34 with a bare ask
  (`09c` §4, `FINDINGS` F6).
- Previous pairs: the address form (ти / ви) equals the form in the shown pairs in 54 of 69 cases (78 %); chance
  is 73 % (`13` S5).
- Summary quality: 27 calls, 137 s; the gate kept 36 of 81 sentences, re-asked 26, dropped 19; it refuses ordinary
  words, and what is stored is partly wrong (`09c` §5).
- Cost: full request 243 s against 185 s for names only on 26b (14 requests); 155 s against 139 s on e4b; prompt
  reading 54 s against 19 s on 26b. Removing the blocks saves about 10 % of draft time on e4b and up to 24 % on
  26b, plus the summary calls (6.8 % of the run) and the common-word term calls (10.1 %) (`16`, `12` P3, P4).

**Code today.**

- Chapter summaries are on unless the story shape is "separate stories": `A/project/BookBrief.java`,
  `summariesOn`, lines 338–340. The call: `P/memory/ChapterSummaryCall.java` (lines not re-checked).
- Blocks: `P/context/EarlierChapters.java`, `P/context/RecentlyPresentCard.java` (at most 5 names, 12 paragraphs
  back: lines 38 and 41), `P/context/WindowMap.java` (name and term rows), `P/context/WindowAssembler.java`
  (previous pairs, next item), `P/context/Reminder.java` (the dialogue reminder is added at line 47).

**Fix options the review recorded.**

- (a) Keep name rows, sent only for names that occur in the items.
- (b) Remove chapter summaries, the "recently present" line, common-word term rows and the dialogue reminder from
  the translate request.
- (c) Keep one previous pair and the look-ahead paragraph as a cheap continuity aid, or drop them too. This is a
  judgment call, not a measured need.

**How to prove a fix.** The same replay on a second book and on a context built by 26b. Win for removal (review
experiment E1): panel score within 0.15 of the full request and no rise in name-form variants, narrator-gender
slips, ти/ви switches or fallbacks.

## F11 — The window's short path skips the model's brief and names work

**What happens.** A run started from the window prepares with code scans only. The model's brief suggestion and
the model names pass run only when the person presses their buttons on two screens. On the shortest path names
have no target, and the first spelling the model writes becomes a hint only. This path has never been measured.
Class: Confirmed (code).

**Evidence.**

- Shortest path today: 1 drop + 7 clicks + 1 forced decision (the target language), plus dialogs at Start
  (`14` A1, `SUMMARY`).
- **Corrected.** The review says "up to 2 dialogs" at Start. Start can show **three** cards in a row: names the
  book plays on (meaning names), who tells the story (narrator), and characters without a gender.
- **Corrected.** The review says every measured run "used the command line or the eval harness, which run both"
  the brief suggestion and the names pass. On master the command line does **not** run the brief suggestion. It
  runs names work only with `--review-names`, and that is scan → model review → accept every suggested target,
  not the full names pass. The measured runs that had the brief suggestion and the full names pass came from the
  test harness `BookRunner`. (The review's own evidence: `book-run.json` with `"suggested": true` and 223 names
  calls is harness output; the Russian run used `--review-names`, `14` A1.)
- Traced run, Prepare in the harness: brief 6 calls, 48 s; names 104 calls, 6 min 20 s (`09c` §1). Whole book:
  about 110–230 model calls for the two steps (`14` A1).
- No run exists of the path a first-time window user takes. The consistency figures of F14 are therefore the best
  case, not the default case (`14` A1).
- A name found late: when the glossary is not empty, new names are found at the end of each body unit, after
  their chapter is translated, with no target, no type reading and no gender from the model (`14` B3).

**Code today.**

- The window run prepares with code scans only: `P/run/PrepStage.java`, class Javadoc lines 30–35 ("the model scan
  runs only when the person asks for it on Names & style, never on its own") and `scanIfEmpty`, lines 128–150.
- Brief suggestion from its button: `U/state/StyleSuggestion.java`, lines 167–171 (`suggestBrief`).
- The autonomous names pass from its button: `U/state/GlossaryModelRuns.java`, `autoNames`, line 201.
- Command line: `C/TranslateCommand.java`, lines 166–168 (names only with `reviewNames`); `C/NameReview.java`,
  `review`, lines 59–78 (`glossary.scan`, `glossary.review`, `glossary.acceptSuggested`). No brief suggestion call
  exists in `C/`.
- Test harness: `modules/pipeline/src/test/java/ua/bookloom/pipeline/eval/BookRunner.java`, line 101
  (`acceptBrief`), line 104 (`WholeBookStages.namesPass`), lines 137–138 (`suggestBrief`).
- The three Start cards: `U/dialog/NarratorDialog.java`, `startAfterAsking`, lines 70–74 (meaning names, then the
  narrator question, then character genders); `U/dialog/MeaningNameDialog.java`,
  `U/dialog/CharacterGendersDialog.java`, `U/state/NarratorHints.java`, `U/state/UnknownGenders.java` (lines not
  re-checked).
- The target language is empty in a new brief: `A/project/BookBrief.java`, `defaults`, lines 166–181 (second
  constructor argument `null`). The message "Required: choose the language to translate into." is `MSG` line 697.
- Late names: `P/run/DecisionFollowUp.java`, `scanNames`, lines 102–126. The first-spelling learner writes a
  suggested target only for a one-word character or place: `P/run/TermLearning.java`, `learnSpellings` from
  line 198, `lacksSpelling` lines 235–242.

**Fix options the review recorded.**

- (a) Make the brief suggestion and the names pass part of the default run chain, with Names & style as an
  optional stop. Smaller variant: at Start with an empty or target-less glossary, run the names pass first.
- (b) Fill the target language from the OS language or the last project.
- (c) Replace the Start dialogs with "not stated" defaults (F5), or fold them into one card with "start anyway" as
  the default button.
- (d) One button "Translate" on Import that runs the whole chain with defaults.
- (e) At each unit end, when the scan added names, one target-suggestion call for the new rows (`14` B3).

**How to prove a fix.** Walk the window path once on a real book, with the two steps on and off, and read the
result. Count clicks and forced decisions on the shortest path.

## F12 — "Finished" is shown for a book with untranslated paragraphs

**What happens.** A completed run can leave whole paragraphs with no accepted target. Export writes them in the
source language. The run still says "Finished", and the banner speaks only of segments that "need your review".
The person learns about source-language paragraphs only after the file is written. Class: Confirmed.

**Evidence (`14` A6).**

- English novel on 26b: run COMPLETED; "Pending, written in the source language: 24" (0.6 % of 3,784) and
  "Flagged, written with the machine translation: 20".
- Same book on e4b: 5 and 24. Russian novel on e4b: 1 and 32.
- The blocking check on most of them is the sentence-count check.
- Size of a fix: 5–24 extra calls per novel.

**Code today.**

- Title "Finished": `MSG` line 1503 (`run.title.finished`).
- Banner "Translation finished. N segments need your review. You can export now and review later.": `MSG`
  line 1550 (`run.banner.finishedReview`). Toast with the same count: `MSG` line 250.
- **Corrected.** After export the fallbacks are listed (see F3b): `U/state/ExportReportLines.java` and
  `U/dialog/ExportCompleteDialog.java`, message `export.complete.pending` at `MSG` line 1152. The run state, the
  title and the banner do not mention them.

**Fix options the review recorded.**

- (a) Before the run ends, one plain single-paragraph draft for every paragraph without an accepted target,
  accepted on the hard gates only.
- (b) If that fails, write the best machine draft, marked, never the source.
- (c) Word the banner by what will be exported (for example the count of paragraphs still in the source language).

**How to prove a fix.** On a whole real book: source-language paragraphs in the export of a run that says
"Finished" → 0; or the count is shown before export and equals the export report's count.

## F13 — About 40 % of the run time buys nothing in the exported book

**What happens.** Almost all time is spent inside the model, and most of that is writing replies. About 810 s of
the 2,018 s of the traced run went to calls that changed nothing in the book. Class: Confirmed (traced run).

**Evidence.**

- See [Time breakdown of the traced run](#time-breakdown-of-the-traced-run) for all figures.
- Writing replies 77 %, reading prompts 22.5 %, outside the model 0.8 % (`12`). `13` A1 rounds the first two to
  about 78 % and 22 % **(sources differ by rounding)**.
- One output token costs as much time as about 19 uncached prompt tokens (`12`); 18 (`13` A1) **(sources
  differ)**.
- Not levers: batch size (2.59 / 2.47 / 2.53 / 2.53 s per item at 1 / 2 / 3 / 4 items); prompt order (system
  message and earlier-chapters block already cached, about 35 s reachable); model reloads (none) (`12` P8, P9).
- Whole-book cross-check (e4b, 2,274 calls, 4.26 h): drafts 59.3 %, Proofread 24.6 %, term reading 7.1 %,
  summaries 4.6 %, repair 3.1 % (`12`).
- Generation ran at 35 tokens/s through Translate and 55–60 before and after; cause unknown (`12` P2).
- Expected saving: about a third less time on a whole book (estimate) (`FINDINGS` F13, `12`).

**Code today.**

- Proofread: `P/proofread/ProofreadStage.java`, `P/proofread/ProofreadCall.java`. The Proofread schema makes
  neither `unchanged` nor `items` required: `P/prompt/ProofreadSchema.java`, lines 20–25.
- Common-word term calls: `P/glossary/LexiconReading.java`, `P/glossary/KeyTermScan.java`,
  `P/glossary/NamesPlan.java` (call budgets) (lines not re-checked).
- Chapter summaries and their re-asks: `P/memory/ChapterSummaryCall.java` (lines not re-checked).
- The LM Studio path does not stream, as the review reads `L/client/openai/OpenAiRequestMapper.java` (the request
  is built at lines 105–108 with the literal `false` as its third argument). That the argument is the stream flag
  was not re-checked.
- **Corrected (found during this check).** The review says "the app shows no tokens-per-second figure". On master
  a figure exists: `U/state/ThroughputMeter.java` (rate over the last 20 draft calls) and the title-bar message
  `shell.run.rate` = "{0} tok/s" (`MSG` lines 201–202); the connection tip also shows drafting and judging rates
  (`MSG` line 1324). What the review's option adds is a warning when the rate halves, and the rate in the
  once-a-minute run summary line. Whether the run summary already holds it was not checked.

**Fix options the review recorded.**

- (a) Proofread reads only windows with a mark a reader would see (no typography-only items): −96 s, no risk.
- (b) No model call for common-word terms: −204 s here, and −146 s of Proofread.
- (c) Summaries off (F8): −137 s.
- (d) Proofread replies as edits ("find → replace" or "unchanged"), not whole paragraphs; an edit is applied only
  when `find` occurs exactly once (`13` S8).
- (e) Fewer output tokens per item (F15); short ids in Proofread replies: est. −22 s.
- (f) Warn when tokens per second halves.
- (g) The F4 recovery.

**How to prove a fix.** Re-run the traced slice with a TRACE log on an idle, cool machine. Compare model seconds
by call kind with the table below, and the exported text paragraph by paragraph (no loss). Review experiment E5 for
(d): the 129 Proofread items; win = 0 cut windows, at least 5 times fewer output tokens, at least as many correct
changes.

## F14 — Consistency is followed but not enforced or checked over the whole book

**What happens.** Where a row has a right target the model keeps it. What goes wrong is everything without a
right row: empty or wrong rows, recurring words with no row, the dialogue style, ти/ви for English sources, the
narrator's gender by chapter. Nothing enforces a suggested name, and no whole-book check by code fixes anything.
Class: Confirmed.

**Evidence.** Full tables: [Consistency numbers](#consistency-numbers).

- Rows with a right target: the stem is kept in 98–100 % of paragraphs, on both model sizes and both pairs, with
  no enforcement. Russian → Ukrainian keeps the source's ты/вы in 315 of 318 (`14` B2).
- Wrong or empty rows followed faithfully: a common noun typed as a male character and sounded out in 29
  paragraphs (26b); a place with two renderings, 15/11, and an empty row (e4b) (`14` B2).
- Recurring words with no row: 104/34, 97/26 (two creature names, 26b); Mr 83/12 and Mrs 75/30 (e4b); sir 49/44
  (e4b) (`14` B2).
- A second spelling of the main character in 14 paragraphs on 26b (623 against 14). Wrong case stems on e4b: 31
  uses of one name (about 8 %) (`14` B2).
- Two dialogue conventions in one book on 26b: dash 897, quotes 529, 161 hybrids, both inside 27 of 39 chapters
  (`14` B2).
- English sources: no record of ти/ви. One listener 19/3, another 3/4; 28 switches in 127 consecutive addressed
  items (`14` B2, `13` S6).
- The narrator turns female in about 15 forms per run (`14` B2).
- Almost every name in a real run is "suggested"; only a person's action pins a row (`14` B1a).

**Code today.**

- A wrong spelling of a suggested name is a NOTE; of a pinned name a BLOCK: `P/checks/NameLossCheck.java`,
  line 66.
- The term sweep skips suggested rows: `P/proofread/TermSweep.java`, `notSwept`, lines 197–203 ("the entry is
  only suggested").
- A name found late: `P/run/DecisionFollowUp.java`, `scanNames`, lines 102–126; first-spelling learner
  `P/run/TermLearning.java` (see F11).
- Whole-book audits that report and do not fix: `P/audit/NameVariants.java`, `P/audit/FinalAudit.java` (lines not
  re-checked); `P/audit/SourceVariants.java`, `MIN_WORDS = 6` at line 27 (repeated lines under 6 words are
  skipped).
- Typography keeps the model's dialogue choice ("A dash dialogue stays a dash dialogue", class Javadoc line 12):
  `P/typography/TypographyNormalizer.java`; speech repairs in `P/typography/SpeechRepairs.java`.
- Name forms and groups: `P/glossary/NameForms.java`, `P/glossary/NameGroups.java`, `P/glossary/NameAliases.java`,
  `P/glossary/TargetValidator.java`. Narrator: `P/narrator/NarratorInference.java`. Pin state:
  `A/project/GlossaryEntry.java` (lines not re-checked).

**Fix options the review recorded (all with zero model calls, `14` B5).**

- (a) A whole-book rendering report per row and recurring term after Translate (the B2 tables are this report).
- (b) Pin a suggested name once the book used one stem N times with no rival; then fix minority spellings by code
  (stem replaced, ending kept).
- (c) A per-name declension check on drafts.
- (d) A frequent row may not stay empty (a row with 10 uses or more and no target gets one more suggestion call,
  or is dropped — this one costs 1 call per book).
- (e) One dialogue convention per book, stored, enforced by typography.
- (f) Per-listener ти/ви counts with the minority marked.
- (g) Narrator forms counted per chapter.
- (h) Titles and honorifics as rows that reach the call.
- (i) The repeated-line audit from 1 word for lines repeated 3 times or more.
- Order of value given by the review: (a), then (b), then (e), (d), (h).

**How to prove a fix.** Run the whole-book rendering report on the same whole-book exports before and after.
Targets from the measured cases: second spelling of the main character 14 → 0; one dialogue convention per book;
no row with 10 uses or more left empty; titles sent in every call that holds them.

## F15 — The reply format changes reliability and speed, not quality

**What happens.** JSON with a schema, JSON without, tagged lines, numbered lines and one plain paragraph per call
give the same text quality. The formats differ in how often a reply is lost and in how many tokens the wrapper
costs. Class: Likely (measured).

**Evidence.**

- All five formats score within 0.15 of each other on both models (47 paragraphs, two readers) (`16`). Table:
  [Reply format and runaway replay](#reply-format-and-runaway-replay).
- JSON did no text damage in the traced run: of 216 translate-family replies 210 were strict JSON; the other 6
  are the 3 runaways and 3 cut Proofread replies; 0 backslash escapes, 0 broken quotes, 0 format re-asks; 2 items
  omitted by the model (`13` S3).
- Wrapper cost: 11 % of draft output tokens and 19 % of Proofread output (`FINDINGS` F15, `12` P5: 3,374 of 31,393
  and 2,891 of 15,438). `13` S1 counts 3,558 of 31,534 (11.3 %) for drafts and 1,937 of 15,505 (12.5 %) for
  Proofread **(sources differ for Proofread)**. Estimated time: 149 s (7.4 % of the run), about 60–90 s reachable
  (`12` P5).
- Numbered lines use 5–8 % fewer output tokens and never ran away (0 of 198 + 28) (`16`).
- Unenforced JSON is the worst choice: 3 of 66 replies could not be parsed (`16`).
- Formatting marks are wrong in about 15 % of marked items in any format, all emphasis pairs (`13` S4).

**Code today.**

- Schema on the wire: `L/client/ollama/OllamaClient.java` (Ollama `format`), `L/client/openai/OpenAiRequestMapper.java`
  (`response_format`) (lines not re-checked).
- Loose batch schema: `P/prompt/BatchSchema.java`; Proofread schema `P/prompt/ProofreadSchema.java` lines 20–25.
- Reading: `P/batch/BatchEntries.java`, `P/batch/RawEntries.java`, `P/DraftReplies.java` (see F3, F4).

**Fix options the review recorded.**

- (a) Keep JSON and add the F4 recovery — the smallest change.
- (b) Numbered or tagged lines for translate replies — removes the runaway class and the wrapper; needs a new
  aligner and salvage.
- (c) Paired tags for emphasis instead of two independent marks (untested).
- (d) A recovery ladder: on a misaligned reply halve the batch; on a cut reply send the single paragraph as plain
  text (`15` §2.3).
- Not a lever: a "better" format for quality; unenforced JSON.

**How to prove a fix.** Review experiment E3: at least 300 paragraphs, arms by format. Win: no runaway, no more
lost items or mark errors than today, at least 10 % fewer output tokens, panel on 60 items shows no loss.

## F16 — Defaults work against the goal

**What happens.** Several defaults make the first run slower or weaker than it needs to be. Each is as strong as
the finding it cites. Class: Confirmed (where each default is set).

**Evidence and code today.**

| Default | Where it is set (checked) | Why the review calls it wrong |
|---|---|---|
| Dial Balanced for every model | `A/settings/AppSetting.java` line 52; `A/project/BookBrief.java` line 180; also `P/project/NewBookBrief.java`, `U/state/AutomationSettings.java` (lines not re-checked) | on small models Proofread costs a quarter of the run for about 1 % of changes (F2) |
| Chapter summaries on | `A/project/BookBrief.java` lines 338–340 | 6.8 % of the run, 11.5 % of the Translate stage's model time, no effect shown (F8) |
| Recommended model under 24 GB is e4b; no middle step; pair ignored | `U/state/ModelRecommendation.java` lines 19–35 | e4b: 40 % bad paragraphs in the traced sample (F1) |
| Names pass not part of a run | `P/run/PrepStage.java` lines 30–35 | F11 |
| Target language empty | `A/project/BookBrief.java` lines 166–181 | the only forced decision (F11) |
| Narrator unspecified → a question at Start | `A/project/BookBrief.java` line 181; `U/state/NarratorHints.java` | forced male/female was a coin flip (F5) |
| Batch size 2 for an unknown model | `A/llm/ModelProfile.java` line 42 | half the batch of the built-in Gemma rows |
| Name policy = transliterate | `A/project/BookBrief.java` line 174 | speaking names sounded out (F5) |

- Right as they are: review mode Unattended (`AppSetting.java` line 49), keep-awake on (line 58), reopen last
  project (line 61), open the review desk at the end (line 55); per-chunk commits, export at any time, outage
  waiting (`14` A5, A3).
- Gaps for unattended use: no automatic continue after a crash or quit (1–2 clicks); behaviour on lid-close sleep
  not proven in a run (`14` A3, A4).

**Fix options the review recorded.**

- Fast when the model is below 26b-class; Balanced from there.
- Summaries off until a gain is measured.
- A middle model tier, and the language pair as an input. See [Owner decisions](#owner-decisions): the UI must be
  the same for every language.
- Names pass in the default chain; target language from the OS language or the last project; "not stated"
  narrator default.
- Measure the batch size once per new model and store it.
- Continue at launch after a crash in Unattended mode, behind a short "continuing… Cancel" banner.

**How to prove a fix.** Each default is read from a fresh project on a fresh database. For the dial: run time and
exported text of the traced slice at Fast against Balanced on a small model (Proofread kept 1 good and 2 bad
changes there).

# Measured facts to reuse

## Model table

Hard sample, 1–5, mean of two readers. Summary levels (`SUMMARY`):

| Setup | Score |
|---|---:|
| strong outside model (reference) | about 4.5 |
| gemma4:26b, three drafts merged | 3.0–3.3 |
| gemma4:26b, the app's request | about 2.8 |
| TranslateGemma 27b, the app's request | level with 26b, 5 times slower here |
| MamayLM 12b (Russian → Ukrainian only) | about 2.7 |
| qwen3.8:27b, Lapa, gemma4:12b, Nemotron e4b, gemma4:e4b | 2.0–2.5 |
| qwen3.5:9b, gpt-oss:20b, Ministral 14b | 1.5–1.9 |
| Hunyuan-MT 1.5 7B (translation-only; plain reply only, loses inline marks) | about 2.0 |

Sweep, one sheet per request shape (`09d` §1; third column is one reader only):

| Model | Server | App request, batch of 1 | Lean prompt | App rules, plain reply |
|---|---|---:|---:|---:|
| strong outside model (anchor) | — | 4.68 | 4.60 | 4.50 |
| gemma4:26b-mlx | Ollama | 3.24 | 2.97 | 3.03 |
| mamaylm-gemma-3-12b-it-v2.0 | LM Studio | 2.83 | 2.81 | 2.65 |
| lapa-v0.1.2-instruct | LM Studio | 2.53 | 2.71 | 2.24 |
| gemma4:12b-mlx | Ollama | 2.47 | 2.06 | 2.24 |
| nvidia-nemotron-3.5-bolt-e4b | LM Studio | 2.35 | 2.18 | 2.15 |
| gemma4:e4b-mlx | Ollama | 2.23 | 2.25 | 1.94 |
| google/gemma-4-e4b | LM Studio | 1.96 | 2.01 | 1.94 |
| mistralai/ministral-3-14b-reasoning | LM Studio | 1.96 | 1.84 | 1.76 |
| qwen3.5:9b | Ollama | 1.82 | 1.88 | 1.74 |
| gpt-oss:20b | Ollama | 1.51 | 1.51 | 1.44 |

Later additions, each on its own sheet with its own 26b anchor (`09d` §6, §7):

| Model, app request | Mean | Bad % | 26b anchor on that sheet | Time, 34 paragraphs | Notes |
|---|---:|---:|---:|---:|---|
| translategemma:27b | 2.85 | 12 | 2.94 (21 % bad) | 1,181 s (26b: 218 s) | 11 better / 10 same / 13 worse; its documented format scores 2.68 and keeps inline marks in 7 of 16 paragraphs (14 of 16 with the app's request; 26b 16 of 16) |
| qwen3.8:27b | 2.29 | 59 | 2.94 | 707 s | −0.65 and −0.71 against 26b on two sheets; with thinking on 9 of 34 replies empty and 14 cut |
| Hunyuan-MT 1.5 7B, best arm (app rules, plain reply) | 2.10 | 71 | 3.07 (18 % bad) | about 500 s (26b: 220–230 s) | JSON request: 15 of 34 replies loop to the cap; inline marks kept in 4 of 16; its terminology template followed 62 of 62 |

Other model facts:

- MamayLM 12b scores 3.50 on the Russian book and 2.45–2.62 on the two English books (`09d` §1).
- The same e4b weights: 1.96 under LM Studio, 2.23 under Ollama with the app's request; inside the noise band
  (`09d` §1, `FINDINGS` F10).
- Thinking on 26b used the whole token budget in 24 of 34 calls; temperature 1.0 and thinking on were worse on e4b
  (`WHAT-IS-GOOD`).
- Time for 34 paragraphs: 26b under Ollama about 200 s; MamayLM about 400–490 s; Lapa about 280–370 s (`09d` §4).

## Context-block replay

14 real draft requests, 47 paragraphs read, two readers (`16`).

Quality:

| Arm | 26b mean | 26b bad % | 26b vs `full` (better / same / worse) | e4b mean | e4b bad % | e4b vs `full` |
|---|---:|---:|---|---:|---:|---|
| `full` (the app today) | 3.06 | 19 | — | 2.56 | 43 | — |
| `none` (rules and items only) | 2.91 | 23 | 11 / 20 / 16 | 2.52 | 43 | 9 / 28 / 10 |
| `names` (name rows only) | 2.93 | 23 | 15 / 17 / 15 | 2.57 | 43 | 10 / 29 / 8 |
| `core` (name rows + previous pairs + look-ahead) | 2.97 | 18 | 12 / 17 / 15 | 2.53 | 45 | 7 / 30 / 10 |

Mechanics:

| Arm | 26b: name-row rendering used | 26b time, 14 requests | 26b prompt time | e4b: rendering used | e4b time | Output tokens (26b / e4b) |
|---|---:|---:|---:|---:|---:|---:|
| `full` | 61 of 62 | 243 s | 54 s | 62 of 62 | 155 s | 5,445 / 5,183 |
| `none` | 50 of 62 | 200 s | 18 s | 52 of 62 | 139 s | 5,494 / 5,189 |
| `names` | 62 of 62 | 185 s | 19 s | 62 of 62 | 139 s | 5,417 / 5,184 |
| `core` | 60 of 60 | 284 s (one runaway) | 26 s | 62 of 62 | 146 s | 8,297 / 5,196 |

- Inline marks: kept in 13 of 13 marked paragraphs in every 26b arm; 12–13 of 13 on e4b.
- Both wrong rows of the traced run are in 6–7 of 8 candidates in every arm that carries the row: a wrong row is
  followed too.
- `full` against `names`: +0.14 on 26b, −0.01 on e4b. `full` against `core`: +0.08, +0.03.

Per-block relevance on the traced run (`13` S5):

| Block | Present in draft calls | Share of draft prompt tokens | Measured effect |
|---|---|---:|---|
| Name rows | 132 calls, 431 rows | 3 % | followed in 337 of 341 item uses (99 %) |
| Term rows | 133 calls, 666 rows, 34 distinct | 1.3 % | none visible |
| Recently present | 127 calls, 284 names | 2.1 % | 2 of 284 names used |
| Earlier chapters | 121 calls | 11.6 % | none (7.2 % against 7.0 % word overlap) |
| Previous pairs | 136 calls | 12.6 % | weak (78 % against 73 % chance on ти/ви) |
| Next item | 130 calls (dropped by budget 11 times) | 2.6 % | none visible |
| Dialogue reminder | 117 calls | — | relevant to 0 of 405 items |
| Marks reminder | 58 calls | — | marks still wrong in 15 % of marked items |

Request make-up (all 141 draft calls, `09c` §4): fixed rules 53 %, context 35 %, the text to translate 11 %.
`FINDINGS` F6 gives 53–55 % and 9–11 %.

## Reply format and runaway replay

Quality by format, full context (`16`):

| Arm | 26b mean | 26b bad % | 26b vs `full` | e4b mean | e4b bad % | e4b vs `full` |
|---|---:|---:|---|---:|---:|---|
| `full` (JSON + schema, today) | 3.06 | 19 | — | 2.56 | 43 | — |
| `jsonfree` (JSON, schema not enforced) | 2.91 | 21 | 2 / 34 / 11 | 2.55 | 38 | 5 / 36 / 6 |
| `xml` (one tagged line per item) | 3.03 | 17 | 10 / 25 / 12 | 2.50 | 38 | 6 / 32 / 9 |
| `lines` (one numbered line per item) | 3.12 | 11 | 15 / 20 / 12 | 2.43 | 47 | 4 / 31 / 12 |
| `single` (one paragraph per call, plain text) | 2.91 | 17 | 13 / 16 / 18 | 2.56 | 38 | 14 / 20 / 13 |

Mechanics by format (`16`):

| Arm | 26b rendering used | 26b time | 26b prompt time | e4b rendering used | e4b time | Output tokens (26b / e4b) |
|---|---:|---:|---:|---:|---:|---:|
| `jsonfree` | 61 of 62 | 213 s | 47 s | 62 of 62 | 133 s | 5,451 / 5,168 |
| `xml` | 62 of 62 | 211 s | 39 s | 62 of 62 | 151 s | 5,501 / 5,271 |
| `lines` | 62 of 62 | 197 s | 37 s | 62 of 62 | 140 s | 5,175 / 4,907 |
| `single` | 62 of 62 | 232 s (56 calls) | 71 s | 62 of 62 | 152 s | 5,026 / 4,774 |

Runaway replay, e4b, 66 quote-ending requests × 3 seeds, 534 items per arm (`16`):

| Format | Runaway replies | Items lost | Calls with a loss | Mark errors | Time |
|---|---:|---:|---:|---:|---:|
| JSON + schema (today) | 6 of 198 (the same 2 requests at every seed) | 24 | 6 | 28 | 1,481 s |
| JSON, schema not enforced | 0 | 36 | 9 (the same 3 requests) | 27 | 873 s |
| numbered lines | 0 | 0 | 0 | 24 | 969 s |

- The three seeds gave almost identical replies, so this is 66 independent cases seen three times, not 198.
- The replay cap was 3,000 tokens, so each runaway cost about 100 s. In the app the cap is about 250 tokens for
  such a call: a runaway costs a few seconds and the last item, then one more call.
- Enforced JSON runs away on about 3 % of quote-ending batches on e4b (2 of 66) and once in 56 calls on 26b.

## Time breakdown of the traced run

2,018 s wall time, 353 calls, e4b, Balanced (`12`).

Where the time is:

| Part | Seconds | Share |
|---|---:|---:|
| Generation (writing replies) | 1,540 | 77 % |
| Prompt evaluation (reading prompts) | 450 | 22.5 % |
| Model load | 7.7 | 0.4 % |
| Not in the model | about 17 | 0.8 % |

By stage and call kind:

| Stage / kind | Calls | Seconds | Share of run |
|---|---:|---:|---:|
| Prepare: brief | 6 | 48 | 2.4 % |
| Prepare: names (all) | 104 | 377 | 18.7 % |
| — term reading, names | 21 | 140 | 6.9 % |
| — term reading, recurring words | 60 | 182 | 9.0 % |
| — term reading, total | 81 | 322 | 16.0 % |
| — term choice | 17 | 22 | 1.1 % |
| — suggest targets | 5 | 30 | 1.5 % |
| Translate (all) | 176 | 1,194 | 59.2 % |
| — drafts | 141 | 1,026 | 50.8 % |
| — repair | 8 | 31 | 1.5 % |
| — chapter summaries | 27 | 137 | 6.8 % |
| Proofread | 67 | 382 | 18.9 % |
| Total | 353 | 2,018 | 100 % |

- Tokens: 581,280 prompt (276,981 cached, 48 %; `13` says 47 %), 61,925 output.
- Prompt evaluation 660–690 tokens/s uncached. Generation 35.6 tokens/s through Translate, 54–60 in the first
  minutes of Prepare and the last five minutes of Proofread.
- The floor: drafting the 385 paragraphs took 1,026 s. The run took 1.97 times that.
- 353 calls for 385 paragraphs = 0.92 calls and 5.2 s of model time per paragraph (`13` S11).
- All 353 calls were answered on the first attempt; 0 failed or retried (`09c`).

The four waste items, as `SUMMARY` names them, with the seconds from `12` and `FINDINGS` F13:

| Waste item | Share (`SUMMARY`) | Seconds | Detail |
|---|---:|---:|---|
| Proofread on windows with nothing to fix | 19 % | 382 (whole stage) | 22 windows with only typography-normalised items: 96 s; 24 windows with only typography and common-word notes: 146 s (together 242 s); items written back unchanged: about 100 s; 3 windows lost at the cap: 16.9 s; 21 windows with a real reason: 140 s |
| Model calls judging common words as terms | 10 % | 204 | 60 reading calls (182 s), 58 with an empty answer, plus 17 choice calls (22 s); the same question asked twice |
| Chapter summaries | 7 % | 137 | 27 calls; 13 are re-asks that re-read the chapter (65 s) |
| Re-drafts and runaway replies | 5 % | 61 + 45 (+ 19) | re-drafts of 20 refused batch items: 61 s (about 40 s avoidable); runaway drafts 28.1 s and cut Proofread windows 16.9 s = 45 s; repairs of defects that do not exist: 19 s |

- Sum as `12` counts it: Proofread 382 + term calls 204 + summaries 137 + avoidable re-drafts about 40 + false
  repairs 19 + runaway drafts 28 = **about 810 s, 40 % of the run**. Without them the slice takes about 20 minutes
  instead of 33.6.
- Without the word-term calls, the summaries and the false Proofread items: about 353 → 220 calls and about 25 %
  less model time (`13` S11). `12` estimates about a third on a whole book. **(Two estimates; both are
  estimates.)**
- JSON wrapper: est. 149 s (7.4 %), about 60–90 s reachable (`12` P5).

## Consistency numbers

Traced run (`09c`, `13`):

| Fact | Number |
|---|---|
| Name rows injected in draft calls containing the name | 100 % (e.g. 34 of 34, 27 of 27, 6 of 6) |
| Name rows followed in item uses | 337 of 341 (99 %) |
| Rows that differ from the model's own first choice, followed | 17 of 17 paragraphs |
| Stem variants for the 12 main names in the export | 0 |
| Wrong gender note on a row, ignored by the model | 14 of 14 paragraphs |
| Locked title rows sent | 0 of 14 calls (`FINDINGS`: 0 of 13) |
| Paragraphs with містер/місіс against пан/пані | 21 against 5 |
| "sir" rendered two ways | 7 against 1 |
| Dialogue style in the export | 234 paragraphs with «…», 3 with dash lines |
| Female narrator paragraphs | 4 (narration is male) |
| Address switches | 28 in 127 consecutive addressed items |
| Paragraphs carrying a wrong Prepare name row | 28 of 385 (about 7 %) |

Whole books (`14` B2). "26b" and "e4b" are two runs of the same English novel; "ru" is the Russian novel on e4b.

| Dimension | 26b | e4b | ru (e4b) |
|---|---|---|---|
| Rows with a right target: stem kept | 98–100 % | 98–100 % | 9 of the 10 most frequent names at 99–100 % |
| Main character, second spelling | 623 / 14 (2 %) | 640 / 0 | — |
| One name declined from a wrong stem | regular | 31 uses (about 8 %) | — |
| A place with an empty row: two renderings | 23 / 4 | 15 / 11 | — |
| A common noun typed as a character, sounded out | 29 paragraphs | not sounded out (26 translated) | — |
| A wrong row followed ("Hall") | — | 29 paragraphs | — |
| Creature name 1 (140 paragraphs) | 104 / 34 | 140 / 0 | — |
| Creature name 2 (130 paragraphs) | 97 / 26 (+ 4) | 123 / 4 | — |
| A magic term (47 paragraphs) | 34 / 11 | 24 / 22 | — |
| Mr (94) | пан 70, містер 1 | містер 83, пан 12 | — |
| Mrs (107) | пані 77, місіс 29 | місіс 75, пані 30 | — |
| sir (97) | сер 90, пане 5 | пане 49, сер 44, містере 3 | — |
| Dialogue convention | dash 897, «…» 529, straight-quote 11; 161 hybrids; both inside 27 of 39 chapters | «…» 1,417, dash 16 | dash 795 of 796 |
| Narrator's own past forms outside speech | masculine 565, feminine 15 | masculine 608, feminine 15 | not measured |
| Address to one listener (ти-only / ви-only paragraphs) | 19 / 3; 32 / 0; 3 / 4; 3 / 2 | 6 / 1; 30 / 2; 5 / 1 | source ты → ти 187 of 190; вы → ви 128 of 130 (315 of 318) |
| Same short line, different target | 8 of 48 (punctuation only) | 1 of 48 | 0 of 11 |
| Paragraphs left in the source language | 24 | 5 | 1 |
| Misaligned paragraphs | 0 of 3,581 | 0 | — |

- Three runs of the same book gave three spellings of the title character; each run was consistent inside itself.
- Narrative terms with one clear rendering stayed stable over a whole book (95–100 % one rendering).

## Merge results on gemma4:26b

"Merge" = one extra call at temperature 0.2 that gets the source and several drafts and writes the final text. All
on the 34 hard paragraphs, two readers. Each row is one sheet; scores compare only inside a sheet (`09d`).

| Reading (sheet) | Merge variant | Merge mean | Bad % | Single draft (app request) on that sheet | Bad % | Gain | Better / same / worse | Extra time, 34 paragraphs |
|---|---|---:|---:|---:|---:|---:|---|---|
| First (`p26`) | `multi`: three drafts at temperature 0.8, merged | 3.03 | 26 | 2.75 | 44 | +0.28 | 15 / 11 / 8 | 724 s (base about 200 s) |
| Second (`p26r2`) | `merge3`: 26b's own three low-temperature drafts (app request, lean, TranslateGemma header) | 3.29 | 12 | 2.85 | 26 | +0.44 | 17 / 11 / 6 | 275 s + two drafts |
| Second (`p26r2`) | `ens`: drafts of 26b, MamayLM and Lapa, merged by 26b | 3.29 | 15 | 2.85 | 26 | +0.44 | 15 / 16 / 3 | 264 s + two models |
| Second (`p26r2`) | `multi` again | 3.03 | 21 | 2.85 | 26 | +0.18 | 16 / 9 / 9 | 724 s |
| Third (`p26r3`) | `merge3` | 3.24 | 12 | 2.79 | 29 | +0.45 | 18 / 12 / 4 | 275 s + two drafts |
| Third (`p26r3`) | `ens` | 3.18 | 15 | 2.79 | 29 | +0.39 | 14 / 16 / 4 | 264 s + two models |
| Third (`p26r3`) | `merge3b`: three other drafts (replication) | 3.04 | 18 | 2.79 | 29 | +0.25 | 14 / 14 / 6 | 274 s + two drafts |
| Third (`p26r3`) | `merge2`: two drafts | 3.07 | 18 | 2.79 | 29 | +0.28 | 12 / 16 / 6 | 247 s + one draft |
| `n27b` (same `merge3` texts, read a third time) | `merge3` | 3.35 | 9 | 2.91 | 24 | +0.44 | 19 / 11 / 4 | — |
| `hy` sheet | 26b merges three of its own drafts | 3.41 | 3 | 3.07 | 18 | +0.34 | — | — |

- The "Gain" column is the merge mean minus the single-draft mean of the same sheet. The review states +0.44
  (`p26r2`, `n27b`), +0.28 (`p26`), +0.25 (`merge3b`) and +0.28 (`merge2`); the other gains were computed for this
  digest from the two means.
- Statistics for `merge3` on `p26r2`: two-reader mean +0.44, 95 % interval +0.18…+0.72; significant for the Claude
  reader; +0.32 and not significant for the Codex reader alone. `ens` is significant for both readers
  (`FINDINGS` F2, `09d` §3).
- About 14 second-pass and prompt variants were tried on 26b, so the best one is partly a selection effect. The
  replication (`merge3b` +0.25, `merge2` +0.28) is the fairer estimate. **A fair expectation is about +0.3**, with
  the bad share roughly halved.
- `SUMMARY` gives the merged level as "3.0–3.3"; two later sheets show 3.35 and 3.41 **(sources differ; sheets are
  not comparable with each other)**.
- Single-draft edit instead of a merge: +0.16 (`p26`), 3.01 against 2.85 on `p26r2` — inside noise.
- Low-temperature drafts merge better than hot ones (3.29 against 3.03) and cost a third of the time.
- Cost: three drafts and one merge are about four calls per paragraph instead of one — about four times the time
  of a single draft (`09d` §2, §3). The review compares this with what the app spends on a draft plus Proofread at
  the Max dial.
- The gain exists on gemma4:26b only: TranslateGemma +0.04, qwen3.8:27b +0.10, MamayLM merge 2.13 (68 % bad)
  against 2.73 for its app request, Lapa 2.46 against 2.41; 26b merging three Hunyuan drafts 3.00, level with one
  26b draft (3.07) (`09d` §4, §6, §7).
- Not measured: the merge inside the app (with name rows, checks and batches), on fresh paragraphs, or on a whole
  chapter; its time inside the app.

# What the evidence supports keeping

From `WHAT-IS-GOOD` (review file named after each line). The next change should not rework these.

- A segment is a whole paragraph, never a sentence; the model sees connected prose.
- Structure survives: 3,581 of 3,581 paragraphs aligned in the English novel; no batch item mix-up in any run;
  strict batch ids.
- Code after the model does no harm: typography changes punctuation and case only; the term sweep changed 0 text in
  five closing runs; translation-memory reuse needs an exact source and both neighbours.
- No silent prompt truncation: window 16,384, largest real prompt 3,229 tokens, `truncate:false`. The Cyrillic
  token estimate is well calibrated (2.87 chars/token assumed, 2.83 measured).
- One bounded repair; a refused repair leaves the draft in place. Repair fixes what its checks truly see (both
  mixed-alphabet words of the traced run).
- Finding names by code over the whole book has good recall. The model never decides which names exist.
- Name rows reach the calls that need them (100 % of draft calls containing the name) and the export follows them.
- The run is stable and its accounting is honest: 353 calls, 0 failed or retried; log, JSON and report agree.
- Batch salvage works: complete entries of a broken batch reply are kept; no text was lost in 20 fallbacks.
- Language behaviour is chosen by data files, not branches; any language tag loads the generic file; no Ukrainian
  text leaks into another target. Only 5 rule lines of the Ukrainian file reach a real system prompt.
- The app's fixed rules and reply protocol are not what makes the text bad (4.54 against 4.54 on the strong
  model).
- Low temperature and thinking off are right for drafting.
- The JSON protocol is reliable on normal replies: one format re-ask in a whole book.
- Evals: requests are built by production code; the statistics code is correct; the fast loop (`probe.sh`,
  `--quick`, mini) is a sound way to iterate.
- Also "fine as is" in `12`, `13`, `14`: batch of 4 on e4b; the stable-first prompt order (59 % of draft prompt
  tokens cached); the output cap at about 1.5 times the expected length; Unattended as the default review mode;
  outage handling without the person; one transaction per chunk and export at any time.

# Owner decisions

Recorded 2026-11-14.

- Only measured or observed improvements go into the change. No LanguageTool or any language-specific external tool (they are separate products). The speaker/addressee line is out (untested).
- "Three drafts, then merge" is allowed only at the Max setting and only if an in-app test shows a real gain; a gain of 1–2 % for 15 % or more extra time is rejected.
- gemma4:26b stays the recommended model; no special support for TranslateGemma, Qwen or Hunyuan-MT; MamayLM 27B and gemma4:31b are too slow for the owner's computer and will not be tested.
- The UI must be the same for every language: no language-specific option rows; language files may keep internal defaults.

# Not acted on

The reasons are derived from the owner decisions above and from the class the review itself gave each finding.

- **F6 — The translate request is heavy and contradicts itself.** The review's own class: cost small on a strong
  model, unmeasured on a small one. Rewording the prompt is listed by the review as "not worth doing". The parts
  with a measurement are handled elsewhere: the dialogue reminder under F8, one dialogue style per book under F14.
- **F9 — Universality (non-spaced scripts, other alphabets).** Confirmed by code reading only; nothing was
  measured, and no reader exists for those languages. Not a measured improvement.
- **F10 — Sampling on the LM Studio path differs from Ollama.** Effect unproven: 1.96 against 2.23 on one sheet,
  inside the noise band. Needs one controlled test before anything is built.
- **F17 — Checks without a model call (LanguageTool).** Excluded by the owner: no LanguageTool or other
  language-specific external tool. Its effect on machine translation output was not measured, and its licence was
  not verified. The "several drafts, then merge" idea it mentions is covered by the owner decision on Max.
- **F18 — Models worth testing next.** Closed by the owner: gemma4:26b stays the recommended model; MamayLM 27B
  and gemma4:31b will not be tested; Hunyuan-MT 1.5 7B was tested and rejected (2.0–2.1, 71–79 % bad).
- **F19 — Speaker and addressee context.** A hypothesis, not tested. Excluded by the owner (the speaker/addressee
  line is out).
