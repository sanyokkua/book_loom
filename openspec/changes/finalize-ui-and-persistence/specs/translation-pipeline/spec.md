## ADDED Requirements

### Requirement: Always show the scene summary under the live detail

The Translating screen's live card SHALL always show, under its detail, the current scene summary and where it comes from: "Updated every 20 segments. On Max quality the model also writes one at the end of each chapter."; WHERE no summary exists yet, it SHALL say so rather than hide the line.

**Source**: docs/specification/mockups/README.md (Step 4 · Translating, Scene summary), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T5). In plain words: the summary the model carries from segment to segment is part of why a translation reads as it does, so the person can always see it and learn how often it is refreshed; writing it more often belongs to the pipeline redesign, not to this change.

#### Scenario: The line shows the summary and its source

- **WHEN** the summary after 40 decided segments reads "Ada returns to the harbor." on a Balanced translation
- **THEN** the live card shows that summary and the line "Updated every 20 segments. On Max quality the model also writes one at the end of each chapter."

#### Scenario: No summary yet is stated

- **WHEN** 12 segments are decided and no summary has been written
- **THEN** the line is still shown and says that no summary exists yet

### Requirement: Explain sleep and keep-awake in a closable help line

The Translating screen SHALL show once per project a closable help line: "BookLoom keeps the computer awake while it translates. Closing a laptop lid still puts it to sleep — after sleep, translating goes on by itself. Nothing finished is lost." and, WHILE keep-awake is off in Settings, instead "The computer may sleep while translating — turn on Keep awake in Settings."

**Source**: docs/specification/mockups/README.md (Step 4 · Translating, Help line), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T-series help line; sleep sentence to be verified once with a real sleep). In plain words: a person who closes a laptop lid mid-run should know what happens; the sentence about continuing after sleep ships only after one real laptop sleep has been tried.

#### Scenario: The help line is shown once and can be closed

- **WHEN** the person opens Translating on `Harbor Lights · EN → UK` for the first time and closes the help line
- **THEN** it does not appear again for that project

#### Scenario: Keep-awake off changes the sentence

- **WHEN** Keep awake is off in Settings and the help line is shown
- **THEN** it reads "The computer may sleep while translating — turn on Keep awake in Settings."

### Requirement: Count segments in the text lines and steps in the bar

The translating screen SHALL count segments in its text lines ("74 of 142 segments done", a segment kept as it is counting as done) and SHALL show the share of all the work in steps on the bar and in the title bar ("51 % done", tooltip "140 of 276 steps, checks included"), never printing the two as competing percentages.

**Source**: docs/specification/mockups/README.md (Step 4 · Translating, Summary strip; G10 point 5), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T2, PR1–PR3). In plain words: the text says how many segments are done in words a person understands, and the bar says how much of the whole work is done including checks.

#### Scenario: Text and bar differ on purpose

- **WHEN** 74 of 142 segments are done and 140 of 276 steps are done
- **THEN** the text line reads "74 of 142 segments done", the bar and title bar read "51 %" and the bar's tooltip reads "140 of 276 steps, checks included"

### Requirement: Refuse to start a run with a missing target language or model

The translating screen SHALL refuse to start when no project is open, no target language is chosen, or no model is chosen, and SHALL say which is missing; a run SHALL start with no source language chosen (Auto), and its prompts then name the source as "the language of this segment (infer it from its text)" (ADR-0042).

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, ADR-0042. In plain words: a run that cannot work is refused at once with the reason, while a missing source language is not a reason to refuse.

#### Scenario: A missing target language is named

- **WHEN** a project is open with a model chosen and no target language
- **THEN** starting is refused with a message naming the missing target language

### Requirement: Derive each segment count from the engine's progress snapshot

Each segment count on the translating screen SHALL be derived from the progress snapshot the engine emits: pending never includes a segment kept as source by choice, the segments done are the translated, fixed automatically, to review and kept-as-is counts together, and a total of zero SHALL show zero rather than a division.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), docs/specification/mockups/README.md (Words). In plain words: the numbers a person reads are the engine's own counts, with the plain names the screen uses.

#### Scenario: Kept as source is not pending

