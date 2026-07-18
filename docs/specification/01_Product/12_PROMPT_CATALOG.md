**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:pipeline`, `:llm`), QA **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/specification/01_Product/07_SETTINGS.md`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`, `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`

# Prompt Catalog

This document is the normative catalogue of every LLM call the translation pipeline makes. For each call it gives the
concrete **system** and **user (task)** prompt templates, the injected variables (with **required vs optional**), the
request **parameters** (temperature, output format/schema, reasoning level), and the **expected output shape**. It
complements the pipeline text in `05_TRANSLATION_ALGORITHM.md` and `02_Architecture/05_PIPELINE_ENGINE.md`; the
prompt-building mechanics (template + slots) live in `02_Architecture/05_PIPELINE_ENGINE.md#prompt-builder`.
Placeholders are written `{{variable}}`; the masked inline-tag tokens the model must preserve are written `⟦gN⟧`.

The consistency machinery referenced throughout is the **name/term dictionary**, the **context-aware translation
memory** (exact/context/fuzzy by deterministic string similarity — not vector embeddings), and the **rolling bilingual
summary**. There is no embedding or RAG step anywhere in this catalogue.

## prompt-construction {#prompt-construction}

Every prompt is assembled from a fixed template with named slots. Slots that carry no content for a given chunk collapse
to a literal `(none)` rather than being left dangling, and any purely optional block may be omitted entirely; the model
is told to translate what is present, not to expect every slot. **Load-bearing slots sit at the prompt edges** (the
instruction frame at the top, the masked source at the bottom) to counter lost-in-the-middle
(`05_TRANSLATION_ALGORITHM.md#context-package`).

How each source of context is built and adapted:

- **Style sheet / Book Brief** — the register, voice/era, audience, faithful↔natural bias, name policy, footnote/unit
  policy, and foreign-passage policy derived from the Book Brief (`#book-brief-tone-setup`) are injected into
  `{{styleSheet}}`. Required; if the user accepted defaults it is the default style sheet, never empty.
- **Glossary / name dictionary injection** — only the terms **occurring in the current chunk** are injected into
  `{{glossaryTerms}}` (never the whole dictionary), each with its target rendering, type, and gender for agreement.
  Hard-locked terms are already masked to `⟦gN⟧` and are additionally listed so the model knows their meaning. Optional:
  empty chunk → `(none)`.
- **Translation-memory matches** — injected into `{{tmHits}}` labelled by kind: a **context match** (source and
  neighbours match) is offered as a reuse candidate, an **exact match** as a hint, a **fuzzy match** (deterministic
  string similarity) as a suggestion only. Optional.
- **Rolling bilingual summary** — the book/chapter-so-far summary is injected into `{{rollingSummary}}` for tone and
  terminology continuity. Optional: empty at book start → `(none)`.
- **Preceding-target window** — the last ~3 accepted **target** blocks (dial-capped, soft-reset at chapter start) are
  injected into `{{precedingTarget}}`. This is the main consistency lever; the model is told to continue that voice, not
  re-translate it. Optional: empty at chapter start → `(none)`.
- **Foreign-passage policy** — `{{foreignPassageRule}}` expands from the project's foreign-passage flag (`FR-BRIEF-04`).
  Default keep-as-is renders: *"If a passage is deliberately in a language other than {{sourceLang}}, keep it verbatim;
  do not translate it."* The deterministic target-language QA check is made policy-aware so a kept passage is not scored
  as wrong-language (`FR-QA-03`). A segment is treated as a **legitimate foreign-keep** — and its **untranslated-echo**
  check suppressed — **only when it matches a pre-detected foreign span from import**; an unmarked segment that merely
  echoes the source is still flagged.

## output-contract {#output-contract}

Every call in this catalogue follows the JSON-first, tolerant response contract
(`02_Architecture/04_LLM_INTEGRATION.md`):

