## ADDED Requirements

### Requirement: Keep both term tables editable while a translation runs

WHILE a translation of the project is running, pausing or stopping, the Names & style screen SHALL keep the Glossary and the Recurring terms editable under the banner "Translating is going on. Changes apply to the next segments translated."

**Source**: `docs/specification/mockups/README.md#changes-while-translating-i`, `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, N1–N5 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: hand edits stay possible because the next group of segments reads the glossary again. Which model actions are disabled while a translation runs is the `app-shell` capability's rule ("Disable model actions while a translation runs"), and the read-only screen while the names pass runs hidden is also the `app-shell` capability's rule ("Make the screen that hidden work writes to read-only").

#### Scenario: Typing a translation stays possible

- **WHEN** the translation of `Earth Gravity · EN → UK` is running and the person types `Велк` as the translation of `Velk`
- **THEN** the entry is stored, and the banner says the change applies to the next segments translated

### Requirement: Let every glossary column be resized

The glossary table SHALL let every column be resized, the last filling the remaining width, with minimum widths (Term 140 px, Kind 96, Translation 170, Gender 110, Locked 64, remove 36), grid lines, a header that stays visible and fixed row heights.

**Source**: `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, `docs/specification/mockups/README.md#g9--two-kinds-of-table-jx-g4`, N2 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: the person can widen what is cut, and a column can never be dragged so narrow that its content disappears.

#### Scenario: A column can be resized

- **WHEN** the person drags the border of the Term column to 220 px, and then tries to drag the Locked column to 30 px
- **THEN** Term is 220 px wide and Locked stays at its 64 px minimum

### Requirement: Show the recurring terms with their purpose, use count and empty state

The Recurring terms card SHALL show the purpose line "Words the book repeats — kept the same all through the book. Learned while translating, or set here." and the columns Term, Translation, Used and a link to the glossary, Used being the number of times the term's words occur in the book's text, ignoring letter case and matching exact word forms only; an empty card SHALL read "No recurring terms yet. They are found when you prepare names, or while translating."

**Source**: `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, N1 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: the person sees how often each repeated word occurs and where such words come from. Used counts the words as written: `Warden` and `warden` count together, while an inflected form such as `wardens` is a different word and is not counted.

#### Scenario: An empty lexicon says where terms come from

- **WHEN** no recurring term exists yet
- **THEN** the card reads "No recurring terms yet. They are found when you prepare names, or while translating."

#### Scenario: Used counts exact word forms, ignoring case

- **WHEN** the book's text holds `warden` 9 times, `Warden` 4 times and `wardens` 3 times
- **THEN** the row of `warden` shows Used `13 times`

### Requirement: List the eight steps of the names pass in its progress dialog

The progress dialog of the names pass SHALL list eight steps in order — Find candidates in the book, Decide the names, Find repeated words, Clean up repeated words, Read repeated words in context, Suggest translations for names, Suggest translations for repeated words, Write the results — each with its count ("25 of 30 requests") and a figures line such as "53 % · 26 of about 49 steps · Left about 1 min".

**Source**: `docs/specification/mockups/README.md#dialog--prepare-names-automatically--results-g4-long--n4-n5`, N4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: the person sees which part of the pass is running and how much is left. The dialog itself follows the progress-dialog pattern of the `app-shell` capability.

#### Scenario: The eight steps are listed with counts

- **WHEN** the pass is at its fifth step with 25 of 30 requests answered
- **THEN** the list shows eight rows, the fifth reads "Read repeated words in context" with "25 of 30 requests", and the steps after it say they have not started

### Requirement: Show the names pass's results with Undo all

WHEN the pass finishes, its dialog SHALL become a Results view with the subtitle "5 added · 0 removed · 1 changed · 49 steps · 1 min 48 s", the tabs Changes (Entry, Change, Before, After, grouped) and Steps, and the actions Undo all, Show changed rows and Done.

**Source**: `docs/specification/mockups/README.md#dialog--prepare-names-automatically--results-g4-long--n4-n5`, N5 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: the person sees every change the pass made and can give all of them back.

#### Scenario: Results list the changes

- **WHEN** the pass adds `Mira Holt` and `lantern` and drops `Hale`
- **THEN** the Results view shows "2 added · 1 removed" in its subtitle and lists the three changes in the Changes tab

### Requirement: Run the Scan, Review and Translate actions in the same dialog with two steps

The Scan, Review and Translate actions on a card SHALL run the progress dialog with two steps, the action's requests and then the write, and SHALL NOT be hideable.