- **WHEN** the engine reports 449 pending, 7 of the book's segments being kept as source by choice
- **THEN** the screen's pending count is 449 and the 7 are not counted as left

### Requirement: Offer Simple and Detailed views in a list and detail split

The live card SHALL offer Simple and Detailed views in a list | detail split that never stacks; Simple SHALL show the selected segment's marker and location, a bordered Source | Translation grid and "Being translated now" with the call's sources, and Detailed SHALL add the call's figures and the parsed prompt and reply.

**Source**: docs/specification/mockups/README.md (Step 4 · Translating, List | detail split, Simple detail, Detailed detail; G5), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T1, T3). In plain words: a person who only wants to watch sees plain sentences, and one who wants the technical truth sees the call itself.

#### Scenario: Detailed adds the call's figures

- **WHEN** the person switches from Simple to Detailed
- **THEN** the detail adds the call line "Call 42 · Draft · 4 segments · ch2 · p05–p08 · attempt 1 of 2 · 0:21 of 3:00" and the parsed prompt and reply

### Requirement: Send a batch reply's per-segment results to the rows

WHEN the reply to a batch is parsed, each segment's result SHALL reach its row, so that a segment the batch lacked reads "translated again alone".

**Source**: docs/specification/mockups/README.md (G5, Replies), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T1, T4). In plain words: a batch answers for several segments at once, and each row must show what happened to its own segment.

#### Scenario: A segment the batch lacked is translated again alone

- **WHEN** a batch reply for segments 5–8 lacks the id of segment 7 and segment 7 is drafted alone
- **THEN** the row of segment 7 reads "translated again alone" with the tooltip "the batch reply lacked this id"

### Requirement: Follow the newly translated segment until the person picks another

WHILE "Show each segment when translated" is on, the live card SHALL select the first newly translated segment when a reply arrives, and SHALL stop following only when the person clicks or keys to another row, offering "Follow again".

**Source**: docs/specification/mockups/README.md (Step 4 · Translating, Show each segment when translated), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T1). In plain words: the card keeps up with the translation until the person wants to read something else, and never loses their place by itself.

#### Scenario: Only a click turns following off

- **WHEN** the person clicks the row of `ch7 · p41` while "Show each segment when translated" is on
- **THEN** the selection stays there as later segments are translated, and "Follow again" is offered

### Requirement: Show the finished state with Review and Continue to Export

WHEN a translation completes, the screen SHALL show its outcome with Review (n) and Continue to Export in the toolbar and the banner "Translation finished. 6 segments need your review. You can export now and review later." with "Review 6 segments", or "Translation finished. Nothing needs your review." when nothing needs it.

**Source**: docs/specification/mockups/README.md (Run states, Finished rows), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (A). In plain words: after a run the screen says what happened and where to go next.

#### Scenario: Finished with nothing to review

- **WHEN** a translation completes with 0 segments to review
- **THEN** the banner reads "Translation finished. Nothing needs your review." and the toolbar offers Continue to Export

### Requirement: Show the failed state and continue at the first pending segment

WHEN a translation ends Failed, the screen SHALL show "Did not finish" with Continue translation and Review (n) in the toolbar and the banner "The translation stopped because of an error. Finished segments are saved." with Show details; Continue translation SHALL begin a new run at the project's first pending segment, a start and not a resume.

**Source**: docs/specification/mockups/README.md (Run states, Failed), FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), ADR-0043. In plain words: after an error nothing finished is lost, and the person can carry on from where the run stopped.

#### Scenario: Failed offers Continue translation

- **WHEN** a translation ends Failed after 412 of 1,240 segments were decided
- **THEN** the toolbar offers Continue translation, and it begins a run whose first model call is for the 413th segment

### Requirement: Show a stuck call with one clock

WHILE a call has been outstanding longer than usual for its kind, the screen SHALL show the amber chip "No answer for 3:40", the banner "The model is taking longer than usual (3:40). BookLoom tries again by itself at 6:00. You do not need to do anything.", and ONE clock "3:40 of 6:00".

**Source**: docs/specification/mockups/README.md (Run states, Stuck call; Step 4 · Translating, Call line), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T2, A). In plain words: a slow model should look slow, not broken, and the person is told nothing needs doing.

