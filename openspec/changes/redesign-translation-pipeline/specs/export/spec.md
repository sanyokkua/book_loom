## ADDED Requirements

### Requirement: Make no model call when the book is written

WHEN a book is exported, the system SHALL write it by checking and writing alone: the export action SHALL create no model, send no request to any provider and run no pass over the text, at every quality dial and whether or not a provider answers.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the work that used to wait until export (the last check, the neighbour reread, the backward revision) now happens inside the run, in its Proofread stage, so writing the file is quick, works with the model switched off, and cannot change the text the person has just looked at. ✦ Suggest a name on the Export screen is a separate model call the person starts, not part of writing the book.

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

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: a book written after only part of the proofreading is still a good book, but the person should know that the rest has not been read yet and how to finish it.

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

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: when the person tells BookLoom a character's gender after those paragraphs were translated, the paragraphs are not corrected until the Proofread stage runs again; the export screen says so instead of letting the person think the fix is already in the file.

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

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: a paragraph whose bold, italic or link marks cannot be put back is written as its original text, because a broken paragraph would spoil the file; the person is told before writing, not only afterwards, and can fix it by hand.

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

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a proofread mark is the stage's note that a segment deserves a second look. At Fast no model reads those notes, so the person sees how many there are before choosing to write the book; at Balanced and Max the count is what the stage could not settle.

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

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: what the file contains should be stated from what was true when it was written, not from what the project looks like later; the summary that used to describe the consistency pass now describes the proofreading, and is kept with the export record.

#### Scenario: The summary survives a restart

- **WHEN** a book is written while 12 segments were re-read by the Proofread stage and 3 still wait (1 FLAGGED, 1 holding a proofread mark and 1 an open gender deferral), and the application is closed and opened again
- **THEN** the export screen shows "Proofread: 12 segments re-read, 3 waiting for you" from the stored export

#### Scenario: Later work does not rewrite the record

- **WHEN** the person then clears 2 of the 3 waiting segments on the review desk
- **THEN** the stored export still says 3 waiting until the book is written again

### Requirement: Strip stress marks from the written translation where the target language asks for it

WHERE the target language's file says stress marks are not written, the system SHALL write every translated segment without combining stress marks, and SHALL leave segments written in the source language as they are.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-e-export`, `design.md#d4--name-forms-stress-marks-and-target-validation`. In plain words: a model or a person may add an accent over a vowel to show how to say a word, but a printed book does not carry it; every candidate is already cleaned before any check counts (the `quality-gates` capability), so removing it again at the last moment is only a guard that catches any that slipped past, such as one in a person's edit. A paragraph written in its source language is not touched, because it is the author's own text.

#### Scenario: A stress mark is removed from a translated segment

- **WHEN** a segment's stored translation is `Вона́ прийшла́ додо́му.` and the target language is set to strip stress marks
- **THEN** the written book holds `Вона прийшла додому.`

#### Scenario: A language that does not ask keeps the mark

- **WHEN** the target language does not set the strip and a stored translation holds a combining acute accent
- **THEN** the written book keeps it

#### Scenario: A source-language fallback is not stripped

- **WHEN** a segment is written in its source text because its formatting marks could not be kept, and that text holds `ма́ма`
- **THEN** the written book holds `ма́ма` unchanged


## MODIFIED Requirements

### Requirement: Write the book in its source format with the target language

WHEN an export is started, the system SHALL write the book in its source format from a fresh read of the source file,
writing for each segment:

- an ACCEPTED segment's machine translation;
- a REVISED segment's text as stored: the text as the person saved it, with the typography pass applied (a person's
  edit does not pass through the run's gate), kept as saved if the normalised form would not restore; or the rewrite the
  Proofread stage kept, written as stored because it already passed the run's gates;
- a FLAGGED segment's machine translation, or its source text when no translation of it ever passed the placeholder
  check or its draft failed a gate other than quote balance or script purity (an English leftover is never written as
  a translation);
