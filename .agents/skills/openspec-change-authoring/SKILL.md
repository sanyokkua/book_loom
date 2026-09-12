---
name: openspec-change-authoring
description: >-
  Use when authoring or reviewing an OpenSpec change under `openspec/changes/` for
  BookLoom — writing EARS requirements with a `Source:` block, concrete scenarios,
  and sentence-level `tasks.md` checkboxes. Covers the R1-R4 authoring standard (ADR-0016 as
  amended by ADR-0032), when to set `skip_specs: true`, the 16 capability names, when a
  `design.md` is warranted, and the propose -> validate -> apply -> archive loop. Replaces
  the retired story-and-traceability workflow.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# OpenSpec Change Authoring

An `openspec/changes/<name>/` directory is the unit of tracked work (ADR-0016). The
specification under `docs/specification/` is the reference; a change translates its `FR-*`
clauses into EARS requirements, concrete scenarios, and an implementation checklist.

**The one rule everything else serves:** every artifact must be readable **without opening
another file**. An ID is a *citation*, never a *payload*. A bare `FR-DOC-04` standing alone
as the content of a requirement, a scenario, or a task is a defect.

## When to use

- Authoring a new change (usually via `/opsx:propose`, then reviewing what it generated).
- Reviewing or revising a change's `proposal.md`, `specs/**`, `design.md`, or `tasks.md`.
- Deciding whether a change needs a `design.md` at all, or should set `skip_specs: true`.

## When NOT to use

- Do NOT record a spec deviation somewhere else: when the shipped code legitimately differs
  from a clause, fix the clause in the same change. A decision that is costly to reverse is a
  **new ADR** under `docs/adr/` (use the `adr-authoring` skill).
- Do NOT hand-edit `openspec/specs/**` — it is written by `openspec archive`. The exception
  is fixing a leftover `TBD` Purpose placeholder.
- Do NOT reopen an archived change; follow-up work is a new change.
- Do NOT reintroduce retired machinery: story files, `docs/traceability.yaml`, a `trace` /
  trace-validation Gradle task, or a `Proves: STORY-NNN-AC-N` marker.

## The loop

`CHANGE_BACKLOG.md` entry → `/opsx:propose` → `openspec validate <change> --strict` →
`/opsx:apply` → Definition of Done → `/opsx:archive`.

Use `/opsx:explore` when the shape of the work is genuinely unclear, and `/opsx:update` to
revise a change's artifacts **coherently** rather than editing one in isolation.

## R1 — Requirements in EARS, decomposed

Write, do not transcribe. The five patterns:

| Pattern | Form |
|---|---|
| Ubiquitous | `The <system> SHALL <response>` |
| Event | `WHEN <trigger>, the <system> SHALL <response>` |
| State | `WHILE <state>, the <system> SHALL <response>` |
| Unwanted | `IF <condition>, THEN the <system> SHALL <response>` |
| Optional | `WHERE <feature is included>, the <system> SHALL <response>` |

A dense FR **decomposes into several atomic requirements**. `FR-DOC-04` — one five-line
sentence covering inline tags, locked terms, URLs, selective numerals, code, MathML and
anchors — becomes three or four. Several requirements sharing one `FR-*` citation is
correct and expected; that shared citation is what keeps coverage checkable across the
split.

## R2 — Every requirement carries a `Source:` block

Two halves, both required: the ids **with real in-repo anchors**, and an
**"In plain words:"** sentence saying what it means and why it matters.

## R3 — Concrete scenarios, four hashes

Not "invalid input" → `⟦g1⟧⟦g2⟧` vs `⟦g1⟧`. Not "an error" → `ErrorCode.validation`. Not
"a large file" → a stated size. `#### Scenario:` uses **exactly four hashes** — three fail
silently in OpenSpec's parser. Every requirement needs at least one scenario.

Add a scenario for each ambiguity the FR leaves open. Worked example:

```markdown
### Requirement: Placeholder multiset is a hard gate

WHEN a translated chunk returns from the model, the system SHALL compare the multiset of
`⟦gN⟧` placeholders in the target text against the multiset in the masked source text.

IF the two multisets differ in any way — a placeholder missing, added, or duplicated — THEN
the system SHALL fail the chunk with a `validation` error, and SHALL NOT unmask, repair, or
silently reconcile the difference.

**Source:** FR-DOC-05, FR-QA-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`).
In plain words: the model may rewrite the words, never the inline structure. This is the one
gate that no confidence score and no judge verdict can override.

#### Scenario: Model drops a placeholder
- **WHEN** the masked source contained `⟦g1⟧` and `⟦g2⟧` but the target contains only `⟦g1⟧`
- **THEN** the chunk fails with `ErrorCode.validation` and no unmasking occurs