1. **Structured output is requested** where the provider supports it — OpenAI `response_format` (`json_schema` or
   `json_object`), Ollama `format` (`json` or a JSON schema) — asking for the shape shown per call.
2. **The response is sanitized** before parsing: reasoning/thinking blocks (`<think>…</think>` and analogues),
   chain-of-thought preambles, markdown code fences, and leading/trailing prose are stripped; any separate reasoning
   channel is ignored.
3. **Reasoning level** is set low/off where controllable (Ollama `think`, OpenAI reasoning params) for translation and
   judge calls, to cut latency and noise.
4. **Parsing is tolerant:** unknown/unexpected fields are ignored, missing optional fields are defaulted, whitespace is
   trimmed, and the JSON object is located within the cleaned text. The shapes below are therefore a *contract for what
   to emit*, not a strict rejection schema on read.
5. **One repair retry** on malformed output (*"return only valid JSON matching this shape …"*).
6. **Text fallback** applies to **single-segment draft calls only**: if a draft that carries exactly one segment is
   still unparseable, the cleaned response is treated deterministically as that segment's plain translation and sent
   through the QA gates. A **multi-segment** unparseable draft is **never** text-fallen-back; instead it triggers
   **per-segment calls** (`#draft-translation`), and each single-segment call may then use the text fallback. Other
   calls fall back to their defined default (e.g. judge → treat as non-accept and route to self-heal). This refines the
   JSON-first, tolerant response-handling contract (DD-33).

Nullable request parameters are omitted from the serialized JSON, never sent as `null`.

## draft-translation {#draft-translation}

The primary per-chunk call (`05_TRANSLATION_ALGORITHM.md#chunk-loop`, `FR-ALGO-C4`). Translates the masked source
segments into the target language.

**SYSTEM**

```
You are a professional literary translator translating from {{sourceLang}} into {{targetLang}}.
Translate faithfully: preserve meaning, tone, and register. Do not add, omit, summarize, or explain.

Style guidance:
{{styleSheet}}

Rules:
- Preserve every placeholder token of the form ⟦gN⟧ EXACTLY as written — same text, same order, same count.
  They stand for inline formatting, locked names and terms, URLs, and the specific numerals that were masked
  (standalone/typographic numerals and numerals inside locked terms). Ordinary prose numerals are NOT masked —
  translate and localize them normally. Never translate, reorder, drop, merge, or invent a placeholder, and
  never insert text between a paired ⟦gN⟧ … ⟦gM⟧ that changes what it wraps.
- Apply the glossary renderings exactly, respecting gender and agreement.
- Continue the voice and terminology of the preceding translated text; keep names consistent with it.
- {{foreignPassageRule}}
- If you cannot finalize a segment without a fact revealed later in the book (e.g. a character's gender not yet
  known), still translate it as best you can and record it in "deferrals" with a short reason.
- Follow any extra instruction under [Extra instruction] exactly, without breaking the rules above.
- Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Book so far — bilingual summary]
{{rollingSummary}}

[Glossary — apply exactly (term → target, type, gender)]
{{glossaryTerms}}

[Preceding target text — continue this voice; do NOT re-translate it]
{{precedingTarget}}

[Translation-memory suggestions — reuse only if they fit this exact context]
{{tmHits}}

[Extra instruction — retry-with-note; follow exactly]
{{userNote}}

[Translate these segments from {{sourceLang}} to {{targetLang}}. Return each keyed by its id.]
{{sourceSegments}}

Return JSON exactly as:
{"segments":[{"id":"<segment-id>","target":"<translation>"}],
 "deferrals":[{"segmentId":"<id>","reason":"<why it needs a later fact>"}]}
```

