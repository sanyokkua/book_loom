## ADDED Requirements

### Requirement: Always show the scene summary under the live detail

The Translating screen's live card SHALL always show, under its detail, the current scene summary — its target-language text, else its source text — and where it comes from, in one wording on every quality: "Updated after every 20 accepted segments and at the end of each chapter. On Max quality the model writes the one at the end of each chapter."; WHERE no summary exists yet, it SHALL read "No scene summary yet." rather than hide the line.

**Source**: `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (Scene summary), `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (T5), "Keep a rolling summary of the book so far". In plain words: the summary the model carries from segment to segment is part of why a translation reads as it does, so the person can always see it and how often it is refreshed. On every quality it is refreshed after 20 accepted segments and at each chapter's end; only on Max is the chapter-end one written by the model. Writing it more often belongs to the pipeline redesign, not to this change.

#### Scenario: The line shows the summary and its source

- **WHEN** the summary after 40 accepted segments reads "Mira Holt returns to Velk." on a Balanced translation
- **THEN** the live card shows that summary and the line "Updated after every 20 accepted segments and at the end of each chapter. On Max quality the model writes the one at the end of each chapter."

#### Scenario: No summary yet is stated

- **WHEN** 12 segments are accepted, no chapter has ended and no summary has been written
- **THEN** the line is still shown and reads "No scene summary yet."

### Requirement: Explain sleep and keep-awake in a closable help line

WHILE a translation is running, the Translating screen SHALL show once per project a closable help line: "BookLoom keeps the computer awake while it translates. Closing a laptop lid still puts it to sleep — after sleep, translating goes on by itself. Nothing finished is lost." and, WHILE keep-awake is off in Settings, instead "The computer may sleep while translating — turn on Keep awake in Settings."

**Source**: `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (Help line). In plain words: a person who closes a laptop lid mid-run should know what happens; the README marks the sentence about continuing after sleep as to be verified once with a real laptop sleep before it ships.

#### Scenario: The help line is shown once and can be closed

- **WHEN** the person opens Translating on `Earth Gravity · EN → UK` for the first time and closes the help line
- **THEN** it does not appear again for that project

#### Scenario: Keep-awake off changes the sentence

- **WHEN** Keep awake is off in Settings and the help line is shown
- **THEN** it reads "The computer may sleep while translating — turn on Keep awake in Settings."

### Requirement: Refuse to start a run with a missing target language or model

The translating screen SHALL refuse to start when no project is open, no target language is chosen, or no model is chosen, and SHALL say which is missing; a run SHALL start with no source language chosen (Auto), and its prompts then name the source as "the language of this segment (infer it from its text)" (ADR-0042).

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, ADR-0042. In plain words: a run that cannot work is refused at once with the reason, while a missing source language is not a reason to refuse.

#### Scenario: A missing target language is named

- **WHEN** a project is open with a model chosen and no target language
- **THEN** starting is refused with a message naming the missing target language

### Requirement: Derive each segment count from the engine's progress snapshot

Each segment count on the translating screen SHALL be derived from the progress snapshot the engine emits: pending never includes a segment kept as source by choice, the segments done are the translated, fixed automatically, to review and kept-as-is counts together, and a total of zero SHALL show zero rather than a division.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/mockups/README.md#words-w--user-facing-vocabulary`. In plain words: the numbers a person reads are the engine's own counts, with the plain names the screen uses. How the bar and the text lines show them is the app-shell capability's.

#### Scenario: Kept as source is not pending

- **WHEN** the engine reports 449 pending, 7 of the book's segments being kept as source by choice
- **THEN** the screen's pending count is 449 and the 7 are not counted as left

### Requirement: Offer Simple and Detailed views in a list and detail split

The live card SHALL offer Simple and Detailed views in a list | detail split that never stacks; Simple SHALL show the selected segment's marker and location, a bordered Source | Translation grid and "Being translated now" with the call's sources, and Detailed SHALL add the call's figures and the parsed prompt and reply.

**Source**: `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (List | detail split, Simple detail, Detailed detail), `docs/specification/mockups/README.md#g5--parsed-prompts-and-replies-in-detailed-views-only`, `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (T1, T3). In plain words: a person who only wants to watch sees plain sentences, and one who wants the technical truth sees the call itself.

#### Scenario: Detailed adds the call's figures

- **WHEN** the person switches from Simple to Detailed
- **THEN** the detail adds the call line "Call 42 · Draft · 4 segments · ch2 · p05–p08 · attempt 1 of 2 · 0:21 of 3:00" and the parsed prompt and reply

