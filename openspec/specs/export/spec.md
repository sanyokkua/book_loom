# export Specification

## Purpose
Writes a translated book in its source format, with no model call: the run's three stages (Prepare, Translate,
Proofread) have already decided every segment, and export reads those stored decisions. The written file is opened again
and checked, and the destination is replaced only after that check passes, so an existing file is never replaced by a
broken book. A segment whose formatting broke is written in its source and named, and the export reports exactly what
the file holds.

## Requirements

### Requirement: Write the book in its source format with the target language

WHEN an export is started, the system SHALL write the book in its source format from a fresh read of the source file, and SHALL declare the target language where the format has a place for it: `dc:language` in an EPUB package, `lang` in an FB2 title-info, and the value of an existing top-level `lang` key in a Markdown book's frontmatter. TXT, and a Markdown book whose frontmatter holds no `lang` key, carry no language.

**Source**: FR-EXPORT-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-DOC-06 (`#fr-doc`), ADR-0035, `design.md#d16--export-validates-and-writes-with-no-model-call`; the `document-round-trip` requirements "Set the target language by replacing the first dc:language", "Set the FB2 target language without touching the recorded source language" and "Declare no language metadata in formats that carry none". In plain words: the output opens in the same readers as the source, keeps its formatting, images and structure, and says it is in the new language. Reading the source afresh each time means two exports of the same decisions produce the same file. A Markdown book that already says `lang: en` is told it is now `uk`; one that says nothing is not given a key it never had.

#### Scenario: An EPUB paragraph with inline markup

- **WHEN** an EPUB whose paragraph is `<p>Tom &amp; <i>Jerry</i> ran.</p>` is translated to `uk` with the pseudo model
  and exported
- **THEN** the written paragraph is `<p>TOM &amp; <i>JERRY</i> RAN.</p>`
- **AND** the written package declares `dc:language` `uk`

#### Scenario: An FB2 book declares the target language

- **WHEN** an FB2 book whose title-info holds `<lang>en</lang>` is translated to `uk` and exported
- **THEN** the written book's title-info holds `<lang>uk</lang>`

#### Scenario: A Markdown book's frontmatter language follows the target

- **WHEN** a Markdown book whose frontmatter holds `lang: en` is translated to `uk` and exported
- **THEN** the written book's frontmatter holds `lang: uk`

#### Scenario: A Markdown book without a language key is given none

- **WHEN** a Markdown book whose frontmatter holds only `title: The Lighthouse` is translated to `uk` and exported
- **THEN** the written book's frontmatter holds no `lang` key

#### Scenario: Two exports of the same decisions are the same book

- **WHEN** `Frankenstein.epub` is exported to `/books/a.epub` and then, with no decision changed, to `/books/b.epub`
- **THEN** the two files are canonical-equal

### Requirement: Write each segment with the best text it has

WHEN an export is started, the system SHALL write for each segment the best text it has: an ACCEPTED or FLAGGED segment's machine translation, a REVISED segment's stored text, a PENDING segment's source text, and the source text of every segment kept as source by choice, that is an auxiliary segment whose kind the book brief's "Also translate" switches leave off when the export starts.

**Source**: FR-EXPORT-01, FR-EXPORT-02 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-REVIEW-X2 (`01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), ADR-0035. In plain words: a book can be written at any point, so each segment is written with the best text it has: the person's edit over the machine's, the machine's over the source, and the source where nothing better exists yet.

#### Scenario: A flagged segment writes its machine translation

- **WHEN** the paragraph `He opened the *old* door.` of a Markdown book is FLAGGED with the machine translation
  `Він відчинив *старі* двері.`
- **THEN** the written book contains `Він відчинив *старі* двері.`

#### Scenario: A book paused part-way writes pending segments in the source

- **WHEN** a Markdown book is exported while its run is paused with the paragraph `The end.` still PENDING
- **THEN** the written book contains `The end.` unchanged, in the Markdown format

#### Scenario: A switched-off auxiliary kind keeps its source

- **WHEN** image alt text is switched off in "Also translate" and an image's alt text is `A ship at dawn`
- **THEN** the written image's alt text is `A ship at dawn`

### Requirement: Write a flagged segment in its source when no usable translation exists

IF a FLAGGED segment never had a translation that passed the placeholder check, or its draft failed a gate other than quote balance or script purity, THEN the system SHALL write its source text instead of its machine translation.

**Source**: FR-EXPORT-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-QA-01, ADR-0035. In plain words: an English leftover is never written as a translation; a flagged draft that only broke a soft rule is still the best text the book has.

#### Scenario: A flagged segment keeps its source text

- **WHEN** the paragraph `He opened the *old* door.` is FLAGGED because every reply for it failed the placeholder check
- **THEN** the written book contains `He opened the *old* door.` unchanged

### Requirement: Write a revised segment as stored

The system SHALL write a REVISED segment as stored: the text as the person saved it, with the typography pass applied and kept as saved if the normalised form would not restore, or the rewrite the Proofread stage kept, which already passed the run's gates.

**Source**: FR-EXPORT-02 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-REVIEW-X2 (`01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a person's edit does not pass through the run's gate, and a kept rewrite is machine text that already did, so both are written without a second check.

#### Scenario: A revised segment writes the person's text

- **WHEN** a segment's machine translation is `Він пішов.` and the person saved the edit `Він пішов геть.`
- **THEN** the written book contains `Він пішов геть.`

#### Scenario: A kept proofread rewrite is written

- **WHEN** the paragraph `Tarn will come tomorrow.` was accepted as `Тарн прийшов завтра.` and the Proofread stage kept the rewrite `Тарн прийде завтра.`
- **THEN** the written book contains `Тарн прийде завтра.`

### Requirement: Never write a proposed rewrite over the person's text

A rewrite the Proofread stage only proposed for a segment the person had edited SHALL NOT be written; the person's text is.

**Source**: FR-EXPORT-02 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a rewrite proposed for text the person edited waits for the person, so the person's words are never replaced silently.

#### Scenario: A proposed rewrite does not replace the person's text

- **WHEN** the person saved `Тарн прийде вранці.` for that paragraph and the Proofread stage only proposed `Тарн прийде завтра.`
- **THEN** the written book contains `Тарн прийде вранці.`

### Requirement: Check the written book before it replaces the destination

The system SHALL write the book to a temporary file beside the destination, under a name no file in that folder holds, so a file already there is never written over or removed, open it again, and move it onto the destination, replacing an existing file only when the person agreed, only when it has the source's body segment count and every body segment keeps its placeholders, alt text set aside; any other failure SHALL leave the destination unchanged.

**Source**: FR-EXPORT-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), `docs/spec_baseline/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, ADR-0041. In plain words: the book is proven to open with nothing lost before it takes the place of a good one; only body segments are counted, because auxiliary segments can legitimately merge or vanish on re-opening. The check is code only and makes no model call.

#### Scenario: A segment-count mismatch leaves the destination alone

- **WHEN** `Book.uk.md` exists, the person agreed to replace it, and the written file opens with 2 body segments where the source has 3
- **THEN** the export reports `ErrorCode.validation`, no side file is written
- **AND** `Book.uk.md` is unchanged and no temporary file remains in its folder

#### Scenario: A translated alt text inside a translated paragraph passes the check

- **WHEN** the EPUB paragraph `<p>Before <img src="fig1.png" alt="Figure 1"/> after</p>` is translated, its image's alt text `Figure 1` is translated as `Рисунок 1`, and the book is exported
- **THEN** the check passes and the book is written
- **AND** the written paragraph's image reads `<img src="fig1.png" alt="Рисунок 1" />`

#### Scenario: A translated alt text inside a pending paragraph passes the check

- **WHEN** the EPUB paragraph `<p>Before <img src="fig1.png" alt="Figure 1"/> after</p>` is still PENDING, its image's alt text `Figure 1` is translated as `Рисунок 1`, and the book is exported
- **THEN** the check passes and the book is written
- **AND** the written paragraph reads `<p>Before <img src="fig1.png" alt="Рисунок 1" /> after</p>`

#### Scenario: A translated Markdown alt text passes the check

- **WHEN** the Markdown paragraph `See ![Figure 1](fig1.png) below.` is translated, the image's alt text `Figure 1` is translated as `Рисунок 1`, and the book is exported
- **THEN** the check passes and the book is written
- **AND** the written paragraph holds `![Рисунок 1](fig1.png)`

#### Scenario: Overwrite replaces an existing destination

- **WHEN** `Book.uk.md` exists, the person agreed to replace it, and the written file opens with the source's body segment count and placeholders
- **THEN** `Book.uk.md` holds the new translation and no temporary file remains in its folder

#### Scenario: An NCX label that becomes equal to its navigation label passes the check

- **WHEN** an EPUB's NCX label `The Storm` differs from its navigation label `Storm`, both are translated as `Буря`, and the book is exported
- **THEN** the check passes and the book is written, although on re-opening the two labels are one auxiliary segment

#### Scenario: A failed write leaves nothing behind

- **WHEN** the destination folder `out` is deleted as the export starts
- **THEN** the export reports `ErrorCode.validation` with the details `reason=folder-missing, folder=out`, which the
  window words as advice to create the folder or choose another, and neither the destination nor a temporary file
  exists

#### Scenario: A hidden file beside the destination is left alone

- **WHEN** `.Book.uk.md` already stands beside the destination `Book.uk.md` and the book is exported
- **THEN** `Book.uk.md` holds the translation and `.Book.uk.md` is unchanged

#### Scenario: A full disk says what to fix

- **WHEN** writing the book fails because the disk has no space left
- **THEN** the export reports `ErrorCode.validation` with the details `reason=disk-full, folder=<the folder's name>`,
  and the window says in its display language that the disk is full and to free space or choose another disk

