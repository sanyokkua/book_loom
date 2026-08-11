## MODIFIED Requirements

### Requirement: Parse a book into a skeleton and an ordered segment list

The system SHALL parse an opened book into one immutable skeleton per content unit plus an ordered list of
translatable segments, where each segment is the translatable inner content of one **line-break-delimited run**
within a single block-level element — a block containing no line break having exactly one such run — and carries a
stable identity, its position in document order, and its kind.

Source: FR-DOC-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-07,
`02_Architecture/03_DOCUMENT_MODEL.md#data-model`, ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`).
In plain words: a book stops being a file and becomes two things — the structure, which is frozen, and a numbered list
of the bits of text that may be translated. Everything downstream addresses text by segment id, so the numbering has
to exist before anything can translate, store, review or export. The unit of that numbering is a *run* rather than a
whole block because some converters write an entire chapter as one element whose paragraphs are separated only by
line breaks; treating such a block as one segment produces a segment no model can accept. For the overwhelming
majority of blocks — every one containing no line break — the run and the block are the same thing.

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

#### Scenario: Only the body kinds this change can produce are emitted

- **WHEN** any of the four supported formats is parsed by this change
- **THEN** every emitted segment's kind is one of `PARAGRAPH`, `HEADING`, `VERSE_LINE`, `LIST_ITEM` or `TABLE_CELL`
- **AND** no segment carries the kind `FOOTNOTE`, `CAPTION` or `TITLE`
- **AND** no segment carries a metadata-unit kind such as `METADATA_TITLE` or `FRONTMATTER_VALUE`

### Requirement: Address the skeleton by stable anchor, never by offset

Each segment of a **tree-shaped** skeleton — an EPUB spine document, an FB2 XML document — SHALL locate its source
text by a stable node path plus the index of its line-break-delimited run within that block, and SHALL NOT use a
byte offset, a character offset, or an inserted sentinel node for that purpose. WHERE a format's skeleton is the
original byte buffer rather than a tree, a segment SHALL instead locate its source text by a byte span into that
buffer, and reassembly SHALL copy from the unmodified original buffer into fresh output rather than editing that
buffer in place, so that no segment's span is invalidated by writing back any other segment.

Source: DD-07, `02_Architecture/03_DOCUMENT_MODEL.md#data-model` ("a stable node path/id plus child index … not a byte
offset and not an inserted sentinel node"), FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`).
In plain words: the segment has to remember exactly which slot in the structure it came from, so a translation can be
written back into that same slot. In a tree, a byte offset would be invalidated by the first edit of any earlier
segment, and a sentinel node would mean the parser modified the structure it promised not to touch. Plain text and
Markdown have no tree to point into, so their slots *are* byte spans — and they stay valid for exactly one reason:
nothing ever writes into the buffer they index. The moment reassembly edited that buffer in place, every later span
would be wrong, which is why the copy-from-original rule is part of the requirement rather than an implementation
detail.

#### Scenario: Writing back an earlier segment does not invalidate a later anchor

- **WHEN** a unit's segment `unit:0` receives target text `Розділ перший` that is longer than its source `Chapter One`
- **AND** segment `unit:5` is then written back
- **THEN** `unit:5`'s target text lands in the same element it was parsed from
- **AND** no segment's anchor is recomputed between the two writes

#### Scenario: Parsing adds no nodes to the skeleton

- **WHEN** a document containing 42 elements is parsed
- **THEN** the resulting skeleton contains exactly 42 elements
- **AND** no element carries an attribute that was not present in the source

#### Scenario: A run index distinguishes segments sharing one block

- **WHEN** a block `<div>Розділ перший<br/>Еней був парубок<br/>І хлопець хоть куди</div>` is parsed
- **THEN** three segments are produced, all carrying the same node path
- **AND** their run indices are `0`, `1` and `2`

#### Scenario: An ordinary block carries run index zero

- **WHEN** a block `<p>Prose with no line break.</p>` is parsed
- **THEN** exactly one segment is produced for it, with run index `0`

#### Scenario: A plain-text segment is addressed by byte span

- **WHEN** a TXT file whose bytes are `Alpha.\n\nBeta.\n` is parsed
- **THEN** the first segment's anchor is the byte span `[0, 6)` and the second's is `[8, 13)`
- **AND** neither segment carries a node path

#### Scenario: Two buffer write-backs do not disturb each other

- **WHEN** a TXT file whose bytes are `Alpha.\n\nBeta.\n` has its first segment written back as
  `A considerably longer first paragraph.` and its second written back as `Beta translated.`
- **THEN** the output is `A considerably longer first paragraph.\n\nBeta translated.\n`
- **AND** the second segment's byte span is unchanged from the value computed at parse time

### Requirement: Never regenerate the skeleton

The system SHALL, when writing translated content back into a tree-shaped skeleton, replace only the inner content
of the segment's own run within its block, and SHALL NOT add, remove, reorder or re-serialize any other node. The
system SHALL NOT regenerate, rebuild, or restructure the skeleton.

Source: FR-DOC-03, `02_Architecture/03_DOCUMENT_MODEL.md#reassembly`, ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`).
In plain words: reassembly edits the content of one run and nothing else. Re-serializing the parsed tree on export is
expected and fine; building a new tree from the segments is not, because everything the segments do not capture —
attributes, comments, structure, ordering — would be lost in the rebuild. The unit of replacement is a run's inner
*content* rather than a single text node because a translated paragraph legitimately contains inline markup, and no
text node can hold an element.

#### Scenario: Element identity survives reassembly

- **WHEN** a document containing `<p id="ch01-p07" class="first">Text.</p>` is parsed and reassembled with zero edits
- **THEN** the output element still carries `id="ch01-p07"` and `class="first"`

#### Scenario: Comments and structural whitespace survive

- **WHEN** a document containing an XML comment between two block elements is reassembled
- **THEN** the comment is present in the output, between the same two elements

#### Scenario: A translation containing inline markup is written back as markup

- **WHEN** the segment parsed from `<p>Hello <em>world</em>.</p>` receives target text `Привіт <em>світ</em>.`
- **THEN** the output element is `<p>Привіт <em>світ</em>.</p>`
- **AND** the output contains no escaped markup such as `&lt;em&gt;`
- **AND** the original `<em>world</em>` element is not present alongside the translation

#### Scenario: Writing one run leaves the other runs and their separators alone

- **WHEN** a block `<div>Один<br/>Два<br/>Три</div>` has only its second segment written back as `Two`
- **THEN** the output element is `<div>Один<br/>Two<br/>Три</div>`
- **AND** the output still contains exactly two `<br/>` elements


### Requirement: Repackage with mimetype first and stored

On export the system SHALL write the `mimetype` entry first and STORED (uncompressed), preserve the order of the
remaining entries, preserve each remaining entry's original compression method, and preserve the identity of every
entry it did not translate.

Source: FR-DOC-EPUB-3, FR-DOC-06, `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, DD-43.
In plain words: an EPUB whose `mimetype` entry is compressed or not first is not a valid EPUB — readers reject it,
and the rejection message never mentions compression. This is the single most easily broken rule in the format and
the one a naive re-zip breaks by default. Each entry's original compression method is preserved for a related
reason: publishers deliberately store already-compressed images uncompressed, and the round-trip contract licenses
re-compressing an entry that was already compressed, not converting a stored entry into a compressed one.

#### Scenario: The mimetype entry is first and uncompressed

- **WHEN** a parsed EPUB is reassembled and written
- **THEN** the first zip entry of the output is named `mimetype`
- **AND** its compression method is STORED
- **AND** its content is exactly `application/epub+zip`

#### Scenario: Entry order is preserved

- **WHEN** an EPUB whose entries follow the order `mimetype`, `META-INF/container.xml`, `OEBPS/content.opf`,
  `OEBPS/c01.xhtml` is reassembled
- **THEN** the output's entries appear in that same order

