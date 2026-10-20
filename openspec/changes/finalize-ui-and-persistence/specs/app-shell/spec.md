## ADDED Requirements

### Requirement: Lock the five workflow steps until a project is open

WHILE no project is open, the application SHALL show workflow steps 1 to 5 greyed and unclickable, with a tooltip and the hint "Steps unlock when a project is open", and SHALL keep Projects and Settings available.

**Source**: `docs/specification/mockups/README.md#g6--workflow-steps-need-an-open-project`. In plain words: every step works on a project's book, so before one is open there is nothing for them to show. Greying them, with the reason, is clearer than letting a person open an empty screen. The open project's name shows in the title bar.

#### Scenario: A fresh start locks the steps

- **WHEN** the application starts with no project open
- **THEN** the entries `1 Import`, `2 Book Brief`, `3 Names & style`, `4 Translating` and `5 Export` are unavailable and their tooltip reads "Steps unlock when a project is open"
- **AND** `Projects` and `Settings` can be opened

#### Scenario: Opening a project unlocks them

- **WHEN** the project `Frankenstein · EN → UK` is opened
- **THEN** all five steps can be opened and the title bar shows `Frankenstein · EN → UK`

### Requirement: Show every dialog inside the window, on one of two layers

The application SHALL show every dialog inside the window and open no second operating-system window except the native file choosers. Progress dialogs of model work SHALL sit on a work layer, and every question, confirmation, error, form, test and the review desk on a layer above it; a question opened during work SHALL appear above the progress dialog, which keeps its state.

**Source**: `docs/specification/mockups/README.md#g1--dialogs-live-in-the-window-on-two-layers`. In plain words: a separate window for the model's calls got lost behind the main one. Two layers let a question such as "Cancel the export?" appear over the work it is about without losing where that work had got to.

#### Scenario: A question opens above running work

- **WHEN** the export's progress dialog is showing and `Cancel` is used
- **THEN** the dialog `Cancel the export?` appears above it, and the progress dialog underneath keeps its steps and figures

#### Scenario: No second window

- **WHEN** the model calls of a translation are inspected
- **THEN** they are shown inside the progress dialog and no `Model calls` window exists

### Requirement: Give every dialog one of three sizes with a fixed header and footer

The application SHALL size each dialog as a confirmation (480 pixels wide), a form or result (720 pixels wide) or a large dialog (the smaller of 1180 pixels and 92 % of the window wide, and the smaller of 820 pixels and 90 % high), SHALL fix the header and the footer, and SHALL scroll only the body. A large dialog's height SHALL not change with its content.

**Source**: `docs/specification/mockups/README.md#g1--dialogs-live-in-the-window-on-two-layers`. In plain words: the footer's buttons must be where the hand expects them, whether the body holds three lines or three hundred. The safe action comes first in the footer and the main action last.

#### Scenario: A confirmation is narrow

- **WHEN** `Stop translating?` is shown in a window 1280 pixels wide
- **THEN** the dialog is 480 pixels wide

#### Scenario: A large dialog keeps its footer still

- **WHEN** the review desk is shown in a window 1920 pixels wide by 1080 high, first with 2 segments and then with 200
- **THEN** it is 1180 pixels wide and 820 pixels high both times, and the footer does not move

#### Scenario: A large dialog fits a small window

- **WHEN** a progress dialog is shown in a window 960 by 640 pixels
- **THEN** it is about 883 pixels wide and 576 pixels high, and its body scrolls

### Requirement: Answer Enter, Escape and Tab the same way in every dialog

WHILE a dialog is open, the application SHALL keep Tab inside it, SHALL run the footer's main button on Enter (Ctrl or Command with Enter inside a text area), and SHALL close it on Escape, except that Escape on a progress dialog hides long work, asks first before losing an export, and closes short work. Only About SHALL close on a click outside it.

**Source**: `docs/specification/mockups/README.md#g1--dialogs-live-in-the-window-on-two-layers`. In plain words: a person at the keyboard should be able to answer any dialog without finding the mouse, and one key should never lose work silently.

#### Scenario: Enter presses the main button

- **WHEN** `Stop translating?` is shown and Enter is pressed
- **THEN** the main button of its footer is activated

#### Scenario: Enter in a text area adds a line

- **WHEN** the cursor is in the note field of a dialog and Enter is pressed
- **THEN** a new line is added, and Ctrl+Enter (Command+Enter on macOS) activates the main button

#### Scenario: Escape hides long work

- **WHEN** the names pass's progress dialog is showing and Escape is pressed
- **THEN** the dialog is hidden, the names pass keeps running, and the activity chip in the title bar shows it

#### Scenario: Escape on an export asks first

- **WHEN** the export's progress dialog is showing and Escape is pressed
- **THEN** the dialog `Cancel the export?` appears above it and the export keeps running until the person answers

#### Scenario: Focus stays in the card

- **WHEN** Tab is pressed on the last control of a dialog
- **THEN** focus moves to the first control of the same dialog

#### Scenario: A click outside closes About only

- **WHEN** the scrim outside the open About dialog is clicked, and then outside an open `Stop translating?` dialog
- **THEN** About closes and `Stop translating?` stays open

### Requirement: Hold a dialog nobody asked for until the window is free

WHEN a dialog opens by itself, such as the review desk at the end of a translation, the application SHALL wait until no dialog is open and no model work is visible, and only then show it. A dialog the person asked for SHALL show at once.

**Source**: `docs/specification/mockups/README.md#g1--dialogs-live-in-the-window-on-two-layers`. In plain words: a desk that pops up over a question the person is answering would be taken for the question's own answer, and could swallow a keypress. Waiting costs nothing, because nothing is lost by seeing it a moment later.

#### Scenario: The review desk waits for a question

- **WHEN** a translation finishes with 6 segments to review while the dialog `Stop translating?` is open
- **THEN** the review desk is not shown until that dialog is answered

#### Scenario: A dialog the person asked for is not delayed

- **WHEN** `About` is used while model work is visible
- **THEN** About is shown at once, above the work

### Requirement: Show all model work in one progress dialog

WHEN the application asks the model for the brief, the names, a file name, the last check before saving or a translation's calls, it SHALL show one progress dialog with a header (title, purpose line and a determinate bar), a figures line such as "51 % · 26 of about 49 steps · Left about 1 min", a list of steps with a marker and one result line each, a detail pane, and a footer holding what Cancel does.

**Source**: `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`. In plain words: hours of model work used to hide behind a spinner or raw prompts in another window. One dialog shows what is being done, how far along it is and what came back, whatever the work is.

#### Scenario: The figures line

- **WHEN** the names pass has done 26 of about 49 steps
- **THEN** the bar sits at 51 % and the figures line reads `51 % · 26 of about 49 steps · Left about 1 min`

#### Scenario: A finished step stays selectable

- **WHEN** step 3 of the brief suggestion has finished and step 4 is running
- **THEN** step 3 shows its result line and can still be selected to see its input and output, and step 5 says it has not started

#### Scenario: The footer says what Cancel does

- **WHEN** the brief suggestion's dialog is shown
- **THEN** its footer reads `Cancel writes nothing.` with Cancel on the right

### Requirement: Offer Simple and Detailed views of a step

The progress dialog SHALL show a selected step in a Simple view, with one plain sentence and the input and output in plain words, or in a Detailed view that adds the attempt, a clock against the timeout, first token, tokens, speed, the call's kind and the parsed prompt. It SHALL open in Detailed when "Show technical details" is on.

**Source**: `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`, `docs/specification/mockups/README.md#g12--simple-first-technical-details-on-request`. In plain words: most people want to know what is happening, not which attempt of which call it is. The technical view is one click away for those who do.

#### Scenario: Simple is the default

- **WHEN** a progress dialog opens with "Show technical details" off
- **THEN** it shows Simple, with no attempt count, token figure or call kind

#### Scenario: Detailed adds the call

- **WHEN** Detailed is chosen on a draft call of 4 segments
- **THEN** the pane adds `Call 42 · Draft · 4 segments · attempt 1 of 2`, the clock, first token, tokens and speed

#### Scenario: Technical details open it in Detailed

- **WHEN** "Show technical details" is on and a progress dialog opens
- **THEN** it opens in Detailed

### Requirement: Follow the current step until a person chooses another

The progress dialog SHALL have a follow switch, on by default, that selects the step the work is on, and SHALL turn it off only when a person clicks or presses a key on the list, never because of a selection the application made.

**Source**: `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`. In plain words: a person reading an earlier step must not have it snatched away by the next step starting, and the application's own movement of the selection must not count as the person's choice.

#### Scenario: Following moves the selection

- **WHEN** follow is on and step 5 starts
- **THEN** step 5 is selected

#### Scenario: A click stops following

- **WHEN** the person clicks step 2 while step 5 runs, and step 6 then starts
- **THEN** step 2 stays selected and the switch is off

#### Scenario: Turning follow on again

- **WHEN** the switch is turned on after step 2 was chosen
- **THEN** the running step is selected again

### Requirement: Let long work continue in the background

WHERE model work is long — the names pass or the export's last check — the progress dialog SHALL offer "Continue in background", which hides it and shows the activity chip in the title bar (for example "Names · 53 % · open"). Short work — the brief, a file name, a test — SHALL NOT be hideable. WHEN the chip is clicked, the same dialog SHALL reopen with its state.

