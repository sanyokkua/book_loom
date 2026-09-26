# Spec Delta

## Purpose

How a failure reaches the person: the blocking dialog that carries a failure's own title, message and expandable
safe details; the transient messages that carry the four severities; and the rule that decides, from the typed
code a failure carries, which of those surfaces it goes to — so that a stop a person chose, a destination that
already exists and a model server that is not running are three visibly different events rather than one "it
went wrong".

## ADDED Requirements

### Requirement: Show a blocking failure in a dialog carrying its own details

WHEN a failure is surfaced as blocking, the application SHALL show a dialog carrying that failure's title and
its message, and SHALL offer its details in a section that is collapsed until the person expands it.

The dialog SHALL show only the details the failure itself carries, and SHALL NOT show the underlying cause, a
stack trace, a credential, an authorization header, a request body or a response body at any time.

IF the failure is marked as worth retrying, THEN the dialog SHALL offer to retry alongside dismissing it.

**Source:** FR-NOTIF-03a, FR-NOTIF-03b, FR-NOTIF-03c
(`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#error-dialog`), FR-NOTIF-04b
(`#typed-error-surface`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-error-with-details`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md#ui-surfacing`.
In plain words: the summary is for the person and the details are for whoever they forward it to, which is why
the details are present but folded away rather than absent. The cause is excluded outright because it is the one
part of a failure that was never filtered — it is the original throwable, and it can carry anything that was in
scope when it was thrown.

#### Scenario: A blocking failure shows its title, message and folded details

- **WHEN** a failure titled `Translation failed` carrying the details `attempt 3 of 3; model gemma3:12b` is
  surfaced as blocking
- **THEN** a dialog shows the title `Translation failed` and its message
- **AND** the text `attempt 3 of 3; model gemma3:12b` is present but not shown until the details section is
  expanded

#### Scenario: A retryable failure offers to retry

- **WHEN** a failure carrying `ErrorCode.timeout`, which is marked as worth retrying, is surfaced as blocking
- **THEN** the dialog offers to retry and to dismiss

#### Scenario: A non-retryable failure offers no retry

- **WHEN** a failure carrying `ErrorCode.validation`, which is not marked as worth retrying, is surfaced as
  blocking
- **THEN** the dialog offers to dismiss and does not offer to retry

#### Scenario: The cause never reaches the dialog

- **WHEN** a failure whose cause is a `java.net.ConnectException` carrying the text
  `Connection refused: localhost/127.0.0.1:11434` is surfaced as blocking
- **THEN** neither the message, the details, nor the expanded section contains that text or any stack frame

### Requirement: Carry four severities of transient message, coloured by the status roles

The application SHALL show transient messages in exactly four severities — success, information, warning and
error — and each SHALL take its colour from the matching status role of the token catalogue rather than from a
colour of its own.

A transient message SHALL NOT be the only place a blocking failure is reported.

The application SHALL raise a transient message on exactly these occasions, and SHALL NOT invent others in this
build: a book was opened, at success severity; a provider passed its check, at success severity; a run finished,
at success severity; a run finished with at least one flagged segment, at warning severity; and a model listing
could not be read, at warning severity.

**Source:** FR-NOTIF-01 (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#toasts`), FR-A11Y-8,
FR-THEME-10 (`docs/specification/01_Product/09_THEMING.md#status-colours`).
In plain words: four severities, four status roles, one mapping — so a person learns the colour once and it
means the same thing on a chip, a banner and a message. The last sentence is why a blocking failure always gets
a dialog as well: a transient message is gone in seconds, and somebody looking away has then lost the only
report there was. The five occasions are enumerated because a surface with no stated caller gets built, tested
for its colour, and then either never raised at all or raised from wherever each screen felt like — and the
reference rendering's own trigger table is written for features this build does not have. Five is what the live
screens can actually report.

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

#### Scenario: A run with flagged segments warns rather than congratulates

- **WHEN** a run completes with `1237` accepted and `3` flagged
- **THEN** one transient message of warning severity is raised

#### Scenario: A refusal raises no transient message of its own

- **WHEN** a book is refused with `ErrorCode.validation`
- **THEN** the refusing state is shown on the screen and no transient message is raised, because the screen is
  already reporting it

### Requirement: Report a provider failure as the run's own state

IF a run ends with a failure whose code this capability assigns to the provider-error state, THEN the translating
screen SHALL show that state, and that state SHALL name what happened, offer to open the provider settings, and
keep the counts the run had reached.

The provider-error state SHALL NOT open a dialog, because the run's own screen is already showing the failure and
a dialog over it would say the same thing twice.

**Source:** FR-NOTIF-02 (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#banners`), FR-NOTIF-04c
(`#typed-error-surface`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md#ui-surfacing`.
In plain words: the commonest failure by far is that the model server is not running, and the useful response is
a route to the screen where that is fixed — not a stack trace and not a dead progress bar. Keeping the counts
means the person can see how much was done before it fell over, which is also the only record of it, since
nothing is stored.

#### Scenario: An unreachable server names itself

- **WHEN** a run fails because nothing is listening at `http://localhost:11434`
- **THEN** the screen shows the provider-error state reporting `ErrorCode.unreachable` and offers to open the
  provider settings
- **AND** no dialog is opened

#### Scenario: The counts survive the failure

- **WHEN** a run that had accepted `412` segments fails with a provider error
- **THEN** the screen still reports `412` accepted

### Requirement: Decide the surface from the failure's own code

The application SHALL choose a failure's surface from the typed code it carries, and SHALL use this assignment:

- `unreachable`, `timeout`, `auth`, `rateLimited`, `upstream`, `emptyCompletion`, `modelNotFound`,
  `modelUnavailable`, `missingCredential` and `contextWindow` ending a run SHALL be shown as the run's
  provider-error state;
- `cancelled` SHALL be shown as the run's stopped state, with no dialog and no message of error severity;
- `validation` SHALL be shown in place, on the screen that refused, as a message naming what is wrong, and SHALL
  NOT be shown as a provider error;
- `internal` and `busy` SHALL be surfaced as blocking failures in the dialog;
- `discoveryFailed` SHALL be shown only where a model list was asked for, as the note that no list was obtained,
  and SHALL NOT end a run or open a dialog.

**Source:** FR-NOTIF-04a, FR-NOTIF-04c
(`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#typed-error-surface`), `#error-code-categories`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md#ui-surfacing`.
In plain words: without this table, "the run returned an error" matches every state a screen has, and the
commonest mistake of all — a destination that already exists — plausibly renders as "the model server is
unreachable", which sends the person to fix a machine that was never broken. Naming all fifteen codes is what
makes the routing a thing a test can check rather than a thing each screen decides for itself.

#### Scenario: A provider failure takes the provider-error state

- **WHEN** a run ends with `ErrorCode.unreachable`
- **THEN** the translating screen shows its provider-error state and no dialog is opened

#### Scenario: A refused start is reported in place, not as a provider error

- **WHEN** a run is refused before it begins with `ErrorCode.validation` because the destination already exists
  and replacement is not allowed
- **THEN** the translating screen shows a message naming the destination as the problem
- **AND** the provider-error state is not shown and the provider settings are not offered

#### Scenario: A stop is neither a dialog nor an error message

- **WHEN** a run ends with `ErrorCode.cancelled`
- **THEN** the translating screen shows its stopped state
- **AND** no dialog is opened and no message of error severity is shown

#### Scenario: An unexpected failure is blocking

- **WHEN** a run ends with `ErrorCode.internal`
- **THEN** a blocking dialog is shown carrying the failure's title, message and folded details

#### Scenario: An unreadable model listing stays in the settings

- **WHEN** a model list is asked for and the answer is `ErrorCode.discoveryFailed`
- **THEN** the settings screen reports that no list was obtained
- **AND** no dialog is opened and no run state changes
