# document-round-trip Specification

## Purpose
The document engine's built behaviour: how an EPUB, FB2, Markdown or TXT book is parsed into a skeleton plus
ordered segments, how each segment's inline markup is masked to `⟦gN⟧` placeholders and restored behind a
placeholder-multiset gate, and how the book is written back canonical-equal. Every requirement here is proven by a
test in `modules/document/src/test`; `docs/Architecture.md` is the readable map of the same behaviour.

## Requirements

### Requirement: Parse a book into a skeleton and an ordered segment list

The system SHALL parse an opened book into one immutable skeleton per content unit plus an ordered list of
translatable segments, where each segment is the translatable inner content of one **line-break-delimited run**
within a single block-level element — a block containing no line break having exactly one such run — and carries a
stable identity, its position in document order, and its kind. A run, a Markdown leaf block, a plain-text paragraph or an
auxiliary value whose text holds nothing a reader sees — only Unicode separators (`\p{Z}`, the no-break space among
them), control characters (`\p{Cc}`) and format characters (`\p{Cf}`: the zero-width space, the byte-order mark, the
soft hyphen) — SHALL NOT be a segment; its bytes stay in the skeleton untouched. Which element is the block SHALL not
change because of such characters: a no-break space beside inline markup still makes its element the block.

The system SHALL additionally give every opened book exactly one auxiliary unit, placed after every body unit, holding
the segments of its auxiliary text — metadata, navigation labels, page titles, image alternative text and frontmatter
text values — and SHALL keep those segments out of the body units.

**Source**: FR-DOC-01, FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-07, DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), `02_Architecture/03_DOCUMENT_MODEL.md#data-model`, `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, ADR-0025 (`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`). In plain words: a book stops being a file and becomes two things — the structure, which is frozen, and a numbered list of the bits of text that may be translated. Everything downstream addresses text by segment id, so the numbering has to exist before anything can translate, store, review or export. The unit of that numbering is a *run* rather than a whole block because some converters write an entire chapter as one element whose paragraphs are separated only by line breaks; treating such a block as one segment produces a segment no model can accept. For the overwhelming majority of blocks — every one containing no line break — the run and the block are the same thing. The auxiliary unit is kept apart so that the reading order, the structure tree and the coverage measurement still describe the chapters alone, while the title, the table of contents and the image descriptions can still be translated. It comes last, so every body unit keeps the position it had before auxiliary text was read. A paragraph of a lone no-break space looks empty on the page, yet a plain whitespace test took it for text and a model was asked to translate it; anything a reader cannot see is now no segment in any of the four formats.

#### Scenario: A chapter with three paragraphs yields three ordered segments

- **WHEN** a spine document `OEBPS/chapter01.xhtml` containing exactly three `<p>` elements is parsed
- **THEN** the unit for that document carries exactly three segments
- **AND** their ids are `OEBPS/chapter01.xhtml:0`, `OEBPS/chapter01.xhtml:1` and `OEBPS/chapter01.xhtml:2`
- **AND** their `order` values are `0`, `1` and `2`, matching the order the paragraphs appear in the document

#### Scenario: An invisible paragraph is no segment

- **WHEN** a spine document holds `<p>&nbsp;</p>` followed by `<p>Prose.</p>`, or a plain-text file holds a paragraph of
  only U+200B between `One.` and `Two.`
- **THEN** the invisible paragraph yields no segment, and the next paragraph takes the id that ends in `:0` in the EPUB
- **AND** writing the plain-text file back with no translation reproduces its bytes exactly

#### Scenario: Block kinds are distinguished

- **WHEN** a document contains `<h1>Chapter One</h1>`, `<p>Prose.</p>` and `<li>An item</li>`
- **THEN** the three segments carry kinds `HEADING`, `PARAGRAPH` and `LIST_ITEM` respectively

#### Scenario: Segments know their document-order neighbours

- **WHEN** a unit yields segments `unit:0`, `unit:1` and `unit:2`
- **THEN** `unit:1` reports `prevKey` = `unit:0` and `nextKey` = `unit:2`
- **AND** `unit:0` reports a `prevKey` of `null` and `unit:2` reports a `nextKey` of `null`

#### Scenario: Only the body kinds this change can produce are emitted

- **WHEN** any of the four supported formats is parsed
- **THEN** every segment of a body unit carries one of the kinds `PARAGRAPH`, `HEADING`, `VERSE_LINE`, `LIST_ITEM`
  or `TABLE_CELL`
- **AND** no body segment carries the kind `FOOTNOTE`, `CAPTION`, `TITLE` or an auxiliary kind

#### Scenario: The auxiliary unit carries only auxiliary kinds

- **WHEN** an EPUB with a `dc:title`, a `dc:creator`, a navigation document outside the spine, an XHTML
  `<head><title>` and an image with an `alt` attribute is parsed
- **THEN** its auxiliary unit carries segments of the kinds `METADATA_TITLE`, `METADATA_AUTHOR`, `NAV_LABEL`,
  `TITLE` and `ALT`, and no segment of a body kind

#### Scenario: The auxiliary unit comes after every body unit

- **WHEN** an EPUB whose spine lists 12 content documents is parsed
- **THEN** the document carries 13 units, the first 12 being the spine documents in spine order with `order` values
  `0` to `11`
- **AND** the 13th unit is the auxiliary unit `aux`

### Requirement: Address the skeleton by stable anchor, never by offset

Each segment of a **tree-shaped** skeleton — an EPUB spine document, an FB2 XML document — SHALL locate its source
text by a stable node path plus the index of its line-break-delimited run within that block, and SHALL NOT use a
byte offset, a character offset, or an inserted sentinel node for that purpose. WHERE a format's skeleton is the
original byte buffer rather than a tree, a segment SHALL instead locate its source text by a byte span into that
buffer, and reassembly SHALL copy from the unmodified original buffer into fresh output rather than editing that
buffer in place, so that no segment's span is invalidated by writing back any other segment.

WHERE a segment's text is an attribute value in a tree-shaped document — an image's alternative text — the segment
SHALL locate it by the element's stable node path plus the attribute's name.

**Source**: DD-07, `02_Architecture/03_DOCUMENT_MODEL.md#data-model` ("a stable node path/id plus child index … not a byte offset and not an inserted sentinel node"), FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), ADR-0025 (`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: the segment has to remember exactly which slot in the structure it came from, so a translation can be written back into that same slot. In a tree, a byte offset would be invalidated by the first edit of any earlier segment, and a sentinel node would mean the parser modified the structure it promised not to touch. Plain text and Markdown have no tree to point into, so their slots *are* byte spans — and they stay valid for exactly one reason: nothing ever writes into the buffer they index. The moment reassembly edited that buffer in place, every later span would be wrong, which is why the copy-from-original rule is part of the requirement rather than an implementation detail. An attribute value has no run to index, so its slot is named by the element and the attribute instead.

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

#### Scenario: Alternative text is addressed by element and attribute

- **WHEN** an EPUB paragraph `<p>Before <img src="fig1.png" alt="Figure 1"/> after</p>` is parsed
- **THEN** the auxiliary unit holds a segment with source `Figure 1` anchored to that `img` element's node path and
  the attribute name `alt`
- **AND** the skeleton gains no element and no attribute

### Requirement: Exclude non-translatable blocks from segmentation

The system SHALL produce no segment for a `<pre>` block, a `<pre><code>` code listing, a block-level MathML `<math>`
element, or an XHTML block element whose only translatable content is an inline code span.

The system SHALL likewise produce no segment for an individual **run** within a block whose only content is a
protected span, even where that block's other runs own translatable text.

The system SHALL preserve such a block through the skeleton alone where it stands on its own. Where a book has
nested one inside a block that owns translatable text, the separate requirement covering that case applies instead.

Source: DD-49, `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, FR-DOC-EPUB-9, ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: source code and mathematics must not be translated — renaming a variable or altering an equation
corrupts a technical book. They are excluded from the segment list entirely rather than marked as "do not
translate", because a segment that exists still consumes the token budget and still has to be explained to the
model. The code-only block is added because the structural block rule descends into it: a paragraph containing
nothing but `<code>List.of()</code>` owns no text of its own, so the walk reaches the code span and makes *that* the
segment, handing the model an identifier with no masking possible. A technical book writes a standalone identifier
that way routinely. It is scoped to XHTML because FictionBook uses the same element name for an ordinary prose
style, where a paragraph written entirely in it is real text a reader reads and must still be translated. The
run-level clause is the same argument one level down, and it is not covered by the block-level one: a block that
splits into runs at its line breaks can own real prose in one run and nothing but a protected span in another, so
the block is legitimately a segment source while that one run has nothing left for the model once masking has run.
Emitting it produced a segment whose masked form was a bare `⟦g0⟧` — a model call with nothing to translate and a
placeholder-gate failure risk for no benefit. It is scoped to a *protected span* and deliberately not to "no
character data", because a CDATA section is masked atomically for escaping fidelity rather than untranslatability,
and a block whose only content is CDATA does hold prose a reader reads. The next
paragraph narrows a promise this requirement could not keep once masking exists: a
listing nested inside a text-owning block cannot live in the skeleton alone, because the block around it is a
segment; it lives in the placeholder map instead and is restored identically.

#### Scenario: A code listing produces no segment

- **WHEN** a document contains `<p>Before.</p><pre><code>int x = 1;</code></pre><p>After.</p>`
- **THEN** exactly two segments are produced, for `Before.` and `After.`

#### Scenario: The code listing's text is in no segment

- **WHEN** that same document is parsed
- **THEN** neither segment's `sourceInner` contains `int x = 1;`

#### Scenario: The excluded listing survives the round trip verbatim

- **WHEN** that document is reassembled with zero segment edits
- **THEN** the output contains `<pre><code>int x = 1;</code></pre>` with its text unchanged

#### Scenario: A run holding nothing but a protected span produces no segment

- **WHEN** an EPUB paragraph whose content is `Hi<br/><code>x</code>` is parsed
- **THEN** exactly one segment is produced, for `Hi`

#### Scenario: A run holding a protected span among prose is still a segment

- **WHEN** an EPUB paragraph whose content is `Hi<br/>Call <code>x</code> first.` is parsed
- **THEN** two segments are produced, the second with the masked form `Call ⟦g0⟧ first.`

#### Scenario: Block-level MathML produces no segment

- **WHEN** a document contains a block-level `<math>` element with an `<mi>x</mi>` child
- **THEN** no segment is produced for it

#### Scenario: Block-level MathML survives the round trip unchanged

- **WHEN** that document is reassembled with zero segment edits
- **THEN** the `<math>` element's content is unchanged in the output

#### Scenario: A paragraph holding only an inline code span yields no segment

- **WHEN** a document contains `<p>Before.</p><p><code>List.of()</code></p><p>After.</p>`
- **THEN** exactly two segments are produced, for `Before.` and `After.`

#### Scenario: The standalone code span survives the round trip verbatim

- **WHEN** that document is reassembled with zero segment edits
- **THEN** the output contains `<p><code>List.of()</code></p>` with its text unchanged

#### Scenario: A code span inside prose is still a segment's content

- **WHEN** an EPUB paragraph whose content is `Call <code>List.of()</code> first.` is parsed
- **THEN** one segment is produced whose masked form is `Call ⟦g0⟧ first.`

### Requirement: Never regenerate the skeleton

The system SHALL, when writing translated content back into a tree-shaped skeleton, replace only the inner content
of the segment's own run within its block, and SHALL NOT add, remove, reorder or re-serialize any other node. The
system SHALL NOT regenerate, rebuild, or restructure the skeleton.

WHEN the segment being written back is anchored to an attribute value, the system SHALL replace only that one
attribute's value and SHALL leave the element, its other attributes and its content unchanged.

**Source**: FR-DOC-03, `02_Architecture/03_DOCUMENT_MODEL.md#reassembly`, ADR-0025 (`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: reassembly edits the content of one run and nothing else. Re-serializing the parsed tree on export is expected and fine; building a new tree from the segments is not, because everything the segments do not capture — attributes, comments, structure, ordering — would be lost in the rebuild. The unit of replacement is a run's inner *content* rather than a single text node because a translated paragraph legitimately contains inline markup, and no text node can hold an element. Alternative text is the one closed exception to "only text changes": its value is replaced, and nothing around it moves.

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

#### Scenario: Writing alternative text changes only that attribute

- **WHEN** the alternative-text segment of `<img id="f1" src="fig1.png" alt="Figure 1" title="Fig. 1"/>` receives
  the target `Рисунок 1`
- **THEN** the output element is `<img id="f1" src="fig1.png" alt="Рисунок 1" title="Fig. 1"/>`

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
segments for it — except the EPUB 3 navigation document and the EPUB 2 NCX, whose table-of-contents labels SHALL be
read into auxiliary segments and written back, while their structure, order, `playOrder` values and link targets
SHALL be carried through unchanged.

**Source**: EC-EPUB-3 (`01_Product/03_DOCUMENT_FORMATS.md#epub-edge-cases`), FR-DOC-EPUB-8 (`01_Product/03_DOCUMENT_FORMATS.md#epub`), FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`. In plain words: stylesheets, images, fonts and any stray content document that the spine does not reference are carried, not read. The nav document and the NCX are the two exceptions, because they hold the table of contents a reader's navigation panel shows — one surveyed book kept 494 labels in English beside translated chapter headings. Only their label text changes; where each entry points and in what order stays exactly as it was. Which labels become segments — an NCX label that reads exactly like a navigation-document label does not — is stated in *Produce a book's auxiliary text as translatable segments*.

#### Scenario: A stylesheet is carried but not segmented

- **WHEN** an EPUB containing `OEBPS/styles.css` is parsed
- **THEN** no segment references `OEBPS/styles.css`
- **AND** the reassembled output contains `OEBPS/styles.css` with identical decompressed bytes

#### Scenario: A navigation document outside the spine yields label segments

- **WHEN** an EPUB whose navigation document `OEBPS/toc01.html` is not in the spine and lists 3 entries is parsed
- **THEN** the auxiliary unit holds 3 navigation-label segments taken from those entries' link text

#### Scenario: NCX links survive a translated label

- **WHEN** an NCX `navPoint` with `playOrder="4"` whose label is `Chapter Three` and whose content points at
  `c03.xhtml#start` receives the target `Розділ третій`
- **THEN** the output `navPoint` still has `playOrder="4"` and still points at `c03.xhtml#start`, and its label reads
  `Розділ третій`

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

On export the system SHALL set the target language by replacing the first `dc:language` element in the OPF — found
directly under the metadata element or, failing that, inside a nested legacy `dc-metadata` wrapper — adding one with
the Dublin Core `dc:` prefix if none is present, and SHALL leave any further `dc:language` elements untouched.

On export the system SHALL also write the target language into every one of these whose value equals the run's source
language — the language the run translated from, or, when the writer is given none, the language the package declares
— compared after normalization (so `en-US` and `en` match): an OPF `<meta property="dcterms:language">` element, the
OPF `<package>` element's `xml:lang` attribute, and the `xml:lang` and `lang` attributes of the `<html>` and `<body>`
elements of each XHTML content document and of the navigation document. A language attribute carrying any other
language SHALL be left unchanged, and the system SHALL NOT change the language attribute of any other element.

**Source**: FR-DOC-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-EPUB-6 (`01_Product/03_DOCUMENT_FORMATS.md#epub`), `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`), `docs/next_features.md` §1. In plain words: a translated book that still declares itself English will be read aloud, hyphenated and spell-checked as English. Only the first `dc:language` is replaced because later entries may legitimately record other languages present in the book. Reading systems pick hyphenation, fonts and the text-to-speech voice from the language attributes on the package and on each chapter's root, not only from `dc:language` — one surveyed book kept `xml:lang="en"` on 116 chapters after being translated into Ukrainian — so those are rewritten too, but only where they carried the source language: a chapter marked as Latin is still Latin after translation. The source language is the run's, not only the package's, because a book whose package says English while its chapters say Ukrainian is translated from Ukrainian, and it is the chapters' `uk` that must become the target. Only the two root elements of each document are rewritten; this is a known limit — a chapter wrapped in `<div lang="en">` still says English inside its body.

#### Scenario: The first entry is replaced and the second is left alone

- **WHEN** an OPF declaring `<dc:language>en</dc:language>` then `<dc:language>la</dc:language>` is exported with
  target language `uk`
- **THEN** the output OPF declares `<dc:language>uk</dc:language>` then `<dc:language>la</dc:language>`

#### Scenario: A missing declaration is added

- **WHEN** an OPF declaring no `dc:language` at all is exported with target language `uk`
- **THEN** the output OPF contains exactly one `dc:language` element, written `<dc:language>uk</dc:language>` in the
  Dublin Core namespace

#### Scenario: A declaration nested in the legacy wrapper is replaced, not duplicated

- **WHEN** an OPF whose only language declaration is `<dc-metadata><dc:language>en-GB</dc:language></dc-metadata>` is
  exported with target language `uk`
- **THEN** the nested element reads `uk` and no second `dc:language` element is added

#### Scenario: The dcterms language meta follows

- **WHEN** an OPF carrying `<dc:language>en</dc:language>` and `<meta property="dcterms:language">en</meta>` is
  exported with target language `uk`
- **THEN** the meta element reads `uk`

#### Scenario: The package language attribute follows

- **WHEN** an OPF whose `<package>` element carries `xml:lang="en"` is exported with target language `uk`
- **THEN** the output `<package>` element carries `xml:lang="uk"`

#### Scenario: Chapter root and body language attributes follow

- **WHEN** a book declaring `en-US` has a content document `<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en"
  lang="en">` with `<body lang="en">` and is exported from source language `en` with target language `uk`
- **THEN** that document's `<html>` carries `xml:lang="uk"` and `lang="uk"`, and its `<body>` carries `lang="uk"`

#### Scenario: A language attribute naming another language is kept

- **WHEN** a book declaring `en` has a content document whose `<html>` carries `xml:lang="la"` and is exported with
  target language `uk`
- **THEN** that `<html>` still carries `xml:lang="la"`

#### Scenario: The run's source language decides which attributes follow

- **WHEN** a book whose `<package>` carries `xml:lang="en"` has a content document whose `<html>` carries
  `xml:lang="uk"`, and the book is translated from `uk` to `de` and exported
- **THEN** that `<html>` carries `xml:lang="de"`
- **AND** the `<package>` element still carries `xml:lang="en"`, because `en` is not the source language `uk`

#### Scenario: Without a source language the package's declaration is used

- **WHEN** a book whose package declares `en` has a content document whose `<html>` carries `lang="en"`, and it is
  written with target language `uk` and no source language
- **THEN** that `<html>` carries `lang="uk"`

#### Scenario: The navigation document's root follows

- **WHEN** a navigation document whose `<html>` carries `xml:lang="en"` is exported from `en` to `uk`
- **THEN** its `<html>` carries `xml:lang="uk"`

#### Scenario: A language set inside the body is kept

- **WHEN** a content document `<body lang="en"><div lang="en"><p>Text.</p></div></body>` is exported from `en` to `uk`
- **THEN** the `<body>` carries `lang="uk"` and the `<div>` still carries `lang="en"`

### Requirement: Reject a corrupt container without partial import

IF the container, the OPF, or the spine declaration is missing or malformed, or no spine item's file exists in the
archive, THEN the system SHALL return a validation failure naming the reason and SHALL NOT produce a partially parsed
document. WHEN a spine item names a file the archive does not contain, the system SHALL skip that item, read the
remaining spine documents in spine order with dense unit orders, and report the number skipped.

Source: EC-EPUB-2, `02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`.
In plain words: a book whose structure is missing cannot be translated and says so at import. A book whose table of
contents lists files an authoring tool deleted is a different case — one real book lists twenty pages and ships nine,
and every reader shows the nine — so what exists is read and the gap is reported, not hidden behind a refusal.

#### Scenario: A missing OPF is a validation failure

- **WHEN** a file whose `META-INF/container.xml` points at `OEBPS/content.opf`, which the archive does not contain, is
  opened
- **THEN** the result carries `ErrorCode.validation`
- **AND** no document is returned

#### Scenario: A spine item whose file is absent is skipped

- **WHEN** an EPUB whose spine declares `c01`, `c02`, `c03` is opened and `OEBPS/c02.xhtml` is not in the archive
- **THEN** the document's units are `OEBPS/c01.xhtml` and `OEBPS/c03.xhtml`, with `order` values `0` and `1`

#### Scenario: A spine none of whose files exist is a validation failure

- **WHEN** an EPUB whose spine declares `c01` and `c02` is opened and neither file is in the archive
- **THEN** the result carries `ErrorCode.validation`

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

The system SHALL give each unit an identity that is unique within its document, and SHALL derive a body unit's
identity from the source's own structure: an EPUB spine document's href, an FB2 body's source name suffixed by its
position, or the source file name for the single body unit of a Markdown or plain-text file.

The system SHALL give the auxiliary unit the id `aux`, the href of the EPUB package document or, for the other
formats, the source file name, and the media type `application/x-bookloom-auxiliary`.

A body segment's id SHALL be its unit's id, a colon and its position in the unit. An auxiliary segment's id SHALL be
`aux:` followed by the name of the slot it was read from, never by a position shared with other slots: `title`;
`creator:<i>` and `description:<i>`, with `<i>` counted from `0` in document order; `nav:<entry path>`;
`ncx:<navPoint id>`; `head-title:<content document href>`; `alt:<content document href>:<node path>` for an EPUB image
and `alt:<file name>:img<k>` for a Markdown image; `fm:<key>` for a frontmatter value.

**Source**: FR-DOC-01, FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `02_Architecture/03_DOCUMENT_MODEL.md#data-model`, `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, `02_Architecture/06_DATA_MODEL_SQLITE.md#tables` (`segments.id` is the primary key), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: every segment id is built from its unit's id, and those ids are the primary key of the segment table, so two units sharing an id would collide the moment anything is stored. An FB2 book's bodies all come from one file and would otherwise share one identity, which is why a body's position is part of its id. Markdown and plain text now have two units — the body and the auxiliary unit — so "one unit per file" no longer describes them. Auxiliary segments are named after their slot rather than numbered, because slots come and go independently: a book that gains one table-of-contents entry must not renumber its title, or every translation and review decision stored under the old id would land on the wrong text.

#### Scenario: An FB2 book's two bodies get distinct unit ids

- **WHEN** an FB2 file named `book.fb2` containing a main `<body>` and a `<body name="notes">` is parsed
- **THEN** the body units' ids are `book.fb2#0` and `book.fb2#1`, followed by the auxiliary unit `aux`
- **AND** all three units report `href` `book.fb2`

#### Scenario: A single-unit format uses its file name

- **WHEN** a file named `chapter.md` is parsed
- **THEN** the document contains one body unit whose id and href are both `chapter.md`, followed by the auxiliary unit
- **AND** the body unit's media type is `text/markdown`

#### Scenario: A plain-text file is one unit with the plain-text media type

- **WHEN** a file named `notes.txt` is parsed
- **THEN** the document contains one body unit whose id and href are both `notes.txt`, with the media type
  `text/plain`
- **AND** it is followed by the auxiliary unit `aux`, which holds no segment

#### Scenario: Segment ids are built from the unit id

- **WHEN** the unit `book.fb2#1` yields two segments
- **THEN** their ids are `book.fb2#1:0` and `book.fb2#1:1`

#### Scenario: The auxiliary unit is named and typed as auxiliary

- **WHEN** an EPUB whose package document is `OEBPS/content.opf` is parsed
- **THEN** its last unit has the id `aux`, the href `OEBPS/content.opf` and the media type
  `application/x-bookloom-auxiliary`

#### Scenario: Auxiliary segment ids name their slot

- **WHEN** an EPUB whose package declares the `dc:title` `Frankenstein` and the two `dc:creator` elements
  `Mary Shelley` and `Percy Shelley` is parsed
- **THEN** its title segment's id is `aux:title`
- **AND** its author segments' ids are `aux:creator:0` for `Mary Shelley` and `aux:creator:1` for `Percy Shelley`

#### Scenario: A frontmatter value is named by its key

- **WHEN** a Markdown file whose frontmatter holds `title: The Book` is parsed
- **THEN** the segment holding `The Book` has the id `aux:fm:title`

#### Scenario: A new navigation entry renumbers neither the title nor the authors

- **WHEN** that EPUB is parsed, and parsed again after a fourth entry was added at the start of its navigation
  document
- **THEN** in both parses `aux:title` holds `Frankenstein` and `aux:creator:0` holds `Mary Shelley`
- **AND** every body segment's id is the same in both parses

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

The system SHALL parse an FB2 book as a single XML document and SHALL preserve its comments, CDATA sections, XML
declaration, declared encoding, namespace prefixes, and the whitespace between block-level elements, across a round
trip. Entity references SHALL be expanded to the characters they stand for — from the book's own header, or from the
standard named-entity list bundled with the application — and the application SHALL never fetch a DTD or any other
external resource a book names.

Source: FR-DOC-FB2-1 (`01_Product/03_DOCUMENT_FORMATS.md#fb2`), FR-DOC-02,
`02_Architecture/03_DOCUMENT_MODEL.md#xml-round-trip-config`.
In plain words: FB2 keeps everything in one file, so everything the file carries is at risk from one careless
re-serializer. A parser that collapses CDATA to text or drops a comment produces a book that still opens and is
still a different document — and in verse, the whitespace between blocks is the line layout. Entities are the one
thing deliberately not kept as written: a reader shows the character, so the character is what fidelity means.
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

WHERE detection is reached and the bytes decode without error under more than one single-byte encoding, the system
SHALL prefer the encoding under which the decoded text is coherent in a known script over one under which the same
bytes decode to unrelated characters.

