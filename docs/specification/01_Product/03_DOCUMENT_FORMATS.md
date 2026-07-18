**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:document`), QA **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
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
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Element structure, nesting, and order; attributes and element IDs; images and fonts; ZIP layout and file order; inline markup (masked); inline code `<code>` (masked as an atomic protected placeholder, its text kept exactly — DD-49); block code listings `<pre>`/`<pre><code>` (non-translatable blocks — never segmented, skeleton-preserved, DD-49); MathML `<math>` (verbatim, DD-49); index-term and cross-reference anchor ids/targets (DD-49); URLs, standalone/typographic numerals, numerals inside locked terms, and locked terms (masked); the OPF manifest/spine identity. | Visible prose text nodes; headings, list items, table cell text; figure captions; verse lines; footnote/endnote body text (per policy); admonition/sidebar prose (note/tip/warning/sidebar blocks — ordinary translatable prose, DD-49); visible link text of index-term/cross-reference anchors (DD-49); prose numerals (kept translatable so they inflect/localize); metadata title/author (as configured); frontmatter values in Markdown (as configured); image `alt`/caption text (as configured); EPUB nav (EPUB 3) / NCX (EPUB 2) ToC labels (as configured). |

The last four rows on the "translated" side (metadata title/author, frontmatter values, `alt`/caption, nav/NCX ToC
labels) are governed by the Book-Brief "Also translate" toggle group (see FR-BRIEF and DD-47); each is modelled as a
synthetic **metadata unit** segment (`METADATA_TITLE`, `METADATA_AUTHOR`, `FRONTMATTER_VALUE`, `ALT`, `NAV_LABEL`)
anchored to the OPF / frontmatter / attribute / nav node it came from
(`02_Architecture/03_DOCUMENT_MODEL.md#data-model`). When a toggle is off, the corresponding text is preserved verbatim.

## round-trip-golden-requirement {#round-trip-golden-requirement}

For each format, parsing to skeleton + segments and reassembling **without changing any target text** shall reproduce a
**canonical-equal** copy of the source, except for intentional language-metadata updates (FR-DOC-09). Equality is
asserted on the **canonicalized** form, not on raw bytes (DD-43, ADR-0003), because a faithful re-serializer may
normalize entities, attribute quoting, or insignificant whitespace. Per format:

- **EPUB** — compare entry-by-entry: each text entry (OPF, XHTML, nav, NCX, CSS) by its **decompressed canonical
  content**; **entry order preserved**; `mimetype` **first and STORED**; every unchanged binary entry (images, fonts) by
  **decompressed bytes** (ZIP recompression differences allowed).
- **Markdown** — compare by **re-parse-equal AST**: re-parsing the output yields a CommonMark AST equal to the source
  AST.
- **TXT** — compare **exactly** (byte-for-byte), since TXT reassembles by splicing target spans into the original byte
  buffer (see `#txt`).
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
| FR-DOC-EPUB-9 | Preserve code, math, and technical markup per `#code-and-technical-content` (DD-49): mask inline `<code>` atomically; treat `<pre>`/`<pre><code>` listings as non-translatable blocks; carry MathML `<math>` through verbatim; keep index-term/cross-reference anchor ids and targets while their visible link text stays translatable.                                                                                                                                                                                                                               |

### epub-edge-cases {#epub-edge-cases}

