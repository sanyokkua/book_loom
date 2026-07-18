---
phase: PHASE_01_DOCUMENT_MODEL
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#epub
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement
  - docs/specification/01_Product/03_DOCUMENT_FORMATS.md#verse-tables-notes
  - docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#data-model
  - FR-DOC-01
  - FR-DOC-02
  - FR-DOC-03
  - FR-DOC-06
  - FR-DOC-07
  - FR-DOC-08
  - FR-DOC-09
  - FR-IMPORT-01
  - FR-IMPORT-06
  - FR-IMPORT-07
  - FR-IMPORT-08
  - DD-07
  - DD-43
  - DD-49
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`, `docs/specification/01_Product/03_DOCUMENT_FORMATS.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`

# PHASE_01 — Document Model

## Goal

Introduce the skeleton+segment document model and prove it on EPUB. A book becomes an immutable skeleton plus an
ordered, individually-addressable list of translatable segments; text nodes are the only mutable slots. Reassembly reads
target text back into the same nodes and, when no target changed, reproduces a structure-and-text-preserving
(canonical-equal) document — canonicalized output equals canonicalized source; exact bytes may differ under
re-serialization (DD-43). This establishes seam F1 and the round-trip golden discipline that every format phase depends
on.

## In scope

- The skeleton + ordered segment model (`Unit`, `Segment`, skeleton structures) in `:api` and `:document` (seam F1).
- EPUB 2/3 read: unzip container, parse OPF/spine, parse XHTML bodies (jsoup), build skeleton + segments; extract
  cover/metadata.
- Non-translatable blocks (DD-49): `<pre>`/`<pre><code>` code listings and block-level MathML `<math>` yield **no
  segments** — they are excluded from segmentation and preserved via the skeleton only.
- EPUB write: read target text back into the same nodes; repackage mimetype-first (stored uncompressed); update
  `dc:language` on export.
- Content hashing of the imported source (`source_hash`) for change detection/resume.
- The per-format **round-trip golden test**: reassembly without target changes is canonical-equal to the source —
  canonicalized compare, not raw bytes (modulo intentional language metadata; DD-43).
- `DocumentPort` contract in `:api` implemented by `DocumentService`.

## Out of scope

- FB2/Markdown/TXT (PHASE_02).
- Inline masking of tags/locked-terms/URLs/numbers (PHASE_03).
- DRM and language detection surfacing in UI (detection logic may be stubbed here; UI in PHASE_09).
- Any translation, persistence, or provider logic.

## Dependencies

PHASE_00 (module skeleton, `Result`/`AppError`, build/lint/trace tooling).

## Forward-compatibility

- **Establishes F1** — the skeleton/segment seam: immutable skeleton, ordered addressable segments, text nodes the only
  mutable slots, reassembly reads target back into the same nodes. Consumed by masking (PHASE_03), pipeline (PHASE_06),
  export (PHASE_10).
- Establishes the round-trip golden-test harness reused by PHASE_02 and PHASE_03.
- Consumes F2 (Result/AppError) for all `DocumentPort` results.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                           | Target modules                                                                | Cited spec clauses                                                                             |
|--------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| Define skeleton + segment model records and `DocumentPort`                                                               | `:api/ua.bookloom.api.document`, `:document/ua.bookloom.document.model`       | FR-DOC-01, `02_Architecture/03_DOCUMENT_MODEL.md#data-model`, DD-07                            |
| EPUB container read (unzip, OPF/spine) and XHTML body parse to skeleton+segments                                         | `:document/ua.bookloom.document.epub`                                         | FR-IMPORT-01, FR-DOC-01, `01_Product/03_DOCUMENT_FORMATS.md#epub`                              |
| Extract cover image + title/author metadata                                                                              | `:document/ua.bookloom.document.epub`                                         | FR-IMPORT-06, FR-IMPORT-07                                                                     |
| EPUB reassemble + repackage mimetype-first (stored uncompressed)                                                         | `:document/ua.bookloom.document.epub`                                         | FR-DOC-06, `01_Product/03_DOCUMENT_FORMATS.md#epub`, EC-EPUB-*                                 |
| Update `dc:language` to target on export                                                                                 | `:document/ua.bookloom.document.epub`                                         | FR-DOC-07                                                                                      |
| Never regenerate skeleton — only text nodes change (assert structural identity)                                          | `:document/ua.bookloom.document.model`                                        | FR-DOC-03, DD-07                                                                               |
| EPUB round-trip golden test (canonical-equal modulo language metadata, DD-43)                                            | `:document/ua.bookloom.document.epub`                                         | FR-DOC-02, FR-DOC-09, DD-43, `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement` |
| Exclude `<pre>`/`<pre><code>` listings and block-level MathML from segmentation (non-translatable blocks, skeleton-only) | `:document/ua.bookloom.document.epub`, `:document/ua.bookloom.document.model` | DD-49, `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, EC-CODE-*               |
| Content hash of imported source for resume/change detection                                                              | `:document/ua.bookloom.document`, `:util/ua.bookloom.util.hash`               | FR-IMPORT-08                                                                                   |
| Preserve/place verse, tables, footnotes, scene-break markers in the model                                                | `:document/ua.bookloom.document.model`, `:document/ua.bookloom.document.epub` | FR-DOC-08, `01_Product/03_DOCUMENT_FORMATS.md#verse-tables-notes`, EC-VERSE-*                  |

## Phase exit checklist

- [ ] Skeleton+segment model exists in `:api`/`:document`; segments are ordered and individually addressable (F1
  established).
- [ ] EPUB 2 and 3 parse into skeleton + segments; cover/metadata extracted when present.
- [ ] EPUB reassembles mimetype-first with structure/IDs/images/fonts preserved (structure-and-text-preserving,
  canonical-equal — DD-43).
- [ ] The EPUB round-trip golden test passes: no-change reassembly is canonical-equal to the source (canonicalized
  compare, modulo `dc:language`; DD-43).
- [ ] `<pre>`/`<pre><code>` listings and block-level MathML yield zero segments and pass through skeleton-only (DD-49).
- [ ] The skeleton is never regenerated; a test asserts only text nodes change.
- [ ] `source_hash` computed and stored for the imported source.
- [ ] `DocumentPort` implemented and returns `Result`/`AppError`; FX-free (ArchUnit green).
- [ ] `./gradlew test` and `./gradlew traceCheck` green; module inventory updated.
