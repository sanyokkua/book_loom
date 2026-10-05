# Spec Delta

## MODIFIED Requirements

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

**Source:** FR-DOC-01, FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-07, DD-47
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`),
`02_Architecture/03_DOCUMENT_MODEL.md#data-model`, `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`).
In plain words: a book stops being a file and becomes two things — the structure, which is frozen, and a numbered list
of the bits of text that may be translated. Everything downstream addresses text by segment id, so the numbering has
to exist before anything can translate, store, review or export. The unit of that numbering is a *run* rather than a
whole block because some converters write an entire chapter as one element whose paragraphs are separated only by
line breaks; treating such a block as one segment produces a segment no model can accept. For the overwhelming
majority of blocks — every one containing no line break — the run and the block are the same thing. The auxiliary
unit is kept apart so that the reading order, the structure tree and the coverage measurement still describe the
chapters alone, while the title, the table of contents and the image descriptions can still be translated. It comes
last, so every body unit keeps the position it had before auxiliary text was read.
A paragraph of a lone no-break space looks empty on the page, yet a plain whitespace test took it for text and a model
was asked to translate it; anything a reader cannot see is now no segment in any of the four formats.

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

**Source:** DD-07, `02_Architecture/03_DOCUMENT_MODEL.md#data-model` ("a stable node path/id plus child index … not a byte
offset and not an inserted sentinel node"), FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`), ADR-0041
(`docs/adr/ADR-0041-translatable-attribute-values.md`).
In plain words: the segment has to remember exactly which slot in the structure it came from, so a translation can be
written back into that same slot. In a tree, a byte offset would be invalidated by the first edit of any earlier
segment, and a sentinel node would mean the parser modified the structure it promised not to touch. Plain text and
Markdown have no tree to point into, so their slots *are* byte spans — and they stay valid for exactly one reason:
nothing ever writes into the buffer they index. The moment reassembly edited that buffer in place, every later span
would be wrong, which is why the copy-from-original rule is part of the requirement rather than an implementation
detail. An attribute value has no run to index, so its slot is named by the element and the attribute instead.

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

### Requirement: Never regenerate the skeleton

The system SHALL, when writing translated content back into a tree-shaped skeleton, replace only the inner content
of the segment's own run within its block, and SHALL NOT add, remove, reorder or re-serialize any other node. The
system SHALL NOT regenerate, rebuild, or restructure the skeleton.

WHEN the segment being written back is anchored to an attribute value, the system SHALL replace only that one
attribute's value and SHALL leave the element, its other attributes and its content unchanged.

**Source:** FR-DOC-03, `02_Architecture/03_DOCUMENT_MODEL.md#reassembly`, ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`), ADR-0041
(`docs/adr/ADR-0041-translatable-attribute-values.md`).
In plain words: reassembly edits the content of one run and nothing else. Re-serializing the parsed tree on export is
expected and fine; building a new tree from the segments is not, because everything the segments do not capture —
attributes, comments, structure, ordering — would be lost in the rebuild. The unit of replacement is a run's inner
*content* rather than a single text node because a translated paragraph legitimately contains inline markup, and no
text node can hold an element. Alternative text is the one closed exception to "only text changes": its value is
replaced, and nothing around it moves.

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

### Requirement: Preserve out-of-spine resources verbatim

The system SHALL carry every resource not listed in the spine through to the output unchanged, and SHALL produce no
segments for it — except the EPUB 3 navigation document and the EPUB 2 NCX, whose table-of-contents labels SHALL be
read into auxiliary segments and written back, while their structure, order, `playOrder` values and link targets
SHALL be carried through unchanged.

**Source:** EC-EPUB-3 (`01_Product/03_DOCUMENT_FORMATS.md#epub-edge-cases`), FR-DOC-EPUB-8
(`01_Product/03_DOCUMENT_FORMATS.md#epub`), FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`.
In plain words: stylesheets, images, fonts and any stray content document that the spine does not reference are
carried, not read. The nav document and the NCX are the two exceptions, because they hold the table of contents a
reader's navigation panel shows — one surveyed book kept 494 labels in English beside translated chapter headings.
Only their label text changes; where each entry points and in what order stays exactly as it was. Which labels become
segments — an NCX label that reads exactly like a navigation-document label does not — is stated in *Produce a book's
auxiliary text as translatable segments*.

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

**Source:** FR-DOC-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-EPUB-6
(`01_Product/03_DOCUMENT_FORMATS.md#epub`), `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, ADR-0037
(`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`), `docs/next_features.md` §1.
In plain words: a translated book that still declares itself English will be read aloud, hyphenated and spell-checked
as English. Only the first `dc:language` is replaced because later entries may legitimately record other languages
present in the book. Reading systems pick hyphenation, fonts and the text-to-speech voice from the language
attributes on the package and on each chapter's root, not only from `dc:language` — one surveyed book kept
`xml:lang="en"` on 116 chapters after being translated into Ukrainian — so those are rewritten too, but only where
they carried the source language: a chapter marked as Latin is still Latin after translation. The source language is
the run's, not only the package's, because a book whose package says English while its chapters say Ukrainian is
translated from Ukrainian, and it is the chapters' `uk` that must become the target. Only the two root elements of
each document are rewritten; this is a known limit — a chapter wrapped in `<div lang="en">` still says English inside
its body.

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

**Source:** FR-DOC-05, FR-QA-01, FR-QA-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), EC-INLINE-2
(`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`),
`02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`,
`02_Architecture/05_PIPELINE_ENGINE.md#qa-checks` (tag integrity is a hard gate, pre-QA), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`), ADR-0040
(`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`), `docs/next_features.md` §9.
In plain words: the model may rewrite every word and may move the formatting around the sentence, because word order
differs between languages — but it may not lose, invent or duplicate a piece of formatting, and it may not turn a
piece of formatting inside out. A swapped pair — `⟦g1⟧OLD⟦g0⟧` for `⟦g0⟧old⟦g1⟧` — passed the old count-only check
and produced a closing tag before its opening one; EPUB silently "repaired" it, FB2 refused it at write time, and the
export failed at the very end with no segment named. Checking order here flags that one segment instead. Two more
shapes passed the count and the order and still damaged the book: a pair emptied of its words — `⟦g0⟧⟦g1⟧OLD` for
`⟦g0⟧old⟦g1⟧` — keeps the tokens and loses the emphasis on the word, so a pair that held text must still hold text;
and a line break moved across a pair's edge changes where the written paragraph splits into runs, so the re-opened book
has a different number of segments and the whole export fails at its final check. A pair stretched over every word —
`⟦g0⟧«Понад усе», — сказав він.⟦g1⟧` for the drop cap `⟦g0⟧“A⟦g1⟧bove all,” he said.` — kept count, order and text,
yet a tree reader makes the outermost element owning text of its own the segment's block, so the written paragraph
re-opened as a segment of that `<span>` with no markup left, and its drop-cap style covered the whole paragraph; a
target must therefore keep text outside its pairs when the source did. An image may still move freely, and
a line break may move within its own pair, because that is an ordinary word-order change. This is the one check no
confidence score and no judge verdict can outvote, and it runs before any of them. It reports; it never fixes.
A small model on the fixture book returned `⟦g0⟧Інститут ⟦g1⟧Мерідіанського Зондування⟧ відправив…` for
`The ⟦g0⟧Meridian Survey Institute⟦g1⟧ sent…`: count, order, text in the pair and text outside all held, so the reply
was accepted, the stray `⟧` reached the written book as text and the bold moved onto one word; only the export's
re-open caught it. Any bracket glyph outside a whole token is therefore refused, and a pair that wrapped a phrase must
still wrap a comparable share of the sentence. The deterministic repair a caller may ask for afterwards first rejoins a
split or misspelt token and drops the remaining stray glyphs, then places tokens, and its result passes this gate.

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

**Source:** FR-DOC-01, FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#data-model`, `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`,
`02_Architecture/06_DATA_MODEL_SQLITE.md#tables` (`segments.id` is the primary key), DD-47
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0041
(`docs/adr/ADR-0041-translatable-attribute-values.md`).
In plain words: every segment id is built from its unit's id, and those ids are the primary key of the segment table,
so two units sharing an id would collide the moment anything is stored. An FB2 book's bodies all come from one file
and would otherwise share one identity, which is why a body's position is part of its id. Markdown and plain text
now have two units — the body and the auxiliary unit — so "one unit per file" no longer describes them. Auxiliary
segments are named after their slot rather than numbered, because slots come and go independently: a book that gains
one table-of-contents entry must not renumber its title, or every translation and review decision stored under the
old id would land on the wrong text.

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

### Requirement: Reassemble Markdown by splicing translated spans into the original bytes

WHEN a Markdown document is exported, the system SHALL copy the original file's bytes to the output and substitute
only the byte spans of segments that carry target text and the value of a top-level frontmatter `lang` key, leaving
every other byte untouched, and SHALL NOT re-render the document from its syntax tree.

WHERE a character of the output cannot be represented in the encoding the source was read with, the system SHALL
instead write the whole spliced text as UTF-8, as *Write a TXT or Markdown export as UTF-8 when its encoding cannot
hold the translation* states; every character outside the substituted spans SHALL still be the source's character.