**Source**: `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`, owner decision 11 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: a names pass can take many minutes, and a person should be able to read the book meanwhile. Short work ends before anyone could want to hide it, so it is not offered.

#### Scenario: Hiding the names pass

- **WHEN** `Continue in background` is used at 53 %
- **THEN** the dialog closes, the title bar shows an activity chip `Names · 53 % · open`, and the names pass keeps running

#### Scenario: Reopening

- **WHEN** the chip is clicked
- **THEN** the same dialog opens with its steps, selection and figures as they are now

#### Scenario: A short job has no such button

- **WHEN** the dialog of `Fill in from the book` is shown
- **THEN** its footer offers no `Continue in background`

### Requirement: Make the screen that hidden work writes to read-only

WHILE long model work runs in the background, the screen it writes to SHALL be read-only behind a banner reading, for example, "Names is running in the background — this screen is read-only until it ends", and other model work SHALL be refused with a message that offers "Show progress". WHEN the work ends, the chip SHALL go and a message with "Review the results" SHALL appear; nothing SHALL open by itself.

**Source**: `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`. In plain words: the background pass is writing into the glossary, so editing it by hand at the same time would lose one of the two sets of changes. Saying so, and offering a way back, is better than a silent lock.

#### Scenario: The glossary is read-only

- **WHEN** the names pass is hidden at 53 %
- **THEN** Names & style is read-only and the banner reads `Names is running in the background — this screen is read-only until it ends`

#### Scenario: A second job is refused

- **WHEN** `Fill in from the book` is used while the names pass is hidden
- **THEN** nothing starts and a warning message with `Show progress` is raised

#### Scenario: It ends quietly

- **WHEN** the hidden names pass ends
- **THEN** the chip is gone, a message offers `Review the results`, and no dialog opens by itself

### Requirement: Count progress in steps over the whole operation

The application SHALL count the progress of an operation in steps over all its tasks and their items, and show it as one percentage in the bar and the title bar. A translation's own steps are defined by translation-pipeline. Work found on the way SHALL join the total when it starts, and the bar SHALL NOT move back.

**Source**: `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, PR1–PR3 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: counting only segments made "52 % in 2 hours, 10 hours left", because checks and Max's later work were in neither figure. Counting every step makes the bar honest, and it holds still rather than going backwards when a repair adds work.

#### Scenario: Three tasks of three items

- **WHEN** an operation has 3 tasks of 3 requests each
- **THEN** it has 9 steps, and 4 finished requests show 44 %

#### Scenario: Added work does not move the bar back

- **WHEN** the bar is at 51 % and a failed check adds a repair step
- **THEN** the bar stays at 51 % until the work has caught up and then moves on

### Requirement: Estimate the time left from measured time per kind of step

The application SHALL compute the time left as the sum, over each kind of step, of the steps left times the measured time per step of that kind over the whole operation, excluding pauses. A kind not yet run SHALL borrow the draft time (a check about 0.6 of a draft). It SHALL show "about …" after 5 steps or 3 %, and change at most 10 % a minute.

**Source**: `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`. In plain words: the old estimate multiplied the last 20 segments' average by every segment left, so one slow stretch was projected over the whole book. Per kind of step, over the whole run, is steadier and sets a figure the owner's check can believe.

#### Scenario: The owner's sanity check

- **WHEN** 52 % of the steps are done after 2 hours with steady speed
- **THEN** the time left reads about 2 h

#### Scenario: Nothing is shown too early

- **WHEN** only 3 steps of 276 are done
- **THEN** no time left is shown

#### Scenario: A steady figure

- **WHEN** the estimate would jump from 40 minutes to 70 minutes between two ticks
- **THEN** the shown figure moves by at most 10 % in a minute

#### Scenario: Pauses are not counted

- **WHEN** a translation is paused for 3 hours overnight and resumed
- **THEN** the time left is the same as before the pause

### Requirement: Show the share of work in the bar and the count of segments in the text

The application SHALL show the share of work as one percentage ("51 % done") in the bar and the title bar, and SHALL give counts in text as segments ("74 of 142 segments done"), never printing the two percentages side by side. The bar's tooltip SHALL say "140 of 276 steps, checks included", and "How progress is counted" SHALL list planned, done and measured time per kind of step.

**Source**: `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`. In plain words: two different percentages next to each other look like a mistake. The bar counts steps, the sentences count segments, and a tooltip and a breakdown explain how one relates to the other.

#### Scenario: One percentage, one count

- **WHEN** 74 of 142 segments are done and 140 of 276 steps
- **THEN** the title bar reads `51 % done` and the text reads `74 of 142 segments done`, and no second percentage is shown

#### Scenario: The tooltip explains the bar

- **WHEN** the pointer rests on the bar
- **THEN** the tooltip reads `140 of 276 steps, checks included`

### Requirement: Estimate before Start only from a measurement

WHILE a translation has not started, the application SHALL show a time estimate only when a measured speed exists, from the inference test or an earlier translation, and SHALL mark it "estimate". Without one, it SHALL show the relative cost of the quality choices (Balanced about 1.5 times Fast, Max about 3 times).

**Source**: `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`. In plain words: a guess of hours with no measurement behind it would be believed. Saying "about 1.5 times as long as Fast" is true without knowing the speed of this computer.

#### Scenario: A measured speed gives an estimate

- **WHEN** the inference test measured 9.4 tokens per second and Balanced is chosen
- **THEN** the estimate is shown with the word `estimate`

#### Scenario: No speed gives relative cost

- **WHEN** no test or earlier translation exists
- **THEN** no hours are shown and the choices read `Balanced ≈ 1.5× Fast` and `Max ≈ 3× Fast`

### Requirement: Fit the layout to the width of the content area

The application SHALL classify the content area as compact (below 1040 pixels), regular (1040 to 1439) or wide (1440 and above), with 16 pixels of hysteresis, and SHALL let each region decide its own switch to two columns from its own width. It SHALL cap form and list screens (Projects, Import, Brief, Export, Settings) at 1320 pixels and prose at 680, and size the navigation column 236 pixels, or 200 below a 1200-pixel window.

**Source**: `docs/specification/mockups/README.md#layout-and-sizes-l` (L1, L4, L9). In plain words: the same screens must work at 960 and at 1920 pixels. The 16 pixels of hysteresis stop a layout from flipping back and forth when a scrollbar appears at the edge.

#### Scenario: The class follows the content width

- **WHEN** the content area is 1039 pixels wide, then 1040, then 1440
- **THEN** it is compact, then regular, then wide

#### Scenario: No flicker at the boundary

- **WHEN** the content area is regular at 1040 and a 12-pixel scrollbar narrows it to 1028
- **THEN** it stays regular

#### Scenario: A form screen is capped

- **WHEN** Settings is shown in a window 1920 pixels wide
- **THEN** its content column is 1320 pixels wide and centred, and Translating uses the full width

#### Scenario: A narrower navigation column

- **WHEN** the window is 1100 pixels wide
- **THEN** the navigation column is 200 pixels wide, and 236 at 1280

### Requirement: Keep list and detail side by side and stack the rest in compact

The application SHALL keep these list-and-detail splits side by side at every width: the live panel (list 240 pixels up to 34 %), the review desk and progress dialogs (list 220 pixels up to 30 %), and Settings › Providers (list 240 pixels, 200 in compact). WHILE the content is compact, Names & style's two cards, the Import browser's Text pane and four count tiles SHALL stack (tiles 2 by 2).

**Source**: `docs/specification/mockups/README.md#layout-and-sizes-l` (L6, L7). In plain words: a list without its detail is useless, so those never stack. Cards that read fine one under the other do, instead of being squeezed until they are unreadable.

#### Scenario: Names & style stacks when compact

- **WHEN** Names & style is shown with a content area 944 pixels wide
- **THEN** the Glossary card is above the Recurring terms card

#### Scenario: The review desk never stacks

- **WHEN** the review desk is shown in a window 960 pixels wide
- **THEN** its list and its detail are side by side

#### Scenario: Four tiles go two by two

- **WHEN** the four count tiles are shown in a compact content area
- **THEN** they form two rows of two

### Requirement: Let four screens fill the window's height

The application SHALL let Translating (live card minimum 460 pixels high), Import (text browser minimum 360), Names & style (the two tables) and Settings grow to the window's height, so that Settings' footer stays in view at 960 by 640. Below those minimums the screen SHALL scroll.

**Source**: `docs/specification/mockups/README.md#layout-and-sizes-l` (L5). In plain words: a table that scrolls inside a page that scrolls is hard to use. These screens use the height they are given and only scroll when it is too small to be useful.

#### Scenario: The text browser grows

- **WHEN** Import is shown in a window 1080 pixels high
- **THEN** the text browser fills the height below the verdict

#### Scenario: Below the minimum the page scrolls

- **WHEN** Import is shown in a content area 300 pixels high
- **THEN** the text browser is 360 pixels high and the page scrolls

### Requirement: Cut one-line text with a tooltip only where it is cut

WHEN one line of text does not fit, the application SHALL cut it with an ellipsis and SHALL show its whole text as a tooltip only then. Long text in detail panes and dialogs SHALL wrap, and nothing important SHALL be reachable only by hovering.

**Source**: `docs/specification/mockups/README.md#layout-and-sizes-l` (L8). In plain words: a tooltip on every label is noise, and a cut label with no tooltip hides what it said. Only a cut line needs its tooltip.

#### Scenario: A cut name has a tooltip

