# Traceability & Stories

Scope: `docs/specification/**` (frozen), `docs/stories/**`, `docs/adr/**`, `docs/traceability.yaml` (generated), and the `Proves:` markers in `**/src/test/java/**`. Format: `docs/implementation_plan/02_STORY_FORMAT.md` and `docs/implementation_plan/03_TRACEABILITY.md`. See also `testing.md`.

## MUST

- **MUST** treat `docs/specification/**` as **frozen / read-only**. New or changed behaviour is a **new story** (and, for heavy decisions, a new ADR) — never a spec edit. — Rationale: the spec is the stable contract; churn happens in stories.
- **MUST** work **one story per session**: a story file `docs/stories/story-NNN-<slug>.md` with the required front-matter (`id, title, status, spec_clauses[], modules[], acceptance_criteria[], edge_cases[], depends_on[], adrs[], phase, owner, estimate`) and fixed body order (Goal, In scope, Out of scope, Spec inputs, Design constraints, Acceptance criteria, Test plan, Definition of done). — Rationale: bounded, reviewable units of work.
- **MUST** anchor every `spec_clauses[]` entry to a **real** in-repo `<file>#<anchor>` and every `modules[]` entry to a real module path. — Rationale: traceability chains must resolve.
- **MUST** give each acceptance criterion `### STORY-NNN-AC-N` a **proving test** whose first line/name is `// Proves: STORY-NNN-AC-N`, and give each edge case `EC-<AREA>-<N>` a test. — Rationale: the chain spec-clause → story → AC → test → module must be complete.
- **MUST** keep `docs/traceability.yaml` **generated only** (schema `stories{}, clauses{}, edge_cases{}, modules{}`) — never hand-edited. Regenerate with `./gradlew trace`; validate with `./gradlew traceCheck` (zero orphans, fresh record). — Rationale: traceability is machine-maintained and always fresh.
- **MUST** satisfy the per-story **Definition of Done** before `status: done` (every AC/EC has a passing named test; the **implicit clean-gate AC**: `./gradlew clean build check spotlessCheck` green across the **whole project** — Spotless/Checkstyle/Error Prone+NullAway/SpotBugs/ArchUnit/tests — with **no "pre-existing failure" exemption**; `traceCheck` clean; inventory updated if modules changed; UI matches the mockup; offline invariant holds). — Rationale: `done` means gated, not "code written"; a red check anywhere blocks every story.
- **MUST** keep IDs **permanent** and **done stories immutable**: never renumber `FR/NFR/STORY/AC/EC/ADR` ids; a completed story is not reopened — follow-up work is a new story. — Rationale: stable references across the whole corpus.

## SHOULD

- **SHOULD** size stories `S|M|L` and record `depends_on[]` so planning can order them within a phase. — Rationale: realistic sequencing.
- **SHOULD** mirror heavy decisions as ADRs (`docs/adr/ADR-NNNN-<slug>.md`) and link them from the story `adrs[]`. — Rationale: rationale is captured where it belongs.

## Reject if

- A change edits `docs/specification/**` instead of creating a new story/ADR.
- A story's `spec_clauses[]`/`modules[]` point at a non-existent file/anchor/module.
- An AC or EC has no proving test, or a test lacks its `Proves:`/EC marker (traceability orphan).
- `docs/traceability.yaml` is hand-edited, or `traceCheck` is not run / not clean.
- A story is marked `done` with an unmet DoD gate.
- An existing id is renumbered, or a done story is reopened instead of superseded by a new story.