### Requirement: Send a batch reply's per-segment results to the rows

WHEN the reply to a batch is parsed, each segment's result SHALL reach its row, so that a segment the batch could not be used for reads "translated again alone" from its solo draft on, even while its chunk's check runs, and "to review" if it is then flagged; its tooltip SHALL name the fault (a missing id, a duplicate id, a merged answer). An extra id the batch never asked for has no row.

**Source**: `docs/specification/mockups/README.md#g5--parsed-prompts-and-replies-in-detailed-views-only`, `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (T1, T4). In plain words: a batch answers for several segments at once, and each row must show what happened to its own segment.

#### Scenario: A segment the batch lacked is translated again alone

- **WHEN** a batch reply for segments 5–8 lacks the id of segment 7 and segment 7 is drafted alone
- **THEN** the row of segment 7 reads "translated again alone" with the tooltip "the batch reply lacked this id"

#### Scenario: The tooltip names the fault

- **WHEN** the batch reply answered the id of segment 6 twice, and ran segment 8 into the next one
- **THEN** their rows' tooltips read "the batch reply answered this id twice" and "the batch reply ran this segment into the next one"; an extra id shows only in Detailed's reply grid as "extra"

#### Scenario: A batch of four with one missing

- **WHEN** a batch for `ch7 · p41`–`p44` answers every id but that of `ch7 · p43`, which is drafted alone, and all four are accepted
- **THEN** the rows read translated, translated, translated again alone, translated

### Requirement: Follow the newly translated segment until the person picks another

WHILE "Show each segment when translated" is on, the live card SHALL select the first newly translated segment when a reply arrives, and SHALL stop following only when the person clicks or keys to another row, offering "Follow again".

**Source**: `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (Show each segment when translated), `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (T1). In plain words: the card keeps up with the translation until the person wants to read something else, and never loses their place by itself.

#### Scenario: Only a click turns following off

- **WHEN** the person clicks the row of `ch7 · p41` while "Show each segment when translated" is on
- **THEN** the selection stays there as later segments are translated, and "Follow again" is offered

### Requirement: Show a stuck call with one clock

WHILE an attempt of a model call has had no answer for 60 seconds or more, the screen SHALL show the amber chip "No answer for m:ss", the banner "The model is taking longer than usual (m:ss). BookLoom tries again by itself at m:ss. You do not need to do anything.", and ONE clock reading the time waited of the attempt's time limit.

**Source**: `docs/specification/mockups/README.md#run-states-a` (Stuck call), `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (Call line), `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (T2, A). In plain words: a slow model should look slow, not broken, and the person is told nothing needs doing. A minute without an answer is when the screen calls the call stuck; the time it tries again is the attempt's own time limit, which for a draft grows with the answer it expects.

#### Scenario: A stuck call shows one clock

- **WHEN** a draft attempt whose time limit is 6 minutes has had no answer for 3 minutes 40 seconds
- **THEN** the chip reads "No answer for 3:40", the banner reads "The model is taking longer than usual (3:40). BookLoom tries again by itself at 6:00. You do not need to do anything.", and the call line shows the single clock "3:40 of 6:00"

#### Scenario: Under a minute is not stuck

- **WHEN** a draft attempt has had no answer for 59 seconds
- **THEN** no amber chip and no stuck banner are shown

### Requirement: Announce the planned steps and each step's state

The engine SHALL announce the steps it plans, by kind, as "Plan a run's steps and count the time spent" defines them, the steps it adds as they start, and each step's change of state as it starts and ends, so that the screen's progress counts the run's own work.

**Source**: `docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1`, `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (PR1–PR3). In plain words: the progress bar and time left can only be as honest as the engine's announcement of its own work.

#### Scenario: The plan is announced before the work

- **WHEN** a Balanced translation of 3 segments packed into one chunk starts
- **THEN** a plan of 3 drafts and 1 check is announced, and each step later announces its state as it starts and ends

### Requirement: Announce each batch item's result

WHEN a batch reply is parsed, the engine SHALL announce each item's result (translated, missing or unusable) for its segment.

**Source**: `docs/specification/mockups/README.md#g5--parsed-prompts-and-replies-in-detailed-views-only`, `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (T1, T4). In plain words: the rows and the reply grid need the per-segment answer, not only that a batch came back.

#### Scenario: A batch reply announces each item's result

