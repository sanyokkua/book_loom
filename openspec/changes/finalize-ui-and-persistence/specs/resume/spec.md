## ADDED Requirements

### Requirement: Reopen a stored project as ready to continue

WHEN a project with stored progress is opened, the system SHALL show it as ready to continue: the title bar reading the percentage done, the banner "Welcome back — 74 of 142 segments are done." with the figures of that project, the live line naming where it continues, and Continue translation in the toolbar.

**Source**: FR-RESUME-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), D11 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md` (Run states, Ready to continue). In plain words: after closing the application a person sees at once how far the translation got and what the button will do.

#### Scenario: A project reopens ready to continue

- **WHEN** a project with 74 of 142 segments decided is opened after a restart
- **THEN** the title bar reads `Ready · 51 % done`
- **AND** the banner reads `Welcome back — 74 of 142 segments are done.` and the toolbar offers Continue translation

### Requirement: Never continue a translation by itself

WHEN a project is opened, the system SHALL NOT start or resume a run until the person chooses Continue translation, whatever state the project was stored in.

**Source**: D11 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md` (Run states). In plain words: a model call can take a computer's whole attention, so opening a project to look at it must never begin using the AI model without being asked.

#### Scenario: Opening does not call the model

- **WHEN** a project stored as running is opened and the person does nothing further
- **THEN** no model call is made and the state is ready to continue

### Requirement: Continue at the first segment not yet decided

WHEN the person chooses Continue translation on a reopened project, the system SHALL start a new run whose first model call is for the first pending segment, announced as the live line "Continues at Chapter 2, segment 5." before the call.

**Source**: FR-RESUME-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md` (Run states). In plain words: decided segments are never translated twice, and the person sees where the work picks up.

#### Scenario: Continue picks up at Chapter 2, segment 5

- **WHEN** Chapter 1 and segments 1 to 4 of Chapter 2 are decided and Continue translation is chosen
- **THEN** the live line reads `Continues at Chapter 2, segment 5.` and the first model call is for segment 5 of Chapter 2

### Requirement: Treat a run stored as running or paused as interrupted

WHEN a project whose last run was stored as running or paused is opened, the system SHALL treat that run as interrupted and show the project as ready to continue, whether the application was closed cleanly or ended by a crash.

**Source**: D11 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), FR-PERSIST-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`). In plain words: a run that was in flight when the window closed or the process died is not "still running" on the next launch; it simply stopped, and the decided segments are all kept.

#### Scenario: A crash during a run

- **WHEN** the process is killed while a run is stored as running with 74 of 142 segments decided, and the project is opened
- **THEN** the project is ready to continue at 51 % done and Continue translation is offered

#### Scenario: A paused run after a quit

- **WHEN** the application is closed while a run is paused with 74 of 142 segments decided, and the project is opened
- **THEN** the project is ready to continue and the paused run is not shown as running

### Requirement: Ask when the stored AI model does not answer

WHEN the person chooses Continue translation and the AI model the project used does not answer, the system SHALL show the dialog "AI model not answering" with Try again, Use another AI model… and Not now, and SHALL start no run until the person has chosen.

**Source**: FR-PROV-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/mockups/README.md` (Other dialogs, AI model not answering). In plain words: instead of failing on the first call, the person is told right away that Ollama is not open and can fix that, pick another model, or leave.

#### Scenario: Ollama is not open

- **WHEN** Continue translation is chosen and Ollama does not answer
- **THEN** the dialog `AI model not answering` offers Try again, Use another AI model… and Not now
- **AND** no run has started

#### Scenario: Not now leaves the project as it was

- **WHEN** Not now is chosen
- **THEN** the project stays ready to continue and no model call is made

#### Scenario: Try again continues once the model answers

- **WHEN** Ollama is opened and Try again is chosen
- **THEN** the run starts at the first pending segment

### Requirement: Ask which model to use when a different one is chosen

WHEN the person chooses Continue translation and the current AI model differs from the one the project's decided segments used, the system SHALL show the dialog "A different model is chosen" with Use the earlier model and Continue with the new one, and SHALL start the run only on one of them.

**Source**: FR-PROV-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/mockups/README.md` (Other dialogs, A different model is chosen). In plain words: continuing a half-translated book with another model can change its voice, so the person decides whether to keep the earlier model or accept the change.

