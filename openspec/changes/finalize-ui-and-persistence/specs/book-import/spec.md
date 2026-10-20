## ADDED Requirements

### Requirement: Say in one line what the import found

The import screen SHALL show exactly one verdict line: can translate (with the chapters and about the words), Reading… (a bar "chapter n of N" and Cancel), Already in projects (naming them), Two language marks, DRM, Damaged or Unsupported; each refusal SHALL offer Choose another file….

**Source**: docs/specification/mockups/README.md (Step 1 · Import), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (I1–I4). In plain words: after choosing a file the person gets one plain sentence saying whether BookLoom can translate it, and what to do when it cannot.

#### Scenario: Reading shows a chapter count and Cancel

- **WHEN** a book is being read and the third of 5 chapters has been parsed
- **THEN** the verdict is Reading… with a bar labelled "chapter 3 of 5" and a Cancel button

#### Scenario: A book that is already a project is named

- **WHEN** the person opens a file whose content matches the existing projects `Harbor Lights · EN → UK` and `Harbor Lights · EN → PL`
- **THEN** the verdict is Already in projects, names both projects, and offers Show them

### Requirement: Compare only the file's own language marks

The Two language marks verdict SHALL compare only what the file declares about itself, the package language against the chapters' language tags, and SHALL NOT read the book's text to decide.

**Source**: docs/specification/mockups/README.md (Step 1 · Import), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (I1–I4), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`). In plain words: BookLoom has no language detector, so it only points out when the file contradicts itself and leaves the choice to the person.

#### Scenario: Two language marks compare declarations only

- **WHEN** an EPUB whose package says English has chapters tagged Ukrainian
- **THEN** the verdict reads "The file names two languages: its details say English, its chapters are marked Ukrainian. BookLoom does not read the text to decide — check “Translate from” in the Book Brief."
- **AND** no text of the book was read to reach it

### Requirement: Show the book's details in a closed About this book fold

The "About this book" fold SHALL show, closed, a summary line (format, chapters, words, images, cover) and, opened, the cover or "No cover", the file, `Title · Author`, the declared language, DRM and what is kept, and SHALL omit a row the book does not state.

**Source**: docs/specification/mockups/README.md (Step 1 · Import), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (I1–I4). In plain words: the details are one click away and never cluttered with placeholders for things the book does not say.

#### Scenario: The fold omits what the book does not state

- **WHEN** `notes.txt` declaring no title, author or language is opened and the fold is expanded
- **THEN** it shows the cover area "No cover" and no title, author or language row

### Requirement: Count chapters and words as the structure does

The chapter count SHALL be the number of top-level nodes of the structure tree, the word count SHALL be the words of the translatable body text shown with `~` and rounded to the nearest thousand from 1,000 up, and the screen SHALL show no row for a language detected from the text.

**Source**: FR-IMPORT-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`). In plain words: the fold and the structure screen always agree, and a figure is never more exact than the book allows.

#### Scenario: Words are rounded and chapters counted

- **WHEN** an EPUB has 11 top-level table-of-contents entries and 78,214 words of body text, and a second book has 640 words
- **THEN** the fold reads `11 · ~78,000 words` for the first and `~640 words` for the second

### Requirement: Browse the book's text in three panes

The book's text browser SHALL be three panes — Chapters, the selected chapter's Segments, and the selected segment's Text — with a search of the text or of a place such as `ch1 · p05`, a kind filter, a kind chip on each row, and the line "Nothing matches 'xyz'." when nothing is found.

**Source**: docs/specification/mockups/README.md (Step 1 · Import), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (I1–I4). In plain words: the person can read what BookLoom found before translating, and find a paragraph by its words or its place.

#### Scenario: The text browser narrows by search

- **WHEN** the person types `xyz` into the segment search and no segment of the chapter contains it
- **THEN** the Segments pane reads "Nothing matches 'xyz'."

#### Scenario: The text browser shows a segment's text

- **WHEN** the person selects chapter 1 and then the segment at `ch1 · p05`
- **THEN** the Text pane shows that segment's location, its kind and its text with formatting marks as chips

### Requirement: Ask before a dropped book replaces the open project

WHEN a book is dropped while a project is open, the screen SHALL ask "Open another book?" with "Open as a new project" as the primary choice, and SHALL name "Replace and delete this translation" as the destructive alternative.

