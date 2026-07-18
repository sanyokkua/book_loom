# ADR-0004 — Scope supported formats to EPUB, FB2, Markdown, and TXT; exclude PDF and DOCX

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The application translates whole books and guarantees a structure-and-text-preserving (canonical-equal) round-trip: the
output is structurally identical to the input except for the translated text, though exact bytes may differ under
re-serialization (DD-43). That guarantee rests entirely on the ability to cleanly separate translatable text from
immutable structure and to write the text back into its exact origin node (see
`docs/adr/ADR-0003-document-skeleton-segment-model.md`).

Which input formats the product accepts is therefore not a UI convenience decision but a correctness decision: a format
that cannot support a clean text/structure separation cannot honour the round-trip guarantee. This ADR fixes the
supported-format scope.

## Decision drivers

- **Clean text/structure separation.** A format qualifies only if translatable text can be extracted and re-inserted
  without disturbing structure.
- **Canonical round-trip feasibility.** The format must round-trip through parse-and-reassemble with structure and text
  fully preserved (DD-43).
- **Reflowable, semantic content.** Books are reflowable prose, not fixed-page layouts.
- **Licensing and dependency hygiene.** Parsing libraries must fit the Apache-2.0/MIT/BSD-only license gate.
- **Scope discipline.** A small, high-fidelity format set beats a large set with weak guarantees on some formats.

## Considered options

- **Option A — EPUB, FB2, Markdown, TXT only** (exclude PDF and DOCX).
- **Option B — The above plus DOCX.**
- **Option C — The above plus PDF.**

## Decision outcome

Chosen: **Option A — support EPUB, FB2, Markdown, and TXT only**, and explicitly exclude PDF and DOCX. These four
formats are reflowable and expose a clean separation between semantic text and structure: EPUB and FB2 are XML-based and
round-trip through the skeleton+segment model with structure, IDs, images, fonts, and encoding preserved; Markdown
parses to an AST whose text nodes are cleanly addressable; TXT is text by definition. All four honour the
canonical-equal round-trip guarantee (TXT byte-exact).

PDF and DOCX are excluded as structure-hostile for this guarantee. PDF is a fixed-page layout format built around
positioned glyphs and lines, not reflowable semantic paragraphs; extracting translatable text and re-inserting
differently sized translated text without destroying layout is an unsolved-in-general problem, and the mature tooling
carries licensing weight. DOCX is a rich, deeply nested OOXML package whose faithful round-trip (styles, revision marks,
fields, numbering, embedded objects) is disproportionately hard to guarantee and where the practical parsing libraries
and their license posture add cost far exceeding the value for a book-translation tool.

**Export clause (DD-30).** Export is the inverse of import and is **format-preserving only**: a book is always written
back in its **original format** (EPUB→EPUB, FB2→FB2, Markdown→Markdown, TXT→TXT), structure-and-text-preserving via the
skeleton (canonical-equal, DD-43). **Converting between formats is out of scope** — it is inherently lossy for structure
and metadata, and the export path deliberately offers no target-format choice. The only export contract to verify is the
same-format round trip; there are no conversion code paths.

### Consequences

Positive:

- Every supported format can honour the canonical-equal round-trip; there are no second-class formats with weaker
  guarantees.
- The document module stays focused on a small set of parsers that each fully satisfy the invariant.
- Dependency and license surface stays small and within the Apache-2.0/MIT/BSD gate.
- Import can cleanly reject unsupported files with a clear, testable message.

Negative:

- Users with PDF or DOCX source books must convert them externally first; the product does not help with that
  conversion.
- The addressable market is narrower than a tool that nominally accepts more formats.

Neutral:

- PDF/DOCX remain candidates for a future, separately scoped effort if a faithful round-trip strategy is found;
  excluding them now is a scope decision, not a permanent ban.
- Import must detect and refuse unsupported and corrupt files explicitly (see
  `docs/specification/01_Product/03_DOCUMENT_FORMATS.md`).

## Pros and cons of the options

### Option A — EPUB / FB2 / MD / TXT only

Pros:

- All four formats support clean text/structure separation and a canonical-equal round-trip.
- Small, well-understood parser and dependency set within the license gate.
- No format with a weaker fidelity guarantee than the others.

Cons:

- Excludes two common real-world book formats (PDF, DOCX), pushing conversion onto the user.

### Option B — add DOCX

Pros:

- Accepts a very common authoring/exchange format directly.

Cons:

- OOXML is deeply nested; faithful round-trip of styles, fields, revisions, numbering, and embedded objects is hard to
  guarantee and easy to regress.
- Heavier parsing dependencies with a less favourable license posture relative to the gate.
- Effort is disproportionate to the value for translating reflowable prose.

### Option C — add PDF

Pros:

- Accepts the most widely distributed document format.

Cons:

- Fixed-page, glyph-positioned layout has no reliable reflowable text/structure separation; re-inserting differently
  sized translated text breaks layout.
- Even a canonical-equal round-trip is effectively impossible for arbitrary PDFs.
- Mature PDF tooling adds licensing and dependency weight and still cannot honour the core guarantee.

## Links

- Design decisions: DD-08 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-08-scope-formats`)
- Spec clauses: `docs/specification/01_Product/03_DOCUMENT_FORMATS.md`,
  `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
  `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`, `docs/specification/05_Dependencies/03_LICENSING.md`
- Stories: none yet