#### Scenario: A stored image stays stored

- **WHEN** an EPUB containing `OEBPS/images/cover.jpg` written with the STORED method is reassembled
- **THEN** the output entry `OEBPS/images/cover.jpg` is also STORED

#### Scenario: A deflated entry stays deflated

- **WHEN** an EPUB containing `OEBPS/styles.css` written with the DEFLATED method is reassembled
- **THEN** the output entry `OEBPS/styles.css` is also DEFLATED

## ADDED Requirements

### Requirement: Refuse DRM by what is encrypted, not by the algorithm named

IF `META-INF/encryption.xml` declares any encrypted resource that is not a font, or names a resource the package
manifest does not declare, THEN the system SHALL refuse the book with a DRM-blocked outcome — `ErrorCode.validation`
carrying a message that identifies the book as protected rather than as malformed — and SHALL NOT import any part of
it. IF every encrypted resource resolves to a manifest item declared with a font media type, THEN the system SHALL
process the book normally and carry the obfuscated font bytes through unchanged, whatever encryption algorithm is
named.

Source: FR-DOC-EPUB-7, EC-EPUB-1, EC-FONT-1, FR-IMPORT-04, ADR-0026
(`docs/adr/ADR-0026-drm-adjudication-by-encrypted-resource.md`), `02_Architecture/09_ERROR_HANDLING.md#error-code`.
In plain words: encrypted content cannot be translated and must not be half-imported. Font obfuscation is recorded in
the same file but is not encryption of the book — it is a routine embedding technique, and refusing books that use it
would reject a large share of ordinary EPUBs. The decision is taken from *what* was encrypted rather than from the
algorithm's name, because vendors spell those names inconsistently: a survey of 194 real books found 16 refused over
a single missing character in an algorithm URI, every one of them a book whose only encrypted resources were fonts.
The refusal carries `ErrorCode.validation` because the frozen fifteen-constant error vocabulary has no DRM code and
this change does not add one; what distinguishes a DRM refusal from a corrupt-container refusal is the user-facing
message, which is the only part of the envelope the two do not share.

#### Scenario: Content encryption refuses the whole book

- **WHEN** an EPUB whose `META-INF/encryption.xml` names the encrypted resource `OEBPS/chapter01.xhtml` is opened
- **THEN** the result carries `ErrorCode.validation` with a message identifying the book as protected
- **AND** the message does not describe the file as an invalid or malformed EPUB
- **AND** no unit and no segment is produced

#### Scenario: A font-only manifest is allowed whatever the algorithm is called

- **WHEN** an EPUB whose `META-INF/encryption.xml` declares the algorithm `http://ns.adobe.com/pdf/enc#RC` over
  `OPS/fonts/Charter-Roman.ttf`, declared in the manifest as `application/vnd.ms-opentype`, is opened
- **THEN** parsing succeeds and produces segments
- **AND** the reassembled output carries the obfuscated font bytes unchanged

#### Scenario: A font declared under an alternative media type is still a font

- **WHEN** the encrypted resource `fonts/00111.otf` is declared in the manifest as `application/x-font-otf`
- **THEN** the book is allowed

#### Scenario: An encrypted resource missing from the manifest refuses the book

- **WHEN** `META-INF/encryption.xml` names `OPS/fonts/ghost.ttf` and the package manifest declares no such item
- **THEN** the result carries `ErrorCode.validation` with a message identifying the book as protected, rather than a
  partial import

#### Scenario: The encrypted resource path is read from the container root

- **WHEN** an EPUB whose OPF lives at `OPS/content.opf` declares the manifest item `fonts/Charter-Roman.ttf` and
  whose `encryption.xml` names `OPS/fonts/Charter-Roman.ttf`
- **THEN** the two resolve to the same resource and the book is allowed

### Requirement: Recognise a translatable block by the text it owns

The system SHALL treat an element as segment-bearing when it owns direct non-whitespace text content, SHALL emit the
segment at the innermost such element so that an element containing only other elements is descended into rather than
segmented, and SHALL NOT decide segment-bearing status from the element's tag name. The tag name SHALL decide only the
segment's kind, mapping to `HEADING`, `LIST_ITEM`, `TABLE_CELL` or `VERSE_LINE` where it is recognised and to
`PARAGRAPH` otherwise.

