## ADDED Requirements

### Requirement: Ask for the target language when Start is pressed without one

WHEN `Start translation` is pressed while the open project's brief has no target language, the application SHALL start nothing and SHALL show the dialog "Translate into which language?" with the brief's language list without the source language, Cancel, and `Start translation`, which stays disabled until a language is chosen. WHEN it is confirmed, the application SHALL store the language as the brief's target and go on with the start as if it had been chosen before.

**Source**: `design.md#d23--where-the-cannot-start-notice-shows-and-the-question-at-start`, `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`. In plain words: the target language is the one decision a start cannot guess. It is asked at the moment and place the person starts, not announced on screens where nothing can be done about it. The language is normally pre-filled, so this dialog is rare.

#### Scenario: The question at Start

- **WHEN** `earth-gravity.epub` (source English) is open with `gemma4:26b` chosen and no target language, and `Start translation` is pressed on Translating
- **THEN** the dialog "Translate into which language?" is shown, its list holds no `English`, and no run exists

#### Scenario: Confirming starts the run

- **WHEN** `Ukrainian` is chosen in that dialog and its `Start translation` is pressed
- **THEN** the brief's target is `uk`, the project is named `Earth Gravity · EN → UK`, and the start goes on to a running translation

#### Scenario: Cancel changes nothing

- **WHEN** Cancel or Escape is used in that dialog
- **THEN** the brief still has no target language, no run exists and no banner is raised

#### Scenario: A language the book already has is not started here

- **WHEN** the same book already has the project `Earth Gravity · EN → UK` and `Ukrainian — already in 1 project` is chosen in the dialog
- **THEN** `Start translation` stays disabled and the dialog says "This book already has a project with these languages — choose them on the Book Brief, where it gets its own name."

#### Scenario: A missing model is still named

- **WHEN** no AI model is chosen, the brief has no target language, and `Start translation` is looked at on Translating
- **THEN** it is disabled with the tooltip "Choose an AI model in Settings › Providers, then start the translation.", and no language dialog can be opened

### Requirement: Keep Translating open and Names & style locked while no target language is chosen

WHILE a project is open and its brief has no target language, the application SHALL keep Names & style greyed in the navigation with the tooltip "Choose the languages in Book Brief" and SHALL keep Translating available. WHILE the source and target languages are the same, both steps SHALL stay locked with that tooltip.

**Source**: `design.md#d23--where-the-cannot-start-notice-shows-and-the-question-at-start`. In plain words: names cannot be given a spelling in a language nobody chose, so that step waits. Translating must be reachable, because its Start button is where the missing language is asked for.

#### Scenario: No target language

- **WHEN** `notes.txt` is open with the source `English` and no target language
- **THEN** `3 Names & style` is unavailable with the tooltip "Choose the languages in Book Brief" and `4 Translating` can be opened

#### Scenario: The same language twice

- **WHEN** the source and the target are both `English`
- **THEN** `3 Names & style` and `4 Translating` are both unavailable with that tooltip

### Requirement: Replace the shown name when typing starts in a choice box

WHEN a searchable choice box that shows a chosen value gains the keyboard focus, by Tab or by a click, the application SHALL select its whole text, so that the first typed character replaces the name instead of being inserted into it. A click in a box that already has the focus SHALL place the caret.

**Source**: `research/combo-box-behaviour.md#a-what-the-control-does-today-and-the-two-defects` (A.2), `research/combo-box-behaviour.md#c-proposed-contract-and-the-smallest-way-to-build-it` (rule 1). In plain words: the text in such a box is the name of a chosen value, not prose being edited. Typing "en" into "Find out from the text" used to give "Find out fromen the text" and an empty, dead list.

#### Scenario: A click, then typing

- **WHEN** the source-language box shows "Find out from the text", it is clicked, and `en` is typed
- **THEN** the box holds `en` and the list is narrowed by `en`

#### Scenario: Tab, then typing

- **WHEN** the focus moves into the target-language box showing `Ukrainian` by Tab and `pol` is typed
- **THEN** the box holds `pol` and the list shows `Polish`

#### Scenario: A second click places the caret

- **WHEN** the genre box has the focus and holds `solarpunk novella`, and the person clicks between `solarpunk` and `novella`
- **THEN** the caret stands there and nothing is selected

### Requirement: Open a choice box's list from the keyboard and move through it without choosing

WHEN the Down key or the arrow button is used on a closed searchable choice box whose text is empty or is the chosen value's name, the application SHALL open the whole list with the chosen entry marked and in view. WHILE the list is open, Down and Up SHALL move a highlight from row to row and SHALL change neither the text nor the chosen value, and any change of the text SHALL remove the highlight.