| ID        | Edge case                              | Expected behaviour                                                                                                                                                                                                                                                                                                                                                                                                               |
|-----------|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-EPUB-1 | `META-INF/encryption.xml` present      | Inspect the declared algorithms. Refuse with a DRM-blocked state **only** when it declares content-encryption or any **unknown** algorithm; no partial import. If it declares **only** the two known IDPF font-obfuscation algorithm URIs (`http://www.idpf.org/2008/embedding` and `http://ns.adobe.com/pdf/enc#RC4`), **allow** and process normally (the obfuscated font bytes are carried through unchanged; see EC-FONT-1). |
| EC-EPUB-2 | Malformed or missing OPF / spine       | Reject as corrupt with a clear reason; no partial import.                                                                                                                                                                                                                                                                                                                                                                        |
| EC-EPUB-3 | Content document not in spine          | Preserve out-of-spine resources verbatim; do not translate them — **except** the nav-document (EPUB 3) and NCX (EPUB 2), whose ToC labels are translated as configured (FR-DOC-EPUB-8, DD-47).                                                                                                                                                                                                                                   |
| EC-EPUB-4 | Duplicate or non-unique element IDs    | Preserve as-is; never rewrite IDs.                                                                                                                                                                                                                                                                                                                                                                                               |
| EC-EPUB-5 | Mixed EPUB2/EPUB3 features in one book | Handle both; reassemble to the original structure.                                                                                                                                                                                                                                                                                                                                                                               |

## fb2 {#fb2}

| ID           | Requirement                                                                                                                                                                                                                                                                                                                                           |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-DOC-FB2-1 | Parse FB2 as a single XML document, preserving comments, CDATA, entities, and declared encoding.                                                                                                                                                                                                                                                      |
| FR-DOC-FB2-2 | Support `.fb2.zip` containers, unpacking and repacking faithfully.                                                                                                                                                                                                                                                                                    |
| FR-DOC-FB2-3 | On export, **keep the declared character encoding** (e.g. `windows-1251`) when **every** target character is representable in it; otherwise switch to **UTF-8** and rewrite the XML declaration accordingly (see EC-FB2-1).                                                                                                                           |
| FR-DOC-FB2-4 | Preserve embedded binary images (base64 `binary` elements) unchanged.                                                                                                                                                                                                                                                                                 |
| FR-DOC-FB2-5 | Handle poem/stanza/verse (`poem`, `stanza`, `v`) structure, translating verse lines while preserving line grouping.                                                                                                                                                                                                                                   |
| FR-DOC-FB2-6 | Handle the notes body (`body name="notes"`) footnotes per the footnote policy (translate or keep; default translate — both skeleton-safe).                                                                                                                                                                                                            |
| FR-DOC-FB2-7 | Translate `title-info` metadata title/author as configured (`METADATA_TITLE`/`METADATA_AUTHOR`). On export, set the target language by **replacing the first `<lang>` in `title-info`** (adding one if none); **leave `src-title-info`'s `<lang>` (the source language) untouched**, and do not translate `src-title-info` (it records the original). |

### fb2-edge-cases {#fb2-edge-cases}

| ID       | Edge case                                                | Expected behaviour                                                                                                                                                                                                                                                                                                                              |
|----------|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-FB2-1 | Declared encoding is `windows-1251` (or other non-UTF-8) | Read using the declared encoding (after BOM sniff / ICU charset detection; see `#encoding-and-bom`). On export, keep it if every target character is representable; otherwise switch to UTF-8 and rewrite the XML declaration. An encoding-switched fixture is excluded from the source-language golden (see `#round-trip-golden-requirement`). |
| EC-FB2-2 | Mismatch between XML declaration and actual bytes        | Detect and reject as corrupt, or honour a reliably detected encoding; never silently corrupt.                                                                                                                                                                                                                                                   |
| EC-FB2-3 | Notes body with cross-references to note anchors         | Preserve anchor/link IDs; translate note text per policy.                                                                                                                                                                                                                                                                                       |
| EC-FB2-4 | Poem without explicit stanza grouping                    | Preserve line breaks; translate each verse line as a segment.                                                                                                                                                                                                                                                                                   |

## markdown {#markdown}

| ID          | Requirement                                                                                                                                                                                                                                                                       |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-DOC-MD-1 | Parse Markdown into an AST and translate prose text nodes only.                                                                                                                                                                                                                   |
| FR-DOC-MD-2 | Protect inline code (masked as an atomic protected placeholder) and URLs from translation; treat fenced/indented code blocks as **non-translatable blocks** — excluded from segmentation and the token budget, preserved via the skeleton (DD-49, `#code-and-technical-content`). |
| FR-DOC-MD-3 | Protect frontmatter keys; translate frontmatter values only as configured, modelled as `FRONTMATTER_VALUE` metadata-unit segments anchored to the frontmatter node (DD-47).                                                                                                       |
| FR-DOC-MD-4 | Preserve Markdown structure (headings, lists, tables, blockquotes, emphasis) on reassembly.                                                                                                                                                                                       |

