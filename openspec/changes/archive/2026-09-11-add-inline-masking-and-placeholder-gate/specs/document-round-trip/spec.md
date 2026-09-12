## ADDED Requirements

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

Source: FR-DOC-MD-1 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-TXT-3 (`#txt`), FR-DOC-04
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: these two formats reassemble by splicing bytes back into the file they came from, so any re-spelling
of the text is a change to the document. `\*` in Markdown source means a literal asterisk and `AT&amp;T` means
`AT&T`, but writing either decoded form back into the buffer would change what the next parse sees. The contrast
with EPUB and FB2 is deliberate: there the segment is re-parsed into a tree, here it is copied into a file.

#### Scenario: A Markdown backslash escape is left as written

- **WHEN** a Markdown paragraph whose source text is `A \* B and C` is parsed
- **THEN** the segment's masked form is `A \* B and C`

#### Scenario: A Markdown character reference is left as written

- **WHEN** a Markdown paragraph whose source text is `AT&amp;T and more` is parsed
- **THEN** the segment's masked form is `AT&amp;T and more`

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
tokens in the supplied target against the multiset of tokens in the segment's masked form.

IF the two multisets differ in any way — a token missing, a token added that the source did not contain, or a token
occurring a different number of times — THEN the system SHALL return a failed result carrying
`ErrorCode.validation`, SHALL NOT restore any placeholder, SHALL NOT alter the target text, and SHALL NOT attempt a
repair or a reconciliation of any kind.

Token order SHALL NOT affect the comparison.

Source: FR-DOC-05, FR-QA-01, FR-QA-04 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`, `#fr-qa`), EC-INLINE-2
(`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`),
`02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`,
`02_Architecture/05_PIPELINE_ENGINE.md#qa-checks` (tag integrity is a hard gate, pre-QA), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: the model may rewrite every word and may move the formatting around the sentence, because word order
differs between languages — but it may not lose, invent or duplicate a piece of formatting. This is the one check no
confidence score and no judge verdict can outvote, and it runs before any of them. It reports; it never fixes.
Repairing a mismatch here would mean guessing where the formatting belonged, and a guess that looks right is exactly
the failure this gate exists to prevent. The trigger is the restore operation and not the write path, because
reassembly is also given target text — by a reviewer's manual edit and by the corpus verification — and neither
carries tokens to compare.

#### Scenario: A dropped token fails the gate

- **WHEN** a segment whose masked form is `⟦g0⟧old⟦g1⟧ door` is given the target `⟦g0⟧старі двері`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A failed gate restores nothing

- **WHEN** that same target is supplied
- **THEN** no restored content is returned

#### Scenario: Reordered tokens pass the gate

- **WHEN** a segment whose masked form is `⟦g0⟧A⟦g1⟧ and ⟦g2⟧B⟦g3⟧` is given the target `⟦g2⟧Б⟦g3⟧ і ⟦g0⟧А⟦g1⟧`
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

IF a segment's format expresses inline structure as markup — EPUB or FB2 — THEN the system SHALL treat every part of
the target that is not a placeholder token as character data and escape it accordingly when composing the restored
content, and SHALL insert the mapped fragments as markup.

IF the target contains a character that is significant in that format's markup — a bare `<`, `&` or `>` — THEN the
restored content SHALL carry that character as escaped character data, and the operation SHALL NOT fail.

IF the target contains a character that no XML 1.0 document can carry at all — a C0 control other than tab, line
feed or carriage return; an unpaired surrogate; U+FFFE or U+FFFF — THEN the system SHALL fail **that segment** with
`ErrorCode.validation`, naming no book text on the failure, and SHALL leave every other segment of the book
restorable and the book exportable. A C1 control, which XML 1.0 permits, SHALL NOT be refused.

This escaping SHALL apply only when a target is restored, and SHALL NOT apply to target text supplied directly for
reassembly.

Source: FR-DOC-04, FR-DOC-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
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
visible text is its own destination, and each inline HTML fragment SHALL be masked as a single atomic token. Any
other inline construct SHALL be masked as a single atomic token.

A hard line break SHALL be masked as a single atomic token whose fragment is the trailing whitespace or backslash
that spells it, taken from the end of the preceding text run.

