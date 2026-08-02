# ADR-0017 — Deliver infrastructure first, and build the UI component library in parallel with document handling

**Status:** accepted **Date:** 2026-08-02 **Deciders:** architect

## Context and problem statement

`00_Foundation/06_IMPLEMENTATION_STAGES.md#staged-delivery` (frozen) maps the fourteen phases onto eight delivery
stages: Scaffold → Document model → More formats → Inline masking → Persistence → Providers → Pipeline core →
Consistency → Tiered quality → UI shell → Translate & review UI → Settings & providers UI → Backward revision →
Packaging & release. Under ADR-0016 the unit of work is no longer a phase-scoped story but an OpenSpec change, so the
work has to be regrouped anyway. That regrouping is the moment to fix two ordering problems the frozen staging leaves.

**Problem 1 — the app does not launch until the Application stage.** Under the frozen order, `PHASE_00_SCAFFOLD`
produces a build and a composition root, but a *runnable window* only arrives with `PHASE_09_UI_SHELL`, after document
handling, persistence, providers, and the whole translation engine. That means the JavaFX/JPMS/jpackage toolchain — the
part of this stack most likely to fight back, because a modular JavaFX app with `jpackage` has real
`module-info`/runtime-image failure modes — goes unproven while several thousand lines of business logic accumulate
behind it. If `:app:run` cannot open a window, every module built in the meantime was built against an untested
assumption.

**Problem 2 — UI widgets are blocked behind the engine for no technical reason.** The frozen order puts all UI work in
PHASE_09–PHASE_11, after the pipeline. But the mockup (`docs/specification/mockups/ui-mockup.html`) contains a dedicated
**"Component library"** screen: the segmented picker, toggle switch, chips, status pills, tables, trees, dialogs and
toasts, rendered standalone. Every one of those is verifiable — structurally and against the palette tokens — with
**zero business logic**. Keeping them behind the engine serializes two genuinely independent work streams and defers
discovery of the visual target to the end, which is exactly when re-doing it is most expensive.

Neither problem is a disagreement with the specification's *content*. The phase dependencies, the seam order (F1–F9),
and what each phase delivers are all correct. Only the **grouping** is wrong for a from-zero build.

## Decision drivers

- **De-risk the toolchain before the logic.** A modular JavaFX app that is packaged and launched is the single highest-
  information early test of the stack. Prove it while it costs one change to fix.
- **Verifiable without business logic.** Both a themed empty window and a mockup-conformant widget library can be
  tested (TestFX/Monocle, looked-up-token assertions) with nothing behind them. Work that is verifiable early *should*
  happen early.
- **Parallelism where dependencies allow it.** `:document` and `:ui` share only `:api` and `:util`. Nothing technical
  forces one after the other.
- **Discover the visual target early.** Mockup conformance found at the end is a rewrite; found at widget level it is
  a CSS edit.
- **Do not disturb what is correct.** Seam order and phase dependencies stay exactly as specified.
- **The frozen spec stays frozen.** The deviation is recorded here (`04_ADR_FORMAT.md#when-to-write-one`), not patched
  into `docs/specification/**`.

## Considered options

- **Option A — Keep the frozen eight-stage order, regrouped 1:1 into changes.**
- **Option B — Infrastructure first (build + launchable window as Stage A), then document round-trip **in parallel
  with** the UI component library, then the engine, then screen composition, then release.**
- **Option C — Vertical slice first: one narrow end-to-end path (import a TXT → translate one chunk → export) before
  broadening.**

## Decision outcome

Chosen: **Option B.** Five stages, 28 changes:

| Stage | Name | Contents | Depends on |
|---|---|---|---|
| **A** | Infrastructure | 1 `bootstrap-gradle-and-quality-toolchain` · 2 `bootstrap-app-launch-and-empty-window` | — |
| **B** | Document round-trip core | 3–5: skeleton + EPUB round-trip, FB2/MD/TXT, inline masking + placeholder gate | A |
| **B′** | UI component library | 6 `add-theming-token-system` · 7 `add-ui-component-library` | A (**parallel with B**) |
| **C** | Engine internals | 8–16: storage, resume, provider, inference, chunking, draft loop, QA gate, consistency, judge/self-heal | B |
| **D** | Composition | 17–25: shell + navigation, i18n, and every screen wired to the engine | B′ and C |
| **E** | Whole-book quality & release | 26–28: backward revision, packaging, Ukrainian parity | D |

The ordered backlog with capability mappings is `docs/implementation_plan/CHANGE_BACKLOG.md`.

**Two deviations from `#staged-delivery`, and only two.**

