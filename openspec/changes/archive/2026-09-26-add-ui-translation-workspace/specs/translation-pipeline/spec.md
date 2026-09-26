# Spec Delta

## ADDED Requirements

### Requirement: Start a run and report its progress

The translating screen SHALL start a run for the open book, the chosen target language, the chosen destination
and the chosen model, and WHILE the run is in progress SHALL report the proportion complete, how many segments
have been accepted, how many were flagged, how many remain, and a log of what the run has decided.

The screen SHALL refuse to start when no book is open, no target language is chosen, or no model is chosen, and
SHALL say which is missing.

Each reported figure SHALL be derived from the progress snapshot the engine emits, and from nothing else:
**remaining** is that snapshot's pending count; **accepted** and **flagged** are its accepted and flagged counts;
the **total** is the sum of those three; and the **proportion complete** is the accepted and flagged counts
together, over that total. The chapter-level indices the snapshot also carries SHALL NOT be used for any of them.

WHERE the total is zero, the screen SHALL report the proportion complete as zero rather than dividing by it.

The screen SHALL NOT report a rate of progress or an estimated finishing time.

**Source:** FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`.
In plain words: these four counts are what the engine actually reports, so these are what the dashboard shows.
Throughput and a time estimate are drawn in the reference and are not derivable from what the engine emits, so
they are refused rather than guessed — a made-up estimate on a run that takes hours is worse than no estimate.
The derivation is written out because the snapshot carries no total and no field called "remaining": it carries
accepted, flagged and pending, plus a chapter index and a chapter count. Two people reading "the proportion
complete" off that record will build two different progress bars — one counting segments and one counting
chapters — and only one of them agrees with the counts printed beside it.

#### Scenario: Counts advance as segments are decided

- **WHEN** the engine reports `768` accepted, `3` flagged and `469` pending
- **THEN** the screen reports `768` accepted, `3` flagged, `469` remaining and a total of `1240`
- **AND** the proportion complete is `771` of `1240`

#### Scenario: The proportion ignores the chapter indices

- **WHEN** the engine reports `768` accepted, `3` flagged and `469` pending while its chapter index is `7` of `11`
- **THEN** the proportion complete is `771` of `1240`, and is not `7` of `11`

#### Scenario: An empty book does not divide by zero

- **WHEN** the engine reports `0` accepted, `0` flagged and `0` pending
- **THEN** the screen reports the proportion complete as zero and does not fail

#### Scenario: A run cannot start without a model

- **WHEN** a book is open, the target language is `uk`, and no model has been chosen
- **THEN** starting is refused with a message naming the missing model, and no run begins

#### Scenario: The log records each decision

- **WHEN** a segment is accepted during a run
- **THEN** an entry naming that segment and its outcome is appended to the log

### Requirement: Give every activity-log entry a kind, a status role and a catalogue message

Every entry the activity log holds SHALL carry one kind from a fixed vocabulary of seven — accepted, repaired,
glossary-applied, summary-updated, retried, segment-error and milestone — and each kind SHALL carry both a status
role from the token catalogue and a mark that is not its colour, so the kind is readable without seeing the
colour.

The text of every entry SHALL come from the message catalogue by key, with the segment identifier and any other
value substituted into it.

The log SHALL retain its most recent entries up to a fixed bound and SHALL drop the oldest first.

**Source:** FR-NOTIF-6a, FR-NOTIF-6b
(`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#activity-log`), FR-A11Y-6
(`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#accessibility`), FR-UI-08
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`).
In plain words: the log is the only record of what a multi-hour run decided, and nothing is stored, so it is also
the only record there will ever be. A fixed vocabulary is what makes it scannable — seven kinds a person learns
once, rather than free-form sentences the engine happened to produce. Colour alone cannot carry the kind, because
a person who cannot distinguish sage from terracotta then has a log of identical grey lines. And the text comes
from the catalogue for the same reason every other visible string does: an English log inside a Ukrainian window
is the half-translated interface this project's i18n rules exist to prevent.

#### Scenario: An accepted segment logs its kind and role

- **WHEN** segment `741` is accepted during a run
- **THEN** an entry of kind accepted is appended, referring to the success role, which resolves to `#5f8a6b`
  under the light values
- **AND** it carries a mark distinguishing it from a segment-error entry without relying on colour

#### Scenario: Entry text comes from the catalogue

- **WHEN** the accepted entry for segment `741` is rendered while the interface is in Ukrainian
- **THEN** its text resolves from a catalogue key with `741` substituted into it, and is not an English sentence
  with a number joined to it

#### Scenario: Each kind maps to its own status role

- **WHEN** the seven kinds are read
- **THEN** accepted refers to the success role; repaired, glossary-applied, summary-updated and milestone refer to
  the information role; retried refers to the warning role; and segment-error refers to the danger role

#### Scenario: The log drops its oldest entries

