# Results: quality rounds 15d to 15f

*Measurements of the quality rounds before the redesign.*

## Where the time goes {#where-the-time-goes}

Measured by task 15d.0 (2026-10-04): the first 300 decided segments of Bartimaeus 1 (EPUB, en→uk) through the command
line — `--provider ollama --quality balanced --names transliterate --stop-after 300`, Ollama native endpoint, one
inference at a time — with the provider's own counts and timings from `modelCallsByKind` in the `--report` JSON.
Prompt evaluation and generation are the server's `prompt_eval_duration` and `eval_duration`; their shares of a call's
wall time do not add to 100 % because the rest is load, queueing and the client. Ollama reports no cached-token
figure (it counts only the tokens it evaluated), so the cache saving is not measured here; the prompt-evaluation
throughput below is what the cache would have to beat.

| Model | Call kind | Calls | Input tokens | Output tokens | Wall (share of model time) | Prompt-eval share | Generation share |
|---|---|---:|---:|---:|---:|---:|---:|
| `gemma4:e4b-mlx` | draft | 296 | 301,148 | 19,117 | 756 s (81 %) | 24 % | 74 % |
| | judge | 83 | 117,542 | 1,753 | 168 s (18 %) | 59 % | 39 % |
| | directed fix | 8 | 6,061 | 211 | 12 s (1 %) | 35 % | 62 % |
| `gemma4:26b-mlx` | draft | 296 | 303,970 | 19,978 | 1,138 s (56 %) | 34 % | 65 % |
| | judge | 135 | 179,781 | 9,060 | 602 s (30 %) | 44 % | 54 % |
| | directed fix | 56 | 50,901 | 6,119 | 285 s (14 %) | 29 % | 70 % |
| | repair, reflect, improve | 3 | 2,350 | 146 | 10 s (0 %) | — | — |

Whole run: e4b 387 calls, 936 s of model time, **3.1 s per segment**, 1.29 calls per segment, 296 accepted and 4 flagged
of 300; 26b 490 calls, 2,035 s, **6.8 s per segment**, 1.63 calls per segment, 280 accepted and 20 flagged. A draft call
sends about 1,020 tokens and returns about 65, so a quarter to a third of its time is evaluating a prompt that is mostly
the same instructions again; a judge call sends about 1,400 tokens for about 20–70 back, so on e4b more than half of
a judge call is prompt evaluation. The 26b judge and fix calls fire far more often (135 and 56 against 83 and 8), which,
with the larger model's slower generation, makes up the extra 3.7 s a segment over e4b.

