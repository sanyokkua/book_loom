**Status:** Final **Owner:** architect **Audience:** anyone building BookLoom **Last Updated:** 2026-08-02
**Cross-references:** `docs/adr/ADR-0017-infrastructure-first-delivery-order.md`,
`docs/adr/ADR-0016-openspec-delivery-tracking.md`, `docs/implementation_plan/CHANGE_BACKLOG.md`,
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`, `docs/implementation_plan/README.md`,
`docs/implementation_plan/phases/`

# Roadmap

Delivery is organized into **five stages** holding **28 OpenSpec changes** (ADR-0017). This document is the binding
stage order and the map of stages to forward-compatibility seams. The changes themselves — names and order — are in
`docs/implementation_plan/CHANGE_BACKLOG.md`; each gets its real proposal, specs, and tasks via `/opsx:propose` when its
turn comes.

**This supersedes the fourteen-phase order.** `docs/implementation_plan/phases/PHASE_NN_*.md` is retained as **reference
material** — the per-area task inventories in those files feed future proposals — but it is no longer the execution
sequence. See ADR-0017 for the two deviations from
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#staged-delivery` and why they were taken.

## stages {#stages}

| Stage  | Name                          | Changes | Depends on   | Outcome                                                                                                        |
|--------|-------------------------------|---------|--------------|----------------------------------------------------------------------------------------------------------------|
| **A**  | Infrastructure                | 1–2     | —            | The project builds, passes the full mechanical gate, launches a themed empty window, and packages.             |
| **B**  | Document round-trip core      | 3–5     | A            | All four formats parse to skeleton+segment (F1), round-trip canonical-equal (DD-43), and mask inline spans.    |
| **B′** | UI component library          | 6–7     | A (∥ with B) | Mockup-conformant reusable controls plus the light/dark token system, composed into no application screen yet. |
| **C**  | Engine internals              | 8–16    | B            | Storage (F6/F7), provider abstraction (F3), gate (F4), context package (F5), QA, consistency, tiered self-heal. |
| **D**  | Composition                   | 17–25   | B′ and C     | The JavaFX app: state mirror (F8), i18n, every screen/state/dialog per the mockup, wired to the engine.        |
| **E**  | Whole-book quality & release  | 26–28   | D            | Backward-revision sweep, per-OS packages + release CI, Ukrainian bundle parity.                                |

**Stage B and Stage B′ run in parallel.** They share only `:api` and `:util`, so nothing technical forces one after the
other. Both depend on Stage A being fully archived.

## stage-exit-gates {#stage-exit-gates}

Beyond the invariants below, each stage has a concrete, demonstrable exit:

| Stage  | Exit gate                                                                                                          |
|--------|----------------------------------------------------------------------------------------------------------------|
| **A**  | `./gradlew clean build check spotlessCheck` green on eight modules; `./gradlew :app:run` opens a blank themed JavaFX window; `:app:collectDist` stages a distributable. |
| **B**  | A no-op translate of an EPUB, FB2, Markdown, and TXT file each produces a **canonical-equal** artifact (TXT exact bytes), and the inline placeholder multiset gate rejects a tampered chunk. |
| **B′** | The mockup's **"Component library"** screen renders from real JavaFX controls in both light and dark, asserted structurally and by looked-up palette token. |
| **C**  | A small whole book runs end-to-end through the engine against a WireMock provider, producing accepted and flagged segments, and resumes correctly after an interrupt. |
| **D**  | Every screen and state enumerated in `01_Product/08_UI_SCREENS_AND_STATES.md` renders and is wired, asserted headlessly against the mockup. |
| **E**  | A tagged release produces per-OS artifacts that launch, and the Ukrainian bundle reaches key-set parity with English. |

## stage-exit-invariants {#stage-exit-invariants}

Every stage must exit with: the offline invariant (F9) intact; the **whole-project clean gate** green —
`./gradlew clean build check spotlessCheck` passes with zero findings across the whole project, not just touched code
(Spotless/Checkstyle/Error Prone+NullAway/SpotBugs; no "pre-existing" exemption,
`docs/implementation_plan/06_DEFINITION_OF_DONE.md#per-change-checklist`); ArchUnit boundary tests green (FX-free core
preserved); and `./gradlew test` green. Stages that touch document handling must keep the round-trip golden test green —
a **structure-and-text-preserving (canonical-equal)** comparison of canonicalized output to canonicalized source, not
raw bytes (TXT excepted: exact bytes) (DD-43); stages that touch the UI must match the mockup visual reference (P6).