**Source:** FR-DOC-MD-4, FR-DOC-03, DD-43, `02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`), ADR-0029
(`docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`),
`01_Product/03_DOCUMENT_FORMATS.md#markdown`.
In plain words: re-rendering a Markdown file from its tree normalises formatting everywhere — emphasis markers,
bullet characters, fence styles, and whether the file ends with a newline — in parts of the document nobody
translated. Copying the original and replacing only translated spans makes every untouched byte identical by
construction, which is both stronger and simpler. The frontmatter `lang` value is the one span outside a segment that
changes, because it is where a Markdown book says what language it is in. A switch to UTF-8 re-encodes every byte but
changes no character.

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

### Requirement: Reassemble plain text by splicing target spans into the original bytes

WHEN a TXT document is exported, the system SHALL copy the original file's bytes to the output and substitute only
the byte spans of segments that carry target text, leaving every other byte untouched.

WHERE a character of the output cannot be represented in the encoding the source was read with, the system SHALL
instead write the whole spliced text as UTF-8, as *Write a TXT or Markdown export as UTF-8 when its encoding cannot
hold the translation* states; every character outside the substituted spans SHALL still be the source's character.

**Source:** FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), FR-DOC-03, DD-43,
`02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, ADR-0029
(`docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`).
In plain words: because everything outside a translated paragraph is copied rather than regenerated, a TXT round trip
with nothing translated is byte-identical by construction — the encoding, the line endings and the byte-order mark
survive because nothing re-encodes them. The one exception is a translation the source's code page cannot hold: then
the whole file is re-encoded as UTF-8, every character kept. This is the one format where byte equality is the correct
assertion rather than an over-strict one.

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

**Source:** FR-DOC-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`) read against
`02_Architecture/03_DOCUMENT_MODEL.md#repackaging`, which specifies a language update for EPUB and FB2 only; DD-43;
FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`); the owner's frontmatter decision recorded in
`openspec/changes/complete-translation-workflow/design.md` (D12).
In plain words: the export contract takes a target language for every format, but TXT has nowhere to put it and a
Markdown file has a place only when its frontmatter already declares one. Inventing a place — a `lang:` key in
frontmatter that had none, a header line in TXT — would add content the source never had, and would break the
byte-exact round trip. A Markdown file that already says `lang: en` is different: leaving it would make the
translated file claim to be English, so its value — and only its value, in the quotes it had — becomes the target.
A `language` key is left alone; the rewrite is limited to `lang` on purpose.

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

### Requirement: Pass a golden round-trip test for TXT

The system SHALL pass a golden round-trip test in which a fixture TXT book is parsed and reassembled with zero
segment edits, and the output is **byte-for-byte identical** to the source, including its byte-order mark and line
endings.

**Source:** FR-DOC-09, DD-43 (`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`), FR-DOC-TXT-3.
In plain words: TXT is the one format where exact bytes are the right assertion rather than an over-strict one,
because a zero-edit export never re-encodes anything — it copies the original buffer and substitutes spans, and only a
translated character the source's code page cannot hold switches a file to UTF-8, which a zero-edit export never
carries. Anything weaker than byte equality here would fail to notice a charset or line-ending change that the
mechanism makes impossible, and so would fail to notice the mechanism being replaced by one that does not. The
encoding-switched case is compared by its re-parsed text instead, as *Write a TXT or Markdown export as UTF-8 when its
encoding cannot hold the translation* states.

#### Scenario: A no-edit TXT round trip is byte-identical

- **WHEN** a fixture TXT book with a UTF-8 byte-order mark and `\r\n` line endings is parsed and reassembled with no
  segment receiving target text
- **THEN** the output file's bytes are identical to the source file's, byte-order mark and line endings included

### Requirement: Present a buffer-shaped segment's masked form as its own source text

IF a segment's format reassembles by splicing into the original byte buffer — Markdown or TXT — THEN the masked form
SHALL carry the segment's source text exactly as the file spells it, with no decoding of backslash escapes and no
expansion of character references.

WHERE the segment is a Markdown frontmatter value, its source text SHALL be the value without its surrounding quotes
and without a trailing comment, with `\"` inside double quotes and `''` inside single quotes read as the quote
character. WHERE the segment is a Markdown image's alternative text, its source text SHALL be the text between the
image's brackets with each backslash escape read as the character it escapes.

**Source:** FR-DOC-MD-1 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-TXT-3 (`#txt`), FR-DOC-04
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: these two formats reassemble by splicing bytes back into the file they came from, so any re-spelling
of the text is a change to the document. `\*` in Markdown source means a literal asterisk and `AT&amp;T` means
`AT&T`, but writing either decoded form back into the buffer would change what the next parse sees. The contrast
with EPUB and FB2 is deliberate: there the segment is re-parsed into a tree, here it is copied into a file. The two
auxiliary slots are the exception because each is written back through quoting or escaping of its own — YAML quotes
for a frontmatter value, bracket escapes for an image description — and a quote or backslash left in the text would be
escaped a second time on the way back.

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

**Source:** FR-DOC-04, FR-DOC-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `#xml-round-trip-config`, ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`), ADR-0025
(`docs/adr/ADR-0025-reassembly-replaces-run-inner-content.md`).
In plain words: after masking, every piece of markup in a translated segment came from a fragment the parser itself
recorded — so nothing the model wrote needs to be markup, and nothing it wrote should be parsed as markup. A model
that writes "x < y" or "Tom & Jerry" is writing ordinary prose, and the book should say so rather than refusing the
write. The last clause draws the boundary: reassembly still accepts inline markup written straight into a target,
because that is how a reviewer's manual edit and the corpus harness both work, and escaping there would turn a
restored `<em>` into the literal text `&lt;em&gt;` — the exact defect ADR-0025 exists to remove.

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

**Source:** FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-04
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: this is the same rule that turns a model's stray `<` into `&lt;` for EPUB, applied to the format
whose markup is punctuation rather than tags. A translation containing `5 * 3`, a footnote marker, or an asterisked
aside is ordinary prose, and the book should print it rather than gain emphasis the author never wrote. The
block-level half is not an afterthought: a translated sentence beginning `1985.` becomes an ordered list,
`- як казав автор` a bullet list, and `# Це не заголовок` a heading, and every one of those would otherwise fail a
chunk over a character the frozen spec never protected. A frontmatter value and an image description are not
Markdown content and are not escaped this way (see *Restore an image's alternative text and a frontmatter value as
plain text*).

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

**Source:** FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-05
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-MD-1
(`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: a restored Markdown segment is spliced straight into the file, so a translation that changes its
structure — a link whose label came loose, a task marker moved after its label, a second block inside a heading —
would change the book the next parser sees. Comparing the kinds of construct on both sides catches that and fails the
one segment instead of the book. The check is for body segments only: a frontmatter value and an image description
are not Markdown content and are restored as plain text.

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

## ADDED Requirements

### Requirement: Keep the lines of a list or a stanza

WHEN a TXT paragraph is masked and it has at least two lines of which either two open with a list marker (a bullet,
a dash or `1.`/`1)`) or none is longer than 60 characters, the system SHALL put a line-break token that stands for
nothing before each line end inside it (`• Mass…⟦g0⟧` + line end + `• Weight…`), so a target that merges the lines
fails the placeholder gate; a paragraph of longer lines is prose wrapped at a column and SHALL carry no such token.
Export verification SHALL ignore a placeholder that stands for nothing, since whether a paragraph carries one depends
on its line lengths, which a translation changes.

WHEN a target is restored, the system SHALL first replace the whitespace on both sides of each line-break token that
the masked form has next to a line end with the masked form's own whitespace there, leaving the token where the target
put it — for Markdown and TXT alike.

**Source:** FR-DOC-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`.
In plain words: in the gemma4:e4b run on the earth-gravity fixture, the TXT lists and one stanza came back as one line
each and were accepted, because nothing marked where a line ended; and a Markdown stanza whose model wrote a space
after a hard-break token restored as `землю,\ він`, failed the structure check and stayed in English. A merged
reply now gets its line end put back by position (the restore-missing repair) or a repair note saying the token ends
a line; a space next to the token is simply put back as the line end it was.

#### Scenario: A TXT list keeps one item per line

- **WHEN** the TXT paragraph `• Mass describes how much matter an object contains.` / `• Weight describes the
  gravitational force acting on that mass.` (two lines) is masked
- **THEN** its masked form ends the first line with `⟦g0⟧`, and the target `• Маса … тіло.⟦g0⟧ • Вага … масу.` restores
  as two lines
- **AND** the target `• Маса … тіло. • Вага … масу.` is refused by the gate

#### Scenario: A Markdown stanza with a space after its hard break

