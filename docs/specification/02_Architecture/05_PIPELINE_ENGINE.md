**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-09-27
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
pending segments of one unit, packed to at most `min(effectiveContext − reservedHeadroom, chunkBudgetSetting)`
estimated tokens (`min(8192 − reservedHeadroom, 1200)` with this build's constants) and capped at 8 segments on Fast,
4 on Balanced, 2 on Max, and 1 under Manual review — whichever limit is reached first; a unit boundary always closes
the current chunk and soft-resets the preceding-target window (ADR-0038).

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
6. **Masked source** at the bottom edge — exactly one source segment, with `⟦gN⟧` placeholders intact and its ordered
   sequence repeated immediately before `<Text>…</Text>`.

N (preceding blocks), whether TM/summary are included, and budget are dial-driven.

## tiered-loop {#tiered-loop}

Scoring is **per chunk** when the judge runs, but each check and each repair act on **one segment**: a failure — a
hard gate, a soft check, or a judge finding — repairs only the **offending segment**, and every repair call (directed
fix, reflect, improve, polish) returns exactly one segment's target, never a chunk-mate's (ADR-0038). Per chunk (see
`chunk-translate-loop.mermaid`):

1. **Draft** — one `chat` call for exactly one source segment; the response is exactly `{"target":"…"}`. The last
   three accepted targets may provide context only within their section. A malformed or wrong-shape response gets one
   structural repair containing its delimited rejected reply and parsing diagnosis. A valid target that fails the tag
   multiset gets one separate repair containing the source, rejected target, and required token order. No array, id
   map, prose, embedded-object, or text fallback is accepted, and neither repair recurses. A **context-match TM
   auto-reuse** skips this draft call (and the judge) entirely.
2. **Unmask + validate** — restore placeholders; **tag-multiset hard gate**
   (`03_DOCUMENT_MODEL.md#unmask-and-validate`). Failure → self-heal with a concrete finding.
3. **Deterministic QA gate** — run the checks in `#qa-checks`; compute `confidence`.
4. **Reviewer (dial-gated)** — when the dial enables it, one reviewer call per chunk and pass (Balanced one, Max two)
   over the drafted pairs whose checks passed; it answers per segment `ok`, edits or a rewrite. The code verifies each
   edit (the quote occurs exactly once after normalisation, the replacement keeps the `⟦gN⟧` tokens in order, the
   deterministic checks pass on the edited text, the set of blocking checks does not grow) and applies the verified
   ones; an edit whose quote was found but that was refused gets one directed fix naming the quote; a rewrite is taken
   only when every check passes. A reply that cannot be read, or a call that times out twice, flags its segments
   `reviewer-unavailable` instead of pausing the run.
5. **Accept**, per segment — `accept = hardGatesPass ∧ noSoftCheckFailed ∧ noVerifiedBlockerLeft`; a failed
   untranslated-echo check on a source under 20 code points of display text does not count as a failed soft check
   (`#qa-thresholds`). There is no score and no threshold: confidence only orders segments for review, and the review
   mode decides when the run pauses (ADR-0038; a model-call error instead of a quality failure is routed by
   `design.md` D3's failure-routing table, not this rule). → `ACCEPTED`.
6. **Self-heal** — otherwise, over up to **N QA re-entry rounds** (the repair budget: 1 / 2 / 3 by dial):
    - **Directed fix (1 call)** when QA or a refused reviewer edit produced *concrete* findings (tag mismatch, wrong script, dropped
      content, glossary miss) — inject the exact findings and ask for a targeted correction of this one segment; on a
      **tag-multiset mismatch** inject the expected placeholder multiset ("restore exactly: …").
    - **Reflect → improve (2 calls)** when the failure is *vague* quality (judge score low, no concrete finding) —
      reflect, then rewrite, each returning exactly one segment's target; an **optional monolingual polish** fires only
      when the improved target passes its hard gates, **no soft check failed**, and `confidence ∈ [τ − 0.05, τ)`
      (borderline).
    - Loop back through QA up to `N` rounds. With the judge on, a repaired target that passes its hard gates, fails
      no soft check and reaches `τ` is **judged again on its own** — a one-pair call labelled `s1` whose score and
      findings replace the chunk's for that segment; a target short of any of these goes to the next round with no
      judge call. A self-heal reply that is blank or cut off flags the segment at once; one that is not the required
      object fails its round (a self-heal call gets no format repair). Still failing → the offending segment (s)
      `FLAGGED`, keeping the last target that passed every hard gate as its machine translation.
7. **Persist + update memory** — atomically write the segment; update name dictionary, context-keyed TM, and
   preceding-target window; register any deferred-resolution items. Resume picks up at the **first PENDING** segment
   (FLAGGED is terminal-for-run, excluded from auto-resume). Update the rolling summary on its size-based trigger
   (`#rolling-summary`).

Every self-heal call passes through the `InferenceGate` (`04_LLM_INTEGRATION.md#inference-gate`).

## qa-checks {#qa-checks}

Deterministic, in `:pipeline`. Checks are of two kinds: **hard gates** (boolean; a failure cannot be accepted and is
excluded from `confidence`) and **soft checks** (each yields a margin in `[0,1]` that feeds `confidence`; a soft check
that fails outright blocks acceptance regardless of the blended value — see `#confidence`). No numeral is masked in
this build (owner decision D-7), so there is no number-preservation check; named-entity consistency comes from
locked-glossary masking plus the judge.

| Check                    | Kind          | Fails when                                                                                                                                                                                                              |
|--------------------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| tag integrity            | **hard gate** | the placeholder multiset differs, a paired placeholder's order or nesting is wrong, or a pair rule of ADR-0040 as amended is broken — a pair that held text in the source but holds none in the target, or a line-break token whose innermost enclosing pair changed (pre-QA) |
| refusal                  | **hard gate** | the target's display text is empty while the source's display text is not, or the target starts with a refusal phrase                                                                                                |
| target-script            | soft          | the share of the target's letters that are in the target language's script falls below threshold — respecting the foreign-passage policy so a deliberately kept passage is not scored as wrong-script                |
| untranslated-echo        | soft          | output ≈ source (nothing translated)                                                                                                                                                                                   |
| repetition-loop          | soft          | pathological m-gram repetition                                                                                                                                                                                         |
| omission by length ratio | soft          | target/source length ratio outside the expected band                                                                                                                                                                   |
| glossary compliance      | soft          | a locked term not rendered per the dictionary                                                                                                                                                                          |

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
(`12_PROMPT_CATALOG.md#rolling-summary-update`) is **opt-in**. Only the LLM-generated text is shown to drafts and in the
context panel as the running summary; the deterministic text (glossary lines and heading titles) is stored but never
presented as a summary, so a run without the model summary has none and says so. **No-chapter case** (TXT / single-chapter documents): the
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
output): draft 0.2 (the default), judge 0.1, directed fix 0.2, reflect→improve 0.35 to escape a bad phrasing, backward
revision 0.2, polish/pre-scan/summary 0.2. The default 0.2 is user-adjustable 0.0–2.0 in Generation settings
(`01_Product/07_SETTINGS.md#generation-tab`), and a review-desk retry can opt into 0.1 for its draft call when a lower
temperature is asked. Where a **reasoning level** is controllable (Ollama `think`, OpenAI reasoning params) every call
sets it low/off; the response handler strips any reasoning/thinking channel before parsing (`04_LLM_INTEGRATION.md`).
Per-call values live in `01_Product/12_PROMPT_CATALOG.md`.

The consistency stack this engine relies on is the **name/term dictionary** (`#name-term-dictionary`), the
**context-aware translation memory** (`#context-aware-tm`, matched by deterministic string similarity —
exact/context/fuzzy, never vector embeddings), and the **rolling bilingual summary** (`#rolling-summary`). There is no
embedding or RAG stage.

## quality-dial {#quality-dial}

A single "quality vs speed" dial (`FAST | BALANCED | MAX`) parameterizes the engine's **mechanics**. It does **not** own
the accept threshold `τ`: `τ` is owned by the **review-mode dial** (Unattended/Assisted/Manual) alone — a manual
Settings override is not offered in this build (DD-45, `01_Product/07_SETTINGS.md`); `τ_judge` equals `τ`.

| Parameter                          | FAST  | BALANCED | MAX   |
|------------------------------------|-------|----------|-------|
| chunk token budget                 | large | medium   | small |
| segments per chunk (cap)           | 8     | 4        | 2     |
| preceding-target blocks (N)        | 1     | 2        | 3     |
| repair budget (QA re-entry rounds) | 1     | 2        | 3     |
| judge runs                         | no    | yes      | yes   |
| backward revision runs             | no    | no       | yes   |

Manual review additionally caps every chunk at 1 segment, whatever the dial. The dial sets mechanics; `τ`/`τ_judge`
come from the review-mode dial (DD-45). The automatic-first, tiered-pipeline model this dial serves is ADR-0007.
Requirements: `FR-ALGO-*`, `FR-QA-*`.
