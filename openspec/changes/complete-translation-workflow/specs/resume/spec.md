# Spec Delta

## ADDED Requirements

### Requirement: Pause for review as the review mode says

The system SHALL enable, for every run started from the window, the pause on a provider error in every review mode,
Unattended included, together with the review mode's own pause points:

- Unattended SHALL never pause for review; flagged segments wait in the review panel;
- Assisted SHALL pause on each flagged segment;
- Manual SHALL pause after every decided segment, flagged or not.

A pause on a flagged segment or after a segment SHALL name the segment just decided and SHALL come before any further
model call.

**Source:** FR-REVIEW-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`),
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`,
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, ADR-0036.
In plain words: the default runs to the end and leaves review for afterwards; a careful translator can have the run stop
on every problem, or on every segment, and fix it on the spot. A stopped server or an unloaded model pauses the run in
every mode, so the person can fix it instead of starting over; the command line enables no pause point at all. A segment
is decided only after its whole chunk has been drafted and, when the reviewer is on (Balanced, Max), reviewed — so with the
reviewer on the later segments of the same chunk have already been drafted and reviewed when the pause comes, while with the
reviewer off (Fast) or in Manual, which holds one segment per chunk, no call for the next segment precedes it (the
translation-pipeline capability's "Decide a chunk's segments in document order"). Naming the segment is what lets the
review panel open on the right one (the review-queue capability's "Open the segment a review pause names").

#### Scenario: Unattended runs past a flagged segment

- **WHEN** an Unattended run flags `ch07.xhtml:41` and continues
- **THEN** the run does not pause, and `ch07.xhtml:41` is listed in the review panel

#### Scenario: Unattended from the window pauses on a provider error

- **WHEN** an Unattended run started from the window gets `ErrorCode.unreachable` for the draft call of `ch07.xhtml:42`
- **THEN** the run pauses with `ErrorCode.unreachable` and the screen shows the provider-error state

#### Scenario: Assisted on Fast stops on the flagged segment

- **WHEN** an Assisted run on the Fast dial flags `ch07.xhtml:41`
- **THEN** the run pauses with the reason on-flagged, naming `ch07.xhtml:41`, before any call for `ch07.xhtml:42`

#### Scenario: Assisted on Balanced stops after its chunk was reviewed

- **WHEN** an Assisted run on the Balanced dial packs `ch07.xhtml:40`–`ch07.xhtml:43` into one chunk and flags
  `ch07.xhtml:42`
- **THEN** the run pauses with the reason on-flagged, naming `ch07.xhtml:42`, after the draft calls of all four
  segments and the chunk's reviewer call
- **AND** no model call is made for `ch07.xhtml:43` until the run resumes

#### Scenario: Manual stops after an accepted segment

- **WHEN** a Manual run accepts `ch01.xhtml:0`
- **THEN** the run pauses with the reason after-segment, naming `ch01.xhtml:0`, before any call for `ch01.xhtml:1`

### Requirement: Continue after the person acts on the paused segment

WHEN a run paused for review is resumed, the system SHALL continue with the undecided segments after the paused one,
SHALL make no further model call for the paused segment, and SHALL keep the paused segment's stored decision — the
person's edit, when they made one — as it stands.

**Source:** FR-REVIEW-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-RESUME-03
(`#fr-resume`), ADR-0036.
In plain words: what the person decided during the pause is final for the run — the segment is not drafted or
repaired again — and the sentences translated after it follow the corrected one, not the model's rejected draft. Which
sentences those are is the translation-pipeline capability's "Decide a chunk's segments in document order": with the
reviewer off (Fast) or in Manual it is the very next segment; with the reviewer on, the rest of the paused chunk was already
drafted and reviewed before the pause and is not drafted again, so the edit reaches the next chunk.

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

### Requirement: Keep a run's progress only until the application closes

The system SHALL keep every decision, edit and memory entry of a run in memory only; WHEN the application closes, all
of it SHALL be gone, and the next launch SHALL offer no resume.

**Source:** FR-RESUME-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, ADR-0034.
In plain words: saving progress to disk comes with the database in a later change. Until then a paused or stopped run
can be picked up again only in the same session, and the screens say so rather than suggest otherwise.

#### Scenario: A restart starts from nothing

- **WHEN** a run is stopped with 412 of 1,240 segments decided and the application is closed and opened again
- **THEN** no project is open and no resume is offered

### Requirement: Record whether a run is running, paused or ended

The system SHALL record, for each run of a project, its current state — running from its start, paused at each pause,
running again at each resume, and completed, stopped or failed when it ends — and SHALL let the rest of the application
read the state of a project's latest run.

**Source:** FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, ADR-0034.
In plain words: a review retry needs the model, which a running run is using and a paused or finished one is not; a
record that says only whether a run has ended cannot tell a paused run from a running one, so the current state is kept
and updated on every pause and resume (see the review-queue capability's "Allow a retry whenever no run of the project
is running").

#### Scenario: The state follows pauses and resumes

- **WHEN** an Assisted run of `Book.md` starts, pauses on the flagged `Book.md:1`, is resumed and ends
- **THEN** its recorded state is running, then paused, then running, then completed

#### Scenario: A stopped run is recorded as stopped

- **WHEN** a running run is stopped and the engine reports it cancelled
- **THEN** its recorded state is stopped

#### Scenario: A failed run is recorded as failed

- **WHEN** the model call for `Book.md:1` throws an exception and the run ends Failed with `ErrorCode.internal`
- **THEN** its recorded state is failed

### Requirement: Pause, stop and resume a run from the screen

WHILE a run is in progress, the translating screen SHALL offer to pause it and to stop it. WHILE a run is paused, it
SHALL offer to resume it and to stop it, and, unless the pause is on a provider error, SHALL show the banner `Paused.
Progress is kept until the application closes. Resume any time — it continues at chunk <k>/<n>.`, where `<k>` is the
paused chunk and `<n>` the number of chunks in its section. The title bar's single Pause or Resume control SHALL do what
the screen's control of the same name does.

WHEN a run is stopped, the screen SHALL report it as a neutral outcome rather than a failure, and SHALL NOT show
an error dialog or a message of error severity for it.

WHILE a run is stopped, the screen SHALL offer Resume, which continues the translation with a new run from the first
PENDING segment, SHALL keep the flagged segments in the review panel, and SHALL show the banner `Run stopped. Progress
is kept until the application closes. Resume any time — it re-enters at the first pending segment; flagged segments
wait in the review panel.`

**Source:** FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `#banners`,
`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`, ADR-0034.
In plain words: stopping is something a person chose, so telling them it went wrong is both untrue and unpleasant. The
engine reports a stopped run as cancelled, and the screen has to translate that into "you stopped it" rather than
passing the word through. The decisions are now stored for the session, so the stopped state the reference rendering
calls "resumable" really is: resuming a stopped run starts a new run at the first undecided segment, while resuming a
paused one continues the same run. The reference rendering's banners promise that progress is saved; it is kept only
until the application closes, and the banners say exactly that.

#### Scenario: Stopping is not an error

- **WHEN** a running job is stopped and the engine reports the run as cancelled
- **THEN** the screen moves to the stopped state, shows no error dialog, and shows no message of error severity

#### Scenario: Resuming continues the same run

- **WHEN** a paused job is resumed
- **THEN** the screen returns to the running state and the counts continue from where they stood

#### Scenario: A stopped run resumes where it stood

- **WHEN** a run is stopped with 412 of 1,240 segments decided, 3 of them flagged, and the person presses Resume
- **THEN** a run starts whose first model call is for the 413th segment, and the counts continue from 412
- **AND** the 3 flagged segments are still listed in the review panel

#### Scenario: The title bar pauses the run

- **WHEN** a run is in progress and the person presses Pause in the title bar
- **THEN** the run reports pausing exactly as if the screen's Pause had been pressed, and the title bar then shows Resume

#### Scenario: The paused banner says how long progress lasts

- **WHEN** a run pauses in chunk 41 of the 66 chunks of its section
- **THEN** the screen shows `Paused. Progress is kept until the application closes. Resume any time — it continues at
  chunk 41/66.`

#### Scenario: The stopped banner says where a resume re-enters

- **WHEN** a run is stopped
- **THEN** the screen shows `Run stopped. Progress is kept until the application closes. Resume any time — it re-enters
  at the first pending segment; flagged segments wait in the review panel.` and offers Resume

### Requirement: Continue a segment's repair rounds at the call that failed

WHEN a self-heal call is aborted by a pause, or is answered with an error that pauses the run, the system SHALL keep the
segment's rounds as they stood in memory and, on resume, SHALL continue at the call that failed: the round whose repair
call failed is sent again from that call, and the directed fix for a refused reviewer edit that failed is sent again with
the same verified edits. It SHALL keep the chunk's reviewer verdict, making no second reviewer call for the chunk. WHEN a run
is stopped, the system SHALL drop the chunk's undecided drafts, as "Decide a chunk's segments in document order" says.

**Source:** FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`,
`openspec/changes/complete-translation-workflow/proposal.md#what-changes`; tasks 15b.
In plain words: the rounds were once restarted from the first, which on the Bartimaeus hand test paid for the same
directed fix again after every resume. The rounds already answered are kept while the application runs, so a resume
costs only the call that failed. What the run already paid for at the chunk level — the reviewer's verdict — is kept too,
so a pause never costs a second reviewer call for the chunk (a repaired target is decided by the checks alone, as the
quality gates say).

#### Scenario: A pause during the second repair round

- **WHEN** a Balanced run drafts `He opened the old door.` and gets the echo `HE OPENED THE OLD DOOR.`, which the checks refuse
  so no reviewer call is made, the first directed fix is answered with `He opened the old door.`, a pause is requested while the
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

## MODIFIED Requirements

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

**Source:** FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`,
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0035, ADR-0038.
In plain words: pausing does not wait for a slow model. The request that is waiting is aborted at once and nothing more
is sent, so the Pause button acts within seconds even when a local model takes minutes. The call that was aborted has no
answer yet, so resuming makes it again rather than skipping it; a segment already decided is never redone, and a draft
already made for the chunk is not made twice (the translation-pipeline capability's "Decide a chunk's segments in
document order"). A screen may ask for a pause before it starts the job, and that request must not be lost. The run no
longer writes the book, so its last boundary is the end of the job.

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

**Source:** FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#data-model`, ADR-0036, ADR-0041.
In plain words: a screen can let the person look at each flagged segment, each segment, each section, or the whole
translation before revision. With nothing enabled a job never waits, which is how the command line runs. The book's
title and labels are translated after its body; they are not a chapter the person reads through, so finishing them is
no section's end.

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

**Source:** FR-RESUME-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), ADR-0036.
In plain words: the last segment of a book ends a segment, a section and a stage at once, and the person should see one
pause. A flagged segment is named as flagged even when another point applies too, because that is what the person has to
act on. "Resume and stop pausing" means clearing the pause points, then resuming.

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

WHERE pause on error is enabled, IF a model call fails with a provider error that survived the retry policy —
`ErrorCode.unreachable`, `timeout`, `auth`, `rateLimited`, `upstream`, `modelNotFound`, `modelUnavailable` or
`missingCredential` — or the model call itself answers `ErrorCode.validation` (a provider refusing the request), THEN the system SHALL pause with that error, keep every count as it stood, and on resume
make the interrupted call again from its first request — except a reviewer call answered `timeout`, which does not pause
the run: its segments are flagged as the `quality-gates` capability's "Flag a segment the reviewer could not review" says.
A reviewer call answered `unreachable`, `upstream` or `rateLimited` pauses and recovers like any other call.

The system SHALL count the pauses of each step — a segment's draft, a segment's decision, or a chunk's reviewer call — by
the kind of recovery its error needs, and SHALL, on the failure past that kind's budget, flag the step's segment (for a
chunk's reviewer call, decide the chunk as if the reviewer were unavailable) with the error and go on with the run: two
pauses for `timeout` and for the codes only a person can fix (`auth`, `modelNotFound`, `missingCredential`,
`validation`); three for `ErrorCode.internal`, which a step answers when it throws instead of answering; and ten for an
outage (`unreachable`, `upstream`, `rateLimited`) and for an unloaded model (`modelUnavailable`), which are not the
step's fault and so never spend the step's own budget. WHILE the run is paused on such an error, the job SHALL offer to skip the step: the run
resumes, the step's segment is flagged with the error without its call being sent again, and the run goes on
(`TranslationJob.skipSegment`).

The system SHALL NOT pause on `ErrorCode.contextWindow` or `ErrorCode.emptyCompletion`, which flag the one segment and
let the run go on, nor on `busy` or `discoveryFailed`, which a run never sees and which end it Failed with
`ErrorCode.internal`. An unexpected error outside every step — before the first segment, while the run starts — SHALL
end the run Failed with `ErrorCode.internal` and a message saying the run could not begin.

WHILE a run from the window is paused on any such error, the translating screen SHALL show the provider-error state
naming the error — Retry now, which resumes the run; Open provider settings; and Stay paused, which leaves it paused.

**Source:** `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`, FR-RESUME-03
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/implementation_plan/CHANGE_BACKLOG.md` (D19).
In plain words: a stopped local server or an unloaded model is often fixed in a minute. The window always pauses on such
an error, whatever the review mode (see "Pause for review as the review mode says"), so the person fixes it and presses
Retry now instead of translating the book again. The call that failed — a draft, a repair or a chunk's reviewer call — is
made again; nothing already decided is redone. A bug in the application is not fixed by waiting, so it ends the run; a
prompt too long for the model belongs to one segment, so only that segment is flagged. A step that throws is retried
three times and then flagged, so one bad segment cannot end a night's run.

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
  presses Retry now
- **THEN** the run resumes, the interrupted call is made again, and the counts continue from 412

#### Scenario: Stay paused keeps the run waiting

- **WHEN** the person presses Stay paused on the same pause
- **THEN** the run stays paused and no request is sent

#### Scenario: An unloaded model pauses too

- **WHEN** pause on error is enabled and, on the Fast dial, LM Studio answers `400` with `{"error":"Model unloaded"}`
  for the second of three segments
- **THEN** the job pauses with `ErrorCode.modelUnavailable`, with 1 accepted, 0 flagged and 2 pending
- **AND** the window shows the provider-error state naming that error with Retry now

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

WHILE a run is paused on an error whose recovery is automatic — an outage (`unreachable`, `upstream`, `rateLimited`),
a `timeout`, an `internal` error a step answered, or an unloaded model (`modelUnavailable`) — and the step has pauses of that kind left, the system SHALL wait
and resume by itself, in every review mode, without the person: it SHALL wait 15 s, 30 s, 1 min, 2 min, 5 min and
10 min before the first six wakes and 10 min before every later one; at each wake it SHALL probe the provider (the
connection and the model list, which must still offer the run's model) and resume as soon as a probe passes, the
interrupted call being made again; a failed probe SHALL only extend the wait. An outage SHALL last from its first failure
until a model call answers again; once it has lasted 12 hours (`RecoverySchedule.MAX_OUTAGE`) the system SHALL stop
waking and wait for the person. The person's Retry now, Skip segment and Resume SHALL end the wait at once and start the
wake schedule afresh, but SHALL keep when the outage began (`downSince`), which only an answered model call clears; the
call sent again on the person's word during an outage or an unloaded model SHALL NOT count toward the pauses after
which the step is flagged (after a timeout or a throwing step it still counts). Pause SHALL hold
the run for the person, after which no wake resumes it; Stop SHALL end the run at once.
An unloaded model SHALL wake at most six times in one outage (`PauseDecider.UNLOADED_MODEL_WAKES`), counted across its
pauses until a model call answers again, after which the system SHALL announce that it gave up and wait for the person,
and the window SHALL say "The model is not available — load it in the provider at <host> and press Resume". A pause for
review, a pause the person asked for, and a pause on an error only a person can fix (`auth`, `modelNotFound`,
`missingCredential`, `validation`) SHALL NOT resume by itself.

The system SHALL announce each step of the recovery to its listeners (`RecoveryWaiting`: waiting with the next try's
time and attempt, gave up, or held) and log at INFO the outage's start, each wait, each probe's result and the resume
after N minutes.

**Source:** `docs/implementation_plan` overnight plan step 11 (owner intent: a run left all night is finished in the
morning or visibly retrying with a clear reason, never silently stuck).
In plain words: a local server that restarts, a laptop that loses its network for a while, or a provider that rate
limits are common over a night. The run waits, checks the server now and then without asking the model anything, and
goes on by itself; a wrong key or a missing model is not fixed by waiting, so those still wait for the person. A local
server that unloaded an idle model usually loads it again on the next request, so the run tries six times — about
nineteen minutes — and then asks the person to load it, instead of probing a model nobody loads all night.

#### Scenario: Retry now keeps the outage clock and the segment's budget

- **WHEN** the provider is down and the person presses Retry now at each of eleven waits, each call failing again with
  `ErrorCode.unreachable`, and the twelfth call answers
- **THEN** every wait says the provider is down since the first failure, each wait is attempt 1 of a fresh schedule,
  and the segment is accepted, not flagged after its tenth pause

#### Scenario: A twenty-minute outage resumes at the seventh wake

- **WHEN** the draft of the second of three segments is answered `ErrorCode.unreachable` and the provider stays down for
  20 minutes
- **THEN** the wakes at 0:15, 0:45, 1:45, 3:45, 8:45 and 18:45 find the provider down, and the seventh wake, 28 min 45 s
  after the failure (attempt 8 counting the failed call), finds it back and resumes the run
- **AND** the segment is sent again and accepted, and the run ends Completed with three accepted

#### Scenario: An outage longer than twelve hours waits for the person

- **WHEN** the provider stays down for two days
- **THEN** the run wakes 77 times, and after the wake at 12 h 8 min 45 s it announces that it gave up and stays paused
  until the person resumes or stops it

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

#### Scenario: A model that never loads gives up after six wakes

- **WHEN** every draft call is answered `ErrorCode.modelUnavailable` while every probe passes
- **THEN** the run wakes six times, pausing seven times in all, flags nothing, and announces that it gave up after the
  sixth wake
- **AND** the window shows "The model is not available" asking the person to load it and press Resume

### Requirement: Cancel a job

WHEN cancellation is requested, the system SHALL end the job Cancelled without waiting for the segment in progress or
for the provider, and SHALL keep every decided segment stored, so a new run for the same project in the same session
starts at the first PENDING segment. If a model request is in flight, the system SHALL abort it and SHALL NOT send any
further request. If the job is paused it SHALL end at once.

IF the thread running a paused job is interrupted, THEN the system SHALL treat it as a cancellation.

IF cancellation is requested before the job runs, THEN running it SHALL end the job Cancelled at once, without reading
the opened book the project holds or calling the model.

**Source:** FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0034.
In plain words: stopping is safe and is not an error. A stop does not wait for a slow provider: the request that is
waiting is aborted and nothing more is sent. What was decided before the stop is kept for the rest of the session, which
is what lets a stopped run be resumed; drafts of the current chunk that were not yet decided are dropped, and those
segments stay PENDING and are drafted again by the next run (the translation-pipeline capability's "Decide a chunk's
segments in document order"). A job cancelled before it starts does no work at all.

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

## REMOVED Requirements

### Requirement: Pause, resume and stop a run from the screen

**Reason**: Renamed to "Pause, stop and resume a run from the screen", which keeps the same behaviour — pause, resume
and stop offered on the screen, and stopping reported as a neutral outcome — except that a stopped run is no longer
terminal. The shipped scenario "A stopped run offers no resume" is now deliberately false: decisions are kept for the
session behind the storage ports (ADR-0034), so a stopped run can be resumed. A MODIFIED block cannot drop a shipped
scenario, so the rename is recorded as this removal plus the added requirement.

**Migration**: Read "Pause, stop and resume a run from the screen". It keeps pause, resume and stop and "stopping is
not an error" with their scenarios, replaces "A stopped run offers no resume" with "A stopped run resumes where it
stood" (Resume from the stopped state runs a new job from the first PENDING segment, the flagged segments stay in the
review panel), and adds the title bar's matching Pause/Resume and the paused and stopped banners that say progress lasts
until the application closes.