- **WHEN** a draft batch for `Book.md:0` to `Book.md:3` is answered with a reply that lacks the id of `Book.md:2`
- **THEN** three items are announced as translated, `Book.md:2` is announced as missing, and its row reads "translated again alone" once it is drafted alone

### Requirement: Plan a run's steps and count the time spent

WHEN a chunk is entered, the system SHALL plan one draft step per segment to translate — a segment kept verbatim being a draft step done at once with no call — and the dial's checks for the chunk (Fast 0, Balanced 1, Max 2); it SHALL add a step when a repair round, a batch re-ask or a resend after a timeout starts, plan Max's chapter summaries and backward revision, and count the time spent without the time paused.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (PR1–PR3). In plain words: the reviewer reads a whole chunk at once, so a check is a step per chunk, not per segment. A planned check that turns out not to be needed — no pair of the chunk qualifies for the reviewer, or Max's second pass after an unreadable first verdict — is done at once with no call. How the steps become the bar, the percentage and the time left, and that the bar never moves back, is the app-shell capability's.

#### Scenario: A twelve-segment unit on Balanced

- **WHEN** a Balanced run enters a unit of 12 segments, 2 of them kept verbatim, packed into 2 chunks of 8 and 4
- **THEN** 12 draft steps are planned, the 2 verbatim ones done at once with no call, and 2 check steps are planned

#### Scenario: Steps are counted by quality dial

- **WHEN** one chunk of 3 segments to translate is entered on Fast, Balanced and Max
- **THEN** the planned drafts and checks are 3 + 0, 3 + 1 and 3 + 2, and on Max the chapter summaries and the backward revision are planned as well

#### Scenario: Added work joins the total

- **WHEN** a Balanced run of 3 segments in one chunk plans 4 steps and a directed fix starts
- **THEN** the total becomes 5 steps when the fix starts

#### Scenario: A check with nothing to read is done at once

- **WHEN** a Balanced chunk of 4 segments has no pair that qualifies for the reviewer
- **THEN** its check step is done at once and no reviewer call is made

#### Scenario: A pause does not count as elapsed

- **WHEN** a run translates for 10 minutes, is paused for 3 and translates for 5 more
- **THEN** the elapsed time is 15 minutes

### Requirement: Start a translation and report its progress

The translating screen SHALL start a run for the open project, as briefed, with the chosen model, and WHILE it is in progress SHALL report the time spent and left, tokens per second, the segment counts translated, fixed automatically, to review and kept as is, the live card and a log of what the run has decided.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (Summary strip), `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (T2), ADR-0042, ADR-0043. In plain words: the dashboard shows the run's throughput and counts in plain words; progress is saved as it goes, so the old warning to keep the application open is gone.

#### Scenario: Counts advance as segments are decided

- **WHEN** the engine reports `700` translated, `68` fixed automatically, `3` to review, `20` kept as is and `449` pending in chapter 7 of 11
- **THEN** the screen shows the counts `700`, `68`, `3` and `20`, and the line "791 of 1,240 segments done · Chapter 7 of 11"

#### Scenario: The proportion ignores the chapter indices

- **WHEN** the engine reports the same counts while the section is `7` of `11`
- **THEN** the bar shows its share of steps, not `7` of `11`

#### Scenario: An empty book does not divide by zero

- **WHEN** the engine reports every count as `0`
- **THEN** the screen reports zero and does not fail

#### Scenario: A run cannot start without a model

- **WHEN** a project is open, the target language is `uk`, and no model has been chosen
- **THEN** starting is refused with a message naming the missing model, and no run begins

#### Scenario: A run starts without a source language

- **WHEN** the plain-text book `notes.txt`, which declares no language, is open, the target language is `uk`, a model is chosen and no source language has been chosen (Auto)
- **THEN** the run begins, and every prompt it sends names the source as "the language of this segment (infer it from its text)"

#### Scenario: Starting a run says progress is saved

- **WHEN** the person presses Start translation and the run begins
- **THEN** one transient message titled `Translation started` is raised reading `Progress is saved as you go.`, and it does not say that progress is kept only until the application closes

#### Scenario: The log records each decision

- **WHEN** the segment `ch7 · p42` is accepted during a run
- **THEN** an entry naming `ch7 · p42` and its outcome is appended to the log

### Requirement: End the run Failed on an error no pause can fix

IF a model call answers `busy` or `discoveryFailed`, or `emptyCompletion` or `contextWindow` on a call that does not flag its segment for it, or an error happens outside every step, THEN the system SHALL leave the segment and every later one PENDING and end the job Failed with `ErrorCode.internal`; WHERE pause on error is off, any other model-call error SHALL end it Failed with that error. IF the model answers `cancelled`, THEN the job SHALL end Cancelled.

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, `#boundary-discipline`, `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `docs/implementation_plan/CHANGE_BACKLOG.md` (D19). In plain words: every other model-call error — a provider error, a rejected request, or a step that threw — pauses the run, as the resume capability's "Pause instead of failing when an error stops the job" says; a draft that answers `emptyCompletion` or `contextWindow` flags its segment ("Flag a segment whose reply cannot be used, and continue"). A reply of HTTP `400` or `404` saying the server has no model loaded (such as LM Studio's `Model unloaded` or `No models loaded`) is classified `ErrorCode.modelUnavailable`, not `validation`, so it pauses. A `validation` refusal before any model call, for a run that cannot start, is answered as that refusal, not as a pause.