**Source**: docs/specification/mockups/README.md (Step 1 · Import), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (I1–I4), `docs/specification/mockups/README.md` (G1). In plain words: a second book never silently destroys the first project; the safe choice is the primary one.

#### Scenario: Dropping a second book asks first

- **WHEN** a project is open and a different book is dropped
- **THEN** the dialog "Open another book?" appears with "Open as a new project" as its primary button
- **AND** nothing of the open project is changed until the person chooses

## MODIFIED Requirements

### Requirement: Open a book and report what was found

The import screen SHALL accept a book from the file picker or dropped onto its full-width drop zone, whose content is centred, and on success SHALL show one verdict line, a closed "About this book" fold and the book's text browser, reading the title and author from the metadata keys `title` and `author`.

**Source**: docs/specification/mockups/README.md (Step 1 · Import), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (I1–I4), FR-IMPORT-02, FR-IMPORT-06, FR-IMPORT-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), ADR-0037. In plain words: the import screen takes a book either way and reports what was found; Markdown supplies a title only from its front matter and plain text supplies neither.

#### Scenario: An EPUB opens and reports itself

- **WHEN** `harbor-lights.epub`, an EPUB with 5 top-level chapters, 4,100 words of body text, a declared cover, title `Harbor Lights` and author `Ada North`, is opened
- **THEN** the verdict reads "BookLoom can translate this book: 5 chapters, about 4,100 words. Your file is never changed."
- **AND** the closed fold's summary names the format, 5 chapters, about 4,100 words and the cover

#### Scenario: A regional declaration is shown normalized

- **WHEN** an EPUB whose package declares the language `en-US` is opened and "About this book" is expanded
- **THEN** the declared-language row reads `English (en)`

#### Scenario: A plain-text book reports what little it declares

- **WHEN** `notes.txt`, holding 640 words and declaring no title, author or language, is opened
- **THEN** the fold shows `TXT`, 1 chapter and about 640 words, the cover area reads "No cover", and no title, author or language row is shown

#### Scenario: A Markdown book with a title but no author shows the title alone

- **WHEN** `notes.md`, whose front matter states `title: Field Notes` and no author, is opened
- **THEN** the title row reads `Field Notes` with no separator and no author, and the cover area reads "No cover"

#### Scenario: A Markdown book without a frontmatter title omits both rows

- **WHEN** `notes.md`, whose front matter states no `title`, is opened
- **THEN** the single `Title · Author` row is absent, and the chapters, words, images and fonts are reported
- **AND** the verdict still says BookLoom can translate the book

#### Scenario: An FB2 book reports its format without a version

- **WHEN** a valid `forrest.fb2` is opened
- **THEN** the fold's format row reads `FB2`

#### Scenario: The two metadata keys are named once

- **WHEN** an EPUB whose package declares the title `Harbor Lights` and the creator `Ada North`, and an FB2 whose title information declares the book title `Kobzar` and the author `Taras Shevchenko`, are opened
- **THEN** both parsed books carry their title and author under the same two keys, `title` and `author`
- **AND** the two folds read `Harbor Lights · Ada North` and `Kobzar · Taras Shevchenko`

#### Scenario: A protected book is refused with its own message

- **WHEN** a book whose content documents are encrypted is opened
- **THEN** the verdict says the book is locked by the shop it came from and offers Choose another file…
- **AND** no control to continue becomes available

#### Scenario: A file the application cannot read is refused

- **WHEN** `notes.pdf` is dropped onto the screen
- **THEN** the verdict is Unsupported and says "It reads EPUB, FB2, Markdown and plain text", and offers Choose another file…

#### Scenario: A damaged book of a supported format is refused with its reason

- **WHEN** `broken.epub`, whose ZIP container is truncated, is opened
- **THEN** the verdict is Damaged with "Try downloading it again" and a Details control, and offers Choose another file…

#### Scenario: Opening a second book replaces the first

- **WHEN** a project is open and a different book is dropped
- **THEN** the dialog "Open another book?" appears with "Open as a new project" as its primary button, and nothing of the open project changes until the person chooses
- **AND** choosing "Replace and delete this translation" replaces the first book with the second

#### Scenario: Cancel releases the book

- **WHEN** a book is reported and the person cancels it
- **THEN** the screen shows only the drop zone, and no project is open