**Source**: `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, N4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: every model action looks the same, and only the long names pass can be hidden.

#### Scenario: A card action runs two steps and cannot be hidden

- **WHEN** the person presses Scan on the glossary card
- **THEN** the dialog lists two steps — the scan's requests and Write the results — and offers no Continue in background

### Requirement: Apply glossary edits from the next chunk and keep the glossary across a restart

WHEN the person edits the glossary while a run is paused or translating, the application SHALL apply the edit from the first chunk that starts after it, SHALL NOT change segments already decided except through backward revision on the Max dial or the export's last check, and SHALL keep the glossary stored with the project across a restart; a person's edit and a name the run adds SHALL never overwrite each other.

**Source**: FR-GLOSS-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), ADR-0043, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, `docs/specification/mockups/README.md#changes-while-translating-i`. In plain words: a person who spots a wrong spelling mid-run can fix it, and the rest of the book follows; earlier segments are corrected by the last check or review, not silently. A chunk reads the glossary once, when it starts. Each change is written to the stored entry in one step, so the run adding a name at the end of a chapter and the person typing a target at the same moment both survive.

#### Scenario: A pause-time lock affects later chunks only

- **WHEN** the run is paused after chunk 12 and the person locks `Hale` → `Гейл`
- **THEN** chunk 13 onwards masks `Hale`, and the targets of chunks 1–12 are unchanged by the edit itself
- **AND** on a Balanced run with the export's last check off, they stay unchanged in the written book

#### Scenario: A pause inside a chunk applies from the next chunk

- **WHEN** a Fast Assisted run pauses on the flagged `ch07.xhtml:41` inside the chunk `ch07.xhtml:40`–`ch07.xhtml:47`, and the person locks `Hale` → `Гейл`
- **THEN** `ch07.xhtml:42`–`ch07.xhtml:47` are drafted with `Hale` sent as text
- **AND** the chunk starting at `ch07.xhtml:48` sends `Hale` as a placeholder

#### Scenario: An edit while translating applies from the next chunk

- **WHEN** chunk 9 is being drafted and the person types `Тарн` as the target of `Tarn`
- **THEN** chunk 9 is finished without that target and chunk 10 is the first to receive `Tarn` → `Тарн`

#### Scenario: The glossary survives a restart

- **WHEN** the person adds `Mira Holt` → `Міра Голт`, closes the application and opens the same project again
- **THEN** the glossary contains `Mira Holt` → `Міра Голт`

#### Scenario: A run's addition and a person's edit do not overwrite each other

- **WHEN** the run adds the name `Velk` at the end of chapter 3 while the person types `Тарн` as the target of `Tarn`
- **THEN** the stored glossary holds both `Velk` and `Tarn` → `Тарн`

#### Scenario: The words of a repeated place are not recurring terms

- **WHEN** a book holds `the Great Hall` 4 times, `Old Bailey` 3 times and `the great hall` and `the old courts` in lower case
- **THEN** the key-term scan proposes neither `great`, `hall` nor `old`, while `Master` written 3 times with a capital beside no other capital and `master` 3 times in lower case is still proposed

### Requirement: Keep a stored lexicon of recurring terms and show it on Names & style

The application SHALL keep, for each project, a stored lexicon of recurring common terms and titles that a model would otherwise render differently from one page to the next, SHALL propose them with a deterministic scan and no model, and SHALL show them in a "Recurring terms" card; a term the glossary holds SHALL NOT be asked about or shown from the lexicon.