Source: FR-DOC-01, `02_Architecture/03_DOCUMENT_MODEL.md#data-model` ("the translatable inner content of a single
block-level element … and so on"), ADR-0027 (`docs/adr/ADR-0027-structural-block-segmentation.md`).
In plain words: the specification lists paragraphs, headings and list items as examples and ends with "and so on",
so the importer has to decide for itself what a block is. Deciding by tag name fails silently: a survey of 194 real
books found 42 of them — whole author catalogues — writing every paragraph as `<div class="paragraph">`, so a tag
list covering `p`, `h1`–`h6` and `li` reached only 73.74% of the corpus text and left 41 books under 1%. Asking
instead whether an element owns text reaches 99.99% and cannot be defeated by the next converter's tag choice. The
fallback to `PARAGRAPH` is what makes that safe: an unrecognised tag degrades to a translatable paragraph rather than
to silence. It also means three kinds the enum already declares — `FOOTNOTE`, `CAPTION` and `TITLE` — are never
emitted here. That is deliberate: each needs a *semantic* judgement the structural rule does not make (an FB2 note
body is prose in a `<p>` like any other, a caption is an HTML `figcaption` only by convention), and emitting a
distinct kind buys nothing until something downstream treats it differently, which nothing in this change does.

#### Scenario: A div carrying prose is segmented

- **WHEN** a document contains `<div class="paragraph">Еней був парубок моторний</div>` and no `<p>` element at all
- **THEN** one segment is produced, with `sourceInner` equal to `Еней був парубок моторний`

#### Scenario: A wrapper element is descended into, not segmented

- **WHEN** a document contains `<div class="wrap"><div class="paragraph">Prose.</div></div>`
- **THEN** exactly one segment is produced, for the inner `div`
- **AND** no segment is produced for the outer `div`

#### Scenario: The tag name still decides the kind

- **WHEN** a document contains `<h2>Chapter One</h2>`, `<div>Prose.</div>`, `<li>An item</li>` and `<td>A cell</td>`
- **THEN** the four segments carry kinds `HEADING`, `PARAGRAPH`, `LIST_ITEM` and `TABLE_CELL` respectively

#### Scenario: An excluded block owning text still yields no segment

- **WHEN** a document contains `<pre><code>int x = 1;</code></pre>`
- **THEN** no segment is produced for it, even though the element owns text

### Requirement: Produce no segment for a block that has no text

The system SHALL produce no segment for a block whose content is empty or whitespace-only once markup is
disregarded, and SHALL preserve that block through the skeleton alone.

Source: FR-DOC-01, FR-DOC-03, EC-IMG-1 (`01_Product/03_DOCUMENT_FORMATS.md#images-and-fonts`), ADR-0027.
In plain words: a paragraph that holds only a picture or only a line break has nothing to translate, and turning it
into a segment costs a model call, a slot in the token budget and a step on the progress bar while inviting the model
to mangle a tag it was never meant to see. Real books are full of them — one surveyed EPUB carries 1,965 such blocks
out of 12,412, and one FB2 has 1,950 image-only paragraphs in a single section.

#### Scenario: An image-only paragraph yields no segment

- **WHEN** a document contains `<p><img src="fig1.png"/></p>` between two prose paragraphs
- **THEN** exactly two segments are produced, for the prose paragraphs
- **AND** the reassembled output still contains `<p><img src="fig1.png"/></p>`

#### Scenario: A spacer paragraph yields no segment

- **WHEN** a document contains `<p><br/></p>`
- **THEN** no segment is produced for it

#### Scenario: A block mixing text and an image is still a segment

- **WHEN** a document contains `<p>See <img src="fig1.png"/> here.</p>`
- **THEN** one segment is produced for it

### Requirement: Split a block's content into line-break-delimited runs

WHERE a segment-bearing block contains line-break elements, the system SHALL emit one segment per maximal run of
content between them, in document order, and SHALL preserve the line-break elements themselves in the skeleton.

Source: FR-DOC-01, FR-DOC-08, ADR-0025 (`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`), ADR-0027.
In plain words: some converters write a whole chapter as one element whose paragraphs are separated by line breaks
rather than by real block elements. One surveyed EPUB holds 465,500 characters inside four elements separated by
9,290 line breaks, and one FB2 holds 1,850,973 characters in two paragraphs separated by 11,945 of them. Treating
such a block as a single segment produces a segment far larger than any model can accept; splitting on the breaks
recovers the paragraphs the reader actually sees, while the break elements stay in the structure untouched.

#### Scenario: Three runs yield three segments

- **WHEN** a block `<div>Розділ перший<br/>Еней був парубок<br/>І хлопець хоть куди</div>` is parsed
- **THEN** three segments are produced, with `sourceInner` `Розділ перший`, `Еней був парубок` and
  `І хлопець хоть куди`
- **AND** the reassembled output still contains exactly two `<br/>` elements

#### Scenario: Text carried after a line break is not lost

- **WHEN** an FB2 paragraph holds the text `Пролог` before its first `<br/>` and `Хвіст комети` after it
- **THEN** two segments are produced, for `Пролог` and `Хвіст комети`

#### Scenario: An empty run yields no segment

- **WHEN** a block `<div>Один<br/><br/>Два</div>` is parsed
- **THEN** exactly two segments are produced, for `Один` and `Два`

### Requirement: Give every unit a stable identity appropriate to its format

The system SHALL give each unit an identity that is unique within its document, and SHALL derive that identity from
the source's own structure: an EPUB spine document's href, an FB2 body's source name suffixed by its position, or the
source file name for a single-unit format.

Source: FR-DOC-01, `02_Architecture/03_DOCUMENT_MODEL.md#data-model`,
`02_Architecture/06_DATA_MODEL_SQLITE.md#tables` (`segments.id` is the primary key).
In plain words: every segment id is built from its unit's id, and those ids are the primary key of the segment table,
so two units sharing an id would collide the moment anything is stored. An FB2 book's bodies all come from one file
and would otherwise share one identity, which is why a body's position is part of its id.

#### Scenario: An FB2 book's two bodies get distinct unit ids

- **WHEN** an FB2 file named `book.fb2` containing a main `<body>` and a `<body name="notes">` is parsed
- **THEN** the units' ids are `book.fb2#0` and `book.fb2#1`
- **AND** both units report `href` `book.fb2`

#### Scenario: A single-unit format uses its file name

- **WHEN** a file named `chapter.md` is parsed
- **THEN** the document contains one unit whose id and href are both `chapter.md`
- **AND** its media type is `text/markdown`

#### Scenario: A plain-text file is one unit with the plain-text media type

- **WHEN** a file named `notes.txt` is parsed
- **THEN** the document contains one unit whose id and href are both `notes.txt`
- **AND** its media type is `text/plain`

#### Scenario: Segment ids are built from the unit id

- **WHEN** the unit `book.fb2#1` yields two segments
- **THEN** their ids are `book.fb2#1:0` and `book.fb2#1:1`

### Requirement: Produce identical identities and anchors for identical bytes

The system SHALL produce the same unit ids, segment ids, segment order and anchors every time it parses the same
source bytes.

Source: FR-IMPORT-08, `03_NonFunctional/05_RELIABILITY_AND_RESUME.md`,
`02_Architecture/06_DATA_MODEL_SQLITE.md#tables` (no anchor column exists).
In plain words: anchors are never stored — on resume they are recomputed by parsing the file again — so a parse that
produced different anchors the second time would write translations into the wrong places after a restart. Nothing
in the system states this today, and it becomes load-bearing here because the number of parsers it must hold for
goes from one to four.

#### Scenario: A second parse of the same file agrees with the first

- **WHEN** the same book file is parsed twice in the same process
- **THEN** both parses produce the same unit ids in the same order
- **AND** every segment's id, order and anchor is identical between the two parses

### Requirement: Resolve the book format before parsing and route to its reader

WHEN a book file is opened, the system SHALL resolve its format from the file name extension, confirm that
resolution against the file's leading bytes, and parse it with the reader for that format, supporting EPUB, FB2,
`.fb2.zip`, Markdown and TXT.

Source: FR-IMPORT-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), FR-DOC-06, ADR-0004.
In plain words: the port has to know which of the four formats it is holding before it can parse anything, and the
file name is the only signal that can tell Markdown from TXT — every plain-text file is also valid Markdown, so
content alone cannot decide. The leading bytes are checked as well because `.epub` and `.fb2.zip` are both zip
archives and would otherwise be interchangeable.

#### Scenario: A FictionBook file is parsed as FB2 rather than as an EPUB

- **WHEN** a file named `book.fb2` whose root element is `<FictionBook>` is opened
- **THEN** the parsed document reports format `FB2`
- **AND** the result is not a failure about an invalid EPUB container

#### Scenario: A zipped FictionBook is distinguished from an EPUB by its name

- **WHEN** a file named `book.fb2.zip` is opened
- **THEN** the parsed document reports format `FB2`
- **AND** a file named `book.epub` with the same leading bytes `PK\x03\x04` reports format `EPUB`

#### Scenario: Plain text and Markdown are distinguished by extension alone

- **WHEN** a file named `notes.txt` and a byte-identical file named `notes.md` are each opened
- **THEN** the first reports format `TXT` and the second reports format `MARKDOWN`

### Requirement: Refuse an unsupported input without a partial import

IF a path's extension names no supported format, or its leading bytes contradict the format its extension names, or
the path is not a regular file, THEN the system SHALL return a validation failure naming the reason and SHALL NOT
return a document.

Source: FR-IMPORT-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`),
`02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`.
In plain words: an unknown input has to fail as an unknown input. Falling back to "try each parser and keep whichever
succeeds" would be worse than useless, because Markdown accepts literally any bytes — a corrupt EPUB would parse as a
Markdown book whose text is the base64 of a zip, and the user would only discover it after translating it. The
awkward inputs are real: a downloaded book can arrive as a `.txt.zip` bundle containing images and shortcuts, or as
a *directory* whose name ends in `.fb2`.

#### Scenario: An unrecognised extension is refused

- **WHEN** a file named `book.pdf` is opened
- **THEN** the result carries `ErrorCode.validation`
- **AND** no document is returned

#### Scenario: A zipped text bundle is not a supported format

- **WHEN** a file named `book.txt.zip` is opened
- **THEN** the result carries `ErrorCode.validation`

#### Scenario: A directory is refused as not a file

- **WHEN** a directory named `book.fb2` is opened
- **THEN** the result carries `ErrorCode.validation`
- **AND** the failure does not describe the input as an invalid EPUB

#### Scenario: An extension contradicted by the content is refused

- **WHEN** a file named `book.fb2` whose content is a zip archive is opened
- **THEN** the result carries `ErrorCode.validation`

### Requirement: Bound the resources a container may consume

The system SHALL enforce a limit on a container's entry count, on its total uncompressed size, and on any single
entry's compression ratio, and SHALL return a validation failure rather than exhausting memory when a limit is
exceeded. These limits SHALL apply to **every** zip container the system opens — an `.epub` and a `.fb2.zip` alike.

Source: FR-IMPORT-05, `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`,
`02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`.
In plain words: a book file is untrusted input from the internet. A small archive can be crafted to expand to
hundreds of gigabytes, and the importer reads every entry into memory, so without limits a malicious file takes the
whole application down instead of producing an error message. The limits are stated over *any* container rather than
over EPUB because this change opens a second zip-backed format, and a cap that guards one of two entry points guards
neither: an attacker picks the extension.

#### Scenario: The limits guard a zipped FB2 as well as an EPUB

- **WHEN** a `.fb2.zip` whose single entry expands to more than the configured total-size limit is opened
- **THEN** the result carries `ErrorCode.validation`
- **AND** no document is returned

#### Scenario: A decompression bomb is refused rather than exhausting memory

- **WHEN** an archive whose single entry expands to more than the configured total-size limit is opened
- **THEN** the result carries `ErrorCode.validation`
- **AND** no document is returned

#### Scenario: An archive with an implausible entry count is refused

- **WHEN** an archive declaring more entries than the configured limit is opened
- **THEN** the result carries `ErrorCode.validation`

### Requirement: Read container entry names that are not UTF-8

IF a container's entry names cannot be decoded as UTF-8, THEN the system SHALL re-read the container using the zip
format's historical default encoding rather than reporting the container as corrupt.

Source: FR-IMPORT-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import` — "reject … with a clear reason", read
here as a bar on rejecting a readable book with a wrong one), FR-DOC-FB2-2
(`01_Product/03_DOCUMENT_FORMATS.md#fb2`), design.md D12.
In plain words: archives produced by older tools store their entry names in a legacy code page, without the flag
that says so. Reading one with a UTF-8 decoder throws, and the failure surfaces as "this file is not a valid
container" — a wrong diagnosis for a perfectly readable book. Retrying with the historical default reads it. This is
a robustness measure rather than a frozen obligation: no edge case in the specification names it, and the surveyed
EPUB corpus does not need it — not one of 12,513 entry names contains a non-ASCII byte. It is stated as a
requirement because it was measured directly on real `.zip` archives, where the default decoder throws
`ZipException` and a retry reads every entry, and because `.fb2.zip` is the format most likely to arrive from the
tools that produce them.