**Segment lengths** (`scripts/segment-histogram.py <book>`, words per block; a block approximates a segment, so counts
differ by a few from the app's own):

| Book | Blocks | Words | Mean | Median | Blocks at 1–3 / 4–10 / 11–30 / over 30 words | Words at 1–3 / 4–10 / 11–30 / over 30 |
|---|---:|---:|---:|---:|---|---|
| Bartimaeus 1 | 3,579 | 127,228 | 35.5 | 25 | 9.6 / 17.6 / 29.3 / 43.4 % | 0.5 / 3.5 / 15.9 / 80.1 % |
| Harry Potter 1 | 2,937 | 80,745 | 27.5 | 19 | 5.4 / 22.9 / 39.7 / 31.9 % | 0.4 / 5.7 / 27.3 / 66.6 % |

Reading both together: 27 % of Bartimaeus blocks have at most 10 words yet carry about 4 % of its words, so one call per
segment spends most of its calls on very little text, and the 1,020-token prompt is mostly fixed instructions. That is what
batching (15d.8) and a lean reviewer (15d.6) have to win back; generation of the translation itself is the floor.

## Group 15d: before and after {#15d-results}

Measured 2026-10-05 on Bartimaeus 1 (EPUB, en→uk, Ollama native endpoint, **Balanced**, Unattended, names transliterate,
one inference at a time) with the `--report` JSON; "before" is the 15d.0 measurement on the same first 300 segments and,
for the whole book, the 8 h 18 min LM Studio `google/gemma-4-26b-a4b-qat` run of 2026-10-03. "Seconds per segment" is the
model time (`modelSeconds`) divided by the segments decided.

| Run | Calls / segment | Seconds / segment | Flagged | Notes |
|---|---:|---:|---:|---|
| e4b, first 300, before (15d.0) | 1.29 | 3.1 | 4 of 300 | judge + fix chain, one draft call per segment |
| e4b, first 300, batches of 4 | 0.60 | 3.45 | 5 of 300 | draft 0.30 / segment, reviewer 80 calls |
| e4b, first 300, batches of 8 (now) | **0.34** | **3.02** | 4 of 300 | draft 91 → 51 calls, reviewer 80 → 42 calls |
| 26b, first 300, before (15d.0) | 1.63 | 6.8 | 20 of 300 | |
| 26b, first 300, batches of 4 | 0.69 | 4.48 | 5 of 300 | |
| 26b, first 300, batches of 8 (now) | **0.50** | **5.14** | 3 of 300 | draft calls dominate (1,180 s) |
| e4b, whole book (3,783), now | **0.36** | **3.44** | 92 (2.4 %) | 3 h 40 min against 8 h 18 min; 0 timeouts; 584 repaired, 72 verbatim; audit lists 5 suspicious |
| 26b (LM Studio), whole book, 2026-10-03 | not measured | 7.9 | 170 (4.5 %) | the 8-hour baseline |

Against the targets of task 15d.14: calls per segment ≤ 0.4 **met** (0.34 on the first 300, 0.36 over the book); the whole
book takes 44 % of the baseline's time **(about half, met)**, but the first-300 goal of about 1.6 s (e4b) and 3.4 s (26b)
per segment is **not met** (3.0 s and 5.1 s): generating the translation is the floor (about 20,000 output tokens for 300
segments at about 38 tokens a second, 2.0 s a segment), and the reviewer adds about 0.9 s. Flagged ≤ 2 % is **just missed**
over the book (2.4 %; 4.5 % before). Unbalanced quote pairs: none left in the audit's list. False flags as a share of flags
and name consistency ≥ 99 % were **not measured**. Recurring terms: `gemma4:e4b-mlx` never returns the optional `terms` field (0 renderings recorded over the book), so renderings are
now also learned from the decided pairs by co-occurrence (second e4b whole-book run, 2026-10-06: 3 h 31 min, 0.347 calls per
segment, 70 flagged = 1.85 %): 12 terms learned, 90.3 % of their occurrences carry the learned rendering (`master` 99 %,
`pentacle` 94 %), against the 95 % target; the generic words the key-term scan also proposes pull the aggregate down.

Where e4b's 13,017 s of model time went over the book: draft 8,648 s (674 calls, 1.00 M prompt tokens, 290 k output),
reviewer 3,718 s (484 calls, 1.03 M prompt, 106 k output), directed fix 612 s (198 calls), placeholder repair 39 s.

## Quality round 2: the "before" table (15e.4) {#15e-before}

Measured 2026-10-06/07 on the commit that holds 15e.1–15e.3, Ollama native endpoint, one inference at a time, through the
production request factory (`PromptRequests`, window 8192, Balanced). These are the numbers every 15e task has to move.
Thresholds per model class stay in `eval/thresholds.json`; the new suites assert no floor yet, a task that fixes a class
raises the floor in its own change.

**Reviewer corpus (15d.1 + 15e.2 production path, stability ×3).** e4b: false negatives 0 %, false positives 0 %, stability 100 %, token breaks 0; 26b: false negatives 0 %, false positives 5.9 %, stability 97 %, token breaks 0.

**Batch protocol (size 4 / 8 / 12 / 16: id validity / token gate / too short).** e4b: 100 % / 96 % / 0 %; 100 % / 100 % / 4 %; 100 % / 100 % / 4 %; 100 % / 100 % / 0 %. 26b: 100 % / 100 % / 0 %; 100 % / 100 % / 0 %; 100 % / 100 % / 0 %; 100 % / 100 % / 0 %.

**Real-run corpus (`--suite realrun`, pass rate per kind and call; `+Nk` = known failures reported beside the rate).**

| kind / call | e4b | 26b |
|---|---|---|
| `quotes/draft` | 100% (9/9) | 89% (8/9) |
| `quotes/review` | 90% (9/10) | 80% (8/10) |
| `mixed-script/draft` | 100% (6/6) | 100% (6/6) |
| `mixed-script/review` | 67% (4/6) | 83% (5/6) |
| `invented-word/review` | 0% (0/3) | 100% (3/3) |
| `russian-letters/review` | 0% (0/5) | 20% (1/5) |
| `narrator/draft` | 100% (5/5) | 100% (5/5) |
| `narrator/review` | 83% (5/6) | 83% (5/6) |
| `short-line/draft` | 100% (0/0) +6k | 100% (0/0) +6k |
| `short-line/review` | 100% (0/0) +6k | 100% (0/0) +6k |
| `reviewer-batch/review-batch` | 100% (19/19) | 95% (18/19) |
| `reviewer-long/review-batch` | 92% (11/12) | 92% (11/12) |
| `batch-terms/batch` | 100% (1/1) | 100% (1/1) |
| `batch-context/batch` | 100% (1/1) | 100% (1/1) |
| `repair/repair-placeholder` | 50% (2/4) | 100% (4/4) |
| `repair/repair-structural` | 100% (2/2) | 100% (2/2) |

**Sequence eval (`--suite sequence`, 328 paragraphs, narrator unset | set).**

| metric | e4b unset | e4b set | 26b unset | 26b set |
|---|---:|---:|---:|---:|
| Flagged of 328 | 19 | 15 | 12 | 18 |
| Flagged with no stored target (exported as source) | 2 | 2 | 7 | 11 |
| Hard-gate failures at round 0 | 12 | 16 | 36 | 31 |
| Narrator gender slips (first-person chapters) | 1 | 0 | 16 | 0 |
| English leftovers | 0 | 0 | 0 | 0 |
| Blocking quote failures | 0 | 0 | 0 | 0 |
| Leaked protocol text | 0 | 0 | 0 | 0 |
| Truncated reviewer replies | 0 | 0 | 0 | 0 |
| Wrongly shared learned renderings | 0 | 0 | 0 | 0 |
| Dominant rendering share (mean over terms) | 0.87 | 0.87 | 0.92 | 0.92 |
| Distinct renderings per term (mean) | 1.80 | 1.80 | 1.60 | 1.50 |
| Name spelling variants (sum over names) | 3 | 2 | 2 | 1 |
| Batch fallback rate | 0.01 | 0.01 | 0.07 | 0.05 |
| Calls per segment | 0.35 | 0.36 | 0.62 | 0.48 |
| Seconds per segment | 3.8 | 4.0 | 6.3 | 5.6 |

Reading it: the sequence eval reproduces the run's gate and consistency problems (flagged segments exported without a
target, round-0 hard-gate failures, magician/pentacle/sir/boy splits, name spelling variants) and the narrator-gender
slips that appear only on 26b with the narrator unset (16 against 0 with it set). It does not reproduce leaked protocol
text or truncated reviewer replies at this size; the realrun corpus carries those shapes.

