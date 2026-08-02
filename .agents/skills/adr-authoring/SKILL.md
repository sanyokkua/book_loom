---
name: adr-authoring
description: >-
  Use when a decision is architecturally significant and NOT already settled by the
  frozen specification, and you must record it as an ADR under `docs/adr/`. Covers the
  ADR template and headings, `ADR-NNNN` numbering, the proposed -> accepted ->
  superseded/deprecated status lifecycle, and linking an ADR to design decisions
  (DD-NN), spec clauses, and stories.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# ADR Authoring

An Architecture Decision Record captures one significant, non-obvious decision with its
context, the options weighed, and the consequences. ADRs are the heavy counterpart to the
lightweight `DD-NN` log in `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`.

## When to use

- A decision is architecturally significant (affects module boundaries, a public port, a
  data format, an external seam, cross-cutting behaviour) AND the frozen specification does
  not already settle it.
- Elaborating a design decision (`DD-NN`) that the spec marked as needing a heavy record.
- Superseding an existing ADR because a newer decision replaces it.

## When NOT to use

- Do NOT write an ADR for something the spec already decides. The `DD-NN` entries
  (DD-01..DD-49) and the architecture docs under `docs/specification/02_Architecture/`
  are settled facts — cite them, do not re-litigate them.
- Do NOT use an ADR for routine implementation choices with no cross-module impact — those
  belong in the story's Design-constraints section.
- Do NOT edit `docs/specification/` — the spec is frozen. An ADR lives only in `docs/adr/`.
- Do NOT rewrite an `accepted` ADR's decision after the fact — supersede it with a new one.

## Workflow

1. **Confirm significance and novelty.** Read the relevant `DD-NN` entries and
   `02_Architecture/*` clauses. If they already answer the question, stop and cite them.
2. **Pick the number.** Scan `docs/adr/` for the highest `ADR-NNNN` and assign the next
   zero-padded integer (the log currently ends at `ADR-0015-app-environment-and-paths.md`,
   so the next free number is **ADR-0016** — always re-verify by scanning). Filename:
   `docs/adr/ADR-NNNN-<slug>.md`. Numbers are permanent.
3. **Write the headings in this exact order:**
   - `# ADR-NNNN — <imperative title>`
   - `**Status:** proposed | accepted | superseded by ADR-MMMM | deprecated`
   - `**Date:**` (ISO date) · `**Deciders:**` · `**Supersedes:**` (omit if none)
   - `## Context and problem statement`
   - `## Decision drivers`
   - `## Considered options`
   - `## Decision outcome` — start with "Chosen: <option>, because <reason>." then
     `### Consequences` with Positive / Negative / Neutral bullets.
   - `## Pros and cons of the options` — one subsection per option.
   - `## Links` — Design decisions `DD-NN`, spec clauses (`<file>#<anchor>`), stories
     (`STORY-NNN`).
4. **Set the initial status** to `proposed`. When the decision is ratified, change it to
   `accepted`. When a later ADR replaces it, set `superseded by ADR-MMMM` here and add
   `**Supersedes:** ADR-NNNN` in the new ADR.
5. **Backlink.** If the ADR realizes or refines a `DD-NN`, do NOT add `**ADR:** ADR-NNNN`
   to that DD entry (the spec is frozen) — instead ensure the ADR's `## Links` cites the
   DD, and any story driving the decision lists it in its `adrs[]` front-matter.

## Reference index

- `docs/adr/template.md` — the full heading skeleton to copy.
- `docs/implementation_plan/04_ADR_FORMAT.md` — the ADR format and the
  proposed/accepted/superseded/deprecated lifecycle rules.
- In-repo authorities: `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md` (the DD
  log the ADR elaborates), `docs/specification/00_Foundation/05_SPEC_INDEX.md` (clause
  anchors to cite), `docs/specification/02_Architecture/` (settled architecture).

## Mandatory validation checklist

- [ ] `ADR-NNNN` is unique, zero-padded, higher than every existing ADR number.
- [ ] All required headings present in the exact order above.
- [ ] `**Status:**` is one legal value; `**Supersedes:**` present iff this replaces one.
- [ ] `## Decision outcome` opens with "Chosen: …, because …" and lists Positive/
      Negative/Neutral consequences.
- [ ] Each considered option has a pros/cons subsection.
- [ ] `## Links` cites at least one `DD-NN` and the relevant spec clause(s)/story ids.
- [ ] The ADR decides something the spec does NOT already decide (novelty confirmed).
- [ ] No file under `docs/specification/` was modified.

## Gotchas

- Superseding is bidirectional: the old ADR's Status must point forward
  (`superseded by ADR-MMMM`) and the new one's `**Supersedes:**` must point back.
- An ADR that merely restates a `DD-NN` adds noise — either add genuine analysis
  (drivers, options, trade-offs) or just cite the DD from a story.
- Keep the title imperative ("Use X for Y"), not a question.
- Do not leave an ADR at `proposed` forever; a decision the code already depends on is
  effectively `accepted` and should say so.