Source: `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, FR-DOC-02, FR-DOC-FB2-3, FR-DOC-TXT-1.
In plain words: getting the encoding wrong corrupts every non-Latin character in the book, and the corruption is not
visible until someone reads the translated text. The order encodes evidence strength: a byte-order mark is a fact, a
declaration is a claim, and detection is a guess, so each is consulted only when the stronger evidence above it is
absent. The mark is recorded separately from the charset because re-emitting a file that had none with one added is
itself a change. An EPUB records neither, because each of its content documents declares its own encoding and the
container as a whole has no answer to give. The added clause exists because every single-byte encoding decodes every
byte — nothing fails — so "it decoded" is not evidence. Three surveyed books resolve to a Western European encoding
and render their Russian text as `Ñïàñèáî` instead of `Спасибо`, with no error raised anywhere.

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

#### Scenario: A small Cyrillic region in a mostly-ASCII file is not resolved as Western European

- **WHEN** a TXT file with no byte-order mark holding 2,000 characters of ASCII French prose followed by one
  195-byte line whose bytes decode under `windows-1251` to `Спасибо, что скачали книгу в бесплатной электронной
  библиотеке Royallib.com` and under `windows-1252` to `Ñïàñèáî, ÷òî ñêà÷àëè êíèãó` is parsed
- **THEN** the document records charset `windows-1251`

#### Scenario: A file with no Cyrillic bytes is not dragged to a Cyrillic encoding

- **WHEN** a TXT file with no byte-order mark holding 2,000 characters of `windows-1252`-encoded French prose with
  accented letters and no bytes that decode to Cyrillic is parsed
- **THEN** the document records charset `windows-1252`

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
only the byte spans of segments that carry target text and the value of a top-level frontmatter `lang` key, leaving
every other byte untouched, and SHALL NOT re-render the document from its syntax tree.

WHERE a character of the output cannot be represented in the encoding the source was read with, the system SHALL
instead write the whole spliced text as UTF-8, as *Write a TXT or Markdown export as UTF-8 when its encoding cannot
hold the translation* states; every character outside the substituted spans SHALL still be the source's character.

**Source**: FR-DOC-MD-4, FR-DOC-03, DD-43, `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, ADR-0025 (`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`), ADR-0029 (`docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`), `01_Product/03_DOCUMENT_FORMATS.md#markdown`. In plain words: re-rendering a Markdown file from its tree normalises formatting everywhere — emphasis markers, bullet characters, fence styles, and whether the file ends with a newline — in parts of the document nobody translated. Copying the original and replacing only translated spans makes every untouched byte identical by construction, which is both stronger and simpler. The frontmatter `lang` value is the one span outside a segment that changes, because it is where a Markdown book says what language it is in. A switch to UTF-8 re-encodes every byte but changes no character.

#### Scenario: An untranslated round trip is byte-identical

- **WHEN** a Markdown file whose frontmatter holds no `lang` key is parsed and reassembled with no segment receiving
  target text
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

WHERE a character of the output cannot be represented in the encoding the source was read with, the system SHALL
instead write the whole spliced text as UTF-8, as *Write a TXT or Markdown export as UTF-8 when its encoding cannot
hold the translation* states; every character outside the substituted spans SHALL still be the source's character.

**Source**: FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), FR-DOC-03, DD-43, `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, ADR-0029 (`docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`). In plain words: because everything outside a translated paragraph is copied rather than regenerated, a TXT round trip with nothing translated is byte-identical by construction — the encoding, the line endings and the byte-order mark survive because nothing re-encodes them. The one exception is a translation the source's code page cannot hold: then the whole file is re-encoded as UTF-8, every character kept. This is the one format where byte equality is the correct assertion rather than an over-strict one.

#### Scenario: An untranslated round trip is byte-identical

- **WHEN** a TXT file is parsed and reassembled with no segment receiving target text
- **THEN** the output file's bytes are identical to the source file's

#### Scenario: Only the translated paragraph's bytes change

- **WHEN** a TXT file containing `One.\n\nTwo.\n` has only its second segment written back as `Два.`
- **THEN** the output is `One.\n\nДва.\n`
- **AND** the bytes before the second paragraph are unchanged

### Requirement: Declare no language metadata in formats that carry none

WHERE the exported document has no language-metadata field — a TXT document, or a Markdown document whose frontmatter
holds no top-level `lang` key — the system SHALL leave the content unchanged rather than inserting a language
declaration.

WHERE a Markdown document's frontmatter holds a top-level `lang` key, the system SHALL replace that key's value with the
target language tag, keeping the value's quote style, SHALL change nothing else in the frontmatter, and SHALL leave a
`language` key as it is.

**Source**: FR-DOC-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`) read against `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, which specifies a language update for EPUB and FB2 only; DD-43; FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`); the owner's frontmatter decision recorded in `openspec/changes/archive/2026-10-19-complete-translation-workflow/design.md` (D12). In plain words: the export contract takes a target language for every format, but TXT has nowhere to put it and a Markdown file has a place only when its frontmatter already declares one. Inventing a place — a `lang:` key in frontmatter that had none, a header line in TXT — would add content the source never had, and would break the byte-exact round trip. A Markdown file that already says `lang: en` is different: leaving it would make the translated file claim to be English, so its value — and only its value, in the quotes it had — becomes the target. A `language` key is left alone; the rewrite is limited to `lang` on purpose.

#### Scenario: A Markdown export adds no language key

- **WHEN** a Markdown document with no frontmatter is exported with target language `uk`
- **THEN** the output contains no frontmatter block and no `lang` key

#### Scenario: A frontmatter without a lang key gains none

- **WHEN** a Markdown document opening with `---\ntitle: The Book\n---\n` is exported with target language `uk` and no
  segment carries target text
- **THEN** the output still opens with `---\ntitle: The Book\n---\n`

#### Scenario: An existing lang value is replaced

- **WHEN** a Markdown document opening with `---\ntitle: The Book\nlang: en\n---\n` is exported with target language
  `uk` and no segment carries target text
- **THEN** the output opens with `---\ntitle: The Book\nlang: uk\n---\n`

#### Scenario: A quoted lang value keeps its quotes

- **WHEN** a Markdown document whose frontmatter holds `lang: "en-US"` is exported with target language `uk`
- **THEN** the output's frontmatter holds `lang: "uk"`

#### Scenario: A language key is left alone

- **WHEN** a Markdown document whose frontmatter holds `language: en` and no `lang` key is exported with target
  language `uk`
- **THEN** the output's frontmatter still holds `language: en` and holds no `lang` key

#### Scenario: A TXT export with a target language is still byte-identical

- **WHEN** a TXT document is exported with target language `uk` and no segment carries target text
- **THEN** the output file's bytes are identical to the source file's

### Requirement: Populate the declared language and book metadata from each format's own source

The system SHALL record the language the book declares and its title and author metadata by reading the location
each format provides for them, and SHALL record no value where the source provides none.

WHERE an EPUB package nests its Dublin Core metadata elements inside a legacy wrapper element rather than placing
them directly under the metadata element, the system SHALL read them from that nested location.

Source: FR-IMPORT-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), FR-IMPORT-03, EC-LANG-1
(`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), `02_Architecture/03_DOCUMENT_MODEL.md#data-model`.
In plain words: the declared language is what a later step compares against the language actually detected from the
content, so the mismatch state has something to compare. Title and author are what the import card shows the user.
Both are frequently absent or wrong in real books — one surveyed FB2 declares English on a French book and another
declares no language at all — so absence has to be representable rather than guessed at. The added clause covers an
older packaging convention still present in the wild: a surveyed book carries its title, author and language one
level deeper than the current layout puts them, and is currently reported as having none of the three, which is
indistinguishable from a book that genuinely declares nothing.

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

#### Scenario: An empty declaration element is recorded as absent, not as an empty value

- **WHEN** an FB2 document whose `<title-info>` contains `<lang></lang>` is parsed
- **THEN** the document records no declared language

#### Scenario: A direct declaration outranks one nested in a legacy wrapper

- **WHEN** an EPUB whose package metadata contains a direct `<dc:title>Direct Title</dc:title>` and also nests
  `<dc-metadata><dc:title>Nested Title</dc:title></dc-metadata>` is parsed
- **THEN** the document's metadata contains the title `Direct Title`

#### Scenario: Metadata nested in a legacy wrapper is read, not dropped

- **WHEN** an EPUB whose package metadata contains `<dc-metadata><dc:title>Alice's Adventures in
  Wonderland</dc:title><dc:creator>Lewis Carroll</dc:creator><dc:language>en-GB</dc:language></dc-metadata>` is
  parsed
- **THEN** the document's declared language is `en-GB`
- **AND** its metadata contains the title `Alice's Adventures in Wonderland` and the author `Lewis Carroll`

### Requirement: Prove that segmentation covers the document's translatable text

The system's per-format golden tests SHALL assert that the segments emitted for a fixture cover at least a stated
proportion of that fixture's visible text, counting text inside deliberately excluded blocks and protected spans as
not translatable.

The measurement SHALL compare like with like for every format: the text taken from a segment and the text taken from
the source SHALL both have inline markup removed, and both SHALL be decoded with the encoding resolved for that
document rather than an assumed one.

Source: FR-DOC-01, FR-DOC-09, ADR-0027 (`docs/adr/ADR-0027-structural-block-segmentation.md`), DD-49
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-49-code-and-technical-content-preservation`),
`04_Build_and_Release/06_TESTING_STRATEGY.md`.
In plain words: every other assertion in the document suite checks that nothing was *damaged*, and a book that
yields no segments at all satisfies every one of them perfectly — the skeleton round-trips, the bytes match, the
gate is green, and the product does nothing. This is the only assertion that checks that something *happened*. A
survey of 194 real books found 42 that would have imported empty with no test noticing. The wording widens from
"blocks" to "blocks and protected spans" because a block whose only content is an inline code span now yields no
segment: its text leaves the numerator, so leaving it in the denominator would report a shortfall the parser did not
cause and drag every technical book's measured coverage down.

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

#### Scenario: A code listing's text is outside the measurement

- **WHEN** coverage is measured for a fixture containing `<pre><code>int x = 1;</code></pre>`
- **THEN** the text `int x = 1;` is counted in neither the segments nor the source total

#### Scenario: A standalone inline code span's text is outside the measurement

- **WHEN** coverage is measured for a fixture containing `<p><code>List.of()</code></p>`
- **THEN** the text `List.of()` is counted in neither the segments nor the source total

#### Scenario: A code span inside prose is still inside the measurement

- **WHEN** coverage is measured for a fixture containing `<p>Call <code>List.of()</code> first.</p>`
- **THEN** the text `List.of()` is counted in both the segments and the source total

#### Scenario: Markdown inline syntax does not count as missed text

- **WHEN** a fixture Markdown file whose every block is segmented contains the inline spans `**bold**` and
  `` `code` ``
- **THEN** the coverage assertion reports a coverage above `0.95`

#### Scenario: A non-UTF-8 plain-text fixture is measured in its own encoding

- **WHEN** a fixture TXT file encoded in `windows-1251` whose every paragraph is segmented is measured
- **THEN** the coverage assertion reports a coverage above `0.99`

#### Scenario: Inline markup is stripped for every format, not only some

- **WHEN** a fixture EPUB paragraph whose content is `<b>bold</b> text` is segmented and measured
- **THEN** the coverage assertion reports a coverage above `0.95`

#### Scenario: A declared floor is not left slack against the corrected measurement

- **WHEN** each fixture's coverage is measured and compared against the floor that fixture declares
- **THEN** for every fixture the measured coverage is at or above its declared floor
- **AND** the declared floor is no more than `0.02` below the measured coverage, so a floor cannot silently absorb
  a real regression

### Requirement: Gate every fixture through the port on a real file

The system SHALL hold every round-trip fixture in one enumerated catalogue, and SHALL exercise each catalogued
fixture by writing it to a real file, opening that file through the document port, reassembling it with zero segment
edits, and asserting the fixture's declared segment count, its declared placeholder count, its format's canonical
comparison and its coverage floor. Each fixture's expectations SHALL be declared with the fixture and SHALL NOT be
computed from the parser's own output.

Source: FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-01, FR-DOC-04, ADR-0027
(`docs/adr/ADR-0027-structural-block-segmentation.md`), `04_Build_and_Release/06_TESTING_STRATEGY.md`,
`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`.
In plain words: a fixture that exists but is not asserted proves nothing. The placeholder count joins the segment
count for the same reason the segment count exists: a masker that silently stopped emitting tokens would pass every
identity check trivially — mask nothing, restore nothing, compare equal — and only a number declared by hand next to
the fixture catches it.

#### Scenario: Every catalogued fixture is driven through the port

- **WHEN** the fixture catalogue is exercised
- **THEN** each fixture is written to a file, opened through the document port and reassembled with no segment
  receiving target text

#### Scenario: Every catalogued fixture meets its declared expectations

- **WHEN** the fixture catalogue is exercised
- **THEN** each one meets its declared segment count, its declared placeholder count, its format's canonical
  comparison and its coverage floor

#### Scenario: Every catalogued fixture round-trips through the port

- **WHEN** the fixture catalogue is exercised
- **THEN** each fixture is written to a file, opened through the document port and reassembled with no segment
  receiving target text
- **AND** each one meets its declared segment count, its format's canonical comparison and its coverage floor

#### Scenario: A fixture's declared placeholder count is asserted

- **WHEN** the catalogue is exercised
- **THEN** the total number of placeholders across a fixture's segments equals the count declared with it

#### Scenario: A fixture stacking several pathologies is gated like any other

- **WHEN** a fixture whose prose is `<div class="paragraph">` elements wrapped `p > span > i` and split by `<br/>`
  is exercised
- **THEN** it is opened, reassembled and asserted by the same catalogue-driven gate as a single-pathology fixture

#### Scenario: An ungated fixture is not possible

- **WHEN** a new fixture builder is added to the catalogue
- **THEN** it is exercised by the sweep with no further test being written

### Requirement: Pass a golden round-trip test for FB2

The system SHALL pass a golden round-trip test in which a fixture FB2 book is parsed and reassembled with zero
segment edits, and the output is **canonical-XML-equal** to the source over the re-parsed tree apart from the
declared target-language element, with the declared encoding compared as a value and every `<binary>` payload
compared exactly; a fixture that legitimately switches encoding to UTF-8 is excluded from that comparison and
asserted against a re-parsed canonical tree instead.

WHERE the source's title information declares no language element, the output SHALL carry one declaring the target
language, and the golden comparison SHALL treat that single added element as expected rather than as a difference.

Source: FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43, FR-DOC-FB2-7, FR-DOC-07,
`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`,
`02_Architecture/03_DOCUMENT_MODEL.md#golden-round-trip-test`, EC-FB2-1.
In plain words: canonical XML comparison absorbs the entity spelling and attribute quoting a faithful writer may
legitimately change, while still catching a lost element, a dropped comment or a rewritten id. It also absorbs the
encoding declaration and may re-wrap long text — which is why those two are asserted separately, since they are
precisely what FB2 puts at risk. The encoding is compared as a *value* rather than as declaration text because real
books write it with single quotes and a writer will emit double quotes for a document it preserved perfectly. The
added clause resolves a genuine contradiction between two obligations this capability already carries: the writer is
required to add a language declaration on export, and a zero-edit reassembly is required to be canonical-equal. For a
book declaring no language, both cannot hold, and a surveyed book proves it is not hypothetical. The requirement that
gives way is identity, because stamping the target language is the point of the export.

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

#### Scenario: A book declaring no language gains exactly one element

- **WHEN** a fixture FB2 book whose `<title-info>` contains no `<lang>` element is parsed and reassembled with no
  segment receiving target text and target language `uk`
- **THEN** the golden test passes
- **AND** the output's `<title-info>` contains `<lang>uk</lang>`
- **AND** the two canonical forms differ in nothing else

#### Scenario: The carve-out does not hide a real loss in the same book

- **WHEN** a fixture FB2 book whose `<title-info>` contains no `<lang>` element is reassembled with no segment
  receiving target text, and the output both gains `<lang>uk</lang>` and drops one `<p>` element present in the
  source
- **THEN** the golden test fails

#### Scenario: An empty language element is replaced, not treated as absent

- **WHEN** a fixture FB2 book whose `<title-info>` contains `<lang></lang>` is parsed and reassembled with no
  segment receiving target text and target language `uk`
- **THEN** the golden test passes
- **AND** the output's `<title-info>` contains `<lang>uk</lang>`

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

**Source**: FR-DOC-09, DD-43 (`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`), FR-DOC-TXT-3. In plain words: TXT is the one format where exact bytes are the right assertion rather than an over-strict one, because a zero-edit export never re-encodes anything — it copies the original buffer and substitutes spans, and only a translated character the source's code page cannot hold switches a file to UTF-8, which a zero-edit export never carries. Anything weaker than byte equality here would fail to notice a charset or line-ending change that the mechanism makes impossible, and so would fail to notice the mechanism being replaced by one that does not. The encoding-switched case is compared by its re-parsed text instead, as *Write a TXT or Markdown export as UTF-8 when its encoding cannot hold the translation* states.

#### Scenario: A no-edit TXT round trip is byte-identical

- **WHEN** a fixture TXT book with a UTF-8 byte-order mark and `\r\n` line endings is parsed and reassembled with no
  segment receiving target text
- **THEN** the output file's bytes are identical to the source file's, byte-order mark and line endings included

### Requirement: Parse a content document that self-closes a raw-text element

WHEN an EPUB content document contains an element written in XML self-closing form whose HTML content model is
raw text — `<script/>`, `<style/>` or `<noscript/>` — the system SHALL parse the elements that follow it as
markup, and SHALL NOT treat the remainder of the document as that element's text content.

Source: FR-DOC-01, FR-IMPORT-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`,
`#fr-import`), `02_Architecture/01_SYSTEM_ARCHITECTURE.md#skeleton-and-segments`.
In plain words: `<script src="js/book.js"/>` is valid XHTML and is unrepresentable in HTML, where a `<script>`
element ends only at a literal `</script>`. An HTML parser therefore swallows everything after it — including the
whole `<body>` — as script text, and the book imports with no segments, no error, and nothing for the user to
translate. Twelve of 194 surveyed books do exactly this, all from one converter. The three element names are the
measured set: a self-closed `<title/>` or `<textarea/>` parses correctly and is not covered here.

#### Scenario: A self-closed script element in the head does not hide the body

- **WHEN** a content document whose `<head>` contains `<script src="js/book.js"/>` and whose `<body>` contains two
  `<p>` elements of prose is parsed
- **THEN** both paragraphs become segments

#### Scenario: A self-closed style element in the head does not hide the body

- **WHEN** a content document whose `<head>` contains `<style/>` and whose `<body>` contains one `<p>` of prose is
  parsed
- **THEN** that paragraph becomes a segment

#### Scenario: A document already using the paired form is unaffected

- **WHEN** a content document whose `<head>` contains `<script src="js/book.js"></script>` and whose `<body>`
  contains two `<p>` elements is parsed
- **THEN** both paragraphs become segments

#### Scenario: A self-closed script element in the body does not hide its siblings

- **WHEN** a content document whose `<body>` contains one `<p>` of prose, then `<script src="js/book.js"/>`, then a
  second `<p>` of prose is parsed
- **THEN** both paragraphs become segments

#### Scenario: A script tag written as literal text in a code listing is not rewritten

- **WHEN** a content document whose `<body>` contains a `<pre><code>` listing whose text is the literal
  `<script src="x.js"/>`, followed by one `<p>` of prose, is parsed and reassembled with zero segment edits
- **THEN** the listing's text in the output is still the literal `<script src="x.js"/>`
- **AND** the paragraph becomes a segment

#### Scenario: The expanded form is accepted by the golden comparison

- **WHEN** a source content document holding `<script src="js/book.js"/>` is reassembled with zero segment edits
  and the output holds `<script src="js/book.js"></script>`
- **THEN** the EPUB golden round-trip comparison passes, because both sides are normalized identically before
  comparison

#### Scenario: The golden comparison still fails when a real element is lost

- **WHEN** a source content document holding `<script src="js/book.js"/>` and two `<p>` elements is compared
  against an output holding `<script src="js/book.js"></script>` and only one of those `<p>` elements
- **THEN** the EPUB golden round-trip comparison fails

### Requirement: Reassemble a document with zero edits without changing its structure

WHEN a document is reassembled and no segment carries target text, the system SHALL produce an output whose
re-parsed structure equals the source's, and reassembling that output again SHALL produce a structurally equal
result — the write SHALL be a fixed point.

IF an element in the source is written in XML self-closing form and its HTML content model is not empty, THEN the
system SHALL NOT emit an additional copy of that element, SHALL NOT move a sibling element inside it, and SHALL NOT
duplicate its `id` attribute.

Source: FR-DOC-03, FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43,
ADR-0025 (`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`),
`02_Architecture/01_SYSTEM_ARCHITECTURE.md#skeleton-and-segments`.
In plain words: "the skeleton is never regenerated, only text nodes change" has to hold on a write that changes no
text at all, or it does not hold anywhere. Three real books show it failing today. One duplicates an index anchor's
`id` four times and pulls a chapter heading inside it. Others write a book, and writing the result again produces
different bytes — so the operation has no stable answer. The fixed-point phrasing is deliberate: it is checkable
without a comparator, by writing twice and comparing.

#### Scenario: Writing twice produces the same bytes

- **WHEN** a fixture EPUB is reassembled with zero segment edits, and that output is opened and reassembled again
  with zero segment edits
- **THEN** the second output is canonical-equal to the first

#### Scenario: A self-closed anchor does not duplicate its id

- **WHEN** a content document containing `<a id="idm001" data-type="indexterm"/>` followed by `</p></div></section>`
  and a following `<section><div><h1>Next Chapter</h1>` is parsed and reassembled with zero segment edits
- **THEN** the output contains exactly one element carrying `id="idm001"`
- **AND** the `<h1>` is not a descendant of any `<a>` element

#### Scenario: A paragraph wrapping a division does not gain a phantom sibling

- **WHEN** a content document containing `<p class="p1"><div class="image"><img src="i.jpg"/></div></p>` is parsed
  and reassembled with zero segment edits, and that output is reassembled again
- **THEN** the two outputs are canonical-equal

#### Scenario: A self-closed inline element does not swallow the element after it

- **WHEN** a content document containing `<p>Before <span class="hl" id="s1"/> after</p><p>Next paragraph.</p>` is
  parsed and reassembled with zero segment edits
- **THEN** the output contains exactly one element carrying `id="s1"`
- **AND** the second `<p>` is not a descendant of the `<span>`

#### Scenario: Every catalogued EPUB fixture's write is a fixed point

- **WHEN** each EPUB fixture registered in the fixture catalogue is reassembled with zero segment edits and that
  output is reassembled again with zero segment edits
- **THEN** for every one of them the second output is canonical-equal to the first

### Requirement: Preserve the leading line break of a preformatted block

WHEN a document containing a preformatted block whose content begins with two or more line breaks is reassembled,
the system SHALL preserve every one of those line breaks in the output.

IF a preformatted block's content does not begin with a line break, or begins with a carriage-return–line-feed pair,
or is empty, THEN the system SHALL NOT insert a line break that the source did not have.

Source: FR-DOC-03, FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43, DD-49,
`02_Architecture/01_SYSTEM_ARCHITECTURE.md#skeleton-and-segments`.
In plain words: HTML discards one line break immediately after a preformatted block's start tag, and a serializer
is required to put one back when the content begins with one. Doing the first without the second deletes a line of
the book every time it is written. Inside a preformatted block that line is content — a blank line in a poem — and
DD-49 requires such blocks to survive untouched. One leading break is safe because both sides discard it alike;
two or more is where the loss becomes visible, which is why a fixture needs two. The negative half of this
requirement is not symmetry for its own sake: the parser's discard fires only for a bare line feed, so a block
beginning with a carriage-return–line-feed pair loses nothing today, and a restore written as "if the content starts
with any line break, prepend one" would insert a break into a document that never lost one. An empty block has no
first child at all, so a restore that reaches for one fails outright.

#### Scenario: A block with no leading line break gains none

- **WHEN** a content document containing `<pre>code here</pre>`, whose content does not begin with a line break, is
  parsed and reassembled with zero segment edits
- **THEN** re-parsing the output yields a preformatted block whose content still begins with `code here` and has no
  leading line break

#### Scenario: A block beginning with a carriage-return–line-feed pair is unchanged

- **WHEN** a content document containing a `<pre>` whose content begins with two carriage-return–line-feed pairs
  followed by `X` is parsed and reassembled with zero segment edits
- **THEN** re-parsing the output yields a preformatted block whose content still begins with exactly those two pairs

#### Scenario: An empty preformatted block reassembles without failing

- **WHEN** a content document containing an empty `<pre></pre>` element is parsed and reassembled with zero segment
  edits
- **THEN** the reassembly succeeds and the golden round-trip comparison passes

#### Scenario: A poem's blank first line survives reassembly

- **WHEN** a content document containing `<pre class="poem">` whose content begins with two line breaks followed by
  `        “Speak roughly to your little boy,` is parsed and reassembled with zero segment edits
- **THEN** the output's **markup** still holds two line breaks immediately after the `<pre class="poem">` start tag

  This one scenario is asserted against the output's serialized bytes rather than against a re-parsed tree, and
  that is not a weaker assertion — it is the only satisfiable one. The parse-side discard fires on **every**
  parse, including a parse of a perfectly correct output, so a re-parsed tree shows one line break whatever the
  writer did. Measured: an output whose markup holds two yields a tree holding one. Asserting on the tree here
  would be a test that cannot pass, and rewriting the writer to make it pass would mean emitting three.

#### Scenario: A single leading line break is unchanged

- **WHEN** a content document containing `<pre>` whose content begins with one line break followed by `code here`
  is parsed and reassembled with zero segment edits
- **THEN** the golden round-trip comparison passes

### Requirement: Classify a malformed translated fragment as a validation failure

IF target text supplied for a segment cannot be parsed as the inline markup its format requires — for example text
containing a bare `&` or `<` that does not open a valid entity or element — THEN the system SHALL return a failed
result carrying `ErrorCode.validation`, and SHALL NOT return `ErrorCode.internal`.

Source: FR-DOC-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`, DD-14.
In plain words: `internal` means "something happened that we did not foresee", and the error envelope requires every
failure we *can* name to be named where it is recognized. Malformed translated markup is foreseeable — a model
returns "Tom & Jerry" — and it is the caller's problem to act on, not a bug report. All seven surveyed FB2 books
return `internal` for it today, while the same input on an EPUB is accepted, because the two formats parse the
fragment with parsers of different strictness.

#### Scenario: A bare ampersand in target text is a validation failure

- **WHEN** an FB2 document is reassembled with a segment whose target text is `A & B`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`
- **AND** no exception propagates out of the port method

#### Scenario: A bare less-than sign in target text is a validation failure

- **WHEN** an FB2 document is reassembled with a segment whose target text is `x < y`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: An unrelated failure is still classified internal

- **WHEN** an FB2 document whose every segment carries well-formed target text is reassembled to a destination
  whose parent directory does not exist
- **THEN** the caller receives a failed result carrying `ErrorCode.internal`
- **AND** no exception propagates out of the port method

### Requirement: Verify the round trip against a local real-book corpus on demand

The system SHALL provide a verification that opens every book in a locally configured corpus directory, reassembles
each with zero segment edits, reassembles that zero-edit output a second time, reassembles the book again with
target text set on every segment, restores every segment from its own masked form, re-opens each output, and records
for each book whether its structure, segment identities and segment text survived unchanged.

The verification SHALL record, for each book, the total number of placeholders emitted and the largest number
emitted for any one segment.

The verification SHALL record a distinct failed outcome for a book that opens and writes without error but whose
round trip is not faithful, so that a run can distinguish "nothing threw" from "nothing changed".

The verification SHALL be excluded from the merge gate and from the standard test task, SHALL be skipped when no
corpus directory is configured, and SHALL record every book's outcome rather than stopping at the first failure.

Source: FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-04, DD-43, DD-49
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-49-code-and-technical-content-preservation`),
`04_Build_and_Release/06_TESTING_STRATEGY.md#live-local`, `04_Build_and_Release/02_QUALITY_GATES.md`.
In plain words: every defect the previous change fixed was found by running real books through the writer, and none
of them was visible to the hand-authored fixtures. The mask-then-restore probe is added for the same reason: a
zero-edit round trip never exercises the restore path at all, so without it masking would be proven only against
fixtures. The placeholder statistics are recorded because DD-49 warns that "naive masking would explode the
placeholder multiset", and a number measured across 213 real books is the only way to know whether it has.

#### Scenario: Every book is restored from its own masked form

- **WHEN** the corpus verification runs over a configured directory
- **THEN** each book's segments are restored from their own masked forms and compared against their source content

#### Scenario: Placeholder statistics are recorded per book

- **WHEN** the corpus verification runs
- **THEN** each book's recorded outcome carries its total placeholder count and its largest per-segment count

#### Scenario: A checkout with no corpus configured stays green

- **WHEN** the standard test task runs with no corpus directory configured
- **THEN** the verification is reported as skipped and the build succeeds

#### Scenario: The verification is not part of the merge gate

- **WHEN** the merge gate runs with a corpus directory configured
- **THEN** the verification does not run

#### Scenario: One failing book does not stop the run

- **WHEN** the verification runs over a corpus in which one book fails to open
- **THEN** that book's failure is recorded with its error code
- **AND** every other book in the corpus is still processed and recorded

#### Scenario: A book whose zero-edit write changes its structure is recorded as failed

- **WHEN** the verification runs over a corpus containing a book that opens and writes without error, but whose
  zero-edit output is not structurally equal to the source
- **THEN** that book's outcome is recorded as failed, distinctly from a book that round-tripped faithfully

#### Scenario: A configured but empty corpus directory is not a failure

- **WHEN** the verification runs with a corpus directory that exists and contains no book files
- **THEN** the run completes, records zero books processed, and the build succeeds

### Requirement: Replace a segment's protected spans with placeholder tokens

WHEN a book is parsed, the system SHALL produce, for every segment, a masked form of its content in which each
protected span is replaced by an opaque placeholder token, and an ordered map from each emitted token to the exact
source fragment it replaced.

Source: FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking`,
`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`, DD-19
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-19-paragraph-chunking`).
In plain words: the model is shown prose with numbered holes where the formatting was, and a map that says what each
hole held. Every segment shipped so far has carried its inline markup straight through to the model, which is why
nothing downstream can check that a translation kept the book's formatting.

