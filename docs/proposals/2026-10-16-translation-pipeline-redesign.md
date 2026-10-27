# Proposal: re-centre the translation pipeline on prepare → translate → proofread

Status: proposed (owner review pending) · 2026-10-16, evidence extended 2026-10-20 · evidence (owner-local):
`.temporary_context/new_translation_3/ANALYSIS.md` (two gemma4 26b runs, English sources) and the Russian → Ukrainian run
of 2026-10-18 in the same folder (`За мільярд років до кінця світу`, its run log
`~/Library/Logs/BookLoom-Dev/bookloom-trace-run-20261018-103640.log`), reviewed below.

## Why

The pipeline was meant to translate a book almost unattended: the model prepares the book's data, translates chunks that
fit its window with exactly the context those chunks need, and finally re-reads the whole book to make it consistent. The
skeleton is still there, but around it the pipeline grew per-segment repair loops (a reviewer on every chunk, directed
fixes, repair rounds, an in-run backward revision, an export consistency pass), and each loop builds its prompt context in
its own way. Two owner runs on gemma4 26b (Oct 15–16) and a Russian → Ukrainian run on a 12b (Oct 18) show the cost:

- **Context does not follow one rule.** Translation matches names against the item's own text only (names in the previous
  and next pairs it reads get no rendering); the reviewer matches the whole chunk (22–26 % of its names and terms are not in
  the pairs it reviews) and lists unconfirmed targets as mandatory; the consistency check matches the paragraph and its
  neighbours but has no language rules or word filter; a retry replays a single-draft snapshot even for a batch-drafted
  segment; the directed fix gets no names at all.
- **The summary is about the book, not the passage.** It is cumulative (or a code-built list of the most frequent names in
  the whole book), so prompts name people absent from the passage, and the export pass even sees later events.
- **Rules and examples are fixed.** Every call carries the whole language-rule file and the same worked examples; three
  prompts carry English→Ukrainian examples whatever the language pair.
- **Knowledge comes only from the book.** A name with no pronoun nearby stays unknown even when it is plainly a man's name;
  the model is never asked what a name or a place is.
- **Max costs without paying.** The 26b Max run of a 3,784-segment novel took 8 h 51 min plus 2 h 45 min of export pass;
  about half of the model time was prompt reading with nothing cached, and a blind grading found it no more accurate than an
  earlier, much faster translation of the same book.
- **Close language pairs defeat the checks.** On a Russian source into Ukrainian (the first non-English source and the
  first pair sharing a script), the script check is skipped, the echo check never fires and the length ratio is
  meaningless, so whole Russian lines were exported unnoticed. Every guard was designed for an English source.
- **The glossary is a list of word forms, not of names.** Inflected source forms became separate entries with their own
  targets and genders, targets carried stress accents into the book, and wrong targets passed on "accept all"; most
  name findings of that run were false and most real name errors came from the glossary itself.

## Decisions taken (owner, 2026-10-16)

1. **One window rule for every call.** The text a call works on is its window: the previous chunk, the current chunk and
   the next chunk (for a single paragraph: the previous, current and next paragraph). Mapped items — glossary names,
   characters with their genders, recurring terms, locked names, kept foreign runs — are injected when, and only when, they
   occur in the window (whole word, declined forms through the language file). Nothing else is injected.
2. **The summary is the current scene.** A call gets a short summary of the current chapter up to the window (what led to
   this passage), naming only what occurs in the window or that chapter. The book-level summary stays for the brief and for
   the proofread's chapter view, never for a single call.
3. **Model knowledge counts as "likely".** When the book gives no evidence of a character's gender, the model is asked the
   usual gender of the given name; places and other terms are typed by asking the model explicit category questions (a
   person? a city, a river, a country, a region? an organisation, a product, a material?). The answer is used as "likely",
   marked in Names & style, never enforced by the checks, and book evidence always overrides it. A name the model is unsure
   of stays not specified.

