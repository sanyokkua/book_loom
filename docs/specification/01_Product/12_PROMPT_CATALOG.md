**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:pipeline`, `:llm`), QA **Last Updated:**
2026-09-27 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
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

Every prompt is assembled from a fixed template with named slots. Unless a prompt defines a stricter contract, slots
that carry no content for a given chunk collapse to a literal `(none)` rather than being left dangling, and any purely
optional block may be omitted entirely; the model is told to translate what is present, not to expect every slot. The
single-segment draft contract is the exception: it omits all absent optional blocks, including preceding context, so
its model-facing input is only context when present, the source text, and the strict reply shape. **Load-bearing slots
sit at the prompt edges** (the
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
  do not translate it."* The deterministic target-script QA check is made policy-aware so a kept passage is not scored
  as wrong-script (`FR-QA-03`). A segment is treated as a **legitimate foreign-keep** — and its **untranslated-echo**
  and **target-script** checks suppressed — **only when its own declared block language differs from the run's source
  language, or its dominant script does**; nothing is ever inferred from unmarked text. An unmarked segment that merely
  echoes the source is still flagged.

## output-contract {#output-contract}

Draft translation uses a strict single-segment response contract (`02_Architecture/04_LLM_INTEGRATION.md`):

1. **Structured output is requested** through native Ollama `format` and OpenAI-compatible
   `response_format.json_schema`, requiring exactly `{"target":"…"}` with no additional properties.
2. **The prompt repeats the compact shape** because a provider schema controls the envelope but cannot prove that a
   model preserved the dynamic `⟦gN⟧` sequence inside `target`.
3. **Parsing is exact:** prose, maps, arrays, embedded JSON, extra fields, malformed JSON, and blank targets are
   rejected before unmasking; there is no plain-text fallback.
4. **One structural repair** includes the delimited rejected reply and a parsing diagnosis. A valid target that fails
   the placeholder hard gate receives **one separate placeholder repair** with the original source, rejected target,
   and required ordered tokens. Neither repair recurses.

Nullable request parameters are omitted from the serialized JSON, never sent as `null`.

## draft-translation {#draft-translation}

The primary per-segment call (`05_TRANSLATION_ALGORITHM.md#chunk-loop`, `FR-ALGO-C4`). It translates exactly one
masked source segment; previously accepted targets are context only, never additional inference inputs.

**SYSTEM**

```
You are a professional literary translator translating from {{sourceLang}} into {{targetLang}}.
Translate faithfully: preserve meaning, tone, and register. Do not add, omit, summarize, or explain.

Style guidance:
{{styleSheet}}

Rules:
- Preserve every placeholder token of the form ⟦gN⟧ EXACTLY as written — same text, same order, same count.
  They stand for inline formatting, locked names and terms, URLs, and kept foreign passages. Numerals are NOT
  masked in this build — translate and localize every numeral normally, wherever it appears. Never translate,
  reorder, drop, merge, or invent a placeholder, and never insert text between a paired ⟦gN⟧ … ⟦gM⟧ that changes
  what it wraps.
- Apply the glossary renderings exactly, respecting gender and agreement.
- Continue the voice and terminology of the preceding translated text; keep names consistent with it.
- {{foreignPassageRule}}
- Follow any extra instruction under [Extra instruction] exactly, without breaking the rules above.
- Output ONLY the required JSON object. No commentary, no code fences, no reasoning.

Token-layout examples are structural only: translate the actual <Text>, never copy these labels.
- A ⟦g0⟧B⟦g1⟧ C → X ⟦g0⟧Y⟦g1⟧ Z
- A ⟦g0⟧B⟦g1⟧ C ⟦g2⟧D⟦g3⟧ → X ⟦g0⟧Y⟦g1⟧ Z ⟦g2⟧W⟦g3⟧
- A ⟦g0⟧https://example.test/a⟦g1⟧ meets ⟦g2⟧Ada⟦g3⟧ → X ⟦g0⟧https://example.test/a⟦g1⟧ Y ⟦g2⟧Ada⟦g3⟧
- Return exactly: {"target":"X ⟦g0⟧Y⟦g1⟧ Z"}
```

