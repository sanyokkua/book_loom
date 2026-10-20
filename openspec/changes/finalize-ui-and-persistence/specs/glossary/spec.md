## ADDED Requirements

### Requirement: Block the model actions on Names & style while a translation is going on

WHILE a translation of the project is running, pausing or stopping, the Names & style screen SHALL disable Prepare names automatically, Scan, Review, Translate and the suggestion of renderings with the tooltip "Pause the translation first — BookLoom sends one model request at a time", and SHALL keep both tables editable under the banner "Translating is going on. Changes apply to the next segments translated."

**Source**: docs/specification/mockups/README.md (Changes while translating; Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5, owner decision on one model request at a time). In plain words: the computer runs one model request at a time, so a names action would only queue behind the translation; hand edits stay possible because the next group of segments reads the glossary again.

#### Scenario: Model actions are disabled while translating

- **WHEN** the translation of `Harbor Lights · EN → UK` is running and the person hovers the disabled Scan button
- **THEN** the tooltip reads "Pause the translation first — BookLoom sends one model request at a time", and Prepare names automatically, Review and Translate are disabled too

#### Scenario: Typing a translation stays possible

- **WHEN** the translation is running and the person types `Гавань` as the translation of `Harbor`
- **THEN** the entry is stored, and the banner says the change applies to the next segments translated

#### Scenario: Pausing re-enables the actions

- **WHEN** the translation reaches Paused
- **THEN** Scan, Review, Translate and Prepare names automatically are enabled

### Requirement: Make Names & style read-only while the names pass runs in the background

WHILE the names pass continues in the background, the Names & style screen SHALL be read-only behind the banner "Names is running in the background — this screen is read-only until it ends", and Start translation SHALL wait with the tooltip "Wait until the names pass ends".

**Source**: docs/specification/mockups/README.md (G4, Continue in background; Changes while translating), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N4, owner decision 11). In plain words: the hidden pass writes its result into these tables once, at the end, so a hand edit made meanwhile could be overwritten; the screen says it is locked instead of letting that happen.

#### Scenario: A hidden pass locks the screen

- **WHEN** the person presses Continue in background while the names pass is at step 4 of 8
- **THEN** the tables and the model buttons are disabled, the banner reads "Names is running in the background — this screen is read-only until it ends", and the title bar shows the chip "Names · 53 % · open"

#### Scenario: Start translation waits for the pass

- **WHEN** the names pass is hidden and the person hovers the Start translation button
- **THEN** it is disabled with "Wait until the names pass ends"

#### Scenario: The end of a hidden pass is announced, not forced

- **WHEN** the hidden names pass finishes
- **THEN** the chip disappears, the screen is editable again, and a toast offers "Review the results"
- **AND** no dialog opens by itself

### Requirement: Let every glossary column be resized

The glossary table SHALL let every column be resized, the last filling the remaining width, with minimum widths (Term 140 px, Kind 96, Translation 170, Gender 110, Locked 64, remove 36), grid lines, a header that stays visible and fixed row heights.

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5), docs/specification/mockups/README.md (G9). In plain words: the person can widen what is cut, and a column can never be dragged so narrow that its content disappears.

#### Scenario: A column can be resized

- **WHEN** the person drags the border of the Term column to 220 px, and then tries to drag the Locked column to 30 px
- **THEN** Term is 220 px wide and Locked stays at its 64 px minimum

### Requirement: Mark a changed glossary row on its left edge

A row changed by the last automatic run SHALL be marked by its left edge with the tooltip "Changed by the last automatic run" and SHALL NOT carry a chip in the term cell, so the term is never cut.

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5), docs/specification/mockups/README.md (G9). In plain words: the mark says what changed without hiding the word the person is reading.

#### Scenario: The mark sits on the edge

- **WHEN** the last automatic run changed the row `Earth`
- **THEN** the row shows a mark on its left edge and the Term cell reads `Earth` in full

### Requirement: Say what an empty glossary needs

WHERE the glossary has no entry, the table SHALL read "No names yet. Scan the book for them, or add one." with the actions Scan the book and + Add.

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5). In plain words: an empty table should tell the person what to do next instead of looking broken.

