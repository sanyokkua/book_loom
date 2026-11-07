**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-11-04
**Cross-references:** `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md`,
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/specification/01_Product/12_PROMPT_CATALOG.md`,
`docs/specification/01_Product/07_SETTINGS.md`, `docs/specification/diagrams/pipeline.mermaid`,
`docs/specification/diagrams/chunk-translate-loop.mermaid`

# Pipeline Engine

`:pipeline` is the translation engine. It drives the whole-book flow diagrammed in
`docs/specification/diagrams/pipeline.mermaid` and the per-chunk loop in
`docs/specification/diagrams/chunk-translate-loop.mermaid`. It is FX-free, calls `:document` (parse/mask/reassemble),
`:llm` (inference through the gate), and `:persistence` (checkpoints). As a **non-normative aspiration** most segments
pass on the first call and only a small residual is flagged (measured in **segments**, not chunks); this is a design
goal, not an acceptance gate.

## chunk-packing {#chunk-packing}

Chunking is **paragraph-grouped**: whole segments are packed in document order into a chunk until adding the next
segment would exceed the chunk **token budget**; the chunk then closes and a new one opens. A segment is never split
across chunks, so one target block always maps back to one source block. Concretely, a chunk is a run of consecutive
pending segments of one unit, closed on the **token budget alone**: the current-part budget of the model profile's tier
(`WindowBudgets.currentPart`, 600 estimated source tokens small, 1,000 large; redesign group 11). There is **no
segment cap per chunk**: neither the quality dial nor Manual review caps it any more (redesign group 10.8, owner
decision D-17). How many segments go to the model in one request is the **batch size** (`#tiered-loop`, step 1), cut
inside the chunk. A unit boundary always closes the current chunk (ADR-0038), and so does a chapter boundary
(`ChapterBoundaries`, redesign 12.2: the chapter level is the shallowest level of listed body-structure nodes holding at
least two, a chapter under 300 source tokens joins the next and a short last one the one before, and a book with no
such level is cut into pseudo-chapters of at most 3,000 tokens at paragraph ends), so no batch holds two chapters; the
window of previous pairs crosses both (`#context-builder`).

The budget is the **tier's**, not the window's: the window sizes each call, never a chunk. Because BookLoom ships no
tokenizer, token counts are **estimated deterministically** from code points:

```
estTokens(text) = ceil( codePoints(text) / charsPerToken(script) × 1.15 )
```

