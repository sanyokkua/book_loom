## ADDED Requirements

### Requirement: Offer the automatic translation on the Import screen

WHILE a book is open on Import and the run controls offer a start for its project, the toolbar SHALL offer `Translate automatically` as its main action beside Continue, with an explanation of what it does; WHILE a run is at work or can be resumed or continued, it SHALL offer Go to Translating instead; WHILE no AI model is chosen the action SHALL be disabled with the tooltip that names the missing model, and Import SHALL show no "Cannot start yet" banner.

**Source**: `design.md#d21--defaults-instead-of-questions`, `design.md#d23--where-the-cannot-start-notice-shows-and-the-question-at-start`, `design.md#d20--prepare-steps-inside-the-run`, `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`. In plain words: the shortest path was one drop, seven clicks and one forced decision. The run can now start where the book was dropped, with one press. The explanation, shown as the action's tooltip, reads "BookLoom fills in the Book Brief, finds and prepares the names, translates and proofreads the book by itself. You can review everything afterwards." A person who wants to go step by step uses Continue, as before. Whether a start is offered is read from the one table of run controls the Translating toolbar reads, so Import never shows an action whose command is unavailable: a run paused in its Prepare stage has no decided segment and is not at work, yet it is resumed, not started.

#### Scenario: A book with a run offers its run instead

- **WHEN** the open book's translation is paused at 55 %
- **THEN** the Import toolbar offers Go to Translating and no `Translate automatically`

#### Scenario: No model, no banner on Import

- **WHEN** a book is open on Import and no AI model is chosen
- **THEN** `Translate automatically` is disabled with the tooltip "Choose an AI model in Settings › Providers, then start the translation." and Import shows no "Cannot start yet" banner

#### Scenario: The action is named in Ukrainian

- **WHEN** the interface language is Ukrainian and a book is open on Import
- **THEN** the action reads «Перекласти автоматично» and its explanation reads «BookLoom сам заповнить опис книги, знайде й підготує імена, перекладе та вичитає книгу. Усе можна переглянути потім.»

#### Scenario: A run paused in Prepare at 0 % is resumed, not started again

- **WHEN** `Translate automatically` was pressed, the run was paused during its names step with 0 of 142 segments decided, and Import is opened
- **THEN** the Import toolbar offers Go to Translating and no `Translate automatically`, and Translating offers `Resume`

### Requirement: Do every remaining step of an automatic translation without a question

WHEN `Translate automatically` is pressed, the application SHALL open the Translating screen and start a run that does every remaining step by itself — the brief suggestion and the names step of its Prepare stage, the Translate stage and the Proofread stage — and SHALL ask nothing except the target language when the brief has none.

**Source**: `design.md#d21--defaults-instead-of-questions`, `design.md#d23--where-the-cannot-start-notice-shows-and-the-question-at-start`, `design.md#d20--prepare-steps-inside-the-run`, `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`. In plain words: "automatic" means the person presses once and the app does the rest: it reads the book and fills the brief (genre, style, who narrates), finds the names, lets the model remove what is not a name and suggest a spelling for the rest, translates, and proofreads as the quality setting says. What it skips is the screens — the Book Brief and Names & style are not opened — never the work: the run's own Prepare steps are not skipped. What the person already set is kept: a brief the person filled in is not replaced, and a names pass that already finished is not repeated. The Book Brief and Names & style can be opened at any time afterwards to review what the run wrote.

#### Scenario: Drop a book and translate automatically

- **WHEN** an English book is dropped on the window, the last used target is Ukrainian, a model is chosen, and `Translate automatically` is pressed on Import
- **THEN** the Translating screen opens and the run is in its Prepare stage, with no dialog in between and without the Book Brief or Names & style having been opened

#### Scenario: The run does the brief, the names, the translation and the proofreading

- **WHEN** that run is left alone on a Balanced project with an untouched brief and an empty glossary
- **THEN** its Prepare stage suggests the brief and prepares the names, its Translate stage drafts every segment, its Proofread stage reads the marked paragraphs, the run ends Finished, and no question was asked

#### Scenario: What the person set is kept

- **WHEN** the person chose the genre `Fantasy` on the Book Brief and ran "Prepare names automatically" on Names & style, then goes back to Import and presses `Translate automatically`
- **THEN** the run makes no brief call and no names call, and translates with the brief and the glossary as the person left them

#### Scenario: A refused start is shown on Translating

