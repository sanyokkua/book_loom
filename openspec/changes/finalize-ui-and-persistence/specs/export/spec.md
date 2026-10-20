## ADDED Requirements

### Requirement: Show the tiles from the project before any export

The export screen SHALL show four tiles filled from the project before any export: how many segments are done ("74 of 142"), how many were translated without changes, how many were reviewed by the person, and "checked when written", which SHALL read "—" until a file is written and "opens, nothing missing" afterwards.

**Source**: docs/specification/mockups/README.md#step-5--export--e1e3-i, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (E1). In plain words: the person sees how much of the book is done before choosing to write it, instead of four empty tiles that only fill after the file exists.

#### Scenario: Tiles show the project's figures before an export

- **WHEN** the project `Earth Gravity · EN → UK` has 74 of 142 segments done, 50 translated without changes and 6 reviewed by the person, and nothing has been exported
- **THEN** the tiles read "74 of 142", 50 translated without changes, 6 reviewed by you, and "checked when written" with "—"

#### Scenario: The check tile fills in after writing

- **WHEN** the book is written and its check passes
- **THEN** the fourth tile reads "opens, nothing missing", and the other three tiles keep their figures

### Requirement: Show the book's title and author with a reason when there is none

The export screen's title and author card SHALL show the original title and author read-only and the translated title and author editable, SHALL write the translated title the person typed into the exported book, and WHERE the book names no author SHALL say so in one line, "The book names no author".

**Source**: docs/specification/mockups/README.md#step-5--export--e1e3-i, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (E1). In plain words: the person can correct the book's translated title before it is written, and an empty author field says the book simply names none instead of looking like a bug.

#### Scenario: The translated title is editable and written

- **WHEN** `earth-gravity.epub` names the title `Earth Gravity`, the person types `Земне тяжіння` into the translated title and exports the book
- **THEN** the original title stays `Earth Gravity`, read-only, and the written book's title is `Земне тяжіння`

#### Scenario: A book without an author says so

- **WHEN** `earth-gravity.epub` names the title `Earth Gravity` and no author
- **THEN** the card shows the original title, an editable translated title, and the line "The book names no author"

### Requirement: Take the original title in a fixed order and say where it came from

The system SHALL take the book's original title from the first source that has one — the book's metadata title, then for Markdown the front-matter title, then for Markdown the first heading, then the file name without its extension — and the card SHALL show under it the reason line of that source: "From the book's details", "From the front matter", "From the first heading" or "The book names no title — the file name is used".

**Source**: docs/specification/mockups/README.md#step-5--export--e1e3-i, openspec/changes/finalize-ui-and-persistence/design.md "K8". In plain words: every book gets a title, a Markdown book still gets one from its own text, a plain-text book uses its file name, and the person can see which of these it was.

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

**Source**: docs/specification/mockups/README.md#dialog--suggest-a-file-name-g4-short, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (E1, G4). In plain words: a model call that picks a file name is still a model call, so the person sees it and decides whether to take its answer.

#### Scenario: A suggested name is used only on request

- **WHEN** the model answers `earth-gravity.uk.md` and the person presses "Use this name"
- **THEN** the Save to field names `earth-gravity.uk.md`

#### Scenario: Cancel keeps the current name

- **WHEN** the person presses Cancel while the model has not answered
- **THEN** the Save to field is unchanged

### Requirement: Show the last check as a progress dialog the person can leave or end

WHEN an export runs the last check before saving, the application SHALL show a progress dialog with the groups Steps (Check the translation is complete, Fix he/she forms that became known, Translate unsure segments again), Reread next to the neighbours (one item per segment with its result) and Finish (Write the file), and the figures line "48 % · 12 of 25 steps · Left about 2 min".

**Source**: docs/specification/mockups/README.md#dialog--export--last-check-before-saving-g4-long--e2-e3, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (E2, E3, G4). In plain words: the last check can take minutes, so the person sees each step and each reread paragraph rather than a spinner.

#### Scenario: The groups and figures are shown

- **WHEN** the last check is at 12 of 25 steps with 6 of 18 paragraphs reread
- **THEN** the dialog shows "48 % · 12 of 25 steps · Left about 2 min" and the group "Reread next to the neighbours · 6 of 18"

