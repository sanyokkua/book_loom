## ADDED Requirements

### Requirement: Show the run's stage on the Translating summary strip

WHILE a run is at work, the Translating summary strip SHALL show the stages Prepare, Translate and Proofread with the stage in work marked; at Max the Proofread chip SHALL name the round in work, and at Fast the Proofread step SHALL read "marks for review, no model calls".

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: a run now has three stages, and a person watching must know which one is going and, at Max, how far the second reading of the book has got. At Fast, Proofread makes no model call, and the strip says so rather than leaving a bare stage that never moves.

#### Scenario: The stage in work is marked

- **WHEN** a Balanced run has finished drafting and is proofreading marked windows
- **THEN** the strip shows `Prepare`, `Translate` and `Proofread` with `Proofread` marked as the stage in work

#### Scenario: Max names the round

- **WHEN** a Max run is reading the whole book a second time
- **THEN** the Proofread chip reads `Proofread · round 2 of 2: whole book`

#### Scenario: Fast makes no proofread call

- **WHEN** a Fast run reaches its Proofread step
- **THEN** the step reads "marks for review, no model calls"

#### Scenario: Prepare comes first

- **WHEN** a run has just started and is preparing the book (the style sheet from the brief and, with an empty glossary, the name scan)
- **THEN** `Prepare` is marked

### Requirement: Show a proofread window as a live-card row with its verdict

WHEN the Proofread stage reads a window of paragraphs, the live card SHALL list it as a row such as `window ¶40–¶42` whose detail shows the text before, the text after and the verdict, kept, proposed, rejected or unchanged, with the reason in words.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: Proofread may rewrite a few paragraphs, and a person must be able to see what changed and why an edit was kept or turned down, in the same place as the draft calls.

#### Scenario: A kept edit

- **WHEN** the window of paragraphs 40 to 42 was proofread and the gender ending of a verb was fixed
- **THEN** the row `window ¶40–¶42` shows the text before and after and the verdict `kept: gender ending fixed`

#### Scenario: A rejected edit

- **WHEN** a proofread rewrite of the same window added the Russian word `что` and the never-worse gate turned it down
- **THEN** the row shows the verdict `rejected: added a Russian word «что»` and the text after is the text before

#### Scenario: Unchanged and proposed

- **WHEN** the model returned the paragraphs as they were, and in another window the edit waits for the person
- **THEN** the first row shows `unchanged` and the second `proposed`

### Requirement: Show a chapter summary as a live-card row

WHEN a chapter's summary is written, the live card SHALL list a row `Chapter {n} summarised` whose detail shows the summary text.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: later chapters are drafted with the summaries of earlier ones, so a person can check what the model made of a chapter before it shapes the rest of the book.

#### Scenario: A summary is written

- **WHEN** the summary of chapter 3 is written
- **THEN** the live card lists the row `Chapter 3 summarised` and its detail shows the two or three sentences

#### Scenario: No summaries when the switch is off

- **WHEN** the book's chapter-summaries switch is off
- **THEN** the live card lists no `Chapter {n} summarised` row

### Requirement: Show a figure the engine did not report as a dash

WHERE a call's time to first token, prompt-reading time, cached share or speed was not reported by the engine, the application SHALL show `–` in the live card, the call detail and the progress dialog, and SHALL NOT show 0 or count it in an average.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: some servers report no time to first token. Showing 0 would make them look instant and would pull every average down, so a missing figure stays visibly missing.

#### Scenario: A call without a time to first token

- **WHEN** the call detail of a draft call answered through Ollama's OpenAI-compatible route is opened
- **THEN** its time to first token reads `–`

#### Scenario: A reported figure is shown

- **WHEN** LM Studio reports a time to first token of 0.21 seconds
- **THEN** the call detail shows 210 ms

#### Scenario: A missing figure is left out of the average

- **WHEN** three calls reported 200 ms, 400 ms and nothing
- **THEN** the average time to first token reads 300 ms, from the two calls that reported it

### Requirement: Name the recommended model on the empty Projects screen