- **WHEN** a run appends more entries than the log's bound
- **THEN** the log holds exactly the bound, and the entry appended first is no longer present

#### Scenario: No rate and no estimate are shown

- **WHEN** a run has been in progress for ten minutes
- **THEN** the screen reports the counts and the proportion complete, and shows no segments-per-minute figure and
  no estimated finishing time

### Requirement: Learn the run's outcome even when no event is sent

The application SHALL treat the result the engine returns when a run finishes as the authoritative outcome, and
SHALL treat the events emitted during the run as progress only.

IF a run is refused before it begins and therefore emits no finishing event, THEN the screen SHALL still leave
the running state and report the refusal.

**Source:** FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`),
`docs/next_features.md` §14,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#state-mirror`.
In plain words: a run refused at the start — the destination already exists, or the book will not open — returns
its error without ever emitting a finishing event. A screen that trusted only the event stream would sit spinning
forever on the commonest mistake there is, which is exactly what the review of the command line predicted.

#### Scenario: A refused start leaves the running state

- **WHEN** a run is started with a destination that already exists and replacement is not allowed
- **THEN** the screen leaves the running state and reports the refusal, although no finishing event was emitted

#### Scenario: Events alone do not decide the outcome

- **WHEN** a run emits progress events and then returns a failure
- **THEN** the screen reports the failure rather than the last progress it saw

### Requirement: Pause and stop act without waiting for the provider

WHEN a pause or a stop is requested while a model request is in flight, the engine SHALL abort that request without
waiting for the provider to answer, and SHALL NOT send any further request to the provider — not a retry, not a
structural repair and not a placeholder repair — after the request was made.

WHEN a pause aborted a request, the engine SHALL report the run as paused with no error, and WHEN the run is
resumed the engine SHALL translate the interrupted segment again from its first request, so the segment is counted
once and the run continues from where it stood.

WHEN a stop aborted a request, the engine SHALL end the run as cancelled, and SHALL NOT keep the interrupted
segment as a decision.

The engine SHALL NOT interrupt any work other than a model request: neither the wait while paused nor the export
is ever interrupted by a pause or a stop.

**Source:** FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`.
In plain words: a local model can take minutes to answer, and a request that times out is retried up to three
times. If Pause and Stop were only noticed between segments, pressing either would do nothing for as long as that
takes — a hand run showed a Pause ignored for two minutes while a retry went to the provider, with the screen
saying "pausing" throughout. So the button aborts the request that is waiting, and nothing further is sent. The
segment whose request was aborted has no decision yet, which is why resuming translates it again instead of
skipping it. Aborting is confined to the request itself, because interrupting the export half-way through writing
the file would corrupt it.

#### Scenario: A stop aborts a slow request

- **WHEN** the provider is taking `30` seconds to answer the only segment's request, and a stop is requested `1`
  second after the request was sent
- **THEN** the run ends as cancelled within `5` seconds and the provider has received exactly `1` request

#### Scenario: A stop sends no repair request

- **WHEN** the first reply is not the required JSON object, which would normally be followed by a structural repair
  request, and a stop is requested while that first request is still in flight
- **THEN** the run ends as cancelled and the provider has received exactly `1` request

#### Scenario: A pause during the wait before a retry sends no further request

- **WHEN** the first request timed out after `300` milliseconds, the engine is waiting before its retry, and a pause
  is requested
- **THEN** the run reports paused with reason `REQUESTED` and no error, and the provider has received exactly `1`
  request

#### Scenario: A resumed run translates the interrupted segment again

- **WHEN** a two-segment book has its first segment accepted, a pause aborts the request for the second, and the run
  is resumed
- **THEN** the provider receives that second segment's request a second time, so `3` requests in all, and the final
  report counts `2` segments and `2` accepted

#### Scenario: A pause never interrupts the export

- **WHEN** a pause is requested after the export stage has started
- **THEN** the pause is ignored, the export completes, and the run ends as completed

### Requirement: Announce each model call as it starts

WHEN the engine starts a model call, it SHALL emit one event naming the segment the call belongs to, before waiting for
the answer, and SHALL NOT emit it for a call that was refused because a pause or a stop was already requested. A
segment's first draft, a structural repair and a placeholder repair are each their own model call and each SHALL emit
its own event. The client's own retries of one call — a timeout, a `Retry-After` wait — belong to that same call and
SHALL NOT emit another event.

**Source:** FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`.
In plain words: between two segment decisions the engine says nothing, and with a local model that gap can last
minutes. Without a sign that a call went out, a screen cannot tell "the model is thinking" from "the run is stuck". A
segment that needs a repair asks the model again, so it produces one event per ask. A provider client that retries a
timed-out request is still waiting on the same ask, so the person keeps seeing one continuous wait rather than a
counter that starts over every three minutes.

#### Scenario: One event before each decision

