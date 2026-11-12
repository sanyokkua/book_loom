# Results: prompt rounds 15h to 15j

*Measurements of the 15h to 15j prompt rounds on e4b and 26b.*

## 15i baselines: the 15h prompt round and the book-neutral re-baseline {#15i-baselines}

All rounds 2026-10-12/13 on Ollama `gemma4:e4b-mlx` and `gemma4:26b-mlx`, one run per arm, Balanced, the suites of
`scripts/eval-matrix.sh`; 1-case moves are within noise.

**15h.C1–C4 (one combined round, before e8247cf0 → after 28e34e45).** realrun (172 shared cases): e4b 135 → 138, 26b
144 → 148. Default set: e4b script −1, injection −1 of 2; 26b gate −1, script −1, marker +1. Sequence (328
paragraphs): draft prompt tokens −9 % (e4b) and −10 % (26b), calls per segment 0.372 → 0.363 and 0.643 → 0.607, 26b
directed fixes 47 → 37. Kept: the target moved +3/+4 and no key metric lost 2+. The four changes were measured
together, so their effects are not separable.

**15i.E0 re-baseline (28e34e45 → cceac266: invented names in the prompts' examples, and E1–E4's test material).**
realrun on the shared cases: e4b 138 → 136, 26b 148 → 146 (slang, vocative/review and one placeholder repair −1 each).
Sequence: e4b name variants 3 → 1, seconds per segment 3.86 → 4.17; 26b calls per segment 0.607 → 0.457, flagged 7 → 9.
These are the baselines every later 15i prompt change is measured against; the drop is the cost of examples that no
longer resemble the books the suites were built from.

**15i.E1 names baseline (25cd1699)** is the "base15i" row of the names gate (`#15i-names-gate`).

## 15i brief prediction {#15i-brief}

The 15i.B4 gate round (2026-10-13, Ollama, `gemma4:e4b-mlx` and `gemma4:26b-mlx`, 16k window, server sampling) ran
`scripts/eval-matrix.sh --suite setup|stability|gold` with `BOOKLOOM_EVAL_BOOK=all`: **setup** is the 15 committed
setup cases (8 invented title/author pairs, 4 public-domain classics, a book with no metadata and two plain cases);
**books** is the 9 committed judgement books (stability suite, seed 1 of 3) plus the owner's four gold books ("owner
book 1–4", `BOOKLOOM_CORPUS_DIR`, Fast dial as every judgement round before). A cell is cases right of cases scored.
The baseline is 15h.G1's two samples of three windows (`25cd1699`); "after B4" is the merged 15i.B2–B4 code
(`166f44f9`) before this round.

| model | arm | setup genre | setup register | setup narrator | knowledge: invented unknown | knowledge: classics known | books genre | books register | books narrator | books narrator gender | genre of the six |
|---|---|---|---|---|---|---|---|---|---|---|---|
| e4b | baseline | 1/15 | 9/15 | 13/15 | 11/11 | 4/4 | 0/13 | 12/13 | 11/13 | 8/13 | 0/6 |
| e4b | after B4 | 3/15 | 11/15 | 14/15 | 11/11 | 4/4 | 1/13 | 7/13 | 11/13 | 9/13 | 1/6 |
| e4b | round 1 | 5/15 | 10/15 | 15/15 | 11/11 | 4/4 | 1/13 | 6/13 | 13/13 | 9/13 | 1/6 |
| e4b | round 2 | 7/15 | 10/15 | 15/15 | 11/11 | 4/4 | 3/13 | 13/13 | 13/13 | 9/13 | 2/6 |
| 26b | baseline | 1/15 | 9/15 | 15/15 | 11/11 | 4/4 | 0/13 | 12/13 | 11/13 | 9/13 | 0/6 |
| 26b | after B4 | 7/15 | 7/15 | 14/15 | 11/11 | 4/4 | 0/13 | 13/13 | 11/13 | 9/13 | 0/6 |
| 26b | round 1 | 8/15 | 11/15 | 15/15 | 11/11 | 4/4 | 6/13 | 8/13 | 13/13 | 9/13 | 4/6 |
| 26b | round 2 | 8/15 | 11/15 | 15/15 | 11/11 | 4/4 | 6/13 | 12/13 | 13/13 | 9/13 | 4/6 |