- **WHEN** the project name `Frankenstein · EN → UK · 26b Max quality` does not fit its row
- **THEN** it ends in an ellipsis and its tooltip shows the whole name

#### Scenario: A short name has none

- **WHEN** the project name `Earth Gravity · EN → UK` fits
- **THEN** it shows no tooltip of its own

### Requirement: Show scrollbars whenever content overflows

The application SHALL show scrollbars 12 pixels wide whenever content overflows, never as bars that hide themselves, with the thumb in the muted-2 role on the surface-2 track, and in the navigation column the nav-fg-muted role on the nav-bg-2 track.

**Source**: `docs/specification/mockups/README.md#g3--visible-scrollbars`. In plain words: a hidden scrollbar leaves a person unsure that more exists below the edge. Always showing it, and wide enough to grab, answers that.

#### Scenario: A long list shows its bar

- **WHEN** the glossary has more rows than fit
- **THEN** a vertical scrollbar 12 pixels wide is shown without any pointer movement

#### Scenario: A short list shows none

- **WHEN** the list fits
- **THEN** no scrollbar is shown

### Requirement: Hide technical words unless Show technical details is on

The application SHALL use plain words on the main screens and SHALL show chunks, calls, tokens, attempts, rule codes and wire names only in Detailed views, Advanced folds and when Settings › Appearance › "Show technical details" is on. The switch SHALL be off by default and remembered, SHALL open every Simple and Detailed view in Detailed and open the Settings folds, and SHALL add rule codes and debug lines. There SHALL be no third mode.

**Source**: `docs/specification/mockups/README.md#g12--simple-first-technical-details-on-request`. In plain words: a first-time reader should meet "segments 5–8", not "call 42, chunk 3 of 9". People who need the detail turn one switch on and find it everywhere it was held back.

#### Scenario: Off by default

- **WHEN** the application is first started
- **THEN** "Show technical details" is off and the live card says `Now translating: Chapter 2, segments 5–8`

#### Scenario: On shows the codes

- **WHEN** the switch is turned on
- **THEN** the same card adds `Call 42 · Draft · 4 segments · attempt 1 of 2 · chunk 3 of 9`, and a review row shows its rule code such as `omission · length ratio 0.29, expected 0.35–3.6`

#### Scenario: The switch is remembered

- **WHEN** the switch is on and the application is restarted
- **THEN** it is still on

### Requirement: Use the words of the vocabulary on the main screens

The application SHALL say "AI model" and not "provider", "to review" and not "flagged", "translated", "fixed automatically" and "left" for accepted, repaired and remaining, "translation" for a run, "Try again now" for every retry, and "Skip these 4 segments" with the real count. It SHALL explain "segment" once, in the tooltip of every segment count: "A segment is a paragraph, heading or caption — one piece of the book's text."

**Source**: `docs/specification/mockups/README.md#words-w--user-facing-vocabulary`. In plain words: the words of the engine are not the words of a person translating a book. The owner kept "segment" as the unit and asked that it be explained where it is counted. Ukrainian follows the same rules.

#### Scenario: Counts use the plain words

- **WHEN** the review button is shown for 6 segments
- **THEN** it reads `Review (6)` and no main screen shows the word `flagged`

#### Scenario: The segment tooltip

- **WHEN** the pointer rests on `74 of 142 segments done`
- **THEN** the tooltip reads "A segment is a paragraph, heading or caption — one piece of the book's text."

#### Scenario: One retry verb

- **WHEN** the translation is paused after an error
- **THEN** the button reads `Try again now`, never `Retry` or `Send again`

### Requirement: Show the Projects screen as a list grouped by book

The application SHALL show the Projects screen with the purpose line "A project is one translation of a book. One book can have several — into other languages, or the same language with another AI model. Progress is saved as you go.", a search over books and projects, an All, In progress and Finished filter, and the projects grouped under a header for each book with its cover, title, file, format, segment count and the number of translations.

**Source**: `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`, G6 and decision 8 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: a person may have translated a book into three languages, and wants to find which, and where it stopped. Grouping by book puts a book's translations together.

#### Scenario: Two translations of one book

- **WHEN** `Frankenstein` has the projects `Frankenstein · EN → UK` and `Frankenstein · EN → DE`
- **THEN** one header shows `Frankenstein`, `frankenstein.epub · EPUB · 1,240 segments` and `2 translations`, with both rows below it

#### Scenario: A search with no hits

- **WHEN** `xyz` is typed in the search
- **THEN** the list reads `No project matches 'xyz'.` with a `Clear search` link

#### Scenario: The filter

- **WHEN** `Finished` is chosen
- **THEN** only projects whose translation has finished are listed

### Requirement: Show what a project row says and offers

Each project row SHALL show the project's name (a "named" chip when the person named it, "open" for the open one), its languages, AI model and quality, a progress bar with its percentage and when it was last opened, a status chip coloured by meaning, and Continue (or Open), and a ⋯ menu with "Rename…", "Show book file" and "Remove project…".

**Source**: `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`. In plain words: a row answers "which translation is this, how far has it got, and what can I do with it" without opening it.

#### Scenario: A translation in progress

- **WHEN** the project `Frankenstein · EN → UK` is at 51 %, uses `Ollama · gemma4:e4b` on Balanced and was opened yesterday
- **THEN** its row shows `EN → UK · Ollama · gemma4:e4b · Balanced`, a bar at 51 %, `yesterday`, a `Translating` chip and `Continue`

#### Scenario: The open project

- **WHEN** a project is open
- **THEN** its row carries an `open` chip and offers `Open` rather than `Continue`

#### Scenario: The menu

- **WHEN** ⋯ is used on a row
- **THEN** `Rename…`, `Show book file` and `Remove project…` are offered

### Requirement: Open a project at its step and let a going translation wait paused

WHEN Continue or Open is used on a project, the application SHALL open it at its step. A translation that was going SHALL wait paused ("Ready to continue") rather than start by itself. IF the AI model is missing or changed, THEN the questions of the continue dialogs SHALL be asked first.

**Source**: `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`. In plain words: reopening a project must not begin using the model without a person choosing to. They see where it was and press Continue.

#### Scenario: Continue from the list

- **WHEN** `Continue` is used on `Frankenstein · EN → UK`, which stopped at Chapter 2, segment 5
- **THEN** the Translating screen opens in the state Ready to continue and no model request is sent

#### Scenario: The model is gone

- **WHEN** the stored AI model `gemma4:e4b` is no longer available and Continue is used
- **THEN** a dialog asks whether to try again, use another AI model or not now

### Requirement: Show a first-launch state on Projects when none exist

WHILE no project exists, the application SHALL show on Projects "Translate a book on your own computer", How it works in four steps (choose a book · check the languages · let it translate · save the translated book, with "your original file is never changed"), the AI-model line, and "+ New translation". It SHALL hide search and the filter, and show "+ New translation" with a list too.

**Source**: `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`. In plain words: the first screen a new person sees must say what the program does, what to press, and whether the AI model it needs was found.

#### Scenario: An empty list

- **WHEN** the application starts for the first time
- **THEN** Projects shows the four steps, the AI-model line and `+ New translation`, and no search or filter

#### Scenario: The model was found

- **WHEN** Ollama with `gemma4:e4b` was found when BookLoom first opened
- **THEN** the AI-model line reads `AI model: Ollama · gemma4:e4b — ready ✓ (found on this computer when BookLoom first opened)`

#### Scenario: Nothing was found

- **WHEN** no program answered at the first launch
- **THEN** the line reads `No AI model found`, says `Ollama is not open. Open the Ollama app, then press Try again.` and offers `Try again` and `I use another program…`

### Requirement: Start a new translation from the toolbar

WHEN "+ New translation" is used, the application SHALL create a project and open Import. It SHALL show the button in the toolbar of the list and in the empty state.

**Source**: `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`, `docs/specification/mockups/README.md#layout-and-sizes-l` (L2). In plain words: one obvious way in, always in the same place.

#### Scenario: From the empty state

- **WHEN** `+ New translation` is used with no project
- **THEN** the Import screen opens and the steps unlock

### Requirement: Name every project automatically from its book and languages

The application SHALL name a project `<book title> · <SRC> → <TGT>` (for example `Frankenstein · EN → UK`), taking the title from the book's metadata before a colon or else the file name. Before a target is chosen it SHALL end in `→ …`, and the source code SHALL be left out until a language is known.

**Source**: `docs/specification/mockups/README.md#project-names--one-book-several-translations-pn` (PN1). In plain words: a person never has to invent a name, and the name says what the project is.

#### Scenario: A full name

- **WHEN** the book's title is `Frankenstein: or, The Modern Prometheus` and the languages are English and Ukrainian
- **THEN** the name is `Frankenstein · EN → UK`

#### Scenario: Before a target

- **WHEN** the source is English and no target is chosen
- **THEN** the name is `Frankenstein · EN → …`

#### Scenario: Under Find out from the text

- **WHEN** the source language is "Find out from the text" and none is found yet
- **THEN** the name is `Frankenstein · → …`, with the source code left out

### Requirement: Let an automatic name follow the languages and a typed name stay

WHILE a project's name is automatic, the application SHALL change it when the languages change and say so in a message. A name the person typed SHALL never change by itself. A named project SHALL return to its automatic name only when no other project of the book has the same language pair.

