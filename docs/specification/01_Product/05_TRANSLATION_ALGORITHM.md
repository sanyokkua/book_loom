**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:pipeline`), QA **Last Updated:**
2026-09-27 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/03_DOCUMENT_FORMATS.md`, `docs/specification/01_Product/06_REVIEW_AND_EDITING.md`,
`docs/specification/01_Product/07_SETTINGS.md`, `docs/specification/01_Product/12_PROMPT_CATALOG.md`,
`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`, `docs/specification/diagrams/pipeline.mermaid`,
`docs/specification/diagrams/chunk-translate-loop.mermaid`

# Translation Algorithm

This document specifies the translation pipeline, realizing `FR-ALGO-*` and `FR-QA-*`, implemented in the `:pipeline`
module. The whole-book flow is drawn in `docs/specification/diagrams/pipeline.mermaid` and the per-chunk loop in
`docs/specification/diagrams/chunk-translate-loop.mermaid`; this document is the normative text for both. As a
**non-normative aspiration**, most segments pass on the first call and only a small residual is flagged for review
(DD-15); this is a design goal, not an acceptance gate — the gate is the per-segment accept rule in `#chunk-loop`, and
any figure such as "~1% flagged" is measured in **segments**.

## pipeline-overview {#pipeline-overview}

The pipeline has five phases: A Import, B Prep, C Translate, D Optional backward revision, E Export. The core
correctness invariant: the skeleton is never sent to the model and never regenerated — only text nodes change (DD-07),
so formatting is preserved by construction. Import masks no numerals and runs no language detection over the book's
own text: the source language is the book's own declared language, normalized and left editable on the Book Brief
(ADR-0037).

| Correctness idea                                                                                              | Requirement |
|---------------------------------------------------------------------------------------------------------------|-------------|
| The previous accepted TARGET pairs (not just source) are the main consistency lever: a window of 2 or 3 pairs.  | FR-ALGO-08  |
| Lost-in-the-middle → load-bearing content at prompt edges; inject only glossary terms occurring in the chunk. | FR-ALGO-04  |
| Context-aware TM: auto-reuse only when neighbours also match; exact-match is a hint; fuzzy is a suggestion.   | FR-ALGO-06  |
| Name/term dictionary carries gender for agreement and can hard-lock via placeholder substitution.             | FR-ALGO-05  |

## phase-a-import {#phase-a-import}

Parse the document into a skeleton and an ordered segment list; mask inline tags, locked terms that have a target,
URLs and, under the Keep foreign-passage policy, kept foreign runs to `⟦gN⟧` placeholders; hash the source. No
numeral is masked, and no language is detected from the book's text — the source language is the book's own declared
metadata, normalized and editable (ADR-0037).

| ID         | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-A1 | Parse to skeleton + ordered segments (FR-DOC-01).                                                                                                                                                                                                                                                                                                                                                                                                                 |
| FR-ALGO-A2 | Mask inline markup, locked glossary terms that carry a target, URLs and, under the Keep foreign-passage policy, an inline run whose own declared language differs from the source language, to `⟦gN⟧` placeholders. No numeral, standalone or in running prose, is masked in this build — every numeral stays translatable so it inflects and localizes (owner decision D-7, FR-DOC-04). |
| FR-ALGO-A3 | Hash each segment's source for change detection, TM keying, and resume. The TM/change-detection hash is `source_hash` over the **unmasked, NFC-normalized** source text.                                                                                                                                                                                                                                                                                          |
| FR-ALGO-A4 | The source language preselected for the Book Brief is the normalized declaration from the book's own metadata (or the content documents' majority language when import raised a mismatch warning), left editable by the user; the code infers no language from the book's text, and a book with none to preselect is Auto — the model identifies the language and the code analyses with generic rules (FR-IMPORT-03, ADR-0037, ADR-0042).                                                                                                                                                                                                                                                                                                                               |

## phase-b-prep {#phase-b-prep}

One-time, automatic preparation: seed the name/term dictionary; derive a style sheet from the Book Brief.

