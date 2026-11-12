# resume Specification

## Purpose
Lets a running translation job pause, resume or stop at safe points, either when asked or at pause points the caller
enables, without losing or repeating decided segments. It also covers continuing a stored project after a restart: an
unfinished project reopens ready to continue at the first undecided segment, and a run stored as running or paused is
treated as interrupted. Proofread windows already holding the same fingerprint are skipped; repair and Proofread calls
are otherwise made again.

## Requirements

### Requirement: Pause on request at the next boundary

WHEN a pause is requested while a model request is in flight, the system SHALL abort that request without waiting for the provider to answer, send no further request (not a retry, a repair or a proofread call), and pause with the reason "requested", reporting no error; a reply that had already arrived before the request was made is still decided normally.

**Source**: FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `docs/spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0035, ADR-0038, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: pausing does not wait for a slow model. The request that is waiting is aborted at once and nothing more is sent, so the Pause button acts within seconds even when a local model takes minutes.

#### Scenario: Pause during a slow request

- **WHEN** a Fast job over three segments has its first segment accepted, the provider is taking `30` seconds to answer
  the second segment's request, and a pause is requested `1` second after that request was sent
- **THEN** the job pauses with reason requested within `5` seconds, with 1 accepted and 2 pending and no error, and the
  provider has received exactly `2` requests in total
- **AND** after resume the second segment is requested again from its first request, and the job ends Completed with 3
  accepted and the provider having received `4` requests in total

### Requirement: Make the aborted call again when a paused job resumes

WHILE a job is paused, the system SHALL make no model call; on resume it SHALL make the aborted call again from its first request, whether a segment's draft or repair, a chapter summary or a Proofread window, and then continue where the run stood.

**Source**: FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `docs/spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0035, ADR-0038, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the call that was aborted has no answer yet, so resuming makes it again rather than skipping it; a segment already decided is never redone, and a draft already made for the chunk is not made twice (the translation-pipeline capability's "Keep a chunk's drafts across a pause and drop them on stop").

#### Scenario: A pause during a Proofread call

- **WHEN** a Balanced job (batch size 1) over three segments has drafted all three and repaired `Book.txt:2` once, so
  that segment carries a proofread mark, and a pause is requested while the Proofread call for the window around
  `Book.txt:2` is in flight
- **THEN** the job pauses with reason requested, with 3 accepted and 0 pending, and the provider has received `5`
  requests in total: 3 drafts, 1 repair and 1 Proofread call
- **AND** after resume the Proofread call is made again and no draft or repair is repeated, and the job ends Completed
  with the provider having received `6` requests in total

### Requirement: Pause at the next boundary when no request is in flight

WHEN a pause is requested while no model request is in flight, the system SHALL pause at the next boundary, which is before the next model call or before the job ends, with the reason "requested"; a pause requested before the job runs SHALL take effect at the first boundary, before the first model call.

**Source**: FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `docs/spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0035, ADR-0038, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a screen may ask for a pause before it starts the job, and that request must not be lost. The run no longer writes the book, so its last boundary is the end of the job.

#### Scenario: Pause after the first of three segments

- **WHEN** a Fast job over three segments is asked to pause as soon as the first segment is decided
- **THEN** the job pauses with reason requested, 1 accepted and 2 pending, after exactly 1 model call
- **AND** after resume it ends Completed with exactly 3 model calls in total

#### Scenario: A pause after the last decision

- **WHEN** a pause is requested after the last segment is decided and before the job ends
- **THEN** the job pauses with reason requested and 0 pending, and after resume ends Completed with no file written

#### Scenario: A pause requested before the job runs

- **WHEN** a pause is requested on a job over three segments before it runs, and the job is then run
- **THEN** the job pauses with reason requested before any model call, with 0 accepted and 3 pending

### Requirement: Never pause the export

The export, which is a separate action, SHALL never be paused by a pause request on the run.

**Source**: FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `docs/spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0035, ADR-0038, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the export makes no model call and takes seconds, so there is nothing to wait for, and a half-written book would be worse than a finished one.

#### Scenario: A pause during export is ignored

- **WHEN** a run has ended Completed, the person exports its project, and a pause is requested on that run while the
  export writes `Book.uk.epub`
- **THEN** nothing pauses and the export writes `Book.uk.epub`

### Requirement: Pause at enabled pause points

WHERE the caller enables pause points, the system SHALL pause at each enabled point: after-segment (after each decided segment), on-flagged (after each segment decided FLAGGED), after-section (after the last segment of each section) and between-stages (after the last segment of the Translate stage, before the Proofread stage or the end of the job). A pause at after-segment or on-flagged SHALL name the segment just decided.

**Source**: FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/spec_baseline/specification/02_Architecture/03_DOCUMENT_MODEL.md#data-model`, ADR-0036, ADR-0041, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: a screen can let the person look at each flagged segment, each segment, each section, or the whole translation before it is proofread. With nothing enabled a job never waits, which is how the command line runs.

#### Scenario: Pause on a flagged segment

- **WHEN** a book of three segments is translated with on-flagged enabled and only `Book.md:1` is flagged
- **THEN** the job pauses once, naming `Book.md:1`, with 1 accepted, 1 flagged and 1 pending

#### Scenario: Pause between stages

- **WHEN** a Balanced book (batch size 1) of three segments, one of them accepted with a proofread mark, is translated with between-stages enabled
- **THEN** the job pauses with 0 pending segments and no Proofread call made
- **AND** after resume the marked window is read with exactly 1 Proofread call, the job ends Completed and no file is written

#### Scenario: Pause between stages at Fast

- **WHEN** a Fast book of three segments is translated with between-stages enabled
- **THEN** the job pauses with 0 pending segments before the code-only Proofread stage, and after resume ends Completed with no Proofread call

#### Scenario: No pause points

- **WHEN** a book is translated with no pause points and no pause request
- **THEN** the job never pauses and ends Completed

### Requirement: Count a section only as a whole body unit

A section SHALL be one EPUB spine document, one FB2 body, or a whole Markdown or TXT file; the book's auxiliary text, its title, navigation labels, page titles and image descriptions, is not a section, and a section with no segments SHALL cause no pause.

**Source**: FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/spec_baseline/specification/02_Architecture/03_DOCUMENT_MODEL.md#data-model`, ADR-0036, ADR-0041, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: the book's title and labels are translated after its body; they are not a chapter the person reads through, so finishing them is no section's end.

#### Scenario: Pause after each section of an EPUB

- **WHEN** an EPUB whose two spine documents hold 2 and 1 segments is translated with every "Also translate" switch off
  and after-section enabled
- **THEN** the job pauses twice: first with 2 accepted and 1 pending, then with 3 accepted and 0 pending

#### Scenario: The auxiliary text is not a section

- **WHEN** an EPUB whose two spine documents hold 2 and 1 segments, and whose only auxiliary segment is its title, is
  translated with the metadata switch on and after-section enabled
- **THEN** the job pauses twice: first with 2 accepted and 2 pending, then with 3 accepted and 1 pending
- **AND** after the title is decided the job ends Completed without pausing again

#### Scenario: An empty section causes no pause

- **WHEN** an EPUB whose two spine documents hold 0 and 2 segments is translated with after-section enabled
- **THEN** the job pauses once, after the second spine document

### Requirement: Pause once per boundary, with the pause points in force

WHEN several pause points apply at the same boundary, the system SHALL pause once. It SHALL report "requested" if a
pause was requested; otherwise on-flagged when the segment just decided was flagged and on-flagged is enabled; otherwise
the widest point that applies: between-stages, then after-section, then after-segment.

WHEN the enabled pause points change while a job runs or is paused, the system SHALL apply the new set from the next
boundary.

**Source**: FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), ADR-0036. In plain words: the last segment of a book ends a segment, a section and a stage at once, and the person should see one pause. A flagged segment is named as flagged even when another point applies too, because that is what the person has to act on. "Resume and stop pausing" means clearing the pause points, then resuming.

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