## Evidence: Russian → Ukrainian, 12b, Balanced (2026-10-18)

**The run.** A 1977 Russian novel (1,280 segments, one XHTML unit, partly first-person narration, partly third-person
"documents") translated into Ukrainian by `mamaylm-gemma-3-12b-it-v2.0` (a Ukrainian fine-tune of gemma 3 12b) on LM
Studio's OpenAI-compatible endpoint, Balanced, unattended, window 16k; then exported with and without the consistency
pass. Graded against the Russian original: 55 paragraphs read (26 spread evenly, 15 picked from flags, the 14 the pass
changed), plus a deterministic sweep of the whole book. Paragraph ids `pN` are the bilingual file's rows and the
report's ids.

| Phase | Time | Model calls |
|---|---|---|
| Import, provider verification (tildeopen-30b failed twice, mamaylm passed) | 3.5 min | 3 |
| Brief (knowledge, 4 suggestions, synthesis) | 2 min 50 s | 6 |
| Names pass (readings 30 min, term choice 7 min, lexicon 18 min, targets 4 min) | 59 min (planned "12–19 min") | 153 |
| Idle (no log line), then review: accept all 49, 8 edits, genders dialog dismissed | 3 h 20 min + 5 min | 0 |
| Translation run, including a 43 min pause on a provider error | 5 h 56 min | 799 |
| The owner fixing the 28 flagged segments | 25 min | 1 |
| Export with the consistency pass | 24 min 42 s | 219 |

Run summary: 0.62 calls per segment; draft 391 calls (avg 26 s, p95 66 s, about 1.5k tokens in / 260 out), review 161
(avg 32 s, p95 57 s against a 90 s timeout), directed fix 245 (avg 12 s); 301 draft batches of 2–8 segments, 31
halvings (omission 19, wrong id 8, merge 4), 0 timeouts; 28 flagged (glossary findings in 24, omission 10, fluency 4,
gender 1, language 1), all fixed by hand.

**Quality.** A good draft for a 12b local model: narration reads naturally (`не лыком шиты → не пальцем роблені`),
classic russisms (`приймати участь`, `на протязі`, `слідуючий`) never appear, ти/Ви is consistent, vocatives are mostly
right, and the male narrator has no feminine «я + past verb» form. Meaning errors in about 10–12 % of paragraphs (3 of
26 in the even sample), fluency issues in about 30–35 %. The worst defects are made by the pipeline, not the model:

