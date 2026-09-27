# Proposal

## Why

BookLoom can open a book of any of its four formats, send it to a local model one segment at a time behind the
placeholder hard gate, and write it back canonical-equal. Everything that makes the translation *good* is missing:
nothing the Book Brief asks for reaches the prompt, there is no glossary, no translation memory, no rolling summary, no
deterministic quality check beyond the placeholder gate, no judge, no repair beyond two format retries, no way to review
or edit a segment, and no whole-book consistency pass. The window draws most of the reference rendering and disables it:
four Book Brief cards, the Names & style and Review entries, the live chunk panel, throughput, the export options.

The owner asked for one change that closes that gap — the pipeline stages PHASE_06–08 and PHASE_12 and the screens
PHASE_09–10 of `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#staged-delivery` — so that at the end a
person can import a book, brief it, see its structure, settle its names, watch it translate live, review and edit
segments, and export it with side files. What remains after it: SQLite persistence, Settings persistence and the other
settings tabs, Projects, new provider kinds, packaging and release.

## What Changes

**BREAKING (internal only):** the translation job no longer writes the book. `TranslationRequest` is replaced by a run
request that names a stored project; export becomes its own job (ADR-0035). Every caller is inside this repository
(`:ui`, `:app`, tests) and moves in this change. The command line keeps its flags, its output name and its exit codes.