**Source**: `docs/specification/mockups/README.md#project-names--one-book-several-translations-pn` (PN2, PN4). In plain words: a name that says `EN → UK` must not go on saying it after the target became German, but a name a person chose is theirs.

#### Scenario: The name follows

- **WHEN** the target of `Frankenstein · EN → UK` (automatic) is changed to German
- **THEN** the name becomes `Frankenstein · EN → DE` and a message says so

#### Scenario: A typed name stays

- **WHEN** the project is named `Frankenstein with Max` and the target is changed to German
- **THEN** the name is still `Frankenstein with Max`

#### Scenario: Returning to automatic

- **WHEN** `Use the automatic name again` is used on `Frankenstein with Max` and no other project of the book has `EN → DE`
- **THEN** the name is `Frankenstein · EN → DE`

### Requirement: Allow one project per language pair of a book unless it is named

The application SHALL allow one unnamed project for each language pair of a book (same content). IF a person chooses a used pair, in the Brief or in "Translate into another language", THEN a dialog "Same language pair" SHALL show the existing project with its AI model, quality and progress and offer "Open that project" or "Make a second <language> translation", which needs a name of its own.

**Source**: `docs/specification/mockups/README.md#project-names--one-book-several-translations-pn` (PN3). In plain words: two identically named translations would be confused and overwrite each other's files. Asking once, with the choice to open the first, prevents it.

#### Scenario: A used pair

- **WHEN** `Frankenstein · EN → UK` exists and Ukrainian is chosen again for the same file
- **THEN** the dialog shows that project (`Ollama · gemma4:e4b`, `Balanced`, `51 %`) with `Open that project` and `Make a second Ukrainian translation`

#### Scenario: The button waits for a name

- **WHEN** `Make a second Ukrainian translation` is chosen
- **THEN** the field is empty with chips `· 26b`, `· Max quality`, `· thinking low`, `· formal` and `· v2`, and the main button is disabled

#### Scenario: The error shows after the first edit

- **WHEN** the field is untouched, then `Frankenstein · EN → UK` is typed
- **THEN** no error shows before the first edit, and then the error `That name is already used` shows and the button stays disabled

### Requirement: Keep project names unique and keep side files apart

The application SHALL treat project names as unique after trimming and ignoring letter case, including Cyrillic. It SHALL show the project's name in Projects, the title bar and the remove and continue dialogs. The file names that keep two projects' exports apart are specified by export.

**Source**: `docs/specification/mockups/README.md#project-names--one-book-several-translations-pn` (PN4, PN5). In plain words: every list and dialog must tell two projects apart, and an export of one must never replace an export of the other.

#### Scenario: Case and spaces do not make a new name

- **WHEN** `  frankenstein with max ` is entered while `Frankenstein with Max` exists
- **THEN** it is refused as already used

#### Scenario: Cyrillic is folded too

- **WHEN** `Франкенштейн` is entered while `франкенштейн` exists
- **THEN** it is refused as already used

### Requirement: Rename and remove a project

WHEN "Rename…" is used, the application SHALL ask for a name with chips, an error for a used name and "Use the automatic name again". WHEN "Remove project…" is confirmed, it SHALL delete this project's saved work only, never the book, exported files or the book's other projects.

**Source**: `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`. In plain words: removing one translation must not take away anything else a person has: not the original file, not what they exported, not their other translations of the same book.

#### Scenario: Renaming

- **WHEN** `Frankenstein · EN → UK` is renamed `Frankenstein for Mum`
- **THEN** the list, the title bar and the next export's label use `Frankenstein for Mum`

#### Scenario: Removing leaves the rest

- **WHEN** `Frankenstein · EN → UK` is removed while `Frankenstein · EN → DE` and `frankenstein.epub` exist and `frankenstein.uk.epub` was exported
- **THEN** the UK project is gone, and the DE project, the original file and the exported file are untouched

### Requirement: Translate a book into another language without importing it again

WHEN "Translate into another language" is used, the application SHALL ask for the language, the project name (required for a used pair) and what to start from, create a project that reuses the already imported book, copy the brief and the Glossary and Recurring terms (entries, kinds, genders and locked marks) with every translation emptied, optionally take the latest project's AI model, and never copy the translation memory. It SHALL import nothing again and open the brief.

**Source**: `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`, owner answer Q3 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: the characters and places are the same in any language, so their list is kept; but a rendering chosen for Ukrainian means nothing in German, and old translations would pull the new one wrong, so those are cleared.

#### Scenario: A German copy of a Ukrainian project

- **WHEN** `Frankenstein · EN → UK` has the glossary entry `Victor` (character, male, locked) rendered `Віктор`, and a German translation is made
- **THEN** the new project has `Victor` as a locked male character with an empty rendering, and the brief's other choices are copied

#### Scenario: Recurring terms are copied with empty translations

- **WHEN** the Ukrainian project's Recurring terms hold `dark lord` (title, locked) rendered `темний володар`, and a German translation is made
- **THEN** the German project has `dark lord` as a locked title with an empty rendering

#### Scenario: The AI model can come along

- **WHEN** `Start from` ticks the latest project's AI model, `gemma4:e4b`
- **THEN** the new project uses `gemma4:e4b`; unticked, it uses the current AI model

#### Scenario: The memory is not copied

- **WHEN** the Ukrainian project holds 300 remembered passages
- **THEN** the German project holds none

#### Scenario: A used language is marked

- **WHEN** the language list is shown and Ukrainian is already used
- **THEN** it reads `Ukrainian — already in 1 project`

### Requirement: Look for a local AI program once at first launch

WHEN the application starts and has never looked before, it SHALL look once, by itself and silently, for Ollama at `127.0.0.1:11434` and LM Studio at `127.0.0.1:1234`, and for nothing else. IF one answers with models, THEN it SHALL create the provider and select the only model, or ask which when there are several. IF none answers, THEN it SHALL show "No AI model found". It SHALL never look by itself again.

**Source**: `docs/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`, ADR-0044, owner decision 13 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: a first-time person should not have to know about providers and endpoints to get going. The one look is local, once, and needs no answer; every later check is something the person asks for.

#### Scenario: Ollama with one model

- **WHEN** Ollama answers at `127.0.0.1:11434` with the single model `gemma4:e4b`
- **THEN** the provider is created, `gemma4:e4b` is selected, and no question is asked

#### Scenario: Both programs answer

- **WHEN** Ollama and LM Studio both answer with models
- **THEN** both providers are saved and Ollama becomes the current one

#### Scenario: Several models

- **WHEN** Ollama lists `gemma4:e4b` and `gemma4:26b`
- **THEN** the person is asked which to use

#### Scenario: Nothing answers

- **WHEN** neither address answers
- **THEN** Projects shows `No AI model found` and no error dialog is opened

#### Scenario: Only once, and only locally

- **WHEN** the application is started a second time
- **THEN** no address is tried, and no address other than the two is ever tried

### Requirement: Disable model actions while a translation runs

WHILE a translation is translating, pausing or stopping, the application SHALL disable every other model action — Prepare names, Scan, Review, Translate, Fill in from the book — with the tooltip "Pause the translation first — BookLoom sends one model request at a time". WHILE the names pass runs, even hidden, Start translation SHALL wait with "Wait until the names pass ends".

**Source**: `docs/specification/mockups/README.md#changes-while-translating-i`. In plain words: a local model answers one request at a time, so two jobs would fight for it. Saying why a button is off is kinder than letting it fail.

#### Scenario: A button explains itself

- **WHEN** a translation is running and the pointer rests on `Prepare names automatically`
- **THEN** the button is disabled and its tooltip reads `Pause the translation first — BookLoom sends one model request at a time`

#### Scenario: Pausing frees them

- **WHEN** the translation is paused
- **THEN** the buttons are enabled

#### Scenario: Start waits for the names pass

- **WHEN** the names pass is hidden at 53 % and Start translation is pressed
- **THEN** it is disabled with `Wait until the names pass ends`

### Requirement: Pin the run's banners above the content on every screen

WHILE a banner applies — the AI model is unreachable, a call is stuck, BookLoom gave up, the model is missing, a translation cannot start, welcome back, finished, or hidden work — the application SHALL show it in a slot between the toolbar and the scrolling content, on every screen, with "Go to Translating" where useful.

**Source**: `docs/specification/mockups/README.md#layout-and-sizes-l` (L3). In plain words: a model program that went away matters whichever screen the person is on, and a banner inside a scrolled page can be missed.

#### Scenario: A banner on another screen

- **WHEN** Ollama becomes unreachable while Settings is shown
- **THEN** the banner appears between the toolbar and the content of Settings and offers `Go to Translating`

#### Scenario: It does not scroll away

- **WHEN** the content below the banner is scrolled to its end
- **THEN** the banner stays in place

### Requirement: Show each of the sixteen run states the same way everywhere

The application SHALL put a project in exactly one of sixteen run states — Ready, Ready to continue, Cannot start yet, Translating, Pausing, Paused, Waiting for the AI model, Paused after an error, Stopped trying, Model missing, Stuck call, Stopping, Stopped, Finished to review, Finished nothing to review, Failed — and the title bar's text, the AI-model chip's colour, the toolbar, the banner and the live line SHALL follow that state.

**Source**: `docs/specification/mockups/README.md#run-states-a`. In plain words: one table, not a separate idea of "state" on each screen. If the title bar, the toolbar and the banner all read the same row, they cannot disagree.

#### Scenario: Ready