- **WHEN** `Translate automatically` is pressed on Import and the provider does not answer
- **THEN** the Translating screen is open and shows the provider-error banner in its own words, and Import shows none

#### Scenario: The first book asks for its target

- **WHEN** no target language was ever used and `Translate automatically` is pressed on Import
- **THEN** the dialog "Translate into which language?" of the `app-shell` capability opens, and the run starts when a language is chosen

## MODIFIED Requirements

### Requirement: Show the book's details in an About this book fold that opens with the book

WHEN a book is opened or shown on Import, the "About this book" fold SHALL be open and show the cover or "No cover", the file, `Title · Author`, the declared language, DRM and what is kept, omitting a row the book does not state, with a summary line (format, chapters, words, images, cover) in its title bar. A fold the person closed SHALL stay closed until another book is opened, and the text browser below SHALL keep at least 320 pixels of height.

**Source**: `design.md#d24--about-this-book-opens-with-the-book`, `research/ui-audit-workflow.md#defects` (W-32). In plain words: the first thing a person wants after dropping a file is to see that BookLoom read the right book. The details used to be one click away behind a closed fold; now they are shown, and one click puts them away for that book.

#### Scenario: The fold is open after a drop

- **WHEN** `earth-gravity.epub` is dropped on Import and read
- **THEN** the fold is open and shows the cover, `earth-gravity.epub`, `Earth Gravity · Mara Linde` and `English`, and its title bar reads `EPUB · 12 chapters · about 78,200 words · 3 images · cover`

#### Scenario: The fold omits what the book does not state

- **WHEN** `notes.txt` declaring no title, author or language is opened
- **THEN** the open fold shows the cover area "No cover" and no title, author or language row

#### Scenario: Closing lasts for this book only

- **WHEN** the person closes the fold, opens Book Brief and comes back to Import, and then opens `second-book.fb2` as a new project
- **THEN** the fold is still closed for the first book and is open for `second-book.fb2`

#### Scenario: The text browser keeps its room

- **WHEN** the fold is open in a window 960 by 640 pixels
- **THEN** the text browser is at least 320 pixels high and the page scrolls to reach its end

#### Scenario: Both columns of the fold share one row style

- **WHEN** the open fold shows its details on the left and its counts and checks on the right
- **THEN** the rows of both columns have the same height and the same divider line

### Requirement: Open a book and report what was found

The import screen SHALL accept a book from the file picker or dropped onto its full-width drop zone, whose content is centred, and on success SHALL show one verdict line, the "About this book" fold, open, and the book's text browser, with Continue in the toolbar opening step 2 · Book Brief, reading the title and author from the metadata keys `title` and `author`.

**Source**: `design.md#d24--about-this-book-opens-with-the-book`, `research/ui-audit-workflow.md#defects`. In plain words: the import screen takes a book either way and reports what was found; Markdown supplies a title only from its front matter and plain text supplies neither. The fold that holds the details is open as soon as the book is read, so the person sees at once that the right book was opened.

#### Scenario: An EPUB opens and reports itself

- **WHEN** `earth-gravity.epub`, an EPUB with 5 top-level chapters, 4,100 words of body text, a declared cover, title `Earth Gravity` and author `Ada North`, is opened
- **THEN** the verdict reads "BookLoom can translate this book: 5 chapters, about 4,100 words. Your file is never changed."
- **AND** the fold's title-bar summary names the format, 5 chapters, about 4,100 words and the cover
- **AND** Continue in the toolbar opens step 2 · Book Brief

#### Scenario: A regional declaration is shown normalized

- **WHEN** an EPUB whose package declares the language `en-US` is opened
- **THEN** the declared-language row reads `English (en)`

#### Scenario: A plain-text book reports what little it declares

- **WHEN** `notes.txt`, holding 640 words and declaring no title, author or language, is opened
- **THEN** the fold shows `TXT`, 1 chapter and 640 words, the cover area reads "No cover", and no title, author or language row is shown

#### Scenario: A Markdown book with a title but no author shows the title alone

- **WHEN** `notes.md`, whose front matter states `title: Field Notes` and no author, is opened
- **THEN** the title row reads `Field Notes` with no separator and no author, and the cover area reads "No cover"

#### Scenario: A Markdown book without a frontmatter title omits both rows

- **WHEN** `notes.md`, whose front matter states no `title`, is opened
- **THEN** the single `Title · Author` row is absent, and the chapters, words, images and fonts are reported
- **AND** the verdict still says BookLoom can translate the book

