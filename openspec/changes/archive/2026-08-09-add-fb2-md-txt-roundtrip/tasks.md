# Tasks — add-fb2-md-txt-roundtrip

Extends seam F1 from one format to four, and corrects three things the shipped EPUB path got wrong. The decisions
are `design.md` (D1–D13) and three ADRs: **ADR-0025** (reassembly model), **ADR-0026** (DRM adjudication),
**ADR-0027** (structural block recognition). The frozen sources are `01_Product/03_DOCUMENT_FORMATS.md`
(`#fb2`, `#markdown`, `#txt`, `#encoding-and-bom`, `#round-trip-golden-requirement` and the edge-case tables),
`02_Architecture/03_DOCUMENT_MODEL.md` and `09_ERROR_HANDLING.md`.

**Group order is a dependency chain.** The `:api` shape changes land first because everything compiles against
them; the shared walker and write-back follow because all four formats use them; the shipped-EPUB fixes come next
because they are the smallest and prove the new anchors against a format that already works; the three new formats
follow; fixtures and the goldens land last because they are the gate.

**Test markers.** A test covering a business requirement carries `// Covers: FR-*` plus a one-line EARS restatement
of the obligation it proves; a mechanical test carries none. Do not invent an id — every `FR-DOC-FB2-*`,
`FR-DOC-MD-*`, `FR-DOC-TXT-*`, `FR-IMPORT-*` and `EC-*` id cited below is real and citable.

**No masking in this change.** Every segment ships `masked` equal to `sourceInner` and `placeholders` empty, exactly
as EPUB does today. Excluding a code block from segmentation is not masking and *is* in scope.

**`.temporary_context/` is local-only and must stay invisible to the build.** It is git-ignored and holds copyrighted
books. No test, fixture path, resource directory, Gradle task or CI step may reference it. Every committed fixture is
hand-authored. A clean checkout with that directory absent must build and pass identically — task 12.7 verifies
exactly that.

## 1. Seam F1 shape changes in `:api`

- [x] 1.1 Convert `SkeletonAnchor` from a record into a `sealed interface` permitting `NodeAnchor(List<Integer>
      nodePath, int runIndex)` and `ByteSpanAnchor(int startInclusive, int endExclusive)`, and **delete the
      `childIndex` component**. A tree anchor must identify which line-break-delimited run of a block a segment
      covers, and a buffer format has no tree to index at all; `childIndex` addressed a single text node, which is
      the shape that made write-back wrong.
      → `:api/ua.bookloom.api.document` · ADR-0025, `03_DOCUMENT_MODEL.md#data-model`, FR-DOC-TXT-3
- [x] 1.2 Add `@Nullable charset` and `@Nullable hasBom` components to `Document`. The frozen spec requires both to
      be recorded and reproduced on export; they are nullable because a container format has no document-level
      encoding — an EPUB's spine documents each declare their own, and a byte-order mark has no referent there.
      → `:api/ua.bookloom.api.document` · `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`,
      `03_DOCUMENT_MODEL.md#data-model`, design.md D5
- [x] 1.3 Correct `BookFormat`'s Javadoc, which cites `add-fb2-markdown-txt-roundtrip` — a change name that does not
      exist. A citation that does not resolve is worse than none, and this one is read by every later change that
      opens the enum.
      → `:api/ua.bookloom.api.document` · `docs/implementation_plan/CHANGE_BACKLOG.md`
- [x] 1.4 Cover the model changes: a `ByteSpanAnchor` rejects a negative start and an end below its start, a
      `NodeAnchor` rejects a negative run index, and a `Document` round-trips a null charset. Mechanical
      record-invariant tests; no `Covers:` marker.
      → `:api/ua.bookloom.api.document` · `modules/api/src/test/java/ua/bookloom/api/document/`, `.claude/rules/testing.md`

## 2. Structural block recognition and run splitting

- [x] 2.1 Replace `BlockSegmentWalker`'s tag whitelist with the structural rule: an element is segment-bearing when
      it owns direct non-whitespace text, and the segment is emitted at the innermost such element so a wrapper is
      descended into rather than segmented. A tag list reached only 73.74% of a 194-book corpus's text and left 42
      books importing essentially empty, because whole publishing toolchains write paragraphs as
      `<div class="paragraph">`.
      → `:document/ua.bookloom.document.model` · ADR-0027, FR-DOC-01, `03_DOCUMENT_MODEL.md#data-model`
- [x] 2.2 Map the tag name to `SegmentKind` only — `h1`–`h6` and FB2 `subtitle`/`title` to `HEADING`, `li` to
      `LIST_ITEM`, `td`/`th` to `TABLE_CELL`, FB2 `v` to `VERSE_LINE`, everything else to `PARAGRAPH`. The tag no
      longer decides *whether* an element is a segment, only what kind it is, so an unrecognised tag degrades to a
      paragraph rather than to silence. `FOOTNOTE`, `CAPTION` and `TITLE` are deliberately never emitted — each needs
      a semantic judgement this structural rule does not make, and nothing downstream treats them differently yet —
      so do not reach for one because a tag happens to look like it.
      → `:document/ua.bookloom.document.model` · ADR-0027, `03_DOCUMENT_MODEL.md#data-model`
- [x] 2.3 Emit no segment for a block whose content is empty or whitespace-only once markup is disregarded. One
      surveyed EPUB carries 1,965 such blocks out of 12,412 and one FB2 has 1,950 image-only paragraphs; each would
      otherwise cost a model call and a progress slot with nothing to translate.
      → `:document/ua.bookloom.document.model` · ADR-0027, EC-IMG-1,
      `01_Product/03_DOCUMENT_FORMATS.md#images-and-fonts`