**USER**

```
[Preceding target text — continue this voice; do NOT re-translate it]
{{precedingTarget}}

[Immutable tokens for this text]
Copy this exact ordered sequence unchanged: {{requiredTokenSequence}}
Do not add, reorder, split, translate, or omit these tokens.

<Text>
{{sourceText}}
</Text>

Return exactly one JSON object matching this schema: {"target":"<translation>"}
```

| Variable                           | Required? | Source / notes                                                                                                                                                                                           |
|------------------------------------|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{{sourceLang}}`, `{{targetLang}}` | Required  | Project languages (`FR-BRIEF-01`), rendered for the model as an English display name plus the exact BCP-47 tag (for example, `English (en)`); an unregistered tag is `language tag "&lt;tag&gt;"`. When the source is unknown, use `the language of this segment (infer it from its text)`. |
| `{{styleSheet}}`                   | Required  | Derived style sheet (`#book-brief-tone-setup`); defaults if user did not customize.                                                                                                                      |
| `{{foreignPassageRule}}`           | Required  | Expanded foreign-passage policy (`FR-BRIEF-04`).                                                                                                                                                         |
| `{{sourceText}}`                   | Required  | The one masked source segment, rendered verbatim inside `<Text>`.                                                                                                                                        |
| `{{requiredTokenSequence}}`         | Required  | This segment's exact source-order placeholder sequence, or an explicit no-token statement.                                                                                                              |
| `{{precedingTarget}}`              | Optional  | The last three accepted targets in the current section; the entire block is omitted when absent and reset at a section boundary.                                                                        |
| glossary, summary, TM, retry note  | Deferred  | Omitted until their producers exist; no empty `(none)` blocks are emitted.                                                                                                                               |

**Parameters:** temperature 0.2; output format = the strict `target` JSON schema; reasoning low/off; non-streaming.

**Expected output**

```json
{ "target": "…" }
```

Only this exact shape is accepted. Provider-enforced schema does not replace the document placeholder multiset gate.
Malformed or wrong-shape output gets one repair with a delimited rejected reply and parsing diagnosis. A valid target
that fails the gate gets one distinct repair with the original source, rejected target, and exact required token order;
every repair must parse strictly and pass unmasking.

## judge-quality-evaluation {#judge-quality-evaluation}

LLM-as-judge, run only when the dial enables the judge, over the drafted pairs that passed their hard gates — a pair
that failed a soft check is still judged (`05_TRANSLATION_ALGORITHM.md#chunk-loop`). Scores the whole chunk in **one call**, labelling its qualifying pairs
`s1…sk` in document order — these are **local labels for this call only**, never the segments' real ids. Produces a
quality score compared against the dial's `τ_judge` and, where possible, concrete findings that let self-heal choose a
**directed fix** over reflect→improve.

**SYSTEM**

```
You are a meticulous bilingual translation reviewer for {{sourceLang}} → {{targetLang}}.
Score the translation on four anchored dimensions, each 0.0–1.0:
- fidelity: 1.0 = meaning fully preserved; 0.5 = minor drift; 0.0 = meaning changed or invented.
- completeness: 1.0 = nothing added or omitted; 0.5 = a minor omission/addition; 0.0 = material content missing.
- fluency: 1.0 = natural, idiomatic target prose; 0.5 = understandable but awkward; 0.0 = ungrammatical.
- glossary & style: 1.0 = every locked term and style rule honoured; 0.5 = a minor miss; 0.0 = repeated violations.
The overall "score" is your holistic judgement across these dimensions (not a forced average).
[Style sheet — the style the translation had to follow]
{{styleSheet}}
You are a judge: do not rewrite the text. Report concrete findings against the local labels s1, s2, … given below,
where a specific problem exists.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Glossary that was required]
{{glossaryTerms}}

[Foreign-passage policy in force]
{{foreignPassageRule}}

[Source pairs, labelled s1..sk]
{{sourceSegments}}

[Candidate translations to evaluate, same labels]
{{candidateTarget}}

Score 0.0–1.0 overall (1.0 = publishable, faithful, complete). List findings for concrete defects only, by label.
If a labelled pair needs a fact revealed later in the book, note it under "deferrals".
Return JSON exactly as:
{"score":<0.0-1.0>,"verdict":"accept"|"revise",
 "findings":[{"segmentId":"<s1..sk label>","type":"meaning|omission|fluency|glossary|language|tag","severity":"low|medium|high","note":"<short>"}],
 "deferrals":[{"segmentId":"<s1..sk label>","reason":"<why it needs a later fact>"}]}
```