- **WHEN** the stanza `A stone let go will find the ground,\` / `it never asks the way;\` / … is restored from
  `Камінь знайде землю,⟦g0⟧ він не питає шляху;⟦g1⟧ …`
- **THEN** the restored text is `Камінь знайде землю,\` / `він не питає шляху;\` / …, one line each

### Requirement: Repair a refused target's placeholder tokens without a model

WHEN a caller asks the document port to repair a target the placeholder gate refused, the system SHALL return a
repaired target that passes that gate, or `ErrorCode.validation` when none does, without calling a model and without
changing any word of the target. Two modes SHALL exist:

- **restore missing** keeps every token the target placed, drops each token the masked form holds fewer times, drops
  a token the masked form never holds only when a letter or digit touches it (it is glued to a word, which stays), and
  puts each missing token back at the position of the target most like the one it held in the masked
  form — the same kind of boundary (a word's start, a word's end, after a closing quote) nearest the source position
  scaled to the target's length, and inside a word only where the source's token sat inside a word, at the same letter;
- **re-place all** strips every token and places all of them again, in the masked form's order, by the same rule.

IF a token the masked form never holds stands where a word would — between two spaces, at an edge, or beside
punctuation — THEN neither mode SHALL drop it, and the repair SHALL answer `ErrorCode.validation`, so the reply goes to
the model repair instead of losing the word the token replaced.

The gate itself SHALL stay a check that reports and never repairs; this is a separate operation the caller chooses to
call.

**Source:** FR-DOC-05, FR-QA-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), EC-INLINE-2
(`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`), ADR-0040
(`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`).
In plain words: a small model that translated every word but dropped one token of a pair should not lose its
translation. The two real paragraphs that reached an export broken — a drop cap and an italic phrase before a closing
quote — each lost exactly one token; putting it back where the source had it is a deterministic step any person
could take by hand. The repaired target still has to pass the whole gate, so the repair can never let through what
the gate refuses, and the caller records that the markup was put back so a person can check it. A token the text never
had is different: a small model writes one in place of a name (`і ⟦g1⟧ ніколи` for `and Vance never`), and dropping
it silently deleted that name in the gemma4:e4b run on the earth-gravity fixture, so only a token glued to a word may
go.

#### Scenario: A drop cap's lost closing token goes back after the first letter

- **WHEN** the segment `⟦g0⟧“A⟦g1⟧bove all,” said his master.` is repaired in restore-missing mode with the target
  `⟦g0⟧«Понад усе», — сказав господар.`
- **THEN** the repaired target is `⟦g0⟧«П⟦g1⟧онад усе», — сказав господар.`

#### Scenario: An italic phrase's lost closing token goes back after its closing quote

- **WHEN** the segment `“Remember ⟦g0⟧this,”⟦g1⟧ he said in a soft voice.` is repaired in restore-missing mode with the
  target `«Пам'ятай ⟦g0⟧це», — сказав він тихим голосом.`
- **THEN** the repaired target is `«Пам'ятай ⟦g0⟧це»,⟦g1⟧ — сказав він тихим голосом.`

#### Scenario: An invented token glued to a word is dropped

- **WHEN** the segment `⟦g0⟧old⟦g1⟧ door` is repaired in restore-missing mode with the target `⟦g0⟧старі⟦g1⟧ ⟦g7⟧двері`
- **THEN** the repaired target is `⟦g0⟧старі⟦g1⟧ двері`

#### Scenario: An invented token standing for a word is not dropped

- **WHEN** the segment `and Vance never let` is repaired in either mode with the target `і ⟦g1⟧ ніколи не дозволяв`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A swapped pair is placed again

- **WHEN** the segment `See ⟦g0⟧this⟦g1⟧ now.` is repaired in re-place-all mode with the target
  `Дивись ⟦g1⟧це⟦g0⟧ зараз.`
- **THEN** the repaired target is `Дивись ⟦g0⟧це⟦g1⟧ зараз.`

#### Scenario: Nothing to wrap is not repaired

- **WHEN** the segment `⟦g0⟧old⟦g1⟧ door` is repaired in re-place-all mode with an empty target
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

### Requirement: Show the book's structure as a titled tree

The structure screen SHALL present the book's structure as a nested tree in reading order, titled from the book's own
navigation, each node carrying a count pill of the translatable segments that fall under it, and SHALL report the
book's total segment count.

The tree SHALL show only the book's reading-order content: the auxiliary text (metadata, navigation labels, page
titles, alternative text, frontmatter values) SHALL NOT appear as a node and SHALL NOT be counted in the total. A node
that has no title SHALL be shown as `Untitled`.

The screen SHALL be read-only: it SHALL NOT offer to include or exclude a unit, nor to change anything about the
book. Its `Back` action SHALL return to the Book Brief and its `Continue` action SHALL open Names & style.

**Source:** FR-DOC-01, FR-DOC-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`.
In plain words: this is where a person checks the application understood their book before spending hours on it —
an EPUB that opened with three segments instead of five thousand is visible here in a second. Now that the book's
own table of contents, section titles and headings are read, the screen can show chapters by name and nested the
way the book nests them, instead of a flat list of file paths. The auxiliary text is left out of the tree because it
is not part of the reading order; counting it would make the chapter numbers not add up to what a reader sees. The
screen stays read-only because nothing downstream reads a translate-or-preserve choice, and Names & style is now a
real screen, so it is the next step.

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

- **WHEN** the structure screen reports a total of `1240` segments
- **THEN** the counts of the top-level nodes sum to `1240`

#### Scenario: Auxiliary text is not a node

- **WHEN** an EPUB with a translatable title, 12 navigation labels and 7 image descriptions is open
- **THEN** no node represents the title, the labels or the descriptions, and none of those 20 segments is counted
  in the total

#### Scenario: A node without a title reads Untitled

- **WHEN** a Markdown book whose first paragraph comes before its first heading `# Chapter 1` is open
- **THEN** the tree's first top-level node reads `Untitled` and the second reads `Chapter 1`

#### Scenario: Continue leads to Names & style

- **WHEN** `Continue` is pressed on the structure screen
- **THEN** the Names & style screen is shown

#### Scenario: Nothing on the screen can be changed

- **WHEN** the structure screen is shown for an open book
- **THEN** no control offers to include, exclude, rename or reorder a unit

### Requirement: Produce a book's auxiliary text as translatable segments

WHEN a book is opened, the system SHALL always produce its auxiliary text as segments of its auxiliary unit,
whatever the Book Brief's "Also translate" switches say:

- EPUB: the package's first `dc:title` (kind `METADATA_TITLE`); each `dc:creator` (`METADATA_AUTHOR`); each
  `dc:description` (`METADATA_DESCRIPTION`); the link text of each entry of a navigation document that is not in the
  spine (`NAV_LABEL`); the text of each NCX `navLabel` whose source text differs from that of every navigation-label
  segment (`NAV_LABEL`); the `<head><title>` of each spine content document (`TITLE`); and the `alt` value of each
  image (`ALT`);
- FB2: the title information's `book-title` (`METADATA_TITLE`); each `author` (`METADATA_AUTHOR`), built from its
  `first-name`, `middle-name`, `last-name` and `nickname` only, one placeholder pair around each part; and each
  paragraph of its `annotation` (`METADATA_DESCRIPTION`);
- Markdown: each frontmatter value that is text (`FRONTMATTER_VALUE`), as *Segment only the text values of Markdown
  frontmatter, never its keys* defines it, and the alternative text of each image (`ALT`);
- plain text: nothing.

The system SHALL NOT produce a segment for a Markdown frontmatter key, for an FB2 author's `id`, `email` or
`home-page`, or for anything inside the FB2 `src-title-info` or `history`, and SHALL produce no auxiliary segment for
an empty value.

**Source:** FR-DOC-11, FR-DOC-10 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-DOC-EPUB-8
(`01_Product/03_DOCUMENT_FORMATS.md#epub`), FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), DD-47 as
amended by this change to list descriptions under the metadata switch
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`),
`02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, ADR-0041
(`docs/adr/ADR-0041-translatable-attribute-values.md`), `docs/next_features.md` §8.
In plain words: a translated book whose table of contents, page titles, shelf title, description and image
descriptions are still in the source language reads as half-translated. These texts are always read into segments,
and the brief's switches decide later whether the run translates them — so opening the same file always gives the
same segments, whatever the brief says. A description is metadata and follows the metadata switch; a page title
follows the navigation switch; the switches themselves belong to `translation-pipeline`. An NCX label that reads
exactly like a navigation-document label is not a second segment: translating the same words twice wastes a model
call and lets a reviewer's edit reach only one of them. A navigation document that is also in the spine is body text
already, so its links are not read a second time. An FB2 author is read from the parts of a name only, because the
other fields are identifiers and addresses; the source-book information and the file's history describe another book
and another file, so neither is text to translate. Frontmatter keys are field names a program reads, so they are
never text.

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

#### Scenario: FB2 auxiliary text is collected

- **WHEN** an FB2 whose title information has the `book-title` `Harry Potter et la Coupe de Feu`, one `author` and
  an `annotation` of two paragraphs is opened
- **THEN** its auxiliary unit holds one title segment, one author segment and two description segments

#### Scenario: An FB2 author is read from the parts of the name only

- **WHEN** an FB2 whose title information holds `<author><first-name>Leo</first-name> <last-name>Tolstoy</last-name>
  <id>a1b2</id><email>leo@example.org</email></author>` is opened
- **THEN** its auxiliary unit holds one author segment whose masked form is `⟦g0⟧Leo⟦g1⟧ ⟦g2⟧Tolstoy⟦g3⟧`
- **AND** no segment holds `a1b2` or `leo@example.org`

#### Scenario: FB2 source-book information and history are not read

- **WHEN** an FB2 whose `src-title-info` holds `<book-title>Война и мир</book-title>` and whose `document-info` holds
  the `history` paragraph `v1.0 — scanned` is opened
- **THEN** no segment holds `Война и мир` or `v1.0 — scanned`

#### Scenario: Only a frontmatter text value is a segment

- **WHEN** a Markdown file opening with `---\ntitle: The Book\nlang: en\ndate: 2024-05-01\ndraft: true\n---\n` is
  opened
