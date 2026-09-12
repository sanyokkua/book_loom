---
paths:
  - "modules/document/**"
---

# Document Round-Trip Fidelity

Scope: `:document` (`ua.bookloom.document..` — epub/fb2/md/txt/mask). Spec: `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md`, `docs/specification/01_Product/03_DOCUMENT_FORMATS.md`. FX-free.

## MUST

- **MUST** parse each book into a **skeleton + ordered segment list**, and **never regenerate the skeleton** — only text nodes change. The skeleton is never sent to the model. Reassembly writes translated text back into the original DOM nodes in place; export then re-serializes the parsed model **structure-and-text-preserving (canonical-equal)** — exact bytes may differ under jsoup/CommonMark/zip normalization (DD-43). — Rationale: structure/formatting is preserved by construction.
- **MUST** mask inline tags, locked terms, URLs, and **selective numerals** (standalone/typographic numerals and numerals inside locked glossary terms — prose numerals stay translatable so they inflect/localize) to `⟦gN⟧` placeholders before translation, and on return **validate the placeholder/tag multiset** as a hard gate before unmasking. A multiset mismatch is a `validation` failure, not a silent fix. — Rationale: the model must not drop, add, or reorder inline structure.
- **MUST** preserve code, math, and technical markup verbatim (DD-49): inline `<code>` and inline MathML `<math>` are masked as **single atomic protected placeholders** with their text kept exactly; block code (`<pre>`/`<pre><code>`, fenced/indented blocks) and block math are **non-translatable blocks — excluded from segmentation and from the token budget**, preserved via the skeleton only; index-term/cross-reference **anchor ids/targets are preserved** while the visible link text stays translatable. — Rationale: translating identifiers/keywords/math corrupts a technical book; naive masking would explode the placeholder multiset.
- **MUST** unmask only after the multiset check passes, restoring each `⟦gN⟧` to its exact original inline fragment. — Rationale: masking is reversible and lossless.
- **MUST** configure the XML parser (`SecureXml`) to preserve comments, CDATA, the encoding declaration and `.text`/tail semantics, and to **expand entity references** (from the book's own header or the bundled standard list — a DOCTYPE's external subset is answered from that list, never fetched; an externally-valued entity expands to nothing). Only the translatable text content is altered. — Rationale: a reader shows the character, so the character is what fidelity means; not expanding made the JDK parser report a reference and its expansion, doubling every non-breaking space (ADR-0032 era fix, 2026-09-12).
- **MUST** repackage EPUB **mimetype-first and STORED (uncompressed)** as the first zip entry (synthesized when the source had none, ADR-0030), preserving all IDs, images, fonts, and the manifest/spine, every other entry in its original order with its own compression method; **never change the book's unique-id** (it seeds font obfuscation). A spine item whose file is absent from the archive is **skipped with one warning**, never refused — refuse only when the container, the OPF or the spine declaration is missing, or no spine file exists at all. — Rationale: an EPUB with a re-compressed mimetype or altered unique-id is invalid / breaks obfuscated fonts; real books ship dangling itemrefs and every reader shows what exists.
- **MUST** preserve FB2 encoding (switching the whole file to UTF-8 only when a target character is unrepresentable, ADR-0029), the source's line-ending style and its binary sections; Markdown AST structure; and TXT content/encoding on their respective round-trips; update only the declared language metadata where required. — Rationale: each format round-trips faithfully.
- **MUST** gate **every** format with the **golden round-trip test**: a no-op translate (identity on text) must produce a **canonical-equal** artifact — the test compares **canonicalized** output to canonicalized source (re-parse-equal / canonical-XML equal; EPUB by decompressed canonical content + preserved entry order + mimetype-first/STORED, unchanged binary entries by decompressed bytes; **TXT exact bytes**), never raw bytes for the structured formats (DD-43). This test must pass before any format work is considered done. — Rationale: "opens identically, structure and text intact" is the real requirement; bit-identity is unachievable through jsoup/CommonMark/zip.

## SHOULD

- **SHOULD** handle verse, tables, footnotes, and scene-breaks as structure preserved in the skeleton, translating only their text leaves. — Rationale: layout-sensitive content stays intact.
- **SHOULD** model book metadata title/author, Markdown frontmatter values, image `alt`, and EPUB nav (EPUB3)/NCX (EPUB2) ToC labels as **metadata-unit segments** translated per the Book-Brief **"Also translate" toggle group**; nav/NCX is carved out of the "out-of-spine = verbatim" rule (DD-47). — Rationale: an untranslated ToC/metadata over a translated book is a visible defect.
- **SHOULD** carry a `source_hash` per segment so unchanged source is detectable on resume. — Rationale: supports checkpoint/resume without re-translating.

## Reject if

- The skeleton is semantically regenerated instead of having text nodes mutated in place (re-serializing the parsed model on export is expected; rebuilding/altering structure is not).
- Translated text is written back without the inline placeholder multiset validation passing.
- Placeholders are dropped, added, reordered, or silently "repaired" instead of failing the hard gate.
- EPUB is repackaged with a compressed or non-first mimetype entry, or the book unique-id is changed.
- Comments/CDATA/encoding/tail are lost on XML round-trip, or an entity reference is written back unexpanded or doubled.
- A format ships without a passing golden round-trip (canonical-equal no-op; TXT exact-byte) test.
- Inline `<code>`/inline MathML text is altered, a `<pre>`/block-code/block-math region becomes a segment or is counted in the token budget, or an index/cross-ref anchor id is rewritten (DD-49).
