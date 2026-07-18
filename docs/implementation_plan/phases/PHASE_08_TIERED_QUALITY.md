---
phase: PHASE_08_TIERED_QUALITY
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal
  - docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#prompt-evals
  - FR-ALGO-01
  - FR-ALGO-11
  - FR-BRIEF-08
  - FR-QA-02
  - FR-QA-05
  - FR-REVIEW-03
  - DD-16
  - DD-17
  - DD-40
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/diagrams/chunk-translate-loop.mermaid`

# PHASE_08 — Tiered Quality

## Goal

Complete the quality loop: the LLM-as-judge score compared to τ, and the tiered self-heal that fires only on QA
failure — a directed fix (1 call) when there are concrete QA findings, otherwise reflect→improve (2 calls) with an
optional monolingual polish — looping through QA up to the repair budget N before a chunk is FLAGGED. The single quality
dial (Fast/Balanced/Max) governs chunk size, preceding-target count, repair budget N, whether the judge runs, and
whether backward revision runs; τ is owned by the review-mode dial (DD-45), and the judge score is compared to τ_judge.

## In scope

- LLM-as-judge quality scoring on drafts that pass deterministic checks; compare to τ; judge on/off per dial.
- Tiered self-heal: directed fix (1 call) injecting concrete QA findings; reflect→improve (2 calls) + optional
  monolingual polish when no concrete findings; loop through QA up to N tries; still-failing → FLAGGED.
- Recording per-segment QA findings for directed-fix and for later review.
- Full quality-dial mapping including τ, repair budget N, judge on/off, and backward-revision on/off flag.

## Out of scope

- The backward-revision execution itself (PHASE_12) — this phase sets the dial flag that enables it.
- Review UI (PHASE_10).
- Neural QE sidecar (documented future extension, not built).

## Dependencies

PHASE_06 (deterministic QA gate, loop, gate F4/F5). PHASE_07 for full context feeding the judge and repair
(recommended).

## Forward-compatibility

- **Consumes F4** (all judge/repair calls go through the gate) and **F5** (findings and context feed repair prompts).
- Finalizes the quality-dial contract that PHASE_12 reads for backward-revision on/off.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                                                                                                                | Target modules                                                                  | Cited spec clauses                                                                                   |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| LLM-as-judge quality score on passing drafts, compared to τ; judge on/off per dial                                                                                                                            | `:pipeline/ua.bookloom.pipeline.judge`                                          | FR-QA-02, `01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`, DD-17                                  |
| Directed-fix self-heal (1 call) injecting concrete QA findings                                                                                                                                                | `:pipeline/ua.bookloom.pipeline.heal`                                           | FR-QA-05, `01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`, DD-16                                  |
| Reflect→improve self-heal (2 calls) + optional monolingual polish when no concrete findings                                                                                                                   | `:pipeline/ua.bookloom.pipeline.heal`                                           | `01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`, DD-16                                            |
| Loop through QA up to repair budget N; still-failing → FLAGGED (~1%)                                                                                                                                          | `:pipeline/ua.bookloom.pipeline.heal`, `:pipeline/ua.bookloom.pipeline`         | FR-ALGO-01, FR-REVIEW-03, `01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`                        |
| Record per-segment QA findings for directed-fix and review                                                                                                                                                    | `:pipeline/ua.bookloom.pipeline.qa`, `:persistence/ua.bookloom.persistence.dao` | FR-QA-05                                                                                             |
| Full quality-dial mapping (chunk size, preceding-target count, N, judge on/off, backward-revision on/off — never τ, DD-45)                                                                                    | `:pipeline/ua.bookloom.pipeline.dial`                                           | FR-ALGO-11, FR-BRIEF-08, `01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`               |
| Local prompt evals for the judge, directed-fix, and reflect-critique/rewrite prompts (`promptEval` tag; real prompt builder → real local model → structural/field/cosine rubric; env-gated, excluded from CI) | `:pipeline/src/test/.../eval`                                                   | DD-40, `04_Build_and_Release/06_TESTING_STRATEGY.md#prompt-evals`, `01_Product/12_PROMPT_CATALOG.md` |

## Phase exit checklist

- [ ] The judge scores drafts that pass deterministic checks and compares to τ; judge runs only when the dial says so.
- [ ] Self-heal fires only on QA failure: directed fix (1 call) with concrete findings, else reflect→improve (2 calls) +
  optional polish.
- [ ] The repair loop respects budget N and FLAGS a chunk that still fails (~1% path).
- [ ] Per-segment QA findings are recorded and reused by directed-fix and available for review.
- [ ] The quality dial maps to chunk size, preceding-target count, N, judge on/off, and backward-revision on/off (never
  τ — DD-45).
- [ ] All judge/repair calls go through the `InferenceGate`; offline invariant intact (WireMock only).
- [ ] FX-free (ArchUnit green); `./gradlew test` and `./gradlew traceCheck` green; module inventory updated.
