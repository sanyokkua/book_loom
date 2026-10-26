## ADDED Requirements

### Requirement: Make the brief read-only while a translation is going on

WHILE the run state of the open project is Translating, Stuck call, Waiting for the AI program, Pausing or Stopping, the Book Brief SHALL be read-only and SHALL show the banner "Pause the translation to change the brief. Changes apply to segments not translated yet." with a Pause button; in every other run state, Paused included, it SHALL be editable except the languages, which "Lock the languages once translating has started" governs.

**Source**: docs/specification/mockups/README.md#changes-while-translating-i, docs/specification/mockups/README.md#run-states-a, docs/specification/mockups/README.md#step-2--book-brief--b1b3-i, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (open question 7, recorded default: read-only until a pause). In plain words: the engine rereads the brief's switches only when it resumes, so an edit made mid-run would look accepted and change nothing; the screen therefore waits for a pause and says so.

#### Scenario: The brief is read-only during a translation

- **WHEN** the translation of `Earth Gravity · EN → UK` is running and the person opens the Book Brief
- **THEN** every control is disabled and the banner reads "Pause the translation to change the brief. Changes apply to segments not translated yet." with a Pause button

#### Scenario: Pausing makes it editable again

- **WHEN** the person presses Pause in that banner and the translation reaches Paused
- **THEN** the controls are enabled and the banner is gone

#### Scenario: The brief stays read-only while stopping

- **WHEN** the person presses Stop and the run state is Stopping
- **THEN** every control of the Book Brief is still disabled and the banner is still shown

#### Scenario: Waiting for the AI program keeps it read-only

- **WHEN** the run is waiting for the AI program and retries by itself
- **THEN** every control of the Book Brief is disabled and the banner is shown, because an edit then would be as lost as while translating

#### Scenario: A paused run with decided segments keeps only the languages locked

- **WHEN** the translation is Paused with 74 decided segments
- **THEN** the register, the names policy and the "Also translate" switches can be changed, and Translate from and Translate into are disabled

### Requirement: Lock the languages once translating has started

WHILE a project has started translating — it holds at least one decided segment or a run is in progress — the Book Brief SHALL lock both language choices and SHALL point to "Translate into another language" on Projects for another target.

**Source**: docs/specification/mockups/README.md#changes-while-translating-i, docs/specification/mockups/README.md#step-2--book-brief--b1b3-i. In plain words: changing the languages under finished segments would mix two translations in one book, so a new target is a new project.

#### Scenario: Languages are locked after the first decided segment

- **WHEN** the project `Earth Gravity · EN → UK` has 74 decided segments, is paused, and the Book Brief is shown
- **THEN** the Translate from and Translate into choices are disabled with the hint "To translate into another language, use Translate into another language on Projects"

#### Scenario: Languages are free before translating

- **WHEN** a new project has no decided segment and no run
- **THEN** both language choices can be changed

### Requirement: Show the project name on the brief and let the person rename it

The Book Brief's languages card SHALL show a Project name line reading "automatic — follows the languages" while the name is automatic, with a Rename… action, and an automatic name SHALL follow the chosen languages as `<title> · <SRC> → <TGT>`, with no arrow until a target is chosen (`<title> · <SRC>`, or only the title while no source is known).

**Source**: docs/specification/mockups/README.md#project-names--one-book-several-translations-pn, docs/specification/mockups/README.md#step-2--book-brief--b1b3-i, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (PN1). In plain words: the person sees what the project is called and why, and a name they typed is never overwritten by a language change.

#### Scenario: The automatic name follows a language change

- **WHEN** the project `Earth Gravity · EN` has the target changed to Ukrainian
- **THEN** the Project name line reads "automatic — follows the languages" and the project is named `Earth Gravity · EN → UK`
- **AND** a toast says that the name changed

#### Scenario: A typed name is shown as the person's own

- **WHEN** the project was renamed to `Earth Gravity, 26b`
- **THEN** the Project name line shows `Earth Gravity, 26b` with the words "named by you" and the same Rename… link, and the title bar shows the same name

#### Scenario: The line shows the stored name

- **WHEN** the Book Brief is shown for a stored project
- **THEN** the Project name line and the title bar show the name the project is stored under, never a name worked out on the screen

#### Scenario: A typed name never changes

- **WHEN** the person renamed the project to `Earth Gravity, 26b` and then changes the target from Ukrainian to Polish
- **THEN** the project is still named `Earth Gravity, 26b`

### Requirement: Open Same language pair when the chosen pair is already used

IF the person chooses a source and target that another project of the same book already uses, THEN the Book Brief SHALL open the dialog "Same language pair" naming that project, with "Open that project" and "Make a second <language> translation" that requires a name of its own.