- **THEN** its auxiliary unit holds exactly one segment, `The Book`
- **AND** no segment holds `title`, `lang`, `en`, `2024-05-01` or `true`

#### Scenario: The switches do not change what is produced

- **WHEN** the same EPUB is opened once with every "Also translate" switch on and once with every switch off
- **THEN** both openings produce identical auxiliary segments with identical ids

#### Scenario: A plain-text book has an empty auxiliary unit

- **WHEN** `notes.txt` is opened
- **THEN** its auxiliary unit holds no segment

### Requirement: Write auxiliary translations back into their own slots

WHEN a book is written with target text for auxiliary segments, the system SHALL write each translation into the
exact slot its segment was read from — the metadata element, the name parts of an FB2 author, the navigation link
text, the NCX label, the page title, the image's `alt` value, the frontmatter value — and SHALL leave every auxiliary
slot without target text as it was.

WHERE an image sits inside a body segment and also has an alternative-text segment, the written image SHALL carry the
translated alternative text whether or not the body segment was translated. WHERE one body segment holds several
images with identical markup, the k-th such image in the written translation SHALL carry the k-th image's alternative
text.

WHEN an FB2 author's segment is written, each name part SHALL receive the text its own placeholder pair encloses in
the translation, and the author's other elements SHALL be written unchanged.

WHEN a Markdown image's alternative text is written, the system SHALL escape each `\`, `[` and `]` in it with a
backslash.

**Source:** FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`),
`02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`).
In plain words: producing the segments is half the job; the translated title must land in the title, and the
translated image description must land on that image. An image inside a paragraph travels as a protected token with
the paragraph, so the rule is stated for that case explicitly — otherwise restoring the paragraph could put the
untranslated description back, or a paragraph still waiting for its translation could keep the old description. Two
identical images in one paragraph cannot be told apart by their markup, so they are matched by order. An FB2 author is
written back part by part, so the first name stays a first name and the author's id and address are untouched. A
Markdown description sits between brackets, so a bracket or backslash in the translation is escaped to keep the image
an image.

#### Scenario: A translated title lands in the metadata

- **WHEN** an EPUB whose `dc:title` is `Frankenstein` is written with the target `Франкенштейн` for its title segment
- **THEN** the output OPF declares `<dc:title>Франкенштейн</dc:title>`

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

#### Scenario: An FB2 author's name parts are written in place

- **WHEN** the FB2 author `<first-name>Leo</first-name> <last-name>Tolstoy</last-name><id>a1b2</id>` is written with
  the target `⟦g0⟧Лев⟦g1⟧ ⟦g2⟧Толстой⟦g3⟧`
- **THEN** the output author holds `<first-name>Лев</first-name>` and `<last-name>Толстой</last-name>`
- **AND** it still holds `<id>a1b2</id>`

#### Scenario: An untranslated slot stays in the source language

- **WHEN** an FB2 is written with target text for its `book-title` segment only
- **THEN** its `author` and `annotation` are unchanged in the output

#### Scenario: A frontmatter value is replaced inside the frontmatter

- **WHEN** a Markdown file opening with `---\ntitle: The Book\n---\n` is written with the target `Книга` for its
  frontmatter value
- **THEN** the output opens with `---\ntitle: Книга\n---\n` and its key `title` is unchanged

#### Scenario: A Markdown image description escapes its brackets

- **WHEN** a Markdown paragraph holding `![Figure 1](fig1.png)` is written with the alternative-text target
  `Рисунок [1]`
- **THEN** the output holds `![Рисунок \[1\]](fig1.png)`

### Requirement: Restore an image's alternative text and a frontmatter value as plain text

WHEN an image's alternative-text segment or a Markdown frontmatter-value segment is restored, the system SHALL return
its target as plain text — every placeholder substituted back, with no markup escaping, no Markdown escaping and no
Markdown structure check — so that the writer encodes it exactly once for its slot: as an attribute value in EPUB,
between its image's brackets in Markdown, and as a YAML value in the frontmatter.

The system SHALL restore every other auxiliary segment as a body segment of its format is restored.

**Source:** FR-DOC-11, FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, DD-47
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`), ADR-0041
(`docs/adr/ADR-0041-translatable-attribute-values.md`).
In plain words: the restore step was built for paragraph content, which it escapes as markup or as Markdown. An image
description and a frontmatter value are neither: escaping `&` as markup and then again when the writer puts it into
the attribute printed `&amp;amp;` on the image, and escaping a frontmatter value as Markdown left backslashes a YAML
reader keeps. So these two kinds come back as plain text and are encoded once, where they are written. A title, an
author, a navigation label or a description is written as element text or markup, so it restores exactly as a
paragraph does.

#### Scenario: An ampersand in a description inside a translated paragraph is escaped once

- **WHEN** the image `<img src="fig1.png" alt="Figure 1"/>` inside a paragraph with the body target `До ⟦g0⟧ після`
  receives the alternative-text target `Том & Джеррі`
- **THEN** the written image carries `alt="Том &amp; Джеррі"`
- **AND** the output contains no `&amp;amp;`

#### Scenario: An ampersand in a description inside an untranslated paragraph is escaped once

- **WHEN** the same image's paragraph has no body target and the image receives the alternative-text target
  `Том & Джеррі`
- **THEN** the written image carries `alt="Том &amp; Джеррі"`

#### Scenario: A frontmatter value gains no Markdown backslashes

- **WHEN** a Markdown file whose frontmatter holds `title: The Book` is written with the target
  `Том & Джеррі *назавжди*` for that value
- **THEN** the output's frontmatter holds `title: Том & Джеррі *назавжди*`, with no backslash

#### Scenario: A page title is restored as a body text is

- **WHEN** the `<head><title>` segment `Chapter 1` of an XHTML content document receives the target `Розділ 1 & 2`
- **THEN** the written document's head holds `<title>Розділ 1 &amp; 2</title>`

### Requirement: Render identical navigation labels identically in the navigation document and the NCX

WHERE an EPUB carries both a navigation document outside the spine and an NCX, an NCX label whose source text is
identical to the source text of a navigation-label segment SHALL NOT be a segment of its own, and SHALL be written with
that navigation-label segment's target text.

**Source:** FR-DOC-EPUB-8 (`01_Product/03_DOCUMENT_FORMATS.md#epub`), DD-47
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`),
`02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`.
In plain words: many EPUB 3 books ship a legacy NCX next to the navigation document, and different reading systems
show one or the other. If the same chapter name were translated two different ways, the table of contents would
change depending on the device. Writing both from one segment makes them agree by construction, costs one model call
instead of two, and carries a reviewer's edit to both places.

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

**Source:** FR-DOC-02, FR-DOC-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), ADR-0041
(`docs/adr/ADR-0041-translatable-attribute-values.md`).
In plain words: attribute values usually carry identifiers, links and file names, and changing one breaks a
cross-reference or an image. The exception for alternative text is closed on purpose, so it cannot grow into
"translate any attribute that looks like prose".

#### Scenario: A title attribute is never translated

- **WHEN** a book containing `<a href="#n1" title="Footnote one">1</a>` is fully translated and exported
- **THEN** the output still carries `href="#n1"` and `title="Footnote one"`

### Requirement: Segment only the text values of Markdown frontmatter, never its keys

WHERE a Markdown file opens with a `---`-delimited frontmatter block, the system SHALL produce an auxiliary segment for
a value only when all of these hold:

- its key is neither `lang` nor `language`;
- it is a single-line value on a top-level `key: value` line — not on an indented line, not a list item, not a flow
  collection opening with `[` or `{`, not a block scalar opening with `|` or `>`, and not an anchor, alias or tag
  opening with `&`, `*` or `!`;
- written without quotes, it is not a value YAML reads as another type: a null (`~`, `null` in any letter case, or
  nothing), a boolean (`true`, `false`, `yes`, `no`, `on`, `off`, `y` or `n`, in any letter case), a number
  (hexadecimal, octal, `.inf` and `.nan` included), or an ISO date or date-time;
- it is not a web or e-mail address (`scheme://…`, `www.…`, `mailto:…`, `name@host.domain`);
- with its surrounding quotes, or, when it has none, a trailing `# comment` set aside, it holds at least one letter.

The system SHALL produce no segment for any key, and SHALL carry the block through byte-for-byte when no value
receives target text, apart from the `lang` value that *Declare no language metadata in formats that carry none*
replaces. A `---` line that is not at the start of the file SHALL NOT be treated as frontmatter.

**Source:** FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), EC-MD-3
(`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), FR-DOC-10 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
DD-47 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`),
`02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit`, the owner's frontmatter decision recorded in
`openspec/changes/complete-translation-workflow/design.md` (D12).
In plain words: frontmatter keys are machine-readable field names and translating one breaks whatever reads the file.
Values of other types — dates, flags, counts, addresses, lists — are data a site generator or a reader parses:
translating `true` as `так` or rewriting a date as prose breaks the file just as surely. `lang` is the book's own
language declaration, replaced by the target on export rather than translated. What is left is text a person may want
translated, and the Book Brief's "Frontmatter values" switch, off by default, decides whether the run does. Only the
start of the file can open a frontmatter block, because `---` elsewhere is an ordinary thematic break, and real
documents use it that way.

#### Scenario: A frontmatter block survives byte-for-byte when untranslated

- **WHEN** a Markdown file opening with `---\ntitle: The Book\nlang: en\n---\n` followed by `Prose.` is parsed and
  reassembled with zero segment edits and target language `uk`
- **THEN** exactly one body segment is produced, for `Prose.`
- **AND** the output's first, second and fourth lines are byte-identical to the source's, and its third line reads
  `lang: uk`

#### Scenario: Values that are not text are never segments

- **WHEN** a Markdown file whose frontmatter holds the lines `tags: [war, peace]`, `homepage: https://example.com`,
  `pages: 1225`, `published: yes`, `summary: |` and the indented line `  A long story.` is opened
- **THEN** its auxiliary unit holds no segment

#### Scenario: A quoted value is text only when it holds a letter

- **WHEN** a Markdown file whose frontmatter holds `subtitle: "Part 2"` and `edition: "2"` is opened
- **THEN** its auxiliary unit holds a segment `Part 2` and no segment holding `2` alone

#### Scenario: A trailing comment is not part of the value

- **WHEN** a Markdown file whose frontmatter holds `title: The Book # working title` is opened
- **THEN** its frontmatter segment holds `The Book`

#### Scenario: A thematic break later in the file is not frontmatter

- **WHEN** a Markdown file begins with `# Title` and contains a `---` line between two paragraphs
- **THEN** no frontmatter is recognised and no auxiliary segment is produced
- **AND** the two paragraphs each yield a segment

### Requirement: Quote a translated frontmatter value so it still reads as text

WHEN a frontmatter value's translation is written, the system SHALL keep the value's original quotes, writing `"` as
`\"` inside double quotes and `'` as `''` inside single quotes; and WHERE the value had no quotes, the system SHALL
wrap the translation in double quotes when it contains `: ` or ` #`, begins with one of `-`, `?`, `:`, `,`, `[`, `]`,
`{`, `}`, `#`, `&`, `*`, `!`, `|`, `>`, `'`, `"`, `%`, `@` or a backtick, or would itself read as a null, a boolean, a
number or a date.

**Source:** FR-DOC-MD-3 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-11
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), the owner's frontmatter decision recorded
in `openspec/changes/complete-translation-workflow/design.md` (D12).
In plain words: the translation goes back into YAML, and YAML reads punctuation as structure. Unquoted,
`Книга: Частина 1` is a nested mapping to a YAML reader and a book translated as `1984` becomes a number, so such a
translation is quoted to stay the text it is. A value that was quoted keeps its quotes, with the quote character
escaped the way that style of quote requires.

