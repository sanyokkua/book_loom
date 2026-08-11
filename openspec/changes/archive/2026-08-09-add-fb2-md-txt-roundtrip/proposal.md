# add-fb2-md-txt-roundtrip

## Why

`BookFormat` already declares `FB2`, `MARKDOWN` and `TXT`, and nothing stands behind any of them. `DocumentService`
delegates `open`/`write` straight to `EpubReader`/`EpubWriter` with no dispatch at all, so handing it a `.fb2` file
today produces "this is not a valid EPUB" — technically true, and exactly the wrong answer. FR-IMPORT-01
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`) promises EPUB 2/3, FB2, `.fb2.zip`, Markdown and TXT; three of
the five are currently a name in an enum.

**The ordering argument is the one that put change 3 first.** Masking (change 5), the pipeline, export and every
screen are written once against seam F1 and stay format-agnostic *only if all four formats populate that model before
they arrive*. A format that lands in Stage D does not add a parser — it adds a format branch to every consumer
written in the meantime, which is the "later stages reshape earlier code" the roadmap's seam contract forbids
(`07_ROADMAP.md#forward-compatibility-seams`).

**Each remaining format also breaks the round trip somewhere EPUB structurally cannot.** EPUB's fidelity risk is
container-shaped: entry order, a stored `mimetype`, carried binaries. The other three are text-shaped and fail
differently. FB2 stakes the book on a *declared* encoding and keeps its cover art as base64 inside the XML. Markdown
has **no canonical serialization at all** — `*em*` and `_em_` are the same document — so "compare the output" is only
meaningful against a re-parsed tree. TXT is the opposite extreme and the only format where byte equality is both
achievable and *required*. One comparison rule would be right for one of the three and wrong for the other two.

**And a survey of a real 194-book library turned three assumptions in the shipped EPUB path into measured defects.**
Those are folded into this change because it is the change that opens that code:

| Measured on 194 real EPUBs / 7 FB2 / 8 Markdown / 4 TXT | Consequence |
|---|---|
| **42 books (21.6%) import essentially empty** — their paragraphs are `<div class="paragraph">`, and 41 fall under 1% text coverage | The segment walker's tag whitelist covers only 73.74% of 108M characters (**ADR-0027**) |
| **23.4% of all blocks contain inline markup**; eight books exceed 99% | Write-back escapes the markup and duplicates the original — and no test exercises it (**ADR-0025**) |
| **16 books are refused as DRM-protected** although none carries content encryption | The allowlist matches an algorithm URI that vendors spell inconsistently (**ADR-0026**) |

None of these is visible to the golden round-trip test. A book that yields zero segments round-trips perfectly.

## What Changes

### The three fixes to shipped EPUB behaviour

- **Blocks are recognised structurally, not by tag name (ADR-0027).** An element is segment-bearing when it owns
  direct non-whitespace text, and the segment is emitted at the innermost such element so wrapper `div`s are
  descended into rather than segmented. Coverage rises from 73.74% to 99.99%. The tag name now decides only the
  segment's *kind*.
- **Reassembly replaces a segment run's inner content (ADR-0025).** `SkeletonAnchor` becomes a sealed interface:
  `NodeAnchor(nodePath, runIndex)` for tree skeletons and `ByteSpanAnchor(start, end)` for buffer skeletons. The
  `childIndex` component is deleted — it was the defect.
- **DRM is adjudicated by what is encrypted (ADR-0026).** Every `CipherReference` must resolve to a manifest item
  with a font media type, or the book is refused. Cipher URIs resolve against the **container root**; manifest hrefs
  against the **OPF directory** — 102 of 194 books have a non-root OPF, so the two resolutions stay separate.
- **Original compression method is preserved.** 26 books deliberately STORE 2,578 already-compressed entries; the
  writer re-DEFLATEs all of them today. `RawEntry` already records the method — the writer just discards it.
- **Zip reading is bounded**: entry-count, total-uncompressed-size and compression-ratio caps, plus a Cp437 retry
  when UTF-8 entry-name decoding throws (measured on real Russian archives; `ZipException: invalid LOC header`).

### The three new formats

- **Format dispatch.** `open` resolves the format from the extension and confirms it against the leading bytes;
  `write` switches on `Document.format()`, authoritative because export is same-format-only (ADR-0004, DD-30). An
  unrecognised extension, a `.txt.zip`, and a *directory* named `*.fb2` each refuse with their own reason.