WHERE pause on error is enabled, IF a model call, after the retry policy, answers `ErrorCode.unreachable`, `timeout`, `auth`, `rateLimited`, `upstream`, `modelNotFound`, `modelUnavailable`, `missingCredential` or `validation`, or a step throws and so answers `ErrorCode.internal`, THEN the system SHALL pause with that error, keep every count as it stood, and on resume make the interrupted call again from its first request.

**Source**: `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/spec_baseline/implementation_plan/CHANGE_BACKLOG.md` (the backlog item that made a run pause instead of fail), `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: a stopped local server or an unloaded model is often fixed in a minute, and a step that threw may well succeed when tried again, so the run waits instead of failing. The window always enables this pause; the command line enables it too and stops at a pause only a person can end. Only the failed call is made again. The errors that end a run Failed instead, and the replies that flag one segment at once, are the translation-pipeline capability's.

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

#### Scenario: A Proofread timeout leaves the window unread instead of pausing

- **WHEN** the Proofread call for the window around `ch07.xhtml:42` answers `ErrorCode.timeout` after the window's budget of pauses is spent
- **THEN** the run does not pause again: the window is left unread with its marks kept, and the run goes on

#### Scenario: A Proofread outage pauses

- **WHEN** the Proofread call for the window around `ch07.xhtml:42` answers `ErrorCode.unreachable`
- **THEN** the run pauses with `ErrorCode.unreachable`, and after resume the Proofread call is made again from its first request

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

### Requirement: Carry the provider's reason on an error pause

WHEN the provider's answer to a call that stops the job gives a reason, the system SHALL carry that reason on the pause, and the reason SHALL decide whether the pause ends by itself ("Recover from a provider error by itself").

**Source**: `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: a reason is a word such as out-of-memory, model-loading or no-model-loaded that the provider's answer carries; a model still loading recovers by itself, while too little memory waits for the person to free some.

#### Scenario: Too little memory pauses and waits for the person

- **WHEN** pause on error is enabled and the draft of `Book.txt:2` is answered with HTTP 500 `model requires 14.2 GiB but only 6.1 GiB are available`
- **THEN** the job pauses with `ErrorCode.modelUnavailable` and the reason out-of-memory, naming 14.2 GiB and 6.1 GiB, and waits for the person

#### Scenario: A model still loading pauses and recovers

- **WHEN** pause on error is enabled and llama-server answers the draft of `Book.txt:2` with HTTP 503 `Loading model`
- **THEN** the job pauses with `ErrorCode.modelUnavailable` and the reason model-loading, and recovers by itself once the server reports the model ready

### Requirement: Cancel a job

WHEN cancellation is requested, the system SHALL end the job Cancelled without waiting for the segment in progress or for the provider, and SHALL keep every decided segment stored, so a new run for the same project in the same session starts at the first PENDING segment. If a model request is in flight, the system SHALL abort it and SHALL NOT send any further request. If the job is paused it SHALL end at once.

