# ADR-0029 — Transcode the whole output document to UTF-8 when the source charset cannot represent the target text

**Status:** accepted **Date:** 2026-08-11
**Deciders:** architect
**Supersedes:** none

## Context and problem statement

`fix-document-round-trip-corpus-defects`'s verification harness sets every segment's target to a marker plus its own
source text and writes the book back, which exercises the one case no fixture had: **target text the source's
resolved charset cannot encode**. It found that the four writers do three different things, two of them silently.

| format | behaviour today | where |
|---|---|---|
| TXT | **refuses** — `CorruptContainerException` → `ErrorCode.validation` | `TxtWriter.encode` |
| FB2 | **switches the whole document to UTF-8** and rewrites the XML declaration | `Fb2Writer.serialize` |
| Markdown | **silently substitutes `?`** and returns success | `MarkdownWriter` |
| EPUB | **silently substitutes `?`** — `tree.outerHtml().getBytes(charset)` maps unmappable characters | `EpubWriter` |

Adding TXT's guard to the other three would cement one of three competing behaviours with no requirement behind it,
and would leave EPUB and Markdown corrupting books quietly in the meantime. The evidence and measurements are in
`docs/implementation_plan/notes-corpus-verification.md`.

This is latent only while `:pipeline` is a stub. It stops being latent on the first real run, and not rarely: a
`windows-1252` source translated into Ukrainian has **no** representable segment, so every segment of every such
book hits it at once. The frozen spec already answers the question for exactly one format — FR-DOC-FB2-3 and EC-FB2-1
mandate the UTF-8 switch for FB2 — and is silent for the other three. Silence across three formats that share one
failure mode is a genuine gap, so it is an ADR rather than a spec edit
(`docs/implementation_plan/04_ADR_FORMAT.md#when-to-write-one`).

## Decision drivers

- **A book must never be lost.** Refusing an export after a whole book has been translated destroys hours of local
  inference over a container-level detail the user cannot influence.
- **A book must never be silently damaged.** Substituting `?` is the worst outcome available: it succeeds, it looks
  correct, and the damage is discovered by a reader.
- **One policy, four formats.** Three behaviours for one condition is how the defect stayed invisible; whatever is
  chosen has to be uniform, with a scenario per format.
- **The frozen spec has already answered it once.** FB2's rule was written deliberately and is the only one of the
  four with a requirement behind it.
- **The golden round trip must stay meaningful.** Whatever is chosen must leave the no-edit case — which produces no
  target text at all — byte-for-byte unchanged.

## Considered options

- **A — Transcode the output document to UTF-8 and re-declare the encoding**, uniformly, whenever any target
  character is unrepresentable in the resolved charset.
- **B — Refuse the export** with `ErrorCode.validation`, generalising `TxtWriter`'s guard.
- **C — Mask the unrepresentable characters**, carrying them through as placeholders.
- **D — Substitute a replacement character**, generalising the Markdown/EPUB behaviour.

## Decision outcome

Chosen: **A — transcode to UTF-8**, because it is the only option that neither loses the book nor damages it, and
because it generalises the one behaviour the frozen spec already mandates rather than inventing a fourth.

Concretely, on export, for every format:

1. The writer attempts to encode the assembled output in the charset resolved at import (`#encoding-and-bom`).
2. If **every** character is representable, nothing changes — the declared encoding is preserved exactly as today.
3. If **any** character is not, the **whole output document** is encoded as UTF-8 instead, and the encoding is
   re-declared in the form the format carries it:
   - **FB2** — rewrite the XML declaration's `encoding` (already implemented; unchanged).
   - **EPUB** — rewrite each affected content document's XML declaration and any `<meta charset>`/
     `http-equiv="Content-Type"` it carries. The OCF container is UTF-8 by specification, so only content documents
     are in scope.
   - **Markdown** and **TXT** — no in-band declaration exists; the bytes become UTF-8 and the `Document`'s recorded
     charset is updated so a re-import resolves it correctly.
4. **A byte-order mark is preserved as a property, not as bytes.** If the source carried a BOM, the UTF-8 output
   carries a UTF-8 BOM; if it did not, none is added. Editors and readers that keyed on the source's BOM keep working.
5. The decision is **per document**, never per character or per segment — a document is never half one encoding and
   half another.

### Consequences

- Positive: no book is ever refused at export, and no book is ever silently damaged. The two outcomes that actually
  matter are both removed.
- Positive: one rule, four formats, one requirement with a scenario per format. FB2 needs no code change; TXT, Markdown
  and EPUB converge onto what it already does.