**Source**: docs/specification/mockups/README.md#project-names--one-book-several-translations-pn, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (PN2). In plain words: two projects with the same pair would be indistinguishable, so the person either goes to the existing one or names a deliberate second one.

#### Scenario: A used pair opens the dialog

- **WHEN** `Earth Gravity · EN → UK` exists and the person sets the target of a second project of the same book to Ukrainian
- **THEN** the dialog "Same language pair" names `Earth Gravity · EN → UK` and offers "Open that project" and "Make a second Ukrainian translation"

#### Scenario: Keep and Esc keep the previous pair

- **WHEN** the dialog is open and the person presses "Keep Ukrainian" (or "Keep the current choice" when no language was chosen before) or Esc
- **THEN** the two language boxes show the previous pair again and nothing is stored

#### Scenario: Open that project discards the edit

- **WHEN** the person presses "Open that project"
- **THEN** the other project opens and the edit of the first one is discarded

#### Scenario: Only a second translation keeps the new pair

- **WHEN** the person chooses "Make a second Ukrainian translation" with a name that is unused
- **THEN** the brief and that name are stored together and the brief keeps the new pair

#### Scenario: The second translation needs its own name

- **WHEN** the person chooses "Make a second Ukrainian translation" and the name field is empty
- **THEN** the primary button is disabled, and it becomes enabled once the name is filled in and unused (for example `Earth Gravity · 26b`)

### Requirement: State a time estimate on the brief only when it is measured

The Quality and speed card SHALL show an estimate line, such as "For this book on this computer: about 2 h 30 min (estimate from the model test)", only when a speed in seconds per segment was measured for the chosen model by the inference test or an earlier run, and otherwise SHALL show the relative cost of the choices; a speed in tokens per second alone SHALL NOT give an estimate.

**Source**: docs/specification/mockups/README.md#step-2--book-brief--b1b3-i, docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (PR2). In plain words: a made-up number for hours of work misleads, so a time appears only when the computer was actually measured, and otherwise the card says how the choices compare.

#### Scenario: A measured speed gives an estimate

- **WHEN** the inference test measured 3.0 seconds per segment for the chosen model
- **THEN** the card shows an estimate line "For this book on this computer: about … (estimate from the model test)", worked out from the speed, the book's segment count and the chosen quality (Fast, Balanced or Max)

#### Scenario: Tokens per second alone give no estimate

- **WHEN** the title bar showed 9.4 tok/s during a test, but no seconds per segment were measured for the chosen model
- **THEN** the card shows no time and shows the relative cost of the choices

#### Scenario: No measurement gives relative cost

- **WHEN** no inference test and no earlier run exist for the chosen model
- **THEN** the card shows no time and says Balanced costs about 1.5 times Fast and Max about 3 times

### Requirement: Show the brief suggestion in a progress dialog that is not hideable

The brief suggestion SHALL run in a progress dialog whose Steps group lists what the model knows about the book (not listed when the title is the file name), reading sample 1 to K (K = 3, 4 or 5 for Fast, Balanced or Max, fewer when the story is too short for K windows), and deciding genre, readers and voice; Esc SHALL cancel it and it SHALL NOT be hideable.

**Source**: docs/specification/mockups/README.md#dialog--fill-in-the-brief-from-the-book-g4-short--b1b3, docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (B1–B3). In plain words: the suggestion is short work, so the person watches its steps and can stop it, but it is not pushed into the background.

#### Scenario: The steps follow the dial and skip unknown knowledge

- **WHEN** the person presses "✦ Fill in from the book" on Balanced for a book whose title is only its file name
- **THEN** the dialog lists no "what the model knows" step, then Read sample 1 to Read sample 4 with their location ranges, then Decide genre, readers and voice

#### Scenario: A known title adds the knowledge step

- **WHEN** the person presses "✦ Fill in from the book" on Balanced for a book with a known title
- **THEN** the dialog lists what the model knows about the book, Read sample 1 to Read sample 4 and Decide genre, readers and voice (1 + 4 + 1 steps)

#### Scenario: Esc cancels the suggestion

- **WHEN** the person presses Esc in the dialog before it has ended
- **THEN** the suggestion is cancelled and the brief is unchanged

### Requirement: Show what the suggestion decides with its agreement and verified quote

The dialog's "What it decides" group SHALL list four rows, genre, register, who tells the story and who reads it with the voice, each with its value so far and its agreement ("3 of 4 samples agree"); selecting one SHALL show each sample's answer with its verified quote, and a value without a quote SHALL NOT be used.

**Source**: docs/specification/mockups/README.md#dialog--fill-in-the-brief-from-the-book-g4-short--b1b3, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (B2, B3). In plain words: the person can see why each field has its value and read the sentence of the book that supports it.