| ID         | Requirement                                                                                                                                                                                                                                                                                                                          |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-B1 | Run the deterministic frequency-and-capitalization scan (`12_PROMPT_CATALOG.md#name-term-pre-scan` documents the offline fallback shape) over the whole book when the glossary is empty, seeding it with unlocked candidates of no target and gender unknown. The LLM pre-scan is the person's own button on Names & style, sent in batches of 40 candidates, and is merged into the glossary only when every batch succeeded; it never runs automatically, and never at a run's start (DD-46, FR-GLOSS-01). |
| FR-ALGO-B2 | Derive a style sheet (register, voice, policies, faithful↔natural bias) from the Book Brief for prompt construction.                                                                                                                                                                                                                 |

## phase-c-translate {#phase-c-translate}

Per chapter/unit: pack whole paragraphs into a chunk up to the
token budget; then run the per-chunk loop.

### chunking {#chunking}

Chunking is **paragraph-grouped**. Segments (whole paragraphs/blocks) are packed in document order into a chunk until
the next segment would exceed the chunk **token budget**, then the chunk closes and a new one opens. A paragraph is
never split across two chunks, so a translated block always corresponds to a single source block. Concretely, a chunk
is a run of consecutive pending segments of one unit, closed on the **token budget alone**: the current-part budget of
the model profile's tier (`WindowBudgets.currentPart`, 600 estimated source tokens on a small model, 1,000 on a large
one; `#token-budget`).
The quality dial sets no segment cap on a chunk: the number of segments sent in one drafting call is the **batch size**,
which starts at the model profile's (`ModelProfile.batchSize`; FR-ALGO-C4). A unit boundary always closes the current
chunk (ADR-0038).

The budget is **derived from the provider's effective context window**, not a fixed constant, and the Generation **"
chunk budget" setting is only a CAP** on top of it (DD-44). The concrete arithmetic is in `#token-budget` below. The
quality dial sets no chunk cap; what a call carries beyond the chunk is fitted by the context builder (`#token-budget`).

**Overflow only** triggers finer splitting: a single paragraph that alone exceeds the budget is **sentence-split** at
source-language sentence boundaries, and only the offending paragraph is split — neighbouring paragraphs are unaffected.
Splitting **never cuts a masked inline tag**: a `⟦gN⟧` placeholder and its partner (for paired markup such as
`⟦g1⟧…⟦g2⟧`) are treated as atomic, so a sentence boundary that would fall between an open/close pair is pushed outward
to keep the pair — and the text it wraps — within one chunk. This preserves the tag multiset per chunk and keeps the
unmask hard gate (`FR-ALGO-C5`) local.

**Oversized unsplittable unit.** If a unit is genuinely unsplittable — a single sentence, or a run bounded by a paired
`⟦g1⟧…⟦g2⟧` group, that alone exceeds the budget and has no interior sentence boundary which keeps every tag pair
intact — it is **not** split. It is sent as its **own over-budget chunk with degraded context** (the reserved slices are
trimmed to fit, dropping the optional context first, in the order `#token-budget` gives); a tag pair is **never** broken to force a fit, and the
degraded chunk is **logged** so diagnostics and the review path can see it (`FR-ALGO-C2b`).

### token-budget {#token-budget}

BookLoom ships **no tokenizer** (DD-44), so token counts are **estimated deterministically** from code points. For a
piece of text:

```
estTokens(text) = ceil( codePoints(text) / charsPerToken(script) × 1.15 )
```

`charsPerToken` belongs to the text's dominant writing system (`Script.charsPerToken`); the 1.15 factor is a fixed
over-estimation cushion that biases every estimate high so a call never silently overflows the real window. A whole
call is estimated block by block (`TokenEstimator.estimateCall`): each part is rated at **its own language's** rate (source text at the
source language's, translations at the target language's), and the model profile's template overhead is added once.

| Writing system (examples)              | charsPerToken |
|----------------------------------------|---------------|
| Latin — English, German, most European | 4.1           |
| Cyrillic — Ukrainian, Russian          | 3.3           |
| Greek                                  | 3.5           |
| Han and Hangul                         | 1.5           |
| Unknown (conservative)                 | 3.0           |

**Chunks.** A chunk closes on the token budget alone: the current-part budget of the model profile's tier (600
estimated source tokens small, 1,000 large; `WindowBudgets`), which also bounds a batch and is the threshold above which
one segment is drafted in sentence-aligned pieces. Paragraphs are packed until the next one would pass it; the window
sizes the calls, not the chunks. Before a model is chosen, the Structure screen's planned chunks are packed at the
default profile's tier.

