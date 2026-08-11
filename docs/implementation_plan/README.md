**Status:** Final **Owner:** architect **Audience:** anyone building BookLoom — new contributors first **Last Updated:**
2026-08-02 **Cross-references:** `docs/adr/ADR-0016-openspec-delivery-tracking.md`,
`docs/adr/ADR-0017-infrastructure-first-delivery-order.md`, `docs/specification/00_Foundation/05_SPEC_INDEX.md`,
`docs/implementation_plan/01_MODULE_INVENTORY.md`, `docs/implementation_plan/CHANGE_BACKLOG.md`,
`docs/implementation_plan/06_DEFINITION_OF_DONE.md`, `docs/implementation_plan/07_ROADMAP.md`

# Implementation Plan — Operating Manual

This folder is the **operating manual for building BookLoom**. It explains how to turn the frozen specification into
working, tested code. Read this document first; it tells you where every other artifact lives, in what order to read
them, and the exact loop each unit of work follows.

## working-model {#working-model}

The project separates **what to build** (the specification) from **how we build it** (this plan and the OpenSpec changes
it produces).

- **`docs/specification/` is FROZEN and read-only during implementation.** It is the single source of truth for
  requirements, design decisions, and the UI mockup. Implementation work never edits it. If implementation reveals that
  the spec is wrong or incomplete, stop and record a **new ADR** under `docs/adr/` carrying the deviation — never patch
  the spec silently.
- **Work happens as OpenSpec changes under `openspec/changes/<name>/`** (ADR-0016). A change is the unit of
  implementable, testable work. It holds `proposal.md` (why/what/capabilities/impact), optional `design.md`,
  `specs/<capability>/spec.md` (EARS requirements + concrete scenarios), and `tasks.md` (the implementation checklist).
- **`openspec/specs/` starts empty and grows.** Archiving a change folds its requirements in, so `openspec/specs/`
  always means *what is actually built*, while `docs/specification/` means *what is ultimately intended*. The `FR-*`
  citation is the bridge between them.
- **Architecturally significant decisions not settled by the spec are ADRs** under `docs/adr/`. Format in
  `docs/implementation_plan/04_ADR_FORMAT.md`. A change's `design.md` **cites** the ADR rather than re-arguing it.
- **Transient scratch lives in `docs/implementation_plan/working_notes/`.** Non-binding, disposable, never authoritative.

**Retired by ADR-0016** — do not reintroduce: `docs/stories/`, the story format, `docs/traceability.yaml`,
`./gradlew trace`, `./gradlew traceCheck`, and `// Proves: STORY-NNN-AC-N` markers. Tests now carry `// Covers: FR-*`
plus a one-line EARS restatement, and coverage is `scripts/fr-coverage.sh` (advisory, never a gate).

## the-authoring-standard {#authoring-standard}

Every artifact must be understandable **without opening another file**. IDs are citations, never payloads. A bare
`FR-DOC-04` or `DD-14` standing alone as the content of a requirement, a scenario, or a task is a defect — restate what
it says, then cite it. The six rules (ADR-0016 R1–R6), enforced through `openspec/config.yaml`:

| Rule | What it requires |
|---|---|
| **R1** | Requirements are written in **EARS**, not copied from the FR table. A dense FR decomposes into several atomic requirements. |
| **R2** | Every requirement carries a **`Source:` block** — the `FR-*` ids with anchors, plus an "In plain words:" gloss. |
| **R3** | Scenarios use **concrete values**. Not "invalid input" → `⟦g1⟧⟦g2⟧` vs `⟦g1⟧`. Not "an error" → `ErrorCode.validation`. |
| **R4** | Task items state **what and why in a sentence**, then the module and spec pointer. |
| **R5** | Test markers carry a **one-line EARS restatement** alongside `// Covers: FR-*`. |
| **R6** | Coverage is a **grep** (`scripts/fr-coverage.sh`), not a build task. |

## artifact-map {#artifact-map}