#### Scenario: The earlier model is kept

- **WHEN** the project's segments were translated with `gemma4:e4b`, `qwen3:8b` is now current, and Use the earlier model is chosen
- **THEN** the run starts with `gemma4:e4b`

#### Scenario: The new model is accepted

- **WHEN** Continue with the new one is chosen in the same situation
- **THEN** the run starts with `qwen3:8b`

### Requirement: Pause on these provider errors, except a reviewer timeout

The system SHALL pause on `ErrorCode.unreachable`, `timeout`, `auth`, `rateLimited`, `upstream`, `modelNotFound`, `modelUnavailable` and `missingCredential`; a reviewer call answered `timeout` SHALL NOT pause the run but flag its segments, while a reviewer call answered `unreachable`, `upstream` or `rateLimited` SHALL pause like any other call.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md` (Words, Run states). In plain words: a stopped server is fixed in a minute, but a reviewer that merely times out should not hold a night's run.

#### Scenario: A reviewer timeout flags instead of pausing

- **WHEN** the reviewer call of a chunk answers `ErrorCode.timeout`
- **THEN** the run does not pause and the chunk's segments are flagged as the reviewer being unavailable

#### Scenario: A reviewer outage pauses

- **WHEN** the reviewer call answers `ErrorCode.unreachable`
- **THEN** the run pauses with `ErrorCode.unreachable`

### Requirement: Flag a step after its budget of pauses

The system SHALL count the pauses of each step by the kind of its error and, on a failure past the budget, flag the step's segment with the error and go on: two pauses for `timeout` and the codes only a person can fix, three for `ErrorCode.internal`, and ten for an outage and an unloaded model.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md` (Words, Run states). In plain words: one bad segment must not end a night's run, while an outage that is not the segment's fault never spends the segment's own budget.

#### Scenario: A throwing step is flagged after three pauses

- **WHEN** the draft of `ch07.xhtml:42` answers `ErrorCode.internal` on three pauses in a row
- **THEN** `ch07.xhtml:42` is flagged with that error and the run goes on

#### Scenario: A person-only code is flagged after two

- **WHEN** the draft of `ch07.xhtml:42` answers `ErrorCode.auth` on two pauses
- **THEN** `ch07.xhtml:42` is flagged with `ErrorCode.auth`

### Requirement: Offer to skip the step a pause is stuck on

WHILE the run is paused on such an error, the job SHALL offer to skip the step: the run resumes, the step's segments are flagged with the error without their call being sent again, and the run goes on.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md` (Words, Run states). In plain words: the person can give up on a segment that keeps failing and let the book continue, instead of being stuck.

#### Scenario: Skipping four segments

- **WHEN** a run is paused on `ErrorCode.modelNotFound` for segments 5–8 and the person chooses Skip these 4 segments
- **THEN** segments 5–8 are flagged with `ErrorCode.modelNotFound` and the run goes on with segment 9

### Requirement: Not pause on errors that belong to one segment

The system SHALL NOT pause on `ErrorCode.contextWindow` or `ErrorCode.emptyCompletion`, which flag the one segment and let the run go on, nor on `busy` or `discoveryFailed`, which end the run Failed with `ErrorCode.internal`; an unexpected error before the first segment SHALL end the run Failed with `ErrorCode.internal` and a message saying the run could not begin.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md` (Words, Run states). In plain words: a prompt too long for the model belongs to one segment, and a bug in the application is not fixed by waiting.

#### Scenario: A too-long prompt flags one segment

- **WHEN** the draft of `ch07.xhtml:42` answers `ErrorCode.contextWindow`
- **THEN** only `ch07.xhtml:42` is flagged and the run goes on

### Requirement: Show the provider-error state with its three actions

WHILE a run from the window is paused on such an error, the translating screen SHALL show the provider-error state naming the error, with Try again now (which resumes the run), Open provider settings, and Skip these N segments, N being the count in flight.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md` (Words, Run states). In plain words: the screen says what went wrong and offers the three things a person can do; leaving the run paused needs no button, because the toolbar's Pause already does it.