"Knowledge: invented unknown" counts the 8 invented books and the three other cases that must stay unknown; the 15i.B2
gate (unknown on 7 of 8 invented books, the 4 classics known) holds on both models in every arm. "Genre of the six" is
the gate's set: the four owner books and the two judgement books whose gold genre is itself one of the 40 names
(`female-narrator-address`, a gothic novel, and `straight-quoted-speech`, an adventure); the other seven judgement
books are short invented vignettes whose gold ("rural drama", "pastoral drama", "historical drama") no listed genre
name says, so they are scored but not part of the gate.

What each round changed, and why (from the TRACE logs of the earlier round):

- **Round 1.** (a) Knowledge title: a store's title carries the series ("… (Series Volume 1) (Series Trilogy)") or an
  edition word (", Complete"); 26b echoed the bare title and the answer was refused as `title-mismatch` on owner books
  1 and 4. A title of two words or more that is the whole-word start of the other now matches. (b) Narrator: the
  detector calls a book `MIXED` when any judged chapter is first person, and the brief read every mixed book as first
  person; a third-person novel with a first-person preface (1 of 40 chapters) or a few characters' written accounts (5
  of 24) became `FIRST`. A mixed book is now first person only when at least a quarter of its judged chapters are (first set at a half, which a fixture of 3 chapters in 8 failed)
  (`NarratorHint.judgedChapters`). (c) Genre and audience: 26b left their `evidence` empty ("empty for a neutral value"),
  so every window's genre and audience were refused and the synthesis had nothing to decide from; the prompt now says
  a genre or an audience is never neutral and needs the passage's own words. Genre on 26b 0 → 6 of 13; but register on
  26b fell 13 → 8, because the windows now quoted characters' speech ("Close the gate…", "…ma'am?") as `casual`.
- **Round 2.** Register is the narration's: the prompt says so and names what makes narration formal or casual, and
  `BriefReplies` keeps a register reading only when its quote stands outside the characters' speech
  (`BookSample.holdsInNarration`). e4b had called plain narration "formal" on seven books (books register 6 → 13); 26b
  books register 8 → 12. The genre and audience evidence may not be the title or the author (e4b quoted the metadata's
  title, which the code refused): e4b genre 1 → 3 of 13 books, 5 → 7 setup cases.

No third round was needed: against the baseline no field lost a case on either model, and every target moved by 2+.

Time per suggestion (first knowledge call to the decided brief, from the logs, 26b, owner books 1–4): Fast 37–51 s
(mean 42 s); **Balanced (`BOOKLOOM_EVAL_DIAL=BALANCED`, 4 windows of 2.5k tokens) 59–73 s (mean 64 s)**, under the 90 s
gate; the Balanced run also kept genre, register and narrator right on all four owner books. The narrator's gender stays
unknown on owner books 1 and 2, whose gold is male: no window found a line where someone names or addresses the
narrator that the code could verify, which is the intended refusal, not a miss to tune away.

## 15i translation rounds: T2, T3 and T6 {#15i-translation-rounds}

Rounds on `gemma4:e4b-mlx` and `gemma4:26b-mlx` (Ollama, server sampling, Balanced), one run per suite, realrun at stability 3;
postE0 is `cceac266`, postT adds 15i.T2 and T3 (`a7259e79`), postT6 adds T6 (`fe3fc381`).

| Round | Model | realrun right | replay pass | sequence calls/seg | s/seg | hard-gate fails r0 | LTCR-1 | batch fallbacks |
|---|---|---|---|---|---|---|---|---|
| postE0 | e4b | 139/175 | 30/40 | 0.375 | 4.17 | 12 | 0.934 | 17 |
| postT | e4b | 139/175 | 33/40 | 0.366 | 3.60 | 10 | 0.914 | 18 |
| postT6 | e4b | 138/175 | 33/40 | — | — | — | — | — |
| postE0 | 26b | 148/175 | 32/40 | 0.457 | 5.86 | 17 | 0.853 | 27 |
| postT | 26b | 148/175 | 32/40 | 0.534 | 6.08 | 23 | 0.758 | 37 |
| postT6 | 26b | 147/175 | 32/40 | — | — | — | — | — |