#### Scenario: A legacy-encoded entry name is read rather than refused

- **WHEN** a `.fb2.zip` whose member name is encoded in the zip format's historical default code page is opened
- **THEN** the member is read and the document is returned
- **AND** the result is not a validation failure about a corrupt container

### Requirement: Parse FB2 as one XML document preserving its lexical form

The system SHALL parse an FB2 book as a single XML document and SHALL preserve its comments, CDATA sections, entity
and character-reference spelling, XML declaration, declared encoding, namespace prefixes, and the whitespace between
block-level elements, across a round trip.

Source: FR-DOC-FB2-1 (`01_Product/03_DOCUMENT_FORMATS.md#fb2`), FR-DOC-02,
`02_Architecture/03_DOCUMENT_MODEL.md#xml-round-trip-config`.
In plain words: FB2 keeps everything in one file, so everything the file carries is at risk from one careless
re-serializer. A parser that expands entities, collapses CDATA to text, or drops a comment produces a book that
still opens and is still a different document — and in verse, the whitespace between blocks is the line layout.
Namespace prefixes matter for the same reason: real books declare the link namespace as `l:` or as `xlink:`, and
some redeclare it below the root, so a writer with a fixed prefix table rewrites markup it was asked to preserve.

#### Scenario: A comment between block elements survives

- **WHEN** an FB2 document containing `<p>One.</p><!-- scene break --><p>Two.</p>` is parsed and reassembled with zero
  segment edits
- **THEN** the output contains the comment `<!-- scene break -->` between the same two paragraphs

#### Scenario: A CDATA section stays CDATA

- **WHEN** an FB2 document containing `<p><![CDATA[a < b]]></p>` is reassembled
- **THEN** the output still contains a CDATA section, not the escaped text `a &lt; b`

#### Scenario: The declared encoding is echoed unchanged

- **WHEN** an FB2 document declaring `<?xml version="1.0" encoding="windows-1251"?>` is reassembled with zero segment
  edits
- **THEN** the output declares the encoding `windows-1251`

#### Scenario: A namespace prefix is preserved as declared

- **WHEN** an FB2 document declares the link namespace with the prefix `l` and uses `l:href`
- **THEN** the reassembled output still uses the prefix `l`, not a substituted one

### Requirement: Segment FB2 body content by block kind

WHEN an FB2 body is parsed, the system SHALL emit one segment per translatable block element with the kind matching
that element — `<p>` as `PARAGRAPH`, a section `<title>`'s paragraphs and `<subtitle>` as `HEADING`, a `<v>` verse
line as `VERSE_LINE`, and a `<td>` or `<th>` cell as `TABLE_CELL` — SHALL descend into container elements such as
`<cite>`, `<epigraph>` and `<poem>` to reach the blocks inside them, and SHALL preserve poem, stanza, table row and
column grouping in the skeleton.

Source: FR-DOC-FB2-5 (`01_Product/03_DOCUMENT_FORMATS.md#fb2`), FR-DOC-01, FR-DOC-08, EC-FB2-4, EC-VERSE-1,
EC-VERSE-2 (`01_Product/03_DOCUMENT_FORMATS.md#verse-tables-notes`).
In plain words: verse is the case that breaks naive segmentation. If a whole stanza became one segment, the model
would return prose and the line breaks would be gone; if the grouping lived in the segment rather than the skeleton,
reassembly would have to rebuild it. So each line is its own segment and the stanza structure is never sent at all.

#### Scenario: Each verse line is its own segment

- **WHEN** a `<poem>` containing a `<stanza>` with three `<v>` lines is parsed
- **THEN** exactly three segments of kind `VERSE_LINE` are produced for it
- **AND** the reassembled output still contains one `<stanza>` holding three `<v>` elements

#### Scenario: A poem without stanza grouping still yields one segment per line

- **WHEN** a `<poem>` containing four `<v>` lines and no `<stanza>` element is parsed
- **THEN** four segments of kind `VERSE_LINE` are produced

#### Scenario: Table cells are segments and the table shape is preserved

- **WHEN** a `<table>` with two rows of three `<td>` cells each is parsed
- **THEN** six segments of kind `TABLE_CELL` are produced
- **AND** the reassembled output still contains two rows of three cells

#### Scenario: A quotation's paragraphs are segmented through its container

- **WHEN** a `<cite>` element contains `<p>Quoted prose.</p>` and `<text-author>Author</text-author>`
- **THEN** a segment is produced for `Quoted prose.`
- **AND** the reassembled output still contains the `<cite>` element wrapping it

#### Scenario: A vertical-space element yields no segment

- **WHEN** a body contains `<empty-line/>` between two paragraphs
- **THEN** no segment is produced for it
- **AND** the reassembled output still contains `<empty-line/>` between the same two paragraphs

### Requirement: Segment the FB2 notes body and preserve its anchors

WHEN an FB2 document contains a `<body name="notes">`, the system SHALL emit segments for the note bodies it
contains and SHALL preserve every note's element id and every cross-reference target that points at it, unchanged.

Source: FR-DOC-FB2-6, EC-FB2-3 (`01_Product/03_DOCUMENT_FORMATS.md#fb2-edge-cases`), FR-DOC-08, EC-VERSE-3.
In plain words: footnotes are ordinary prose and are translated by default, but the link from the text to the note is
an id, and rewriting one breaks the jump in a way nothing else in the book reveals. The note text changes; the
plumbing does not.

#### Scenario: Note text is segmented and its anchor id is unchanged

- **WHEN** an FB2 document whose notes body contains `<section id="n1"><p>A note.</p></section>`, referenced from the
  main body by `<a l:href="#n1" type="note">1</a>`, is parsed and reassembled with zero segment edits
- **THEN** a segment is produced for the note's paragraph `A note.`
- **AND** the output still contains `id="n1"` and the reference `l:href="#n1"`

### Requirement: Carry FB2 embedded binary sections through unchanged

The system SHALL preserve every FB2 `<binary>` element's base64 payload exactly, SHALL NOT re-encode, re-wrap or
re-line-break it, and SHALL produce no segment for it.

