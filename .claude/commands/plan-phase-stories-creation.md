---
description: Plan the real story files for a phase, refined against the current codebase (planning only; spec frozen; ends with ExitPlanMode).
argument-hint: <PHASE_NN or N>
allowed-tools: Read, Grep, Glob, Bash, WebSearch, Task, TodoWrite, ExitPlanMode
model: opus
---

# Plan Phase Stories Creation

Plan the creation of the real story files for phase **$ARGUMENTS**, refined against the
current codebase. This command is READ-ONLY and produces a plan only.

## Read-only contract

- You are in PLANNING MODE. Do NOT create, edit, or delete any file. Do NOT run `git`
  writes, `./gradlew` writes, or any mutating command.
- `docs/specification/` is FROZEN — it is input, never output.
- The phase's "Suggested stories / tasks" table is a BACKLOG, not the source of truth —
  refine it against the spec and the actual codebase state.
- End by calling `ExitPlanMode` with the proposed story set. On approval, the `architect`
  subagent (not this command) writes the story files into `docs/stories/` and updates
  `docs/stories/README.md`, then runs `./gradlew trace` / `./gradlew traceCheck`.

## Grounding context (auto-collected)

- Target phase argument: `$ARGUMENTS`
- Existing stories: !`ls docs/stories/ 2>/dev/null || echo "(none yet)"`
- Highest current story id: !`grep -rhoE 'STORY-[0-9]{3}' docs/stories/ 2>/dev/null | sort -u | tail -1 || echo "(none)"`
- Phase files: !`ls docs/implementation_plan/phases/ 2>/dev/null || echo "(none yet)"`
- Roadmap head: !`sed -n '1,40p' docs/implementation_plan/07_ROADMAP.md 2>/dev/null || echo "(roadmap not present)"`
- Spec index head: !`sed -n '1,24p' docs/specification/00_Foundation/05_SPEC_INDEX.md 2>/dev/null`
- Traceability head: !`sed -n '1,12p' docs/traceability.yaml 2>/dev/null || echo "(no traceability record yet)"`
- Recent history: !`git log --oneline -15 2>/dev/null || echo "(no git history)"`

## Steps

1. **Resolve the phase.** Normalize `$ARGUMENTS` to `PHASE_NN_<SLUG>` and open its file
   under `docs/implementation_plan/phases/`. Read its goal, scope, dependencies,
   forward-compat notes, the Suggested-stories backlog table, the exit checklist, and its
   **phase→clause manifest** (`phase_clauses:` YAML front-matter, read by `traceCheck` at
   phase exit) — the story set must leave no manifest clause uncited by a story.
2. **Load binding inputs in order:**
   - `CLAUDE.md` (invariants, tracking model).
   - `docs/specification/INDEX.md` if present, else
     `docs/specification/00_Foundation/05_SPEC_INDEX.md`.
   - `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md` (seams F1..F9 and their
     phase mapping).
   - The phase file itself, and `docs/implementation_plan/07_ROADMAP.md`.
   - The process formats under `docs/implementation_plan/`:
     `02_STORY_FORMAT`, `05_ACCEPTANCE_CRITERIA_PATTERNS`, `03_TRACEABILITY`,
     `06_DEFINITION_OF_DONE`, `01_MODULE_INVENTORY`, and `04_ADR_FORMAT` (the spec index
     itself was loaded above).
   - `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md` and every product/architecture
     clause the phase cites (resolve each via the spec index).
3. **Delegate discovery to the `investigator` subagent (read-only) via Task.** Ask it to
   report: the current state of the modules this phase touches, which forward-compat seams
   already exist vs. must be created, reusable ports/classes, fixtures, and any drift
   between the backlog and the real code. Provide it the phase scope and the cited clauses.
4. **Refine the backlog into a real story set.** Treat the Suggested-stories table as a
   starting point: split, merge, add, or drop items so each story is atomic, testable, and
   maps to spec clauses. Assign monotonic unique `STORY-NNN` ids AFTER the current highest
   id (from grounding). Sequence stories by `depends_on`, honoring the seam order.
5. **Draft each story** (do not write files) per `docs/stories/STORY_TEMPLATE.md`:
   front-matter (`id, title, status:draft, spec_clauses[], modules[], acceptance_criteria[],
   edge_cases[], depends_on[], adrs[], phase: PHASE_NN_SLUG, owner, estimate S|M|L`), the
   acceptance criteria (P1..P6, one value each), the test plan (AC -> tier · test file ·
   method · `Proves: STORY-NNN-AC-N`), the traceability delta, and the design constraints
   (layering, envelope, records-first, FX-free core, token-only theming, offline,
   skeleton-not-regenerated / canonical-equal round-trip). Every story's DoD carries the
   **implicit clean-gate AC**: `./gradlew clean build check spotlessCheck` green across the
   whole project, no pre-existing-failure exemption (`06_DEFINITION_OF_DONE.md`).
6. **Present the plan** as an ordered story list with, per story: id, title, size,
   depends_on, the clauses/ECs it covers, and any ADR-needed flag. Confirm the set covers
   the phase scope and leaves the seams intact, and that `traceCheck` will pass with zero
   orphans once tests land.

## Guardrails

- Never invent spec clauses — cite only anchors that resolve via the spec index.
- Never assign a story id at or below the current highest id; ids are permanent.
- A story that needs an architecturally-significant, spec-unsettled decision must flag
  `ADR-needed` (the architect authors the ADR during execution).
- UI stories must cite the mockup and use a P6 visual-reference AC.
- Do not exceed the phase scope; out-of-scope items go to a later phase's backlog note.

## Exit

Call `ExitPlanMode` with the refined, ordered story set and the id assignments for review.
