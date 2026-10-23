## ADDED Requirements

### Requirement: Say in one line what the import found

The import screen SHALL show exactly one verdict line: can translate (with the chapters and the words), Reading… (an indeterminate bar and Cancel, because the parsers report no chapter count while they work), Already in projects (naming them, with Show them), Two language marks, DRM, Damaged or Unsupported; each refusal SHALL offer Choose another file….

**Source**: docs/specification/mockups/README.md#step-1--import--i1i4. In plain words: after choosing a file the person gets one plain sentence saying whether BookLoom can translate it, and what to do when it cannot.

#### Scenario: Reading shows an indeterminate bar and Cancel

- **WHEN** a book is being read
- **THEN** the verdict is "Reading…" with an indeterminate progress bar and a Cancel button

#### Scenario: Cancel while reading abandons the import

- **WHEN** the person presses Cancel on the Reading line and the read finishes afterwards
- **THEN** the page returns to the drop zone at once, the project the read created is removed, its result is ignored, and a project that was already open stays open

#### Scenario: A book that is already a project is named

- **WHEN** the person opens a file whose content matches the existing projects `Earth Gravity · EN → UK` and `Earth Gravity · EN → PL`
- **THEN** the verdict line is Already in projects, names both projects, and offers Show them, and no dialog opens
- **AND** no project is created or removed, and Continue is not offered

#### Scenario: Show them opens the book's projects

- **WHEN** the person presses Show them on that verdict line
- **THEN** the Projects screen opens on the book group `Earth Gravity` listing both projects

### Requirement: Round the word count only from a thousand up

The import screen SHALL show the words of the translatable body text as the exact number below 1,000 ("640 words") and, from 1,000 up, rounded to the nearest hundred with "about" ("about 4,100 words"), in the verdict line and in the "About this book" summary alike.

**Source**: FR-IMPORT-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), docs/specification/mockups/README.md#step-1--import--i1i4. In plain words: a short text is counted exactly, and a long book gets a round figure that is never more exact than it needs to be; both places say the same number.

#### Scenario: A long book is rounded to the hundred

- **WHEN** an EPUB has 78,214 words of body text
- **THEN** the verdict and the fold's summary both say "about 78,200 words"

#### Scenario: A short text keeps its exact count

- **WHEN** `notes.txt` has 640 words
- **THEN** the verdict and the fold's summary both say "640 words", without "about"

#### Scenario: The boundary is a thousand

- **WHEN** one book has 999 words and another has 1,049
- **THEN** they show "999 words" and "about 1,000 words"

### Requirement: Compare only the file's own language marks

The Two language marks verdict SHALL compare only what the file declares about itself, the package language against the chapters' language tags, and SHALL NOT read the book's text to decide.

**Source**: docs/specification/mockups/README.md#step-1--import--i1i4, ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`). In plain words: BookLoom has no language detector, so it only points out when the file contradicts itself and leaves the choice to the person.

#### Scenario: Text in another language raises no verdict by itself

- **WHEN** an EPUB whose package says English and whose chapters carry no language tag holds Ukrainian text
- **THEN** the verdict says BookLoom can translate the book, no Two language marks verdict is shown, and no text of the book was read to decide

### Requirement: Show the book's details in a closed About this book fold

The "About this book" fold SHALL show, closed, a summary line (format, chapters, words, images, cover) and, opened, the cover or "No cover", the file, `Title · Author`, the declared language, DRM and what is kept, and SHALL omit a row the book does not state.

**Source**: docs/specification/mockups/README.md#step-1--import--i1i4, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (I2). In plain words: the details are one click away and never cluttered with placeholders for things the book does not say.

#### Scenario: The fold omits what the book does not state

- **WHEN** `notes.txt` declaring no title, author or language is opened and the fold is expanded
- **THEN** it shows the cover area "No cover" and no title, author or language row

### Requirement: Count chapters as the structure does

The chapter count SHALL be the number of top-level nodes of the structure tree, and the screen SHALL show no row for a language detected from the text.