### markdown-edge-cases {#markdown-edge-cases}

| ID      | Edge case                                                      | Expected behaviour                                                      |
|---------|----------------------------------------------------------------|-------------------------------------------------------------------------|
| EC-MD-1 | Inline code or link label containing translatable-looking text | Keep code spans verbatim; translate link text but never the URL target. |
| EC-MD-2 | HTML embedded in Markdown                                      | Preserve raw HTML structure; translate only text nodes within it.       |
| EC-MD-3 | Frontmatter with mixed keys/values                             | Never translate keys; translate values only where configured.           |

## txt {#txt}

| ID           | Requirement                                                                                                                                                                                                                                                           |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-DOC-TXT-1 | Treat plain text as paragraph-delimited segments (blank-line separated), preserving line endings and encoding.                                                                                                                                                        |
| FR-DOC-TXT-2 | Preserve whitespace/indentation structure on reassembly.                                                                                                                                                                                                              |
| FR-DOC-TXT-3 | Model the TXT skeleton as the **original byte buffer plus a list of segment byte-span slots**; export splices each target span back into its slot, leaving all other bytes untouched (so the no-edit round trip is byte-exact; see `#round-trip-golden-requirement`). |

## encoding-and-bom {#encoding-and-bom}

Character encoding is resolved once on import and re-emitted faithfully on export:

1. **BOM sniff** — if a byte-order mark is present, it fixes the charset; the BOM presence is recorded and re-emitted
   exactly as found.
2. **ICU charset detection** — with no BOM (and no reliable in-band declaration such as an XML declaration), run ICU
   charset detection over the bytes.
3. **Default UTF-8** — if detection is inconclusive, default to UTF-8.

The detected charset **and** whether a BOM was present are recorded on the `Document` and reproduced verbatim on export.
Format-specific rules layer on top: FB2 honours its declared encoding and may switch to UTF-8 with a rewritten
declaration when a target character is unrepresentable (FR-DOC-FB2-3, EC-FB2-1); TXT preserves the exact charset and
line endings via its byte-buffer skeleton (FR-DOC-TXT-3). See `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`.

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
- locked glossary terms;
- URLs;
- **standalone/typographic numerals**, numerals **inside locked terms**, and numerals inside URLs;
- a detected **foreign-language inline run** (masked as a protected keep-as-is placeholder so surrounding prose still
  translates — see EC-FOREIGN-3).

Block code listings (`<pre>`, `<pre><code>`, fenced/indented Markdown blocks) are **not masked** — they never become
segments at all: they are non-translatable blocks that live only in the skeleton (DD-49, `#code-and-technical-content`).

**What is NOT masked** (stays translatable): **prose numerals** — numerals embedded in running prose remain in the
segment text so they can inflect and localize (DD-09). Number preservation for masked numerals is already guaranteed by
the tag-multiset hard gate, so there is no separate number-preservation QA check.