#### Scenario: A paragraph's emphasis becomes two tokens

- **WHEN** an EPUB paragraph whose content is `He opened the <em>old</em> door.` is parsed
- **THEN** the segment's masked form is `He opened the ⟦g0⟧old⟦g1⟧ door.`

#### Scenario: The map holds the exact fragments that were replaced

- **WHEN** that same paragraph is parsed
- **THEN** its placeholder map is exactly `{g0: "<em>", g1: "</em>"}`

#### Scenario: A block with no protected span masks to itself

- **WHEN** an EPUB paragraph whose content is `Plain prose with no markup.` is parsed
- **THEN** the segment's masked form is `Plain prose with no markup.`

#### Scenario: A block with no protected span has an empty map

- **WHEN** that same paragraph is parsed
- **THEN** its placeholder map is empty

### Requirement: Spell a placeholder token as a bracketed g and digits

A placeholder token SHALL be spelled `⟦gN⟧` — U+27E6 MATHEMATICAL LEFT WHITE SQUARE BRACKET, the letter `g`, one or
more ASCII digits, U+27E7 MATHEMATICAL RIGHT WHITE SQUARE BRACKET. The system SHALL treat no other text as a token,
in a masked form or in a target.

The placeholder map SHALL key each entry by the token's **index form** without its brackets — `g0`, not `⟦g0⟧` —
so that one key corresponds to exactly one token in the masked form.

Source: FR-DOC-04, FR-DOC-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking` (the normative token grammar).
In plain words: the grammar is what the multiset gate counts and what restoring substitutes, so it has to be exact
in both directions. Requiring the closing bracket as part of the match is also what stops `⟦g1⟧` being mistaken for
the beginning of `⟦g12⟧` — the frozen spec asks for that with a "longest-first" caution, and a whole-grammar match
gets it by construction. The key form is stated because the two frozen documents that mention it disagree — the
document model writes the map as `⟦gN⟧ → fragment` while the database schema comments it as `gN -> fragment` — and
a persisted map cannot be read back under one spelling if it was written under the other.

#### Scenario: Text with a non-digit index is not a token

- **WHEN** an EPUB paragraph whose content is `See note ⟦gX⟧ below.` is parsed
- **THEN** the segment's masked form is `See note ⟦g0⟧gX⟦g1⟧ below.`

#### Scenario: A two-digit index is one token, not a one-digit token followed by text

- **WHEN** a segment whose masked form ends `…⟦g11⟧b⟦g12⟧` is given a target ending `…⟦g11⟧б⟦g12⟧`
- **THEN** the target is read as containing the tokens `⟦g11⟧` and `⟦g12⟧`, and not as containing `⟦g1⟧`

### Requirement: Number placeholder tokens densely from zero in first-appearance order

The system SHALL assign placeholder indices densely from `0`, in the order the protected spans first appear in the
segment.

Source: FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking` ("assigned densely from `0` in first-appearance order").
In plain words: zero-based is what the normative uniqueness clause states, and what the masking bullet above it
illustrates, even though the worked appendix in `01_Product/05_TRANSLATION_ALGORITHM.md` and the templates in
`01_Product/12_PROMPT_CATALOG.md` illustrate with `⟦g1⟧` as their first token — those are marked non-normative.
Dense numbering also makes a gate failure readable: a gap in the sequence means something was lost, with no
ambiguity about whether the index ever existed.

#### Scenario: Two emphasised words yield indices zero through three

- **WHEN** an EPUB paragraph whose content is `<b>A</b> and <i>B</i>` is parsed
- **THEN** the segment's masked form is `⟦g0⟧A⟦g1⟧ and ⟦g2⟧B⟦g3⟧`

#### Scenario: Order follows first appearance, not element type

- **WHEN** an EPUB paragraph whose content is `<i>x</i><b>y</b>.` is parsed
- **THEN** the segment's masked form is `⟦g0⟧x⟦g1⟧⟦g2⟧y⟦g3⟧.`

### Requirement: Wrap translatable inline content in a paired placeholder group

WHEN an element inside a segment's content has at least one child node **and is not a protected span**, the system
SHALL emit **two** tokens for it — one carrying its opening tag with every attribute and namespace declaration the
format's parser records for it, one carrying its closing tag — and SHALL mask its children between them, so any text
among them is still translated.

IF such elements nest, THEN the system SHALL emit a well-formed set of pairs whose nesting mirrors the source.

Source: FR-DOC-04, FR-DOC-EPUB-9 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`,
`01_Product/03_DOCUMENT_FORMATS.md#epub`), EC-INLINE-1
(`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`), DD-49
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-49-code-and-technical-content-preservation`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking` (paired-markup placeholder group), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: a link's target and an index anchor's id must survive untouched, but the words a reader sees between
`<a>` and `</a>` are ordinary prose and must still be translated. Swallowing the whole element into one token would
hide those words from the model; leaving the element visible would let the model rewrite the href. The
protected-span carve-out is what keeps this requirement from claiming an inline code span too — that span has
children as well, and the next requirement makes it atomic. "As the parser records it" rather than "as written" is
deliberate: both parsers normalize attribute quoting and the EPUB parser lower-cases attribute names, so a promise
of byte-identical attributes is one the system cannot keep. What it can keep is that the fragment restored is the
fragment captured, prefix and namespace declarations included.

#### Scenario: A link's target is protected while its visible text stays translatable

- **WHEN** an EPUB paragraph whose content is `See <a href="ch2.xhtml#top" id="x1">chapter two</a>.` is parsed
- **THEN** the segment's masked form is `See ⟦g0⟧chapter two⟦g1⟧.`

#### Scenario: The opening token carries the anchor's attributes

- **WHEN** that same paragraph is parsed
- **THEN** the placeholder map's `g0` entry is `<a href="ch2.xhtml#top" id="x1">`

#### Scenario: An FB2 note anchor's namespaced attribute is carried in its opening token

- **WHEN** an FB2 paragraph whose content is `Дивись <a l:href="#n1" type="note">1</a> тут.` is parsed, in a
  document declaring the prefix `l` on its root element
- **THEN** the placeholder map's `g0` entry carries both `l:href="#n1"` and `type="note"`

#### Scenario: A prefixed element keeps its prefix in both tokens

- **WHEN** an FB2 paragraph containing `<x:mark>слово</x:mark>` is parsed
- **THEN** the placeholder map's two entries are `<x:mark>` and `</x:mark>`

#### Scenario: Nested emphasis produces two well-formed pairs

- **WHEN** an EPUB paragraph whose content is `x <b>bold <i>and italic</i></b>` is parsed
- **THEN** the segment's masked form is `x ⟦g0⟧bold ⟦g1⟧and italic⟦g2⟧⟦g3⟧`

#### Scenario: An FB2 inline emphasis is paired the same way

- **WHEN** an FB2 paragraph whose content is `Він відчинив <emphasis>старі</emphasis> двері.` is parsed
- **THEN** the segment's masked form is `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.`

#### Scenario: An element enclosing only whitespace is still a pair

- **WHEN** an EPUB paragraph whose content is `a<span> </span>b` is parsed
- **THEN** the segment's masked form is `a⟦g0⟧ ⟦g1⟧b`

### Requirement: Replace a protected span with a single atomic placeholder

WHEN a node inside a segment's content is a **protected span**, the system SHALL emit exactly **one** token for it
whose mapped fragment is that node's complete serialized form, and SHALL NOT expose any part of its interior in the
masked form.

A protected span SHALL be any of: inline MathML or an inline vector-graphic element, protected in **every** markup
format the system parses because the vocabulary that gives those two names their meaning is foreign to both host
formats rather than owned by either; a preformatted element (`pre`, `listing`), protected in every markup format
for a different reason — those are the elements whose leading line feed an HTML parser discards, so masking one by
anything short of its whole serialized form loses a line on every export; an inline code span, scoped to XHTML
(EPUB) alone; a non-translatable block that a book has nested inside a translatable one; an element with
no child nodes; a comment; a processing instruction; a CDATA section; an entity reference; and a literal U+27E6 `⟦`
or U+27E7 `⟧` occurring in character data. Markdown is unaffected by the element names, naming no elements at all
and masking its backtick code span atomically under its own requirement below.