**Source**: FR-IMPORT-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`). In plain words: the fold and the book's text browser always agree on how many chapters there are, and no language is guessed from the text.

#### Scenario: Chapters are the top-level nodes

- **WHEN** an EPUB has 11 top-level table-of-contents entries, two of them with sub-entries
- **THEN** the fold's summary says 11 chapters and the Chapters pane lists 11 top-level nodes

### Requirement: Browse the book's text in three panes

The book's text browser SHALL be three panes — Chapters, the selected chapter's Segments, and the selected segment's Text — with a search of the text or of a place such as `ch1 · p05`, a kind filter, a kind chip on each row, and the line "Nothing matches 'xyz'." when nothing is found.

**Source**: docs/specification/mockups/README.md#step-1--import--i1i4, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (I3, I4). In plain words: the person can read what BookLoom found before translating, and find a paragraph by its words or its place.

#### Scenario: The text browser narrows by search

- **WHEN** the person types `xyz` into the segment search and no segment of the chapter contains it
- **THEN** the Segments pane reads "Nothing matches 'xyz'."

#### Scenario: The text browser shows a segment's text

- **WHEN** the person selects chapter 1 and then the segment at `ch1 · p05`
- **THEN** the Text pane shows that segment's location, its kind and its text with formatting marks as chips

#### Scenario: A Markdown chapter lists its own segments

- **WHEN** a Markdown book `# One` / `First.` / `Second.` / `# Two` / `Third.` is opened and the person selects `One`
- **THEN** the Segments pane lists exactly `One`, `First.` and `Second.`, and selecting `First.` shows `First.` in the Text pane

#### Scenario: The picked segment survives the listing being asked again

- **WHEN** the person has selected a segment and a brief save lists the chapter's segments again
- **THEN** the same row stays selected and the Text pane still shows its text

### Requirement: Ask before another book is opened while a project is open

WHEN a book is dropped or chosen on the import screen while a project is open, the application SHALL ask "Open another book?" with "Open as a new project" as the primary choice, "Replace and delete this translation" as the named destructive choice and Cancel, and SHALL change nothing of the open project until the person chooses.

**Source**: docs/specification/mockups/README.md#step-1--import--i1i4, docs/specification/mockups/README.md#other-dialogs, docs/specification/mockups/README.md#g1--dialogs-live-in-the-window-on-two-layers. In plain words: a second book never silently destroys the first project, and the safe choice is the primary one.

The question comes first, before the file is read; "Already in projects" is a verdict that can only follow it (an answer of "Open as a new project" for a book that is already stored). "Open as a new project" SHALL stop a run that is still going and wait for it to end, because one translation exists per application run and the stopped translation can be continued later from Projects; it SHALL leave a paused, stopped or finished run as it is, and SHALL never remove the open project. "Replace and delete this translation" SHALL end any run that has not ended, delete the open project, open the book and leave the run idle.

#### Scenario: Dropping a second book asks first

- **WHEN** the project `Earth Gravity · EN → UK` is open and `velk.fb2` is dropped
- **THEN** the dialog "Open another book?" appears with "Open as a new project" as its primary button
- **AND** nothing of the open project is changed until the person chooses

#### Scenario: Choosing a file asks the same question

- **WHEN** the project `Earth Gravity · EN → UK` is open and the person picks `velk.fb2` with Choose a file…
- **THEN** the same dialog "Open another book?" appears

#### Scenario: Open as a new project keeps the first one

- **WHEN** the person presses "Open as a new project"
- **THEN** `velk.fb2` opens as a new project, and `Earth Gravity · EN → UK` is still listed on Projects with its progress

#### Scenario: A running translation is stopped before the new book opens

- **WHEN** the translation of `earth-gravity.epub` is running, `velk.fb2` is chosen and the person answers `Open as a new project`
- **THEN** the card shows "Stopping the run…" with its buttons disabled, the run is stopped and waited for, and only then does `velk.fb2` open while `Earth Gravity · EN → UK` stays in Projects and can be continued later

### Requirement: Open the import screen empty from New translation

WHEN the person presses "+ New translation" on Projects, the application SHALL open step 1 · Import showing only its heading, the formats line and the drop zone, and SHALL create the project only when a book is opened; until then Projects SHALL list no new project and, WHILE no project is open, steps 2 to 5 SHALL stay locked.

**Source**: docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d, docs/specification/mockups/README.md#g6--workflow-steps-need-an-open-project. In plain words: a translation is always of a book, so pressing New translation leads to choosing the book, and leaving without one leaves nothing behind.

#### Scenario: New translation opens an empty import screen