#### Scenario: The source changed during the job

- **WHEN** the bytes of the source file change after the book was opened and before the export
- **THEN** the export reports `ErrorCode.validation` and nothing is written

### Requirement: Write a segment with broken placeholders in its source and report it

IF a segment's stored target fails the placeholder gate, or its placeholders differ when the written book is opened again, THEN the system SHALL write that segment in its source and report it, and SHALL refuse the export only when the segment differs even in its source.

**Source**: FR-EXPORT-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), `docs/spec_baseline/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, ADR-0041. In plain words: one paragraph whose formatting broke is written in its source and named instead of costing the whole book.

#### Scenario: A placeholder mismatch writes that segment in its source and names it

- **WHEN** the segment `ch5 · p12` was written carrying `⟦g0⟧⟦g1⟧⟦g2⟧⟦g3⟧` and re-opens carrying `⟦g0⟧⟦g1⟧⟦g2⟧`, while the segment count matches
- **THEN** the book is written again with `ch5 · p12` in its source, `<b>He</b> saw …`, and published
- **AND** the report's source fallbacks are exactly `ch5 · p12`, it counts `ch5 · p12` as pending, and no temporary file remains in its folder

#### Scenario: A segment that differs even in its source still refuses the export

- **WHEN** the segment `ch5 · p12` re-opens with one placeholder fewer both as translated and as written in its source
- **THEN** the export reports `ErrorCode.validation` naming `ch5 · p12`
- **AND** the destination is unchanged and no temporary file remains in its folder

#### Scenario: A stored target with broken placeholders is written in its source

- **WHEN** `Book.md:0`, whose masked source is `He opened the ⟦g0⟧old⟦g1⟧ door.`, is ACCEPTED with the masked target `Зламано ⟦g0⟧тут` and `Book.md:1` is ACCEPTED as `Вона пішла.`
- **THEN** the written file reads `He opened the *old* door.` and `Вона пішла.`
- **AND** the report lists the source fallback `Book.md:0` and counts 1 segment written

#### Scenario: Two broken targets are both listed

- **WHEN** `Book.md:0` and `Book.md:1` both hold stored targets whose placeholders do not match their segments
- **THEN** the written file is `He opened the *old* door.` followed by `She *left*.`, exactly the source
- **AND** the report lists the source fallbacks `Book.md:0` then `Book.md:1` and counts 0 segments written

### Requirement: Report the finished file

WHEN an export succeeds, the export screen SHALL show the title "Translated book ready" and four tiles: the number of segments written with a translation ("segments translated"), the share of segments accepted without review ("auto-accepted"), the number of segments the person accepted, edited or reverted ("reviewed by you"), and whether the written file passed its verification ("file validates").

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/known-limits.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: after pressing Export the person sees that the book exists, that it was checked, and how much of it the machine did on its own; "auto-accepted" is counted over the segments that needed a decision, the written ones less those kept as they are, plus the pending ones.

#### Scenario: A completed run reports its file

- **WHEN** a run over `Frankenstein.epub` completes with `1240` segments, `1224` auto-accepted and `16` edited by the
  person, and the book is exported to `/books/Frankenstein.uk.epub`
- **THEN** the screen shows `Translated book ready` and the tiles `1,240` segments translated, `98.7%` auto-accepted,
  `16` reviewed by you, and a passed mark for file validates
- **AND** it lists `Re-opened and verified · structure, ids and fonts preserved` and
  `Language metadata updated (en → uk)`

### Requirement: Report every kind of segment the export wrote

WHEN an export succeeds, the system SHALL report with the written file the counts of segments written with a translation; pending and written in the source language (a flagged one with no machine translation among them); kept as source by choice; kept as they are because they have nothing to translate (also counted as written); flagged and written with their machine translation; accepted without review; and reviewed by the person.

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/known-limits.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the counts say exactly what the file holds, and they are what the tiles, the report side file and the command line's report line show.

#### Scenario: The export reports every kind of segment it wrote

- **WHEN** a book of `1260` segments, its `7` image descriptions switched off, is exported while `312` body segments are
  pending, `3` are flagged with a machine translation, `922` were accepted without review and `16` were reviewed by the
  person
- **THEN** the export reports `941` segments written with a translation, `312` pending written in the source language,
  `7` kept as source by choice, `3` flagged written with their machine translation, `922` accepted without review and
  `16` reviewed

#### Scenario: A flagged segment with no machine translation counts as pending

- **WHEN** a Markdown book of `12` body segments is exported with `10` accepted, `1` pending and `1` flagged whose every
  draft failed a hard gate, so it holds no machine translation
- **THEN** the export reports `10` segments written with a translation, `2` pending written in the source language and
  `0` flagged written with their machine translation

### Requirement: List the checks an export passed

WHEN an export succeeds, the export screen SHALL list the checks it passed: "Re-opened and verified · structure, ids and fonts preserved" and, for an EPUB, an FB2, or a Markdown book whose frontmatter holds a `lang` key, "Language metadata updated (<source> → <target>)".

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/known-limits.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the check line says what the application actually verified, which is that it re-opened the file, rather than the reference's EPUBCheck, which is not run.

#### Scenario: A format with no language metadata lists no language check

- **WHEN** `Book.md`, whose frontmatter holds no `lang` key, is exported to `/books/Book.uk.md`
- **THEN** the screen lists the re-open check and no language-metadata line

#### Scenario: A Markdown book that declares its language lists the language check

- **WHEN** `Notes.md`, whose frontmatter holds `lang: en`, is translated from `en` to `uk` and exported to
  `/books/Notes.uk.md`
- **THEN** the screen lists the re-open check and `Language metadata updated (en → uk)`

### Requirement: Show one export-complete dialog as the success notice

WHEN an export succeeds, the application SHALL show an export-complete dialog naming the written file, its folder, its verification result and its size, offering Close, Open folder and Open book, and SHALL raise no transient message for it: the dialog is the export's one success notice.

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/known-limits.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: one success notice is enough: the dialog already names the file, so a transient message on top of it would only repeat it.

#### Scenario: The export-complete dialog

- **WHEN** `/books/Frankenstein.uk.epub`, 936 KB, is written and verified
- **THEN** a dialog names `Frankenstein.uk.epub`, the folder `/books`, a passed verification and `936 KB`
- **AND** offers Close, Open folder and Open book

#### Scenario: The dialog is the only success notice

- **WHEN** `/books/Frankenstein.uk.epub` is written and verified
- **THEN** the export-complete dialog is shown
- **AND** no transient message is raised for the export

### Requirement: Show or open the written file with the system's own commands

The export screen's Open folder action and the dialog's SHALL show the written file in the system file manager, selecting it where the platform supports it (macOS, Windows) and otherwise opening its folder; an exit code of 1 from the Windows file manager SHALL count as success. Open book SHALL open the written file in the application the system associates with its type.

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/known-limits.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: Windows Explorer returns 1 even when it succeeds, so treating that as a failure would report an error for a file that was shown. The commands are built from the path alone, never a shell and never the network.

#### Scenario: Showing the file selects it where the platform can

- **WHEN** the written file is `/books/Frankenstein.uk.epub` and Open folder is taken
- **THEN** on macOS the command `open -R /books/Frankenstein.uk.epub` is run
- **AND** on Windows, with the file at `C:\books\Frankenstein.uk.epub`, the command `explorer.exe` with the single
  argument `/select,C:\books\Frankenstein.uk.epub` is run
- **AND** on Linux the command `xdg-open /books` is run, because there is no portable way to select a file

#### Scenario: Explorer's exit code 1 is a success

- **WHEN** Open folder is taken on Windows and `explorer.exe` exits with code 1
- **THEN** no failure is reported

#### Scenario: Opening the book uses the system's viewer

- **WHEN** Open book is taken on macOS for `/books/Frankenstein.uk.epub`
- **THEN** the command `open /books/Frankenstein.uk.epub` is run

### Requirement: State the side files and the proofread summary

WHEN an export succeeds, the screen's checks and the dialog SHALL also name each side file written, one per line, and state the proofread summary, "Proofread: 12 segments re-read, 3 waiting for you", which gives both counts also when one is 0, and neither SHALL mention a model pass the export makes.

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/known-limits.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the proofread line says how much of the book the Proofread stage read and how much still waits for the person, from the counts stored with the export; the export itself makes no model call.

#### Scenario: The proofread summary is reported

- **WHEN** `Frankenstein.epub` is exported after its Proofread stage re-read 12 segments and 3 still wait
- **THEN** the screen's checks and the export-complete dialog read "Proofread: 12 segments re-read, 3 waiting for you"

#### Scenario: A Fast book reports what waits

- **WHEN** a Fast book is exported with 4 segments carrying a proofread mark and none re-read
- **THEN** the checks read "Proofread: 0 segments re-read, 4 waiting for you"

#### Scenario: The export names no model pass

- **WHEN** any book is exported
- **THEN** no check line and no dialog line mentions a consistency pass

### Requirement: Name each source fallback by its locator

WHEN any segment was a source fallback (the requirement "Write a segment with broken placeholders in its source and report it"), the screen and the dialog SHALL say how many, and name each by its locator, in a warning line: "2 segments were written in the source language because their translation broke the formatting: ch12 · p02, ch12 · p06", worded with the language's plural forms.

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/known-limits.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the person learns which paragraphs stayed in the source language and can fix them in the review desk.

#### Scenario: A source fallback is named after the export

- **WHEN** an export of an EPUB succeeds with the one source fallback `ch12 · p02`
- **THEN** the export screen and the export-complete dialog read "1 segment was written in the source language
  because its translation broke the formatting: ch12 · p02"

### Requirement: Forget an export result the next press will not write

WHEN the destination, Replace or the chosen side files change after a success, the export screen SHALL forget that result, so its tiles, checks, Open folder and Open book never describe a file the next press will not write. WHEN the destination occupied after a success is the file just exported, the screen SHALL say so beside Save to in a neutral line, not as a refusal, and Export book SHALL wait for Replace or another name.

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/known-limits.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: a stale result would claim a file that the next press will not write; the neutral line says why Export book waits instead of refusing.

#### Scenario: A changed destination clears the result

- **WHEN** the export of `/books/a.epub` succeeded and the destination is changed to `/books/b.epub`
- **THEN** the tiles, the checks, Open folder and Open book no longer describe `/books/a.epub`

#### Scenario: The file just exported is named in a neutral line

- **WHEN** the destination is `/books/a.epub`, the file just exported, and Replace is off
- **THEN** the line `This is the file just exported. To write it again, turn on Replace or choose another name.` shows beside Save to, and Export book waits

### Requirement: Show no export result before an export has succeeded

WHILE no export of the open book has succeeded in the session, the export screen SHALL list no checks, SHALL show the file-validates tile as not yet known, and SHALL offer nothing to reveal or open. WHILE a run is translating, the export screen SHALL offer no control that writes a book, and its side-file choices SHALL stay visible and unavailable.

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/known-limits.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the screen never shows a result that does not exist, and a book is not written while the run still changes it.

#### Scenario: Nothing to report before a run finishes

- **WHEN** `Frankenstein.epub` is open, its run is still paused at 78%, and no export has been written
- **THEN** the export screen lists no checks, shows the file-validates tile as `—`, and offers neither Open folder nor
  Open book

#### Scenario: The screen triggers nothing

- **WHEN** the export screen is shown while the run over `Frankenstein.epub` is running at 42%
- **THEN** no control on it that writes a book is available

#### Scenario: The auxiliary offerings are visible and unavailable

- **WHEN** the export screen is shown while the run over `Frankenstein.epub` is running at 42%
- **THEN** the glossary, bilingual-copy and report choices are present and unavailable

### Requirement: Choose where to save on the export screen

The export screen SHALL show where the book is saved as a short path such as "Books ▸ earth-gravity.uk.md (in your home folder)" whose tooltip holds the full path, with Change… that opens the system's save dialog and "✦ Suggest a name" that proposes a file name.

**Source**: FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, docs/spec_baseline/specification/mockups/README.md#step-5--export--e1e3-i. In plain words: the person sees a readable place, not a long absolute path, and can still read the full path on hover; the name matches the command line's because for a `.fb2.zip` book the suffix is two extensions and the two must never disagree about where a book goes.

#### Scenario: The Save to field and Browse are present

- **WHEN** the export screen is shown for the open project
- **THEN** it shows the short path "Books ▸ earth-gravity.uk.md (in your home folder)" with the full path as its tooltip, a Change… button and a "✦ Suggest a name" button

#### Scenario: Change… opens the system dialog

- **WHEN** the person presses Change…
- **THEN** the system's save dialog opens on the current folder and the chosen path replaces the short path

### Requirement: Propose a default save path beside the source book

WHEN a book is open and the person has not edited the Save to path, the export screen SHALL propose a path beside the
source book whose name is the source name with the target language inserted before the format's suffix, and SHALL
recompute that proposal whenever the target language changes.

**Source**: FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`. In plain words: the person gets a sensible destination without typing, and it follows the target language until they choose their own; for a `.fb2.zip` book the suffix is two extensions and stays whole.