**Reply reserve.** `OutputReserve.of` sets the room a call keeps for its reply: the pair's output ratio (from the pair's
language file, default 1.5) times the items' source tokens, plus 16 tokens per item for the id and wrapper. A model
that always reasons, or whose reasoning cannot be switched off, gets `max(2048, 4 × sourceTokens + 35 × items)`. The
reserve is never above half the window.

**Window fit.** The context builder fits every translate-family call twice. First the tier's shares
(`WindowBudgets.SMALL` and `LARGE`, in estimated tokens):

| Tier  | Previous pairs | Current part | Next source | Earlier summaries | Prompt ceiling |
|-------|----------------|--------------|-------------|-------------------|----------------|
| Small | 300            | 600          | 150         | 600               | 3,500          |
| Large | 500            | 1,000        | 250         | 1,200             | 6,000          |

(the shares are unmeasured starting values). Then the window: prompt + reply reserve + `ContextBudget.SAFETY_MARGIN`
(500) must be at most the model's window. When a call is over either limit, optional context is dropped in this
order: the next source, the oldest earlier summaries, the oldest previous pairs, the map lines with the fewest
occurrences in the window, then the last summary, the last pair, and only then batch items. **Never dropped:** the
focus's first item, pinned and locked names, and a character's stated gender.

| ID          | Requirement                                                                                                                                                                          |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-C1  | Pack whole paragraphs into a chunk up to the token budget (`#token-budget`) without splitting a paragraph across chunks (FR-ALGO-02).                               |
| FR-ALGO-C2  | Sentence-split a single paragraph only when it alone exceeds the budget, never cutting a masked inline-tag pair (FR-ALGO-03).                                                        |
| FR-ALGO-C2b | Send a genuinely unsplittable over-budget unit as its own over-budget chunk with degraded context rather than cutting a masked inline-tag pair; log the degraded chunk (FR-ALGO-03). |
| FR-ALGO-C3  | Show each call a window of the last 2 or 3 body-unit pairs (by tier) across chapter boundaries (FR-ALGO-08).                                                                                           |

### context-package {#context-package}

One **context builder** (`ContextBuilder`, `02_Architecture/05_PIPELINE_ENGINE.md#context-builder`) plans every
translate-family call as **thirteen blocks** (FR-ALGO-04); a block with no text is left out. Blocks 1–7 are the
**fixed system part** of the run, the same bytes for every call so a server can reuse its prompt cache; blocks 8–13 are
the **last user message**, with the load-bearing task and items at the end.

