**Status:** Final **Owner:** architect **Audience:** architect, engineering, QA, program management **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`,
`docs/implementation_plan/README.md`, `docs/implementation_plan/phases/`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`

# Roadmap

The roadmap orders the work into fourteen phases, `PHASE_00`..`PHASE_13`. Each phase has a dedicated file under
`docs/implementation_plan/phases/` with its goal, scope, dependencies, forward-compatibility seams, a candidate-task
backlog, and an exit checklist. This document is the binding phase order and the map of phases to delivery stages and
forward-compatibility seams. Phases are executed in dependency order; within a phase, work is broken into stories by
`/plan-phase-stories-creation` (see `docs/implementation_plan/README.md#end-to-end-flow`).

## phase-table {#phase-table}

| Phase                            | Goal                                                                                                                                                                                                                                                                  | Stage                   | Depends on                                       |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------|--------------------------------------------------|
| `PHASE_00_SCAFFOLD`              | Gradle multi-module + JPMS skeleton, Guice composition root, logging, Lefthook, Spotless/lint/ArchUnit, CI, jpackage smoke, trace tooling.                                                                                                                            | Scaffold                | —                                                |
| `PHASE_01_DOCUMENT_MODEL`        | Skeleton+segment document model; EPUB read/write round-trip golden test.                                                                                                                                                                                              | Document model          | PHASE_00                                         |
| `PHASE_02_FB2_MD_TXT`            | FB2, Markdown, and TXT parse/reassemble with round-trip golden tests.                                                                                                                                                                                                 | More formats            | PHASE_01                                         |
| `PHASE_03_INLINE_MASKING`        | Inline tag/locked-term masking, placeholder-multiset validation, reassembly.                                                                                                                                                                                          | Inline masking          | PHASE_01 (PHASE_02 for full format coverage)     |
| `PHASE_04_PERSISTENCE`           | SQLite schema, Flyway, JDBI DAOs, typed settings KV, resume checkpoints.                                                                                                                                                                                              | Persistence             | PHASE_00                                         |
| `PHASE_05_PROVIDERS`             | Provider abstraction, profiles, factory, discovery, three-stage verification, credentials, `InferenceGate`, retry, error mapping.                                                                                                                                     | Providers               | PHASE_00, PHASE_04                               |
| `PHASE_06_PIPELINE_CORE`         | Chunking, context assembly, single-model draft loop, deterministic QA gate, persist/resume.                                                                                                                                                                           | Pipeline core           | PHASE_03, PHASE_04, PHASE_05                     |
| `PHASE_07_CONSISTENCY`           | Name/term dictionary, context-aware TM, rolling summary, glossary pre-scan.                                                                                                                                                                                           | Consistency             | PHASE_06                                         |
| `PHASE_08_TIERED_QUALITY`        | LLM-as-judge, directed-fix + reflect→improve self-heal, quality dial.                                                                                                                                                                                                 | Tiered quality          | PHASE_06 (PHASE_07 for full context)             |
| `PHASE_09_UI_SHELL`              | JavaFX app, theming tokens light/dark, **i18n infrastructure (ResourceBundles, `ui.language` KV, OS-locale first-start, English bundle) established here so every screen uses bundle keys from the start**, navigation, Projects/Import/Book Brief/Structure screens. | UI shell                | PHASE_00, PHASE_01, PHASE_04                     |
| `PHASE_10_UI_TRANSLATE_REVIEW`   | Translating dashboard + states, Review queue side-by-side, Glossary, Export screens.                                                                                                                                                                                  | Translate & review UI   | PHASE_01, PHASE_02, PHASE_06, PHASE_07, PHASE_09 |
| `PHASE_11_SETTINGS_PROVIDERS_UI` | Settings tabs, provider dialog + test trio, notifications/dialogs.                                                                                                                                                                                                    | Settings & providers UI | PHASE_05, PHASE_09                               |
| `PHASE_12_BACKWARD_REVISION`     | Deferred-resolution register + whole-book consistency sweep.                                                                                                                                                                                                          | Backward revision       | PHASE_07, PHASE_08                               |
| `PHASE_13_PACKAGING_RELEASE`     | Script-driven jpackage packages (macOS `.app`/`.dmg`, Windows portable zip — no installer, Linux tar.gz/`.deb`), three-workflow CI + tagged GitHub Releases, docs, **completion of the Ukrainian bundle to parity (infra already in PHASE_09)**, accessibility pass.  | Packaging & release     | All prior phases                                 |

## stage-rollup {#stage-rollup}

Phases group into the delivery stages defined in
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#staged-delivery`. Each stage is shippable-in-principle for
its own scope and leaves the forward-compatibility seams intact for the next.