### Requirement: Show each reread item next to its neighbours with a before and an after

Selecting a reread item SHALL show a bordered Before | After grid holding the segment before, this segment and the segment after, and a fix SHALL show the old text struck through above the new one; an item that did not change SHALL read "Unchanged — kept as it was".

**Source**: docs/specification/mockups/README.md#dialog--export--last-check-before-saving-g4-long--e2-e3, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (E2). In plain words: the person can judge each change the pass made in its place in the story, and unchanged paragraphs are said to be unchanged.

#### Scenario: A fix shows the old text struck through

- **WHEN** the item `ch4 · p07` was fixed from `Вона втомився` to `Вона втомилася`
- **THEN** the After column shows `Вона втомився` struck through above `Вона втомилася`, between the segments before and after it

#### Scenario: An unchanged item says so

- **WHEN** the item `ch4 · p08` was reread and left as it was
- **THEN** its detail reads "Unchanged — kept as it was"

### Requirement: Let the person write the file early or cancel the last check with a question first

The last-check dialog SHALL offer "Write the file now", which asks first "6 of 18 reread, 1 fixed; the other 12 stay as they are", and Cancel, which asks "Cancel the export? No file is written."; Continue in background SHALL hide the dialog and Esc SHALL hide it too.

**Source**: docs/specification/mockups/README.md#dialog--export--last-check-before-saving-g4-long--e2-e3, docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (E2, E3). In plain words: stopping early is allowed but never silent: the person is told what stays as it is, and cancelling is confirmed because it throws the work away.

#### Scenario: Write the file now asks what stays

- **WHEN** 6 of 18 items are reread and 1 is fixed, and the person presses "Write the file now"
- **THEN** a question reads "6 of 18 reread, 1 fixed; the other 12 stay as they are", and confirming writes the file

#### Scenario: Cancel asks before discarding

- **WHEN** the person presses Cancel in the dialog
- **THEN** the question "Cancel the export? No file is written." appears above the dialog, and answering yes ends the export with no file written

#### Scenario: Esc hides the dialog

- **WHEN** the person presses Esc in the last-check dialog
- **THEN** the dialog is hidden into the activity chip, no question is asked, and the pass continues

#### Scenario: Continue in background hides the dialog

- **WHEN** the person presses "Continue in background"
- **THEN** the dialog is hidden into the activity chip, the pass continues, and pressing the chip shows the dialog again

### Requirement: Announce a partial export in a banner

WHILE the run state is Translating, Stuck call, Pausing or Stopping, the export screen SHALL show the banner "Pause or stop the translation to export. You can export what is done so far; the rest keeps the original text." with a Pause button; WHEN some but not all segments are translated and the run state is none of these, it SHALL show "74 of 142 segments are translated. The other segments keep the original text".

**Source**: docs/specification/mockups/README.md#step-5--export--e1e3-i, docs/specification/mockups/README.md#changes-while-translating-i. In plain words: the person is told before writing exactly what a partial book will hold, and how to get to a state where it can be written.

#### Scenario: The banner follows the run state

- **WHEN** the translation is running at 51 %, and later paused with 74 of 142 segments translated
- **THEN** the banner first offers Pause, and then reads "74 of 142 segments are translated. The other segments keep the original text"

### Requirement: Run the whole-book backward revision before writing the book

WHEN an export starts with the last check switched on, the system SHALL run the whole-book backward revision — the locked-name sweep and the resolution of deferred items — before the book is written, and SHALL leave every segment the person edited as the person wrote it.