- [x] 2.4 Split a segment-bearing block's content on line-break elements into runs, emitting one segment per run and
      leaving the break elements in the skeleton. One EPUB holds 465,500 characters across four elements separated by
      9,290 `<br/>`, and one FB2 holds 1,850,973 characters in two paragraphs — as the **tails** of the break
      elements, not as the block's own text, so an implementation reading `element.text` alone extracts 25 characters
      from a 3.2 MB book.
      → `:document/ua.bookloom.document.model` · ADR-0025, FR-DOC-08, design.md D9
- [x] 2.5 Keep the existing exclusions ahead of the text test: `<pre>` and its subtree, block-level `<math>`, and
      (per format) Markdown code and raw-HTML blocks yield no segment however much text they own. The structural rule
      widens what counts as a block, so the exclusions must be checked first or a `<pre>` becomes a segment.
      → `:document/ua.bookloom.document.model` · DD-49, `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`
- [x] 2.6 Compute an anchor per segment: `NodeAnchor(nodePath, runIndex)` for tree skeletons, without inserting a
      node or attribute. Anchors must be derived from positions already present in the skeleton, because a parser
      that marked its own segments would violate the no-regeneration guarantee the whole model rests on.
      → `:document/ua.bookloom.document.model` · DD-07, ADR-0025, design.md D2
- [x] 2.7 Cover the walker: a text-bearing `div` is segmented, a wrapper `div` is not, a `<pre>` owning text yields
      nothing, an image-only paragraph yields nothing, a spacer `<p><br/></p>` yields nothing, three runs yield three
      segments with run indices 0/1/2, and an ordinary paragraph carries run index 0. Mark each
      `// Covers: FR-DOC-01` / `FR-DOC-08` / `DD-49` with its one-line EARS restatement.
      → `:document/ua.bookloom.document.model` · `modules/document/src/test/java/ua/bookloom/document/model/`
- [x] 2.8 Cover the invariant that no two segments' extents overlap. The innermost-element rule is what prevents a
      wrapper and its child both being segmented; a regression there would double-translate text and corrupt
      write-back, and no other assertion in the suite would notice.
      → `:document/ua.bookloom.document.model` · ADR-0027, design.md "Risks"

## 3. Write-back per ADR-0025

- [x] 3.1 Rewrite tree write-back to replace the inner content of the segment's own run within its block, parsing
      the target content as markup rather than setting it as literal text. The shipped implementation writes into a
      single text node, which escapes any restored inline markup and leaves the original inline children in place —
      23.4% of blocks in a 194-book corpus carry inline markup and eight books exceed 99%.
      → `:document/ua.bookloom.document.model` · ADR-0025, FR-DOC-03, `03_DOCUMENT_MODEL.md#reassembly`
- [x] 3.2 Implement buffer write-back: copy the original buffer into fresh output in one ascending pass and
      substitute only the spans of segments carrying target text, never mutating the source buffer. This single rule
      is what keeps every byte span valid and makes a no-edit round trip byte-identical by construction; a refactor
      to a mutable builder, or writing spans out of order, breaks it only once two segments are translated.
      → `:document/ua.bookloom.document.model` · ADR-0025, FR-DOC-TXT-3, design.md D2
- [x] 3.3 Cover write-back with inline markup: a segment parsed from `<p>Hello <em>world</em>.</p>` written back as
      `Привіт <em>світ</em>.` produces that markup, contains no `&lt;em&gt;`, and does not retain the original
      `<em>world</em>`. **No fixture or test in the repository exercises this today** — every existing fixture
      paragraph is plain text and the golden round trip performs zero edits, which is why the defect shipped.
      Mark it `// Covers: FR-DOC-03`.
      → `:document/ua.bookloom.document.model` · ADR-0025, `specs/document-round-trip/spec.md`
- [x] 3.4 Cover run-scoped write-back: writing only the second run of `<div>Один<br/>Два<br/>Три</div>` yields
      `<div>Один<br/>Two<br/>Три</div>` with both `<br/>` elements intact. A rule that replaced the whole block would
      pass a single-run test and destroy the separators here. Mark it `// Covers: FR-DOC-03`.
      → `:document/ua.bookloom.document.model` · ADR-0025
- [x] 3.5 Cover multi-write span stability: write two buffer segments back and assert the second segment's byte span
      is unchanged from parse time and the output is correct. A scheme that recomputed spans after each write passes
      every single-segment test and fails exactly this. Mark it `// Covers: FR-DOC-TXT-3`.
      → `:document/ua.bookloom.document.model` · ADR-0025, design.md D2
- [x] 3.6 Cover parse determinism: parsing the same bytes twice yields identical unit ids, segment ids, order and
      anchors. Anchors are never persisted — `06_DATA_MODEL_SQLITE.md#tables` has no anchor column — so resume
      recomputes them by re-parsing, and a non-deterministic parse would write translations into the wrong places
      after a restart. Mark it `// Covers: FR-IMPORT-08`.
      → `:document/ua.bookloom.document.model` · design.md D13, `03_NonFunctional/05_RELIABILITY_AND_RESUME.md`

## 4. Shipped EPUB corrections

- [x] 4.1 Rewrite `DrmAdjudicator` to decide from what is encrypted: resolve every `CipherReference/@URI` against the
      **container root**, match it to a manifest item whose `href` resolves against the **OPF directory**, and allow
      the book only when every encrypted resource carries a font media type. A survey of 194 books found 30 with an
      encryption manifest, none of them DRM, and **16 refused** over a single missing character in an algorithm URI.
      → `:document/ua.bookloom.document.epub` · ADR-0026, EC-EPUB-1, EC-FONT-1, FR-DOC-EPUB-7
- [x] 4.2 Define the font media-type allowlist explicitly — `application/vnd.ms-opentype`, `application/x-font-otf`,
      `application/x-font-ttf`, `application/font-sfnt`, `application/font-woff`, `application/x-font-truetype`, and
      the RFC 8081 `font/*` family. The corpus's fonts split across the first three, so neither the file extension
      nor any single media type is sufficient, and a substring test for `font` matches none of the 57
      `vnd.ms-opentype` cases.
      → `:document/ua.bookloom.document.epub` · ADR-0026