#### Scenario: An unexpected code fails the job as internal

- **WHEN** pause on error is enabled and the model call for the first segment answers `ErrorCode.discoveryFailed`
- **THEN** the job ends Failed with `ErrorCode.internal` and does not pause, and that segment stays PENDING

#### Scenario: A failure while the run starts ends it Failed

- **WHEN** the glossary store throws while the run prepares, before any segment
- **THEN** the job ends Failed with `ErrorCode.internal` and the message "An unexpected error stopped the run before it began."

#### Scenario: An unreachable model fails the job when pausing is off

- **WHEN** pause on error is off, a book has three segments, and the model answers `ErrorCode.unreachable` for the second
- **THEN** the job ends Failed with `ErrorCode.unreachable`, with 1 accepted and 2 pending

#### Scenario: An unloaded model is recognised and pauses

- **WHEN** pause on error is enabled and LM Studio answers `400` with the body `{"error":"Model unloaded"}` for the second of 40 segments
- **THEN** the call's error is `ErrorCode.modelUnavailable`, the job pauses rather than failing, and 0 segments are flagged and 39 pending

#### Scenario: A cancelled model call cancels the job

- **WHEN** the model answers `ErrorCode.cancelled` for the second segment
- **THEN** the job ends Cancelled with that segment PENDING

## RENAMED Requirements

- FROM: `### Requirement: Resolve the review mode once at launch`
- TO: `### Requirement: Read the review mode at the start of each run`

## MODIFIED Requirements

### Requirement: Map the quality dial to the run's mechanics

The system SHALL set the run's mechanics from the Book Brief's quality dial, and from nothing else:

| Mechanic | Fast | Balanced | Max |
|---|---|---|---|
| preceding targets | 1 | 2 | 3 |
| repair rounds per failing segment | 1 | 2 | 3 |
| reviewer passes per chunk | 0 | 1 | 2 |
| backward revision | off | off | on |
| segments per chunk, at most | 8 | 8 | 2 |

WHERE the review mode is Manual, a chunk SHALL hold exactly one segment. The dial SHALL NOT change the trust threshold.

**Source**: FR-ALGO-11, FR-BRIEF-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`, `#fr-brief`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#quality-dial`, ADR-0036, ADR-0038. In plain words: one control trades speed for care, and the table says exactly what it changes; the reviewer reads each chunk once on Balanced and twice on Max, the second time only when the first verdict was readable. The threshold that decides what gets flagged belongs to the review mode, so turning the dial never silently flags more or fewer segments.

#### Scenario: Fast skips the reviewer

- **WHEN** a Fast run translates a chapter of 10 short segments that all pass their checks
- **THEN** no reviewer call is made, and each draft carries at most 1 preceding target

#### Scenario: Balanced packs up to eight segments

- **WHEN** a Balanced run in the Unattended review mode enters a unit of 12 short segments
- **THEN** they are packed into chunks of at most 8 segments, and each chunk gets one reviewer pass

#### Scenario: Max revises after the last segment

- **WHEN** a Max run decides its last segment
- **THEN** the revision stage starts before the run ends

### Requirement: Show the segment just decided and the one in progress in the live panel