#### Scenario: The default path follows the source

- **WHEN** `/books/Frankenstein.epub` is open, the target language is `uk`, and the Save to path has not been edited
- **THEN** the Save to field holds `/books/Frankenstein.uk.epub`

#### Scenario: Changing the target language moves the proposal

- **WHEN** the Save to field holds the proposal `/books/Frankenstein.uk.epub` and the target language is changed to `de`
- **THEN** the Save to field holds `/books/Frankenstein.de.epub`

#### Scenario: A composite suffix is kept whole

- **WHEN** `/books/Kobzar.fb2.zip` is open and the target language is `uk`
- **THEN** the proposed path is `/books/Kobzar.uk.fb2.zip`, not `/books/Kobzar.fb2.uk.zip`

### Requirement: Keep a save path the person edited

WHEN the person has edited the Save to path, by typing or through Browse, the export screen SHALL keep that path when
the target language changes.

**Source**: FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035. In plain words: a destination the person chose is theirs, and changing the target language must not silently move it.

#### Scenario: An edited path is kept

- **WHEN** the Save to path has been chosen through Browse as `/archive/out.epub` and the target language is changed to
  `de`
- **THEN** the Save to field still holds `/archive/out.epub`

### Requirement: Name the proposed file as the command line names its output

The proposed name SHALL be the name the command line gives its output for the same source file and target language; WHERE the project is named and another project of the same book has the same pair, it SHALL add after the language code a label: the part of the project name after its automatic name, else after the book title, else the whole name, lower-cased, spaces turned into hyphens, keeping only letters, digits and hyphens.

