# Spec Delta

## ADDED Requirements

### Requirement: Choose where to save on the export screen

The export screen SHALL carry a Save to field holding the path the translated book is written to, and a Browse action
that opens the system's save dialog to choose it.

WHEN a book is open and the person has not edited the Save to path, the export screen SHALL propose a path beside the
source book whose name is the source name with the target language inserted before the format's suffix, and SHALL
recompute that proposal whenever the target language changes.

WHEN the person has edited the Save to path, by typing or through Browse, the export screen SHALL keep that path when
the target language changes.

The proposed name SHALL be the name the command line gives its output for the same source file and target language.

**Source:** FR-EXPORT-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`.
In plain words: the translation job no longer writes the book, so the place it is written to belongs with the button
that writes it — which is where the reference rendering always drew it. The name matches the command line's because
the rule is not the obvious one-liner it looks like: for a `.fb2.zip` book the suffix is two extensions, and the screen
and the command line must never disagree about where a book goes. How the one rule is shared is a design matter
(`design.md` D13).

#### Scenario: The default path follows the source

- **WHEN** `/books/Frankenstein.epub` is open, the target language is `uk`, and the Save to path has not been edited
- **THEN** the Save to field holds `/books/Frankenstein.uk.epub`

#### Scenario: Changing the target language moves the proposal

- **WHEN** the Save to field holds the proposal `/books/Frankenstein.uk.epub` and the target language is changed to `de`
- **THEN** the Save to field holds `/books/Frankenstein.de.epub`

#### Scenario: An edited path is kept

- **WHEN** the Save to path has been chosen through Browse as `/archive/out.epub` and the target language is changed to
  `de`
- **THEN** the Save to field still holds `/archive/out.epub`

#### Scenario: A composite suffix is kept whole

- **WHEN** `/books/Kobzar.fb2.zip` is open and the target language is `uk`
- **THEN** the proposed path is `/books/Kobzar.uk.fb2.zip`, not `/books/Kobzar.fb2.uk.zip`

#### Scenario: The command line names its output the same way

- **WHEN** the command line translates `/books/Kobzar.fb2.zip` to `uk` without an explicit output path
- **THEN** it writes `/books/Kobzar.uk.fb2.zip`, the same path the export screen proposes

### Requirement: Replace an existing file only when the person allows it

The export screen SHALL carry a replace-if-exists switch, off by default.

IF a file already exists at the Save to path, or at the path of a side file that is chosen, and the replace-if-exists
switch is off, THEN the export screen SHALL say so beside the Save to field as soon as the path is chosen, naming the
occupied path, and SHALL make Export book unavailable until the path changes or the switch is turned on.

**Source:** FR-EXPORT-03, FR-EXPORT-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`),
ADR-0035, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`.
In plain words: an existing book is never replaced by accident, and the person learns the path is taken when they pick
it, not after pressing Export and waiting for a refusal. The side files follow the same rule, because a glossary file
someone edited by hand is as worth protecting as the book.

#### Scenario: Replacing is off by default

- **WHEN** the export screen is shown for an open book
- **THEN** the replace-if-exists switch is present and off

#### Scenario: An occupied path is reported at once

- **WHEN** `/books/Frankenstein.uk.epub` already exists, the switch is off, and the Save to path is set to it
- **THEN** the screen reports beside the Save to field that `/books/Frankenstein.uk.epub` already exists
- **AND** Export book is unavailable, without an export having been attempted

#### Scenario: Allowing replacement releases the action

- **WHEN** the occupied path `/books/Frankenstein.uk.epub` is reported and the replace-if-exists switch is turned on
- **THEN** the warning is withdrawn and Export book is available

#### Scenario: An occupied side-file path is reported too

- **WHEN** the glossary side file is chosen, `/books/Frankenstein.uk.glossary.csv` already exists, the book's own path is
  free, and the switch is off
- **THEN** the screen names `/books/Frankenstein.uk.glossary.csv` as occupied and Export book is unavailable

### Requirement: Refuse a destination the book cannot be written to

IF the Save to path is the source file itself, or its file type differs from the source's — a zipped FB2 book
(`.fb2.zip`) and a plain one (`.fb2`) being different types, while `.md` and `.markdown` are both Markdown — THEN the
export screen SHALL say so beside the Save to field and SHALL make Export book unavailable, and an export requested with
that path SHALL fail with `ErrorCode.validation` without writing anything.

**Source:** FR-EXPORT-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`.
In plain words: these checks used to guard the start of a run, which wrote the book; now the export writes it, so they
guard the export. A translation must never be written over the book it was made from, and a book is written back in the
container it came in, so a zipped FB2 given a plain name would only fail its own re-open check after writing.

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