#### Scenario: A field shows its agreement and quote

- **WHEN** on Balanced three of four samples answer `formal` for the register, each with a quote found in its sample
- **THEN** "What it decides" shows Register `Formal` with "3 of 4 samples agree", and selecting it shows each sample's answer with its quote

### Requirement: Write nothing to the brief until the person applies the suggestion

The application SHALL write nothing to the brief until the person presses "Apply to the brief", which SHALL be offered when all steps have ended, and Cancel SHALL leave the brief unchanged.

**Source**: docs/specification/mockups/README.md#dialog--fill-in-the-brief-from-the-book-g4-short--b1b3, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (B1). In plain words: a model's suggestion is only a proposal; the brief changes when the person says so.

#### Scenario: Nothing is written until Apply

- **WHEN** all steps have ended and the person closes the dialog with Esc before pressing "Apply to the brief"
- **THEN** the suggestion is cancelled and the brief is unchanged

#### Scenario: A failed or stopped suggestion offers no Apply

- **WHEN** the model cannot answer or the person stops the suggestion
- **THEN** the dialog closes, no "Apply to the brief" is offered, the brief is unchanged and the card says "The suggestion was stopped. The brief is unchanged." after a stop

#### Scenario: The language proposal and the evidence are kept only on Apply

- **WHEN** the person closes the dialog without pressing "Apply to the brief"
- **THEN** no evidence line and no proposed source language is kept, and both appear only after Apply

#### Scenario: Apply writes the decided fields

- **WHEN** all steps have ended and the person presses "Apply to the brief"
- **THEN** the decided genre, register, narrator and readers are written to the brief and the dialog closes

### Requirement: Judge the narration before the Start question when the import could not

WHEN Start translation is pressed and the open book's narration was not judged when it was opened, because the book declares no language (Markdown, plain text) and its source was chosen afterwards, the application SHALL first judge it from the text with the source language of the stored brief with no model call and no write, and SHALL then decide the `Who narrates?` question on that judgement. Enter SHALL choose `Not stated — start anyway`, as its text says.

**Source**: `openspec/specs/book-brief/spec.md` "Ask who narrates once at Start translation". In plain words: a first-person Markdown or text book was judged only by the run, after the Start question, so the question never came and the narrator's gender went unasked; an EPUB that declares its language was asked.

#### Scenario: A first-person Markdown book is asked about

- **WHEN** `Harbour.md`, told as "I" with `Anna` named 9 times and no gender in the glossary, is opened, its source set to English and its target to Ukrainian, and Start translation is pressed
- **THEN** `Who tells the story?` is shown with "Enter chooses Not stated.", Enter closes it without a gender, and the question about characters without a gender names `Anna` before anything starts

### Requirement: Cap the free-text fields every call carries

The Book Brief SHALL take at most 100 characters of genre, 200 of audience and 500 of narrative voice and era notes; typing stops at the cap and a paste keeps only what fits.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`. In plain words: these fields go into every model call of the run, so a pasted page would be sent thousands of times; each cap is far more than a genre, an audience or a note on the voice needs.

#### Scenario: A pasted page is cut to the cap

- **WHEN** 600 characters are pasted into Audience and into Narrative voice / era notes
- **THEN** Audience holds 200 characters and the notes 500, and the brief stores exactly those

## MODIFIED Requirements

### Requirement: Suggest the tone and style from verified readings, one decision per field

WHEN the person presses "✦ Fill in from the book", the application SHALL ask what the model knows of the book, read several samples with one `brief-suggestion` call each, ask one `brief-synthesis` call over the facts and the samples' verified readings, and decide each field once; a failed sample counts as no reading, and a stop leaves the brief unchanged.

**Source**: FR-BRIEF-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`); tasks 15h.G1, 15i.B2–B4 (`openspec/changes/archive/2026-10-19-complete-translation-workflow/additional-work.md`); `docs/specification/01_Product/12_PROMPT_CATALOG.md#brief-synthesis`; docs/specification/mockups/README.md#dialog--fill-in-the-brief-from-the-book-g4-short--b1b3. In plain words: one sample can be an odd stretch of a book and a model's memory can be wrong, so a field is kept only when most samples or checked knowledge say it.

#### Scenario: Three of four windows decide the register

- **WHEN** three of four windows answer `casual` with quotes found in them and the fourth answers `formal`
- **THEN** the suggested register is `Casual`, shown as "3 of 4 samples agree"

#### Scenario: A split register stays neutral and uncertain

- **WHEN** two windows answer `casual` and two answer `formal`
- **THEN** the suggested register is `Neutral` and shown as uncertain