| Variable                           | Required? | Source / notes                                                          |
|------------------------------------|-----------|---------------------------------------------------------------------------|
| `{{sourceLang}}`, `{{targetLang}}` | Required  | Project languages.                                                      |
| `{{sourceSegments}}`               | Required  | The chunk's masked source pairs that passed their hard gates and were not reused, labelled `s1…sk`. |
| `{{candidateTarget}}`              | Required  | The unmasked-then-remasked drafts under review, labelled with the same `s1…sk`.                    |
| `{{styleSheet}}`                   | Required  | So style adherence can be judged; in the SYSTEM message, as in every call of a run. |
| `{{glossaryTerms}}`                | Optional  | Terms in the chunk; `(none)` if empty.                                  |
| `{{foreignPassageRule}}`           | Required  | So a kept foreign passage is not scored as wrong-script.                |

**Parameters:** temperature 0.1, one sample (no multi-sample averaging); output format = JSON object / schema;
reasoning low/off.

**Decision rule:** `score ≥ τ_judge` **decides** acceptance for a labelled pair, together with the absence of a
`medium`/`high` finding on it (`τ_judge` defaults to `τ`); `verdict` is **advisory/logging only** and never overrides
the numeric gate. An **unreadable** reply (fails to parse, or omits `score`) is treated as **not accepted** for every
pair in the call, routing each to self-heal.

**Expected output**

```json
{ "score": 0.86, "verdict": "accept",
  "findings": [ { "segmentId": "s2", "type": "glossary", "severity": "low", "note": "…" } ],
  "deferrals": [ { "segmentId": "s2", "reason": "pronoun depends on later-revealed gender" } ] }
```

Tolerant read: `findings` and `deferrals` may be absent/empty; unknown fields ignored; a finding with an unknown
severity, or a finding or deferral whose label is outside `s1…sk`, is dropped; a finding's `type` is kept as written
(`tag` here is what the deterministic checks call `markup`).

## directed-fix-repair {#directed-fix-repair}

Self-heal path when concrete findings exist (deterministic QA finding or judge finding), for **one** failing segment.
**One** call that asks the model to correct exactly the named problems in that segment and change nothing else
(`05_TRANSLATION_ALGORITHM.md#self-heal`, `FR-ALGO-C11`).

**SYSTEM**

```
You are revising your own {{sourceLang}} → {{targetLang}} translation to fix specific, listed defects.
Change ONLY what the findings require. Keep every correct part of the sentence and every ⟦gN⟧ placeholder unchanged.
Do not re-translate freely, do not paraphrase unaffected text, do not add or omit content.
If [Expected placeholders] is present, your output MUST contain exactly those ⟦gN⟧ tokens, in that order — restore
any that are missing and remove any that were invented, without changing what each one wraps.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.

[Style sheet]
{{styleSheet}}
{{foreignPassageRule}}
```

**USER**

```
[Source]
{{sourceSegment}}

[Defects to fix — address each exactly]
{{findings}}

[Expected placeholders — restore exactly this sequence]
{{expectedPlaceholders}}

<Text>
{{textToRewrite}}
</Text>

Return exactly one JSON object matching this schema: {"target":"<corrected translation>"}
```

