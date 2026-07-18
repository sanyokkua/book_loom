**Status:** Final **Owner:** architect **Audience:** architect, coder, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`,
`docs/implementation_plan/02_STORY_FORMAT.md`, `docs/implementation_plan/03_TRACEABILITY.md`,
`docs/specification/00_Foundation/05_SPEC_INDEX.md`

# ADR Format

An **Architecture Decision Record (ADR)** is a heavy, durable record of a single architecturally significant decision
that the specification does not already settle. ADRs live under `docs/adr/` as `ADR-NNNN-<slug>.md`. They complement the
lightweight design-decisions log (`DD-NN`) in `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`: a `DD` is a
one-liner in the frozen spec; an ADR is the full reasoning behind a decision taken during implementation.

## when-to-write-one {#when-to-write-one}

Write an ADR when a decision is **architecturally significant and not settled by the spec**. Concretely:

- It affects module boundaries, a public port in `:api`, a persisted schema, the threading model, or a
  forward-compatibility seam (F1–F9).
- It is costly or disruptive to reverse later.
- It picks among genuine alternatives where a reasonable engineer could choose differently.
- It refines or operationalizes a spec `DD-NN` that left the "how" open (e.g. the exact retry backoff policy behind
  DD-13, the concrete checkpoint format behind DD-27).

**Do not** write an ADR when the spec already decides it (cite the `DD-`/`FR-` instead), when it is a local,
easily-reversible implementation choice, or when it merely restates an existing decision. When in doubt, prefer citing
the spec; reserve ADRs for real, load-bearing forks.

## numbering-and-naming {#numbering-and-naming}

- File: `docs/adr/ADR-NNNN-<slug>.md`, `NNNN` zero-padded and monotonically increasing across the whole project
  (`ADR-0001`, `ADR-0002`, …). Numbers are never reused.
- `<slug>` is kebab-case of the decision title.
- A superseding ADR gets a new number; the superseded ADR's status is updated to point at it (both files are kept).

## template {#template}

```markdown
# ADR-NNNN — <imperative decision statement>

**Status:** proposed | accepted | superseded by ADR-MMMM | deprecated
**Date:** 2026-07-17
**Deciders:** <roles/names>
**Supersedes:** ADR-KKKK    <!-- omit this line if none -->

## Context and problem statement
<The forces at play, the constraint being resolved, and why a decision is needed now. Reference the spec DD-NN / FR-… that frames it.>

## Decision drivers
- <driver 1 — e.g. offline invariant NFR-OFFLINE-…>
- <driver 2 — e.g. FX-free core boundary>
- <driver 3>

## Considered options
- <Option A>
- <Option B>
- <Option C>

## Decision outcome
Chosen: **<Option X>**, because <the decisive reason tied to the drivers>.

### Consequences
- **Positive:** <what gets better>
- **Negative:** <the cost / trade-off accepted>
- **Neutral:** <notable but neither good nor bad>

## Pros and cons of the options
### <Option A>
- Good: ...
- Bad: ...
### <Option B>
- Good: ...
- Bad: ...
### <Option C>
- Good: ...
- Bad: ...

## Links
- **Design decisions:** DD-NN (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-NN-<slug>`)
- **Spec clauses:** FR-…, NFR-… (resolve via `docs/specification/00_Foundation/05_SPEC_INDEX.md`)
- **Stories:** STORY-NNN, STORY-MMM
- **Supersedes / superseded by:** ADR-KKKK / ADR-MMMM (if any)
```

## status-lifecycle {#status-lifecycle}

```
proposed ──► accepted ──► superseded by ADR-MMMM
                    └────► deprecated
```

| Status                   | Meaning                                                                                |
|--------------------------|----------------------------------------------------------------------------------------|
| `proposed`               | Drafted, under discussion; not yet binding.                                            |
| `accepted`               | The decision is in force and stories may depend on it.                                 |
| `superseded by ADR-MMMM` | Replaced by a newer ADR; kept for history. The new ADR sets `Supersedes:` to this one. |
| `deprecated`             | No longer relevant and not replaced (the need went away).                              |

Only `accepted` ADRs bind implementation. A story cites an ADR in its `adrs:` front-matter; `./gradlew traceCheck` fails
if the cited `ADR-NNNN` has no file under `docs/adr/` (check #11 in `docs/implementation_plan/03_TRACEABILITY.md`).

## linking-rules {#linking-rules}

Every ADR closes the loop to the rest of the corpus:

- **Link the DD-NN it refines or operationalizes.** If an ADR contradicts a `DD-`, that is a spec conflict — resolve it
  through a spec revision, not silently in the ADR.
- **Cite the spec clauses** (`FR-…`, `NFR-…`) the decision serves, resolvable via the spec index.
- **List the stories** that implement or depend on the decision. Those stories cite the ADR back in their `adrs:`
  front-matter, making the link bidirectional.
- Keep every cross-reference in-repo and repo-relative. No external references.