- [x] 4.3 Move adjudication to run after the OPF is parsed but **before any content document is read and before any
      `Document`, `Unit` or `Segment` is constructed**. Change 3's design put it first to make "no partial import"
      achievable; the guarantee survives because parsing an OPF into memory is not a partial import — no unit and no
      segment exists yet.
      → `:document/ua.bookloom.document.epub` · ADR-0026, EC-EPUB-1, design.md D11
- [x] 4.4 Preserve each zip entry's original compression method on write. `RawEntry` already records
      `entry.getMethod()` and the writer discards it; 26 corpus books deliberately STORE 2,578 already-compressed
      entries, and DD-43 licenses recompressing a *previously-DEFLATED* entry, not converting a stored one.
      → `:document/ua.bookloom.document.epub` · FR-DOC-EPUB-3, FR-DOC-06, DD-43
- [x] 4.5 Move `ZipEntryReader` and `RawEntry` from `ua.bookloom.document.epub` into `ua.bookloom.document.model`
      before touching either, so one zip reader serves both container formats. They are package-private today, so the
      `.fb2.zip` reader of task 6.2 cannot reach them, and the two fixes below are worthless to FB2 if they land
      behind that wall — the resource caps in particular guard nothing if an attacker can choose the other extension.
      → `:document/ua.bookloom.document.model` · design.md D12, `01_MODULE_INVENTORY.md#module-document`
- [x] 4.6 Add container resource limits to that shared reader — entry count, total uncompressed size, per-entry
      compression ratio — returning `ErrorCode.validation` rather than exhausting memory, for **every** zip container
      the system opens. A book file is untrusted input and the reader inflates every entry into memory, so a crafted
      archive currently takes the application down instead of producing an error.
      → `:document/ua.bookloom.document.model` · FR-IMPORT-05, `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`
- [x] 4.7 Retry zip reading with the format's historical default code page when UTF-8 entry-name decoding throws,
      in that same shared reader. Measured directly: `ZipInputStream` with the default charset throws `ZipException:
      invalid LOC header (bad entry name)` on real archives whose names are in a legacy code page, and a Cp437 retry
      reads every entry. Note in the code that the current EPUB corpus does not need this — it is a `.fb2.zip`
      robustness fix, not a corpus-proven requirement.
      → `:document/ua.bookloom.document.model` · FR-IMPORT-05, design.md D12
- [x] 4.8 Cover the corrections: a font-only manifest is allowed whatever algorithm it names, a manifest naming a
      content document is refused with `ErrorCode.validation` and a protected-not-malformed message, an encrypted
      resource absent from the manifest is refused, a font declared `application/x-font-otf` is recognised, a STORED
      entry stays STORED, a DEFLATED entry stays DEFLATED, and an over-limit archive returns `ErrorCode.validation`
      whether it arrives as `.epub` or as `.fb2.zip`. Mark each `// Covers: FR-DOC-EPUB-7` / `FR-DOC-EPUB-3` /
      `FR-IMPORT-05` with its EARS restatement.
      → `:document/ua.bookloom.document.epub` · `modules/document/src/test/java/ua/bookloom/document/epub/`

## 5. Dependencies, encoding resolution and format dispatch

- [x] 5.1 Add commonmark-java, its GFM tables extension, and ICU4J to `gradle/libs.versions.toml`, declared on
      `:document` only, pinning exact versions. Tables are block-level and change segmentation; strikethrough and
      autolink are **deliberately excluded** because they are inline constructs that alter no block boundary and the
      surveyed Markdown corpus contains zero of either. commonmark is BSD-2-Clause; ICU4J's ICU License is an
      allowlisted exception already recorded for `com.ibm.icu:icu4j`.
      → repo root · `gradle/libs.versions.toml`, `modules/document/build.gradle.kts`, `05_Dependencies/03_LICENSING.md`
- [x] 5.2 Add `requires org.commonmark` and `requires com.ibm.icu` to `:document`'s `module-info.java`, keeping both
      out of every other module. A parser reachable from `:ui` is a book's tree reachable from a screen.
      → `:document` · `modules/document/src/main/java/module-info.java`,
      `02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules`
- [x] 5.3 Implement the charset ladder — byte-order mark, then an in-band declaration where the format carries one,
      then ICU charset detection, then UTF-8 — returning the resolved charset and whether a mark was present. The
      order encodes evidence strength: a mark is a fact, a declaration is a claim, detection is a guess. Lands in
      `document.detect`, the package `01_MODULE_INVENTORY.md#module-document` already names for detection.
      → `:document/ua.bookloom.document.detect` · `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, design.md D5
- [x] 5.4 Resolve the book format from the file extension and confirm it against the leading bytes — a zip magic for
      `.epub` and `.fb2.zip`, a `FictionBook` root for `.fb2` — refusing an unrecognised extension, a `.txt.zip`, a
      directory, or a contradicted extension as `ErrorCode.validation` before any parser runs. Extension first is not
      laziness: every valid TXT file is also valid Markdown, so nothing in the bytes separates them.
      → `:document/ua.bookloom.document` · FR-IMPORT-01, FR-IMPORT-05, design.md D1
- [x] 5.5 Replace `DocumentService`'s direct reader/writer fields with dispatch: `open` routes on the resolved
      format, `write` switches on `Document.format()` using an exhaustive switch over `BookFormat` rather than an
      injected handler map. A map turns "you added a format and forgot to bind it" into a null a user discovers; the
      switch makes it a compile error.
      → `:document/ua.bookloom.document` · `01_MODULE_INVENTORY.md#module-document`, ADR-0004, DD-30, design.md D3