| Stage              | Phases                       | Outcome                                                                                                                                                   |
|--------------------|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Foundations        | PHASE_00                     | The build, module skeleton, and cross-cutting invariants (F2, F9) exist and are enforced.                                                                 |
| Document handling  | PHASE_01, PHASE_02, PHASE_03 | All four formats parse to skeleton+segment (F1), round-trip **structure-and-text-preservingly (canonical-equal, DD-43)**, and mask inline spans.          |
| Local state        | PHASE_04                     | Durable SQLite state, typed settings KV (F7), and crash-safe checkpoints (F6).                                                                            |
| Inference          | PHASE_05                     | Provider abstraction (F3) and single-flight gate (F4) with verification, retry, and error mapping.                                                        |
| Translation engine | PHASE_06, PHASE_07, PHASE_08 | The automatic pipeline: context package (F5), deterministic QA, consistency stack, tiered self-heal, quality dial.                                        |
| Application        | PHASE_09, PHASE_10, PHASE_11 | The JavaFX app: state mirror (F8), every screen/state/dialog per the mockup.                                                                              |
| Whole-book quality | PHASE_12                     | Deferred-resolution and whole-book consistency sweep.                                                                                                     |
| Release            | PHASE_13                     | Per-OS packages (app-images, `.dmg`, `.deb`; Windows portable zip), three-workflow CI + GitHub Releases, UK-bundle parity, advisory accessibility review. |

## forward-compatibility-seams {#forward-compatibility-seams}

The seams F1–F9 are the architectural boundaries that must exist early so later phases bolt on cleanly. They are defined
authoritatively in `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#forward-compatibility-seams`; each
phase file's `## Forward-compatibility` section names the seams it establishes or consumes.

| Seam | What it is                                                            | Introduced | Consumed by                  |
|------|-----------------------------------------------------------------------|------------|------------------------------|
| F1   | Skeleton / segment seam — text nodes the only mutable slots           | PHASE_01   | PHASE_03, PHASE_06, PHASE_10 |
| F2   | `Result` / `AppError` envelope                                        | PHASE_00   | every phase                  |
| F3   | Provider abstraction (`Provider`/`ProviderProfile`/`ProviderFactory`) | PHASE_05   | PHASE_06+, PHASE_11          |
| F4   | Single-flight `InferenceGate`                                         | PHASE_05   | PHASE_06, PHASE_08           |
| F5   | Pipeline context-package assembler                                    | PHASE_06   | PHASE_07, PHASE_08, PHASE_12 |
| F6   | Persistence / resume checkpoint seam                                  | PHASE_04   | PHASE_06, PHASE_12           |
| F7   | Typed settings KV                                                     | PHASE_04   | PHASE_09–PHASE_11            |
| F8   | UI observable state-mirror seam                                       | PHASE_09   | PHASE_09–PHASE_11            |
| F9   | Offline invariant (structural)                                        | PHASE_00   | every phase                  |

## execution-notes {#execution-notes}

- **Respect dependencies, not just numeric order.** PHASE_04 depends only on PHASE_00, so persistence work can proceed
  in parallel with the document phases. PHASE_09 (UI shell) needs PHASE_01 and PHASE_04 but not the pipeline.
- **One story per session** (see `docs/implementation_plan/README.md`). A phase completes when all its stories are
  `done` and its exit checklist passes.
- **Seams before consumers.** A phase must establish its declared seams even before their consumers exist, so later
  phases do not reshape earlier code.
- **Foundations precede their users (no forced mocks).** Two ordering rules make this concrete: (1) the **app-paths
  resolver** (per-OS data/log dirs, dev/prod) is a PHASE_00 task that runs before logging and SQLite, so PHASE_04
  persistence and all logging have their directories from day one (`02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md`,
  DD-39); (2) the **i18n ResourceBundle infrastructure** (plus `ui.language` KV and OS-locale first-start, English
  bundle) is established in **PHASE_09**, not deferred to PHASE_13, so every UI screen in PHASE_09–PHASE_11 is built
  against bundle keys and never hardcodes strings that would be retrofitted later. PHASE_13 only completes the Ukrainian
  bundle to parity and runs the accessibility pass.
- **Prompt evals and visual validation ride their features.** Local prompt evals (DD-40) are added in the phase that
  introduces each prompt (PHASE_06 draft; PHASE_08 judge/repair/reflect), not batched at the end; visual/rendered UI
  validation (DD-41) is required in the late UI phases (PHASE_09–PHASE_11) and PHASE_13.
- **Every phase exits green:** the stage-exit invariants in
  `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#stage-exit-invariants` and the phase-level exit criteria
  in `docs/implementation_plan/06_DEFINITION_OF_DONE.md#phase-level-exit-criteria`.
