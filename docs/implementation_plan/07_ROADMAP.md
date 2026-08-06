**Status:** Final **Owner:** architect **Audience:** anyone building BookLoom **Last Updated:** 2026-08-05
**Cross-references:** `docs/adr/ADR-0017-infrastructure-first-delivery-order.md`,
`docs/adr/ADR-0023-backend-complete-milestone-and-backlog-interstitials.md`,
`docs/adr/ADR-0016-openspec-delivery-tracking.md`, `docs/implementation_plan/CHANGE_BACKLOG.md`,
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`, `docs/implementation_plan/README.md`,
`docs/implementation_plan/phases/`

# Roadmap

Delivery is organized into **five stages** holding **28 numbered OpenSpec changes plus six unnumbered interstitials**
(ADR-0017; interstitials added by ADR-0021 and ADR-0023). This document is the binding stage order and the map of
stages to forward-compatibility seams. The changes themselves — names and order — are in
`docs/implementation_plan/CHANGE_BACKLOG.md`; each gets its real proposal, specs, and tasks via `/opsx:propose` when its
turn comes.

**The backend is complete at the end of Stage C, not somewhere inside Stage D.** ADR-0023 found five FX-free backend
concerns parked inside changes named for screens, and repaired the sequence: every service a screen calls exists and
runs before Stage D opens. See `#backend-complete-milestone`.

**This supersedes the fourteen-phase order.** `docs/implementation_plan/phases/PHASE_NN_*.md` is retained as **reference
material** — the per-area task inventories in those files feed future proposals — but it is no longer the execution
sequence. See ADR-0017 for the two deviations from
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#staged-delivery` and why they were taken.

## stages {#stages}

| Stage  | Name                          | Changes | Depends on   | Outcome                                                                                                        |
|--------|-------------------------------|---------|--------------|----------------------------------------------------------------------------------------------------------------|
| **A**  | Infrastructure                | 1–2     | —            | The project builds, passes the full mechanical gate, launches a themed empty window, and packages.             |
| **B**  | Document round-trip core      | 3–5 + 1 | A            | All four formats parse to skeleton+segment (F1), round-trip canonical-equal (DD-43), mask inline spans, emit metadata units, and detect the real source language. |
| **B′** | Contract floor + UI components | 6–7 + 1 | A (∥ with B) | The whole `:api` port surface plus in-memory stubs, then mockup-conformant reusable controls and the light/dark token system, composed into no application screen yet. |
| **C**  | Engine internals              | 8–16 + 3 | B           | Storage (F6/F7), provider abstraction (F3), gate (F4), context package (F5), the prompt layer, QA, consistency, tiered self-heal, project/import/export orchestration — **and the whole book running against a stub provider**. |
| **D**  | Composition                   | 17–25   | B′ and C     | The JavaFX app: state mirror (F8), i18n, every screen/state/dialog per the mockup, wired to an engine that already works. |
| **E**  | Whole-book quality & release  | 26–28   | D            | Backward-revision sweep, per-OS packages + release CI, Ukrainian bundle parity.                                |

**Stage B and Stage B′ run in parallel.** They share only `:api` and `:util`, so nothing technical forces one after the
other. Both depend on Stage A being fully archived.

## stage-exit-gates {#stage-exit-gates}

Beyond the invariants below, each stage has a concrete, demonstrable exit:

| Stage  | Exit gate                                                                                                          |
|--------|----------------------------------------------------------------------------------------------------------------|
| **A**  | `./gradlew clean build check spotlessCheck` green on eight modules; `./gradlew :app:run` opens a blank themed JavaFX window; `:app:collectDist` stages a distributable. |
| **B**  | A no-op translate of an EPUB, FB2, Markdown, and TXT file each produces a **canonical-equal** artifact (TXT exact bytes), the inline placeholder multiset gate rejects a tampered chunk, metadata units and nav/NCX labels are emitted as segments, and a book whose declared language contradicts its content reports the mismatch. |
| **B′** | Every `:api` port exists with a working in-memory stub, and the mockup's **"Component library"** screen renders from real JavaFX controls in both light and dark against those stubs, asserted structurally and by looked-up palette token. |
| **C**  | **The backend-complete milestone** (`#backend-complete-milestone`): a small book imports from disk, runs the complete pipeline against a canned-response stub `Provider`, resumes correctly after an interrupt, and exports canonical-equal. Separately, `:llm` is proved against WireMock for both dialects. |
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