**Source**: FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, docs/spec_baseline/specification/mockups/README.md#project-names--one-book-several-translations-pn. In plain words: the screen and the command line never disagree about where a book goes, and a second translation of the same pair never replaces the first on disk.

#### Scenario: The command line names its output the same way

- **WHEN** the command line translates `/books/earth-gravity.fb2.zip` to `uk` without an explicit output path
- **THEN** it writes `/books/earth-gravity.uk.fb2.zip`, the same path the export screen proposes

#### Scenario: Two projects with the same pair never collide

- **WHEN** the automatic project `Earth Gravity · EN → UK` and the named project `Earth Gravity · 26b max`, also English to Ukrainian, of the same book are exported as Markdown
- **THEN** the files are `earth-gravity.uk.md` and `earth-gravity.uk.26b-max.md`

#### Scenario: A name that extends the automatic name keeps only the added part

- **WHEN** a second English → Ukrainian project of `earth-gravity.md` is named `Earth Gravity · EN → UK · formal`
- **THEN** its proposed file is `earth-gravity.uk.formal.md`

#### Scenario: A project alone with its pair gets no label

- **WHEN** the only English → Ukrainian project of `earth-gravity.md` is named `Earth Gravity · 26b max`
- **THEN** its proposed file is `earth-gravity.uk.md`

### Requirement: Replace an existing file only when the person allows it

IF a file already exists at the Save to path, or at the path of a side file that is chosen, THEN the export SHALL ask before replacing it, naming the file, and SHALL replace it only when the person answers that it may be replaced; the screen SHALL carry no replace switch.

**Source**: FR-EXPORT-03, FR-EXPORT-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, docs/spec_baseline/specification/mockups/README.md#step-5--export--e1e3-i. In plain words: an existing book is never replaced by accident, and the protection is a question at the moment it matters; side files follow the same rule.

#### Scenario: Replacing is off by default

- **WHEN** the export screen is shown for an open project
- **THEN** it shows no replace switch, and nothing is replaced unless the person answers the question

#### Scenario: An occupied path is reported at once

- **WHEN** `/books/earth-gravity.uk.epub` already exists and the person presses Export book
- **THEN** the screen asks whether `/books/earth-gravity.uk.epub` may be replaced before anything is written

#### Scenario: Allowing replacement releases the action

- **WHEN** that question is answered with replace
- **THEN** the book is written and checked, and then moved onto `/books/earth-gravity.uk.epub`

#### Scenario: An occupied side-file path is reported too

- **WHEN** the glossary side file is chosen, `/books/earth-gravity.uk.glossary.csv` already exists and the book's own path is free
- **THEN** the screen asks about `/books/earth-gravity.uk.glossary.csv` before writing, and declining leaves that file untouched

#### Scenario: Declining keeps the existing file

