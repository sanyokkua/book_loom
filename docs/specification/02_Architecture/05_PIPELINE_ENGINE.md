**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
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
across chunks, so one target block always maps back to one source block.

The budget is **derived from the provider's effective context window** rather than a fixed constant, and the Generation
"chunk budget" setting is only a **cap** on it (DD-44). Because BookLoom ships no tokenizer, token counts are
**estimated deterministically** from characters:

```
estTokens(text) = ceil( ( chars(text) / K(script) ) × (1 + safetyMargin) )
chunkBudget     = min( effectiveContext − reservedHeadroom , chunkBudgetSetting )
```

`K(script)` is a chars-per-token divisor by dominant writing system — Latin 4.0, Cyrillic 3.0, Greek 3.5, Arabic/Hebrew
3.0, CJK 1.5, mixed/unknown 3.0 (conservative) — and `safetyMargin` is a fixed over-estimation cushion (default
**0.15**); the estimate always rounds up (full table and rationale in
`01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`). `effectiveContext` comes from the per-provider effective context
window (Ollama `num_ctx` / `/api/show` → discovery → the provider profile's manual "effective context (tokens)" field →
conservative default). From it the packer subtracts `reservedHeadroom` — the summed `estTokens` of the system frame and
Book Brief, rolling bilingual summary, injected glossary terms, the preceding-target window, and TM hits, **plus** an
allowance for the target output (target-script `K`, often longer than the source for pairs like EN→UK). The
`chunkBudgetSetting` can only **lower** the result, never raise it above `effectiveContext − reservedHeadroom`. The
reserved slices are dial-driven (`#quality-dial`): a richer preceding-target window and more TM context on `MAX` shrink
the effective paragraph budget, which is why the dial's "chunk token budget" row moves inversely to context richness.

A single segment that alone exceeds the budget is **sentence-split** using ICU4J `BreakIterator` for the source
language, and **only on overflow**; neighbouring segments are untouched. The split **never cuts a masked inline tag**: a
`⟦gN⟧` placeholder and its partner in a paired-markup group (`⟦g1⟧…⟦g2⟧`) are atomic, so a candidate boundary that would
fall between an open/close pair is moved outward to keep the pair and its wrapped text in one sub-segment. This
preserves the per-chunk placeholder multiset that the unmask hard gate checks
(`03_DOCUMENT_MODEL.md#unmask-and-validate`). A genuinely **unsplittable over-budget unit** (a single sentence, or a
tag-pair-bounded run, with no interior boundary that keeps every tag pair intact) is **not** split: it is sent as its
**own over-budget chunk with degraded context** (preceding-target and TM slices trimmed first), a tag pair is never
broken to fit, and the degraded chunk is **logged**.

Chunk boundaries prefer chapter/unit boundaries; the preceding-target window is soft-reset at chapter start
(`#rolling-summary`). A worked multi-paragraph packing example (including the sentence-split-with-tag case) is in
`01_Product/05_TRANSLATION_ALGORITHM.md#chunking-worked-example`.

## context-package-assembler {#context-package-assembler}

For each chunk the assembler builds a prompt whose **load-bearing items sit at the EDGES** to counter
lost-in-the-middle. Order:

1. **System + Book Brief** (top edge) — languages, genre, register, voice/era, audience, name policy, foreign-passage
   policy, unit policy, faithful↔natural position.
2. Rolling **bilingual summary** of the book/chapter so far.
3. Relevant **glossary** — only the terms that occur in this chunk (never the whole dictionary).
4. **Preceding-target window** — the last ~N translated (target) blocks, capped ~3; this is the main consistency lever,
   not the source.
5. **TM hits** — exact/context/fuzzy suggestions for segments in the chunk.
6. **Masked source** at the bottom edge — the segments to translate, keyed by segment id, with `⟦gN⟧` placeholders
   intact.

N (preceding blocks), whether TM/summary are included, and budget are dial-driven.

## tiered-loop {#tiered-loop}

Scoring is **per chunk**, but a failure flags only the **offending segment (s)**; a directed fix re-renders the whole
chunk and the result is re-QA'd per segment. Per chunk (see `chunk-translate-loop.mermaid`):