- [x] 5.6 Cover dispatch and encoding: an `.fb2` parses as FB2 rather than failing as an invalid EPUB, byte-identical
      `.txt` and `.md` files resolve to different formats, a `.pdf` and a `.txt.zip` and a directory each return
      `ErrorCode.validation` with distinct reasons, a byte-order mark outranks detection, and an EPUB records no
      charset. Mark each `// Covers: FR-IMPORT-01` / `FR-IMPORT-05` with its EARS restatement.
      → `:document/ua.bookloom.document` · `modules/document/src/test/java/ua/bookloom/document/`

## 6. FB2 read

- [x] 6.1 Parse an FB2 book as a single XML document with the `SecureXml` JDOM2 configuration the OPF already uses,
      preserving comments, CDATA, entity spelling, the XML declaration, namespace prefixes as declared, and
      block-level whitespace. Real books declare the link namespace as `l:` or `xlink:` and some redeclare it below
      the root, so a writer with a fixed prefix table rewrites markup it was asked to preserve.
      → `:document/ua.bookloom.document.fb2` · FR-DOC-FB2-1, `03_DOCUMENT_MODEL.md#xml-round-trip-config`, design.md D4
- [x] 6.2 Read a `.fb2.zip` by unpacking its single FB2 member and recording the member name so export can restore
      the wrapper. Refuse the book as **DRM-blocked** when the member is zip-encrypted and as `ErrorCode.validation`
      when the archive contains no FB2 member. FB2 has no encryption manifest, so the zip's own flag is the whole of
      what there is to detect.
      → `:document/ua.bookloom.document.fb2` · FR-DOC-FB2-2, FR-IMPORT-04, EC-DRM-1, FR-IMPORT-05
- [x] 6.3 Honour the FB2 XML declaration's encoding over detection, and refuse the book when the declaration
      disagrees with a confidently detected encoding. The check cannot be a decode attempt: `iso-8859-1` and
      `windows-1252` accept every possible byte, so decoding always succeeds and produces mojibake — which is exactly
      the case the frozen edge case describes.
      → `:document/ua.bookloom.document.fb2` · EC-FB2-2, `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, design.md D5
- [x] 6.4 Emit one unit per `<body>` in document order, with the unit id suffixed by its position
      (`book.fb2#0`, `book.fb2#1`). A book's bodies share one href, while the unit id seeds every segment id and
      `segments.id` is a SQLite primary key, so two units sharing an identity would collide as soon as anything is
      stored.
      → `:document/ua.bookloom.document.fb2` · `06_DATA_MODEL_SQLITE.md#tables`, design.md D10
- [x] 6.5 Walk each body with the shared structural walker, descending into `<cite>`, `<epigraph>`, `<poem>` and
      `<stanza>` to reach the blocks inside them, and emitting no segment for `<empty-line/>`. One corpus book
      carries 3,657 `<empty-line/>` elements; each holds no words and would otherwise become an empty segment.
      → `:document/ua.bookloom.document.fb2` · FR-DOC-FB2-5, FR-DOC-08, EC-VERSE-1, EC-VERSE-2, ADR-0027
- [x] 6.6 Segment the `body name="notes"` footnote bodies while preserving every note id and every `l:href`
      cross-reference that points at one. Footnotes are ordinary prose and translate by default, but the link from
      the text to the note is an id, and rewriting one breaks the jump in a way nothing else in the book reveals.
      → `:document/ua.bookloom.document.fb2` · FR-DOC-FB2-6, EC-FB2-3, EC-VERSE-3
- [x] 6.7 Produce no segment for a `<binary>` element and carry its base64 payload through untouched — never
      re-encoded, re-wrapped or re-line-broken. An FB2 cover is base64 text inside the XML, so it looks like ordinary
      character data to a writer free to re-wrap long content, and re-wrapping rewrites the image without breaking
      anything a structural comparison notices.
      → `:document/ua.bookloom.document.fb2` · FR-DOC-FB2-4, EC-IMG-1
- [x] 6.8 Populate `declaredLang` from `title-info/lang` and the metadata map from `title-info/book-title` and
      `title-info/author`, recording nothing where the source provides nothing. The declared language is what a later
      change compares against the detected language; real books get it wrong or omit it — one corpus FB2 declares
      English on a French book and another declares no language at all.
      → `:document/ua.bookloom.document.fb2` · FR-IMPORT-07, FR-IMPORT-03, EC-LANG-1
- [x] 6.9 Cover the FB2 read path: a comment survives, CDATA stays CDATA, a namespace prefix is preserved, each `<v>`
      is its own segment, a poem without stanzas still yields one segment per line, table cells are segments with the
      shape intact, a `<cite>`'s paragraphs are reached, `<empty-line/>` yields nothing, a note id and its reference
      are unchanged, `<binary>` yields no segment, two bodies get distinct unit ids, `declaredLang` and the
      title/author map are read from `title-info` and left absent when the source omits them, a contradicted
      declaration returns `ErrorCode.validation`, a `.fb2.zip` with an encrypted member is refused with
      `ErrorCode.validation` and a protected-not-corrupt message, and a `.fb2.zip` with no FB2 member returns
      `ErrorCode.validation`. Mark each `// Covers: FR-DOC-FB2-1` / `-2` / `-4` / `-5` / `-6` / `FR-IMPORT-04` /
      `FR-IMPORT-07` / `EC-FB2-2` / `EC-DRM-1` with its EARS restatement.
      → `:document/ua.bookloom.document.fb2` · `modules/document/src/test/java/ua/bookloom/document/fb2/`

## 7. FB2 write

- [x] 7.1 Write each accepted segment's target content back into its run via the shared tree write-back, performing
      every write before serialization begins so no write can perturb resolving a later anchor.
      → `:document/ua.bookloom.document.fb2` · ADR-0025, FR-DOC-03, `03_DOCUMENT_MODEL.md#reassembly`