- **WHEN** Projects is empty and the person presses "+ New translation"
- **THEN** step 1 · Import shows its heading, the formats line and the drop zone, and Book Brief, Names & style, Translating and Export are locked

#### Scenario: Leaving without a book creates nothing

- **WHEN** the person presses "+ New translation" and then opens Projects without choosing a book
- **THEN** Projects still shows its empty state

## MODIFIED Requirements

### Requirement: Open a book and report what was found

The import screen SHALL accept a book from the file picker or dropped onto its full-width drop zone, whose content is centred, and on success SHALL show one verdict line, a closed "About this book" fold and the book's text browser, with Continue in the toolbar opening step 2 · Book Brief, reading the title and author from the metadata keys `title` and `author`.

**Source**: docs/specification/mockups/README.md#step-1--import--i1i4, docs/specification/mockups/README.md#layout-and-sizes-l, FR-IMPORT-02, FR-IMPORT-06, FR-IMPORT-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), ADR-0037. In plain words: the import screen takes a book either way and reports what was found; Markdown supplies a title only from its front matter and plain text supplies neither.

#### Scenario: An EPUB opens and reports itself

- **WHEN** `earth-gravity.epub`, an EPUB with 5 top-level chapters, 4,100 words of body text, a declared cover, title `Earth Gravity` and author `Ada North`, is opened
- **THEN** the verdict reads "BookLoom can translate this book: 5 chapters, about 4,100 words. Your file is never changed."
- **AND** the closed fold's summary names the format, 5 chapters, about 4,100 words and the cover
- **AND** Continue in the toolbar opens step 2 · Book Brief

#### Scenario: A regional declaration is shown normalized

- **WHEN** an EPUB whose package declares the language `en-US` is opened and "About this book" is expanded
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

#### Scenario: A damaged book of a supported format is refused with its reason

- **WHEN** `broken.epub`, whose ZIP container is truncated, is opened
- **THEN** the verdict is Damaged with "Try downloading it again" and a Details control, and offers Choose another file…

#### Scenario: Opening a second book replaces the first

- **WHEN** the project `Earth Gravity · EN → UK` is open, `velk.fb2` is dropped and the person chooses "Replace and delete this translation" in the dialog "Open another book?"
- **THEN** `velk.fb2` replaces the book of the open project, and the translation of `Earth Gravity · EN → UK` is deleted

#### Scenario: Cancel releases the book

- **WHEN** a book is reported and the person cancels it
- **THEN** the screen shows only the drop zone, and no project is open

### Requirement: Warn when an EPUB's own language declarations disagree

WHEN an EPUB is opened whose package declares one language and more than half of its content documents that declare a language declare a different one (both compared after normalization), the import screen SHALL show the Two language marks verdict line naming both languages by display name, SHALL still show the "About this book" fold and the book's text browser, and SHALL keep Continue available.

**Source**: FR-IMPORT-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-LANG-1 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), docs/specification/mockups/README.md#step-1--import--i1i4, ADR-0037. In plain words: an EPUB often contradicts itself — the package says English while every chapter says Ukrainian — and that is worth a line; the person is warned, not stopped, because the source language stays editable on the brief.

#### Scenario: The chapters outvote the package

- **WHEN** `earth-gravity.epub`, whose package declares `en` and whose 24 content documents all declare `xml:lang="uk"`, is opened
- **THEN** the verdict reads "The file names two languages: its details say English, its chapters are marked Ukrainian. BookLoom does not read the text to decide — check “Translate from” in the Book Brief."
- **AND** the "About this book" fold and the text browser are shown and Continue is available

#### Scenario: Region differences are not a mismatch

- **WHEN** an EPUB whose package declares `en-US` and whose content documents declare `lang="en"` is opened
- **THEN** no Two language marks verdict is shown

### Requirement: Warn when a book declares a language the application does not recognize

IF a book's declared language, after normalization, is not a language tag the application can name, THEN the import screen SHALL show a line quoting the declared code as written and saying the model will identify the source language and that it can be chosen on the Book Brief, SHALL keep Continue available, and SHALL leave the Book Brief with the source language "Find out from the text" (no source language chosen; ADR-0042).