WHILE a book is open and no run is translating it — no run has started, or the run is paused (for any reason, including
a provider error), stopped or finished — the export screen SHALL make Export book available, subject only to the
occupied-path rule.

WHILE a run is running, pausing or stopping, the export screen SHALL make Export book unavailable and SHALL show the note
"Pause the run to export".

WHILE an export is being written, the export screen SHALL make Export book unavailable and SHALL report the export as in
progress.

**Source:** FR-REVIEW-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-REVIEW-X1
(`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), ADR-0035.
In plain words: a person can take the book out at any point — before translating a word, halfway, after stopping — and
review never blocks it. The one exception is while the run is actively translating, because a book written then would
disagree with what the run decides a moment later; pausing first is one click.

#### Scenario: A paused run can be exported

- **WHEN** the run over `Frankenstein.epub` is paused at 78%
- **THEN** Export book is available

#### Scenario: A running run cannot be exported

- **WHEN** the run over `Frankenstein.epub` is running at 42%
- **THEN** Export book is unavailable and the note `Pause the run to export` is shown

#### Scenario: A book with no run can be exported

- **WHEN** `Frankenstein.epub` has been opened and no run has been started
- **THEN** Export book is available
- **AND** every segment will be written in the source language

#### Scenario: A run auto-paused by a provider error can be exported

- **WHEN** the run is paused because `http://localhost:11434` stopped answering
- **THEN** Export book is available

### Requirement: State what a partial export will write before it is written

WHEN the open book has segments that would not be written with a translation, the export screen SHALL state, before
Export book is pressed, how many pending segments will be written in the source language and how many flagged segments
will be written with their machine translation.

The pending count SHALL leave out every segment kept as source by choice, and WHERE the book has such segments the
export screen SHALL state their number apart, as `<n> segments are kept as source by choice`.

**Source:** FR-REVIEW-X2 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), FR-BRIEF-09
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), ADR-0035; "kept as source by choice" is
defined by `translation-pipeline` (`openspec/changes/complete-translation-workflow/specs/translation-pipeline/spec.md`).
In plain words: a partial book is allowed, but never a surprise — the person reads "312 segments will be written in the
source language" before choosing to write it. Auxiliary text the person switched off on the Book Brief — say, every
image description — is not waiting for anything, so counting it as pending would make a finished book look unfinished;
it is named on its own line instead, so nothing written in the source language goes unmentioned.

#### Scenario: Pending and flagged counts are stated

- **WHEN** the book has `1240` segments, `312` still pending and `3` flagged
- **THEN** the screen states `312 segments will be written in the source language`
- **AND** states `3 flagged segments will be written with their machine translation`

#### Scenario: A complete book states nothing partial

- **WHEN** every one of the book's `1240` segments is accepted or revised
- **THEN** the screen states no pending or flagged count

#### Scenario: Segments kept by choice are named apart from pending ones

- **WHEN** the book has `1260` segments, image alt text is switched off for its `7` image descriptions, `312` body
  segments are still pending and `3` are flagged
- **THEN** the screen states `312 segments will be written in the source language`,
  `3 flagged segments will be written with their machine translation` and `7 segments are kept as source by choice`

#### Scenario: A finished book with switched-off text states only the kept count

- **WHEN** every segment of the book is accepted or revised except its `7` image descriptions, whose switch is off
- **THEN** the screen states no pending or flagged count
- **AND** states `7 segments are kept as source by choice`

### Requirement: Write the side files that are chosen beside the book

The export screen SHALL offer three side files, each with its own choice: "Glossary (names & terms) — reusable for
sequels", chosen by default; "Bilingual copy (source + target side by side)", not chosen by default; and "Report (flagged
items, consistency notes)", not chosen by default.

WHEN an export succeeds, the system SHALL write each chosen side file into the folder of the written book, named after
the written book's name with its format suffix removed: `<name>.glossary.csv` holding the book's glossary in the same
columns Names & style imports (term, target, type, gender, locked); `<name>.bilingual.html` holding one row per segment
with its source and its written target, needing no file or network resource outside itself; and `<name>.report.md`
holding the segment counts, the flagged segments with their findings, and the consistency notes.

IF the book itself is not written, THEN the system SHALL write no side file.