- **WHEN** a project has no progress
- **THEN** the title bar reads `Ready`, the chip is grey `AI model ready`, the toolbar offers `Start translation`, the live line reads `Nothing translated yet.`, and review and export are off

#### Scenario: Ready to continue

- **WHEN** a project reopened with 74 of 142 segments done
- **THEN** the title bar reads `Ready · 51 % done`, the toolbar offers `Continue translation` and `Review (6)`, the banner reads `Welcome back — 74 of 142 segments are done.` and the live line reads `Continues at Chapter 2, segment 5.`

#### Scenario: Cannot start yet

- **WHEN** no AI model is chosen
- **THEN** the chip is red `No AI model`, `Start translation` is disabled with a tooltip naming what is missing, and the banner reads `Cannot start yet — no AI model is chosen.` with `Open settings`

#### Scenario: Translating

- **WHEN** a translation runs at 51 % with about 1 h 10 min left
- **THEN** the title bar reads `51 % done · about 1 h 10 min left`, the chip is blue `Model answering`, the toolbar offers `Pause`, `Stop` and `Review (6)`, the live line reads `Now translating: Chapter 2, segments 5–8`, review is read-only ("Pause to edit"), export is disabled and the computer is kept awake

#### Scenario: Pausing

- **WHEN** Pause was used and segments 5–8 are still in flight
- **THEN** the title bar reads `Pausing…`, `Pause` is disabled, and the live line reads `Pausing… segments 5–8 are translated again when you resume.`

#### Scenario: Paused

- **WHEN** a translation is paused at 51 %
- **THEN** the title bar reads `Paused at 51 %`, the chip is grey `Model idle`, the toolbar offers `Resume`, `Stop` and `Review (6)`, the live line reads `Paused. Segments 5–8 are translated again when you resume.`, review is editable and a partial export is allowed

#### Scenario: Waiting for the AI model

- **WHEN** Ollama cannot be reached and BookLoom retries by itself
- **THEN** the title bar reads `Waiting for Ollama`, the chip is red `Cannot reach Ollama`, the toolbar offers Pause (to stop trying by itself) and `Stop`, the banner offers `Try again now`, `Skip these 4 segments` and `Open settings`, and the computer stays awake

#### Scenario: Paused after an error

- **WHEN** the person paused the wait
- **THEN** the title bar reads `Paused at 51 %`, the chip is red, the toolbar offers `Resume` and `Stop`, and the banner reads `Paused. Ollama could not be reached. Open Ollama, then press Resume.`

#### Scenario: Stopped trying

- **WHEN** Ollama has not answered for 12 hours
- **THEN** the chip is red `Ollama: no answer for 12 h`, the toolbar offers `Try again now` and `Stop`, and the banner reads `Ollama has not answered for 12 hours — BookLoom stopped trying by itself.`

#### Scenario: Model missing

- **WHEN** the model `gemma4:e4b` was unloaded
- **THEN** the chip is red `Model missing`, the toolbar offers `Try again now` and `Stop`, and the banner reads `The AI model gemma4:e4b is not available — check that it is installed.` with `Choose another model…`

#### Scenario: Stuck call

- **WHEN** a call has run 3 min 40 s of its 6 min limit
- **THEN** the chip is amber `No answer for 3:40`, the toolbar is as for Translating, and the banner reads `The model is taking longer than usual (3:40). BookLoom tries again by itself at 6:00. You do not need to do anything.`

#### Scenario: Stopping

- **WHEN** Stop was used and calls are still in flight
- **THEN** the title bar reads `Stopping…`, every toolbar action is disabled and the live line reads `Stopping… segments 5–8 are translated next time.`

#### Scenario: Stopped

- **WHEN** a translation is stopped at 51 %
- **THEN** the title bar reads `Stopped at 51 %`, the chip is grey `Model idle`, the toolbar offers `Continue translation` and `Review (6)`, and the live line reads `Stopped. Continues at Chapter 2, segment 5.`

#### Scenario: Finished with segments to review

- **WHEN** a translation finishes with 6 segments to review
- **THEN** the title bar reads `Finished`, the toolbar offers `Review (6)` and `Continue to Export`, the Translating screen shows `Translation finished. 6 segments need your review. You can export now and review later.` with `Review 6 segments`, export is on and the review desk opens by itself

#### Scenario: Finished with nothing to review

- **WHEN** a translation finishes with 0 segments to review
- **THEN** the banner reads `Translation finished. Nothing needs your review.`, the toolbar offers `Continue to Export` and the review desk does not open

#### Scenario: Failed

- **WHEN** a translation ends on an unexpected error
- **THEN** the title bar reads `Did not finish`, the chip is red `Error`, the toolbar offers `Continue translation` and `Review (n)`, and the banner reads `The translation stopped because of an error. Finished segments are saved.` with `Show details`

### Requirement: Show technical details of a failure only on request

WHEN a failure is shown with a "Show details" or "Details for support" action, the application SHALL show a plain message first and the technical details in a section collapsed until the person opens it, with Copy details.

**Source**: `docs/specification/mockups/README.md#g12--simple-first-technical-details-on-request`, `docs/specification/mockups/README.md#other-dialogs`. In plain words: the person needs to know what went wrong in a sentence, and the support person needs the code. Both get what they need without the first being buried under the second.

#### Scenario: An unreadable file

- **WHEN** `Broken.epub` cannot be read
- **THEN** the dialog reads `BookLoom could not read this file.` with `Details for support` collapsed, and `Copy details` after it is opened

### Requirement: Ask before leaving a screen while long model work runs

WHEN a person leaves a screen while long model work started from it is running, the application SHALL ask "Model work is running" with the choices Stay and Continue in background, and SHALL change nothing until the person answers.

**Source**: `docs/specification/mockups/README.md#other-dialogs`, `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`. In plain words: moving to another screen would otherwise leave a names pass writing into a screen nobody can see. The person decides whether to wait there or let it run on in the background, where its screen turns read-only.

#### Scenario: Leaving during the names pass

- **WHEN** the names pass is at 53 % and the Export entry is activated
- **THEN** the dialog `Model work is running` offers `Stay` and `Continue in background`, and the Names & style screen is still shown

#### Scenario: Continuing in the background

- **WHEN** `Continue in background` is chosen
- **THEN** Export opens, the chip `Names · 53 % · open` shows in the title bar, and the names pass keeps running

### Requirement: Find a project's book when its file is missing or changed

IF the book file of a project is missing or its content has changed when the project is opened, THEN the application SHALL offer "Find the book…", and SHALL accept a chosen file only when its content hash equals the stored book's. Otherwise it SHALL say "This is a different book" and change nothing.

**Source**: `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (open questions, recorded default), `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`. In plain words: a book moved to another folder should not orphan its translation, but a different book must never be attached to someone else's saved work.

#### Scenario: The file was moved

- **WHEN** `Frankenstein · EN → UK` is opened, `frankenstein.epub` is no longer where it was, and an identical copy is chosen with `Find the book…`
- **THEN** the project opens and remembers the new location

#### Scenario: A different book is refused

- **WHEN** `Kobzar.fb2` is chosen instead
- **THEN** the dialog says `This is a different book` and the project stays closed

### Requirement: Let title-bar items give way by priority

WHILE the window is narrower than the title bar needs, the application SHALL hide the quality and review mode, the elapsed time and the speed at 1280 pixels or less, and the project name at 1060 pixels or less, keeping the state text and the AI-model chip.

**Source**: `docs/specification/mockups/README.md#window-chrome`. In plain words: a narrow window loses the least important items first, so the name of the state and whether the AI model is answering are never the ones to go.

#### Scenario: Items give way in a narrow window

- **WHEN** the window is 1200 pixels wide
- **THEN** the quality, elapsed time and speed are hidden and the name is shown
- **WHEN** the window is 1000 pixels wide
- **THEN** the name is hidden as well and the state text and the chip remain

## MODIFIED Requirements

### Requirement: Present one window divided into a fixed set of regions

The application SHALL present a single window containing, in fixed positions: a title bar with the product name, a theme control and an about action; a navigation column; a toolbar with a breadcrumb and the screen's actions; a banner slot; the current screen; a work layer for progress dialogs; a layer above it for other dialogs; and transient messages. The toolbar SHALL belong to the shell, not to a screen.

**Source**: FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/mockups/README.md#window-chrome`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`. In plain words: the furniture never moves. A person learns once where the navigation, the actions, the banners and the messages are, and every screen after that is just the middle changing. The banner slot and the two dialog layers are named so that a banner cannot end up inside a scrolled page and a question cannot hide behind work.

#### Scenario: The window opens with its regions in place

- **WHEN** the application is launched
- **THEN** the window shows a title bar, a navigation column, a toolbar with its breadcrumb, a banner slot and a screen area
- **AND** the title bar carries the text `BookLoom`, a theme control and an about action

#### Scenario: A screen with no shell-level action leaves the area empty

- **WHEN** a screen that offers no toolbar action is shown
- **THEN** the toolbar's action area is present and holds no control
- **AND** the region itself does not collapse or move the screen area

#### Scenario: An empty banner slot takes no room

- **WHEN** no banner applies
- **THEN** the banner slot has no height and the screen area starts directly beneath the toolbar

### Requirement: Keep every part of the window reachable at its smallest size

