**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:document`), QA **Last Updated:**
2026-09-27 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/00_Foundation/02_GLOSSARY.md`, `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`

# Document Formats

This document specifies per-format parsing, translation-vs-preservation rules, repackaging, and edge cases for the
supported formats. It realizes `FR-DOC-*`, `FR-IMPORT-*`, and `FR-EXPORT-*` and is implemented in the `:document`
module. The governing principle (DD-07, DD-43): parse to a skeleton plus ordered segments, translate the inner content
of block elements only, and reassemble so the result is **structure-and-text-preserving (canonical-equal)** to the
source — the skeleton is never semantically regenerated, only its text nodes change.

## export-same-format-only {#export-same-format-only}

Export always writes the book back in its **original format** — EPUB→EPUB, FB2→FB2, MD→MD, TXT→TXT (DD-30, and the
export clause of ADR-0004). The output is produced from the original skeleton, so it is
**structure-and-text-preserving (canonical-equal)** to the source container except for translated text nodes and the
intentional language-metadata update (FR-DOC-09). "Canonical-equal" means the re-serialized container preserves
structure, element/attribute nesting, IDs, images, fonts, encoding, and all non-translated text; exact bytes may differ
where serialization is free to normalize (entity/attribute-quote/whitespace normalization by the XML/HTML/Markdown
writer, ZIP recompression of previously-DEFLATED entries). The export path therefore offers **no target-format choice**:
it re-emits the same container and format it imported, and there is nothing for the user to select.

**Converting between document formats is explicitly out of scope.** Cross-format conversion (for example EPUB→PDF,
FB2→EPUB, or MD→DOCX) is inherently lossy — it cannot preserve the skeleton, structure, and container identity this
pipeline guarantees — and is not offered by any code path in this version. The same-format guarantee above is what makes
the round-trip golden requirement below meaningful.

## translated-vs-preserved {#translated-vs-preserved}

| Preserved verbatim                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Translated                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Element structure, nesting, and order; attributes and element IDs; images and fonts; ZIP layout and file order; inline markup (masked); inline code `<code>` (masked as an atomic protected placeholder, its text kept exactly — DD-49); block code listings `<pre>`/`<pre><code>` (non-translatable blocks — never segmented, skeleton-preserved, DD-49); MathML `<math>` (verbatim, DD-49); index-term and cross-reference anchor ids/targets (DD-49); URLs and locked terms (masked). No numeral, standalone or in prose, is masked in this build (owner decision D-7). | Visible prose text nodes; headings, list items, table cell text; figure captions; verse lines; footnote/endnote body text (per policy); admonition/sidebar prose (note/tip/warning/sidebar blocks — ordinary translatable prose, DD-49); visible link text of index-term/cross-reference anchors (DD-49); every numeral, wherever it appears, so it inflects and localizes; metadata title/author (as configured); frontmatter values in Markdown (as configured); image `alt`/caption text (as configured); EPUB nav (EPUB 3) / NCX (EPUB 2) ToC labels (as configured). |

The last four rows on the "translated" side (metadata title/author, frontmatter values, `alt`/caption, nav/NCX ToC
labels) are governed by the Book-Brief "Also translate" toggle group (see FR-BRIEF and DD-47); each is modelled as a
synthetic **metadata unit** segment (`METADATA_TITLE`, `METADATA_AUTHOR`, `FRONTMATTER_VALUE`, `ALT`, `NAV_LABEL`)
anchored to the OPF / frontmatter / attribute / nav node it came from
(`02_Architecture/03_DOCUMENT_MODEL.md#data-model`). When a toggle is off, the corresponding text is preserved verbatim.

## round-trip-golden-requirement {#round-trip-golden-requirement}

For each format, parsing to skeleton + segments and reassembling **without changing any target text** shall reproduce a
**canonical-equal** copy of the source, except for intentional language-metadata updates (FR-DOC-09) — including the
Markdown frontmatter `lang` value, which is intentional language metadata like OPF `dc:language` and FB2 `<lang>`, not
an accidental deviation. Equality is asserted on the **canonicalized** form, not on raw bytes (DD-43, ADR-0003),
because a faithful re-serializer may normalize entities, attribute quoting, or insignificant whitespace. Per format:

- **EPUB** — compare entry-by-entry: each text entry (OPF, XHTML, nav, NCX, CSS) by its **decompressed canonical
  content**; **entry order preserved**; `mimetype` **first and STORED**; every unchanged binary entry (images, fonts) by
  **decompressed bytes** (ZIP recompression differences allowed).
- **Markdown** — compare by **re-parse-equal AST**: re-parsing the output yields a CommonMark AST equal to the source
  AST. A no-edit export preserves the resolved encoding and BOM presence; an encoding-switched fixture (see
  `#encoding-and-bom`) is asserted against a re-parsed canonical form instead (extending EC-FB2-1's carve-out to
  Markdown, ADR-0029).
- **TXT** — compare **exactly** (byte-for-byte) for a no-edit export, since TXT reassembles by splicing target spans
  into the original byte buffer (see `#txt`); an encoding-switched fixture (see `#encoding-and-bom`) is likewise
  asserted against a re-parsed canonical form rather than raw bytes (ADR-0029).
- **FB2** — compare by canonical XML over the parsed tree; when the export legitimately switches the declared encoding
  to UTF-8 (see `#fb2`, EC-FB2-1), that fixture is **excluded from the source-language golden** and instead asserted
  against a re-parsed canonical tree.

This is verified by a per-format round-trip golden test over representative fixtures. The skeleton is never semantically
regenerated (FR-DOC-03); target strings are written back into the same nodes on export (FR-EXPORT-02). Because export
re-emits the source container in its original format only (see `#export-same-format-only`), this same-format round trip
is the complete export contract — there is no format-conversion path to verify.

## epub {#epub}

| ID            | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-DOC-EPUB-1 | Parse the OPF package document, read the spine to establish reading order, and process each content document in spine order.                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| FR-DOC-EPUB-2 | Support EPUB 2 and EPUB 3 structural variants.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| FR-DOC-EPUB-3 | On repackage, write the `mimetype` entry first and stored (uncompressed), preserving the remaining ZIP entries and their identity.                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| FR-DOC-EPUB-4 | Preserve all element IDs, internal cross-reference targets, images, and embedded fonts unchanged.                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| FR-DOC-EPUB-5 | Parse XHTML content bodies with an HTML-aware parser and extract translatable text nodes as segments while masking inline markup.                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| FR-DOC-EPUB-6 | On export, set the target language in the OPF: **replace the first `dc:language`** (adding one if none is present), leaving any additional `dc:language` entries untouched (FR-DOC-07).                                                                                                                                                                                                                                                                                                                                                                               |
| FR-DOC-EPUB-7 | Detect content-encryption DRM and refuse to process it, while **allowing the two known IDPF font-obfuscation algorithms** and processing normally (FR-IMPORT-04; see EC-EPUB-1, EC-FONT-1).                                                                                                                                                                                                                                                                                                                                                                           |
| FR-DOC-EPUB-8 | Translate ToC labels as configured: EPUB 3 nav-document (`nav[epub:type=toc]`) link text and EPUB 2 NCX `navLabel/text`, modelled as `NAV_LABEL` metadata-unit segments (DD-47). Nav/NCX resources are **carved out of the out-of-spine verbatim rule** (see EC-EPUB-3) so their labels can be translated; their structure, `playOrder`, and href targets are preserved. Real EPUB 3 books often ship a **legacy NCX alongside the nav-document**; when both are present, translate **both, consistently** — the same label yields the same rendering in nav and NCX. |
| FR-DOC-EPUB-9 | Preserve code, math, and technical markup per `#code-and-technical-content` (DD-49): mask inline `<code>` atomically; treat `<pre>`/`<pre><code>` listings as non-translatable blocks; carry MathML `<math>` through verbatim; keep index-term/cross-reference anchor ids and targets while their visible link text stays translatable.                                                                                                                                                                                                                              |

### epub-edge-cases {#epub-edge-cases}