Source: FR-DOC-FB2-4 (`01_Product/03_DOCUMENT_FORMATS.md#fb2`), FR-DOC-02, EC-IMG-1.
In plain words: an FB2 book's cover and illustrations live inside the XML as base64 text, not as separate files. That
makes them look like ordinary character data to a writer that is free to re-wrap long content, and re-wrapping
rewrites the image without breaking anything a structural comparison would notice — which is why the payload is
compared exactly rather than canonically.

#### Scenario: A cover image's base64 text is identical after a round trip

- **WHEN** an FB2 document containing `<binary id="cover.jpg" content-type="image/jpeg">` with a base64 payload is
  reassembled with zero segment edits
- **THEN** that element's text content in the output is character-for-character identical to the source's
- **AND** no segment references `cover.jpg`

### Requirement: Read and re-emit a zipped FB2 container

WHERE the source is a `.fb2.zip` container, the system SHALL read the single FB2 document it contains, treat the
result as an FB2 document, and on export SHALL write a zip container back preserving the member's name.

Source: FR-DOC-FB2-2 (`01_Product/03_DOCUMENT_FORMATS.md#fb2`), FR-IMPORT-01, FR-DOC-06.
In plain words: the zip wrapper is packaging, not format — the book inside is an ordinary FB2 and behaves identically
once unpacked. Export has to put the wrapper back because export re-emits the container it was given, and a user who
imported a `.fb2.zip` expects a `.fb2.zip`.

#### Scenario: A zipped FB2 parses as FB2 and re-emits as a zip

- **WHEN** a `.fb2.zip` containing the single entry `book.fb2` is opened and reassembled with zero segment edits
- **THEN** the parsed document reports format `FB2`
- **AND** the output is a zip archive whose single entry is named `book.fb2`
- **AND** that entry's content is canonical-XML-equal to the source entry's

#### Scenario: A zip with no FictionBook member is refused

- **WHEN** a file named `book.fb2.zip` containing only the entry `readme.txt` is opened
- **THEN** the result carries `ErrorCode.validation`
- **AND** no document is returned

### Requirement: Refuse an encrypted FB2 container as DRM-blocked

IF a `.fb2.zip` container declares an encrypted entry, THEN the system SHALL refuse the book with a DRM-blocked
outcome — `ErrorCode.validation` carrying a message that identifies the book as protected rather than as malformed —
and SHALL NOT import any part of it.

Source: FR-IMPORT-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-DRM-1
(`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`),
`02_Architecture/09_ERROR_HANDLING.md#error-code`.
In plain words: encrypted content cannot be translated, and half-importing it would leave the user with a book whose
missing parts are invisible. FB2 has no encryption manifest of its own, so the only thing there is to detect is the
zip's own encryption flag — and that is enough, because an unreadable entry is unreadable either way. As with the
EPUB refusal, the code is `ErrorCode.validation` because the error vocabulary carries no DRM constant; the message is
what tells the two refusals apart.

#### Scenario: A password-protected zipped FB2 is refused

- **WHEN** a `.fb2.zip` whose `book.fb2` entry is flagged as encrypted is opened
- **THEN** the result carries `ErrorCode.validation` with a message identifying the book as protected
- **AND** the message does not describe the archive as corrupt
- **AND** no unit and no segment is produced

### Requirement: Keep the FB2 declared encoding on export when every character is representable

WHEN an FB2 document is exported, the system SHALL write it in the encoding the source declared IF every character of
the output is representable in that encoding, and OTHERWISE SHALL write it in UTF-8 and rewrite the XML declaration
to match.

Source: FR-DOC-FB2-3, EC-FB2-1 (`01_Product/03_DOCUMENT_FORMATS.md#fb2-edge-cases`), FR-DOC-02,
`01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`.
In plain words: a `windows-1251` book whose declaration still says `windows-1251` but whose bytes are UTF-8 is
unreadable, and so is the reverse. The decision has to be made against the characters actually being written — not
guessed from the target language — because the character that forces the switch is usually an em-dash or a curly
quote nobody predicted, not the alphabet.

#### Scenario: A representable translation keeps the declared encoding

- **WHEN** an FB2 document declaring `encoding="windows-1251"` is exported with target language `uk` and every output
  character is representable in `windows-1251`
- **THEN** the output declares the encoding `windows-1251`
- **AND** the output bytes decode cleanly as `windows-1251`

#### Scenario: One unrepresentable character switches the whole document to UTF-8

- **WHEN** an FB2 document declaring `encoding="windows-1251"` is exported and one segment's target text contains the
  character `車`
- **THEN** the output declares the encoding `UTF-8`
- **AND** the output bytes decode cleanly as UTF-8

### Requirement: Set the FB2 target language without touching the recorded source language

On export the system SHALL set the target language by replacing the first `<lang>` element within `<title-info>`,
adding one if none is present, and SHALL leave `<src-lang>` and any `<src-title-info>` block unchanged.

Source: FR-DOC-FB2-7 (`01_Product/03_DOCUMENT_FORMATS.md#fb2`), FR-DOC-07,
`02_Architecture/03_DOCUMENT_MODEL.md#repackaging`.
In plain words: FB2 records two languages — what the book is, and what it was translated from. Setting the first is
the point of the export; overwriting the second destroys the only remaining record that the book started out in
another language, which the translated file cannot recover. Real books record the source language as `<src-lang>`
inside `title-info` far more often than as a separate `<src-title-info>` block, so both must be left alone.

#### Scenario: The title-info language is replaced and src-lang is left alone

- **WHEN** an FB2 document whose `<title-info>` declares `<lang>fr</lang>` and `<src-lang>en</src-lang>` is exported
  with target language `uk`
- **THEN** the output's `<title-info>` declares `<lang>uk</lang>`
- **AND** it still declares `<src-lang>en</src-lang>`

#### Scenario: A missing title-info language is added

- **WHEN** an FB2 document whose `<title-info>` declares no `<lang>` at all is exported with target language `uk`
- **THEN** the output's `<title-info>` contains exactly one `<lang>` element, with the value `uk`

### Requirement: Resolve character encoding once on import and record it on the document

WHEN a book file is opened, the system SHALL resolve its character encoding by taking, in order, a byte-order mark if
present, then an in-band declaration if the format carries one, then charset detection over the bytes, and finally
UTF-8; and SHALL record both the resolved encoding and whether a byte-order mark was present on the parsed document,
leaving both unrecorded for a container format that has no document-level encoding.

Source: `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, FR-DOC-02, FR-DOC-FB2-3, FR-DOC-TXT-1.
In plain words: getting the encoding wrong corrupts every non-Latin character in the book, and the corruption is not
visible until someone reads the translated text. The order encodes evidence strength: a byte-order mark is a fact, a
declaration is a claim, and detection is a guess, so each is consulted only when the stronger evidence above it is
absent. The mark is recorded separately from the charset because re-emitting a file that had none with one added is
itself a change. An EPUB records neither, because each of its content documents declares its own encoding and the
container as a whole has no answer to give.

#### Scenario: A byte-order mark fixes the charset and is re-emitted

- **WHEN** a TXT file beginning with the UTF-8 byte-order mark `EF BB BF` is parsed and reassembled with zero segment
  edits
- **THEN** the document records charset `UTF-8` and a byte-order mark as present
- **AND** the output begins with the same three bytes

#### Scenario: An FB2 declaration outranks detection

- **WHEN** an FB2 file with no byte-order mark declaring `encoding="windows-1251"` is parsed
- **THEN** the document records charset `windows-1251`

#### Scenario: An undeclared, unmarked file falls through to detection

- **WHEN** a TXT file with no byte-order mark whose bytes are `windows-1251`-encoded Cyrillic prose is parsed
- **THEN** the document records the detected charset rather than `UTF-8`

#### Scenario: A container format records no document-level encoding

- **WHEN** an EPUB is parsed
- **THEN** the document records no charset and no byte-order-mark flag

### Requirement: Refuse an FB2 whose declared encoding contradicts its content

IF an FB2 document's declared encoding disagrees with the encoding detected from its bytes with high confidence,
THEN the system SHALL return a validation failure and SHALL NOT substitute the detected encoding.

Source: EC-FB2-2 (`01_Product/03_DOCUMENT_FORMATS.md#fb2-edge-cases`) — "never silently corrupt",
`02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`.
In plain words: the frozen edge case allows either refusing or honouring a reliably detected encoding, and binds only
one thing — never corrupt silently. Refusal is chosen because a book decoded under the wrong charset is not visibly
broken: it imports, it segments, it translates, and the damage surfaces as mojibake in the finished file after the
whole run. Note that the check cannot be "try to decode and see if it fails": the encodings real books misdeclare —
`iso-8859-1`, `windows-1252` — accept every possible byte, so decoding always succeeds and always produces nonsense.