#### Scenario: A colon in the translation is quoted

- **WHEN** the frontmatter line `title: The Book` is written with the target `Книга: Частина 1`
- **THEN** the output line reads `title: "Книга: Частина 1"`

#### Scenario: A translation that reads as a number is quoted

- **WHEN** the frontmatter line `title: Nineteen Eighty-Four` is written with the target `1984`
- **THEN** the output line reads `title: "1984"`

#### Scenario: Double quotes are kept and escaped

- **WHEN** the frontmatter line `title: "The Book"` is written with the target `Книга "Перша"`
- **THEN** the output line reads `title: "Книга \"Перша\""`

#### Scenario: Single quotes are kept and doubled

- **WHEN** the frontmatter line `title: 'The Book'` is written with the target `Книга 'Перша'`
- **THEN** the output line reads `title: 'Книга ''Перша'''`

### Requirement: Keep the zero-edit round trip canonical-equal with auxiliary text present

The system's per-format golden round-trip tests SHALL show that a book whose auxiliary text is produced as segments —
metadata, navigation document, NCX, page titles, alternative text, frontmatter values — and reassembled with zero
segment edits is still canonical-equal to its source, apart from the intentional target-language metadata.

**Source:** FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`),
`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`, `02_Architecture/03_DOCUMENT_MODEL.md#golden-round-trip-test`,
ADR-0041 (`docs/adr/ADR-0041-translatable-attribute-values.md`).
In plain words: reading more slots must not make writing less faithful. The same golden test that proved "we preserve
the book" keeps proving it after the navigation document stops being copied byte for byte and starts being parsed and
written. For Markdown the intentional language metadata is the frontmatter's `lang` value.

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

**Source:** FR-DOC-09, FR-DOC-11 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), ADR-0041
(`docs/adr/ADR-0041-translatable-attribute-values.md`), `02_Architecture/03_DOCUMENT_MODEL.md#golden-round-trip-test`.
In plain words: the zero-edit test proves nothing is broken when nothing is written; this second golden proves that
writing the new slots touches those slots and nothing next to them — no neighbouring attribute, no link, no element.

#### Scenario: Only the edited slots differ

- **WHEN** a fixture EPUB is written with targets for its title, one navigation label whose NCX label reads the same,
  and one image's alternative text, and for no body segment
- **THEN** a canonical comparison against the source reports differences in exactly four values — the title, the
  navigation label, the NCX label and the alternative text — and in the language metadata, and nowhere else

### Requirement: Inspect a book before opening it

WHEN a file is inspected, the system SHALL answer with a verdict of **readable**, **DRM-protected** or
**unsupported**, together with the format and its version where known (`EPUB 2.0`, `EPUB 3.0`, `FB2`, `Markdown`,
`TXT`), the language evidence the book's metadata gives, and:

- for a DRM-protected book, the encryption scheme by name where the book's encryption manifest identifies it (for
  example `Adobe ADEPT`), or `ZIP encryption` for a zipped FB2 whose entry is encrypted;
- for an unsupported file, the detected file type judged from its name and its leading bytes (`PDF`, `DOCX`, `MOBI`,
  or `Unknown`).

A refusing verdict SHALL be a normal answer, not a failure. Inspecting SHALL NOT change what opening a book returns:
a DRM-protected or unsupported file SHALL still fail to open with `ErrorCode.validation`.