1. **A runnable empty window is a Stage A exit gate, not an Application-stage deliverable.** Change 2 must end with
   `./gradlew :app:run` opening a blank, themed JavaFX window and `:app:collectDist` staging a distributable. It
   absorbs the app-paths resolver, programmatic Logback bootstrap, the version resource, the `Result`/`AppError`
   envelope (seam F2), the `Launcher` + Guice composition root + single-instance lock, and the packaging scripts +
   launch smoke — i.e. all of `PHASE_00`'s runtime half plus the minimum of `PHASE_09` needed to open a window. What
   moves earlier is only the empty `Application` + Scene-level stylesheet; every screen still belongs to Stage D.

2. **The UI component library (Stage B′) runs in parallel with document handling (Stage B)**, rather than after the
   translation engine. It builds reusable, mockup-conformant controls against the mockup's own **"Component library"**
   screen — widget-level, composed into no application screen. Screen composition remains in Stage D, after the engine,
   exactly as specified.

**Unchanged:** the forward-compatibility seams F1–F9 keep their order and their meaning; F2 and F9 are still
established first (Stage A), F1 in the first document change, F6/F7 with storage, F3/F4 with the provider work, F5 with
the pipeline, F8 with the shell. Every phase *dependency* in `07_ROADMAP.md#phase-table` still holds — nothing is
reordered across a real dependency edge. The `#stage-exit-invariants` (offline invariant, whole-project clean gate,
ArchUnit green, `./gradlew test` green, round-trip golden green, mockup conformance) apply to these five stages exactly
as they applied to the fourteen phases, minus the `traceCheck` clause struck by ADR-0016.

Option C (vertical slice) is rejected because a genuine end-to-end slice requires the document model, persistence, a
provider, and the pipeline loop before anything runs — that is most of Stage B plus most of Stage C, so the "thin"
slice is neither thin nor early. It would also force throwaway scaffolding at each seam it stubs, and this app's value
is precisely in the parts a thin slice omits: the QA gate, the consistency stack, and the self-heal tiers.

### Consequences

Positive:

- The riskiest integration — Java 25 + JPMS + JavaFX 25 + Guice + jpackage — is proven at change 2, when a fix costs
  one change rather than a refactor across eight modules.
- Two independent work streams (B and B′) can proceed concurrently after Stage A.
- The visual target is discovered at widget level, where correcting it is a stylesheet change.
- Every module that follows is built inside a project already known to build, launch, package, and pass the full
  quality gate.
- Stage D screens assemble from already-conformant widgets, so screen work is layout and wiring rather than layout,
  wiring, *and* control design.

Negative:

- **Stage B′ builds controls before their consuming screens exist**, so some API guesswork is unavoidable and a
  control may need reshaping in Stage D. Mitigated by the mockup being binding and detailed enough to design against,
  and by the reshaping being local to `:ui`.
- The empty-window change (2) is large — it absorbs six former stories. Accepted: they are mutually entangled
  (paths → logging → lock → injector → launch), and splitting them yields changes that cannot independently prove
  anything.
- Two frozen clauses now read correctly only alongside this ADR.

Neutral:

- `docs/implementation_plan/phases/**` is retained as reference material feeding future proposals; it is no longer the
  execution order. `07_ROADMAP.md` is rewritten to these five stages and keeps the `#stage-exit-invariants` and
  `#forward-compatibility-seams` tables intact.
- 28 changes against 14 phases is roughly one change per coherent deliverable slice — finer than a phase, coarser than
  a story.

## Pros and cons of the options

### Option A — frozen order, regrouped 1:1

- Good: zero deviation to record; the phase files map straight onto changes.
- Bad: the app first launches after the engine is complete, leaving the JavaFX/JPMS/jpackage toolchain unproven behind
  thousands of lines; serializes two independent streams; defers mockup conformance to the most expensive moment.

### Option B — infrastructure first, UI library in parallel (chosen)

- Good: earliest possible toolchain proof; genuine parallelism; visual target discovered cheaply; every later change
  lands in a green, launchable project.
- Bad: widgets designed slightly ahead of their consumers; one large bootstrap change.

### Option C — vertical slice first

- Good: an end-to-end demo early; forces the seams to meet.
- Bad: the "thin" slice needs document + persistence + provider + pipeline, so it is neither thin nor early; requires
  throwaway stubs at every omitted seam; omits exactly the quality machinery that is this product's value.

## Links

- Design decisions: DD-39 (paths-first startup, which change 2 realizes as a Stage A gate)
- Spec clauses: `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#staged-delivery` (deviated from — see
  the two deviations above), `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#forward-compatibility-seams`
  (unchanged), `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#stage-exit-invariants` (unchanged except
  the `traceCheck` clause struck by ADR-0016), `docs/specification/mockups/ui-mockup.html` (the "Component library"
  screen Stage B′ builds against)
- Related: ADR-0016 (OpenSpec changes as the unit of work), ADR-0001 (Java + JavaFX), ADR-0015 (app paths)
- Changes: `docs/implementation_plan/CHANGE_BACKLOG.md` is the ordered backlog this ADR defines.