| ID        | Edge case                              | Expected behaviour                                                                                                                                                                                                                                                                                                                                                                                                               |
|-----------|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-EPUB-1 | `META-INF/encryption.xml` present      | Inspect the declared algorithms. Refuse with a DRM-blocked state **only** when it declares content-encryption or any **unknown** algorithm; no partial import. If it declares **only** the two known IDPF font-obfuscation algorithm URIs (`http://www.idpf.org/2008/embedding` and `http://ns.adobe.com/pdf/enc#RC4`), **allow** and process normally (the obfuscated font bytes are carried through unchanged; see EC-FONT-1). |
| EC-EPUB-2 | Malformed or missing OPF / spine       | Reject as corrupt with a clear reason; no partial import. A spine item whose file is absent is skipped and reported (readers show what exists); refuse only when none exist.                                                                                                                                                                                                                                                                                                                                                                        |
| EC-EPUB-3 | Content document not in spine          | Preserve out-of-spine resources verbatim; do not translate them — **except** the nav-document (EPUB 3) and NCX (EPUB 2), whose ToC labels are translated as configured (FR-DOC-EPUB-8, DD-47).                                                                                                                                                                                                                                   |
| EC-EPUB-4 | Duplicate or non-unique element IDs    | Preserve as-is; never rewrite IDs.                                                                                                                                                                                                                                                                                                                                                                                               |
| EC-EPUB-5 | Mixed EPUB2/EPUB3 features in one book | Handle both; reassemble to the original structure.                                                                                                                                                                                                                                                                                                                                                                               |

## fb2 {#fb2}