| Variable                                   | Required? | Source / notes                                                                                                                    |
|--------------------------------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------|
| `{{textToRewrite}}`                        | Required  | The single `<Text>` block: the rejected target, or the masked source when the finding is a refusal or an empty target.            |
| `{{sourceSegment}}`                        | Required  | The masked source, shown under `[Source]` outside `<Text>` for reference.                                                          |
| `{{findings}}`                             | Required  | Concrete findings for this one segment (type, note).                                                                               |
| `{{styleSheet}}`, `{{foreignPassageRule}}` | Required  | Same frame as the draft, in the SYSTEM message.                                                                                     |
| `{{expectedPlaceholders}}`                 | Optional  | On a placeholder or protected-span failure, the expected `⟦gN⟧` tokens in source order (e.g. `⟦g1⟧ ⟦g2⟧ ⟦g3⟧`); omitted otherwise. |

A review panel "Retry with note" is a draft with the note under `[Extra instruction]`, not a directed fix (`#draft-translation`).

**Parameters:** temperature 0.2; output format = JSON object / schema; reasoning low/off.

**Expected output:** `{"target":"<corrected translation>"}` — the same single-segment shape as `#draft-translation`.
Re-enters unmask + QA for this one segment; bounded by the repair budget N.

## reflect-improve {#reflect-improve}

Self-heal path when the failure is a **vague** quality concern with no concrete finding (e.g. a low judge score alone),
for **one** failing segment. Two calls — a reflection critique, then a rewrite that consumes it — optionally followed by
a monolingual polish (`05_TRANSLATION_ALGORITHM.md#self-heal`, `FR-ALGO-C11`).

### reflect (call 1 — critique) {#reflect-critique}

**SYSTEM**

```
You are a translation critic for {{sourceLang}} → {{targetLang}}.
Do NOT rewrite. Identify what weakens the candidate translation — awkward phrasing, tone drift,
terminology inconsistency, subtle meaning loss — and say concretely how to improve it.
Raise at most five issues, the most important first, each in one short sentence; return an empty list when there is
nothing to improve.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.

[Style sheet]
{{styleSheet}}
{{foreignPassageRule}}
```

**USER**

```
[Glossary]
{{glossaryTerms}}

[Preceding target text — the voice to match]
{{precedingTarget}}

[Source]
{{sourceSegment}}

<Text>
{{candidateTarget}}
</Text>

Return JSON exactly as:
{"issues":[{"note":"<what is wrong>","suggestion":"<how to fix>"}]}
```

| Variable             | Required? | Source / notes                                                        |
|----------------------|-----------|--------------------------------------------------------------------------|
| `{{candidateTarget}}` | Required  | The single `<Text>` block: the candidate translation under critique.    |
| `{{sourceSegment}}`   | Required  | The masked source, shown under `[Source]` outside `<Text>` for reference. |

**Parameters:** temperature 0.35; output format = JSON object / schema; reasoning low/off; output capped at 600 tokens, expected 256 (at most five issues). **Expected output**

```json
{ "issues": [ { "note": "…", "suggestion": "…" } ] }
```

Tolerant read: `issues` may be absent or empty, and an issue may be a plain string; an object is read as
`note — suggestion`.

### improve (call 2 — rewrite) {#reflect-rewrite}

**SYSTEM**

```
You are a professional literary translator ({{sourceLang}} → {{targetLang}}) applying a critique to improve a translation.
Produce a better translation that resolves the critique while staying faithful to the source.
Preserve every ⟦gN⟧ placeholder exactly. Apply the glossary. Continue the preceding voice.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.

[Style sheet]
{{styleSheet}}
{{foreignPassageRule}}
```

**USER**

```
[Glossary]
{{glossaryTerms}}

[Preceding target text]
{{precedingTarget}}

[Source]
{{sourceSegment}}

[Critique to apply]
{{reflection}}

<Text>
{{candidateTarget}}
</Text>

Return exactly one JSON object matching this schema: {"target":"<improved translation>"}
```