**Source**: `research/combo-box-behaviour.md#b-what-an-editable-type-to-filter-box-is-expected-to-do`, `research/combo-box-behaviour.md#c-proposed-contract-and-the-smallest-way-to-build-it` (rules 3–6). In plain words: this is what every platform's combo box does. Here Down used to choose the second row at once and close the list, and Down on a closed box did nothing.

#### Scenario: Down opens the whole list

- **WHEN** the target-language box shows `Ukrainian`, has the focus, and Down is pressed
- **THEN** the list opens with all 34 languages and `Ukrainian` marked and in view

#### Scenario: Arrows choose nothing

- **WHEN** `e` is typed into an empty target-language box and Down is pressed twice
- **THEN** the second listed row is highlighted, the list is still open, the box still holds `e` and no language is chosen

#### Scenario: Typing drops the highlight

- **WHEN** a row is highlighted and `n` is typed
- **THEN** no row is highlighted and the list is narrowed by `en`

#### Scenario: Nothing matches

- **WHEN** `zz` is typed into the target-language box
- **THEN** no list is shown and the box holds `zz`

### Requirement: Choose a listed entry by a click or by Enter

WHEN a row of a searchable choice box's list is clicked, the application SHALL choose that row whatever was chosen before. WHEN Enter is pressed, it SHALL choose the highlighted row; else the value the typed text names exactly (or, in a box that takes free text, the text itself); else the only listed row; and otherwise SHALL choose nothing and keep the text and the list. After a choice the box SHALL show the chosen name, close the list and offer the whole list again.

**Source**: `research/combo-box-behaviour.md#a-what-the-control-does-today-and-the-two-defects` (A.3, A.4), `research/combo-box-behaviour.md#c-proposed-contract-and-the-smallest-way-to-build-it` (rules 7, 8, 11, 13, 14). In plain words: a click on the one remaining row was ignored when the entry chosen before stood at the same row number, and Enter chose the first row even when several matched, or wiped the text when none did.

#### Scenario: A click on the only row

- **WHEN** the source-language box shows "Find out from the text", `eng` is typed and the one listed row `English` is clicked
- **THEN** the source language is `en`, the box shows `English`, the list is closed and holds every language again

#### Scenario: Enter on a single match

- **WHEN** `eng` is typed into the same box and Enter is pressed
- **THEN** the source language is `en`

#### Scenario: Enter with several matches chooses nothing

- **WHEN** `e` is typed into an empty target-language box and Enter is pressed
- **THEN** no language is chosen, the box still holds `e` and the list stays open

#### Scenario: An exact tag wins over a partial match

- **WHEN** `en` is typed into the target-language box, where several names contain "en", and Enter is pressed
- **THEN** the target language is `en`

#### Scenario: A highlighted row

- **WHEN** `Pol` is typed into the "Translate into" box of "Translate into another language", Down is pressed and then Enter
- **THEN** `Polish` is chosen

#### Scenario: Free text

- **WHEN** `solarpunk novella` is typed into the genre box and Enter is pressed
- **THEN** the genre is `solarpunk novella`

#### Scenario: Enter never clears the text

- **WHEN** `zz` is typed into the target-language box and Enter is pressed
- **THEN** the box still holds `zz` and nothing is chosen

#### Scenario: No failure reaches the window

- **WHEN** `English` is the chosen target, `e` is typed and the row `English` is clicked
- **THEN** `English` is chosen and no error is reported

### Requirement: Cancel an unfinished search in a choice box with Escape

WHEN Escape is pressed in a searchable choice box whose list is open or whose text differs from the chosen value's name, the application SHALL close the list, show the chosen value's name again (nothing when none is chosen), choose nothing, and SHALL NOT pass the key on, so a dialog holding the box stays open. WHEN there is nothing to cancel, Escape SHALL act as it does anywhere else.

**Source**: `research/combo-box-behaviour.md#c-proposed-contract-and-the-smallest-way-to-build-it` (rule 9). In plain words: Escape used to close the list and leave the half-typed text, which the next move of the focus then took as a choice: "eng" left behind became English.

#### Scenario: Escape restores the name

- **WHEN** the target is `Ukrainian`, `eng` is typed into its box, Escape is pressed and the focus is moved away
- **THEN** the box shows `Ukrainian` and the target is still `uk`

#### Scenario: A dialog stays open

- **WHEN** `Pol` is typed in the language box of "Translate into another language" and Escape is pressed once
- **THEN** the box is empty again and the dialog is still open; a second Escape closes the dialog