- **WHEN** the person answers that `/books/earth-gravity.uk.epub` must not be replaced
- **THEN** no file is written and the existing file is unchanged

### Requirement: Refuse a destination the book cannot be written to

IF the Save to path is the source file itself, or its file type differs from the source's — a zipped FB2 book
(`.fb2.zip`) and a plain one (`.fb2`) being different types, while `.md` and `.markdown` are both Markdown — THEN the
export screen SHALL say so beside the Save to field and SHALL make Export book unavailable, and an export requested with
that path SHALL fail with `ErrorCode.validation` without writing anything.

**Source**: FR-EXPORT-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/spec_baseline/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`. In plain words: these checks used to guard the start of a run, which wrote the book; now the export writes it, so they guard the export. A translation must never be written over the book it was made from, and a book is written back in the container it came in, so a zipped FB2 given a plain name would only fail its own re-open check after writing.

#### Scenario: The source itself is refused

- **WHEN** the Save to path is set to `/books/Frankenstein.epub`, the open book's own file
- **THEN** the screen says the translation cannot replace its source and Export book is unavailable

#### Scenario: A zipped FB2 book is not written under a plain FB2 name

- **WHEN** `/books/Kobzar.fb2.zip` is open and the Save to path is set to `/books/Kobzar.uk.fb2`
- **THEN** the screen says the file type must stay `.fb2.zip` and Export book is unavailable

#### Scenario: A Markdown book may take either Markdown suffix

- **WHEN** `/books/Book.md` is open and the Save to path is set to `/books/Book.uk.markdown`
- **THEN** no file-type warning is shown

### Requirement: Export at any time except while a run is translating

WHILE the run state is Translating, Stuck call, Pausing or Stopping, Export book SHALL be disabled with the tooltip "Pause or stop the translation to export"; with nothing translated yet it SHALL be disabled with the banner "Nothing is translated yet."; otherwise it SHALL be available.

**Source**: FR-REVIEW-07 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-REVIEW-X1 (`docs/spec_baseline/specification/01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), ADR-0035, docs/spec_baseline/specification/mockups/README.md#changes-while-translating-i, docs/spec_baseline/specification/mockups/README.md#run-states-a. In plain words: the book can be taken out whenever something is translated and the run is not actively deciding segments.

#### Scenario: A paused run can be exported

- **WHEN** the translation of `Earth Gravity · EN → UK` is paused with 74 of 142 segments translated
- **THEN** Export book is available and the banner reads "74 of 142 segments are translated. The other segments keep the original text"

#### Scenario: A running run cannot be exported

- **WHEN** the translation is running at 51 %
- **THEN** Export book is disabled with "Pause or stop the translation to export"

#### Scenario: A book with no run can be exported

- **WHEN** the project has been created and nothing is translated yet
- **THEN** Export book is disabled and the banner reads "Nothing is translated yet.", and the tiles show 0

#### Scenario: A run auto-paused by a provider error can be exported

- **WHEN** the translation is paused because `http://127.0.0.1:11434` stopped answering, with 74 of 142 segments translated
- **THEN** Export book is available with the partial banner

### Requirement: Hold Export book while an export is being written

WHILE an export is being written, the export screen SHALL make Export book unavailable and SHALL report the export as
in progress.

**Source**: FR-REVIEW-07 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-REVIEW-X1 (`docs/spec_baseline/specification/01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), ADR-0035. In plain words: a second press while the first export is still writing would start a second write of the same file.

#### Scenario: Export book is unavailable during a write

- **WHEN** the export of `Frankenstein.epub` to `/books/Frankenstein.uk.epub` is being written
- **THEN** Export book is unavailable and the screen reports the export as in progress

### Requirement: State what a partial export will write before it is written

WHEN the open book has segments that would not be written with a translation, the export screen SHALL state, before
Export book is pressed, how many segments will be written in the source language — the pending ones and the flagged
ones that have no machine translation — and how many flagged segments will be written with their machine
translation.

**Source**: FR-REVIEW-X2 (`docs/spec_baseline/specification/01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), FR-BRIEF-09 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), ADR-0035; "kept as source by choice" is defined by `translation-pipeline` (`openspec/changes/archive/2026-10-19-complete-translation-workflow/specs/translation-pipeline/spec.md`). In plain words: a partial book is allowed, but never a surprise — the person reads "312 segments will be written in the source language" before choosing to write it. Auxiliary text the person switched off on the Book Brief — say, every image description — is not waiting for anything, so counting it as pending would make a finished book look unfinished; it is named on its own line instead, so nothing written in the source language goes unmentioned.

#### Scenario: Pending and flagged counts are stated

- **WHEN** the book has `1240` segments, `312` still pending and `3` flagged
- **THEN** the screen states `312 segments will be written in the source language`
- **AND** states `3 flagged segments will be written with their machine translation`

#### Scenario: A flagged segment with no machine translation is announced as written in the source language

- **WHEN** the book has `12` segments, `1` pending, `3` flagged of which `1` has no machine translation
- **THEN** the screen states `2 segments will be written in the source language`
- **AND** states `2 flagged segments will be written with their machine translation`

#### Scenario: A complete book states nothing partial

- **WHEN** every one of the book's `1240` segments is accepted or revised
- **THEN** the screen states no pending or flagged count

### Requirement: State the segments kept as source by choice apart from the pending ones

The pending count SHALL leave out every segment kept as source by choice, and WHERE the book has such segments the
export screen SHALL state their number apart, as `<n> segments are kept as source by choice`.

**Source**: FR-REVIEW-X2 (`docs/spec_baseline/specification/01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), FR-BRIEF-09 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), ADR-0035; "kept as source by choice" is defined by `translation-pipeline` (`openspec/changes/archive/2026-10-19-complete-translation-workflow/specs/translation-pipeline/spec.md`). In plain words: auxiliary text the person switched off on the Book Brief — say, every image description — is not waiting for anything, so counting it as pending would make a finished book look unfinished; it is named on its own line instead.

#### Scenario: Segments kept by choice are named apart from pending ones

- **WHEN** the book has `1260` segments, image alt text is switched off for its `7` image descriptions, `312` body
  segments are still pending and `3` are flagged
- **THEN** the screen states `312 segments will be written in the source language`,
  `3 flagged segments will be written with their machine translation` and `7 segments are kept as source by choice`

#### Scenario: A finished book with switched-off text states only the kept count

- **WHEN** every segment of the book is accepted or revised except its `7` image descriptions, whose switch is off
- **THEN** the screen states no pending or flagged count
- **AND** states `7 segments are kept as source by choice`

### Requirement: State the segments kept as is on a line of their own

WHERE the book has segments kept as they are because they have nothing to translate (`translation-pipeline` "Keep a
segment with nothing to translate as it is"), the export screen SHALL state their number on a line of its own, as
`<n> segments are kept as is: they have nothing to translate (numbers, symbols)`, and SHALL never count them as kept
as source by choice.

**Source**: FR-REVIEW-X2 (`docs/spec_baseline/specification/01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), FR-BRIEF-09 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), ADR-0035. In plain words: a chapter number or a scene break is neither untranslated work nor a choice of the person, so nothing written in the source language goes unmentioned and nothing is mislabelled.