#### Scenario: The empty table offers Scan and Add

- **WHEN** the project's glossary holds no entry
- **THEN** the table reads "No names yet. Scan the book for them, or add one." with the buttons Scan the book and + Add

### Requirement: Place the two cards side by side only when the content is wide

The glossary card and the Recurring terms card SHALL sit side by side only while the content is at least 1440 px wide, and stacked below, with the tables filling the available height.

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5), docs/specification/mockups/README.md (L5, L7). In plain words: six columns need about 640 px, so two cards share a row only when both fit.

#### Scenario: Cards stack below the wide class

- **WHEN** the content is 1500 px wide, and then 1200 px wide
- **THEN** the two cards sit side by side at 1500 px and are stacked at 1200 px

### Requirement: Offer Start translation or Go to Translating in the toolbar

The Names & style screen SHALL offer Start translation in the toolbar, and Go to Translating instead while the project's translation is running, paused or stopped.

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5), docs/specification/mockups/README.md (L2). In plain words: the main action of the step is always in the toolbar, and it never starts a second translation beside one that exists.

#### Scenario: The toolbar leads to a paused translation

- **WHEN** the translation of `Harbor Lights · EN → UK` is paused at 55 %
- **THEN** the toolbar offers Go to Translating and no new translation begins

### Requirement: Show the recurring terms with their purpose, use count and empty state

The Recurring terms card SHALL show the purpose line "Words the book repeats — kept the same all through the book. Learned while translating, or set here." and the columns Term, Translation, Used and a link to the glossary, Used being how often the book's text uses the term ("13 times"); an empty card SHALL read "No recurring terms yet. They are found when you prepare names, or while translating."

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5). In plain words: the person sees how often each repeated word occurs and where such words come from.

#### Scenario: An empty lexicon says where terms come from

- **WHEN** no recurring term exists yet
- **THEN** the card reads "No recurring terms yet. They are found when you prepare names, or while translating."

#### Scenario: Used counts the book's text

- **WHEN** the book's text uses `master` 13 times
- **THEN** its row shows Used `13 times`

### Requirement: Keep the recurring terms across restarts

The recurring terms SHALL be stored with the project and kept across restarts, and an edit SHALL change the stored entry in one step.

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5), ADR-0043. In plain words: what the book's repeated words were rendered as is not lost when the application is closed.

#### Scenario: The list survives a restart

- **WHEN** the person types `пан` as the rendering of `master`, the application is closed and opened again, and the project is opened
- **THEN** the card still lists `master` with `пан`

### Requirement: List the eight steps of the names pass in its progress dialog

The progress dialog of the names pass SHALL list eight steps in order — Find candidates in the book, Decide the names, Find repeated words, Clean up repeated words, Read repeated words in context, Suggest translations for names, Suggest translations for repeated words, Write the results — each with its count ("25 of 30 requests") and a figures line such as "53 % · 26 of about 49 steps · Left about 1 min".

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5), docs/specification/mockups/README.md (Dialog · Prepare names automatically → Results). In plain words: the person sees which part of the pass is running and how much is left.

#### Scenario: The eight steps are listed with counts

- **WHEN** the pass is at its fifth step with 25 of 30 requests answered
- **THEN** the list shows eight rows, the fifth reads "Read repeated words in context" with "25 of 30 requests", and the steps after it say they have not started

### Requirement: Let the names pass continue in the background or be cancelled

The names pass dialog's footer SHALL offer Continue in background, the line "Cancel writes nothing." and Cancel; Esc SHALL hide the dialog, and Cancel SHALL stop the pass and leave both tables as they were.

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5), docs/specification/mockups/README.md (G4, Continue in background). In plain words: a pass that takes minutes can be left running, and stopping it never leaves half a result.

#### Scenario: Esc hides the long pass

- **WHEN** the person presses Esc in the dialog of the names pass
- **THEN** the dialog is hidden into the activity chip and the pass continues

### Requirement: Show the names pass's results with Undo all