#### Scenario: A stuck call shows one clock

- **WHEN** a draft call has waited 3 minutes 40 seconds and its retry is due at 6 minutes
- **THEN** the chip reads "No answer for 3:40", the banner reads "The model is taking longer than usual (3:40). BookLoom tries again by itself at 6:00. You do not need to do anything.", and the call line shows the single clock "3:40 of 6:00"

### Requirement: Announce the planned steps and each step's state

The engine SHALL announce the steps it plans, by kind, and each step's change of state as it starts and ends, so that the screen's progress counts the run's own work.

**Source**: docs/specification/mockups/README.md (G4, G10), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (PR1–PR3). In plain words: the progress bar and time left can only be as honest as the engine's announcement of its own work.

#### Scenario: The plan is announced before the work

- **WHEN** a Balanced translation of 3 segments starts
- **THEN** a plan of 3 drafts and 3 checks is announced, and each step later announces its state as it starts and ends

### Requirement: Announce each batch item's result

WHEN a batch reply is parsed, the engine SHALL announce each item's result (translated, missing or unusable) for its segment.

**Source**: docs/specification/mockups/README.md (G5), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T1, T4). In plain words: the rows and the reply grid need the per-segment answer, not only that a batch came back.

#### Scenario: A batch reply announces each item's result

- **WHEN** a draft batch for `Book.md:0` to `Book.md:3` is answered with a reply that lacks the id of `Book.md:2`
- **THEN** three items are announced as translated, `Book.md:2` is announced as missing, and its row reads "translated again alone" once it is drafted alone

### Requirement: Classify an unloaded-model reply as modelUnavailable

A chat reply of HTTP `400` or `404` whose body says the server has no model loaded — LM Studio's `Model unloaded`, `Model is unloaded`, `No models loaded`, or a model "not loaded" — SHALL be classified `ErrorCode.modelUnavailable`, not `validation`.

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`. In plain words: loading the model fixes it, which the resume capability's "Recover from a provider error by itself" waits for, so it must not look like a bad request.

#### Scenario: An unloaded model is recognised

- **WHEN** LM Studio answers `400` with the body `{"error":"Model unloaded"}`
- **THEN** the call's error is `ErrorCode.modelUnavailable`

### Requirement: End the job Failed on an internal error and never pause

IF the model call throws or answers `ErrorCode.internal`, or any code the run's failure requirements do not name (`busy` and `discoveryFailed` included), or restoring the markup fails with an error other than `validation`, THEN the system SHALL leave the segment and every later one PENDING and end the job Failed with `ErrorCode.internal`, whether or not pause on error is enabled.

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, `#boundary-discipline`. In plain words: an internal error is a bug that pressing Try again now cannot fix, so it ends the run with a dialog rather than pausing it.

#### Scenario: A thrown exception ends the job

- **WHEN** pause on error is enabled and the model call throws an exception for the first segment
- **THEN** the job ends Failed with `ErrorCode.internal` and does not pause

### Requirement: End the job Cancelled when the model answers cancelled

IF the model answers `ErrorCode.cancelled`, THEN the system SHALL end the job Cancelled; a `validation` refusal made before any model call, for a run that cannot start, SHALL be answered as that refusal and not as a pause.

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`. In plain words: a stop by the person is not a failure, and a run that never started is not paused.

#### Scenario: A cancelled call ends the job Cancelled

- **WHEN** the model answers `ErrorCode.cancelled` for the second segment
- **THEN** the job ends Cancelled with that segment PENDING

## RENAMED Requirements

- FROM: `### Requirement: Resolve the review mode once at launch`
- TO: `### Requirement: Read the review mode at the start of each run`

## MODIFIED Requirements

### Requirement: Estimate the time left and the time spent

WHILE a run translates, the system SHALL count its work as steps — a draft per segment, the checks the dial plans for each translated segment (Fast 0, Balanced 1, Max 2), the repairs and re-asks added as they start, and on Max the chapter summaries and the backward revision — and SHALL report the time spent without the time paused, the time left following the shared progress rule of the app-shell capability.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, docs/specification/mockups/README.md (G10), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (PR1–PR3). In plain words: a slow stretch used to be projected over the whole rest of the book; the run now reports every kind of step it does, and the shared rule turns them into a trustworthy figure.

