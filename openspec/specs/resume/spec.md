# resume Specification

## Purpose
Lets a running translation job pause, resume or stop at safe points, either when asked or at pause points the caller
enables, without losing or repeating decided segments. It covers one run in memory; continuing after a restart comes
with local storage.

## Requirements

### Requirement: Pause on request at the next boundary

WHEN a pause is requested while a job translates, the system SHALL:

- abort the model request in flight, without waiting for the provider to answer, and send no further request — not a
  retry, not a repair and not a reviewer call; a reply that had already arrived before the request was made is still
  decided normally;
- pause with the reason "requested", reporting no error;
- make no model call while paused;
- on resume, make the aborted call again from its first request — a segment's draft or repair, or its chunk's reviewer
  call — and then continue where the run stood.

WHEN a pause is requested while no model request is in flight, the system SHALL pause at the next boundary, which is
before the next model call or before the job ends, with the reason "requested".

A pause requested before the job runs SHALL take effect at the first boundary, before the first model call. The export,
which is a separate action, is never paused.

**Source**: FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0035, ADR-0038. In plain words: pausing does not wait for a slow model. The request that is waiting is aborted at once and nothing more is sent, so the Pause button acts within seconds even when a local model takes minutes. The call that was aborted has no answer yet, so resuming makes it again rather than skipping it; a segment already decided is never redone, and a draft already made for the chunk is not made twice (the translation-pipeline capability's "Decide a chunk's segments in document order"). A screen may ask for a pause before it starts the job, and that request must not be lost. The run no longer writes the book, so its last boundary is the end of the job.

#### Scenario: Pause after the first of three segments

- **WHEN** a Fast job over three segments is asked to pause as soon as the first segment is decided
- **THEN** the job pauses with reason requested, 1 accepted and 2 pending, after exactly 1 model call
- **AND** after resume it ends Completed with exactly 3 model calls in total

#### Scenario: Pause during a slow request

- **WHEN** a Fast job over three segments has its first segment accepted, the provider is taking `30` seconds to answer
  the second segment's request, and a pause is requested `1` second after that request was sent
- **THEN** the job pauses with reason requested within `5` seconds, with 1 accepted and 2 pending and no error, and the
  provider has received exactly `2` requests in total
- **AND** after resume the second segment is requested again from its first request, and the job ends Completed with 3
  accepted and the provider having received `4` requests in total

#### Scenario: A pause during the reviewer call

- **WHEN** a Balanced job over three segments packed into one chunk has drafted all three, and a pause is requested
  while the chunk's reviewer call is in flight
- **THEN** the job pauses with reason requested, with 0 accepted and 3 pending, and the provider has received `4`
  requests in total
- **AND** after resume the reviewer call is made again and no draft call is repeated, and the job ends Completed with 3
  accepted and the provider having received `5` requests in total

#### Scenario: A pause after the last decision

- **WHEN** a pause is requested after the last segment is decided and before the job ends
- **THEN** the job pauses with reason requested and 0 pending, and after resume ends Completed with no file written

#### Scenario: A pause during export is ignored

- **WHEN** a run has ended Completed, the person exports its project, and a pause is requested on that run while the
  export writes `Book.uk.epub`
- **THEN** nothing pauses and the export writes `Book.uk.epub`

#### Scenario: A pause requested before the job runs

- **WHEN** a pause is requested on a job over three segments before it runs, and the job is then run
- **THEN** the job pauses with reason requested before any model call, with 0 accepted and 3 pending

### Requirement: Pause at enabled pause points

WHERE the caller enables pause points, the system SHALL pause at each enabled point:

- after-segment: after each decided segment;
- on-flagged: after each segment decided FLAGGED;
- after-section: after the last segment of each section;
- between-stages: after the last segment of the translation stage, before the revision stage or the end of the job.

A section is one EPUB spine document, one FB2 body, or a whole Markdown or TXT file; the book's auxiliary text — its
title, navigation labels, page titles and image descriptions — is not a section. A section with no segments SHALL
cause no pause. A pause at after-segment or on-flagged SHALL name the segment just decided.

**Source**: FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#data-model`, ADR-0036, ADR-0041. In plain words: a screen can let the person look at each flagged segment, each segment, each section, or the whole translation before revision. With nothing enabled a job never waits, which is how the command line runs. The book's title and labels are translated after its body; they are not a chapter the person reads through, so finishing them is no section's end.

#### Scenario: Pause after each section of an EPUB

- **WHEN** an EPUB whose two spine documents hold 2 and 1 segments is translated with every "Also translate" switch off
  and after-section enabled
- **THEN** the job pauses twice: first with 2 accepted and 1 pending, then with 3 accepted and 0 pending

#### Scenario: The auxiliary text is not a section

- **WHEN** an EPUB whose two spine documents hold 2 and 1 segments, and whose only auxiliary segment is its title, is
  translated with the metadata switch on and after-section enabled
- **THEN** the job pauses twice: first with 2 accepted and 2 pending, then with 3 accepted and 1 pending
- **AND** after the title is decided the job ends Completed without pausing again

#### Scenario: Pause on a flagged segment

- **WHEN** a book of three segments is translated with on-flagged enabled and only `Book.md:1` is flagged
- **THEN** the job pauses once, naming `Book.md:1`, with 1 accepted, 1 flagged and 1 pending

#### Scenario: Pause between stages

- **WHEN** a book is translated with between-stages enabled
- **THEN** the job pauses with 0 pending segments
- **AND** after resume the job ends Completed and no file is written

#### Scenario: An empty section causes no pause

- **WHEN** an EPUB whose two spine documents hold 0 and 2 segments is translated with after-section enabled
- **THEN** the job pauses once, after the second spine document

#### Scenario: No pause points

- **WHEN** a book is translated with no pause points and no pause request
- **THEN** the job never pauses and ends Completed

### Requirement: Pause once per boundary, with the pause points in force

WHEN several pause points apply at the same boundary, the system SHALL pause once. It SHALL report "requested" if a
pause was requested; otherwise on-flagged when the segment just decided was flagged and on-flagged is enabled; otherwise
the widest point that applies: between-stages, then after-section, then after-segment.

WHEN the enabled pause points change while a job runs or is paused, the system SHALL apply the new set from the next
boundary.

**Source**: FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), ADR-0036. In plain words: the last segment of a book ends a segment, a section and a stage at once, and the person should see one pause. A flagged segment is named as flagged even when another point applies too, because that is what the person has to act on. "Resume and stop pausing" means clearing the pause points, then resuming.

