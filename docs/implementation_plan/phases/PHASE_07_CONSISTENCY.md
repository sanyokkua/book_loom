---
phase: PHASE_07_CONSISTENCY
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#context-package
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-b-prep
  - FR-ALGO-04
  - FR-ALGO-05
  - FR-ALGO-06
  - FR-ALGO-07
  - FR-GLOSS-01
  - FR-GLOSS-02
  - FR-GLOSS-03
  - FR-GLOSS-04
  - FR-GLOSS-05
  - DD-18
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`

# PHASE_07 — Consistency

## Goal

Add the consistency stack that keeps names, terms, and tone stable across a whole book: the name/term dictionary (with
type and gender for target-language agreement and optional hard-lock), context-aware translation memory, the rolling
bilingual summary updated per chapter, and the glossary pre-scan that proposes names/terms. These feed the
context-package assembler's previously-empty slots (F5) without changing the loop.

## In scope

- Name/term dictionary carrying type and gender for target-language agreement; optional hard-lock via placeholder
  substitution (reuses the PHASE_03 masking primitive).
- Context-aware TM: automatic reuse only when neighbours also match; exact-match as a hint; fuzzy as a suggestion.
- Rolling bilingual summary updated at each chapter/unit end and injected into later prompts.
- Glossary auto pre-scan proposing names/terms; editable glossary entries (type + gender), lock, import/export
  (persistence + engine side; the table UI is PHASE_10).
- Wiring these sources into the context-package assembler's glossary/TM/summary slots.

## Out of scope

- LLM-as-judge and self-heal (PHASE_08).
- The glossary editing screen and side-by-side review UI (PHASE_10).
- Whole-book backward revision (PHASE_12) — the rolling summary and dictionary are built here; the retrospective sweep
  comes later.

## Dependencies

PHASE_06 (context-package assembler F5, the running loop, persistence of segments/TM).

## Forward-compatibility

- **Consumes F5** — plugs the glossary, TM, and rolling-summary sources into the assembler slots without reshaping the
  loop.
- Produces the name dictionary + rolling summary that PHASE_12's deferred-resolution and consistency sweep build on.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                          | Target modules                                                                        | Cited spec clauses                                                                    |
|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| Name/term dictionary with type + gender for target-language agreement                                   | `:pipeline/ua.bookloom.pipeline.memory`, `:persistence/ua.bookloom.persistence.dao`   | FR-ALGO-05, FR-GLOSS-05, `01_Product/05_TRANSLATION_ALGORITHM.md#phase-b-prep`, DD-18 |
| Optional hard-lock of a term via placeholder substitution                                               | `:pipeline/ua.bookloom.pipeline.memory`, `:document/ua.bookloom.document.mask`        | FR-GLOSS-03, FR-ALGO-05                                                               |
| Context-aware TM (auto-reuse only when neighbours match; exact=hint; fuzzy=suggestion)                  | `:pipeline/ua.bookloom.pipeline.memory`, `:persistence/ua.bookloom.persistence.dao`   | FR-ALGO-06, `01_Product/05_TRANSLATION_ALGORITHM.md#context-package`, DD-18           |
| Rolling bilingual summary updated per chapter and injected into prompts                                 | `:pipeline/ua.bookloom.pipeline.memory`                                               | FR-ALGO-07, DD-18                                                                     |
| Glossary auto pre-scan proposing names/terms                                                            | `:pipeline/ua.bookloom.pipeline.glossary`                                             | FR-GLOSS-01, `01_Product/05_TRANSLATION_ALGORITHM.md#phase-b-prep`                    |
| Editable glossary entries (source/target/type/gender), lock, import/export (engine + persistence)       | `:pipeline/ua.bookloom.pipeline.glossary`, `:persistence/ua.bookloom.persistence.dao` | FR-GLOSS-02, FR-GLOSS-04                                                              |
| Wire glossary/TM/summary into the context-package assembler slots (inject only in-chunk glossary terms) | `:pipeline/ua.bookloom.pipeline.context`                                              | FR-ALGO-04, `01_Product/05_TRANSLATION_ALGORITHM.md#context-package`                  |

## Phase exit checklist

- [ ] The name/term dictionary carries type + gender and drives target-language agreement; hard-lock enforced via
  placeholder substitution.
- [ ] Context-aware TM reuses only when neighbours match; exact-match is a hint and fuzzy a suggestion (never a force).
- [ ] The rolling bilingual summary updates at chapter end and appears in later prompts.
- [ ] The glossary pre-scan proposes names/terms; entries are editable, lockable, and import/exportable at the engine
  layer.
- [ ] The context-package assembler injects only the glossary terms occurring in the chunk, at prompt edges.
- [ ] Consistency across a multi-chapter fixture is measurably improved vs PHASE_06 (name/term stability test).
- [ ] FX-free (ArchUnit green); `./gradlew test` and `./gradlew traceCheck` green; module inventory updated.