| Variable                                    | Required?         | Source / notes                                                     |
|-----------------------------------------------|-------------------|-------------------------------------------------------------------|
| `{{candidateTarget}}`                        | Required          | The single `<Text>` block: the target being improved.             |
| `{{sourceSegment}}`                          | Required          | The masked source, shown under `[Source]` outside `<Text>`.       |
| `{{reflection}}`                             | Required (call 2) | The `issues` JSON from the reflect call.                          |
| `{{styleSheet}}`, `{{foreignPassageRule}}`   | Required          | Same frame as the draft.                                          |
| `{{precedingTarget}}`                        | Optional          | `(none)` at chapter start.                                        |
| `{{glossaryTerms}}`                          | Optional          | Terms in the segment; `(none)` if empty.                          |

**Parameters:** temperature 0.35 (to escape a bad local phrasing); output format = JSON object / schema; reasoning
low/off. **Expected output:** `{"target":"<improved translation>"}` — the same single-segment shape as
`#draft-translation`. Re-enters unmask + QA for this one segment; bounded by N.

### polish (optional call — monolingual smoothing) {#monolingual-polish}

An **optional** third call in the reflect→improve path, run **only** when the post-improve check leaves the segment
**borderline** (hard gates pass, no soft check failed, and `confidence ∈ [τ − 0.05, τ)`;
`05_TRANSLATION_ALGORITHM.md#self-heal`). It smooths the **target text** for fluency; the source is shown under
`[Source]`, outside the one `<Text>` block, only so the smoothing cannot drift the meaning, and every placeholder is
preserved.

**SYSTEM**

```
You are a {{targetLang}} copy-editor polishing an already-faithful translation for fluency and rhythm.
The source is given for reference only: you must NOT change meaning, add, or omit content — only improve wording,
flow, and naturalness in {{targetLang}}.
Preserve every ⟦gN⟧ placeholder EXACTLY (same text, order, count). Keep names and glossary terms unchanged.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.

[Style sheet]
{{styleSheet}}
{{foreignPassageRule}}
```

**USER**

```
[Source]
{{sourceSegment}}

[Preceding target text — match this voice]
{{precedingTarget}}

<Text>
{{candidateTarget}}
</Text>

Return exactly one JSON object matching this schema: {"target":"<polished translation>"}
```

| Variable                           | Required? | Source / notes                                                  |
|------------------------------------|-----------|-------------------------------------------------------------------|
| `{{candidateTarget}}`              | Required  | The single `<Text>` block: the post-improve target.             |
| `{{sourceSegment}}`                | Required  | The masked source, under `[Source]` outside `<Text>`.            |
| `{{targetLang}}`, `{{styleSheet}}` | Required  | Target language and style frame.                                |
| `{{precedingTarget}}`              | Optional  | `(none)` at chapter start.                                       |

**Parameters:** temperature 0.2; output format = JSON object / schema; reasoning low/off. **Expected output:**
`{"target":"<polished translation>"}` — the same single-segment shape as `#draft-translation`. Re-enters unmask + QA;
still bounded by the same repair round.

## backward-revision-consistency {#backward-revision-consistency}

Optional whole-book pass (dial-gated: Max or the export toggle), applied **one segment per call**. Resolves
deferred-resolution items with full-book facts and aligns terminology, re-rendering an affected earlier segment to
`REVISED` (`05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`, `FR-ALGO-D1`/`D2`). This **LLM re-render is invoked
only for gender/agreement deferrals**; **locked-term** consistency is applied by **deterministic string substitution**
with no LLM call, and the sweep is **bounded to segments containing a swept term**. **User-edited `REVISED` segments
are protected** — the sweep proposes but does not overwrite them, re-rendering a user-edited segment only with
explicit user opt-in.

**SYSTEM**

```
You are performing a consistency revision on an already-translated book ({{sourceLang}} → {{targetLang}}).
Using facts now known about the whole book, correct only this one segment so that names, gender agreement and key
terminology are consistent with the rest of the book. The source is given for reference only: do NOT change meaning,
add, or omit content, and do not restyle text that is already consistent.

Style guidance:
{{styleSheet}}

Rules:
- Preserve every placeholder token of the form ⟦gN⟧ EXACTLY (same text, order, count).
- Make every word that agrees with a character named under [Resolved facts] agree with the gender given there.
- {{foreignPassageRule}}
- Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Resolved facts revealed later in the book]
{{resolvedFacts}}

[Source]
{{sourceSegment}}

<Text>
{{currentTarget}}
</Text>

Return exactly one JSON object matching this schema: {"target":"<revised translation>"}
```