| Artifact            | Location                                                      | Authority                                          | Edited by                                |
|---------------------|-----------------------------------------------------------------|-----------------------------------------------------|------------------------------------------|
| Specification       | `docs/specification/`                                         | Binding, **frozen**                                 | Nobody during implementation             |
| UI mockup           | `docs/specification/mockups/ui-mockup.html`                   | Binding visual source of truth                      | Nobody during implementation             |
| ADRs                | `docs/adr/ADR-NNNN-<slug>.md`                                 | Binding decisions; carry deviations from the spec   | Whoever takes the decision               |
| OpenSpec config     | `openspec/config.yaml`                                        | Binding — steers every generated artifact           | Deliberate process change                |
| Changes             | `openspec/changes/<name>/`                                    | **Binding unit of work**                            | `/opsx:propose` → `/opsx:apply`          |
| Shipped specs       | `openspec/specs/<capability>/spec.md`                         | The ledger of built behaviour                       | `openspec archive` (generated)           |
| Change backlog      | `docs/implementation_plan/CHANGE_BACKLOG.md`                  | Binding order; names only, unplanned                | architect                                |
| Module inventory    | `docs/implementation_plan/01_MODULE_INVENTORY.md`             | Binding — the canonical module/package map          | the change that adds a module            |
| ADR format          | `docs/implementation_plan/04_ADR_FORMAT.md`                   | Binding                                             | architect                                |
| Scenario patterns   | `docs/implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md` | Binding — P1–P6                                     | architect                                |
| Definition of Done  | `docs/implementation_plan/06_DEFINITION_OF_DONE.md`           | Binding                                             | architect                                |
| Roadmap             | `docs/implementation_plan/07_ROADMAP.md`                      | Binding stage order                                 | architect                                |
| Phase files         | `docs/implementation_plan/phases/PHASE_NN_*.md`               | **Reference material** — task inventories, not order | architect                                |
| FR coverage script  | `scripts/fr-coverage.sh`                                      | Advisory                                            | anyone                                   |
| Working notes       | `docs/implementation_plan/working_notes/`                     | Non-binding scratch                                 | anyone, disposable                       |

## end-to-end-flow {#end-to-end-flow}

**One change at a time**, in the order `docs/implementation_plan/CHANGE_BACKLOG.md` gives.

1. **Pick the next entry** from `CHANGE_BACKLOG.md`, respecting its stage. Stage A must be fully archived before B or B′
   starts; C depends on B; D on B′ and C; E on D. The backlog is deliberately **names and order only** — no entry is
   pre-planned.
2. **Propose it:** run `/opsx:propose`. This creates `openspec/changes/<name>/` and generates `proposal.md`, the delta
   `specs/`, `design.md` (when warranted), and `tasks.md` in one step, steered by the R1–R6 rules in
   `openspec/config.yaml`. Read the generated artifacts critically — the rules shape them, they do not guarantee them.
   Use `/opsx:explore` first when the shape of the work is genuinely unclear, and `/opsx:update` to revise a change
   whose plan needs reworking.
3. **Validate:** `openspec validate <change> --strict`. A malformed checkbox or a three-hash scenario fails here.
4. **Implement it:** run `/opsx:apply`. Work through `tasks.md`, checking items off as they actually land. Tests carry
   `// Covers: FR-*` plus a one-line EARS restatement of the obligation they prove.
5. **Run the Definition of Done** (`06_DEFINITION_OF_DONE.md`): every requirement covered by a test, every task checked,
   `./gradlew clean build check spotlessCheck` **green project-wide** with no "pre-existing failure" exemption, the
   implied test types present, ArchUnit green, module inventory updated, mockup conformance for `:ui`, offline invariant
   intact.
6. **Archive it:** `/opsx:archive`. The change's requirements fold into `openspec/specs/<capability>/`, which is how the
   built-behaviour ledger grows.
7. **At each stage boundary**, run `bash scripts/fr-coverage.sh` and read the output. It is advisory — mid-build-out
   gaps are correct.

```
CHANGE_BACKLOG.md entry
  └─ /opsx:propose  → proposal.md + specs/ + design.md + tasks.md
       └─ openspec validate <change> --strict
            └─ /opsx:apply  → code + tests (// Covers: FR-*)
                 └─ Definition of Done → /opsx:archive → openspec/specs/ grows
```

