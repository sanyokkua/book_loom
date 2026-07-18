# ADR-0003 — Represent documents as an immutable skeleton plus an extracted segment list

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The application translates whole books while keeping everything that is not prose — structure, images, fonts, IDs,
styles, encoding, container layout — intact across a structure-and-text-preserving (canonical-equal) round trip (DD-43).
A translated EPUB or FB2 must remain a valid, structurally identical file whose only difference from the source is the
visible text. Inline markup (emphasis, links, footnote references) sits inside the very text that gets translated and
must survive re-insertion in the right places.

The central architectural question is how to model a parsed document so that translation touches only the text and
nothing else can drift. This ADR fixes that model.

## Decision drivers

- **Byte-faithful round-trip.** A load-then-save with no translation must reproduce the source, preserving comments,
  CDATA, entities, encoding, and node tail semantics.
- **Translate text only.** The model must expose exactly the translatable text and nothing structural.
- **Inline markup safety.** Tags and locked terms inside a text run must be masked out of the model's view and restored
  exactly.
- **Correct-by-construction fidelity.** Fidelity should follow from the model's shape, not from careful reassembly logic
  that can regress.
- **Testability.** A single golden round-trip test must be able to guarantee the invariant per format.
- **Order and addressability.** Segments must carry stable IDs so translations map back to their exact origin node.

## Considered options

- **Option A — Flatten to Markdown.** Convert every input format to Markdown, translate the Markdown, and convert back.
- **Option B — Full model regeneration.** Parse into a rich in-memory document model and serialize the whole model back
  out after translation.
- **Option C — Skeleton + segment list (minimal-diff).** Parse into a skeleton (the original XML/AST tree, retained
  verbatim) plus an ordered list of extracted text segments; translate the segments; write each translation back into
  its original text node; never regenerate the skeleton.

## Decision outcome

Chosen: **Option C — skeleton + segment list**, because it makes structural fidelity correct by construction: the
skeleton is the original parsed tree kept verbatim and is never sent to the model and never regenerated. Translation
operates only on the ordered segment list, where each segment carries a stable ID pointing to its source text node.
Inline tags, locked terms, URLs, and numbers inside a segment are masked to `⟦gN⟧` placeholders before the text leaves
the module and are restored on the way back, with the tag multiset validated as a hard gate. Export writes each
translated string back into its exact DOM/AST node and repackages the container.

This invariant is protected by a mandatory golden round-trip test per format: parse a source file, reassemble with no
translation applied, and assert canonical equality — canonicalized output equals canonicalized source (re-parse-equal /
canonical-XML; TXT compares exact bytes); exact bytes of structured formats may differ under re-serialization (DD-43).
Because the skeleton is untouched, structure, images, fonts, IDs, encoding, and container layout cannot drift regardless
of what the model returns.

### Consequences

Positive:

- Structural fidelity is guaranteed by the model's shape, not by reassembly heuristics that could regress.
- The model surface exposes exactly the translatable text; nothing structural is translatable by accident.
- The skeleton is never transmitted, reducing token cost and eliminating a whole class of structure-mangling failures.
- One golden round-trip test per format locks the invariant into CI.
- Inline masking with multiset validation catches tag loss deterministically before a chunk is accepted.

Negative:

- Two coordinated representations (skeleton + segments) must be kept in sync via stable IDs; the mapping is a real piece
  of machinery to build and test.
- Each format needs a parser that both preserves the tree verbatim and extracts a faithful, correctly ordered segment
  list.
- Edge structures (verse, tables, footnotes, scene breaks) require deliberate segmentation rules so text is neither
  split badly nor merged across boundaries.

Neutral:

- The document module owns masking/unmasking and stays free of any translation or provider logic.
- Segment granularity is a tunable that the chunking stage builds on (see
  `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`).

## Pros and cons of the options

### Option A — Flatten to Markdown

Pros:

- One simple internal representation to translate regardless of source format.
- Markdown is easy to chunk and reason about.

Cons:

- Lossy by nature: EPUB/FB2 structure, IDs, attributes, encoding, and container details do not survive a Markdown
  round-trip.
- Byte-faithful export becomes impossible; the invariant the product depends on is broken from the start.
- Inline and structural richness (footnotes, verse, tables, per-node IDs) is flattened away and cannot be reconstructed.

### Option B — Full model regeneration

Pros:

- A single rich model can represent every format uniformly.
- Serialization is centralized in one place.

Cons:

- Regenerating the whole document re-serializes structure the user never wanted changed; comment/CDATA/entity/encoding
  and node-tail nuances are easy to lose, so even canonical fidelity depends on the serializer handling every input
  correctly.
- Fidelity becomes a property of reassembly code, which can regress silently on unusual inputs.
- More surface area sent to and reasoned about by downstream stages than necessary.

### Option C — Skeleton + segment list (minimal-diff)

Pros:

- Fidelity is correct by construction; the untouched skeleton cannot drift.
- Only text is translatable; structure is out of the model's reach.
- Cheaper prompts (skeleton never sent) and a clean masking/validation gate.
- A single golden round-trip test guarantees the invariant per format.

Cons:

- Requires maintaining a skeleton-to-segment ID mapping and per-format extraction rules.
- More upfront parser work than a naive flatten-and-reconvert.

## Links

- Design decisions: DD-07 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-07-skeleton-segment-model`)
- Spec clauses: `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`,
  `docs/specification/01_Product/03_DOCUMENT_FORMATS.md`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`
- Stories: none yet