The application SHALL NOT allow the outer window to be smaller than 960 by 640 pixels. At that size every screen SHALL fit the width of the content area (944 by 600) in English and Ukrainian and every part SHALL be reachable by vertical scrolling; the navigation column SHALL scroll vertically only. A count tile SHALL keep at least 120 pixels and show its number and caption whole, and a progress bar SHALL stay 9 pixels high.

**Source**: FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/mockups/README.md#layout-and-sizes-l` (L10), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`. In plain words: 960 by 640 is the whole window, so the space inside is smaller by the title bar and borders. Nothing may be cut off sideways, whatever does not fit in height must be scrollable, and Ukrainian labels, about 26 % longer, must fit as well as English.

#### Scenario: The window cannot be shrunk below the minimum

- **WHEN** the application's window is shown
- **THEN** its minimum outer width is 960 pixels and its minimum outer height is 640 pixels

#### Scenario: Every screen fits the width at the outer minimum

- **WHEN** the Translating screen is shown in a content area 944 pixels wide by 600 high, in English and then in Ukrainian
- **THEN** the content area shows no horizontal scroll bar
- **AND** scrolling to the bottom brings the last control fully into view

#### Scenario: A short window scrolls the screen instead of clipping it

- **WHEN** the Translating screen is shown in a window whose content area is 300 pixels high
- **THEN** the content area scrolls vertically
- **AND** scrolling to the bottom brings the last control fully into view

#### Scenario: The toolbar and title bar fit in Ukrainian

- **WHEN** Translating is shown in Ukrainian at 960 by 640 with the translation paused
- **THEN** the toolbar's three actions and the title bar's items are shown whole or give way by priority, with none overlapping

#### Scenario: A short window scrolls the navigation column

- **WHEN** the window is 640 pixels high and the navigation column is taller
- **THEN** the title bar is fully visible and the column scrolls so that the Settings entry can be reached

#### Scenario: Count tiles keep their captions at the minimum width

- **WHEN** Translating is shown in a content area 944 pixels wide with 768 translated, 41 fixed automatically, 3 to review and 428 left
- **THEN** each of the four tiles is at least 120 pixels wide and its caption and number are shown without an ellipsis

#### Scenario: The progress bar is never squeezed away

- **WHEN** Translating is shown in a window shorter than the screen's own height, at 42 %
- **THEN** the progress bar is 9 pixels high and its filled part has a positive width

### Requirement: Group the navigation and number the workflow

The navigation column SHALL present its entries in two named groups — the workflow and the application — with each heading in capital letters. The workflow group SHALL list Projects with no number, then five numbered steps: Import, Book Brief, Names & style, Translating and Export. It SHALL list no review step, because review is a dialog, and no entry SHALL be inert. Exactly one entry SHALL be marked current, following the screen shown.

**Source**: FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/mockups/README.md#g7--five-steps`, `docs/specification/mockups/README.md#window-chrome`. In plain words: the numbers tell a first-time user that this is a sequence with an end, and the one highlighted entry always agrees with what is on screen. The structure is part of Import and review is a dialog on Translating, so there are five steps. Projects is now a real screen, so nothing in the column is a placeholder.

#### Scenario: The navigation has exactly two groups

- **WHEN** the navigation column is read
- **THEN** it carries exactly two group headings, `WORKFLOW` and `APPLICATION`, in that order
- **AND** the application group lists Settings alone

#### Scenario: The workflow entries are numbered in order

- **WHEN** the navigation column is read
- **THEN** the workflow group lists Projects with no number, then entries numbered 1 to 5 for Import, Book Brief, Names & style, Translating and Export
- **AND** no entry names a review queue

#### Scenario: Completed steps carry a done mark

- **WHEN** `Frankenstein.epub` is open, the person has continued from the Book Brief, and a translation has started
- **THEN** Import, Book Brief and Names & style show ✓ in place of their numbers, and Translating and Export show 4 and 5

#### Scenario: Projects can be opened

- **WHEN** the Projects entry is activated while the Book Brief is shown
- **THEN** the Projects screen is shown and its entry is marked current

#### Scenario: The current entry follows the screen

- **WHEN** Translating is shown
- **THEN** its entry is the only one marked current and the toolbar's breadcrumb reads `Workflow / Translating · step 4 of 5`

### Requirement: Use the control each drawn widget stands for

WHERE the reference rendering draws a widget for which `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#control-mapping-summary` names a real control, the screen SHALL be built from that control rather than from an approximation of it.

**Source**: FR-UI-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#control-mapping-summary`, `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#controls-mapping`. In plain words: a segmented picker drawn as three radio buttons and a toggle switch drawn as a checkbox both work, and both make the application read as a different program from the one that was designed.

#### Scenario: A quality dial is a segmented picker, not three buttons

- **WHEN** the Book Brief's quality choice is rendered
- **THEN** it is a segmented picker carrying the three choices as one control

#### Scenario: An auxiliary-text choice is a toggle switch

- **WHEN** the Book Brief's auxiliary-text choices are rendered
- **THEN** each is a toggle switch, not a checkbox

#### Scenario: An unavailable settings area uses its real control

- **WHEN** a settings area has nothing to show yet, such as Models before any AI model exists
- **THEN** it is still a tab of the same tab control as the Providers area, as are Generation, Appearance, Automation and Storage & logs

### Requirement: Match the reference rendering

For every screen the application shows, the arrangement of its parts and the roles they refer to SHALL match `docs/specification/mockups/ui-mockup.html` for that screen, in both the light and the dark block, except for the aids that exist only to explain the mockup: its "Mockup reference" pages, its preview bars, its mockup-only buttons, its requirement badges, its sample figures and its table-resize glyph. A transient message SHALL NOT carry the name of the requirement it shows.

**Source**: FR-UI-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), DD-28, `docs/specification/mockups/README.md#for-ai-agents-building-the-javafx-ui-from-this-mockup`. In plain words: the mockup settles how things look, and this revision of it describes the five-step application with review as a dialog, a toolbar of actions and a Projects screen, so the earlier list of departures no longer applies. What remains is what only helps the reader of the mockup. Exact pixel placement is not part of the obligation.

#### Scenario: A screen's roles match the reference

- **WHEN** Settings is rendered under the light values
- **THEN** its card surfaces refer to the surface role, which resolves to `#ffffff`
- **AND** its separators refer to the border role, which resolves to `#ddd5c8`

#### Scenario: The export screen now carries what the reference draws

- **WHEN** the Export screen is rendered for an open project and compared with the reference
- **THEN** it carries the Save to field, the Browse action and, in the toolbar, the Export book action the reference draws

#### Scenario: A stated deviation does not fail conformance

- **WHEN** the Import screen is rendered and compared with the reference
- **THEN** the absence of the drawn preview bars and `Mockup: finish →` buttons does not fail this requirement, because it names them as mockup-only
- **AND** every other part of the screen and its roles still match

#### Scenario: Pixel placement is not asserted

- **WHEN** a screen is rendered on a machine whose font rendering differs from the reference
- **THEN** it still satisfies this requirement, provided its parts and their roles match

### Requirement: Report an action that has not answered yet

WHILE an action the person started is running away from the thread that draws the window and has not yet returned, the screen SHALL report that it is in progress and SHALL make the control that started it unavailable until it answers. Model work SHALL be reported in the progress dialog; opening a book SHALL be reported by a "Reading…" bar with Cancel on the Import screen. WHEN the action answers, the control SHALL be available again.

**Source**: FR-UI-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`, `docs/specification/mockups/README.md#step-1--import--i1i4`. In plain words: the small busy card that said "working" is gone; model work now shows its steps in the progress dialog, and a book being read says which chapter it is on. The control is unavailable while the work runs so that a person does not press it four more times.

#### Scenario: Checking a provider reports itself while it runs

- **WHEN** the check is started on the selected provider and has not yet answered
- **THEN** the test's dialog shows it in progress and the button that started it is unavailable

#### Scenario: A finished action releases its control

- **WHEN** the verification answers with a first finding of `ErrorCode.unreachable`
- **THEN** the dialog stops showing progress, the first finding is shown as a failure, and the control is available again

#### Scenario: Opening a book reports itself while it runs

- **WHEN** `Frankenstein.epub` is being read
- **THEN** the Import verdict reads `Reading…` with a bar `chapter 3 of 5` and a Cancel, and neither the file chooser nor the drop target accepts a second book

#### Scenario: A short job uses the progress dialog

- **WHEN** `Fill in from the book` is used
- **THEN** the progress dialog opens with its steps, and no separate busy card exists

### Requirement: Show the run in the title bar while one exists

WHILE no project is open, the title bar SHALL show only the product name, the theme control and the about action. WHILE a project is open, it SHALL show, in this order: the project's name, the state text of the run state, the quality and review mode, elapsed time and speed while translating, the AI-model chip, and one run control, Pause or Resume, which does exactly what the toolbar's does. It SHALL also show the activity chip of hidden work.

**Source**: FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/mockups/README.md#window-chrome`, `docs/specification/mockups/README.md#run-states-a`. In plain words: a book takes hours and a person spends most of that time on other screens. The title bar keeps the project and its run in sight from anywhere. It gives way in a fixed order, name last, so a narrow window never loses what the person is looking for.

#### Scenario: No run, no run status

- **WHEN** the application has just been launched and no project is open
- **THEN** the title bar shows `BookLoom`, the theme control and the about action, and nothing else

#### Scenario: A running run is shown with Pause