#### Scenario: A declaration that does not match the content is refused

- **WHEN** an FB2 file declaring `encoding="windows-1252"` whose bytes are UTF-8-encoded Cyrillic prose is opened
- **THEN** the result carries `ErrorCode.validation`
- **AND** no document is returned

#### Scenario: A single-byte declaration that decodes without error is still checked

- **WHEN** an FB2 file declaring `encoding="iso-8859-1"` whose bytes are `windows-1251`-encoded Cyrillic prose is
  opened
- **THEN** the result carries `ErrorCode.validation`
- **AND** the failure is not a successfully parsed document containing replacement characters

### Requirement: Parse Markdown into an AST and segment its leaf blocks

The system SHALL parse a Markdown book into a CommonMark abstract syntax tree and SHALL emit one segment per
translatable **leaf** block — a heading as `HEADING`, a paragraph as `PARAGRAPH`, a paragraph inside a list item as
`LIST_ITEM`, a table cell as `TABLE_CELL`, a paragraph inside a block quote as `PARAGRAPH` — and SHALL NOT emit a
segment for a container block such as a list, a list item, a block quote or a table.

Source: FR-DOC-MD-1, FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-01, FR-DOC-08, ADR-0027.
In plain words: Markdown has no single correct spelling — `*em*` and `_em_` are the same document — so the tree, not
the text, is what has to be preserved. Segments come from leaf blocks only because a container's extent can enclose
things that must never be touched: a real list item can hold a paragraph, then a fenced code block, then more prose,
and replacing the item's extent would destroy the code.

#### Scenario: Headings, paragraphs and list items are distinguished

- **WHEN** a Markdown file containing `## Chapter One`, then `Prose.`, then `- An item` is parsed
- **THEN** three segments are produced with kinds `HEADING`, `PARAGRAPH` and `LIST_ITEM` respectively

#### Scenario: A list item containing a code block yields segments only for its prose

- **WHEN** a list item contains the paragraph `Copy the package:`, then a fenced code block, then the paragraph
  `Then review it.`
- **THEN** exactly two segments are produced, for the two paragraphs
- **AND** no segment's extent includes the fenced code block

#### Scenario: Table cells are segments and the table is not

- **WHEN** a Markdown table with two rows of three cells is parsed
- **THEN** six segments of kind `TABLE_CELL` are produced
- **AND** no segment is produced for the table or for a table row

#### Scenario: Nested list structure survives a no-edit round trip

- **WHEN** a Markdown file containing a two-level nested bullet list is reassembled with zero segment edits
- **THEN** re-parsing the output yields a tree with the same list nesting depth and item order as the source

### Requirement: Exclude Markdown code blocks from segmentation

The system SHALL produce no segment for a fenced code block or an indented code block, SHALL preserve each through
the skeleton alone, and SHALL preserve a fenced block's info string and its content exactly.

Source: FR-DOC-MD-2 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), DD-49,
`01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, EC-CODE-1.
In plain words: translating a code listing renames variables and breaks the program the book is teaching. They are
excluded from the segment list entirely rather than marked "do not translate", because a segment that exists still
costs token budget and still has to be explained to the model. The info string goes with it: a fence that loses its
declared language means every listing in the book stops being syntax-highlighted.

#### Scenario: A fenced block produces no segment and keeps its info string

- **WHEN** a Markdown file containing `Before.`, then a fenced block opened with ` ```java ` containing `int x = 1;`,
  then `After.` is parsed
- **THEN** exactly two segments are produced, for `Before.` and `After.`
- **AND** the reassembled output's fenced block still declares the info string `java` and contains `int x = 1;`

#### Scenario: Markdown-looking content inside a fence is not segmented

- **WHEN** a fenced code block contains the lines `---`, `title: Example` and `# A heading`
- **THEN** no segment is produced for any of them
- **AND** the reassembled output contains those three lines unchanged inside the fence

#### Scenario: A document that is entirely one code block yields no segments

- **WHEN** a Markdown file whose whole content is one fenced code block is parsed
- **THEN** the document contains one unit with zero segments
- **AND** reassembly succeeds and re-parses to the same tree

### Requirement: Preserve Markdown frontmatter verbatim without segmenting it

WHERE a Markdown file opens with a `---`-delimited frontmatter block, the system SHALL carry that block through to
the output byte-for-byte and SHALL produce no segment for any key or value it contains. A `---` line that is not at
the start of the file SHALL NOT be treated as frontmatter.

Source: FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), EC-MD-3, DD-47,
`02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`.
In plain words: frontmatter keys are machine-readable field names and translating one breaks whatever reads the file.
Frontmatter *values* are translatable, but only when the user turns that on, and that toggle and its segments belong
to a later change — so until then the honest behaviour is to touch none of it. Only the start of the file can open a
frontmatter block, because `---` elsewhere is an ordinary thematic break, and real documents use it that way.

#### Scenario: A frontmatter block survives byte-for-byte and yields no segments

- **WHEN** a Markdown file opening with `---\ntitle: The Book\nlang: en\n---\n` followed by `Prose.` is parsed and
  reassembled with zero segment edits
- **THEN** exactly one segment is produced, for `Prose.`
- **AND** the output's first four lines are byte-identical to the source's

#### Scenario: A thematic break later in the file is not frontmatter

- **WHEN** a Markdown file begins with `# Title` and contains a `---` line between two paragraphs
- **THEN** no frontmatter is recognised
- **AND** the two paragraphs each yield a segment

### Requirement: Preserve embedded raw HTML in Markdown without segmenting it

WHERE a Markdown file contains a raw HTML block, the system SHALL preserve that block's content exactly and SHALL
produce no segment for it.