### Requirement: Give every dialog footer one main action on the right

Every dialog footer SHALL hold at most one filled main button and SHALL place it rightmost; the button that leaves without acting (Cancel, Close, Not now, Keep editing, or the choice that keeps things as they are) SHALL be the outlined secondary button to its left; an action that discards or deletes SHALL be the red outlined button, and red SHALL be used for nothing else. A dialog whose only button is Close SHALL show it as the outlined secondary button.

**Source**: `research/ui-audit-shell-dialogs.md#cross-dialog-inconsistencies` (D-02, D-03, D-04), `research/ui-audit-settings.md#defects` (S-18). In plain words: the hand expects the confirming button in one place. Cancel had four looks, one dialog had its main button on the left, the leave guard had none, and a red Cancel beside "Apply to the brief" read as a destructive action.

#### Scenario: The leave guard

- **WHEN** "Save the changes to Ollama?" is shown
- **THEN** its footer reads `Keep editing` (outlined), `Discard` (red outlined), `Save` (filled), left to right

#### Scenario: Switching provider

- **WHEN** "Use LM Studio for the rest of this book?" is shown
- **THEN** `Keep Ollama` (outlined) stands left of `Use LM Studio` (filled)

#### Scenario: Cancel is never borderless or red on a finished result

- **WHEN** "Add glossary term", "Export complete" and the finished "Fill in the brief" dialog are shown
- **THEN** `Cancel`, `Close` and `Cancel` are outlined secondary buttons, and `Add term`, `Open book` and `Apply to the brief` are the filled ones

#### Scenario: Cancelling work in progress stays red

- **WHEN** the names pass is running in its progress dialog
- **THEN** its `Cancel`, which discards the pass, is the red outlined button

#### Scenario: A lone Close

- **WHEN** About is shown
- **THEN** `Close` is an outlined secondary button

### Requirement: Keep the banner slot as wide as the content below it

The banner slot SHALL end at the same right edge as the cards of the screen below it, at every window width and whether or not the content has a scrollbar, and the scrolled content SHALL start one card gap (14 pixels) below the slot, so no card slides under a banner's edge.

**Source**: `research/ui-audit-workflow.md#defects` (W-02, W-03), `research/ui-audit-shell-dialogs.md#defects` (D-07). In plain words: the banner was 8 to 20 pixels wider than everything under it, and scrolled cards came out from under it with no gap.

#### Scenario: Equal right edges

- **WHEN** Names & style shows the welcome-back banner at 1440 and at 1000 pixels of window width
- **THEN** the banner's right edge and the right edge of the first card are at the same x at both widths

#### Scenario: A gap under the banner

- **WHEN** the content of Export is scrolled by 200 pixels under a banner
- **THEN** the first visible card's top is clipped 14 pixels below the banner's bottom edge, not at it

### Requirement: Show whole lines and aligned pairs in the detail panes

A pane that shows prompt or reply text SHALL wrap it inside the visible width; a source and translation shown side by side SHALL start at the same top edge and, where both are boxes, be equally high; a pane limited to a number of lines SHALL be cut on a whole line; and the live card's segment list SHALL keep one width whether the run is going on or finished.

**Source**: `research/ui-audit-workflow.md#defects` (W-11, W-12, W-13, W-18, W-19). In plain words: prompt text ran under its own scrollbar, the English box of the review desk stood 6 pixels higher and up to 65 pixels taller than the Ukrainian one, the readable view showed the clipped top of a third line, and the segment list was 225 pixels wide while running and 375 after.

#### Scenario: Prompt text wraps

- **WHEN** the Detailed view shows a prompt whose longest line has 400 characters
- **THEN** no horizontal scrollbar is shown and the last character of every line is inside the box

#### Scenario: The review desk's two boxes

- **WHEN** the review desk shows a segment in English or in Ukrainian interface language
- **THEN** the source box and the translation box have the same top and the same height

#### Scenario: The segment list keeps its width

- **WHEN** Translating is shown at 1440 pixels while the run goes on, and again after it finished
- **THEN** the segment list has the same width both times

### Requirement: Name the segments with no translation in the Finished state

WHILE a project is in the Finished state and its export would write paragraphs in the source language for want of a usable translation (the number the `export` capability warns about), the application SHALL show that number in the title bar's state text, in the Translating screen's finished banner before the count of segments to review, and in the run-finished message, and SHALL say in the banner that an export writes them in the source language.