Source: FR-DOC-04, FR-DOC-EPUB-9, FR-DOC-MD-2 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`,
`01_Product/03_DOCUMENT_FORMATS.md#epub`, `#markdown`), DD-49
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-49-code-and-technical-content-preservation`), EC-CODE-3, EC-CODE-4
(`01_Product/03_DOCUMENT_FORMATS.md#code-edge-cases`), EC-IMG-1
(`01_Product/03_DOCUMENT_FORMATS.md#images-and-fonts`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking` (atomic placeholders), `#xml-round-trip-config`, ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: translating an identifier, a keyword or a mathematical expression corrupts the book, so nothing
inside an XHTML code span or inside a `<math>` in either format may reach the model at all — one token swallows
the whole span. (An FB2 `<code>` is the deliberate exception, and is treated below.) A vector graphic
is on the list because it is image content, which is never translated, and because left to the structural rule its
own `<text>` elements would become translatable prose. The named elements are protected for **two different reasons, not one**: `math` and `svg` because their vocabulary
is foreign to both host formats, and `pre`/`listing` because an HTML parser discards their leading line feed, so a
fragment that is anything less than the element's whole serialized form comes back a line short.
**Only `code` is scoped to one format**, because FictionBook
uses it for an ordinary prose style, not a code element — a paragraph an author wrote entirely in that style is
real text a reader reads, so an FB2 `<code>` is masked the same way any other inline element with children is: as a
paired group whose enclosed text stays translatable, not as an atomic span. An earlier wording of this requirement
scoped `code`, `math` and `svg` alike to XHTML on the reasoning that FictionBook declares neither `math` nor `svg`; measured, that
is a reason they are *foreign content that must stay atomic*, not a reason to walk into them — an FB2 book with an
inline `<m:math>` handed the mathematical identifier `alpha` to the model as prose, and one with an inline `<svg>`
handed over the figure's drawn caption. The system's own block-level segmentation already treated `math` as
format-agnostic, so the narrower scoping also contradicted it: a block-level FB2 `<math>` was protected while an
inline one was not. The same single-token shape covers
everything with no interior to translate: a line break, an image, a comment, a processing instruction, a CDATA
section, an entity reference. CDATA is on the list for a reason that is easy to get wrong — a CDATA section is a
kind of text node in the XML parser this project uses, so an implementation that asks "is this text?" will decode
it, re-escape it on the way back, and quietly destroy the CDATA form a shipped requirement guarantees.

#### Scenario: An inline code span is one token

- **WHEN** an EPUB paragraph whose content is `Call <code>List.of()</code> first.` is parsed
- **THEN** the segment's masked form is `Call ⟦g0⟧ first.`

#### Scenario: The code span's text is carried in the map, not in the masked form

- **WHEN** that same paragraph is parsed
- **THEN** the placeholder map's `g0` entry is `<code>List.of()</code>`

#### Scenario: An FB2 inline code span in prose is paired, not atomic

- **WHEN** an FB2 paragraph whose content is `Виклич <code>List.of()</code> спочатку.` is parsed
- **THEN** the segment's masked form is `Виклич ⟦g0⟧List.of()⟦g1⟧ спочатку.`

#### Scenario: Inline MathML is one token

- **WHEN** an EPUB paragraph whose content is `Let <math><mi>x</mi></math> be positive.` is parsed
- **THEN** the segment's masked form is `Let ⟦g0⟧ be positive.`

#### Scenario: An inline vector graphic's own text is never exposed

- **WHEN** an EPUB paragraph whose content is `See <svg><text>Fig 1</text></svg> above.` is parsed
- **THEN** the segment's masked form is `See ⟦g0⟧ above.`

#### Scenario: FB2 inline MathML is one token, like EPUB's

- **WHEN** an FB2 paragraph whose content is `Формула <m:math><m:mi>alpha</m:mi></m:math> тут.` is parsed
- **THEN** the segment's masked form is `Формула ⟦g0⟧ тут.`, and `alpha` appears nowhere in it

#### Scenario: An FB2 inline vector graphic's caption is never exposed

- **WHEN** an FB2 paragraph whose content is `Малюнок <svg><text>Fig 1</text></svg> тут.` is parsed
- **THEN** the segment's masked form is `Малюнок ⟦g0⟧ тут.`, and `Fig 1` appears nowhere in it

#### Scenario: A preformatted element nested inside a translatable block is one token

- **WHEN** an EPUB `<div>` whose content is `See <code><pre>` + two line feeds + `x</pre></code> here.` is parsed
  and restored unchanged
- **THEN** the written element still carries both line feeds

#### Scenario: An inline code span inside a heading is masked

- **WHEN** an EPUB heading whose content is `Using <code>Optional</code> well` is parsed
- **THEN** a segment is produced whose masked form is `Using ⟦g0⟧ well`

#### Scenario: That heading's segment is still a heading

- **WHEN** that same heading is parsed
- **THEN** the segment's kind is `HEADING`

#### Scenario: An image is one token

- **WHEN** an EPUB paragraph whose content is `Before <img src="fig1.png" alt="Figure 1"/> after` is parsed
- **THEN** the segment's masked form is `Before ⟦g0⟧ after`

#### Scenario: A line break nested inside inline markup is one token

- **WHEN** an EPUB paragraph whose content is `x<em>one<br/>two</em>y` is parsed
- **THEN** the segment's masked form is `x⟦g0⟧one⟦g1⟧two⟦g2⟧y`

#### Scenario: An XML comment inside a paragraph is one token

- **WHEN** an EPUB paragraph whose content is `Text <!-- editor note --> more text` is parsed
- **THEN** the placeholder map's `g0` entry is `<!-- editor note -->`

#### Scenario: An FB2 CDATA section is one token

- **WHEN** an FB2 paragraph whose content is `Порівняй <![CDATA[a < b]]> тут` is parsed
- **THEN** the placeholder map's `g0` entry is `<![CDATA[a < b]]>`

#### Scenario: A CDATA section survives a mask-then-restore cycle as CDATA

- **WHEN** that same paragraph is masked and its masked form is supplied back as its own target
- **THEN** the restored content contains a CDATA section, not the escaped text `a &lt; b`

#### Scenario: An FB2 entity reference is one token

- **WHEN** an FB2 paragraph whose content is `Розділ&nbsp;1` is parsed, in a document declaring the entity `nbsp`
- **THEN** the placeholder map's `g0` entry is `&nbsp;`

#### Scenario: The characters the parser reports beside a reference stay character data

- **WHEN** that same paragraph is parsed
- **THEN** the segment's masked form is `Розділ⟦g0⟧` followed by whatever character data the parser reports after
  the reference, and only the reference itself is a token

### Requirement: Mask a non-translatable block that a book has nested inside a translatable one

WHEN a `<pre>` listing or a block-level MathML element occurs among the children of a block that owns translatable
text, the system SHALL mask it as a protected span, and the restored content SHALL be identical to the source
content it replaced.

Source: FR-DOC-04, FR-DOC-EPUB-9 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`,
`01_Product/03_DOCUMENT_FORMATS.md#epub`), DD-49
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-49-code-and-technical-content-preservation`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: "a code listing is never a segment, it lives in the skeleton alone" is written for a listing that
stands on its own, and it holds there. A listing that a book has nested inside a text-owning block has no such
option — that block *is* a segment, so the listing either becomes a placeholder or reaches the model as raw `<pre>`
markup inside the prose. The nesting is only reachable in a non-paragraph block, because an HTML parser closes an
open paragraph at a `<pre>` start tag; a `<div>`, `<li>`, `<td>` or `<blockquote>` keeps it as a child, and a
block-level `<math>` survives inside a paragraph. The identity clause is not decoration: an HTML parser discards one
line feed after a `<pre>` start tag, so a listing captured and re-parsed loses a line of the book on every cycle
unless the capture puts it back.

#### Scenario: A listing nested in a text-owning div is masked rather than left bare

- **WHEN** an EPUB block whose content is `<div>Note: <pre>code();</pre> ends it.</div>` is parsed
- **THEN** the segment's masked form is `Note: ⟦g0⟧ ends it.`

#### Scenario: The listing's text is carried in the map

- **WHEN** that same block is parsed
- **THEN** the placeholder map's `g0` entry is `<pre>code();</pre>`

#### Scenario: A block-level MathML element nested in a paragraph is masked

- **WHEN** an EPUB paragraph whose content is `Given <math display="block"><mi>x</mi></math> we conclude.` is parsed
- **THEN** the segment's masked form is `Given ⟦g0⟧ we conclude.`

#### Scenario: A nested listing's leading line breaks survive a mask-then-restore cycle

- **WHEN** an EPUB block whose content is `<div>Note: <pre>` followed by two line feeds, then `code();</pre> ends
  it.</div>` is masked and its masked form is supplied back as its own target
- **THEN** the restored content's listing still begins with two line feeds

### Requirement: Mask every node that is not character data, whatever it is called

The system SHALL mask a node inside a segment's content on the strength of what it is, not of what it is named, so
that an element whose name appears in no format specification is masked identically to a known one — except for the
protected spans that are named individually, because "never translate the interior" is a fact about those elements
that no structural signal carries.

Source: FR-DOC-04, FR-DOC-EPUB-5 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`,
`01_Product/03_DOCUMENT_FORMATS.md#epub`), ADR-0027
(`docs/adr/ADR-0027-structural-block-segmentation.md`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking`.
In plain words: the frozen spec lists `<em>`, `<a href>`, `<sup>` and `<span>` as examples, not as a closed set, and
a whitelist of block tags already failed this project once — it reached 73.74% of a 194-book corpus and left 42
books importing essentially empty, because real publishing toolchains invent their own element names. The same
toolchains invent inline names, and an unmasked unknown element is worse than an unsegmented one: it reaches the
model as raw markup and comes back mangled.

#### Scenario: An element name that appears in no specification is still masked

- **WHEN** an EPUB paragraph whose content is `a <calibre-inline class="x">b</calibre-inline> c` is parsed
- **THEN** the segment's masked form is `a ⟦g0⟧b⟦g1⟧ c`

#### Scenario: An FB2 element name unknown to the FictionBook schema is still masked

- **WHEN** an FB2 paragraph whose content is `до <custom-run>тексту</custom-run> тут` is parsed
- **THEN** the segment's masked form is `до ⟦g0⟧тексту⟦g1⟧ тут`

### Requirement: Leave prose text unmasked

The system SHALL leave every piece of character data in a segment that is not itself a protected span present and
translatable in the masked form, including the text that follows an inline element's closing tag, and including
numerals occurring in running prose.

Source: FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking` (which both lists "inline descendant elements **and their
tails**" among what is replaced and states that "Prose numerals are NOT masked"),
`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`, ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: masking hides formatting, not words. The frozen phrase "and their tails" reads, taken alone, as
though the text after `</em>` were masked too — but a reading that masked tails would leave the model almost nothing
to translate, which cannot be what a masking scheme is for, and the worked appendix reads the same way. ADR-0031
records the deviation. A numeral in running prose stays visible for a different reason: the target language has to
be able to inflect and localize it. The protected-span exclusion is what reconciles this with the literal-bracket
rule below, which masks a character that is also character data.

#### Scenario: The text after a closing tag is still translatable

- **WHEN** an EPUB paragraph whose content is `He opened the <em>old</em> door at seven.` is parsed
- **THEN** the segment's masked form contains the literal text ` door at seven.`

#### Scenario: A numeral in running prose is not masked

- **WHEN** an EPUB paragraph whose content is `It was built in 1893.` is parsed
- **THEN** the segment's masked form is `It was built in 1893.`

#### Scenario: A paragraph of prose alone carries no placeholders

- **WHEN** that same paragraph is parsed
- **THEN** its placeholder map is empty

#### Scenario: A plain-text paragraph masks to itself

- **WHEN** a TXT file's paragraph `Це звичайний абзац без розмітки.` is parsed
- **THEN** the segment's masked form is `Це звичайний абзац без розмітки.`

#### Scenario: A plain-text paragraph carries no placeholders

- **WHEN** that same paragraph is parsed
- **THEN** its placeholder map is empty

### Requirement: Protect a placeholder bracket that occurs in the source text

IF a segment's character data contains U+27E6 `⟦` or U+27E7 `⟧`, THEN the system SHALL replace each such character
with its own placeholder token, and restoring SHALL return the original character exactly.

Source: FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-INLINE-5, EC-CODE-2
(`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`, `#code-edge-cases`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking` (escape rule), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: a book that happens to print a mathematical white square bracket must not have that bracket read as
part of a placeholder. The frozen rule says such a bracket is "escaped before masking and un-escaped on unmask" but
never says with what, and names only the opening bracket. Treating both brackets as ordinary protected spans meets
the obligation exactly — the model never sees a bare bracket, so no token can be forged from one — and it gets the
restoration covered by the placeholder-multiset gate for free, which a private escape sequence would not.

#### Scenario: A literal bracket pair in prose becomes two tokens

- **WHEN** an EPUB paragraph whose content is `He wrote ⟦x⟧ on the board.` is parsed
- **THEN** the segment's masked form is `He wrote ⟦g0⟧x⟦g1⟧ on the board.`

#### Scenario: Each bracket is mapped to itself

- **WHEN** that same paragraph is parsed
- **THEN** the placeholder map is exactly `{g0: "⟦", g1: "⟧"}`

#### Scenario: A lone opening bracket is protected on its own

- **WHEN** an EPUB paragraph whose content is `The symbol ⟦ is rare.` is parsed
- **THEN** the segment's masked form is `The symbol ⟦g0⟧ is rare.`

#### Scenario: A lone closing bracket is protected on its own

- **WHEN** an EPUB paragraph whose content is `The symbol ⟧ is rare.` is parsed
- **THEN** the segment's masked form is `The symbol ⟦g0⟧ is rare.`

#### Scenario: Brackets inside an inline code span need no separate protection

- **WHEN** an EPUB paragraph whose content is `Type <code>⟦g0⟧</code> exactly.` is parsed
- **THEN** the segment's masked form is `Type ⟦g0⟧ exactly.`

#### Scenario: The code span's brackets stay inside its fragment

- **WHEN** that same paragraph is parsed
- **THEN** the placeholder map's `g0` entry is `<code>⟦g0⟧</code>`

#### Scenario: A bracket inside an attribute value needs no protection

- **WHEN** an EPUB paragraph whose content is `a <span title="⟦x⟧">b</span> c` is parsed
- **THEN** the segment's masked form is `a ⟦g0⟧b⟦g1⟧ c`

#### Scenario: A bracketed sequence in prose survives the round trip

- **WHEN** an EPUB paragraph whose content is `He wrote ⟦x⟧ on the board.` is masked and its masked form is supplied
  back as its own target
- **THEN** the restored content is `He wrote ⟦x⟧ on the board.`

### Requirement: Present a markup-shaped segment's masked form as character data

IF a segment's format expresses inline structure as markup — EPUB or FB2 — THEN the masked form SHALL contain the
segment's character data in its decoded form, with no markup and no entity or character-reference syntax.

Source: FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#data-model` ("the model sees clean block-level prose interleaved with
placeholders"), `#inline-masking`, DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: if the model is handed `Smith &amp; Sons`, it is being asked to translate escaped syntax and graded
on reproducing it. Decoding first means it reads `Smith & Sons` and answers in prose — which is also what makes the
restored fragment safe, because everything the model returns can then be treated as text rather than parsed as
markup.

#### Scenario: An escaped ampersand reaches the model as a plain ampersand

- **WHEN** an EPUB paragraph whose content is `Smith &amp; Sons` is parsed
- **THEN** the segment's masked form is `Smith & Sons`

#### Scenario: A numeric character reference reaches the model as its character

- **WHEN** an EPUB paragraph whose content is `1880&#8212;1893` is parsed
- **THEN** the segment's masked form is `1880—1893`

#### Scenario: An FB2 escaped less-than sign reaches the model as a plain less-than sign

- **WHEN** an FB2 paragraph whose content is `Якщо x &lt; y, то` is parsed
- **THEN** the segment's masked form is `Якщо x < y, то`

### Requirement: Present a buffer-shaped segment's masked form as its own source text

IF a segment's format reassembles by splicing into the original byte buffer — Markdown or TXT — THEN the masked form
SHALL carry the segment's source text exactly as the file spells it, with no decoding of backslash escapes and no
expansion of character references.

WHERE the segment is a Markdown frontmatter value, its source text SHALL be the value without its surrounding quotes
and without a trailing comment, with `\"` inside double quotes and `''` inside single quotes read as the quote
character. WHERE the segment is a Markdown image's alternative text, its source text SHALL be the text between the
image's brackets with each backslash escape read as the character it escapes.

**Source**: FR-DOC-MD-1 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-TXT-3 (`#txt`), FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), ADR-0031 (`docs/adr/ADR-0031-masked-text-is-character-data.md`). In plain words: these two formats reassemble by splicing bytes back into the file they came from, so any re-spelling of the text is a change to the document. `\*` in Markdown source means a literal asterisk and `AT&amp;T` means `AT&T`, but writing either decoded form back into the buffer would change what the next parse sees. The contrast with EPUB and FB2 is deliberate: there the segment is re-parsed into a tree, here it is copied into a file. The two auxiliary slots are the exception because each is written back through quoting or escaping of its own — YAML quotes for a frontmatter value, bracket escapes for an image description — and a quote or backslash left in the text would be escaped a second time on the way back.

#### Scenario: A Markdown backslash escape is left as written

- **WHEN** a Markdown paragraph whose source text is `A \* B and C` is parsed
- **THEN** the segment's masked form is `A \* B and C`

#### Scenario: A Markdown character reference is left as written

- **WHEN** a Markdown paragraph whose source text is `AT&amp;T and more` is parsed
- **THEN** the segment's masked form is `AT&amp;T and more`

#### Scenario: A quoted frontmatter value is read inside its quotes

- **WHEN** a Markdown file whose frontmatter holds `title: "The \"Old\" House"` is parsed
- **THEN** the frontmatter segment's masked form is `The "Old" House`

#### Scenario: An escaped bracket in an image description is read as a bracket

- **WHEN** a Markdown paragraph holding the image `![Figure \[1\]](fig1.png)` is parsed
- **THEN** the image's alternative-text segment's masked form is `Figure [1]`

### Requirement: Assert the placeholder invariant at mask time

WHEN a segment is masked, the system SHALL assert that every emitted token is unique within that segment and that
the placeholder map is a bijection over exactly the tokens present in the masked form, considering only the masked
form and not the contents of the mapped fragments.

Source: FR-DOC-04, FR-DOC-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking` (uniqueness validation at mask time),
`02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`.
In plain words: the multiset gate can only mean something if the source side of the comparison is trustworthy. The
frozen rule calls a violation "a parser-side invariant failure, not a model error", so if one ever escapes it is
reported as an internal failure of the importer and never as a translation problem. The clause about mapped
fragments is what keeps the check honest in the one case where it would otherwise cry wolf: a code span whose own
text is the literal `⟦g0⟧` is legitimate, and a check that scanned fragments as well as the masked form would reject
that book.

#### Scenario: A masked segment's map covers exactly its tokens

- **WHEN** an EPUB paragraph whose content is `<b>A</b> and <code>x</code>` is parsed
- **THEN** the placeholder map has exactly one key per token in the masked form, three of each, and every token
  occurs exactly once

#### Scenario: A fragment containing a token spelling does not break the invariant

- **WHEN** an EPUB paragraph whose content is `Type <code>⟦g0⟧</code> exactly.` is parsed
- **THEN** the segment's masked form contains exactly one token

### Requirement: Leave the source content and its hash untouched by masking

The system SHALL keep each segment's source content exactly as parsed, and SHALL keep its content hash equal to the
SHA-256 of that source content after Unicode NFC normalization.

Source: FR-DOC-04, FR-IMPORT-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-import`), FR-ALGO-A3
(`01_Product/05_TRANSLATION_ALGORITHM.md`), `02_Architecture/03_DOCUMENT_MODEL.md#data-model` (`sourceHash` is
"SHA-256 over the exact `sourceInner` (pre-mask, NFC-normalized)"), `02_Architecture/06_DATA_MODEL_SQLITE.md#tables`.
In plain words: the segment hash keys translation memory, resume, and per-segment change detection. If masking moved
it, every stored translation in every project would miss on the next run, and a book already half-translated would
restart. Masking adds a second view of the segment; it does not redefine the first.

#### Scenario: The source content still carries its inline markup after masking

- **WHEN** an EPUB paragraph whose content is `He opened the <em>old</em> door.` is parsed
- **THEN** the segment's source content is still `He opened the <em>old</em> door.`

#### Scenario: The hash is taken over the source content, not the masked form

- **WHEN** that same paragraph is parsed
- **THEN** the segment's content hash equals the SHA-256 of the NFC-normalized text
  `He opened the <em>old</em> door.`

### Requirement: Compare the placeholder multiset as a hard gate before restoring anything

WHEN the restore operation is invoked for a masked segment, the system SHALL first compare the multiset of `⟦gN⟧`
tokens in the supplied target against the multiset of tokens in the segment's masked form, and SHALL then check,
against the pairs and the line-break tokens recorded when the segment was masked, that:

- every **paired** token — an opening token and its closing partner — appears opening-before-closing and properly
  nested with every other pair;
- every pair whose content in the masked form holds text — a character other than whitespace that is not part of a
  token — still holds such a character between its two tokens in the target;
- every line-break token has the same innermost enclosing pair in the target as in the masked form, or no enclosing
  pair in both;
- when the masked form holds text outside every pair, the target does too;
- no `⟦` or `⟧` glyph stands outside a whole `⟦gN⟧` token — a token split by whitespace (`⟦g1 ⟧`), misspelt
  (`⟦G1⟧`), or a lone bracket left over from a moved token is checked first and fails as a stray bracket;
- a pair that wraps at least 12 visible characters of a masked form which also holds text outside it wraps, in the
  target, a share of the visible characters no less than a third and no more than three times its share in the masked
  form.

IF the target holds a stray bracket glyph, or IF the two multisets differ in any way — a token missing, a token added that the source did not contain, or a token
occurring a different number of times — or IF a pair's closing token precedes its opening token, two pairs overlap
without one enclosing the other, a pair that held text holds none, a line-break token has changed its innermost
enclosing pair, every word of the target sits inside a pair although the masked form left text outside them, or a
long pair's share of the text shrank or grew more than threefold, THEN the system SHALL return a failed result carrying `ErrorCode.validation`, SHALL NOT restore any
placeholder, SHALL NOT alter the target text, and SHALL NOT attempt a repair or a reconciliation of any kind.

Apart from those rules, the position of an **atomic** token — an image, a code span, a line break — SHALL NOT affect
the comparison, and a whole pair SHALL be free to move within the target as long as it stays properly formed.

**Source**: FR-DOC-05, FR-QA-01, FR-QA-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), EC-INLINE-2 (`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`), `02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `02_Architecture/05_PIPELINE_ENGINE.md#qa-checks` (tag integrity is a hard gate, pre-QA), ADR-0031 (`docs/adr/ADR-0031-masked-text-is-character-data.md`), ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`), `docs/next_features.md` §9. In plain words: the model may rewrite every word and may move the formatting around the sentence, because word order differs between languages — but it may not lose, invent or duplicate a piece of formatting, and it may not turn a piece of formatting inside out. A swapped pair — `⟦g1⟧OLD⟦g0⟧` for `⟦g0⟧old⟦g1⟧` — passed the old count-only check and produced a closing tag before its opening one; EPUB silently "repaired" it, FB2 refused it at write time, and the export failed at the very end with no segment named. Checking order here flags that one segment instead. Two more shapes passed the count and the order and still damaged the book: a pair emptied of its words — `⟦g0⟧⟦g1⟧OLD` for `⟦g0⟧old⟦g1⟧` — keeps the tokens and loses the emphasis on the word, so a pair that held text must still hold text; and a line break moved across a pair's edge changes where the written paragraph splits into runs, so the re-opened book has a different number of segments and the whole export fails at its final check. A pair stretched over every word — `⟦g0⟧«Понад усе», — сказав він.⟦g1⟧` for the drop cap `⟦g0⟧“A⟦g1⟧bove all,” he said.` — kept count, order and text, yet a tree reader makes the outermost element owning text of its own the segment's block, so the written paragraph re-opened as a segment of that `<span>` with no markup left, and its drop-cap style covered the whole paragraph; a target must therefore keep text outside its pairs when the source did. An image may still move freely, and a line break may move within its own pair, because that is an ordinary word-order change. This is the one check no confidence score and no judge verdict can outvote, and it runs before any of them. It reports; it never fixes. A small model on the fixture book returned `⟦g0⟧Інститут ⟦g1⟧Мерідіанського Зондування⟧ відправив…` for `The ⟦g0⟧Meridian Survey Institute⟦g1⟧ sent…`: count, order, text in the pair and text outside all held, so the reply was accepted, the stray `⟧` reached the written book as text and the bold moved onto one word; only the export's re-open caught it. Any bracket glyph outside a whole token is therefore refused, and a pair that wrapped a phrase must still wrap a comparable share of the sentence. The deterministic repair a caller may ask for afterwards first rejoins a split or misspelt token and drops the remaining stray glyphs, then places tokens, and its result passes this gate.

#### Scenario: A stray bracket glyph fails the gate

- **WHEN** a segment whose masked form is `The ⟦g0⟧Meridian Survey Institute⟦g1⟧ sent its first gravity team…` is
  given the target `⟦g0⟧Інститут ⟦g1⟧Мерідіанського Зондування⟧ відправив свою першу гравітаційну команду…`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation` naming a broken placeholder, and nothing
  is restored

#### Scenario: A split or misspelt token fails the gate

- **WHEN** the target spells a token `⟦g1 ⟧` or `⟦G1⟧`, or holds a lone `⟦`
- **THEN** the gate fails as a stray bracket before the multiset is compared

#### Scenario: A long pair moved onto one word fails the gate

- **WHEN** the same masked form is given `⟦g0⟧Інститут ⟦g1⟧Мерідіанського Зондування відправив…` (the bold now wraps
  one word of the sentence instead of the three-word name)
- **THEN** the gate fails; a drop cap `⟦g0⟧G⟦g1⟧ravity…` translated as `⟦g0⟧Г⟦g1⟧равітація…` still passes, because a
  pair under 12 visible characters may change its share freely

#### Scenario: A dropped token fails the gate

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧ door` is given the target `⟦g0⟧старі двері`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A failed gate restores nothing

- **WHEN** that same target is supplied
- **THEN** no restored content is returned

#### Scenario: Reordered tokens pass the gate

- **WHEN** a segment whose masked form is `See ⟦g0⟧ and ⟦g1⟧.`, where `⟦g0⟧` and `⟦g1⟧` are two images, is given the
  target `Дивіться ⟦g1⟧ і ⟦g0⟧.`
- **THEN** the comparison passes and restoring proceeds

#### Scenario: A swapped pair fails the gate

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧`, where `⟦g0⟧` opens and `⟦g1⟧` closes one emphasis, is given
  the target `⟦g1⟧OLD⟦g0⟧`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: Whole pairs moved past each other pass the gate

- **WHEN** a segment whose masked form is `⟦g0⟧A⟦g1⟧ and ⟦g2⟧B⟦g3⟧` is given the target `⟦g2⟧Б⟦g3⟧ і ⟦g0⟧А⟦g1⟧`
- **THEN** the comparison passes and restoring proceeds

#### Scenario: Overlapping pairs fail the gate

- **WHEN** a segment whose masked form is `⟦g0⟧a ⟦g1⟧b⟦g2⟧ c⟦g3⟧`, where `⟦g0⟧`…`⟦g3⟧` enclose `⟦g1⟧`…`⟦g2⟧`, is given
  the target `⟦g0⟧а ⟦g1⟧б⟦g3⟧ в⟦g2⟧`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: An atomic token may move inside a pair

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧ door ⟦g2⟧`, where `⟦g2⟧` is an image, is given the target
  `⟦g0⟧старі ⟦g2⟧⟦g1⟧ двері`
- **THEN** the comparison passes and restoring proceeds

#### Scenario: A pair emptied of its text fails the gate

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧`, where `⟦g0⟧` opens and `⟦g1⟧` closes one emphasis, is given
  the target `⟦g0⟧⟦g1⟧OLD`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A pair that held only whitespace may be left empty

- **WHEN** a segment whose masked form is `a⟦g0⟧ ⟦g1⟧b`, where `⟦g0⟧` and `⟦g1⟧` are one pair, is given the target
  `а⟦g0⟧⟦g1⟧б`
- **THEN** the comparison passes and restoring proceeds

#### Scenario: A line break moved out of its pair fails the gate

- **WHEN** a segment whose masked form is `x⟦g0⟧one⟦g1⟧two⟦g2⟧y`, where `⟦g0⟧` and `⟦g2⟧` are one emphasis and `⟦g1⟧` is
  a line break, is given the target `x⟦g0⟧один два⟦g2⟧⟦g1⟧y`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A line break moved within its pair passes the gate

- **WHEN** that same segment is given the target `x⟦g0⟧один два⟦g1⟧⟦g2⟧y`
- **THEN** the comparison passes and restoring proceeds

#### Scenario: A pair stretched over the whole target fails the gate

- **WHEN** a segment whose masked form is `⟦g0⟧“A⟦g1⟧bove all,” he said.`, where `⟦g0⟧` and `⟦g1⟧` are one drop-cap
  span, is given the target `⟦g0⟧«Понад усе», — сказав він.⟦g1⟧`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A pair that wrapped the whole source may wrap the whole target

- **WHEN** a segment whose masked form is `⟦g0⟧Whole line.⟦g1⟧` is given the target `⟦g0⟧Увесь рядок.⟦g1⟧`
- **THEN** the comparison passes and restoring proceeds

#### Scenario: A duplicated token fails the gate

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧` is given the target `⟦g0⟧старі⟦g1⟧⟦g1⟧`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: An invented token fails the gate

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧` is given the target `⟦g0⟧старі⟦g1⟧ ⟦g7⟧`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A two-digit token replaced by its one-digit prefix fails the gate

- **WHEN** a segment whose masked form is `⟦g0⟧a⟦g1⟧b⟦g2⟧c⟦g3⟧d⟦g4⟧e⟦g5⟧f⟦g6⟧g⟦g7⟧h⟦g8⟧i⟦g9⟧j⟦g10⟧k⟦g11⟧l⟦g12⟧` is
  given a target identical except that its final `⟦g12⟧` is written `⟦g1⟧`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A segment with no placeholders accepts a target with none

- **WHEN** a segment whose masked form is `Plain prose.` is given the target `Звичайна проза.`
- **THEN** the restored content is `Звичайна проза.`

#### Scenario: A segment with no placeholders rejects a target that invents one

- **WHEN** a segment whose masked form is `Plain prose.` is given the target `Звичайна ⟦g0⟧ проза.`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: An empty target for a segment that had placeholders fails the gate

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧` is given a target that is the empty string
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

### Requirement: Report the expected placeholders in full and the observed ones within a bound

WHEN the placeholder-multiset comparison fails, the system SHALL name, on the returned failure, every token of the
expected multiset without truncating it, and SHALL render both multisets in a form a caller can read back token by
token.

WHERE the multiset was derived from the model's response rather than from the system's own masking, the system
SHALL bound what it renders by a maximum token count and a maximum per-token length, and SHALL state the number of
tokens omitted rather than dropping them silently.

The failure SHALL NOT include the segment's source text, the target text, or any file path.

Source: FR-DOC-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop` (a directed fix injects the expected multiset, "restore exactly:
…"), `02_Architecture/09_ERROR_HANDLING.md#safe-details-allowlist`,
`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#offline-invariant`.
In plain words: the repair tier that comes later has to tell the model exactly which tokens to put back, and it can
only do that if the failure carries all of them — a truncated list produces a repair prompt that asks for part of
the formatting and silently drops the rest, which is worse than asking for none. **That argument holds for the
expected side and not for the observed one**, and the asymmetry is the point: the expected multiset is the system's
own masking output, so its size is bounded by the segment; the observed multiset is scanned out of whatever the
model returned, and the token grammar's index is an unbounded run of digits. Measured, one degenerate token
rendered a 200 KB error detail into a dialog and a 200 KB line into the log file. A repair tier cannot ask the
model to restore tokens the model invented anyway, so bounding that side costs nothing and is stated rather than
silent, so a reader can tell a bounded report from a complete one. The envelope carries one string, so
"readable back token by token" is the honest obligation: the tokens are rendered in their own field, separated, and
in the same grammar the gate counts, so recovering the list is a scan and not an interpretation. Book text must not
travel with the failure: an error detail is rendered in the UI and written
to a log file, and a paragraph of somebody's book has no business in either.

#### Scenario: A gate failure names the missing token

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧` is given the target `⟦g0⟧старі`
- **THEN** the failure names `⟦g0⟧` and `⟦g1⟧` as expected, and `⟦g0⟧` as observed

#### Scenario: An overlong observed token is capped

- **WHEN** the model's response carries a single token whose digit run makes it 200,000 characters long
- **THEN** the failure renders that token cut to its first 16 characters, and the rendered detail stays under a
  kilobyte

#### Scenario: More observed tokens than the cap states the overflow

- **WHEN** the model's response carries 50,000 distinct placeholder tokens
- **THEN** the failure renders the first 64 of them and states `+49936 more`

#### Scenario: Bounding the observed side leaves the expected side complete

- **WHEN** a forty-placeholder segment's comparison fails against a response carrying 50,000 tokens
- **THEN** all forty expected tokens are still rendered untruncated

#### Scenario: A forty-placeholder segment's report is not truncated

- **WHEN** a segment whose masked form carries the forty tokens `⟦g0⟧` through `⟦g39⟧` is given a target carrying
  only `⟦g0⟧`
- **THEN** the failure names all forty expected tokens, including `⟦g39⟧`

#### Scenario: A gate failure carries no book text

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧ door` is given the target `старі двері`
- **THEN** the failure's detail contains neither the word `old` nor the word `двері`

### Requirement: Restore a translated segment by substituting each placeholder back

WHEN the placeholder-multiset comparison passes, the system SHALL replace every `⟦gN⟧` token in the target with the
exact source fragment recorded for it, in a single pass, and SHALL leave every other character of the target as the
model wrote it, subject to the composition rules below.

The system SHALL NOT read a placeholder token that appears inside a restored fragment as a token to be substituted
again.

Source: FR-DOC-04, FR-DOC-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate` (step 1), `#reassembly`, ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`).
In plain words: restoring is a substitution, not a re-derivation — the fragment that comes back is byte-for-byte the
fragment that went in, which is the only way an anchor's id, a link's href, or a font-dependent `<span>`'s class
survives translation. The single-pass clause matters because a code span may legitimately contain the literal text
`⟦g0⟧`; substituting into the output of a substitution would expand it a second time and destroy the book's own
example.

#### Scenario: A translated paragraph restores its emphasis around the translated words

- **WHEN** a segment whose masked form is `He opened the ⟦g0⟧old⟦g1⟧ door.` is given the target
  `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.`
- **THEN** the restored content is `Він відчинив <em>старі</em> двері.`

#### Scenario: An atomic code span comes back byte-for-byte

- **WHEN** a segment whose masked form is `Call ⟦g0⟧ first.` with `g0` mapped to `<code>List.of()</code>` is given
  the target `Спочатку викличте ⟦g0⟧.`
- **THEN** the restored content is `Спочатку викличте <code>List.of()</code>.`

#### Scenario: A moved token restores at its new position

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧ door` is given the target `двері ⟦g0⟧старі⟦g1⟧`
- **THEN** the restored content is `двері <em>старі</em>`

#### Scenario: A token spelled inside a restored fragment is not substituted again

- **WHEN** a segment whose masked form is `Type ⟦g0⟧ exactly.` with `g0` mapped to `<code>⟦g0⟧</code>` is given the
  target `Введіть ⟦g0⟧ точно.`
- **THEN** the restored content is `Введіть <code>⟦g0⟧</code> точно.`

### Requirement: Compose a markup-shaped restored fragment so it is well-formed by construction

IF a segment's format expresses inline structure as markup — EPUB or FB2 — and the segment is not an image's
alternative text, THEN the system SHALL treat every part of the target that is not a placeholder token as character
data and escape it accordingly when composing the restored content, and SHALL insert the mapped fragments as markup.

IF the target contains a character that is significant in that format's markup — a bare `<`, `&` or `>` — THEN the
restored content SHALL carry that character as escaped character data, and the operation SHALL NOT fail.

IF the target contains a character that no XML 1.0 document can carry at all — a C0 control other than tab, line
feed or carriage return; an unpaired surrogate; U+FFFE or U+FFFF — THEN the system SHALL fail **that segment** with
`ErrorCode.validation`, naming no book text on the failure, and SHALL leave every other segment of the book
restorable and the book exportable. A C1 control, which XML 1.0 permits, SHALL NOT be refused.

This escaping SHALL apply only when a target is restored, and SHALL NOT apply to target text supplied directly for
reassembly.

**Source**: FR-DOC-04, FR-DOC-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `#xml-round-trip-config`, ADR-0031 (`docs/adr/ADR-0031-masked-text-is-character-data.md`), ADR-0025 (`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`). In plain words: after masking, every piece of markup in a translated segment came from a fragment the parser itself recorded — so nothing the model wrote needs to be markup, and nothing it wrote should be parsed as markup. A model that writes "x < y" or "Tom & Jerry" is writing ordinary prose, and the book should say so rather than refusing the write. The last clause draws the boundary: reassembly still accepts inline markup written straight into a target, because that is how a reviewer's manual edit and the corpus harness both work, and escaping there would turn a restored `<em>` into the literal text `&lt;em&gt;` — the exact defect ADR-0025 exists to remove.

The refusal clause is the one case where "treat it as character data" has nothing to treat: a character outside
XML's own `Char` production is not character data in that format at any spelling, escaped or not. Leaving it
unchecked was measured to fail in two different wrong ways at once — FB2 threw when the restored fragment was
parsed at write-back, which aborts before any bytes are written and so made **one** bad segment cost the export of
the **whole** book, naming no segment; EPUB accepted the same input and deleted the character silently at
serialization, so the book shipped with data missing and no error at all. Refusing the one segment at restore time
makes the two formats agree and keeps the partial-results promise the error envelope makes: the rest of the book
stays accepted and exportable. C1 controls are called out because they *look* like the same class of character and
are not — XML 1.0 permits them, so refusing them would reject legitimate text.

An image's alternative text is outside this rule because it is an attribute value, not content: it is restored as
plain text and encoded once, by the writer, as *Restore an image's alternative text and a frontmatter value as plain
text* states.

#### Scenario: A bare less-than sign from the model is written as text

- **WHEN** a segment whose masked form is `Compare them.` is given the target `Якщо x < y`
- **THEN** the restored content is `Якщо x &lt; y`

#### Scenario: A bare ampersand from the model is written as text

- **WHEN** a segment whose masked form is `Smith & Sons` is given the target `Сміт & Сини`
- **THEN** the restored content is `Сміт &amp; Сини`

#### Scenario: Text that looks like a tag is written as text, not as an element

- **WHEN** a segment whose masked form is `Read it.` is given the target `Прочитай <це>`
- **THEN** the restored content is `Прочитай &lt;це&gt;`

#### Scenario: A restored fragment is accepted by the write path

- **WHEN** an FB2 document's segment is restored from the target `Сміт & Сини` and the document is written
- **THEN** the written document's paragraph text reads `Сміт & Сини`

#### Scenario: Markup written straight into a target is still written as markup

- **WHEN** an FB2 document is reassembled with a segment whose target text is `Привіт <emphasis>світ</emphasis>.`
- **THEN** the output contains the element `<emphasis>` and not the literal text `&lt;emphasis&gt;`

#### Scenario: A C0 control in the target fails that segment for both markup formats

- **WHEN** an EPUB segment, and separately an FB2 segment, is given a target containing U+0008
- **THEN** each caller receives a failed result carrying `ErrorCode.validation`, and the failure carries no book
  text

#### Scenario: An unpaired surrogate in the target fails that segment

- **WHEN** an EPUB segment, and separately an FB2 segment, is given a target containing a lone U+D800
- **THEN** each caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A C1 control is legal in XML and is restored normally

- **WHEN** an EPUB segment, and separately an FB2 segment, is given a target containing U+0085 or U+009F
- **THEN** the restored content carries that character and the operation succeeds

#### Scenario: A plain-text format does not refuse the same character

- **WHEN** a Markdown or TXT segment is given a target containing U+0008
- **THEN** the restored content carries that character and the operation succeeds

### Requirement: Restore a fragment carrying an entity reference

WHEN a restored fragment carries an entity reference the source document declared, the system SHALL parse it with
that declaration in scope so the reference survives, and SHALL NOT resolve any entity declared outside the document.

Source: FR-DOC-FB2-1 (`01_Product/03_DOCUMENT_FORMATS.md#fb2`), FR-DOC-02
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#xml-round-trip-config`,
`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#offline-invariant`, ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: an entity reference is masked atomically, so its fragment is the reference itself — and a fragment
is re-parsed on its own, outside the document that declared it. Without the declaration in scope that parse fails
with "the entity was referenced, but not declared", which would make every FB2 segment containing a non-breaking
space impossible to write back; the surveyed corpus carries 10,377 of them. Carrying the source's own internal
declarations into that parse costs nothing and fetches nothing — they are inline text, and an entity pointing at a
file or a URL is still never resolved.

#### Scenario: A restored non-breaking space entity is written back

- **WHEN** a segment of a document declaring `<!ENTITY nbsp "&#160;">`, whose masked form is `Розділ⟦g0⟧1` with `g0`
  mapped to `&nbsp;`, is given the target `Глава ⟦g0⟧1`
- **THEN** the operation succeeds

#### Scenario: The restored reference is still a reference

- **WHEN** that same target is restored
- **THEN** the restored content contains the entity reference `&nbsp;`

#### Scenario: An undeclared entity in a target is still a failure

- **WHEN** a document declaring only `nbsp` is reassembled with a segment whose target text is `a&mdash;b`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: An externally declared entity is not resolved

- **WHEN** an FB2 document declaring `<!ENTITY x SYSTEM "file:///etc/passwd">` and containing `<p>A&x;B</p>` is
  parsed
- **THEN** no content of that file appears anywhere in the parsed document

### Requirement: Mask a Markdown segment by the source ranges of its inline constructs

WHEN a Markdown block is segmented, the system SHALL replace each inline construct in it using the source ranges
that construct occupies, and SHALL leave the remaining source text unaltered in the masked form.

An emphasis, a strong emphasis and a link whose visible text is not its own destination SHALL be masked as a paired
group so their enclosed text is still translated. A code span, an image, a link written in autolink form or whose
visible text is its own destination, and each inline HTML fragment SHALL be masked as a single atomic token. A
GitHub-Flavored Markdown task-list marker at the start of a list-item segment — `[ ]`, `[x]`, or `[X]` together with
its required following horizontal whitespace — SHALL also be one atomic token, leaving only the task label
translatable. Any other inline construct SHALL be masked as a single atomic token.

A hard line break SHALL be masked as a single atomic token whose fragment is the trailing whitespace or backslash
that spells it, taken from the end of the preceding text run.

Source: FR-DOC-MD-1, FR-DOC-MD-2, FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-04,
FR-DOC-10 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-MD-1
(`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-49
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-49-code-and-technical-content-preservation`).

#### Scenario: An unchecked task marker stays protected while its label translates

- **WHEN** a Markdown list item is written `- [ ] Gravity is identical everywhere.`
- **THEN** its segment starts with one placeholder whose fragment is `[ ] ` and whose remaining masked text is
  `Gravity is identical everywhere.`

#### Scenario: A checked task marker preserves its state

- **WHEN** a Markdown list item starts with `[x] ` or `[X] `
- **THEN** its marker and following separator are one atomic placeholder and the checked state is unchanged on restore

#### Scenario: An emphasis is masked as a pair with its text still translatable

- **WHEN** a Markdown paragraph whose source is `He opened the *old* door.` is parsed
- **THEN** the segment's masked form is `He opened the ⟦g0⟧old⟦g1⟧ door.`

#### Scenario: The emphasis delimiters are what the map holds

- **WHEN** that same paragraph is parsed
- **THEN** the placeholder map is exactly `{g0: "*", g1: "*"}`

#### Scenario: A link's destination is protected inside the closing token

- **WHEN** a Markdown paragraph whose source is `See [chapter two](ch2.md) now.` is parsed
- **THEN** the placeholder map's `g1` entry is `](ch2.md)`

#### Scenario: A link's label stays translatable

- **WHEN** that same paragraph is parsed
- **THEN** the segment's masked form is `See ⟦g0⟧chapter two⟦g1⟧ now.`

#### Scenario: An autolinked URL is one atomic token

- **WHEN** a Markdown paragraph whose source is `See <https://x.org/a> now.` is parsed
- **THEN** the segment's masked form is `See ⟦g0⟧ now.`

#### Scenario: An autolinked email address is one atomic token

- **WHEN** a Markdown paragraph whose source is `Write to <me@example.com> now.` is parsed
- **THEN** the segment's masked form is `Write to ⟦g0⟧ now.`

#### Scenario: A link whose label repeats its destination is one atomic token

- **WHEN** a Markdown paragraph whose source is `See [https://x.org](https://x.org) now.` is parsed
- **THEN** the segment's masked form is `See ⟦g0⟧ now.`

#### Scenario: A code span is one atomic token

- **WHEN** a Markdown paragraph whose source is ``Call `List.of()` first.`` is parsed
- **THEN** the segment's masked form is `Call ⟦g0⟧ first.`

#### Scenario: An image is one atomic token

- **WHEN** a Markdown paragraph whose source is `Before ![Figure 1](fig1.png) after` is parsed
- **THEN** the segment's masked form is `Before ⟦g0⟧ after`

#### Scenario: An inline HTML start tag and end tag are two separate atomic tokens

- **WHEN** a Markdown paragraph whose source is `a <span class="x">b</span> c` is parsed
- **THEN** the segment's masked form is `a ⟦g0⟧b⟦g1⟧ c`

#### Scenario: An unpaired inline HTML tag is still one atomic token

- **WHEN** a Markdown paragraph whose source is `a <br> b` is parsed
- **THEN** the segment's masked form is `a ⟦g0⟧ b`

#### Scenario: A strong emphasis nested in an emphasis produces well-formed pairs

- **WHEN** a Markdown paragraph whose source is `*a **b** c*` is parsed
- **THEN** the segment's masked form is `⟦g0⟧a ⟦g1⟧b⟦g2⟧ c⟦g3⟧`

#### Scenario: An emphasis spanning a line break is masked across both of its ranges

- **WHEN** a Markdown paragraph whose source is `*bcd` followed by a line feed and `efg*` is parsed
- **THEN** the segment's masked form is `⟦g0⟧bcd` followed by a line feed and `efg⟦g1⟧`

#### Scenario: A hard line break is one token

- **WHEN** a Markdown paragraph whose source is `line one` followed by two spaces, a line feed and `line two` is parsed
- **THEN** the segment's masked form is `line one⟦g0⟧` followed by a line feed and `line two`

#### Scenario: The hard line break's own spelling is what the map holds

- **WHEN** that same paragraph is parsed
- **THEN** the placeholder map's `g0` entry is the two space characters

### Requirement: Escape model-introduced Markdown punctuation when restoring

IF a segment's format is Markdown, the segment belongs to a body unit, and the text the model supplied would parse as
a construct the source did not contain, THEN the system SHALL neutralise the characters that would form it so the restored text carries no such
construct, and the operation SHALL NOT fail.

This SHALL apply to a construct that begins a block as well as to one within a line.

Neutralising SHALL be a backslash escape WHERE the character that would form the construct is ASCII punctuation,
because that is the only position at which CommonMark gives a backslash escaping meaning.

WHERE the construct is a hard line break spelled as trailing spaces, neutralising SHALL instead delete those
spaces, and SHALL leave untouched any space that came from a restored fragment.

IF the character that would form the construct is neither ASCII punctuation nor such a hard line break, THEN the
system SHALL leave it as written and allow the structure comparison to report the difference. This SHALL hold for
every construct the system classifies, without exception for the kind of construct it is.

Neutralisation SHALL be bounded: the system SHALL act on at most a fixed number of model-introduced constructs in
one segment, and IF a segment carries more than that, THEN the system SHALL leave the remainder as written for the
structure comparison to report rather than continuing without limit.

WHERE the segment's content is inline content owned by a block marker the skeleton holds — a heading or a table
cell — the system SHALL NOT neutralise a block construct type that the structure comparison disregards for that
kind, since neutralising it spends a visible character on a difference the comparison is about to ignore.

**Source**: FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), ADR-0031 (`docs/adr/ADR-0031-masked-text-is-character-data.md`). In plain words: this is the same rule that turns a model's stray `<` into `&lt;` for EPUB, applied to the format whose markup is punctuation rather than tags. A translation containing `5 * 3`, a footnote marker, or an asterisked aside is ordinary prose, and the book should print it rather than gain emphasis the author never wrote. The block-level half is not an afterthought: a translated sentence beginning `1985.` becomes an ordered list, `- як казав автор` a bullet list, and `# Це не заголовок` a heading, and every one of those would otherwise fail a chunk over a character the frozen spec never protected. A frontmatter value and an image description are not Markdown content and are not escaped this way (see *Restore an image's alternative text and a frontmatter value as plain text*).