## read-order {#read-order}

A new contributor or agent should read in this order:

1. **`AGENTS.md`** (repo root) — the binding operating manual and architecture invariants, and the single source of
   truth for them. `CLAUDE.md` imports it and adds nothing.
2. **`docs/DEVELOPMENT.md`** — how to actually build, run, debug and test the thing on your machine. Skip only if you
   will never run the code.
3. **`docs/specification/00_Foundation/01_VISION_AND_SCOPE.md`** — what the app is and is not.
4. **`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`** — the locked `DD-NN` decisions.
5. **`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`** — the module boundaries and ArchUnit rules.
6. **`docs/implementation_plan/README.md`** (this file) — the working model and the loop.
7. **`docs/adr/ADR-0016`** and **`ADR-0017`** — why delivery works this way and in this order. Both deviate from clauses
   in the frozen spec; reading the spec's build/CI sections without them will mislead you.
8. **`docs/implementation_plan/01_MODULE_INVENTORY.md`** — where code and tests live.
9. **`05_ACCEPTANCE_CRITERIA_PATTERNS.md`** and **`06_DEFINITION_OF_DONE.md`** — how to author and prove a change.
10. **`07_ROADMAP.md`** and **`CHANGE_BACKLOG.md`** — what to build next.
11. The **cited spec clauses** for the change in hand (resolve via `docs/specification/00_Foundation/05_SPEC_INDEX.md`).

## build-and-test-commands {#build-and-test-commands}

All commands use the Gradle wrapper. Java 25, JavaFX 26, JPMS. They all exist and run today. For the human-facing
detail behind each — prerequisites, IDE setup, debugging, troubleshooting — see `docs/DEVELOPMENT.md`.

| Command                                     | Purpose                                                                                              |
|---------------------------------------------|--------------------------------------------------------------------------------------------------|
| `./gradlew build`                           | Compile all modules, run tests, lint, ArchUnit.                                                    |
| `./gradlew :app:run`                        | Launch the app (1024x700 window). Classpath launch, not module path.                               |
| `./gradlew test`                            | Unit + integration tests (`liveLocal`/`promptEval`/`visual` tags excluded).                        |
| `./gradlew :ui:test`                        | UI tests headless via TestFX on JavaFX 26's built-in `glass.platform=Headless` (ADR-0019; not Monocle). |
| `./gradlew spotlessApply` / `spotlessCheck` | Apply / verify Palantir Java Format (120-col).                                                     |
| `./gradlew check`                           | Full gate: tests, Checkstyle, Error Prone + NullAway, SpotBugs + FindSecBugs, ArchUnit, coverage.  |
| `./gradlew liveLocal`                       | Local-only provider tests against a real Ollama / LM Studio. Env-gated; never in CI.               |
| `./gradlew :app:archTest`                   | The 8 ArchUnit boundary rules. Hosted in :app; wired into check.                                    |
| `./gradlew :app:collectDist`                | Stage the app jar + runtime classpath for packaging.                                               |
| `scripts/package-<os>`                      | (not Gradle) drive `jpackage` per OS — no cross-compile.                                           |
| `bash scripts/fr-coverage.sh`               | Advisory: which frozen `FR-*` ids no shipped requirement claims yet.                               |

A change is not done until `./gradlew clean build check spotlessCheck` is green **across the whole project** — not just
the touched scope, and with no "pre-existing failure" exemption.

## invariants-reminder {#invariants-reminder}

Every change preserves the always-true architecture invariants (full list in `06_DEFINITION_OF_DONE.md`): FX-free core;
`Result`/`AppError` envelope; records-first data carriers; token-only theming; the offline invariant (only user-triggered
provider communication with the configured provider); the skeleton is never regenerated (only text nodes change);
credentials stored as a reference, never the secret; single-flight inference gate; SQLite + typed KV persistence. These
are enforced by ArchUnit and the Definition of Done, not left to judgement.
