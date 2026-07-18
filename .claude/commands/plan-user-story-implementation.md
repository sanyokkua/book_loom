---
description: Plan the full implementation and testing of one story (planning only; spec frozen; ends with ExitPlanMode).
argument-hint: <STORY-NNN or N>
allowed-tools: Read, Grep, Glob, Bash, WebSearch, Task, TodoWrite, ExitPlanMode
model: opus
---

# Plan User Story Implementation

Plan the complete implementation and testing of ONE story: **$ARGUMENTS**. This command is
READ-ONLY and produces a plan only.

## Read-only contract

- You are in PLANNING MODE. Do NOT create, edit, or delete any file. Do NOT run mutating
  `git`/`./gradlew` commands.
- `docs/specification/` is FROZEN — input only.
- End by calling `ExitPlanMode`. On approval the executors run: the `coder` implements the
  change-set; the `tester` writes the AC/edge-case tests and runs `./gradlew trace` /
  `./gradlew traceCheck`; the `debugger` diagnoses any failures; the
  `spec-conformance-reviewer` reviews before the story is marked done.

## Grounding context (auto-collected)

- Target story argument: `$ARGUMENTS`
- Story files: !`ls docs/stories/ 2>/dev/null || echo "(none yet)"`
- Matching story file: !`ls docs/stories/ 2>/dev/null | grep -iE "$(echo "$ARGUMENTS" | grep -oE '[0-9]+' | head -1)" || echo "(resolve by id in Step 1)"`
- Traceability head: !`sed -n '1,12p' docs/traceability.yaml 2>/dev/null || echo "(no traceability record yet)"`
- Spec index head: !`sed -n '1,24p' docs/specification/00_Foundation/05_SPEC_INDEX.md 2>/dev/null`
- Module inventory: !`sed -n '1,30p' docs/implementation_plan/01_MODULE_INVENTORY.md 2>/dev/null || echo "(module inventory not present)"`
- Rules loaded: !`ls .claude/rules/ 2>/dev/null || echo "(none)"`
- Skills available: !`ls .claude/skills/ 2>/dev/null`
- Recent history: !`git log --oneline -15 2>/dev/null || echo "(no git history)"`

## Steps

1. **Resolve and read the story in full.** Normalize `$ARGUMENTS` to `STORY-NNN`, open its
   file under `docs/stories/`, and read every section. CONFIRM `status: ready` and that
   every `depends_on` story is `done`; if not, stop and report the blocker.
2. **Load binding inputs in order:**
   - `CLAUDE.md`.
   - Every cited spec clause (resolve via
     `docs/specification/00_Foundation/05_SPEC_INDEX.md`), each `DD-NN` in
     `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`, and each ADR the story lists
     under `docs/adr/`.
   - The process formats under `docs/implementation_plan/`:
     `02_STORY_FORMAT`, `05_ACCEPTANCE_CRITERIA_PATTERNS`, `03_TRACEABILITY`,
     `06_DEFINITION_OF_DONE`, `01_MODULE_INVENTORY`.
   - The applicable `.claude/rules/*` (always-loaded invariants) and `.claude/skills/*` for
     the touched modules (e.g. `document-pipeline`, `llm-provider-integration`,
     `javafx-ui-designer`, `testing-javafx`).
3. **Delegate discovery to the `investigator` subagent (read-only) via Task.** Ask it to
   report: the current state of the module(s) this story touches, reusable seams/ports/
   classes, existing tests and fixtures to extend, and the concrete edge cases (map each to
   its `EC-` id). Provide the story goal, ACs, and cited clauses.
4. **Produce an ordered change-set.** List files to create/modify per the module inventory,
   in dependency order, each honoring the invariants: layering (cross-module calls hit
   `:api` ports, not concretes), the `Result`/`AppError` envelope, records-first, FX-free
   core, token-only theming, offline, and skeleton-not-regenerated. Note any new port,
   Guice binding, or migration.
5. **Produce the test plan.** Map every acceptance criterion AND every edge case to:
   tier · test file · test method · `Proves: STORY-NNN-AC-N`. Use the right tier per
   pattern (P1..P6); UI ACs get a P4 control-state and, where practical, a P6 mockup
   screenshot; the LLM seam is WireMock; persistence uses temp SQLite / `:memory:`.
6. **State the traceability delta.** Give the `traceability.yaml` additions (story, clauses,
   edge_cases, modules) and confirm `./gradlew traceCheck` will pass with zero orphans and a
   fresh record once the tests land.
7. **Assemble the Definition-of-Done checklist** for this story (every AC proven, every EC
   tested, `traceCheck` zero orphans, module inventory updated if modules changed,
   UI matches the mockup, offline invariant intact) — including the **implicit clean-gate
   AC** of every story: `./gradlew clean build check spotlessCheck` green across the
   **whole project** (Spotless/Checkstyle/Error Prone+NullAway/SpotBugs/ArchUnit/tests),
   with **no "pre-existing failure" exemption** (`06_DEFINITION_OF_DONE.md`).
8. **List risks, open questions, and any ADR-needed** (architecturally-significant decision
   not settled by the spec -> flag for the architect).
9. **Write the execution handoff:** coder implements the change-set; tester writes the AC +
   edge-case tests and runs `./gradlew trace` / `traceCheck`; debugger on any failure;
   spec-conformance-reviewer before done.

## Guardrails

- Do not plan work outside the story's scope; note out-of-scope items separately.
- Do not proceed if `status != ready` or a `depends_on` is not `done`.
- Cite only spec anchors that resolve via the spec index; never invent a clause.
- Keep every planned change inside the owning module per the layering rules.

## Exit

Call `ExitPlanMode` with the change-set, test plan (AC/EC -> `Proves:`), traceability delta,
DoD checklist, risks/ADR-needed, and the execution handoff for review.