**Source:** FR-IMPORT-04, FR-IMPORT-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-DRM-1
(`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), `02_Architecture/09_ERROR_HANDLING.md#error-code`,
ADR-0039 (`docs/adr/ADR-0039-import-refusal-kind-is-data.md`).
In plain words: the import screen has to tell "this book is protected" apart from "this is a PDF", and both used to
arrive as the same validation error told apart only by message text. Inspection answers that question as data. The
error vocabulary stays at its fixed set, and every caller that opens books — the command line first — sees exactly
what it saw before. A zipped FB2 has no encryption manifest, so the zip's own encryption flag is the scheme it names.

#### Scenario: A readable EPUB reports its version

- **WHEN** `Frankenstein.epub`, whose package declares `version="2.0"`, is inspected
- **THEN** the verdict is readable, with the format `EPUB 2.0`

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

#### Scenario: Font obfuscation does not make a book DRM-protected

- **WHEN** an EPUB whose only encrypted entries are fonts under the IDPF font-obfuscation algorithm is inspected
- **THEN** the verdict is readable

#### Scenario: A password-protected zipped FB2 names its zip encryption

- **WHEN** `Kobzar.fb2.zip`, whose `Kobzar.fb2` entry carries the zip encryption flag, is inspected
- **THEN** the verdict is DRM-protected with the scheme `ZIP encryption`
- **AND** opening the same file fails with `ErrorCode.validation`

### Requirement: Report the language evidence a book's metadata gives

WHEN a book is inspected, the system SHALL report its language evidence: the declared language code as written; that
code normalized, or none where it names no language the application can name; for an EPUB, the language declared by
more than half of the content documents that declare one on their root element, or none where no language has such a
majority; and a verdict of

- **match** — a recognized declaration with no content majority or a content majority equal to it;
- **mismatch** — a recognized declaration and a content majority that differs from it;
- **unrecognized** — a declaration that names no language the application can name, such as `xx-yy`, whether or not
  the Book Brief lists the language it does name;
- **absent** — no declaration.

Declarations SHALL be compared after normalization. The system SHALL NOT infer a language from the book's text.

**Source:** FR-IMPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-LANG-1
(`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0037
(`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`).
In plain words: this is everything the book says about its own language, weighed without guessing. The import screen
turns "mismatch" and "unrecognized" into warnings and the Book Brief turns the evidence into a preselected source.
Reading the text itself is deliberately out of scope: it would need a large detection library, and the person can
correct the source on the brief.

#### Scenario: Chapters disagreeing with the package is a mismatch

- **WHEN** an EPUB whose package declares `en` and whose 24 content documents all declare `xml:lang="uk"` is
  inspected
- **THEN** the evidence reports declared `en`, content majority `uk` and the verdict mismatch

#### Scenario: A regional spelling of the same language is a match

- **WHEN** an EPUB whose package declares `en-US` and whose content documents declare `lang="en"` is inspected
- **THEN** the verdict is match

#### Scenario: An FB2 declaration has no content majority

- **WHEN** an FB2 whose title information declares `<lang>ua</lang>` is inspected
- **THEN** the evidence reports declared `ua`, normalized `uk`, no content majority and the verdict match

#### Scenario: A language outside the list is recognized

- **WHEN** a Markdown file whose frontmatter declares `lang: la` is inspected
- **THEN** the normalized code is `la` and the verdict is match

#### Scenario: An unknown code is unrecognized

- **WHEN** a Markdown file whose frontmatter declares `lang: xx-yy` is inspected
- **THEN** the verdict is unrecognized

#### Scenario: Plain text declares nothing

- **WHEN** `notes.txt` is inspected
- **THEN** the verdict is absent and no content majority is reported

### Requirement: Record the language a block declares on its segment

WHEN an EPUB content document or an FB2 document is parsed, the system SHALL record on each segment the value, as
written, of the `xml:lang` or `lang` attribute of the nearest element that encloses the segment's text and lies below
the document's root element — the segment's own block included — or no language where none of those elements
declares one.

The system SHALL NOT, while parsing, compare that declaration with any other language or mark a segment as foreign.

**Source:** FR-IMPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-FOREIGN-1, EC-FOREIGN-2
(`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0037
(`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`); the quality-gates requirement "Skip the echo and script
checks for a marked foreign passage kept by policy"
(`openspec/changes/complete-translation-workflow/specs/quality-gates/spec.md`) is what reads it.
In plain words: whether a passage is foreign depends on the language the run translates from, which the person settles
on the Book Brief after opening the book — so parsing only records what the book says about each block, and the run
makes the comparison. Comparing at parse time against the package's language marked every paragraph of a Ukrainian
book whose template said English as foreign. The root element is left out because its declaration describes the
whole document, not a passage in it; the import's language evidence already weighs it, and treating it as a passage
marking would keep a whole chapter untranslated whenever it disagreed with the brief.

#### Scenario: A block's own declaration is recorded

- **WHEN** an EPUB content document holding `<p xml:lang="fr">Bonjour, mon ami.</p>` is parsed
- **THEN** that paragraph's segment records the declared language `fr`

#### Scenario: A declaration on the body reaches every paragraph under it

- **WHEN** an EPUB whose package declares `en` has a content document whose `<body xml:lang="uk">` holds
  `<p>Привіт.</p>`
- **THEN** that paragraph's segment records the declared language `uk`
- **AND** parsing marks no segment of the book as foreign

#### Scenario: The root element's declaration is not a block's

- **WHEN** an EPUB content document whose only language declaration is `<html xml:lang="uk">` holds `<p>Привіт.</p>`
- **THEN** that paragraph's segment records no declared language

#### Scenario: A format without elements records none

- **WHEN** a Markdown file and a TXT file are parsed
- **THEN** no segment of either records a declared language

### Requirement: Record the language an inline element declares on its placeholder pair

WHEN an inline element of a segment is masked as a placeholder pair, the system SHALL record on that pair the value,
as written, of that element's own `xml:lang` or `lang` attribute, or no language where the element carries neither.

**Source:** FR-DOC-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-FOREIGN-3
(`01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), `02_Architecture/03_DOCUMENT_MODEL.md#inline-masking`,
ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`), ADR-0040
(`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`).
In plain words: under the Book Brief's Keep policy for foreign passages, a run keeps an inline passage the book itself
marks as another language exactly as written, while the prose around it is translated; the policy and the protection
belong to the pipeline. Recording each pair's own marking when the segment is masked is what lets the run find those
passages from the book's metadata alone, without guessing from the text.

#### Scenario: A marked inline passage records its language on its pair

- **WHEN** an EPUB paragraph whose content is `He said <span xml:lang="fr">bonjour</span> and left.` is parsed
- **THEN** the segment's masked form is `He said ⟦g0⟧bonjour⟦g1⟧ and left.`
- **AND** the pair `⟦g0⟧`…`⟦g1⟧` records the language `fr`

#### Scenario: An unmarked pair records none

- **WHEN** an EPUB paragraph whose content is `He opened the <em>old</em> door.` is parsed
- **THEN** the pair `⟦g0⟧`…`⟦g1⟧` records no language

### Requirement: Extract the book's cover image

WHEN a book's profile is built, the system SHALL find its cover image by the first rule that applies:

- EPUB: the manifest item whose properties include `cover-image`; else the item named by `<meta name="cover">`,
  whose value is read as a manifest id or, failing that, as a manifest href; else the first image in the document the
  guide references with `type="cover"`;
- FB2: the binary the title information's `coverpage` image references;
- Markdown and plain text: none.

WHERE a rule names a resource that does not exist, the system SHALL try the next rule, and SHALL report no cover
rather than a failure when none applies.

**Source:** FR-IMPORT-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), `01_Product/03_DOCUMENT_FORMATS.md#epub`,
`01_Product/03_DOCUMENT_FORMATS.md#fb2`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`.
In plain words: the cover is how a person recognises a book at a glance, and books mark it in several historical ways:
EPUB 3 has a proper property, EPUB 2 used a `meta` element that tools filled with an id or, wrongly but commonly,
with a file path, and the oldest books only point at a cover page. A book whose cover marking is broken still opens —
it just shows the placeholder.

#### Scenario: An EPUB 3 cover property wins

- **WHEN** an EPUB 3 whose manifest marks `images/cover.jpg` with `properties="cover-image"` is profiled
- **THEN** the cover is `images/cover.jpg`

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

#### Scenario: An FB2 coverpage references its binary

- **WHEN** an FB2 whose title information holds `<coverpage><image l:href="#cover.jpg"/></coverpage>` and a
  `<binary id="cover.jpg">` is profiled
- **THEN** the cover is that binary's image

#### Scenario: A dangling cover reference yields no cover and no error

- **WHEN** an EPUB whose only cover marking is `<meta name="cover" content="missing-id"/>`, matching no manifest item,
  is profiled
- **THEN** the profile reports no cover and the book opens normally

#### Scenario: Plain text has no cover

- **WHEN** `notes.txt` is profiled
- **THEN** the profile reports no cover

### Requirement: Derive the book's structure tree from its own navigation

WHEN a book's profile is built, the system SHALL build its structure tree from the book's own navigation, counting
every body segment in exactly one node:

- EPUB: the navigation document's table of contents, or the NCX where there is none, with each entry mapped onto the
  spine document its link points into and nested entries shown as child nodes; each spine document SHALL be counted
  once, under the first entry that points into it, and a later entry pointing into an already counted document SHALL
  appear without a count of its own; a spine document no entry points into SHALL be a top-level node at its spine
  position, titled by the text of its first heading segment or, where it has none, by its file name;
- FB2: the nested sections of each body, titled by their `title`; content of the main body that lies outside every
  section SHALL be an untitled top-level node ahead of the sections; the notes body SHALL be a top-level node titled
  by its own `title` or, where it has none, by its `name` attribute;
- Markdown: the headings, nested by heading level; text before the first heading SHALL be an untitled top-level node
  ahead of them;
- plain text: the single unit.

Each node's count SHALL be the number of body segments that fall under it, its descendants included, so that the
counts of the top-level nodes add up to the book's body segment count.

**Source:** FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`01_Product/03_DOCUMENT_FORMATS.md#epub`, `01_Product/03_DOCUMENT_FORMATS.md#verse-tables-notes`,
`01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`.
In plain words: a person recognises their book by its chapter names, not by file paths, so the tree is titled with the
names the book itself gives. Books often point several table-of-contents entries into one file, which is why each
file is counted once — otherwise the chapter numbers would not add up to the book's total. Every segment has to land
in some node for the same reason: text before the first Markdown heading, an FB2 epigraph before the first section
and the FB2 notes each used to fall outside every node. A spine document the navigation leaves out is titled by its
file name rather than a bare number, because the name is something a person can find in the book. The top-level nodes
are what the import card counts as chapters (`book-import`).

#### Scenario: EPUB navigation titles the tree

- **WHEN** an EPUB whose navigation document lists `Chapter 1` → `c01.xhtml` and `Chapter 2` → `c02.xhtml`, holding
  66 and 58 segments, is profiled
- **THEN** the tree's nodes are `Chapter 1` with count `66` and `Chapter 2` with count `58`

#### Scenario: Entries into one file are counted once

- **WHEN** an EPUB whose navigation lists `Author's Introduction` → `intro.xhtml` with children `Letter 1` →
  `intro.xhtml#l1` and `Letter 2` → `intro.xhtml#l2`, and `intro.xhtml` holds 41 segments, is profiled
- **THEN** `Author's Introduction` has count `41` and its children `Letter 1` and `Letter 2` carry no count

#### Scenario: An NCX is used when there is no navigation document

- **WHEN** an EPUB 2 whose NCX labels its first spine document `Preface` is profiled
- **THEN** the first node is titled `Preface`

#### Scenario: A unit without an entry takes its first heading

- **WHEN** an EPUB's spine document `c07.xhtml` is not in its navigation and begins with `<h1>Interlude</h1>`
- **THEN** a node titled `Interlude` stands at that document's reading position

#### Scenario: A unit with neither entry nor heading takes its file name

- **WHEN** the fourth spine document of an EPUB, `OEBPS/c04.xhtml`, is not in its navigation and holds no heading
- **THEN** a top-level node titled `c04.xhtml` stands at the fourth reading position

#### Scenario: Markdown headings nest by level

- **WHEN** a Markdown file holding `# Part One`, `## Chapter 1` and `## Chapter 2` is profiled
- **THEN** `Chapter 1` and `Chapter 2` are children of `Part One`

#### Scenario: Markdown text before the first heading is counted

- **WHEN** a Markdown file holding the paragraph `Foreword text.`, then the heading `# Chapter 1` and the paragraph
  `Body.`, is profiled
- **THEN** the top-level nodes are an untitled node with count `1` and `Chapter 1` with count `2`
- **AND** their counts add up to the book's `3` body segments

#### Scenario: FB2 content outside sections and the notes body are counted

- **WHEN** an FB2 whose main body holds an `<epigraph>` of one paragraph ahead of its only section — titled
  `Chapter 1` and holding 4 paragraphs — and whose `<body name="notes">`, with no title, holds 2 note paragraphs, is
  profiled
- **THEN** the top-level nodes are an untitled node with count `1`, `Chapter 1` with count `5` and `notes` with count
  `2`
- **AND** their counts add up to the book's `8` body segments

### Requirement: Compute the book's statistics

WHEN a book's profile is built, the system SHALL compute: the number of body segments; the number of words in the
body segments' visible text, placeholders excluded; the number of images the book carries; the number of code blocks
preserved untranslated; the number of embedded fonts; the number of verse-line segments; the number of footnotes
(FB2 notes sections, EPUB elements marked as footnotes or endnotes); the number of tables; and the kinds of inline
formatting that were protected as placeholders (italics, bold, links, quotes, code, line breaks, other markup).

**Source:** FR-DOC-01, FR-DOC-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`01_Product/03_DOCUMENT_FORMATS.md#verse-tables-notes`, `01_Product/03_DOCUMENT_FORMATS.md#images-and-fonts`,
`01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`.
In plain words: these numbers let a person judge the job before starting it — how long the book is, what will be
carried untouched (images, fonts, code), and which delicate structures (verse, footnotes, tables) the translation has
to respect.

#### Scenario: An EPUB's statistics are counted

- **WHEN** an EPUB with 1,240 body segments of 78,214 words, 7 image resources, no fonts, no code blocks, no verse,
  no footnotes and no tables, whose inline markup is only `<i>` and `<q>`, is profiled
- **THEN** the statistics report `1240` segments, `78214` words, `7` images, `0` code blocks, `0` fonts, `0` verse
  lines, `0` footnotes, `0` tables and the formatting kinds italics and quotes

#### Scenario: Placeholders are not words

- **WHEN** a book's only body segment has the masked form `⟦g0⟧old⟦g1⟧ door`
- **THEN** the word count is `2`

#### Scenario: A technical Markdown book counts its code blocks

- **WHEN** a Markdown file with 3 fenced code blocks and 1 table is profiled
- **THEN** the statistics report `3` code blocks and `1` table

### Requirement: Show the book's statistics beside its structure

The structure screen SHALL show, beside the tree, a statistics card reading: `Translatable segments`, `Words` (prefixed
with `~`), `Images (kept as-is)`, `Code blocks` with the note `preserved — never translated`, `Embedded fonts`,
`Verse / poetry` (reading `none` when zero), `Footnotes · tables`, and `Inline formatting` listing the protected
kinds followed by `→ protected`.

**Source:** FR-DOC-01, FR-DOC-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`.
In plain words: the structure screen is the last look before the long part begins, so it shows what will be
translated and what will be carried through untouched, in the words the reference drawing uses.

#### Scenario: The card reads the book's statistics

- **WHEN** the EPUB of 1,240 segments, 78,214 words, 7 images and italics and quotes is open on the structure screen
- **THEN** the card reads `1,240`, `~78,000`, `7`, `0` with `preserved — never translated`, `0`, `none`, `0 · 0` and
  `italics, quotes → protected`

### Requirement: Show the round-trip and resource checks beside the structure

WHEN the structure screen is shown for an open book, the system SHALL run a round-trip check in the background — the
source opened afresh and written with zero edits, then compared with the source — and SHALL show it as running until
it ends, then either `Round-trip check passed — structure & text preserved` or
`Round-trip check failed — structure not preserved` naming what differed, the two segment counts when they differ.

The screen SHALL show `All image / font / link IDs preserved` when every image, font and link identifier of the source
is present in that written copy, and otherwise SHALL show `IDs missing after the round trip:` followed by the missing
identifiers.

WHERE any body segment is estimated at more tokens than the chunk budget of 1,200 tokens, the screen SHALL show a
warning naming how many segments exceed it and saying they will be split by sentence and re-joined.

Neither check SHALL block `Continue`.

**Source:** FR-DOC-02, FR-DOC-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`, DD-43
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`).
In plain words: the golden tests prove the writer on fixtures; this check proves it on the person's actual book,
before hours of translation depend on it. It runs on a fresh copy so it cannot disturb the opened book, and in the
background so the screen stays usable. The oversized warning explains in advance why some paragraphs will be
handled in pieces. The checks inform rather than block, because a person may still want a partial result.

#### Scenario: A faithful book passes both checks

- **WHEN** `Frankenstein.epub` is open and the structure screen is shown
- **THEN** after the background check ends the screen shows `Round-trip check passed — structure & text preserved`
  and `All image / font / link IDs preserved`

#### Scenario: An oversized paragraph is warned about

- **WHEN** an open English book holds one paragraph of 6,000 characters, estimated at 1,725 tokens
- **THEN** the screen warns that 1 segment exceeds the chunk budget and will be split by sentence and re-joined

#### Scenario: No oversized segment, no warning

- **WHEN** every body segment of the open book is estimated below 1,200 tokens
- **THEN** no chunk-budget warning is shown

#### Scenario: The check does not hold the screen

- **WHEN** the round-trip check is still running
- **THEN** the tree and the statistics are shown and `Continue` is available

#### Scenario: A missing identifier is named

- **WHEN** the round-trip check's written copy of the open book lacks the image identifier `img-7` that the source
  carries
- **THEN** the screen shows `IDs missing after the round trip: img-7` in place of
  `All image / font / link IDs preserved`

#### Scenario: A dropped paragraph fails the structure check

- **WHEN** the round-trip check's written copy of the open book re-opens with 1,239 body segments where the source has
  1,240
- **THEN** the screen shows `Round-trip check failed — structure not preserved` naming `1,239 of 1,240 segments`

#### Scenario: A failed check does not block Continue

- **WHEN** the round-trip check has failed and the identifier `img-7` is reported missing
- **THEN** `Continue` is available

### Requirement: Keep the package document's line ends and declaration

WHEN an EPUB is written, the system SHALL write its package document, its NCX and its navigation document with
line-feed line ends only, SHALL write an XML declaration in each only when the source document had one, and SHALL keep
the encoding that declaration named.

**Source:** FR-DOC-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`),
`02_Architecture/03_DOCUMENT_MODEL.md#xml-round-trip-config`, `docs/next_features.md` §2.
In plain words: the package document gained a declaration it never had and carriage returns it never had, and the
golden comparison could not see it because it re-serialized both sides the same way. Nothing broke, but a file that
changes where nothing was translated is exactly what "canonical-equal" is meant to rule out. The NCX and the
navigation document are written back now that their labels are translated, so the same rule covers them.

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

**Source:** FR-DOC-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), `docs/next_features.md` §3.
In plain words: an XML reader turns a raw line feed in an attribute into a space, so writing `&#10;` as a raw line
feed silently changes the value. One surveyed book loses the line break in a bookmark label this way; any `alt` or
`title` holding one would too.

#### Scenario: A line-feed reference survives

- **WHEN** a content document whose element carries `data-pdf-bookmark="Communication: &#10;So Many Choices"` is
  written with zero edits
- **THEN** the output attribute is written with a line-feed character reference, and re-reading it yields a value
  containing a line feed, not a space

### Requirement: Keep an XHTML document's XML declaration as a declaration

WHEN an XHTML document — a content document or the navigation document — that begins with an XML declaration or a
DOCTYPE is written, the system SHALL write its prolog back verbatim ahead of the document, and SHALL NOT turn the
declaration into a comment.

**Source:** FR-DOC-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`,
`02_Architecture/03_DOCUMENT_MODEL.md#xml-round-trip-config`, `docs/next_features.md` §4.
In plain words: the declaration was written out as `<!--?xml … ?-->`, a comment. For a UTF-8 chapter that is harmless;
for a chapter in any other encoding the reader no longer learns the encoding and decodes it wrongly. The navigation
document is written back now that its labels are translated, so its prolog is kept the same way.

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

**Source:** `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, FR-DOC-02
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/next_features.md` §5.
In plain words: the encoding rules already say a byte-order mark is recorded and re-emitted exactly as found; plain
text did that and FB2 dropped it. Some readers rely on the mark to choose the encoding.

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

**Source:** FR-DOC-MD-2 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-04, FR-DOC-10
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-MD-1 (`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`),
`docs/next_features.md` §10.
In plain words: a URL is an address, not prose, and a model asked to translate a sentence will happily "translate" or
re-case the address in it — the pseudo model turned `https://example.com/path` into `HTTPS://EXAMPLE.COM/PATH`. Masking
it hands the model a token it must keep.

#### Scenario: A bare URL is one token

- **WHEN** a Markdown paragraph whose source is `See https://example.com/path` is parsed
- **THEN** the segment's masked form is `See ⟦g0⟧`

#### Scenario: The URL survives translation unchanged

- **WHEN** that paragraph is translated as `ДИВ. ⟦g0⟧` and written
- **THEN** the output line reads `ДИВ. https://example.com/path`

### Requirement: Split an oversized segment at sentence boundaries

WHEN a segment's masked text is to be split, the system SHALL divide it at the sentence boundaries of the source
language into pieces that, joined in order, reproduce the masked text exactly, whitespace included.

WHERE a sentence boundary lies next to placeholder tokens, the system SHALL place it after every closing or atomic
token that directly follows the end of the sentence, together with the whitespace after those tokens, and before an
opening token that begins the next sentence.

The system SHALL NOT use a boundary that falls between a paired placeholder's opening and closing tokens; such a
boundary SHALL be skipped so that the pair stays inside one piece.

WHERE no usable boundary exists, the system SHALL return the whole masked text as a single piece.

**Source:** FR-ALGO-C2, FR-ALGO-C2b (`01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`),
`01_Product/05_TRANSLATION_ALGORITHM.md#chunking`,
`02_Architecture/05_PIPELINE_ENGINE.md#chunk-packing`, ADR-0040
(`docs/adr/ADR-0040-placeholder-pairs-keep-their-order.md`).
In plain words: a paragraph longer than the model's budget has to be translated in pieces, and the pieces must fit
back together into exactly the original paragraph — any lost or doubled space would show in the book. Splitting by the
source language's sentences keeps each piece meaningful. A piece must never hold half of a formatting pair, because
each piece is checked on its own and half a pair can never pass the gate. Tokens next to a sentence end are the hard
case: read as plain text, a token looks like the next sentence continuing, and the boundary disappears on both sides
of it. So a sentence that ends inside emphasis ends after the closing token, and a sentence that starts with emphasis
starts at the opening token. A paragraph that cannot be split safely is sent whole rather than cut badly; drafting the
pieces and joining them is the pipeline's part (`translation-pipeline`).

#### Scenario: Three sentences become three pieces

- **WHEN** the English masked text `Call me Ishmael. Some years ago I went to sea. It was cold.` is split
- **THEN** the pieces are `Call me Ishmael. `, `Some years ago I went to sea. ` and `It was cold.`
- **AND** joining them gives back the original text exactly

#### Scenario: The source language's sentence rules are used

- **WHEN** the Ukrainian masked text `Він пішов. Вона лишилася.` is split with source language `uk`
- **THEN** the pieces are `Він пішов. ` and `Вона лишилася.`

#### Scenario: A boundary inside a pair is skipped

- **WHEN** the masked text `⟦g0⟧He left. She stayed.⟦g1⟧ Night fell.` is split, where `⟦g0⟧` and `⟦g1⟧` are one pair
- **THEN** the pieces are `⟦g0⟧He left. She stayed.⟦g1⟧ ` and `Night fell.`
- **AND** joining them gives back the original text exactly

#### Scenario: A sentence that starts with inline markup is split before its opening token

- **WHEN** the masked text `He left. ⟦g0⟧She⟦g1⟧ stayed. Night fell.` is split, where `⟦g0⟧` and `⟦g1⟧` are one pair
- **THEN** the pieces are `He left. `, `⟦g0⟧She⟦g1⟧ stayed. ` and `Night fell.`
- **AND** joining them gives back the original text exactly

#### Scenario: An opening token right after a sentence end starts the next piece

- **WHEN** the masked text `He left.⟦g0⟧ She stayed.⟦g1⟧ Night fell.` is split, where `⟦g0⟧` and `⟦g1⟧` are one pair
- **THEN** the pieces are `He left.`, `⟦g0⟧ She stayed.⟦g1⟧ ` and `Night fell.`

#### Scenario: A pair spanning every boundary keeps the text whole

- **WHEN** the masked text `⟦g0⟧One. Two. Three.⟦g1⟧` is split
- **THEN** there is one piece, `⟦g0⟧One. Two. Three.⟦g1⟧`

#### Scenario: Text without a sentence boundary stays whole

- **WHEN** a masked text of 6,000 characters containing no sentence-ending punctuation is split
- **THEN** there is one piece, equal to the whole text

### Requirement: Write a TXT or Markdown export as UTF-8 when its encoding cannot hold the translation

WHEN a TXT or Markdown document is written and a character of its output cannot be represented in the encoding
resolved when the document was opened, the system SHALL write the whole document as UTF-8, SHALL begin it with a
byte-order mark exactly when the source began with one, and SHALL write every character, never a replacement
character such as `?` in its place.

WHERE every character of the output can be represented in that encoding, the system SHALL write the document in it.

The system SHALL NOT refuse a TXT or Markdown export because of its encoding.

The per-format golden tests SHALL compare a fixture whose export switches to UTF-8 against its expected text re-parsed
from the written file, rather than against the source's bytes; the zero-edit goldens SHALL stay as they are.

**Source:** ADR-0029 (`docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`), FR-DOC-TXT-1,
FR-DOC-TXT-3 (`01_Product/03_DOCUMENT_FORMATS.md#txt`), `01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom`, EC-FB2-1
(`01_Product/03_DOCUMENT_FORMATS.md#fb2-edge-cases`, the carve-out this extends),
`01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`, `docs/next_features.md` §6.
In plain words: a `windows-1251` or Western European text file has no room for a character outside its code page, and
a Ukrainian translation of a `windows-1252` book has almost no character that fits. Refusing the export threw away a
finished translation over a detail the person cannot change, and writing `?` destroyed it silently; switching the whole
file to UTF-8 keeps every character, which is what FB2 has always done. A TXT or Markdown file cannot declare its new
encoding, so the byte-order mark stays exactly as the source had it and the application's own re-open reads the file as
UTF-8. EPUB never needs this, because its writer spells such a character as a numeric character reference, and FB2
keeps the switch it already has. A switched file cannot equal its source's bytes, so its golden compares text instead.

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

#### Scenario: A book whose code page holds no Cyrillic is written, not refused

- **WHEN** a TXT file read as `windows-1252` holding `Hello.\n` is exported with the target `Привіт.`
- **THEN** the export succeeds and the output decodes as UTF-8 to `Привіт.\n`

#### Scenario: A representable translation keeps the source encoding

- **WHEN** a TXT file read as `windows-1251` holding `One.\n\nTwo.\n` is exported with the target `Два — три.` for its
  second segment
- **THEN** the output decodes as `windows-1251` to `One.\n\nДва — три.\n`
- **AND** the bytes before the second paragraph are unchanged

## REMOVED Requirements

### Requirement: Show the structure the book was parsed into

**Reason**: The structure screen no longer shows a flat list of units identified by resource path. The book's own navigation, section titles and headings are now read, so the screen shows a nested tree titled with chapter names. The clauses "a flat sequence of rows", "carry no nesting" and "SHALL NOT show a chapter title", and the scenarios "A book's units are listed in reading order" (no row nested) and "A row is identified by its resource path and position", are now false.

**Migration**: Replaced by *Show the book's structure as a titled tree*, with the tree's derivation in *Derive the book's structure tree from its own navigation* and the side cards in *Show the book's statistics beside its structure* and *Show the round-trip and resource checks beside the structure*.

### Requirement: Preserve Markdown frontmatter verbatim without segmenting it

**Reason**: Frontmatter text values are now produced as auxiliary segments and written back when translated (FR-DOC-11,
DD-47, FR-DOC-MD-3), so the clause "SHALL produce no segment for any key or value" is false for text values, and the
scenario "exactly one segment is produced" no longer holds once the auxiliary unit is counted. Keys are still never
segmented, and an untranslated block is still carried byte-for-byte apart from a `lang` value replaced by the target
language.

**Migration**: Replaced by *Segment only the text values of Markdown frontmatter, never its keys*, which keeps the
byte-for-byte and thematic-break scenarios, by *Quote a translated frontmatter value so it still reads as text*, by
*Produce a book's auxiliary text as translatable segments* and *Write auxiliary translations back into their own
slots*, and, for the `lang` value, by *Declare no language metadata in formats that carry none*.

### Requirement: Refuse a plain-text export the source encoding cannot represent

**Reason**: The owner reversed this behaviour (ADR-0029, as amended by this change): a TXT or Markdown export whose
translation the source's encoding cannot hold is now written as UTF-8 instead of being refused, so the clauses "SHALL
return a validation failure" and "SHALL NOT write an output file", and the scenario "An unrepresentable target character
fails the export", are now false. The rule that no `?` is ever substituted is kept.

**Migration**: Replaced by *Write a TXT or Markdown export as UTF-8 when its encoding cannot hold the translation*,
whose first scenario reuses this one's `windows-1251` file and `車`. No refusal title exists any more; nothing shows
`This translation cannot be saved in the book's text encoding`.

### Requirement: Drop the sort keys of a translated title or author

WHEN an EPUB is written with a different text in a `dc:title` or `dc:creator` than it was read with, the system SHALL
remove the sort keys that described the old text: that element's `opf:file-as` attribute, a `file-as` refinement meta
that refines its `id`, and, for a title, every `calibre:title_sort` meta. An element whose text is unchanged SHALL keep
all of its keys, so a book written with nothing translated stays canonical-equal to its source. This is a permitted
change of the package document beside the language rewrite.

**Source:** FR-DOC-EPUB metadata, task 15d.13.
In plain words: a translated book is not shelved under the source-language title or author spelling; a reader that
finds no key sorts by the translated text.

#### Scenario: A translated title loses its Calibre sort key

- **WHEN** `The Amulet of Samarkand` (`calibre:title_sort` = `Amulet of Samarkand, The`) is written with the title `Амулет Самарканда`
- **THEN** the package holds the new title and no `calibre:title_sort`, while `calibre:series` is unchanged

#### Scenario: Nothing translated keeps every key

- **WHEN** the same book is written with no translated title or author
- **THEN** every `file-as` and `calibre:title_sort` is still there