WHILE no project exists, the Projects screen SHALL show a line naming the model recommended for the computer's memory with its pull command as text, such as "This computer has 16 GB: gemma4:e4b is recommended. Pull it with `ollama pull gemma4:e4b`.", and SHALL NOT run the command.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-projects`, `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a new person does not know which model fits their computer. The first screen says which one to get and how, and leaves the download to them. Which model fits which memory is the settings capability's rule for the model choice.

#### Scenario: A 16 GB computer

- **WHEN** the application starts for the first time on a computer with 16 GB of memory
- **THEN** the line reads "This computer has 16 GB: gemma4:e4b is recommended. Pull it with `ollama pull gemma4:e4b`."

#### Scenario: A 32 GB computer

- **WHEN** the first launch happens on a computer with 32 GB of memory
- **THEN** the line names `gemma4:26b-a4b` as recommended with `ollama pull gemma4:26b-a4b`

#### Scenario: Nothing is pulled

- **WHEN** the line is shown
- **THEN** BookLoom starts no download

#### Scenario: A list of projects hides the line

- **WHEN** at least one project exists
- **THEN** the line is not shown

### Requirement: Answer Enter, Escape and Tab alike in every dialog

WHILE a dialog is open, the application SHALL keep Tab inside it, SHALL run the footer's main button on Enter (Ctrl or Command with Enter inside a text area, and everywhere in the review desk, whose main button decides a segment and so runs only on Ctrl or Command with Enter), and SHALL close it on Escape. On a progress dialog Escape SHALL hide long work and cancel short work; only the export dialog's Cancel button SHALL ask "Cancel the export?". Only About SHALL close on a click outside it.

**Source**: `docs/specification/mockups/README.md#g1--dialogs-live-in-the-window-on-two-layers`, owner answer of 2026-10-20 recorded in `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D1", `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: a person at the keyboard should be able to answer any dialog without finding the mouse, and Escape never loses long work: it only puts it out of the way. The export no longer has a last check to hide, so the only long work Escape hides is the names pass.

#### Scenario: Enter presses the main button

- **WHEN** `Stop translating?` is shown and Enter is pressed
- **THEN** the main button of its footer is activated

#### Scenario: Plain Enter in the review desk decides nothing

- **WHEN** the review desk is open on `ch5 · p12` with a row of its list focused and Enter is pressed
- **THEN** no segment is accepted, and Ctrl+Enter (Command+Enter on macOS) runs the desk's main button

#### Scenario: Enter in a text area adds a line

- **WHEN** the cursor is in the note field of a dialog and Enter is pressed
- **THEN** a new line is added, and Ctrl+Enter (Command+Enter on macOS) activates the main button

#### Scenario: Escape hides long work

- **WHEN** the names pass's progress dialog is showing and Escape is pressed
- **THEN** the dialog is hidden, the names pass keeps running, and the activity chip in the title bar shows it

#### Scenario: Escape cancels short work

- **WHEN** the progress dialog of `Fill in from the book` is showing at step 3 of 6 and Escape is pressed
- **THEN** the work is cancelled, the dialog closes and nothing is written to the brief

#### Scenario: Focus stays in the card

- **WHEN** Tab is pressed on the last control of a dialog
- **THEN** focus moves to the first control of the same dialog

#### Scenario: A click outside closes About only

- **WHEN** the scrim outside the open About dialog is clicked, and then outside an open `Stop translating?` dialog
- **THEN** About closes and `Stop translating?` stays open

## MODIFIED Requirements

### Requirement: Show the run in the title bar while one exists

WHILE no project is open, the title bar SHALL show only the product name, the theme control and the about action. WHILE a project is open, it SHALL show, left to right: the project's name, the run state's text, the stage chip while a run is running or paused, the quality and review mode, elapsed time and speed while translating, the AI-model chip, the activity chip of hidden work, and one run control, Pause or Resume, doing exactly what the toolbar's does.

**Source**: FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/mockups/README.md#window-chrome`, `docs/specification/mockups/README.md#run-states-a`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: a book takes hours and a person spends most of that time on other screens. The title bar keeps the project, its run and the stage the run is in in sight from anywhere.

#### Scenario: No run, no run status

- **WHEN** the application has just been launched and no project is open
- **THEN** the title bar shows `BookLoom`, the theme control and the about action, and nothing else

#### Scenario: A running run is shown with Pause