**Source**: FR-RESUME-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0034. In plain words: stopping is safe and is not an error. What was decided before the stop is kept for the rest of the session, which is what lets a stopped run be resumed; drafts of the current chunk that were not yet decided are dropped, and those segments stay PENDING and are drafted again by the next run (the translation-pipeline capability's "Decide a chunk's segments in document order").

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

### Requirement: Treat an interrupted thread as a cancellation

IF the thread running a paused job is interrupted, THEN the system SHALL treat it as a cancellation.

**Source**: FR-RESUME-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`. In plain words: an interrupt is how the host stops a thread, and it must end the job cleanly instead of leaving it paused forever.

#### Scenario: Interrupt while paused

- **WHEN** the thread running a paused job is interrupted
- **THEN** the job ends Cancelled

### Requirement: End a job cancelled before it runs without any work

IF cancellation is requested before the job runs, THEN running it SHALL end the job Cancelled at once, without reading the opened book the project holds or calling the model.

**Source**: FR-RESUME-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), ADR-0034. In plain words: a job cancelled before it starts does no work at all.

#### Scenario: Cancel before the job runs

- **WHEN** cancellation is requested on a job before it runs, and the job is then run
- **THEN** the job ends Cancelled with no model call

### Requirement: Report a pause or a stop as requested until the engine acts on it

WHEN a pause or a stop is requested, the application SHALL immediately make that control unavailable and report the run as pausing or as stopping, and SHALL leave the run in that reported state until the engine reports the run paused or the run returns. WHILE a run is pausing or stopping, the screen SHALL keep reporting the counts as they continue to advance, and SHALL NOT report the run as paused or stopped.

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`. In plain words: a pause or a stop is not instant. The engine aborts the model request in flight (see "Pause and stop act without waiting for the provider" in the `translation-pipeline` capability) and then reaches a safe point, so there is a short visible gap in which the run is still winding down. The reported state makes that gap legible instead of looking like a dead button: without it the screen would claim the run is paused while it still calls the model, or leave the button live so it can be pressed four more times.

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

**Source**: FR-REVIEW-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, ADR-0036, ADR-0045, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: the default runs to the end and leaves review for afterwards; a careful translator can have it stop on every problem or every segment. A stopped server pauses the run in every mode. The command line always runs Unattended with the same pause on errors and the same recovery from an outage, and stops the run at a pause only a person can end.

#### Scenario: Unattended runs past a flagged segment

- **WHEN** an Unattended run flags `ch07.xhtml:41` and continues
- **THEN** the run does not pause, and `ch07.xhtml:41` is listed in the review desk

#### Scenario: Unattended from the window pauses on a provider error

- **WHEN** an Unattended run started from the window gets `ErrorCode.unreachable` for the draft call of `ch07.xhtml:42`
- **THEN** the run pauses with `ErrorCode.unreachable` and the screen shows the provider-error state

#### Scenario: The mode is read from the setting

- **WHEN** "When to stop and ask you" is set to After every segment, no launch flag is set, and a run is started from the window
- **THEN** the run pauses after each decided segment, as Manual does

### Requirement: Name the segment a review pause stops after and pause before any further call

A pause on a flagged segment or after a segment SHALL name the segment just decided and SHALL come before any further model call.

**Source**: FR-REVIEW-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, ADR-0036, ADR-0045, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: a batch is drafted in one call and its segments are then decided one at a time, each with its one repair where the dial spends one, so a pause on a segment comes after the batch call and the repairs of the segments up to it, never after a call for a later segment. Naming the segment lets the review desk open on the right one.

#### Scenario: Assisted on Fast stops on the flagged segment

- **WHEN** an Assisted run on the Fast dial flags `ch07.xhtml:41`
- **THEN** the run pauses because a segment was flagged, naming `ch07.xhtml:41`, before any call for `ch07.xhtml:42`

#### Scenario: Assisted on Balanced stops after its chunk was reviewed

- **WHEN** an Assisted run on the Balanced dial drafts `ch07.xhtml:40`–`ch07.xhtml:43` in one batch call, and `ch07.xhtml:42` keeps a blocking finding after its one repair and is flagged
- **THEN** the run pauses because a segment was flagged, naming `ch07.xhtml:42`, after the batch call and the one repair of `ch07.xhtml:42`
- **AND** no model call is made for `ch07.xhtml:43` until the run resumes

#### Scenario: Manual stops after an accepted segment

- **WHEN** a Manual run accepts `ch01.xhtml:0`
- **THEN** the run pauses after that segment, naming `ch01.xhtml:0`, before any call for `ch01.xhtml:1`

### Requirement: Continue after the person acts on the paused segment

WHEN a run paused for review is resumed, the system SHALL continue with the undecided segments after the paused one,
SHALL make no further model call for the paused segment, and SHALL keep the paused segment's stored decision — the
person's edit, when they made one — as it stands.

**Source**: FR-REVIEW-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), ADR-0036, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: what the person decided during the pause is final for the run — the segment is not drafted or repaired again — and the sentences translated after it follow the corrected one, not the model's rejected draft. Which sentences those are is the translation-pipeline capability's "Decide a batch's segments one at a time in document order" and "Keep a chunk's drafts across a pause and drop them on stop": the rest of the paused batch was already drafted in the batch call and is decided from those drafts, so the edit reaches the next batch.

#### Scenario: An edit feeds the next draft

- **WHEN** an Assisted run on the Fast dial with batch size 1 is paused on the flagged `ch07.xhtml:41`, the person saves the edit
  `Він рвучко відчинив двері.`, and the run is resumed