- **WHEN** `Frankenstein · EN → UK` translates at 51 % with about 1 h 10 min left, after 1 h 02 m, at 9.4 tok/s
- **THEN** the title bar shows `Frankenstein · EN → UK`, `51 % done · about 1 h 10 min left`, `Balanced · Unattended`, `1h 02m`, `9.4 tok/s`, the chip `Model answering` and `Pause`
- **AND** the tooltip of the speed reads "how fast the model writes", and `Resume` is not shown

#### Scenario: A paused run is shown with Resume

- **WHEN** the translation is paused at 51 %
- **THEN** the title bar shows `Paused at 51 %`, a grey `Model idle` chip and `Resume`, and `Pause` is not shown

#### Scenario: A provider failure is named in the title bar

- **WHEN** the translation is paused because Ollama stopped answering
- **THEN** the title bar shows the red chip `Cannot reach Ollama` and `Resume` or, while BookLoom still retries, `Pause`

#### Scenario: A finished run offers no run control

- **WHEN** every segment has been decided
- **THEN** the title bar shows `Finished` and neither `Pause` nor `Resume`

#### Scenario: A failed run offers no run control

- **WHEN** the translation ends on an unexpected error at 51 %
- **THEN** the title bar shows `Did not finish` and a red `Error` chip, and neither `Pause` nor `Resume`

#### Scenario: Pausing from another screen

- **WHEN** Settings is shown, the translation runs at 42 %, and the title bar's `Pause` is used
- **THEN** the translation pauses at 42 %, the title bar shows `Paused at 42 %` and `Resume`, and Translating, when opened, shows it paused

#### Scenario: The control waits for a pause to take effect

- **WHEN** `Pause` has been used and segments 5–8 are still in flight
- **THEN** the title bar reads `Pausing…` and its run control is shown unavailable

#### Scenario: Each run state has its own text

- **WHEN** a translation is running, paused, stopped, waiting for the AI model, finished or failed, at 51 %
- **THEN** the state text reads `51 % done`, `Paused at 51 %`, `Stopped at 51 %`, `Waiting for Ollama`, `Finished` and `Did not finish` respectively

#### Scenario: The run control follows the state

- **WHEN** the translation is running, or paused or stopped, or finished or failed
- **THEN** the run control is `Pause`, or `Resume`, or absent respectively, and `Pause` and `Resume` are never shown together

#### Scenario: Both controls show the same state

- **WHEN** the title bar's run control is used
- **THEN** the application does what the toolbar's `Pause` or `Resume` does, and both show the same state

#### Scenario: Hidden work shows its chip

- **WHEN** the names pass is hidden at 53 %
- **THEN** the title bar shows the chip `Names · 53 % · open`

### Requirement: Show the quality dial and review mode in the title bar

WHILE a translation exists, the title bar SHALL show its quality dial and review mode, joined by ` · `, between the state text and the elapsed time (`Balanced · Unattended`), in English and Ukrainian, with a hover explanation, unless the window is 1280 pixels wide or less, and SHALL show nothing for it when none was named.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#title-bar-run-status`, `docs/specification/mockups/README.md#window-chrome`. In plain words: from any screen a person sees how hard the translation works and when it will stop to ask for them, when there is room for it.

#### Scenario: A run with the Balanced dial and Unattended mode

- **WHEN** a translation starts with the dial Balanced and the review mode Unattended in a window 1400 pixels wide
- **THEN** the title bar reads `Balanced · Unattended` next to the state text

#### Scenario: A narrow window hides it

- **WHEN** the window is 1280 pixels wide
- **THEN** `Balanced · Unattended` is not shown

### Requirement: Keep the computer awake while a run is at work

WHERE the Automation setting "Keep the computer awake while translating" is on, which it is by default, and WHILE a run translates, pauses or stops, or waits for the AI model by itself, and WHILE any other activity asks the model for text, the desktop application SHALL keep the computer from sleeping through the operating system's own mechanism. It SHALL let the computer sleep again only when the last activity has ended and at exit, and a missing or failing command SHALL never fail the run.

**Source**: the overnight plan, step 11 (owner intent: a run left all night must not be frozen by an idle sleep), `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`. In plain words: a laptop left alone sleeps after a few minutes, and a sleeping computer translates nothing. A person who does not want that can now turn it off in Settings. Starting a local system command is not network traffic, so the offline invariant is untouched.

#### Scenario: A run keeps the Mac awake until the person pauses it

- **WHEN** a run starts on macOS, waits for the AI model by itself for an hour, then is paused by the person
- **THEN** `caffeinate -i -m -w <pid>` runs from the start through the wait and is ended at the person's pause

#### Scenario: The setting is on by default

- **WHEN** the application is first started
- **THEN** Settings › Automation shows "Keep the computer awake while translating" switched on

#### Scenario: Turned off, nothing is held

- **WHEN** the setting is switched off and a run starts
- **THEN** no keep-awake command is started, and the Translating screen's help line reads "The computer may sleep while translating — turn on Keep awake in Settings."

#### Scenario: A long names pass keeps the computer awake

- **WHEN** a names scan runs for ten minutes with no translation run
- **THEN** the computer is kept awake throughout and is let sleep when the scan ends

#### Scenario: Pausing the run does not release an export's hold

- **WHEN** the run is paused by the person while an export still asks the model
- **THEN** the computer stays awake until the export ends

#### Scenario: No command on Windows

- **WHEN** a run starts on Windows
- **THEN** nothing is started, one WARN line says keep-awake is not implemented on this OS, and the run goes on

#### Scenario: Linux uses systemd-inhibit when it is installed

- **WHEN** a run starts on Linux
- **THEN** `systemd-inhibit` holds the computer awake when it is installed, and otherwise nothing is started and one WARN line says so

#### Scenario: Exit releases the hold

- **WHEN** the application exits while a hold is in place
- **THEN** the computer may sleep again

#### Scenario: A failing command does not fail the run

- **WHEN** the keep-awake command is missing or fails
- **THEN** the run goes on

#### Scenario: Every activity that asks the model for text holds the computer awake

- **WHEN** a names or terms scan, a review or translation, a review retry, an export, a suggestion or the inference test asks the model for text
- **THEN** the computer is kept awake until that activity has ended

#### Scenario: The connection chip says the provider is being retried

- **WHEN** the run waits for the AI model by itself
- **THEN** the AI-model chip reads `Cannot reach Ollama`

### Requirement: Close each workflow step with a Back and a forward action

The application SHALL hold each workflow screen's main and run actions in the toolbar, the one action bar, always visible: Import Continue; the Book Brief Back and Continue; Names & style Back and Start translation (or Go to Translating); Translating Back, Pause or Resume, Stop and Review (n), or Start, Continue or Try again now by state; Export Back and Export book, then Open folder and Open book. WHILE the content is compact, Back SHALL read "‹ Back" with the full label as its tooltip.

**Source**: FR-UI-01, FR-UI-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/mockups/README.md#layout-and-sizes-l` (L2). In plain words: the same buttons in the same place on every step, where they cannot scroll out of sight, make the numbered workflow feel like one path. They used to sit at the foot of each page, which could be below the fold.

#### Scenario: The brief's footer

- **WHEN** the Book Brief is shown
- **THEN** the toolbar holds Back to Import and Continue to Names & style

#### Scenario: Cancel clears the import screen

- **WHEN** `Frankenstein.epub` is being read on Import and Cancel on the `Reading…` bar is used
- **THEN** the Import screen shows its empty drop area and no detected book

#### Scenario: A refused book offers only another file

- **WHEN** `Purchased_Novel.epub` is refused as DRM-protected on Import
- **THEN** the verdict offers `Choose another file…` and the toolbar holds no Continue

#### Scenario: The last step cannot go forward

- **WHEN** Export is shown
- **THEN** the toolbar holds `Back`, which shows Translating, and `⬇ Export book`, and no forward step exists

#### Scenario: The import screen's footer

- **WHEN** a book is open on Import
- **THEN** the toolbar holds Continue, which shows the Book Brief

#### Scenario: Names and style's footer

- **WHEN** Names & style is shown
- **THEN** the toolbar holds Back to the Book Brief and `Start translation`, or `Go to Translating` while a run can be continued

#### Scenario: The translating screen's footer

- **WHEN** Translating is shown with the translation running, and then paused
- **THEN** the toolbar holds `Back`, `Pause`, `Stop` and `Review (6)`, and then `Back`, `Resume`, `Stop` and `Review (6)`

#### Scenario: The brief's footer names its targets

- **WHEN** the Book Brief is shown
- **THEN** Back leads to Import and Continue leads to Names & style

#### Scenario: Export's toolbar after writing

- **WHEN** the file has been written
- **THEN** the toolbar also holds `Open folder` and `Open book`

#### Scenario: Projects offers a new translation

- **WHEN** Projects is shown
- **THEN** the toolbar holds `+ New translation`

#### Scenario: Back is shortened when compact

- **WHEN** the content area is 944 pixels wide
- **THEN** the button reads `‹ Back` and its tooltip reads `Back to Book Brief`

### Requirement: Show the chosen provider and model in the navigation footer

The navigation column SHALL end with a footer line naming the AI program and the chosen model, separated by " · ", with the tooltip "the one network destination". Its dot and text SHALL follow the same source as the AI-model chip in the title bar: red `No AI model found` when none is chosen, and for example `Ollama · Cannot reach Ollama` when it does not answer.

