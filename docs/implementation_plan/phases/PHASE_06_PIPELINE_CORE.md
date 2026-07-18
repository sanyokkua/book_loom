---
phase: PHASE_06_PIPELINE_CORE
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#context-package
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping
  - docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-status-state-machine
  - docs/specification/01_Product/12_PROMPT_CATALOG.md#draft-translation
  - docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#prompt-evals
  - FR-ALGO-01
  - FR-ALGO-02
  - FR-ALGO-03
  - FR-ALGO-04
  - FR-ALGO-08
  - FR-ALGO-09
  - FR-ALGO-11
  - FR-BRIEF-08
  - FR-DOC-05
  - FR-QA-01
  - FR-QA-03
  - FR-QA-04
  - FR-QA-05
  - FR-RESUME-01
  - FR-REVIEW-06
  - DD-15
  - DD-17
  - DD-19
  - DD-26
  - DD-27
  - DD-40
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`, `docs/specification/diagrams/pipeline.mermaid`,
`docs/specification/diagrams/chunk-translate-loop.mermaid`

# PHASE_06 — Pipeline Core

## Goal

Assemble the automatic translation engine end to end for a single translator model: paragraph-grouped chunking with
sentence-split overflow, the context-package assembler, the JSON-array draft request with per-segment fallback, unmask +
placeholder validation, the deterministic QA gate with a confidence score, the segment-status transitions, and per-chunk
persist/resume. This is the first phase where a book actually translates. It establishes seam F5 (context package) and
consumes F1/F4/F6.

## In scope

- Paragraph-grouped chunking up to the token budget without splitting a paragraph; sentence-split (ICU4J
  `BreakIterator`) only on overflow.
- The context-package assembler (seam F5): system+brief, rolling summary (empty at this phase), relevant glossary (empty
  at this phase), preceding-target window, TM hits (empty at this phase), masked source — with load-bearing items at the
  prompt edges.
- Prompt building; draft request as a JSON array keyed by segment id; per-segment fallback when ids mismatch.
- Unmask + placeholder-multiset hard gate (reuses PHASE_03); the deterministic QA gate (tag integrity, target-language,
  untranslated-echo, refusal, repetition-loop, omission-by-length-ratio, number/NE preservation, confidence score);
  policy-aware target-language check for foreign passages.
- Segment status state machine PENDING → ACCEPTED/FLAGGED; preceding-target window update; per-chunk atomic checkpoint
  (reuses F6); pause/resume/resume-on-launch.
- The `TranslationEngine` port driving all inference through the `InferenceGate` (F4).

## Out of scope

- LLM-as-judge and self-heal repair tiers (PHASE_08) — a chunk that fails the deterministic gate here goes straight to
  FLAGGED.
- Name/term dictionary, context-aware TM, rolling summary content (PHASE_07) — the assembler slots exist but are fed
  empty/degenerate sources.
- Backward revision / consistency sweep (PHASE_12).
- Any UI (PHASE_10 renders this engine's state).

## Dependencies

PHASE_03 (masking + placeholder validation), PHASE_04 (checkpoints, repositories), PHASE_05 (provider, inference, gate).

## Forward-compatibility

- **Establishes F5** — the context-package assembler where new context sources plug in without changing the loop
  (consumed by PHASE_07, PHASE_08, PHASE_12).
- **Consumes F1** (segments), **F4** (gate), **F6** (checkpoints).
- Leaves clean extension points for the judge, self-heal, and consistency stack.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                                                                                  | Target modules                                                                      | Cited spec clauses                                                                                                   |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Paragraph-grouped chunking up to the token budget                                                                                                                               | `:pipeline/ua.bookloom.pipeline.chunk`                                              | FR-ALGO-02, `01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, DD-19                                                 |
| Sentence-split overflow via ICU4J BreakIterator                                                                                                                                 | `:pipeline/ua.bookloom.pipeline.chunk`                                              | FR-ALGO-03, `01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, EC-CTX-*                                              |
| Context-package assembler with load-bearing items at prompt edges                                                                                                               | `:pipeline/ua.bookloom.pipeline.context`                                            | FR-ALGO-04, `01_Product/05_TRANSLATION_ALGORITHM.md#context-package` (seam F5)                                       |
| Soft-reset + cap the preceding-target window (~3 blocks) at chapter start                                                                                                       | `:pipeline/ua.bookloom.pipeline.context`                                            | FR-ALGO-08, DD-19                                                                                                    |
| Prompt build + JSON-array draft keyed by segment id; per-segment fallback                                                                                                       | `:pipeline/ua.bookloom.pipeline.prompt`                                             | FR-ALGO-09, `01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`, `01_Product/12_PROMPT_CATALOG.md#draft-translation` |
| Local prompt eval for the draft-translation prompt (`promptEval` tag; calls the real prompt builder → real local model → structural/cosine rubric; env-gated, excluded from CI) | `:pipeline/src/test/.../eval`                                                       | DD-40, `04_Build_and_Release/06_TESTING_STRATEGY.md#prompt-evals`                                                    |
| Unmask + placeholder-multiset hard gate                                                                                                                                         | `:pipeline/ua.bookloom.pipeline`, `:document/ua.bookloom.document.mask`             | FR-DOC-05, FR-QA-04                                                                                                  |
| Deterministic QA checks + confidence score                                                                                                                                      | `:pipeline/ua.bookloom.pipeline.qa`                                                 | FR-QA-01, FR-QA-05, `01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`, DD-17                                       |
| Policy-aware target-language check (foreign passages not flagged)                                                                                                               | `:pipeline/ua.bookloom.pipeline.qa`                                                 | FR-QA-03, DD-26, EC-FOREIGN-*                                                                                        |
| Segment status state machine PENDING → ACCEPTED/FLAGGED                                                                                                                         | `:pipeline/ua.bookloom.pipeline`, `:api/ua.bookloom.api.pipeline`                   | FR-REVIEW-06, `01_Product/06_REVIEW_AND_EDITING.md#segment-status-state-machine`                                     |
| Per-chunk atomic checkpoint + pause/resume/resume-on-launch                                                                                                                     | `:pipeline/ua.bookloom.pipeline`, `:persistence/ua.bookloom.persistence.checkpoint` | FR-RESUME-01..03, DD-27                                                                                              |
| Automatic end-to-end run targeting ~99% first-pass acceptance                                                                                                                   | `:pipeline/ua.bookloom.pipeline`                                                    | FR-ALGO-01, DD-15                                                                                                    |
| Quality-dial → parameters mapping (chunk size, preceding-target count, τ)                                                                                                       | `:pipeline/ua.bookloom.pipeline.dial`                                               | FR-ALGO-11, FR-BRIEF-08, `01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`                               |

## Phase exit checklist

- [ ] A whole book translates automatically end to end with a single translator model against WireMock stubs.
- [ ] Chunking packs whole paragraphs to the budget and sentence-splits only on overflow.
- [ ] The context-package assembler composes all slots with load-bearing items at the edges (F5 established).
- [ ] Draft parsed from a JSON array keyed by segment id; per-segment fallback on id mismatch.
- [ ] Placeholder-multiset mismatch hard-fails; deterministic QA gate + confidence score run; foreign passages are
  policy-aware.
- [ ] Segments transition PENDING → ACCEPTED/FLAGGED deterministically; ~1% flagged path exists.
- [ ] Per-chunk checkpoints persist atomically; pause/resume and resume-on-launch work without redoing completed
  segments.
- [ ] All inference goes through the `InferenceGate`; offline invariant intact (WireMock only).
- [ ] FX-free (ArchUnit green); `./gradlew test` and `./gradlew traceCheck` green; module inventory updated.
