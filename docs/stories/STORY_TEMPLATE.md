---
id: STORY-NNN
title: <imperative sentence, no trailing period>
status: draft            # draft | ready | in-progress | done | superseded
spec_clauses:
  - <spec-file>#<anchor>            # e.g. docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import
modules:
  - <module path from docs/implementation_plan/01_MODULE_INVENTORY.md>   # e.g. :document/ua.bookloom.document.epub
acceptance_criteria:
  - STORY-NNN-AC-1
edge_cases: [ ]           # optional; EC-AREA-N (e.g. EC-EPUB-1)
depends_on: [ ]           # story ids; must all be done before this story is ready
adrs: [ ]                 # accepted ADR ids (e.g. ADR-0003)
phase: PHASE_NN_SLUG     # e.g. PHASE_01_DOCUMENT_MODEL — must resolve to docs/implementation_plan/phases/<phase>.md (traceCheck check #13)
owner: coder             # architect | coder | tester
estimate: S              # S | M | L
---

# STORY-NNN — <title>

## Goal

<One paragraph, user-visible/behavioural. No implementation detail.>

## In scope

- <what this story creates/changes>

## Out of scope

- <adjacent work; name the owning story id where one exists>

## Spec inputs

- <clause> — <what to take from it>

## Design constraints

- <layering / Result-AppError envelope / FX-free core / adapter-port / token-only theming / offline invariant /
  skeleton-not-regenerated / credentials-as-reference / single-flight gate; cite the DD-NN and ADR-NNNN ids>

## Acceptance criteria

### STORY-NNN-AC-1

<criterion, independently verifiable, phrased per docs/implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md (P1–P6)>

## Test plan

- STORY-NNN-AC-1 → <tier: unit|integration|ui|architecture> · <test file path> · <test method> ·
  `Proves: STORY-NNN-AC-1`

## Definition of done

- [ ] Every AC has a passing test whose first line names it (`// Proves: STORY-NNN-AC-N`).
- [ ] Every edge case has a passing test.
- [ ] Spotless / Checkstyle / Error Prone+NullAway / SpotBugs clean for touched code; `./gradlew test` green; ArchUnit
  boundary tests green.
- [ ] `./gradlew traceCheck` passes with zero orphans and a fresh record; `01_MODULE_INVENTORY.md` updated if modules
  changed; `phase` resolves to a file under `docs/implementation_plan/phases/` (traceCheck check #13).
- [ ] UI stories match the visual reference in `docs/specification/mockups/ui-mockup.html`.
- [ ] No new background/unsolicited network calls; the only outbound traffic is user-triggered provider communication —
  inference, model discovery, verification (offline invariant).
