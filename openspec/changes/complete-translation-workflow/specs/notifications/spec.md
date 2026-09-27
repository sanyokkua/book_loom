# Spec Delta

## ADDED Requirements

### Requirement: Route an import refusal by the inspection's verdict

WHEN the inspection of a chosen file answers that it is DRM-protected, the import screen SHALL show a banner of error
severity titled `This book is DRM-protected.` that names the encryption scheme when it is known, and SHALL offer to
choose another file.

WHEN the inspection answers that the file is unsupported, the import screen SHALL show a banner of error severity
titled `Couldn't read this file.` that names the file type detected, and SHALL offer to choose another file.

The application SHALL choose between those two surfaces from the inspection's verdict, never from an error code or from
the text of a message, and SHALL open no dialog and raise no transient message for either.

**Source:** ADR-0039, `docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#banners`,
`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#error-code-categories`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`, FR-IMPORT-05
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`).
In plain words: a DRM-protected book and a PDF both fail to open with the same error code, but they need different
explanations — one the person cannot fix and one where they picked the wrong file. The inspection names which it is as
plain data, so the screen routes on that rather than on a localized message that could change under it. The banner is
already on screen, so a dialog or a toast would only repeat it.

#### Scenario: A DRM-protected book names its scheme

- **WHEN** `Purchased_Novel.epub`, encrypted with Adobe ADEPT, is chosen on the import screen
- **THEN** an error banner titled `This book is DRM-protected.` names `Adobe ADEPT` and the screen offers
  `Choose another file`
- **AND** no dialog is opened and no transient message is raised

#### Scenario: An unsupported file names its type

- **WHEN** `book.pdf` is chosen on the import screen
- **THEN** an error banner titled `Couldn't read this file.` names `PDF` and the screen offers `Choose another file`
- **AND** no dialog is opened and no transient message is raised

#### Scenario: The same error code is routed two ways

- **WHEN** `Purchased_Novel.epub` and `book.pdf` would each fail to open with `ErrorCode.validation`
- **THEN** the first is shown as `This book is DRM-protected.` and the second as `Couldn't read this file.`

## MODIFIED Requirements

### Requirement: Carry four severities of transient message, coloured by the status roles

The application SHALL show transient messages in exactly four severities — success, information, warning and
error — and each SHALL take its colour from the matching status role of the token catalogue rather than from a
colour of its own.

A transient message SHALL NOT be the only place a blocking failure is reported.

The application SHALL raise a transient message on exactly these occasions, and SHALL NOT invent others in this
build:

- a book was opened, at success severity;
- a provider passed every stage it was tested on, at success severity;
- a run started, at information severity, titled `Translation started`, reading `Keep the application open —
  progress is kept only until it closes.`;
- a run finished, at success severity, or at warning severity when at least one segment is flagged;
- a flagged segment was accepted in review, at success severity, naming the segment and how many flagged segments remain;
- a retry was refused because a run is translating (`busy`), at warning severity;
- a model listing could not be read, at warning severity.

An export being written raises no transient message of its own: `export`'s completion dialog is the one surface
that reports it (see `export`, "Report the finished file").