- **WHEN** `Earth Gravity · EN → UK` translates at 51 % with about 1 h 10 min left, after 1 h 02 m, at 9.4 tok/s, in a window 1440 pixels wide
- **THEN** the title bar shows, from left to right, `Earth Gravity · EN → UK`, `51 % done · about 1 h 10 min left`, the stage chip `Translate`, `Balanced · Unattended`, `1h 02m`, `9.4 tok/s`, the chip `Model answering` and `Pause`
- **AND** the tooltip of the speed reads "how fast the model writes", and `Resume` is not shown

#### Scenario: The stage chip follows the run

- **WHEN** a Balanced run moves from drafting to proofreading marked windows
- **THEN** the stage chip changes from `Translate` to `Proofread`

#### Scenario: Preparation has its own chip

- **WHEN** a run has started and is preparing the book
- **THEN** the stage chip reads `Prepare`

#### Scenario: Max names the proofread round

- **WHEN** a Max run is reading the whole book a second time
- **THEN** the stage chip reads `Proofread · round 2 of 2: whole book`

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

- **WHEN** the translation is running, or paused, or stopped, or finished or failed
- **THEN** the run control is `Pause`, or `Resume`, or `Continue translation`, or absent respectively, and `Pause` and `Resume` are never shown together

#### Scenario: Both controls show the same state

- **WHEN** the title bar's run control is used
- **THEN** the application does what the toolbar's `Pause` or `Resume` does, and both show the same state

#### Scenario: Hidden work shows its chip

- **WHEN** the names pass is hidden at 53 %
- **THEN** the title bar shows the chip `Names · 53 % · open`

#### Scenario: No stage chip once the run has ended

- **WHEN** the run of the open project completes or fails
- **THEN** the title bar shows no stage chip, and the state text reads `Finished` or `Did not finish`

### Requirement: Show all model work in one progress dialog

WHEN the application asks the model for the brief, the names, a file name or a translation's calls, among them chapter summaries and proofread windows, it SHALL show one progress dialog with a header (title, purpose line and a determinate bar), a figures line such as "51 % · 26 of about 49 steps · Left about 1 min", a list of steps with a marker and one result line each, a detail pane, and a footer holding what Cancel does.