#### Scenario: An FB2 book reports its format without a version

- **WHEN** a valid `velk.fb2` is opened
- **THEN** the fold's format row reads `FB2`

#### Scenario: The two metadata keys are named once

- **WHEN** an EPUB whose package declares the title `Earth Gravity` and the creator `Ada North`, and an FB2 whose title information declares the book title `Velk Chronicles` and the author `Tarn Ives`, are opened
- **THEN** both parsed books carry their title and author under the same two keys, `title` and `author`
- **AND** the two folds read `Earth Gravity · Ada North` and `Velk Chronicles · Tarn Ives`

#### Scenario: A protected book is refused with its own message

- **WHEN** a book whose content documents are encrypted is opened
- **THEN** the verdict says the book is locked by the shop it came from and offers Choose another file…
- **AND** Continue is not available

#### Scenario: A file the application cannot read is refused

- **WHEN** `notes.pdf` is dropped onto the screen
- **THEN** the verdict is Unsupported and says "It reads EPUB, FB2, Markdown and plain text", and offers Choose another file…

#### Scenario: A file of an unknown type is never named "Unknown"

- **WHEN** `notes.xyz`, whose bytes match no known type, is dropped onto the screen
- **THEN** the verdict reads `notes.xyz is not a format BookLoom reads. It reads EPUB, FB2, Markdown and plain text.` and never prints `Unknown`

#### Scenario: A damaged book of a supported format is refused with its reason

- **WHEN** `broken.epub`, whose ZIP container is truncated, is opened
- **THEN** the verdict reads `broken.epub could not be read as an EPUB. The file may be damaged.` with a Details control, and offers Choose another file…

#### Scenario: A file whose content is not the format its name promises is damaged, not unsupported

- **WHEN** `corrupt.epub`, which holds plain text instead of a ZIP archive, or `odd.fb2`, which has no FictionBook root, is opened
- **THEN** the inspection answers the `DAMAGED` verdict with the format the name promises, and the verdict reads `corrupt.epub could not be read as an EPUB. The file may be damaged.` (`odd.fb2 could not be read as an FB2 book. …`), never `is Unknown`

#### Scenario: An EPUB that names one document twice

- **WHEN** an EPUB's manifest holds two items with the same id, or its spine lists one document twice
- **THEN** the repeated manifest id is refused as an unreadable book (`validation`, "could not be read as a book") and no project is left in Projects; a document the spine repeats is read once, at its first place, so every stored segment id is unique; and whenever storing a new project's segments fails, the project row written before them is removed again

#### Scenario: A file named as text that holds something else

- **WHEN** `noise.txt` holds binary bytes (a NUL byte, or more than one control character in ten besides tab, line breaks and form feed) or `book.txt` is an EPUB archive renamed
- **THEN** the inspection answers `DAMAGED` before any reader runs, no project is stored, and the verdict reads `noise.txt is not a text file.` (Ukrainian `noise.txt — не текстовий файл.`) with Choose another file…; a UTF-16 or UTF-32 text with its byte-order mark is still read

#### Scenario: A book with no text is refused

- **WHEN** a 0-byte or whitespace-only `empty.txt`, an empty Markdown file, a 0-byte `.epub` or `.fb2`, an FB2 with no paragraph or no `<body>` at all, or an EPUB whose body is empty is opened
- **THEN** the import answers the `NO_TEXT` verdict and stores no project, and the verdict reads `This book has no text to translate.` (Ukrainian `У цій книзі немає тексту для перекладу.`) with Choose another file… and no Continue

#### Scenario: Opening a second book replaces the first

- **WHEN** the project `Earth Gravity · EN → UK` is open, `velk.fb2` is dropped and the person chooses "Replace and delete this translation" in the dialog "Open another book?"
- **THEN** `velk.fb2` replaces the book of the open project, and the translation of `Earth Gravity · EN → UK` is deleted

#### Scenario: Cancel releases the book

- **WHEN** `Frankenstein.epub` is reported on the card and `Cancel` is pressed
- **THEN** the book is released and the screen shows only the drop zone
- **AND** the Book Brief reports that no book is open
- **AND** the project this import stored new is removed, so it is not listed in Projects; a project that was stored before this import, or one a run already decided segments in, is kept

## RENAMED Requirements

- FROM: `### Requirement: Show the book's details in a closed About this book fold`
- TO: `### Requirement: Show the book's details in an About this book fold that opens with the book`