#### Scenario: The three actions are shown

- **WHEN** a run is paused on `ErrorCode.unreachable` with segments 5–8 in flight
- **THEN** the screen offers Try again now, Open provider settings and Skip these 4 segments

## MODIFIED Requirements

### Requirement: Pause for review as the review mode says

The system SHALL pause a run started from the window on a provider error in every review mode, and SHALL add the mode's own pause points: none for Never, each flagged segment for When something needs review, every decided segment for After every segment. The mode is the stored setting "When to stop and ask you", read when each run starts.

A pause on a flagged segment or after a segment SHALL name the segment just decided and SHALL come before any further model call.

**Source**: FR-REVIEW-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, ADR-0036, ADR-0045. In plain words: the default runs to the end and leaves review for afterwards; a careful translator can have the run stop on every problem, or on every segment, and fix it in the review desk on the spot. A stopped server or an unloaded model pauses the run in every mode, so the person can fix it instead of starting over; the command line enables no pause point at all. A segment is decided only after its whole chunk has been drafted and, when the reviewer is on (Balanced, Max), reviewed — so with the reviewer on the later segments of the same chunk have already been drafted and reviewed when the pause comes, while with the reviewer off (Fast) or in After every segment, which holds one segment per chunk, no call for the next segment precedes it. Naming the segment lets the review desk open on the right one.

#### Scenario: Unattended runs past a flagged segment

- **WHEN** an Unattended run flags `ch07.xhtml:41` and continues
- **THEN** the run does not pause, and `ch07.xhtml:41` is listed in the review desk

#### Scenario: Unattended from the window pauses on a provider error

- **WHEN** an Unattended run started from the window gets `ErrorCode.unreachable` for the draft call of `ch07.xhtml:42`
- **THEN** the run pauses with `ErrorCode.unreachable` and the screen shows the provider-error state

#### Scenario: Assisted on Fast stops on the flagged segment

- **WHEN** an Assisted run on the Fast dial flags `ch07.xhtml:41`
- **THEN** the run pauses with the reason on-flagged, naming `ch07.xhtml:41`, before any call for `ch07.xhtml:42`

#### Scenario: Assisted on Balanced stops after its chunk was reviewed

- **WHEN** an Assisted run on the Balanced dial packs `ch07.xhtml:40`–`ch07.xhtml:43` into one chunk and flags `ch07.xhtml:42`
- **THEN** the run pauses with the reason on-flagged, naming `ch07.xhtml:42`, after the draft calls of all four segments and the chunk's reviewer call
- **AND** no model call is made for `ch07.xhtml:43` until the run resumes

#### Scenario: Manual stops after an accepted segment

- **WHEN** a Manual run accepts `ch01.xhtml:0`
- **THEN** the run pauses with the reason after-segment, naming `ch01.xhtml:0`, before any call for `ch01.xhtml:1`

#### Scenario: The mode is read from the setting

- **WHEN** "When to stop and ask you" is set to After every segment and a run is started
- **THEN** the run pauses after each decided segment, as Manual does

### Requirement: Record whether a run is running, paused or ended