#### Scenario: Coinciding pause points pause once

- **WHEN** a TXT book with one segment is translated with after-segment, after-section and between-stages enabled
- **THEN** the job pauses exactly once, with reason between-stages

#### Scenario: A flagged segment wins over after-segment

- **WHEN** a Manual run, with after-segment and on-flagged enabled, flags `ch01.xhtml:4`
- **THEN** the job pauses exactly once after it, with reason on-flagged

#### Scenario: Pause points cleared before resuming

- **WHEN** a job over three segments pauses after the first with after-segment enabled, and the pause points are
  cleared before it resumes
- **THEN** the job does not pause again and ends Completed

### Requirement: Pause instead of failing when an error stops the job

WHERE pause on error is enabled, IF a model call, after the retry policy, answers `ErrorCode.unreachable`, `timeout`, `auth`, `rateLimited`, `upstream`, `modelNotFound`, `modelUnavailable`, `missingCredential` or `validation`, or a step throws and so answers `ErrorCode.internal`, THEN the system SHALL pause with that error, keep every count as it stood, and on resume make the interrupted call again from its first request — except a reviewer call answered `timeout`, which flags its chunk's segments instead.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/implementation_plan/CHANGE_BACKLOG.md` (D19). In plain words: a stopped local server or an unloaded model is often fixed in a minute, and a step that threw may well succeed when tried again, so the run waits instead of failing. The window always enables this pause; the command line enables it too and stops at a pause only a person can end. Only the failed call is made again. A reviewer timeout flags as the quality-gates capability's "Flag a segment the reviewer could not review" says. The errors that end a run Failed instead, and the replies that flag one segment at once, are the translation-pipeline capability's.

#### Scenario: The model is unreachable once

- **WHEN** pause on error is enabled and, on the Fast dial, the model answers `ErrorCode.unreachable` for the second of three segments, then answers normally
- **THEN** the job pauses with `ErrorCode.unreachable`, with 1 accepted and 2 pending
- **AND** after resume the second segment is sent again and accepted, and the job ends Completed

#### Scenario: A step that throws pauses and recovers by itself

- **WHEN** pause on error is enabled and, on the Fast dial, the draft of the second of three segments throws an exception once, then answers normally
- **THEN** the job pauses once with `ErrorCode.internal`, resumes by itself, and ends Completed with three accepted

#### Scenario: A rejected request pauses

- **WHEN** pause on error is enabled and the provider answers `ErrorCode.validation` to the draft of `Book.txt:1`
- **THEN** the job pauses with `ErrorCode.validation` and waits for the person

#### Scenario: A reviewer timeout flags instead of pausing

- **WHEN** the reviewer call of the chunk holding `ch07.xhtml:40`–`ch07.xhtml:43` answers `ErrorCode.timeout`
- **THEN** the run does not pause and the chunk's segments are flagged as the reviewer being unavailable

#### Scenario: A reviewer outage pauses

- **WHEN** the reviewer call of a chunk answers `ErrorCode.unreachable`
- **THEN** the run pauses with `ErrorCode.unreachable`

#### Scenario: The export fails once

- **WHEN** a run is paused on `ErrorCode.upstream` and an export of its project then fails because the destination folder was deleted
- **THEN** the run stays paused with its counts unchanged, and the export reports its own failure without resuming or ending the run

