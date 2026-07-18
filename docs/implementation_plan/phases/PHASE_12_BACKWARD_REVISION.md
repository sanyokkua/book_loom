---
phase: PHASE_12_BACKWARD_REVISION
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping
  - FR-ALGO-10
  - FR-ALGO-11
  - FR-EXPORT-06
  - FR-RESUME-01
  - FR-REVIEW-06
  - DD-16
  - DD-27
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`

# PHASE_12 — Backward Revision

## Goal

Add the optional whole-book pass that resolves deferred items with full-book facts and re-renders affected earlier
segments, plus a global term-consistency sweep. Segments revised this way move to REVISED. The pass is enabled by the
Max quality dial (and the export final-consistency toggle) and runs crash-safely over the checkpointed state.

## In scope

- The deferred-resolution register: recording items during PHASE_06–PHASE_08 that need whole-book facts to resolve (the
  register is populated by the running loop; this phase adds the retrospective resolution).
- Backward revision: with full-book facts (final name dictionary, rolling summary), re-render flagged/affected earlier
  segments → REVISED.
- Global term-consistency sweep across the whole book.
- Crash-safe execution over checkpointed state (reuses F6); wired to the Max dial and the export final-consistency
  toggle.

## Out of scope

- New UI beyond the already-exposed toggle/dial (built in PHASE_10/PHASE_11).
- Packaging/release (PHASE_13).

## Dependencies

PHASE_07 (name dictionary, rolling summary, TM — the full-book facts), PHASE_08 (quality dial with backward-revision
on/off, self-heal, judge). Uses F6 (checkpoints) and F5 (context assembler).

## Forward-compatibility

- **Consumes F5** (context assembler feeds re-render prompts with full-book facts) and **F6** (revision runs
  crash-safely over checkpoints).
- Closes the optional whole-book quality loop; no new seam is introduced.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                        | Target modules                                                                               | Cited spec clauses                                                                           |
|---------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| Deferred-resolution register: record items needing whole-book facts                   | `:pipeline/ua.bookloom.pipeline.revision`, `:persistence/ua.bookloom.persistence.dao`        | FR-ALGO-10, `01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`, DD-16        |
| Backward revision: re-render affected earlier segments with full-book facts → REVISED | `:pipeline/ua.bookloom.pipeline.revision`                                                    | FR-ALGO-10, FR-REVIEW-06, `01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision` |
| Global term-consistency sweep across the whole book                                   | `:pipeline/ua.bookloom.pipeline.revision`, `:pipeline/ua.bookloom.pipeline.memory`           | FR-ALGO-10, `01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`               |
| Crash-safe execution over checkpointed state                                          | `:pipeline/ua.bookloom.pipeline.revision`, `:persistence/ua.bookloom.persistence.checkpoint` | FR-RESUME-01, DD-27                                                                          |
| Wire backward revision to the Max dial + export final-consistency toggle              | `:pipeline/ua.bookloom.pipeline.dial`, `:pipeline/ua.bookloom.pipeline.revision`             | FR-ALGO-11, FR-EXPORT-06, `01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`      |

## Phase exit checklist

- [ ] The deferred-resolution register captures items needing whole-book facts during translation.
- [ ] Backward revision re-renders affected earlier segments with full-book facts and moves them to REVISED.
- [ ] The global term-consistency sweep improves whole-book term stability (measured on a multi-chapter fixture).
- [ ] The pass runs crash-safely over checkpointed state and resumes if interrupted.
- [ ] It is gated by the Max quality dial and the export final-consistency toggle; off by default on Fast/Balanced.
- [ ] All model calls go through the `InferenceGate`; offline invariant intact (WireMock only).
- [ ] FX-free (ArchUnit green); `./gradlew test` and `./gradlew traceCheck` green; module inventory updated.