| Variable                           | Required? | Source / notes                                                                                                                                                                                           |
|------------------------------------|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{{sourceLang}}`, `{{targetLang}}` | Required  | Project languages (`FR-BRIEF-01`).                                                                                                                                                                       |
| `{{styleSheet}}`                   | Required  | Derived style sheet (`#book-brief-tone-setup`); defaults if user did not customize.                                                                                                                      |
| `{{foreignPassageRule}}`           | Required  | Expanded foreign-passage policy (`FR-BRIEF-04`).                                                                                                                                                         |
| `{{sourceSegments}}`               | Required  | Masked chunk segments, each with its id and `⟦gN⟧` intact.                                                                                                                                               |
| `{{glossaryTerms}}`                | Optional  | Only terms occurring in the chunk; `(none)` if empty.                                                                                                                                                    |
| `{{rollingSummary}}`               | Optional  | `(none)` at book start.                                                                                                                                                                                  |
| `{{precedingTarget}}`              | Optional  | Up to ~3 target blocks; `(none)` at chapter start.                                                                                                                                                       |
| `{{tmHits}}`                       | Optional  | Labelled exact/context/fuzzy; `(none)` if empty.                                                                                                                                                         |
| `{{userNote}}`                     | Optional  | Retry-with-note free-text instruction (`06_REVIEW_AND_EDITING.md#segment-actions`); `(none)` in normal runs. On a retry-with-note the original chunk context is reconstructed and this note is injected. |

**Parameters:** temperature ~0.2; output format = JSON object / schema; reasoning low/off; non-streaming; `num_ctx`
sized so the budget reservation holds (`02_Architecture/05_PIPELINE_ENGINE.md#chunk-packing`).

**Expected output**

```json
{ "segments": [ { "id": "s10", "target": "…" }, { "id": "s11", "target": "…" } ],
  "deferrals": [ { "segmentId": "s10", "reason": "gender of ‘the visitor' not yet established" } ] }
```

Tolerant read: an id→text object map (`{"s10":"…"}`) is also accepted; `deferrals` may be absent/empty; unknown fields
ignored. **Id-mismatch handling** — a **missing or duplicate** id triggers **per-segment calls for the missing ids
only** (valid returned segments are reused, **extra/unknown ids are ignored**, order is irrelevant); a
**single-segment** unparseable response uses the text fallback, while a **multi-segment** unparseable response goes to
per-segment calls (`FR-ALGO-C4`). Recorded `deferrals` feed the deferred-resolution / backward-revision machinery
(`02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`).

## judge-quality-evaluation {#judge-quality-evaluation}

LLM-as-judge, run only when the dial enables the judge and the deterministic QA gate has passed
(`05_TRANSLATION_ALGORITHM.md#chunk-loop`). Produces a quality score compared against the dial's `τ` and, where
possible, concrete findings that let self-heal choose a **directed fix** over reflect→improve.

**SYSTEM**

```
You are a meticulous bilingual translation reviewer for {{sourceLang}} → {{targetLang}}.
Score the translation on four anchored dimensions, each 0.0–1.0:
- fidelity: 1.0 = meaning fully preserved; 0.5 = minor drift; 0.0 = meaning changed or invented.
- completeness: 1.0 = nothing added or omitted; 0.5 = a minor omission/addition; 0.0 = material content missing.
- fluency: 1.0 = natural, idiomatic target prose; 0.5 = understandable but awkward; 0.0 = ungrammatical.
- glossary & style: 1.0 = every locked term and style rule honoured; 0.5 = a minor miss; 0.0 = repeated violations.
The overall "score" is your holistic judgement across these dimensions (not a forced average).
You are a judge: do not rewrite the text. Report concrete, segment-level findings where a specific problem exists.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Style + glossary that were required]
{{styleSheet}}
{{glossaryTerms}}

[Foreign-passage policy in force]
{{foreignPassageRule}}

[Source segments]
{{sourceSegments}}

[Candidate translation to evaluate]
{{candidateTarget}}

Score 0.0–1.0 overall (1.0 = publishable, faithful, complete). List findings for concrete defects only.
If a segment needs a fact revealed later in the book, note it under "deferrals".
Return JSON exactly as:
{"score":<0.0-1.0>,"verdict":"accept"|"revise",
 "findings":[{"segmentId":"<id>","type":"meaning|omission|fluency|glossary|language|tag","severity":"low|medium|high","note":"<short>"}],
 "deferrals":[{"segmentId":"<id>","reason":"<why it needs a later fact>"}]}
```

