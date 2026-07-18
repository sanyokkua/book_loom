**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:pipeline`), QA **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
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
so formatting is preserved by construction.

| Correctness idea                                                                                              | Requirement |
|---------------------------------------------------------------------------------------------------------------|-------------|
| Preceding TARGET translation (not just source) is the main consistency lever, capped ~3 blocks.               | FR-ALGO-08  |
| Lost-in-the-middle → load-bearing content at prompt edges; inject only glossary terms occurring in the chunk. | FR-ALGO-04  |
| Context-aware TM: auto-reuse only when neighbours also match; exact-match is a hint; fuzzy is a suggestion.   | FR-ALGO-06  |
| Name/term dictionary carries gender for agreement and can hard-lock via placeholder substitution.             | FR-ALGO-05  |

## phase-a-import {#phase-a-import}

Parse the document into a skeleton and an ordered segment list; mask inline tags, locked terms, URLs, and **selected**
numerals to `⟦gN⟧` placeholders; hash the source; detect the content language.

| ID         | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-A1 | Parse to skeleton + ordered segments (FR-DOC-01).                                                                                                                                                                                                                                                                                                                                                                                                                 |
| FR-ALGO-A2 | Mask inline markup, locked glossary terms, URLs, and — **selectively** — only standalone/typographic numerals and numerals **inside locked terms**, leaving **prose numerals translatable** so they inflect and localize (FR-DOC-04). There is no separate number/named-entity preservation gate (D9): masked numerals are already covered by the placeholder-multiset hard gate, and named-entity consistency comes from locked-glossary masking plus the judge. |
| FR-ALGO-A3 | Hash each segment's source for change detection, TM keying, and resume. The TM/change-detection hash is `source_hash` over the **unmasked, NFC-normalized** source text.                                                                                                                                                                                                                                                                                          |
| FR-ALGO-A4 | Detect content language, ignoring declared metadata on disagreement (FR-IMPORT-03).                                                                                                                                                                                                                                                                                                                                                                               |

## phase-b-prep {#phase-b-prep}

One-time, automatic preparation: pre-scan entities to seed the name/term dictionary; derive a style sheet from the Book
Brief.

| ID         | Requirement                                                                                                                                                                                                                                                                                                                          |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-B1 | Pre-scan the book with the **LLM name/term pre-scan** call (`12_PROMPT_CATALOG.md#name-term-pre-scan`) to propose names/terms with type and provisional gender for the glossary; when the provider is offline/disabled, fall back to a **deterministic frequency + casing** extraction with `gender = unknown` (DD-46, FR-GLOSS-01). |
| FR-ALGO-B2 | Derive a style sheet (register, voice, policies, faithful↔natural bias) from the Book Brief for prompt construction.                                                                                                                                                                                                                 |

## phase-c-translate {#phase-c-translate}

Per chapter/unit: soft-reset the preceding-target window at chapter start; pack whole paragraphs into a chunk up to the
token budget; then run the per-chunk loop.

### chunking {#chunking}

Chunking is **paragraph-grouped**. Segments (whole paragraphs/blocks) are packed in document order into a chunk until
the next segment would exceed the chunk **token budget**, then the chunk closes and a new one opens. A paragraph is
never split across two chunks, so a translated block always corresponds to a single source block.

The budget is **derived from the provider's effective context window**, not a fixed constant, and the Generation **"
chunk budget" setting is only a CAP** on top of it (DD-44). The concrete arithmetic is in `#token-budget` below. Because
the reserved slices scale with the dial (larger preceding-target window and more TM context on Max), the effective
paragraph budget is smaller on Max and larger on Fast; this is why the quality dial's "chunk size" row moves inversely
to context richness (`#quality-dial-mapping`).

**Overflow only** triggers finer splitting: a single paragraph that alone exceeds the budget is **sentence-split** at
source-language sentence boundaries, and only the offending paragraph is split — neighbouring paragraphs are unaffected.
Splitting **never cuts a masked inline tag**: a `⟦gN⟧` placeholder and its partner (for paired markup such as
`⟦g1⟧…⟦g2⟧`) are treated as atomic, so a sentence boundary that would fall between an open/close pair is pushed outward
to keep the pair — and the text it wraps — within one chunk. This preserves the tag multiset per chunk and keeps the
unmask hard gate (`FR-ALGO-C5`) local.