**"SHALL NOT fail" has three exceptions, not two, and the third is this bound.** Each round of neutralisation
re-parses the candidate and acts on one construct, so the round limit is in practice a cap on how many
model-introduced constructs a single segment may carry — measured, a target with twenty-five independent emphasis
pairs exhausts it with five still unescaped, and the structure comparison then reports the mismatch as
`ErrorCode.validation`. The bound is deliberate: without it a construct that re-parses to the same excess type
after being acted on loops forever, which is exactly what a setext heading did before the escapability guard was
made universal. It is stated here because it was not stated anywhere before — it predates this change's second
audit and neither audit swept it, and an unstated bound on a requirement that promises not to fail is precisely
the shape of defect that audit was fixing.

#### Scenario: More model-introduced constructs than the bound are reported, not escaped

- **WHEN** a Markdown paragraph segment whose masked form is `plain words` is given a target carrying twenty-five
  independent emphasis pairs
- **THEN** the caller receives a failed result carrying `ErrorCode.validation` rather than the escaper continuing
  without limit

#### Scenario: A model-introduced hard line break is neutralised by deleting its spaces

- **WHEN** a Markdown segment whose masked form is `alpha beta` followed by a line feed and `gamma delta` is given
  a target identical except that two spaces precede the line feed
- **THEN** the restored content is `alpha beta` followed by a line feed and `gamma delta`, and the operation
  succeeds

#### Scenario: A hard line break the source itself owns is left alone

- **WHEN** a Markdown segment whose source spells a hard line break as two trailing spaces is given its own masked
  form as its target
- **THEN** the restored content still carries those two spaces

#### Scenario: A construct whose marker is not ASCII punctuation is reported rather than escaped

- **WHEN** a Markdown segment whose masked form is `Just prose here` is given the target `    відступ`, whose four
  leading spaces would parse as an indented code block
- **THEN** the caller receives a failed result carrying `ErrorCode.validation` and no backslash is written

#### Scenario: A setext heading's marker is not on the line the escape would reach

- **WHEN** a Markdown heading segment is given a target holding `Заголовок`, a line feed, `---`, a line feed and
  `x`, whose second line underlines the first into a setext heading
- **THEN** the caller receives a failed result carrying `ErrorCode.validation` and no backslash is written

#### Scenario: A heading's ordered-list marker is left as the model wrote it

- **WHEN** a Markdown heading segment whose masked form is `Alpha beta` is given the target `1. Альфа бета`
- **THEN** the restored content is `1. Альфа бета`, carrying no backslash, and the operation succeeds

#### Scenario: A model-introduced emphasis is escaped rather than parsed

- **WHEN** a Markdown segment whose masked form is `plain words` is given the target `звичайні *слова*`
- **THEN** the restored content is `звичайні \*слова\*`

#### Scenario: An asterisk that forms no construct is left alone

- **WHEN** a Markdown segment whose masked form is `plain words` is given the target `5 * 3 дорівнює 15`
- **THEN** the restored content is `5 * 3 дорівнює 15`

#### Scenario: A translation beginning with a year and a period does not become a list

- **WHEN** a Markdown segment whose masked form is `In 1985 he opened the door.` is given the target
  `1985. Він відчинив двері.`
- **THEN** the restored content is `1985\. Він відчинив двері.`

#### Scenario: A translation beginning with a dash does not become a bullet

- **WHEN** a Markdown segment whose masked form is `As the author said` is given the target `- як казав автор`
- **THEN** the restored content is `\- як казав автор`

#### Scenario: A restored placeholder fragment is not escaped

- **WHEN** a Markdown segment whose masked form is `the ⟦g0⟧old⟦g1⟧ door` is given the target
  `⟦g0⟧старі⟦g1⟧ \*нові\* двері`
- **THEN** the restored content is `*старі* \*нові\* двері`, the restored delimiters unescaped and the model's own
  escaped

### Requirement: Verify that a restored Markdown segment keeps its structure

WHEN a Markdown body segment's placeholders have been restored, the system SHALL parse the segment's source text and
the restored text in the same way, each on its own, and SHALL compare the multiset of construct types each yields,
disregarding text and soft line breaks.

WHERE the source contains a paired emphasis, strong emphasis, or translatable link with nonblank visible text, the
restored counterpart SHALL also have nonblank visible text. WHERE the source segment's only nonblank inline construct
is a translatable link, the restored segment's only nonblank inline construct SHALL also be that link. WHERE a
list-item source starts with a task-list marker, the restored text SHALL start with that exact marker and separator.

WHERE the segment's content is inline content owned by a block marker the skeleton holds — a heading or a table
cell — the comparison SHALL disregard block construct types on both sides.

WHERE the segment's content is owned by such a block marker, IF the restored text carries more line terminators than
the segment's source text, THEN the system SHALL treat the segment as a structure mismatch.

WHERE the segment is a table cell, IF the restored text carries more unescaped `|` characters than the segment's
source text, THEN the system SHALL treat the segment as a structure mismatch.

IF the two multisets differ, either paired-content or task-marker condition fails, or either containment condition
above holds, THEN the system SHALL return a failed result carrying `ErrorCode.validation`, and SHALL NOT attempt a
repair inside `:document`.

**Source**: FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-MD-1 (`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-43 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), ADR-0031 (`docs/adr/ADR-0031-masked-text-is-character-data.md`). In plain words: a restored Markdown segment is spliced straight into the file, so a translation that changes its structure — a link whose label came loose, a task marker moved after its label, a second block inside a heading — would change the book the next parser sees. Comparing the kinds of construct on both sides catches that and fails the one segment instead of the book. The check is for body segments only: a frontmatter value and an image description are not Markdown content and are restored as plain text.

#### Scenario: Link delimiters cannot be detached from their label

- **WHEN** a source link `[chapter two](ch2.md)` is restored as translated prose followed by `[](ch2.md)`
- **THEN** the caller receives `ErrorCode.validation`

#### Scenario: A sole-link label cannot spill outside its link

- **WHEN** source `[Visual Overview](#visual-overview)` restores as `Overview [Visual outline](#visual-overview)`
- **THEN** the caller receives `ErrorCode.validation`

#### Scenario: A task marker cannot move after its label

- **WHEN** an unchecked task-list item is restored with `[ ] ` after its translated label
- **THEN** the caller receives `ErrorCode.validation`

#### Scenario: A numbered heading's translation is not rejected for losing a list it never had

- **WHEN** a Markdown heading written `## 1. Alpha beta gamma` yields the segment `1. Alpha beta gamma`, and that segment is given the target `gamma beta Alpha 1.`
- **THEN** the restored content is `gamma beta Alpha 1.` and the operation succeeds

#### Scenario: A table cell's translation is not rejected for losing a list it never had

- **WHEN** a Markdown table cell whose content is `1. Alpha` is given the target `Alpha 1.`
- **THEN** the restored content is `Alpha 1.` and the operation succeeds

#### Scenario: A heading whose translation gains a second block is a validation failure

- **WHEN** a Markdown heading written `# Title` yields the segment `Title`, and that segment is given a target holding `Заголовок`, a blank line, and `second paragraph`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A table cell whose translation adds an unescaped pipe is a validation failure

- **WHEN** a Markdown table cell whose content is `cell` is given the target `Комірка | друга`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A table cell whose translation escapes its own pipe succeeds

- **WHEN** a Markdown table cell whose content is `cell` is given the target `Комірка \| друга`
- **THEN** the restored content is `Комірка \| друга` and the operation succeeds

#### Scenario: A paragraph that becomes a bullet list is still a validation failure

- **WHEN** a Markdown paragraph segment whose masked form is `⟦g0⟧old⟦g1⟧ door` is given the target `⟦g0⟧ старі⟦g1⟧ двері`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A space introduced inside an emphasis pair is a validation failure

- **WHEN** a Markdown segment whose masked form is `the ⟦g0⟧old⟦g1⟧ door` is given the target `⟦g0⟧ старі ⟦g1⟧ двері`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A translation that moves the emphasis to the front passes

- **WHEN** a Markdown segment whose masked form is `the ⟦g0⟧old⟦g1⟧ door` is given the target `⟦g0⟧старі⟦g1⟧ двері`
- **THEN** the restored content is `*старі* двері`

#### Scenario: A segment restored unchanged always passes

- **WHEN** every Markdown fixture segment is given its own masked form as its target
- **THEN** no segment fails the structure comparison

#### Scenario: Rendering two soft-wrapped source lines as one passes

- **WHEN** a Markdown segment whose masked form is `line one` followed by a line feed and `line two`, with no placeholder between them, is given the target `рядок один рядок два`
- **THEN** the restored content is `рядок один рядок два`

### Requirement: Mask identically for identical bytes

WHEN the same file is parsed twice, the system SHALL produce, for every segment, the same masked form and the same
placeholder map in the same order.

Source: FR-IMPORT-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`),
`02_Architecture/03_DOCUMENT_MODEL.md#inline-masking` (first-appearance order), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`).
In plain words: resume, translation-memory lookup and change detection all assume a second parse of an unchanged
book agrees with the first. A masker whose numbering depended on iteration order over a hash map would break all
three, intermittently, in a way no single test run would show.

#### Scenario: Every format agrees with itself across parses

- **WHEN** one fixture of each of EPUB, FB2, Markdown and TXT is parsed twice
- **THEN** every segment's masked form and placeholder map are equal between the two parses

### Requirement: Restore a masked segment to its source content when nothing is translated

WHEN a segment's masked form is supplied back as its own target, the system SHALL produce restored content that is
equal to the segment's source content for TXT and Markdown, and canonical-equal to it for EPUB and FB2.

Source: FR-DOC-04, FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`),
`02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`.
In plain words: mask-then-restore with no translation in between must be a no-op, or masking is losing something no
other test would catch. Equal rather than canonical-equal for the two buffer formats, because their restored text is
spliced back into the file as bytes and has nothing to normalize; canonical-equal for the two tree formats, because
decoding a character reference and re-escaping it may legitimately choose a different spelling for the same
character — the same allowance the golden round trip already makes.

#### Scenario: Every EPUB and FB2 fixture survives a mask-then-restore cycle

- **WHEN** every EPUB and FB2 fixture in the catalogue is parsed and each segment's masked form is supplied back as
  its own target
- **THEN** every segment's restored content is canonical-equal to its source content

#### Scenario: A plain-text segment survives byte-for-byte

- **WHEN** a TXT fixture is parsed and each segment's masked form is supplied back as its own target
- **THEN** every segment's restored content is exactly equal to its source content

#### Scenario: A Markdown segment survives byte-for-byte

- **WHEN** a Markdown fixture is parsed and each segment's masked form is supplied back as its own target
- **THEN** every segment's restored content is exactly equal to its source content

#### Scenario: A segment holding a character reference, a comment and nested emphasis survives

- **WHEN** an EPUB paragraph whose content is `Smith &amp; <b>Sons <i>Ltd</i></b><!-- note -->` is parsed and its
  masked form is supplied back as its own target
- **THEN** the restored content is canonical-equal to `Smith &amp; <b>Sons <i>Ltd</i></b><!-- note -->`

### Requirement: Leave the no-edit round trip unaffected by masking

WHEN a document is reassembled and no segment carries target text, the system SHALL produce an output that is
canonical-equal to the source for EPUB, FB2 and Markdown, and byte-for-byte identical for TXT.

Source: FR-DOC-09, FR-EXPORT-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-export`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`),
`02_Architecture/03_DOCUMENT_MODEL.md#reassembly`.
In plain words: masking adds a second view of a segment and must not touch the first. The four golden round-trip
tests are the whole fidelity guarantee of this project, and a change that quietly moved one of them would be trading
proven behaviour for unproven behaviour. Stated as its own requirement so that it is checked deliberately rather
than assumed because the goldens happen to be green.

#### Scenario: The EPUB golden is unchanged

- **WHEN** an EPUB fixture is reassembled with zero segment edits
- **THEN** the output is canonical-equal to the source

#### Scenario: The FB2 golden is unchanged

- **WHEN** an FB2 fixture is reassembled with zero segment edits
- **THEN** the output is canonical-equal to the source

#### Scenario: The Markdown golden is unchanged

- **WHEN** a Markdown fixture is reassembled with zero segment edits
- **THEN** the output is canonical-equal to the source

#### Scenario: The TXT golden is still byte-exact

- **WHEN** a TXT fixture is reassembled with zero segment edits
- **THEN** the output is byte-for-byte identical to the source

### Requirement: Release an opened book

WHEN a caller releases an opened book, the system SHALL drop what it holds in memory for that book and report that the
book was open. WHEN the same book is released again, the system SHALL report that it was not open. IF a released book
is written, THEN the system SHALL fail with `ErrorCode.internal` and create no file.

**Source:** `docs/implementation_plan/CHANGE_BACKLOG.md#decision-debt` (finding D3),
`02_Architecture/03_DOCUMENT_MODEL.md#data-model`.
In plain words: an opened EPUB holds every unzipped entry and a parsed tree per chapter until something lets it go.
Without a release, every translated book would stay in memory until the app quits.

#### Scenario: Releasing a book twice

- **WHEN** a TXT book is opened, released, and released again
- **THEN** the first release reports that it was open and the second reports that it was not

#### Scenario: Writing a released book

- **WHEN** an opened Markdown book is released and then written to `Book.uk.md`
- **THEN** the result is `ErrorCode.internal` and `Book.uk.md` is not created

### Requirement: Keep the lines of a list or a stanza

WHEN a TXT paragraph is masked and it has at least two lines of which either two open with a list marker (a bullet,
a dash or `1.`/`1)`) or none is longer than 60 characters, the system SHALL put a line-break token that stands for
nothing before each line end inside it, so a target that merges the lines fails the placeholder gate.

**Source**: FR-DOC-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`. In plain words: in the gemma4:e4b run on the earth-gravity fixture, the TXT lists and one stanza came back as one line each and were accepted, because nothing marked where a line ended; and a Markdown stanza whose model wrote a space after a hard-break token restored as `землю,\ він`, failed the structure check and stayed in English. A merged reply now gets its line end put back by position (the restore-missing repair) or a repair note saying the token ends a line; a space next to the token is simply put back as the line end it was.

#### Scenario: A TXT list keeps one item per line

- **WHEN** the TXT paragraph `• Mass describes how much matter an object contains.` / `• Weight describes the
  gravitational force acting on that mass.` (two lines) is masked
- **THEN** its masked form ends the first line with `⟦g0⟧`, and the target `• Маса … тіло.⟦g0⟧ • Вага … масу.` restores
  as two lines
- **AND** the target `• Маса … тіло. • Вага … масу.` is refused by the gate

### Requirement: Add no line-break token to prose wrapped at a column

WHEN a TXT paragraph has at least one line longer than 60 characters and fewer than two lines that open with a list
marker, the system SHALL treat it as prose wrapped at a column and SHALL put no line-break token that stands for nothing
in its masked form.

**Source**: FR-DOC-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`. In plain words: a paragraph hard-wrapped at a column is one sentence flow, and a model must be free to re-wrap it.

#### Scenario: Wrapped prose carries no line-break token

- **WHEN** a TXT paragraph of two lines of 72 and 68 characters, neither opening with a list marker, is masked
- **THEN** its masked form holds no `⟦gN⟧` token

### Requirement: Ignore a placeholder that stands for nothing when verifying an export

Export verification SHALL ignore a placeholder that stands for nothing, since whether a paragraph carries one depends
on its line lengths, which a translation changes.

**Source**: FR-DOC-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`. In plain words: a translation changes line lengths, so a paragraph may legitimately gain or lose a line-break token that stands for nothing, and that must not fail the export check.

#### Scenario: A line-break token that stands for nothing does not fail the check

- **WHEN** a two-line TXT paragraph carrying a line-break token that stands for nothing is written with a translation
  whose lines are longer than 60 characters, so that on re-opening the paragraph carries no such token
- **THEN** the export's placeholder comparison passes

### Requirement: Restore the whitespace beside a line-break token from the masked form

WHEN a target is restored, the system SHALL first replace the whitespace on both sides of each line-break token that
the masked form has next to a line end with the masked form's own whitespace there, leaving the token where the target
put it — for Markdown and TXT alike.

**Source**: FR-DOC-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`. In plain words: a model that writes a space after a hard-break token would otherwise restore as a stray backslash and a space; the space is simply put back as the line end it was.

#### Scenario: A Markdown stanza with a space after its hard break

- **WHEN** the stanza `A stone let go will find the ground,\` / `it never asks the way;\` / … is restored from
  `Камінь знайде землю,⟦g0⟧ він не питає шляху;⟦g1⟧ …`
- **THEN** the restored text is `Камінь знайде землю,\` / `він не питає шляху;\` / …, one line each

### Requirement: Repair a refused target's placeholder tokens without a model

WHEN a caller asks the document port to repair a target the placeholder gate refused, the system SHALL return a
repaired target that passes that gate, or `ErrorCode.validation` when none does, without calling a model and without
changing any word of the target. Two modes SHALL exist: restore missing and re-place all.

**Source**: FR-DOC-05, FR-QA-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), EC-INLINE-2 (`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`), ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: a small model that translated every word but dropped one token of a pair should not lose its translation. The two real paragraphs that reached an export broken — a drop cap and an italic phrase before a closing quote — each lost exactly one token; putting it back where the source had it is a deterministic step any person could take by hand. The repaired target still has to pass the whole gate, so the repair can never let through what the gate refuses, and the caller records that the markup was put back so a person can check it. A token the text never had is different: a small model writes one in place of a name (`і ⟦g1⟧ ніколи` for `and Vance never`), and dropping it silently deleted that name in the gemma4:e4b run on the earth-gravity fixture, so only a token glued to a word may go.

#### Scenario: Nothing to wrap is not repaired

- **WHEN** the segment `⟦g0⟧old⟦g1⟧ door` is repaired in re-place-all mode with an empty target
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

### Requirement: Repair by restoring the missing tokens

In restore-missing mode the system SHALL keep every token the target placed, drop each token the masked form holds
fewer times, drop a token the masked form never holds only when a letter or digit touches it (it is glued to a word,
which stays), and put each missing token back at the position the placement rule gives.

**Source**: FR-DOC-05, FR-QA-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), EC-INLINE-2 (`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`), ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: a model that dropped one token of a pair loses nothing else, and a token glued to a word is noise that can go while the word stays.

#### Scenario: An invented token glued to a word is dropped

- **WHEN** the segment `⟦g0⟧old⟦g1⟧ door` is repaired in restore-missing mode with the target `⟦g0⟧старі⟦g1⟧ ⟦g7⟧двері`
- **THEN** the repaired target is `⟦g0⟧старі⟦g1⟧ двері`

#### Scenario: A surplus token is dropped

- **WHEN** the segment `⟦g0⟧old⟦g1⟧ door` is repaired in restore-missing mode with the target
  `⟦g0⟧старі⟦g1⟧⟦g1⟧ двері`
- **THEN** the repaired target is `⟦g0⟧старі⟦g1⟧ двері`

### Requirement: Place a missing token where the source had it

The system SHALL put a missing token at the position of the target most like the one it held in the masked form: the
same kind of boundary (a word's start, a word's end, after a closing quote) nearest the source position scaled to the
target's length, and inside a word only where the source's token sat inside a word, at the same letter.

**Source**: FR-DOC-05, FR-QA-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), EC-INLINE-2 (`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`), ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: the two real paragraphs that reached an export broken — a drop cap and an italic phrase before a closing quote — each lost exactly one token; putting it back where the source had it is a deterministic step any person could take by hand.

#### Scenario: A drop cap's lost closing token goes back after the first letter

- **WHEN** the segment `⟦g0⟧“A⟦g1⟧bove all,” said his master.` is repaired in restore-missing mode with the target
  `⟦g0⟧«Понад усе», — сказав господар.`
- **THEN** the repaired target is `⟦g0⟧«П⟦g1⟧онад усе», — сказав господар.`

#### Scenario: An italic phrase's lost closing token goes back after its closing quote

- **WHEN** the segment `“Remember ⟦g0⟧this,”⟦g1⟧ he said in a soft voice.` is repaired in restore-missing mode with the
  target `«Пам'ятай ⟦g0⟧це», — сказав він тихим голосом.`
- **THEN** the repaired target is `«Пам'ятай ⟦g0⟧це»,⟦g1⟧ — сказав він тихим голосом.`

### Requirement: Repair by placing all the tokens again

In re-place-all mode the system SHALL strip every token of the target and place all of them again, in the masked
form's order, by the placement rule.

**Source**: FR-DOC-05, FR-QA-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), EC-INLINE-2 (`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`), ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: when a pair is swapped or tangled, starting the tokens over in the source's order is safer than patching.

#### Scenario: A swapped pair is placed again

- **WHEN** the segment `See ⟦g0⟧this⟦g1⟧ now.` is repaired in re-place-all mode with the target
  `Дивись ⟦g1⟧це⟦g0⟧ зараз.`
- **THEN** the repaired target is `Дивись ⟦g0⟧це⟦g1⟧ зараз.`

### Requirement: Never drop a token that stands for a word

IF a token the masked form never holds stands where a word would — between two spaces, at an edge, or beside
punctuation — THEN neither repair mode SHALL drop it, and the repair SHALL answer `ErrorCode.validation`, so the
reply goes to the model repair instead of losing the word the token replaced.

**Source**: FR-DOC-05, FR-QA-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), EC-INLINE-2 (`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`), ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: a small model writes a token in place of a name (`і ⟦g1⟧ ніколи` for `and Vance never`), and dropping it silently deleted that name in the gemma4:e4b run on the earth-gravity fixture, so only a token glued to a word may go.