- **FB2 read** as a single XML document through the JDOM2 configuration the OPF already uses — comments, CDATA,
  entity spelling, the XML declaration, namespace prefixes and block-level whitespace all preserved. Each `<body>` is
  a unit; `<p>`, section `<title>`, `<subtitle>`, `<v>`, `<td>`/`<th>` and the `body name="notes"` footnote bodies
  become segments (FR-DOC-FB2-1, FR-DOC-FB2-5, FR-DOC-FB2-6).
- **`.fb2.zip`** unpacked and repacked; a zip-encrypted member is refused as DRM-blocked (FR-DOC-FB2-2).
- **FB2 `<binary>` sections** carried through untouched — never re-encoded or re-wrapped (FR-DOC-FB2-4).
- **FB2 export** keeps the declared encoding when every output character is representable and otherwise switches to
  UTF-8 with a rewritten declaration (FR-DOC-FB2-3, EC-FB2-1); sets the target language by replacing the first
  `<lang>` in `title-info`, leaving `<src-lang>` and any `<src-title-info>` untouched (FR-DOC-FB2-7).
- **Markdown read/write by patching the original buffer.** The CommonMark AST is used for *analysis only* — to locate
  each leaf block's byte span — and export copies the original bytes and substitutes only translated spans. Segments
  are **leaf blocks only**: `Paragraph`, `Heading`, `TableCell`, never `ListItem`/`BulletList`/`BlockQuote`, because a
  container's span can enclose a code block.
- **Markdown code blocks, raw-HTML blocks and frontmatter yield no segments** and are carried verbatim
  (FR-DOC-MD-2, FR-DOC-MD-3, EC-MD-2, EC-MD-3, DD-49).
- **TXT parses to a byte buffer plus paragraph byte spans**; export splices target spans into a copy of the original
  and leaves every other byte untouched, so a no-edit round trip is byte-exact by construction (FR-DOC-TXT-1..3).
- **Character encoding is resolved once** — BOM, then an in-band declaration, then ICU detection, then UTF-8 — and
  recorded on the document (`01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`). An FB2 whose declaration
  contradicts its bytes is refused (EC-FB2-2).
- **`declaredLang` and the title/author metadata map** are populated for FB2 (`title-info`) and Markdown
  (frontmatter), matching what the EPUB reader already does.

### Testing

- **A text-coverage assertion joins every format's golden test.** Each asserts that segments cover a stated
  proportion of the fixture's visible text. This is the only assertion that measures whether the importer *does*
  anything — every other document assertion measures that nothing was damaged, which a zero-segment import satisfies
  trivially (ADR-0027).
- **Three canonical comparisons**, one per format: canonical-XML **plus the declared encoding value plus each
  `<binary>` payload exactly** for FB2; re-parse-equal AST plus byte-exact frontmatter for Markdown; exact bytes for
  TXT and TXT only.

**BREAKING (`:api`, source-only):** `SkeletonAnchor` becomes a sealed interface; `Document` gains nullable `charset`
and `hasBom`. Both change a canonical constructor, so every construction site is a compile break. All of them are
inside `:document` and its tests, and this is the last change for which that is true. No released artifact and no
persisted data exists; the SQLite schema is unaffected (there is no anchor column).

**Non-goals, stated because a reader could reasonably expect them here.**

- **No inline masking, for any format.** `masked` stays equal to `sourceInner` and `placeholders` empty, exactly as
  EPUB ships today (change 3 `design.md` D2). That includes Markdown code spans and URLs. Excluding a code *block*
  from segmentation is not masking and **is** in scope.
- **No metadata-unit segments.** FB2 `title-info` title/author and Markdown frontmatter values are `METADATA_TITLE`,
  `METADATA_AUTHOR` and `FRONTMATTER_VALUE`, owned by `add-metadata-units-and-language-detection` (ADR-0023). The
  metadata *map* and `declaredLang` are populated here; segments for them are not.
- **No language detection.** `Document.detectedSourceLang` stays null.
- **EC-MD-2's second half** — translating text nodes inside a Markdown raw-HTML block — is deferred to change 5,
  which introduces the nested-parse mechanism it needs. Recorded in `CHANGE_BACKLOG.md`.

## Capabilities

### New Capabilities

None. `openspec/specs/document-round-trip/` already exists.

### Modified Capabilities

- `document-round-trip`: opening a book file, parsing it into an immutable skeleton plus an ordered list of
  translatable segments, and writing it back in the same format so a no-edit round trip is canonical-equal to the
  source. Change 3 established the capability and covered EPUB. This change extends it to the three remaining
  formats FR-IMPORT-01 promises, adds the format dispatch the port has never had, and revises three requirements the
  shipped EPUB implementation got wrong: how a translatable block is recognised, how a translation is written back,
  and how DRM is adjudicated.

