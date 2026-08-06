# document-round-trip

Opening a book, taking it apart into an immutable skeleton plus an ordered list of translatable segments, and putting
it back together in the same format such that a no-edit round trip is canonical-equal to the source.

This delta establishes the capability and covers **EPUB only**. FB2, Markdown and TXT extend it in the next change;
inline masking and the placeholder gate extend it in the one after.

## ADDED Requirements

### Requirement: Parse a book into a skeleton and an ordered segment list

The system SHALL parse an opened book into one immutable skeleton per content unit plus an ordered list of
translatable segments, where each segment is the translatable inner content of a single block-level element and
carries a stable identity, its position in document order, and its kind.

Source: FR-DOC-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md`), DD-07,
`02_Architecture/03_DOCUMENT_MODEL.md#data-model`.
In plain words: a book stops being a file and becomes two things — the structure, which is frozen, and a numbered list
of the bits of text that may be translated. Everything downstream addresses text by segment id, so the numbering has
to exist before anything can translate, store, review or export.

#### Scenario: A chapter with three paragraphs yields three ordered segments

- **WHEN** a spine document `OEBPS/chapter01.xhtml` containing exactly three `<p>` elements is parsed
- **THEN** the unit for that document carries exactly three segments
- **AND** their ids are `OEBPS/chapter01.xhtml:0`, `OEBPS/chapter01.xhtml:1` and `OEBPS/chapter01.xhtml:2`
- **AND** their `order` values are `0`, `1` and `2`, matching the order the paragraphs appear in the document

#### Scenario: Block kinds are distinguished

- **WHEN** a document contains `<h1>Chapter One</h1>`, `<p>Prose.</p>` and `<li>An item</li>`
- **THEN** the three segments carry kinds `HEADING`, `PARAGRAPH` and `LIST_ITEM` respectively

#### Scenario: Segments know their document-order neighbours

- **WHEN** a unit yields segments `unit:0`, `unit:1` and `unit:2`
- **THEN** `unit:1` reports `prevKey` = `unit:0` and `nextKey` = `unit:2`
- **AND** `unit:0` reports a `prevKey` of `null` and `unit:2` reports a `nextKey` of `null`

### Requirement: Address the skeleton by stable anchor, never by offset

Each segment SHALL locate its source text within the skeleton by a stable node path plus child index, and the system
SHALL NOT use a byte offset, a character offset, or an inserted sentinel node for that purpose.

