## Purpose

Writes a translated book in its source format. The destination is replaced only after the written file has been opened
again and checked, so an existing file is never replaced by a broken book.

## ADDED Requirements

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