#### Scenario: An estimate after five segments

- **WHEN** a Fast translation of 105 segments has decided 5 drafts in 12 seconds each and 100 drafts remain
- **THEN** the time left is "about 20 min"

#### Scenario: Only the last twenty segments count

- **WHEN** 29 drafts took 12 seconds each, the 30th took 252 seconds, and 100 drafts remain on Fast
- **THEN** the time left is based on the mean of all 30 drafts, 20 seconds, and reads "about 33 min", not on the last 20

#### Scenario: No estimate too early

- **WHEN** 4 steps of 276 are done
- **THEN** no time left is reported
- **AND** at 5 steps it is

#### Scenario: A pause does not count as elapsed

- **WHEN** a run translates for 10 minutes, is paused for 3 and translates for 5 more
- **THEN** the elapsed time is 15 minutes

#### Scenario: Steps are counted by quality dial

- **WHEN** a book of 3 translated segments is run on Fast, Balanced and Max
- **THEN** the planned drafts and checks are 3 + 0, 3 + 3 and 3 + 6, and on Max the chapter summaries and the backward revision are planned as well

#### Scenario: Added work joins the total

- **WHEN** a Balanced run of 3 segments plans 6 steps and a directed fix starts
- **THEN** the total becomes 7 steps when the fix starts

### Requirement: Start a run and report its progress

The translating screen SHALL start a run for the open project, as briefed, with the chosen model, and WHILE it is in progress SHALL report the time spent and left, tokens per second, the segment counts translated, fixed automatically, to review and kept as is, the live card and a log of what the run has decided.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, docs/specification/mockups/README.md (Step 4 · Translating, Summary strip), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T2), ADR-0042, ADR-0043. In plain words: the dashboard shows the run's throughput and counts in plain words; progress is saved as it goes, so the old warning to keep the application open is gone.

#### Scenario: Counts advance as segments are decided

- **WHEN** the engine reports `700` translated, `68` fixed automatically, `3` to review, `20` kept as is and `449` pending in chapter 7 of 11
- **THEN** the screen shows the counts `700`, `68`, `3` and `20`, and the line "791 of 1,240 segments done · Chapter 7 of 11"

#### Scenario: The proportion ignores the chapter indices

- **WHEN** the engine reports the same counts while the section is `7` of `11`
- **THEN** the bar shows its share of steps, not `7` of `11`, and its tooltip counts steps ("140 of 276 steps, checks included")

#### Scenario: An empty book does not divide by zero

- **WHEN** the engine reports every count as `0`
- **THEN** the screen reports zero and does not fail

#### Scenario: A run cannot start without a model

- **WHEN** a project is open, the target language is `uk`, and no model has been chosen
- **THEN** starting is refused with a message naming the missing model, and no run begins

#### Scenario: A run starts without a source language

- **WHEN** the plain-text book `notes.txt`, which declares no language, is open, the target language is `uk`, a model is chosen and no source language has been chosen (Auto)
- **THEN** the run begins, and every prompt it sends names the source as "the language of this segment (infer it from its text)"

#### Scenario: Starting a run says to keep the application open

- **WHEN** the person presses Start translation and the run begins
- **THEN** one transient message titled `Translation started` is raised, and it does not say that progress is kept only until the application closes

#### Scenario: The log records each decision

- **WHEN** the segment `ch7 · p42` is accepted during a run
- **THEN** an entry naming `ch7 · p42` and its outcome is appended to the log

### Requirement: Show the segment just decided and the one in progress in the live panel