**Source**: FR-IMPORT-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`), docs/specification/mockups/README.md#step-1--import--i1i4. In plain words: a code the application cannot name is not a language it can preselect, and quoting it as written lets the person see what the book claimed and choose accordingly.

#### Scenario: An unrecognized code is quoted and nothing is preselected

- **WHEN** an EPUB whose package declares `xx-yy` is opened
- **THEN** a line shows the code `xx-yy` and says the model will identify the source language, which can be chosen on the Book Brief
- **AND** Continue is available
- **AND** step 2 · Book Brief opens with "Translate from" set to "Find out from the text"

### Requirement: Block a DRM-protected book and name its encryption

WHEN a book whose content is DRM-encrypted is chosen, the import screen SHALL show the DRM verdict line, saying the book is locked by the shop it came from and naming the file, under the error banner `This book is DRM-protected.`, SHALL offer Choose another file… as its only action, and SHALL offer no way to continue.

**Source**: FR-IMPORT-04, FR-IMPORT-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-DRM-1 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), docs/specification/mockups/README.md#step-1--import--i1i4, ADR-0039. In plain words: a protected book is not broken and not the person's mistake, so the line says why it cannot be used and points at the only way forward; the banner is the notifications capability's.

#### Scenario: An Adobe-protected EPUB is blocked by name

- **WHEN** `Purchased_Novel.epub`, whose encryption manifest encrypts its content documents under the Adobe ADEPT scheme, is dropped onto the screen
- **THEN** the verdict line says `Purchased_Novel.epub` is locked by the shop it came from, under the banner `This book is DRM-protected.`, and names the encryption `Adobe ADEPT`
- **AND** the only action is Choose another file…, and Continue is not available

#### Scenario: Font obfuscation is not DRM

- **WHEN** an EPUB whose only encrypted entries are fonts obfuscated with the IDPF font-obfuscation algorithm is chosen
- **THEN** the verdict says BookLoom can translate the book, and "About this book" shows DRM `none`

### Requirement: Name the encryption scheme of a blocked book

The DRM verdict SHALL name the encryption scheme where the inspection names one — from the book's encryption manifest, or `ZIP encryption` for a zipped FB2 whose entry is encrypted — and WHERE the scheme cannot be identified it SHALL name no scheme and still block the import.

**Source**: FR-IMPORT-04, FR-IMPORT-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-DRM-1 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0039. In plain words: naming the scheme ("Adobe ADEPT") tells the person which shop's protection this is, but an unrecognized scheme must never let the book through.

#### Scenario: A password-protected zipped FB2 is blocked by name

- **WHEN** `earth-gravity.fb2.zip`, whose `earth-gravity.fb2` entry carries the zip encryption flag, is chosen
- **THEN** the DRM verdict names the file `earth-gravity.fb2.zip` and the encryption `ZIP encryption`, and Continue is not available

#### Scenario: An unidentified scheme still blocks

- **WHEN** an EPUB whose content documents are encrypted under an algorithm the application does not recognize is chosen
- **THEN** the DRM verdict is shown naming no scheme, and Continue is not available

### Requirement: Refuse a file of an unsupported type and name what it is

WHEN a file whose type is not one the application reads is chosen, the import screen SHALL show the Unsupported verdict line, naming the file and its detected type with "not supported" and saying "It reads EPUB, FB2, Markdown and plain text", under the error banner `Couldn't read this file.`, SHALL offer Choose another file… as its only action, and SHALL offer no way to continue.

**Source**: FR-IMPORT-01, FR-IMPORT-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), docs/specification/mockups/README.md#step-1--import--i1i4, ADR-0039. In plain words: when someone drops a PDF, "unsupported" alone leaves them wondering whether the file is damaged; naming the type and listing what is supported answers the question in one glance. The banner is the notifications capability's.

#### Scenario: A PDF is named and refused

- **WHEN** `book.pdf`, whose bytes begin with `%PDF-`, is dropped onto the screen
- **THEN** the Unsupported verdict names `book.pdf` and the detected type `PDF` with "not supported", under the banner `Couldn't read this file.`
- **AND** it says "It reads EPUB, FB2, Markdown and plain text", and the only action is Choose another file…

#### Scenario: A Word document is named and refused

- **WHEN** `report.docx` is chosen
- **THEN** the Unsupported verdict names the detected type `DOCX`

#### Scenario: Choosing another file leaves the state

- **WHEN** the Unsupported verdict is shown, Choose another file… is pressed and `earth-gravity.epub` is chosen
- **THEN** the verdict "BookLoom can translate this book: …" for `earth-gravity.epub` replaces the Unsupported verdict