| ID           | Requirement                                                                                                                                                                                                                                                                                                                                           |
|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-DOC-FB2-1 | Parse FB2 as a single XML document, preserving comments, CDATA and declared encoding; entities are expanded (from the book's header or a bundled standard list), never fetched.                                                                                                                                                                                                                                                      |
| FR-DOC-FB2-2 | Support `.fb2.zip` containers, unpacking and repacking faithfully.                                                                                                                                                                                                                                                                                    |
| FR-DOC-FB2-3 | On export, **keep the declared character encoding** (e.g. `windows-1251`) when **every** target character is representable in it; otherwise switch to **UTF-8** and rewrite the XML declaration accordingly (see EC-FB2-1).                                                                                                                           |
| FR-DOC-FB2-4 | Preserve embedded binary images (base64 `binary` elements) unchanged.                                                                                                                                                                                                                                                                                 |
| FR-DOC-FB2-5 | Handle poem/stanza/verse (`poem`, `stanza`, `v`) structure, translating verse lines while preserving line grouping.                                                                                                                                                                                                                                   |
| FR-DOC-FB2-6 | Handle the notes body (`body name="notes"`) footnotes per the footnote policy (translate or keep; default translate — both skeleton-safe).                                                                                                                                                                                                            |
| FR-DOC-FB2-7 | Translate `title-info` metadata title/author as configured (`METADATA_TITLE`/`METADATA_AUTHOR`). On export, set the target language by **replacing the first `<lang>` in `title-info`** (adding one if none); **leave `src-title-info`'s `<lang>` (the source language) untouched**, and do not translate `src-title-info` (it records the original). |

### fb2-edge-cases {#fb2-edge-cases}

| ID       | Edge case                                                | Expected behaviour                                                                                                                                                                                                                                                                                                                              |
|----------|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-FB2-1 | Declared encoding is `windows-1251` (or other non-UTF-8) | Read using the declared encoding (after BOM sniff / ICU charset detection; see `#encoding-and-bom`). On export, keep it if every target character is representable; otherwise switch to UTF-8 and rewrite the XML declaration. An encoding-switched fixture is excluded from the source-language golden (see `#round-trip-golden-requirement`). |
| EC-FB2-2 | Mismatch between XML declaration and actual bytes        | Detect and reject as corrupt, or honour a reliably detected encoding; never silently corrupt.                                                                                                                                                                                                                                                   |
| EC-FB2-3 | Notes body with cross-references to note anchors         | Preserve anchor/link IDs; translate note text per policy.                                                                                                                                                                                                                                                                                       |
| EC-FB2-4 | Poem without explicit stanza grouping                    | Preserve line breaks; translate each verse line as a segment.                                                                                                                                                                                                                                                                                   |

## markdown {#markdown}

| ID          | Requirement                                                                                                                                                                                                                                                                       |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-DOC-MD-1 | Parse Markdown into an AST and translate prose text nodes only.                                                                                                                                                                                                                   |
| FR-DOC-MD-2 | Protect inline code (masked as an atomic protected placeholder) and URLs from translation; treat fenced/indented code blocks as **non-translatable blocks** — excluded from segmentation and the token budget, preserved via the skeleton (DD-49, `#code-and-technical-content`). |
| FR-DOC-MD-3 | Protect frontmatter keys; translate a frontmatter value as a `FRONTMATTER_VALUE` metadata-unit segment only when it is a single-line, untyped text scalar — never a key, never `lang`/`language`, never a YAML-typed value (null, boolean, number, date), a list, a map, or a URL/e-mail (owner decision D-2). An existing top-level `lang` value is replaced by the target language tag in its own quote style on export; `lang` is never added where absent, and `language` is left untouched. |
| FR-DOC-MD-4 | Preserve Markdown structure (headings, lists, tables, blockquotes, emphasis) on reassembly.                                                                                                                                                                                       |

### markdown-edge-cases {#markdown-edge-cases}

| ID      | Edge case                                                      | Expected behaviour                                                      |
|---------|--------------------------------------------------------------------|---------------------------------------------------------------------------|
| EC-MD-1 | Inline code or link label containing translatable-looking text | Keep code spans verbatim; translate link text but never the URL target. |
| EC-MD-2 | HTML embedded in Markdown                                      | Preserve raw HTML structure; translate only text nodes within it.       |
| EC-MD-3 | Frontmatter with mixed keys/values                             | Never translate a key. Translate a value only when it qualifies as a text scalar under owner decision D-2 — a single-line, untyped, non-URL text value that is not `lang` or `language`; a typed, multi-line, list, map, or `lang`/`language` value is always preserved verbatim. |

## txt {#txt}

| ID           | Requirement                                                                                                                                                                                                                                                           |
|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-DOC-TXT-1 | Treat plain text as paragraph-delimited segments (blank-line separated), preserving line endings; preserving encoding **unless** a target character is unrepresentable in it, in which case the whole document is written as UTF-8 (ADR-0029).                       |
| FR-DOC-TXT-2 | Preserve whitespace/indentation structure on reassembly.                                                                                                                                                                                                              |
| FR-DOC-TXT-3 | Model the TXT skeleton as the **original byte buffer plus a list of segment byte-span slots**; export splices each target span back into its slot, leaving all other bytes untouched (so the no-edit round trip is byte-exact; see `#round-trip-golden-requirement`). |

## encoding-and-bom {#encoding-and-bom}

Character encoding is resolved once on import and re-emitted faithfully on export:

1. **BOM sniff** — if a byte-order mark is present, it fixes the charset; the BOM presence is recorded and re-emitted
   exactly as found.
2. **ICU charset detection** — with no BOM (and no reliable in-band declaration such as an XML declaration), run ICU
   charset detection over the bytes.
3. **Default UTF-8** — if detection is inconclusive, default to UTF-8.

The detected charset **and** whether a BOM was present are recorded on the `Document` and reproduced verbatim on
export, **unless a target character is unrepresentable in the resolved charset** (TXT and Markdown, ADR-0029): the
whole document is then written as UTF-8, a byte-order mark is written exactly when the source had one, and the
`Document`'s recorded charset becomes UTF-8. Format-specific rules layer on top: FB2 honours its declared encoding and
may switch to UTF-8 with a rewritten declaration when a target character is unrepresentable (FR-DOC-FB2-3, EC-FB2-1),
generalized by ADR-0029 to TXT and Markdown; EPUB always writes lossless numeric character references and never
switches its declared encoding. TXT preserves the exact charset and line endings via its byte-buffer skeleton
(FR-DOC-TXT-3) except under this same-encoding-unrepresentable rule. See
`02_Architecture/03_DOCUMENT_MODEL.md#repackaging`.

## inline-masking-rules {#inline-masking-rules}

Across all formats, inline masking (FR-DOC-04) replaces protected spans with `⟦gN⟧` placeholders before translation and
restores them after, with a hard-gate placeholder-multiset check (FR-DOC-05). The masking/unmasking contract,
placeholder token grammar, escaping, and uniqueness validation are specified normatively in
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking`.

**What is masked** (protected, restored verbatim):

- all inline markup descendants and their tails (see the "text node" definition in
  `02_Architecture/03_DOCUMENT_MODEL.md#data-model`);
- **inline code** (`<code>`, Markdown code spans) — masked as a single **atomic** protected placeholder whose text is
  kept exactly (DD-49, `#code-and-technical-content`);
- **inline MathML** `<math>` inside a prose block — the whole subtree as one atomic placeholder (DD-49);
- locked glossary terms that carry a target;
- URLs;
- under the Keep foreign-passage policy, an inline element whose own `lang`/`xml:lang` differs from the source
  language — protected by the pipeline as one token and restored verbatim, known from the element's own markup only,
  never detected from unmarked text (ADR-0037).

Block code listings (`<pre>`, `<pre><code>`, fenced/indented Markdown blocks) are **not masked** — they never become
segments at all: they are non-translatable blocks that live only in the skeleton (DD-49, `#code-and-technical-content`).

**What is NOT masked** (stays translatable): **every numeral**, wherever it appears — standalone, typographic, inside
running prose, or inside a locked term — is left in the segment text so it can inflect and localize; no numeral is
masked in this build (owner decision D-7). Number preservation for the categories that ARE masked (inline markup, code,
math, locked terms, kept foreign runs) is already guaranteed by the placeholder-multiset hard gate, so there is no
separate number-preservation QA check.

| ID          | Edge case                                      | Expected behaviour                                                                                                                                |
|-------------|--------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-INLINE-1 | Nested inline tags (e.g. bold inside italic)   | Mask as a well-formed placeholder set; restore exact nesting.                                                                                     |
| EC-INLINE-2 | Placeholder dropped or duplicated by the model | Hard-fail the chunk; route to self-heal/flag; never export a broken placeholder set.                                                              |
| EC-INLINE-3 | Locked glossary term inside a longer word      | Mask only the intended span; avoid partial-word substitution.                                                                                     |
| EC-INLINE-4 | Number/unit the unit policy must convert       | No numeral is ever masked; the unit policy's conversion path applies to the numeral in its translatable text, wherever it appears.                |
| EC-INLINE-5 | Source already contains a `⟦` bracket          | Escape the pre-existing `⟦` before masking so it cannot be read as a placeholder token, and restore it on unmask (`#inline-masking` escape rule). |

## code-and-technical-content {#code-and-technical-content}

Technical books are saturated with code and technical markup — a real technical-EPUB corpus shows on the order of
900–2,700 inline `<code>` spans and 15–52 `<pre>` listings per book, plus MathML, heavy figures/tables, and sidebars —
so their handling is normative (DD-49), across all formats:

- **Inline code** (`<code>`, Markdown code spans) is masked as a single **atomic** protected `⟦gN⟧` placeholder; its
  text is carried in the placeholder map and restored exactly. It is never split, never partially translated, and a
  sentence/chunk split never cuts through it.
- **Block code listings** (`<pre>`, `<pre><code>`, fenced/indented Markdown blocks) are **non-translatable blocks**:
  they yield **no segments**, are **excluded from segmentation and the chunk token budget**, and are preserved via the
  immutable skeleton. They are never sent to the model in any form.
- **MathML `<math>`** is preserved **verbatim**: inline `<math>` inside a prose block is masked as one atomic
  placeholder; block-level `<math>` is a non-translatable block like `<pre>`.
- **Index-term and cross-reference anchors** keep their ids and href targets unchanged; their **visible link text
  remains translatable** (it is ordinary prose within its segment).
- **Admonitions and sidebars** (`note`/`tip`/`warning`/`sidebar` classes) are **ordinary translatable prose** blocks —
  their class/structure is skeleton-preserved, their text translates normally.
- **Table cells and figure captions** are translatable segments with row/column and figure structure preserved (see
  EC-VERSE-2, EC-IMG-2).

### code-edge-cases {#code-edge-cases}

| ID        | Edge case                                                              | Expected behaviour                                                                                                                                                                                                                                                                               |
|-----------|--------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-CODE-1 | A `<pre>` listing block is the entire chapter/content document         | The unit yields zero segments and passes through skeleton-only; the run counts the unit complete without stalling; the round trip preserves the listing exactly (canonical-equal, DD-43).                                                                                                        |
| EC-CODE-2 | Inline code whose text contains the masking bracket characters `⟦`/`⟧` | The code span is captured atomically into the placeholder map **before** placeholder-token scanning, so its brackets can never be read as placeholder tokens; brackets in the surrounding prose follow the escape rule (EC-INLINE-5). Restored byte-exactly within the canonical-equal contract. |
| EC-CODE-3 | Inline `<code>` inside a heading                                       | The heading is a normal translatable segment (`HEADING`); the code span is masked as an atomic placeholder within it and restored exactly — including when the heading's text is mirrored in a `NAV_LABEL` ToC entry, which must render consistently.                                            |
| EC-CODE-4 | MathML `<math>` inside a paragraph                                     | The paragraph stays a translatable segment; the entire `<math>` subtree is one atomic placeholder restored verbatim. A block-level `<math>` outside prose is a non-translatable block.                                                                                                           |

## verse-tables-notes {#verse-tables-notes}

| ID         | Edge case                                  | Expected behaviour                                                                                                                                                         |
|------------|-----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-VERSE-1 | Verse/poem line breaks and stanza grouping | Translate per line; preserve breaks and grouping.                                                                                                                          |
| EC-VERSE-2 | Tables                                     | Translate cell text; preserve rows/columns and header structure.                                                                                                           |
| EC-VERSE-3 | Footnotes/endnotes and scene-break markers | Preserve anchors/markers; translate note text per the footnote policy (translate or keep; default translate — the removed "omit-marker" behaviour is out of scope for v1). |

## images-and-fonts {#images-and-fonts}

| ID        | Edge case                                                                 | Expected behaviour                                                                                                                                              |
|-----------|-----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-IMG-1  | Raster/vector images referenced from content                              | Preserve bytes and references unchanged; never translate image content.                                                                                         |
| EC-IMG-2  | Image with translatable `alt`/caption text                                | Translate `alt`/caption text as configured, modelled as an `ALT` metadata-unit segment anchored to the attribute node (DD-47); keep the image binary untouched. |
| EC-FONT-1 | Embedded fonts obfuscated per a **known** IDPF font-obfuscation algorithm | Not treated as DRM: allow and process; preserve the font resources and their obfuscation bytes exactly (see EC-EPUB-1).                                         |

## drm-and-language-detection {#drm-and-language-detection}

| ID           | Edge case                                                                    | Expected behaviour                                                                                                                                                                   |
|--------------|--------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-DRM-1     | Content-encryption / unknown-algorithm DRM detected in EPUB or FB2 container | Refuse with a DRM-blocked state; do not attempt to decrypt. Known IDPF font-obfuscation algorithms are **not** DRM and do not trigger refusal (see EC-EPUB-1, EC-FONT-1).            |
| EC-LANG-1    | Declared language metadata disagrees with detected content language          | Trust only the book's own metadata: an EPUB whose package language disagrees with the majority of its content documents' declared languages raises a language-mismatch warning and preselects the content documents' language as the source on the Book Brief; no language is ever inferred from the book's text (FR-IMPORT-03, ADR-0037). |
| EC-LANG-2    | Multi-language book with a dominant language                                 | Preselect the source language from the book's declared metadata, normalized (FR-IMPORT-03); handle per-segment foreign passages via policy, decided from each block's own declared language or dominant script, never from detected content language. |
| EC-FOREIGN-1 | Untagged foreign-language passage within source-language text                | A block is foreign only when it, or an ancestor below the document root, declares a language other than the run's source language, or its dominant script differs from the source language's — nothing is detected from unmarked text; the foreign-passage policy applies (default keep-as-is, DD-26) and the QA wrong-language check is policy-aware (FR-QA-03). Under the Keep policy a block whose declared language differs from both the source language and the book's own declared language is kept verbatim whole, with no model call (owner decision D-6 as extended for blocks, 2026-10-02). |
| EC-FOREIGN-2 | Epigraph/quotation intended to remain foreign                                | Keep-as-is under the default policy; do not flag as wrong-language.                                                                                                                  |
| EC-FOREIGN-3 | Foreign-language run **inline** within a source-language segment             | Under the Keep policy, mask an inline element whose own `lang`/`xml:lang` differs from the source language as one protected `⟦gN⟧` placeholder so the surrounding prose still translates; restore the run verbatim on unmask; an unmarked foreign run is never detected from its text (see `#inline-masking-rules`, owner decision D-6, ADR-0037). A whole block that declares a foreign language (EC-FOREIGN-1) is kept the same way as one placeholder for its whole text and never reaches the model. |