WHILE a run translates, the live card SHALL list the segments of the call in flight in reading order, one line each with formatting marks left out and the full text in the tooltip, each with a marker: waiting, translating…, translated, to review, translated again alone, or kept as is.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`, docs/specification/mockups/README.md (Step 4 · Translating, Live card; G11), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T1–T4). In plain words: a book takes hours, and watching sentences go by is how a person judges the model before the run ends; the card shows every segment of the call with its state instead of two rows.

#### Scenario: The decided segment above the one in progress

- **WHEN** a Fast call covers `ch7 · p41` and `ch7 · p42`, `ch7 · p41` is decided as `Вона втратила матір.`, and the draft of `ch7 · p42` has not arrived
- **THEN** the rows read `ch7 · p41` translated and `ch7 · p42` translating…, and Simple detail for `ch7 · p41` shows its source and translation

#### Scenario: A draft waits for the reviewer

- **WHEN** on a Balanced run the draft of `ch7 · p42` has arrived and its check has not finished
- **THEN** its row still reads translating…, because the segment is not decided yet

#### Scenario: A decision moves the segment up

- **WHEN** `ch7 · p42` is accepted
- **THEN** its row reads translated, and "Show each segment when translated" selects it unless the person clicked another row

#### Scenario: The second row waits for the draft

- **WHEN** a segment has started and its draft is not ready
- **THEN** its detail reads "Being translated now…" under the source, with "Not translated yet." in the translation column

#### Scenario: The second row is empty between segments

- **WHEN** the last segment of a call is decided and the next call has not started
- **THEN** the rows of the finished call stay, all marked translated, until the next call's segments arrive

#### Scenario: Rows show display text from the announcements

- **WHEN** a row is shown
- **THEN** it holds display text only, taken from the run's announcements, with formatting marks left out and the full text with marks `⟦1⟧` in the tooltip

#### Scenario: A paused run shows waiting rows

- **WHEN** the run is paused while segments 5–8 are in flight
- **THEN** their rows read waiting

### Requirement: Show the Translating screen's idle, completed and failed states

WHILE no translation has started for the open project, the Translating screen SHALL show the Ready state — a ready card, the live line "Nothing translated yet." and the toolbar's Start translation.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), FR-RESUME-05 (`#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#error-dialog`, docs/specification/mockups/README.md (Run states), ADR-0035. In plain words: the screen always says what can happen next; the finished and failed states are stated beside this one.

#### Scenario: Idle shows the ready card

- **WHEN** `Harbor Lights` is open, briefed from `en` to `uk` on Balanced, the model `gemma4:26b` is chosen, the review mode is Unattended, 1,240 segments are pending and no translation has started
- **THEN** the screen shows a ready card naming `Harbor Lights`, `gemma4:26b`, Unattended, Balanced and 1,240 pending segments, and the live line "Nothing translated yet."
- **AND** the toolbar offers Start translation and no Pause, Stop or Resume

#### Scenario: Completed points to Export

- **WHEN** a translation completes with 1,180 translated, 45 fixed automatically, 6 to review and 12 kept as is, leaving nothing pending
- **THEN** the toolbar offers Review (6) and Continue to Export and no Pause or Resume
- **AND** the banner reads "Translation finished. 6 segments need your review. You can export now and review later." with "Review 6 segments"

#### Scenario: Completed with pending segments offers a start

- **WHEN** a translation of a Markdown book completes with `Frontmatter values` switched off, and the person then switches `Frontmatter values` on, leaving the book's 3 frontmatter values pending
- **THEN** the toolbar offers Start translation beside Continue to Export
- **AND** Start translation begins a new run whose only model calls are for those 3 frontmatter values

#### Scenario: Failed shows the dialog, then offers a new start

- **WHEN** a translation ends Failed with `ErrorCode.internal` after 412 of 1,240 segments were decided
- **THEN** the title bar reads "Did not finish", the banner reads "The translation stopped because of an error. Finished segments are saved." with Show details, and the toolbar offers Continue translation
- **AND** Continue translation begins a new run whose first model call is for the 413th segment

#### Scenario: The ready card names what is missing

- **WHEN** no run has started and a model or a brief field is missing
- **THEN** the ready card shows the book, the model, the review mode, the quality dial and the pending count, and Start translation is disabled with a tooltip naming the missing model or brief field

#### Scenario: The outcome shows four counts

- **WHEN** a translation completes
- **THEN** the outcome shows the translated, fixed automatically, to review and kept-as-is counts

#### Scenario: The review button counts suspicious segments

- **WHEN** a translation completes with 6 segments to review and the final audit doubts 3 accepted segments
- **THEN** the toolbar button reads "Review (6)" and the outcome shows "3" under "worth a second look"