**Source**: `design.md#d5--one-number-for-no-translation-shown-before-export`, `research/review-digest.md#f12--finished-is-shown-for-a-book-with-untranslated-paragraphs`. In plain words: three measured novels ended "Finished" with 24, 5 and 1 paragraphs still in the source language, and the person learned it only from the dialog shown after the file was written. The word "Finished" must not hide them: the number is shown wherever the state is shown, and it is the same number the Export screen warns about.

#### Scenario: Finished with two segments that have no translation

- **WHEN** the translation of `Earth Gravity · EN → UK` completes with 6 segments to review, 2 of which hold no translation
- **THEN** the title bar reads `Finished · 2 not translated`
- **AND** the banner reads `Translation finished. 2 segments have no translation and will be exported in the source language. 6 segments need your review.` with `Review 6 segments`
- **AND** the run-finished message reads `Translation finished` with `2 segments have no translation` on its second line

#### Scenario: One segment uses the singular form

- **WHEN** 1 segment holds no translation and 1 segment needs review
- **THEN** the title bar reads `Finished · 1 not translated` and the banner reads `Translation finished. 1 segment has no translation and will be exported in the source language. 1 segment needs your review.`

#### Scenario: Finished with a translation for every segment

- **WHEN** the translation completes with 6 segments to review and export would write each of them with a translation
- **THEN** the title bar reads `Finished` and the banner reads `Translation finished. 6 segments need your review. You can export now and review later.`

#### Scenario: The number follows the review desk

- **WHEN** the person saves an edit for one of the 2 segments with no translation
- **THEN** the title bar reads `Finished · 1 not translated`

#### Scenario: A refused reply that export will write is not counted

- **WHEN** the translation completes with 2 FLAGGED segments that hold no machine translation, and the refused reply of one of them is fit for export to write
- **THEN** the title bar reads `Finished · 1 not translated`

#### Scenario: The words are in both languages

- **WHEN** the interface language is Ukrainian and 2 segments hold no translation
- **THEN** the title bar and the banner give the same number in Ukrainian words, with the plural form for 2

### Requirement: Start a translation without asking about the book

WHEN Start translation is pressed for a project that has a target language and a chosen model, the application SHALL start the run at once and SHALL show no question about names with a meaning, about who narrates or about characters' genders.

**Source**: `design.md#d23--where-the-cannot-start-notice-shows-and-the-question-at-start`, `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d21--defaults-instead-of-questions`. In plain words: Start could show three cards in a row, and a forced male-or-female answer about the narrator was a coin flip that went into every request. "Not stated" is a complete answer the pipeline handles: nothing is held to a gender nobody stated. Whoever wants to answer does so on the Book Brief ("Who narrates?") and on Names & style (the Gender and Translation columns), before or during the run.

#### Scenario: Start with characters of unknown gender

- **WHEN** the glossary holds five characters named 5 times or more with no gender, the brief's narrator is first person with gender not stated, and Start translation is pressed
- **THEN** the run starts and no card is shown

#### Scenario: A missing target language still asks

- **WHEN** Start translation is pressed with no target language
- **THEN** the dialog "Translate into which language?" opens ("Ask for the target language when Start is pressed without one"), and the run starts when a language is chosen, with no further question

### Requirement: Show the application's icon on its windows

The system SHALL give the main window, and the window that reports a failed start, the BookLoom icon in the sizes 16, 32, 48, 64, 128 and 256 pixels, so that the operating system shows it in the title bar and in the task bar where it draws a window's icon.

**Source**: `research/ci-and-release.md#d-packaging-facts` (Icons, gap 2), `design.md#d36--one-icon-source-under-modulesapp-the-window-gets-its-icon`. In plain words: today the running window has no icon of its own, so on Linux and Windows the title bar and the task bar show the toolkit's default picture even though the launcher file carries the BookLoom icon; a person then cannot find the app among open windows.

#### Scenario: The main window carries the icon in six sizes

- **WHEN** the application starts and its main window is shown
- **THEN** the window's icon list holds six images, `16×16`, `32×32`, `48×48`, `64×64`, `128×128` and `256×256` pixels
- **AND** each is the BookLoom tile on a transparent background

#### Scenario: The task bar shows the BookLoom icon on Linux

- **WHEN** the packaged application `BookLoom/bin/BookLoom` is started on a Linux desktop
- **THEN** the task-bar entry and the window's title bar show the BookLoom icon, not the default Java icon

#### Scenario: A failed start still shows the icon

- **WHEN** the application cannot open its database file `bookloom.db` and shows the start-failure message
- **THEN** that message window's icon list holds the same six images

## MODIFIED Requirements

### Requirement: Pin the run's banners above the content on every screen