## Quality round 2: after the Phase 2 fixes (15e.5–15e.12) {#15e-after2}

Same eval, models and window as the "before" table above; each cell is before → **after**. The fixes: keep the usable
draft and repair quotes instead of exporting English (15e.5), leaked protocol text (15e.6), reviewer truncation (15e.7),
compact-line length (15e.8), name-missing precision (15e.9), honest counts (15e.10), target-alphabet check and key-term
noise (15e.11), lexicon precision (15e.12).

| metric | e4b unset | e4b set | 26b unset | 26b set |
|---|---:|---:|---:|---:|
| Flagged of 328 | 19 → **17** | 15 → **12** | 12 → **7** | 18 → **4** |
| Flagged with no stored target | 2 → **0** | 2 → **0** | 7 → **0** | 11 → **0** |
| Hard-gate failures at round 0 | 12 → **8** | 16 → **8** | 36 → **5** | 31 → **4** |
| Narrator gender slips | 1 → **0** | 0 → **0** | 16 → **8** | 0 → **0** |
| Dominant rendering share | 0.87 → **0.89** | 0.87 → **0.87** | 0.92 → **0.90** | 0.92 → **0.90** |
| Distinct renderings per term | 1.8 → **2.1** | 1.8 → **1.9** | 1.6 → **1.6** | 1.5 → **1.6** |
| Name spelling variants | 3 → **3** | 2 → **3** | 2 → **2** | 1 → **2** |
| Batch fallback rate | 0.01 → **0.02** | 0.01 → **0.01** | 0.07 → **0.07** | 0.05 → **0.09** |
| Calls per segment | 0.35 → **0.35** | 0.36 → **0.33** | 0.62 → **0.44** | 0.48 → **0.54** |
| Seconds per segment | 3.8 → **3.7** | 4.0 → **3.6** | 6.3 → **5.0** | 5.6 → **5.2** |