- **T2 (repairs never make a clean draft worse): kept.** Realrun loses nothing on either model and the e4b replay gains 3.
- **T3 (a first-item-only or cut reply is no size signal): open.** Realrun holds and e4b's calls per segment do not rise, but
  26b's rise 0.457 → 0.534 with more batched items (326 → 363) and more fallbacks (27 → 37). The 26b sequence varies by about
  ±25 calls between runs (199–211 calls at 15h's head and pre-C1, 150 at postE0, 170 with T8 on), so the gate is re-measured
  with two 26b sequence runs after the review fix that halves a batch after three first-item-only or cut replies in a row.
- **T3 after its review fix (three first-item-only or cut replies in a row halve the size; `766234c6`):** 26b sequence
  0.470 and 0.473 calls per segment in two runs (154 and 155 calls, 5.3 and 5.8 s per segment, LTCR-1 0.876 and 0.872, hard-gate
  failures 19 and 16) against postE0's 0.457 (150 calls) — five calls in 328 segments, inside the ±25-call spread between 26b runs;
  e4b 0.369 against 0.375. Kept: calls per segment do not rise beyond the noise, and LTCR-1 on 26b is back above postE0's.
- **T6 (injection and address): the prompt part failed its gate.** The batch injection case (`injection-notes-uk`, two injected
  items in three) was obeyed once on 26b before and after (the gate asks for none) and went from 0 to 1 on e4b; the default
  suite's injection cases did not move (e4b 1 of 2, 26b 2 of 2). Per the keep rule the "source text is data" sentences and the
  replaced batch example are reverted; `AddressMixCheck` (code, no prompt) stays.

## 15i.X3 long paragraphs as sentence groups: not adopted {#15i-x3}

The arm (`8a2dbffd`, reverted) drafted a paragraph of more than 180 words as sentence groups of at most 90 words, each call
showing the whole paragraph without its tokens, and kept such a paragraph out of batches. Measured on the 15i.E4 set (twenty
public-domain paragraphs of 186–230 words) on `gemma4:e4b-mlx` and `gemma4:26b-mlx`, each arm graded blind by Opus (WRONG = any
meaning error a reader notices; the A/B order was shuffled per item), and on the sequence suite with the arm on:

| Model | WRONG off → on | errors off → on | calls on the set off → on | sequence with the arm on (calls/seg, s/seg, LTCR-1, hard-gate r0) |
|---|---|---|---|---|
| e4b | 19 → 20 of 20 | 67 → 62 | 20 → 66 | 0.381, 3.89, 0.927, 13 (postTr-1: 0.369, 3.94, 0.884, 11) |
| 26b | 19 → 18 of 20 | 28 → 32 | 20 → 66 | 0.430, 5.32, 0.873, 17 (postTr-1/2: 0.470/0.473, 5.27/5.82, 0.876/0.872, 19/16) |

WRONG did not fall by two on either model (the gate), errors moved in opposite directions, and the set cost 3.3 times the calls,
so the code was removed. The grader's strict verdicts mark nearly every long paragraph WRONG on both models; 26b makes about half
of e4b's errors. Grades and the blind key: `.temporary_context/eval-artifacts/eval_2026-10-12/x3grade/` (owner-local).

## 15i.X2 thinking and notes per call kind: all off {#15i-x2}

Screened on `gemma4:e4b-mlx` only (the owner's rule: an arm that does not improve e4b gets no 26b run), every arm against a
baseline of the same tree (`55f4e223`), one run per suite. A call kind turns on only if its target moves by 2+ with its time up
by at most 30 %.

| Arm | realrun right | sequence s/seg | LTCR-1 | flagged | hard-gate r0 | coref right / wrong / abstain | Verdict |
|---|---|---|---|---|---|---|---|
| base | 142/179 | 3.99 | 0.908 | 9 | 10 | 8 / 2 / 21 | — |
| draft batch thinks | 142/179 | 8.82 (+121 %) | 0.886 | 10 | 14 | — | off: no gain, twice the time |
| batch notes | 143/179 | 4.20 (+5 %) | 0.893 | 10 | 10 | — | off: +1, the rule asks +2 |
| reviewer thinks | — | 14.01 (+251 %) | 0.934 | 109 | 12 | — | off: the reviewer runs out of time and 100 more segments are flagged |
| term reading thinks | — | — | — | — | — | 7 / 2 / 22 | off: one right vote fewer |

`brief-synthesis` with thinking on was not run (stopped on the owner's instruction once the other arms had failed); it stays off
with the rest; since redesign task 6.7 every call kind asks for thinking off and the eval switch is gone.

## 15i narrator's gender, in order {#15i-narrator}

The narrator's gender is never taken from a model's word alone (15i.T5, which also closes 15f.A4). In order: (1) the brief's
verified evidence (15i.B4: a name or a word of address the code checks); a quote that calls the narrator by a name nobody knows
yet is kept on `Narrator.evidence` as a *pending* quote, with the gender unknown; (2) `SetupAssistant.recheckNarrator`
(`NarratorRecheck`, no model, no write) checks the pending quotes against the glossary as Names & style left it, with the same
filter as the brief (a name the narrator says to someone else proves nothing), and a glossary character of confirmed gender
(the person's or verified, not a suggestion) makes the narrator that gender, `VERIFIED`; (3) Start runs the re-check only when
it is about to ask "Who narrates?" and stores a verified result through the Book Brief, a failed re-check asks as before; (4)
still unknown when translation ends (`NarratorStage`, after the last chunk, before backward revision): the targets of the
segments whose source narration holds «I» are read with the target language file's past endings and first-person pronouns
(`PastTenseReader`, redesign 7.4; it replaced the Ukrainian-only `firstPersonPast` regex) outside quotes and dialogue; with at least 20 such segments, 8 forms and 80 % of
them (inclusive) agreeing the narrator is stored as first person with the origin `INFERRED`, logged at INFO (forms, agreeing,
gender), and each segment holding a form of the other gender gets a `GENDER_UNKNOWN` deferral waiting on
`DeferralRegister.NARRATOR`, which `GenderRevision` re-renders against the narrator at Max (the stored narrator is read when
the pass starts). A merely `SUGGESTED` gender is replaced by an inferred one, since the decided targets are stronger evidence
than an unverified guess; a person-set, verified, inferred or third-person narrator never is. The style sheet states a
person-set, verified or inferred gender and orders its form; a suggested one reads "likely male/female" and orders none.
The count runs once, after translation: a gender found during a run reaches the checks and the drafts of the *next* run
(the run's call frame is built once), and the window's brief is refreshed by the next Start, whose re-check returns the stored
narrator.

## 15i.M1 dial budgets measured {#15i-m1}

The names pass (the `names` suite, `scripts/eval-matrix.sh`) on `gemma4:e4b-mlx` at tree 41642a45, 2026-10-15, against the
minutes each dial promises. The owner rule screens on e4b only.

| Book | Fast (target 5–10 min) | Balanced (15–25) | Max (30–60) |
|---|---|---|---|
| owner novel | 10.4 min, 136 calls | 23.1 min, 257 calls | about 54 min (from the log; the run hit the eval's 90-minute limit later) |
| Tom Sawyer (public domain, about 70k words) | 6.9 min, 111 calls | 19.3 min, 250 calls | not reached |
| DDIA (technical) | 26.2 min, 202 calls | 40.5 min, 392 calls | over 31 min, cut by the time limit |

The novels fit Fast and Balanced at about 4.6 seconds a call (3.7 to 5.4); the technical book misses both because it has
more candidate terms and its calls take 6 to 8 seconds. `NamesPlan` now holds a call budget per dial, taken from the
table: a pass of 120 calls at Fast, 260 at Balanced and 520 at Max (6 to 10, 16 to 25 and 31 to 60 minutes at the pace
constants `NamesPlan` carries). The names reading may use 60 % of it (`readCallCap`: 72, 156 and 312 calls) and reads the
most mentioned terms first, so a book with many candidates is read for fewer of them; the rest of the budget is left to the
batched review, recurring terms and renderings, whose share was not measured by kind and is an assumption to check with the
26b round. The old cap of 80 terms is gone: it was a cap on terms, not on calls, and Max reads two calls a term.

Open finding for N7, not fixed here: the recurring terms kept were fewer at Balanced than at Fast (owner novel 3 of 23
against 11 of 23, Tom Sawyer 1 of 26 against 10 of 26).

Pending owner: the 26b closing round of these measurements and 15h.F2.

## 15j results: closing the change on e4b, then 26b once {#15j-results}

Measured 2026-10-17/18 on the final tree against the `0a587148` baseline (the tree before 15j), e4b first and 26b once,
with the fast loop (`#testing`, "The fast loop"); the numbers below are the record (the per-run reports were not kept).

| Suite (e4b) | Baseline | Final | Note |
|---|---|---|---|
| mini (28 segments, 50 expectations) | 46/50 | 46/50 | 1 min 51 s; misses: an obeyed injection, two names, one place |
| default | parse 98 %, gate 100 % | parse 100 %, gate 97 % | script, marker, injection, reviewer separation unchanged |
| realrun | — | identical on every column | |
| sequence, narrator unset | flag 1.8 %, LTCR-1 89.5 % | flag 2.7 %, LTCR-1 90.1 % | first-round blocking findings 4 → 13, all the alphabet check (15j.J5), so calls rose 51 → 119 |
| coref, setup | — | identical | |
| names, 3 owner books, Balanced | terms kept 3/23, 0/13, 0/21; lost 4, 3, 2 | 9/23, 7/13, 2/21; lost 2, 3, 2 | 0 wrong verified genders, junk unchanged |
| batch 4 / 8 (json) | — | tokens kept 96 % / 83 % | small sample; the default stays 8 |

On 26b once: mini as on e4b; realrun with no truncation or leak; sequence LTCR-1 78.7 %; names terms kept 11/23, 5/13,
2/21 (level with the earlier 26b runs), 0 wrong verified genders; batch 4 / 8 both keep every token.

The owner's book on e4b (Burning Chrome through `--suite book`, every decision the model's): Balanced 26.6 min, Max 48.2
min; 11 Latin words left in the story text at Balanced and 10 at Max, against 35 in the pre-15i e4b run and 35 in the
Oct 15 26b run; no word with a Russian letter (the Oct 15 run accepted one); brands and materials Cyrillic. Blind Opus
grading, 50 paragraphs each: against the pre-15i e4b run 15 WRONG and 68 errors against 16 and 80, preferred 20 to 17;
Max against Balanced 13 WRONG and 58 errors against 15 and 71, preferred 28 to 15 — about a fifth fewer errors at 1.8
times the time. Two things the run shows that this change does not fix: the predicted brief left the narrator's gender
unknown at Balanced, and two characters' genders stayed unknown, so 123 segments wait on a gender (15j.J3's Confirm
helps where a gender is suggested); both belong to the redesign's model-knowledge step.

Tried and reverted: the token rules at the end of the draft and batch system prompts (`#time-to-first-token`).


## Group 7 results {#group-7-results}

What group 7 (`lemma-book-map`) of `redesign-translation-pipeline` measured, all on invented data: arm 4a (what a model knows
about a bare name: 0 wrong of the confident answers on `gemma4:e4b-mlx` and `gemma4:26b-mlx`, no unisex name answered at 0.7 or
more, so likely values stay in the prompts) and arm 4b (a `male (likely)` hint on the character line against the name alone: 0
gender-agreement errors on both sides, so the name alone stays) are recorded under `#redesign-arms`, with the `bare-names` dataset
and the mini books named there. The estimate and speed figures are unit-tested rules, not measurements; the hand check of 7.10
(a short invented Russian book on `gemma4:e4b-mlx`) is recorded with the task.

## Thinking per call and the notes arm (15i.X2) {#x2-arms}

Published studies find that thinking does not help translation itself, so every call asks the model not to think, as it
always did. Since redesign task 6.7 no switch changes that: `BOOKLOOM_REASONING` and its property are gone, and each
model's thinking is decided per call kind from its profile and the person's choice on Settings › Models (design D20) —
Recommended (off), Allow on proofreading and names, or Always. A model that cannot stop thinking gets no control and a
reasoning reserve of `max(2048, 4·S + 35·n)` tokens on its cap for a source of S tokens in n items. The notes
arm's switch (a one-line `notes` string before each batch `target`, read only to be logged) was removed in redesign task
2.3 with the related-pairs switch (15i.T8, off since its 2026-10-13 round missed its gate) and the batch-format switch; a
reply that still carries a `notes` field is read as before, the field ignored. The report's provenance records
`reasoning` as `off` (reports made before task 2.3 also carry a `batchNotes` field). The arms measured below ran with
the retired switch.

Compare each arm with the same suite without the switch: a call kind turns on only where its target moves by 2+ with its
time up by at most 30 %. The measured table goes here when the round is run. The live request shape of both dialects is
checked by `chat_thinkingOn_isAcceptedAndStillAnswers` in `OllamaLiveTest` and `LmStudioLiveTest` (`./gradlew liveLocal`).
