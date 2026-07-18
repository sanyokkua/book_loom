**Status:** Final **Owner:** architect **Audience:** architect, engineering, QA, program management **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `docs/implementation_plan/07_ROADMAP.md`

# Implementation Stages and Forward-Compatibility Constraints

This document defines the staged delivery order and the forward-compatibility seams that must exist early so later
phases bolt on cleanly. The detailed phase files live under `docs/implementation_plan/phases/`; this document is the
specification-side view of staging and the binding list of seams.

## forward-compatibility-seams {#forward-compatibility-seams}

Each seam is an architectural boundary that must be created in the phase noted, even before its consumers exist, so
later work does not require reshaping earlier code.

| ID | Seam                                 | What must exist early                                                                                                                                                                                                                                   | Introduced | Consumed by                                                                     |
|----|--------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------|
| F1 | Skeleton / segment seam              | A document is represented as an immutable skeleton plus an ordered, individually-addressable segment list; text nodes are the only mutable slots. Reassembly reads target text back into the same nodes.                                                | PHASE_01   | Masking (PHASE_03), pipeline (PHASE_06), export (PHASE_10)                      |
| F2 | Result / AppError envelope           | The `Result{data,error}` type, typed `AppError`/`ErrorCode`, safe-details allowlist, and partial-result support in `:api`, used from the first service.                                                                                                 | PHASE_00   | Every module                                                                    |
| F3 | Provider abstraction seam            | `Provider` interface, per-kind `ProviderProfile` data, and `ProviderFactory` map, so new kinds are data not code.                                                                                                                                       | PHASE_05   | Pipeline (PHASE_06+), settings UI (PHASE_11)                                    |
| F4 | InferenceGate                        | A single-flight gate wrapping all inference so a local model serves one request at a time; present before any pipeline concurrency.                                                                                                                     | PHASE_05   | Pipeline loop (PHASE_06), self-heal (PHASE_08)                                  |
| F5 | Pipeline context-package seam        | A context-package assembler that composes system+brief, rolling summary, relevant glossary, preceding-target window, TM hits, and masked source, with load-bearing items placed at prompt edges. New context sources plug in without changing the loop. | PHASE_06   | Consistency (PHASE_07), tiered quality (PHASE_08), backward revision (PHASE_12) |
| F6 | Persistence / resume checkpoint seam | Atomic per-chunk checkpoint writes and a resume reader, so any later long-running stage is crash-safe by default.                                                                                                                                       | PHASE_04   | Pipeline (PHASE_06), backward revision (PHASE_12)                               |
| F7 | Settings KV                          | A generic typed `settings(key,value,type)` store with a read/write API, so any later feature can persist a preference without a schema change.                                                                                                          | PHASE_04   | Generation params, appearance, automation (PHASE_09–PHASE_11)                   |
| F8 | UI state-mirror seam                 | An observable state mirror decoupled from domain services, updated off the FX thread via `Platform.runLater`, so screens bind to state rather than to services.                                                                                         | PHASE_09   | All UI screens (PHASE_09–PHASE_11)                                              |
| F9 | Offline invariant                    | A structural guarantee that no code path issues network calls other than user-triggered provider communication (inference, model discovery, verification) with the configured provider, enforceable in tests (ArchUnit + WireMock isolation).           | PHASE_00   | All phases                                                                      |

## seam-to-decision-mapping {#seam-to-decision-mapping}

| Seam | Backing decisions |
|------|-------------------|
| F1   | DD-07             |
| F2   | DD-14             |
| F3   | DD-10             |
| F4   | DD-12             |
| F5   | DD-18, DD-19      |
| F6   | DD-20, DD-27      |
| F7   | DD-20             |
| F8   | DD-21             |
| F9   | DD-01             |

## staged-delivery {#staged-delivery}

Delivery follows the phase roadmap. Each stage is shippable-in-principle for its own scope and leaves the seams above
intact for the next.

| Stage                   | Phase                          | Goal                                                                                                                              | Seams established / used |
|-------------------------|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|--------------------------|
| Scaffold                | PHASE_00_SCAFFOLD              | Multi-module JPMS skeleton, Guice root, logging, hooks, lint, CI, jpackage smoke, trace tooling                                   | F2, F9                   |
| Document model          | PHASE_01_DOCUMENT_MODEL        | Skeleton+segment model, EPUB round-trip golden test                                                                               | F1                       |
| More formats            | PHASE_02_FB2_MD_TXT            | FB2, Markdown, TXT round-trip                                                                                                     | F1                       |
| Inline masking          | PHASE_03_INLINE_MASKING        | Tag/locked-term masking, validation, reassembly                                                                                   | F1                       |
| Persistence             | PHASE_04_PERSISTENCE           | SQLite schema, Flyway, JDBI, settings KV, checkpoints                                                                             | F6, F7                   |
| Providers               | PHASE_05_PROVIDERS             | Provider abstraction, profiles, factory, discovery, verification, credentials, gate, retry, error mapping                         | F3, F4                   |
| Pipeline core           | PHASE_06_PIPELINE_CORE         | Chunking, context assembly, single-model draft loop, deterministic QA gate, persist/resume                                        | F5, uses F1/F4/F6        |
| Consistency             | PHASE_07_CONSISTENCY           | Name dictionary, context-aware TM, rolling summary, glossary pre-scan                                                             | uses F5                  |
| Tiered quality          | PHASE_08_TIERED_QUALITY        | LLM-as-judge, directed-fix + reflect→improve self-heal, quality dial (mechanics only — τ is owned by the review-mode dial, DD-45) | uses F4/F5               |
| UI shell                | PHASE_09_UI_SHELL              | JavaFX app, theming tokens, navigation, Projects/Import/Book Brief/Structure                                                      | F8                       |
| Translate & review UI   | PHASE_10_UI_TRANSLATE_REVIEW   | Translating dashboard + states, Review queue, Glossary, Export                                                                    | uses F1/F8               |
| Settings & providers UI | PHASE_11_SETTINGS_PROVIDERS_UI | Settings tabs, provider dialog + test trio, notifications/dialogs                                                                 | uses F3/F7/F8            |
| Backward revision       | PHASE_12_BACKWARD_REVISION     | Deferred-resolution + whole-book consistency sweep                                                                                | uses F5/F6               |
| Packaging & release     | PHASE_13_PACKAGING_RELEASE     | jpackage per-OS, CI release, docs, i18n, advisory (non-gating) a11y pass                                                          | uses F9                  |

## stage-exit-invariants {#stage-exit-invariants}

Every stage must exit with: the offline invariant (F9) intact; the **whole-project clean gate** green —
`./gradlew clean build check spotlessCheck` passes with zero findings across the whole project, not just touched code
(Spotless/Checkstyle/Error Prone+NullAway/SpotBugs; no "pre-existing" exemption —
`docs/implementation_plan/06_DEFINITION_OF_DONE.md#per-story-checklist`); ArchUnit boundary tests green (FX-free core
preserved); `./gradlew test` green; and `./gradlew traceCheck` passing with zero orphans. Stages that touch document
handling must keep the round-trip golden test green — a **structure-and-text-preserving (canonical-equal)** comparison
of canonicalized output to canonicalized source, not raw bytes (TXT excepted: exact bytes) (DD-43); stages that touch
the UI must match the mockup visual reference (P6).