**Source**: FR-UI-01, FR-MODEL-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`), `docs/specification/mockups/README.md#window-chrome`. In plain words: the navigation is visible from every screen, so a person never has to open Settings to see which program a translation would use or whether it is answering. The footer and the chip are one fact shown twice, so they can never disagree.

#### Scenario: The footer names the chosen provider and model

- **WHEN** Ollama is selected and `gemma4:26b` is chosen
- **THEN** the navigation footer reads `Ollama · gemma4:26b`

#### Scenario: No model chosen shows the empty state

- **WHEN** no AI model is chosen
- **THEN** the footer reads `No AI model found` in the error colour

#### Scenario: An unreachable program

- **WHEN** Ollama stops answering
- **THEN** the footer reads `Ollama · Cannot reach Ollama` and the title bar's chip reads `Cannot reach Ollama`

#### Scenario: Choosing a model updates the footer

- **WHEN** the footer reads `No AI model found` and `qwen3:8b` is then chosen
- **THEN** the footer reads `Ollama · qwen3:8b`

### Requirement: Ask before an import replaces a translation that can continue

WHEN a book is chosen for import while the open project's translation is running, paused, stopped or ready to continue, the application SHALL ask "Open another book?" with "Open as a new project" as the main choice, "Replace and delete this translation" as the named destructive choice and Cancel, and SHALL change nothing until the person answers. WHEN the translation is finished or failed, it SHALL open the book as a new project without asking.

**Source**: `docs/specification/mockups/README.md#other-dialogs`, `docs/specification/mockups/README.md#step-1--import--i1i4`. In plain words: importing another book used to throw away hours of work that could still be finished. Now the safe answer is the main one: the earlier translation stays, saved, in Projects. Deleting it is a separate choice with its own name. A translation that has nothing left to continue never gets in the way.

#### Scenario: Keeping a paused translation

- **WHEN** the translation of `Frankenstein.epub` is paused, `Kobzar.fb2` is chosen, and the person answers `Open as a new project`
- **THEN** `Kobzar.fb2` is opened as a new project and `Frankenstein · EN → UK` stays in Projects, paused

#### Scenario: Discarding a paused translation

- **WHEN** the same book is chosen and the person answers `Replace and delete this translation`
- **THEN** the earlier project's saved work is deleted and `Kobzar.fb2` is opened

#### Scenario: A completed run is replaced without asking

- **WHEN** the translation of `Frankenstein.epub` is finished and `Kobzar.fb2` is chosen
- **THEN** no dialog is shown and `Kobzar.fb2` is opened as a new project, the finished one staying in Projects

#### Scenario: Keeping the run leaves the screen as it was

- **WHEN** the person answers `Cancel`
- **THEN** the paused project, the open book and the screen stay as they were

### Requirement: Ask before an import resets a book's earlier translation

WHEN a book is chosen for import and a project of that file already holds translated segments, and the file is unchanged, the application SHALL show "Already in projects" and ask whether to continue that project, open the book as a new project or start over, and SHALL change nothing until the person answers. Otherwise it SHALL import the book without asking.

**Source**: `openspec/changes/archive/2026-10-19-complete-translation-workflow/additional-work.md` 15i.T7, `docs/specification/mockups/README.md#step-1--import--i1i4`. In plain words: importing a file again used to rebuild its segments as pending and lose decisions. Now the earlier project is named, with its number of translated segments, and the person chooses between carrying on with it and making another.

#### Scenario: Re-importing a file with decided segments asks

- **WHEN** `Dracula.epub` is chosen and its project `Dracula · EN → UK` holds seven translated segments
- **THEN** the question names `Dracula · EN → UK` and seven translated segments, and nothing is opened yet

#### Scenario: Continue keeps the decisions

- **WHEN** the person answers `Continue that project`
- **THEN** the project opens with its brief and all seven decisions

#### Scenario: Start over clears them

- **WHEN** the person answers `Start over`
- **THEN** the book is imported afresh into that project and no segment holds a decision

#### Scenario: A new project leaves the old one alone

- **WHEN** the person answers `Open as a new project`
- **THEN** a second project of the book is created and `Dracula · EN → UK` keeps its seven segments

#### Scenario: Escape does neither

- **WHEN** the question is closed with Escape
- **THEN** no project is opened and nothing is reset

#### Scenario: A failed lookup opens the book without asking

- **WHEN** looking up the count of translated segments fails
- **THEN** the book is opened without a question, and the lookup runs off the interface thread

#### Scenario: A finished run is cleared only after the choice

- **WHEN** the question is shown while a finished translation is on screen
- **THEN** the finished translation is replaced only once the person has chosen

#### Scenario: A file never imported, without decisions, or changed since is imported without asking

- **WHEN** the chosen file was never imported, holds no translated segment, or has changed since it was imported
- **THEN** the book is imported without a question

### Requirement: Move through the workflow by its five available steps

WHEN a person advances from a workflow screen, the application SHALL move to the next step, and every step SHALL remain reachable from the navigation column at any time once a project is open. WHEN "Start translation" is used on Names & style while a start is offered, the application SHALL start a run and show Translating, or, IF something the run needs is missing, show it naming what is missing and start nothing.

**Source**: FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, translation-pipeline's "Start a run and report its progress". In plain words: the numbering is a suggested order, not a wizard. Start translation takes the person to watch the run; while a run can be continued it only takes them to Translating, so two runs never begin side by side. What each state shows is stated by the run states.

#### Scenario: Continuing from the book brief skips the inert step

- **WHEN** a person continues from the Book Brief, step 2
- **THEN** Names & style, step 3, is shown and its entry is marked current
- **AND** no step is skipped, because no entry is inert

#### Scenario: Start translation starts the run

- **WHEN** `Frankenstein.epub` is open, the model `gemma3:12b` is chosen, and `Start translation` is used on Names & style
- **THEN** a run starts and Translating is shown with the run running

#### Scenario: Start translation with no model starts nothing

- **WHEN** no AI model is chosen and `Start translation` is used
- **THEN** Translating is shown with the banner `Cannot start yet — no AI model is chosen.` and no run exists

#### Scenario: Resuming a paused run continues it in place

- **WHEN** the run over `Frankenstein.epub` is paused at 55 % and `Resume` is used from the title bar or Translating
- **THEN** the same run continues from chunk 41/66, and no new run begins

#### Scenario: Resuming a stopped run begins a new one at the first pending segment

- **WHEN** the run was stopped with `ch12 · p03` the first pending segment, and `Continue translation` is used
- **THEN** a new run begins at `ch12 · p03` and every segment already decided stays decided

#### Scenario: A screen is reachable without walking the workflow

- **WHEN** a project is open and the Settings entry is activated directly
- **THEN** Settings is shown, without the Book Brief having been visited

#### Scenario: A start is offered when there is nothing to resume

- **WHEN** the project has no run, or its last run completed with segments still pending, or failed
- **THEN** a start is offered

#### Scenario: The run is started from the translating screen

- **WHEN** Translating is opened from the navigation with `Frankenstein.epub` open and `gemma3:12b` chosen, and `Start translation` is used on the toolbar
- **THEN** a run starts, and no workflow screen carries another control that starts one besides Names & style's `Start translation`

#### Scenario: No start is offered while a run can be resumed or nothing is pending

- **WHEN** the run is running, paused, stopped or paused after an error, or its last run completed and left nothing pending
- **THEN** the application offers no start

#### Scenario: Start translation while a run can be resumed starts nothing

- **WHEN** a run is paused and `Start translation` is used on Names & style
- **THEN** no second run begins and Translating is shown

### Requirement: Resume a run that exists and never call it a start

Resuming SHALL mean continuing a translation that already exists for the book. WHILE it is paused, Resume SHALL continue it in place; WHILE it is stopped, interrupted by a restart or failed, "Continue translation" SHALL begin at the first pending segment, keeping the segments already decided. A continuation SHALL never be labelled "Start", and the title bar's control SHALL do exactly what the toolbar's control of the same name does.

**Source**: FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/mockups/README.md#run-states-a`. In plain words: Start is for a book with nothing done; Resume and Continue always carry something on. A person who closed the application overnight sees Continue translation and "Ready to continue", not a button that suggests starting over.

#### Scenario: The title bar's Resume does what the screen's does

- **WHEN** the run over `Frankenstein.epub` is paused and `Resume` is used on the title bar
- **THEN** the paused run continues, exactly as the toolbar's `Resume` does

#### Scenario: A reopened project says Continue

- **WHEN** a project with 74 of 142 segments done is opened after a restart
- **THEN** the toolbar offers `Continue translation` and nothing is labelled `Start`

#### Scenario: Resume is not offered while the run is running

- **WHEN** the run over `Frankenstein.epub` is running
- **THEN** no Resume control is offered, and none labelled `Start`

#### Scenario: Resume is offered only while the run is paused, stopped or in the provider-error state

- **WHEN** the run is paused, paused after an error, or stopped
- **THEN** `Resume` is offered on the toolbar and the title bar for the first two, and `Continue translation` for a stopped run

## REMOVED Requirements

### Requirement: Show an entry that has no working screen behind it as unavailable

**Reason**: The only entry it covered, Projects, now has a working screen, so no navigation entry is inert. A placeholder rule with no entry left to apply to would be dead text.

**Migration**: The Projects screen is specified by "Show the Projects screen as a list grouped by book". The steps that cannot be used before a project is open are locked, not inert; see "Lock the five workflow steps until a project is open".
