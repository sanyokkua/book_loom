---
phase: PHASE_02_FB2_MD_TXT
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#fb2
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#fb2-edge-cases
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#markdown
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#txt
  - FR-DOC-01
  - FR-DOC-06
  - FR-DOC-07
  - FR-DOC-09
  - FR-DOC-10
  - FR-IMPORT-01
  - FR-IMPORT-05
  - DD-43
  - DD-49
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`, `docs/specification/01_Product/03_DOCUMENT_FORMATS.md`,
`docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`, `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`

# PHASE_02 — FB2, Markdown, TXT

## Goal

Extend the skeleton+segment model to the remaining three formats — FB2 (and `.fb2.zip`), Markdown, and TXT — each with a
passing round-trip golden test. After this phase, all four supported formats parse to the same model and reassemble with
a structure-and-text-preserving (canonical-equal) round trip — exact bytes may differ under re-serialization, except
TXT, which reassembles exactly byte-for-byte (DD-43) — so the pipeline and export never need format-specific branches
beyond the parser/reassembler.

## In scope

- FB2 read/write via JDOM2 preserving declared encoding, comments/CDATA/entities, and `.text`/tail semantics; `.fb2.zip`
  container handling; update FB2 language metadata on export.
- Markdown parse/render via a CommonMark AST; protect code and technical content per DD-49 — inline code spans are
  atomic protected placeholders, fenced/indented code blocks are non-translatable blocks excluded from segmentation —
  plus URLs and frontmatter keys, while translating prose and frontmatter values as configured.
- TXT paragraph model (blank-line separated) with **exact byte-for-byte** reassembly (the TXT exception to
  canonical-equal, DD-43).
- Round-trip golden tests for FB2, Markdown, and TXT reusing the PHASE_01 harness.

## Out of scope

- Inline masking of arbitrary inline tags/locked terms (PHASE_03 — this phase protects only format-structural spans like
  MD code/URLs/frontmatter keys).
- Translation, persistence, provider, or UI logic.
- EPUB (done in PHASE_01).

## Dependencies

PHASE_01 (skeleton+segment model, `DocumentPort`, round-trip golden harness).

## Forward-compatibility

- **Consumes and extends F1** — every new format populates the same skeleton/segment model with text nodes as the only
  mutable slots.
- Completes format coverage so PHASE_03 masking and PHASE_06 pipeline are format-agnostic.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                                                                         | Target modules                       | Cited spec clauses                                                                                                                                 |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| FB2 read to skeleton+segments (JDOM2, encoding/entities/CDATA preserved)                                                                                               | `:document/ua.bookloom.document.fb2` | FR-IMPORT-01, FR-DOC-01, `01_Product/03_DOCUMENT_FORMATS.md#fb2`                                                                                   |
| `.fb2.zip` container read/write                                                                                                                                        | `:document/ua.bookloom.document.fb2` | FR-IMPORT-01, EC-FB2-*                                                                                                                             |
| FB2 reassemble preserving declared encoding; update FB2 lang on export                                                                                                 | `:document/ua.bookloom.document.fb2` | FR-DOC-06, FR-DOC-07                                                                                                                               |
| FB2 round-trip golden test                                                                                                                                             | `:document/ua.bookloom.document.fb2` | FR-DOC-09, `01_Product/03_DOCUMENT_FORMATS.md#fb2-edge-cases`                                                                                      |
| Markdown parse/render (CommonMark AST) to skeleton+segments                                                                                                            | `:document/ua.bookloom.document.md`  | FR-IMPORT-01, FR-DOC-01, `01_Product/03_DOCUMENT_FORMATS.md#markdown`                                                                              |
| Protect MD code (inline spans as atomic placeholders; fenced/indented blocks as non-translatable blocks), URLs, frontmatter keys; translate prose + frontmatter values | `:document/ua.bookloom.document.md`  | FR-DOC-10, DD-49, `01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`, `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, EC-MD-* |
| Markdown round-trip golden test                                                                                                                                        | `:document/ua.bookloom.document.md`  | FR-DOC-09                                                                                                                                          |
| TXT paragraph parse/render + round-trip golden test                                                                                                                    | `:document/ua.bookloom.document.txt` | FR-IMPORT-01, FR-DOC-09, `01_Product/03_DOCUMENT_FORMATS.md#txt`                                                                                   |
| Reject unsupported/corrupt files with a clear reason and no partial import                                                                                             | `:document/ua.bookloom.document`     | FR-IMPORT-05, EC-EPUB-*, EC-FB2-*                                                                                                                  |

## Phase exit checklist

- [ ] FB2, `.fb2.zip`, Markdown, and TXT parse into the shared skeleton+segment model.
- [ ] Each format has a passing round-trip golden test (canonical-equal, canonicalized compare modulo language metadata;
  TXT exact byte-for-byte — DD-43).
- [ ] FB2 preserves declared encoding, comments/CDATA/entities; Markdown protects code per DD-49 (inline spans atomic,
  fenced/indented blocks excluded from segmentation) plus URLs/frontmatter keys.
- [ ] Language metadata updated on export for FB2 and (where applicable) Markdown frontmatter.
- [ ] Unsupported/corrupt files rejected with a typed error and no partial import.
- [ ] FX-free (ArchUnit green); `./gradlew test` and `./gradlew traceCheck` green; module inventory updated.