#### Scenario: The failed dialog keeps the decided segments

- **WHEN** a translation ends Failed
- **THEN** the dialog says that finished segments are saved, and does not say that decided segments are kept only until the application closes

### Requirement: Tell the person when the model is slow to answer

WHILE the run is running and a model call has been outstanding for at least `10` seconds, the live line SHALL read "Waiting for the model… m:ss", updated once a second and counting across the client's own retries, and the screen SHALL withdraw it when the segment is decided, a new call starts, or a pause or stop is requested.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, docs/specification/mockups/README.md (Run states). In plain words: a slow local model can make the counters stand still for minutes; ten seconds in the live line tells the person it is waiting, and the stuck state beside this one says when to worry.

#### Scenario: Nothing is said for a normal answer

- **WHEN** a model call has been outstanding for `9` seconds
- **THEN** the live line shows the plain line of the run state ("Now translating: Chapter 2, segments 5–8") and no waiting text

#### Scenario: The waiting text appears at ten seconds and counts on

- **WHEN** a model call has been outstanding for `10` seconds, and later for `12` seconds
- **THEN** the live line reads "Waiting for the model… 0:10" and then "Waiting for the model… 0:12", and in Ukrainian «Очікування відповіді моделі… 0:12»

#### Scenario: A client retry does not restart the count

- **WHEN** the draft call's first attempt timed out after `3` minutes and the client has sent its second attempt `5` seconds ago
- **THEN** the live line reads "Waiting for the model… 3:05", and not "Waiting for the model… 0:05"

#### Scenario: A decision withdraws it

- **WHEN** the live line shows "Waiting for the model… 0:12" and the segment is then decided
- **THEN** the live line shows the plain line of the run state again

#### Scenario: A pause request replaces it

- **WHEN** the live line shows "Waiting for the model… 0:12" and the person presses Pause
- **THEN** the screen shows "Pausing…" and never "Waiting for the model…" while the pause is pending

### Requirement: Read the review mode at the start of each run

WHEN a translation starts, the system SHALL take the review mode from the environment variable `BOOKLOOM_REVIEW_MODE`, or else the system property `bookloom.review.mode`, or else the stored Automation setting: `unattended` or `auto` is Unattended, `assisted` or `semi-manual` Assisted, `manual` Manual, in any case, else Unattended; a value naming no mode SHALL give Unattended and one WARN line naming it.

**Source**: FR-REVIEW-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, ADR-0036, ADR-0045, docs/specification/mockups/README.md (Settings, Automation). In plain words: the careful modes are now a saved choice in Settings, read when a translation starts; the launch flag still wins because the command line and the evaluations use it, and a typo must not silently change how the run behaves.

#### Scenario: A spelled-out alias is accepted

- **WHEN** a translation starts with `BOOKLOOM_REVIEW_MODE` set to `Semi-Manual`
- **THEN** the run uses Assisted

#### Scenario: The environment wins over the property

- **WHEN** `BOOKLOOM_REVIEW_MODE` is `auto` and `bookloom.review.mode` is `manual`
- **THEN** the run uses Unattended

#### Scenario: An unknown value falls back

- **WHEN** `BOOKLOOM_REVIEW_MODE` is `strict`
- **THEN** the run uses Unattended and the log holds one WARN line holding `strict`

#### Scenario: The stored setting applies without a flag

- **WHEN** neither the variable nor the property is set and the Automation setting stores Assisted
- **THEN** the run uses Assisted

#### Scenario: A flag overrides the stored setting

- **WHEN** the Automation setting stores Assisted and `BOOKLOOM_REVIEW_MODE` is `manual`
- **THEN** the run uses Manual

### Requirement: Announce each model call as it starts

WHEN the engine starts a model call, it SHALL emit one event naming the call's kind (draft, structural repair, placeholder repair, reviewer, directed fix, pre-scan, summary or revision) and its segments, before waiting for the answer, SHALL NOT emit it for a call refused because a pause or stop was requested, and SHALL NOT emit another for the client's own retries.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, docs/specification/mockups/README.md (G4, G5, G10). In plain words: between two segment decisions the engine may make several calls, and naming the kind lets the screen and the log say what the model is doing; the step announcements and batch results are stated beside this one.