WHILE a banner applies — the AI model is unreachable, a call is stuck, BookLoom gave up, the model is missing, welcome back, finished, or hidden work — the application SHALL show it in a slot between the toolbar and the scrolling content, on every screen, with "Go to Translating" where useful. The "Cannot start yet" banner SHALL be shown only on Names & style and Translating and SHALL never be raised for a missing target language.

**Source**: `design.md#d23--where-the-cannot-start-notice-shows-and-the-question-at-start`, `research/ui-audit-shell-dialogs.md#defects` (D-06). In plain words: a model program that went away matters whichever screen the person is on, and a banner inside a scrolled page can be missed. A start that is not possible yet is announced only on the two screens of the run; on Import, Projects, Settings and Export it took a sixth of the window to say something nobody could act on there.

#### Scenario: A banner on another screen

- **WHEN** Ollama becomes unreachable while Settings is shown
- **THEN** the banner appears between the toolbar and the content of Settings and offers `Go to Translating`

#### Scenario: It does not scroll away

- **WHEN** the content below the banner is scrolled to its end
- **THEN** the banner stays in place

#### Scenario: A missing target language leads to the Book Brief

- **WHEN** `notes.txt` is opened and its brief has no target language, and Import, Book Brief, Translating, Export, Projects and Settings are shown one after another
- **THEN** none of them shows "Cannot start yet — no target language is chosen."; the Book Brief marks its target box "Required: choose the language to translate into.", and `Start translation` on Translating asks for it

#### Scenario: A missing AI model is named only where Start is

- **WHEN** a project is open and no AI model is chosen
- **THEN** Translating and Names & style show "Cannot start yet — no AI model is chosen." with `Open settings`, and Import, Export, Projects and Settings show no such banner, while the title-bar chip `No AI model` and the navigation footer stay red on every screen

### Requirement: Show each of the sixteen run states the same way everywhere

The application SHALL put a project in exactly one of sixteen run states — Ready, Ready to continue, Cannot start yet, Translating, Pausing, Paused, Waiting for the AI model, Paused after an error, Stopped trying, Model missing, Stuck call, Stopping, Stopped, Finished to review, Finished nothing to review, Failed — and the title bar's text, the AI-model chip's colour, the toolbar, the banner and the live line SHALL follow that state.

**Source**: `design.md#d5--one-number-for-no-translation-shown-before-export`, `design.md#d23--where-the-cannot-start-notice-shows-and-the-question-at-start`, `research/ui-audit-shell-dialogs.md#defects` (D-06). In plain words: one table, not a separate idea of "state" on each screen. If the title bar, the toolbar and the banner all read the same row, they cannot disagree. A Finished state that holds segments with no translation says how many ("Name the segments with no translation in the Finished state").

#### Scenario: Ready

- **WHEN** a project has no progress
- **THEN** the title bar reads `Ready`, the chip is grey `AI model ready`, the toolbar offers `Start translation`, the live line reads `Nothing translated yet.`, and review and export are off

#### Scenario: Ready to continue

- **WHEN** a project whose stored run reads paused, with 74 of 142 segments done, is opened
- **THEN** the title bar reads `Ready · 51 % done`, the toolbar offers `Continue translation` and `Review (6)`, the banner reads `Welcome back — 74 of 142 segments are done.` and the live line reads `Continues at Chapter 2, segment 5.`

#### Scenario: Cannot start yet

- **WHEN** no AI model is chosen and Translating is shown
- **THEN** the chip is red `No AI model`, `Start translation` is disabled with a tooltip naming what is missing, and the banner reads `Cannot start yet — no AI model is chosen.` with `Open settings`
- **AND** on Import, Projects and Export the chip is red and no such banner is shown

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

- **WHEN** an attempt of a draft call whose time limit is 6:00 has had no answer for 3 minutes 40 seconds
- **THEN** the chip is amber `No answer for 3:40`, the toolbar is as for Translating, the banner reads `The model is taking longer than usual (3:40). BookLoom tries again by itself at 6:00. You do not need to do anything.`, and the call line shows the one clock `3:40 of 6:00`

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

### Requirement: Move through the five workflow steps and start one run at a time

WHEN a person advances from a workflow screen, the application SHALL move to the next step, and every step SHALL remain reachable from the navigation column at any time once a project is open. WHEN "Start translation" is used on Import, Names & style or Translating while a start is offered, the application SHALL start a run and show Translating, or, IF no AI model is chosen, show Translating naming it and start nothing.