WHILE a run translates, the live card SHALL list the segments of the chunk being translated in reading order (the call in flight among them), one line each with formatting marks left out and the full text in the tooltip, each with a marker: waiting, translating…, translated, to review, translated again alone, or kept as is.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`, `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (Live card), `docs/specification/mockups/README.md#g11--state-markers-and-one-colour-map`, `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (T1–T4). In plain words: a book takes hours, and watching sentences go by is how a person judges the model before the run ends; the card shows every segment of the call with its state instead of two rows.

#### Scenario: The decided segment above the one in progress

- **WHEN** a Fast call covers `ch7 · p41` and `ch7 · p42`, `ch7 · p41` is decided as `Вона втратила матір.`, and the draft of `ch7 · p42` has not arrived
- **THEN** the rows read `ch7 · p41` translated and `ch7 · p42` translating…, and Simple detail for `ch7 · p41` shows its source and translation

#### Scenario: A draft waits for the reviewer

- **WHEN** on a Balanced run the draft of `ch7 · p42` has arrived and its chunk's check has not finished
- **THEN** its row still reads translating…, because the segment is not decided yet

#### Scenario: A decision moves the segment up

- **WHEN** `ch7 · p42` is accepted
- **THEN** its row reads translated, and "Show each segment when translated" selects it unless the person clicked another row

#### Scenario: The second row waits for the draft

- **WHEN** a segment has started and its draft is not ready
- **THEN** its detail reads "Being translated now…" under the source, with "Not translated yet." in the translation column

#### Scenario: The second row is empty between segments

- **WHEN** the last segment of a call is decided and the next call has not started
- **THEN** the rows of the chunk stay, the decided ones marked translated, until the next chunk's segments arrive

#### Scenario: A directed fix keeps the chunk's rows

- **WHEN** a directed-fix call for `ch7 · p42` is made in the chunk `ch7 · p41`–`p44`
- **THEN** the rows `ch7 · p41` to `ch7 · p44` stay, and `ch7 · p42` reads translating… while the fix waits for the model

#### Scenario: A verbatim segment reads kept as is

- **WHEN** `ch7 · p43` is a chapter number accepted as it is, with no model call
- **THEN** its row reads "kept as is"

#### Scenario: A run that stopped shows its undecided rows as waiting

- **WHEN** the run ends Stopped or Failed while `ch7 · p43` and `ch7 · p44` were in a call
- **THEN** both rows read "waiting", and no row reads translating…

#### Scenario: Rows show display text from the announcements

- **WHEN** a row is shown
- **THEN** it holds display text only, taken from the run's announcements, with formatting marks left out and the full text with marks `⟦1⟧` in the tooltip

#### Scenario: A paused run shows waiting rows

- **WHEN** the run is paused while segments 5–8 are in flight
- **THEN** their rows read waiting

### Requirement: Show the Translating screen's idle, completed and failed states

WHILE no translation has started for the open project, the Translating screen SHALL show the Ready state with Start translation. WHEN a translation completes, it SHALL show the outcome with Review (n) and Continue to Export. WHEN it ends Failed, it SHALL show "Did not finish", the banner "The translation stopped because of an error. Finished segments are saved." with Show details, and Continue translation, which starts a new run at the first pending segment.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/mockups/README.md#run-states-a`, ADR-0035, ADR-0043. In plain words: the screen always says what can happen next; after a failure nothing finished is lost and the person can carry on from where the run stopped, a start and not a resume. How every run state looks across the window is the app-shell capability's "Show each of the sixteen run states the same way everywhere".

#### Scenario: Idle shows the ready card

- **WHEN** `Earth Gravity` is open, briefed from `en` to `uk` on Balanced, the model `gemma4:26b` is chosen, the review mode is Unattended, 1,240 segments are pending and no translation has started
- **THEN** the screen shows a ready card naming `Earth Gravity`, `gemma4:26b`, Unattended, Balanced and 1,240 pending segments, and the live line "Nothing translated yet."
- **AND** the toolbar offers Start translation and no Pause, Stop or Resume

#### Scenario: The ready card names what is missing

- **WHEN** no run has started and a model or a brief field is missing
- **THEN** the ready card shows the book, the model, the review mode, the quality dial and the pending count, and Start translation is disabled with a tooltip naming the missing model or brief field

#### Scenario: Completed points to Export

- **WHEN** a translation completes with 1,180 translated, 45 fixed automatically, 6 to review and 12 kept as is, leaving nothing pending
- **THEN** the toolbar offers Review (6) and Continue to Export and no Pause or Resume
- **AND** the banner reads "Translation finished. 6 segments need your review. You can export now and review later." with "Review 6 segments"

#### Scenario: Finished with nothing to review

- **WHEN** a translation completes with 0 segments to review
- **THEN** the banner reads "Translation finished. Nothing needs your review." and the toolbar offers Continue to Export

#### Scenario: Completed with pending segments offers a start