| Variable                           | Required? | Source / notes                                                                                       |
|-------------------------------------|-----------|--------------------------------------------------------------------------------------------------------|
| `{{currentTarget}}`                | Required  | The single `<Text>` block: the segment's current masked target — the person's pending proposal or edit for an edited segment. |
| `{{sourceSegment}}`                | Required  | The masked source, shown under `[Source]` outside `<Text>`.                                          |
| `{{resolvedFacts}}`                | Optional  | One line per character whose gender became known, e.g. `- Sam (Сем): female`; the block is dropped when empty. |
| `{{sourceLang}}`, `{{targetLang}}` | Required  | Project languages.                                                                                     |
| `{{styleSheet}}`, `{{foreignPassageRule}}` | Required | The run's style frame, as every repair call carries it.                                          |

No book-wide glossary block is sent: a locked term is already swept deterministically before any revision call, and
the reply must still carry every locked rendering present in the segment (the glossary check).

**Parameters:** temperature 0.2; output format = JSON object / schema; reasoning low/off. **Expected output**

```json
{ "target": "…" }
```

`{"target":"<revised translation>"}` — the same single-segment shape as `#draft-translation`; a deferral that does not
need re-rendering is simply not called.

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
|--------------------------------------------------------------------------------------------------------|-----------|-----------------------------------------------|
| `{{sourceLang}}`, `{{targetLang}}`                                                                   | Required  | `FR-BRIEF-01`.                                |
| `{{genre}}`, `{{register}}`, `{{faithfulNaturalBias}}`, `{{namePolicy}}`, `{{foreignPassagePolicy}}` | Required  | `FR-BRIEF-02..05`; all have defaults.         |
| `{{voiceEra}}`, `{{audience}}`                                                                       | Optional  | Free-text; `(none)` if unset.                 |
| `{{footnotePolicy}}`, `{{unitPolicy}}`                                                               | Optional  | `FR-BRIEF-06/07`; default behaviour if unset. |

**Parameters:** temperature 0.2; output format = JSON object / schema; reasoning low/off. **Expected output**

```json
{ "styleSheet": { "summary": "…", "register": "neutral", "voice": "…",
  "faithfulNaturalBias": 0.5, "namePolicy": "transliterate", "foreignPassagePolicy": "keep", "notes": "…" } }
```

## name-term-pre-scan {#name-term-pre-scan}

A one-time Prep call (phase B, `05_TRANSLATION_ALGORITHM.md#phase-b-prep`, `FR-ALGO-B1`, `FR-GLOSS-01`, DD-46) that
proposes **name/term candidates** — characters, places, organizations, recurring domain terms — each with a **type** and
a **provisional gender**, to seed the user-editable Glossary (Names & Style) step. It is an **app-runtime LLM call**,
distinct from any eval-only embeddings; there is no NER library. Long books are scanned in batches of 40 candidates and
the candidate lists are merged/deduplicated deterministically before display.

**SYSTEM**

```
You are extracting a name and terminology list for a {{sourceLanguage}} → {{targetLanguage}} book translation.
You receive candidates taken from the book's running text, one per line: a capitalised word or run of words, then the
first sentence that holds it.
A name is the proper name of one particular person, place, organisation or named object that the text refers to.
Choose those names and the domain-specific terms that must be translated consistently, and give each one its type
and — for a person — your best guess of the gender that target-language agreement needs, with a confidence.
Never propose: a chapter, section or book title or a phrase from one; a number or an ordinal written as a word
("Six", "Seventh"); a language or a nationality ("Latin", "French"); a general concept or a common noun written with
a capital ("Gravity Formula", "Long Night"); a word capitalised only because it opens a sentence or a line of speech.
Do not translate the terms; propose the source form exactly as listed.
Propose only terms from the candidate list; do not invent entries.
If gender is not inferable, use "unknown".
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Candidates — a capitalised word or run from the book, then the first sentence that holds it]
{{candidates}}

{{#existingTerms}}
[Existing glossary terms — do not duplicate these]
{{existingTerms}}
{{/existingTerms}}

Return JSON exactly as:
{"terms":[{"term":"<source form as listed>","type":"person|place|org|term|other",
 "gender":"male|female|neuter|unknown","note":"<short context/disambiguation>","confidence":<0.0-1.0>}]}
```

