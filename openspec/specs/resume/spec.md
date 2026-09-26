# resume Specification

## Purpose
Lets a running translation job pause, resume or stop at safe points, either when asked or at pause points the caller
enables, without losing or repeating decided segments. It covers one run in memory; continuing after a restart comes
with local storage.

## Requirements

### Requirement: Pause on request at the next boundary

WHEN a pause is requested while a job translates, the system SHALL:

- abort the model request in flight, without waiting for the provider to answer, and send no further request — not a
  retry, not a structural repair and not a placeholder repair; a reply that had already arrived before the request was
  made is still decided normally;
- pause with the reason "requested", reporting no error;
- make no model call while paused;
- on resume, translate the segment whose request was aborted again from its first request, and then continue with the
  next PENDING segment.

WHEN a pause is requested while no model request is in flight, the system SHALL pause at the next boundary, which is
before the next model call or before export starts, with the reason "requested".

A pause requested after export has started SHALL be ignored. A pause requested before the job runs SHALL take effect at
the first boundary, before the first model call.

**Source:** FR-RESUME-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`.
In plain words: pausing no longer waits for a slow model. The request that is waiting is aborted at once and nothing
more is sent, so the Pause button acts within seconds even when a local model takes minutes. The segment whose request
was aborted has no decision yet, so resuming translates it again rather than skipping it; a segment already decided is
never redone. A reply that had already come back before the click is not thrown away. The spec pauses at chunk
boundaries; until chunking exists, every segment is its own chunk. A screen may ask for a pause before it starts the
job, and that request must not be lost. The export is never interrupted, so a pause asked for during it is ignored (the
engine-side rule is "Pause and stop act without waiting for the provider" in the `translation-pipeline` capability).

#### Scenario: Pause after the first of three segments

- **WHEN** a job over three segments is asked to pause as soon as the first segment is decided
- **THEN** the job pauses with reason requested, 1 accepted and 2 pending, after exactly 1 model call
- **AND** after resume it ends Completed with exactly 3 model calls in total

#### Scenario: Pause during a slow request

- **WHEN** a job over three segments has its first segment accepted, the provider is taking `30` seconds to answer the
  second segment's request, and a pause is requested `1` second after that request was sent
- **THEN** the job pauses with reason requested within `5` seconds, with 1 accepted and 2 pending and no error, and the
  provider has received exactly `2` requests in total
- **AND** after resume the second segment is requested again from its first request, and the job ends Completed with 3
  accepted and the provider having received `4` requests in total

#### Scenario: A pause during export is ignored

- **WHEN** a pause is requested after the export stage has started
- **THEN** the job does not pause and ends Completed

#### Scenario: A pause requested before the job runs

- **WHEN** a pause is requested on a job over three segments before it runs, and the job is then run
- **THEN** the job pauses with reason requested before any model call, with 0 accepted and 3 pending

### Requirement: Pause at enabled pause points

WHERE the caller enables pause points, the system SHALL pause at each enabled point:

- after-segment: after each decided segment;
- after-section: after the last segment of each section;
- between-stages: after the last segment, before export.

A section is one EPUB spine document, one FB2 body, or a whole Markdown or TXT file. A section with no segments SHALL
cause no pause.

**Source:** FR-RESUME-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `02_Architecture/03_DOCUMENT_MODEL.md#data-model`.
In plain words: a screen can let the user look at each section, or at the whole translation before it is written. With
nothing enabled a job never waits, which is how the command line runs.

#### Scenario: Pause after each section of an EPUB

- **WHEN** an EPUB whose two spine documents hold 2 and 1 segments is translated with after-section enabled
- **THEN** the job pauses twice: first with 2 accepted and 1 pending, then with 3 accepted and 0 pending

#### Scenario: Pause between stages

- **WHEN** a book is translated with between-stages enabled
- **THEN** the job pauses with 0 pending segments and no file at the destination
- **AND** after resume the destination file is written

#### Scenario: An empty section causes no pause

- **WHEN** an EPUB whose two spine documents hold 0 and 2 segments is translated with after-section enabled
- **THEN** the job pauses once, after the second spine document

#### Scenario: No pause points

- **WHEN** a book is translated with no pause points and no pause request
- **THEN** the job never pauses and ends Completed

### Requirement: Pause once per boundary, with the pause points in force

WHEN several pause points apply at the same boundary, the system SHALL pause once. It SHALL report "requested" if a
pause was requested, and otherwise the widest point that applies: between-stages, then after-section, then
after-segment.

WHEN the enabled pause points change while a job runs or is paused, the system SHALL apply the new set from the next
boundary.