**Source**: `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: hours of model work used to hide behind a spinner or raw prompts in another window. One dialog shows what is being done, how far along it is and what came back, whatever the work is. The export's last check is no longer model work, so it is no longer listed; chapter summaries and proofread windows are.

#### Scenario: The figures line

- **WHEN** the names pass has done 26 of about 49 steps
- **THEN** the bar sits at 51 % and the figures line reads `51 % · 26 of about 49 steps · Left about 1 min`

#### Scenario: A finished step stays selectable

- **WHEN** step 3 of the brief suggestion has finished and step 4 is running
- **THEN** step 3 shows its result line and can still be selected to see its input and output, and step 5 says it has not started

#### Scenario: Chapter summaries and proofread windows are steps

- **WHEN** a Balanced run has written the summary of chapter 3 and is proofreading the window of paragraphs 40 to 42
- **THEN** the dialog lists a step `Chapter 3 summarised` with its result line and a step `window ¶40–¶42` with its verdict

#### Scenario: The footer says what Cancel does

- **WHEN** the brief suggestion's dialog is shown
- **THEN** its footer reads `Cancel writes nothing.` with Cancel on the right

#### Scenario: Work that cannot be stopped promises no Cancel

- **WHEN** the dialog shows the opening of a book, which cannot be stopped
- **THEN** it says `This work cannot be stopped; it ends by itself.` under its title, and neither a Cancel button nor `Cancel writes nothing.` is shown

### Requirement: Let long work continue in the background

WHERE model work is long — the names pass — the progress dialog SHALL offer "Continue in background", which hides it and shows the activity chip in the title bar (for example "Names · 53 % · open"). Short work — the brief, a file name, a test — SHALL NOT be hideable. WHEN the chip is clicked, the same dialog SHALL reopen with its state.

**Source**: `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`, owner decision 11 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: a names pass can take many minutes, and a person should be able to read the book meanwhile. Short work ends before anyone could want to hide it, so it is not offered. The export's last check, once also hideable, no longer exists.

#### Scenario: Hiding the names pass

- **WHEN** `Continue in background` is used at 53 %
- **THEN** the dialog closes, the title bar shows an activity chip `Names · 53 % · open`, and the names pass keeps running

#### Scenario: Reopening

- **WHEN** the chip is clicked
- **THEN** the same dialog opens with its steps, selection and figures as they are now

#### Scenario: A short job has no such button

- **WHEN** the dialog of `Fill in from the book` is shown
- **THEN** its footer offers no `Continue in background`

### Requirement: Show the live card's Live and Activity log tabs

The Translating screen's live card SHALL hold two tabs, Live and Activity log. The Activity log SHALL offer the filter "All · Problems only" and list proofread and chapter-summary entries with the other kinds, and "All calls…" on the Live tab SHALL open the progress dialog listing the translation's calls, newest first, keeping the last 50.

**Source**: `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (C1, the live card, Activity log tab and All calls… rows), `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: what is happening now and what has happened are two different questions, so they get two tabs. A book makes thousands of calls, so only the latest 50 are kept for reading. The Proofread stage and the chapter summaries appear in the same places as the drafts.

#### Scenario: Two tabs

- **WHEN** Translating is shown during a translation
- **THEN** the live card shows the tabs `Live` and `Activity log`, with `Live` selected

#### Scenario: Problems only

- **WHEN** the Activity log holds 40 lines of which 3 are problems, and `Problems only` is chosen
- **THEN** only those 3 lines are listed, and `All` lists the 40 again

#### Scenario: Proofread and summary entries are in the Activity log

- **WHEN** the Activity log is opened after chapter 3 was summarised and the window of paragraphs 40 to 42 was proofread
- **THEN** it lists an entry of the kind chapter summary and an entry of the kind proofread

#### Scenario: The last 50 calls

- **WHEN** a translation has made 120 calls and `All calls…` is used
- **THEN** the progress dialog lists calls 120 down to 71, newest first, and no older call

#### Scenario: The call still at work

- **WHEN** `All calls…` is used while call 16, a Translate call of 7 segments, still waits for its answer, and call 15 was answered with their translations
- **THEN** call 16 reads `Working on Translate.` and `Nothing yet.`, not done
- **AND** call 15 reads `Translate is done.` with the translations it brought back under What it says

### Requirement: Show a first-launch state on Projects when none exist

WHILE no project exists, the application SHALL show on Projects "Translate a book. Private when you use a local AI model.", How it works in four steps (choose a book · check the languages · let it translate · save the translated book, with "your original file is never changed"), the line with the result of the first-launch look, and "+ New translation". While the look runs the line reads `Looking for an AI program on this computer…` with `Try again` visible but disabled; a screen that shows no look this session still offers `Try again` under `No AI model found`. When several models are found the person is asked which one to use; while that question is open the line still reads `Looking for an AI program on this computer…` and never says a program is not open, and the navigation footer reads `Looking for an AI model…`; dismissing that question with `Not now` shows the `No AI model chosen yet` line (the footer reads `No AI model chosen`) and `Try again` asks again. The words `(found on this computer when BookLoom first opened)` appear only in the session whose first-launch look found the model, and are not stored. It SHALL hide search and the filter, and show "+ New translation" with a list too.

**Source**: `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`, `docs/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`, the owner's decision that privacy is stated as "private when used with local models". In plain words: the first screen a new person sees must say what the program does, what to press, and whether the AI model it needs was found. It must not promise that a cloud model keeps the book on this computer, so the tagline says "private when you use a local AI model". The look itself is specified by llm-provider ("Look once for a local AI program at first launch").

#### Scenario: An empty list

- **WHEN** the application starts for the first time
- **THEN** Projects shows the four steps, the AI-model line and `+ New translation`, and no search or filter

#### Scenario: The tagline does not claim a cloud model is local

- **WHEN** the first-launch Projects screen is shown
- **THEN** it shows `Translate a book. Private when you use a local AI model.` and does not say the book is translated "on your own computer"

#### Scenario: The model was found

- **WHEN** Ollama with `gemma4:e4b` was found when BookLoom first opened
- **THEN** the AI-model line reads `AI model: Ollama · gemma4:e4b — ready ✓ (found on this computer when BookLoom first opened)`

#### Scenario: Nothing was found

- **WHEN** no program answered at the first launch
- **THEN** the line reads `No AI model found`, says `Ollama is not open. Open the Ollama app, then press Try again.` and offers `Try again` and `I use another program…`, and no error dialog is opened

### Requirement: Call a model call stuck after a minute and show one clock

WHILE an attempt of a model call has had no answer for 60 seconds or more, the application SHALL show the Stuck call state, saying the model is "taking longer than usual", and SHALL show one clock: that attempt's elapsed time against that call's own time limit, for example "3:40 of 6:00".

**Source**: `docs/specification/mockups/README.md#run-states-a` (Stuck call), `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (Call line). In plain words: a slow model should look slow, not broken. One clock, counting the attempt against its own limit, says how long it has waited and when BookLoom will try again, with no second figure to reconcile. It applies to every kind of call, a proofread call as much as a draft.

