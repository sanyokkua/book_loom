**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer, program management, new
contributors **Last Updated:** 2026-07-18 **Cross-references:** `docs/specification/00_Foundation/05_SPEC_INDEX.md`,
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`, `docs/implementation_plan/01_MODULE_INVENTORY.md`,
`docs/implementation_plan/02_STORY_FORMAT.md`, `docs/implementation_plan/03_TRACEABILITY.md`,
`docs/implementation_plan/06_DEFINITION_OF_DONE.md`, `docs/implementation_plan/07_ROADMAP.md`

# Implementation Plan — Operating Manual

This folder is the **operating manual for building BookLoom**. It explains how to turn the frozen specification into
working, tested, traceable code. Read this document first; it tells you where every other artifact lives, in what order
to read them, and the exact loop each unit of work follows.

## working-model {#working-model}

The project separates **what to build** (the specification) from **how we build it** (this plan and the stories it
produces).

- **`docs/specification/` is FROZEN and read-only during implementation.** It is the single source of truth for
  requirements, design decisions, and the UI mockup. Implementation work never edits it. If implementation reveals that
  the spec is wrong or incomplete, stop, raise it as a decision, and change the spec through a deliberate spec
  revision — not silently inside a story.
- **Work happens as stories under `docs/stories/`.** A story is the unit of implementable, testable work. Each story
  cites the spec clauses it satisfies, lists the modules it touches, and enumerates acceptance criteria that become
  proving tests. Story format is defined in `docs/implementation_plan/02_STORY_FORMAT.md`.
- **Architecturally significant decisions not settled by the spec are recorded as ADRs under `docs/adr/`.** Format in
  `docs/implementation_plan/04_ADR_FORMAT.md`.
- **Traceability is captured in `docs/traceability.yaml`**, a generated file linking spec clause → story → acceptance
  criterion → test → module. It is never hand-edited. See `docs/implementation_plan/03_TRACEABILITY.md`.
- **Transient scratch lives in `docs/implementation_plan/working_notes/`.** Investigation notes and planning scratch are
  non-binding and may be cleaned up freely. Nothing there is authoritative.

## artifact-map {#artifact-map}

| Artifact            | Location                                                      | Authority                                        | Edited by                                   |
|---------------------|---------------------------------------------------------------|--------------------------------------------------|---------------------------------------------|
| Specification       | `docs/specification/`                                         | Binding, frozen during implementation            | Spec revision only (not stories)            |
| UI mockup           | `docs/specification/mockups/ui-mockup.html`                   | Binding visual source of truth                   | Spec revision only                          |
| Module inventory    | `docs/implementation_plan/01_MODULE_INVENTORY.md`             | Binding — the list stories' `modules:` must cite | architect (in the story that adds a module) |
| Story format        | `docs/implementation_plan/02_STORY_FORMAT.md`                 | Binding schema                                   | architect                                   |
| Traceability rules  | `docs/implementation_plan/03_TRACEABILITY.md`                 | Binding                                          | architect                                   |
| ADR format          | `docs/implementation_plan/04_ADR_FORMAT.md`                   | Binding                                          | architect                                   |
| AC patterns         | `docs/implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md` | Binding                                          | architect                                   |
| Definition of Done  | `docs/implementation_plan/06_DEFINITION_OF_DONE.md`           | Binding                                          | architect                                   |
| Roadmap             | `docs/implementation_plan/07_ROADMAP.md`                      | Binding phase order                              | architect                                   |
| Phase files         | `docs/implementation_plan/phases/PHASE_NN_*.md`               | Backlog + phase exit criteria                    | architect                                   |
| Stories             | `docs/stories/story-NNN-<slug>.md`                            | Binding unit of work                             | architect writes; coder/tester implement    |
| ADRs                | `docs/adr/ADR-NNNN-<slug>.md`                                 | Binding decisions                                | architect                                   |
| Traceability record | `docs/traceability.yaml`                                      | Generated, never hand-edited                     | `./gradlew trace`                           |
| Working notes       | `docs/implementation_plan/working_notes/`                     | Non-binding scratch                              | anyone, disposable                          |

## end-to-end-flow {#end-to-end-flow}

Work flows from a phase, to a set of stories, to implemented and traced code. **One story per session.**

1. **Pick a phase** from `docs/implementation_plan/07_ROADMAP.md`, respecting its declared dependencies. Each phase has
   a backlog of candidate tasks in its `docs/implementation_plan/phases/PHASE_NN_*.md` file (a starting point, not the
   source of truth).
2. **Plan the phase's stories:** run `/plan-phase-stories-creation <PHASE>`. The command works in plan mode: it reads
   the frozen spec, the phase file, the module inventory, and the current codebase, then refines the phase backlog into
   concrete proposed stories and ends with `ExitPlanMode`. **On approval, the architect writes the story files** into
   `docs/stories/`, assigning `STORY-NNN` ids at that point (the phase backlog is intentionally id-less).
3. **Plan one story's implementation:** run `/plan-user-story-implementation <STORY>`. The command works in plan mode:
   it reads the story, its cited spec clauses, the module inventory, and the affected code, then produces an
   implementation + test plan and ends with `ExitPlanMode`. **On approval, coder and tester implement it** — production
   code plus proving tests.
4. **Land the proving tests.** Every acceptance criterion `STORY-NNN-AC-N` has at least one test whose first line
   declares `// Proves: STORY-NNN-AC-N`. Every cited `EC-` edge case has a covering test. See
   `docs/implementation_plan/03_TRACEABILITY.md` and `docs/implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md`.