- [x] 7.2 Decide the output encoding by serializing the reassembled tree and attempting to encode it with the
      declared charset using `CodingErrorAction.REPORT`: keep the declaration on success, switch to UTF-8 and rewrite
      it on failure. Deciding against the real output rather than predicting from the target language is the point —
      a `windows-1251` book translated to Ukrainian is fully representable until one em-dash appears.
      → `:document/ua.bookloom.document.fb2` · FR-DOC-FB2-3, EC-FB2-1, design.md D6
- [x] 7.3 Set the target language by replacing the first `<lang>` inside `<title-info>`, adding one when absent, and
      leave `<src-lang>` and any `<src-title-info>` untouched. FB2 records both what the book is and what it was
      translated from; overwriting the second destroys the only remaining record of the source language. Real books
      use `<src-lang>` far more often than a separate `<src-title-info>` block, so both must be left alone.
      → `:document/ua.bookloom.document.fb2` · FR-DOC-FB2-7, FR-DOC-07
- [x] 7.4 Re-zip a document imported as `.fb2.zip`, preserving the member name, and write a bare `.fb2` for one that
      was not. Export re-emits the container it was given, so a user who imported a zipped book expects one back.
      → `:document/ua.bookloom.document.fb2` · FR-DOC-FB2-2, FR-DOC-06,
      `01_Product/03_DOCUMENT_FORMATS.md#export-same-format-only`
- [x] 7.5 Cover the FB2 write path: a representable translation keeps `windows-1251`, one unrepresentable character
      switches the document to UTF-8 with a rewritten declaration, `title-info`'s `<lang>` is replaced while
      `<src-lang>` is not, a missing `<lang>` is added, and a `.fb2.zip` re-emits as a zip with the same member name.
      Mark each `// Covers: FR-DOC-FB2-3` / `-7` / `-2` / `EC-FB2-1` with its EARS restatement.
      → `:document/ua.bookloom.document.fb2` · `modules/document/src/test/java/ua/bookloom/document/fb2/`

## 8. Markdown

- [x] 8.1 **First, verify source spans.** Parse a probe document containing a table with alignment, a fenced block
      with an info string, a reference-link definition, a nested list and a raw-HTML block with source spans enabled,
      and confirm that every leaf block — **including `TableCell`** — reports a byte range that lands exactly on its
      own text. Cell spans are the one unverified dependency of the whole Markdown design and tables are the most
      common structure in the surveyed corpus; the fallback is locating cells by unescaped-pipe scanning within the
      row's span. Do this before the walker is written against it.
      → `:document/ua.bookloom.document.md` · design.md D4, design.md "Risks", FR-DOC-MD-4
- [x] 8.2 Split a leading `---`-delimited frontmatter block off as raw bytes before the parser sees it, re-emit it
      verbatim ahead of the body, and produce no segment for any key or value. Only the first line may open such a
      block: `---` elsewhere is a thematic break, and the corpus contains 23 of those plus two files showing
      frontmatter *inside* a code fence as an example.
      → `:document/ua.bookloom.document.md` · FR-DOC-MD-3, EC-MD-3, DD-47, design.md D4
- [x] 8.3 Read `lang:` and `title:` out of that same frontmatter block with a flat top-level scan, populating
      `declaredLang` and the metadata map and recording nothing when a key is absent, nested or not a plain scalar.
      Preserving the block and reading it are independent obligations — task 8.2 satisfies the first and nothing yet
      satisfies the second, so a Markdown book currently reaches the import card with no title and no declared
      language. A flat scan rather than a YAML parser: two scalar keys do not justify the largest dependency in the
      module.
      → `:document/ua.bookloom.document.md` · FR-IMPORT-07, FR-IMPORT-03, EC-LANG-1, design.md D4
- [x] 8.4 Emit exactly one unit for a Markdown file, with its id and href both the source file name and its media
      type `text/markdown`. Every segment id is built from the unit id and `segments.id` is a SQLite primary key, so
      the single-unit formats need their identity stated as deliberately as FB2's multi-body case does.
      → `:document/ua.bookloom.document.md` · `06_DATA_MODEL_SQLITE.md#tables`, design.md D10
- [x] 8.5 Walk the parsed tree and emit one segment per translatable **leaf** block — `Paragraph`, `Heading`,
      `TableCell`, a list item's paragraphs, a block quote's paragraphs — and never for a container block. A real
      list item can hold a paragraph, then an indented fenced code block, then more prose; replacing the item's
      extent would destroy the code.
      → `:document/ua.bookloom.document.md` · FR-DOC-MD-1, FR-DOC-MD-4, ADR-0027, design.md D4
- [x] 8.6 Emit no segment for a fenced or indented code block or a raw-HTML block, preserving each through the
      skeleton. Code blocks are excluded rather than marked untranslatable because a segment that exists still costs
      token budget; a raw-HTML block is one opaque literal node with nothing yet to address its inner prose by, and
      translating inside it is deferred to change 5.
      → `:document/ua.bookloom.document.md` · FR-DOC-MD-2, EC-MD-2, DD-49
- [x] 8.7 Anchor each Markdown segment by `ByteSpanAnchor` into the original buffer, trimming the span to the block's
      rendered text so trailing whitespace stays outside it. Nine genuine hard line breaks (two trailing spaces) exist
      in the surveyed corpus and would die invisibly if the replaced range swallowed them.
      → `:document/ua.bookloom.document.md` · ADR-0025, design.md D4
- [x] 8.8 Export by splicing translated spans into a copy of the original bytes, never re-rendering from the tree,
      and add no language metadata. Re-rendering normalises emphasis markers, bullet characters and fence styles in
      parts nobody translated, and would add a trailing newline to the six-in-eight corpus files that lack one.
      → `:document/ua.bookloom.document.md` · ADR-0025, FR-DOC-MD-4, FR-DOC-07, design.md D4