`charsPerToken` is `Script.charsPerToken`: Latin 4.1, Cyrillic 3.3, Greek 3.5, Han and Hangul 1.5, unknown 3.0
(conservative); the estimate always rounds up (`TokenEstimator`; full text and the window fit in
`01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`). The window comes from the per-model window (`ModelWindow`: one
window per model, from the model's settings); every fit keeps a 500-token safety margin
(`ContextBudget.SAFETY_MARGIN`) beside the prompt and its reply reserve. The quality dial does not move the budget. What each call carries beyond its items is then fitted by the context builder to the tier's
shares and to the window (`#context-builder`).

A single segment that alone exceeds the tier's current-part budget is **sentence-split** using ICU4J `BreakIterator` for the source
language, and **only on overflow**; neighbouring segments are untouched. The split **never cuts a masked inline tag**: a
`⟦gN⟧` placeholder and its partner in a paired-markup group (`⟦g1⟧…⟦g2⟧`) are atomic, so a candidate boundary that would
fall between an open/close pair is moved outward to keep the pair and its wrapped text in one sub-segment. This
preserves the per-chunk placeholder multiset that the unmask hard gate checks
(`03_DOCUMENT_MODEL.md#unmask-and-validate`). A genuinely **unsplittable over-budget unit** (a single sentence, or a
tag-pair-bounded run, with no interior boundary that keeps every tag pair intact) is **not** split: it is sent as its
**own over-budget chunk with degraded context** (optional context trimmed first, in the order `#context-builder` gives), a tag pair is never
broken to fit, and the degraded chunk is **logged**.

Chunk boundaries prefer chapter/unit boundaries; the window of previous pairs crosses them, and the recently-present
card covers a chapter start (`#context-builder`). A worked multi-paragraph packing example (including the sentence-split-with-tag case) is in
`01_Product/05_TRANSLATION_ALGORITHM.md#chunking-worked-example`.

## context-builder {#context-builder}

One `ContextBuilder` (`ua.bookloom.pipeline.context`) plans every translate-family call (draft batch, reviewer, repair,
revision, consistency, retry) and the analysis calls (names, brief, file name). A call is thirteen blocks (`BlockSlots`),
in the order sent, so the **load-bearing items sit at the edges** against lost-in-the-middle; an empty block is left out.

- **Blocks 1–7, the fixed system part of the run:** role and language pair; book text is data; the book rules (chosen
  once per book by `BookRules.choose`: marks, dialogue and address, numbers, agreement, inflection); the style sheet;
  the language rules (leaving out what block 3 states); examples; the reply shape. `RunFrame.frameHash` is the SHA-256
  of these blocks, and `RunFrames.buildStored` saves the frame once per project as `promptFrame:<hash>`.
- **Blocks 8–13, the last user message:** the scene card (`RecentlyPresentCard`: up to five characters or places from
  the previous 12 story paragraphs of the chapter that are not in the window; earlier chapters' summaries join it in
  group 12); the map lines (`WindowMap`: glossary and recurring-term entries whose forms occur in the window's source,
  locked names by token); the window; what only this kind of call adds (a repair's defects); the task; and the items,
  with the reminder (at most three lines: marks, dialogue, narrator, each only when it applies) last. The key-term block
  (11b) was deleted by task 12.9 (arm 5b).
- **Window** (`WindowAssembler`): the last 2 (small tier) or 3 (large tier) body-unit segments before the focus, across
  chapter and unit boundaries, never front or back matter or the auxiliary unit; a neighbour with no accepted target is
  shown as source only; the next segment is shown as source only.
- **Fit** (`WindowBudgets`): shares per tier (small 300 / 600 / 150 / 600 and ceiling 3,500; large 500 / 1,000 / 250 /
  1,200 and ceiling 6,000 — previous, current part, next, summaries, prompt, in estimated tokens), and the window:
  prompt + `OutputReserve` + `ContextBudget.SAFETY_MARGIN` must be at most the model's window. Over either, drop in
  this order: the next source, the oldest summaries, the oldest previous pairs, the map lines with the fewest
  occurrences, then the last summary, the last pair, and finally batch items. Never dropped: the first focus item,
  pinned and locked names, a character's stated gender.
- **Analysis calls** use `AnalysisFrame`: blocks 1, 2 and 7 as three English sentences, with the task as the one user
  block.
- **Retry:** a review retry rebuilds the frame and the context from today's glossary and settings.

## prompt-templates {#prompt-templates}

Prompts are built from **templates + slots**: fixed instruction scaffolding in prompt files under
`ua/bookloom/pipeline/prompt/` with named slots (`PromptTemplates`, `Slots`), filled by the context builder's blocks.
The repair template adds the previous attempt, the defects and the expected tokens to the same frame. Templates are
data, so the quality dial and policies parameterize the prompt without code changes. The concrete system/user
templates for every pipeline call — batch draft, reviewer, repair, backward revision, book-brief/tone setup, and
the chapter summary — with their injected variables, required-vs-optional fields, parameters, and expected JSON
shapes, are the normative catalogue in `01_Product/12_PROMPT_CATALOG.md`.

## tiered-loop {#tiered-loop}

Scoring is **per chunk** when the judge runs, but each check and each repair act on **one segment**: a failure — a
hard gate, a check, or a judge finding — repairs only the **offending segment**, and the repair call returns exactly
one segment's target, never a chunk-mate's (ADR-0038). Per chunk (see `chunk-translate-loop.mermaid`):

1. **Draft** — consecutive segments of the chunk go out together in one batch call, and the reply is read per id. The
   batch **starts at the model profile's size** (`ModelProfile.batchSize`, read once per run by the run wiring; the
   quality dial does not change it, so Fast, Balanced and Max batch alike, and Manual review drafts in the same
   batches and still pauses as its review mode says). The size halves on a wrong, missing, merged or unknown id and
   after three stalls, grows by one after three clean batches, never above the profile's size and never below the
   smaller of 2 and that size; a call that returned no reply leaves it unchanged (`BatchDrafter`). When a readable
   reply leaves out two or more ids, one **re-ask** names only those ids and its answers are merged into the first
   reply; a failed or unreadable re-ask changes nothing. An id that is missing, malformed or wrong-shape after that is
   drafted alone as a batch of one in the same `translate` prompt and `{"items":[…]}` reply; a malformed reply gets the
   format re-ask (`format-reask`), and no text fallback is accepted. The window's previous
   pairs provide context only. A **context-match TM auto-reuse** and a segment with nothing to translate skip the draft call (and the
   reviewer) entirely.
2. **Unmask + validate** — restore placeholders; **tag-multiset hard gate**
   (`03_DOCUMENT_MODEL.md#unmask-and-validate`). Lost formatting marks are first **put back by code** where they can
   be; a mismatch that remains is a BLOCK finding.
3. **Deterministic QA gate** — run the checks in `#qa-checks` (each finding has a level: BLOCK, REPAIR or NOTE);
   compute `confidence`. Typography and code repairs (apostrophes, quote marks, dashes, a heading's number) are made
   by code before any model reads the draft.
4. **Reviewer (dial-gated)** — when the dial enables it, one reviewer call per chunk and pass (Balanced one, Max two)
   over the drafted pairs whose checks passed; it answers per segment `ok`, edits or a rewrite. The code verifies each
   edit (the quote occurs exactly once after normalisation, the replacement keeps the `⟦gN⟧` tokens in order, the
   deterministic checks pass on the edited text, the set of blocking checks does not grow) and applies the verified
   ones; an edit whose quote was found but that was refused gets one directed fix naming the quote (the directed fix
   remains for this one use until redesign group 14 removes the reviewer); a rewrite is taken only when every check
   passes. A malformed reply is salvaged entry by entry and its unread pairs are asked once more (another seed),
   those still unread being taken as `ok`; an empty reply, a refused prompt, or a call that times out twice, flags
   its segments `reviewer-unavailable` instead of pausing the run.
5. **Accept**, per segment — `AcceptanceRule.decide`: `accepted = hardGatesPass ∧ noSoftCheckFailedOutright ∧
   noBlockLevelFindingLeft ∧ noVerifiedBlockerLeft`, where a finding's level is read by `CheckLevels.of`
   (`#qa-checks`, levels); a failed untranslated-echo check on a source under 20 code points of display text does not
   count as a failed soft check (`#qa-thresholds`). There is no score and no threshold: confidence only orders
   segments for review, and the review mode decides when the run pauses (ADR-0038; a model-call error instead of a
   quality failure is routed by `design.md` D3's failure-routing table, not this rule). → `ACCEPTED`. REPAIR findings
   that remain on an accepted segment become its **proofread mark**, NOTE findings its **audit mark**
   (`AcceptanceRule.Mark`).
6. **The one repair** (`heal/OneRepair`, `RepairMode` of the dial) — otherwise, and also for an accepted draft that
   still holds a REPAIR finding:
    - **Balanced and Max (`RepairMode.ONE`)**: exactly **one** call (`CallKind.REPAIR`, prompt `repair`,
      `12_PROMPT_CATALOG.md#repair`) names every BLOCK and REPAIR defect with the span it quotes, shows the previous
      attempt as data and repeats the expected tokens. A segment drafted whole is repaired through its own request; one
      drafted in pieces by drafting its pieces again with the defects. The repair **replaces the text only when it
      passes the hard gates and `NeverWorse.judge` keeps it** (`#qa-checks`, never worse); a text that stands on no
      machine target (a refusal, an echo, a wrong-language reply, lost marks) keeps its repair when it changed and
      has fewer blockers, without the text vetoes. The outcome is then **read on the text that stays**: BLOCK findings
      left → `FLAGGED`, keeping the last target that passed every hard gate as its machine translation; REPAIR left →
      accepted with the proofread mark; NOTE only → accepted with the audit mark.
    - **Fast (`RepairMode.MARKS_ONLY`)**: no fixing call. Only a segment whose formatting marks are still lost after
      the code restore is drafted **once more**, plainly (the draft request with a new seed, no defect named), under
      the same keep rule; otherwise the findings left mark it, and a BLOCK finding flags it (export then writes its
      source). A refusal, an echo or a wrong-language draft at Fast gets no call and is flagged.
    - **A review retry** from the desk makes no repair call: the retried draft is decided by the hard gates, the checks
      and the acceptance rule alone.
    - There are no repair rounds, no best-candidate path and no soft-fix call: the former round counter is gone
      (`DialParameters.repairRounds`, `SegmentRecord.repairRounds`; a database stored with it is set aside on the next
      launch). A reply that is blank or cut off flags the segment at once. A pause or a pausing error during the repair
      makes the repair call again on resume from the draft kept in memory, counted as the one repair.
7. **Persist + update memory** — atomically write the segment; update name dictionary and context-keyed TM; register any deferred-resolution items. Resume picks up at the **first PENDING** segment
   (FLAGGED is terminal-for-run, excluded from auto-resume). Opening a stored project never continues a run: a stored RUNNING or PAUSED run belongs to a process that is gone and is rewritten as CANCELLED, so the next Start or Continue resumes at the first PENDING segment. Chapter summaries are kept per finished chapter (`#rolling-summary`).

Every model call, the repair included, passes through the `InferenceGate` (`04_LLM_INTEGRATION.md#inference-gate`).

**Step announcements.** The run tells its listeners what it will do and what it has done as `StepsPlanned(kind,
added, estimated)` and `StepChanged(kind, label, index, state, itemRef, result)` events, so a progress bar can count
model work instead of segments. `estimated` is `false` when every planned step will be ended (possibly with `NO_CALL`)
and `true` for a forecast the run may correct. A step's `index` counts from 0 within its kind; a step started again
under the same key (a retry after a pause) keeps its index, so it is never counted twice, and a step the run stopped
inside stays open — it is never ended `FAILED` — because the next run plans afresh.

| Kind | Planned or added | `itemRef` | Ends |
|------|------------------|-----------|------|
| `DRAFT` | one per segment, planned when its chunk is entered | the segment id | `DONE`; `NO_CALL` for a shortcut or memory reuse; `FAILED` when flagged at once |
| `CHECK` | one per reviewer pass the dial enables, planned when the chunk is entered | the chunk's first segment id | `DONE`; `NO_CALL` when no pair qualified or the first verdict was unreadable (pass 2); `FAILED` when the reviewer is given up |
| `REPAIR` | one added when the one repair (or Fast's marks-only re-draft) or the reviewer's directed fix starts | the segment id | `DONE`; `FAILED` when the segment is flagged now |
| `RE_ASK` | one added when a batch reply left out two or more ids | the first missing id | `DONE`; `FAILED` when the second reply is unreadable |
| `RESEND` | one added for each attempt past the first of a model call | the call's only segment, or none | `DONE` or `FAILED` with the attempt |
| `SUMMARY` | one per chapter, planned at the start of translation while the brief's chapter-summary switch is on | the chapter's first segment id | `DONE`; `NO_CALL` for a stored summary or the book's last chapter; `FAILED` when not kept or not stored |
| `PROOFREAD` | one per proofread window, planned exactly when each round starts (windows whose fingerprint is already stored are skipped); before that the ledger forecasts them (Balanced and Max: one per mark so far; Max: ⌈story paragraphs ÷ B⌉ for round 2); none at Fast | the window's first focus segment id | `DONE`; `FAILED` when left unread after its pause budget |

Labels are stable lower-case keys the screens translate (`draft`, `check-1`, `check-2`, `repair`, `redraft`,
`directed-fix`, `re-ask`, `resend-N`, `summary`, `proofread`). The plan is per chunk, not per book; a screen that shows
a book-level total seeds it from the run's figures.

## qa-checks {#qa-checks}

Deterministic, in `:pipeline`. Checks are of two kinds: **hard gates** (boolean; a failure cannot be accepted and is
excluded from `confidence`) and **soft checks** (each yields a margin in `[0,1]` that feeds `confidence`; a soft check
that fails outright blocks acceptance regardless of the blended value — see `#confidence`). Beside them run the
**text checks** of `checks/` (each raises findings, none enters `confidence`), described under `#levels` below.
Numerals are not masked in this build (owner decision D-7); they are compared by value by the number check instead.

| Check                    | Kind          | Fails when                                                                                                                                                                                                              |
|--------------------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| tag integrity            | **hard gate** | the placeholder multiset differs, a paired placeholder's order or nesting is wrong, or a pair rule of ADR-0040 as amended is broken — a pair that held text in the source but holds none in the target, or a line-break token whose innermost enclosing pair changed (pre-QA) |
| refusal                  | **hard gate** | the target's display text is empty while the source's display text is not, or the target starts with a refusal phrase                                                                                                |
| target-script            | soft          | the share of the target's letters that are in the target language's script falls below threshold — respecting the foreign-passage policy so a deliberately kept passage is not scored as wrong-script                |
| untranslated-echo        | soft          | output ≈ source (nothing translated)                                                                                                                                                                                   |
| repetition-loop          | soft          | pathological m-gram repetition                                                                                                                                                                                         |
| omission by length ratio | soft          | target/source length ratio outside the expected band                                                                                                                                                                   |
| glossary compliance      | soft          | a locked term not rendered per the dictionary                                                                                                                                                                          |

### levels {#levels}

Every finding has one **level**, the single answer to "what does this do to its segment". `qa/CheckLevel` has three
values; `CheckName` carries each check's level for its high-severity findings (the one level table, design D12), and
`CheckLevels.of(finding)` reads a stored or fresh finding back, so a level is **derived and never stored** (a stored
finding keeps only its check's wire name and its severity):

| Level | Effect on the segment | Severity it is raised at |
|-------|-----------------------|--------------------------|
| `BLOCK` | the segment cannot be accepted while the finding stays; if it survives the one repair the segment is `FLAGGED` | high (and a soft check with a confidence weight that fails outright, at medium) |
| `REPAIR` | the segment may be accepted; the finding earns the one repair and, if it stays, the **proofread mark** | medium |
| `NOTE` | the segment is accepted with an **audit mark**; the finding earns no call | low |

A finding that no check raised (the reviewer's, an applied edit's, the audit's prefixed copies, a stored reply flag)
reads as `NOTE`. `AcceptanceRule.decide` splits a segment's findings by level and accepts when the hard gates pass, no
soft check failed outright, no `BLOCK` finding is left and no verified blocker is left. `BlockingFindings` is the
reader the export and the consistency pass use; it answers `CheckLevels.of(finding) == BLOCK`, so a finding that kept a
segment from being accepted is called blocking in the same way before and after the run (and none when the person's
own text replaced the machine's).

The levels of the checks (a check that differs by case raises the lower ones at medium or low severity):

| Level | Checks (`raisedBy`) |
|-------|---------------------|
| `BLOCK` | `refusal`, `placeholder`, `locked-term`, `kept-run`, `script`, `echo`, `repetition`, `length`, `glossary`, `script-purity`, `quote-balance`, `language-identity`, `sentence-count`, `vocative`, `protocol-leak`, `name-swap`, `identifier`, `latin-run`, `alphabet`; `number` when a leading number was replaced; `name-spelling` for a pinned name |
| `REPAIR` | `function-word-residue`, `terminal-mark`, `address-parity`, `address-mix`, `gender`, `name-gender`, `name-missing`; `number` for a lost number of two or more digits; `name-spelling` for a name that is not pinned |
| `NOTE` | `duplicate-word`, `spacing`, `unknown-word`, `normalised` (the typography pass changed the text), an extra `number`, and every audit finding |

The text checks added in redesign group 10, each generic for any language pair:

- **Numbers** (`checks/NumberCheck`): compared by value, spelled-out numbers not compared. A leading number replaced
  (`3. …` → `5. …`) is `BLOCK`; a missing number of two digits or more is `REPAIR`; an extra number is `NOTE`.
- **Terminal mark** (`checks/TerminalMarkCheck`, `terminal-mark`): the last mark of a paragraph is another class than
  its source's (a question that became a full stop). `REPAIR`.
- **Form of address** (`checks/AddressMixCheck`): `address-mix` is one quoted speech that mixes informal and formal
  forms; `address-parity` is a speech whose form of address changed between informal and formal against its source.
  `REPAIR`; `address-parity` is off unless the source language file declares both the informal and the formal address
  keys.
- **Names**: `name-missing` (a glossary name lost) is `REPAIR`; `name-spelling` names the exact replacement and is
  `BLOCK` for a pinned name whose target form is none of `NameForms.forms`, `REPAIR` otherwise; `name-swap` and
  `vocative` stay `BLOCK`.
- **Audit-only doubts** (`audit/FinalAudit`, `audit/NameVariants`, `audit/SourceVariants`; stored as `audit:…` findings,
  `NOTE`): a source with two or more negations more than the target (`checks/NegationCount`, same-family pair whose
  language files declare negation words on both sides), and the minority target of a repeated source paragraph of six
  words or more.

### function-word residue {#function-word-residue}

A line translated into the wrong sister language slips past a script check when the two languages share an alphabet
(Russian words left in a Ukrainian sentence). `checks/LanguageIdentityCheck` therefore counts, in the target, the hits
of the **source-only function words** of the pair — the computed set `FunctionWords.sourceOnly(source, target)`, whole
words, lower case, no exemption for words the source holds, a one-letter word only before a lower-case word — and
raises `function-word-residue` (`REPAIR`) naming the words it found. It is a separate finding from the
**wrong-language paragraph** finding `language-identity` (`BLOCK`, a paragraph of at least 12 words), which is raised
when fewer than 60 % of the target's letters are in the target's script or, when the scripts match, when the share of
source-only function words reaches 30 % (`MAX_SOURCE_STOP_WORD_RATE`); one stray word is residue and never that
finding. The alphabet check exempts a word the source holds only when the two scripts differ. The list-based
foreign-word check is gone: no language file holds another language's words (owner decision D-27).

### never worse {#never-worse}

`qa/NeverWorse.judge(current, candidate, facts)` is the one code gate that keeps an edit only when no check got worse;
it returns `Keep` or `Reject(reason)`. It rejects a candidate that fails the placeholder gate, adds a blocking
finding, adds a forbidden letter or raises the count of source-only function words, writes a name form outside
`NameForms.forms` of a pinned or locked target, drops a map name, loses a sentence or a negation, loses a number,
quotes, dashes or clauses, adds a new Latin run or a stress mark, or raises the count of any of the findings
`alphabet`, `language-identity`, `script-purity`, `number`, `terminal-mark`, `address-parity` or `sentence-count`.
The counted vetoes are compared with the current text and the source: a veto fires only if the candidate loses what
the source holds (it falls below both; a number counts only when the source holds it; negations are bound by the
source only when its language has a negation list), so a repair that replaces a wrong number with the source's,
removes a stray quote or drops a doubled sentence is not refused for it.
A candidate that passes the vetoes is kept when its score is not higher and the finding that triggered the edit is
cleared or the score is strictly lower; the score weighs a blocking finding 5, a repair-level (soft) one 1 and an
audit one 1. The one repair and the Proofread stage's windows (`#proofread-stage`) use it; the export's last check reaches it in group 14 of the redesign.

### qa-thresholds {#qa-thresholds}

Each soft check is made **testable** by an explicit threshold. Defaults:

| Check                          | Threshold (default)                                                                                                          | Notes                                                                                                                           |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| untranslated-echo               | similarity(target, source) **≥ 0.90** → fail                                                                                 | `1 − Levenshtein / max(length)` over the NFC-normalized, `toLowerCase(Locale.ROOT)` display texts, with every token removed and whitespace collapsed |
| repetition-loop                 | any **m-gram (m = 3)** repeated **≥ k = 3** consecutive times → fail                                                          | m/k tunable; guards against decode loops                                                                                        |
| omission by length ratio        | `len(target)/len(source)` (chars) **outside the per-pair band** → fail                                                        | band is **per script / language-pair** (below), **widened for short segments**                                                  |
| target-script (gate condition)  | check fires **only** when the source display text is **≥ 20 code points** and the pair's two languages use **different scripts**; fails when fewer than **0.60** of the target's letters sit in the target script | below the floor, when source and target share a script, when the target has no letters (counted after the keep-original name removal below) or when the target language is not catalogued, the check is skipped (treated as pass) so short/ambiguous fragments are not mis-flagged |
| glossary compliance             | every locked in-chunk term present in the target per its dictionary rendering                                                | binary per term; margin = fraction of locked terms honoured                                                                     |

**Omission length-ratio bands** (`target/source` char ratio; widen the lower/upper bound outward by ×0.5 / ×2 for
**short** source segments `< 25 chars`, and by ×0.85 / ×1.25 for those `< 60 chars`). Inside the band the check also
fails a target with more `letter + space + . or ,` spots than its source (a dropped word's gap), or — for a source of
8+ words in a spaced script — under 0.55 target words per source word while the char ratio is under 0.85:

| Language-pair class           | Band `[lo, hi]` |
|-------------------------------|-----------------|
| Latin → Cyrillic (e.g. EN→UK) | `[0.7, 1.8]`    |
| Same-script (Latin → Latin)   | `[0.6, 1.7]`    |
| Latin → CJK                   | `[0.2, 1.0]`    |
| CJK → Latin                   | `[1.0, 5.0]`    |
| Default / unknown             | `[0.5, 2.5]`    |

**Echo floor.** When the source display text holds **fewer than 20 code points**, a failed untranslated-echo check does
not fail the segment outright — it contributes its `0.0` margin to `confidence` and raises only a `low` finding, so a
very short fragment (a two-word title, a one-word exclamation) is never blocked on echo alone (`design.md` D8,
ADR-0038). A source whose display text is empty (a segment that is all tokens) skips the echo and length-ratio checks.

**Keep-original name policy.** Under the "keep original" name policy, every whole-word occurrence of a glossary term is
removed from **both** the source and target display texts before any skip rule is decided and before the
target-script share, the untranslated-echo similarity and the echo floor are computed — in addition to the protected-span removal already applied to display texts — so a line that
is mostly kept names is never scored as an echo or a wrong script on their account.

**Refusal phrases.** The refusal hard gate's phrase list is a set of **anchored prefixes** compared
case-insensitively (`Locale.ROOT`) against the trimmed target **display text**, with a curly apostrophe `’` read as a
straight `'`, and a phrase matches only when the end of the text or a character that is neither a letter nor a digit
follows it (`Як ШІ` never catches `Як шість…`, `As an AI` never catches `As an aide…`). Every phrase names the task or
the model — never a character's own in-story apology — and the list covers **English**, the run's **source
language**, and its **target language** (tags normalized first; a language with no list contributes none). The prefix
test is skipped when the source display text itself starts with a listed phrase, so a book's own `Translation:` line is
not a refusal. The lists (`qa.RefusalPhrases`):

- `en` — `I'm sorry, but I can't translate`, `I am sorry, but I cannot translate`, `I cannot translate`,
  `I can't translate`, `I am unable to translate`, `I'm unable to translate`, `As an AI`, `As a language model`,
  `Here is the translation`, `Here's the translation`, `Translation:`, `Sure, here is the translation`;
- `uk` — `Вибачте, я не можу перекласти`, `Я не можу перекласти`, `Не можу перекласти`, `Як мовна модель`, `Як ШІ`,
  `Ось переклад`, `Переклад:`.

**Foreign-keep vs echo/script.** A segment is marked **foreign** under the Keep foreign-passage policy when its
block's own declared language (recorded on the segment at masking time, `03_DOCUMENT_MODEL.md#data-model`) differs
from the run's source language, or its dominant script differs from the source language's — never from anything
detected in unmarked text. A marked segment skips the **untranslated-echo** and **target-script** checks. A kept
foreign run **inside** a segment, and a locked glossary term, are protected spans removed from both display texts
before every soft check runs. An unmarked segment that simply echoes the source is still failed by untranslated-echo —
foreign-keep is not a blanket excuse for an untranslated output.

### confidence {#confidence}

`confidence ∈ [0,1]` is a **documented weighted blend of the soft-check margins only** — hard gates are excluded (they
are pre-gate booleans) and the **judge score is NOT folded in** (it is compared separately against `τ_judge`). For each
soft check, `margin_i ∈ [0,1]` is how far the observed value sits from that check's failure cutoff: a **failed** check
contributes margin `0.0` and a **skipped** check contributes margin `1.0`; a passing, non-skipped check's margin is:

- target script — `clamp((share − 0.60) / 0.20)`
- untranslated echo — `clamp((0.90 − similarity) / 0.10)`
- repetition — `1.0` with no repeated run, `0.5` for a run of exactly two
- length ratio — `clamp(distance to the nearer band bound / (0.10 × band width))`
- glossary compliance — the fraction of in-segment locked terms rendered as entered; `1.0` whenever the protected-span
  hard gate passed, since a locked term is masked and cannot mis-render

The blend, summed in this fixed order:

```
confidence = 0.30·m_glossary + 0.25·m_lengthRatio + 0.20·m_script
           + 0.15·m_untranslatedEcho + 0.10·m_repetitionLoop
```

(weights sum to 1.0). `confidence` is then compared against the review-mode dial's `τ`; the judge, when run, is a
separate `judgeScore ≥ τ_judge` term in the accept rule (`#tiered-loop`).

**A soft check that fails outright blocks acceptance**, independently of the blended `confidence` value, and raises a
`medium` finding — `language` for a failed target-script or untranslated-echo check, `fluency` for a failed
repetition check, `omission` for a failed length-ratio check, `glossary` for a failed glossary-compliance check —
except the **echo floor** above, where a failed echo under 20 code points contributes only its `0.0` margin and a
`low` finding without blocking. The blend decides only the close calls that no check failed outright (ADR-0038).
Every finding also records what raised it — the check (`script`, `echo`, `repetition`, `length`, `glossary`,
`refusal`, `placeholder`, `locked-term`, `kept-run`) or `judge` — so a review can tell a length-ratio omission from a
judge's.

## name-term-dictionary {#name-term-dictionary}

A per-project dictionary of names/terms with **type** and **gender** (for target-language agreement). Built by the Prep
pre-scan (`pipeline` phase B) and grown during translation. Terms can be **hard-locked**: replaced by a placeholder
before inference and substituted back on unmask, guaranteeing an exact rendering. Persisted in `glossary`
(`06_DATA_MODEL_SQLITE.md#glossary`).

An entry is a **lemma entry**: its term is the name's nominative and its forms list the other spellings the book
uses. The scan reads the source language file's `nameParadigms` rows the other way round (`glossary/NameForms`) and
groups the forms whose possible nominatives meet: `Велемир`, `Велемира` and `Велемиру` become one entry `Велемир`,
counted together against the three-use floor. A nominative is accepted when the book writes it and another form agrees,
or when two forms agree on it though the book never writes it (the entry is then marked `lemmaInferred`); one oblique
form alone stays as written, and a language with no `nameParadigms` keeps one entry per written form. A form that is
also another name's nominative (`Велемира`, a woman) is decided mention by mention by the clause around it
(`glossary/ClauseEvidence`: a past-tense ending after it, a word before it that asks for another case, a gendered form of
address); a mention the clause leaves open is unsure and gives no gender vote. Every lookup — a duplicate, a held or
removed term in a scan, a chunk's additions, the Add term dialog, a CSV row, the terms a window carries — compares by
one key against the lemma and every form; the id stays the project id plus the lemma's key.

The names pass runs ten stages (`NamesPassImpl`): scan, decide the names, `name-knowledge` (the model's answer for each bare name nobody settled, recorded as a **likely** value that no check enforces; `#name-knowledge` in the prompt catalog), recurring-term scan, clean-up and reading, target suggestions for names and for recurring terms, a `check` stage (no request: every suggestion against the target language's rules, counting how many Accept all would take) and the write. Accept all (`acceptSuggested`) strips the stress marks the target language asks to remove, validates each suggested target and makes only the valid ones the person's; the rest are reported with their reasons.

## context-aware-tm {#context-aware-tm}

Translation memory keyed by `(source_hash, context_key)`:

- `source_hash` = hash of the **unmasked, NFC-normalized** source text (the same hash as `FR-ALGO-A3`), so
  masking/placeholder churn never changes the key.
- `context_key` = `hash(prevSourceHash ⊕ nextSourceHash)` — the source hashes of the immediate neighbours. At a
  chapter/document boundary the missing neighbour uses an explicit **boundary sentinel** (`⟦BOS⟧` before the first
  segment, `⟦EOS⟧` after the last) so first/last segments have a stable, collision-free context key.

Match kinds:

- **Exact match** — same `source_hash`; used as a *hint*, not forced.
- **Context match** — same `source_hash` *and* same `context_key` (both neighbours match) → safe to **auto-reuse**. An
  auto-reused target still passes the **tag-multiset hard gate + the deterministic QA gate** before acceptance, but
  **skips the draft and judge calls**.
- **Fuzzy match** — near source by **deterministic string similarity** (e.g. normalized edit distance / token overlap,
  **not** vector embeddings) → surfaced as a *suggestion* only.

This prevents a repeated sentence from being blindly reused where surrounding context changed its correct rendering.
Store: `06_DATA_MODEL_SQLITE.md#tm`.

## chapter-summaries {#rolling-summary}

The rolling summary, its every-`K`-segments refresh and the Max unit-end `SUMMARY` call are gone (design D10 of
`redesign-translation-pipeline`, task 12.3); the recently-present card (`#context-builder`) carries who was last seen.
In their place each finished chapter gets its own summary: a `ChapterSummary` record (project, chapter key, the unit
and first segment it starts at, its segment count, the text in the target language and the hash of the source it was
written from), saved one per chapter in one transaction and listed oldest first through `SummaryRepository`. Store:
`06_DATA_MODEL_SQLITE.md#record-shapes` (`project_docs` kind `chapterSummary:<chapter key>`). Whether summaries are
written follows the brief: `BookBrief.chapterSummaries` when the person forced it, else on unless the story shape is
`SEPARATE_STORIES` (no analysis means on).

**When.** `run/ChapterSummaries` runs on the job thread right after the chunk commit that decides a chapter's last
story segment (a FLAGGED one counts), before the next chunk is drafted, so the call is single-flight through the same
gate as every other. Nothing is called when the switch is off, when a segment of the chapter is still pending, for the
book's last chapter (no later window reads it), or for a chapter already tried in this run.

Before a run's first draft — so on every Continue, and with nothing pending too — the same class plans one `SUMMARY`
step per chapter and, in reading order, writes the summary of every finished chapter (every story segment decided)
whose summary is missing or whose stored `sourceHash` differs from the one its current source gives: a stop, a failed
call, the switch turned on mid-book or a changed book all leave such a gap. An open chapter is left for its commit, the
book's last chapter and a chapter with a current summary end their step `NO_CALL`, and a stored summary whose chapter
key the current boundaries no longer produce is ignored. A store that cannot be read skips this pass with one WARN. A
chapter is tried at most once per run, so a failed one is tried again on the next Continue; a stale summary whose
rewrite fails stays stored and still reaches block 8, since the port has no remove. The same pass runs again after every chunk commit and once before the run reports completion, so a chapter finished by a skip or the review desk is summarised in the run; a stored summary written from other source text than the chapter holds now is not offered to block 8. One INFO line counts a pass that wrote anything:
`chapter summaries written missing=… stale=… stored=…`.

A pause or a stop aborts the call without waiting for the provider: its `cancelled` answer is not a failure (no WARN)
but is routed like a draft's (`RoutedCalls`): a pause waits, then the whole call is made again from part 1 on resume
(an aborted re-ask never falls back to the first answer); a stop ends the run with nothing of the summary kept and its
step left open.

**The call** (`memory/ChapterSummaryCall`, `12_PROMPT_CATALOG.md#rolling-summary-update`). It reads the chapter's
masked source (tokens removed) with the map lines of the entries the chapter names, on the ANALYSIS frame, and asks
for two or three sentences in the target language. A chapter over the tier's part budget (2,500 estimated source
tokens on a small model, 4,500 on a large one) is read in consecutive parts cut at segment ends, each part's call given
the previous part's kept summary; the last part's sentences are kept.

**The gate** (`memory/SummaryGate`). At most three whole sentences; a sentence that names a glossary entry the chapter
never names is dropped, and so is one holding angle-bracket markup (a leaked prompt tag such as `<Names>`); a sentence with a letter the target alphabet lacks or a source-only function word is asked once
more and dropped if the second answer still has it. A sentence that spells a glossary name by its source spelling when the entry has a target is dropped. A vocabulary check
(`memory/SummaryVocabulary`) joins the same one re-ask: the words known are those of the chapter's decided target text, every form of the glossary's target names, the language's function words and the closed list `prompt/languages/summary-words/<language>.txt` (numerals, story verbs); a word also counts when its stem (its letters less a two-letter ending, at least four) starts a known word, and words of fewer than three letters are never judged. A sentence with more than one unknown word is asked again with those words named and dropped if the second answer still has them; with no decided text the check is off. The glossary and lexicon are only read: no rendering is learned
from a summary.

**Outcome.** A kept summary is saved with the hash of the chapter's masked sources and announced with the job event
`ChapterSummaryKept` (chapter key, number, sentence count, text; its `toString` leaves the text out). A failed call
(`contextWindow` included), an unreadable reply or nothing left keeps no summary and logs one WARN
`Chapter summary not kept chapter=… key=… reason=…`; a storage failure logs one WARN too; the run goes on. The INFO line
is `chapter summary chapter=… sentences=… dropped=…`; the text is logged at TRACE only. The kept summaries reach later draft prompts as the earlier-chapters block (`12_PROMPT_CATALOG.md#draft-translation`).

## deferred-resolution-and-backward-revision {#deferred-resolution}

When a segment is decided before a fact it depends on is known (a character's gender, a target the person changes
later), the engine records a **deferral** (`api: project/Deferral`, `DeferralReason`). One helper,
`revision/DeferralRegister.changed(before, after, decided)`, is the only producer for every glossary write (the service,
`GenderConfirmations` through it, the names pass's `NamesPassStore` and the CSV import's `GlossaryRowsImport`): a TERM
deferral when a PINNED or LOCKED entry's non-empty target changes, a `GENDER_CHANGED` one per decided segment naming an
entry whose enforced gender (the person's or the book's, a confirmed likely value included) appears or changes, and
`GENDER_UNKNOWN` only for an entry with no gender of any origin. A proofread rewrite of the person's edit waits on a
`PROOFREAD` deferral, which carries the proposal. Max's backward-revision pass is gone from the run; the deferrals are
the Proofread stage's input.

### proofread-stage {#proofread-stage}

`pipeline.proofread.ProofreadStage` runs after the last chunk at every dial (`run/StageRunner`); the run is COMPLETED
only after it and the stage is announced with `StageStarted(JobStage.PROOFREAD)`. As Translate ends, `run/RunAudit`
runs the final audit once (never again after the stage). Then, by code: the term sweep (`revision/CodeSweep` over
`TermSweep`; LOCKED whole-word, PINNED through `NameForms.forms`, never SUGGESTED, a proposal through `RevisionWriter`
for a person-edited segment; it consumes TERM deferrals), the narrator inference (`run/NarratorStage`), the deferrals
whose fact arrived checked by the gender check (agreeing → resolved, no call; disagreeing → an open mark with the reason
`gender now known: {target} — {gender}`, `proofread/GenderNowKnown`), and `proofread/MarkList` (tier 1 = FLAGGED,
a REPAIR-level finding left, deferrals whose fact arrived; tier 2 = NOTE findings, audit doubts, the map-name, one-source
-two-targets, recurring-term and gender-ending detectors; story segments only). **Marks** are findings or open
deferrals, never records, so the desk computes the same list. A stop is checked between the code steps
(`JobBoundaries.isStopRequested()`). The dial's `proofreadCoverage` decides what follows: `NONE` (Fast) stops here,
`MARKED_AND_DOUBTFUL` (Balanced) reads the marked windows, `MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH` (Max) adds round 2.

**Windows.** `ProofreadWindows` merges marks in reading order with at most one unmarked story segment between them,
up to the profile's batch size, plus one read-only neighbour each side, keeping each mark's reasons (blocking first);
`ProofreadWindows.tiles` cuts Max's round 2 into windows of at most the batch size, open marks riding as reasons. A
first-round window whose focus all holds whole-book verdicts is skipped. `ProofreadCall` builds `PromptName.PROOFREAD`
/ `CallKind.PROOFREAD` through `ContextBuilder` with the TRANSLATE frame (the drafts' system part byte for byte), block
12 the target file's `proofreadInstruction` and `proofreadRules`, block 13 the focus items, block 10 the neighbours
read-only; temperature 0.1, reasoning off unless the person's override allows it, schema titled `proofread`, the
run's own model through the one gate (`12_PROMPT_CATALOG.md#proofread`).

**Verdicts.** `WindowVerdicts` reads the reply through `ProofreadJudge` (hard gates + `NeverWorse`;
`qa/NeverWorseReasons` names what got worse) into `api: project/ProofreadVerdict(fingerprint, outcome, reason, before,
statusBefore, maskedBefore, findingsBefore)` with `ProofreadOutcome {KEPT, PROPOSED, REJECTED, UNCHANGED}`, held in
`SegmentRecord.proofread`. KEPT: ACCEPTED → REVISED, FLAGGED → REVISED only when every blocking finding is cleared;
otherwise, and for a person-edited text, PROPOSED on a `PROOFREAD` deferral; REJECTED with the gate's reason. A later
window that changes nothing leaves an earlier KEPT verdict and its `before`, so Undo proofread restores the original.
A window's texts, verdicts and resolved deferrals are one `ChunkCommit` (`resolvedDeferralIds`), one transaction in both
adapter families.

**Resume.** `proofread/WindowFingerprint` hashes the focus sources and current targets, the neighbour targets, the map
lines the planned call carries (`ProofreadCall.plan`, no model call), the prompt version and the round, written
`r<round>:<sha-256>`. Planning skips a window whose focus all holds a verdict with the current fingerprint; each round
plans the rest as `PROOFREAD` steps when it starts (`StepsPlanned`). Continue starts at the first stage with work and logs
`Proofread round 1 continues at window 41 of 120`; a pause aborts the in-flight proofread call and makes it again on
resume; a stop leaves the window undecided; a window past its pause budget is left unread (`FAILED` step) with its marks
kept. The between-stages pause point sits before Proofread. The window learns its standing through `api:
pipeline/ProofreadProgress` (`proofread/ProofreadTallies`: windows read are a round's distinct stored fingerprints).
Events: `ProofreadForecast`, `ProofreadRoundStarted`, `ProofreadWindowRead`.

## brief-key-recheck {#brief-key-recheck}

When the person saves a Book Brief whose **apostrophe** or **quote style** changed, `run/BriefKeyRecheck` re-runs the
typography pass by code over the masked target of every decided segment that no person reviewed or edited, with the new
keys, and compares it with the same pass under the old keys, so only a difference the key change causes counts. A
segment it would still change gets the finding `audit:normalised` (listed under "Worth a second look" with the label
`typography`); its status and target are not changed, and the audit's next run replaces the finding. A changed default
address or declared convention has no check that reads stored text yet, so it re-checks nothing. The count of segments
carrying the finding reaches the Book Brief as `ProjectChange.Saved.rechecked`, which shows a banner; a failed re-check
is a warning in the log and the brief is saved all the same. The Export screen separately states, before Export book,
how many segments will be written in the source language because their formatting marks were lost (flagged, no target,
no edit, a `placeholder` finding).

## generation-parameters {#generation-parameters}

Inference runs at **low temperature** for fidelity (fewer hallucinations, less drift off the glossary, more reproducible
output): draft 0.2 (the default), judge 0.1, repair 0.1, directed fix 0.2, proofread
0.1, pre-scan/summary 0.2. These are the pipeline's per-kind values; a model's own settings on Settings ›
Models (`01_Product/07_SETTINGS.md#models-tab`) may replace them, and a review-desk retry can opt into 0.1 for its draft
call when a lower temperature is asked. Every call carries a **reasoning intent** for its kind (off for draft, repair,
proofread, names and brief), never a level; `:llm` maps the intent through the model's profile to the engine's own field
or sends nothing the model does not declare, and the response handler strips any reasoning/thinking channel before
parsing (`04_LLM_INTEGRATION.md`). The pipeline never names a model: it reads `ChatModel.profile()` only for the one
window every call of a run is planned against (`ModelWindow`) and the batch size.
Per-call values live in `01_Product/12_PROMPT_CATALOG.md`. A batch draft call and its re-ask are `DRAFT`-kind calls
whose reply is capped by the size of the items sent (`maxOutputTokens` / `num_predict`); a timed-out call is repeated once
with a lower cap and a new seed, and that second attempt is announced as a `RESEND` step.

The consistency stack this engine relies on is the **name/term dictionary** (`#name-term-dictionary`), the
**context-aware translation memory** (`#context-aware-tm`, matched by deterministic string similarity —
exact/context/fuzzy, never vector embeddings), and the **chapter summaries** (`#rolling-summary`). There is no
embedding or RAG stage.

## quality-dial {#quality-dial}

A single "quality vs speed" dial (`FAST | BALANCED | MAX`) parameterizes the engine's **mechanics**. It does **not** own
the accept threshold `τ`: `τ` is owned by the **review-mode dial** (Unattended/Assisted/Manual) alone — a manual
Settings override is not offered in this build (DD-45, `01_Product/07_SETTINGS.md`); `τ_judge` equals `τ`.

| Parameter                          | FAST             | BALANCED | MAX   |
|------------------------------------|------------------|----------|-------|
| fixing call (`RepairMode`)         | marks-only re-draft | one repair | one repair |
| reviewer passes per chunk          | 0                | 1        | 2     |
| Proofread stage (`#proofread-stage`) | code only, no call | + one call per marked window | + round 2 over every story paragraph |

The dial no longer sets the batch size or a chunk's segment cap: the batch size belongs to the model profile
(`#tiered-loop`, step 1), so the dial means the same for every model. A review retry runs with no repair
(`LoopSettings.repairs` false). The dial sets mechanics; `τ`/`τ_judge` come from the review-mode dial (DD-45). The
automatic-first, tiered-pipeline model this dial serves is ADR-0007. Requirements: `FR-ALGO-*`, `FR-QA-*`.