## Impact

- **`:api/ua.bookloom.api.document`** — `SkeletonAnchor` becomes a sealed interface permitting `NodeAnchor` and
  `ByteSpanAnchor` (ADR-0025); `Document` gains `@Nullable charset` and `@Nullable hasBom`, null for container
  formats that have no document-level encoding (`01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`).
  `BookFormat`'s Javadoc, which cites a change name that does not exist, is corrected. Framework-free as always: no
  CommonMark or ICU type may appear here.
- **`:document`** — three new packages, all already planned in `01_MODULE_INVENTORY.md#module-document`:
  `ua.bookloom.document.fb2`, `ua.bookloom.document.md`, `ua.bookloom.document.txt`, plus
  `ua.bookloom.document.detect` for the charset ladder. `ua.bookloom.document` finally performs the "format dispatch"
  its inventory row has always claimed, and `ua.bookloom.document.model` grows the structural block walker
  (ADR-0027) and the run/span anchor machinery (ADR-0025) that all four formats share. The zip reader and its
  `RawEntry` move there too, out of `document.epub`: `.epub` and `.fb2.zip` are both zip containers, and the resource
  caps added here guard nothing if only one of the two entry points has them.
- **Dependencies added, `:document` only** — commonmark-java plus its GFM tables extension (BSD-2-Clause), and ICU4J
  (ICU License, already an allowlisted exception for `com.ibm.icu:icu4j` in
  `config/license/allowed-licenses.json`). Strikethrough and autolink extensions are deliberately **not** added:
  they are inline constructs that change no block boundary, and the surveyed corpus contains zero of either. Both
  libraries are named in `05_Dependencies/01_DEPENDENCIES.md`; note that its "Used by" column lists ICU4J against
  `:pipeline` and `:ui` only, because it was written around sentence segmentation and `MessageFormat`, while
  `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom` independently mandates ICU charset detection on the import
  path. Under-inclusive, not contradictory — flagged so it is reviewed rather than discovered. `:document` locks
  normally, so lockfiles must show a real diff.
- **`module-info.java`** — `:document` adds `requires org.commonmark` and `requires com.ibm.icu`; neither may appear
  in any other module (`02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules`).
- **Error handling** — every new failure classifies at the point of recognition and surfaces through the existing
  envelope. An unrecognised format, a `.txt.zip`, a directory, a malformed FB2, a `.fb2.zip` with no FB2 member, a
  declaration/bytes encoding mismatch and a zip exceeding its resource caps are all `ErrorCode.validation`. So is a
  DRM refusal — a zip-encrypted `.fb2.zip` member, a non-font encrypted EPUB resource — because the frozen
  fifteen-constant vocabulary (`02_Architecture/09_ERROR_HANDLING.md#error-code`) has no DRM code and this change
  does not add one; what separates a DRM refusal from a corrupt-container refusal is the user-facing message, which
  the scenarios now assert rather than leaving to the reader. No filesystem path enters `AppError.details`
  (`02_Architecture/09_ERROR_HANDLING.md#safe-details-allowlist`).
- **Test fixtures are hand-authored and committed; the real-book corpus is neither.** Fixtures reproduce the shapes
  the survey revealed — `div`-as-paragraph, `<br/>`-delimited prose, spacer-only `<p>`, image-only blocks, an
  encryption manifest naming a non-font resource, a `windows-1251` FB2, a notes body, a table, CDATA/comments. The
  surveyed books are copyrighted and live only in the git-ignored `.temporary_context/`; **no test, fixture, resource
  path or build step may reference that directory**, so a clean checkout and CI build identically. Verifying the
  built code against that corpus is a local, manual, optional step recorded in the final task group.
- **Offline invariant** — unaffected, and now measured rather than assumed: 53 books rely on XHTML named entities
  (`&nbsp;` × 10,377), so the parser must resolve them **without fetching a DTD over the network**. jsoup resolves
  HTML entities internally and the existing `SecureXml` configuration already disables external DTD and entity
  loading. Neither commonmark-java nor ICU4J performs I/O. No module gains `java.net.http`.
- **Frozen spec:** unedited. **ADRs consumed:** ADR-0003/DD-43, ADR-0004/DD-30, ADR-0014, ADR-0023, and change 3's
  `design.md` D1–D7. **ADRs added:** ADR-0025 (reassembly model), ADR-0026 (DRM adjudication), ADR-0027 (structural
  block segmentation).