- [x] 8.9 Cover the Markdown path: headings/paragraphs/list items are distinguished, a list item containing a fence
      yields segments only for its prose, table cells are segments and the table is not, a fenced block keeps its
      `java` info string, Markdown-looking content inside a fence yields nothing, a document that is entirely one
      fence yields zero segments, frontmatter survives byte-for-byte, a later `---` is a thematic break, a raw-HTML
      block round-trips, a no-edit round trip is byte-identical, a file without a trailing newline still lacks one,
      a hard line break survives, frontmatter `title`/`lang` reach the metadata map and `declaredLang`, the unit's id
      and media type are the file name and `text/markdown`, and an export with target language `uk` adds no
      frontmatter block and no `lang` key to a file that had none. Mark each `// Covers: FR-DOC-MD-1` / `-2` / `-3` /
      `-4` / `FR-DOC-07` / `FR-IMPORT-07` / `EC-MD-2` / `EC-MD-3` with its EARS restatement.
      → `:document/ua.bookloom.document.md` · `modules/document/src/test/java/ua/bookloom/document/md/`

## 9. TXT

- [x] 9.1 **First, spike ICU4J charset detection.** Run `CharsetDetector` against a hand-authored `windows-1251`
      CRLF sample and report the detected charset and confidence. Two common Python detectors returned the wrong
      codec for all three real `windows-1251` books — one at 0.637 confidence — and TXT has no declaration to
      cross-check against, so detection is the only signal. If ICU is also wrong, stop and re-decide before writing
      the reader rather than after.
      → `:document/ua.bookloom.document.detect` · design.md D5, `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`
- [x] 9.2 Model the TXT skeleton as the original byte buffer plus paragraph byte spans, scanning for blank-line
      boundaries, emitting one `PARAGRAPH` segment per paragraph, and producing no segment for a run of blank lines.
      Real files contain runs of two or more blank lines — one has 762 of them — which must not become empty
      segments. Emit exactly one unit for the file, with its id and href both the source file name and its media type
      `text/plain`, for the same reason the Markdown unit needs one: the unit id seeds every segment id, which is a
      SQLite primary key.
      → `:document/ua.bookloom.document.txt` · FR-DOC-TXT-1, FR-DOC-TXT-2, ADR-0025, design.md D2, design.md D10
- [x] 9.3 Export by splicing translated spans into a copy of the original buffer in one ascending pass, and ignore
      the target-language argument. TXT has nowhere to record a language, and a header line invented for the purpose
      would add content the source never had and break the byte-exact round trip for a field nothing reads.
      → `:document/ua.bookloom.document.txt` · FR-DOC-TXT-3, FR-DOC-03, FR-DOC-07
- [x] 9.4 Refuse the export with `ErrorCode.validation`, writing no file, when target text contains a character the
      source encoding cannot represent. FB2 solves this by switching to UTF-8 and rewriting its declaration; plain
      text has no declaration, so a silent re-encode makes every non-Latin character unreadable to whatever opens it
      next.
      → `:document/ua.bookloom.document.txt` · FR-DOC-TXT-3, `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`
- [x] 9.5 Cover the TXT path: blank lines separate paragraphs, a run of blank lines yields no extra segment, CRLF and
      four-space indentation survive, a no-edit round trip is byte-identical including its byte-order mark, only the
      translated paragraph's bytes change, an unrepresentable target character fails the export with no file written,
      the unit's id and media type are the file name and `text/plain`, and an export passed target language `uk` is
      still byte-identical to the source. Mark each `// Covers: FR-DOC-TXT-1` / `-2` / `-3` / `FR-DOC-07` with its
      EARS restatement.
      → `:document/ua.bookloom.document.txt` · `modules/document/src/test/java/ua/bookloom/document/txt/`

## 10. Fixtures

**Fixtures are built in Java, not committed as files.** `modules/document/src/test/resources/` does not exist and is
not created: change 3 established `EpubZipBuilder` + `PrimaryFixtureEpub` writing a real file into a JUnit
`@TempDir`, and every fixture below follows that pattern. Two reasons, and the second is the binding one. A builder
is *reviewable* — a diff shows which trap the fixture sets, where a committed `.epub` is a binary blob nobody reads.
And a committed file cannot survive this repository: a `windows-1251` FB2 and a BOM + CRLF + trailing-whitespace TXT
are fixtures whose entire content *is* a byte sequence, and an editor, `core.autocrlf`, or a Spotless run would
silently rewrite any of them. `xml.getBytes(Charset.forName("windows-1251"))` and `"﻿" + "One.\r\n\r\n"` cannot
be normalised by anything. If a committed binary ever does become necessary, it needs a `.gitattributes` `-text`
entry in the same commit — but nothing in this change needs one.

- [x] 10.1 Author the EPUB fixtures **in this repository**: a `div`-as-paragraph book with zero `<p>`, a book whose
      `<p>` are all `<br/>` spacers with prose in divs, a book with `<br/>`-delimited runs, and a book whose
      paragraphs are wrapped `p > span > i`. The first two reproduce the 42-book zero-segment case and defeat a
      "has `<p>` therefore fine" heuristic; the last is the inline-markup shape **no existing fixture contains**.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/fixture/`, ADR-0027, ADR-0025, design.md D8
- [x] 10.2 Author the EPUB encryption fixtures: one whose `encryption.xml` names only fonts declared
      `application/x-font-otf` under the algorithm `http://ns.adobe.com/pdf/enc#RC`, and one naming a content
      document. The refusal path has **no natural example** — a 194-book survey found zero genuinely DRM-protected
      books — so it is provable only by a hand-authored fixture.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/fixture/`, ADR-0026, EC-EPUB-1
- [x] 10.3 Author an EPUB fixture with deliberately STORED entries alongside DEFLATED ones, so the compression-method
      preservation is asserted rather than assumed.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/fixture/`, FR-DOC-EPUB-3, DD-43