- **Working state behind storage ports.** Projects, segment decisions (machine target kept apart from the user's edit),
  glossary, translation memory, rolling summary, deferrals and runs are reached through repository ports in `:api`,
  implemented in memory in `:persistence` (ADR-0034). Nothing survives a restart; stopped runs resume within the
  session.
- **The Book Brief drives the prompts.** Editable, searchable source and target languages (34 languages; the declared
  language preselected and normalized); genre (searchable list or free text), register, narrative voice/era, audience;
  name, foreign-passage, footnote and unit policies and the faithful↔natural balance; the four "Also translate"
  switches; the quality dial with its model row. A deterministic style sheet is derived from them and injected into every
  call (FR-BRIEF-01..09, `01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`; FR-ALGO-B2). The destination leaves the
  brief.
- **The consistency stack.** A glossary seeded by a deterministic scan and, on request, an LLM pre-scan; locked terms
  masked so they render exactly; only terms occurring in the chunk injected with type and gender (FR-GLOSS-01..05,
  `#fr-gloss`). A context-aware translation memory (exact, context, fuzzy), a rolling bilingual summary and a context
  package with load-bearing items at the prompt's edges (seam F5, FR-ALGO-04..08, `#fr-algo`).
- **Names grow during the run** (FR-ALGO-C9, `01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`). At the end of each
  chapter a deterministic re-scan adds names it has not seen before as unlocked entries with no target; it never
  overwrites an existing entry and never brings back one the person removed.
- **Kept foreign runs** (EC-FOREIGN-3, `01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`). Under the
  Keep foreign-passage policy, an inline element whose own `lang`/`xml:lang` differs from the run's source language is
  protected verbatim as one token — read from the metadata only, never detected from the text.
- **Quality gates.** Deterministic checks — refusal and placeholder integrity (now also pair order, ADR-0040) as hard
  gates; target script, untranslated echo, repetition, length ratio and glossary compliance blended into a confidence —
  an LLM judge per chunk, and self-heal by directed fix or reflect → improve → polish within the dial's repair budget
  (FR-QA-01..07, `#fr-qa`; ADR-0038). A soft check that fails outright blocks acceptance and sends the segment to
  repair; confidence decides only the close calls. Every prompt comes from the catalogue as a data template.
- **Review modes and review inside Translating.** Unattended (default) never pauses; Assisted pauses on each flagged
  segment or error; Manual pauses after every segment; each owns τ (ADR-0036). The mode comes from a launch flag until
  Settings are persisted. The review panel lists flagged segments with filters and offers Accept, Save edit, Revert to
  machine target, Retry, Retry with note and Skip beside a side-by-side compare (FR-REVIEW-01..09, `#fr-review`).
- **A live Translating screen.** Progress by percentage, chapter and chunk; time left and tokens per second (from the
  provider's usage figures, estimated when absent); auto-accepted / repaired / flagged / remaining tiles; a two-row
  "Current chunk (live)" panel; a tagged activity log; Running, Paused, Stopped (resumable in the session) and Provider
  error (auto-paused, with Retry now) states. The title bar shows the book, its state and a single Pause/Resume while a
  run exists.
- **Backward revision.** Deferred-resolution items from the judge and an unknown-gender heuristic, a locked-term sweep
  by deterministic substitution, and an LLM re-render only for gender deferrals; user edits are protected (FR-ALGO-D1..D3,
  `05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`). It runs on the Max dial or from Export's consistency pass.
- **Export as its own action.** Save to / Browse / overwrite, glossary CSV, bilingual HTML and Markdown quality report
  side files, the final consistency pass, an export-complete dialog; allowed at any time except while a run is actively
  translating, partial books included (FR-EXPORT-04..06, `#fr-export`; ADR-0035).
- **Import, Structure and Names & style completed.** Cover (or a placeholder), format version, chapters and words,
  images and fonts, and distinct Detected, Language mismatch, DRM blocked and Unsupported states from a new inspection
  (FR-IMPORT-03..07, `#fr-import`; ADR-0037, ADR-0039). A structure tree with chapter titles, statistics, a background
  round-trip check and the resource-id check. A glossary table with Add term, import/export and Start translation.
- **Auxiliary text.** Metadata title, author and description, ToC/navigation labels, page titles, image alt text and
  Markdown frontmatter values become translatable units with write-back (FR-DOC-11, `#fr-doc`; DD-47; ADR-0041). Only
  frontmatter text values become segments — never `lang`, and never numbers, booleans (YAML `yes`/`on` included),
  nulls, dates, URLs, lists or maps. On export an existing top-level `lang` value is replaced by the target language
  tag, keeping its quotes; a `lang` key is never added.
- **Plain-text exports the book's encoding cannot hold** (ADR-0029,
  `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`). A TXT or Markdown export whose translation has a character the
  book's charset cannot represent is written entirely as UTF-8, keeping a byte-order mark only if the source had one,
  instead of being refused or silently corrupted to `?`. EPUB keeps its lossless numeric character references; FB2
  keeps its existing switch.
- **Fixes from `docs/next_features.md`** in the modules this change touches: EPUB language attributes (§1), package
  document declaration and line ends (§2), attribute line-feed references (§3), XHTML prolog (§4), FB2 byte-order mark
  (§5), the UTF-8 fallback above (§6), the jsoup reference pin and the ADR-0029 wording (§7), paired placeholder order
  (§9), Markdown bare URLs (§10), log volume (§12), the command line's Ctrl+C and Guice warning (§13), per-segment
  export verification (§14), the stray interrupt clear, the Brief's truncated Ukrainian labels, the Windows reveal exit
  code and provider errors that should pause the run (§15, backlog D19). Oversized segments are split at sentence
  boundaries (backlog D5).
- **Settings, lightly.** Providers tab only: underline tabs, subtitle, provider rows with host and status, and three test
  actions reporting measured time and model count. Nothing else in Settings changes.

Assumptions taken, each reversible:

1. **One change, far above the ~10-task norm** (`AGENTS.md#how-work-is-planned`). The owner asked for it explicitly and
   asked that it not be split; the task list is grouped so each group can be merged and reviewed on its own.
2. **No text-based language detection** (owner decision, ADR-0037): the source language is the declared one, editable.
3. **τ values** 0.60 / 0.75 / 0.85 for Unattended / Assisted / Manual — the specification gives none.
4. **Ollama receives `num_ctx` 8192**, because its default silently truncates longer prompts; OpenAI-compatible servers
   receive no context field.
5. **Glossary proposals start unlocked**, because a locked term cannot inflect (Ukrainian and Polish cases); the person
   locks what must never change.
6. **A soft check that fails outright blocks acceptance** (owner decision): it raises a medium finding, so the segment
   is repaired and then flagged; confidence decides only close calls. The echo check blocks only when the source's
   display text has at least 20 code points — below that it lowers confidence only, so short names, Roman numerals and
   "OK" are not flagged en masse. The pseudo model's upper-cased echo of a source of 20 or more code points is
   therefore flagged in every review mode.
7. **Foreign runs are known from metadata only** (owner decision, ADR-0037): an inline element is a kept foreign run
   when its own declared language differs from the source language; no text is examined.
8. **Review stays a panel inside Translating** (owner decision, ADR-0036) — an approved departure from the mockup,
   which draws a separate Review step.
9. **Every other number this change invents** — chunk caps, check margins, the echo floor, the fuzzy threshold,
   summary size, batch sizes, temperatures, timeouts — is listed once, with its source, in the design's "Tuning
   constants" table.

## Capabilities

### New Capabilities

- `quality-gates`: the deterministic checks every translated segment must pass or score on, the confidence they blend
  into, the trust threshold each review mode sets, the per-chunk LLM judge, and the self-heal rounds that repair a
  failing segment before it is flagged — the gate between a draft and an accepted translation.
- `glossary`: the book's names and terms — proposed by a scan, edited by the person on Names & style, imported and
  exported as CSV, injected into prompts only where they occur, and, when locked, enforced exactly.
- `review-queue`: how a person inspects and corrects segments inside the Translating screen — the flagged list, the
  side-by-side compare, the six actions, the segment status machine, and the pauses each review mode causes.

### Modified Capabilities

- `book-import`: the import card reports cover, format version, chapters, words, images and fonts; declared languages
  are normalized; DRM-blocked and Unsupported become distinct states naming their cause; the language-mismatch state
  gets a metadata trigger.
- `book-brief`: every brief choice becomes live and reaches the prompts; the source language becomes editable; the
  language lists grow and become searchable; the destination moves to Export.
- `document-round-trip`: chapter titles, statistics and the cover are reported; auxiliary text becomes translatable
  units with write-back; paired placeholders must keep order; oversized segments split at sentence boundaries; the
  next_features fidelity fixes; the structure listing becomes a titled tree.
- `translation-pipeline`: the run reads the brief, packs chunks, assembles the context package, reuses memory, keeps a
  summary, reports live text and throughput, pauses on provider errors instead of flagging, and no longer writes the
  book; the command line translates then exports.
- `resume`: pause points for review; a stopped run resumes within the session; Retry now after a provider error.
- `export`: the destination is chosen on Export; export runs any time a run is not actively translating, writes side files and can run the consistency
  pass; the written book is verified segment by segment.
- `app-shell`: the title bar shows the run; Names & style becomes a screen and the Review entry goes; every fixed list
  is searchable by typing.
- `settings`: the Providers tab's three test actions and provider rows.
- `llm-provider`: token usage is read from both dialects; Ollama receives the context size; verification reports
  measured values.
- `inference`: a reply carries token usage when the provider reports it; a request may carry a context size.
- `notifications`: the provider-error state is an auto-pause with Retry now; a finished export is confirmed in a dialog.

## Impact

- **Modules:**
  - `:api` — storage ports and project records (`ua.bookloom.api.persistence`, `ua.bookloom.api.project`), the brief and
    its enums, the inspection and profile contracts, service ports for projects, glossary, review and export, new job
    events, token usage. The job's request and report change shape.
  - `:util` — the language catalogue and tag normalization (`ua.bookloom.util.lang`).
  - `:persistence` — in-memory adapters for every port (`ua.bookloom.persistence.memory`); still no database.
  - `:llm` — usage mapping for both dialects, `num_ctx` for Ollama, measured verification, an output-scaled timeout, a
    pseudo model that answers every catalogue response format.
  - `:document` — inspection, cover, titles, statistics, auxiliary units and their write-back, the sentence splitter,
    the pair-order check, the UTF-8 fallback for TXT and Markdown, and the next_features fidelity fixes.
  - `:pipeline` — most of the change: prompts as data, style sheet, chunks, context package, glossary, memory, summary,
    QA, judge, self-heal, dial, review desk, export job, backward revision, the reworked job.
  - `:ui` — a searchable combo, the title-bar run status, and rebuilt Import, Book Brief, Structure, Names & style,
    Translating (with review), Export and Providers screens; three new dialogs.
  - `:app` — the review-mode launch flag, bindings, and the command line's translate-then-export flow.
- **Dependencies:** `org.commonmark:commonmark-ext-autolink` (BSD-2-Clause, same project as the CommonMark parser
  already used) in `:document`, so a bare URL in Markdown prose is masked. No other new dependency; ICU4J (already in
  `:document`) does the sentence splitting.
- **Network:** unchanged in kind — user-triggered provider communication only. New calls (judge, repairs, summary,
  revision) happen inside a run the person started; the LLM glossary pre-scan runs only when its button is pressed.
- **Decision records:** ADR-0034 (storage ports, in-memory), ADR-0035 (export separate), ADR-0036 (review modes pause),
  ADR-0037 (metadata-only language, script check), ADR-0038 (judge per chunk, generate per segment), ADR-0039 (import
  refusal as data), ADR-0040 (placeholder pairs keep order), ADR-0041 (translatable alt text). ADR-0033 is amended by
  ADR-0035. ADR-0029 is implemented for TXT and Markdown here, and ADR-0034, 0036, 0037, 0038 and 0040 carry dated
  amendments for the owner's decisions above.
- **Docs corrected in this change** (the first task group): `08_UI_SCREENS_AND_STATES.md` (every screen this change
  touches, the translating states, review inside Translating), `07_UI_ARCHITECTURE_JAVAFX.md#jobprogress` and
  `#screens`, FR-IMPORT-03/06, FR-BRIEF-01, FR-EXPORT-04, FR-REVIEW-01/09, FR-ALGO-A4/C13, DD-30 and DD-45 wording,
  `02_TRANSLATION_WORKFLOW.md#review-modes` and `#workflow-states-and-recovery`, the QA target-language row and the
  foreign-keep rule, the prompt catalogue's multi-segment shapes, `06_DATA_MODEL_SQLITE.md#ddl-normative` (machine
  target), `03_DOCUMENT_MODEL.md#metadata-unit` (units always produced; the pipeline applies the switches),
  `09_ERROR_HANDLING.md` (no new code; inspection verdicts), ADR-0029's EPUB wording and status, `AGENTS.md` and
  `.claude/rules/document-roundtrip.md` (the alt-text exception, pair order), and the backlog, module inventory and
  `docs/next_features.md` at the gate. Also the clauses the owner's decisions contradict: numeral masking (FR-DOC-04,
  EC-INLINE-4), the automatic pre-scan (FR-ALGO-B1, FR-GLOSS-01, DD-46), the context size (32768 → 8192: FR-ALGO-02,
  C1, DD-44, FR-MODEL-07, `07_SETTINGS.md`), batched drafts (FR-ALGO-09), per-segment acceptance (FR-QA-07), the sweep
  (FR-ALGO-D2), the reflect temperature, the metadata-only foreign rules (EC-FOREIGN-1/3), the source-change warning
  (FR-RESUME-04), DD-40, DD-47, the REVIEW navigation entry, and the single-flight rule texts in `openspec/config.yaml`
  and `.claude/rules/llm-provider-integration.md`.
- **Non-goals:** SQLite and Flyway; resume across a restart; Settings persistence and the Models, Generation,
  Automation and Storage tabs; adding or editing providers; the Projects screen; text-based language detection;
  numeral masking (owner decision: not built in this change; the reference clauses are edited to say so); an
  automatic model name pre-scan at run start (the pre-scan runs only from its button; FR-ALGO-B1 and DD-46 edited); the
  `promptEval` harness; streaming replies; the LLM tone-setup call (the style sheet is deterministic); the other
  writer-policy debts (D4, D6, D11, D13); packaging and release.