**Source**: `additional-work.md` 15d.9, `docs/specification/01_Product/12_PROMPT_CATALOG.md#recurring-terms`, `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, ADR-0043. In plain words: a book that calls a warden "наглядач" in chapter one and "сторож" in chapter nine reads as two characters; the app finds the words a book keeps repeating and keeps every later request to one rendering. The lexicon is stored with the project, so it is still there after a restart.

#### Scenario: The scan proposes a title and a dual-use word

- **WHEN** a book's running text holds `Mr.` four times, and `Warden` capitalised mid-sentence three times and `warden` in lower case three times
- **THEN** the scan proposes `mr` and `warden` and nothing for a name written only with a capital or a word written only in lower case

#### Scenario: A held term is not proposed

- **WHEN** the glossary holds `Warden` and the lexicon holds `MR`
- **THEN** the scan proposes neither

#### Scenario: Editing a rendering

- **WHEN** the person types `пан` in the rendering field of `master` and presses Enter
- **THEN** the lexicon stores `пан` as the person's rendering of `master`, every later request is asked to keep it, and clearing the field returns to the most used verified rendering

#### Scenario: Promoting a term

- **WHEN** the person presses "To glossary" on `master`, whose established rendering is `господар`
- **THEN** the glossary holds `master` → `господар` (term, unlocked, the person's target) and the Recurring terms card no longer lists `master`

#### Scenario: A duplicate term is refused

- **WHEN** the person types `WARDEN` in the add field while `warden` is listed
- **THEN** the line above the glossary says `WARDEN is already in the glossary or the list` and no term is added

#### Scenario: What the deterministic scan proposes

- **WHEN** the scan reads the book's running text
- **THEN** it proposes a title of the source language's bundled list (English first) that the text uses at least three times, and a word the book writes both with a capital away from a sentence start and not beside another capitalised word (so it is no part of a name such as `Great Hall`), and in lower case (so it is neither a name nor a function word) at least five times, at most thirty
- **AND** never a term the glossary or the lexicon already holds

#### Scenario: When the scan runs

- **WHEN** a run is prepared and the lexicon is empty, or the person asks on Names & style ("Find recurring terms")
- **THEN** the scan runs
- **AND** a word that is only ever lower case, such as `lantern`, is added by hand

#### Scenario: What the card shows

- **WHEN** the Recurring terms card lists `master`, which the book's text uses 13 times
- **THEN** it shows the term, its Translation (the person's if typed, else the learned or most used verified rendering, else the model's suggestion) and Used `13 times`
- **AND** there is no separate column for the renderings the drafts used

#### Scenario: What the card lets the person do

- **WHEN** the person works in the card
- **THEN** they can edit the rendering (Enter or leaving the field stores it; clearing it returns to the most used), suggest renderings with the model (the glossary's suggestion call over terms of type term, needing a chosen model), add a term by hand (one the glossary or the list already holds is refused in the screen's banner), and remove a term
- **AND** they can promote a term to the glossary, which stores it there as an unlocked entry of type term with its established rendering as the target and removes it from the lexicon

#### Scenario: The lexicon is stored and an edit changes the stored entry

- **WHEN** the person types `пан` as the rendering of `master`, the application is closed and opened again, and the project is opened
- **THEN** the card still lists `master` with `пан`
- **AND** the edit changed the entry as it stands in the store, in one step, never from a copy read earlier: a count a running draft records meanwhile is kept, and a term removed or promoted meanwhile is not created again

### Requirement: Offer the one-pass preparation in a progress dialog

The Names & style screen SHALL offer "Prepare names automatically" in a card above both tables that says what it does and how long it takes for the brief's quality dial ("About 1 min. You can undo it all afterwards."), and a press SHALL run the names pass on the chosen model in the progress dialog, whose Cancel ("Cancel writes nothing.") stops the pass and leaves both tables as they were.

**Source**: `additional-work.md` 15i.U6, `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, N4, N5 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: one button replaces five presses and gives every change back one by one or all together. Hiding the dialog with Continue in background or Esc, and the activity chip, are the `app-shell` capability's progress-dialog rules.

#### Scenario: The press fills both tables and Undo all gives both back

- **WHEN** the person presses "Prepare names automatically" and the pass adds `Mira Holt`, drops `Hale` and adds `lantern`
- **THEN** the Results view shows "2 added · 1 removed" in its subtitle and lists the three changes in the Changes tab
- **AND** Undo all removes `Mira Holt` and `lantern` and puts `Hale` back

#### Scenario: Cancel in the progress dialog changes nothing

- **WHEN** the person presses Cancel in the progress dialog while the pass waits for the model
- **THEN** the dialog says "Stopped. Nothing was changed." and both tables are as they were

#### Scenario: The person's entries are left alone

- **WHEN** the glossary holds a locked `Gatewarden`, a `Marrow Lane` with the person's target and a `Wren` whose female gender the person chose, and the lexicon holds `anvil` with the person's rendering
- **THEN** after the pass those entries are unchanged, `Wren` is still female, and `anvil` was not asked about

#### Scenario: A removal is remembered

- **WHEN** the person removed the recurring term `gantry` and the model would keep it
- **THEN** the pass does not add `gantry` back

#### Scenario: The estimate comes before the first call

- **WHEN** five open names are each mentioned at least three times at the Balanced dial
- **THEN** the estimate is 5 calls and 1 to 2 minutes

#### Scenario: The estimate line

- **WHEN** the screen opens, or a change of the dial has been saved
- **THEN** the card states the estimate again ("About 1 min. You can undo it all afterwards.")
- **AND** the estimate is left out when it cannot be made

#### Scenario: The button is off while a model action runs

- **WHEN** any model action runs, or no model is chosen
- **THEN** the button is off while a model action runs, and with no model chosen a press reports that in the screen's banner and calls nothing

#### Scenario: The pass runs in the progress dialog

- **WHEN** the pass runs
- **THEN** the progress dialog is shown on the work layer, the computer is kept awake, and a stopped or failed pass leaves both tables as they were

#### Scenario: Changed rows are marked on both cards

