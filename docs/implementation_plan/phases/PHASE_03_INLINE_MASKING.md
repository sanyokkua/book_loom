---
phase: PHASE_03_INLINE_MASKING
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules
  - docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop
  - FR-DOC-02
  - FR-DOC-04
  - FR-DOC-05
  - FR-GLOSS-03
  - FR-QA-04
  - DD-19
  - DD-43
  - DD-49
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`, `docs/specification/01_Product/03_DOCUMENT_FORMATS.md`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`

# PHASE_03 — Inline Masking

## Goal

Add inline masking so protected spans inside a segment — inline tags, locked terms, URLs, selected numerals
(standalone/typographic and inside locked terms; prose numerals stay translatable), inline code, and inline MathML
(DD-49) — are replaced with `⟦gN⟧` placeholders before translation and restored after, with a hard placeholder-multiset
validation gate. This lets the pipeline send only translatable text to the model while keeping inline structure intact
by construction (structure-and-text-preserving, canonical-equal — DD-43).

## In scope

- Masking: replace inline tags, locked terms, URLs, and selected numerals (standalone/typographic numerals and numerals
  inside locked terms — prose numerals stay translatable) in a segment's text with stable `⟦gN⟧` placeholders; record
  the mask map.
- Protected content (DD-49): inline `<code>` and inline MathML `<math>` mask as **single atomic placeholders** with
  their text kept exactly. Block-level code (`<pre>`/`<pre><code>`, fenced/indented blocks) and block-level math never
  reach the masking layer — they are non-translatable blocks already excluded from segmentation (PHASE_01/02).
- Unmasking: restore placeholders to their original spans after translation.
- The **placeholder-multiset validation** as a hard gate: the unmasked target must carry exactly the same placeholder
  multiset as the masked source, or the chunk fails.
- Reassembly through the masking layer so masked/unmasked round-trips remain canonical-equal (TXT: exact bytes) across
  all four formats (DD-43).

## Out of scope

- Deterministic QA checks beyond placeholder integrity (PHASE_06 QA gate consumes this).
- The name/term dictionary hard-lock via placeholder substitution (PHASE_07 builds on the masking primitive).
- Any model call — masking is validated with synthetic target strings, not live inference.

## Dependencies

PHASE_01 (skeleton+segment model). PHASE_02 for full format coverage of the masking round-trip.

## Forward-compatibility

- **Consumes F1** — masking operates on segment text nodes without touching the skeleton.
- Provides the placeholder primitive that PHASE_06's QA hard gate (FR-QA-04) and PHASE_07's glossary hard-lock
  (FR-GLOSS-03) build on.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                     | Target modules                        | Cited spec clauses                                                                          |
|----------------------------------------------------------------------------------------------------|---------------------------------------|---------------------------------------------------------------------------------------------|
| Mask inline tags/locked-terms/URLs/selected-numerals to `⟦gN⟧` placeholders; record mask map       | `:document/ua.bookloom.document.mask` | FR-DOC-04, `01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`, DD-19                  |
| Mask inline `<code>` and inline MathML as single atomic protected placeholders (text kept exactly) | `:document/ua.bookloom.document.mask` | FR-DOC-04, DD-49, `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, EC-CODE-* |
| Unmask placeholders back to original spans                                                         | `:document/ua.bookloom.document.mask` | FR-DOC-04                                                                                   |
| Placeholder-multiset validation as a hard gate on the unmasked target                              | `:document/ua.bookloom.document.mask` | FR-DOC-05, FR-QA-04, `01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`                    |
| Mask/unmask round-trip fidelity across all four formats                                            | `:document/ua.bookloom.document.mask` | FR-DOC-02, EC-INLINE-*                                                                      |
| Handle edge cases: nested/adjacent inline tags, numbers inside words, URLs in prose                | `:document/ua.bookloom.document.mask` | EC-INLINE-*, `01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`                       |

## Phase exit checklist

- [ ] Inline tags, locked terms, URLs, and selected numerals (standalone/typographic + inside locked terms) mask to
  stable `⟦gN⟧` placeholders and unmask exactly; prose numerals stay translatable.
- [ ] Inline `<code>` and inline MathML mask as single atomic placeholders restored verbatim; block code/math yields no
  segments (DD-49).
- [ ] Placeholder-multiset mismatch is a hard failure that cannot be accepted (FR-DOC-05 / FR-QA-04).
- [ ] Mask→translate (synthetic)→unmask→reassemble stays canonical-equal (TXT: exact bytes) across EPUB/FB2/MD/TXT
  (DD-43).
- [ ] Inline edge cases (`EC-INLINE-*`) covered by proving tests.
- [ ] FX-free (ArchUnit green); `./gradlew test` and `./gradlew traceCheck` green; module inventory updated.
