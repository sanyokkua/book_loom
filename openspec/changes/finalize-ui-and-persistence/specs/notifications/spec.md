## ADDED Requirements

### Requirement: Stack transient messages in the bottom-right corner and time them by severity

The application SHALL show transient messages stacked in the window's bottom-right corner, 16 pixels from the edges and 360 pixels wide, newest at the bottom, at most four at once. A success or information message SHALL stay 5 seconds; a warning, an error and any message that carries an action SHALL stay 12 seconds. Each SHALL have a close mark and at most one action.

**Source**: `docs/specification/mockups/README.md#g2--toasts-in-the-bottom-right-corner`, FR-NOTIF-01 (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#toasts`). In plain words: the bottom-centre spot sat on the buttons of dialogs and of the toolbar's neighbours. In the corner a message is seen but covers nothing that is pressed, and the longer times give a person time to read the ones that matter or can be acted on.

#### Scenario: Placement and size

- **WHEN** one transient message is shown in a window 1280 pixels wide
- **THEN** it is 360 pixels wide with its right edge 16 pixels from the window's right edge and its bottom 16 pixels from the bottom

#### Scenario: A fifth message pushes the oldest out

- **WHEN** four messages are showing and a fifth is raised
- **THEN** four are showing, the newest at the bottom, and the oldest is gone

#### Scenario: Times by severity

- **WHEN** a success message `Book opened` and a warning message `Could not read the model list` are raised together
- **THEN** the success message is gone after 5 seconds and the warning after 12 seconds

#### Scenario: An action lengthens the stay

- **WHEN** an information message that carries the action `Show progress` is raised
- **THEN** it stays 12 seconds and shows exactly one action beside its close mark

### Requirement: Report a dialog's own feedback in its footer

WHILE a dialog is open or model work is visible, the application SHALL show feedback about what happens in it as one line in that dialog's own footer and SHALL NOT raise a transient message for it.

**Source**: `docs/specification/mockups/README.md#g2--toasts-in-the-bottom-right-corner`. In plain words: a message in the corner would land on the dialog's buttons or be missed behind its scrim, and the person is looking at the footer anyway. This also replaces the toast that used to confirm an accepted review segment, because review is now a dialog.

#### Scenario: Accepting in the review desk answers in the footer

- **WHEN** the segment `ch5 · p12` is accepted in the review desk and 2 remain
- **THEN** the desk's footer line reads `Accepted — ch5 · p12 saved. 2 left.` and no transient message is raised

#### Scenario: The same event outside a dialog is a message

- **WHEN** a hidden names pass finishes while no dialog is open
- **THEN** a transient message is raised, not a footer line

### Requirement: Raise a transient message only on the occasions the application names

The application SHALL raise a transient message only when a book is opened, a provider test passes, a translation starts or finishes, a project's automatic name changes, a retry is refused, a model list cannot be read, hidden work finishes, or other model work is refused while work is hidden. Each occasion has its severity and wording in the scenarios.

**Source**: FR-NOTIF-01 (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#toasts`), `docs/specification/mockups/README.md#g2--toasts-in-the-bottom-right-corner`, `docs/specification/mockups/README.md#project-names--one-book-several-translations-pn`. In plain words: a message with no named caller is built, tested for its colour, and then raised from wherever a screen felt like. The list is closed. The old start message told a person to keep the application open because progress was lost on closing; progress is now saved as it goes, so it says so instead.

#### Scenario: The automatic name changes

- **WHEN** the target language of the project `Frankenstein · EN → …` is set to Ukrainian and the name was automatic
- **THEN** one information message reads `Project name changed to Frankenstein · EN → UK`

#### Scenario: Hidden work finishes

- **WHEN** a names pass that was continued in the background ends
- **THEN** one success message is raised with the action `Review the results`, and nothing opens by itself

#### Scenario: Other model work is refused while work is hidden

- **WHEN** `Fill in from the book` is used while the names pass runs in the background
- **THEN** no work starts and one warning message is raised with the action `Show progress`

#### Scenario: Starting a translation says progress is saved

- **WHEN** a translation of `Frankenstein.epub` is started
- **THEN** one information message titled `Translation started` is raised reading `Progress is saved as you go.`
- **AND** the text `progress is kept only until it closes` appears nowhere

#### Scenario: A retry refused while translating is a warning

- **WHEN** a review retry is asked for while a translation is running
- **THEN** one warning message says the retry waits for the translation

#### Scenario: A provider test that passes

- **WHEN** a provider passes every stage it was tested on
- **THEN** one success message is raised

#### Scenario: A model list that cannot be read

- **WHEN** the list of models cannot be read
- **THEN** one warning message is raised and no dialog opens

#### Scenario: A finished translation names how many need review

- **WHEN** a translation ends with `1237` segments translated and `3` that need review
- **THEN** one warning message is raised that reads `3 segments need your review`

### Requirement: Tell a person the AI model cannot be reached while waiting for it by itself

WHILE a paused translation waits for the AI model by itself, the application SHALL show a warning banner that names the program, says when BookLoom tries again, and offers "Try again now", "Skip these 4 segments" (always the real count) and "Open settings".

**Source**: FR-NOTIF-02 (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#banners`), `docs/specification/mockups/README.md#run-states-a`. In plain words: the commonest failure by far is that the model program was closed. The banner says which program, when the next try is, and what a person can do, in words that need no knowledge of servers or providers.

#### Scenario: The wait is explained

- **WHEN** Ollama stops answering while segments 5–8 are being translated, and the next try is in 12 seconds
- **THEN** a warning banner reads `BookLoom cannot reach Ollama — Is the Ollama app open? BookLoom tries again by itself in 0:12.` and offers `Try again now`, `Skip these 4 segments` and `Open settings`

#### Scenario: A person who paused the wait gets the plain paused banner

- **WHEN** the person pauses while the wait is running
- **THEN** the banner reads `Paused. Ollama could not be reached. Open Ollama, then press Resume.` and the toolbar offers `Resume` and `Stop`

## MODIFIED Requirements

### Requirement: Carry four severities of transient message, coloured by the status roles

The application SHALL show transient messages in exactly four severities — success, information, warning and error — each coloured by its matching status role. A transient message SHALL NOT be the only place a blocking failure is reported, and SHALL be raised only on the occasions that "Raise a transient message only on the occasions the application names" lists. An export being written raises none; the export screen reports it.

**Source**: FR-NOTIF-01 (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#toasts`), FR-A11Y-8, FR-THEME-10 (`docs/specification/01_Product/09_THEMING.md#status-colours`), `docs/specification/mockups/README.md#g2--toasts-in-the-bottom-right-corner`. In plain words: four severities, four status roles, one mapping, so the colour means the same thing on a chip, a banner and a message. A blocking failure always gets a dialog as well, because a message is gone in seconds and somebody looking away has then lost the only report there was.

#### Scenario: Each severity takes its status role

- **WHEN** an error-severity transient message is shown under the light values
- **THEN** its foreground refers to the danger role, which resolves to `#b0574c`
- **AND** a success-severity message refers to the success role, which resolves to `#5f8a6b`

#### Scenario: A blocking failure is never transient-only

- **WHEN** a blocking failure is surfaced
- **THEN** a dialog is shown, whether or not a transient message is also shown

#### Scenario: Opening a book raises one success message

- **WHEN** `Frankenstein.epub` is opened successfully
- **THEN** one transient message of success severity is raised naming the book

#### Scenario: Starting a run raises one information message

- **WHEN** a run over `Frankenstein.epub` is started
- **THEN** one transient message of information severity titled `Translation started` is raised

#### Scenario: Accepting in review names the segment and what is left

- **WHEN** the flagged segment `ch5 · p12` is accepted in the review desk and 2 remain
- **THEN** the desk's footer line reads `Accepted — ch5 · p12 saved. 2 left.` and no transient message is raised

#### Scenario: A run with flagged segments warns rather than congratulates

- **WHEN** a run completes with `1237` translated and `3` that need review
- **THEN** one transient message of warning severity is raised

#### Scenario: A refusal raises no transient message of its own

- **WHEN** a book is refused because its inspection finds it DRM-protected
- **THEN** the refusing state is shown on the screen and no transient message is raised, because the screen is already reporting it

### Requirement: Report a provider failure as the run's own state

IF a run meets a failure that this capability assigns to the provider-error state, THEN the run SHALL pause with its counts kept, and a banner SHALL say in plain words what happened and name the program (for example Ollama), with the progress bar in the danger role and the actions "Try again now", "Skip these 4 segments" and "Open settings". IF a run ends on `ErrorCode.internal`, THEN the run is Failed and a blocking dialog reports it, not a banner.

**Source**: FR-NOTIF-02 (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#banners`), FR-NOTIF-04c (`#typed-error-surface`), `docs/specification/02_Architecture/09_ERROR_HANDLING.md#ui-surfacing`, ADR-0036, `docs/specification/mockups/README.md#run-states-a`. In plain words: a stopped model server is not a reason to lose the translation or to flag half a chapter. The run pauses, says what went wrong and where, and offers what a person might want: try again now that it is back, fix the settings, or skip the segments that keep failing. The work is saved as it goes, so the banner says that finished segments are saved, not that they are lost when the window closes.

#### Scenario: An unreachable server names itself

- **WHEN** the translation of `Frankenstein.epub` is at 62 % and nothing is listening at `http://localhost:11434` after the retries
- **THEN** the translation is paused and the banner names `Ollama` and says finished segments are saved
- **AND** the progress bar is drawn in the danger role, which resolves to `#b0574c` under the light values
- **AND** no dialog is opened

#### Scenario: The counts survive the failure

- **WHEN** a translation that had `412` segments translated is paused by a provider error
- **THEN** the screen still reports `412` translated

#### Scenario: A provider's refusal is a pause too

- **WHEN** the provider answers `ErrorCode.validation` to a call for `ch7 · p41`
- **THEN** the translation is paused and the same banner is shown, not a dialog

#### Scenario: An unloaded model shows the provider-error state

- **WHEN** LM Studio answers `400` `{"error":"Model unloaded"}` for `ch7 · p41` and the run pauses with `ErrorCode.modelUnavailable`
- **THEN** the banner reads `The AI model gemma4:e4b is not available — check that it is installed.` and offers `Choose another model…`, and the toolbar offers `Try again now` and `Stop`
- **AND** no dialog is opened

#### Scenario: An internal failure is not a provider error

- **WHEN** the run throws while it prepares, before its first segment
- **THEN** the run ends Failed with `ErrorCode.internal` and the blocking dialog is shown
- **AND** no provider-error banner is shown

#### Scenario: The run waits for the provider visibly

- **WHEN** the run paused on `ErrorCode.unreachable` waits 4 min 12 s for its sixth try
- **THEN** a warning banner reads `BookLoom cannot reach Ollama — Is the Ollama app open?` and says when the next try is, and the toolbar offers `Pause` and `Stop`

#### Scenario: After twelve hours the banner hands the run to the person

- **WHEN** Ollama has not answered for 12 hours and BookLoom gave up after 77 tries
- **THEN** an error banner reads `Ollama has not answered for 12 hours — BookLoom stopped trying by itself.` and the toolbar offers `Try again now` and `Stop`

#### Scenario: Retry now redoes the interrupted segment

- **WHEN** the translation was paused by `ErrorCode.unreachable` while translating segments 5–8, Ollama is opened again, and `Try again now` is used
- **THEN** the translation resumes and the next request sent is the first request for segments 5–8

#### Scenario: Skipping names the count

- **WHEN** the banner is shown for 4 interrupted segments
- **THEN** the button reads `Skip these 4 segments`, and using it flags those 4 segments for review and carries on

#### Scenario: Staying paused keeps the run

- **WHEN** the person uses `Pause` while the run waits for Ollama by itself
- **THEN** the run stays paused, BookLoom stops trying by itself, and `Resume` is offered

#### Scenario: Open provider settings goes to the providers area

- **WHEN** `Open settings` is used on the banner
- **THEN** Settings is shown on its Providers tab and the translation stays paused