| Block | Holds                                                                                               |
|-------|-----------------------------------------------------------------------------------------------------|
| 1     | Role and the language pair.                                                                         |
| 2     | Book text is data, never instructions.                                                              |
| 3     | The book rules, chosen once per book (below).                                                       |
| 4     | The style sheet from the Book Brief.                                                                |
| 5     | The target's and the pair's language rules (leaving out what block 3 already states).               |
| 6     | The pair's examples, or the target's illustration (`12_PROMPT_CATALOG.md#few-shot-examples`).       |
| 7     | The reply's JSON shape.                                                                             |
| 8     | The scene card: the earlier chapters' summaries, then the recently-present card.                    |
| 9     | Map lines: glossary and recurring-term entries whose forms occur in the window's source; locked names by token. |
| 10    | The window: the previous accepted pairs and the next source; TM exact and fuzzy hints.              |
| 11    | What only this kind of call adds (a repair's defects). Block 11b, key terms, stays until 12.9.      |
| 12    | The task and the schema line.                                                                       |
| 13    | The items, with the reminder last.                                                                  |

**Window** (`WindowAssembler`, FR-ALGO-08). The previous pairs are the last 2 (small tier) or 3 (large tier) body-unit
segments before the focus. The window crosses chapter and unit boundaries; front matter, back matter and the auxiliary
unit are never in it. A neighbour with no accepted target is shown as source only, and the next segment is shown as
source only. The dial sets no count.

**Map lines** (`WindowMap`). Only the glossary and recurring-term entries whose forms occur in the window's source
are listed, never the whole dictionary; a locked name is listed by its token.

**Rules once per book** (`BookRules.choose`, decided once per run and kept per book): `marks` when a story segment holds
a formatting mark, `dialogue` and `address` when the dialogue share is at least 0.05, `numbers` when the story holds a
digit, `agreement` for a gendered cast and a target with a gendered past tense, `inflect` when the target declines
names. Block 5 omits the dialogue, address, numbers and agreement rules that block 3 states.

**Earlier chapters** (block 8, first). Under `[Earlier chapters]`, one line `Chapter n: text` for each kept summary of
a finished chapter before the segment's chapter, oldest first and ordered by the chapter's place in the book; a
summary whose chapter the book no longer has is passed over. They are fitted to the tier's summary share, dropping the
oldest first, so the most recent always stay; none in the first chapter or with the brief's switch off. The summaries
are read from the store at each draft, so a summary kept mid-run reaches the next chapter's first draft; a repair or
format re-ask carries what its draft carried; a desk retry reads today's. The snapshot stores the keys of the
summaries the call carried. When the person renames a pinned or locked target (an edit or a CSV row), the old target
is replaced in the kept summaries by code: the whole word for a locked one, each generated form for a pinned one;
a suggestion changes nothing.

**Recently-present card** (block 8). Up to five characters or places seen in the previous 12 story paragraphs of the
same chapter but not in the window, most recent first; a likely gender is told as likely. It is left out at a chapter
start, and carries a narrator line when the chapter is first person and the brief names none.

**Reminder** (last in block 13, at most three lines): the marks line only if an item has a `⟦`, the dialogue line only
if an item has speech, the narrator line only if a first-person narrator's gender is stated and an item is first
person.

**Translation memory.** A context-match reuse still skips the draft (`run/MemoryReuse`); the other TM lines go in
block 10. A **retry** (Try again) rebuilds the frame and the context from today's glossary and settings.

**Analysis calls** (names, brief, file name) use the `AnalysisFrame`: blocks 1, 2 and 7 as three plain English
sentences, with no style sheet, rules or examples; the task is the one user block.

The run's frame is stored once per project as `promptFrame:<hash>`, `frameHash` being the SHA-256 of the system blocks;
a segment's snapshot keeps that hash and its own sections.

Every prompt is written so a small local model (~4B) follows it: one task, numbered rules, book text declared as data
rather than instructions, the JSON schema together with a literal valid reply, and the placeholder rules wherever the
text carries `⟦gN⟧` tokens (`12_PROMPT_CATALOG.md#prompt-design`). The local-only `promptEval` set measures that on a
real model (`04_Build_and_Release/06_TESTING_STRATEGY.md#prompt-evals`).

### chunk-loop {#chunk-loop}

For each chunk (see `chunk-translate-loop.mermaid`):

| ID          | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-C4  | Issue draft inferences as batches of ids: consecutive segments of one unit go in one call, and the reply is one JSON object read per id (`{"items":[{"id":"…","target":"…"}]}`). The batch size starts at its start value, halves on a wrong id, an omission or a merge, grows by one after three clean batches, and never grows above its start value. A start of 1 means no batching. Prior accepted targets may be supplied only as section-local context and never as additional source inputs. A segment whose id is still missing or wrong after one re-ask is drafted alone, requiring exactly `{"target":"…"}`: no source id, envelope array, map, extra field, wrapper prose, or text fallback. A malformed or wrong-shape reply receives one structural repair; a valid target that fails the placeholder hard gate receives one separate token-preservation repair. Every repair is strictly parsed and re-gated, with no loop. |
| FR-ALGO-C4b | A **context-match TM auto-reuse** skips the draft and judge calls but still passes the unmask hard gate and the deterministic QA gate before acceptance (FR-ALGO-06).                                                                                                                                                                                                                                                                                                              |
| FR-ALGO-C5  | Unmask and validate the placeholder multiset as a hard gate; a mismatch cannot be accepted (FR-DOC-05, FR-QA-04).                                                                                                                                                                                                                                                                                                                                                                  |
| FR-ALGO-C6  | Run the deterministic QA gate and compute the `confidence` scalar — the documented weighted blend of the **soft** QA-check margins (hard gates and judge excluded; see `02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`) (FR-QA-01).                                                                                                                                                                                                                                              |
| FR-ALGO-C7  | Accept each segment individually — never the whole chunk at once — by the rule `hardGatesPass ∧ noSoftCheckFailedOutright ∧ noBlockLevelFindingLeft ∧ noVerifiedBlockerLeft` (15d.6; `AcceptanceRule.decide`, levels read by `CheckLevels.of`): a verified blocker is an issue the **reviewer** evidenced with a quote the code found in the text and that no verified edit or directed fix resolved; there is no confidence threshold and no score. A failed echo check on a source whose display text is under 20 code points is not counted as a failed soft check — it only contributes its 0.0 margin to `confidence`, lowering it rather than blocking acceptance outright (ADR-0038). The confidence is derived for sorting only. The review-mode dial decides when the run pauses, never what is accepted. |
| FR-ALGO-C8  | Otherwise (or when a REPAIR-level finding is left) give the segment the **one repair** (see below; Fast: a marks-only re-draft); if a BLOCK finding is still left, mark the **offending segment(s)** FLAGGED.                                                                                                                                                                                                                                                                 |
| FR-ALGO-C9  | On acceptance, update the name dictionary and the context-keyed TM (the next window is read from the accepted targets); register deferred-resolution items; persist atomically. Resume picks up at the **first PENDING** segment; FLAGGED is terminal-for-run and excluded from auto-resume (FR-RESUME-01).                                                                                                                                                                                              |
| FR-ALGO-C10 | Update the rolling bilingual summary on a **size-based trigger — every K accepted blocks or at chapter end, whichever comes first** (FR-ALGO-07; `02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`).                                                                                                                                                                                                                                                                         |

### self-heal {#self-heal}

QA runs **per segment**. A failure routes only the **offending segment** to repair, never its chunk-mates: the repair
call returns exactly one segment's target (`{"target":"…"}`), so it never touches a sibling segment that already passed
its checks. Every finding has a **level** (`BLOCK`, `REPAIR` or `NOTE`; `02_Architecture/05_PIPELINE_ENGINE.md#levels`).
The repair fires only when a BLOCK or REPAIR finding exists (DD-16) and is **one call, not rounds** (`heal/OneRepair`):

| Dial              | Path                                                                                                                                                                                                                                  |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Balanced, Max     | One `REPAIR` call: the draft's request plus the previous attempt, every BLOCK and REPAIR defect with its quoted span, and the expected tokens (`12_PROMPT_CATALOG.md#repair`). Kept only when the hard gates pass and `NeverWorse.judge` keeps it. |
| Fast              | No fixing call. A segment whose formatting marks are still lost after the code restore is re-drafted once, plainly; otherwise the findings left mark the segment.                                                                     |

The outcome is read on the text that stays: BLOCK left → FLAGGED (export writes the last target that passed every hard
gate, else the source), REPAIR left → accepted with the proofread mark, NOTE only → accepted with the audit mark. The
reflect → improve → polish chain (15d.7), the repair rounds and the soft-fix call are gone. The directed fix survives
only for a reviewer edit the checks refused, until redesign group 14. This realizes the automatic-first, tiered
self-heal model (ADR-0007).

| ID          | Requirement                                                                                                                                                                              |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-C11 | Send the one repair only for a segment with a BLOCK or REPAIR finding, naming every defect with its span and the expected tokens; replace the text only when the hard gates pass and `NeverWorse.judge` keeps the repair. A directed fix is sent only for the quote of a reviewer edit the checks refused (until group 14). |
| FR-ALGO-C12 | Make at most one repair call per segment per run of the loop (no repair rounds; a pause during the repair makes it again on resume, counted as the one).                                                                                                   |
| FR-ALGO-C13 | Flag only the offending segment(s); repair each failing segment on its own — the repair call returns exactly one segment's target — and re-QA that segment alone (ADR-0038). |

## phase-d-backward-revision {#phase-d-backward-revision}

Optional whole-book pass (enabled by the Max dial or the export toggle). The sweep **proposes, never overwrites**:
user-edited `REVISED` segments are **protected** and change only with explicit user opt-in.

| ID         | Requirement                                                                                                                                                                                                                                                                            |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-D1 | Resolve deferred-resolution items using full-book facts and re-render affected earlier segments to REVISED. User-`REVISED` segments are protected: the sweep proposes a change but does not overwrite them.                            |
| FR-ALGO-D2 | Run a global term-consistency sweep **bounded to segments that contain a swept term**. Apply **deterministic string substitution, without a model call**, and only where the glossary held a previous target for that term which the person then changed; a rendering the model chose on its own for a term that had no glossary target is never swept. Invoke an **LLM re-render only** where a gender/agreement deferral now has a known gender. |
| FR-ALGO-D3 | A `REVISED → REVISED` transition (re-sweeping an already user-edited segment) happens **only with user opt-in**.                                                                                                                                                                       |

## phase-e-export {#phase-e-export}

| ID         | Requirement                                                                                                            |
|------------|--------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-E1 | Write each target string back into its DOM/AST node (FR-EXPORT-02).                                                    |
| FR-ALGO-E2 | Repackage in the source format (EPUB mimetype-first / FB2 encoding-preserving / MD / TXT) and validate (FR-EXPORT-03). |

## quality-dial-mapping {#quality-dial-mapping}

A single quality-vs-speed dial sets pipeline **mechanics** (FR-ALGO-11). It does **not** set the accept threshold `τ`:
`τ` is owned solely by the **review-mode dial** (Unattended/Assisted/Manual); a manual Settings override is not offered
in this build (DD-45, `07_SETTINGS.md`). `τ_judge` equals `τ`.

| Parameter                            | Fast                | Balanced   | Max        |
|--------------------------------------|---------------------|------------|------------|
| Fixing call (`RepairMode`)           | Marks-only re-draft | One repair | One repair |
| Reviewer passes per chunk            | 0                   | 1          | 2          |
| Backward revision runs               | Off                 | Off        | On         |

Neither the chunk budget, the batch size nor the number of previous pairs is a dial row: the first closes on the token budget, the batch size is the model profile's, and the window's count follows the model's tier. A review retry runs with no repair.

## generation-parameters {#generation-parameters}

Every model-facing call uses **low temperature** for fidelity: the goal is a faithful, deterministic rendering, not
creative variation, and low temperature reduces hallucination, drift off the glossary, and the
paraphrase-away-from-source failure mode. The per-phase guidance is:

| Phase                                    | Temperature              | Rationale                                                                                                                          |
|-------------------------------------------|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| Draft translation                        | 0.2 (default)            | Faithful and reproducible; enough flexibility for fluent target phrasing without inventing content.                |
| Judge / deterministic-QA-assisting judge | 0.1                      | A scorer should be near-deterministic so the same draft yields the same verdict; τ comparisons stay stable.        |
| Repair                                   | 0.1                      | A second try that keeps what the draft got right and fixes the named defects.                                                      |
| Directed fix (reviewer's refused edit)   | 0.2                      | A targeted correction of named findings; stay close to the accepted draft.                                                         |
| Backward revision                        | 0.2                      | Consistency alignment across the book; determinism preferred.                                                      |

The default is **0.2**. Per model, Settings › Models can replace these values (`07_SETTINGS.md#models-tab`: the
Temperature row's My value or Don't send, or the Sampling row's Custom values); the review panel's "lower temperature for
this retry" option sends that retry — a draft, not a repair — at 0.1. **Reasoning** is decided per call kind from the
model's profile (design D20 of `redesign-translation-pipeline`): draft, repair, proofread, names and brief calls ask for
thinking off, the profile maps that to the engine's own field (Ollama `think`, `reasoning_effort`, llama-server's
`enable_thinking`, gpt-oss's lowest level `low`), and a model that cannot be switched gets no control and room on its cap
for the reasoning; any reasoning in the reply is removed before parsing. Concrete per-call
parameter values and output schemas are catalogued in `12_PROMPT_CATALOG.md`.

## chunking-worked-example {#chunking-worked-example}

*Illustrative appendix — a short multi-paragraph passage EN→UK, showing how it becomes chunks and what context each
carries. Non-normative; token counts are illustrative.*

Source (chapter opening, four blocks; assume an illustrative paragraph budget of ~120 target tokens after context
reservation):

- **s10 (short):** `Mr <b>Hale</b> arrived late.`
- **s11 (short):** `"You are welcome at 7 Baker Street," she said.`
- **s12 (long, ~140 tokens):** a single dense paragraph describing the room, one sentence of which is
  `The <em>heavy velvet curtains</em>, drawn against the fog, made the parlour feel like a sealed box.`
- **s13 (short):** `He said nothing.`

**Import / mask.** `<b>…</b>` in s10 → `⟦g1⟧Hale⟦g2⟧` (also a locked name); `7 Baker Street` in s11 → locked term
`⟦g3⟧`; `<em>…</em>` in s12 → `⟦g4⟧…⟦g5⟧`. Each source is hashed.

**Packing.** s10 + s11 fit within the budget and pack into **Chunk 1**. s12 alone (~140 tokens) exceeds the ~120 budget,
so it becomes its own chunk and is **sentence-split**; the split points fall at sentence boundaries, and the boundary
logic keeps the `⟦g4⟧…⟦g5⟧` pair (and the words between them) inside one sub-segment rather than cutting it. s13 packs
into a following **Chunk 3** (here shown packing after s12's sub-segments close).

- **Chunk 1** = {s10, s11}. Context carried: system + Book Brief at the top edge; recently-present card **empty**
  (book start, nobody seen yet); map lines for the glossary terms for `⟦g1⟧⟦g2⟧` (`Hale → Гейл`) and `⟦g3⟧` (`Baker Street, 7 → Бейкер-стріт, 7`);
  window **empty** (nothing accepted yet); no TM hits; masked source {s10, s11} at the bottom
  edge.
- **Chunk 2** = s12 (sentence-split sub-segments s12a, s12b, …). Context carried: same system + brief; the card now
  names Hale as recently present; map line for `⟦g4⟧⟦g5⟧` if the enclosed phrase is a locked term (otherwise none injected);
  the window now holds the **source and target** pairs of s10 and s11 (the two just-accepted blocks, up to 2 or 3 by tier); any
  TM hits for repeated sentences; masked s12 sub-segments at the bottom edge. The `⟦g4⟧…⟦g5⟧` pair stays intact across
  the split.
- **Chunk 3** = {s13}. Context carried: the window now holds the last 2 or 3 accepted pairs (the tail of s12 and
  s11), giving the model the surrounding voice so `He said nothing.` is rendered consistently; masked s13 at the bottom
  edge.

At **chapter end** nothing is carried by a summary into the draft prompt: the next chapter's first window still holds
the last accepted pairs of this one, and the recently-present card names who was last seen here (Hale), so who is who
survives the chapter boundary. This is the
same edge-loaded, capped-window context described in `#chunking` and `#context-package`, applied block by block.

## worked-micro-example {#worked-micro-example}

*Illustrative appendix — one paragraph EN→UK through the stages. Non-normative; shows how the requirements above
compose.*

- **Source segment (s42):** `He opened the <em>old</em> door at 7 Baker Street.`
- **A Import / mask:** inline `<em>` → `⟦g1⟧…⟦g2⟧`; address `7 Baker Street` treated as a locked term via the name
  dictionary → `⟦g3⟧`. Masked source: `He opened the ⟦g1⟧old⟦g2⟧ door at ⟦g3⟧.` Source hashed. No numeral is masked
  (owner decision D-7).
- **B Prep:** pre-scan already proposed `Baker Street → Бейкер-стріт` (type: place; gender: n/a), locked. Style sheet:
  genre literary, register neutral, faithful↔natural mid.
- **C Context package:** system part (role, rules, style sheet) first; the recently-present card (empty at book
  start); the map line for `⟦g3⟧`; window (the prior sentence's source and UK translation); masked source in the last
  block.
- **C Draft (a batch of one):** `{"items":[{"id":"s42","target":"Він відчинив ⟦g1⟧старі⟦g2⟧ двері біля ⟦g3⟧."}]}`
- **C Unmask + hard gate:** placeholder multiset `{g1,g2,g3}` matches → pass; `⟦g1⟧⟦g2⟧` restore to `<em>…</em>`; `⟦g3⟧`
  restores to the locked target `Бейкер-стріт, 7`.
- **C Deterministic QA:** target-script check passes — the source is well above the 20-code-point floor and the
  target's letters are comfortably above the 0.60-Cyrillic-share threshold; no untranslated echo; length ratio within
  the EN→UK band; glossary term present; `confidence ≥ τ`. No numeral is masked in this build, so there is no
  separate number-preservation check (owner decision D-7).
- **C Judge:** `score ≥ τ_judge` (verdict advisory), no medium/high finding on the segment, and `confidence ≥ τ` →
  **ACCEPTED**. TM updated (context-keyed), the next window reads it, name dictionary unchanged, segment
  persisted atomically.
- **E Export:** target written back into the s42 text node; `<em>` and the address preserved; document language metadata
  updated to `uk`.