#### Scenario: Model reorders placeholders
- **WHEN** the target contains `⟦g2⟧⟦g1⟧` and the source contained `⟦g1⟧⟦g2⟧`
- **THEN** the multiset matches, the gate passes, and unmasking proceeds
```

The second scenario resolves an ambiguity the one-line FR leaves open — a multiset is
order-insensitive. Surfacing those is the point of the rephrasing pass.

## R4 — Task items are sentences

`- [ ] 2.1 <what changes>. <why it matters / what breaks otherwise>. → :module ·
<spec-file>#<anchor>, DD-NN`

The **final task group is always the green gate**: `./gradlew clean build check
spotlessCheck` green project-wide with no pre-existing-failure exemption, the change's
user-visible behaviour exercised in the running app, and `01_MODULE_INVENTORY.md` updated if a
module or package was added.

## Tests are the evidence

A test is named `method_state_expected` and, when the name is not enough, carries a one-line
plain-language comment saying what it proves:

```java
// IF the placeholder multiset of the target differs from the source, THEN the chunk fails as a
// validation error with no repair attempt.
@Test
void validateChunk_placeholderMissingFromTarget_returnsValidationError() { … }
```

There is no requirement-id marker and no coverage script (ADR-0032): a checkbox or an id
count is a claim, the red-then-green test and the running app are the evidence.

## Capabilities and `skip_specs`

Use only the 16 kebab-case capability names in `openspec/config.yaml` /
`CHANGE_BACKLOG.md#capability-map`; a new one requires an ADR first. Check
`openspec/specs/` before calling a capability NEW — if the folder exists it is MODIFIED and
needs a delta spec whose MODIFIED requirement blocks carry the **entire** original content,
not a fragment.

Set `skip_specs: true` in the change's `.openspec.yaml` when there is genuinely **no
user-observable behaviour** — build tooling, lint config, ArchUnit rules, CI wiring,
packaging. Never invent a fake requirement to satisfy validation; `openspec/specs/` is a
ledger of product behaviour and developer mechanics would pollute it.

## When to write a `design.md`

Only when the change is cross-cutting across modules, adds an external dependency, changes
the persisted schema, touches a forward-compatibility seam (F1–F9), or holds a genuine
ambiguity worth deciding before coding. **Cite the governing ADR** rather than re-arguing
it, and **omit the file entirely** when an ADR already settles everything. This is the one
artifact where class names, library names, and module paths belong.

## Reference index

- `openspec/config.yaml` — the rules that steer generation, plus project context.
- `docs/adr/ADR-0016-openspec-delivery-tracking.md` — R1–R4 and the superseded spec clauses;
  `ADR-0032-tests-are-the-evidence.md` — why there are no test markers or coverage script.
- `docs/adr/ADR-0017-infrastructure-first-delivery-order.md` — the five stages.
- `docs/implementation_plan/CHANGE_BACKLOG.md` — the ordered backlog and capability map.
- `AGENTS.md` — the Definition of Done.
- `docs/specification/00_Foundation/05_SPEC_INDEX.md` — resolve any `FR-*`/`NFR-*`/`DD-*`.

## Mandatory validation checklist

- [ ] Every requirement is EARS-shaped, atomic, and rewritten — not copied from the FR table.
- [ ] Every requirement has a `Source:` block with resolvable anchors **and** a plain-words gloss.
- [ ] Every scenario uses concrete values and exactly four hashes; every requirement has ≥1.
- [ ] No class/library/method name appears in a `spec.md` — those belong in `design.md`.
- [ ] Capability names come from the 16-name map; NEW vs MOD checked against `openspec/specs/`.
- [ ] A new capability's delta opens with a real `## Purpose` (50+ characters).
- [ ] Every `tasks.md` checkbox is a sentence with module and spec pointer; final group is the green gate.
- [ ] `skip_specs: true` is set **iff** there is genuinely no user-observable behaviour.
- [ ] `openspec validate <change> --strict` is clean.
- [ ] No spec file under `docs/specification/**` was touched.
- [ ] No retired machinery reintroduced (story file, generated traceability yaml, trace tasks, `Proves:` marker).

## Gotchas

- **Three hashes on a scenario fail silently.** No error, no scenario — it simply is not
  parsed. This is the single easiest mistake to make and the hardest to notice.
- **A malformed `tasks.md` checkbox is untracked.** Only `- [ ] X.Y …` is parsed by apply.
- **MODIFIED with partial content loses detail at archive time.** Copy the entire
  requirement block from `openspec/specs/<capability>/spec.md`, then edit.
- **`## Purpose` on a delta for an existing capability is ignored.** To change it, edit the
  main spec directly.
- **A `Source:` block that is only ids is incomplete** — it is exactly the anti-pattern the
  standard exists to prevent.
- **Invented capability names fragment the ledger.** Use the 16-name map.