Reading it: segments flagged without a stored target (the 23 English paragraphs of the 6-hour run) are 0 in every run;
first-round hard-gate failures fall by 33–86 %; leaked protocol text and truncated reviewer replies are 0. Gender slips
with the narrator unset are the open problem on 26b (8; 0 when the brief names the narrator), and recurring terms still
split (dominant share 0.89–0.91, 1.6–2.1 renderings per term): the next task, prompting for consistency (15e.13/15e.14).

| realrun kind / call | e4b | 26b |
|---|---|---|
| `short-line/draft` | 100 % (6/6) | 100 % (6/6) |
| `short-line/review` | 33 % (2/6) | 50 % (3/6) |
| `russian-letters/draft` | 100 % (3/3) | 100 % (3/3) |
| `russian-letters/review` | 40 % (2/5) | 80 % (4/5) |
| `invented-word/review` | 0 % (0/3) | 100 % (3/3) |
| `batch-terms/batch` | 100 % (1/1) | 100 % (1/1) |
| `reviewer-long/review-batch` | 92 % (11/12) | 92 % (11/12) |
| `repair/repair-placeholder` | 50 % (2/4) | 100 % (4/4) |

## Quality round 3: prompting for consistency (15e.13) {#15e-after3}

Same eval, window and dial as the tables above. Each lever was one change, one e4b sequence run (narrator unset, 20 min);
the e4b realrun is deterministic (the same 17 groups, reviewer batches ±1 case) so one run decides it. The sequence eval
is not: three identical runs of the baseline gave 4.3 / 4.6 / 5.2 % flagged, 3 / 6 / 8 first-round hard-gate failures,
88.2 / 88.1 / 89.4 % dominant share and 1.9 / 1.9 / 2.1 renderings per term, so a move counts only beyond about 1.5
points of dominant share, 0.2 renderings per term, 2 points of flagged and 3 hard-gate failures.