#### Scenario: Retry now resumes the run

- **WHEN** an Unattended run from the window is paused with `ErrorCode.auth` at 412 of 1,240 segments and the person presses Try again now
- **THEN** the run resumes, the interrupted call is made again, and the counts continue from 412

#### Scenario: Stay paused keeps the run waiting

- **WHEN** the person presses nothing further on the same pause, there being no Stay paused button any more
- **THEN** the run stays paused and no request is sent

#### Scenario: An unloaded model pauses too

- **WHEN** pause on error is enabled and, on the Fast dial, LM Studio answers `400` with `{"error":"Model unloaded"}` for the second of three segments
- **THEN** the job pauses with `ErrorCode.modelUnavailable`, with 1 accepted, 0 flagged and 2 pending
- **AND** the window shows the provider-error state naming that error with Try again now

#### Scenario: A step that throws is retried, then flagged

- **WHEN** pause on error is enabled and, on the Fast dial, the model call throws an exception four times in a row for the second of three segments
- **THEN** the job pauses three times with `ErrorCode.internal`, each pause recovering by itself
- **AND** the fourth failure flags `Book.txt:1` with `ErrorCode.internal`, and the job ends Completed with two accepted

#### Scenario: A failure while the run starts ends it Failed

- **WHEN** the glossary store throws while the run prepares, before any segment
- **THEN** the run does not pause; it ends Failed as the translation-pipeline capability's "End the run Failed on an error no pause can fix" says

#### Scenario: A segment that keeps timing out is flagged after two pauses

- **WHEN** the draft of `Book.txt:1` is answered with `ErrorCode.timeout` three times in a row, and the run resumes after each of the first two pauses
- **THEN** the run pauses twice, then flags `Book.txt:1` with `ErrorCode.timeout` and drafts `Book.txt:2`
- **AND** the run ends Completed with two segments accepted and one flagged

#### Scenario: A 429 does not spend the segment's budget

- **WHEN** the draft of `Book.txt:1` is answered with `ErrorCode.rateLimited` three times in a row, then normally
- **THEN** the run pauses three times and recovers by itself each time, and `Book.txt:1` is accepted, not flagged

#### Scenario: Skipping the failing segment from the pause

- **WHEN** the run is paused because the draft of `Book.txt:1` was answered with `ErrorCode.upstream`, and the person skips the segment
- **THEN** `Book.txt:1` is flagged with `ErrorCode.upstream`, its draft is not sent again, and `Book.txt:2` is drafted next

### Requirement: Cancel a job

WHEN cancellation is requested, the system SHALL end the job Cancelled without waiting for the segment in progress or
for the provider, and SHALL keep every decided segment stored, so a new run for the same project in the same session
starts at the first PENDING segment. If a model request is in flight, the system SHALL abort it and SHALL NOT send any
further request. If the job is paused it SHALL end at once.

IF the thread running a paused job is interrupted, THEN the system SHALL treat it as a cancellation.

IF cancellation is requested before the job runs, THEN running it SHALL end the job Cancelled at once, without reading
the opened book the project holds or calling the model.