| Defect | Example (source → output → expected) | Cause | Who fixes it |
|---|---|---|---|
| Stress accents in names | `Малянов → Маля́нов`; 973 U+0301 in the book (0 in the source) | 18 of 49 accepted glossary targets carry U+0301; injected into every prompt | Stage A target normalisation; a deterministic output check |
| Declension blocked, false name findings | `Вайнгартена → Вайнгартен` as its own entry; output `Вайнгартеня` ×5, «про Сніговий»; 381 NAME_SWAP warnings and 683 name-loss findings, spans `0..0` | Inflected source forms stored as separate entries with a nominative target | Stage A lemma entries |
| Wrong genders | `Глухова` (genitive of a man's name) female, `Гартвиг` declined as feminine (`функції Гартвіги`), the son `Бобка` unknown → `звалилася` | Gender attached to a word form; pronoun seeding found nothing (`entriesSeeded=0`) | Stage A gender per lemma, per-source-language seeding |
| Invented or unstable renderings | `Валька → Вайлько` ×19; `Фил → Філл/Філ/Фил`; `Мироздание → миробудова` ×19 (not a word; `світобудова`) | Model targets accepted with "accept all", nothing locked | Stage A validation before accept, lock after review |
| Untranslated Russian | whole lines p295 «сказал Малянов тупо», p1144; `черт` ×8 (`чорт`), `очки` (`окуляри`), `Дождик` | Script check skipped for a shared script, no echo signal, ratio useless (1 of 985 long paragraphs outside 0.7–1.5) | Stage B source-residue check |
| Dialogue punctuation | 22 lines lost the leading «—» (6 with an ASCII hyphen), 50 × «,—» with no space | No dash normaliser for this pair | Stage B normaliser |
| Lost numbers | section numbers `3.` (p124), `7.` (p448) dropped | Number check does not see a leading heading number | Stage B check |
| Meaning errors | negation lost (p971 «не может … бросить» → «ніколи б мене так просто кинув»), omission with unchanged length (p493), agreement with unnamed referents (p172 two men → feminine, p1270 «руде спокій») | Model | Judge or proofread; deterministic negation-count hint |
| Allusion, slang, rare words | a Pushkin line broken (p170), `отцы → отці` (`хлопці`), «посів» where «посинів» was meant (p979) | Model ceiling | Stronger reviewer or the owner |
| Title page | escaped `&lt;br/&gt;` shown as text; distributor boilerplate half translated (only link texts segmented) | Document layer | Out of scope here (separate fixes) |

The second author's name is missing from the source OPF itself (a distributor artefact); the pipeline kept what it
was given.

**The consistency pass** changed 14 paragraphs (the report lists 15; p908 is identical): 9 better (gender fixes p555,
p1107, p1166; a swapped name undone p811; Russian removed p1067), 3 worse (p1175 *introduced* Russian «толкает меня на
драку», p1090 introduced `Вайнгартеня` ×4, p1129 replaced a correct patronymic with `Філла`), 2 wrong either way. It
needs a diff gate: a rewrite may not add source-language words or a name form outside the map.

**From the logs.**
- *Preparation is model-bound and badly estimated*: the names pass used 3,535 s of model time in 3,537 s of wall time,
  3–5× its plan line; name readings averaged 43 s per call.
- *Reviewer yield is low*: 803 "ok", 304 "edits", 4 rewrites; of 349 suggested edits 71 were applied, 270 were "not
  found" (the quoted text was not in the draft) and 8 refused; edits labelled "terminology" were often style changes.
- *No timing split on LM Studio*: the OpenAI-compatible reply reports no prompt-eval time, cached tokens or time to
  first token, so `cached:0` means "not reported", and `tokensPerSecond=9.1` divides output tokens by the whole call. A
  fit over 1,177 calls gives roughly 120 tok/s prefill and 15 tok/s decode, so prompt reading is a large share again.
- *Provider errors are misclassified*: LM Studio's HTTP 400 `{"error":"terminated"}` became non-retryable `validation`
  and paused the run for 43 minutes; tildeopen-30b's "Failed to initialize samplers" (a strict `json_schema` request)
  shows in the run log only as `validation`.
- *Prompt examples leak and do not fit the pair*: a brief window returned the prompt's English example sentence word for
  word; the batch example was "generic" for ru → uk.
- *Genders left open*: the Start dialog asked about 21 characters, was dismissed, and 247 segments ended "waiting on a
  character's gender"; the narrator was MIXED (6 of 11 chapters first person) and its gender unknown until the owner
  chose it.
- *Source language* came from the EPUB metadata (`declaredRaw=ru`), with no detection from the text.
- *What the logs could not answer*: the detailed trace rotated away the preparation and the first 2 h 15 min of the run;
  preparation and consistency-pass calls are missing from the by-kind summaries; the provider's error text is only at
  TRACE; nothing records why 3 h 20 min passed idle.

## Proposed design

**Stage A — Prepare** (as today, with knowledge): book index, names pass (book evidence first, model knowledge as
"likely"), recurring terms, the brief. Output: the book map (glossary, lexicon, characters, narrator, style).

**Stage B — Translate**: chunks sized to the window budget, each built by **one `ContextBuilder`** used by every call kind
(draft, review, fix, retry, proofread). The builder takes the window, selects map items found in it, adds the scene
summary, and chooses **rules and examples by what the window contains** (a dialogue rule and example only when there is
dialogue, a number rule when there are numbers, a gender rule when a gendered character or the narrator speaks, an
example in the book's own language pair). The fixed parts (system prompt, style sheet, language-pair rules) go first and
unchanged so the provider's prompt cache can reuse them. Deterministic checks stay as gates; a failing segment gets one
repair, then is marked for the proofread instead of looping.

**Stage C — Proofread**: one whole-book pass with the same builder over windows of consecutive paragraphs: names, terms,
gender and endings agree with the map and with each other, marked segments are re-drafted with their full window,
deterministic checks re-run. This replaces the in-run backward revision and the export consistency pass (one pass, not
two). The quality dial decides how many windows are re-read (flagged only, flagged + doubtful, all), not how many loops run
per segment.

### Amendments from the Russian → Ukrainian run

**Stage A — the map is made of lemmas.**
- A glossary entry is one name (a lemma): the nominative source form, the inflected source forms found in the book as
  its aliases (through the *source* language's file, as aliases already share a base target), one nominative target
  and one gender. A scan that finds `Снегового` and `Снеговым` adds aliases to `Снеговой`, never new entries.
- Targets are normalised before they are stored or injected: combining marks such as U+0301 are stripped, and a target
  that is not a nominative form (a declined form, a different word count) is flagged for review.
- "Accept all" runs the deterministic validators first (target script, stress marks, case, the spelling the book's own
  decided text uses) and accepts only what passes; an accepted name is locked, so the reviewer and the proofread cannot
  respell it.
- Pronoun and verb-ending gender seeding has evidence lists per *source* language (Russian past-tense endings carry
  gender as clearly as English pronouns do).
- Worked examples come in the book's own language pair, and a reply that repeats the prompt's example is rejected.
- The names pass shows an estimate from measured call times (the provider's own speed on the first calls), not a fixed
  guess.

**Stage B — checks that work when source and target share a script.**
- A source-residue check: the share of target words identical to source words in a long paragraph, and a list of words
  that exist only in the source language (from the source language file). Either one triggers the one repair; both are
  silent for pairs in different scripts, where the script check already works.
- A dialogue normaliser for the target's conventions (a leading «—», «, —» with a space, no ASCII hyphen as a dash),
  and a number check that sees a leading heading number.
- Provider errors are classified by what they mean, not by the HTTP status: a server's `terminated` is retryable once;
  a structured-output failure (an unsupported `json_schema` sampler) falls back to plain JSON instructions and is named
  in the run log.

**Stage C — the proofread may not make things worse.** A rewrite is kept only when it adds no source-language word,
no name form outside the map and loses no negation or sentence, and the deterministic checks score it no worse than the
text it replaces (the pass made 3 of 14 paragraphs worse without such a gate).

**Observability.**
- Prompt reading and time to first token are measured from the stream when the endpoint does not report them, and a
  missing figure is shown as missing, not as 0.
- Preparation (brief, names pass) and proofread calls appear in the by-kind run summary.
- The provider's error reason (sanitised, from the safe-details allowlist) reaches the run log, not only TRACE.
- A run's start (preparation, the first chapters) is kept beyond the rolling trace's rotation, in the run log or the
  evidence log.

## How we evaluate

The redesign is judged by evals, so the evals follow the practice summarised for this proposal (OpenAI evals, Google
Stax / Vertex AI evaluation, Anthropic's published methods): representative cases, the production code, automated
graders calibrated against people, and comparison with a baseline.

**What exists today** (`modules/pipeline/src/test/java/ua/bookloom/pipeline/eval/`, `scripts/eval-matrix.sh`,
`scripts/eval-score.py`, `scripts/probe.sh`): `promptEval` suites (prompt, realrun, sequence, mini, names, coref, gold,
stability, the stage suites, batch, words, replay, recall, book) built through the production `PromptRequests` and
guarded by `PromptRequestsEquivalenceTest`; deterministic graders; four model classes in `eval/thresholds.json` (used by
the prompt suite only); one `report.json` per run in `.temporary_context/eval-history/` with the provenance from
`EvalProvenance` (git sha, prompt hash, model, provider, window, dial). Missing: a judge of meaning and fluency (the
blind gradings in this proposal were done by hand with Opus), any dataset version, per-case results with their prompt
and reply, sampling parameters, relative thresholds, a named baseline, measured variance, and a non-English source.

**Principles.**
- Evals run the production prompt builders and the production job, never copies of prompt text (already true; keep it
  for the one `ContextBuilder`).
- Datasets live apart from the prompts, are invented text (the book-neutral rule) and are versioned by a content hash
  recorded in every report.
- Every defect found in an owner run becomes a regression case before it is fixed. From this run: a stress-marked
  target, an inflected form scanned as a new name, a genitive given its own gender, a Russian line returned as the
  translation, a Russian-only word inside Ukrainian, a lost leading dash, a lost heading number, a lost negation, and a
  proofread rewrite that adds a source word.
- Cases are labelled by task, language pair, difficulty and length, so a category's regression is not hidden by the
  average.

**Levels.**

| Level | When | What |
|---|---|---|
| Unit and prompt-construction tests | every commit (`check`) | deterministic logic; the builder's blocks, order and injected items per window |
| Mini eval book | every pipeline change | the defect cases above, under 5 minutes on e4b, repeated 3 times |
| Suites on e4b | before a change closes | realrun, sequence, names, recall, stability |
| One owner book on e4b, 26b once | a change's end | the whole-book metrics below |
| Human blind review | major changes, and to calibrate the judge | a sample of paragraphs, A/B order shuffled |

**Graders.**
- Deterministic first: output language and script, source residue, stress marks, names per lemma, glossary terms used,
  numbers, quotes and dashes, placeholders, sentence and negation counts.
- An LLM judge for what code cannot see. A translation rubric gets the source, the output, the window and the map:
  meaning 0–5, grammar 0–5, terminology 0–5, fluency 0–5, omissions and additions listed, formatting pass/fail. A
  pairwise mode compares two arms with the order shuffled per item. The judge is a dev-only, configurable endpoint in the
  eval harness, never part of the app, so the offline rule holds.
- The judge is trusted only after calibration: it grades a human-labelled sample (the owner's and the earlier blind
  gradings), and agreement per criterion is measured and recorded; the rubric changes where they disagree.
- Reference metrics (chrF, BLEU) only as a secondary signal on a small reference set; a good paraphrase scores low.

**Reports.** Each run records, beside today's provenance: the dataset hash, the sampling parameters (temperature, top_p,
output caps, seed, quantisation, server and its version), the judge's model and rubric version, and per case its id,
labels, result, scores, time and tokens. Prompts and replies are kept with the report under `.temporary_context/`
(owner-local, never committed).

**Thresholds and noise.**
- Absolute floors per category (for example: valid batch replies, no source residue, no stress marks) and a relative
  limit against a named baseline file kept in the repo, both per category and in aggregate.
- Repeats measure variance (at least 3 on the mini book); a change inside the measured noise is not a result, and the
  noise band is computed per run instead of quoted from memory.

**Coverage.** A non-English source and a pair sharing a script (ru → uk) join the mini book and the language cases;
today nearly every case is English-source.

### Measuring this change

- **A mini eval book** (invented text, 20–30 segments, under 5 minutes on gemma4 e4b) with one segment per known defect —
  dialogue punctuation, inflected titles, brands and materials, names with and without evidence, places, a Russian-letter
  trap, an injected instruction, a long paragraph, a ти/Ви mix, the narrator's gender, a footnote aside — each with its
  expected outcome, run by `scripts/eval-matrix.sh` on every change and on every new model the owner brings.
- Then the existing suites on e4b (realrun, sequence, names), then one owner book on e4b, and 26b once when the change
  closes (the e4b-first rule).
- Success: on the owner books, fewer meaning errors in a blind grading than today's Balanced run, no injected entry absent
  from its window, prompt reading under 25 % of call time, and Max at most 2× Balanced's time.
- Success on the Russian → Ukrainian book (rerun with the same model and dial): no stress mark and no source-language
  line in the export, one spelling per name lemma (inflection aside), no proofread rewrite that makes a paragraph worse,
  and fewer paragraphs with a meaning error in a blind sample than today's (about 11 %).

## Out of scope here

The book format work (open, mask, reassemble) is not touched. Providers stay local; no paid API is assumed (the eval
judge is a developer's choice, outside the app). Two document-layer defects seen in the Russian → Ukrainian run are
separate fixes in `:document`: an EPUB title whose `<br/>` elements come back as escaped text, and distributor
boilerplate of which only the link texts are segmented, so the sentences around them stay untranslated.

## Moved here from complete-translation-workflow

Items the closing of `complete-translation-workflow` (2026-10-17) did not do, because they belong to this redesign. The
follow-up change is named **`redesign-translation-pipeline`**; each item names the task it came from (in that change's
`additional-work.md`).

- **The batch format (15i.X1).** json stays the default; the `markers` and `dual` arms stay behind
  `BOOKLOOM_BATCH_FORMAT` until this change decides and removes the losers. On e4b markers met the speed rule (4.11 →
  3.60 s per segment, 1.60 → 1.32 s on a 300-segment run) but were 17 % slower on 26b; the 60-segment WRONG grading was
  never run.
- **A gated reviewer (15h.F1).** Reviewing only segments with a soft finding, confidence under 1, a gendered character or
  dialogue was never measured; Stage B's "one repair, then marked for the proofread" replaces the per-chunk reviewer it
  would have gated.
- **Whole-book speed (15e.15).** "The 26b whole book in at most 5 h" and a window cap above 16k; this change's measures
  (prompt reading under 25 % of call time, Max at most 2× Balanced) own them.
- **What review costs at Max (15d.6).** The reviewer writes about 3.3–4.0k completion tokens per fixture book against
  about 0.8k for the old judge; the dial's meaning ("how many windows are re-read") is redefined here.

- **Prompt order for the provider's cache (15h.C5, 15j.J11 (1)).** Moving the token rules to the end of the draft and
  batch system prompts gave a shared prefix but cost token fidelity on e4b (token-dropping batch items 4 → 16), so it was
  reverted; the one context builder decides block order with both effects measured.

- **Recurring terms on 26b, and words read as ordinary (15i.N7).** After the lower-case rule e4b keeps 9/23, 7/13 and
  2/21 gold recurring terms, but 26b is level with before, and Balanced still drops nouns whose windows the model reads
  as ordinary words.
- **Batch size per model (15h.F1).** On e4b size 4 kept tokens in 96 % of items against 83 % at the default 8; on 26b
  both kept every token. Sizing chunks to the window and the model belongs to Stage B.
- **The narrator's and characters' gender when the book is silent.** The e4b book run left the narrator's gender and
  two characters' genders unknown (123 segments waiting); decision 3 (model knowledge as "likely") is the fix.

## Open questions for the owner

- **Meaningful surnames.** The run translated a speaking surname (`Снеговой → Сніговий`) where a transliteration
  (`Снєговий`) keeps the name a name; both exist in published translations. Should the name policy translate a surname
  whose meaning is plain, transliterate it, or ask?
- **The eval judge.** May the judge be a stronger, non-local model during development evals (the app stays offline), or
  must it be a local model? A local judge is weaker and needs more calibration.
- **The reviewer at Balanced.** It ran on about half the batches and 270 of its 349 suggested edits did not match the
  draft. Should Balanced keep a per-chunk reviewer until Stage C proves it can carry the load, or drop it with this
  change?

## Next step

Turn this into an OpenSpec change (proposal, delta specs, tasks) after the owner's review; group 15j's fixes ship first
inside the current change. The evidence now covers two English sources on gemma4 26b and one Russian source on a 12b
Ukrainian fine-tune, so the change's eval set needs both kinds of source from its first task.