- **WHEN** a translation of a Markdown book completes with `Frontmatter values` switched off, and the person then switches `Frontmatter values` on, leaving the book's 3 frontmatter values pending
- **THEN** the toolbar offers Start translation beside Continue to Export
- **AND** Start translation begins a new run whose only model calls are for those 3 frontmatter values

#### Scenario: The review button counts suspicious segments

- **WHEN** a translation completes with 6 segments to review and the final audit doubts 3 accepted segments
- **THEN** the toolbar button reads "Review (6)" and the outcome shows "3" under "worth a second look"

#### Scenario: The outcome shows four counts

- **WHEN** a translation completes
- **THEN** the outcome shows the translated, fixed automatically, to review and kept-as-is counts

#### Scenario: Failed shows the dialog, then offers a new start

- **WHEN** a translation ends Failed with `ErrorCode.internal` after 412 of 1,240 segments were decided
- **THEN** the title bar reads "Did not finish", the banner reads "The translation stopped because of an error. Finished segments are saved." with Show details, no dialog opens by itself, and the toolbar offers Continue translation and Review (n)
- **AND** Show details opens the failure's dialog, and Continue translation begins a new run whose first model call is for the 413th segment

#### Scenario: The failed dialog keeps the decided segments

- **WHEN** a translation ends Failed and the person uses Show details
- **THEN** the dialog says that finished segments are saved, and does not say that decided segments are kept only until the application closes

### Requirement: Tell the person when the model is slow to answer

WHILE the run is running and a model call has been outstanding for at least `10` seconds, the live line SHALL read "Waiting for the model… m:ss", updated once a second and counting across the client's own retries, and the screen SHALL withdraw it when the segment is decided, a new call starts, or a pause or stop is requested; from a minute without an answer the stuck line ("The model is taking longer than usual with segments …") replaces the waiting text.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/mockups/README.md#run-states-a`. In plain words: a slow local model can make the counters stand still for minutes; ten seconds in the live line tells the person it is waiting, and the stuck state beside this one, at a minute without an answer, says when to look closer.

#### Scenario: Nothing is said for a normal answer

- **WHEN** a model call has been outstanding for `9` seconds
- **THEN** the live line shows the plain line of the run state ("Now translating: Chapter 2, segments 5–8") and no waiting text

#### Scenario: The waiting text appears at ten seconds and counts on

- **WHEN** a model call has been outstanding for `10` seconds, and later for `12` seconds
- **THEN** the live line reads "Waiting for the model… 0:10" and then "Waiting for the model… 0:12", and in Ukrainian «Очікування відповіді моделі… 0:12»

#### Scenario: A client retry does not restart the count

- **WHEN** the draft call's first attempt timed out after `3` minutes and the client has sent its second attempt `5` seconds ago
- **THEN** the live line reads "Waiting for the model… 3:05", and not "Waiting for the model… 0:05"

#### Scenario: A minute without an answer is the stuck line

- **WHEN** a draft attempt of `ch2 · p05` has had no answer for `1:00`
- **THEN** the live line reads "The model is taking longer than usual with segments ch2 · p05." and no waiting text

#### Scenario: A decision withdraws it

- **WHEN** the live line shows "Waiting for the model… 0:12" and the segment is then decided
- **THEN** the live line shows the plain line of the run state again

#### Scenario: A pause request replaces it

- **WHEN** the live line shows "Waiting for the model… 0:12" and the person presses Pause
- **THEN** the screen shows "Pausing…" and never "Waiting for the model…" while the pause is pending

### Requirement: Read the review mode at the start of each run

WHEN a translation starts from the window, the system SHALL take the review mode from the environment variable `BOOKLOOM_REVIEW_MODE`, or else the system property `bookloom.review.mode`, or else the stored setting "When to stop and ask you", by this table, in any letter case; a flag value naming no mode SHALL give Unattended and one WARN line naming it. The command line SHALL ignore these flags and run Unattended.

| Settings word | Mode | Flag values |
|---|---|---|
| Never — translate unattended | Unattended (the default) | `unattended`, `auto` |
| When something needs review | Assisted | `assisted`, `semi-manual` |
| After every segment | Manual | `manual` |