**Oversized unsplittable unit.** If a unit is genuinely unsplittable — a single sentence, or a run bounded by a paired
`⟦g1⟧…⟦g2⟧` group, that alone exceeds the budget and has no interior sentence boundary which keeps every tag pair
intact — it is **not** split. It is sent as its **own over-budget chunk with degraded context** (the reserved slices are
trimmed to fit, dropping preceding-target and TM context first); a tag pair is **never** broken to force a fit, and the
degraded chunk is **logged** so diagnostics and the review path can see it (`FR-ALGO-C2b`).

### token-budget {#token-budget}

BookLoom ships **no tokenizer** (DD-44), so token counts are **estimated deterministically** from character counts. For
a piece of text:

```
estTokens(text) = ceil( ( chars(text) / K(script) ) × (1 + safetyMargin) )
```

`K(script)` is a chars-per-token divisor chosen by the text's dominant writing system; `safetyMargin` is a fixed
over-estimation cushion (default **0.15**) that biases every estimate high so a chunk never silently overflows the real
window. Source text is measured with the **source** script's `K`; the reserved output allowance is measured with the
**target** script's `K`. The estimator always rounds up.

| Writing system (examples)              | K (chars/token) |
|----------------------------------------|-----------------|
| Latin — English, German, most European | 4.0             |
| Cyrillic — Ukrainian, Russian          | 3.0             |
| Greek                                  | 3.5             |
| Arabic, Hebrew                         | 3.0             |
| CJK — Chinese, Japanese, Korean        | 1.5             |
| Mixed / unknown (conservative)         | 3.0             |

The **chunk budget** is then a `min` of the context-derived budget and the user cap:

```
chunkBudget = min( effectiveContext − reservedHeadroom , chunkBudgetSetting )
```