#### Scenario: An invented token standing for a word is not dropped

- **WHEN** the segment `and Vance never let` is repaired in either mode with the target `і ⟦g1⟧ ніколи не дозволяв`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

### Requirement: Keep the placeholder gate a check that never repairs

The placeholder gate itself SHALL stay a check that reports and never repairs; the repair is a separate operation the
caller chooses to call.

**Source**: FR-DOC-05, FR-QA-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: the gate is the one check no confidence score can outvote, so it reports and never fixes; fixing is a separate step whose result must pass the gate again.

#### Scenario: The gate refuses and leaves the target as it was

- **WHEN** the gate is given the target `⟦g0⟧старі двері` for the masked form `⟦g0⟧old⟦g1⟧ door`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation` and no repaired text is returned

### Requirement: Show the book's structure as a titled tree

The import screen SHALL, once a book is open, present the book's structure as a nested tree in reading order, titled from the book's own
navigation, each node carrying a count pill of the translatable segments that fall under it, and SHALL report the
book's total segment count.

**Source**: FR-DOC-01, FR-DOC-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: this is where a person checks the application understood their book before spending hours on it — an EPUB that opened with three segments instead of five thousand is visible here in a second. Now that the book's own table of contents, section titles and headings are read, the screen can show chapters by name and nested the way the book nests them, instead of a flat list of file paths. The auxiliary text is left out of the tree because it is not part of the reading order; counting it would make the chapter numbers not add up to what a reader sees. The tree stays read-only because nothing downstream reads a translate-or-preserve choice.

#### Scenario: Chapters are listed by their navigation titles

- **WHEN** an EPUB whose navigation lists `Letter 1`, `Chapter 1` and `Chapter 2` for its first three spine
  documents is open
- **THEN** the tree's first three top-level nodes read `Letter 1`, `Chapter 1` and `Chapter 2`, each with its own
  segment count

#### Scenario: Nested entries are nested nodes

- **WHEN** an FB2 book whose first section `Part One` contains the sections `Chapter 1` and `Chapter 2` is open
- **THEN** `Chapter 1` and `Chapter 2` are shown as children of `Part One`
- **AND** `Part One`'s count is the number of segments in all three sections

#### Scenario: The counts add up to the reported total

- **WHEN** the import screen reports a total of `1240` segments
- **THEN** the counts of the top-level nodes sum to `1240`

### Requirement: Leave auxiliary text out of the structure tree

The structure tree SHALL show only the book's reading-order content: the auxiliary text (metadata, navigation labels,
page titles, alternative text, frontmatter values) SHALL NOT appear as a node and SHALL NOT be counted in the total.

**Source**: FR-DOC-01, FR-DOC-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: the auxiliary text is left out of the tree because it is not part of the reading order; counting it would make the chapter numbers not add up to what a reader sees.

#### Scenario: Auxiliary text is not a node

- **WHEN** an EPUB with a translatable title, 12 navigation labels and 7 image descriptions is open
- **THEN** no node represents the title, the labels or the descriptions, and none of those 20 segments is counted
  in the total

### Requirement: Show Untitled for a structure node without a title

A structure node that has no title SHALL be shown as `Untitled`.

**Source**: FR-DOC-01, FR-DOC-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: text before a book's first heading still has to be a node a person can see, so it gets a plain name instead of an empty one.

#### Scenario: A node without a title reads Untitled

- **WHEN** a Markdown book whose first paragraph comes before its first heading `# Chapter 1` is open
- **THEN** the tree's first top-level node reads `Untitled` and the second reads `Chapter 1`

### Requirement: Keep the structure tree read-only

The structure tree SHALL be read-only: it SHALL NOT offer to include or exclude a unit, nor to change anything about
the book. The import screen's own `Continue` is the only way on; the structure has no step of its own since 15i.U3.

**Source**: FR-DOC-01, FR-DOC-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: the tree stays read-only because nothing downstream reads a translate-or-preserve choice.

#### Scenario: Nothing in the tree can be changed

- **WHEN** the import screen is shown for an open book
- **THEN** no control offers to include, exclude, rename or reorder a unit

### Requirement: Produce a book's auxiliary text as translatable segments

WHEN a book is opened, the system SHALL always produce its auxiliary text as segments of its auxiliary unit,
whatever the Book Brief's "Also translate" switches say.

**Source**: FR-DOC-11, FR-DOC-10 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-EPUB-8 (`01_Product/03_DOCUMENT_FORMATS.md#epub`), FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), DD-47 as amended by this change to list descriptions under the metadata switch (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`), `docs/next_features.md` §8. In plain words: a translated book whose table of contents, page titles, shelf title, description and image descriptions are still in the source language reads as half-translated. These texts are always read into segments, and the brief's switches decide later whether the run translates them — so opening the same file always gives the same segments, whatever the brief says. A description is metadata and follows the metadata switch; a page title follows the navigation switch; the switches themselves belong to `translation-pipeline`. An NCX label that reads exactly like a navigation-document label is not a second segment: translating the same words twice wastes a model call and lets a reviewer's edit reach only one of them. A navigation document that is also in the spine is body text already, so its links are not read a second time. An FB2 author is read from the parts of a name only, because the other fields are identifiers and addresses; the source-book information and the file's history describe another book and another file, so neither is text to translate. Frontmatter keys are field names a program reads, so they are never text.

#### Scenario: The switches do not change what is produced

- **WHEN** the same EPUB is opened once with every "Also translate" switch on and once with every switch off
- **THEN** both openings produce identical auxiliary segments with identical ids

### Requirement: Produce an EPUB's metadata as auxiliary segments

From an EPUB the system SHALL produce auxiliary segments for the package's first `dc:title` (kind `METADATA_TITLE`),
each `dc:creator` (`METADATA_AUTHOR`) and each `dc:description` (`METADATA_DESCRIPTION`).

**Source**: FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47 as amended by this change to list descriptions under the metadata switch (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`. In plain words: the shelf title, the author and the description are what a library shows; a description is metadata and follows the metadata switch.

#### Scenario: EPUB metadata is collected

- **WHEN** an EPUB with `dc:title` `Frankenstein`, `dc:creator` `Mary Shelley` and a `dc:description` is opened
- **THEN** its auxiliary unit holds one `METADATA_TITLE`, one `METADATA_AUTHOR` and one `METADATA_DESCRIPTION` segment

### Requirement: Produce an EPUB's navigation, page titles and image text as auxiliary segments

From an EPUB the system SHALL produce auxiliary segments for the link text of each entry of a navigation document that
is not in the spine (`NAV_LABEL`), the text of each NCX `navLabel` whose source text differs from that of every
navigation-label segment (`NAV_LABEL`), the `<head><title>` of each spine content document (`TITLE`), and the `alt`
value of each image (`ALT`).

**Source**: FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-EPUB-8 (`01_Product/03_DOCUMENT_FORMATS.md#epub`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: a translated book whose table of contents, page titles and image descriptions are still in the source language reads as half-translated. An NCX label that reads exactly like a navigation-document label is not a second segment, and a navigation document that is also in the spine is body text already, so its links are not read a second time.

#### Scenario: EPUB auxiliary text is collected

- **WHEN** an EPUB with `dc:title` `Frankenstein`, `dc:creator` `Mary Shelley`, a `dc:description`, a navigation
  document outside the spine of 11 entries, an NCX whose 11 labels read exactly like those entries, 12 content
  documents each with a `<head><title>` and 7 images with non-empty `alt` values is opened
- **THEN** its auxiliary unit holds 1 title, 1 author, 1 description, 11 navigation-label, 12 page-title and 7
  alternative-text segments

#### Scenario: An NCX label that differs from the navigation document is its own segment

- **WHEN** an EPUB whose navigation document labels an entry `Chapter 1` and whose NCX labels the same entry
  `Chapter I` is opened
- **THEN** its auxiliary unit holds one navigation-label segment for `Chapter 1` and another for `Chapter I`

#### Scenario: A navigation document in the spine yields no navigation label

- **WHEN** an EPUB whose navigation document `nav.xhtml` is listed in the spine and holds 5 entries is opened
- **THEN** its auxiliary unit holds no navigation-label segment
- **AND** the 5 entries' link texts are segments of the `nav.xhtml` body unit

### Requirement: Produce an FB2's auxiliary segments

From an FB2 the system SHALL produce auxiliary segments for the title information's `book-title` (`METADATA_TITLE`),
each `author` (`METADATA_AUTHOR`), built from its `first-name`, `middle-name`, `last-name` and `nickname` only, one
placeholder pair around each part, and each paragraph of its `annotation` (`METADATA_DESCRIPTION`).

**Source**: FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`). In plain words: an FB2 author is read from the parts of a name only, because the other fields are identifiers and addresses.

#### Scenario: FB2 auxiliary text is collected

- **WHEN** an FB2 whose title information has the `book-title` `Harry Potter et la Coupe de Feu`, one `author` and
  an `annotation` of two paragraphs is opened
- **THEN** its auxiliary unit holds one title segment, one author segment and two description segments

#### Scenario: An FB2 author is read from the parts of the name only

- **WHEN** an FB2 whose title information holds `<author><first-name>Leo</first-name> <last-name>Tolstoy</last-name>
  <id>a1b2</id><email>leo@example.org</email></author>` is opened
- **THEN** its auxiliary unit holds one author segment whose masked form is `⟦g0⟧Leo⟦g1⟧ ⟦g2⟧Tolstoy⟦g3⟧`
- **AND** no segment holds `a1b2` or `leo@example.org`

### Requirement: Produce a Markdown or plain-text book's auxiliary segments

From a Markdown file the system SHALL produce auxiliary segments for each frontmatter value that is text
(`FRONTMATTER_VALUE`), as *Segment only the text values of Markdown frontmatter, never its keys* defines it, and for the
alternative text of each image (`ALT`); from a plain-text file it SHALL produce none.

**Source**: FR-DOC-10, FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: a Markdown book's title lives in its frontmatter and its image descriptions in the text; a plain-text file has neither.

#### Scenario: Only a frontmatter text value is a segment

- **WHEN** a Markdown file opening with `---\ntitle: The Book\nlang: en\ndate: 2024-05-01\ndraft: true\n---\n` is
  opened
- **THEN** its auxiliary unit holds exactly one segment, `The Book`
- **AND** no segment holds `title`, `lang`, `en`, `2024-05-01` or `true`

#### Scenario: A Markdown image description is an auxiliary segment

- **WHEN** a Markdown file holding `![Figure 1](fig1.png)` is opened
- **THEN** its auxiliary unit holds one `ALT` segment with the source `Figure 1`

#### Scenario: A plain-text book has an empty auxiliary unit

- **WHEN** `notes.txt` is opened
- **THEN** its auxiliary unit holds no segment

### Requirement: Produce no auxiliary segment for what is not text

The system SHALL NOT produce a segment for a Markdown frontmatter key, for an FB2 author's `id`, `email` or
`home-page`, or for anything inside the FB2 `src-title-info` or `history`, and SHALL produce no auxiliary segment for
an empty value.

**Source**: FR-DOC-11, FR-DOC-10 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`). In plain words: the source-book information and the file's history describe another book and another file, identifiers and addresses are not prose, and frontmatter keys are field names a program reads, so none of them is text to translate.

#### Scenario: FB2 source-book information and history are not read

- **WHEN** an FB2 whose `src-title-info` holds `<book-title>Война и мир</book-title>` and whose `document-info` holds
  the `history` paragraph `v1.0 — scanned` is opened
- **THEN** no segment holds `Война и мир` or `v1.0 — scanned`

#### Scenario: An empty value yields no segment

- **WHEN** an EPUB whose package holds an empty `<dc:description></dc:description>` is opened
- **THEN** its auxiliary unit holds no description segment

### Requirement: Write auxiliary translations back into their own slots

WHEN a book is written with target text for auxiliary segments, the system SHALL write each translation into the
exact slot its segment was read from — the metadata element, the name parts of an FB2 author, the navigation link
text, the NCX label, the page title, the image's `alt` value, the frontmatter value — and SHALL leave every auxiliary
slot without target text as it was.

**Source**: FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: producing the segments is half the job; the translated title must land in the title, and the translated image description must land on that image. An image inside a paragraph travels as a protected token with the paragraph, so the rule is stated for that case explicitly — otherwise restoring the paragraph could put the untranslated description back, or a paragraph still waiting for its translation could keep the old description. Two identical images in one paragraph cannot be told apart by their markup, so they are matched by order. An FB2 author is written back part by part, so the first name stays a first name and the author's id and address are untouched. A Markdown description sits between brackets, so a bracket or backslash in the translation is escaped to keep the image an image.

#### Scenario: A translated title lands in the metadata

- **WHEN** an EPUB whose `dc:title` is `Frankenstein` is written with the target `Франкенштейн` for its title segment
- **THEN** the output OPF declares `<dc:title>Франкенштейн</dc:title>`

#### Scenario: An untranslated slot stays in the source language

- **WHEN** an FB2 is written with target text for its `book-title` segment only
- **THEN** its `author` and `annotation` are unchanged in the output

#### Scenario: A frontmatter value is replaced inside the frontmatter

- **WHEN** a Markdown file opening with `---\ntitle: The Book\n---\n` is written with the target `Книга` for its
  frontmatter value
- **THEN** the output opens with `---\ntitle: Книга\n---\n` and its key `title` is unchanged

### Requirement: Give an image in a body segment its translated alternative text

WHERE an image sits inside a body segment and also has an alternative-text segment, the written image SHALL carry the
translated alternative text whether or not the body segment was translated. WHERE one body segment holds several
images with identical markup, the k-th such image in the written translation SHALL carry the k-th image's alternative
text.

**Source**: FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: an image inside a paragraph travels as a protected token with the paragraph, so restoring the paragraph could put the untranslated description back, or a paragraph still waiting for its translation could keep the old description; two identical images cannot be told apart by their markup, so they are matched by order.

#### Scenario: An image inside a translated paragraph keeps its translated description

- **WHEN** the paragraph `<p>Before <img src="fig1.png" alt="Figure 1"/> after</p>` is written with the body target
  `До ⟦g0⟧ після` and the alternative-text target `Рисунок 1`
- **THEN** the output paragraph is `<p>До <img src="fig1.png" alt="Рисунок 1"/> після</p>`

#### Scenario: An image inside an untranslated paragraph still takes its translated description

- **WHEN** the paragraph `<p>Before <img src="fig1.png" alt="Figure 1"/> after</p>` has no body target and is written
  with the alternative-text target `Рисунок 1`
- **THEN** the output paragraph is `<p>Before <img src="fig1.png" alt="Рисунок 1"/> after</p>`

#### Scenario: Identical images take their descriptions in order

- **WHEN** the paragraph `<p><img src="dot.png" alt="Dot"/> and <img src="dot.png" alt="Dot"/></p>` is written with
  the body target `⟦g0⟧ і ⟦g1⟧`, the first image's alternative-text target `Перша точка` and the second's
  `Друга точка`
- **THEN** the output paragraph is `<p><img src="dot.png" alt="Перша точка"/> і <img src="dot.png"
  alt="Друга точка"/></p>`

### Requirement: Write an FB2 author back part by part

WHEN an FB2 author's segment is written, each name part SHALL receive the text its own placeholder pair encloses in
the translation, and the author's other elements SHALL be written unchanged.

**Source**: FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`. In plain words: an FB2 author is written back part by part, so the first name stays a first name and the author's id and address are untouched.

#### Scenario: An FB2 author's name parts are written in place

- **WHEN** the FB2 author `<first-name>Leo</first-name> <last-name>Tolstoy</last-name><id>a1b2</id>` is written with
  the target `⟦g0⟧Лев⟦g1⟧ ⟦g2⟧Толстой⟦g3⟧`
- **THEN** the output author holds `<first-name>Лев</first-name>` and `<last-name>Толстой</last-name>`
- **AND** it still holds `<id>a1b2</id>`

### Requirement: Escape the brackets of a Markdown image description

WHEN a Markdown image's alternative text is written, the system SHALL escape each `\`, `[` and `]` in it with a
backslash.

**Source**: FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: a Markdown description sits between brackets, so a bracket or backslash in the translation is escaped to keep the image an image.

#### Scenario: A Markdown image description escapes its brackets

- **WHEN** a Markdown paragraph holding `![Figure 1](fig1.png)` is written with the alternative-text target
  `Рисунок [1]`
- **THEN** the output holds `![Рисунок \[1\]](fig1.png)`

### Requirement: Restore an image's alternative text and a frontmatter value as plain text

WHEN an image's alternative-text segment or a Markdown frontmatter-value segment is restored, the system SHALL return
its target as plain text — every placeholder substituted back, with no markup escaping, no Markdown escaping and no
Markdown structure check — so that the writer encodes it exactly once for its slot.

**Source**: FR-DOC-11, FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0031 (`docs/adr/ADR-0031-masked-text-is-character-data.md`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: the restore step was built for paragraph content, which it escapes as markup or as Markdown. An image description and a frontmatter value are neither: escaping `&` as markup and then again when the writer puts it into the attribute printed `&amp;amp;` on the image, and escaping a frontmatter value as Markdown left backslashes a YAML reader keeps. So these two kinds come back as plain text and are encoded once, where they are written. A title, an author, a navigation label or a description is written as element text or markup, so it restores exactly as a paragraph does.

#### Scenario: An ampersand in a description inside a translated paragraph is escaped once

- **WHEN** the image `<img src="fig1.png" alt="Figure 1"/>` inside a paragraph with the body target `До ⟦g0⟧ після`
  receives the alternative-text target `Том & Джеррі`
- **THEN** the written image carries `alt="Том &amp; Джеррі"`
- **AND** the output contains no `&amp;amp;`

#### Scenario: An ampersand in a description inside an untranslated paragraph is escaped once

- **WHEN** the same image's paragraph has no body target and the image receives the alternative-text target
  `Том & Джеррі`
- **THEN** the written image carries `alt="Том &amp; Джеррі"`

#### Scenario: A Markdown image description is encoded once between its brackets

- **WHEN** the Markdown image `![Figure 1](fig1.png)` receives the alternative-text target `Рисунок [1]`
- **THEN** restoring returns the plain text `Рисунок [1]` and the writer encodes it once, as
  `![Рисунок \[1\]](fig1.png)`

#### Scenario: A frontmatter value gains no Markdown backslashes

- **WHEN** a Markdown file whose frontmatter holds `title: The Book` is written with the target
  `Том & Джеррі *назавжди*` for that value
- **THEN** the output's frontmatter holds `title: Том & Джеррі *назавжди*`, with no backslash

### Requirement: Restore every other auxiliary segment as a body segment

The system SHALL restore every auxiliary segment other than an image's alternative text and a frontmatter value as a
body segment of its format is restored.

**Source**: FR-DOC-11, FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: a title, an author, a navigation label or a description is written as element text or markup, so it restores exactly as a paragraph does.

#### Scenario: A page title is restored as a body text is

- **WHEN** the `<head><title>` segment `Chapter 1` of an XHTML content document receives the target `Розділ 1 & 2`
- **THEN** the written document's head holds `<title>Розділ 1 &amp; 2</title>`

### Requirement: Render identical navigation labels identically in the navigation document and the NCX

WHERE an EPUB carries both a navigation document outside the spine and an NCX, an NCX label whose source text is
identical to the source text of a navigation-label segment SHALL NOT be a segment of its own, and SHALL be written with
that navigation-label segment's target text.

**Source**: FR-DOC-EPUB-8 (`01_Product/03_DOCUMENT_FORMATS.md#epub`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`. In plain words: many EPUB 3 books ship a legacy NCX next to the navigation document, and different reading systems show one or the other. If the same chapter name were translated two different ways, the table of contents would change depending on the device. Writing both from one segment makes them agree by construction, costs one model call instead of two, and carries a reviewer's edit to both places.

#### Scenario: One label, one rendering

- **WHEN** an EPUB whose navigation document and NCX both label a chapter `The Storm` is translated to Ukrainian and
  exported
- **THEN** the navigation document's entry and the NCX's label for that chapter carry exactly the same target text
- **AND** the book's auxiliary unit holds one navigation-label segment for `The Storm`, not two

#### Scenario: A reviewer's edit reaches both labels

- **WHEN** the navigation-label segment `The Storm` was translated `Буря`, the person saved the edit `Шторм`, and the
  book is exported
- **THEN** both the navigation document's entry and the NCX's label read `Шторм`

### Requirement: Change no attribute value outside the translatable list

WHEN a book is written, the system SHALL change no attribute value except the alternative-text values of images and
the language attributes the target-language requirement names; every other attribute — `title`, `href`, `src`, `id`,
`class`, data attributes — SHALL be written exactly as it was read.

**Source**: FR-DOC-02, FR-DOC-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: attribute values usually carry identifiers, links and file names, and changing one breaks a cross-reference or an image. The exception for alternative text is closed on purpose, so it cannot grow into "translate any attribute that looks like prose".

#### Scenario: A title attribute is never translated

- **WHEN** a book containing `<a href="#n1" title="Footnote one">1</a>` is fully translated and exported
- **THEN** the output still carries `href="#n1"` and `title="Footnote one"`

### Requirement: Segment only the text values of Markdown frontmatter, never its keys

WHERE a Markdown file opens with a `---`-delimited frontmatter block, the system SHALL produce an auxiliary segment for
a value only when its key is neither `lang` nor `language`, and SHALL produce no segment for any key.

**Source**: FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), EC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), FR-DOC-10 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, the owner's frontmatter decision recorded in `openspec/changes/archive/2026-10-19-complete-translation-workflow/design.md` (D12). In plain words: frontmatter keys are machine-readable field names and translating one breaks whatever reads the file. Values of other types — dates, flags, counts, addresses, lists — are data a site generator or a reader parses: translating `true` as `так` or rewriting a date as prose breaks the file just as surely. `lang` is the book's own language declaration, replaced by the target on export rather than translated. What is left is text a person may want translated, and the Book Brief's "Frontmatter values" switch, off by default, decides whether the run does. Only the start of the file can open a frontmatter block, because `---` elsewhere is an ordinary thematic break, and real documents use it that way.