#### Scenario: Segments kept as is are not kept by choice

- **WHEN** every segment of the book is accepted, `2` of them — the chapter number `2` and the scene break `***` — kept
  as they are with no model call, and `1` image description is switched off
- **THEN** the screen states `1 segment is kept as source by choice` and
  `2 segments are kept as is: they have nothing to translate (numbers, symbols)`

### Requirement: Write the side files that are chosen beside the book

The export screen SHALL offer three side files, each with its own choice: "Glossary (names & terms) — reusable for
sequels", chosen by default; "Bilingual copy (source + target side by side)", not chosen by default; and "Report (flagged
items, proofread notes)", not chosen by default.

**Source**: FR-EXPORT-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-GLOSS-04 (`#fr-gloss`), ADR-0035, `design.md#d16--export-validates-and-writes-with-no-model-call`, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/spec_baseline/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`. In plain words: the glossary is on by default because it is what makes a sequel translate the same names the same way; the other two are for checking the work. The glossary file uses the import format so it can be loaded straight into the next book. The bilingual copy is self-contained so it opens offline and leaks nothing. The report lists what the Proofread stage left, because there is no consistency pass any more.

#### Scenario: The default choices

- **WHEN** the export screen is shown for an open book
- **THEN** the glossary side file is chosen and the bilingual copy and the report are not

#### Scenario: The report is labelled with proofread notes

- **WHEN** the export screen is shown for an open book
- **THEN** the third side-file choice reads "Report (flagged items, proofread notes)"

### Requirement: Name each side file after the written book and put it beside it

WHEN an export succeeds, the system SHALL write each chosen side file into the folder of the written book, named after
the written book's name with its format suffix removed: `<name>.glossary.csv`, `<name>.bilingual.html` and
`<name>.report.md`.

**Source**: FR-EXPORT-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-GLOSS-04 (`#fr-gloss`), ADR-0035, `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`. In plain words: the side files travel with the book, so they sit in its folder under its name.

#### Scenario: Side files land beside the book

- **WHEN** `/books/Frankenstein.uk.epub` is exported with all three side files chosen
- **THEN** `/books/Frankenstein.uk.glossary.csv`, `/books/Frankenstein.uk.bilingual.html` and
  `/books/Frankenstein.uk.report.md` are written

#### Scenario: A composite suffix is removed whole for side files

- **WHEN** `/books/Kobzar.uk.fb2.zip` is exported with the glossary chosen
- **THEN** the glossary is written to `/books/Kobzar.uk.glossary.csv`

### Requirement: Fill each side file with the book's data

The glossary file SHALL hold the book's glossary in the same columns Names & style imports (term, target, type,
gender, locked); the bilingual copy SHALL hold one row per segment with its source and its written target and need no
file or network resource outside itself; and the report SHALL hold the segment counts, the flagged segments with their
findings, and the proofread notes (each segment the Proofread stage read, with its verdict and reason, and each segment still waiting).

**Source**: FR-EXPORT-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-GLOSS-04 (`#fr-gloss`), ADR-0035, `docs/spec_baseline/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the glossary file uses the import format so it can be loaded straight into the next book, and the bilingual copy is self-contained so it opens offline and leaks nothing.

#### Scenario: The glossary file loads back into Names & style

- **WHEN** a glossary holding `Victor` → `Віктор`, type `character`, gender `male`, locked, is exported and the file
  is imported on Names & style for another book
- **THEN** that book's glossary holds `Victor` → `Віктор`, type `character`, gender `male`, locked

#### Scenario: The bilingual copy is self-contained

- **WHEN** a book of `1240` segments is exported with the bilingual copy chosen
- **THEN** `Frankenstein.uk.bilingual.html` holds `1240` rows, each with a segment's source and its written target, and
  references no file or network resource outside itself

#### Scenario: The report holds counts, flagged segments and proofread notes

- **WHEN** a book with `3` flagged segments is exported with the report chosen, after the Proofread stage rejected its rewrite of `ch2 · p05` because it added a source-language word
- **THEN** `Frankenstein.uk.report.md` holds the segment counts, the `3` flagged segments with their findings, and a proofread note naming `ch2 · p05` as rejected with that reason, in place of the former consistency notes

#### Scenario: The report holds no section of consistency notes

- **WHEN** any report is written
- **THEN** it holds no section of consistency notes

### Requirement: Write no side file when the book is not written

IF the book itself is not written, THEN the system SHALL write no side file.

**Source**: FR-EXPORT-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), ADR-0035. In plain words: a report or glossary about a book that was never written would describe a file that does not exist.

#### Scenario: A failed export writes no side file

- **WHEN** an export with the report chosen fails its verification
- **THEN** neither the book nor `/books/Frankenstein.uk.report.md` is written

### Requirement: Show the export format as the book's own

The export screen SHALL show the format as a read-only row naming the book's format and stating that it is the same as
the original, and SHALL offer no choice of format.

**Source**: FR-EXPORT-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30. In plain words: a book always comes out in the format it went in; saying so where a format picker would be answers the question before it is asked.

#### Scenario: An EPUB is exported as EPUB

- **WHEN** `Frankenstein.epub` is open and the export screen is shown
- **THEN** the format row reads `EPUB — same as the original` and cannot be changed

### Requirement: Show the tiles from the project before any export

The export screen SHALL show four tiles filled from the project before any export: segments done, translated without changes, reviewed by the person, and "checked when written" ("—" until a file is written). After a written export the first tile SHALL read what the stored export wrote and the fourth "checked: opens, nothing missing", also after a restart. A line under the tiles SHALL give the stored export's proofread summary, "—" before any.

**Source**: docs/spec_baseline/specification/mockups/README.md#step-5--export--e1e3-i, openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md (E1), `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the person sees how much of the book is done before choosing to write it, instead of four empty tiles that only fill after the file exists.

#### Scenario: Tiles show the project's figures before an export

- **WHEN** the project `Earth Gravity · EN → UK` has 74 of 142 segments done, 50 translated without changes and 6 reviewed by the person, and nothing has been exported
- **THEN** the tiles read "74 of 142", 50 translated without changes, 6 reviewed by you, and "checked when written" with "—"

#### Scenario: The check tile fills in after writing

- **WHEN** the book is written and its check passes
- **THEN** the fourth tile reads "✓" with "checked: opens, nothing missing", and the translated-without-changes and reviewed tiles keep the project's live figures

#### Scenario: The tiles after a restart come from the stored export