Source: EC-MD-2 (`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), FR-DOC-MD-4, FR-DOC-02.
In plain words: a raw HTML block is a single opaque run of text as far as the Markdown tree is concerned, so there is
nothing yet to address the prose inside it by. Preserving it whole is the half of the edge case that can be honoured
without a second parser nested inside the first; translating the text nodes within it needs that mechanism and is
not part of this change.

#### Scenario: A raw HTML block round-trips unchanged and produces no segment

- **WHEN** a Markdown file containing the block `<div class="note">Careful.</div>` surrounded by blank lines is parsed
  and reassembled with zero segment edits
- **THEN** no segment is produced for it
- **AND** the output contains `<div class="note">Careful.</div>` unchanged

### Requirement: Reassemble Markdown by splicing translated spans into the original bytes

WHEN a Markdown document is exported, the system SHALL copy the original file's bytes to the output and substitute
only the byte spans of segments that carry target text, leaving every other byte untouched, and SHALL NOT re-render
the document from its syntax tree.

Source: FR-DOC-MD-4, FR-DOC-03, DD-43, `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`).
In plain words: re-rendering a Markdown file from its tree normalises formatting everywhere — emphasis markers,
bullet characters, fence styles, and whether the file ends with a newline — in parts of the document nobody
translated. Copying the original and replacing only translated spans makes every untouched byte identical by
construction, which is both stronger and simpler.

#### Scenario: An untranslated round trip is byte-identical

- **WHEN** a Markdown file is parsed and reassembled with no segment receiving target text
- **THEN** the output file's bytes are identical to the source file's

#### Scenario: A file that does not end with a newline still does not

- **WHEN** a Markdown file whose last byte is not a newline is reassembled with zero segment edits
- **THEN** the output's last byte is not a newline either

#### Scenario: Emphasis spelling outside a translated span is untouched

- **WHEN** a Markdown file containing `Some _emphasis_ here.` in one paragraph and `Other text.` in another has only
  the second paragraph written back
- **THEN** the first paragraph still reads `Some _emphasis_ here.` with underscores

#### Scenario: A hard line break at the end of a translated block survives

- **WHEN** a paragraph ending with two trailing spaces followed by a newline is written back
- **THEN** the output paragraph still ends with two trailing spaces followed by a newline

### Requirement: Segment plain text by blank-line-separated paragraphs

The system SHALL treat a TXT file as a sequence of paragraphs separated by one or more blank lines, emit one
`PARAGRAPH` segment per paragraph in file order, produce no segment for a run of blank lines, and preserve the
file's line endings, indentation and trailing whitespace.

Source: FR-DOC-TXT-1, FR-DOC-TXT-2 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), FR-DOC-01.
In plain words: plain text has no markup, so the blank line is the only structure there is — it is what separates one
paragraph from the next, and it is also what a poem uses to separate stanzas. Line endings and indentation are
likewise the whole of the formatting, which is why they are preserved rather than normalized. Runs of several blank
lines are common in real files and must not produce empty segments.

#### Scenario: Blank lines separate paragraphs

- **WHEN** a TXT file containing `One.\n\nTwo.\n\nThree.\n` is parsed
- **THEN** three segments of kind `PARAGRAPH` are produced, in that order

#### Scenario: A run of several blank lines yields no extra segment

- **WHEN** a TXT file containing `One.\r\n\r\n\r\n\r\nTwo.\r\n` is parsed
- **THEN** exactly two segments are produced

#### Scenario: CRLF line endings and indentation are preserved

- **WHEN** a TXT file using `\r\n` line endings and containing a paragraph indented by four spaces is reassembled
  with zero segment edits
- **THEN** the output uses `\r\n` line endings throughout
- **AND** the indented paragraph still begins with four spaces

### Requirement: Reassemble plain text by splicing target spans into the original bytes

WHEN a TXT document is exported, the system SHALL copy the original file's bytes to the output and substitute only
the byte spans of segments that carry target text, leaving every other byte untouched.

Source: FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), FR-DOC-03, DD-43,
`02_Architecture/03_DOCUMENT_MODEL.md#repackaging`.
In plain words: because everything outside a translated paragraph is copied rather than regenerated, a TXT round trip
with nothing translated is byte-identical by construction — the encoding, the line endings and the byte-order mark
survive because nothing ever re-encodes them. This is the one format where byte equality is the correct assertion
rather than an over-strict one.

#### Scenario: An untranslated round trip is byte-identical

- **WHEN** a TXT file is parsed and reassembled with no segment receiving target text
- **THEN** the output file's bytes are identical to the source file's

#### Scenario: Only the translated paragraph's bytes change

- **WHEN** a TXT file containing `One.\n\nTwo.\n` has only its second segment written back as `Два.`
- **THEN** the output is `One.\n\nДва.\n`
- **AND** the bytes before the second paragraph are unchanged

### Requirement: Refuse a plain-text export the source encoding cannot represent

IF a TXT document's target text contains a character that cannot be represented in the encoding the source was read
with, THEN the system SHALL return a validation failure and SHALL NOT write an output file.

Source: FR-DOC-TXT-3, `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, EC-FB2-1 (the analogous rule for a format
that *can* record a change), `02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`.
In plain words: FB2 solves this by switching to UTF-8 and rewriting its declaration, but plain text has no
declaration to rewrite — a reader has no way to learn that the encoding changed. Silently re-encoding the file would
make every non-Latin character in it unreadable to whatever opens it next, so the export fails loudly instead and the
user can re-import the book as UTF-8.

#### Scenario: An unrepresentable target character fails the export

- **WHEN** a TXT document read as `windows-1251` is exported and one segment's target text contains the character `車`
- **THEN** the result carries `ErrorCode.validation`
- **AND** no output file is written

### Requirement: Declare no language metadata in formats that carry none

WHERE the exported format has no language-metadata field — Markdown and TXT — the system SHALL leave the content
unchanged rather than inserting a language declaration.

Source: FR-DOC-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`) read against
`02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, which specifies a language update for EPUB and FB2 only; DD-43.
In plain words: the export contract takes a target language for every format, but only two formats have anywhere to
put it. Inventing a place — a `lang:` key in Markdown frontmatter, a header line in TXT — would add content the
source never had, and would break the byte-exact round trip for a field nothing reads.

#### Scenario: A Markdown export adds no language key

- **WHEN** a Markdown document with no frontmatter is exported with target language `uk`
- **THEN** the output contains no frontmatter block and no `lang` key

#### Scenario: A TXT export with a target language is still byte-identical

- **WHEN** a TXT document is exported with target language `uk` and no segment carries target text
- **THEN** the output file's bytes are identical to the source file's

### Requirement: Populate the declared language and book metadata from each format's own source

The system SHALL record the language the book declares and its title and author metadata by reading the location
each format provides for them, and SHALL record no value where the source provides none.

Source: FR-IMPORT-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), FR-IMPORT-03, EC-LANG-1
(`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), `02_Architecture/03_DOCUMENT_MODEL.md#data-model`.
In plain words: the declared language is what a later step compares against the language actually detected from the
content, so the mismatch state has something to compare. Title and author are what the import card shows the user.
Both are frequently absent or wrong in real books — one surveyed FB2 declares English on a French book and another
declares no language at all — so absence has to be representable rather than guessed at.

#### Scenario: FB2 metadata is read from title-info

- **WHEN** an FB2 document whose `<title-info>` declares `<lang>fr</lang>`, `<book-title>Harry Potter et la Coupe de
  Feu</book-title>` and an `<author>` is parsed
- **THEN** the document's declared language is `fr`
- **AND** its metadata contains the title `Harry Potter et la Coupe de Feu`

#### Scenario: A missing declaration is recorded as absent, not invented

- **WHEN** an FB2 document whose `<title-info>` contains no `<lang>` element is parsed
- **THEN** the document records no declared language

#### Scenario: Markdown metadata is read from frontmatter

- **WHEN** a Markdown file whose frontmatter declares `title: The Book` and `lang: en` is parsed
- **THEN** the document's declared language is `en` and its metadata contains the title `The Book`

### Requirement: Prove that segmentation covers the document's translatable text

The system's per-format golden tests SHALL assert that the segments emitted for a fixture cover at least a stated
proportion of that fixture's visible text, counting text inside deliberately excluded blocks as not translatable.

Source: FR-DOC-01, FR-DOC-09, ADR-0027 (`docs/adr/ADR-0027-structural-block-segmentation.md`),
`04_Build_and_Release/06_TESTING_STRATEGY.md`.
In plain words: every other assertion in the document suite checks that nothing was *damaged*, and a book that yields
no segments at all satisfies every one of them perfectly — the skeleton round-trips, the bytes match, the gate is
green, and the product does nothing. This is the only assertion that checks that something *happened*. A survey of
194 real books found 42 that would have imported empty with no test noticing.

#### Scenario: A fixture whose prose is not segmented fails the gate

- **WHEN** a fixture EPUB whose paragraphs are `<div class="paragraph">` elements is parsed and the walker recognises
  only `<p>`, `<h1>`–`<h6>` and `<li>`
- **THEN** the coverage assertion fails, reporting a coverage close to `0`

#### Scenario: A correctly segmented fixture passes