Source: FR-DOC-MD-1, FR-DOC-MD-2, FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-04,
FR-DOC-10 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-MD-1
(`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-49
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-49-code-and-technical-content-preservation`).
In plain words: Markdown has no tags to hide, only punctuation that means something because of where it sits. The
whole construct — the `*` on both ends of an emphasis, the `](url)` on the end of a link — must be replaced, not
just its label, or the model is left holding delimiters it can move. The link rule splits in two: `[chapter
two](ch2.md)` has a label a reader reads and a target a reader does not, so the label translates; but an autolink
such as `<https://x.org/a>` or `<me@example.com>` has the address *as* its visible text, and pairing it would hand
that address to the model as prose — precisely what the requirement to protect URLs forbids. "Ranges" is plural
because an emphasis spanning a line break is reported as two disjoint ranges. A hard line break needs its own clause
because the parser reports no range for its common spelling — two trailing spaces — and folds them into the
preceding text instead; masking it anyway is what gives the model a token to preserve, and what makes its loss a
failure the repair tier can name rather than an unexplainable one. The catch-all clause exists so that enabling a
Markdown extension later cannot silently leave a new construct unmasked.

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

- **WHEN** a Markdown paragraph whose source is `line one` followed by two spaces, a line feed and `line two` is
  parsed
- **THEN** the segment's masked form is `line one⟦g0⟧` followed by a line feed and `line two`

#### Scenario: The hard line break's own spelling is what the map holds

- **WHEN** that same paragraph is parsed
- **THEN** the placeholder map's `g0` entry is the two space characters

### Requirement: Escape model-introduced Markdown punctuation when restoring

IF a segment's format is Markdown and the text the model supplied would parse as a construct the source did not
contain, THEN the system SHALL neutralise the characters that would form it so the restored text carries no such
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

Source: FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-04
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: this is the same rule that turns a model's stray `<` into `&lt;` for EPUB, applied to the format
whose markup is punctuation rather than tags. A translation containing `5 * 3`, a footnote marker, or an asterisked
aside is ordinary prose, and the book should print it rather than gain emphasis the author never wrote. The
block-level half is not an afterthought: a translated sentence beginning `1985.` becomes an ordered list,
`- як казав автор` a bullet list, and `# Це не заголовок` a heading, and every one of those would otherwise fail a
chunk over a character the frozen spec never protected.

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

WHEN a Markdown segment's placeholders have been restored, the system SHALL parse the segment's source text and the
restored text in the same way, each on its own, and SHALL compare the multiset of construct types each yields,
disregarding text and soft line breaks.

WHERE the segment's content is inline content owned by a block marker the skeleton holds — a heading or a table
cell — the comparison SHALL disregard block construct types on both sides.

WHERE the segment's content is owned by such a block marker, IF the restored text carries more line terminators than
the segment's source text, THEN the system SHALL treat the segment as a structure mismatch.

WHERE the segment is a table cell, IF the restored text carries more unescaped `|` characters than the segment's
source text, THEN the system SHALL treat the segment as a structure mismatch.

IF the two multisets differ, or either containment condition above holds, THEN the system SHALL return a failed
result carrying `ErrorCode.validation`, and SHALL NOT attempt a repair.