The system SHALL store, for each run of a project, its current state — running from its start, paused at each pause, running again at each resume, and completed, stopped or failed when it ends — and SHALL let the rest of the application read the state of a project's latest run, also after a restart. A run stored as running or paused SHALL count as interrupted when its project is opened.

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, D11 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: a review retry needs the model, which a running run is using and a paused or finished one is not, so the current state is kept on every pause and resume. Because it is stored, a run that was in flight when the application closed is recognised as interrupted instead of looking alive (see the review-queue capability's "Allow a retry whenever no run of the project is running").

#### Scenario: The state follows pauses and resumes

- **WHEN** an Assisted run of `Book.md` starts, pauses on the flagged `Book.md:1`, is resumed and ends
- **THEN** its recorded state is running, then paused, then running, then completed

#### Scenario: A stopped run is recorded as stopped

- **WHEN** a running run is stopped and the engine reports it cancelled
- **THEN** its recorded state is stopped

#### Scenario: A failed run is recorded as failed

- **WHEN** the model call for `Book.md:1` throws an exception and the run ends Failed with `ErrorCode.internal`
- **THEN** its recorded state is failed

#### Scenario: A run stored as running counts as interrupted

- **WHEN** a project whose latest run is stored as running is opened after a restart
- **THEN** the run is read as interrupted and the project is ready to continue

### Requirement: Pause, stop and resume a run from the screen

WHILE a run is in progress, the translating screen SHALL offer to pause it and to stop it. WHILE a run is paused, it SHALL offer to resume it and to stop it and, unless the pause is on a provider error, SHALL show the paused line saying the segments in flight are translated again on resume. The title bar's single Pause or Resume control SHALL do what the screen's control of the same name does.

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `#banners`, `docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`, `docs/specification/mockups/README.md` (Run states, Pause vs Stop). In plain words: Pause is a short break inside this translation; Stop ends translating for now. Everything decided is saved either way, so the old promise "kept until the application closes" is gone, and the paused line tells the person what resume will redo.

#### Scenario: Resuming continues the same run

- **WHEN** a paused job is resumed
- **THEN** the screen returns to the running state and the counts continue from where they stood

#### Scenario: The title bar pauses the run

- **WHEN** a run is in progress and the person presses Pause in the title bar
- **THEN** the run reports pausing exactly as if the screen's Pause had been pressed, and the title bar then shows Resume

#### Scenario: The paused banner says how long progress lasts

- **WHEN** a run pauses while segments 5–8 are in flight
- **THEN** the screen shows `Paused. Segments 5–8 are translated again when you resume.`
- **AND** it makes no claim that progress is lost when the application closes

### Requirement: Resume a stopped run with a new run

WHEN a run is stopped, the screen SHALL report a neutral outcome, not a failure, and SHALL NOT show an error dialog or a message of error severity. WHILE a run is stopped, the screen SHALL offer Continue translation, which continues the translation with a new run from the first PENDING segment, SHALL keep the flagged segments in the review desk, and SHALL show the stopped line saying where it continues.

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `#banners`, `docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`, `docs/specification/mockups/README.md` (Run states, Stopped). In plain words: stopping is something a person chose, so telling them it went wrong is both untrue and unpleasant. Decisions are saved, so a stopped run starts a new run at the first undecided segment, today or after a restart.

#### Scenario: Stopping is not an error

- **WHEN** a running job is stopped and the engine reports the run as cancelled
- **THEN** the screen moves to the stopped state, shows no error dialog, and shows no message of error severity

#### Scenario: A stopped run resumes where it stood

- **WHEN** a run is stopped with 412 of 1,240 segments decided, 3 of them flagged, and the person presses Continue translation
- **THEN** a run starts whose first model call is for the 413th segment, and the counts continue from 412
- **AND** the 3 flagged segments are still listed in the review desk

#### Scenario: The stopped banner says where a resume re-enters

- **WHEN** a run is stopped with Chapter 2, segment 5 as the first pending segment
- **THEN** the screen shows `Stopped. Continues at Chapter 2, segment 5.` and offers Continue translation

### Requirement: Pause instead of failing when an error stops the job

WHERE pause on error is enabled, IF a model call fails with a provider error that survived the retry policy, or answers `ErrorCode.validation`, THEN the system SHALL pause with that error, keep every count as it stood, and on resume make the interrupted call again from its first request.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/implementation_plan/CHANGE_BACKLOG.md` (D19). In plain words: a stopped local server or an unloaded model is often fixed in a minute. The window always pauses on such an error, whatever the review mode (see "Pause for review as the review mode says"), so the person fixes it and presses Try again now instead of translating the book again. The call that failed — a draft, a repair or a chunk's reviewer call — is made again; nothing already decided is redone. A bug in the application is not fixed by waiting, so it ends the run; a prompt too long for the model belongs to one segment, so only that segment is flagged. A step that throws is retried three times and then flagged, so one bad segment cannot end a night's run.

#### Scenario: The model is unreachable once

- **WHEN** pause on error is enabled and, on the Fast dial, the model answers `ErrorCode.unreachable` for the second of
  three segments, then answers normally
- **THEN** the job pauses with `ErrorCode.unreachable`, with 1 accepted and 2 pending
- **AND** after resume the second segment is sent again and accepted, and the job ends Completed

#### Scenario: The export fails once

- **WHEN** a run is paused on `ErrorCode.upstream` and an export of its project then fails because the destination
  folder was deleted
- **THEN** the run stays paused with its counts unchanged, and the export reports its own failure without resuming or
  ending the run

#### Scenario: Retry now resumes the run

- **WHEN** an Unattended run from the window is paused with `ErrorCode.upstream` at 412 of 1,240 segments and the person
  presses Try again now
- **THEN** the run resumes, the interrupted call is made again, and the counts continue from 412

#### Scenario: Stay paused keeps the run waiting

- **WHEN** the person presses nothing further on the same pause
- **THEN** the run stays paused and no request is sent

#### Scenario: An unloaded model pauses too

- **WHEN** pause on error is enabled and, on the Fast dial, LM Studio answers `400` with `{"error":"Model unloaded"}`
  for the second of three segments
- **THEN** the job pauses with `ErrorCode.modelUnavailable`, with 1 accepted, 0 flagged and 2 pending
- **AND** the window shows the provider-error state naming that error with Try again now

#### Scenario: A step that throws is retried, then flagged

- **WHEN** pause on error is enabled and, on the Fast dial, the model call throws an exception four times in a row for
  the second of three segments
- **THEN** the job pauses three times with `ErrorCode.internal`, each pause recovering by itself
- **AND** the fourth failure flags `Book.txt:1` with `ErrorCode.internal`, and the job ends Completed with two accepted

#### Scenario: A failure while the run starts ends it Failed

- **WHEN** the glossary store throws while the run prepares, before any segment
- **THEN** the job ends Failed with `ErrorCode.internal` and the message "An unexpected error stopped the run before it
  began."

#### Scenario: A segment that keeps timing out is flagged after two pauses

- **WHEN** the draft of `Book.txt:1` is answered with `ErrorCode.timeout` three times in a row, and the run resumes
  after each of the first two pauses
- **THEN** the run pauses twice, then flags `Book.txt:1` with `ErrorCode.timeout` and drafts `Book.txt:2`
- **AND** the run ends Completed with two segments accepted and one flagged

#### Scenario: A 429 does not spend the segment's budget

- **WHEN** the draft of `Book.txt:1` is answered with `ErrorCode.rateLimited` three times in a row, then normally
- **THEN** the run pauses three times and recovers by itself each time, and `Book.txt:1` is accepted, not flagged

#### Scenario: Skipping the failing segment from the pause

- **WHEN** the run is paused because the draft of `Book.txt:1` was answered with `ErrorCode.upstream`, and the person
  skips the segment
- **THEN** `Book.txt:1` is flagged with `ErrorCode.upstream`, its draft is not sent again, and `Book.txt:2` is drafted
  next

### Requirement: Recover from a provider error by itself

WHILE a run is paused on an error whose recovery is automatic (an outage, a `timeout`, an `internal` error a step answered, or an unloaded model) and the step has pauses of that kind left, the system SHALL wait and resume by itself, in every review mode, as soon as a probe of the provider passes, the interrupted call being made again. A pause for review, a pause the person asked for and a pause on an error only a person can fix SHALL NOT resume by itself.

**Source**: `docs/specification/mockups/README.md` (Words), `docs/implementation_plan` overnight plan step 11 (owner intent: a run left all night is finished in the morning or visibly retrying with a clear reason, never silently stuck). In plain words: a local server that restarts, a laptop that loses its network for a while, or a provider that rate limits are common over a night. The run waits, checks the server now and then without asking the model anything, and goes on by itself; a wrong key or a missing model is not fixed by waiting, so those still wait for the person. A local server that unloaded an idle model usually loads it again on the next request, so the run tries six times — about nineteen minutes — and then asks the person to load it, instead of probing a model nobody loads all night.

#### Scenario: Retry now keeps the outage clock and the segment's budget

- **WHEN** the provider is down and the person presses Try again now at each of eleven waits, each call failing again with
  `ErrorCode.unreachable`, and the twelfth call answers
- **THEN** every wait says the provider is down since the first failure, each wait is attempt 1 of a fresh schedule,
  and the segment is accepted, not flagged after its tenth pause

#### Scenario: A twenty-minute outage resumes at the seventh wake

- **WHEN** the draft of the second of three segments is answered `ErrorCode.unreachable` and the provider stays down for
  20 minutes
- **THEN** the wakes at 0:15, 0:45, 1:45, 3:45, 8:45 and 18:45 find the provider down, and the seventh wake, 28 min 45 s
  after the failure (attempt 8 counting the failed call), finds it back and resumes the run
- **AND** the segment is sent again and accepted, and the run ends Completed with three accepted

#### Scenario: Stop during the wait cancels the recovery

- **WHEN** the person stops the run while it waits for its third wake
- **THEN** the run ends Cancelled at once and the provider is probed no more

#### Scenario: Pause during the wait holds the run

- **WHEN** the person presses Pause while the run waits for its second wake
- **THEN** the recovery is announced as held, no wake resumes the run, and the person's Resume continues it

#### Scenario: A rejected request waits for the person

- **WHEN** the draft is answered `ErrorCode.validation`
- **THEN** the run pauses and never wakes by itself

#### Scenario: An unloaded model loads again at the first wake

- **WHEN** the draft of the second of three segments is answered `ErrorCode.modelUnavailable` once and the probe passes
- **THEN** the run wakes once, 15 s after the failure, resumes, and ends Completed with three accepted

#### Scenario: Which errors recover by themselves

- **WHEN** the run is paused on `unreachable`, `upstream` or `rateLimited` (an outage), `timeout`, `internal`, or `modelUnavailable` (an unloaded model)
- **THEN** it waits and resumes by itself, whereas `auth`, `modelNotFound`, `missingCredential` and `validation` wait for the person

#### Scenario: The wakes follow a fixed schedule

- **WHEN** the run waits to resume by itself
- **THEN** it waits 15 s, 30 s, 1 min, 2 min, 5 min and 10 min before the first six wakes and 10 min before every later one

#### Scenario: Each wake probes the provider

- **WHEN** the run wakes
- **THEN** it probes the connection and the model list, which must still offer the run's model, and resumes as soon as a probe passes, while a failed probe only extends the wait

#### Scenario: The person ends the wait

- **WHEN** the person uses Try again now, Skip these 4 segments or Resume during the wait
- **THEN** the wait ends at once and the wake schedule starts afresh, but the time the outage began (`downSince`) is kept, because only an answered model call clears it
- **AND** the call sent again on the person's word during an outage or an unloaded model does not count toward the pauses after which the step is flagged, whereas after a timeout or a throwing step it still counts

#### Scenario: Each step of the recovery is announced and logged

- **WHEN** the run recovers by itself
- **THEN** the listeners are told `RecoveryWaiting` (waiting with the next try's time and attempt, gave up, or held), and the outage's start, each wait, each probe's result and the resume after N minutes are logged at INFO

## REMOVED Requirements

### Requirement: Keep a run's progress only until the application closes

**Reason**: Decisions, edits and memory entries are now stored (`local-storage`), so progress survives closing the application and a project can be continued after a restart.

**Migration**: See "Reopen a stored project as ready to continue" and "Continue at the first segment not yet decided" above, and `local-storage` ("Keep the application's work across a restart").
