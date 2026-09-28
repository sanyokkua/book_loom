# Spec Delta

## MODIFIED Requirements

### Requirement: Open a book and report what was found

The import screen SHALL accept a book either from a file chosen through the system's file picker or from a file
dropped onto it, and on success SHALL show a detected-file card reporting, in this order: the file name; the format
together with its version where the format has one (`EPUB 2.0`, `EPUB 3.0`; `FB2`, `Markdown` and `TXT` carry no
version); the title and the author as one row written `Title · Author`; the language the book declares, normalized
and written as its English display name followed by its code in parentheses; the number of chapters and the
approximate number of words; the number of images and of embedded fonts; and a DRM row reading `none`.

The title and the author SHALL be read from the parsed book's metadata under the keys `title` and `author`, which
are the keys the EPUB and FB2 readers write. A Markdown book supplies a title only when its frontmatter states one and
never supplies an author, and a plain-text book supplies neither.

WHERE the book declares a title but no author, the title row SHALL show the title alone; WHERE it declares neither,
or declares no language, the screen SHALL omit that row rather than showing a placeholder, and SHALL report the rest
without any error.

The chapter count SHALL be the number of top-level nodes in the book's structure tree. The word count SHALL be
the number of words in the book's translatable body text, always shown prefixed with `~`; the number is rounded
to the nearest thousand when it is 1,000 or more, and is the exact count below 1,000.

The card SHALL show the book's cover image as a thumbnail, and WHERE the book has no cover — every plain-text and
Markdown book, and an EPUB or FB2 that declares none — SHALL show a neutral cover placeholder in its place.

The card SHALL offer `Continue to Book Brief` and `Cancel`. `Cancel` SHALL release the opened book and return the
screen to its empty drop zone.

The card SHALL NOT show a row for a language detected from the book's text, because no text-based language
detection exists.

WHEN a different book is opened while a book is open and no run of the open book can continue, the screen SHALL replace
the open book with the new one and release the first. WHERE a run of the open book can continue, the `app-shell`
capability's "Ask before an import replaces a translation that can continue" applies first.

**Source:** FR-IMPORT-02, FR-IMPORT-06, FR-IMPORT-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`, ADR-0037
(`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`), ADR-0039
(`docs/adr/ADR-0039-import-refusal-kind-is-data.md`).
In plain words: the card is where a person confirms the application recognised the right book before spending
hours on it, so it now reports everything the reference drawing shows and the parse can actually back — the cover,
the format version, chapters and words, images and fonts. Each row is still allowed to be absent when the book does
not say, because inventing a title or a cover would make the card look more certain than the file is. The
detected-language row the drawing also shows is left out on purpose: the application reads the language from the
book's metadata only, so a "detected" value would be a restatement dressed up as evidence. The chapters are the
top-level nodes of the structure tree that `document-round-trip` builds, so the card and the structure screen always
agree. How the two key names are held in code is a design matter (`design.md` D11), not a behaviour.

#### Scenario: An EPUB opens and reports itself

- **WHEN** `Frankenstein.epub`, an EPUB whose package declares version `2.0`, language `en`, title `Frankenstein`
  and author `Mary Shelley`, with 11 top-level table-of-contents entries, 78,214 words of body text, 7 images, no
  embedded fonts and a declared cover image, is opened
- **THEN** the card reads `Frankenstein.epub`, `EPUB 2.0`, `Frankenstein · Mary Shelley`, `English (en)`,
  `11 · ~78,000 words`, `7 · 0` and DRM `none`
- **AND** the book's cover image is shown as a thumbnail
- **AND** `Continue to Book Brief` and `Cancel` are available

#### Scenario: A regional declaration is shown normalized

- **WHEN** an EPUB whose package declares the language `en-US` is opened
- **THEN** the declared-language row reads `English (en)`

#### Scenario: A plain-text book reports what little it declares

- **WHEN** a valid `notes.txt` holding 640 words, declaring no title, no author and no language, is opened
- **THEN** the card reads `notes.txt`, `TXT`, `1 · ~640 words`, `0 · 0` and DRM `none`
- **AND** the title and declared-language rows are absent rather than empty, and no error is shown
- **AND** the cover placeholder is shown in place of a cover
- **AND** `Continue to Book Brief` is available

#### Scenario: A Markdown book with a title but no author shows the title alone

- **WHEN** a valid `notes.md` whose frontmatter states `title: Field Notes` is opened
- **THEN** the title row reads `Field Notes`, with no separator and no author
- **AND** the cover placeholder is shown

#### Scenario: A Markdown book without a frontmatter title omits both rows

- **WHEN** a valid `notes.md` whose frontmatter states no `title` is opened
- **THEN** the single `Title · Author` row, which would carry both, is absent, and the chapter, word, image and font
  rows are reported
- **AND** `Continue to Book Brief` is available

#### Scenario: An FB2 book reports its format without a version

- **WHEN** a valid `Forrest_Gump.fb2` is opened
- **THEN** the format row reads `FB2`

#### Scenario: The two metadata keys are named once

- **WHEN** an EPUB whose package declares the title `Frankenstein` and the creator `Mary Shelley`, and an FB2 whose
  title information declares the `book-title` `Kobzar` and the author `Taras Shevchenko`, are opened
- **THEN** both parsed books carry their title and author under the same two metadata keys, `title` and `author`
- **AND** the two cards read `Frankenstein · Mary Shelley` and `Kobzar · Taras Shevchenko`

#### Scenario: Cancel releases the book

- **WHEN** `Frankenstein.epub` is reported on the card and `Cancel` is pressed
- **THEN** the book is released and the screen shows only the drop zone
- **AND** the Book Brief reports that no book is open

#### Scenario: A protected book is refused with its own message

- **WHEN** a book whose content documents are encrypted is opened
- **THEN** the screen shows the DRM-blocked state under the banner `This book is DRM-protected.` with the status
  `Import blocked`, not the detected-file card
- **AND** no control to continue becomes available

#### Scenario: A file the application cannot read is refused

- **WHEN** a file named `notes.pdf` is dropped onto the screen
- **THEN** the screen shows the Unsupported state under the banner `Couldn't read this file.`, naming the detected type
  `PDF`