- **WHEN** the last export of `Earth Gravity · EN → UK` wrote 70 segments with 72 pending, the application restarts, and the project now has 74 of 142 segments done
- **THEN** the first tile reads "70 of 142" with "segments written", and the fourth reads "✓" with "checked: opens, nothing missing"

#### Scenario: The proofread line reads the stored export

- **WHEN** the last export of `Earth Gravity · EN → UK` was written with 12 segments re-read and 3 waiting, and the application is restarted
- **THEN** the line under the tiles reads "Proofread: 12 segments re-read, 3 waiting for you"

#### Scenario: The proofread line is empty before any export

- **WHEN** nothing has been exported
- **THEN** the line under the tiles reads "—"

### Requirement: Show the book's title and author with a reason when there is none

The export screen's title and author card SHALL show the original title and author read-only and the translated title and author editable, SHALL write the translated title the person typed into the exported book, and WHERE the book names no author SHALL say so in one line, "The book names no author".

**Source**: docs/spec_baseline/specification/mockups/README.md#step-5--export--e1e3-i, openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md (E1). In plain words: the person can correct the book's translated title before it is written, and an empty author field says the book simply names none instead of looking like a bug.

#### Scenario: The translated title is editable and written

- **WHEN** `earth-gravity.epub` names the title `Earth Gravity`, the person types `Земне тяжіння` into the translated title and exports the book
- **THEN** the original title stays `Earth Gravity`, read-only, and the written book's title is `Земне тяжіння`

#### Scenario: A book without an author says so

- **WHEN** `earth-gravity.epub` names the title `Earth Gravity` and no author
- **THEN** the card shows the original title, an editable translated title, and the line "The book names no author"

#### Scenario: A title with formatting marks keeps them

- **WHEN** the title of `notes.md` is its first heading `# The *Field* Notes`, translated as `Нотатки *Поля*`
- **THEN** the original title reads `The ⟦0⟧Field⟦1⟧ Notes` and the translated field `Нотатки ⟦0⟧Поля⟦1⟧`; deleting `⟦1⟧` in the field leaves it in place, and an edit the store refuses shows a line starting "Not saved:"

### Requirement: Take the original title in a fixed order and say where it came from

The system SHALL take the book's original title from the first source that has one — the book's metadata title, then for Markdown the front-matter title, then for Markdown the first heading, then the file name without its extension — and the card SHALL show under it the reason line of that source: "From the book's details", "From the front matter", "From the first heading" or "The book names no title — the file name is used".

**Source**: docs/spec_baseline/specification/mockups/README.md#step-5--export--e1e3-i, openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md "K8". In plain words: every book gets a title, a Markdown book still gets one from its own text, a plain-text book uses its file name, and the person can see which of these it was.

#### Scenario: The metadata title comes first

- **WHEN** `earth-gravity.epub` names the title `Earth Gravity` in its details and its first chapter starts with the heading `Prologue`
- **THEN** the original title reads `Earth Gravity` with the line "From the book's details"

#### Scenario: Markdown prefers the front matter

- **WHEN** `notes.md` states `title: Field Notes` in its front matter and starts with the heading `# Chapter One`
- **THEN** the original title reads `Field Notes` with the line "From the front matter"

#### Scenario: Markdown takes its title from the first heading

- **WHEN** `notes.md` has no front-matter title and starts with the heading `# Field Notes`
- **THEN** the original title reads `Field Notes` with the line "From the first heading"

#### Scenario: Plain text uses the file name

- **WHEN** the book is `earth-gravity.txt`
- **THEN** the original title reads `earth-gravity` with the line "The book names no title — the file name is used"

### Requirement: Suggest a file name in a short progress dialog

WHEN the person presses "✦ Suggest a name", the application SHALL open a short progress dialog with one step that shows what was sent (title, target language, format) and the answer (the name and how it was made), with the line "Cancel keeps the current name." and the buttons Cancel and "Use this name".

**Source**: docs/spec_baseline/specification/mockups/README.md#dialog--suggest-a-file-name-g4-short, openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md (E1, G4). In plain words: a model call that picks a file name is still a model call, so the person sees it and decides whether to take its answer.

#### Scenario: A suggested name is used only on request

- **WHEN** the model answers `earth-gravity.uk.md` and the person presses "Use this name"
- **THEN** the Save to field names `earth-gravity.uk.md`

#### Scenario: Cancel keeps the current name

- **WHEN** the person presses Cancel while the model has not answered
- **THEN** the Save to field is unchanged

#### Scenario: A title edit asks the model nothing

- **WHEN** a suggested name `Земне тяжіння.uk.md` is in use and the person edits the translated title
- **THEN** the name stays, no model call is made, and the line "The title changed. Press ✦ Suggest a name for a file name that matches it." appears

### Requirement: Announce a partial export in a banner

WHILE the run state is Translating (which covers Prepare, Translate and Proofread), Stuck call, Pausing or Stopping, the export screen SHALL show the banner "Pause or stop the translation to export. You can export what is done so far; the rest keeps the original text." with a Pause button; WHEN some but not all segments are translated and the run state is none of these, it SHALL show "74 of 142 segments are translated. The other segments keep the original text".

**Source**: docs/spec_baseline/specification/mockups/README.md#step-5--export--e1e3-i, docs/spec_baseline/specification/mockups/README.md#changes-while-translating-i. In plain words: the person is told before writing exactly what a partial book will hold, and how to get to a state where it can be written.

#### Scenario: The banner follows the run state

- **WHEN** the translation is running at 51 %, and later paused with 74 of 142 segments translated
- **THEN** the banner first offers Pause, and then reads "74 of 142 segments are translated. The other segments keep the original text"

#### Scenario: The banner holds during the Proofread stage

- **WHEN** the run is in its Proofread stage, reading window 41 of 120
- **THEN** the banner offers Pause and Export book is unavailable until the run is paused or stopped

### Requirement: Make no model call when the book is written

WHEN a book is exported, the system SHALL write it by checking and writing alone: the export action SHALL create no model, send no request to any provider and run no pass over the text, at every quality dial and whether or not a provider answers.

**Source**: `docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the work that used to wait until export (the last check, the neighbour reread, the backward revision) now happens inside the run, in its Proofread stage, so writing the file is quick, works with the model switched off, and cannot change the text the person has just looked at. ✦ Suggest a name on the Export screen is a separate model call the person starts, not part of writing the book.

#### Scenario: Export sends no request

- **WHEN** a translated 40-segment Markdown book at the Max dial is exported to `/books/Notes.uk.md` while a recording server stands in for the provider
- **THEN** the file is written and the server has received 0 requests

#### Scenario: Export works with the provider down

- **WHEN** the book `Frankenstein.epub` is fully translated, the provider at `http://127.0.0.1:11434` no longer answers, and Export book is pressed
- **THEN** `Frankenstein.uk.epub` is written and verified, and no connection error is shown

#### Scenario: A glossary change made after the run is not applied at export