`effectiveContext` is the per-provider effective context window (DD-44; Ollama `num_ctx` / `/api/show` → discovery → the
provider profile's manual "effective context (tokens)" field → conservative default). `reservedHeadroom` is the summed
`estTokens` of everything else the prompt carries — system frame and Book Brief, rolling bilingual summary, injected
glossary terms, the preceding-target window, and TM hits — **plus** the expected target-output allowance (target-script
`K`, larger for expanding pairs such as EN→UK). The Generation **"chunk budget" setting is a CAP**: it can only lower
the budget below what the window allows, never raise it above `effectiveContext − reservedHeadroom`. Paragraphs are
packed until the next one would push the running `estTokens` over `chunkBudget`.

Chunk boundaries prefer chapter/unit boundaries. At chapter start the **preceding-target window is soft-reset** so stale
local phrasing from the previous chapter does not leak in, while the rolling bilingual summary carries book-level facts
across the boundary. Context is **edge-loaded**: the load-bearing items (instruction frame and the masked source to
translate) sit at the two ends of the prompt to counter lost-in-the-middle (`#context-package`). The **preceding-target
window is capped at ~3 blocks** (dial-driven, `#quality-dial-mapping`) — enough to anchor terminology and voice without
crowding out the source.

| ID          | Requirement                                                                                                                                                                          |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-C1  | Pack whole paragraphs into a chunk up to `chunkBudget = min(effectiveContext − reservedHeadroom, chunkBudgetSetting)` without splitting a paragraph across chunks (FR-ALGO-02).      |
| FR-ALGO-C2  | Sentence-split a single paragraph only when it alone exceeds the budget, never cutting a masked inline-tag pair (FR-ALGO-03).                                                        |
| FR-ALGO-C2b | Send a genuinely unsplittable over-budget unit as its own over-budget chunk with degraded context rather than cutting a masked inline-tag pair; log the degraded chunk (FR-ALGO-03). |
| FR-ALGO-C3  | Soft-reset the preceding-target window at chapter start; cap it at ~3 blocks (FR-ALGO-08).                                                                                           |

### context-package {#context-package}

Assemble the prompt with load-bearing items at the EDGES (FR-ALGO-04). The package contains, in order of placement
priority:

| Component                    | Role                                                       |
|------------------------------|------------------------------------------------------------|
| System + brief (style sheet) | Instruction frame; placed at an edge.                      |
| Rolling bilingual summary    | Book-so-far context for tone/terminology.                  |
| Relevant glossary terms      | Only terms occurring in the chunk are injected.            |
| Preceding-target window      | Up to ~3 prior TARGET blocks — the main consistency lever. |
| TM hits                      | Context/exact/fuzzy matches per the reuse policy.          |
| Masked source                | The chunk's segments to translate; placed at an edge.      |

### chunk-loop {#chunk-loop}

For each chunk (see `chunk-translate-loop.mermaid`):

| ID          | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-C4  | Request the draft as a JSON array keyed by segment id. On any **id mismatch** (a missing or duplicate id) re-issue **per-segment calls for the missing ids only**, reuse the valid returned segments as-is, **ignore extra/unknown ids**, and treat id **order as irrelevant**. A multi-segment draft that is wholly **unparseable** falls back to **per-segment** calls — the text fallback never applies to a multi-segment response, only to single-segment calls (FR-ALGO-09). |
| FR-ALGO-C4b | A **context-match TM auto-reuse** skips the draft and judge calls but still passes the unmask hard gate and the deterministic QA gate before acceptance (FR-ALGO-06).                                                                                                                                                                                                                                                                                                              |
| FR-ALGO-C5  | Unmask and validate the placeholder multiset as a hard gate; a mismatch cannot be accepted (FR-DOC-05, FR-QA-04).                                                                                                                                                                                                                                                                                                                                                                  |
| FR-ALGO-C6  | Run the deterministic QA gate and compute the `confidence` scalar — the documented weighted blend of the **soft** QA-check margins (hard gates and judge excluded; see `02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`) (FR-QA-01).                                                                                                                                                                                                                                              |
| FR-ALGO-C7  | Accept the chunk's segments when `hardGatesPass ∧ confidence ≥ τ ∧ (judgeOff ∨ judgeScore ≥ τ_judge)`; with the judge off this reduces to `hardGatesPass ∧ confidence ≥ τ`. `τ` is owned by the **review-mode dial** (not the quality dial); `τ_judge` defaults to `τ`. The judge's `score` decides; its `verdict` is advisory/logging only (DD-45).                                                                                                                               |
| FR-ALGO-C8  | Otherwise enter self-heal (see below) for up to **N QA re-entry rounds** (the repair budget from the quality dial; `N=0` flags on the first failure); if still failing, mark the **offending segment(s)** FLAGGED.                                                                                                                                                                                                                                                                 |
| FR-ALGO-C9  | On acceptance, update the name dictionary, the context-keyed TM, and the preceding-target window; register deferred-resolution items; persist atomically. Resume picks up at the **first PENDING** segment; FLAGGED is terminal-for-run and excluded from auto-resume (FR-RESUME-01).                                                                                                                                                                                              |
| FR-ALGO-C10 | Update the rolling bilingual summary on a **size-based trigger — every K accepted blocks or at chapter end, whichever comes first** (FR-ALGO-07; `02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`).                                                                                                                                                                                                                                                                         |

### self-heal {#self-heal}

QA and the judge score **per chunk**, but a failure flags only the **offending segment (s)**. A directed fix re-renders
the **whole chunk** (to keep intra-chunk consistency), and the result is re-QA'd **per segment**. Self-heal fires only
on a QA/judge failure (DD-16) and consumes the repair budget **N = the number of QA re-entry rounds**; each round takes
exactly one path:

| Path              | Trigger                                            | Calls                                                                                                                                                                                                                   |
|-------------------|----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Directed fix      | Concrete QA/judge findings exist                   | 1 call injecting the findings, asking the model to correct exactly those. On a **tag-multiset mismatch** the instruction is specialised to inject the expected placeholder multiset ("restore exactly: `⟦g1⟧ ⟦g2⟧ …`"). |
| Reflect → improve | Only a vague quality concern (no concrete finding) | 2 calls (reflect, then improve), followed by an **optional monolingual polish** — triggered only when the post-improve QA still leaves the segment **borderline** (hard gates pass but `confidence` in `[τ − ε, τ)`).   |

`N=0` flags on the first failure with no repair round; after N rounds the still-failing segment (s) are FLAGGED. This
realizes the automatic-first, tiered self-heal model (ADR-0007).

| ID          | Requirement                                                                                                                                                                              |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-C11 | Prefer directed fix when concrete findings exist; use reflect→improve otherwise; specialise the directed fix for a tag-multiset mismatch by injecting the expected placeholder multiset. |
| FR-ALGO-C12 | Bound total repair rounds by the repair budget N from the quality dial; `N=0` = flag on first failure.                                                                                   |
| FR-ALGO-C13 | Score per chunk, flag only the offending segment(s), re-render the chunk on a directed fix, and re-QA per segment.                                                                       |

## phase-d-backward-revision {#phase-d-backward-revision}

Optional whole-book pass (enabled by the Max dial or the export toggle). The sweep **proposes, never overwrites**:
user-edited `REVISED` segments are **protected** and change only with explicit user opt-in.

| ID         | Requirement                                                                                                                                                                                                                            |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-D1 | Resolve deferred-resolution items using full-book facts and re-render affected earlier segments to REVISED. User-`REVISED` segments are protected: the sweep proposes a change but does not overwrite them.                            |
| FR-ALGO-D2 | Run a global term-consistency sweep **bounded to segments that contain a swept term**. Apply **deterministic string substitution** for **locked** terms; invoke an **LLM re-render only** where gender/agreement deferrals require it. |
| FR-ALGO-D3 | A `REVISED → REVISED` transition (re-sweeping an already user-edited segment) happens **only with user opt-in**.                                                                                                                       |

## phase-e-export {#phase-e-export}

| ID         | Requirement                                                                                                            |
|------------|------------------------------------------------------------------------------------------------------------------------|
| FR-ALGO-E1 | Write each target string back into its DOM/AST node (FR-EXPORT-02).                                                    |
| FR-ALGO-E2 | Repackage in the source format (EPUB mimetype-first / FB2 encoding-preserving / MD / TXT) and validate (FR-EXPORT-03). |

## quality-dial-mapping {#quality-dial-mapping}

A single quality-vs-speed dial sets pipeline **mechanics** (FR-ALGO-11). It does **not** set the accept threshold `τ`:
`τ` is owned solely by the **review-mode dial** (Unattended/Assisted/Manual), with an advanced Manual-settings override
at highest precedence (DD-45, `07_SETTINGS.md`). `τ_judge` defaults to `τ`.

| Parameter                            | Fast       | Balanced | Max      |
|--------------------------------------|------------|----------|----------|
| Chunk size (paragraphs/tokens)       | Larger     | Medium   | Smaller  |
| Preceding-target blocks              | Fewer (≤1) | ~2       | ~3 (cap) |
| Repair budget N (QA re-entry rounds) | 1          | 2        | 3        |
| Judge runs                           | Off        | On       | On       |
| Backward revision runs               | Off        | Off      | On       |

## generation-parameters {#generation-parameters}

Every model-facing call uses **low temperature** for fidelity: the goal is a faithful, deterministic rendering, not
creative variation, and low temperature reduces hallucination, drift off the glossary, and the
paraphrase-away-from-source failure mode. The per-phase guidance is:

| Phase                                    | Temperature              | Rationale                                                                                                          |
|------------------------------------------|--------------------------|--------------------------------------------------------------------------------------------------------------------|
| Draft translation                        | ~0.2 (default)           | Faithful and reproducible; enough flexibility for fluent target phrasing without inventing content.                |
| Judge / deterministic-QA-assisting judge | ~0.0–0.2                 | A scorer should be near-deterministic so the same draft yields the same verdict; τ comparisons stay stable.        |
| Directed fix                             | ~0.2                     | A targeted correction of named findings; stay close to the accepted draft.                                         |
| Reflect → improve                        | slightly higher (≤ ~0.4) | The failure is vague quality; a little more latitude helps the rewrite escape a bad local phrasing. Still bounded. |
| Backward revision                        | ~0.2                     | Consistency alignment across the book; determinism preferred.                                                      |

The default is **0.2**, user-adjustable in Generation settings over the range 0.0–2.0 (`07_SETTINGS.md#generation-tab`);
the "lower temperature for this retry" option nudges a repair attempt further toward determinism. Where the provider
exposes a **reasoning level** (Ollama `think`, OpenAI reasoning params), translation and judge calls run it **low/off**
to cut latency and noise; any separate reasoning channel is ignored on parse. Concrete per-call parameter values and
output schemas are catalogued in `12_PROMPT_CATALOG.md`.

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

- **Chunk 1** = {s10, s11}. Context carried: system + Book Brief at the top edge; rolling summary **empty**
  (chapter/book start); glossary terms for `⟦g1⟧⟦g2⟧` (`Hale → Гейл`) and `⟦g3⟧` (`Baker Street, 7 → Бейкер-стріт, 7`);
  preceding-target window **empty** (soft-reset at chapter start); no TM hits; masked source {s10, s11} at the bottom
  edge.
- **Chunk 2** = s12 (sentence-split sub-segments s12a, s12b, …). Context carried: same system + brief; rolling summary
  still empty; glossary term for `⟦g4⟧⟦g5⟧` if the enclosed phrase is a locked term (otherwise none injected);
  preceding-target window now holds the **target** renderings of s10 and s11 (the two just-accepted blocks, ≤ ~3); any
  TM hits for repeated sentences; masked s12 sub-segments at the bottom edge. The `⟦g4⟧…⟦g5⟧` pair stays intact across
  the split.
- **Chunk 3** = {s13}. Context carried: preceding-target window now holds the last ~3 target blocks (the tail of s12 and
  s11), giving the model the surrounding voice so `He said nothing.` is rendered consistently; masked s13 at the bottom
  edge.

At **chapter end** the rolling bilingual summary is updated from the accepted target so the next chapter opens with
book-level facts (who Hale is, the Baker Street setting) even though the preceding-target window resets. This is the
same edge-loaded, capped-window context described in `#chunking` and `#context-package`, applied block by block.

## worked-micro-example {#worked-micro-example}

*Illustrative appendix — one paragraph EN→UK through the stages. Non-normative; shows how the requirements above
compose.*

- **Source segment (s42):** `He opened the <em>old</em> door at 7 Baker Street.`
- **A Import / mask:** inline `<em>` → `⟦g1⟧…⟦g2⟧`; address `7 Baker Street` treated as a locked term via the name
  dictionary → `⟦g3⟧`; number `7` protected inside the locked term. Masked source:
  `He opened the ⟦g1⟧old⟦g2⟧ door at ⟦g3⟧.` Source hashed.
- **B Prep:** pre-scan already proposed `Baker Street → Бейкер-стріт` (type: place; gender: n/a), locked. Style sheet:
  genre literary, register neutral, faithful↔natural mid.
- **C Context package:** system+brief at edge; rolling summary (empty at book start); glossary term for `⟦g3⟧`;
  preceding-target window (prior sentence's UK translation); masked source at the other edge.
- **C Draft (JSON keyed by id):** `{"s42": "Він відчинив ⟦g1⟧старі⟦g2⟧ двері біля ⟦g3⟧."}`
- **C Unmask + hard gate:** placeholder multiset `{g1,g2,g3}` matches → pass; `⟦g1⟧⟦g2⟧` restore to `<em>…</em>`; `⟦g3⟧`
  restores to the locked target `Бейкер-стріт, 7`.
- **C Deterministic QA:** target language = Ukrainian (pass, policy-aware, above the min-length + Lingua-confidence
  floor); no untranslated echo; length ratio within the EN→UK band; glossary term present; `confidence ≥ τ`. There is
  **no** separate number/named-entity check — the number `7` is preserved by riding inside the locked term `⟦g3⟧` (D9).
- **C Judge:** `score ≥ τ_judge` (verdict advisory) and `confidence ≥ τ` → **ACCEPTED**. TM updated (context-keyed),
  preceding-target window advanced, name dictionary unchanged, segment persisted atomically.
- **E Export:** target written back into the s42 text node; `<em>` and the address preserved; document language metadata
  updated to `uk`.
