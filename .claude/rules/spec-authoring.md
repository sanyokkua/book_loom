---
paths:
  - "openspec/**"
  - "docs/adr/**"
  - "docs/implementation_plan/**"
---

# Spec & Change Authoring

Scope: `openspec/**`, `docs/adr/**`, `docs/implementation_plan/**`. `docs/specification/**` is the reference and is
editable. Rules that steer OpenSpec generation live in `openspec/config.yaml`; the retired ID-tracking machinery is
recorded in ADR-0032. See also `testing.md`.

## MUST

- **MUST** make every artifact readable **without opening another file**. An ID is a **citation, never a payload**: a
  bare `FR-DOC-04` or `DD-14` as the content of a requirement, a scenario, or a task is a defect. Restate what it says,
  then cite it. — Rationale: this is the single failure the story format had.
- **MUST** write requirements in **EARS** (R1) — `The <system> SHALL …` / `WHEN <trigger>, the <system> SHALL …` /
  `WHILE <state>, …` / `IF <condition>, THEN …` / `WHERE <feature>, …` — decomposing a dense FR into **several atomic
  requirements** rather than transcribing it. — Rationale: one requirement, one testable obligation.
- **MUST** end every requirement with a **`Source:` block** (R2): the spec file and anchor it comes from, and an
  **"In plain words:"** sentence saying what it means and why it matters. — Rationale: the pointer locates, the gloss
  explains.
- **MUST** write scenarios with **concrete values** (R3) — `ErrorCode.validation`, not "an error"; `⟦g1⟧⟦g2⟧` vs
  `⟦g1⟧`, not "invalid input" — using exactly **four hashes** (`#### Scenario: <name>`); every requirement needs at
  least one scenario. — Rationale: a scenario is a test case; an abstraction is not.
- **MUST** write every `tasks.md` checkbox as a **full sentence** stating what changes and why it matters, then the
  owning module (R4). The **final task group is always the gate and the app run by hand**. A checkbox is a claim, never
  evidence. — Rationale: the reader must know what to do without a second file; the test is the proof.
- **MUST** keep a change small: a proposal of at most one page, at most ~10 tasks, the acceptance test observed red
  before the implementation. — Rationale: a change that does not fit on a page is two changes.
- **MUST** anchor every spec citation to a **real** in-repo `<file>#<anchor>` and every module reference to a real
  module. — Rationale: a citation that does not resolve is worse than none.
- **MUST** use only the **16 capability names** in `openspec/config.yaml`; a new capability requires an ADR first.
  — Rationale: the ledger stays navigable.
- **MUST** set `skip_specs: true` in a change's `.openspec.yaml` when there is genuinely **no user-observable
  behaviour** (build tooling, lint config, CI, packaging), and **MUST NOT** invent a fake requirement to satisfy
  validation. — Rationale: `openspec/specs/` is a ledger of product behaviour.
- **MUST** edit a spec clause the code legitimately outgrew **in the same change**; write an ADR only for a decision
  that is costly to reverse, and name in it what would falsify it. — Rationale: a frozen document forces every
  learning into an ADR and the project into reconciling documents instead of shipping.
- **MUST** keep IDs **permanent**: never renumber `FR/NFR/DD/EC/ADR`. An archived change is not reopened — follow-up
  work is a new change. — Rationale: stable references.

## SHOULD

- **SHOULD** write `design.md` only when the change is cross-cutting, adds an external dependency, changes the persisted
  schema, or holds a genuine ambiguity — and cite the governing ADR instead of re-arguing it. — Rationale: a design
  doc that restates an ADR is noise.
- **SHOULD** add a scenario for each ambiguity the FR leaves open (is a "multiset" order-insensitive? what happens on
  empty input?). — Rationale: surfacing those is the point of the rephrasing pass.
- **SHOULD** use `/opsx:explore` when the shape of the work is unclear, and `/opsx:update` to revise a change's artifacts
  coherently rather than editing one in isolation. — Rationale: the artifacts are interdependent.

## Reject if

- A requirement is copied from the FR table rather than rewritten in EARS, or a dense FR is transcribed instead of
  decomposed.
- A requirement has no `Source:` block, or its `Source:` block is ids with no plain-words gloss.
- A scenario uses an abstraction where a concrete value belongs, uses three hashes instead of four, or a requirement
  ships with no scenario at all.
- A `tasks.md` checkbox is a bare ID, or the final group is not the gate plus the app run by hand.
- A citation points at a non-existent file, anchor, module, or capability name.
- A fake requirement is invented to avoid `skip_specs: true` on a pure-infrastructure change.
- An existing id is renumbered, or an archived change is reopened instead of superseded by a new one.
- **Retired machinery is reintroduced** (ADR-0016, ADR-0032): a story file, `docs/traceability.yaml`, a trace Gradle
  task, a `Proves:` or `// Covers:` test marker, a requirement-id coverage script, or a checkbox gate.