#### Scenario: One clock against the call's own limit

- **WHEN** a proofread call whose time limit is 4:00 has waited 1:30 on its attempt
- **THEN** the chip reads `No answer for 1:30` and the call line shows the one clock `1:30 of 4:00`

#### Scenario: Not stuck before a minute

- **WHEN** an attempt has had no answer for 59 seconds
- **THEN** the state is Translating and no stuck-call banner is shown, and at 60 seconds the state is Stuck call

#### Scenario: A new attempt starts its clock again

- **WHEN** the stuck attempt reaches its limit and BookLoom tries again
- **THEN** the clock restarts at `0:00` for the new attempt and the state returns to Translating until 60 seconds pass without an answer

### Requirement: Continue an existing translation and never call it a start

Resuming SHALL mean continuing a translation that already exists for the book. WHILE it is paused, Resume SHALL continue it in place; WHILE it is stopped, interrupted by a restart or failed, "Continue translation" SHALL continue the stage that still has work (Prepare, Translate or Proofread), keeping what is already decided. A continuation SHALL never be labelled "Start", and the title bar's control SHALL do exactly what the toolbar's control of the same name does.

**Source**: FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `docs/specification/mockups/README.md#run-states-a`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: Start is for a book with nothing done; Resume and Continue always carry something on. A person who closed the application overnight sees Continue translation and "Ready to continue", not a button that suggests starting over, and the run picks up the stage that was not finished, whether that is drafting or the second reading.

#### Scenario: The title bar's Resume does what the screen's does

- **WHEN** the run over `earth-gravity.epub` is paused and `Resume` is used on the title bar
- **THEN** the paused run continues, exactly as the toolbar's `Resume` does

#### Scenario: A reopened project says Continue

- **WHEN** a project with 74 of 142 segments done is opened after a restart
- **THEN** the toolbar offers `Continue translation` and nothing is labelled `Start`

#### Scenario: Continue picks up the stage with work left

- **WHEN** a project whose 142 segments are all drafted but whose proofread of 6 marked windows stopped after 2 is opened after a restart
- **THEN** the toolbar offers `Continue translation` and the run continues with the Proofread stage

#### Scenario: Resume is not offered while the run is running

- **WHEN** the run over `earth-gravity.epub` is running
- **THEN** no Resume control is offered, and none labelled `Start`

#### Scenario: Resume for a paused run, Continue translation for a stopped one

- **WHEN** the run is paused, paused after an error, or stopped
- **THEN** `Resume` is offered on the toolbar and the title bar for the first two, and `Continue translation` for a stopped run

## REMOVED Requirements

### Requirement: Answer Enter, Escape and Tab the same way in every dialog

**Reason**: The export's last check no longer exists, so the scenario that Escape hides the export's last check cannot hold; the requirement is restated without it.
**Migration**: "Answer Enter, Escape and Tab alike in every dialog" replaces it.

### Requirement: Estimate before Start only from a measured speed

**Reason**: The estimate is now the planned calls of each kind times the seconds per call measured on the model, the relative-cost fallback ("Balanced about 1.5 times Fast") is dropped because the cost is now told in calls and in measured seconds, and the estimate line sits on the Book Brief, so one capability owns it.
**Migration**: Replaced by the book-brief capability's "State a time estimate on the brief as measured or as an estimate", which holds the measured-per-kind figure, the labelled range from a measured draft speed and no time when nothing was measured.