5. **Regenerate and validate traceability:** `./gradlew trace` regenerates `docs/traceability.yaml`;
   `./gradlew traceCheck` validates it (zero orphans, fresh record). Both must be green.
6. **Run the Definition of Done** in `docs/implementation_plan/06_DEFINITION_OF_DONE.md`: every AC and edge case proven,
   lint/format/ArchUnit clean for touched code, `./gradlew test` green, `traceCheck` green, module inventory updated if
   modules changed, UI stories matched against the mockup, offline invariant intact.
7. **Mark the story `done`.** A `done` story is immutable (see `docs/implementation_plan/02_STORY_FORMAT.md#lifecycle`);
   further change is a new story that may `supersede` it.

```
phase (roadmap)
  └─ /plan-phase-stories-creation PHASE_NN   → plan mode → ExitPlanMode → (approve) → architect writes stories
       └─ story-NNN
            └─ /plan-user-story-implementation STORY-NNN → plan mode → ExitPlanMode → (approve) → coder + tester
                 └─ proving tests land → ./gradlew trace + traceCheck → Definition of Done → story = done
```

## read-order {#read-order}

A new contributor or agent should read in this order:

1. **`CLAUDE.md`** (repo root) — the binding operating manual and architecture invariants.
2. **`docs/specification/00_Foundation/01_VISION_AND_SCOPE.md`** — what the app is and is not.
3. **`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`** — the locked decisions (DD-01..DD-28).
4. **`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`** — the module boundaries and ArchUnit rules.
5. **`docs/implementation_plan/README.md`** (this file) — the working model and the loop.
6. **`docs/implementation_plan/01_MODULE_INVENTORY.md`** — where code and tests live.
7. **`docs/implementation_plan/02_STORY_FORMAT.md`**, **`03_TRACEABILITY.md`**, **
   `05_ACCEPTANCE_CRITERIA_PATTERNS.md`**, **`06_DEFINITION_OF_DONE.md`** — how to author and prove a story.
8. **`docs/implementation_plan/07_ROADMAP.md`** and the relevant **`phases/PHASE_NN_*.md`** — what to build next.
9. The **cited spec clauses** for the story in hand (resolve via `docs/specification/00_Foundation/05_SPEC_INDEX.md`).

## build-test-trace-commands {#build-test-trace-commands}

All commands use the Gradle wrapper. Java 25, JavaFX 25, JPMS.

| Command                                | Purpose                                                                                                                                |
|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `./gradlew build`                      | Full build: compile all modules, run unit tests, lint, ArchUnit.                                                                       |
| `./gradlew test`                       | Run JVM unit + integration tests (excludes heavy TestFX where configured).                                                             |
| `./gradlew :ui:test`                   | Run UI tests headless via TestFX + Monocle.                                                                                            |
| `./gradlew spotlessApply`              | Apply Palantir Java Format (120-col) to touched code.                                                                                  |
| `./gradlew spotlessCheck`              | Verify formatting; fails the build on drift.                                                                                           |
| `./gradlew check`                      | Aggregate verification: tests, Checkstyle, Error Prone + NullAway, SpotBugs + FindSecBugs, ArchUnit.                                   |
| `./gradlew trace`                      | Regenerate `docs/traceability.yaml` from stories, tests, and modules.                                                                  |
| `./gradlew traceCheck`                 | Validate traceability: zero orphans, all ACs/edge cases proven, record fresh.                                                          |
| `./gradlew jpackageImage` / `jpackage` | Build the runtime image / native installer on the current OS (see `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md`). |

A story is not done until `./gradlew check`, `./gradlew test`, and `./gradlew traceCheck` are all green for the touched
scope.

## invariants-reminder {#invariants-reminder}

Every story preserves the always-true architecture invariants (full list in
`docs/implementation_plan/06_DEFINITION_OF_DONE.md`): FX-free core; `Result`/`AppError` envelope; records-first data
carriers; token-only theming; the offline invariant (only user-triggered inference to the configured provider); the
skeleton is never regenerated (only text nodes change); credentials stored as a reference, never the secret;
single-flight inference gate; SQLite + typed KV persistence. These are enforced by ArchUnit and the DoD, not left to
judgement.