**Source**: FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0034. In plain words: stopping is safe and is not an error. A stop does not wait for a slow provider: the request that is waiting is aborted and nothing more is sent. What was decided before the stop is kept for the rest of the session, which is what lets a stopped run be resumed; drafts of the current chunk that were not yet decided are dropped, and those segments stay PENDING and are drafted again by the next run (the translation-pipeline capability's "Decide a chunk's segments in document order"). A job cancelled before it starts does no work at all.

#### Scenario: Cancel while paused

- **WHEN** a job over three segments is paused after the first and cancellation is requested
- **THEN** the job ends Cancelled with 1 accepted and 2 pending, and no file is written

#### Scenario: Cancel during a slow request

- **WHEN** the provider is taking `30` seconds to answer the second of three segments' request on the Fast dial, and
  cancellation is requested `1` second after that request was sent
- **THEN** the job ends Cancelled within `5` seconds with 1 accepted and 2 pending, and the provider has received
  exactly `2` requests in total

#### Scenario: Decisions survive a cancel

- **WHEN** a job over three segments is cancelled after the first is accepted, and a new job is run for the same project
- **THEN** the new job's first model call is for the second segment

#### Scenario: Interrupt while paused

- **WHEN** the thread running a paused job is interrupted
- **THEN** the job ends Cancelled

#### Scenario: Cancel before the job runs

- **WHEN** cancellation is requested on a job before it runs, and the job is then run
- **THEN** the job ends Cancelled with no model call

### Requirement: Report a pause or a stop as requested until the engine acts on it

WHEN a pause or a stop is requested, the application SHALL immediately make that control unavailable and report
the run as pausing or as stopping, and SHALL leave the run in that reported state until the engine reports the
run paused or the run returns.

WHILE a run is pausing or stopping, the screen SHALL keep reporting the counts as they continue to advance, and
SHALL NOT report the run as paused or stopped.

**Source:** FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, `docs/next_features.md` §14.
In plain words: a pause or a stop is not instant. The engine aborts the model request in flight (see "Pause and stop
act without waiting for the provider" in the `translation-pipeline` capability) and then reaches a safe point, so
there is a short visible gap in which the run is still winding down and the person has already asked it to stop.
The reported state is exactly what makes that gap legible instead of looking like a dead button. Without a state for
it the screen has two bad options: claim the run is paused while it is still calling the model, or leave the button
live so it can be pressed four more times. A requested-but-not-yet-effective state is the honest third one.

#### Scenario: A requested pause reads as pausing, not paused

- **WHEN** a pause is requested during a model call that has not returned
- **THEN** the screen reports the run as pausing, the pause control is unavailable, and the stop control stays
  available
- **AND** the run is not reported as paused

#### Scenario: Counts keep moving while a pause is pending

- **WHEN** a pause is pending and the segment in flight is accepted, taking the accepted count from `412` to
  `413`
- **THEN** the screen reports `413` accepted while still reporting the run as pausing

#### Scenario: The engine's report ends the pending state

- **WHEN** a pause is pending and the engine reports the run paused
- **THEN** the screen reports the run as paused and offers resume and stop

#### Scenario: A requested stop reads as stopping until the run returns

- **WHEN** a stop is requested during a model call that has not returned
- **THEN** the screen reports the run as stopping and the stop control is unavailable
- **WHEN** the run then returns reporting the run as cancelled
- **THEN** the screen reports the run as stopped

### Requirement: Pause for review as the review mode says

The system SHALL pause a run started from the window on a model-call error in every review mode, and SHALL add the mode's own pause points: none for Never, each flagged segment for When something needs review, every decided segment for After every segment. The mode is read when each run starts, as the translation-pipeline capability's "Read the review mode at the start of each run" says.

A pause on a flagged segment or after a segment SHALL name the segment just decided and SHALL come before any further model call.

**Source**: FR-REVIEW-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, ADR-0036, ADR-0045. In plain words: the default runs to the end and leaves review for afterwards; a careful translator can have it stop on every problem or every segment. A stopped server pauses the run in every mode. The command line always runs Unattended with the same pause on errors and the same recovery from an outage, and stops the run at a pause only a person can end. A segment is decided only after its whole chunk is drafted and, on Balanced and Max, reviewed, so the pause comes after that chunk's calls. Naming the segment lets the review desk open on the right one.

#### Scenario: Unattended runs past a flagged segment

- **WHEN** an Unattended run flags `ch07.xhtml:41` and continues
- **THEN** the run does not pause, and `ch07.xhtml:41` is listed in the review desk

#### Scenario: Unattended from the window pauses on a provider error

- **WHEN** an Unattended run started from the window gets `ErrorCode.unreachable` for the draft call of `ch07.xhtml:42`
- **THEN** the run pauses with `ErrorCode.unreachable` and the screen shows the provider-error state

#### Scenario: Assisted on Fast stops on the flagged segment

- **WHEN** an Assisted run on the Fast dial flags `ch07.xhtml:41`
- **THEN** the run pauses because a segment was flagged, naming `ch07.xhtml:41`, before any call for `ch07.xhtml:42`

#### Scenario: Assisted on Balanced stops after its chunk was reviewed

- **WHEN** an Assisted run on the Balanced dial packs `ch07.xhtml:40`–`ch07.xhtml:43` into one chunk and flags `ch07.xhtml:42`
- **THEN** the run pauses because a segment was flagged, naming `ch07.xhtml:42`, after the draft calls of all four segments and the chunk's reviewer call
- **AND** no model call is made for `ch07.xhtml:43` until the run resumes

#### Scenario: Manual stops after an accepted segment

- **WHEN** a Manual run accepts `ch01.xhtml:0`
- **THEN** the run pauses after that segment, naming `ch01.xhtml:0`, before any call for `ch01.xhtml:1`

#### Scenario: The mode is read from the setting

- **WHEN** "When to stop and ask you" is set to After every segment, no launch flag is set, and a run is started from the window
- **THEN** the run pauses after each decided segment, as Manual does

### Requirement: Continue after the person acts on the paused segment

WHEN a run paused for review is resumed, the system SHALL continue with the undecided segments after the paused one,
SHALL make no further model call for the paused segment, and SHALL keep the paused segment's stored decision — the
person's edit, when they made one — as it stands.

**Source**: FR-REVIEW-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-RESUME-03 (`#fr-resume`), ADR-0036. In plain words: what the person decided during the pause is final for the run — the segment is not drafted or repaired again — and the sentences translated after it follow the corrected one, not the model's rejected draft. Which sentences those are is the translation-pipeline capability's "Decide a chunk's segments in document order": with the reviewer off (Fast) or in Manual it is the very next segment; with the reviewer on, the rest of the paused chunk was already drafted and reviewed before the pause and is not drafted again, so the edit reaches the next chunk.

#### Scenario: An edit feeds the next draft

- **WHEN** an Assisted run on the Fast dial is paused on the flagged `ch07.xhtml:41`, the person saves the edit
  `Він рвучко відчинив двері.`, and the run is resumed
- **THEN** the draft call for `ch07.xhtml:42` carries `Він рвучко відчинив двері.` as a preceding target
- **AND** no further call is made for `ch07.xhtml:41`

#### Scenario: An edit inside a reviewed chunk feeds the next chunk

- **WHEN** an Assisted run on the Balanced dial is paused on the flagged `ch07.xhtml:42` inside the chunk
  `ch07.xhtml:40`–`ch07.xhtml:43`, the person saves the edit `Він рвучко відчинив двері.`, and the run is resumed
- **THEN** `ch07.xhtml:43` is decided from the draft it already had, with no new draft call for it
- **AND** the draft call for `ch07.xhtml:44` carries `Він рвучко відчинив двері.` as a preceding target

### Requirement: Record whether a run is running, paused or ended

The system SHALL store, for each run of a project, its current state — running from its start, paused at each pause, running again at each resume, and completed, stopped or failed when it ends — and SHALL let the rest of the application read the state of a project's latest run, also after a restart.

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11". In plain words: a review retry needs the model, which a running run is using and a paused or finished one is not, so the current state is kept on every pause and resume (see the review-queue capability's "Allow a retry whenever no run of the project is running"). Because it is stored, a run that was in flight when the application closed is recognised as interrupted ("Treat a run stored as running or paused as interrupted").

#### Scenario: The state follows pauses and resumes

- **WHEN** an Assisted run of `Book.md` starts, pauses on the flagged `Book.md:1`, is resumed and ends
- **THEN** its recorded state is running, then paused, then running, then completed

#### Scenario: A stopped run is recorded as stopped

- **WHEN** a running run is stopped and the engine reports it cancelled
- **THEN** its recorded state is stopped

#### Scenario: A failed run is recorded as failed

- **WHEN** the glossary store throws while the run of `Book.md` prepares, and the run ends Failed with `ErrorCode.internal`
- **THEN** its recorded state is failed

### Requirement: Pause, stop and resume a run from the screen

WHILE a run is in progress, the translating screen SHALL offer to pause it and to stop it. WHILE a run is paused, it SHALL offer to resume it and to stop it and, unless the pause is on a model-call error, SHALL show the paused line saying the segments in flight are translated again on resume. The title bar's single Pause or Resume control SHALL do what the screen's control of the same name does.

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`, `docs/specification/mockups/README.md#run-states-a`. In plain words: Pause is a short break inside this translation; Stop ends translating for now. Everything decided is saved either way, so the old promise "kept until the application closes" is gone, and the paused line tells the person what resume will redo.

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

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`, `docs/specification/mockups/README.md#run-states-a`. In plain words: stopping is something a person chose, so telling them it went wrong is both untrue and unpleasant. Decisions are saved, so a stopped run starts a new run at the first undecided segment, today or after a restart.

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

### Requirement: Continue a segment's repair rounds at the call that failed

WHEN a self-heal call is aborted by a pause, or is answered with an error that pauses the run, the system SHALL keep
the segment's rounds as they stood in memory and, on resume, SHALL continue at the call that failed, keeping the
chunk's reviewer verdict and making no second reviewer call for the chunk. WHEN a run is stopped, the system SHALL drop
the chunk's undecided drafts, as "Decide a chunk's segments in document order" says.

**Source**: FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `openspec/changes/archive/2026-10-19-complete-translation-workflow/proposal.md#what-changes`; tasks 15b. In plain words: the rounds were once restarted from the first, which on the Bartimaeus hand test paid for the same directed fix again after every resume. The rounds already answered are kept while the application runs, so a resume costs only the call that failed. What the run already paid for at the chunk level — the reviewer's verdict — is kept too, so a pause never costs a second reviewer call for the chunk (a repaired target is decided by the checks alone, as the quality gates say).

#### Scenario: A pause during the second repair round

- **WHEN** a Balanced run drafts `He opened the old door.` and gets the echo `HE OPENED THE OLD DOOR.`, which the checks refuse
  so no reviewer call is made, the first directed fix is answered with `Він відчинив the old door.` (one blocker fewer, so it is kept), a pause is requested while the
  second directed fix is in flight, and after resume the second directed fix is answered with
  `Він відчинив старі двері.`
- **THEN** the provider has received 3 requests before the pause — draft, first fix, second fix
- **AND** it has received 4 in total — the second fix again — and the segment ends ACCEPTED after 2 rounds

#### Scenario: An unreachable provider during the second repair round

- **WHEN** the same run's second directed fix is answered with `ErrorCode.unreachable` with pause on error enabled, and
  after resume it is answered with `Він відчинив старі двері.`
- **THEN** the run pauses with `ErrorCode.unreachable` after 3 requests
- **AND** it has received 4 in total

#### Scenario: A failed directed fix for a refused edit is the only call sent again

- **WHEN** the reviewer answered an edit whose result the checks refuse, the directed fix for it is answered with
  `ErrorCode.upstream` with pause on error enabled, and after resume the fix is answered with a text that no longer
  holds the quote
- **THEN** the requests are draft, draft, reviewer, fix and fix — the reviewer is not asked again — and the segment ends
  ACCEPTED with the fixed target
- **AND** the second fix is sent with the same verified edits as the first

#### Scenario: A stop drops the chunk's undecided drafts

- **WHEN** a Balanced run has drafted the chunk `ch07.xhtml:40`–`ch07.xhtml:43`, decided `ch07.xhtml:40` and
  `ch07.xhtml:41`, and is stopped
- **THEN** `ch07.xhtml:42` and `ch07.xhtml:43` stay PENDING with their drafts dropped, and the next run drafts them
  again

### Requirement: Recover from a provider error by itself

WHILE a run is paused on an error whose recovery is automatic (an outage, a timeout, an error a step threw, or an unloaded model) and the step has pauses of that kind left, the system SHALL wait and resume by itself, in every review mode, as soon as a probe of the provider passes, the interrupted call being made again. A pause for review, a pause the person asked for and a pause on an error only a person can fix SHALL NOT resume by itself.

**Source**: `docs/specification/mockups/README.md#run-states-a`, `docs/specification/mockups/README.md#words-w--user-facing-vocabulary`, `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`. In plain words: a run left all night is finished in the morning or visibly retrying with a clear reason, never silently stuck. The run waits, checks the server now and then without asking the model anything, and goes on by itself; a wrong key or a missing model still waits for the person. A server that unloaded an idle model usually loads it again on the next request, so the run wakes at most six times for one such outage — about nineteen minutes — and then asks the person to load it ("Stop waking after a twelve-hour outage or six wakes of an unloaded model"); its budget of ten pauses before the step is flagged is a separate count.

#### Scenario: Retry now keeps the outage clock and the segment's budget

- **WHEN** the provider is down and the person presses Try again now at each of eleven waits, each call failing again with `ErrorCode.unreachable`, and the twelfth call answers
- **THEN** every wait says the provider is down since the first failure, each wait starts a fresh wake schedule at its first wake, and the segment is accepted, not flagged after its tenth pause

#### Scenario: A twenty-minute outage resumes at the seventh wake

- **WHEN** the draft of the second of three segments is answered `ErrorCode.unreachable` and the provider stays down for 20 minutes
- **THEN** the wakes at 0:15, 0:45, 1:45, 3:45, 8:45 and 18:45 find the provider down, and the seventh wake, 28 min 45 s after the failure (attempt 8 counting the failed call), finds it back and resumes the run
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
- **THEN** the wait ends at once and the wake schedule starts afresh, but the time the outage began is kept, because only an answered model call clears it
- **AND** the call sent again on the person's word during an outage or an unloaded model does not count toward the pauses after which the step is flagged, whereas after a timeout or a throwing step it still counts

#### Scenario: Each step of the recovery is announced and logged

- **WHEN** the run recovers by itself
- **THEN** the screen is told the run is waiting (with the next try's time and attempt), has stopped trying, or is held, and the outage's start, each wait, each probe's result and the resume after N minutes are logged at INFO

### Requirement: Stop waking after a twelve-hour outage or six wakes of an unloaded model

An outage SHALL last from its first failure until a model call answers again; once it has lasted 12 hours (`RecoverySchedule.MAX_OUTAGE`) the system SHALL stop waking and wait for the person. An unloaded model SHALL wake at most six times in one outage (`PauseDecider.UNLOADED_MODEL_WAKES`), counted across its pauses until a model call answers again, after which the system SHALL announce that it gave up and wait for the person.

**Source**: `docs/implementation_plan` overnight plan step 11 (owner intent: a run left all night is finished in the morning or visibly retrying with a clear reason, never silently stuck). In plain words: a local server that restarts, a laptop that loses its network for a while, or a provider that rate limits are common over a night. The run waits, checks the server now and then without asking the model anything, and goes on by itself; a wrong key or a missing model is not fixed by waiting, so those still wait for the person. A local server that unloaded an idle model usually loads it again on the next request, so the run tries six times — about nineteen minutes — and then asks the person to load it, instead of probing a model nobody loads all night.

#### Scenario: An outage longer than twelve hours waits for the person

- **WHEN** the provider stays down for two days
- **THEN** the run wakes 77 times, and after the wake at 12 h 8 min 45 s it announces that it gave up and stays paused
  until the person resumes or stops it

#### Scenario: A model that never loads gives up after six wakes

- **WHEN** every draft call is answered `ErrorCode.modelUnavailable` while every probe passes
- **THEN** the run wakes six times, pausing seven times in all, flags nothing, and announces that it gave up after the
  sixth wake
- **AND** the window shows "The model is not available" asking the person to load it and press Resume

#### Scenario: The window says how to load the model

- **WHEN** the system gave up on an unloaded model
- **THEN** the window says "The model is not available — load it in the provider at <host> and press Resume"

### Requirement: Reopen an unfinished project as ready to continue

WHEN a project that has at least one decided segment and at least one pending segment is opened after a restart, the system SHALL show it as ready to continue: the title bar reading the share of its steps done, the banner "Welcome back — 74 of 142 segments are done." with that project's figures, the live line naming where it continues, and Continue translation in the toolbar. A project with no pending segment SHALL NOT be shown as ready to continue.

**Source**: FR-RESUME-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/specification/mockups/README.md#run-states-a`. In plain words: after closing the application a person sees at once how far an unfinished translation got and what the button will do; a finished one has nothing to continue.

#### Scenario: A project reopens ready to continue

- **WHEN** a project with 74 of 142 segments decided and 51 % of its steps done is opened after a restart
- **THEN** the title bar reads `Ready · 51 % done`
- **AND** the banner reads `Welcome back — 74 of 142 segments are done.` and the toolbar offers Continue translation

#### Scenario: A finished project is not ready to continue

- **WHEN** a project whose 142 segments are all decided is opened after a restart
- **THEN** no `Welcome back` banner is shown and the toolbar offers Continue to Export, not Continue translation

### Requirement: Never continue a translation by itself

WHEN a project is opened, the system SHALL NOT start or resume a run until the person chooses Continue translation, whatever state the project was stored in.

**Source**: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/specification/mockups/README.md#run-states-a`. In plain words: a model call can take a computer's whole attention, so opening a project to look at it must never begin using the AI model without being asked.

#### Scenario: Opening does not call the model

- **WHEN** a project stored as running is opened and the person does nothing further
- **THEN** no model call is made and the project is ready to continue

### Requirement: Continue at the first segment not yet decided

WHEN the person chooses Continue translation on a reopened project, the system SHALL start a new run whose first model call is for the first pending segment, announced as the live line "Continues at Chapter 2, segment 5." before the call.

**Source**: FR-RESUME-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md#run-states-a`. In plain words: decided segments are never translated twice, and the person sees where the work picks up.

#### Scenario: Continue picks up at Chapter 2, segment 5

- **WHEN** Chapter 1 and segments 1 to 4 of Chapter 2 are decided and Continue translation is chosen
- **THEN** the live line reads `Continues at Chapter 2, segment 5.` and the first model call is for segment 5 of Chapter 2

### Requirement: Treat a run stored as running or paused as interrupted

WHEN a project whose last run was stored as running or paused is opened, the system SHALL treat that run as interrupted and show the project as ready to continue, whether the application was closed cleanly or ended by a crash.

**Source**: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", FR-PERSIST-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`). In plain words: a run that was in flight when the window closed or the process died is not "still running" on the next launch; it simply stopped, and the decided segments are all kept.

#### Scenario: A crash during a run

- **WHEN** the process is killed while a run is stored as running with 74 of 142 segments decided, and the project is opened
- **THEN** the run is read as interrupted, the project is ready to continue and Continue translation is offered

#### Scenario: A paused run after a quit

- **WHEN** the application is closed while a run is paused with 74 of 142 segments decided, and the project is opened
- **THEN** the project is ready to continue and the paused run is not shown as running

### Requirement: Recompute the progress of a reopened project from its stored decisions

WHEN a project is opened after a restart, the system SHALL make the run's step plan again from the book and the brief and count as done the draft of every decided segment and the checks of every chunk whose segments are all decided; it SHALL show no time left until the new run has timed its own steps.

**Source**: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D3", `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`. In plain words: the steps are not stored, but the decisions are, so the bar after a restart starts where the work really stands; the speed of the earlier run is not measured in this one, so no time left is guessed.

#### Scenario: The bar starts from the stored decisions

- **WHEN** a Balanced project of 40 segments, packed into 5 chunks of 8, is opened after a restart with the 16 segments of its first 2 chunks decided
- **THEN** 18 of 45 steps count as done, the title bar reads `Ready · 40 % done` and the bar's tooltip reads `18 of 45 steps, checks included`

#### Scenario: Time left waits for the new run

- **WHEN** the same project is opened and Continue translation is chosen
- **THEN** no time left is shown on opening, and it appears only once the new run has timed its own steps

### Requirement: Ask when the stored AI model does not answer

WHEN the person chooses Continue translation and the AI model the project's stored run used does not answer, the system SHALL show the dialog "AI model not answering" with Try again, Use another AI model… and Not now, and SHALL start no run until the person has chosen. "Does not answer" means the model's program fails to list its models, or lists some and not this one (a listing, never a model call); a program that lists none counts as answering. The stored model is read from the project's latest run each time Continue is chosen. Escape counts as Not now. Try again here asks only that one model again: it does not look for programs and saves nothing (it is not the Projects screen's Try again).

**Source**: FR-PROV-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/specification/mockups/README.md#other-dialogs`. In plain words: instead of failing on the first call, the person is told right away that Ollama is not open and can fix that, pick another model, or leave.

#### Scenario: Ollama is not open

- **WHEN** the project's stored run used Ollama · `gemma4:e4b`, the same model is current, Continue translation is chosen, and nothing answers at `http://127.0.0.1:11434`
- **THEN** the dialog `AI model not answering` offers Try again, Use another AI model… and Not now
- **AND** no run has started

#### Scenario: Not now leaves the project as it was

- **WHEN** Not now is chosen
- **THEN** the project stays ready to continue and no model call is made

#### Scenario: Try again continues once the model answers

- **WHEN** Ollama is opened and Try again is chosen
- **THEN** the run starts with `gemma4:e4b` at the first pending segment

### Requirement: Ask which model to use when a different one is chosen

WHEN the person chooses Continue translation and the current AI model differs from the one the project's stored run used, the system SHALL show the dialog "A different model is chosen" with Use the earlier model and Continue with the new one, and SHALL start the run only on one of them. This question comes first; the answer test of the model that was chosen follows it. Escape starts nothing. Use the earlier model leaves the current model unchanged.

**Source**: FR-PROV-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/specification/mockups/README.md#other-dialogs`. In plain words: continuing a half-translated book with another model can change its voice, so the person decides whether to keep the earlier model or accept the change.

#### Scenario: The earlier model is kept

- **WHEN** the project's stored run used `gemma4:e4b`, `qwen3:8b` is now current, and Use the earlier model is chosen
- **THEN** the run starts with `gemma4:e4b`

#### Scenario: The new model is accepted

- **WHEN** Continue with the new one is chosen in the same situation
- **THEN** the run starts with `qwen3:8b`

#### Scenario: The same model asks nothing

- **WHEN** the project's stored run used `gemma4:e4b`, `gemma4:e4b` is current and answers, and Continue translation is chosen
- **THEN** no dialog opens and the run starts with `gemma4:e4b`

### Requirement: Flag a step after its budget of pauses

The system SHALL count the pauses of each step — a segment's draft, a segment's decision, or a chunk's reviewer call — by the kind of its error and, on the failure past that kind's budget, flag the step's segment with the error and go on: two pauses for a timeout and for the errors only a person can fix, three for an error the step threw, and ten for an outage and for an unloaded model.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md#run-states-a`. In plain words: one bad segment must not end a night's run, while an outage, not the segment's fault, has its own larger budget. The errors only a person can fix are `auth`, `modelNotFound`, `missingCredential` and `validation`; an outage is `unreachable`, `upstream` or `rateLimited`; an unloaded model is `modelUnavailable`; a step that throws answers `ErrorCode.internal`. For a chunk's reviewer call, flagging means deciding the chunk as if the reviewer were unavailable. A call not tied to one segment has no step to flag, so past its budget it waits for the person.

#### Scenario: An error only a person can fix is flagged after two

- **WHEN** the draft of `ch07.xhtml:42` answers `ErrorCode.auth`, the person resumes after each of two pauses, and it answers `ErrorCode.auth` a third time
- **THEN** `ch07.xhtml:42` is flagged with `ErrorCode.auth` and the run goes on

### Requirement: Offer to skip the step a pause is stuck on

WHILE the run is paused on a model-call error, the job SHALL offer to skip the step: the run resumes, the step's segments are flagged with the error without their call being sent again, and the run goes on.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md#words-w--user-facing-vocabulary`. In plain words: the person can give up on segments that keep failing and let the book continue, instead of being stuck.

#### Scenario: Skipping four segments

- **WHEN** a run is paused on `ErrorCode.modelNotFound` for segments 5–8 and the person chooses Skip these 4 segments
- **THEN** segments 5–8 are flagged with `ErrorCode.modelNotFound`, their call is not sent again, and the run goes on with segment 9

### Requirement: Show the provider-error state with its three actions

WHILE a run from the window is paused on a model-call error, the translating screen SHALL show the provider-error state naming the error, with Try again now (which resumes the run), Open settings, and Skip these N segments, N being the count in flight.

**Source**: `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/mockups/README.md#words-w--user-facing-vocabulary`, `docs/specification/mockups/README.md#run-states-a`. In plain words: the screen says what went wrong and offers the three things a person can do; leaving the run paused needs no button, because the toolbar's Pause already does it. The banner's wording per state is the notifications capability's.

#### Scenario: The three actions are shown

- **WHEN** a run is paused on `ErrorCode.unreachable` with segments 5–8 in flight
- **THEN** the screen offers Try again now, Open settings and Skip these 4 segments