| Variable                                 | Required? | Source / notes                                                                                |
|------------------------------------------|-----------|-----------------------------------------------------------------------------------------------|
| `{{sourceLanguage}}`, `{{targetLanguage}}` | Required  | System message; the project's languages (`FR-BRIEF-01`).                                      |
| `{{candidates}}`                         | Required  | The batch's candidates, one `term — first sentence` line each (deterministic scan, at most 40). |
| `{{existingTerms}}`                      | Optional  | Terms the glossary already holds, comma-separated; the whole block is dropped when there are none. |

**Parameters:** temperature 0.2; output format = JSON object / schema; reasoning low/off; output capped at 64 + 48 per candidate, expected half. **Expected output**

```json
{ "terms": [
  { "term": "Hale", "type": "person", "gender": "male", "note": "Mr Hale, arrives ch.1", "confidence": 0.9 },
  { "term": "Baker Street", "type": "place", "gender": "unknown", "note": "street address", "confidence": 0.95 } ] }
```

Tolerant read: `note`/`confidence` may be absent; `gender` defaults to `unknown`; unknown fields ignored.
**Offline/disabled fallback (deterministic):** when the provider is unavailable or the pre-scan is disabled, candidates
are derived by **frequency + casing** — repeated capitalized tokens/phrases not at sentence start (a dash or a quotation
mark also starts one; a sentence-initial word counts once it is seen capitalised mid-sentence twice), ranked by
frequency, leaving out a word written in lower case at least 20% of the time, a word on the source language's bundled
stop-word list and a word mostly seen inside a longer candidate — with `type = other` and **`gender = unknown`** (a person's gender is filled later by the user or by backward revision).
The **unknown-gender heuristic**: any candidate person whose gender stays `unknown`, and any segment referencing such a
person, is a **deferred-resolution** signal (`02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`).

## glossary-review {#glossary-review}

A call the person asks for with **Review with model** on Names & style (`FR-GLOSS-01`, DD-46, call kind
`REVIEW_TERMS`): every unlocked glossary entry with no target is sent, 40 per call, with how many times the book uses it
in any case and up to two sentences that hold it, and the model judges each a name, a term or not a name. The entries
are only written once every call has answered: an entry judged not a name is removed (and remembered as removed) only
while its type is still `other` and its gender `unknown`; an unset type or gender takes the model's guess; a locked
entry or one with a target is never sent or changed.

**SYSTEM**

```
You are reviewing the name list of a {{sourceLanguage}} → {{targetLanguage}} book translation.
The list was gathered by counting capitalised words, so it holds real names and also ordinary words that were
capitalised only because they opened a sentence, a line of speech or a heading.
For each listed term decide:
- "name" — a proper name of a person, a place, an organisation or another named thing;
- "term" — a domain-specific word or phrase that must be translated the same way every time;
- "not-a-name" — an ordinary word (an interjection, a contraction, a common noun, a function word), a chapter or
  book title or a phrase from one, a number or an ordinal written as a word ("Six", "Seventh"), a language or a
  nationality ("Latin", "French"), or a general concept written with a capital ("Gravity Formula").
Give each its type (person, place, org, term, title or other) and, for a person, the gender target-language
agreement needs, or "unknown" when the examples do not show it.
Judge from the count and the example sentences. Keep the term exactly as listed; do not translate it.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
[Terms — each with how many times the book uses it, in any case, and up to two sentences that hold it]
{{terms}}

Return JSON exactly as:
{"verdicts":[{"term":"<term as listed>","verdict":"name|term|not-a-name","type":"person|place|org|term|title|other",
 "gender":"male|female|neuter|unknown"}]}
```

| Variable                                   | Required? | Source / notes                                                                                     |
|--------------------------------------------|-----------|----------------------------------------------------------------------------------------------------|
| `{{sourceLanguage}}`, `{{targetLanguage}}` | Required  | System message; the project's languages (`FR-BRIEF-01`).                                           |
| `{{terms}}`                                | Required  | One `- term — N× — "example" / "example"` line per open entry of the batch (at most 40).           |

**Parameters:** temperature 0.1; output format = strict JSON schema (every field required, closed value lists,
`maxItems` 40); output capped at 2048 tokens, expected 32 per term; reasoning off; the helper-call timeout (120 s).
**Expected output**

```json
{ "verdicts": [
  { "term": "Well", "verdict": "not-a-name", "type": "other", "gender": "unknown" },
  { "term": "Hale", "verdict": "name", "type": "person", "gender": "male" } ] }
```

Tolerant read: a verdict on a term outside the batch is dropped; an unlisted verdict, type or gender reads as no
opinion; an unreadable reply is no verdicts, so it changes nothing. The pseudo model judges every listed term a name
with no type or gender, so a review with it changes nothing.

## rolling-summary-update {#rolling-summary-update}

Refreshes the rolling **bilingual** summary carried into later prompts, on a **size-based trigger — every K accepted
blocks OR at chapter end, whichever comes first** (`05_TRANSLATION_ALGORITHM.md#chunk-loop`, `FR-ALGO-07`;
`02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`). The **default is a deterministic summary** (accumulated key
facts, truncated/condensed to budget) — kept for counting and condensing, but **never shown to a draft or to the
person as a summary**: it is the glossary lines and the heading titles, which already reach the draft through the
glossary block or tell the model nothing about the story. Only this **LLM-generated** variant (the Max dial, opt-in) is
the summary a draft and the context panel show; without it there is none, and the context panel says so. For a no-chapter document (TXT /
single chapter) the every-K-blocks trigger drives updates and end-of-document acts as the chapter-end trigger.

**SYSTEM**

```
You maintain a short rolling bilingual summary of a book being translated ({{sourceLanguage}} → {{targetLanguage}}).
Update the running summary with what this chapter established: characters, relationships, places, and
terminology decisions. Keep it compact and factual — it is context for translating later chapters, not a retelling.
Provide the summary in both {{sourceLanguage}} and {{targetLanguage}}, each at most 150 words, and at most five facts.
Output ONLY the required JSON object. No commentary, no code fences, no reasoning.
```

**USER**

```
{{#previousSummary}}
[Running summary so far]
{{previousSummary}}

{{/previousSummary}}
[This chapter — source]
{{chapterSource}}

[This chapter — accepted target]
{{chapterTarget}}

Return JSON exactly as:
{"summary":{"source":"<updated summary in the source language>","target":"<updated summary in the target language>"},
 "facts":["<key fact>","<key fact>"]}
```

| Variable                                 | Required? | Source / notes                                                                           |
|--------------------------------------------|-----------|--------------------------------------------------------------------------------------------|
| `{{sourceLanguage}}`, `{{targetLanguage}}` | Required  | System message; the project's languages. The user message names them generically.         |
| `{{chapterSource}}`, `{{chapterTarget}}` | Required  | The just-finished unit's accepted source and target display text, one segment per line.  |
| `{{previousSummary}}`                    | Optional  | The latest summary (its target text, else its deterministic text); the whole block is dropped for the first unit. |

**Parameters:** temperature 0.2; output format = JSON object / schema; reasoning low/off; output capped at 1024 tokens, expected 600 (at most 150 words per language and five facts). **Expected output**

```json
{ "summary": { "source": "…", "target": "…" },
  "facts": [ "Hale is male", "Story set at 7 Baker Street" ] }
```

Tolerant read: `facts` may be absent; unknown fields ignored; a reply that is not JSON, or whose `summary.target` is
empty, is unreadable — the previous summary is kept and one WARN is logged; the bilingual `summary` is stored per
`02_Architecture/06_DATA_MODEL_SQLITE.md#summaries`.