- a PENDING segment's source text;
- the source text of every segment kept as source by choice — an auxiliary segment whose kind the book brief's "Also
  translate" switches leave off when the export starts.

A rewrite the Proofread stage only proposed for a segment the person had edited SHALL NOT be written; the person's text
is.

It SHALL declare the target language where the format has a place for it: `dc:language` in an EPUB package, `lang` in an
FB2 title-info, and the value of an existing top-level `lang` key in a Markdown book's frontmatter. TXT, and a Markdown
book whose frontmatter holds no `lang` key, carry no language.

**Source**: FR-EXPORT-01, FR-EXPORT-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-DOC-06 (`#fr-doc`), FR-REVIEW-X2 (`01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), ADR-0035, `design.md#d16--export-validates-and-writes-with-no-model-call`; the shipped `document-round-trip` requirements "Set the target language by replacing the first dc:language", "Set the FB2 target language without touching the recorded source language" and "Declare no language metadata in formats that carry none" (`openspec/specs/document-round-trip/spec.md`). In plain words: the output opens in the same readers as the source, keeps its formatting, images and structure, and says it is in the new language. A book can be written at any point, so each segment is written with the best text it has: the person's edit over the machine's, the machine's over the source, and the source where nothing better exists yet. Reading the source afresh each time means two exports of the same decisions produce the same file. A Markdown book that already says `lang: en` is told it is now `uk`; one that says nothing is not given a key it never had. A rewrite the Proofread stage kept is machine text that already passed the run's gates, so it is written as stored; a rewrite proposed for text the person edited waits for the person, so the person's words are never replaced silently.

#### Scenario: An EPUB paragraph with inline markup

- **WHEN** an EPUB whose paragraph is `<p>Tom &amp; <i>Jerry</i> ran.</p>` is translated to `uk` with the pseudo model
  and exported
- **THEN** the written paragraph is `<p>TOM &amp; <i>JERRY</i> RAN.</p>`
- **AND** the written package declares `dc:language` `uk`

#### Scenario: An FB2 book declares the target language

- **WHEN** an FB2 book whose title-info holds `<lang>en</lang>` is translated to `uk` and exported
- **THEN** the written book's title-info holds `<lang>uk</lang>`

#### Scenario: A flagged segment writes its machine translation

- **WHEN** the paragraph `He opened the *old* door.` of a Markdown book is FLAGGED with the machine translation
  `Він відчинив *старі* двері.`
- **THEN** the written book contains `Він відчинив *старі* двері.`

#### Scenario: A flagged segment keeps its source text

- **WHEN** the paragraph `He opened the *old* door.` is FLAGGED because every reply for it failed the placeholder check
- **THEN** the written book contains `He opened the *old* door.` unchanged

#### Scenario: A revised segment writes the person's text

- **WHEN** a segment's machine translation is `Він пішов.` and the person saved the edit `Він пішов геть.`
- **THEN** the written book contains `Він пішов геть.`

#### Scenario: A book paused part-way writes pending segments in the source

- **WHEN** a Markdown book is exported while its run is paused with the paragraph `The end.` still PENDING
- **THEN** the written book contains `The end.` unchanged, in the Markdown format

#### Scenario: A switched-off auxiliary kind keeps its source

- **WHEN** image alt text is switched off in "Also translate" and an image's alt text is `A ship at dawn`
- **THEN** the written image's alt text is `A ship at dawn`

#### Scenario: A Markdown book's frontmatter language follows the target

- **WHEN** a Markdown book whose frontmatter holds `lang: en` is translated to `uk` and exported
- **THEN** the written book's frontmatter holds `lang: uk`

#### Scenario: A Markdown book without a language key is given none

- **WHEN** a Markdown book whose frontmatter holds only `title: The Lighthouse` is translated to `uk` and exported
- **THEN** the written book's frontmatter holds no `lang` key

#### Scenario: Two exports of the same decisions are the same book

- **WHEN** `Frankenstein.epub` is exported to `/books/a.epub` and then, with no decision changed, to `/books/b.epub`
- **THEN** the two files are canonical-equal

#### Scenario: A kept proofread rewrite is written

- **WHEN** the paragraph `Tarn will come tomorrow.` was accepted as `Тарн прийшов завтра.` and the Proofread stage kept the rewrite `Тарн прийде завтра.`
- **THEN** the written book contains `Тарн прийде завтра.`

#### Scenario: A proposed rewrite does not replace the person's text

- **WHEN** the person saved `Тарн прийде вранці.` for that paragraph and the Proofread stage only proposed `Тарн прийде завтра.`
- **THEN** the written book contains `Тарн прийде вранці.`

### Requirement: Report the finished file

WHEN an export succeeds, the export screen SHALL show the title "Translated book ready" and four tiles: the number of
segments written with a translation ("segments translated"), the share of segments accepted without review
("auto-accepted", of the segments that needed a decision: the written ones less those kept as they are, plus the pending ones), the number of segments the person accepted, edited or reverted ("reviewed by you"), and whether the
written file passed its verification ("file validates").

WHEN an export succeeds, the system SHALL report with the written file: the number of segments written with a
translation; the number of pending segments written in the source language, a flagged segment that has no machine
translation counted among them; the number of segments kept as source by choice; the number of segments kept as they
are because they have nothing to translate, also counted among those written; the number of flagged segments
written with their machine translation; the number of segments accepted without review; and the number of segments
the person reviewed.

WHEN an export succeeds, the export screen SHALL list the checks it passed: "Re-opened and verified · structure, ids and
fonts preserved" and, for an EPUB, an FB2, or a Markdown book whose frontmatter holds a `lang` key, "Language metadata
updated (<source> → <target>)".

WHEN an export succeeds, the application SHALL show an export-complete dialog naming the written file, its folder, its
verification result and its size, offering Close, Open folder and Open book, and SHALL raise no transient message for
it: the dialog is the export's one success notice.

The export screen's Open folder action and the dialog's SHALL show the written file in the system file manager,
selecting it where the platform supports it (macOS, Windows) and otherwise opening its folder; an exit code of 1 from the
Windows file manager SHALL count as success. Open book SHALL open the written file in the application the system
associates with its type.

WHEN an export succeeds, the screen's checks and the dialog SHALL also name each side file written, one per line, and state the
proofread summary, "Proofread: 12 segments re-read, 3 waiting for you", which gives both counts also when one is 0. WHEN any segment was a source fallback (the
requirement "Check the written book before it replaces the destination"), the screen and the dialog SHALL say how many,
and name each by its locator, in a warning line: "2 segments were written in the source language because their
translation broke the formatting: ch12 · p02, ch12 · p06", worded with the language's plural forms.

WHEN the destination, Replace or the chosen side files change after a success, the export
screen SHALL forget that result, so its tiles, checks, Open folder and Open book never describe a file the next press
will not write. WHEN the destination occupied after a success is the file just exported, the screen SHALL say so beside
Save to in a neutral line, not as a refusal, and Export book SHALL wait for Replace or another name: "This is the file
just exported. To write it again, turn on Replace or choose another name."

WHILE no export of the open book has succeeded in the session, the export screen SHALL list no checks, SHALL show the
file-validates tile as not yet known, and SHALL offer nothing to reveal or open. WHILE a run is translating, the export
screen SHALL offer no control that writes a book, and its side-file choices SHALL stay visible and unavailable.

**Source**: FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`, `docs/next_features.md#15-left-open-by-the-hand-test`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: after pressing Export the person sees that the book exists, that it was checked, and how much of it the machine did on its own. The check line says what the application actually verified — it re-opens the file — rather than the reference's EPUBCheck, which is not run. Windows Explorer returns 1 even when it succeeds, so treating that as a failure would report an error for a file that was shown. Showing and opening the file use the system's own commands, built from the path alone, never a shell and never the network. The counts say exactly what the file holds — how much the machine translated on its own, what the person reviewed, what is still in the source language because it was not reached, and what is in it because the person chose so — and they are what the tiles, the report side file and the command line's report line show. One success notice is enough: the dialog already names the file, so a transient message on top of it would only repeat it. The proofread line replaces the consistency-pass line: it says how much of the book the Proofread stage read and how much still waits for the person, from the counts stored with the export.

#### Scenario: A source fallback is named after the export

- **WHEN** an export of an EPUB succeeds with the one source fallback `ch12 · p02`
- **THEN** the export screen and the export-complete dialog read "1 segment was written in the source language
  because its translation broke the formatting: ch12 · p02"

#### Scenario: A completed run reports its file

- **WHEN** a run over `Frankenstein.epub` completes with `1240` segments, `1224` auto-accepted and `16` edited by the
  person, and the book is exported to `/books/Frankenstein.uk.epub`
- **THEN** the screen shows `Translated book ready` and the tiles `1,240` segments translated, `98.7%` auto-accepted,
  `16` reviewed by you, and a passed mark for file validates
- **AND** it lists `Re-opened and verified · structure, ids and fonts preserved` and
  `Language metadata updated (en → uk)`

#### Scenario: A format with no language metadata lists no language check

- **WHEN** `Book.md`, whose frontmatter holds no `lang` key, is exported to `/books/Book.uk.md`
- **THEN** the screen lists the re-open check and no language-metadata line

#### Scenario: A Markdown book that declares its language lists the language check

- **WHEN** `Notes.md`, whose frontmatter holds `lang: en`, is translated from `en` to `uk` and exported to
  `/books/Notes.uk.md`
- **THEN** the screen lists the re-open check and `Language metadata updated (en → uk)`

#### Scenario: The export-complete dialog

- **WHEN** `/books/Frankenstein.uk.epub`, 936 KB, is written and verified
- **THEN** a dialog names `Frankenstein.uk.epub`, the folder `/books`, a passed verification and `936 KB`
- **AND** offers Close, Open folder and Open book

#### Scenario: The dialog is the only success notice

- **WHEN** `/books/Frankenstein.uk.epub` is written and verified
- **THEN** the export-complete dialog is shown
- **AND** no transient message is raised for the export

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

#### Scenario: The proofread summary is reported

- **WHEN** `Frankenstein.epub` is exported after its Proofread stage re-read 12 segments and 3 still wait
- **THEN** the screen's checks and the export-complete dialog read "Proofread: 12 segments re-read, 3 waiting for you"

#### Scenario: A Fast book reports what waits

- **WHEN** a Fast book is exported with 4 segments carrying a proofread mark and none re-read
- **THEN** the checks read "Proofread: 0 segments re-read, 4 waiting for you"

#### Scenario: The report names no consistency pass

- **WHEN** any book is exported
- **THEN** no check line and no dialog line mentions a consistency pass

### Requirement: Write the side files that are chosen beside the book

The export screen SHALL offer three side files, each with its own choice: "Glossary (names & terms) — reusable for
sequels", chosen by default; "Bilingual copy (source + target side by side)", not chosen by default; and "Report (flagged
items, proofread notes)", not chosen by default.

**Source**: FR-EXPORT-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-GLOSS-04 (`#fr-gloss`), ADR-0035, `design.md#d16--export-validates-and-writes-with-no-model-call`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`. In plain words: the glossary is on by default because it is what makes a sequel translate the same names the same way; the other two are for checking the work. The glossary file uses the import format so it can be loaded straight into the next book. The bilingual copy is self-contained so it opens offline and leaks nothing. The report lists what the Proofread stage left, because there is no consistency pass any more.

#### Scenario: The default choices

- **WHEN** the export screen is shown for an open book
- **THEN** the glossary side file is chosen and the bilingual copy and the report are not

#### Scenario: The report is labelled with proofread notes

- **WHEN** the export screen is shown for an open book
- **THEN** the third side-file choice reads "Report (flagged items, proofread notes)"

### Requirement: Fill each side file with the book's data

The glossary file SHALL hold the book's glossary in the same columns Names & style imports (term, target, type,
gender, locked); the bilingual copy SHALL hold one row per segment with its source and its written target and need no
file or network resource outside itself; and the report SHALL hold the segment counts, the flagged segments with their
findings, and the proofread notes (each segment the Proofread stage read, with its verdict and reason, and each segment still waiting).

**Source**: FR-EXPORT-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-GLOSS-04 (`#fr-gloss`), ADR-0035, `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the glossary file uses the import format so it can be loaded straight into the next book, and the bilingual copy is self-contained so it opens offline and leaks nothing.

#### Scenario: The glossary file loads back into Names & style

- **WHEN** a glossary holding `Victor` → `Віктор`, type `character`, gender `male`, locked, is exported and the file
  is imported on Names & style for another book
- **THEN** that book's glossary holds `Victor` → `Віктор`, type `character`, gender `male`, locked

#### Scenario: The bilingual copy is self-contained

- **WHEN** a book of `1240` segments is exported with the bilingual copy chosen
- **THEN** `Frankenstein.uk.bilingual.html` holds `1240` rows, each with a segment's source and its written target, and
  references no file or network resource outside itself

#### Scenario: The report holds counts, flagged segments and consistency notes

- **WHEN** a book with `3` flagged segments is exported with the report chosen, after the Proofread stage rejected its rewrite of `ch2 · p05` because it added a source-language word
- **THEN** `Frankenstein.uk.report.md` holds the segment counts, the `3` flagged segments with their findings, and a proofread note naming `ch2 · p05` as rejected with that reason, in place of the former consistency notes

#### Scenario: The report has no consistency notes

- **WHEN** any report is written
- **THEN** it holds no section of consistency notes

### Requirement: Show the tiles from the project before any export

The export screen SHALL show four tiles filled from the project before any export: segments done, translated without changes, reviewed by the person, and "checked when written" ("—" until a file is written). After a written export the first tile SHALL read what the stored export wrote and the fourth "checked: opens, nothing missing", also after a restart. A line under the tiles SHALL give the stored export's proofread summary, "—" before any.

**Source**: docs/specification/mockups/README.md#step-5--export--e1e3-i, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (E1), `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: the person sees how much of the book is done before choosing to write it, instead of four empty tiles that only fill after the file exists.

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

### Requirement: Announce a partial export in a banner

WHILE the run state is Translating (which covers Prepare, Translate and Proofread), Stuck call, Pausing or Stopping, the export screen SHALL show the banner "Pause or stop the translation to export. You can export what is done so far; the rest keeps the original text." with a Pause button; WHEN some but not all segments are translated and the run state is none of these, it SHALL show "74 of 142 segments are translated. The other segments keep the original text".

**Source**: docs/specification/mockups/README.md#step-5--export--e1e3-i, docs/specification/mockups/README.md#changes-while-translating-i. In plain words: the person is told before writing exactly what a partial book will hold, and how to get to a state where it can be written.

#### Scenario: The banner follows the run state

- **WHEN** the translation is running at 51 %, and later paused with 74 of 142 segments translated
- **THEN** the banner first offers Pause, and then reads "74 of 142 segments are translated. The other segments keep the original text"

#### Scenario: The banner holds during the Proofread stage

- **WHEN** the run is in its Proofread stage, reading window 41 of 120
- **THEN** the banner offers Pause and Export book is unavailable until the run is paused or stopped


## REMOVED Requirements

### Requirement: Run the final consistency pass before writing when it is switched on

**Reason**: The last check, its switch, its Automation default and the consistency pass it ran are gone: export now only checks and writes, and the work moves into the run.

**Migration**: "Make no model call when the book is written" in export, and "Run the Proofread stage after the last chunk at every dial" in translation-pipeline.

### Requirement: Sweep only a rendering the glossary held before the person changed it

**Reason**: The glossary-rendering sweep no longer happens at export; it is code the Proofread stage runs inside the run.

**Migration**: "Sweep a changed rendering through the decided text by code" in translation-pipeline; "Make no model call when the book is written" in export states that export applies none.

### Requirement: Draft flagged and doubted segments again during the pass

**Reason**: Drafting flagged and doubtful segments again at export is replaced by the Proofread stage's marked windows behind the never-worse gate.

**Migration**: "Proofread the marked windows at Balanced and Max with a minimal edit" in translation-pipeline.

### Requirement: Keep a fresh draft only when it is better and loses nothing

**Reason**: "Better and loses nothing" is replaced by a single code gate that keeps an edit only when no check got worse.

**Migration**: "Reject a candidate text that breaks a never-worse veto" and "Keep a candidate text only when its score is not worse and its mark is cleared" in quality-gates.

### Requirement: Check risky paragraphs against their neighbours during the pass

**Reason**: Checking risky paragraphs against their neighbours at export is replaced by Proofread windows, each with one read-only neighbour on each side; Max re-reads every story paragraph.

**Migration**: "Proofread the marked windows at Balanced and Max with a minimal edit" and "Re-read every story paragraph at Max in a second round" in translation-pipeline.

### Requirement: Show the model the neighbours and the book's context in the neighbour check

**Reason**: The neighbour check's own prompt goes with the check; a Proofread window call is built by the one context builder like every other call.

**Migration**: "Build every model call of a run through one context builder" in translation-pipeline.

### Requirement: Send only story text to the consistency pass

**Reason**: Only story text reaching a model is now a rule of the Proofread stage's mark list, not of export.

**Migration**: "Build the proofread mark list by code" in translation-pipeline.

### Requirement: Skip a paragraph an earlier check already found unchanged

**Reason**: The in-memory list of paragraphs already found unchanged is replaced by a fingerprint kept on each proofread verdict, so it survives a restart.

**Migration**: "Skip a Proofread window that already holds the same fingerprint" in resume.

### Requirement: Never change an edited segment, and survive a failed call or a cancellation

**Reason**: Not overwriting a person's edit becomes a proposal the person accepts; surviving a failed call or a cancellation belongs to the Proofread stage.

**Migration**: "Make a proposal of a proofread rewrite of the person's own edit" in translation-pipeline, "Apply proofread proposals for edited segments only on acceptance" in review-queue, and "Pause instead of failing when an error stops the job" in resume.

### Requirement: Show the last check as a progress dialog the person can leave or end

**Reason**: The last-check progress dialog goes with the last check; the Proofread stage shows its progress on the Translating screen.

**Migration**: "Show the run's stage on the Translating summary strip" in app-shell.

### Requirement: Show each reread item next to its neighbours with a before and an after

**Reason**: The reread grid goes with the last check; the review desk shows a proofread segment's before and after.

**Migration**: "Show a segment's proofread verdict and its reason in the compare" in review-queue.

### Requirement: Let the person write the file early or cancel the last check with a question first

**Reason**: "Write the file now" and its question belong to the removed last-check dialog; there is no pass to end early.

**Migration**: none: the person who wants the file sooner stops the run on Translating and exports, as "Export at any time except while a run is translating" says.

### Requirement: Run the whole-book backward revision before writing the book

**Reason**: The whole-book backward revision before writing is replaced by the Proofread stage inside the run, which also resolves deferrals at every dial.

**Migration**: "Run the Proofread stage after the last chunk at every dial" and "Settle every deferral whose fact has arrived in the Proofread stage" in translation-pipeline, and "Warn about segments that wait on a fact the person has since given" in export.