- **THEN** the draft call for `ch07.xhtml:42` carries `Він рвучко відчинив двері.` as a preceding target
- **AND** no further call is made for `ch07.xhtml:41`

#### Scenario: An edit inside a reviewed chunk feeds the next chunk

- **WHEN** an Assisted run on the Balanced dial is paused on the flagged `ch07.xhtml:42` inside the batch
  `ch07.xhtml:40`–`ch07.xhtml:43`, the person saves the edit `Він рвучко відчинив двері.`, and the run is resumed
- **THEN** `ch07.xhtml:43` is decided from the draft it already had, with no new draft call for it
- **AND** the batch call that starts at `ch07.xhtml:44` carries `Він рвучко відчинив двері.` as a preceding target

### Requirement: Record whether a run is running, paused or ended

The system SHALL store, for each run of a project, its current state — running from its start, paused at each pause, running again at each resume, and completed, stopped or failed when it ends — and SHALL let the rest of the application read the state of a project's latest run, also after a restart.

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11". In plain words: a review retry needs the model, which a running run is using and a paused or finished one is not, so the current state is kept on every pause and resume (see the review-queue capability's "Allow a retry whenever no run of the project is running"). Because it is stored, a run that was in flight when the application closed is recognised as interrupted ("Treat a run stored as running or paused as interrupted").

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

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/spec_baseline/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`, `docs/spec_baseline/specification/mockups/README.md#run-states-a`. In plain words: Pause is a short break inside this translation; Stop ends translating for now. Everything decided is saved either way, so the old promise "kept until the application closes" is gone, and the paused line tells the person what resume will redo.

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

WHEN a run is stopped, the screen SHALL report a neutral outcome, not a failure, and SHALL NOT show an error dialog or a message of error severity. WHILE a run is stopped, the screen SHALL offer Continue translation, which continues the translation with a new run at the stage that has work ("Continue the stage that has work"), SHALL keep the flagged segments in the review desk, and SHALL show the stopped line saying where it continues.

**Source**: FR-RESUME-03, FR-RESUME-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/spec_baseline/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`, `docs/spec_baseline/specification/mockups/README.md#run-states-a`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: stopping is something a person chose, so telling them it went wrong is both untrue and unpleasant. Decisions and proofread verdicts are saved, so a stopped run starts a new run at the stage where the work stands, today or after a restart.

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

#### Scenario: A run stopped in Proofread says so

- **WHEN** a Balanced run is stopped in its Proofread stage with 40 of 120 windows read
- **THEN** the screen shows `Stopped. Continues with Proofread at window 41 of 120.` and offers Continue translation

### Requirement: Recover from a provider error by itself

WHILE a run is paused on an error whose recovery is automatic (an outage, a timeout, an error a step threw, or a model not loaded or still loading) and the step has pauses of that kind left, the system SHALL wait and resume by itself, in every review mode, as soon as a probe of the provider passes. A pause for review, a pause the person asked for and a pause on an error only a person can fix (such as a wrong key or too little memory) SHALL NOT resume by itself.

**Source**: `docs/spec_baseline/specification/mockups/README.md#run-states-a`, `docs/spec_baseline/specification/mockups/README.md#words-w--user-facing-vocabulary`, `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: a run left all night is finished in the morning or visibly retrying with a clear reason, never silently stuck. The run waits, checks the server now and then without asking the model anything, and goes on by itself; a wrong key or a missing model still waits for the person. A server that unloaded an idle model usually loads it again on the next request, so the run wakes at most six times for one such outage — about nineteen minutes — and then asks the person to load it ("Stop waking after a twelve-hour outage or six wakes of an unloaded model"); its budget of ten pauses before the step is flagged is a separate count.

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

- **WHEN** the run is paused on `unreachable`, `upstream` or `rateLimited` (an outage), `timeout`, `internal`, or `modelUnavailable` with the reason unloaded, model-loading or no-model-loaded
- **THEN** it waits and resumes by itself, whereas `auth`, `modelNotFound`, `missingCredential`, `validation` and `modelUnavailable` with the reason out-of-memory wait for the person

#### Scenario: The wakes follow a fixed schedule

- **WHEN** the run waits to resume by itself
- **THEN** it waits 15 s, 30 s, 1 min, 2 min, 5 min and 10 min before the first six wakes and 10 min before every later one

#### Scenario: Each wake probes the provider

- **WHEN** the run wakes
- **THEN** it probes the connection and the model list, which must still offer the run's model, and, for a model still loading, the server's health check, and resumes as soon as a probe passes, while a failed probe only extends the wait

#### Scenario: The person ends the wait

- **WHEN** the person uses Try again now, Skip these 4 segments or Resume during the wait
- **THEN** the wait ends at once and the wake schedule starts afresh, but the time the outage began is kept, because only an answered model call clears it
- **AND** the call sent again on the person's word during an outage or an unloaded model does not count toward the pauses after which the step is flagged, whereas after a timeout or a throwing step it still counts

#### Scenario: Each step of the recovery is announced and logged

- **WHEN** the run recovers by itself
- **THEN** the screen is told the run is waiting (with the next try's time and attempt), has stopped trying, or is held, and the outage's start, each wait, each probe's result and the resume after N minutes are logged at INFO

#### Scenario: A model still loading is waited for

- **WHEN** llama-server answers the draft of the second of three segments with HTTP 503 `Loading model` and its health check reports the model ready 40 s after the failure
- **THEN** the first wake at 15 s finds it still loading, the second wake at 45 s finds it ready and resumes the run, and the segment is accepted

#### Scenario: Too little memory does not wake

- **WHEN** the draft is answered `ErrorCode.modelUnavailable` with the reason out-of-memory
- **THEN** the run pauses, is never woken by itself, and waits for the person

#### Scenario: No model loaded in LM Studio waits

- **WHEN** LM Studio answers the draft with HTTP 400 `No models loaded` and the person then loads `qwen2.5:1.5b`
- **THEN** the next wake finds the model and resumes the run, and the interrupted call is sent again

### Requirement: Stop waking after a twelve-hour outage or six wakes of an unloaded model

An outage SHALL last from its first failure until a model call answers again; once it has lasted 12 hours (`RecoverySchedule.MAX_OUTAGE`) the system SHALL stop waking and wait for the person. An unloaded model SHALL wake at most six times in one outage (`PauseDecider.UNLOADED_MODEL_WAKES`), counted across its pauses until a model call answers again, after which the system SHALL announce that it gave up and wait for the person.

**Source**: `docs/spec_baseline/implementation_plan` overnight plan step 11 (owner intent: a run left all night is finished in the morning or visibly retrying with a clear reason, never silently stuck). In plain words: a local server that restarts, a laptop that loses its network for a while, or a provider that rate limits are common over a night. The run waits, checks the server now and then without asking the model anything, and goes on by itself; a wrong key or a missing model is not fixed by waiting, so those still wait for the person. A local server that unloaded an idle model usually loads it again on the next request, so the run tries six times — about nineteen minutes — and then asks the person to load it, instead of probing a model nobody loads all night.

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

**Source**: FR-RESUME-02 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/spec_baseline/specification/mockups/README.md#run-states-a`. In plain words: after closing the application a person sees at once how far an unfinished translation got and what the button will do; a finished one has nothing to continue.