#### Scenario: Keys and the language declaration are never segments

- **WHEN** a Markdown file whose frontmatter holds `title: The Book` and `language: en` is opened
- **THEN** its auxiliary unit holds exactly one segment, `The Book`
- **AND** no segment holds `title`, `language` or `en`

### Requirement: Segment only single-line frontmatter values

The system SHALL produce an auxiliary segment for a frontmatter value only when it is a single-line value on a
top-level `key: value` line — not on an indented line, not a list item, not a flow collection opening with `[` or `{`,
not a block scalar opening with `|` or `>`, and not an anchor, alias or tag opening with `&`, `*` or `!`.

**Source**: FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), EC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), the owner's frontmatter decision recorded in `openspec/changes/archive/2026-10-19-complete-translation-workflow/design.md` (D12). In plain words: lists, collections and block scalars are structure a YAML reader parses, so only a plain one-line value is text.

#### Scenario: Structured values are never segments

- **WHEN** a Markdown file whose frontmatter holds the lines `tags: [war, peace]`, `summary: |` and the indented line
  `  A long story.` is opened
- **THEN** its auxiliary unit holds no segment

### Requirement: Skip frontmatter values YAML reads as another type

The system SHALL produce no auxiliary segment for a frontmatter value that, written without quotes, YAML reads as
another type: a null (`~`, `null` in any letter case, or nothing), a boolean (`true`, `false`, `yes`, `no`, `on`,
`off`, `y` or `n`, in any letter case), a number (hexadecimal, octal, `.inf` and `.nan` included), or an ISO date or
date-time.

**Source**: FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), EC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), the owner's frontmatter decision recorded in `openspec/changes/archive/2026-10-19-complete-translation-workflow/design.md` (D12). In plain words: translating `true` as `так` or rewriting a date as prose breaks the file just as surely as translating a key.

#### Scenario: Values of another type are never segments

- **WHEN** a Markdown file whose frontmatter holds `pages: 1225`, `published: yes` and `date: 2024-05-01` is opened
- **THEN** its auxiliary unit holds no segment

### Requirement: Skip web addresses, e-mail addresses and values without a letter

The system SHALL produce no auxiliary segment for a frontmatter value that is a web or e-mail address (`scheme://…`,
`www.…`, `mailto:…`, `name@host.domain`) or that holds no letter once its surrounding quotes, or, when it has none, a
trailing `# comment`, are set aside.

**Source**: FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), EC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), the owner's frontmatter decision recorded in `openspec/changes/archive/2026-10-19-complete-translation-workflow/design.md` (D12). In plain words: an address is not prose and a value with no letter has nothing to translate; a trailing comment is not part of the value.

#### Scenario: An address is never a segment

- **WHEN** a Markdown file whose frontmatter holds `homepage: https://example.com` is opened
- **THEN** its auxiliary unit holds no segment

#### Scenario: A quoted value is text only when it holds a letter

- **WHEN** a Markdown file whose frontmatter holds `subtitle: "Part 2"` and `edition: "2"` is opened
- **THEN** its auxiliary unit holds a segment `Part 2` and no segment holding `2` alone

#### Scenario: A trailing comment is not part of the value

- **WHEN** a Markdown file whose frontmatter holds `title: The Book # working title` is opened
- **THEN** its frontmatter segment holds `The Book`

### Requirement: Carry the frontmatter block through byte-for-byte

The system SHALL carry the frontmatter block through byte-for-byte when no value receives target text, apart from the
`lang` value that *Declare no language metadata in formats that carry none* replaces. A `---` line that is not at the
start of the file SHALL NOT be treated as frontmatter.

**Source**: FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), EC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), the owner's frontmatter decision recorded in `openspec/changes/archive/2026-10-19-complete-translation-workflow/design.md` (D12). In plain words: only the start of the file can open a frontmatter block, because `---` elsewhere is an ordinary thematic break, and real documents use it that way.

#### Scenario: A frontmatter block survives byte-for-byte when untranslated

- **WHEN** a Markdown file opening with `---\ntitle: The Book\nlang: en\n---\n` followed by `Prose.` is parsed and
  reassembled with zero segment edits and target language `uk`
- **THEN** exactly one body segment is produced, for `Prose.`
- **AND** the output's first, second and fourth lines are byte-identical to the source's, and its third line reads
  `lang: uk`

#### Scenario: A thematic break later in the file is not frontmatter

- **WHEN** a Markdown file begins with `# Title` and contains a `---` line between two paragraphs
- **THEN** no frontmatter is recognised and no auxiliary segment is produced
- **AND** the two paragraphs each yield a segment

### Requirement: Quote a translated frontmatter value so it still reads as text

WHEN a frontmatter value's translation is written, the system SHALL keep the value's original quotes, writing `"` as
`\"` inside double quotes and `'` as `''` inside single quotes.

**Source**: FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), the owner's frontmatter decision recorded in `openspec/changes/archive/2026-10-19-complete-translation-workflow/design.md` (D12). In plain words: the translation goes back into YAML, and YAML reads punctuation as structure. Unquoted, `Книга: Частина 1` is a nested mapping to a YAML reader and a book translated as `1984` becomes a number, so such a translation is quoted to stay the text it is. A value that was quoted keeps its quotes, with the quote character escaped the way that style of quote requires.

#### Scenario: Double quotes are kept and escaped

- **WHEN** the frontmatter line `title: "The Book"` is written with the target `Книга "Перша"`
- **THEN** the output line reads `title: "Книга \"Перша\""`

#### Scenario: Single quotes are kept and doubled

- **WHEN** the frontmatter line `title: 'The Book'` is written with the target `Книга 'Перша'`
- **THEN** the output line reads `title: 'Книга ''Перша'''`

### Requirement: Wrap an unquoted frontmatter translation in double quotes when YAML would misread it

WHERE a frontmatter value had no quotes, the system SHALL wrap its translation in double quotes when it contains `: `
or ` #`, begins with one of `-`, `?`, `:`, `,`, `[`, `]`, `{`, `}`, `#`, `&`, `*`, `!`, `|`, `>`, `'`, `"`, `%`, `@` or
a backtick, or would itself read as a null, a boolean, a number or a date.

**Source**: FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), the owner's frontmatter decision recorded in `openspec/changes/archive/2026-10-19-complete-translation-workflow/design.md` (D12). In plain words: unquoted, `Книга: Частина 1` is a nested mapping to a YAML reader and a book translated as `1984` becomes a number, so such a translation is quoted to stay the text it is.

#### Scenario: A colon in the translation is quoted

- **WHEN** the frontmatter line `title: The Book` is written with the target `Книга: Частина 1`
- **THEN** the output line reads `title: "Книга: Частина 1"`

#### Scenario: A translation that reads as a number is quoted

- **WHEN** the frontmatter line `title: Nineteen Eighty-Four` is written with the target `1984`
- **THEN** the output line reads `title: "1984"`

### Requirement: Keep the zero-edit round trip canonical-equal with auxiliary text present

The system's per-format golden round-trip tests SHALL show that a book whose auxiliary text is produced as segments —
metadata, navigation document, NCX, page titles, alternative text, frontmatter values — and reassembled with zero
segment edits is still canonical-equal to its source, apart from the intentional target-language metadata.

**Source**: FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`, `02_Architecture/03_DOCUMENT_MODEL.md#golden-round-trip-test`, ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`). In plain words: reading more slots must not make writing less faithful. The same golden test that proved "we preserve the book" keeps proving it after the navigation document stops being copied byte for byte and starts being parsed and written. For Markdown the intentional language metadata is the frontmatter's `lang` value.

#### Scenario: An EPUB with nav, NCX and alt text still round-trips

- **WHEN** a fixture EPUB carrying a navigation document, an NCX, page titles and images with `alt` values is parsed
  and reassembled with no segment receiving target text
- **THEN** every text entry, the navigation document and the NCX included, re-parses to the same canonical form as
  the source entry

#### Scenario: A Markdown file with frontmatter and image alt text still round-trips

- **WHEN** a fixture Markdown file whose frontmatter holds `title: The Book` and `lang: en`, with an image
  `![Figure 1](fig1.png)`, is reassembled with zero edits and target language `uk`
- **THEN** the output's bytes are identical to the source's except that `lang: en` reads `lang: uk`

### Requirement: Change only the auxiliary slots when only auxiliary segments are edited

The system's per-format golden tests SHALL show that when only auxiliary segments receive target text, the written
book differs from its source only in those slots' values and the intentional target-language metadata.

**Source**: FR-DOC-09, FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`), `02_Architecture/03_DOCUMENT_MODEL.md#golden-round-trip-test`. In plain words: the zero-edit test proves nothing is broken when nothing is written; this second golden proves that writing the new slots touches those slots and nothing next to them — no neighbouring attribute, no link, no element.

#### Scenario: Only the edited slots differ

- **WHEN** a fixture EPUB is written with targets for its title, one navigation label whose NCX label reads the same,
  and one image's alternative text, and for no body segment
- **THEN** a canonical comparison against the source reports differences in exactly four values — the title, the
  navigation label, the NCX label and the alternative text — and in the language metadata, and nowhere else

### Requirement: Inspect a book before opening it

WHEN a file is inspected, the system SHALL answer with a verdict of **readable**, **DRM-protected** or
**unsupported**, together with the format and its version where known (`EPUB 2.0`, `EPUB 3.0`, `FB2`, `Markdown`,
`TXT`) and the language evidence the book's metadata gives.

**Source**: FR-IMPORT-04, FR-IMPORT-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-DRM-1 (`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), `02_Architecture/09_ERROR_HANDLING.md#error-code`, ADR-0039 (`docs/adr/ADR-0039-import-refusal-kind-is-data.md`). In plain words: the import screen has to tell "this book is protected" apart from "this is a PDF", and both used to arrive as the same validation error told apart only by message text. Inspection answers that question as data. The error vocabulary stays at its fixed set, and every caller that opens books — the command line first — sees exactly what it saw before. A zipped FB2 has no encryption manifest, so the zip's own encryption flag is the scheme it names.

#### Scenario: A readable EPUB reports its version

- **WHEN** `Frankenstein.epub`, whose package declares `version="2.0"`, is inspected
- **THEN** the verdict is readable, with the format `EPUB 2.0`

#### Scenario: Font obfuscation does not make a book DRM-protected

- **WHEN** an EPUB whose only encrypted entries are fonts under the IDPF font-obfuscation algorithm is inspected
- **THEN** the verdict is readable

### Requirement: Name the encryption scheme and the detected type of a refusing verdict

A DRM-protected verdict SHALL name the encryption scheme where the book's encryption manifest identifies it (for
example `Adobe ADEPT`), or `ZIP encryption` for a zipped FB2 whose entry is encrypted; an unsupported verdict SHALL
name the detected file type judged from the file's name and its leading bytes (`PDF`, `DOCX`, `MOBI`, or `Unknown`).

**Source**: FR-IMPORT-04, FR-IMPORT-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-DRM-1 (`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0039 (`docs/adr/ADR-0039-import-refusal-kind-is-data.md`). In plain words: a zipped FB2 has no encryption manifest, so the zip's own encryption flag is the scheme it names; a refused file is named by what it is, not just refused.

#### Scenario: An Adobe-protected EPUB is named

- **WHEN** `Purchased_Novel.epub`, whose encryption manifest encrypts its content documents under the Adobe ADEPT
  scheme, is inspected
- **THEN** the verdict is DRM-protected with the scheme `Adobe ADEPT`
- **AND** opening the same file fails with `ErrorCode.validation`

#### Scenario: A PDF is recognized by its bytes

- **WHEN** `book.pdf`, whose bytes begin with `%PDF-`, is inspected
- **THEN** the verdict is unsupported with the detected type `PDF`
- **AND** opening the same file fails with `ErrorCode.validation`

#### Scenario: An unrecognizable file is unsupported and unknown

- **WHEN** `mystery.bin`, holding 2,048 random bytes, is inspected
- **THEN** the verdict is unsupported with the detected type `Unknown`

#### Scenario: A password-protected zipped FB2 names its zip encryption

- **WHEN** `Kobzar.fb2.zip`, whose `Kobzar.fb2` entry carries the zip encryption flag, is inspected
- **THEN** the verdict is DRM-protected with the scheme `ZIP encryption`
- **AND** opening the same file fails with `ErrorCode.validation`

### Requirement: Answer a refusing verdict as data, not as a failure

A refusing verdict SHALL be a normal answer, not a failure. Inspecting SHALL NOT change what opening a book returns: a
DRM-protected or unsupported file SHALL still fail to open with `ErrorCode.validation`.

**Source**: FR-IMPORT-04, FR-IMPORT-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), `02_Architecture/09_ERROR_HANDLING.md#error-code`, ADR-0039 (`docs/adr/ADR-0039-import-refusal-kind-is-data.md`). In plain words: the error vocabulary stays at its fixed set, and every caller that opens books — the command line first — sees exactly what it saw before.

#### Scenario: Inspecting is an answer and opening still fails

- **WHEN** `Purchased_Novel.epub` is inspected and then opened
- **THEN** inspecting returns the DRM-protected verdict as a normal answer, with no error
- **AND** opening it fails with `ErrorCode.validation`

### Requirement: Report the language evidence a book's metadata gives

WHEN a book is inspected, the system SHALL report its language evidence: the declared language code as written; that
code normalized, or none where it names no language the application can name; and, for an EPUB, the language declared
by more than half of the content documents that declare one on their root element, or none where no language has such
a majority.

**Source**: FR-IMPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-LANG-1 (`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`). In plain words: this is everything the book says about its own language, weighed without guessing. The import screen turns "mismatch" and "unrecognized" into warnings and the Book Brief turns the evidence into a preselected source. Reading the text itself is deliberately out of scope: it would need a large detection library, and the person can correct the source on the brief.

#### Scenario: An FB2 declaration has no content majority

- **WHEN** an FB2 whose title information declares `<lang>ua</lang>` is inspected
- **THEN** the evidence reports declared `ua`, normalized `uk`, no content majority and the verdict match

### Requirement: Give a match or mismatch verdict for a recognized declaration

The system SHALL give the language evidence the verdict **match** for a recognized declaration with no content
majority or a content majority equal to it, and the verdict **mismatch** for a recognized declaration and a content
majority that differs from it.

**Source**: FR-IMPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-LANG-1 (`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`). In plain words: a match means the book says nothing against its own declaration; a mismatch means the chapters contradict the package.

#### Scenario: Chapters disagreeing with the package is a mismatch

- **WHEN** an EPUB whose package declares `en` and whose 24 content documents all declare `xml:lang="uk"` is
  inspected
- **THEN** the evidence reports declared `en`, content majority `uk` and the verdict mismatch

#### Scenario: A language outside the list is recognized

- **WHEN** a Markdown file whose frontmatter declares `lang: la` is inspected
- **THEN** the normalized code is `la` and the verdict is match

### Requirement: Give an unrecognized or absent verdict

The system SHALL give the language evidence the verdict **unrecognized** for a declaration that names no language the
application can name, such as `xx-yy`, whether or not the Book Brief lists the language it does name, and the verdict
**absent** where there is no declaration.

**Source**: FR-IMPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-LANG-1 (`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`). In plain words: a code nobody can name and a book that says nothing are different cases, and the import screen treats them differently.

#### Scenario: An unknown code is unrecognized

- **WHEN** a Markdown file whose frontmatter declares `lang: xx-yy` is inspected
- **THEN** the verdict is unrecognized

#### Scenario: Plain text declares nothing

- **WHEN** `notes.txt` is inspected
- **THEN** the verdict is absent and no content majority is reported

### Requirement: Compare language declarations after normalization and never infer from text

The system SHALL compare declarations after normalization, and SHALL NOT infer a language from the book's text.

**Source**: FR-IMPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-LANG-1 (`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`). In plain words: reading the text itself is deliberately out of scope because it would need a large detection library, and the person can correct the source on the brief.

#### Scenario: A regional spelling of the same language is a match

- **WHEN** an EPUB whose package declares `en-US` and whose content documents declare `lang="en"` is inspected
- **THEN** the verdict is match

#### Scenario: The text is never read for a language

- **WHEN** `notes.txt`, whose text is English prose and which declares no language, is inspected
- **THEN** the verdict is absent and no language is derived from the text

### Requirement: Record the language a block declares on its segment

WHEN an EPUB content document or an FB2 document is parsed, the system SHALL record on each segment the value, as
written, of the `xml:lang` or `lang` attribute of the nearest element that encloses the segment's text and lies below
the document's root element — the segment's own block included — or no language where none of those elements
declares one.

**Source**: FR-IMPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-FOREIGN-1, EC-FOREIGN-2 (`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`); the quality-gates requirement "Skip the echo and script checks for a marked foreign passage kept by policy" (`openspec/changes/archive/2026-10-19-complete-translation-workflow/specs/quality-gates/spec.md`) is what reads it. In plain words: whether a passage is foreign depends on the language the run translates from, which the person settles on the Book Brief after opening the book — so parsing only records what the book says about each block, and the run makes the comparison. Comparing at parse time against the package's language marked every paragraph of a Ukrainian book whose template said English as foreign. The root element is left out because its declaration describes the whole document, not a passage in it; the import's language evidence already weighs it, and treating it as a passage marking would keep a whole chapter untranslated whenever it disagreed with the brief.

#### Scenario: A block's own declaration is recorded

- **WHEN** an EPUB content document holding `<p xml:lang="fr">Bonjour, mon ami.</p>` is parsed
- **THEN** that paragraph's segment records the declared language `fr`

#### Scenario: A declaration on the body reaches every paragraph under it

- **WHEN** an EPUB whose package declares `en` has a content document whose `<body xml:lang="uk">` holds
  `<p>Привіт.</p>`
- **THEN** that paragraph's segment records the declared language `uk`

#### Scenario: The root element's declaration is not a block's

- **WHEN** an EPUB content document whose only language declaration is `<html xml:lang="uk">` holds `<p>Привіт.</p>`
- **THEN** that paragraph's segment records no declared language

#### Scenario: A format without elements records none

- **WHEN** a Markdown file and a TXT file are parsed
- **THEN** no segment of either records a declared language

### Requirement: Mark no segment foreign while parsing

The system SHALL NOT, while parsing, compare a segment's declared language with any other language or mark a segment
as foreign.

**Source**: FR-IMPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-FOREIGN-1, EC-FOREIGN-2 (`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`). In plain words: whether a passage is foreign depends on the language the run translates from, which the person settles on the Book Brief after opening the book, so the run makes the comparison, not the parser.

#### Scenario: Parsing marks no segment as foreign

- **WHEN** an EPUB whose package declares `en` has a content document whose `<body xml:lang="uk">` holds
  `<p>Привіт.</p>` and is parsed
- **THEN** parsing marks no segment of the book as foreign

### Requirement: Record the language an inline element declares on its placeholder pair

WHEN an inline element of a segment is masked as a placeholder pair, the system SHALL record on that pair the value,
as written, of that element's own `xml:lang` or `lang` attribute, or no language where the element carries neither.

**Source**: FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-FOREIGN-3 (`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), `02_Architecture/03_DOCUMENT_MODEL.md#inline-masking`, ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`), ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: under the Book Brief's Keep policy for foreign passages, a run keeps an inline passage the book itself marks as another language exactly as written, while the prose around it is translated; the policy and the protection belong to the pipeline. Recording each pair's own marking when the segment is masked is what lets the run find those passages from the book's metadata alone, without guessing from the text.

#### Scenario: A marked inline passage records its language on its pair

- **WHEN** an EPUB paragraph whose content is `He said <span xml:lang="fr">bonjour</span> and left.` is parsed
- **THEN** the segment's masked form is `He said ⟦g0⟧bonjour⟦g1⟧ and left.`
- **AND** the pair `⟦g0⟧`…`⟦g1⟧` records the language `fr`

#### Scenario: An unmarked pair records none

- **WHEN** an EPUB paragraph whose content is `He opened the <em>old</em> door.` is parsed
- **THEN** the pair `⟦g0⟧`…`⟦g1⟧` records no language

### Requirement: Extract the book's cover image

WHEN a book's profile is built, the system SHALL find its cover image by the first rule that applies for its format.

**Source**: FR-IMPORT-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), `01_Product/03_DOCUMENT_FORMATS.md#epub`, `01_Product/03_DOCUMENT_FORMATS.md#fb2`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: the cover is how a person recognises a book at a glance, and books mark it in several historical ways: EPUB 3 has a proper property, EPUB 2 used a `meta` element that tools filled with an id or, wrongly but commonly, with a file path, and the oldest books only point at a cover page. A book whose cover marking is broken still opens — it just shows the placeholder.

#### Scenario: An EPUB 3 cover property wins

- **WHEN** an EPUB 3 whose manifest marks `images/cover.jpg` with `properties="cover-image"` is profiled
- **THEN** the cover is `images/cover.jpg`

### Requirement: Find an EPUB's cover image

For an EPUB the system SHALL take the manifest item whose properties include `cover-image`; else the item named by
`<meta name="cover">`, whose value is read as a manifest id or, failing that, as a manifest href; else the first image
in the document the guide references with `type="cover"`.

**Source**: FR-IMPORT-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), `01_Product/03_DOCUMENT_FORMATS.md#epub`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: EPUB 3 has a proper property, EPUB 2 used a `meta` element that tools filled with an id or, wrongly but commonly, with a file path, and the oldest books only point at a cover page.

#### Scenario: An EPUB 2 cover meta holding an id

- **WHEN** an EPUB 2 carrying `<meta name="cover" content="cover-img"/>` and a manifest item `id="cover-img"`
  `href="cover.png"` is profiled
- **THEN** the cover is `cover.png`

#### Scenario: An EPUB 2 cover meta holding a path

- **WHEN** an EPUB 2 carrying `<meta name="cover" content="images/cover.png"/>` and a manifest item whose href is
  `images/cover.png` is profiled
- **THEN** the cover is `images/cover.png`

#### Scenario: The guide's cover page supplies the image

- **WHEN** an EPUB with no cover property and no cover meta whose guide references `cover.xhtml` with `type="cover"`,
  and `cover.xhtml` contains `<img src="art/front.jpg"/>`, is profiled
- **THEN** the cover is `art/front.jpg`

### Requirement: Find an FB2's cover image

For an FB2 the system SHALL take the binary the title information's `coverpage` image references; for Markdown and
plain text it SHALL find no cover.

**Source**: FR-IMPORT-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), `01_Product/03_DOCUMENT_FORMATS.md#fb2`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: an FB2 names its cover page image in the title information, while Markdown and plain text have nowhere to name one.

#### Scenario: An FB2 coverpage references its binary

- **WHEN** an FB2 whose title information holds `<coverpage><image l:href="#cover.jpg"/></coverpage>` and a
  `<binary id="cover.jpg">` is profiled
- **THEN** the cover is that binary's image

#### Scenario: Plain text has no cover

- **WHEN** `notes.txt` is profiled
- **THEN** the profile reports no cover

### Requirement: Try the next cover rule when a reference dangles

WHERE a cover rule names a resource that does not exist, the system SHALL try the next rule, and SHALL report no cover
rather than a failure when none applies.

**Source**: FR-IMPORT-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), `01_Product/03_DOCUMENT_FORMATS.md#epub`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: a book whose cover marking is broken still opens — it just shows the placeholder.

#### Scenario: A dangling cover reference yields no cover and no error

- **WHEN** an EPUB whose only cover marking is `<meta name="cover" content="missing-id"/>`, matching no manifest item,
  is profiled
- **THEN** the profile reports no cover and the book opens normally

### Requirement: Derive the book's structure tree from its own navigation

WHEN a book's profile is built, the system SHALL build its structure tree from the book's own navigation, counting
every body segment in exactly one node, and each node's count SHALL be the number of body segments that fall under it,
its descendants included, so that the counts of the top-level nodes add up to the book's body segment count.