| ID          | Edge case                                      | Expected behaviour                                                                                                                                |
|-------------|------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-INLINE-1 | Nested inline tags (e.g. bold inside italic)   | Mask as a well-formed placeholder set; restore exact nesting.                                                                                     |
| EC-INLINE-2 | Placeholder dropped or duplicated by the model | Hard-fail the chunk; route to self-heal/flag; never export a broken placeholder set.                                                              |
| EC-INLINE-3 | Locked glossary term inside a longer word      | Mask only the intended span; avoid partial-word substitution.                                                                                     |
| EC-INLINE-4 | Number/unit the unit policy must convert       | Standalone/locked-term numerals are masked; prose numerals stay translatable; the unit policy's conversion path applies to the translatable text. |
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
|-----------|------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-CODE-1 | A `<pre>` listing block is the entire chapter/content document         | The unit yields zero segments and passes through skeleton-only; the run counts the unit complete without stalling; the round trip preserves the listing exactly (canonical-equal, DD-43).                                                                                                        |
| EC-CODE-2 | Inline code whose text contains the masking bracket characters `⟦`/`⟧` | The code span is captured atomically into the placeholder map **before** placeholder-token scanning, so its brackets can never be read as placeholder tokens; brackets in the surrounding prose follow the escape rule (EC-INLINE-5). Restored byte-exactly within the canonical-equal contract. |
| EC-CODE-3 | Inline `<code>` inside a heading                                       | The heading is a normal translatable segment (`HEADING`); the code span is masked as an atomic placeholder within it and restored exactly — including when the heading's text is mirrored in a `NAV_LABEL` ToC entry, which must render consistently.                                            |
| EC-CODE-4 | MathML `<math>` inside a paragraph                                     | The paragraph stays a translatable segment; the entire `<math>` subtree is one atomic placeholder restored verbatim. A block-level `<math>` outside prose is a non-translatable block.                                                                                                           |

## verse-tables-notes {#verse-tables-notes}

| ID         | Edge case                                  | Expected behaviour                                                                                                                                                         |
|------------|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-VERSE-1 | Verse/poem line breaks and stanza grouping | Translate per line; preserve breaks and grouping.                                                                                                                          |
| EC-VERSE-2 | Tables                                     | Translate cell text; preserve rows/columns and header structure.                                                                                                           |
| EC-VERSE-3 | Footnotes/endnotes and scene-break markers | Preserve anchors/markers; translate note text per the footnote policy (translate or keep; default translate — the removed "omit-marker" behaviour is out of scope for v1). |

## images-and-fonts {#images-and-fonts}

| ID        | Edge case                                                                 | Expected behaviour                                                                                                                                              |
|-----------|---------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-IMG-1  | Raster/vector images referenced from content                              | Preserve bytes and references unchanged; never translate image content.                                                                                         |
| EC-IMG-2  | Image with translatable `alt`/caption text                                | Translate `alt`/caption text as configured, modelled as an `ALT` metadata-unit segment anchored to the attribute node (DD-47); keep the image binary untouched. |
| EC-FONT-1 | Embedded fonts obfuscated per a **known** IDPF font-obfuscation algorithm | Not treated as DRM: allow and process; preserve the font resources and their obfuscation bytes exactly (see EC-EPUB-1).                                         |

## drm-and-language-detection {#drm-and-language-detection}

| ID           | Edge case                                                                    | Expected behaviour                                                                                                                                                                   |
|--------------|------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-DRM-1     | Content-encryption / unknown-algorithm DRM detected in EPUB or FB2 container | Refuse with a DRM-blocked state; do not attempt to decrypt. Known IDPF font-obfuscation algorithms are **not** DRM and do not trigger refusal (see EC-EPUB-1, EC-FONT-1).            |
| EC-LANG-1    | Declared language metadata disagrees with detected content language          | Trust detected content language; surface a language-mismatch state; let the user confirm (FR-IMPORT-03).                                                                             |
| EC-LANG-2    | Multi-language book with a dominant language                                 | Detect the dominant source language; handle per-segment foreign passages via policy.                                                                                                 |
| EC-FOREIGN-1 | Untagged foreign-language passage within source-language text                | Detect per segment; apply the foreign-passage policy (default keep-as-is, DD-26); the QA wrong-language check is policy-aware (FR-QA-03).                                            |
| EC-FOREIGN-2 | Epigraph/quotation intended to remain foreign                                | Keep-as-is under the default policy; do not flag as wrong-language.                                                                                                                  |
| EC-FOREIGN-3 | Foreign-language run **inline** within a source-language segment             | Mask the detected foreign inline run as a protected `⟦gN⟧` (keep-as-is) so the surrounding prose still translates; restore the run verbatim on unmask (see `#inline-masking-rules`). |