These are the invariants from
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#stage-exit-invariants`, unchanged except that the
`./gradlew traceCheck` clause is **struck by ADR-0016** — that tooling is never built. In its place, run
`bash scripts/fr-coverage.sh` at each stage boundary and read the output; it is advisory, never a gate.

## forward-compatibility-seams {#forward-compatibility-seams}

The seams F1–F9 are the architectural boundaries that must exist early so later work bolts on cleanly. They are defined
authoritatively in `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#forward-compatibility-seams`. Their
**order and meaning are unchanged** by ADR-0017 — only the stage grouping moved.

| Seam | What it is                                                            | Introduced by                     | Consumed by             |
|------|-----------------------------------------------------------------------|-----------------------------------|-------------------------|
| F1   | Skeleton / segment seam — text nodes the only mutable slots           | change 3 (Stage B)                | changes 5, 12–13, 23    |
| F2   | `Result` / `AppError` envelope                                        | change 2 (Stage A)                | every change            |
| F3   | Provider abstraction (`Provider`/`ProviderProfile`/`ProviderFactory`) | change 10 (Stage C)               | changes 11–16, 24       |
| F4   | Single-flight `InferenceGate`                                         | change 11 (Stage C)               | changes 12–16, 26       |
| F5   | Pipeline context-package assembler                                    | change 12 (Stage C)               | changes 13–16, 26       |
| F6   | Persistence / resume checkpoint seam                                  | changes 8–9 (Stage C)             | changes 12–16, 26       |
| F7   | Typed settings KV                                                     | change 8 (Stage C)                | changes 17–25           |
| F8   | UI observable state-mirror seam                                       | change 17 (Stage D)               | changes 17–25           |
| F9   | Offline invariant (structural)                                        | change 1 (Stage A, ArchUnit rule) | every change            |

A stage must establish its declared seams **even before their consumers exist**, so later stages do not reshape earlier
code. Each seam must be exercised by a test before its stage closes.

## execution-notes {#execution-notes}

- **Respect the stage dependencies, not the change numbers.** Within Stage C, storage (8–9) and the provider stack
  (10–11) are independent of each other and can proceed in either order; both must precede the pipeline (12+). Stage B′
  is genuinely parallel to Stage B.
- **One change at a time per person**, following the `/opsx:propose → /opsx:apply → /opsx:archive` loop
  (`README.md#end-to-end-flow`).
- **Infrastructure first is the point of Stage A.** A modular JavaFX app that is built, launched, and packaged proves
  the riskiest integration in the stack (Java 25 + JPMS + JavaFX 25 + Guice + jpackage) while a fix still costs one
  change rather than a refactor across eight modules. See ADR-0017.
- **Foundations precede their users (no forced mocks).** The **app-paths resolver** (per-OS data/log dirs, dev/prod)
  runs before logging and SQLite, so persistence and logging have their directories from day one
  (`02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md`, DD-39, ADR-0015) — it lands in change 2. The **i18n
  ResourceBundle infrastructure** (plus `ui.language` KV and OS-locale first-start, English bundle) lands early in
  Stage D as change 18, before the screens, so every screen is built against bundle keys and never hardcodes a string
  that would be retrofitted later. Change 28 only completes the Ukrainian bundle to parity.
- **The UI component library is built against the mockup, not against screens.** Stage B′ builds widget-level controls
  from the mockup's own "Component library" screen. Screen composition is Stage D — after the engine — exactly as the
  frozen staging specified.
- **Prompt evals and visual validation ride their features.** Local prompt evals (DD-40) are added in the change that
  introduces each prompt (13 draft; 16 judge/repair/reflect), not batched at the end; visual/rendered UI validation
  (DD-41) is required across Stage D and change 27.
- **Every stage exits green:** `#stage-exit-invariants` above plus the per-change Definition of Done.
