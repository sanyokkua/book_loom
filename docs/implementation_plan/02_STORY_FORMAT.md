**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/01_MODULE_INVENTORY.md`, `docs/implementation_plan/03_TRACEABILITY.md`,
`docs/implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md`, `docs/implementation_plan/06_DEFINITION_OF_DONE.md`,
`docs/specification/00_Foundation/05_SPEC_INDEX.md`

# Story Format

A **story** is the unit of implementable, testable work. Stories live at `docs/stories/story-NNN-<slug>.md`. The
architect writes them (after `/plan-phase-stories-creation` is approved); coder and tester implement them (after
`/plan-user-story-implementation` is approved). This document defines the front-matter schema, the fixed body order,
naming, sizing, and lifecycle. Deviations fail review and `./gradlew traceCheck`.

## naming {#naming}

- File: `docs/stories/story-NNN-<slug>.md` where `NNN` is a zero-padded, monotonically increasing integer
  (`story-001-...`, `story-017-...`) and `<slug>` is kebab-case of the title.
- The `STORY-NNN` id in prose and traceability uses uppercase (`STORY-017`); the filename uses lowercase `story-017`.
- Ids are assigned when the architect writes the story, not in the phase backlog (phase files are intentionally
  id-less).

## front-matter {#front-matter}

YAML front-matter at the top of the file. All keys are required unless marked optional.

```yaml
---
id: STORY-NNN
title: <imperative, one line>
status: draft            # draft | ready | in-progress | done | superseded
spec_clauses:            # spec clauses this story satisfies; see below
  - docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import
  - FR-IMPORT-03
modules:                 # module paths from 01_MODULE_INVENTORY.md
  - :document/ua.bookloom.document.detect
  - :api/ua.bookloom.api.document
acceptance_criteria:     # ids of the ACs defined in the body
  - STORY-NNN-AC-1
  - STORY-NNN-AC-2
edge_cases:              # EC- ids covered by this story (may be empty)
  - EC-LANG-1
depends_on:              # prior STORY- ids that must be done first (may be empty)
  - STORY-012
adrs:                    # ADR- ids that govern this story (may be empty)
  - ADR-0003
