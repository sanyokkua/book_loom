## ADDED Requirements

### Requirement: Warn before export about every segment that has no translation

WHERE export of the stored book would write paragraphs in the source language for want of a usable translation, the export screen SHALL show, before Export book is pressed and except while a run is translating, one warning that gives their number and says they can be translated or edited on the review desk. The number SHALL be computed by the code that decides what export writes and SHALL equal the one the finished run state gives.

**Source**: `research/review-digest.md#f12--finished-is-shown-for-a-book-with-untranslated-paragraphs`, `research/review-digest.md#f3b--a-paragraph-with-no-accepted-target-is-exported-in-the-source-language`, `design.md#d5--one-number-for-no-translation-shown-before-export`. In plain words: a book with paragraphs still in the source language must never be a surprise found by the reader. The number is not a guess from the stored statuses: it is what export itself would do with the book right now. It counts a flagged paragraph that has no translation and no refused reply good enough to write, and a paragraph whose stored translation lost its formatting marks and so falls back to the source. It does not count what is in the source language on purpose (kinds the brief keeps, paragraphs with nothing to translate, kept foreign passages), and it does not count a flagged paragraph whose refused reply export will write.

#### Scenario: Three segments have no translation

- **WHEN** a finished run left `ch12 · p02`, `ch12 · p06` and `ch14 · p01` FLAGGED with no machine translation and no refused reply that passes the blocking checks, and the export screen is shown
- **THEN** it reads "3 segments have no translation and will be written in the source language. Translate or edit them on the review desk."
- **AND** Export book is available

#### Scenario: One segment uses the singular form

- **WHEN** only `ch12 · p02` is in that state
- **THEN** the warning reads "1 segment has no translation and will be written in the source language. Translate or edit it on the review desk."

#### Scenario: The person's edit removes the segment from the count

- **WHEN** the person saves the edit `Вона мовчала.` for `ch12 · p02` on the review desk
- **THEN** the warning no longer counts `ch12 · p02`

#### Scenario: A refused reply that export will write is not counted

- **WHEN** `ch12 · p06` is FLAGGED with no machine translation, and its stored refused reply `Він довго мовчав.` restores its formatting marks and fails no blocking check
- **THEN** the warning does not count `ch12 · p06`, and the written book holds `Він довго мовчав.` for it

#### Scenario: A translation whose formatting marks cannot be restored is counted

- **WHEN** `ch03 · p11` is ACCEPTED with the stored translation `Вона ⟦g0⟧засміялася.`, whose source holds `⟦g0⟧` and `⟦g1⟧`, so its marks cannot be restored
- **THEN** the warning counts `ch03 · p11`, and the written book holds its source text

#### Scenario: Source text kept on purpose is not counted

- **WHEN** the brief keeps footnotes untranslated, the book holds 14 footnotes and the paragraph `1999` was kept as it is because it has nothing to translate
- **THEN** the warning counts none of the 15

#### Scenario: A book with a translation for every segment shows no warning

- **WHEN** export would write no paragraph in the source language for want of a translation
- **THEN** the export screen shows no such warning

### Requirement: Show the rendering report on the Export screen

WHEN the Export screen is shown for a project that holds decided body paragraphs, the application SHALL show a closed fold "Names and dialogue in the book" whose summary line counts the rows agreed, split and without a rendering and the dialogue styles, and whose body lists the split rows and the rows without a rendering first, with their renderings and counts. The figures SHALL be counted from the stored texts each time the screen reads them.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `research/review-digest.md#consistency-numbers`, `design.md#d19--the-rendering-report`. In plain words: the person should learn before writing the file that a place is spelled two ways 15 and 11 times, not from a reader afterwards. The fold is closed so a clean book costs one line; the rows that need a decision come first. Because the count is made again whenever the screen reads its figures — when a project opens, a run ends, a review-desk action is stored or a book is exported — an edit made after the run is in the next reading.

#### Scenario: A book with one split row

- **WHEN** the stored texts give 41 agreed rows, the split row `Velk` (`Велк` 15, `Вельк` 11), no row without a rendering, 1,380 dash paragraphs and 46 quoted ones
- **THEN** the fold's line reads "Names: 41 the same all through, 1 written two ways, 0 without a translation · Speech: 1,380 with a dash, 46 in quotation marks"
- **AND** the opened fold lists `Velk` first with "Велк 15 · Вельк 11"