**Source**: `design.md#d21--defaults-instead-of-questions`, `design.md#d23--where-the-cannot-start-notice-shows-and-the-question-at-start`, `research/ui-audit-shell-dialogs.md#defects` (D-06). In plain words: the numbering is a suggested order, not a wizard. While a run can be continued, Start translation only shows Translating, so two runs never begin side by side. A missing target language is asked for at Start instead (see "Ask for the target language when Start is pressed without one").

#### Scenario: Continuing from the book brief shows the next step

- **WHEN** a person continues from the Book Brief, step 2
- **THEN** Names & style, step 3, is shown and its entry is marked current

#### Scenario: Start translation starts the run

- **WHEN** `earth-gravity.epub` is open, the model `gemma3:12b` is chosen, and `Start translation` is used on Names & style
- **THEN** a run starts and Translating is shown with the run running

#### Scenario: Start translation with no model starts nothing

- **WHEN** no AI model is chosen and `Start translation` is used
- **THEN** Translating is shown with the banner `Cannot start yet — no AI model is chosen.` and no run exists

#### Scenario: Resuming a paused run continues it in place

- **WHEN** the run over `earth-gravity.epub` is paused at 55 % and `Resume` is used from the title bar or Translating
- **THEN** the same run continues where it paused, and no new run begins

#### Scenario: Continuing a stopped run begins a new one at the first pending segment

- **WHEN** the run was stopped with `ch12 · p03` the first pending segment, and `Continue translation` is used
- **THEN** a new run begins at `ch12 · p03` and every segment already decided stays decided

#### Scenario: A screen is reachable without walking the workflow

- **WHEN** a project is open and the Settings entry is activated directly
- **THEN** Settings is shown, without the Book Brief having been visited

#### Scenario: A start is offered when there is nothing to continue

- **WHEN** the project has no run, or its last run completed with segments still pending
- **THEN** `Start translation` is offered

#### Scenario: A failed run offers Continue translation

- **WHEN** the last run ended Failed at 51 %
- **THEN** the toolbar offers `Continue translation`, and no `Start translation`

#### Scenario: The run is started from the translating screen

- **WHEN** Translating is opened from the navigation with `earth-gravity.epub` open and `gemma3:12b` chosen, and `Start translation` is used on the toolbar
- **THEN** a run starts, and no workflow screen carries another control that starts one besides `Start translation` on Import and on Names & style

#### Scenario: No start is offered while a run can be continued or nothing is pending

- **WHEN** the run is running, paused, stopped, paused after an error or failed, or its last run completed and left nothing pending
- **THEN** the application offers no start

#### Scenario: Start translation while a run can be resumed starts nothing

- **WHEN** a run is paused and `Start translation` is used on Names & style
- **THEN** no second run begins and Translating is shown

### Requirement: Show all model work in one progress dialog

WHEN the application asks the model for the brief, the names, a file name or a translation's calls, among them the Prepare steps of a run and its proofread windows, it SHALL show one progress dialog with a header (title, purpose line and a determinate bar), a figures line such as "51 % · 26 of about 49 steps · Left about 1 min", a list of steps with a marker and one result line each, a detail pane, and a footer holding what Cancel does.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: hours of model work used to hide behind a spinner or raw prompts in another window. One dialog shows what is being done, how far along it is and what came back, whatever the work is. The export's last check is no longer model work, so it is no longer listed; the Prepare steps of a run and its proofread windows are, and chapter summaries, which are no longer written, are not.

#### Scenario: The figures line

- **WHEN** the model work in the dialog has done 26 of about 49 steps
- **THEN** the bar sits at 51 % and the figures line reads `51 % · 26 of about 49 steps · Left about 1 min`

#### Scenario: A finished step stays selectable

- **WHEN** step 3 of the brief suggestion has finished and step 4 is running
- **THEN** step 3 shows its result line and can still be selected to see its input and output, and step 5 says it has not started

#### Scenario: Chapter summaries and proofread windows are steps

- **WHEN** a Balanced run has finished the names step of its Prepare stage and is proofreading the window of paragraphs 40 to 42
- **THEN** the dialog lists the step `Suggest translations for names` with its result line and a step `window ¶40–¶42` with its verdict, and no step for a chapter summary

#### Scenario: The footer says what Cancel does

- **WHEN** the brief suggestion's dialog is shown
- **THEN** its footer reads `Cancel writes nothing.` with Cancel on the right

#### Scenario: Work that cannot be stopped promises no Cancel

- **WHEN** the dialog shows the opening of a book, which cannot be stopped
- **THEN** it says `This work cannot be stopped; it ends by itself.` under its title, and neither a Cancel button nor `Cancel writes nothing.` is shown