#### Scenario: A project reopens ready to continue

- **WHEN** a project with 74 of 142 segments decided and 51 % of its steps done is opened after a restart
- **THEN** the title bar reads `Ready · 51 % done`
- **AND** the banner reads `Welcome back — 74 of 142 segments are done.` and the toolbar offers Continue translation

#### Scenario: A finished project is not ready to continue

- **WHEN** a project whose 142 segments are all decided is opened after a restart
- **THEN** no `Welcome back` banner is shown and the toolbar offers Continue to Export, not Continue translation

### Requirement: Never continue a translation by itself

WHEN a project is opened, the system SHALL NOT start or resume a run until the person chooses Continue translation, whatever state the project was stored in.

**Source**: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/spec_baseline/specification/mockups/README.md#run-states-a`. In plain words: a model call can take a computer's whole attention, so opening a project to look at it must never begin using the AI model without being asked.

#### Scenario: Opening does not call the model

- **WHEN** a project stored as running is opened and the person does nothing further
- **THEN** no model call is made and the project is ready to continue

### Requirement: Continue at the first segment not yet decided

WHEN the person chooses Continue translation on a reopened project, the system SHALL announce where the new run ("Continue the stage that has work") picks up, before its first model call, as a live line: "Continues at Chapter 2, segment 5." for Translate, "Continues with Proofread at window 41 of 120." for Proofread, "Continues with Prepare." for Prepare.

**Source**: FR-RESUME-02 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/mockups/README.md#run-states-a`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: decided segments are never translated twice and windows already read are not read again, and the person sees where the work picks up.

#### Scenario: Continue picks up at Chapter 2, segment 5

- **WHEN** Chapter 1 and segments 1 to 4 of Chapter 2 are decided and Continue translation is chosen
- **THEN** the live line reads `Continues at Chapter 2, segment 5.` and the first model call is for segment 5 of Chapter 2

#### Scenario: Continue picks up at Proofread

- **WHEN** all segments are decided, 120 Proofread windows are planned, the focus segments of 40 of them hold proofread verdicts with their current fingerprint, and Continue translation is chosen
- **THEN** the live line reads `Continues with Proofread at window 41 of 120.` and the first model call is for window 41

#### Scenario: Continue picks up at Prepare

- **WHEN** a project's last run stopped before its Prepare stage finished, no segment is decided, and Continue translation is chosen
- **THEN** the live line reads `Continues with Prepare.`

### Requirement: Treat a run stored as running or paused as interrupted

WHEN a project whose last run was stored as running or paused is opened, the system SHALL treat that run as interrupted and show the project as ready to continue, whether the application was closed cleanly or ended by a crash.

**Source**: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", FR-PERSIST-02 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`). In plain words: a run that was in flight when the window closed or the process died is not "still running" on the next launch; it simply stopped, and the decided segments are all kept.

#### Scenario: A crash during a run

- **WHEN** the process is killed while a run is stored as running with 74 of 142 segments decided, and the project is opened
- **THEN** the run is read as interrupted, the project is ready to continue and Continue translation is offered

#### Scenario: A paused run after a quit

- **WHEN** the application is closed while a run is paused with 74 of 142 segments decided, and the project is opened
- **THEN** the project is ready to continue and the paused run is not shown as running

### Requirement: Recompute the progress of a reopened project from its stored decisions

WHEN a project is opened after a restart, the system SHALL plan the run's steps again from the book, the brief and the stored decisions (a step per chapter summary the book asks for, per window planned from the stored proofread marks and, at Max, per window of the whole-book reading), count as done every decided draft, stored summary and window whose focus segments hold verdicts with its current fingerprint, and show no time left until the new run has timed its steps.

**Source**: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D3", `docs/spec_baseline/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the steps are not stored, but the decisions are, so the bar after a restart starts where the work really stands; the speed of the earlier run is not measured in this one, so no time left is guessed.