#### Scenario: A project with no report shows no fold

- **WHEN** the project holds no decided body paragraph
- **THEN** the Export screen shows no "Names and dialogue in the book" fold

#### Scenario: A split row leads to the glossary

- **WHEN** the person presses the row `Velk` in the opened fold
- **THEN** Names & style opens with `Velk` selected, where typing a target pins it for the next run or review

#### Scenario: An edit on the review desk is in the next reading

- **WHEN** the row `Hale` reads agreed with `Гейл` 24, and the person then saves an edit on the review desk that writes `Хейл` in one of those paragraphs, and the Export screen is shown again
- **THEN** the row `Hale` reads "Гейл 23 · Хейл 1" among the split rows

### Requirement: Print the rendering report at the end of a command-line run

WHEN a command-line run ends, the application SHALL print the summary line of the rendering report stored with the run, its split rows and its rows without a rendering, and SHALL write the whole report into the run's report file.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `design.md#d19--the-rendering-report`. In plain words: the command line already printed a consistency figure, but it was counted over the removed recurring-term list and scored a consistently wrong name as perfect. The report counted at the end of the run's Proofread stage replaces it, so a headless run and the evaluation read the names the way a reader would.

#### Scenario: The summary and the split rows are printed

- **WHEN** a command-line run ends with 41 agreed rows and the split row `Velk`
- **THEN** the output holds `renderings agreed=41 split=1 none=0 dash=1380 quotes=46 fixed=14 notFixed=1` and one line for `Velk` with its two counts

## MODIFIED Requirements

### Requirement: Write a flagged segment in its source when no usable translation exists

IF a FLAGGED segment holds no machine translation, no edit by the person and no refused reply that restores its formatting marks and fails no blocking check, THEN the system SHALL write its source text; a FLAGGED segment that holds a machine translation SHALL be written with it, and one with only such a refused reply SHALL be written with that reply. IF the stored translation of any segment cannot have its formatting marks restored, THEN the system SHALL write that segment's source text.

**Source**: `research/review-digest.md#f12--finished-is-shown-for-a-book-with-untranslated-paragraphs`, `research/review-digest.md#f3b--a-paragraph-with-no-accepted-target-is-exported-in-the-source-language`, `design.md#d5--one-number-for-no-translation-shown-before-export`. In plain words: a paragraph that is not a translation — an English leftover, a refusal, broken formatting marks — is never written as one. A real translation is better for the reader than a paragraph in the other language, so export writes the flagged segment's machine translation when it has one, and else the model's last refused reply when that reply is fit to print. Every paragraph that still falls back to its source is what the warning before export counts.

#### Scenario: A flagged segment keeps its source text

- **WHEN** the paragraph `He opened the *old* door.` is FLAGGED because every reply for it failed the placeholder check
- **THEN** the written book contains `He opened the *old* door.` unchanged

#### Scenario: A kept machine translation is written

- **WHEN** the paragraph `She closed the window. Then she sat down.` is FLAGGED with a `sentence-count` finding and holds the machine translation `Вона зачинила вікно і сіла.`
- **THEN** the written book contains `Вона зачинила вікно і сіла.`, and the export counts the segment among the flagged segments written with their machine translation

#### Scenario: A fit refused reply is written

- **WHEN** the paragraph `He waited a long time.` is FLAGGED with no machine translation, and its stored refused reply `Він довго чекав.` restores its formatting marks and fails no blocking check
- **THEN** the written book contains `Він довго чекав.`

#### Scenario: A translation with lost formatting marks falls back to the source

- **WHEN** the stored translation of `She ⟦g0⟧laughed⟦g1⟧.` is `Вона ⟦g0⟧засміялася.`
- **THEN** the written book contains the source paragraph, and the export report lists it among the paragraphs written in the source language

## REMOVED Requirements

### Requirement: Warn about segments that will be written in the source language because their formatting marks were lost

**Reason**: The warning covered one cause only. "Warn before export about every segment that has no translation" replaces it with one warning and one number for every paragraph export would write in the source language for want of a translation, lost formatting marks included (`research/review-digest.md#f12--finished-is-shown-for-a-book-with-untranslated-paragraphs`).

**Migration**: The banner `export-banner-source-fallback` and its message `export.banner.source_fallback` are replaced by the new warning; the count `ReviewCounts.sourceFallback` is deleted.