WHEN the pass finishes, its dialog SHALL become a Results view with the subtitle "5 added · 0 removed · 1 changed · 49 steps · 1 min 48 s", the tabs Changes (Entry, Change, Before, After, grouped) and Steps, and the actions Undo all, Show changed rows and Done.

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5). In plain words: the person sees every change the pass made and can give all of them back.

#### Scenario: Results list the changes

- **WHEN** the pass adds `Mid` and `pentacle` and drops `Hale`
- **THEN** the Results view shows "2 added · 1 removed" in its subtitle and lists the three changes in the Changes tab

### Requirement: Run the Scan, Review and Translate actions in the same dialog with two steps

The Scan, Review and Translate actions on a card SHALL run the progress dialog with two steps, the action's requests and then the write, and SHALL NOT be hideable.

**Source**: docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5). In plain words: every model action looks the same, and only the long names pass can be hidden.

#### Scenario: A card action runs two steps and cannot be hidden

- **WHEN** the person presses Scan on the glossary card
- **THEN** the dialog lists two steps — the scan's requests and Write the results — and offers no Continue in background

## MODIFIED Requirements

### Requirement: Show the glossary as an editable table on Names & style

The Names & style screen SHALL show the glossary as a table with the columns Term, Kind, Translation (an empty one shows the placeholder "No target"), Gender, Locked and a remove cross, under the purpose line "Names of people and places — written the same way every time. Set the gender so names take the right grammatical form."

**Source**: FR-GLOSS-02, FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5). In plain words: this is where the person settles spellings before translating; the table now explains its purpose, and the rest of the screen's behaviour (resizing, marks, empty state, layout, toolbar) is stated beside it.

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

- **WHEN** the content is 1500 px wide, and then 1200 px wide
- **THEN** the two cards sit side by side at 1500 px and are stacked at 1200 px
- **AND** a card too narrow for its toolbar folds Import and Export into a three-dot menu

#### Scenario: Start translation during a paused run shows it

- **WHEN** the translation of `Harbor Lights · EN → UK` is paused at 55 % and the person looks at the toolbar of Names & style
- **THEN** it offers Go to Translating, pressing it shows the paused translation offering Resume, and no new translation begins

#### Scenario: The screen with no book open

- **WHEN** no project is open
- **THEN** the Names & style step is locked in the navigation with the hint "Steps unlock when a project is open", so the screen cannot be reached and no glossary table, scan button or Start translation is shown

#### Scenario: The banner and the Start translation button

- **WHEN** a project is open
- **THEN** the screen shows the purpose line "Decide how names and repeated words are translated. Optional — skip it and BookLoom builds both while it translates." and the toolbar's Start translation button

#### Scenario: Start translation is pressed while no start is offered

- **WHEN** the project's translation is running, paused, stopped or in the provider-error state, or it completed with nothing pending
- **THEN** the toolbar offers Go to Translating instead of Start translation, and nothing starts

#### Scenario: The Type column and the remove column

- **WHEN** the table shows a row
- **THEN** its Kind is one of character, place, term, title or other, and the Translation is editable
- **AND** the last column has no header text and holds the row's remove cross

### Requirement: Keep a lexicon of recurring terms and show it on Names & style

The application SHALL keep, for each project, a lexicon of recurring common terms and titles that a model would otherwise render differently from one page to the next, SHALL propose them with a deterministic scan and no model, and SHALL show them in a "Recurring terms" card; a term the glossary holds SHALL NOT be asked about or shown from the lexicon.

**Source**: `additional-work.md` 15d.9; `docs/specification/01_Product/12_PROMPT_CATALOG.md#recurring-terms`; docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5), ADR-0043. In plain words: a book that calls a master "господар" in chapter one and "учитель" in chapter nine reads as two characters; the app finds the words a book keeps repeating and keeps every later request to one rendering.

#### Scenario: The scan proposes a title and a dual-use word

- **WHEN** a book's running text holds `Mr.` four times, and `Imp` capitalised mid-sentence three times and `imp` in lower
  case three times
- **THEN** the scan proposes `mr` and `imp` and nothing for a name written only with a capital or a word written only in
  lower case

#### Scenario: A held term is not proposed

- **WHEN** the glossary holds `Imp` and the lexicon holds `MR`
- **THEN** the scan proposes neither