#### Scenario: The bar starts from the stored decisions

- **WHEN** a Balanced project of 40 segments in 4 chapters of 10, chapter summaries on, is opened after a restart with the 20 segments of its first 2 chapters decided and their 2 summaries stored
- **THEN** 22 of 44 steps count as done (40 drafts and 4 summaries), and the title bar reads `Ready · 50 % done`

#### Scenario: Proofread windows already read count as done

- **WHEN** the same project is opened with all 40 segments decided, all 4 summaries stored, and 6 Proofread windows planned from the stored proofread marks, the focus segments of 1 of which hold verdicts with its current fingerprint
- **THEN** 45 of 50 steps count as done and the title bar reads `Ready · 90 % done`

#### Scenario: A missing summary is a step still to do

- **WHEN** the same project is opened with Chapter 2's summary missing
- **THEN** that summary counts as a step not yet done

#### Scenario: Time left waits for the new run

- **WHEN** the same project is opened and Continue translation is chosen
- **THEN** no time left is shown on opening, and it appears only once the new run has timed its own steps

### Requirement: Ask when the stored AI model does not answer

WHEN the person chooses Continue translation and the AI model the project's stored run used does not answer, the system SHALL show the dialog "AI model not answering" with Try again, Use another AI model… and Not now, and SHALL start no run until the person has chosen; Escape counts as Not now.

**Source**: FR-PROV-09 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/spec_baseline/specification/mockups/README.md#other-dialogs`. In plain words: instead of failing on the first call, the person is told right away that Ollama is not open and can fix that, pick another model, or leave.

#### Scenario: Ollama is not open

- **WHEN** the project's stored run used Ollama · `gemma4:e4b`, the same model is current, Continue translation is chosen, and nothing answers at `http://127.0.0.1:11434`
- **THEN** the dialog `AI model not answering` offers Try again, Use another AI model… and Not now
- **AND** no run has started

#### Scenario: Not now leaves the project as it was

- **WHEN** Not now is chosen
- **THEN** the project stays ready to continue and no model call is made

### Requirement: Count a stored model as not answering by its listing alone

A stored model SHALL count as not answering when its program fails to list its models, or lists some and not this one (a listing, never a model call), a program that lists none SHALL count as answering, and the stored model SHALL be read from the project's latest run each time Continue is chosen.

**Source**: FR-PROV-09 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/spec_baseline/specification/mockups/README.md#other-dialogs`. In plain words: a listing is free and never loads a model, so asking costs nothing; a program that lists no models may still serve the stored one by its name.

#### Scenario: A program that lists no models counts as answering

- **WHEN** the project's stored run used Ollama · `gemma4:e4b`, Continue translation is chosen, and Ollama answers its model listing with no models
- **THEN** no `AI model not answering` dialog is shown and the run starts

### Requirement: Try again asks only the stored model

Try again in that dialog SHALL ask only that one model again: it SHALL NOT look for programs and SHALL save nothing, and it is not the Projects screen's Try again.

**Source**: FR-PROV-09 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/spec_baseline/specification/mockups/README.md#other-dialogs`. In plain words: the person who just opened Ollama wants the same run to continue, not a new search or a changed setting.

#### Scenario: Try again continues once the model answers

- **WHEN** Ollama is opened and Try again is chosen
- **THEN** the run starts with `gemma4:e4b` at the first pending segment

### Requirement: Ask which model to use when a different one is chosen

WHEN the person chooses Continue translation and the current AI model differs from the one the project's stored run used, the system SHALL show the dialog "A different model is chosen" with Use the earlier model and Continue with the new one, and SHALL start the run only on one of them. This question comes first; the answer test of the model that was chosen follows it. Escape starts nothing. Use the earlier model leaves the current model unchanged.

**Source**: FR-PROV-09 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D11", `docs/spec_baseline/specification/mockups/README.md#other-dialogs`. In plain words: continuing a half-translated book with another model can change its voice, so the person decides whether to keep the earlier model or accept the change.

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

The system SHALL count the pauses of each step — a segment's draft, a segment's decision, or a Proofread window call — by the kind of its error and, on the failure past that kind's budget, flag the step's segment with the error and go on (a Proofread window is left unread instead): two pauses for a timeout and for the errors only a person can fix, three for an error the step threw, and ten for an outage and for an unloaded model.

**Source**: `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/mockups/README.md#run-states-a`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: one bad segment must not end a night's run, while an outage, not the segment's fault, has its own larger budget. The errors only a person can fix are `auth`, `modelNotFound`, `missingCredential`, `validation` and `modelUnavailable` with the reason out-of-memory; an outage is `unreachable`, `upstream` or `rateLimited`; an unloaded model is `modelUnavailable`; a step that throws answers `ErrorCode.internal`. For a Proofread window call, going past the budget leaves the window as it was: its texts and marks stay for the review desk, no fingerprint is stored for it so the next Continue reads it again, and the stage goes on (this is the simplest safe handling, not fixed by the research). A call not tied to one segment has no step to flag, so past its budget it waits for the person.

#### Scenario: An error only a person can fix is flagged after two

- **WHEN** the draft of `ch07.xhtml:42` answers `ErrorCode.auth`, the person resumes after each of two pauses, and it answers `ErrorCode.auth` a third time
- **THEN** `ch07.xhtml:42` is flagged with `ErrorCode.auth` and the run goes on

#### Scenario: Too little memory is flagged after two pauses

