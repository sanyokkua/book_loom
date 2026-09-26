# Spec Delta

## ADDED Requirements

### Requirement: Show the structure the book was parsed into

The structure screen SHALL present the book's units in reading order as a flat sequence of rows, each showing how
many translatable segments it holds, and SHALL report the book's total segment count.

The rows SHALL carry no nesting, because a parsed unit has no parent: the reference rendering draws a nested
outline, and the book model this build reads has one level.

Each row SHALL identify its unit by the unit's resource path within the source container, together with its
position in reading order. The screen SHALL NOT show a chapter title, because a parsed unit does not carry one.

The screen SHALL be read-only: it SHALL NOT offer to include or exclude a unit, nor to change anything about the
book.

**Source:** FR-DOC-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`.
In plain words: this is where a person checks the application understood their book before spending hours on it —
an EPUB that opened with three segments instead of five thousand is visible here in a second, and was previously
only visible in a log. The row is labelled with the resource path because that is the only name a parsed unit
has; chapter titles arrive with `add-metadata-units-and-language-detection`, and inventing one from a file name
would make the screen look like it knows more than it does. Flatness is stated for the same reason: a unit
carries an order and a path and no parent, so a hierarchy would have to be guessed from path prefixes, which is
the same invention wearing a different hat. The screen is read-only for the same reason the reference drawing's
"confirm translate-vs-preserve" is not built: nothing downstream reads such a choice.

#### Scenario: A book's units are listed in reading order

- **WHEN** an EPUB whose spine holds eleven documents is open
- **THEN** eleven rows are shown in spine order, each with its own segment count
- **AND** no row is nested inside another

#### Scenario: A row is identified by its resource path and position

- **WHEN** the first spine document of an open EPUB is the resource `OEBPS/chapter-01.xhtml`
- **THEN** its row reads `OEBPS/chapter-01.xhtml` at position `0`, and carries no title

#### Scenario: The counts add up to the reported total

- **WHEN** the structure screen reports a total of `1240` segments
- **THEN** the per-unit counts sum to `1240`

#### Scenario: Nothing on the screen can be changed

- **WHEN** the structure screen is shown for an open book
- **THEN** no control offers to include, exclude, rename or reorder a unit
