# Proposal: re-centre the translation pipeline on prepare → translate → proofread

Status: proposed (owner review pending) · 2026-10-16 · evidence: `.temporary_context/new_translation_3/ANALYSIS.md` (owner-local)

## Why

The pipeline was meant to translate a book almost unattended: the model prepares the book's data, translates chunks that
fit its window with exactly the context those chunks need, and finally re-reads the whole book to make it consistent. The
skeleton is still there, but around it the pipeline grew per-segment repair loops (a reviewer on every chunk, directed
fixes, repair rounds, an in-run backward revision, an export consistency pass), and each loop builds its prompt context in
its own way. Two owner runs on gemma4 26b (Oct 15–16) show the cost:

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

## How we measure

- **A mini eval book** (invented text, 20–30 segments, under 5 minutes on gemma4 e4b) with one segment per known defect —
  dialogue punctuation, inflected titles, brands and materials, names with and without evidence, places, a Russian-letter
  trap, an injected instruction, a long paragraph, a ти/Ви mix, the narrator's gender, a footnote aside — each with its
  expected outcome, run by `scripts/eval-matrix.sh` on every change and on every new model the owner brings.
- Then the existing suites on e4b (realrun, sequence, names), then one owner book on e4b, and 26b once when the change
  closes (the e4b-first rule).
- Success: on the owner books, fewer meaning errors in a blind grading than today's Balanced run, no injected entry absent
  from its window, prompt reading under 25 % of call time, and Max at most 2× Balanced's time.

## Out of scope here

The book format work (open, mask, reassemble) is not touched. Providers stay local; no paid API is assumed.

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

## Next step

Turn this into an OpenSpec change (proposal, delta specs, tasks) after the owner's review; group 15j's fixes ship first
inside the current change.