**Source:** FR-RESUME-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`).
In plain words: the last segment of a book ends a segment, a section and a stage at once, and the user should see one
pause named after the biggest step that just finished. "Resume and stop pausing" means clearing the pause points, then
resuming.

#### Scenario: Coinciding pause points pause once

- **WHEN** a TXT book with one segment is translated with after-segment, after-section and between-stages enabled
- **THEN** the job pauses exactly once, with reason between-stages

#### Scenario: Pause points cleared before resuming

- **WHEN** a job over three segments pauses after the first with after-segment enabled, and the pause points are
  cleared before it resumes
- **THEN** the job does not pause again and ends Completed

### Requirement: Pause instead of failing when an error stops the job

WHERE pause on error is enabled, IF a model call or the export fails with an error that would otherwise end the job
Failed, THEN the system SHALL pause with that error, and on resume retry the step that failed.

**Source:** `01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03
(`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`).
In plain words: a stopped local server or a full disk is often fixed in a minute. The user fixes it and continues,
instead of translating the book again.

#### Scenario: The model is unreachable once

- **WHEN** pause on error is enabled and the model answers `ErrorCode.unreachable` for the second of three segments,
  then answers normally
- **THEN** the job pauses with `ErrorCode.unreachable`
- **AND** after resume the second segment is sent again and accepted, and the job ends Completed

#### Scenario: The export fails once

- **WHEN** pause on error is enabled and the destination folder is deleted as the export stage starts
- **THEN** the job pauses with the write error, and the destination does not exist
- **AND** after the folder is created again and the job resumes, the book is written and the job ends Completed

### Requirement: Cancel a job

WHEN cancellation is requested, the system SHALL end the job Cancelled without waiting for the segment in progress or
for the provider, keep the decided segments in its report, and write nothing. If a model request is in flight, the
system SHALL abort it and SHALL NOT send any further request. If the job is paused it SHALL end at once. Otherwise it
SHALL end at the latest before the translated book would replace the destination, because the export itself is never
interrupted.

IF the thread running a paused job is interrupted, THEN the system SHALL treat it as a cancellation.

IF cancellation is requested before the job runs, THEN running it SHALL end the job Cancelled at once, without opening
the book or calling the model.

**Source:** FR-RESUME-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`.
In plain words: stopping is safe and is not an error; the report says what was done. A stop no longer waits for the
current segment to finish or for a slow provider to answer: the request that is waiting is aborted and nothing more is
sent, which is what makes the Stop button act at once even when a local model takes minutes (the engine-side rule is
"Pause and stop act without waiting for the provider" in the `translation-pipeline` capability). A screen that cancels
its background task interrupts the thread, and a paused job must then stop too rather than wait forever. A job
cancelled before it starts does no work at all. Picking a cancelled job up again later needs saved progress, which
comes with local storage.

#### Scenario: Cancel while paused

- **WHEN** a job over three segments is paused after the first and cancellation is requested
- **THEN** the job ends Cancelled with 1 accepted and 2 pending, and no file is written

#### Scenario: Cancel during a slow request

- **WHEN** the provider is taking `30` seconds to answer the second of three segments' request, and cancellation is
  requested `1` second after that request was sent
- **THEN** the job ends Cancelled within `5` seconds with 1 accepted and 2 pending, the provider has received exactly
  `2` requests in total, and no file is written

#### Scenario: Interrupt while paused

- **WHEN** the thread running a paused job is interrupted
- **THEN** the job ends Cancelled and no file is written

#### Scenario: Cancel before the job runs

- **WHEN** cancellation is requested on a job before it runs, and the job is then run
- **THEN** the job ends Cancelled with no model call and no file written

### Requirement: Pause, resume and stop a run from the screen

WHILE a run is in progress, the translating screen SHALL offer to pause it and to stop it. WHILE a run is
paused, it SHALL offer to resume it and to stop it.

WHEN a run is stopped, the screen SHALL report it as a neutral outcome rather than a failure, and SHALL NOT show
an error dialog or a message of error severity for it.

A stopped run SHALL be terminal in this build: the screen SHALL NOT offer to resume it, and starting again
SHALL start a new run from the beginning.

**Source:** FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`.
In plain words: stopping is something a person chose, so telling them it went wrong is both untrue and
unpleasant. The engine reports a stopped run as cancelled, and the screen has to translate that into "you stopped
it" rather than passing the word through. The reference rendering calls the stopped state "resumable", and it is
not one here: picking a cancelled run up again means knowing which segments were already decided, which needs
storage that does not exist yet — so the state is stated as terminal rather than offering a control that would
silently restart from zero.

#### Scenario: Stopping is not an error

- **WHEN** a running job is stopped and the engine reports the run as cancelled
- **THEN** the screen moves to the stopped state, shows no error dialog, and shows no message of error severity

#### Scenario: Resuming continues the same run

- **WHEN** a paused job is resumed
- **THEN** the screen returns to the running state and the counts continue from where they stood

#### Scenario: A stopped run offers no resume

- **WHEN** the screen is in the stopped state
- **THEN** it offers to start a new run and does not offer to resume the stopped one

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