| Variable                           | Required? | Source / notes                                              |
|------------------------------------|-----------|-------------------------------------------------------------|
| `{{sourceLang}}`, `{{targetLang}}` | Required  | Project languages.                                          |
| `{{sourceSegments}}`               | Required  | Masked source of the chunk.                                 |
| `{{candidateTarget}}`              | Required  | The unmasked-then-remasked draft under review, keyed by id. |
| `{{styleSheet}}`                   | Required  | So style adherence can be judged.                           |
| `{{glossaryTerms}}`                | Optional  | Terms in the chunk; `(none)` if empty.                      |
| `{{foreignPassageRule}}`           | Required  | So a kept foreign passage is not scored as wrong-language.  |

**Parameters:** temperature ~0.0–0.2 (low, for stability — but LLM scoring is not bit-reproducible, so no "identical
input → identical score" guarantee is claimed); output format = JSON object / schema; reasoning low/off. Where the dial
calls for it, the judge is run as a **small multi-sample average** (e.g. 2–3 samples), and the **averaged `score`** is
what the accept rule compares against `τ_judge`.

**Decision rule:** `score ≥ τ_judge` **decides** acceptance (`τ_judge` defaults to `τ`); `verdict` is **advisory/logging
only** and never overrides the numeric gate.

**Expected output**

```json
{ "score": 0.86, "verdict": "accept",
  "findings": [ { "segmentId": "s11", "type": "glossary", "severity": "low", "note": "…" } ],
  "deferrals": [ { "segmentId": "s11", "reason": "pronoun depends on later-revealed gender" } ] }
```

Tolerant read: `findings` and `deferrals` may be absent/empty; unknown fields ignored. If unparseable, the judge is
treated as non-accept and the chunk routes to self-heal.

## directed-fix-repair {#directed-fix-repair}

Self-heal path when concrete findings exist (deterministic QA finding or judge finding). **One** call that asks the
model to correct exactly the named problems and change nothing else (`05_TRANSLATION_ALGORITHM.md#self-heal`,
`FR-ALGO-C11`).

**SYSTEM**

```
You are revising your own {{sourceLang}} → {{targetLang}} translation to fix specific, listed defects.
Change ONLY what the findings require. Keep every correct sentence and every ⟦gN⟧ placeholder unchanged.
Do not re-translate freely, do not paraphrase unaffected text, do not add or omit content.
If [Expected placeholders] is present, your output MUST contain exactly that multiset of ⟦gN⟧ tokens — restore
any that are missing and remove any that were invented, without changing what each one wraps.
Follow any [Extra instruction] exactly, without breaking these rules.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Style + glossary]
{{styleSheet}}
{{glossaryTerms}}
{{foreignPassageRule}}

[Source segments]
{{sourceSegments}}

[Your current translation]
{{candidateTarget}}

[Defects to fix — address each exactly]
{{findings}}

[Expected placeholders — restore exactly this multiset]
{{expectedPlaceholders}}

[Extra instruction — retry-with-note; follow exactly]
{{userNote}}

Return the corrected translation in the same shape:
{"segments":[{"id":"<segment-id>","target":"<corrected translation>"}]}
```

| Variable                                   | Required? | Source / notes                                                                                                                    |
|--------------------------------------------|-----------|-----------------------------------------------------------------------------------------------------------------------------------|
| `{{sourceSegments}}`                       | Required  | The chunk's masked source.                                                                                                        |
| `{{candidateTarget}}`                      | Required  | The rejected draft, keyed by id.                                                                                                  |
| `{{findings}}`                             | Required  | Concrete findings from QA and/or the judge (type, segmentId, note).                                                               |
| `{{styleSheet}}`, `{{foreignPassageRule}}` | Required  | Same frame as the draft.                                                                                                          |
| `{{glossaryTerms}}`                        | Optional  | Terms in the chunk; `(none)` if empty.                                                                                            |
| `{{expectedPlaceholders}}`                 | Optional  | On a **tag-multiset mismatch** finding, the exact expected multiset of `⟦gN⟧` tokens (e.g. `⟦g1⟧ ⟦g2⟧ ⟦g3⟧`); `(none)` otherwise. |
| `{{userNote}}`                             | Optional  | Retry-with-note free-text; `(none)` in normal runs.                                                                               |

**Parameters:** temperature ~0.2 (the "lower temperature for this retry" option may reduce it further); output format =
JSON object / schema; reasoning low/off.

**Expected output:** same shape as `#draft-translation`. Re-enters unmask + QA; bounded by the repair budget N.

## reflect-improve {#reflect-improve}

Self-heal path when the failure is a **vague** quality concern with no concrete finding (e.g. a low judge score alone).
Two calls — a reflection critique, then a rewrite that consumes it — optionally followed by a monolingual polish
(`05_TRANSLATION_ALGORITHM.md#self-heal`, `FR-ALGO-C11`).

### reflect (call 1 — critique) {#reflect-critique}

**SYSTEM**

```
You are a translation critic for {{sourceLang}} → {{targetLang}}.
Do NOT rewrite. Identify what weakens the candidate translation — awkward phrasing, tone drift,
terminology inconsistency, subtle meaning loss — and say concretely how to improve it.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Style + glossary]
{{styleSheet}}
{{glossaryTerms}}

[Preceding target text — the voice to match]
{{precedingTarget}}

[Source segments]
{{sourceSegments}}

[Candidate translation]
{{candidateTarget}}

Return JSON exactly as:
{"issues":[{"segmentId":"<id>","note":"<what is wrong>","suggestion":"<how to fix>"}]}
```

**Parameters:** temperature ~0.2; output format = JSON object / schema; reasoning low/off. **Expected output**

```json
{ "issues": [ { "segmentId": "s12", "note": "…", "suggestion": "…" } ] }
```

### improve (call 2 — rewrite) {#reflect-rewrite}

**SYSTEM**

```
You are a professional literary translator ({{sourceLang}} → {{targetLang}}) applying a critique to improve a translation.
Produce a better translation that resolves the critique while staying faithful to the source.
Preserve every ⟦gN⟧ placeholder exactly. Apply the glossary. Continue the preceding voice.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Style + glossary]
{{styleSheet}}
{{glossaryTerms}}
{{foreignPassageRule}}

[Preceding target text]
{{precedingTarget}}

[Source segments]
{{sourceSegments}}

[Previous translation]
{{candidateTarget}}

[Critique to apply]
{{reflection}}

Return the improved translation as:
{"segments":[{"id":"<segment-id>","target":"<improved translation>"}]}
```

| Variable                                    | Required?         | Source / notes                             |
|---------------------------------------------|-------------------|--------------------------------------------|
| `{{sourceSegments}}`, `{{candidateTarget}}` | Required          | Chunk source and the draft being improved. |
| `{{reflection}}`                            | Required (call 2) | The `issues` JSON from the reflect call.   |
| `{{styleSheet}}`, `{{foreignPassageRule}}`  | Required          | Same frame as the draft.                   |
| `{{precedingTarget}}`                       | Optional          | `(none)` at chapter start.                 |
| `{{glossaryTerms}}`                         | Optional          | Terms in the chunk; `(none)` if empty.     |

**Parameters:** temperature slightly higher than draft (≤ ~0.4) to escape a bad local phrasing; output format = JSON
object / schema; reasoning low/off. **Expected output:** same shape as `#draft-translation`. Re-enters unmask + QA;
bounded by N.

### polish (optional call — monolingual smoothing) {#monolingual-polish}

An **optional** third call in the reflect→improve path, run **only** when the post-improve QA still leaves the segment
**borderline** (hard gates pass but `confidence ∈ [τ − ε, τ)`; `05_TRANSLATION_ALGORITHM.md#self-heal`). It smooths the
**target text monolingually** — it is given the target only, not the source, so it cannot drift the meaning — while
preserving every placeholder.

**SYSTEM**

```
You are a {{targetLang}} copy-editor polishing an already-faithful translation for fluency and rhythm.
You do NOT have the source and must NOT change meaning, add, or omit content — only improve wording,
flow, and naturalness in {{targetLang}}.
Preserve every ⟦gN⟧ placeholder EXACTLY (same text, order, count). Keep names and glossary terms unchanged.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Style]
{{styleSheet}}

[Preceding target text — match this voice]
{{precedingTarget}}

[Text to polish — improve fluency only]
{{candidateTarget}}

Return JSON exactly as:
{"segments":[{"id":"<segment-id>","target":"<polished translation>"}]}
```

| Variable                           | Required? | Source / notes                                                  |
|------------------------------------|-----------|-----------------------------------------------------------------|
| `{{candidateTarget}}`              | Required  | The post-improve target, keyed by id (target only — no source). |
| `{{targetLang}}`, `{{styleSheet}}` | Required  | Target language and style frame.                                |
| `{{precedingTarget}}`              | Optional  | `(none)` at chapter start.                                      |

**Parameters:** temperature ~0.2; output format = JSON object / schema; reasoning low/off. **Expected output:** same
shape as `#draft-translation`. Re-enters unmask + QA; still bounded by the same repair round.

## backward-revision-consistency {#backward-revision-consistency}

Optional whole-book pass (dial-gated: Max or the export toggle). Resolves deferred-resolution items with full-book facts
and aligns terminology, re-rendering affected earlier segments to `REVISED`
(`05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`, `FR-ALGO-D1`/`D2`). This **LLM re-render is invoked only for
gender/agreement deferrals**; **locked-term** consistency is applied by **deterministic string substitution** with no
LLM call, and the sweep is **bounded to segments containing a swept term**. **User-edited `REVISED` segments are
protected** — the sweep proposes but does not overwrite them, re-rendering a user-edited segment only with explicit user
opt-in.

**SYSTEM**

```
You are performing a consistency revision on an already-translated book ({{sourceLang}} → {{targetLang}}).
Using now-known book-wide facts and the canonical glossary, correct only the listed segments so that
names, gender agreement, and key terminology are consistent with the rest of the book.
Keep every ⟦gN⟧ placeholder exactly. Do not restyle text that is already consistent.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Canonical glossary / name dictionary — book-wide]
{{canonicalGlossary}}

[Resolved facts revealed later in the book]
{{resolvedFacts}}

[Segments to revise — with their current translation]
{{segmentsToRevise}}

Return JSON exactly as:
{"revisions":[{"id":"<segment-id>","target":"<revised translation>","reason":"<short>"}]}
```

| Variable                           | Required? | Source / notes                                                                                       |
|------------------------------------|-----------|------------------------------------------------------------------------------------------------------|
| `{{segmentsToRevise}}`             | Required  | Earlier segments flagged by deferred-resolution / term sweep, with source + current target.          |
| `{{canonicalGlossary}}`            | Required  | Finalized name/term dictionary for the whole book.                                                   |
| `{{resolvedFacts}}`                | Optional  | Deferred-resolution facts (e.g. a character's later-revealed gender); `(none)` if only a term sweep. |
| `{{sourceLang}}`, `{{targetLang}}` | Required  | Project languages.                                                                                   |

**Parameters:** temperature ~0.2; output format = JSON object / schema; reasoning low/off. **Expected output**

```json
{ "revisions": [ { "id": "s08", "target": "…", "reason": "gender now known" } ] }
```

Tolerant read: an empty `revisions` array means "nothing to change"; unknown fields ignored.

## book-brief-tone-setup {#book-brief-tone-setup}

One-time Prep call (phase B, `05_TRANSLATION_ALGORITHM.md#phase-b-prep`, `FR-ALGO-B2`) that turns the user's Book Brief
into a compact **style sheet** reused verbatim in `{{styleSheet}}` by every later call. This call is **LLM-assisted and
optional**: when disabled or unavailable the style sheet is assembled deterministically from the brief fields; the LLM
variant mainly smooths free-text voice/era/audience notes into concise guidance.

**SYSTEM**

```
You are preparing a concise translator style sheet for a {{sourceLang}} → {{targetLang}} book translation.
Turn the brief into short, actionable guidance a translator can follow consistently.
Do not translate anything. Do not invent facts not in the brief.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Book Brief]
Genre: {{genre}}
Register: {{register}}
Voice / era notes: {{voiceEra}}
Target audience: {{audience}}
Faithful↔natural bias (0=faithful … 1=natural): {{faithfulNaturalBias}}
Name policy: {{namePolicy}}
Foreign-passage policy: {{foreignPassagePolicy}}
Footnote policy: {{footnotePolicy}}
Unit policy: {{unitPolicy}}

Return JSON exactly as:
{"styleSheet":{"summary":"<2-4 sentences>","register":"<...>","voice":"<...>",
 "faithfulNaturalBias":<0.0-1.0>,"namePolicy":"<...>","foreignPassagePolicy":"<...>","notes":"<optional>"}}
```

| Variable                                                                                             | Required? | Source / notes                                |
|------------------------------------------------------------------------------------------------------|-----------|-----------------------------------------------|
| `{{sourceLang}}`, `{{targetLang}}`                                                                   | Required  | `FR-BRIEF-01`.                                |
| `{{genre}}`, `{{register}}`, `{{faithfulNaturalBias}}`, `{{namePolicy}}`, `{{foreignPassagePolicy}}` | Required  | `FR-BRIEF-02..05`; all have defaults.         |
| `{{voiceEra}}`, `{{audience}}`                                                                       | Optional  | Free-text; `(none)` if unset.                 |
| `{{footnotePolicy}}`, `{{unitPolicy}}`                                                               | Optional  | `FR-BRIEF-06/07`; default behaviour if unset. |

**Parameters:** temperature ~0.2; output format = JSON object / schema; reasoning low/off. **Expected output**

```json
{ "styleSheet": { "summary": "…", "register": "neutral", "voice": "…",
  "faithfulNaturalBias": 0.5, "namePolicy": "transliterate", "foreignPassagePolicy": "keep", "notes": "…" } }
```

## name-term-pre-scan {#name-term-pre-scan}

A one-time Prep call (phase B, `05_TRANSLATION_ALGORITHM.md#phase-b-prep`, `FR-ALGO-B1`, `FR-GLOSS-01`, DD-46) that
proposes **name/term candidates** — characters, places, organizations, recurring domain terms — each with a **type** and
a **provisional gender**, to seed the user-editable Glossary (Names & Style) step. It is an **app-runtime LLM call**,
distinct from any eval-only embeddings; there is no NER library. Long books are scanned in batches and the candidate
lists are merged/deduplicated deterministically before display.

**SYSTEM**

```
You are extracting a name and terminology list for a {{sourceLang}} → {{targetLang}} book translation.
Identify recurring proper names (people, places, organizations) and domain-specific terms that must be
translated consistently. For each, give its type and — for persons — your best guess of grammatical/character
gender for target-language agreement, with a confidence. Do not translate the terms; propose the source form.
If gender is not inferable, use "unknown". Do not invent entries that are not in the text.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Book text or representative excerpt to scan]
{{scanText}}

[Existing glossary terms — do not duplicate these]
{{existingTerms}}

Return JSON exactly as:
{"terms":[{"term":"<source form>","type":"person|place|org|term|other",
 "gender":"male|female|neuter|unknown","note":"<short context/disambiguation>","confidence":<0.0-1.0>}]}
```

| Variable                           | Required? | Source / notes                                                             |
|------------------------------------|-----------|----------------------------------------------------------------------------|
| `{{sourceLang}}`, `{{targetLang}}` | Required  | Project languages (`FR-BRIEF-01`).                                         |
| `{{scanText}}`                     | Required  | The book text (or a representative excerpt / current batch) to scan.       |
| `{{existingTerms}}`                | Optional  | Already-known glossary terms to avoid duplicating; `(none)` on first scan. |

**Parameters:** temperature ~0.2; output format = JSON object / schema; reasoning low/off. **Expected output**

```json
{ "terms": [
  { "term": "Hale", "type": "person", "gender": "male", "note": "Mr Hale, arrives ch.1", "confidence": 0.9 },
  { "term": "Baker Street", "type": "place", "gender": "unknown", "note": "street address", "confidence": 0.95 } ] }
```

Tolerant read: `note`/`confidence` may be absent; `gender` defaults to `unknown`; unknown fields ignored.
**Offline/disabled fallback (deterministic):** when the provider is unavailable or the pre-scan is disabled, candidates
are derived by **frequency + casing** — repeated capitalized tokens/phrases not at sentence start, ranked by frequency —
with `type = other` and **`gender = unknown`** (a person's gender is filled later by the user or by backward revision).
The **unknown-gender heuristic**: any candidate person whose gender stays `unknown`, and any segment referencing such a
person, is a **deferred-resolution** signal (`02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`).

## rolling-summary-update {#rolling-summary-update}

Refreshes the rolling **bilingual** summary carried into later prompts, on a **size-based trigger — every K accepted
blocks OR at chapter end, whichever comes first** (`05_TRANSLATION_ALGORITHM.md#chunk-loop`, `FR-ALGO-07`;
`02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`). The **default is a deterministic summary** (accumulated key
facts, truncated/condensed to budget); this **LLM-generated** variant is **opt-in**. For a no-chapter document (TXT /
single chapter) the every-K-blocks trigger drives updates and end-of-document acts as the chapter-end trigger.

**SYSTEM**

```
You maintain a short rolling bilingual summary of a book being translated ({{sourceLang}} / {{targetLang}}).
Update the running summary with what this chapter established: characters, relationships, places, and
terminology decisions. Keep it compact and factual — it is context for translating later chapters, not a retelling.
Provide the summary in both {{sourceLang}} and {{targetLang}}.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Running summary so far]
{{previousSummary}}

[This chapter — source]
{{chapterSource}}

[This chapter — accepted target]
{{chapterTarget}}

Return JSON exactly as:
{"summary":{"source":"<updated summary in {{sourceLang}}>","target":"<updated summary in {{targetLang}}>"},
 "facts":["<key fact>","<key fact>"]}
```

| Variable                                 | Required? | Source / notes                                                                           |
|------------------------------------------|-----------|------------------------------------------------------------------------------------------|
| `{{sourceLang}}`, `{{targetLang}}`       | Required  | Project languages.                                                                       |
| `{{chapterSource}}`, `{{chapterTarget}}` | Required  | The just-finished chapter's source and accepted target (may be condensed to fit budget). |
| `{{previousSummary}}`                    | Optional  | `(none)` for the first chapter.                                                          |

**Parameters:** temperature ~0.2; output format = JSON object / schema; reasoning low/off. **Expected output**

```json
{ "summary": { "source": "…", "target": "…" },
  "facts": [ "Hale is male", "Story set at 7 Baker Street" ] }
```

Tolerant read: `facts` may be absent; unknown fields ignored; the bilingual `summary` is stored per
`02_Architecture/06_DATA_MODEL_SQLITE.md#summaries`.