#### Scenario: One event before each decision

- **WHEN** a two-segment book is translated on Fast and each segment is answered by its first call
- **THEN** the model-call events are, in order, a draft for `Book.md:0` before that segment's decision and a draft for
  `Book.md:1` before that segment's decision, and no export stage is announced

#### Scenario: A repair is its own call

- **WHEN** the first reply for `Book.md:0` is not the required JSON object and the structural repair call is answered
  correctly
- **THEN** a draft event and a structural-repair event for `Book.md:0` are emitted, one before each call, before that
  segment's decision

#### Scenario: The reviewer is announced by kind

- **WHEN** a Balanced run reviews the chunk holding `ch07.xhtml:40` and `ch07.xhtml:41`
- **THEN** one model-call event of kind review is emitted for that chunk

#### Scenario: The client's retry does not announce again

- **WHEN** the first attempt of the draft call for `Book.md:0` times out and the client sends its second attempt
- **THEN** exactly one model-call event for `Book.md:0` was emitted for that draft call, and none for the second
  attempt

#### Scenario: A refused call is not announced

- **WHEN** a pause is already requested when the engine is about to make a model call
- **THEN** the provider receives no request and no model-call event is emitted

### Requirement: Stop the job on any other failure

IF the model call, after the retry policy is exhausted, answers `unreachable`, `timeout`, `auth`, `rateLimited`, `upstream`, `modelNotFound`, `modelUnavailable`, `missingCredential` or `validation`, THEN the system SHALL leave the segment and every later one PENDING and, WHERE pause on error is enabled, pause with that error, otherwise end the job Failed with it.

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, `#boundary-discipline`, `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `docs/implementation_plan/CHANGE_BACKLOG.md` (D19). In plain words: an unreachable server, a missing key or an unloaded model would make every following call fail too. Flagging them used to finish a run with every remaining segment flagged; now the segment waits, and the run pauses so the person can fix the server and press Try again now. The command line pauses on an error like the window and recovers from an outage by itself, but stops a pause only a person could end and writes what it translated. An internal error is a bug in the application, which pressing Try again now cannot fix, so it ends the run with a dialog rather than pausing it; a code a run's call should never answer means the same. Only a reply the model actually produced, or a prompt too long for the model, can be flagged.

#### Scenario: An unreachable model pauses the run

- **WHEN** pause on error is enabled, a book has three segments, and the model answers `ErrorCode.unreachable` for the
  second after three attempts
- **THEN** the job pauses with `ErrorCode.unreachable`, with 1 accepted, 0 flagged and 2 pending

#### Scenario: An unloaded model flags nothing

- **WHEN** pause on error is enabled and LM Studio answers `400` with the body `{"error":"Model unloaded"}` for the second
  of 40 segments
- **THEN** the job pauses with `ErrorCode.modelUnavailable`, with 0 flagged and 39 pending

#### Scenario: An unreachable model fails the job

- **WHEN** pause on error is off, a book has three segments, and the model answers `ErrorCode.unreachable` for the second
- **THEN** the job ends Failed with `ErrorCode.unreachable`, with 1 accepted and 2 pending

#### Scenario: A thrown exception fails the job as internal

- **WHEN** pause on error is enabled and the model call throws an exception for the first segment
- **THEN** the job ends Failed with `ErrorCode.internal` and does not pause, and that segment stays PENDING

#### Scenario: A restore that fails unexpectedly fails the job

- **WHEN** pause on error is enabled and restoring the markup of the first segment's reply fails with
  `ErrorCode.internal`
- **THEN** the job ends Failed with `ErrorCode.internal` and does not pause, and that segment stays PENDING

#### Scenario: An unexpected code fails the job as internal

- **WHEN** pause on error is enabled and the model call for the first segment answers `ErrorCode.discoveryFailed`
- **THEN** the job ends Failed with `ErrorCode.internal` and does not pause, and that segment stays PENDING

#### Scenario: A cancelled model call cancels the job

- **WHEN** the model answers `ErrorCode.cancelled`
- **THEN** the job ends Cancelled

### Requirement: Tag every activity-log entry with one of seven kinds

Every entry the activity log holds SHALL carry one kind from a fixed vocabulary of seven, each shown by its tag, with
both a status role from the token catalogue and a mark that is not its colour. The run SHALL produce each kind at its
own event, and the text of every entry SHALL come from the message catalogue by key. The log SHALL retain its most
recent entries up to a fixed bound and SHALL drop the oldest first.

**Source**: FR-NOTIF-6a, FR-NOTIF-6b, FR-NOTIF-6c (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#activity-log`), FR-A11Y-6 (`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#accessibility`), FR-UI-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`). In plain words: the log is the only record of what a multi-hour run decided. A fixed vocabulary of seven short tags, lined up in a column, is what makes it scannable, and now that the engine repairs, remembers and summarises, every one of the seven actually appears. Colour alone cannot carry the kind, and the text comes from the catalogue so a Ukrainian window never shows an English log. An entry names a segment the way the review dialog does, by its locator, because a file name and an ordinal mean nothing to the person reading it.

#### Scenario: An accepted segment logs its kind and role

- **WHEN** the segment `ch7 · p42` is accepted during a run
- **THEN** an entry of kind accepted with the tag `ok` is appended, referring to the success role, which resolves to
  `#5f8a6b` under the light values