**The `:api` port surface is a seam by that same definition, and ADR-0023 treats it as one.** F1–F9 name nine specific
boundaries, but the rule above — establish it before its consumers exist — applies to every contract, and until
ADR-0023 the ports arrived one at a time with the change that implemented each. `TranslationEngine` in particular
appears in `02_Architecture/02_MODULES_AND_LAYERING.md` and in `01_MODULE_INVENTORY.md` but in no seam row and no
backlog entry, so nothing owned landing it. The `add-api-contract-floor-and-stubs` interstitial at the head of Stage B′
lands every port change 3 does not own, plus in-memory stubs, so **F3 and F8 have a declared shape from Stage B′
onward** even though their real implementations arrive at changes 10 and 17. No seam letter is added; the existing
rows are unchanged.

## backend-complete-milestone {#backend-complete-milestone}

Stage C ends with `add-stub-provider-whole-book-e2e`, whose green gate is the single claim the whole backend is judged
by (ADR-0023, `CHANGE_BACKLOG.md#backend-complete-milestone`):

> A small book is imported from disk, parsed to skeleton + segments, chunked, context-assembled, drafted, QA-gated,
> judged, self-healed, consistency-passed, checkpointed, **interrupted, resumed**, and exported to a canonical-equal
> artifact — with the `Provider` port bound to a **stub returning canned responses**. Nothing in the pipeline is absent
> or faked except the model itself.

Two things this milestone is *not*:

- **It is not a substitute for building `:llm`.** Changes 10–11 land the real Ollama-native and OpenAI-compatible
  clients and prove them at the **WireMock HTTP seam**, because tolerant parsing, `<think>` stripping, the repair
  retry, `Retry-After` and the structured-output downgrade are exactly what a stub cannot exercise (ADR-0013). The
  pipeline is written against a port whose real implementation already exists.
- **It is not permission to mock the `Provider` when testing `:llm`.** `.claude/rules/testing.md` rejects that
  outright. The stub `Provider` is a **pipeline** e2e fixture, which the same rule explicitly permits; the two clauses
  do not conflict and neither changes.

After this gate is green, a Stage D change adds a surface and a binding — never a service.

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
- **The prompt layer is one owned change, and evals ride their features on top of it.** ADR-0023 adds
  `add-prompt-catalog-and-output-contract` between changes 12 and 13: it owns
  `:pipeline/ua.bookloom.pipeline.prompt` — the nine templates of `01_Product/12_PROMPT_CATALOG.md`, the
  variable-expansion mechanism, the JSON `#output-contract`, and the **`promptEval` harness** (real local model +
  embedding scorer) that DD-40 requires and that change 1 created only the excluded Gradle task for. Individual evals
  are then added in the change that introduces each prompt (13 draft; 16 judge/repair/reflect), not batched at the end.
  It comes after 12 because a prompt builder consumes the context package (F5). Visual/rendered UI validation (DD-41)
  is required across Stage D and change 27.
- **Language detection is a Stage B concern, not a Stage D one.** Lingua lands in
  `:document/ua.bookloom.document.detect` with `add-metadata-units-and-language-detection` at the close of Stage B,
  because the deterministic QA gate's target-language check needs it at change 14
  (`02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`) — well before the import screen that also needs it
  (FR-IMPORT-03). Leaving it in change 19 would have meant building it twice.
- **Every stage exits green:** `#stage-exit-invariants` above plus the per-change Definition of Done.