- **AND** no control to continue becomes available

#### Scenario: A damaged book of a supported format is refused with its reason

- **WHEN** `broken.epub`, a file whose ZIP container is truncated, is opened
- **THEN** the screen shows a refusing state carrying the reason the opening gave and `ErrorCode.validation`
- **AND** no control to continue becomes available

#### Scenario: Opening a second book replaces the first

- **WHEN** a book is open, no run of it can continue, and a different book is opened
- **THEN** the screen reports the second book, and the first is released

## ADDED Requirements

### Requirement: Normalize a declared language code before using it

WHEN a book's declared language code is read, the system SHALL normalize it before showing it or preselecting it:
letter case SHALL be folded to the standard form, the retired code `ua` SHALL become `uk`, a region SHALL be dropped
(`en-US` → `en`, `pt-BR` → `pt`), except that Chinese SHALL keep its script distinction — `zh`, `zh-CN` and `zh-SG`
SHALL become `zh-Hans`, and `zh-TW` and `zh-HK` SHALL become `zh-Hant`.

IF the normalized code is not a well-formed language tag the application can name, THEN the system SHALL treat the
declaration as unrecognized rather than guessing the nearest language. A code the application can name is recognized
whether or not the Book Brief lists it.

**Source:** FR-IMPORT-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-LANG-1
(`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), ADR-0037
(`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`).
In plain words: books spell the same language many ways — `EN`, `en-GB`, the old `ua` for Ukrainian — and each
spelling would otherwise look like a different language, fail to preselect anything on the Book Brief, and raise
false mismatch warnings. Chinese is the one case where the part after the dash matters, because Simplified and
Traditional Chinese are written differently and are separate choices on the brief.

#### Scenario: Case is folded

- **WHEN** a book declares `EN`
- **THEN** the normalized language is `en`

#### Scenario: The retired Ukrainian code is mapped

- **WHEN** an FB2 book declares `<lang>ua</lang>`
- **THEN** the normalized language is `uk` and the card reads `Ukrainian (uk)`

#### Scenario: A region is dropped

- **WHEN** a book declares `en-US`
- **THEN** the normalized language is `en`

#### Scenario: Traditional Chinese keeps its script

- **WHEN** a book declares `zh-TW`
- **THEN** the normalized language is `zh-Hant`

#### Scenario: Bare Chinese means Simplified

- **WHEN** a book declares `zh`
- **THEN** the normalized language is `zh-Hans`

#### Scenario: A language outside the list is recognized

- **WHEN** a book declares `la`
- **THEN** the normalized language is `la`, the card reads `Latin (la)` and the language verdict is match

#### Scenario: An unknown code is not guessed

- **WHEN** a book declares `xx-yy`
- **THEN** no language is derived from it and the declaration is treated as unrecognized

### Requirement: Warn when an EPUB's own language declarations disagree

WHEN an EPUB is opened whose package declares one language and more than half of its content documents that declare
a language declare a different one (both compared after normalization), the import screen SHALL show a
language-mismatch warning naming both languages by display name, SHALL still show the detected-file card, SHALL keep
`Continue to Book Brief` available, and SHALL preselect the content documents' language as the book's source
language on the Book Brief.

WHERE no single language is declared by more than half of the content documents that declare one, or no content
document declares a language, the system SHALL raise no warning and SHALL use the package's language.

WHERE the package declares no language and more than half of the content documents that declare one agree, the
system SHALL raise no warning and SHALL preselect that language as the source.

A book in any other format SHALL NOT raise this warning: FB2 and Markdown carry a single declaration and plain text
carries none.

**Source:** FR-IMPORT-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), EC-LANG-1
(`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`, ADR-0037
(`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`).
In plain words: the application does not guess a language from the text, but an EPUB often contradicts itself — the
package says English while every chapter file says Ukrainian — and that contradiction is worth a warning. The
chapters' own declaration is preferred because it sits next to the text and is written by whatever produced the
text, while the package language is often a template default. The person is warned, not stopped: the source
language stays editable on the brief, so a wrong preselection costs one click.

#### Scenario: The chapters outvote the package

- **WHEN** `Witcher.epub`, whose package declares `en` and whose 24 content documents all declare `xml:lang="uk"`,
  is opened
- **THEN** a language-mismatch warning names `English` as declared by the package and `Ukrainian` as declared by
  the content
- **AND** the detected-file card is shown and `Continue to Book Brief` is available
- **AND** the Book Brief opens with `Ukrainian` preselected as the source language

#### Scenario: Region differences are not a mismatch

- **WHEN** an EPUB whose package declares `en-US` and whose content documents declare `lang="en"` is opened
- **THEN** no language-mismatch warning is shown

#### Scenario: A tie raises no warning

- **WHEN** an EPUB whose package declares `en` has two content documents declaring `uk` and two declaring `en`
- **THEN** no warning is shown and the source language preselected on the Book Brief is `English`

#### Scenario: Silent content documents raise no warning

- **WHEN** an EPUB whose package declares `fr` has content documents that declare no language at all
- **THEN** no warning is shown and the preselected source language is `French`

#### Scenario: A missing package language is filled from the chapters

- **WHEN** an EPUB whose package declares no language has content documents all declaring `de`
- **THEN** no warning is shown and the preselected source language is `German`

#### Scenario: A plain-text book never warns

- **WHEN** `notes.txt` is opened
- **THEN** no language-mismatch warning is shown and the Book Brief starts with no source language chosen

### Requirement: Warn when a book declares a language the application does not recognize

IF a book's declared language, after normalization, is not a language tag the application can name, THEN the
import screen SHALL show a warning quoting the declared code as written and saying the source language must be
chosen on the Book Brief, SHALL keep `Continue to Book Brief` available, and SHALL leave the Book Brief with no
source language chosen.

**Source:** FR-IMPORT-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), ADR-0037
(`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`.
In plain words: a code the application cannot name is not a language it can preselect, and silently ignoring it
would look the same as a book that declares nothing. Quoting the code as written lets the person see what the book
claimed and choose accordingly.

#### Scenario: An unrecognized code is quoted and nothing is preselected

- **WHEN** an EPUB whose package declares `xx-yy` is opened
- **THEN** a warning shows the code `xx-yy` and says the source language must be chosen on the Book Brief
- **AND** `Continue to Book Brief` is available
- **AND** the Book Brief opens with no source language chosen

### Requirement: Block a DRM-protected book and name its encryption

WHEN a book whose content is DRM-encrypted is chosen, the import screen SHALL show a DRM-blocked state: an error
banner titled `This book is DRM-protected.` saying that encrypted files can't be opened or translated, the file name,
the encryption scheme by name where the inspection names one — from the book's encryption manifest, or
`ZIP encryption` for a zipped FB2 whose entry is encrypted — a status row reading `Import blocked`, a note that only
DRM-free EPUB and FB2 files are supported, and a single `Choose another file` action. The state SHALL offer no way to
continue.

WHERE the encryption scheme cannot be identified, the state SHALL omit the scheme row and SHALL still block the
import.

Opening such a book through any caller other than the import screen — the command line included — SHALL still fail
with `ErrorCode.validation`, exactly as before this state existed.

**Source:** FR-IMPORT-04, FR-IMPORT-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`),
EC-DRM-1 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`, ADR-0039
(`docs/adr/ADR-0039-import-refusal-kind-is-data.md`).
In plain words: a protected book is not broken and not the person's mistake, so it gets its own screen that says
exactly why it cannot be used and points at the only way forward — another file. Naming the scheme ("Adobe ADEPT")
tells the person which shop's protection this is. The error vocabulary is unchanged, so scripts that rely on the
command line's exit codes see nothing new. The banner title is the reference drawing's own text, and the banner the
notifications capability raises for this state uses the same title.

#### Scenario: An Adobe-protected EPUB is blocked by name

- **WHEN** `Purchased_Novel.epub`, whose encryption manifest encrypts its content documents under the Adobe ADEPT
  scheme, is dropped onto the screen
- **THEN** the screen shows the DRM-blocked state under the banner `This book is DRM-protected.`, with the file
  `Purchased_Novel.epub`, the encryption `Adobe ADEPT` and the status `Import blocked`
- **AND** the only action is `Choose another file`, and no control to continue is available

#### Scenario: A password-protected zipped FB2 is blocked by name

- **WHEN** `Kobzar.fb2.zip`, whose `Kobzar.fb2` entry carries the zip encryption flag, is chosen
- **THEN** the DRM-blocked state is shown with the file `Kobzar.fb2.zip`, the encryption `ZIP encryption` and the
  status `Import blocked`

#### Scenario: An unidentified scheme still blocks

- **WHEN** an EPUB whose content documents are encrypted under an algorithm the application does not recognize is
  chosen
- **THEN** the DRM-blocked state is shown without an encryption row, with the status `Import blocked`

#### Scenario: Font obfuscation is not DRM

- **WHEN** an EPUB whose only encrypted entries are fonts obfuscated with the IDPF font-obfuscation algorithm is
  chosen
- **THEN** the detected-file card is shown, with DRM `none`

#### Scenario: The command line still refuses with the same code

- **WHEN** `Purchased_Novel.epub` is translated from the command line
- **THEN** opening it fails with `ErrorCode.validation` and the command exits with the same exit code as before this
  change

### Requirement: Refuse a file of an unsupported type and name what it is

WHEN a file whose type is not one the application reads is chosen, the import screen SHALL show an Unsupported
state: an error banner titled `Couldn't read this file.` saying that it looks malformed or isn't a supported format,
the file name, the detected type followed by the badge
`not supported`, the hint that the supported formats are EPUB, FB2 (`.fb2` / `.fb2.zip`), Markdown and TXT, and a
single `Choose another file` action. The state SHALL offer no way to continue.