#### Scenario: Editing a rendering

- **WHEN** the person types `пан` in the rendering field of `master` and presses Enter
- **THEN** the lexicon stores `пан` as the person's rendering of `master`, every later request is asked to keep it, and
  clearing the field returns to the most used verified rendering

#### Scenario: Promoting a term

- **WHEN** the person presses "To glossary" on `master`, whose established rendering is `господар`
- **THEN** the glossary holds `master` → `господар` (term, unlocked, the person's target) and the Recurring terms card no
  longer lists `master`

#### Scenario: A duplicate term is refused

- **WHEN** the person types `IMP` in the add field while `imp` is listed
- **THEN** the line above the glossary says `IMP is already in the glossary or the list` and no term is added

#### Scenario: What the deterministic scan proposes

- **WHEN** the scan reads the book's running text
- **THEN** it proposes a title of the source language's bundled list (English first) that the text uses at least three
  times, and a word the book writes both with a capital away from a sentence start and not beside another capitalised
  word (so it is no part of a name such as `Great Hall`), and in lower case (so it is neither a name nor a function word)
  at least five times, at most thirty
- **AND** never a term the glossary or the lexicon already holds

#### Scenario: When the scan runs

- **WHEN** a run is prepared and the lexicon is empty, or the person asks on Names & style ("Find recurring terms")
- **THEN** the scan runs
- **AND** a word that is only ever lower case, such as `pentacle`, is added by hand

#### Scenario: What the card lets the person do

- **WHEN** the person works in the card
- **THEN** they can edit the rendering (Enter or leaving the field stores it; clearing it returns to the most used),
  suggest renderings with the model (the glossary's suggestion call over terms of type term, one model action at a time
  with the name scan and review, needing a chosen model), add a term by hand (a small card asks for the word; one the
  glossary or the list already holds is refused in the screen's banner), and remove a term
- **AND** they can promote a term to the glossary, which stores it there as an unlocked entry of type term with its
  established rendering as the target and removes it from the lexicon

#### Scenario: What the card shows

- **WHEN** the Recurring terms card lists `master`, which the book's text uses 13 times
- **THEN** it shows the term, its Translation (the person's if typed, else the learned or most used verified rendering, else the model's suggestion) and Used `13 times`
- **AND** there is no separate column for the renderings the drafts used

#### Scenario: The lexicon lives in memory and an edit changes the stored entry

- **WHEN** the person types `пан` as the rendering of `master`, the application is closed and opened again, and the project is opened
- **THEN** the card still lists `master` with `пан`
- **AND** the edit changed the entry as it stands in the store, in one step, never from a copy read earlier: a count a running draft records meanwhile is kept, and a term removed or promoted meanwhile is not created again

### Requirement: Offer the one-pass preparation on Names & style

The Names & style screen SHALL offer "Prepare names automatically" in a card above both tables that says what it does and how long it takes for the brief's quality dial ("About 1 min. You can undo it all afterwards."), and a press SHALL run the names pass on the chosen model in a progress dialog.

**Source**: `additional-work.md` 15i.U6; docs/specification/mockups/README.md (Step 3 · Names & style), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (N1–N5). In plain words: one button replaces five presses and gives every change back one by one or all together; the dialog's steps, background option and results are stated beside it.

#### Scenario: The press fills both tables and Undo all gives both back

- **WHEN** the person presses "Prepare names automatically" and the pass adds `Mid`, drops `Hale` and adds `pentacle`
- **THEN** the Results view shows "2 added · 1 removed" in its subtitle and lists the three changes in the Changes tab
- **AND** Undo all removes `Mid` and `pentacle` and puts `Hale` back

#### Scenario: Cancel on the busy card changes nothing

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

#### Scenario: The pass is a blocking activity

- **WHEN** the pass runs
- **THEN** the progress dialog is shown on the work layer, the computer is kept awake, and a stopped or failed pass leaves both tables as they were

#### Scenario: Changed rows are marked on both cards

- **WHEN** the pass succeeds and the person presses Show changed rows
- **THEN** every changed row on both cards shows the left-edge mark, and both cards' changed filters are on