Source: DD-07, `02_Architecture/03_DOCUMENT_MODEL.md#data-model` ("a stable node path/id plus child index … not a byte
offset and not an inserted sentinel node").
In plain words: the segment has to remember exactly which slot in the structure it came from, so a translation can be
written back into that same slot. A byte offset would be invalidated by the first edit of any earlier segment, and a
sentinel node would mean the parser modified the structure it promised not to touch.

#### Scenario: Writing back an earlier segment does not invalidate a later anchor

- **WHEN** a unit's segment `unit:0` receives target text `Розділ перший` that is longer than its source `Chapter One`
- **AND** segment `unit:5` is then written back
- **THEN** `unit:5`'s target text lands in the same element it was parsed from
- **AND** no segment's anchor is recomputed between the two writes

#### Scenario: Parsing adds no nodes to the skeleton

- **WHEN** a document containing 42 elements is parsed
- **THEN** the resulting skeleton contains exactly 42 elements
- **AND** no element carries an attribute that was not present in the source

### Requirement: Exclude non-translatable blocks from segmentation

The system SHALL produce no segment for a `<pre>` block, a `<pre><code>` code listing, or a block-level MathML
`<math>` element, and SHALL preserve each of them through the skeleton alone.

Source: DD-49, `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, FR-DOC-EPUB-9.
In plain words: source code and mathematics must not be translated — renaming a variable or altering an equation
corrupts a technical book. They are excluded from the segment list entirely rather than marked as "do not translate",
because a segment that exists still consumes the token budget and still has to be explained to the model.

#### Scenario: A code listing produces no segment

- **WHEN** a document contains `<p>Before.</p><pre><code>int x = 1;</code></pre><p>After.</p>`
- **THEN** exactly two segments are produced, for `Before.` and `After.`
- **AND** neither segment's `sourceInner` contains `int x = 1;`

#### Scenario: The excluded listing survives the round trip verbatim

- **WHEN** that document is reassembled with zero segment edits
- **THEN** the output contains `<pre><code>int x = 1;</code></pre>` with its text unchanged

#### Scenario: Block-level MathML produces no segment

- **WHEN** a document contains a block-level `<math>` element with an `<mi>x</mi>` child
- **THEN** no segment is produced for it, and its content is unchanged in the reassembled output

### Requirement: Never regenerate the skeleton

The system SHALL alter only text nodes when writing translated content back, and SHALL NOT regenerate, rebuild, or
restructure the skeleton.

Source: FR-DOC-03, `02_Architecture/03_DOCUMENT_MODEL.md#reassembly`.
In plain words: reassembly edits the leaves of the tree and nothing else. Re-serializing the parsed tree on export is
expected and fine; building a new tree from the segments is not, because everything the segments do not capture —
attributes, comments, structure, ordering — would be lost in the rebuild.

#### Scenario: Element identity survives reassembly

- **WHEN** a document containing `<p id="ch01-p07" class="first">Text.</p>` is parsed and reassembled with zero edits
- **THEN** the output element still carries `id="ch01-p07"` and `class="first"`

#### Scenario: Comments and structural whitespace survive

- **WHEN** a document containing an XML comment between two block elements is reassembled
- **THEN** the comment is present in the output, between the same two elements

### Requirement: Read EPUB content in spine order

The system SHALL read `META-INF/container.xml` to locate the OPF package document, parse the OPF, and process content
documents in the order the spine declares, supporting both EPUB 2 and EPUB 3 structural variants.

Source: FR-DOC-EPUB-1, FR-DOC-EPUB-2 (`01_Product/03_DOCUMENT_FORMATS.md#epub`), FR-IMPORT-01.
In plain words: the spine is the book's reading order, and it is not the same as the order files happen to sit in the
zip. Translating in zip order would produce a book whose context — and whose progress indicator — runs in the wrong
sequence.

#### Scenario: Spine order is followed, not zip order

- **WHEN** an EPUB whose zip entries appear as `OEBPS/c02.xhtml` then `OEBPS/c01.xhtml`, and whose spine declares
  `c01` then `c02`, is parsed
- **THEN** the document's units appear in the order `OEBPS/c01.xhtml`, `OEBPS/c02.xhtml`
- **AND** their `order` values are `0` and `1` respectively

#### Scenario: An EPUB 2 book with an NCX parses

- **WHEN** an EPUB 2 book carrying an NCX and no nav document is parsed
- **THEN** parsing succeeds and its spine documents are read in declared order

### Requirement: Preserve out-of-spine resources verbatim

The system SHALL carry every resource not listed in the spine through to the output unchanged, and SHALL produce no
segments for it.

Source: EC-EPUB-3 (`01_Product/03_DOCUMENT_FORMATS.md#epub-edge-cases`).
In plain words: stylesheets, images, fonts and any stray content document that the spine does not reference are
carried, not read. The nav document and NCX are carved out of this rule so their table-of-contents labels can be
translated, but that carve-out is not exercised by this change.

#### Scenario: A stylesheet is carried but not segmented

- **WHEN** an EPUB containing `OEBPS/styles.css` is parsed
- **THEN** no segment references `OEBPS/styles.css`
- **AND** the reassembled output contains `OEBPS/styles.css` with identical decompressed bytes

### Requirement: Repackage with mimetype first and stored

On export the system SHALL write the `mimetype` entry first and STORED (uncompressed), preserve the order of the
remaining entries, and preserve the identity of every entry it did not translate.

Source: FR-DOC-EPUB-3, FR-DOC-06, `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`.
In plain words: an EPUB whose `mimetype` entry is compressed or not first is not a valid EPUB — readers reject it, and
the rejection message never mentions compression. This is the single most easily broken rule in the format and the one
a naive re-zip breaks by default.

#### Scenario: The mimetype entry is first and uncompressed

- **WHEN** a parsed EPUB is reassembled and written
- **THEN** the first zip entry of the output is named `mimetype`
- **AND** its compression method is STORED
- **AND** its content is exactly `application/epub+zip`

#### Scenario: Entry order is preserved

- **WHEN** an EPUB whose entries follow the order `mimetype`, `META-INF/container.xml`, `OEBPS/content.opf`,
  `OEBPS/c01.xhtml` is reassembled
- **THEN** the output's entries appear in that same order

### Requirement: Preserve ids, cross-references, images and fonts

The system SHALL preserve every element id, internal cross-reference target, image and embedded font unchanged across
a round trip, and SHALL NOT rewrite an id even when it is duplicated or non-unique in the source.

Source: FR-DOC-EPUB-4, FR-DOC-02, EC-EPUB-4, `01_Product/03_DOCUMENT_FORMATS.md#images-and-fonts`.
In plain words: ids are what footnote links, cross-references and the table of contents point at, so rewriting one
silently breaks navigation somewhere else in the book. A duplicate id is the source's problem, and "fixing" it would
break whichever link happened to depend on the duplication.

#### Scenario: A duplicate id is preserved rather than corrected

- **WHEN** a document contains two elements both carrying `id="note1"`
- **THEN** the reassembled output still contains two elements carrying `id="note1"`

#### Scenario: An embedded font is carried through byte-for-byte

- **WHEN** an EPUB containing `OEBPS/fonts/serif.otf` is reassembled
- **THEN** the output entry `OEBPS/fonts/serif.otf` has decompressed bytes identical to the source's

### Requirement: Set the target language by replacing the first dc:language

On export the system SHALL set the target language by replacing the first `dc:language` element in the OPF, adding one
if none is present, and SHALL leave any further `dc:language` elements untouched.

Source: FR-DOC-07, FR-DOC-EPUB-6, `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`.
In plain words: a translated book that still declares itself English will be read aloud, hyphenated and spell-checked
as English. Only the first entry is replaced because later entries may legitimately record other languages present in
the book, and overwriting all of them would discard that.

#### Scenario: The first entry is replaced and the second is left alone

- **WHEN** an OPF declaring `<dc:language>en</dc:language>` then `<dc:language>la</dc:language>` is exported with
  target language `uk`
- **THEN** the output OPF declares `<dc:language>uk</dc:language>` then `<dc:language>la</dc:language>`

#### Scenario: A missing declaration is added

- **WHEN** an OPF declaring no `dc:language` at all is exported with target language `uk`
- **THEN** the output OPF contains exactly one `dc:language` element, with the value `uk`

### Requirement: Refuse DRM but allow known font obfuscation

IF `META-INF/encryption.xml` declares content encryption or any algorithm the system does not recognise, THEN the
system SHALL refuse the book with a DRM-blocked outcome and SHALL NOT import any part of it. IF that file declares
only the two known IDPF font-obfuscation algorithms, THEN the system SHALL process the book normally and carry the
obfuscated font bytes through unchanged.

Source: FR-DOC-EPUB-7, EC-EPUB-1, EC-FONT-1, FR-IMPORT-04.
In plain words: encrypted content cannot be translated and must not be half-imported. Font obfuscation looks like
encryption in the same file but is not — it is a routine embedding technique, and refusing books that use it would
reject a large share of ordinary EPUBs.

#### Scenario: Content encryption refuses the whole book

- **WHEN** an EPUB whose `META-INF/encryption.xml` declares `http://www.w3.org/2001/04/xmlenc#aes256-cbc` over a
  spine document is opened
- **THEN** the result is a failure identifying the book as DRM-protected
- **AND** no unit and no segment is produced

#### Scenario: An unknown algorithm is treated as DRM

- **WHEN** an EPUB whose `META-INF/encryption.xml` declares an algorithm URI the system does not recognise is opened
- **THEN** the result is a DRM-protected failure rather than a partial import

#### Scenario: IDPF font obfuscation is allowed

- **WHEN** an EPUB whose `META-INF/encryption.xml` declares only `http://www.idpf.org/2008/embedding` is opened
- **THEN** parsing succeeds and produces segments
- **AND** the reassembled output carries the obfuscated font bytes unchanged

### Requirement: Reject a corrupt container without partial import

IF the container, the OPF, or the spine is missing or malformed, THEN the system SHALL return a validation failure
naming the reason and SHALL NOT produce a partially parsed document.

Source: EC-EPUB-2, `02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`.
In plain words: half a book is worse than no book, because the user cannot tell which half is missing. Failing whole
means the error is visible at import rather than discovered as absent chapters after a long translation run.

#### Scenario: A missing OPF is a validation failure

- **WHEN** a file whose `META-INF/container.xml` points at `OEBPS/content.opf`, which the archive does not contain, is
  opened
- **THEN** the result carries `ErrorCode.validation`
- **AND** no document is returned

#### Scenario: A file that is not a zip archive is a validation failure

- **WHEN** a file named `book.epub` whose contents are plain text is opened
- **THEN** the result carries `ErrorCode.validation`

#### Scenario: The failure carries no filesystem path in its details

- **WHEN** any parse failure is returned
- **THEN** the error's `details` field does not contain the source file's path
- **AND** the originating exception is carried on the error's `cause` instead

### Requirement: Pass a golden round-trip test for EPUB

The system SHALL pass a golden round-trip test in which a fixture EPUB is parsed and reassembled with zero segment
edits, and the output is **canonical-equal** to the source: text entries compared by decompressed canonical content,
entry order preserved, `mimetype` first and STORED, and unchanged binary entries compared by decompressed bytes.

Source: FR-DOC-09, DD-43, `02_Architecture/03_DOCUMENT_MODEL.md#golden-round-trip-test`,
`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`.
In plain words: this is the test that makes "we preserve the book" a fact rather than an intention. It compares
canonicalized forms rather than raw bytes because a faithful re-serializer legitimately normalizes entity spelling,
attribute quoting and zip compression — insisting on identical bytes would fail for reasons that harm no reader.

#### Scenario: A no-edit round trip is canonical-equal

- **WHEN** a fixture EPUB is parsed and reassembled with no segment receiving target text
- **THEN** every text entry of the output re-parses to the same canonical form as the corresponding source entry
- **AND** every binary entry has identical decompressed bytes
- **AND** the entry order and the stored-first `mimetype` match the source

#### Scenario: Byte equality is not the assertion

- **WHEN** the reassembled output differs from the source only in zip compression level or entity spelling
- **THEN** the golden test still passes

### Requirement: Compute a content hash of the imported source

The system SHALL compute a SHA-256 hash over the imported source file and carry it on the parsed document, and SHALL
compute a separate SHA-256 hash over each segment's pre-mask inner content.

Source: FR-IMPORT-08, `02_Architecture/03_DOCUMENT_MODEL.md#data-model`.
In plain words: the document hash answers "is this the same file I imported last time", which is what makes resuming a
run safe. The per-segment hash answers the same question one paragraph at a time, so an edited book can reuse the
translations of the parts that did not change.

#### Scenario: The same file yields the same document hash

- **WHEN** the same EPUB file is parsed twice
- **THEN** both parses report an identical document content hash

#### Scenario: Segments carry their own hash

- **WHEN** two segments in a book have byte-identical inner content
- **THEN** they report the same `sourceHash`
- **AND** a segment whose inner content differs by one character reports a different `sourceHash`

### Requirement: Return failures through the typed envelope

Every operation the document port exposes SHALL return the shared result envelope carrying either data or one typed
error, and SHALL NOT allow an exception to escape across the module boundary.

Source: `02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`, DD-14.
In plain words: callers branch on a typed code rather than catching exceptions, so a parse failure reaches the user as
a message the UI already knows how to render, instead of as a stack trace nobody can act on.

#### Scenario: An unexpected failure is wrapped rather than thrown

- **WHEN** parsing fails with an unforeseen runtime exception
- **THEN** the caller receives a failed result carrying `ErrorCode.internal`
- **AND** the original exception is available on the error's `cause`
- **AND** no exception propagates out of the port method