1. **Draft** — one `chat` call; model returns a JSON array keyed by segment id. On any **id mismatch** (missing or
   duplicate id) re-issue per-segment calls for the **missing ids only**, reuse valid returned segments, ignore
   extra/unknown ids, and treat order as irrelevant. A wholly **unparseable multi-segment** draft falls back to
   **per-segment** calls (the **text fallback applies to single-segment calls only**). A **context-match TM auto-reuse**
   skips this draft call (and the judge) entirely.
2. **Unmask + validate** — restore placeholders; **tag-multiset hard gate**
   (`03_DOCUMENT_MODEL.md#unmask-and-validate`). Failure → self-heal with a concrete finding.
3. **Deterministic QA gate** — run the checks in `#qa-checks`; compute `confidence`.
4. **Judge (dial-gated)** — if QA passes and the dial enables it, an LLM-as-judge quality score; `score` decides,
   `verdict` is advisory.
5. **Accept** — `accept = hardGatesPass ∧ confidence ≥ τ ∧ (judgeOff ∨ judgeScore ≥ τ_judge)`; with the judge off,
   `accept = hardGatesPass ∧ confidence ≥ τ`. `τ` is owned by the **review-mode dial** (not this engine's quality dial);
   `τ_judge` defaults to `τ`. → `ACCEPTED`.
6. **Self-heal** — otherwise, over up to **N QA re-entry rounds** (repair budget; `N=0` flags on first failure):
    - **Directed fix (1 call)** when QA/the judge produced *concrete* findings (tag mismatch, wrong language, dropped
      content, glossary miss) — inject the exact findings and ask for a targeted correction; on a **tag-multiset
      mismatch** inject the expected placeholder multiset ("restore exactly: …").
    - **Reflect → improve (2 calls)** when the failure is *vague* quality (judge score low, no concrete finding) —
      reflect, then rewrite; an **optional monolingual polish** fires only when post-improve QA is still **borderline**
      (`confidence ∈ [τ − ε, τ)`).
    - Loop back through QA up to `N` rounds. Still failing → the offending segment (s) `FLAGGED`.
7. **Persist + update memory** — atomically write the segment; update name dictionary, context-keyed TM, and
   preceding-target window; register any deferred-resolution items. Resume picks up at the **first PENDING** segment
   (FLAGGED is terminal-for-run, excluded from auto-resume). Update the rolling summary on its size-based trigger
   (`#rolling-summary`).

Every self-heal call passes through the `InferenceGate` (`04_LLM_INTEGRATION.md#inference-gate`).

## qa-checks {#qa-checks}

Deterministic, in `:pipeline`. Checks are of two kinds: **hard gates** (boolean; a failure cannot be accepted and is
excluded from `confidence`) and **soft checks** (each yields a margin in `[0,1]` that feeds `confidence`). There is
**no** number/named-entity preservation check (D9): masked numerals are already covered by the tag-multiset hard gate,
and named-entity consistency comes from locked-glossary masking plus the judge.

| Check                    | Kind          | Fails when                                                                                                                            |
|--------------------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------|
| tag integrity            | **hard gate** | placeholder multiset differs (pre-QA)                                                                                                 |
| refusal                  | **hard gate** | output is a model refusal / meta-comment                                                                                              |
| target-language          | soft          | output not in the target language (Lingua) — respecting foreign-passage policy so a deliberately kept passage is not "wrong language" |
| untranslated-echo        | soft          | output ≈ source (nothing translated)                                                                                                  |
| repetition-loop          | soft          | pathological m-gram repetition                                                                                                        |
| omission by length ratio | soft          | target/source length ratio outside the expected band                                                                                  |
| glossary compliance      | soft          | a locked term not rendered per the dictionary                                                                                         |

### qa-thresholds {#qa-thresholds}

Each soft check is made **testable** by an explicit threshold. Defaults:

| Check                            | Threshold (default)                                                                              | Notes                                                                                                                           |
|----------------------------------|--------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| untranslated-echo                | normalized similarity(target, source) **≥ 0.90** → fail                                          | normalized Levenshtein / token-overlap on NFC-normalized, case-folded text                                                      |
| repetition-loop                  | any **m-gram (m = 3)** repeated **≥ k = 3** consecutive times → fail                             | m/k tunable; guards against decode loops                                                                                        |
| omission by length ratio         | `len(target)/len(source)` (chars) **outside the per-pair band** → fail                           | band is **per script / language-pair** (below), **widened for short segments**                                                  |
| target-language (gate condition) | check fires **only** when `len(source) ≥ 20 chars` **and** Lingua relative confidence **≥ 0.60** | below the min-length + confidence floor the check is skipped (treated as pass) so short/ambiguous fragments are not mis-flagged |
| glossary compliance              | every locked in-chunk term present in the target per its dictionary rendering                    | binary per term; margin = fraction of locked terms honoured                                                                     |

**Omission length-ratio bands** (`target/source` char ratio; widen the lower/upper bound outward by ×0.5 / ×2 for
**short** source segments `< 25 chars`):

| Language-pair class           | Band `[lo, hi]` |
|-------------------------------|-----------------|
| Latin → Cyrillic (e.g. EN→UK) | `[0.7, 1.8]`    |
| Same-script (Latin → Latin)   | `[0.6, 1.7]`    |
| Latin → CJK                   | `[0.2, 1.0]`    |
| CJK → Latin                   | `[1.0, 5.0]`    |
| Default / unknown             | `[0.5, 2.5]`    |

**Foreign-keep vs echo.** A segment is treated as a legitimate **foreign-keep** — its **untranslated-echo** and
**target-language** checks suppressed — **only when it matches a pre-detected foreign span from import** (or the
foreign-passage policy explicitly keeps it). An unmarked segment that simply echoes the source is still failed by
untranslated-echo; foreign-keep is not a blanket excuse for an untranslated output.

### confidence {#confidence}

`confidence ∈ [0,1]` is a **documented weighted blend of the soft-check margins only** — hard gates are excluded (they
are pre-gate booleans) and the **judge score is NOT folded in** (it is compared separately against `τ_judge`). For each
soft check, `margin_i ∈ [0,1]` is how far the observed value sits from that check's failure cutoff, clamped to `[0,1]`
(1.0 = comfortably passing, 0.0 = at/over the cutoff). The blend:

```
confidence = 0.30·m_glossary + 0.25·m_lengthRatio + 0.20·m_targetLanguage
           + 0.15·m_untranslatedEcho + 0.10·m_repetitionLoop
```

(weights sum to 1.0). A skipped check (e.g. target-language below its firing floor) contributes `margin = 1.0`.
`confidence` is then compared against the review-mode dial's `τ`; the judge, when run, is a separate
`judgeScore ≥ τ_judge` term in the accept rule (`#tiered-loop`).

## name-term-dictionary {#name-term-dictionary}

A per-project dictionary of names/terms with **type** and **gender** (for target-language agreement). Built by the Prep
pre-scan (`pipeline` phase B) and grown during translation. Terms can be **hard-locked**: replaced by a placeholder
before inference and substituted back on unmask, guaranteeing an exact rendering. Persisted in `glossary`
(`06_DATA_MODEL_SQLITE.md#glossary`).

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

## rolling-summary {#rolling-summary}

A rolling **bilingual** summary is maintained and carried into the context package. It is soft-reset (preceding-target
window cleared) at chapter start so cross-chapter drift does not leak stale local context, while the summary preserves
book-level facts. Store: `06_DATA_MODEL_SQLITE.md#summaries`.

**Update trigger** is **size-based: every K accepted blocks OR at chapter end, whichever comes first** (default
`K = 20`). This bounds how stale the summary can get inside a long chapter. **Condensation rule:** if appending a
chapter would push the summary over its token budget, the summary is **condensed** — oldest narrative detail is
dropped/re-summarized first while character, place, and terminology facts are retained — so it always fits its reserved
slice.

**Default is a deterministic summary** (accumulated key facts truncated to budget); the **LLM-generated** variant
(`12_PROMPT_CATALOG.md#rolling-summary-update`) is **opt-in**. **No-chapter case** (TXT / single-chapter documents): the
whole document is treated as one chapter, the every-`K`-blocks trigger is the only periodic trigger, and
**end-of-document** acts as the chapter-end trigger.

## deferred-resolution-and-backward-revision {#deferred-resolution}

When a chunk cannot be finalized without facts revealed later (a gendered pronoun for a not-yet-introduced character, a
term whose canonical form is decided later), the engine records a **deferred-resolution** entry. Deferrals are detected
two ways: the draft/judge calls may **emit** an optional `deferrals:[{segmentId,reason}]` array
(`12_PROMPT_CATALOG.md`), and a deterministic **unknown-gender heuristic** flags a target segment that references a
glossary person whose dictionary gender is still `unknown`.

In the optional **backward-revision** pass (dial-gated, whole-book), deferred items are resolved with full-book facts
and affected earlier segments are re-rendered → `REVISED`, and a global **term-consistency sweep** normalizes glossary
usage. The sweep **proposes, never overwrites**: **user-`REVISED` segments are protected** and change only with explicit
user opt-in (a `REVISED → REVISED` transition). The sweep is **bounded to segments containing a swept term**; it applies
**deterministic string substitution for locked terms** and invokes an **LLM re-render only** for gender/agreement
deferrals.

## prompt-builder {#prompt-builder}

Prompts are built from a **template + slots**: fixed instruction scaffolding with named slots (`{brief}`, `{summary}`,
`{glossary}`, `{precedingTarget}`, `{tmHits}`, `{sourceSegments}`, `{findings}`). Directed-fix and reflect→improve use
dedicated templates that add a `{findings}` or `{reflection}` slot. Templates are data, so the quality dial and policies
parameterize the prompt without code changes. The concrete system/user templates for every pipeline call — draft, judge,
directed fix, reflect→improve, backward revision, book-brief/tone setup, and rolling-summary update — with their
injected variables, required-vs-optional fields, parameters, and expected JSON shapes, are the normative catalogue in
`01_Product/12_PROMPT_CATALOG.md`.

## generation-parameters {#generation-parameters}

Inference runs at **low temperature** for fidelity (fewer hallucinations, less drift off the glossary, more reproducible
output): draft ~0.2 (the default), judge/QA ~0.0–0.2, directed fix ~0.2, reflect→improve slightly higher (≤ ~0.4) to
escape a bad phrasing, backward revision ~0.2. The default 0.2 is user-adjustable 0.0–2.0 in Generation settings
(`01_Product/07_SETTINGS.md#generation-tab`), and a repair attempt can opt into an even lower "temperature for this
retry". Where a **reasoning level** is controllable (Ollama `think`, OpenAI reasoning params) translation and judge
calls set it low/off; the response handler strips any reasoning/thinking channel before parsing
(`04_LLM_INTEGRATION.md`). Per-call values live in `01_Product/12_PROMPT_CATALOG.md`.

The consistency stack this engine relies on is the **name/term dictionary** (`#name-term-dictionary`), the
**context-aware translation memory** (`#context-aware-tm`, matched by deterministic string similarity —
exact/context/fuzzy, never vector embeddings), and the **rolling bilingual summary** (`#rolling-summary`). There is no
embedding or RAG stage.

## quality-dial {#quality-dial}

A single "quality vs speed" dial (`FAST | BALANCED | MAX`) parameterizes the engine's **mechanics**. It does **not** own
the accept threshold `τ`: `τ` is owned by the **review-mode dial** (Unattended/Assisted/Manual), Manual-settings
override highest (DD-45); `τ_judge` defaults to `τ`.

| Parameter                          | FAST  | BALANCED | MAX   |
|------------------------------------|-------|----------|-------|
| chunk token budget                 | large | medium   | small |
| preceding-target blocks (N)        | 1     | 2        | 3     |
| repair budget (QA re-entry rounds) | 1     | 2        | 3     |
| judge runs                         | no    | yes      | yes   |
| backward revision runs             | no    | no       | yes   |

The dial sets mechanics; `τ`/`τ_judge` come from the review-mode dial (DD-45). The automatic-first, tiered-pipeline
model this dial serves is ADR-0007. Requirements: `FR-ALGO-*`, `FR-QA-*`.