**Source:** FR-NOTIF-01 (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#toasts`), FR-A11Y-8,
FR-THEME-10 (`docs/specification/01_Product/09_THEMING.md#status-colours`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#toasts`.
In plain words: four severities, four status roles, one mapping — so a person learns the colour once and it
means the same thing on a chip, a banner and a message. A blocking failure always gets a dialog as well, because a
transient message is gone in seconds and somebody looking away has then lost the only report there was. The occasions
are enumerated because a surface with no stated caller gets built, tested for its colour, and then either never raised
at all or raised from wherever each screen felt like. This change adds the ones its new actions need — starting a run,
accepting in review, and a retry that has to wait — each drawn in the reference rendering; a written export gets a
dialog instead of a toast, because export writes once and a dialog is what stays on screen long enough to read the
count it reports.

#### Scenario: Each severity takes its status role

- **WHEN** an error-severity transient message is shown under the light values
- **THEN** its foreground refers to the danger role, which resolves to `#b0574c`
- **AND** a success-severity message refers to the success role, which resolves to `#5f8a6b`

#### Scenario: A blocking failure is never transient-only

- **WHEN** a blocking failure is surfaced
- **THEN** a dialog is shown, whether or not a transient message is also shown

#### Scenario: Opening a book raises one success message

- **WHEN** `Frankenstein.epub` is opened successfully
- **THEN** one transient message of success severity is raised naming the book

#### Scenario: Starting a run raises one information message

- **WHEN** a run over `Frankenstein.epub` is started
- **THEN** one transient message of information severity titled `Translation started` is raised
- **AND** its body reads `Keep the application open — progress is kept only until it closes.`

#### Scenario: Accepting in review names the segment and what is left

- **WHEN** the flagged segment `ch5 · p12` is accepted in review and 2 flagged segments remain
- **THEN** one transient message of success severity reads `Accepted — ch5 · p12 saved. 2 left.`

#### Scenario: A run with flagged segments warns rather than congratulates

- **WHEN** a run completes with `1237` accepted and `3` flagged
- **THEN** one transient message of warning severity is raised

#### Scenario: A refusal raises no transient message of its own

- **WHEN** a book is refused because its inspection finds it DRM-protected
- **THEN** the refusing state is shown on the screen and no transient message is raised, because the screen is
  already reporting it

### Requirement: Report a provider failure as the run's own state

IF a run meets a failure whose code this capability assigns to the provider-error state, THEN the run SHALL be paused
with the counts it had reached kept, and the translating screen SHALL show that paused state as a provider error: a
banner of error severity naming what happened and the endpoint's host, saying that no work was lost; the progress bar
drawn in the danger role; and three actions — Retry now, Open provider settings and Stay paused. Every pause on error
SHALL be shown this way, including a pause on a `validation` refusal the provider returned.

IF a run ends on `ErrorCode.internal`, THEN the screen SHALL NOT show the provider-error state; the run is Failed and the
blocking dialog reports it. IF a model call answers `ErrorCode.contextWindow` or `ErrorCode.emptyCompletion`, THEN the
segment is flagged and the run is neither paused nor ended.

WHEN Retry now is used, the application SHALL resume the run, translating the interrupted segment again from its first
request.

WHEN Stay paused is used, the application SHALL withdraw the banner's actions and leave the run paused, resumable like
any paused run.

The provider-error state SHALL NOT open a dialog, because the run's own screen is already showing the failure and
a dialog over it would say the same thing twice.

**Source:** FR-NOTIF-02 (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#banners`), FR-NOTIF-04c
(`#typed-error-surface`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md#ui-surfacing`, ADR-0036,
`docs/next_features.md#15-left-open-by-the-hand-test`.
In plain words: the commonest failure by far is that the model server stopped — it was closed, it crashed, it unloaded
the model. That is not a reason to lose the run or to flag half a chapter: the run waits, says what went wrong and where,
and offers the three things a person might want — try again now that the server is back, go and fix the settings, or
leave it for later. Retry now redoes the one segment that was interrupted, so nothing is skipped.

#### Scenario: An unreachable server names itself

- **WHEN** the run over `Frankenstein.epub` is at 62% and nothing is listening at `http://localhost:11434` after the
  retries
- **THEN** the run is paused and the translating screen shows an error banner naming `Model server unreachable` and
  `localhost:11434` and saying no work was lost
- **AND** the progress bar is drawn in the danger role, which resolves to `#b0574c` under the light values
- **AND** Retry now, Open provider settings and Stay paused are offered, and no dialog is opened

#### Scenario: The counts survive the failure

- **WHEN** a run that had accepted `412` segments is paused by a provider error
- **THEN** the screen still reports `412` accepted

#### Scenario: An unloaded model shows the provider-error state

- **WHEN** LM Studio answers `400` `{"error":"Model unloaded"}` for `ch7 · p41` and the run pauses with
  `ErrorCode.validation`
- **THEN** the translating screen shows the provider-error banner with Retry now, Open provider settings and Stay paused
- **AND** no dialog is opened

#### Scenario: An internal failure is not a provider error

- **WHEN** the run's model call throws for `ch7 · p41`
- **THEN** the run ends Failed with `ErrorCode.internal` and the blocking dialog is shown
- **AND** no provider-error banner is shown

#### Scenario: Retry now redoes the interrupted segment

- **WHEN** the run was paused by `ErrorCode.unreachable` while translating `ch7 · p41`, the server is started again, and
  Retry now is used
- **THEN** the run resumes and the next request sent is the first request for `ch7 · p41`

#### Scenario: Staying paused keeps the run

- **WHEN** Stay paused is used on the provider-error banner
- **THEN** the banner's actions are withdrawn and the run stays paused with Resume offered

#### Scenario: Open provider settings goes to the providers area

- **WHEN** Open provider settings is used on the provider-error banner
- **THEN** the settings screen is shown on its providers area and the run stays paused

### Requirement: Decide the surface from the failure's own code

The application SHALL choose a failure's surface from the typed code it carries, and SHALL use this assignment:

- `unreachable`, `timeout`, `auth`, `rateLimited`, `upstream`, `modelNotFound`, `modelUnavailable` and
  `missingCredential` reaching a run SHALL be shown as the run's provider-error state, naming the code, the run paused
  and resumable, not ended;
- `validation` answered by a model call during a run — a provider refusing the request, such as LM Studio's
  `Model unloaded` — SHALL likewise be shown as the provider-error state naming that code, the run paused;
- `contextWindow` and `emptyCompletion` SHALL flag the segment they were returned for, shown in the review list with
  that finding, and SHALL NOT pause or end the run or open a dialog;
- `cancelled` SHALL be shown as the run's stopped state, with no dialog and no message of error severity;
- any other `validation` — a refusal made before any model call, such as a start or an export refused — SHALL be shown
  in place, on the screen that refused, as a message naming what is wrong, and SHALL NOT be shown as a provider error;
- `busy`, which this capability only ever sees when a review retry is asked for while the project's run is running,
  SHALL be shown as a transient message of warning severity saying the retry waits for the run, and SHALL NOT open a
  dialog and SHALL NOT pause or end the run;
- `internal`, and any code this list does not otherwise name, SHALL end a run Failed and SHALL be surfaced as a
  blocking failure in the dialog, never as a pause. A run's own model call can never answer `busy` (the request
  queues instead of being refused) or `discoveryFailed` (a run makes no discovery call); WHERE either code ever
  reaches a run regardless, it SHALL be treated exactly as `internal`, so that neither code can end a run through any
  other path than this one;
- `discoveryFailed`, reached only from a model listing, SHALL be shown only where that list was asked for, as the
  note that no list was obtained, and SHALL NOT pause or end a run or open a dialog.

**Source:** FR-NOTIF-04a, FR-NOTIF-04c
(`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`), `#error-code-categories`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md#ui-surfacing`, ADR-0022, ADR-0036.
In plain words: without this table, "something returned an error" matches every state a screen has, and a damaged
export plausibly renders as "the model server is unreachable", which sends the person to fix a machine that was never
broken. A provider failure now pauses the run instead of ending it, because the server coming back is the usual
outcome; an empty reply or a prompt too long for the model is one segment's problem, so it flags that segment and the
run carries on; a provider's `400` refusal arrives as `validation` but behaves like any provider failure, so it is told
apart from a screen's own refusal by where it came from; and a retry pressed while a run is translating is a "not
now", not a failure worth a dialog. `busy` and `discoveryFailed` are named twice on purpose: once for the one place
each is actually seen, and once to say what happens if a run's model call ever answered with either anyway — treated
as `internal`, so a code meant to mean "try later" or "no list today" can never be read as "the run has failed" and
vice versa. Naming all fifteen codes is what makes the routing a thing a test can check rather than a thing each
screen decides for itself.

#### Scenario: A provider failure takes the provider-error state

- **WHEN** a run meets `ErrorCode.upstream` after its retries
- **THEN** the translating screen shows its provider-error state with the run paused and Retry now offered
- **AND** no dialog is opened

#### Scenario: An empty reply flags one segment and the run goes on

- **WHEN** the reply for `ch3 · p8` is `ErrorCode.emptyCompletion`
- **THEN** `ch3 · p8` is flagged and appears in the review list, and the run continues with the next segment
- **AND** no dialog is opened

#### Scenario: A provider refusal answered as validation takes the provider-error state

- **WHEN** a run's model call is answered `400` `{"error":"Model unloaded"}`, which the client reports as
  `ErrorCode.validation`
- **THEN** the run is paused and the translating screen shows its provider-error state naming `validation`, with Retry
  now offered
- **AND** no dialog is opened

#### Scenario: A context overflow flags one segment and the run goes on

- **WHEN** the model answers `ErrorCode.contextWindow` for `ch3 · p9`
- **THEN** `ch3 · p9` is flagged and appears in the review list, and the run continues with the next segment
- **AND** the run is neither paused nor ended and no dialog is opened

#### Scenario: A refused start is reported in place, not as a provider error

- **WHEN** starting an export is refused with `ErrorCode.validation` because the Save to path is the source file
  `/books/Frankenstein.epub`
- **THEN** the export screen shows a message naming `/books/Frankenstein.epub` as the problem
- **AND** the provider-error state is not shown and the provider settings are not offered

#### Scenario: A stop is neither a dialog nor an error message

- **WHEN** a run ends with `ErrorCode.cancelled`
- **THEN** the translating screen shows its stopped state
- **AND** no dialog is opened and no message of error severity is shown

#### Scenario: A retry during a run is a warning, not a dialog

- **WHEN** Retry is used on a flagged segment while the run is translating and the answer is `ErrorCode.busy`
- **THEN** one transient message of warning severity is raised
- **AND** no dialog is opened

#### Scenario: An unexpected failure is blocking

- **WHEN** a run ends with `ErrorCode.internal`
- **THEN** a blocking dialog is shown carrying the failure's title, message and folded details
- **AND** the run is Failed, not paused, and the provider-error state is not shown

#### Scenario: A code a run's own model call cannot answer is treated as internal

- **WHEN** a run's model call answers `ErrorCode.busy` or `ErrorCode.discoveryFailed`, which its gate and its retry
  logic never actually return
- **THEN** the run ends Failed with the blocking dialog, exactly as `ErrorCode.internal` would
- **AND** no transient message of warning severity is raised for it

#### Scenario: An unreadable model listing stays in the settings

- **WHEN** a model list is asked for and the answer is `ErrorCode.discoveryFailed`
- **THEN** the settings screen reports that no list was obtained
- **AND** no dialog is opened and no run state changes
