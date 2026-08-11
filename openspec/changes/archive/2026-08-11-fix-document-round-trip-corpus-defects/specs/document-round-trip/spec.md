## ADDED Requirements

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
target text set on every segment, re-opens each output, and records for each book whether its structure, segment
identities and segment text survived unchanged.

The verification SHALL record a distinct failed outcome for a book that opens and writes without error but whose
round trip is not faithful, so that a run can distinguish "nothing threw" from "nothing changed".

The verification SHALL be excluded from the merge gate and from the standard test task, SHALL be skipped when no
corpus directory is configured, and SHALL record every book's outcome rather than stopping at the first failure.

Source: FR-DOC-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43,
`04_Build_and_Release/06_TESTING_STRATEGY.md#live-local`,
`04_Build_and_Release/02_QUALITY_GATES.md`.
In plain words: every defect this change fixes was found by running real books through the writer, and none of them
was visible to the hand-authored fixtures. Keeping that measurement means the same fixes can be re-proved at the end
of this change and re-run whenever the parser or writer is touched again. It cannot be a merge gate, because the
corpus is third-party copyrighted material that will never be in the repository — so it follows the pattern the
testing strategy already sets for tests that need something the checkout does not have: tagged, environment-gated,
green by skipping. Recording rather than asserting is what makes it a measurement: one unparseable book must not
hide the other 213.

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

## MODIFIED Requirements

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
proportion of that fixture's visible text, counting text inside deliberately excluded blocks as not translatable.

The measurement SHALL compare like with like for every format: the text taken from a segment and the text taken from
the source SHALL both have inline markup removed, and both SHALL be decoded with the encoding resolved for that
document rather than an assumed one.

Source: FR-DOC-01, FR-DOC-09, ADR-0027 (`docs/adr/ADR-0027-structural-block-segmentation.md`),
`04_Build_and_Release/06_TESTING_STRATEGY.md`.
In plain words: every other assertion in the document suite checks that nothing was *damaged*, and a book that yields
no segments at all satisfies every one of them perfectly — the skeleton round-trips, the bytes match, the gate is
green, and the product does nothing. This is the only assertion that checks that something *happened*. A survey of
194 real books found 42 that would have imported empty with no test noticing. The added clause exists because the
measurement currently cannot do its job on two of the four formats: it compares Markdown segment text that still
carries its `**bold**` syntax against source text with the syntax already stripped, and it decodes plain-text sources
as UTF-8 whatever the resolved encoding was. Both fabricate a shortfall, so a real shortfall cannot be told from
noise — and a floor calibrated against a metric that understates is a floor that passes a genuine regression.

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