- Positive: UTF-8 is what the target language usually needs. A `windows-1251` FB2 translated into Ukrainian is
  representable, but a `windows-1252` source is not, and transcoding is the only outcome that produces a readable book.
- Negative: **the output declares an encoding the source did not.** For a reader or toolchain that hard-codes the
  source's charset instead of honouring the declaration, the exported book is mojibake. This is the real cost, and it
  is accepted because the alternative is `?` where the text should be.
- Negative: **the golden round trip needs a stated carve-out.** `#round-trip-golden-requirement` asserts TXT
  byte-for-byte and FB2 canonical-XML against the source. A transcoded export is neither. The carve-out is narrow and
  already has precedent: EC-FB2-1 excludes an encoding-switched fixture from the source-language golden and asserts it
  against a re-parsed canonical tree instead, and the same treatment extends to the other three formats.
- Neutral: the **no-edit** golden is untouched. With no target text there is nothing unrepresentable, so step 2 always
  applies and every existing golden assertion — including TXT's byte-exactness — holds unchanged.
- Neutral: `Document` gains a way to report the charset actually written. Nothing consumes it yet; the export screen
  (change 23) is where a user would be told.

### Deviations from the frozen specification

| Clause | As frozen | As decided here |
|---|---|---|
| `01_Product/03_DOCUMENT_FORMATS.md#txt` FR-DOC-TXT-1 | plain text preserves "line endings and encoding" | encoding is preserved **unless** a target character is unrepresentable, in which case the document is written UTF-8 |
| `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom` | the detected charset and BOM presence are "reproduced verbatim on export" | reproduced verbatim unless a target character is unrepresentable; BOM **presence** is reproduced, its bytes become UTF-8's |
| `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement` | TXT compared byte-for-byte; FB2 canonical-XML against the source | unchanged for the no-edit golden; an **encoding-switched** fixture in any format is asserted against a re-parsed canonical form, extending EC-FB2-1's existing carve-out to all four |
| `02_Architecture/03_DOCUMENT_MODEL.md#repackaging` | "encoding … preserved" among the canonical-equal guarantees | preserved except under this rule, which is the one intentional deviation alongside the language-metadata update |

FR-DOC-FB2-3 and EC-FB2-1 are **not** deviations — this generalises them.

## Pros and cons of the options

### Option A — Transcode to UTF-8

- Good: never loses a book, never damages one; the only option true of both.
- Good: already specified and implemented for FB2, so it is a generalisation rather than an invention.
- Good: trivially testable — one scenario per format with a source charset that cannot hold the target text.
- Bad: the output declares an encoding the source did not, which a charset-hard-coding reader will get wrong.
- Bad: needs the golden carve-out above, so the fidelity claim becomes "canonical-equal, modulo language metadata
  **and** an encoding switch" rather than the simpler sentence it is today.

### Option B — Refuse the export

- Good: absolutely faithful to the source container; nothing is ever declared that was not there.
- Good: no golden carve-out; the fidelity claim stays one sentence.
- Bad: **the failure arrives after the work.** The user learns their book cannot be exported only once a whole book
  has been translated, and there is no action they can take — the charset is a property of the file they imported.
- Bad: for a `windows-1252` source translated into Ukrainian this refuses **every** book, which is not a rare edge.

### Option C — Mask the unrepresentable characters

- Good: reuses the placeholder mechanism change 5 introduces.
- Bad: solves nothing. A placeholder must be restored before the bytes are written, and restoring it reintroduces
  exactly the character the charset cannot hold. It relocates the failure rather than removing it.

### Option D — Substitute a replacement character

- Good: no code change for EPUB or Markdown; it is what they do today.
- Bad: **silent corruption**, which is the defect being fixed. It reports success and hands back a damaged book.
- Bad: for the common case it destroys the entire translation — every Cyrillic character in a `windows-1252` container
  becomes `?`.

## Links

- Design decisions: DD-43 (canonical-equal round trip), DD-30 (same-format export)
- Spec clauses: `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#fb2` (FR-DOC-FB2-3, EC-FB2-1),
  `#txt` (FR-DOC-TXT-1, FR-DOC-TXT-3), `#encoding-and-bom`, `#round-trip-golden-requirement`,
  `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#repackaging`
- Evidence: `docs/implementation_plan/notes-corpus-verification.md` (the four-writer measurement table)
- Implemented by: the `settle-writer-policy-and-document-lifetime` change (backlog item D1)
- Related: ADR-0030 (the other half of the writer's acceptance policy)