Source: FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-05
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-MD-1
(`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).
In plain words: Markdown's delimiters are context-sensitive in a way tags are not — `*old*` is emphasis but
`* old *` is three literal characters, and at the start of a line it is a bullet list. A model that returns
`⟦g0⟧ старі ⟦g1⟧` satisfies the placeholder multiset perfectly and still deletes the formatting from the book,
silently. Three details keep it from misfiring. Both sides are parsed the same way and on their own, so restoring a
segment unchanged always passes — parsing the source in document context and the restored text alone would fail
any segment holding a reference link, whose definition lives outside it. A multiset and not a sequence, because
translation reorders: `*старі* двері` against a source `the *old* door` must pass even though the emphasis moved to
the front. And soft line breaks are disregarded, because rendering two source lines as one is legal Markdown and
routine in translation. EPUB and FB2 need none of this, because a restored element is spliced back as a node and
cannot be redefined by its neighbours.

The fourth detail is the block carve-out, and it is not a refinement of taste — without it an ordinary numbered
heading cannot be translated at all. A heading's segment is the text *after* its `#` marker, and a table cell's is
the text *between* its pipes; both markers live in the skeleton, not in the segment. So `1. Alpha beta`, parsed on
its own, is an ordered list — and any translation that does not keep the numeral at position zero loses a construct
the document never contained. That failure is unrepairable, because escaping can only remove a construct the model
*added*, never restore one the parse invented. Measured against the 213-book corpus, 54 of 2,065 Markdown segments
across four books are in this position: 43 headings and 11 table cells. A paragraph keeps the full comparison, so
the scenario below in which a restored segment becomes a bullet list still fails.

**The carve-out holds in one direction only, and the containment conditions above are what make that true.** An
earlier wording of this requirement justified it with "a heading or a cell has no block structure of its own to
lose", which is right about what such a segment can *lose* and wrong about what it can *gain*. Because the multiset
already disregards text nodes, a heading translation carrying a blank line reduces to `[Paragraph, Paragraph]`
against a source's `[Paragraph]`, and disregarding block types then empties *both* sides — making the comparison
vacuous rather than merely relaxed. Measured, a heading given the target `Заголовок` + blank line + `second
paragraph` was accepted verbatim and written to disk as a heading *plus a new paragraph*, turning two segments into
three; the same target was correctly rejected as a paragraph. So the block carve-out is paired with a containment
test on the characters that actually terminate the enclosing block: a line terminator for either kind, since a
heading ends at its line and a newline inside a cell ends its row, and an unescaped `|` for a cell, since a pipe
opens a new column. Both are compared against the source rather than forbidden outright, so a segment whose source
already holds one is unaffected and a model that escapes its own pipe as `\|` is still accepted. The pipe condition
also retires what was recorded as decision debt D10: a `|` written into a translated cell was measured to collapse
a four-cell row into a single paragraph, destroying the table.

#### Scenario: A numbered heading's translation is not rejected for losing a list it never had

- **WHEN** a Markdown heading written `## 1. Alpha beta gamma` yields the segment `1. Alpha beta gamma`, and that
  segment is given the target `gamma beta Alpha 1.`
- **THEN** the restored content is `gamma beta Alpha 1.` and the operation succeeds

#### Scenario: A table cell's translation is not rejected for losing a list it never had

- **WHEN** a Markdown table cell whose content is `1. Alpha` is given the target `Alpha 1.`
- **THEN** the restored content is `Alpha 1.` and the operation succeeds

#### Scenario: A heading whose translation gains a second block is a validation failure

- **WHEN** a Markdown heading written `# Title` yields the segment `Title`, and that segment is given a target
  holding `Заголовок`, a blank line, and `second paragraph`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A table cell whose translation adds an unescaped pipe is a validation failure

- **WHEN** a Markdown table cell whose content is `cell` is given the target `Комірка | друга`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A table cell whose translation escapes its own pipe succeeds

- **WHEN** a Markdown table cell whose content is `cell` is given the target `Комірка \| друга`
- **THEN** the restored content is `Комірка \| друга` and the operation succeeds

#### Scenario: A paragraph that becomes a bullet list is still a validation failure

- **WHEN** a Markdown paragraph segment whose masked form is `⟦g0⟧old⟦g1⟧ door` is given the target
  `⟦g0⟧ старі⟦g1⟧ двері`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A space introduced inside an emphasis pair is a validation failure

- **WHEN** a Markdown segment whose masked form is `the ⟦g0⟧old⟦g1⟧ door` is given the target
  `⟦g0⟧ старі ⟦g1⟧ двері`
- **THEN** the caller receives a failed result carrying `ErrorCode.validation`

#### Scenario: A translation that moves the emphasis to the front passes

- **WHEN** a Markdown segment whose masked form is `the ⟦g0⟧old⟦g1⟧ door` is given the target `⟦g0⟧старі⟦g1⟧ двері`
- **THEN** the restored content is `*старі* двері`


#### Scenario: A segment restored unchanged always passes

- **WHEN** every Markdown fixture segment is given its own masked form as its target
- **THEN** no segment fails the structure comparison

#### Scenario: Rendering two soft-wrapped source lines as one passes

- **WHEN** a Markdown segment whose masked form is `line one` followed by a line feed and `line two`, with no
  placeholder between them, is given the target `рядок один рядок два`
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

## MODIFIED Requirements

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