- [x] 10.4 Author the FB2 fixtures: a **`windows-1251`** primary carrying a `<binary>` cover, a `<poem>` with
      `<stanza>` and `<v>`, a `body name="notes"` with a cross-referenced note, a `<table>`, a CDATA section, an XML
      comment, a named entity, `<lang>` and `<src-lang>`, and an `<empty-line/>`; plus an encoding-switch fixture; plus
      the primary wrapped as `.fb2.zip`; plus one with `<br/>`-delimited runs. **Five of these shapes have no natural
      example** in the surveyed corpus — every real FB2 there is UTF-8, none has a notes body, a table, CDATA or a
      comment — so the requirements covering them are provable only here.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/fixture/`, EC-FB2-1, EC-FB2-3, EC-FB2-4, design.md D8
- [x] 10.5 Author the Markdown and TXT fixtures: a Markdown file with frontmatter, a fenced block with an info
      string, an indented code block, a table with alignment, a reference-link definition, a raw-HTML block, a list
      item containing a fence, nested lists, a blockquote, a hard line break, a thematic break, and no trailing
      newline; and a TXT file with a byte-order mark, CRLF endings, runs of blank lines, indentation and trailing
      whitespace. Each element is there because it is something a renderer or re-encoder would quietly normalise.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/fixture/`, EC-MD-2, EC-MD-3, design.md D8
- [x] 10.6 Author the refusal fixtures: an FB2 that is not well-formed XML, a `.fb2.zip` with no FB2 member, a
      `.fb2.zip` with an encrypted member, an FB2 declaring `windows-1252` over Cyrillic bytes, an archive exceeding
      the resource limits, and a file with an unrecognised extension. Each exists to make one failure path provable
      rather than argued.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/fixture/`, EC-FB2-2, EC-DRM-1, FR-IMPORT-05
- [x] 10.7 Author three **combination** fixtures, each stacking pathologies the way a real book does rather than
      isolating one: an EPUB whose prose is `<div class="paragraph">` **and** wrapped `p > span > i` **and** split by
      `<br/>` runs, with a `<pre>` listing and an image-only paragraph among them; an FB2 in `windows-1251` whose
      notes body contains a poem whose `<v>` lines carry inline markup, alongside a `<binary>` cover and a CDATA
      section; and a Markdown file whose list item holds a paragraph, a fenced block, a table and a hard line break
      under frontmatter. Every fixture above isolates one shape, and every shape passed in isolation while the
      shipped code corrupted real books — the survey's finding is that the failures are *interactions*: `<br/>`
      splitting is unreachable until structural recognition lands, and write-back only corrupts once a run and inline
      markup coincide.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/fixture/`, ADR-0025, ADR-0027, design.md D8
- [x] 10.8 Expose every fixture through one `FixtureCatalog` enumerating each as a named case carrying its builder,
      its format, its comparison mode and its **declared** expectations — expected segment count, expected kinds, and
      coverage floor — written out per case, never computed. Declared rather than derived because a catalogue that
      asked the walker how many segments it produces would agree with the walker whatever the walker did, which is
      the anti-tautology rule in `.claude/rules/testing.md` and the exact failure that let a 73.74%-coverage walker
      look correct. The catalogue is also what makes task 11.8 a gate that a *new* fixture joins automatically.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/fixture/`, `.claude/rules/testing.md`

## 11. Canonical comparisons, goldens and the coverage gate

- [x] 11.1 Implement the FB2 canonical comparison: re-parse and serialize through a fixed canonical XML writer and
      compare as strings, **plus** compare the declared encoding as a *value*, **plus** compare every `<binary>`
      element's text exactly. The extras are not belt-and-braces — a canonical writer normalises the declaration away
      so EC-FB2-1 would be unprovable through it, and it may re-wrap long text and silently rewrite a cover image.
      Compare the encoding value rather than the declaration text, because a real book writes it single-quoted while
      JDOM2 emits double quotes.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/golden/`, DD-43, design.md D7
- [x] 11.2 Implement the Markdown comparison as a **deterministic structural serialization** of the re-parsed tree —
      node type, literal text and the fields that affect rendering, depth-first — and never as an equality check on
      two tree objects. Syntax-tree nodes inherit identity equality, so asserting equality compares two references
      and passes only when they are the same object: a test written that way fails to fail.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/golden/`, DD-43, design.md D7
- [x] 11.3 Implement the TXT comparison as **exact bytes**, byte-order mark and line endings included. TXT is the one
      format whose *golden* comparison is byte equality — the frozen round-trip requirement names re-parse-equal AST
      for Markdown and canonical XML for FB2, and `.claude/rules/testing.md` rejects a golden that asserts raw bytes
      for a structured format. Markdown's no-edit round trip is byte-identical too, but that is a property of the
      splice mechanism and is asserted as its own named test in 11.7, not as the Markdown golden.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/golden/`, DD-43, FR-DOC-TXT-3,
      `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`
- [x] 11.4 Implement the text-coverage measurement — the proportion of a fixture's visible text covered by emitted
      segments, excluding deliberately excluded blocks — and assert it in **every** format's golden test with the
      threshold and excluded-block budget stated next to it. Every other document assertion checks that nothing was
      damaged, which a book yielding zero segments satisfies perfectly; this is the only one that checks something
      happened.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/golden/`, ADR-0027, FR-DOC-01
- [x] 11.5 Add the three golden round-trip tests — parse each format's primary fixture, reassemble with **zero**
      segment edits, and assert that format's own comparison plus its coverage threshold — with the encoding-switch
      FB2 fixture excluded from the source-language golden and asserted against a re-parsed canonical tree instead.
      Mark each `// Covers: FR-DOC-09` with its EARS restatement, and assert raw-byte equality in no golden except
      TXT's.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/golden/`, FR-DOC-09,
      `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`