### Requirement: Show the live card's Live and Activity log tabs

The Translating screen's live card SHALL hold two tabs, Live and Activity log. The Activity log SHALL offer the filter "All · Problems only" and list proofread entries with the other kinds, and "All calls…" on the Live tab SHALL open the progress dialog listing the translation's calls, newest first, keeping the last 50.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`. In plain words: what is happening now and what has happened are two different questions, so they get two tabs. A book makes thousands of calls, so only the latest 50 are kept for reading. The Proofread stage appears in the same places as the drafts; there is no chapter-summary entry, because none is written.

#### Scenario: Two tabs

- **WHEN** Translating is shown during a translation
- **THEN** the live card shows the tabs `Live` and `Activity log`, with `Live` selected

#### Scenario: Problems only

- **WHEN** the Activity log holds 40 lines of which 3 are problems, and `Problems only` is chosen
- **THEN** only those 3 lines are listed, and `All` lists the 40 again

#### Scenario: Proofread and summary entries are in the Activity log

- **WHEN** the Activity log is opened after chapter 3 was finished and the window of paragraphs 40 to 42 was proofread
- **THEN** it lists an entry of the kind proofread and no entry of a chapter-summary kind

#### Scenario: The last 50 calls

- **WHEN** a translation has made 120 calls and `All calls…` is used
- **THEN** the progress dialog lists calls 120 down to 71, newest first, and no older call

#### Scenario: The call still at work

- **WHEN** `All calls…` is used while call 16, a Translate call of 7 segments, still waits for its answer, and call 15 was answered with their translations
- **THEN** call 16 reads `Working on Translate.` and `Nothing yet.`, not done
- **AND** call 15 reads `Translate is done.` with the translations it brought back under What it says

### Requirement: Keep list and detail side by side and stack cards where they do not fit

The application SHALL keep the list-and-detail splits of the live panel, the review desk, the progress dialogs and Settings › Providers side by side at every width. It SHALL give Names & style's one Glossary card the whole width of the content at every width, and WHILE the content is compact it SHALL stack the Import browser's Text pane and set the four count tiles 2 by 2. IF a pair of cards does not fit side by side at its content's minimum widths (two equal columns as wide as the wider minimum plus the gap, or a main column's minimum beside a side column's preferred width), THEN the application SHALL stack it whatever its breakpoint, and SHALL put it side by side again only 16 pixels past that width; no control that cannot shrink is cut at the right edge in English or Ukrainian.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d7--the-recurring-term-list-is-removed-whole`. In plain words: a list without its detail is useless, so those never stack. The glossary is the one table of Names & style, so it takes the whole width. The split widths are the README's.

#### Scenario: Names & style stacks below wide

- **WHEN** Names & style is shown with a content area 1439 pixels wide, and then 944
- **THEN** both times the Glossary card is as wide as the content and no Recurring terms card is shown

#### Scenario: Names & style is side by side when wide

- **WHEN** Names & style is shown with a content area 1440 pixels wide
- **THEN** the Glossary card is as wide as the content and no card stands beside it

#### Scenario: A pair stacks before a control is cut

- **WHEN** the Book Brief is shown in Ukrainian with a content area 748 pixels wide (a 1000-pixel window), above its 700-pixel breakpoint but narrower than its Tone & style card's register choice beside the Languages card
- **THEN** the Tone & style card is below the Languages card and no control of either runs past the screen's right edge

#### Scenario: The review desk never stacks

- **WHEN** the review desk is shown in a window 960 pixels wide
- **THEN** its list and its detail are side by side

#### Scenario: Four tiles go two by two

- **WHEN** the four count tiles are shown in a compact content area
- **THEN** they form two rows of two

### Requirement: Translate a book into another language without importing it again

WHEN "Translate into another language" is used, the application SHALL ask for the language and the project name (required for a used pair), create a project on the already imported book that uses the currently chosen AI model, copy the brief and the Glossary (entries, kinds, genders, locked marks) with every translation emptied, never copy the translation memory, import nothing again and open the new project's brief.

**Source**: `design.md#d7--the-recurring-term-list-is-removed-whole`, `design.md#d16--titles-are-glossary-rows`. In plain words: the characters and places are the same in any language, so their list is kept; renderings and old translations would pull a new language wrong, so they are cleared. There is one list to copy now: titles are glossary rows and the recurring-term list is removed.

#### Scenario: A German copy of a Ukrainian project

- **WHEN** `Earth Gravity · EN → UK` has the glossary entry `Mira Holt` (character, female, locked) rendered `Міра Голт`, and a German translation is made
- **THEN** the new project has `Mira Holt` as a locked female character with an empty rendering, and the brief's other choices are copied