- **WHEN** the pass succeeds and the person presses Show changed rows
- **THEN** every changed row on both cards shows the left-edge mark, and both cards' changed filters are on

## MODIFIED Requirements

### Requirement: Show the glossary as an editable table on Names & style

The Names & style screen SHALL show the glossary as a table with the columns Term, Kind, Translation (an empty one shows the placeholder "No target"), Gender, Locked and a remove cross, under the purpose line "Names of people and places — written the same way every time. Set the gender so names take the right grammatical form."

**Source**: FR-GLOSS-02, FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, N1, N3 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: this is where the person settles spellings before translating; the table explains its purpose, a changed row is marked on its edge so the term is never cut, an empty table says what to do, and the two cards sit side by side only when the content is at least 1440 px wide.

#### Scenario: A scanned entry appears unlocked

- **WHEN** the scan proposed `Hale`
- **THEN** its row shows Term `Hale`, Kind `other`, an empty editable Translation with the placeholder "No target", Gender "—" and the Locked switch off

#### Scenario: Editing a target and locking it

- **WHEN** the person types `Гейл` into the Translation of `Hale` and turns the Locked switch on
- **THEN** the glossary entry `Hale` reads target `Гейл`, locked

#### Scenario: An empty glossary still offers the start

- **WHEN** the project's glossary holds no entry
- **THEN** the table reads "No names yet. Scan the book for them, or add one." with the buttons Scan the book and + Add
- **AND** Start translation is still enabled

#### Scenario: The Gender column

- **WHEN** the table shows a row's gender
- **THEN** it reads female, male, neuter or unknown for a character and "—" for any other kind, with "(suggested)" only on a gender that was suggested and not confirmed

#### Scenario: A changed row is marked

- **WHEN** the last automatic run changed the row `Earth`
- **THEN** the row shows a mark on its left edge, its tooltip reads "Changed by the last automatic run", and the Term cell reads `Earth` in full with no chip

#### Scenario: The glossary and the Recurring terms are two equal cards

- **WHEN** the content is 1500 px wide, and then 1200 px wide, in the regular and in the compact width class
- **THEN** the two cards sit side by side at 1500 px and are stacked at 1200 px, with the tables filling the available height
- **AND** a card too narrow for its toolbar folds Import and Export into a three-dot menu

#### Scenario: Start translation during a paused run shows it

- **WHEN** the translation of `Earth Gravity · EN → UK` is paused at 55 % and the person looks at the toolbar of Names & style
- **THEN** it offers Go to Translating, pressing it shows the paused translation offering Resume, and no new translation begins

#### Scenario: The screen with no book open

- **WHEN** no project is open
- **THEN** the Names & style step is locked in the navigation with the hint "Steps unlock when a project is open", so the screen cannot be reached and no glossary table, scan button or Start translation is shown

#### Scenario: The banner and the Start translation button

- **WHEN** a project is open
- **THEN** the screen shows the purpose line "Decide how names and repeated words are translated. Optional — skip it and BookLoom builds both while it translates." and the toolbar's Start translation button

#### Scenario: Start translation is pressed while no start is offered

- **WHEN** the project's translation is running, paused, stopped, failed, waiting for the AI model or ready to continue, or it finished
- **THEN** the toolbar offers Go to Translating instead of Start translation, and nothing starts
- **AND** Translating offers Resume for a paused translation and Continue translation for a stopped, failed or ready-to-continue one

#### Scenario: The Type column and the remove column

- **WHEN** the table shows a row
- **THEN** its Kind is one of character, place, term, title or other, and the Translation is editable
- **AND** the last column has no header text and holds the row's remove cross

## REMOVED Requirements

### Requirement: Apply glossary edits made during a pause from the next chunk

**Reason**: The glossary is now stored and stays editable while a run translates, so a rule limited to a pause that also says the glossary does not survive closing the application no longer holds.

**Migration**: Replaced by "Apply glossary edits from the next chunk and keep the glossary across a restart" in this capability, which keeps both pause scenarios and the repeated-place scenario.

### Requirement: Keep a lexicon of recurring terms and show it on Names & style

**Reason**: The lexicon is now stored with the project; the old scenario "The lexicon lives in memory and an edit changes the stored entry" says it does not survive closing the application.

**Migration**: Replaced by "Keep a stored lexicon of recurring terms and show it on Names & style" in this capability, with the same scan, card and edit rules.

### Requirement: Offer the one-pass preparation on Names & style

**Reason**: The busy card and the separate results dialog are replaced by the progress dialog with its Results view; the old scenarios "Cancel on the busy card changes nothing" and "The pass is a blocking activity" describe the removed busy card.

**Migration**: Replaced by "Offer the one-pass preparation in a progress dialog" and "Show the names pass's results with Undo all" in this capability.
