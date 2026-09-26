# export Specification

## Purpose
Writes a translated book in its source format. The destination is replaced only after the written file has been opened
again and checked, so an existing file is never replaced by a broken book.

## Requirements

### Requirement: Write the book in its source format with the target language

WHEN a job's translation stage ends, the system SHALL write the book in its source format, with each ACCEPTED segment's
translation and each FLAGGED segment's source text. It SHALL declare the target language where the format has a place
for it: `dc:language` in an EPUB package, `lang` in an FB2 title-info. Markdown and TXT carry no language.

**Source:** FR-EXPORT-01, FR-EXPORT-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-DOC-06 (`#fr-doc`);
the shipped `document-round-trip` requirements "Set the target language by replacing the first dc:language", "Set the
FB2 target language without touching the recorded source language" and "Declare no language metadata in formats that
carry none" (`openspec/specs/document-round-trip/spec.md`).
In plain words: the output opens in the same readers as the source, keeps its formatting, images and structure, and
says it is in the new language. Where each format records its language is already built and specified; this
requirement is that a job passes its target language through to it.

#### Scenario: An EPUB paragraph with inline markup

- **WHEN** an EPUB whose paragraph is `<p>Tom &amp; <i>Jerry</i> ran.</p>` is translated to `uk` with the pseudo model
- **THEN** the written paragraph is `<p>TOM &amp; <i>JERRY</i> RAN.</p>`
- **AND** the written package declares `dc:language` `uk`

#### Scenario: An FB2 book declares the target language

- **WHEN** an FB2 book whose title-info holds `<lang>en</lang>` is translated to `uk`
- **THEN** the written book's title-info holds `<lang>uk</lang>`

#### Scenario: A flagged segment keeps its source text

- **WHEN** the paragraph `He opened the *old* door.` of a Markdown book is FLAGGED
- **THEN** the written book contains `He opened the *old* door.` unchanged

### Requirement: Check the written book before it replaces the destination

The system SHALL write the translated book to a temporary file in the destination folder and open that file again. It
SHALL move the file onto the destination only when it opens with as many segments as the source, and SHALL replace an
existing destination only when overwrite is on.

IF any of the following happens, THEN the system SHALL leave the destination unchanged, remove the temporary file, and
report the error:

- the source file changed since the job started;
- writing fails;
- the written file does not open;
- the written file's segment count differs from the source's.

**Source:** FR-EXPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`),
`02_Architecture/09_ERROR_HANDLING.md#partial-results`.
In plain words: a half-written file, or a book that silently lost paragraphs, never takes the place of a good one.
Counting segments after opening the written file again catches content a translation damaged without raising an error.

#### Scenario: A segment-count mismatch leaves the destination alone

- **WHEN** `Book.uk.md` exists, overwrite is on, and the written file opens with 2 segments where the source has 3
- **THEN** the job reports `ErrorCode.validation`
- **AND** `Book.uk.md` is unchanged and no temporary file remains in its folder

#### Scenario: A failed write leaves nothing behind

- **WHEN** the destination folder is deleted as the export stage starts
- **THEN** the job reports the write error, and neither the destination nor a temporary file exists

#### Scenario: The source changed during the job

- **WHEN** the bytes of the source file change after the job has read its segments and before export
- **THEN** the job reports `ErrorCode.validation` and nothing is written

#### Scenario: Overwrite replaces an existing destination

- **WHEN** `Book.uk.md` exists, overwrite is on, and the written file opens with the source's segment count
- **THEN** `Book.uk.md` holds the new translation and no temporary file remains in its folder

### Requirement: Decide where the translation is written before the run starts

The application SHALL let a person choose the file the translation is written to, and whether an existing file
at that path may be replaced, on the book-brief screen — before a run is started, not after it finishes.

The application SHALL propose a default path beside the source book whose name is the source name with the
target language inserted before the suffix, and SHALL recompute that proposal when the target language changes
unless the person has edited the path themselves.

The rule that computes that name SHALL be the same code the command line uses, not a second implementation of it.

IF a file already exists at the chosen destination and replacement has not been allowed, THEN the book-brief
screen SHALL say so beside the destination, and SHALL do so when the destination is chosen rather than waiting
for the run to be refused.