**Source:** FR-EXPORT-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-GLOSS-04
(`#fr-gloss`), ADR-0035, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`,
`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`.
In plain words: the glossary is on by default because it is what makes a sequel translate the same names the same way;
the other two are for checking the work. The glossary file uses the import format so it can be loaded straight into the
next book. The bilingual copy is self-contained so it opens offline and leaks nothing.

#### Scenario: The default choices

- **WHEN** the export screen is shown for an open book
- **THEN** the glossary side file is chosen and the bilingual copy and the report are not

#### Scenario: Side files land beside the book

- **WHEN** `/books/Frankenstein.uk.epub` is exported with all three side files chosen
- **THEN** `/books/Frankenstein.uk.glossary.csv`, `/books/Frankenstein.uk.bilingual.html` and
  `/books/Frankenstein.uk.report.md` are written

#### Scenario: A composite suffix is removed whole for side files

- **WHEN** `/books/Kobzar.uk.fb2.zip` is exported with the glossary chosen
- **THEN** the glossary is written to `/books/Kobzar.uk.glossary.csv`

#### Scenario: The glossary file loads back into Names & style

- **WHEN** a glossary holding `Victor` → `Віктор`, type `character`, gender `male`, locked, is exported and the file is
  imported on Names & style for another book
- **THEN** that book's glossary holds `Victor` → `Віктор`, type `character`, gender `male`, locked

#### Scenario: A failed export writes no side file

- **WHEN** an export with the report chosen fails its verification
- **THEN** neither the book nor `/books/Frankenstein.uk.report.md` is written

### Requirement: Run the final consistency pass before writing when it is switched on

The export screen SHALL offer a final consistency pass as a switch labelled "Whole-book name sweep & resolve later
reveals", off by default, and on by default when the book brief's quality dial is Max.

WHEN an export starts with the switch on, the system SHALL run the whole-book backward revision — the locked-name sweep
and the resolution of deferred items — before the book is written, and SHALL leave every segment the person edited as the
person wrote it. The sweep SHALL replace a rendering only where the glossary held a previous target for that term which
the person then changed; a rendering the model chose on its own for a term that had no glossary target SHALL NOT be
swept.

**Source:** FR-EXPORT-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-ALGO-10
(`#fr-algo`), FR-REVIEW-10 (`#fr-review`), FR-BRIEF-08 (`#fr-brief`),
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`.
In plain words: a name settled in chapter 20 can be carried back into chapter 2 before the book is written, which is
slow and so off unless the person asked for the best quality. A person's own edit is never overwritten by it. The sweep
is a plain text substitution, so it can only find a spelling it knew: the one the glossary held before the change.

#### Scenario: The pass follows the quality dial by default

- **WHEN** the book brief's quality dial is Max and the export screen is shown
- **THEN** the consistency-pass switch is on
- **AND** with the dial on Balanced it is off

#### Scenario: A locked name is swept through earlier chapters

- **WHEN** the glossary held `Justine` → `Джастін` while chapter 2 was accepted rendering it `Джастін`, the person then
  changed the target to `Жустіна` and locked it, and the book is exported with the switch on
- **THEN** chapter 2 in the written book renders the name `Жустіна`

#### Scenario: A rendering the glossary never held is not swept

- **WHEN** `Justine` had no glossary target while chapter 2 was accepted with the model's own `Джастін`, the person then
  added `Justine` → `Жустіна` locked, and the book is exported with the switch on
- **THEN** chapter 2 in the written book still reads `Джастін`, because the sweep replaces only a rendering the glossary
  itself held before the change

#### Scenario: A person's edit survives the pass

- **WHEN** the person saved an edit of `ch2 · p4` reading `Джастін прийшла.` and the book is exported with the switch on
- **THEN** the written `ch2 · p4` reads `Джастін прийшла.`

### Requirement: Show the export format as the book's own

The export screen SHALL show the format as a read-only row naming the book's format and stating that it is the same as
the original, and SHALL offer no choice of format.

**Source:** FR-EXPORT-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30.
In plain words: a book always comes out in the format it went in; saying so where a format picker would be answers the
question before it is asked.

#### Scenario: An EPUB is exported as EPUB

- **WHEN** `Frankenstein.epub` is open and the export screen is shown
- **THEN** the format row reads `EPUB — same as the original` and cannot be changed

### Requirement: Say so when no book is open on the export screen

WHILE no book is open, the export screen SHALL show an empty state saying no book is open, with a way to go to the import
screen, and SHALL show no Save to field, no side-file choice and no Export book action.

**Source:** FR-NOTIF-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-notif`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`.
In plain words: with nothing open there is nothing to write, and an export form over nothing would only invite a press
that has to be refused.

#### Scenario: The export screen with no book

- **WHEN** the export screen is opened before any book has been opened
- **THEN** it reports that no book is open and offers to go to the import screen
- **AND** it shows no Save to field and no Export book action

## MODIFIED Requirements

### Requirement: Write the book in its source format with the target language

WHEN an export is started, the system SHALL write the book in its source format from a fresh read of the source file,
writing for each segment:

- an ACCEPTED segment's machine translation;
- a REVISED segment's text as the person saved it;
- a FLAGGED segment's machine translation, or its source text when no translation of it ever passed the placeholder
  check;
- a PENDING segment's source text;
- the source text of every segment kept as source by choice — an auxiliary segment whose kind the book brief's "Also
  translate" switches leave off when the export starts.

It SHALL declare the target language where the format has a place for it: `dc:language` in an EPUB package, `lang` in an
FB2 title-info, and the value of an existing top-level `lang` key in a Markdown book's frontmatter. TXT, and a Markdown
book whose frontmatter holds no `lang` key, carry no language.

**Source:** FR-EXPORT-01, FR-EXPORT-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-DOC-06 (`#fr-doc`),
FR-REVIEW-X2 (`01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), ADR-0035; the shipped `document-round-trip`
requirements "Set the target language by replacing the first dc:language", "Set the FB2 target language without touching
the recorded source language" and "Declare no language metadata in formats that carry none"
(`openspec/specs/document-round-trip/spec.md`).
In plain words: the output opens in the same readers as the source, keeps its formatting, images and structure, and says
it is in the new language. A book can be written at any point, so each segment is written with the best text it has:
the person's edit over the machine's, the machine's over the source, and the source where nothing better exists yet.
Reading the source afresh each time means two exports of the same decisions produce the same file. A Markdown book
that already says `lang: en` is told it is now `uk`; one that says nothing is not given a key it never had.

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

### Requirement: Check the written book before it replaces the destination

The system SHALL write the translated book to a temporary file in the destination folder and open that file again. It
SHALL move the file onto the destination only when it opens with as many body segments as the source and every
re-opened body segment carries the same placeholders as the segment written into it — the same count, in the same
order, and each placeholder's markup the same once any image's alt text in it is set aside — and SHALL replace an
existing destination only when replace-if-exists is on. The check SHALL cover the book's body segments only, and SHALL
NOT count or compare its auxiliary text.

IF any of the following happens, THEN the system SHALL leave the destination unchanged, write no side file, remove the
temporary file, and report the error:

- the source file changed since the book was opened;
- writing fails;
- the written file does not open;
- the written file's body segment count differs from the source's;
- a re-opened body segment's placeholders differ from those written into it, in which case the report SHALL name the
  first such segment by its locator.

**Source:** FR-EXPORT-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`),
`02_Architecture/09_ERROR_HANDLING.md#partial-results`, `docs/next_features.md#14-for-the-translation-screen`, ADR-0041.
In plain words: a half-written file, or a book that silently lost paragraphs, never takes the place of a good one.
Counting segments catches lost content; comparing each segment's placeholders catches a paragraph whose formatting a
translation damaged while the count stayed right, and naming the first one tells the person where to look. An image's
alt text is translated on its own, as auxiliary text, yet it sits inside the markup of the paragraph that holds the
image, so the markup is compared with the alt text set aside — in XML and XHTML the `alt` attribute's value, in
Markdown the text between `![` and `](` — or every translated alt text would fail the check, even in a paragraph left
in the source. The auxiliary text is not re-counted because its segments can legitimately change on re-opening: an NCX
label translated to the same words as its navigation label becomes one segment with it, and a frontmatter value
written as `"1984"` holds no letter and is no longer text; its write-back is proven by document-round-trip's "Write
auxiliary translations back into their own slots".