**Source**: FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#epub`, `01_Product/03_DOCUMENT_FORMATS.md#verse-tables-notes`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`. In plain words: a person recognises their book by its chapter names, not by file paths, so the tree is titled with the names the book itself gives. Books often point several table-of-contents entries into one file, which is why each file is counted once — otherwise the chapter numbers would not add up to the book's total. Every segment has to land in some node for the same reason: text before the first Markdown heading, an FB2 epigraph before the first section and the FB2 notes each used to fall outside every node. A spine document the navigation leaves out is titled by its file name rather than a bare number, because the name is something a person can find in the book. The top-level nodes are what the import card counts as chapters (`book-import`).

#### Scenario: EPUB navigation titles the tree

- **WHEN** an EPUB whose navigation document lists `Chapter 1` → `c01.xhtml` and `Chapter 2` → `c02.xhtml`, holding
  66 and 58 segments, is profiled
- **THEN** the tree's nodes are `Chapter 1` with count `66` and `Chapter 2` with count `58`

### Requirement: Map EPUB navigation entries onto spine documents

For an EPUB the system SHALL build the tree from the navigation document's table of contents, or the NCX where there
is none, with each entry mapped onto the spine document its link points into and nested entries shown as child nodes.

**Source**: FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#epub`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`. In plain words: the book's own table of contents names its chapters, so the tree is titled and nested the way the book nests them.

#### Scenario: An NCX is used when there is no navigation document

- **WHEN** an EPUB 2 whose NCX labels its first spine document `Preface` is profiled
- **THEN** the first node is titled `Preface`

### Requirement: Count an EPUB spine document once

Each EPUB spine document SHALL be counted once, under the first entry that points into it, and a later entry pointing
into an already counted document SHALL appear without a count of its own.

**Source**: FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#epub`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`. In plain words: books often point several table-of-contents entries into one file, which is why each file is counted once — otherwise the chapter numbers would not add up to the book's total.

#### Scenario: Entries into one file are counted once

- **WHEN** an EPUB whose navigation lists `Author's Introduction` → `intro.xhtml` with children `Letter 1` →
  `intro.xhtml#l1` and `Letter 2` → `intro.xhtml#l2`, and `intro.xhtml` holds 41 segments, is profiled
- **THEN** `Author's Introduction` has count `41` and its children `Letter 1` and `Letter 2` carry no count

### Requirement: Place an EPUB spine document no entry points into

An EPUB spine document no entry points into SHALL be a top-level node at its spine position, titled by the text of its
first heading segment or, where it has none, by its file name.

**Source**: FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#epub`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`. In plain words: a spine document the navigation leaves out is titled by its file name rather than a bare number, because the name is something a person can find in the book.

#### Scenario: A unit without an entry takes its first heading

- **WHEN** an EPUB's spine document `c07.xhtml` is not in its navigation and begins with `<h1>Interlude</h1>`
- **THEN** a node titled `Interlude` stands at that document's reading position

#### Scenario: A unit with neither entry nor heading takes its file name

- **WHEN** the fourth spine document of an EPUB, `OEBPS/c04.xhtml`, is not in its navigation and holds no heading
- **THEN** a top-level node titled `c04.xhtml` stands at the fourth reading position

### Requirement: Nest FB2 sections into the structure tree

For an FB2 the system SHALL build the tree from the nested sections of each body, titled by their `title`; content of
the main body that lies outside every section SHALL be an untitled top-level node ahead of the sections; the notes
body SHALL be a top-level node titled by its own `title` or, where it has none, by its `name` attribute.

**Source**: FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#verse-tables-notes`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`. In plain words: an FB2 epigraph before the first section and the notes body each used to fall outside every node; every segment has to land in some node.

#### Scenario: FB2 content outside sections and the notes body are counted

- **WHEN** an FB2 whose main body holds an `<epigraph>` of one paragraph ahead of its only section — titled
  `Chapter 1` and holding 4 paragraphs — and whose `<body name="notes">`, with no title, holds 2 note paragraphs, is
  profiled
- **THEN** the top-level nodes are an untitled node with count `1`, `Chapter 1` with count `5` and `notes` with count
  `2`
- **AND** their counts add up to the book's `8` body segments

### Requirement: Nest Markdown headings into the structure tree

For Markdown the system SHALL build the tree from the headings, nested by heading level, with text before the first
heading as an untitled top-level node ahead of them; for plain text the tree SHALL be the single unit.

**Source**: FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#markdown`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`. In plain words: text before the first Markdown heading used to fall outside every node; every segment has to land in some node.

#### Scenario: Markdown headings nest by level

- **WHEN** a Markdown file holding `# Part One`, `## Chapter 1` and `## Chapter 2` is profiled
- **THEN** `Chapter 1` and `Chapter 2` are children of `Part One`

#### Scenario: Markdown text before the first heading is counted

- **WHEN** a Markdown file holding the paragraph `Foreword text.`, then the heading `# Chapter 1` and the paragraph
  `Body.`, is profiled
- **THEN** the top-level nodes are an untitled node with count `1` and `Chapter 1` with count `2`
- **AND** their counts add up to the book's `3` body segments

#### Scenario: A plain-text book is one node

- **WHEN** `notes.txt` holding 5 segments is profiled
- **THEN** the tree is one node holding count `5`

### Requirement: Compute the book's statistics

WHEN a book's profile is built, the system SHALL compute the number of body segments, the number of words in the body
segments' visible text (placeholders excluded), the number of images the book carries, the number of code blocks
preserved untranslated, and the number of embedded fonts.

**Source**: FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#verse-tables-notes`, `01_Product/03_DOCUMENT_FORMATS.md#images-and-fonts`, `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`. In plain words: these numbers let a person judge the job before starting it — how long the book is, what will be carried untouched (images, fonts, code), and which delicate structures (verse, footnotes, tables) the translation has to respect.

#### Scenario: An EPUB's statistics are counted

- **WHEN** an EPUB with 1,240 body segments of 78,214 words, 7 image resources, no fonts and no code blocks is profiled
- **THEN** the statistics report `1240` segments, `78214` words, `7` images, `0` code blocks and `0` fonts

#### Scenario: Placeholders are not words

- **WHEN** a book's only body segment has the masked form `⟦g0⟧old⟦g1⟧ door`
- **THEN** the word count is `2`

#### Scenario: A technical Markdown book counts its code blocks

- **WHEN** a Markdown file with 3 fenced code blocks and 1 table is profiled
- **THEN** the statistics report `3` code blocks and `1` table

### Requirement: Count verse lines, footnotes and tables

WHEN a book's profile is built, the system SHALL also compute the number of verse-line segments, the number of
footnotes (FB2 notes sections, EPUB elements marked as footnotes or endnotes), and the number of tables.

**Source**: FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#verse-tables-notes`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`. In plain words: verse, footnotes and tables are the delicate structures a translation has to respect, so a person sees how many there are before starting.

#### Scenario: Verse lines and footnotes are counted

- **WHEN** an FB2 with 12 verse-line segments and a notes body of 5 note sections is profiled
- **THEN** the statistics report `12` verse lines and `5` footnotes

#### Scenario: A book without them reports zero

- **WHEN** an EPUB with no verse, no footnotes and no tables is profiled
- **THEN** the statistics report `0` verse lines, `0` footnotes and `0` tables

### Requirement: Report the inline formatting kinds that were protected

WHEN a book's profile is built, the system SHALL also report the kinds of inline formatting that were protected as
placeholders (italics, bold, links, quotes, code, line breaks, other markup).

**Source**: FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`. In plain words: a person sees which formatting the translation has to carry through untouched.

#### Scenario: The formatting kinds are listed

- **WHEN** an EPUB whose inline markup is only `<i>` and `<q>` is profiled
- **THEN** the statistics report the formatting kinds italics and quotes

### Requirement: Show the book's statistics beside its structure

The import screen SHALL show, beside the book's details, a facts card reading: `Translatable segments`, `Words` (prefixed
with `~`), `Images (kept as-is)`, `Code blocks` with the note `preserved — never translated`, `Embedded fonts`,
`Verse / poetry` (reading `none` when zero), `Footnotes · tables`, and `Inline formatting` listing the protected
kinds followed by `→ protected`.

**Source**: FR-DOC-01, FR-DOC-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: the import screen is the last look before the long part begins, so it shows what will be translated and what will be carried through untouched, in the words the reference drawing uses.

#### Scenario: The card reads the book's statistics

- **WHEN** the EPUB of 1,240 segments, 78,214 words, 7 images and italics and quotes is open on the import screen
- **THEN** the card reads `1,240`, `~78,000`, `7`, `0` with `preserved — never translated`, `0`, `none`, `0 · 0` and
  `italics, quotes → protected`

### Requirement: Show the round-trip and resource checks beside the structure

WHEN the import screen is shown for an open book, the system SHALL run a round-trip check in the background — the
source opened afresh and written with zero edits, then compared with the source — and SHALL show it as running until
it ends, then either `Round-trip check passed — structure & text preserved` or
`Round-trip check failed — structure not preserved` naming what differed, the two segment counts when they differ.

**Source**: FR-DOC-02, FR-DOC-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`, DD-43 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`). In plain words: the golden tests prove the writer on fixtures; this check proves it on the person's actual book, before hours of translation depend on it. It runs on a fresh copy so it cannot disturb the opened book, and in the background so the screen stays usable. The oversized warning explains in advance why some paragraphs will be handled in pieces. The checks inform rather than block, because a person may still want a partial result.

#### Scenario: A faithful book passes the round-trip check

- **WHEN** `Frankenstein.epub` is open and the import screen is shown
- **THEN** after the background check ends the screen shows `Round-trip check passed — structure & text preserved`

#### Scenario: A dropped paragraph fails the structure check

- **WHEN** the round-trip check's written copy of the open book re-opens with 1,239 body segments where the source has
  1,240
- **THEN** the screen shows `Round-trip check failed — structure not preserved` naming `1,239 of 1,240 segments`

### Requirement: Show whether image, font and link IDs survived the round trip

The import screen SHALL show `All image / font / link IDs preserved` when every image, font and link identifier of the
source is present in the round-trip check's written copy, and otherwise SHALL show `IDs missing after the round trip:`
followed by the missing identifiers.

**Source**: FR-DOC-02, FR-DOC-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`, DD-43 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`). In plain words: a book whose image or link identifiers vanish in the written copy would break its cross-references, so the person is told which ones before translating.

#### Scenario: All identifiers are preserved

- **WHEN** `Frankenstein.epub` is open and the background check ends
- **THEN** the screen shows `All image / font / link IDs preserved`

#### Scenario: A missing identifier is named

- **WHEN** the round-trip check's written copy of the open book lacks the image identifier `img-7` that the source
  carries
- **THEN** the screen shows `IDs missing after the round trip: img-7` in place of
  `All image / font / link IDs preserved`

### Requirement: Warn about segments over the chunk budget

WHERE any body segment is estimated at more tokens than the chunk budget of 1,200 tokens, the import screen SHALL show
a warning naming how many segments exceed it and saying they will be split by sentence and re-joined.

**Source**: FR-DOC-02, FR-DOC-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`. In plain words: the oversized warning explains in advance why some paragraphs will be handled in pieces.

#### Scenario: An oversized paragraph is warned about

- **WHEN** an open English book holds one paragraph of 6,000 characters, estimated at 1,725 tokens
- **THEN** the screen warns that 1 segment exceeds the chunk budget and will be split by sentence and re-joined

#### Scenario: No oversized segment, no warning

- **WHEN** every body segment of the open book is estimated below 1,200 tokens
- **THEN** no chunk-budget warning is shown

### Requirement: Never block Continue on a structure check

Neither the round-trip and identifier checks nor the chunk-budget warning SHALL block the import screen's `Continue`.

**Source**: FR-DOC-02, FR-DOC-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`. In plain words: the checks inform rather than block, because a person may still want a partial result, and they run in the background so the screen stays usable.

#### Scenario: The check does not hold the screen

- **WHEN** the round-trip check is still running
- **THEN** the tree and the statistics are shown and `Continue` is available

#### Scenario: A failed check does not block Continue

- **WHEN** the round-trip check has failed and the identifier `img-7` is reported missing
- **THEN** `Continue` is available

### Requirement: Keep the package document's line ends and declaration

WHEN an EPUB is written, the system SHALL write its package document, its NCX and its navigation document with
line-feed line ends only, SHALL write an XML declaration in each only when the source document had one, and SHALL keep
the encoding that declaration named.

**Source**: FR-DOC-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), `02_Architecture/03_DOCUMENT_MODEL.md#xml-round-trip-config`, `docs/next_features.md` §2. In plain words: the package document gained a declaration it never had and carriage returns it never had, and the golden comparison could not see it because it re-serialized both sides the same way. Nothing broke, but a file that changes where nothing was translated is exactly what "canonical-equal" is meant to rule out. The NCX and the navigation document are written back now that their labels are translated, so the same rule covers them.

#### Scenario: No declaration and no carriage return are added

- **WHEN** an EPUB whose package document has no XML declaration and only line-feed line ends is written with zero
  edits
- **THEN** the output package document has no XML declaration and contains no carriage-return byte

#### Scenario: An existing declaration is kept

- **WHEN** an EPUB whose package document begins `<?xml version="1.0" encoding="UTF-8"?>` is written
- **THEN** the output package document begins with a declaration naming `UTF-8`

#### Scenario: A rewritten NCX gains no declaration and no carriage return

- **WHEN** an EPUB whose NCX has no XML declaration and only line-feed line ends is written with its table-of-contents
  labels translated
- **THEN** the output NCX has no XML declaration and contains no carriage-return byte

### Requirement: Keep line-feed references in attribute values

WHEN an XHTML content document is written, the system SHALL write a line feed, carriage return or tab inside an
attribute value as a character reference, so that the value read back equals the source's value.

**Source**: FR-DOC-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), `docs/next_features.md` §3. In plain words: an XML reader turns a raw line feed in an attribute into a space, so writing `&#10;` as a raw line feed silently changes the value. One surveyed book loses the line break in a bookmark label this way; any `alt` or `title` holding one would too.

#### Scenario: A line-feed reference survives

- **WHEN** a content document whose element carries `data-pdf-bookmark="Communication: &#10;So Many Choices"` is
  written with zero edits
- **THEN** the output attribute is written with a line-feed character reference, and re-reading it yields a value
  containing a line feed, not a space

### Requirement: Keep an XHTML document's XML declaration as a declaration

WHEN an XHTML document — a content document or the navigation document — that begins with an XML declaration or a
DOCTYPE is written, the system SHALL write its prolog back verbatim ahead of the document, and SHALL NOT turn the
declaration into a comment.

**Source**: FR-DOC-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, `02_Architecture/03_DOCUMENT_MODEL.md#xml-round-trip-config`, `docs/next_features.md` §4. In plain words: the declaration was written out as `<!--?xml … ?-->`, a comment. For a UTF-8 chapter that is harmless; for a chapter in any other encoding the reader no longer learns the encoding and decodes it wrongly. The navigation document is written back now that its labels are translated, so its prolog is kept the same way.

#### Scenario: The declaration stays a declaration

- **WHEN** a content document beginning `<?xml version='1.0' encoding='utf-8'?>` is written with zero edits
- **THEN** the output entry begins `<?xml version='1.0' encoding='utf-8'?>` and contains no `<!--?xml`

#### Scenario: The DOCTYPE is kept as written

- **WHEN** a content document whose DOCTYPE spans two lines with single-quoted identifiers is written
- **THEN** the output entry carries that DOCTYPE with the same quotes and line break

#### Scenario: The navigation document keeps its prolog

- **WHEN** a navigation document beginning `<?xml version="1.0" encoding="UTF-8"?>` followed by `<!DOCTYPE html>` is
  written with its labels translated
- **THEN** the output entry begins with that declaration and that DOCTYPE, and contains no `<!--?xml`

### Requirement: Write an FB2 byte-order mark back

WHEN an FB2 book that began with a byte-order mark is written, the system SHALL begin the output with the same mark,
and SHALL add none to a book that had none.

**Source**: `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, FR-DOC-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/next_features.md` §5. In plain words: the encoding rules already say a byte-order mark is recorded and re-emitted exactly as found; plain text did that and FB2 dropped it. Some readers rely on the mark to choose the encoding.

#### Scenario: A mark is re-emitted

- **WHEN** `Bom.fb2`, beginning with the bytes `EF BB BF`, is written with zero edits
- **THEN** the output begins with the bytes `EF BB BF`

#### Scenario: No mark is invented

- **WHEN** `Forrest_Gump.fb2`, beginning with `<?xml`, is written
- **THEN** the output begins with `<?xml`

### Requirement: Protect a bare URL in Markdown prose

WHEN a Markdown block containing a bare web address in running text — one written without angle brackets or link
syntax, such as `https://example.com/path` or `www.example.com` — is segmented, the system SHALL mask that address as
a single atomic placeholder, so that it is written back unchanged.

**Source**: FR-DOC-MD-2 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-04, FR-DOC-10 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-MD-1 (`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), `docs/next_features.md` §10. In plain words: a URL is an address, not prose, and a model asked to translate a sentence will happily "translate" or re-case the address in it — the pseudo model turned `https://example.com/path` into `HTTPS://EXAMPLE.COM/PATH`. Masking it hands the model a token it must keep.

#### Scenario: A bare URL is one token

- **WHEN** a Markdown paragraph whose source is `See https://example.com/path` is parsed
- **THEN** the segment's masked form is `See ⟦g0⟧`

#### Scenario: The URL survives translation unchanged

- **WHEN** that paragraph is translated as `ДИВ. ⟦g0⟧` and written
- **THEN** the output line reads `ДИВ. https://example.com/path`

### Requirement: Split an oversized segment at sentence boundaries

WHEN a segment's masked text is to be split, the system SHALL divide it at the sentence boundaries of the source
language into pieces that, joined in order, reproduce the masked text exactly, whitespace included.

**Source**: FR-ALGO-C2, FR-ALGO-C2b (`01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`), `01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, `02_Architecture/05_PIPELINE_ENGINE.md#chunk-packing`, ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: a paragraph longer than the model's budget has to be translated in pieces, and the pieces must fit back together into exactly the original paragraph — any lost or doubled space would show in the book. Splitting by the source language's sentences keeps each piece meaningful. A piece must never hold half of a formatting pair, because each piece is checked on its own and half a pair can never pass the gate. Tokens next to a sentence end are the hard case: read as plain text, a token looks like the next sentence continuing, and the boundary disappears on both sides of it. So a sentence that ends inside emphasis ends after the closing token, and a sentence that starts with emphasis starts at the opening token. A paragraph that cannot be split safely is sent whole rather than cut badly; drafting the pieces and joining them is the pipeline's part (`translation-pipeline`).

#### Scenario: Three sentences become three pieces

- **WHEN** the English masked text `Call me Ishmael. Some years ago I went to sea. It was cold.` is split
- **THEN** the pieces are `Call me Ishmael. `, `Some years ago I went to sea. ` and `It was cold.`
- **AND** joining them gives back the original text exactly

#### Scenario: The source language's sentence rules are used

- **WHEN** the Ukrainian masked text `Він пішов. Вона лишилася.` is split with source language `uk`
- **THEN** the pieces are `Він пішов. ` and `Вона лишилася.`

### Requirement: Place a sentence boundary beside placeholder tokens

WHERE a sentence boundary lies next to placeholder tokens, the system SHALL place it after every closing or atomic
token that directly follows the end of the sentence, together with the whitespace after those tokens, and before an
opening token that begins the next sentence.

**Source**: FR-ALGO-C2, FR-ALGO-C2b (`01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`), `01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: read as plain text, a token looks like the next sentence continuing, and the boundary disappears on both sides of it; so a sentence that ends inside emphasis ends after the closing token, and a sentence that starts with emphasis starts at the opening token.

#### Scenario: A sentence that starts with inline markup is split before its opening token

- **WHEN** the masked text `He left. ⟦g0⟧She⟦g1⟧ stayed. Night fell.` is split, where `⟦g0⟧` and `⟦g1⟧` are one pair
- **THEN** the pieces are `He left. `, `⟦g0⟧She⟦g1⟧ stayed. ` and `Night fell.`
- **AND** joining them gives back the original text exactly

#### Scenario: An opening token right after a sentence end starts the next piece

- **WHEN** the masked text `He left.⟦g0⟧ She stayed.⟦g1⟧ Night fell.` is split, where `⟦g0⟧` and `⟦g1⟧` are one pair
- **THEN** the pieces are `He left.`, `⟦g0⟧ She stayed.⟦g1⟧ ` and `Night fell.`

### Requirement: Keep a placeholder pair inside one piece when splitting

The system SHALL NOT use a sentence boundary that falls between a paired placeholder's opening and closing tokens; such
a boundary SHALL be skipped so that the pair stays inside one piece.

**Source**: FR-ALGO-C2, FR-ALGO-C2b (`01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`), `01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: a piece must never hold half of a formatting pair, because each piece is checked on its own and half a pair can never pass the gate.

#### Scenario: A boundary inside a pair is skipped

- **WHEN** the masked text `⟦g0⟧He left. She stayed.⟦g1⟧ Night fell.` is split, where `⟦g0⟧` and `⟦g1⟧` are one pair
- **THEN** the pieces are `⟦g0⟧He left. She stayed.⟦g1⟧ ` and `Night fell.`
- **AND** joining them gives back the original text exactly

#### Scenario: A pair spanning every boundary keeps the text whole

- **WHEN** the masked text `⟦g0⟧One. Two. Three.⟦g1⟧` is split
- **THEN** there is one piece, `⟦g0⟧One. Two. Three.⟦g1⟧`

### Requirement: Return the whole text when no sentence boundary is usable

WHERE no usable sentence boundary exists, the system SHALL return the whole masked text as a single piece.

**Source**: FR-ALGO-C2, FR-ALGO-C2b (`01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`), `01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, ADR-0040 (`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`). In plain words: a paragraph that cannot be split safely is sent whole rather than cut badly; drafting the pieces and joining them is the pipeline's part (`translation-pipeline`).

#### Scenario: Text without a sentence boundary stays whole

- **WHEN** a masked text of 6,000 characters containing no sentence-ending punctuation is split
- **THEN** there is one piece, equal to the whole text

### Requirement: Write a TXT or Markdown export as UTF-8 when its encoding cannot hold the translation

WHEN a TXT or Markdown document is written and a character of its output cannot be represented in the encoding
resolved when the document was opened, the system SHALL write the whole document as UTF-8, SHALL begin it with a
byte-order mark exactly when the source began with one, and SHALL write every character, never a replacement
character such as `?` in its place.

**Source**: ADR-0029 (`docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`), FR-DOC-TXT-1, FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, EC-FB2-1 (`01_Product/03_DOCUMENT_FORMATS.md#fb2-edge-cases`, the carve-out this extends), `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`, `docs/next_features.md` §6. In plain words: a `windows-1251` or Western European text file has no room for a character outside its code page, and a Ukrainian translation of a `windows-1252` book has almost no character that fits. Refusing the export threw away a finished translation over a detail the person cannot change, and writing `?` destroyed it silently; switching the whole file to UTF-8 keeps every character, which is what FB2 has always done. A TXT or Markdown file cannot declare its new encoding, so the byte-order mark stays exactly as the source had it and the application's own re-open reads the file as UTF-8. EPUB never needs this, because its writer spells such a character as a numeric character reference, and FB2 keeps the switch it already has. A switched file cannot equal its source's bytes, so its golden compares text instead.

#### Scenario: A character outside windows-1251 switches the whole file to UTF-8

- **WHEN** a TXT file read as `windows-1251`, with no byte-order mark and holding `Привіт.\n\nHello.\n`, is exported
  with the target `車.` for its second segment
- **THEN** the output decodes as UTF-8 to `Привіт.\n\n車.\n`
- **AND** it begins with no byte-order mark and holds no `?` in place of any character
- **AND** re-opening the written file resolves its encoding as `UTF-8`

#### Scenario: A Markdown file switches the same way

- **WHEN** a Markdown file read as `ISO-8859-1` is exported and one segment's target text contains the character `Ÿ`,
  which `ISO-8859-1` cannot represent
- **THEN** the whole output decodes as UTF-8 and contains `Ÿ`
- **AND** no `?` stands in its place

### Requirement: Keep the source encoding when every character fits

WHERE every character of a TXT or Markdown output can be represented in the encoding resolved when the document was
opened, the system SHALL write the document in that encoding.

**Source**: ADR-0029 (`docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`), FR-DOC-TXT-1, FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`. In plain words: a file whose translation fits its code page stays in it, so nothing changes without a reason.

#### Scenario: A representable translation keeps the source encoding

- **WHEN** a TXT file read as `windows-1251` holding `One.\n\nTwo.\n` is exported with the target `Два — три.` for its
  second segment
- **THEN** the output decodes as `windows-1251` to `One.\n\nДва — три.\n`
- **AND** the bytes before the second paragraph are unchanged

### Requirement: Never refuse a TXT or Markdown export for its encoding

The system SHALL NOT refuse a TXT or Markdown export because of its encoding.

**Source**: ADR-0029 (`docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`), FR-DOC-TXT-1, FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`. In plain words: refusing the export threw away a finished translation over a detail the person cannot change.

#### Scenario: A book whose code page holds no Cyrillic is written, not refused

- **WHEN** a TXT file read as `windows-1252` holding `Hello.\n` is exported with the target `Привіт.`
- **THEN** the export succeeds and the output decodes as UTF-8 to `Привіт.\n`

### Requirement: Compare an encoding-switched export by its re-parsed text

The per-format golden tests SHALL compare a fixture whose export switches to UTF-8 against its expected text re-parsed
from the written file, rather than against the source's bytes; the zero-edit goldens SHALL stay as they are.

**Source**: ADR-0029 (`docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`), `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`. In plain words: a switched file cannot equal its source's bytes, so its golden compares text instead.

#### Scenario: A switched fixture is compared by its text

- **WHEN** the golden test exports the `windows-1251` fixture `Привіт.\n\nHello.\n` with the target `車.` for its second
  segment
- **THEN** it compares the re-parsed text `Привіт.\n\n車.\n` of the written file, not the source's bytes
- **AND** the zero-edit golden of the same file still compares byte for byte