- **WHEN** the same fixture is parsed with structural block recognition
- **THEN** the coverage assertion reports a coverage above `0.95` and passes

#### Scenario: An excluded code listing does not count against coverage

- **WHEN** a fixture contains a `<pre><code>` listing of 200 characters and 1,000 characters of prose, all of which
  is segmented
- **THEN** the coverage assertion passes

### Requirement: Gate every fixture through the port on a real file

The system SHALL hold every round-trip fixture in one enumerated catalogue, and SHALL exercise each catalogued
fixture by writing it to a real file, opening that file through the document port, reassembling it with zero segment
edits, and asserting the fixture's declared segment count, its format's canonical comparison and its coverage floor.
Each fixture's expectations SHALL be declared with the fixture and SHALL NOT be computed from the parser's own
output.

Source: FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-01, ADR-0027
(`docs/adr/ADR-0027-structural-block-segmentation.md`), `04_Build_and_Release/06_TESTING_STRATEGY.md`,
`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`.
In plain words: a fixture that exists but is not asserted proves nothing, and that is not hypothetical — the shape
that corrupts a translation containing inline markup had no fixture and no test in the shipped code, which is why
the defect reached a release with the gate green. Enumerating fixtures means a newly authored one is gated by
existing so it cannot be added and quietly forgotten. Going through a real file and the port rather than through the
parser directly is what makes the assertion mean "a user could open this book": format resolution, container
reading, charset resolution and error classification all sit between the two, and a test that calls the parser
directly skips every one of them. The expectations are declared rather than derived because a catalogue that asked
the walker how many segments it produced would agree with the walker no matter what the walker did.

#### Scenario: Every catalogued fixture round-trips through the port

- **WHEN** the fixture catalogue is exercised
- **THEN** each fixture is written to a file, opened through the document port and reassembled with no segment
  receiving target text
- **AND** each one meets its declared segment count, its format's canonical comparison and its coverage floor

#### Scenario: A fixture stacking several pathologies is gated like any other

- **WHEN** a fixture whose prose is `<div class="paragraph">` elements wrapped `p > span > i` and split by `<br/>`
  is exercised
- **THEN** it is opened, reassembled and asserted by the same catalogue-driven gate as a single-pathology fixture

#### Scenario: An ungated fixture is not possible

- **WHEN** a new fixture builder is added to the catalogue
- **THEN** it is exercised by the sweep with no further test being written

### Requirement: Pass a golden round-trip test for FB2

The system SHALL pass a golden round-trip test in which a fixture FB2 book is parsed and reassembled with zero
segment edits, and the output is **canonical-XML-equal** to the source over the re-parsed tree, with the declared
encoding compared as a value and every `<binary>` payload compared exactly; a fixture that legitimately switches
encoding to UTF-8 is excluded from that comparison and asserted against a re-parsed canonical tree instead.

Source: FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43,
`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`,
`02_Architecture/03_DOCUMENT_MODEL.md#golden-round-trip-test`, EC-FB2-1.
In plain words: canonical XML comparison absorbs the entity spelling and attribute quoting a faithful writer may
legitimately change, while still catching a lost element, a dropped comment or a rewritten id. It also absorbs the
encoding declaration and may re-wrap long text — which is why those two are asserted separately, since they are
precisely what FB2 puts at risk. The encoding is compared as a *value* rather than as declaration text because real
books write it with single quotes and a writer will emit double quotes for a document it preserved perfectly.

#### Scenario: A no-edit FB2 round trip is canonical-XML-equal

- **WHEN** a fixture FB2 book is parsed and reassembled with no segment receiving target text
- **THEN** re-parsing the output yields a canonical form equal to the source's
- **AND** the output's declared encoding value equals the source's
- **AND** every `<binary>` element's text is character-for-character identical

#### Scenario: Declaration quoting is not the assertion

- **WHEN** a source declaring `<?xml version='1.0' encoding='utf-8'?>` is reassembled as
  `<?xml version="1.0" encoding="utf-8"?>`
- **THEN** the golden test still passes, because both declare the encoding value `utf-8`

#### Scenario: Byte equality is not the assertion for FB2

- **WHEN** the reassembled FB2 differs from the source only in attribute quoting or the spelling of a character
  reference
- **THEN** the golden test still passes

### Requirement: Pass a golden round-trip test for Markdown

The system SHALL pass a golden round-trip test in which a fixture Markdown book is parsed and reassembled with zero
segment edits, and re-parsing the output yields a CommonMark syntax tree **structurally equal** to the source's,
compared by node type, order, nesting and literal text rather than by object identity.

Source: FR-DOC-09, DD-43, `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`,
`02_Architecture/03_DOCUMENT_MODEL.md#golden-round-trip-test`, FR-DOC-MD-4.
In plain words: Markdown has no canonical text form, so the tree is the canonical form and comparing it is what the
round-trip contract asks for. The comparison must be structural rather than an equality check on two tree objects,
because syntax-tree nodes compare by identity — a test written that way passes only when both sides are the same
object, which means it can never fail.

#### Scenario: A no-edit Markdown round trip is re-parse-equal

- **WHEN** a fixture Markdown book is parsed and reassembled with no segment receiving target text
- **THEN** re-parsing the output yields a tree equal to the source's in node types, order, nesting and literal text

#### Scenario: A lost reference-link definition fails

- **WHEN** the reassembled Markdown drops a reference-link definition present in the source
- **THEN** the golden test fails

#### Scenario: Table alignment survives

- **WHEN** a fixture containing a table with a `:---:` alignment row is reassembled with zero segment edits
- **THEN** re-parsing the output yields the same table with the same alignment

### Requirement: Pass a golden round-trip test for TXT

The system SHALL pass a golden round-trip test in which a fixture TXT book is parsed and reassembled with zero
segment edits, and the output is **byte-for-byte identical** to the source, including its byte-order mark and line
endings.

Source: FR-DOC-09, DD-43 (`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`), FR-DOC-TXT-3.
In plain words: TXT is the one format where exact bytes are the right assertion rather than an over-strict one,
because export never re-encodes anything — it copies the original buffer and substitutes spans. Anything weaker than
byte equality here would fail to notice a charset or line-ending change that the mechanism makes impossible, and so
would fail to notice the mechanism being replaced by one that does not.

#### Scenario: A no-edit TXT round trip is byte-identical

- **WHEN** a fixture TXT book with a UTF-8 byte-order mark and `\r\n` line endings is parsed and reassembled with no
  segment receiving target text
- **THEN** the output file's bytes are identical to the source file's, byte-order mark and line endings included

## REMOVED Requirements

### Requirement: Refuse DRM but allow known font obfuscation

**Reason:** ADR-0026 replaced the mechanism this requirement states. It adjudicated from the declared algorithm
URI, and two of its scenarios encode that mechanism directly — one of them, "An unknown algorithm is treated as
DRM", is now **deliberately false**: an unrecognised algorithm applied only to fonts is allowed. Keeping the
requirement and quietly dropping those scenarios would leave the ledger asserting a rule the code no longer
follows, so the requirement is retired whole and replaced by *Refuse DRM by what is encrypted, not by the
algorithm named*.

The evidence is measured, not theoretical: a survey of 194 real books found 30 carrying an encryption manifest,
**none of them DRM**, and 16 refused by this rule over a single missing character in an algorithm URI. The rule was
also blind in the other direction — inspecting only the algorithm, it would have *allowed* a book that encrypted
its content documents under a font-obfuscation URI, which is the exact failure EC-EPUB-1 exists to prevent.

**Migration:** none at runtime. No released artifact and no persisted data exists, the SQLite schema is unaffected,
and the obligation itself is unchanged — refuse encrypted content, allow font obfuscation, never half-import. Only
the evidence the decision is taken from moves, from the algorithm's name to the encrypted resource's kind. Books
previously refused now open; books that encrypt a content document under a font-obfuscation algorithm now refuse.