**Source:** FR-EXPORT-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`.
In plain words: the destination belongs with the brief because it is part of the instruction you give before the
run, not something you decide afterwards — the run writes the file itself as its last act, so the path has to
exist before it starts. The reference rendering draws the field on the export screen instead; honouring that
would mean a person could start a run having never opened the last screen, and the run would be refused with a
message about a path they were never asked for. The naming rule is shared rather than copied because it is not
the obvious one-liner it looks like: it strips the format's real suffix, and for a `.fb2.zip` book that is two
extensions, not one — so a second implementation gets that case wrong quietly, on exactly the format nobody tests
by hand. The existing-file warning is stated here for the same reason: the run's refusal for that case arrives
two screens later, on the translating screen, about a path the person chose on this one.

#### Scenario: The default destination follows the source

- **WHEN** `/books/Frankenstein.epub` is open and the target language is `uk`
- **THEN** the proposed destination is `/books/Frankenstein.uk.epub`

#### Scenario: Changing the target language moves the proposal

- **WHEN** the proposed destination is `/books/Frankenstein.uk.epub` and the target language is changed to `de`
- **THEN** the proposed destination becomes `/books/Frankenstein.de.epub`

#### Scenario: An edited path is not overwritten by a proposal

- **WHEN** the destination has been edited by hand to `/archive/out.epub` and the target language is changed to
  `de`
- **THEN** the destination stays `/archive/out.epub`

#### Scenario: A composite suffix is stripped whole

- **WHEN** `/books/Kobzar.fb2.zip` is open and the target language is `uk`
- **THEN** the proposed destination is `/books/Kobzar.uk.fb2.zip`, not `/books/Kobzar.fb2.uk.zip`

#### Scenario: An occupied destination says so on the brief

- **WHEN** the destination is set to `/books/Frankenstein.uk.epub`, a file already exists there, and replacement
  has not been allowed
- **THEN** the book-brief screen reports that a file already exists at that path
- **AND** it reports it without a run having been started

#### Scenario: Replacing an existing file is a choice, not a default

- **WHEN** the book-brief screen is shown
- **THEN** the choice to replace an existing file at the destination is present and not chosen

### Requirement: Report the finished file

WHEN a run completes, the export screen SHALL report the book's format, the path the translated book was written
to, how many segments were accepted and how many were flagged, and SHALL offer to show the written file in the system
file manager, selecting the file where the platform supports it (macOS, Windows) and otherwise opening its folder.

The screen SHALL NOT offer to trigger an export and SHALL NOT carry a save-path field, because the run writes the
book as its final stage and the path was chosen with the brief.

The screen's auxiliary offerings — a glossary, a bilingual copy, a quality report and a final consistency pass —
SHALL be shown and unavailable.

**Source:** FR-EXPORT-01, FR-EXPORT-04, FR-EXPORT-05, FR-EXPORT-06
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`.
In plain words: there is no separate export step to press, because exporting is the run's last act — the file is
already on disk by the time this screen can show anything. The reference drawing has an export button and a save
path; keeping either would mean building a second way to write the file, which is engine work this change does
not do. Showing the file goes to the system's own file manager by a command built from the path alone, never
to a web browser and never through a shell, and it touches no network.

#### Scenario: A completed run reports its file

- **WHEN** a run over `1240` segments completes with `1237` accepted and `3` flagged, writing
  `/books/Frankenstein.uk.epub`
- **THEN** the screen reports that path, the format EPUB, `1237` accepted and `3` flagged
- **AND** offers to show the written file in the system file manager

#### Scenario: Showing the file selects it where the platform can

- **WHEN** the written file is `/books/Frankenstein.uk.epub` and the offer is taken
- **THEN** on macOS the command `open -R /books/Frankenstein.uk.epub` is run
- **AND** on Windows, with the file at `C:\books\Frankenstein.uk.epub`, the command
  `explorer.exe` with the single argument `/select,C:\books\Frankenstein.uk.epub` is run
- **AND** on Linux the command `xdg-open /books` is run, because there is no portable way to select a file

#### Scenario: Nothing to report before a run finishes

- **WHEN** the export screen is opened before any run has completed
- **THEN** it reports that no translated book has been produced yet, and offers nothing to reveal

#### Scenario: The screen triggers nothing

- **WHEN** the export screen is shown after a completed run
- **THEN** it carries no save-path field and no control that writes a book

#### Scenario: The auxiliary offerings are visible and unavailable

- **WHEN** the export screen is shown
- **THEN** the glossary, bilingual-copy, report and consistency-pass controls are present and unavailable