#### Scenario: A segment-count mismatch leaves the destination alone

- **WHEN** `Book.uk.md` exists, replace-if-exists is on, and the written file opens with 2 body segments where the
  source has 3
- **THEN** the export reports `ErrorCode.validation`
- **AND** `Book.uk.md` is unchanged and no temporary file remains in its folder

#### Scenario: A placeholder mismatch names the segment

- **WHEN** the segment `ch5 · p12` was written carrying `⟦g1⟧⟦g2⟧⟦g3⟧` and re-opens carrying `⟦g1⟧⟦g2⟧`, while the
  segment count matches
- **THEN** the export reports `ErrorCode.validation` naming `ch5 · p12`
- **AND** the destination is unchanged and no temporary file remains in its folder

#### Scenario: A translated alt text inside a translated paragraph passes the check

- **WHEN** the EPUB paragraph `<p>Before <img src="fig1.png" alt="Figure 1"/> after</p>` is translated, its image's
  alt text `Figure 1` is translated as `Рисунок 1`, and the book is exported
- **THEN** the check passes and the book is written
- **AND** the written paragraph's image reads `<img src="fig1.png" alt="Рисунок 1"/>`

#### Scenario: A translated alt text inside a pending paragraph passes the check

- **WHEN** the EPUB paragraph `<p>Before <img src="fig1.png" alt="Figure 1"/> after</p>` is still PENDING, its image's
  alt text `Figure 1` is translated as `Рисунок 1`, and the book is exported