phase: PHASE_01_DOCUMENT_MODEL
owner: coder             # architect | coder | tester
estimate: M              # S | M | L
---
```

### front-matter key reference {#front-matter-key-reference}

| Key                   | Type   | Rule                                                                                                                                                                                                                                                  |
|-----------------------|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`                  | string | `STORY-NNN`, unique, matches the filename.                                                                                                                                                                                                            |
| `title`               | string | Imperative, one line.                                                                                                                                                                                                                                 |
| `status`              | enum   | `draft`\|`ready`\|`in-progress`\|`done`\|`superseded`. See `#lifecycle`.                                                                                                                                                                              |
| `spec_clauses`        | list   | Each entry is a citable clause — a `docs/specification/...#anchor` path or a requirement id (`FR-…`, `NFR-…`, `DD-…`) resolvable via `docs/specification/00_Foundation/05_SPEC_INDEX.md`. Must resolve, or `traceCheck` fails. At least one required. |
| `modules`             | list   | Each entry must exist in `docs/implementation_plan/01_MODULE_INVENTORY.md`. At least one required.                                                                                                                                                    |
| `acceptance_criteria` | list   | Every `STORY-NNN-AC-N` id that appears as a body heading. Must match the body exactly.                                                                                                                                                                |
| `edge_cases`          | list   | `EC-<AREA>-N` ids; each must be covered by a proving test. May be empty.                                                                                                                                                                              |
| `depends_on`          | list   | `STORY-` ids; must form an acyclic graph (`traceCheck` enforces). May be empty.                                                                                                                                                                       |
| `adrs`                | list   | `ADR-NNNN` ids; must exist under `docs/adr/`. May be empty.                                                                                                                                                                                           |
| `phase`               | string | The `PHASE_NN_SLUG` this story belongs to; must resolve to a file `docs/implementation_plan/phases/<phase>.md` or `traceCheck` fails (check #13, `docs/implementation_plan/03_TRACEABILITY.md#check-table`).                                          |
| `owner`               | enum   | `architect`\|`coder`\|`tester` — who primarily executes it.                                                                                                                                                                                           |
| `estimate`            | enum   | `S`\|`M`\|`L`. See `#sizing`.                                                                                                                                                                                                                         |

## body-order {#body-order}

After the front-matter, the body sections appear in exactly this order:

1. **`# STORY-NNN — <title>`** — heading.
2. **`## Goal`** — one paragraph: the outcome this story delivers and why.
3. **`## In scope`** — bulleted list of what this story does.
4. **`## Out of scope`** — bulleted list of what it deliberately does not do (points at other stories/phases where
   relevant).
5. **`## Spec inputs`** — the cited spec clauses (mirrors `spec_clauses`) with a one-line note on what each contributes.
6. **`## Design constraints`** — the invariants and ADRs that bound the implementation (FX-free core, `Result`/
   `AppError`, records-first, offline, etc., as applicable), plus any cited `ADR-NNNN`.
7. **`## Acceptance criteria`** — each criterion as a `### STORY-NNN-AC-N` sub-heading, written to a pattern from
   `docs/implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md`. One value per AC; no implementation detail.
8. **`## Test plan`** — a table mapping each AC (and each edge case) to: tier · test file · test method ·
   `Proves: STORY-NNN-AC-N`. See `docs/implementation_plan/03_TRACEABILITY.md`.
9. **`## Definition of done`** — the per-story DoD checklist from `docs/implementation_plan/06_DEFINITION_OF_DONE.md`
   (may reference it rather than restate every line, but must confirm the story-specific items: modules updated, edge
   cases covered, UI visual reference matched where applicable).

### acceptance-criteria-heading-rule {#acceptance-criteria-heading-rule}

Each acceptance criterion is a level-3 heading `### STORY-NNN-AC-N` where `N` starts at 1. The set of AC headings must
equal the `acceptance_criteria` front-matter list. Each AC has at least one proving test in the Test plan whose first
line is `// Proves: STORY-NNN-AC-N`.

In addition, every story carries one **implicit acceptance criterion** that is never listed in `acceptance_criteria`:
the whole-project clean gate — `./gradlew clean build check spotlessCheck` green across the entire project, with no
"pre-existing" exemption (`docs/implementation_plan/06_DEFINITION_OF_DONE.md#per-story-checklist`).

## sizing {#sizing}

One story = one session. Keep stories small enough to plan, implement, test, and trace in a single focused pass.

| Size  | Bound                                                                                                                                                                                          |
|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **S** | 1–2 acceptance criteria; a single module package; no new module; no new ADR; a few hours of focused work.                                                                                      |
| **M** | 3–5 acceptance criteria; up to a few packages within one or two modules; possibly one ADR; the default size.                                                                                   |
| **L** | 6–8 acceptance criteria; spans multiple modules or introduces a new module/seam; likely an ADR. **An L that would exceed ~8 ACs must be split** into multiple stories with `depends_on` edges. |

If a story cannot be expressed within `L`, it is too big — split it during `/plan-phase-stories-creation`.

## lifecycle {#lifecycle}

```
draft ──► ready ──► in-progress ──► done
                                     │
   (any state) ───────────────────► superseded
```

| Status        | Meaning                                                                                                   | Entry condition                             |
|---------------|-----------------------------------------------------------------------------------------------------------|---------------------------------------------|
| `draft`       | Written by the architect, not yet validated for readiness.                                                | Story file created.                         |
| `ready`       | Reviewed; clauses resolve, modules exist, ACs are patterned, `depends_on` satisfiable. Eligible to start. | Passes readiness review.                    |
| `in-progress` | Being implemented in the current session.                                                                 | `/plan-user-story-implementation` approved. |
| `done`        | All ACs and edge cases proven; DoD satisfied; `traceCheck` green. **Immutable.**                          | Definition of Done passes.                  |
| `superseded`  | Replaced by a later story; kept for history.                                                              | A newer story supersedes it.                |

**`done` is immutable.** A done story is never edited. If its behaviour must change, write a new story that references
and `supersedes` it; the old story moves to `superseded`. Traceability keeps both, but only the live story proves the
current clauses. Exactly one non-superseded story proves any given acceptance chain at a time.

## example-skeleton {#example-skeleton}

```markdown
---
id: STORY-001
title: Detect real source language ignoring declared metadata
status: draft
spec_clauses: [FR-IMPORT-03, docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection]
modules: [:document/ua.bookloom.document.detect, :util/ua.bookloom.util.lang]
acceptance_criteria: [STORY-001-AC-1, STORY-001-AC-2]
edge_cases: [EC-LANG-1]
depends_on: []
adrs: []
phase: PHASE_01_DOCUMENT_MODEL
owner: coder
estimate: M
---

# STORY-001 — Detect real source language ignoring declared metadata

## Goal
...

## In scope
- ...

## Out of scope
- ...

## Spec inputs
- FR-IMPORT-03 — detect real language from content, surface mismatch.

## Design constraints
- FX-free core; results returned as `Result<LanguageDetection>` with typed `AppError`.

## Acceptance criteria
### STORY-001-AC-1
Given ... When ... Then ...
### STORY-001-AC-2
...

## Test plan
| AC / EC | Tier | Test file | Method | Proves |
|---|---|---|---|---|
| STORY-001-AC-1 | Unit | `:document/src/test/java/ua/bookloom/document/detect/LanguageDetectorTest.java` | `detectsContentLanguageOverMetadata` | STORY-001-AC-1 |
| EC-LANG-1 | Unit | same | `flagsMismatchWhenMetadataDisagrees` | STORY-001-AC-1 |

## Definition of done
See `docs/implementation_plan/06_DEFINITION_OF_DONE.md`. Story-specific: EC-LANG-1 covered; no module inventory change.
```