- **WHEN** segment `ch2 · p04` was accepted as `Хейл відчинив двері.`, the person then changed the glossary target of `Hale` to `Гейл`, and the book is exported
- **THEN** the written book holds `Хейл відчинив двері.` as stored, because applying the new rendering is the Proofread stage's work on the next Continue

### Requirement: Warn when the Proofread stage has not read every window

WHILE the Proofread stage has planned windows still unread and the run is paused, stopped or ended, the export screen SHALL state before Export book is pressed how many windows were read of how many are planned, and that the person can continue on Translating or export as it is.

**Source**: `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: a book written after only part of the proofreading is still a good book, but the person should know that the rest has not been read yet and how to finish it.

#### Scenario: Proofread stopped half-way

- **WHEN** a Balanced run planned 120 proofread windows, was stopped after 40, and the export screen is shown
- **THEN** it reads "Proofread stopped at 40 of 120 windows — Continue on Translating, or export as it is."
- **AND** Export book is available

#### Scenario: A finished Proofread stage shows no warning

- **WHEN** all 120 planned windows were read
- **THEN** the export screen shows no Proofread warning

#### Scenario: Fast plans no windows

- **WHEN** a Fast run has ended, its Proofread stage having only marked segments for the review desk
- **THEN** the export screen shows no "Proofread stopped" warning

### Requirement: Warn about segments that wait on a fact the person has since given

WHERE segments wait on a fact the person has given since they were decided, the export screen SHALL state before Export book is pressed how many segments wait and which fact each waits on, and that Continue on Translating applies it.

**Source**: `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: when the person tells BookLoom a character's gender after those paragraphs were translated, the paragraphs are not corrected until the Proofread stage runs again; the export screen says so instead of letting the person think the fix is already in the file.

#### Scenario: Segments wait on a gender

- **WHEN** 3 segments were decided before the person set the gender of `Велемир` to male, and the run has not yet applied it
- **THEN** the export screen reads "3 segments wait on a fact you have since given (gender of Велемир). Continue on Translating to apply it."

#### Scenario: Two facts are named

- **WHEN** 2 segments wait on the gender of `Велемир` and 1 on the gender of `Mara Quill`
- **THEN** the export screen reads "3 segments wait on a fact you have since given (gender of Велемир, gender of Mara Quill). Continue on Translating to apply it."

#### Scenario: Nothing waits

- **WHEN** no deferred fact has arrived since the segments were decided
- **THEN** the export screen shows no such line

### Requirement: Warn about segments that will be written in the source language because their formatting marks were lost

WHERE a segment's formatting marks could not be kept in any draft of it, the export screen SHALL state before Export book is pressed how many segments will be written in the source language for that reason, and that the person can fix them on the review desk.

**Source**: `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: a paragraph whose bold, italic or link marks cannot be put back is written as its original text, because a broken paragraph would spoil the file; the person is told before writing, not only afterwards, and can fix it by hand.

#### Scenario: Two segments will fall back to the source

- **WHEN** `ch12 · p02` and `ch12 · p06` are flagged because every draft lost their formatting marks
- **THEN** the export screen reads "2 segments will be written in the source language because their formatting marks could not be kept. Fix them on the review desk."

#### Scenario: One segment uses the singular form

- **WHEN** only `ch12 · p02` is in that state
- **THEN** the export screen reads "1 segment will be written in the source language because its formatting marks could not be kept. Fix it on the review desk."

#### Scenario: The person fixes the segment

- **WHEN** the person saves an edit of `ch12 · p02` on the review desk that keeps its marks
- **THEN** the line no longer counts it

### Requirement: State the segments that still carry a proofread mark before the book is written

WHERE segments carry a proofread mark the Proofread stage has not cleared, the export screen SHALL state before Export book is pressed how many there are and that they are written as they are and can be reviewed on the desk.

**Source**: `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a proofread mark is the stage's note that a segment deserves a second look. At Fast no model reads those notes, so the person sees how many there are before choosing to write the book; at Balanced and Max the count is what the stage could not settle.

#### Scenario: A Fast book with marks

- **WHEN** a Fast run has ended with 4 segments carrying a proofread mark
- **THEN** the export screen reads "4 segments still carry a proofread mark. They are written as they are; review them on the desk."

#### Scenario: A Balanced book where the stage cleared every mark

- **WHEN** a Balanced run's Proofread stage has read every marked window and each mark was cleared
- **THEN** the export screen shows no proofread-mark line

#### Scenario: A rejected rewrite leaves its mark

- **WHEN** the Proofread stage rejected its rewrite of `ch3 · p09`, so the segment keeps its mark
- **THEN** the export screen counts `ch3 · p09` among the segments that still carry a proofread mark

### Requirement: Keep the proofread summary with the written export

WHEN a book is written, the system SHALL store with the export the number of segments the Proofread stage had read and the number still waiting for the person (FLAGGED, or holding a proofread mark or an open deferral, when the book was written), so that the tiles and the report read them again after a restart.

**Source**: `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: what the file contains should be stated from what was true when it was written, not from what the project looks like later; the summary that used to describe the consistency pass now describes the proofreading, and is kept with the export record.

#### Scenario: The summary survives a restart

- **WHEN** a book is written while 12 segments were re-read by the Proofread stage and 3 still wait (1 FLAGGED, 1 holding a proofread mark and 1 an open gender deferral), and the application is closed and opened again
- **THEN** the export screen shows "Proofread: 12 segments re-read, 3 waiting for you" from the stored export

#### Scenario: Later work does not rewrite the record

- **WHEN** the person then clears 2 of the 3 waiting segments on the review desk
- **THEN** the stored export still says 3 waiting until the book is written again

### Requirement: Strip stress marks from the written translation where the target language asks for it

WHERE the target language's file says stress marks are not written, the system SHALL write every translated segment without combining stress marks, and SHALL leave segments written in the source language as they are.

**Source**: `docs/spec_baseline/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-e-export`, `design.md#d4--name-forms-stress-marks-and-target-validation`. In plain words: a model or a person may add an accent over a vowel to show how to say a word, but a printed book does not carry it; every candidate is already cleaned before any check counts (the `quality-gates` capability), so removing it again at the last moment is only a guard that catches any that slipped past, such as one in a person's edit. A paragraph written in its source language is not touched, because it is the author's own text.

#### Scenario: A stress mark is removed from a translated segment

- **WHEN** a segment's stored translation is `Вона́ прийшла́ додо́му.` and the target language is set to strip stress marks
- **THEN** the written book holds `Вона прийшла додому.`

#### Scenario: A language that does not ask keeps the mark

- **WHEN** the target language does not set the strip and a stored translation holds a combining acute accent
- **THEN** the written book keeps it

#### Scenario: A source-language fallback is not stripped

- **WHEN** a segment is written in its source text because its formatting marks could not be kept, and that text holds `ма́ма`
- **THEN** the written book holds `ма́ма` unchanged