- **WHEN** the draft of `ch07.xhtml:42` answers `ErrorCode.modelUnavailable` with the reason out-of-memory, the person resumes after each of two pauses, and it answers the same a third time
- **THEN** `ch07.xhtml:42` is flagged with `ErrorCode.modelUnavailable` and the run goes on

#### Scenario: A Proofread window that keeps timing out is left unread

- **WHEN** the Proofread call for window 41 answers `ErrorCode.timeout` three times in a row, and the run resumes after each of the first two pauses
- **THEN** window 41 is left as it was with its marks kept, window 42 is read next, and the run ends Completed with window 41 counted as unread

### Requirement: Offer to skip the step a pause is stuck on

WHILE the run is paused on a model-call error, the job SHALL offer to skip the step: the run resumes, the step's segments are flagged with the error without their call being sent again, and the run goes on.

**Source**: `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/mockups/README.md#words-w--user-facing-vocabulary`. In plain words: the person can give up on segments that keep failing and let the book continue, instead of being stuck.

#### Scenario: Skipping four segments

- **WHEN** a run is paused on `ErrorCode.modelNotFound` for segments 5–8 and the person chooses Skip these 4 segments
- **THEN** segments 5–8 are flagged with `ErrorCode.modelNotFound`, their call is not sent again, and the run goes on with segment 9

### Requirement: Show the provider-error state with its three actions

WHILE a run from the window is paused on a model-call error, the translating screen SHALL show the provider-error state naming the error, with Try again now (which resumes the run), Open settings, and Skip these N segments, N being the count in flight.

**Source**: `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/spec_baseline/specification/mockups/README.md#words-w--user-facing-vocabulary`, `docs/spec_baseline/specification/mockups/README.md#run-states-a`. In plain words: the screen says what went wrong and offers the three things a person can do; leaving the run paused needs no button, because the toolbar's Pause already does it. The banner's wording per state is the notifications capability's.

#### Scenario: The three actions are shown

- **WHEN** a run is paused on `ErrorCode.unreachable` with segments 5–8 in flight
- **THEN** the screen offers Try again now, Open settings and Skip these 4 segments

### Requirement: Continue the stage that has work

WHEN the person chooses Continue translation, the system SHALL start a new run at the first stage that has work left: Prepare while the last run stopped before its Prepare stage finished, Translate while any segment is pending, Proofread while an open deferral (a changed rendering or a fact that has arrived) exists or any planned window's focus segments do not all hold a proofread verdict with its current fingerprint.

**Source**: `docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`, `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: a run has three stages and may be stopped in any of them, today or before a restart. Continue goes to the stage where the work stands instead of always looking for an untranslated segment, and does no model work when nothing is left. Prepare inside a run is short code work (the style sheet and, on an empty glossary, the code name scan); the brief suggestion and "Prepare names automatically" are actions the person starts and Continue never starts them. At Fast the Proofread stage is code only, so a gender set or a rendering changed after a finished Fast run is still settled by Continue, with no model call.

#### Scenario: A stopped translation continues at Translate

- **WHEN** a Balanced run is stopped with 412 of 1,240 segments decided and the person chooses Continue translation
- **THEN** the new run starts in the Translate stage at the 413th segment

#### Scenario: A finished translation continues at Proofread

- **WHEN** all 1,240 segments are decided, 120 Proofread windows are planned, the focus segments of 40 of them hold proofread verdicts with their current fingerprint, and the person chooses Continue translation
- **THEN** the new run starts in the Proofread stage and reads the other 80 windows

#### Scenario: A run stopped before any draft continues at Prepare

- **WHEN** a run was stopped before its Prepare stage finished, with 0 of 1,240 segments decided
- **THEN** Continue translation starts in the Prepare stage, makes no model call there, and goes on to Translate at the first segment

#### Scenario: A project with no work left makes no call

- **WHEN** all segments are decided, no deferral is open, and the focus segments of every planned window hold proofread verdicts with their current fingerprint
- **THEN** Continue translation sends no request to the provider and the run ends Completed

#### Scenario: A gender set after a finished Max run is offered and read

- **WHEN** a Max run has ended Completed, the person then sets the gender of `Hale` to female, which marks `Book.md:0` (`Хейл пішов.`) with `gender now known: Хейл — female`
- **THEN** the Translating screen offers Continue translation, and once the Continue has read that window it offers nothing more

#### Scenario: A rendering changed after a finished Fast run is offered

- **WHEN** a Fast run has ended Completed and the person then changes the locked target of `Hale` from `Хейл` to `Гейл`
- **THEN** the Translating screen offers Continue translation, the Continue sweeps `Гейл` into the decided text with no model call, and then nothing more is offered

#### Scenario: A gender set after a finished Fast run is settled by code

- **WHEN** a Fast run has ended Completed, the person then sets the gender of `Ірвін` to male in the Glossary, which records deferrals for 3 decided segments that name him, and chooses Continue translation
- **THEN** the new run starts in the Proofread stage, settles the 3 deferrals by code, sends no request to the provider, and ends Completed

### Requirement: Skip a Proofread window that already holds the same fingerprint

WHEN the Proofread stage plans its windows, the system SHALL skip, with no model call, each window whose focus segments all hold a proofread verdict with the fingerprint the window has now, and SHALL read every other window.

**Source**: `docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`, `docs/spec_baseline/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#resume-support`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the fingerprint is a hash of everything a window's reading depended on: the source and current texts of its paragraphs and the marks they were read for, the neighbouring texts it was shown, the book-map lines its texts name, the narrator's person and gender, the prompt version and whether it was the first or the whole-book reading. If none of those changed, reading it again would give the same answer, so a restart or a Continue pays only for what is new or changed. Only the focus segments count: a neighbour shown read-only gets no verdict from that window.