**Source**: FR-EXPORT-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-ALGO-10 (`#fr-algo`), FR-REVIEW-10 (`#fr-review`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`, docs/specification/mockups/README.md#dialog--export--last-check-before-saving-g4-long--e2-e3. In plain words: a name settled in chapter 20 can be carried back into chapter 2 before the book is written; a person's own edit is never overwritten by it.

#### Scenario: A locked name is swept through earlier chapters

- **WHEN** the glossary held `Mira` → `Міра` while chapter 2 was accepted rendering it `Міра`, the person then
  changed the target to `Мірра` and locked it, and the book is exported with the switch on
- **THEN** chapter 2 in the written book renders the name `Мірра`

#### Scenario: The pass is reported after the export

- **WHEN** the book is exported with the switch on and the pass adjusted 3 segments
- **THEN** the screen's checks and the export-complete dialog read "Consistency pass: 3 segments adjusted"
- **AND** when it adjusted none they read "Consistency pass ran — nothing needed changing", unless deferrals are
  still open: with 142 segments waiting for a character's gender they read "Consistency pass: 142 segments await the
  gender of a character; set it in Names & style and run the pass again" (the pass records the open deferrals by
  reason in its summary)
- **AND** with the switch off neither mentions the pass
- **AND** after a pass whose fresh drafts improved 4 flagged or doubtful segments and kept 2, whose neighbour check
  left 27 paragraphs unchanged, refused 3 answers (2 by `quotes`, 1 by `worse`) and skipped 1 failed call, they also
  read "Consistency pass: a fresh draft improved 4 flagged or doubtful segments; 2 were no better and stayed as
  before", "Consistency pass: 27 paragraphs checked against their neighbours needed no change", "Consistency pass: 3
  answers were refused because they would make the text worse (quotes: 2, worse: 1)" and "Consistency pass: 1 segment
  was skipped — the model call failed"

#### Scenario: The progress dialog shows the pass's current model call

- **WHEN** the export's neighbour check is waiting on the model for `ch1 · p08`
- **THEN** the last-check dialog shows the step "Reread next to the neighbours" with the item for `ch1 · p08` marked "reading…", and its detail holds the paragraph that was sent
- **AND** while the pass drafts doubted segments again the step "Translate unsure segments again" is the one in progress

#### Scenario: A model that cannot be created does not stop the export

- **WHEN** the book is exported with the switch on and the chosen provider cannot be reached, or its model cannot be
  created
- **THEN** the book is written with the name sweep only, and one warning is logged
- **AND** the result reads "Consistency pass: the gender step was skipped — no model available"

#### Scenario: A person's edit survives the pass

- **WHEN** the person saved an edit of `ch2 · p04` reading `Міра прийшла.` and the book is exported with the switch on
- **THEN** the written `ch2 · p04` reads `Міра прийшла.`

## MODIFIED Requirements

### Requirement: Replace an existing file only when the person allows it

IF a file already exists at the Save to path, or at the path of a side file that is chosen, THEN the export SHALL ask before replacing it, naming the file, and SHALL replace it only when the person answers that it may be replaced; the screen SHALL carry no replace switch.

**Source**: FR-EXPORT-03, FR-EXPORT-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), ADR-0035, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, docs/specification/mockups/README.md#step-5--export--e1e3-i. In plain words: an existing book is never replaced by accident, and the protection is a question at the moment it matters; side files follow the same rule.

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

### Requirement: Check the written book before it replaces the destination

The system SHALL write the book to a temporary file beside the destination, open it again, and move it onto the destination — replacing an existing file only when the person agreed — only when it has the source's body segment count and every body segment keeps its placeholders, alt text set aside; a segment whose stored target fails the placeholder gate, or whose placeholders differ, SHALL be written in its source and reported; any other failure SHALL leave the destination unchanged.

**Source**: FR-EXPORT-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, ADR-0041. In plain words: the book is proven to open with nothing lost before it takes the place of a good one, and one paragraph whose formatting broke is written in its source and named instead of costing the whole book; only body segments are counted, because auxiliary segments can legitimately merge or vanish on re-opening.

#### Scenario: A segment-count mismatch leaves the destination alone

- **WHEN** `Book.uk.md` exists, the person agreed to replace it, and the written file opens with 2 body segments where the source has 3
- **THEN** the export reports `ErrorCode.validation`, no side file is written
- **AND** `Book.uk.md` is unchanged and no temporary file remains in its folder

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

- **WHEN** the destination folder is deleted as the export starts
- **THEN** the export reports the write error, and neither the destination nor a temporary file exists

#### Scenario: The source changed during the job

- **WHEN** the bytes of the source file change after the book was opened and before the export
- **THEN** the export reports `ErrorCode.validation` and nothing is written

### Requirement: Name the proposed file as the command line names its output

The proposed name SHALL be the name the command line gives its output for the same source file and target language; WHERE the project is named and another project of the same book has the same pair, it SHALL add after the language code a label: the part of the project name after its automatic name, else after the book title, else the whole name, lower-cased, spaces turned into hyphens, keeping only letters, digits and hyphens.

**Source**: FR-EXPORT-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, docs/specification/mockups/README.md#project-names--one-book-several-translations-pn. In plain words: the screen and the command line never disagree about where a book goes, and a second translation of the same pair never replaces the first on disk.

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

### Requirement: Choose where to save on the export screen

The export screen SHALL show where the book is saved as a short path such as "Books ▸ earth-gravity.uk.md (in your home folder)" whose tooltip holds the full path, with Change… that opens the system's save dialog and "✦ Suggest a name" that proposes a file name.

**Source**: FR-EXPORT-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), DD-30, ADR-0035, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, docs/specification/mockups/README.md#step-5--export--e1e3-i. In plain words: the person sees a readable place, not a long absolute path, and can still read the full path on hover; the name matches the command line's because for a `.fb2.zip` book the suffix is two extensions and the two must never disagree about where a book goes.

#### Scenario: The Save to field and Browse are present

- **WHEN** the export screen is shown for the open project
- **THEN** it shows the short path "Books ▸ earth-gravity.uk.md (in your home folder)" with the full path as its tooltip, a Change… button and a "✦ Suggest a name" button

#### Scenario: Change… opens the system dialog

- **WHEN** the person presses Change…
- **THEN** the system's save dialog opens on the current folder and the chosen path replaces the short path

### Requirement: Export at any time except while a run is translating

WHILE the run state is Translating, Stuck call, Pausing or Stopping, Export book SHALL be disabled with the tooltip "Pause or stop the translation to export"; with nothing translated yet it SHALL be disabled with the banner "Nothing is translated yet."; otherwise it SHALL be available.

**Source**: FR-REVIEW-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-REVIEW-X1 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), ADR-0035, docs/specification/mockups/README.md#changes-while-translating-i, docs/specification/mockups/README.md#run-states-a. In plain words: the book can be taken out whenever something is translated and the run is not actively deciding segments.

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

### Requirement: Run the final consistency pass before writing when it is switched on

The export screen SHALL offer the last check before saving as a switch ("Check the uncertain places again — Recommended. Rereads the segments that needed fixing next to their neighbours."), its starting value coming from the Automation setting for the last check.

The switch SHALL be off and disabled for a partial export, with the note "Runs when the whole book is translated".

**Source**: FR-EXPORT-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), FR-BRIEF-08 (`#fr-brief`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`, docs/specification/mockups/README.md#step-5--export--e1e3-i. In plain words: the last check is slow, so it starts from the person's saved preference and is not offered while part of the book is untranslated.

#### Scenario: The pass follows the quality dial by default

- **WHEN** the Automation setting for the last check has not been changed, the quality dial is Max, and the export screen is shown for a fully translated book
- **THEN** the last-check switch is on
- **AND** with the dial on Balanced it is off

#### Scenario: A saved Automation choice sets the starting value

- **WHEN** the person set the Automation last-check default to on and the quality dial is Balanced
- **THEN** the last-check switch starts on

#### Scenario: A partial export turns the last check off

- **WHEN** 74 of 142 segments are translated and the export screen is shown
- **THEN** the last-check switch is off and disabled with "Runs when the whole book is translated"

## REMOVED Requirements

### Requirement: Say so when no book is open on the export screen

**Reason**: The five workflow steps are locked until a project is open, so the Export screen cannot be reached without one and the "no book is open" state has no way to appear.

**Migration**: The locked Export step with the hint "Steps unlock when a project is open" (the `app-shell` capability) replaces it; the Projects screen is where a project is opened.

### Requirement: Run the whole-book backward revision before the book is written

**Reason**: The pass now runs in the last-check progress dialog instead of the old busy card; the scenario that named the busy card is restated for the dialog.

**Migration**: Use "Run the whole-book backward revision before writing the book", which keeps the same rule and the same reports.