- **WHEN** a two-segment book is translated and each segment is answered by its first call
- **THEN** the events are, in order, the translate stage start, a model-call event for `Book.md:0`, that segment's
  decision, a model-call event for `Book.md:1`, that segment's decision, the export stage start and the finish

#### Scenario: A repair is its own call

- **WHEN** the first reply for `Book.md:0` is not the required JSON object and the structural repair call is answered
  correctly
- **THEN** two model-call events for `Book.md:0` are emitted, one before each call, before that segment's decision

#### Scenario: The client's retry does not announce again

- **WHEN** the first attempt of the draft call for `Book.md:0` times out and the client sends its second attempt
- **THEN** exactly one model-call event for `Book.md:0` was emitted for that draft call, and none for the second
  attempt

#### Scenario: A refused call is not announced

- **WHEN** a pause is already requested when the engine is about to make a model call
- **THEN** the provider receives no request and no model-call event is emitted

### Requirement: Tell the person when the model is slow to answer

WHILE the run is running and a model call has been outstanding for at least `10` seconds, the dashboard SHALL
show in its state banner the text "Waiting for the model… m:ss", where m:ss is how long that call has waited,
and SHALL update it once a second.

WHEN the run's next segment is decided, or a new model call starts, or a pause or a stop is requested, or the run
pauses, resumes, finishes or is stopped, the dashboard SHALL stop showing the waiting text and SHALL show the
banner of the run's state instead; a call answered in under `10` seconds SHALL show no waiting text at all. The client's
own retries of the same call are not a new model call, so the waiting text SHALL keep counting across them.

**Source:** FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`.
In plain words: a hand run showed the counters standing still for minutes while one request waited on a slow
model, and the dashboard looked hung. Ten seconds is longer than a normal answer from a local model, so the notice
appears only when something is unusually slow and never flickers on a healthy run. The banners for pausing,
stopping, paused and the outcomes keep their own words, because they answer the person's button, not the model.

#### Scenario: Nothing is said for a normal answer

- **WHEN** a model call has been outstanding for `9` seconds
- **THEN** the banner shows the running text "The run is in progress. You can pause or stop it at any time."

#### Scenario: The waiting text appears at ten seconds and counts on

- **WHEN** a model call has been outstanding for `10` seconds, and later for `12` seconds
- **THEN** the banner text is "Waiting for the model… 0:10" and then "Waiting for the model… 0:12", and in Ukrainian
  «Очікування відповіді моделі… 0:12»

#### Scenario: A client retry does not restart the count

- **WHEN** the draft call's first attempt timed out after `3` minutes and the client has sent its second attempt
  `5` seconds ago
- **THEN** the banner text is "Waiting for the model… 3:05", and not "Waiting for the model… 0:05"

#### Scenario: A decision withdraws it

- **WHEN** the banner shows "Waiting for the model… 0:12" and the segment is then decided
- **THEN** the banner shows the running text again

#### Scenario: A pause request replaces it

- **WHEN** the banner shows "Waiting for the model… 0:12" and the person presses Pause
- **THEN** the banner shows "Pausing" and never "Waiting for the model…" while the pause is pending

## MODIFIED Requirements

### Requirement: Report progress and the outcome

WHILE a job runs, the system SHALL notify every subscriber, in order, when:

- a stage starts;
- a model call starts, naming the segment it belongs to, before each model call — that is, before each segment's
  decision and before each repair;
- a segment is decided, with the accepted, flagged and pending counts;
- the job pauses, with its reason, or resumes;
- the job finishes.

WHEN a job ends, the system SHALL return a successful result carrying:

- the book format;
- how the job ended: Completed, Cancelled or Failed;
- the number of segments, and the accepted and flagged counts;
- each flagged segment with its reason;
- the written file when Completed, and the error when Failed.

**Source:** `02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`, `02_Architecture/09_ERROR_HANDLING.md#partial-results`.
In plain words: a screen can follow a job live, and a job that stopped still tells its caller exactly what was done and
why it stopped. The announcement that a model call has started is what lets a screen tell a slow model from a stuck
run, because between two decisions nothing else is said. A caller that needs no live updates, like the command line,
subscribes to nothing.

#### Scenario: Events for a three-segment book

- **WHEN** a book with three segments is translated with no pause points and every reply is accepted on the first call
- **THEN** a subscriber receives, in order:
  1. translation stage started;
  2. a model-call event and then a segment-decided event, three times over, the segment-decided events having pending
     counts 2, 1 and 0;
  3. export stage started;
  4. finished as Completed.

#### Scenario: A failed job still returns its report

- **WHEN** a three-segment job ends Failed because the model answered `ErrorCode.unreachable` for the second segment
- **THEN** the result is successful
- **AND** its report says Failed with `ErrorCode.unreachable`, 3 segments, 1 accepted, 0 flagged and no written file