| round | lever | e4b unset: flagged / hard0 / renderings per term / name variants / dominant / fallbacks | decision |
|---|---|---|---|
| 0 | baseline (twice, today's code) | 4.3, 4.6 % / 3, 6 / 1.90, 1.90 / 3, 3 / 88.2, 88.1 % / 1.8, 1.5 % | |
| 1 | the first rendering stays (`retained`) and a name with no target learns its spelling | 3.0 % / 4 / 1.80 / 2 / 87.9 % / 2.1 % | kept; `Ms → коли` (a function word) learned, so round 3 |
| 2 | a rendering must not be overtaken by an unrelated word; a name's competitors count every case | 3.0 % / 0 / 1.60 / 1 / 90.2 % / 2.4 % | kept |
| 3 | stop words of the target language are never a rendering | 5.8 % / 8 / 1.70 / 2 / 90.6 % / 1.5 % | kept (noise in flagged and hard0; `коли` gone) |
| 4 | term blocks (glossary, suggested, established) moved next to the text, "exactly as shown" | 2.7 % / 4 / 1.60 / 1 / 89.5 % / 0.6 % | **rejected** after the 26b check below |
| 5 | a title is read with its capitals and a name never takes a word that renders a term (`Lovelace → Міс`) | 5.5 % / 2 / 1.90 / 1 / 87.0 % / 0.6 % (with round 4) | kept (the wrong suggestion is gone; dominant share is noise on `magician`, 30/30) |
| 6 | reviewer: no noun swap for gender, a clean agreement example instead of the idiom | realrun unchanged on every group | rejected |
| final | rounds 1-3 and 5 | 5.5 % / 4 / 1.70 / 2 / 90.6 % / 2.7 % | |

What moved. Names: `Bartimaeus` has one spelling in 6 of 7 runs where the baseline split it 25/13 and 24/21 (the learned
spelling is stored as a suggestion and the model follows it in 50-56 of 56 segments); name variants fall from 3 to 1-2 on e4b and
from 2 to 0-1 on 26b. Terms the model renders one way first (`master`, `Mr`, `Mrs`, `Ms`, `sir`) stay at 100 % — before,
`sir` was 77-100 % and `master` 98 % because the hint came and went. The learner no longer learns `коли` for `Ms` (rounds
1-2 did; round 3 stopped it), and a title before a recurring surname is never that name's spelling (`Lovelace → Міс`, found in round 4).
What did not move: terms the model splits from the first occurrence (`magician` маг/чарівник 47-64 %, `pentacle`
пентакл/пентаграма 52-67 %) never reach three matching segments before the rival does, so there is nothing to keep; the
only ways left were the model's own `terms` report (e4b does not give it; retired by arm 5b, task 12.9) or choosing a winner with no evidence, which
would also pick a sense of a word with two meanings.

Rejected: moving the term blocks next to the text (round 4). On e4b it cut batch fallbacks from 1.5-2.4 % to 0.6 % in
three runs; on 26b, one run each, it raised them from 9.7 % to 17.5 % (50 batches with a missing id against 26), calls per
segment 0.51 to 0.77 and seconds per segment 5.7 to 6.9. 26b is the model of the owner's real run, so the layout stays
as it was.

| 26b unset | before (15e-after2) | after |
|---|---:|---:|
| Flagged of 328 | 7 | 6 (1.8 %) |
| Hard-gate failures at round 0 | 5 | 9 |
| Narrator gender slips | 8 | 10 (random, 0-16 between identical runs; 0 only when the brief names the narrator; 26b with the narrator detected, i.e. the Start question answered, was not measured) |
| Dominant share / renderings per term | 0.90 / 1.6 | 0.91 / 1.7 |
| Name spelling variants | 2 | 0 |
| Batch fallback rate | 0.07 | 0.10 |
| Calls / seconds per segment | 0.44 / 5.0 | 0.51 / 5.7 |

e4b with the narrator detected (final code): flagged 3.4 %, gender slips 0, dominant share 90.8 %, 1.8 renderings per term, 3 names with a stray spelling (`Bartimaeus` 54 of 56 in one, `Whitlock`, `Harrowgate`), fallbacks 1.8 %, 3.56 s per segment. Not measured in this round (time): e4b with the narrator set, 26b with the narrator detected. The realrun table is the same
as above on e4b and 26b: the prompt levers did not touch the reviewer's weak groups. Two of them are corpus artefacts:
the `short-line` "good" lines are compact translations that leave words out, so a reviewer that adds them is right, and
`russian-letters`/`mixed-script` good twins draw `gender` edits that swap a noun (`Стара брама → Старий замок`), which
a model of e4b's size makes whatever the prompt says.

## Quality round 2: final numbers {#15e-final}

Same eval, models and window as the tables above (Ollama native, Balanced, 328-paragraph sequence fixture). Each cell is
the "before" table → after Phase 2 → final (after prompting rounds, the narrator question and the review fixes, commit
2b852b0). The sequence eval has run-to-run noise (about ±1.5 points of dominant share, ±3 first-round hard-gate failures,
flagged 12–20 on e4b between identical runs), so read single cells as ranges.

| metric (sequence) | e4b, narrator unset | 26b, narrator unset | 26b, narrator detected (Start answer male) |
|---|---:|---:|---:|
| Flagged of 328 | 19 → 17 → 20 | 12 → 7 → — | — → — → **4** |
| Flagged with no stored target (English in the book) | 2 → 0 → **0** | 7 → 0 → — | 0 (after Phase 2 set mode) → **0** |
| Hard-gate failures at round 0 | 12 → 8 → **0** | 36 → 5 → — | — → — → **2** |
| Narrator gender slips | 1 → 0 → 0 | 16 → 8 → — | — → — → **0** |
| Dominant rendering share | 0.87 → 0.89 → 0.87 | 0.92 → 0.90 → — | **0.92** |
| Name spelling variants | 3 → 3 → **1** | 2 → 2 → — | **0** |
| Leaked protocol text / truncated reviewer replies | 0 / 0 | 0 / 0 | 0 / 1 (re-asked, nothing flagged) |
| Calls / seconds per segment | 0.35 / 3.8 → 0.34 / 3.75 | 0.62 / 6.3 → 0.44 / 5.0 | 0.54 / 5.7 |

Real-run corpus (final, no regression against the "after Phase 2" cells): quotes/draft 100 % e4b, 89 % 26b;
russian-letters/draft 100 % both (the target-alphabet check fixes them in the run); leaked 0 %, too short 0 %,
truncated 0, stability 100 % / 98.5 %.

What is still open, measured: `magician` and `pentacle` stay split (50–65 % one rendering) because the model splits them
from the first occurrence; the reviewer edits clean short lines (short-line/review 33 % e4b, 50 % 26b), misses invented
words on e4b (0 %), and 26b batches still fall back on missing ids (10 %). Speed work (a larger window and
re-asking only missing ids, task 15e.15) is not started.

## Quality round 3: the Burning Chrome review (15f) {#15f-results}

The owner's 26b run of *Burning Chrome* (Balanced, 21 min, 229 segments) showed a dropped last sentence, a lost vocative name, 10 false "suspicious" audits of 11 and a consistency pass that changed nothing. After 15f.A0–A9, the same book on `gemma4:e4b-mlx` (Ollama, 16k window), measured by `build/…/run1` and `run2` of one session:

| | 26b, before (owner) | e4b, Balanced | e4b, Max |
|---|---|---|---|
| time | 21 min | 14 min 53 s | 27 min 29 s |
| accepted / flagged | 228 / 1 | 228 / 1 | 225 / 4 |
| suspicious (audit) | 11 (10 false) | 1 | – |
| "We were partners." (ch4 p14) | missing | present | – |
| vocative *Finn* (ch4 p26) | missing | present | – |
| paragraphs ending `.»` (wrong attribution) | 22 of 217 | 1 of 206 | – |
| consistency pass, paragraphs corrected | 0 (byte-identical export) | – | 35 of 91 checked |

The models differ, so the time column compares nothing about speed; it shows the new run costs no more than the old one. What the Max run's 35 corrections were, read from the TRACE log: narrator gender (шукала, знайшла, щасливий), a wrong ending (дивитеся → дивишся), a lower-case sentence start, a doubled full stop after a closing quote — and one doubtful swap (база даних → вежа даних). Not measured: the 26b with the new checks, the Russian→Ukrainian book, and the speed effect of the 16k window alone (15e.15 asks for a first-300 A/B).

## Time to first token {#time-to-first-token}

The time before a call's first token (the provider's prompt evaluation, "prefill") is read from the provider's own
timings: Ollama's `prompt_eval_duration`, LM Studio's `stats.time_to_first_token`. A provider that sends neither leaves
the figure unknown (`–`), never zero; the same dash stands for any token or time figure no call of that kind reported. Read it in the final `run summary final ... byKind[...]` line of the log, per call
kind: `ttftMs:` is the mean over the calls that reported it and `prefill:` is its share of those calls' wall time. The
unattended command line's JSON report carries the same as `meanFirstTokenSeconds` and `prefillShare` under
`modelCallsByKind`.