- **AND** it carries a mark distinguishing it from a segment-error entry without relying on colour

#### Scenario: A repair round logs fix

- **WHEN** the segment `ch07.xhtml:41`, whose locator is `ch7 · p42`, goes through one directed fix
- **THEN** an entry tagged `fix` naming `ch7 · p42`, and not `ch07.xhtml:41`, is appended, and its tag is rendered
  before its text in a monospace face

#### Scenario: A format repair and a resume after a provider error log retry

- **WHEN** the segment `ch7 · p43` gets one placeholder repair, and later the run pauses on `ErrorCode.unreachable` and
  the person presses Try again now
- **THEN** an entry tagged `retry` naming `ch7 · p43` is appended for the repair, the pause is logged as `info`, and the
  resume appends a second `retry` entry

#### Scenario: Entry text comes from the catalogue

- **WHEN** the accepted entry for the segment `ch7 · p42` is rendered while the interface is in Ukrainian
- **THEN** its text resolves from a catalogue key with `ch7 · p42` substituted into it, and is not an English sentence
  with the locator joined to it

#### Scenario: Each kind maps to its own status role

- **WHEN** the seven kinds are read
- **THEN** accepted refers to the success role; repaired, glossary-applied, summary-updated and milestone refer to
  the information role; retried refers to the warning role; and segment-error refers to the danger role

#### Scenario: The log drops its oldest entries

- **WHEN** a run appends more entries than the log's bound
- **THEN** the log holds exactly the bound, and the entry appended first is no longer present

#### Scenario: The seven kinds and their tags

- **WHEN** the activity log holds entries
- **THEN** each carries one kind, shown by its tag: accepted `ok`, repaired `fix`, glossary or memory applied `mem`,
  summary updated `sum`, retried `retry`, segment error `err` and milestone `info`

#### Scenario: Entries are monospace with the tag first

- **WHEN** the activity log renders an entry
- **THEN** it is rendered in a monospace face with the tag first, and its kind is readable without seeing the colour

#### Scenario: Each event produces its kind

- **WHEN** a run lives through its events
- **THEN** `ok` is logged when a segment is accepted; `fix` for each repair call — a directed fix; `retry` for a
  structural or placeholder format repair and for the resume that follows a provider-error pause; `mem` when a name
  scan adds glossary entries or a segment is reused from the translation memory; `sum` when the rolling summary is
  refreshed; `err` when a segment is flagged; and `info` for a run milestone — a stage start, a pause (including a
  provider-error pause), a resume or the finish

#### Scenario: A provider client's own retry makes no entry

- **WHEN** the provider client retries inside one call
- **THEN** the run does not see it and logs no entry

#### Scenario: An entry names the segment by its locator

- **WHEN** an entry about a segment is written
- **THEN** its text comes from the catalogue with the segment's locator, such as `ch7 · p42`, never its id, and any
  other value substituted into it