#### Scenario: One window alone does not decide the genre

- **WHEN** the synthesis names `Mystery` citing one of four windows and the model does not know the book
- **THEN** the genre stays empty and is shown as uncertain

#### Scenario: A cited window that read another genre is no support

- **WHEN** the synthesis names `Thriller` citing two windows that read `fantasy` and `romance`
- **THEN** the genre stays empty and is shown as uncertain

#### Scenario: One failed window does not fail the suggestion

- **WHEN** one of four window calls fails and the other three answer
- **THEN** the suggestion is made from the three, the failed window counting against every majority

#### Scenario: A stop during the synthesis changes nothing

- **WHEN** the person stops the suggestion while the synthesis call is running
- **THEN** the suggestion ends as cancelled and the brief is unchanged

#### Scenario: Knowledge that agrees keeps the audience

- **WHEN** the model's accepted knowledge says `adult readers` and the synthesis names the audience `adult` on that
  knowledge
- **THEN** the audience is `adult`, shown as from what the model knows of this book

#### Scenario: The detector's person outweighs the windows

- **WHEN** the detector reads the book as third person and every window answers `first`
- **THEN** the suggested narrator is third person

#### Scenario: Under Auto the windows' language is only proposed

- **WHEN** the source language is Auto and three of four windows say the passage is French
- **THEN** the suggestion proposes French and the brief's source language stays Auto

#### Scenario: Knowledge is accepted only by its echo check

- **WHEN** the model answers what it knows of the book
- **THEN** the answer is accepted only when the title (also by its part before `;` or `:`) and the author (in either name order) match the metadata, and the year is no later than five after a metadata year, because that year is often an edition's

#### Scenario: The number of windows follows the dial

- **WHEN** the suggestion is made on Fast, Balanced or Max for a story long enough
- **THEN** 3, 4 or 5 single windows of the story are read, of at most 1,500, 2,500 or 4,000 tokens each

#### Scenario: A short story gets fewer windows

- **WHEN** the suggestion is made on Balanced for a story of 6,000 tokens
- **THEN** 2 windows are read, because 6,000 tokens hold only two windows of 2,500 tokens; a book with any story text gets at least 1

#### Scenario: An answer is kept only with a quote found in its window

- **WHEN** a window's answer is read
- **THEN** it is kept only with a quote found in its window: a register's quote in the window's narration, outside the characters' speech, and a genre or an audience never without a quote

#### Scenario: No synthesis for an unknown book that no window read

- **WHEN** the book is unknown to the model and no window read genre, audience or voice
- **THEN** no `brief-synthesis` call is made, because nothing could stand

#### Scenario: The suggestion fails only when stopped or when every window fails

- **WHEN** the person stopped it, or every window call failed
- **THEN** the suggestion fails, whereas a failed knowledge question or synthesis only leaves its fields uncertain

#### Scenario: A stop during the knowledge question changes nothing

- **WHEN** the person stops the suggestion while the knowledge question is running
- **THEN** the suggestion ends as cancelled and the brief is unchanged


### Requirement: Refuse a brief whose source and target are the same language

IF the chosen source language and the chosen target language are the same, THEN the book-brief screen SHALL show the message `The source and target languages are the same.` beside the language fields, SHALL keep `Continue` unavailable until they differ, and SHALL NOT store the brief while they are the same, so the project keeps its stored pair and its name.

**Source**: FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`. In plain words: a run like that would spend hours asking a model to translate a book into the language it is already in. Storing the pair renamed the project to `X · EN → EN` and announced it, so a refused pair is now only shown; any other choice made meanwhile is stored with the next pair that differs.

#### Scenario: The same language on both sides is refused

- **WHEN** the source is `English` and `English` is chosen as the target
- **THEN** the message `The source and target languages are the same.` is shown and `Continue` is unavailable

#### Scenario: Changing either side clears the refusal

- **WHEN** that brief's target is changed to `Ukrainian`
- **THEN** the message disappears and `Continue` becomes available

#### Scenario: A refused pair is never stored

- **WHEN** the project `Night · EN → UK` has its target changed to `English`, its genre set to `Noir`, and its target changed back to `Ukrainian`
- **THEN** the stored target stays `uk` throughout, the project stays named `Night · EN → UK` with no message about a new name, and the genre `Noir` is stored with the last change

## REMOVED Requirements

### Requirement: Say so when no book is open on the book brief

**Reason**: The five workflow steps are locked until a project is open, so the Book Brief can no longer be reached without one and the "open a book first" state has no way to appear.

**Migration**: The locked step in the navigation, with the hint "Steps unlock when a project is open", replaces it (the `app-shell` capability); the Projects screen is where a person starts or opens a project.