WHERE the file's type cannot be recognized at all, the detected type SHALL read `Unknown`.

Opening such a file through any caller other than the import screen SHALL still fail with `ErrorCode.validation`.

**Source:** FR-IMPORT-01, FR-IMPORT-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`, ADR-0039
(`docs/adr/ADR-0039-import-refusal-kind-is-data.md`).
In plain words: when someone drops a PDF, "unsupported" alone leaves them wondering whether the file is damaged;
saying "PDF — not supported" and listing what is supported answers the question in one glance. As with DRM, the
distinction is carried as data for the screen, and the error code every other caller sees is unchanged. The banner
title is the reference drawing's own text, and the banner the notifications capability raises for this state uses the
same title.

#### Scenario: A PDF is named and refused

- **WHEN** `book.pdf`, whose bytes begin with `%PDF-`, is dropped onto the screen
- **THEN** the screen shows the Unsupported state under the banner `Couldn't read this file.`, with the file
  `book.pdf` and the detected type `PDF` with the badge `not supported`
- **AND** the hint lists EPUB, FB2 (`.fb2` / `.fb2.zip`), Markdown and TXT
- **AND** the only action is `Choose another file`

#### Scenario: A Word document is named and refused

- **WHEN** `report.docx` is chosen
- **THEN** the Unsupported state shows the detected type `DOCX`

