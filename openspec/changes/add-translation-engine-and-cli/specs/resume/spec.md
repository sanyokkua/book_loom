## Purpose

Lets a running translation job pause, resume or stop at safe points, either when asked or at pause points the caller
enables, without losing or repeating decided segments. It covers one run in memory; continuing after a restart comes
with local storage.

## ADDED Requirements

### Requirement: Pause on request at the next boundary

WHEN a pause is requested while a job translates, the system SHALL:

- let the model call in progress finish and its segment be decided;
- pause at the next boundary, which is before the next model call or before export starts, with the reason "requested";
- make no model call while paused;
- on resume, continue with the next PENDING segment.

A pause requested after export has started SHALL be ignored. A pause requested before the job runs SHALL take effect at
the first boundary, before the first model call.

**Source:** FR-RESUME-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, `02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`.
In plain words: pausing never cuts a model off mid-answer and never throws that answer away. The spec pauses at chunk
boundaries; until chunking exists, every segment is its own chunk. A screen may ask for a pause before it starts the
job, and that request must not be lost.

#### Scenario: Pause after the first of three segments

- **WHEN** a job over three segments is asked to pause as soon as the first segment is decided
- **THEN** the job pauses with reason requested, 1 accepted and 2 pending, after exactly 1 model call
- **AND** after resume it ends Completed with exactly 3 model calls in total

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

WHEN cancellation is requested, the system SHALL end the job Cancelled at the next boundary, keep the decided segments
in its report, and write nothing. If the job is paused it SHALL end at once. Otherwise it SHALL end at the latest before
the translated book would replace the destination.

IF the thread running a paused job is interrupted, THEN the system SHALL treat it as a cancellation.

IF cancellation is requested before the job runs, THEN running it SHALL end the job Cancelled at once, without opening
the book or calling the model.

**Source:** FR-RESUME-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`.
In plain words: stopping is safe and is not an error; the report says what was done. A screen that cancels its
background task interrupts the thread, and a paused job must then stop too rather than wait forever. A job cancelled
before it starts does no work at all. Picking a cancelled job up again later needs saved progress, which comes with
local storage.

#### Scenario: Cancel while paused

- **WHEN** a job over three segments is paused after the first and cancellation is requested
- **THEN** the job ends Cancelled with 1 accepted and 2 pending, and no file is written

#### Scenario: Interrupt while paused

- **WHEN** the thread running a paused job is interrupted
- **THEN** the job ends Cancelled and no file is written

#### Scenario: Cancel before the job runs

- **WHEN** cancellation is requested on a job before it runs, and the job is then run
- **THEN** the job ends Cancelled with no model call and no file written