**Source**: FR-REVIEW-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, ADR-0036, ADR-0045, `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, "Translate one book from the command line". In plain words: the careful modes are now a saved choice in Settings, read each time a translation starts, so a change applies to the next translation. A launch flag set on purpose still wins in the window, for evaluations and testing; the command line has nobody to stop for, so it always runs Unattended and logs that it ignored the flag. A typo must not silently change how the run behaves.

#### Scenario: A spelled-out alias is accepted

- **WHEN** a translation starts from the window with `BOOKLOOM_REVIEW_MODE` set to `Semi-Manual`
- **THEN** the run uses Assisted

#### Scenario: The environment wins over the property

- **WHEN** `BOOKLOOM_REVIEW_MODE` is `auto` and `bookloom.review.mode` is `manual`
- **THEN** the run uses Unattended

#### Scenario: An unknown value falls back

- **WHEN** `BOOKLOOM_REVIEW_MODE` is `strict`
- **THEN** the run uses Unattended and the log holds one WARN line holding `strict`

#### Scenario: The stored setting applies without a flag

- **WHEN** neither the variable nor the property is set and "When to stop and ask you" stores When something needs review
- **THEN** the run uses Assisted

#### Scenario: A change applies to the next translation

- **WHEN** "When to stop and ask you" is changed from Never to After every segment between two translations, with no flag set
- **THEN** the first translation used Unattended and the second uses Manual

#### Scenario: A flag overrides the stored setting

- **WHEN** "When to stop and ask you" stores When something needs review and `BOOKLOOM_REVIEW_MODE` is `manual`
- **THEN** the run uses Manual

#### Scenario: The command line ignores the flag

- **WHEN** the translate command runs with `BOOKLOOM_REVIEW_MODE` set to `manual`
- **THEN** the run uses Unattended and the log holds one line saying the review-mode flag was ignored

### Requirement: Announce each model call as it starts

WHEN the engine starts a model call, it SHALL emit one event naming the call's kind (draft, structural repair, placeholder repair, reviewer, directed fix, pre-scan, summary or revision) and its segments, before waiting for the answer, SHALL NOT emit it for a call refused because a pause or stop was requested, and SHALL NOT emit another for the client's own retries.

**Source**: FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`. In plain words: between two segment decisions the engine may make several calls, and naming the kind lets the screen and the log say what the model is doing; the step announcements and batch results are stated beside this one.

#### Scenario: One event before each decision

- **WHEN** a two-segment book is translated on Fast and each segment is answered by its first call
- **THEN** the model-call events are, in order, a draft for `Book.md:0` before that segment's decision and a draft for `Book.md:1` before that segment's decision, and no export stage is announced

#### Scenario: A repair is its own call

- **WHEN** the first reply for `Book.md:0` is not the required JSON object and the structural repair call is answered correctly
- **THEN** a draft event and a structural-repair event for `Book.md:0` are emitted, one before each call, before that segment's decision

#### Scenario: The reviewer is announced by kind

- **WHEN** a Balanced run reviews the chunk holding `ch07.xhtml:40` and `ch07.xhtml:41`
- **THEN** one model-call event of kind review is emitted for that chunk

#### Scenario: The client's retry does not announce again

- **WHEN** the first attempt of the draft call for `Book.md:0` times out and the client sends its second attempt
- **THEN** exactly one model-call event for `Book.md:0` was emitted for that draft call, and none for the second attempt

#### Scenario: A refused call is not announced

- **WHEN** a pause is already requested when the engine is about to make a model call
- **THEN** the provider receives no request and no model-call event is emitted

### Requirement: Tag every activity-log entry with one of seven kinds

Every entry the activity log holds SHALL carry one kind from a fixed vocabulary of seven, each shown by its tag, with both a status role from the token catalogue and a mark that is not its colour. The run SHALL produce each kind at its own event, and the text of every entry SHALL come from the message catalogue by key. An answered draft, review, summary or revision call, a call interrupted by a pause or stop, and a repair round starting SHALL be technical `info` lines, listed only while Show technical details is on; a failed attempt and each step of an automatic recovery SHALL be `retry` lines. The log SHALL retain its most recent entries up to a fixed bound and SHALL drop the oldest first.