The 10 % rule (15h.C5) was met: the owner's 26b Max run (2026-10-16) spent 41–60 % of each kind's call time in
prefill. `cached:` is the prompt tokens the provider served from its cache: Ollama's `prompt_eval_cached_count` (read
since 2026-10-17; before, the run summary said `cached:0` while Ollama's own server log showed 57 % of that run's prompt
tokens and 49 % of its export pass cached), LM Studio's `usage.prompt_tokens_details.cached_tokens`. On Ollama's MLX
runner `prompt_eval_count` is the whole prompt and `prompt_eval_duration` only its uncached part.

The reorder was tried and reverted (2026-10-17). Moving the draft and batch system prompts' token rules (present only
when a segment has `⟦gN⟧` tokens) to their end let two no-token drafts share the system prompt on `gemma4:e4b-mlx`
(478 of 579 tokens cached, prefill 1,091 → 263 ms; a repeat of the same case is served whole either way, 806 of 810),
but the first call after a with-token draft reused nothing, because Ollama's MLX runner caches a branch point only from
the second call that shares it — and the sequence suite lost token fidelity: batch items that dropped a `⟦gN⟧` token
rose from 4 to 16 and batch fallbacks from 15 to 29 against the same tree without the reorder. On e4b the rules work
better before the style sheet, so they stay there; prompt layout for the cache moves to `redesign-translation-pipeline`,
whose one context builder decides the order of every call's blocks.