#### Scenario: A stopped stage resumes with the unread windows only

- **WHEN** a Balanced run planned 6 windows, was stopped after windows 1 to 4 were decided, and Continue is chosen
- **THEN** exactly 2 proofread calls are sent, for windows 5 and 6

#### Scenario: A changed glossary target re-reads only the affected window

- **WHEN** all 6 windows were read, the person then changes the target of the name `Hale` from `Хейл` to `Гейл`, only window 2 carries a book-map line for `Hale`, and Continue is chosen
- **THEN** exactly 1 proofread call is sent, for window 2

#### Scenario: An edited paragraph re-reads the windows that contain it

- **WHEN** all 6 windows were read and the person edits a paragraph that window 3 shows as its read-only neighbour and window 4 reads as its focus
- **THEN** Continue sends exactly 2 proofread calls, for windows 3 and 4

#### Scenario: A rejected or unchanged window is skipped too

- **WHEN** window 5 ended with the verdict rejected and window 6 with unchanged, and nothing has changed since
- **THEN** Continue does not read either window again

#### Scenario: The whole-book reading does not skip on the first reading's verdicts

- **WHEN** a Max run has read the marked windows (first reading) and is stopped before its whole-book reading, and Continue is chosen
- **THEN** the whole-book windows are read, because their fingerprint differs from the first reading's

#### Scenario: A window cut off by a quit is read again

- **WHEN** the application was killed while window 6 was being read, so nothing of it was stored
- **THEN** Continue reads window 6 again and reads windows 1 to 5 no more

### Requirement: Write the missing chapter summaries first

WHEN Continue starts a run with chapter summaries on, the system SHALL write the summary of every finished chapter that has none, or whose stored summary was written from other source text, before the next draft that needs it, and SHALL write none for a chapter that is not finished nor for the book's last chapter.

**Source**: `docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`, `docs/spec_baseline/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#resume-support`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a chapter summary is what later chapters learn the story from, so a restart that lost one would leave every later window without it; Continue first fills the gap. A chapter that is still being translated is not summarised until it is done.

#### Scenario: A finished chapter without a summary gets one before the next draft

- **WHEN** Chapter 3 is fully decided, the application was closed before its summary call, and Continue is chosen
- **THEN** the first request of the new run is Chapter 3's summary call, and the first draft call of Chapter 4 follows it

#### Scenario: A chapter in progress is not summarised

- **WHEN** Chapter 4 has 12 of 30 segments decided and Continue is chosen
- **THEN** no summary call is made for Chapter 4, and the drafts of its remaining segments carry the stored summaries of Chapters 1 to 3

#### Scenario: A failed summary is tried once more on the next Continue

- **WHEN** Chapter 3's summary call failed during a run, the run went on, and Continue is chosen later
- **THEN** one summary call for Chapter 3 is made before the next draft

#### Scenario: Turning the switch on mid-book fills the gaps

- **WHEN** the person turns chapter summaries on for a book whose first 3 chapters are decided and Continue is chosen
- **THEN** 3 summary calls are made, for Chapters 1 to 3, before the next draft

#### Scenario: A chapter whose source changed gets a new summary

- **WHEN** the stored summary of Chapter 2 was written from different source text than the chapter now holds
- **THEN** Continue writes Chapter 2's summary again

#### Scenario: A switched-off book writes none

- **WHEN** chapter summaries are off for the book and Continue is chosen
- **THEN** no summary call is made

### Requirement: Make a cut-off repair again from the draft kept in memory

WHEN a pause aborts a segment's repair call, or the call is answered with an error that pauses the run, the system SHALL on resume make the repair call again from its first request for the draft kept in memory, make no new draft call for the segment, and count that call as the segment's one repair.

**Source**: `docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: there are no repair rounds to continue any more, only one repair. If it is cut off while the application is still running, it is simply made again for the same draft and costs one call. If the run is stopped or the application is closed, the undecided draft is gone, the segment stays pending, and the next run drafts it again. This is the simplest safe behaviour.

#### Scenario: A pause during the repair

- **WHEN** a Balanced run (batch size 1) drafts `He opened the old door.` and gets the echo `HE OPENED THE OLD DOOR.`, which the checks refuse, a pause is requested while the repair call is in flight, and after resume the repair is answered with `Він відчинив старі двері.`
- **THEN** the provider has received 2 requests before the pause, the draft and the repair
- **AND** it has received 3 in total, the repair again, and the segment ends ACCEPTED after its one repair

#### Scenario: An unreachable provider during the repair

- **WHEN** the same repair is answered with `ErrorCode.unreachable` with pause on error enabled, and after resume it is answered with `Він відчинив старі двері.`
- **THEN** the run pauses with `ErrorCode.unreachable` after 2 requests and has received 3 in total

#### Scenario: The resumed repair is the only repair

- **WHEN** after resume the repair is answered with `Він відчинив the old door.`, which the checks refuse again
- **THEN** no further repair call is made, the provider has received 3 requests in total, and the segment is flagged

#### Scenario: A stop drops the chunk's undecided drafts

- **WHEN** a Balanced run has drafted the chunk `ch07.xhtml:40`–`ch07.xhtml:43`, decided `ch07.xhtml:40` and `ch07.xhtml:41`, and is stopped
- **THEN** `ch07.xhtml:42` and `ch07.xhtml:43` stay PENDING with their drafts dropped, and the next run drafts them again
