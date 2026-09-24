## MODIFIED Requirements

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

### Requirement: Verify that a restored Markdown segment keeps its structure

WHEN a Markdown segment's placeholders have been restored, the system SHALL parse the segment's source text and the
restored text in the same way, each on its own, and SHALL compare the multiset of construct types each yields,
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

Source: FR-DOC-MD-4 (`01_Product/03_DOCUMENT_FORMATS.md#markdown`), FR-DOC-05
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), EC-MD-1
(`01_Product/03_DOCUMENT_FORMATS.md#markdown-edge-cases`), DD-43
(`00_Foundation/04_DESIGN_DECISIONS.md#dd-43-canonical-round-trip`), ADR-0031
(`docs/adr/ADR-0031-masked-text-is-character-data.md`).

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