- **THEN** the check passes and the book is written
- **AND** the written paragraph reads `<p>Before <img src="fig1.png" alt="Рисунок 1"/> after</p>`

#### Scenario: A translated Markdown alt text passes the check

- **WHEN** the Markdown paragraph `See ![Figure 1](fig1.png) below.` is translated, the image's alt text `Figure 1` is
  translated as `Рисунок 1`, and the book is exported
- **THEN** the check passes and the book is written
- **AND** the written paragraph holds `![Рисунок 1](fig1.png)`

#### Scenario: An NCX label that becomes equal to its navigation label passes the check

- **WHEN** an EPUB's NCX label `The Storm` differs from its navigation label `Storm`, both are translated as `Буря`, and
  the book is exported
- **THEN** the check passes and the book is written, although on re-opening the two labels are one auxiliary segment

#### Scenario: A failed write leaves nothing behind

- **WHEN** the destination folder is deleted as the export starts
- **THEN** the export reports the write error, and neither the destination nor a temporary file exists

#### Scenario: The source changed during the job

- **WHEN** the bytes of the source file change after the book was opened and before the export
- **THEN** the export reports `ErrorCode.validation` and nothing is written

#### Scenario: Overwrite replaces an existing destination

- **WHEN** `Book.uk.md` exists, replace-if-exists is on, and the written file opens with the source's body segment
  count and placeholders
- **THEN** `Book.uk.md` holds the new translation and no temporary file remains in its folder

### Requirement: Report the finished file

WHEN an export succeeds, the export screen SHALL show the title "Translated book ready" and four tiles: the number of
segments written with a translation ("segments translated"), the share of segments accepted without review
("auto-accepted"), the number of segments the person accepted, edited or reverted ("reviewed by you"), and whether the
written file passed its verification ("file validates").

WHEN an export succeeds, the system SHALL report with the written file: the number of segments written with a
translation; the number of pending segments written in the source language; the number of segments kept as source by
choice; the number of flagged segments written with their machine translation; the number of segments accepted
without review; and the number of segments the person reviewed.

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

WHILE no export of the open book has succeeded in the session, the export screen SHALL list no checks, SHALL show the
file-validates tile as not yet known, and SHALL offer nothing to reveal or open. WHILE a run is translating, the export
screen SHALL offer no control that writes a book, and its side-file choices and consistency-pass switch SHALL stay
visible and unavailable.

**Source:** FR-EXPORT-01, FR-EXPORT-03, FR-EXPORT-04
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-export-complete`,
`docs/next_features.md#15-left-open-by-the-hand-test`.
In plain words: after pressing Export the person sees that the book exists, that it was checked, and how much of it the
machine did on its own. The check line says what the application actually verified — it re-opens the file — rather
than the reference's EPUBCheck, which is not run. Windows Explorer returns 1 even when it succeeds, so treating that as
a failure would report an error for a file that was shown. Showing and opening the file use the system's own commands,
built from the path alone, never a shell and never the network. The counts say exactly what the file holds — how much
the machine translated on its own, what the person reviewed, what is still in the source language because it was not
reached, and what is in it because the person chose so — and they are what the tiles, the report side file and the
command line's report line show. One success notice is enough: the dialog already names the file, so a transient
message on top of it would only repeat it.

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
- **THEN** the glossary, bilingual-copy and report choices and the consistency-pass switch are present and unavailable

## REMOVED Requirements

### Requirement: Decide where the translation is written before the run starts

**Reason**: The translation job no longer writes the book; export is a separate action started from the export screen,
which owns the destination and the replace choice (ADR-0035). A destination on the book brief would be asked for before
it is needed and ignored by edits made after the run.

**Migration**: The destination, its default name, the shared naming rule and the occupied-path warning move to the export
screen — see "Choose where to save on the export screen" and "Replace an existing file only when the person allows it".
The command line keeps its flags, output name and exit codes.