#### Scenario: An unrecognizable file is refused as unknown

- **WHEN** `mystery.bin`, holding 2,048 random bytes, is chosen
- **THEN** the Unsupported state shows the detected type `Unknown`

#### Scenario: Choosing another file leaves the state

- **WHEN** the Unsupported state is shown and `Choose another file` is pressed and `Frankenstein.epub` is chosen
- **THEN** the detected-file card for `Frankenstein.epub` replaces the Unsupported state

### Requirement: Show no preview-state switcher on the import screen

The import screen SHALL NOT show the "Preview state" selector the reference rendering draws above the drop zone; the
screen's state SHALL be decided only by the file that was chosen.

**Source:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`,
`docs/specification/mockups/ui-mockup.html` (the Import screen's "Preview state" row).
In plain words: the switcher exists in the reference drawing so a reader can flip between the four import states
without real files; in the application it would let a person display a "DRM blocked" card for a book that is fine,
so it is left out.

#### Scenario: The switcher is absent

- **WHEN** the import screen is shown with no book chosen
- **THEN** it shows the heading, the supported-formats line and the drop zone, and no control that selects between
  Detected, Language mismatch, DRM blocked and Unsupported

## REMOVED Requirements

### Requirement: Describe the language-mismatch state without a way to reach it

**Reason**: The language-mismatch state is now reachable. ADR-0037 decides that the book's own metadata raises it —
an EPUB whose package language disagrees with the language most of its content documents declare, or a declared code
the application does not recognize — so the clause "no input places the screen in that state" is no longer true, and
its scenario "Opening a book never reaches that state" would fail.

**Migration**: Replaced by *Warn when an EPUB's own language declarations disagree* and *Warn when a book declares a
language the application does not recognize*, which state the triggers, what the warning names, and what the Book
Brief preselects.