#### Scenario: Recurring terms are copied with empty translations

- **WHEN** the Ukrainian project's Glossary holds the title row `warden` (title, locked) rendered `наглядач`, and a German translation is made
- **THEN** the German project has `warden` as a locked title with an empty rendering

#### Scenario: The current AI model is used

- **WHEN** the Ukrainian project used `gemma4:e4b`, `gemma4:26b` is the AI model chosen now, and a German translation is made
- **THEN** the new project uses `gemma4:26b`, and the dialog offers no choice of AI model

#### Scenario: The memory is not copied

- **WHEN** the Ukrainian project holds 300 remembered passages
- **THEN** the German project holds none

#### Scenario: Nothing is imported again

- **WHEN** `Create and open the brief` is used
- **THEN** the new project's Book Brief opens, and no `Reading…` bar is shown

#### Scenario: A used language is marked

- **WHEN** the language list is shown and Ukrainian is already used
- **THEN** it reads `Ukrainian — already in 1 project`

### Requirement: Name the recommended model on the empty Projects screen

WHILE no project exists, the Projects screen SHALL show a line naming the model recommended for the computer's memory with its pull command as text, such as "This computer has 32 GB: gemma4:26b is recommended." followed by its pull command, and SHALL NOT run the command.

**Source**: `research/review-digest.md#owner-decisions`, `research/review-digest.md#f1--the-model-sets-the-quality-ceiling`, `design.md#d21--defaults-instead-of-questions`. In plain words: a new person does not know which model fits their computer. The first screen says which one to get and how, and leaves the download to them. Which model fits which memory is the settings capability's rule for the model choice. gemma4:26b is the recommended model; on a smaller computer the line says what the model that fits costs in quality.

#### Scenario: A 16 GB computer

- **WHEN** the application starts for the first time on a computer with 16 GB of memory
- **THEN** the line reads "This computer has 16 GB: gemma4:e4b fits it. gemma4:26b translates clearly better and needs 24 GB. Pull it with `ollama pull gemma4:e4b`."

#### Scenario: A 32 GB computer

- **WHEN** the first launch happens on a computer with 32 GB of memory
- **THEN** the line names `gemma4:26b` as recommended and shows, as text, the `ollama pull` command with the exact tag the recommendation holds for it

#### Scenario: Nothing is pulled

- **WHEN** the line is shown
- **THEN** BookLoom starts no download

#### Scenario: A list of projects hides the line

- **WHEN** at least one project exists
- **THEN** the line is not shown

### Requirement: Make the screen that hidden work writes to read-only

WHILE long model work runs in the background, the screen it writes to SHALL be read-only behind a banner reading, for example, "Names is running in the background" with the line "This screen is read-only until it ends, so its results and your edits do not overwrite each other." and a "Show progress" button, and other model work SHALL be refused with a message that offers "Show progress". WHILE the Prepare stage of a run is running its names step, Names & style SHALL be read-only in the same way, with "Show progress" leading to Translating. WHEN the work ends, the chip SHALL go and a message with "Review the results" SHALL appear; nothing SHALL open by itself.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: the background pass is writing into the glossary, so editing it by hand at the same time would lose one of the two sets of changes. Saying so, and offering a way back, is better than a silent lock. The names pass now also runs inside a run's Prepare stage, and it writes the same glossary, so the same rule holds there.

#### Scenario: The glossary is read-only

- **WHEN** the names pass is hidden at 53 %
- **THEN** Names & style is read-only and the banner reads `Names is running in the background` over `This screen is read-only until it ends, so its results and your edits do not overwrite each other.`

#### Scenario: A second job is refused

- **WHEN** `Fill in from the book` is used while the names pass is hidden
- **THEN** nothing starts and a warning message with `Show progress` is raised

#### Scenario: It ends quietly

- **WHEN** the hidden names pass ends
- **THEN** the chip is gone, a message offers `Review the results`, and no dialog opens by itself

#### Scenario: The names step of a run makes Names & style read-only

- **WHEN** a run is in its Prepare stage, its names step has answered 25 requests, and Names & style is opened
- **THEN** Names & style is read-only under the banner `Names is running in the background`, `Show progress` opens Translating, and the screen is editable again when the step ends

## REMOVED Requirements

### Requirement: Show a chapter summary as a live-card row

**Reason**: No chapter summary is written: the calls took 6.8 % of the traced run and no effect on the translated text could be measured (`research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`).

**Migration**: None. The live card keeps its segment rows and its proofread-window rows.
