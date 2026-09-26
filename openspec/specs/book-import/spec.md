# book-import Specification

## Purpose
How a person gets a book into the application and learns what the application made of it: choosing or dropping a
file, seeing what the parse actually found in it, and being refused with a reason the person can act on when the
file is protected, damaged or of a kind the application does not read.

## Requirements

### Requirement: Open a book and report what was found

The import screen SHALL accept a book either from a file chosen through the system's file picker or from a file
dropped onto it, and on success SHALL report the file name, the format, the title and author the book declares,
the language the book declares, the number of units it was divided into, and the number of translatable segments
found.

The title and the author SHALL be read from the parsed book's metadata under the keys `title` and `author`, which
are the keys the EPUB and FB2 readers already write; those two key names SHALL be declared once as named
constants in `:api` rather than written as string literals at the point of use. A Markdown book supplies a title
only when its frontmatter states one and never supplies an author (the frontmatter scanner reads only `title` and
`lang`), and a plain-text book supplies neither.

WHERE the book declares no title, no author or no language, the screen SHALL omit that row rather than showing a
placeholder, and SHALL report the rest without any error.

The screen SHALL NOT report a chapter count, a word count, an image or font count, a cover image, or a detected
source language, because the parsed book does not carry them.

**Source:** FR-IMPORT-02, FR-IMPORT-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`.
In plain words: the card says what the parse actually learned and nothing else. The reference drawing shows a
cover, a chapter count and a word count; none of those exists in the parsed book today, and a card that showed
them would have to invent them. `add-metadata-units-and-language-detection` is what makes those rows real. Title
and author are a second case of the same rule: EPUB and FB2 carry both, Markdown carries a title only when its
frontmatter states one and never an author, and plain text carries neither — so the row has to be allowed to be absent. The two key
names are pinned here because the parsed book has no title or author field: both live in a general-purpose
metadata map, so the screen is reading a string key across a module boundary, and a key spelled once in the
reader and once in the window is a row that silently goes missing the day either is edited.

#### Scenario: An EPUB opens and reports itself

- **WHEN** a valid EPUB declaring `en`, titled `Frankenstein` by `Mary Shelley` is opened
- **THEN** the screen reports the format as EPUB, the declared language as `en`, the title, the author, its unit
  count and its segment count
- **AND** a control to continue to the book brief becomes available

#### Scenario: A plain-text book reports what little it declares

- **WHEN** a valid `notes.txt` declaring no title, no author and no language is opened
- **THEN** the screen reports the file name `notes.txt`, the format TXT, its unit count and its segment count
- **AND** the title, author and declared-language rows are absent rather than empty, and no error is shown
- **AND** a control to continue to the book brief becomes available

#### Scenario: A Markdown book without a frontmatter title omits both rows

- **WHEN** a valid `notes.md` whose frontmatter states no `title` is opened
- **THEN** the title and author rows are absent, and the unit and segment counts are reported
- **AND** a control to continue to the book brief becomes available

#### Scenario: The two metadata keys are named once

- **WHEN** the code that reads the title and the author from a parsed book is read
- **THEN** it refers to the two key names through constants declared in `:api`, and contains no `"title"` or
  `"author"` string literal of its own

#### Scenario: A protected book is refused with its own message

- **WHEN** a book whose content is encrypted is opened
- **THEN** the screen shows a refusing state carrying the title `This book is protected`
- **AND** no control to continue becomes available

#### Scenario: A file the application cannot read is refused

- **WHEN** a file named `notes.pdf` is dropped onto the screen
- **THEN** the screen shows a refusing state reporting `ErrorCode.validation`
- **AND** no control to continue becomes available

#### Scenario: Opening a second book replaces the first

- **WHEN** a book is open and a different book is opened
- **THEN** the screen reports the second book, and the first is released

### Requirement: Describe the language-mismatch state without a way to reach it

The import screen SHALL carry a state that warns the language a book declares disagrees with the language its
text is actually in, and offers a control to continue anyway.

IF no source-language detection exists in the build, THEN no input SHALL place the screen in that state.

**Source:** FR-IMPORT-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-LANG-1
(`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`.
In plain words: the screen is built for a warning it cannot yet raise, because raising it needs language
detection that is not built. Saying that plainly is better than shipping a state nobody can see and nobody
remembers to finish — and the state is still covered by a test that puts the screen into it directly.

#### Scenario: The state renders when it is asked for directly

- **WHEN** the screen is placed in the language-mismatch state with a declared language of `en` and a detected
  language of `uk`
- **THEN** it shows a warning naming both languages and a control to continue anyway

#### Scenario: Opening a book never reaches that state

- **WHEN** any book is opened
- **THEN** the screen is in either the reporting state or a refusing state, never the language-mismatch state