- [x] 11.6 Re-run the EPUB golden against the new segmentation and anchors, and add its coverage assertion. EPUB is
      the only format with a shipped golden, so it is the one that proves the structural walker and the run anchors
      did not regress a format that already worked.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/golden/`, FR-DOC-09
- [x] 11.7 Assert the individual traps as **named tests** beside the goldens, so a fixture that stops covering one
      fails a test that says which. This matters most for Markdown, where tree equality is deliberately blind to what
      a renderer normalises: assert the fence info string, reference-link definitions, table alignment, the hard line
      break and the absent trailing newline, alongside FB2's binary payload, declaration and note ids, and TXT's
      byte-order mark and line endings.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/golden/`, design.md "Risks"
- [x] 11.8 Add the fixture sweep: a `@ParameterizedTest` over `FixtureCatalog` that writes each fixture to a
      `@TempDir`, opens it through `DocumentPort`, reassembles it with zero segment edits, and asserts that case's
      declared segment count and kinds, its format's canonical comparison and its coverage floor — one real file
      through the real port per case, nothing mocked. Use `@MethodSource`, never a loop in the test body
      (`.claude/rules/testing.md` bans control flow in a test method, because a loop that skips a case reports green).
      The named per-format goldens above stay: they say *which* obligation broke, where the sweep says *which
      fixture* broke. What the sweep adds is that a fixture registered in the catalogue can never be authored and
      then left ungated — which is how the inline-markup shape came to have no test at all.
      Mark it `// Covers: FR-DOC-09` with its EARS restatement.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/golden/`, FR-DOC-09, ADR-0027

## 12. Green gate

- [x] 12.1 Run `./gradlew spotlessApply`, then regenerate and commit lock state with `./gradlew resolveAndLockAll
      --write-locks`, because commonmark-java, its tables extension and ICU4J are new coordinates. Unlike `:app`,
      `:document` locks normally, so a missing lockfile diff here is a real problem rather than the expected outcome.
      → repo root · `01_BUILD_AND_TOOLING.md#dependency-locking`, task 5.1
- [x] 12.2 Run `./gradlew :build-logic:clean` before the gate: `build-logic` is an included build, so the root
      `clean` does not reach it and its canary suite can report `UP-TO-DATE` from a stale cache while the run still
      prints `BUILD SUCCESSFUL`.
      → repo root · `01_MODULE_INVENTORY.md#as-built-baseline`, `AGENTS.md` "What will bite you"
- [x] 12.3 Run `./gradlew clean build check spotlessCheck` and confirm it is green across the whole project,
      including the eight ArchUnit rules — which now police two new parser dependencies that must not have leaked
      past `:document`, and an `:api` type that changed from a record to a sealed interface and must still satisfy
      `records-first` (the rule exempts interfaces; the two permitted implementations are records). No "pre-existing
      failure" exemption; paste the tail as evidence.
      → whole project · `06_DEFINITION_OF_DONE.md#per-change-checklist`, `.claude/rules/gradle-build-and-quality.md`
- [x] 12.4 Run `./gradlew -PstrictLocks verifyLocks` and `./gradlew checkLicense`. The licence gate matters here
      because ICU4J ships under the **ICU License** — a recorded exception rather than the default Apache/MIT/BSD
      family — which is exactly the case the gate exists to adjudicate rather than assume.
      → whole project · `01_BUILD_AND_TOOLING.md#dependency-locking`, `05_Dependencies/03_LICENSING.md`
- [x] 12.5 Update `01_MODULE_INVENTORY.md#as-built-baseline` to record that `ua.bookloom.document.fb2`,
      `.md`, `.txt` and `.detect` now exist and that `ua.bookloom.document` performs the format dispatch its
      `#inventory` row has always claimed. The inventory is the citation target every later change resolves against,
      so a stale row is a broken citation in every proposal that follows.
      → `docs/implementation_plan/` · `01_MODULE_INVENTORY.md#as-built-baseline`
- [x] 12.6 Confirm the offline invariant, which this change makes measurable rather than assumed: 53 books in the
      surveyed corpus rely on XHTML named entities under a DOCTYPE, so verify the XHTML parser resolves `&nbsp;`
      **without fetching a DTD over the network**, that `SecureXml` still disables external entity and DTD loading on
      the FB2 path, and that neither commonmark-java nor ICU4J performs I/O. An XML parser fetching an external DTD
      is a network call no ArchUnit rule would catch.
      → whole project · `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, DD-01, NFR-PRIV-01
- [x] 12.7 Verify the build is independent of `.temporary_context/`: grep the whole repository for that path and
      confirm zero references in any source, test, resource, Gradle script or CI workflow, and confirm `.gitignore`
      still excludes it and `git ls-files` reports nothing under it. That directory holds copyrighted books and
      exists only on one machine; a test or fixture that reached into it would pass locally and fail every clean
      checkout and every CI run with a missing-file error.
      → whole project · `.gitignore`, `06_DEFINITION_OF_DONE.md#per-change-checklist`
- [x] 12.8 **Optional, local only:** run the built code against the real-book corpus in `.temporary_context/` and
      record what happened in the change's notes — how many books open, how many produce zero segments, whether any
      is refused as DRM. This is a manual sanity check on real data, not a gate: it must be skippable with no effect
      on the build, and nothing it produces may be committed.
      → local machine · design.md D8, ADR-0027
- [x] 12.9 Run `openspec validate add-fb2-md-txt-roundtrip --strict`, confirm it is clean, then archive — folding the
      four modified and the new requirements into `openspec/specs/document-round-trip/`. Afterwards run
      `bash scripts/fr-coverage.sh` and note which `FR-DOC-*` ids the ledger now covers; the output is advisory, and
      a remaining gap mid-build-out is expected rather than a failure.
      → `openspec/` · ADR-0016, `.claude/rules/spec-authoring.md`