**Source**: FR-NOTIF-6a, FR-NOTIF-6b, FR-NOTIF-6c (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#activity-log`), FR-A11Y-6 (`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#accessibility`), FR-UI-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`). In plain words: the log is the only record of what a multi-hour run decided. A fixed vocabulary of seven short tags, lined up in a column, is what makes it scannable, and now that the engine repairs, remembers and summarises, every one of the seven actually appears. Colour alone cannot carry the kind, and the text comes from the catalogue so a Ukrainian window never shows an English log. An entry names a segment the way the review dialog does, by its locator, because a file name and an ordinal mean nothing to the person reading it.

#### Scenario: An accepted segment logs its kind and role

- **WHEN** the segment `ch7 · p42` is accepted during a run
- **THEN** an entry of kind accepted with the tag `ok` is appended, referring to the success role, which resolves to `#5f8a6b` under the light values
- **AND** it carries a mark distinguishing it from a segment-error entry without relying on colour

#### Scenario: A repair round logs fix

- **WHEN** the segment `ch07.xhtml:41`, whose locator is `ch7 · p42`, goes through one directed fix
- **THEN** an entry tagged `fix` naming `ch7 · p42`, and not `ch07.xhtml:41`, is appended, and its tag is rendered before its text in a monospace face

#### Scenario: A format repair and a resume after a provider error log retry

- **WHEN** the segment `ch7 · p43` gets one placeholder repair, and later the run pauses on `ErrorCode.unreachable` and the person presses Try again now
- **THEN** an entry tagged `retry` naming `ch7 · p43` is appended for the repair, the pause is logged as `info`, and the resume appends a second `retry` entry

#### Scenario: An answered draft is a technical line

- **WHEN** the draft call for `ch7 · p42` is answered while Show technical details is off, and later while it is on
- **THEN** the first answer adds no visible line, and the second adds one `info` line

#### Scenario: Entry text comes from the catalogue

- **WHEN** the accepted entry for the segment `ch7 · p42` is rendered while the interface is in Ukrainian
- **THEN** its text resolves from a catalogue key with `ch7 · p42` substituted into it, and is not an English sentence with the locator joined to it

#### Scenario: Each kind maps to its own status role

- **WHEN** the seven kinds are read
- **THEN** accepted refers to the success role; repaired, glossary-applied, summary-updated and milestone refer to the information role; retried refers to the warning role; and segment-error refers to the danger role

#### Scenario: The log drops its oldest entries

- **WHEN** a run appends more entries than the log's bound
- **THEN** the log holds exactly the bound, and the entry appended first is no longer present

#### Scenario: The seven kinds and their tags

- **WHEN** the activity log holds entries
- **THEN** each carries one kind, shown by its tag: accepted `ok`, repaired `fix`, glossary or memory applied `mem`, summary updated `sum`, retried `retry`, segment error `err` and milestone `info`

#### Scenario: Entries are monospace with the tag first

- **WHEN** the activity log renders an entry
- **THEN** it is rendered in a monospace face with the tag first, and its kind is readable without seeing the colour

#### Scenario: Each event produces its kind

- **WHEN** a run lives through its events
- **THEN** `ok` is logged when a segment is accepted; `fix` for each repair call — a directed fix; `retry` for a structural or placeholder format repair and for the resume that follows a provider-error pause; `mem` when a name scan adds glossary entries or a segment is reused from the translation memory; `sum` when the rolling summary is refreshed; `err` when a segment is flagged; and `info` for a run milestone — a stage start, a pause (including a provider-error pause), a resume or the finish

#### Scenario: A provider client's own retry makes no entry

- **WHEN** the provider client retries inside one call
- **THEN** the run does not see it and logs no entry

#### Scenario: An entry names the segment by its locator

- **WHEN** an entry about a segment is written
- **THEN** its text comes from the catalogue with the segment's locator, such as `ch7 · p42`, never its id, and any other value substituted into it

## REMOVED Requirements

### Requirement: Estimate the time left and the time spent

**Reason**: The estimate from the last 20 segments times the pending segments projected one slow stretch over the whole book. Progress now counts steps: what a step is belongs here, and how steps become the time left belongs to the app-shell capability.

**Migration**: See "Plan a run's steps and count the time spent" above for the steps and the time spent, and the app-shell capability's "Estimate the time left from measured time per kind of step" for the time left.

### Requirement: Start a run and report its progress

**Reason**: Its start message promised that progress lasts only until the application closes, and its counts used the old words; progress is now saved as it goes.

**Migration**: See "Start a translation and report its progress", "Refuse to start a run with a missing target language or model" and "Derive each segment count from the engine's progress snapshot" above.

### Requirement: Stop the job on any other failure

**Reason**: It said an internal error ends the run and never pauses, while a step that throws is answered as `ErrorCode.internal` and pauses, recovering by itself (three pauses, then the step is flagged). The pausing errors belong to the resume capability.

**Migration**: See "End the run Failed on an error no pause can fix" above for the errors that end a run, and the resume capability's "Pause instead of failing when an error stops the job" for the errors that pause it.
