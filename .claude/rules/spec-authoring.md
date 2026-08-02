# Spec & Change Authoring

Scope: `openspec/**`, `docs/adr/**`, `docs/implementation_plan/**`, and the `Covers:` markers in
`**/src/test/java/**`. `docs/specification/**` is frozen and read-only. Authoring standard: ADR-0016 (R1–R6);
delivery order: ADR-0017. Rules that steer generation live in `openspec/config.yaml`. Scenario patterns P1–P6:
`docs/implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md`. See also `testing.md`.

## MUST

- **MUST** treat `docs/specification/**` as **frozen / read-only**. New or changed behaviour is a **new OpenSpec
  change**; a genuine gap or deviation is a **new ADR** under `docs/adr/` — never a spec edit. — Rationale: the spec is
  the stable contract; churn happens in changes and ADRs.
- **MUST** work **one change at a time**, taken in the order of `docs/implementation_plan/CHANGE_BACKLOG.md`, through
  the loop `/opsx:propose → openspec validate --strict → /opsx:apply → Definition of Done → /opsx:archive`. — Rationale:
  bounded, reviewable units of work with a validated artifact set.
- **MUST** make every artifact readable **without opening another file**. An ID is a **citation, never a payload**: a
  bare `FR-DOC-04` or `DD-14` as the content of a requirement, a scenario, or a task is a defect. Restate what it says,
  then cite it. — Rationale: this is the single failure the story format had; it is not being reintroduced.
- **MUST** write requirements in **EARS** (R1) — `The <system> SHALL …` / `WHEN <trigger>, the <system> SHALL …` /
  `WHILE <state>, …` / `IF <condition>, THEN …` / `WHERE <feature>, …` — decomposing a dense FR into **several atomic
  requirements** rather than transcribing it. Several requirements may share one `FR-*` citation. — Rationale: one
  requirement, one testable obligation; the decomposition is where ambiguity surfaces.
- **MUST** end every requirement with a **`Source:` block** (R2) carrying (a) the `FR-*`/`NFR-*`/`DD-*` ids with real
  in-repo anchors and (b) an **"In plain words:"** sentence saying what it means and why it matters. — Rationale: the
  citation locates, the gloss explains; ids alone are the anti-pattern.
- **MUST** write scenarios with **concrete values** (R3) — `ErrorCode.validation`, not "an error"; `⟦g1⟧⟦g2⟧` vs
  `⟦g1⟧`, not "invalid input" — using exactly **four hashes** (`#### Scenario: <name>`). Three hashes fail silently in
  OpenSpec's parser, and every requirement needs at least one scenario. — Rationale: a scenario is a test case; an
  abstraction is not.
- **MUST** write every `tasks.md` checkbox as a **full sentence** stating what changes and why it matters, then the
  owning module and the spec pointer (R4): `- [ ] 2.1 <what>. <why>. → :module · <spec-file>#<anchor>, DD-NN`. The
  **final task group is always the green gate**. — Rationale: the reader must know what to do without a second file.
- **MUST** mark every covering test `// Covers: FR-*` followed by a **one-line EARS restatement** of the obligation it
  proves (R5). — Rationale: the id is greppable, the restatement is readable; `FR-*` ids are permanent because the spec
  is frozen.
- **MUST** anchor every spec citation to a **real** in-repo `<file>#<anchor>` and every module reference to a real
  module path from `docs/implementation_plan/01_MODULE_INVENTORY.md`. — Rationale: a citation that does not resolve is
  worse than none.
- **MUST** use only the **16 capability names** mapped from the FR areas (`openspec/config.yaml`,
  `CHANGE_BACKLOG.md#capability-map`); a new capability requires an ADR first. — Rationale: the FR-id join key only
  holds if the map is exact.
- **MUST** set `skip_specs: true` in a change's `.openspec.yaml` when there is genuinely **no user-observable
  behaviour** (build tooling, lint config, ArchUnit rules, CI, packaging), and **MUST NOT** invent a fake requirement to
  satisfy validation. — Rationale: `openspec/specs/` is a ledger of product behaviour; developer mechanics would
  pollute it.
- **MUST** satisfy the **Definition of Done** (`docs/implementation_plan/06_DEFINITION_OF_DONE.md`) before archiving —
  including the **implicit clean-gate requirement**: `./gradlew clean build check spotlessCheck` green across the
  **whole project**, with **no "pre-existing failure" exemption**. — Rationale: archived means gated, not "code
  written".
- **MUST** keep IDs **permanent**: never renumber `FR/NFR/DD/EC/ADR`. An archived change is not reopened — follow-up
  work is a new change. — Rationale: stable references across the whole corpus.

## SHOULD

- **SHOULD** write `design.md` only when the change is cross-cutting, adds an external dependency, changes the persisted
  schema, touches a seam (F1–F9), or holds a genuine ambiguity — and **cite the governing ADR** instead of re-arguing
  it. Omit the file entirely when an ADR already settles everything. — Rationale: a design doc that restates an ADR is
  noise.
- **SHOULD** add a scenario for each ambiguity the frozen FR leaves open (is a "multiset" order-insensitive? what
  happens on empty input? which side wins a conflict?). — Rationale: surfacing those is the point of the rephrasing
  pass.
- **SHOULD** run `bash scripts/fr-coverage.sh` at stage boundaries and read the output as **advisory** — mid-build-out
  gaps are expected, since `openspec/specs/` tracks what is built, not what is intended. — Rationale: coverage is
  information, not a gate (R6).
- **SHOULD** use `/opsx:explore` when the shape of the work is genuinely unclear, and `/opsx:update` to revise a
  change's artifacts coherently rather than editing one in isolation. — Rationale: the artifacts are interdependent.

## Reject if

- A change edits `docs/specification/**` instead of creating a new change/ADR.
- A requirement is copied from the FR table rather than rewritten in EARS, or a dense FR is transcribed instead of
  decomposed.
- A requirement has no `Source:` block, or its `Source:` block is ids with no plain-words gloss.
- A scenario uses an abstraction where a concrete value belongs, uses three hashes instead of four, or a requirement
  ships with no scenario at all.
- A `tasks.md` checkbox is a bare ID, or lacks its module and spec pointer, or the final group is not the green gate.
- A test lacks `// Covers: FR-*`, or carries the id with no EARS restatement.
- A citation points at a non-existent file, anchor, module, or capability name.
- A capability outside the 16-name map is introduced without an ADR.
- A fake requirement is invented to avoid `skip_specs: true` on a pure-infrastructure change.
- A change is archived with an unmet Definition-of-Done gate, or an existing id is renumbered, or an archived change is
  reopened instead of superseded by a new one.
- **Retired machinery is reintroduced** (ADR-0016): a story file under `docs/stories/`, `docs/traceability.yaml`, a
  `trace` / trace-validation Gradle task, or a `Proves: STORY-NNN-AC-N` test marker.
