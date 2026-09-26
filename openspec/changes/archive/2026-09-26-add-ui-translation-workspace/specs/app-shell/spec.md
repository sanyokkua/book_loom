# Spec Delta

## Purpose

The application window and everything structural inside it — the title bar, the grouped navigation, the
breadcrumb, the area the current screen occupies, and the hosts that dialogs and transient messages appear in —
together with the rules that keep the window responsive, say which mockup regions each screen must match, and
say which real control stands behind each drawn one.

The workflow screens themselves are contracted by the capability whose behaviour each one surfaces: opening a
book by `book-import`, the brief by `book-brief`, the parsed structure by `document-round-trip`, driving a run by
`translation-pipeline`, pausing and stopping it by `resume`, the finished file by `export`, and every failure
surface by `notifications`.

## ADDED Requirements

### Requirement: Present one window divided into a fixed set of regions

The application SHALL present a single window containing, in fixed positions: a title bar carrying the product
name, a theme control and an about action; a navigation column down the left; a breadcrumb naming where the
person currently is; an area in which the current screen's own actions appear; an area in which one screen at a
time is shown; an area in which a modal dialog is shown over a dimming layer; and an area in which transient
messages appear.

The actions area SHALL be a region of the shell rather than part of any screen, and SHALL be empty for a screen
that offers no shell-level action.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`.
In plain words: the furniture never moves. A person learns once where the navigation is and where errors appear,
and every screen after that is just the middle changing. The actions area is named as a region rather than left
to each screen because the reference rendering draws it in one place on every screen, and a screen that drew its
own would put it somewhere slightly different every time.

#### Scenario: The window opens with its regions in place

- **WHEN** the application is launched
- **THEN** the window shows a title bar, a navigation column, a breadcrumb, an actions area and a screen area
- **AND** the title bar carries the text `BookLoom`, a theme control and an about action

#### Scenario: A screen with no shell-level action leaves the area empty

- **WHEN** the structure screen, which offers no shell-level action, is shown
- **THEN** the actions area is present and holds no control
- **AND** the region itself does not collapse or move the screen area

### Requirement: Keep every part of the window reachable at its smallest size

The application SHALL NOT allow the window to be made smaller than 960 pixels wide by 640 pixels high, measured as the
outer window, its title bar and borders included. The content area inside it is smaller by the size of that
decoration — about 944 pixels wide where the borders are 8 pixels each and about 612 pixels high where the title bar
is 28 pixels — so the guarantees below are stated for a content area of 944 by 600 pixels.

At the outer minimum every screen SHALL fit the width of the content area, and every part of it SHALL be reachable,
scrolling vertically where the screen is taller than the content area.

WHEN the navigation column is taller than the window, the column SHALL scroll vertically and SHALL NOT scroll
sideways. WHEN the screen being shown is taller or wider than the area the shell gives it, the area SHALL scroll,
and a screen with a list that grows SHALL still fill the area when it is not too tall.

At the smallest window size a count tile SHALL keep a width of at least 120 pixels and SHALL show its number and
its caption whole, without an ellipsis. A progress bar SHALL keep a height of 9 pixels however short the window is.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`, and the reference rendering's
`.content` (scrolls, padded), `.sidebar` (scrolls), `.stat` (`min-width: 120px`) and `.bar` (`height: 9px`) rules.
In plain words: 960 by 640 is the size of the whole window, not of the space the screen is drawn in, and the space
inside is smaller by the title bar and the borders, by different amounts on different systems. So the promise is
written against a content area of 944 by 600, which is no larger than any system gives at the minimum, and it is that:
nothing is cut off sideways, and whatever does not fit in height can be scrolled to. A window that can be dragged
smaller than its own contents, with nothing to scroll, hides parts of
the application for good: the hand run lost the logo, the Pause and Stop row and the progress bar, and cards
showed only "…". The reference rendering already scrolls its sidebar and its content and gives a tile and the bar
a fixed floor, so the application does the same, and refuses a window too small to be useful.

#### Scenario: The window cannot be shrunk below the minimum

- **WHEN** the application's window is shown
- **THEN** its minimum outer width is 960 pixels and its minimum outer height is 640 pixels

#### Scenario: Every screen fits the width at the outer minimum

- **WHEN** the translating screen is shown in a content area 944 pixels wide by 600 pixels high
- **THEN** the content area shows no horizontal scroll bar
- **AND** scrolling to the bottom brings the Stop button fully into view

#### Scenario: A short window scrolls the screen instead of clipping it

- **WHEN** the translating screen is shown in a window whose content area is 300 pixels high
- **THEN** the content area scrolls vertically
- **AND** scrolling to the bottom brings the Stop button fully into view

#### Scenario: A short window scrolls the navigation column

- **WHEN** the window is 320 pixels high
- **THEN** the title bar is fully visible at the top of the window
- **AND** the navigation column scrolls so that the Settings entry can be reached

#### Scenario: Count tiles keep their captions at the minimum width

- **WHEN** the translating screen is shown in a content area 944 pixels wide with 768 segments accepted, 3 flagged, 469
  remaining and 1,240 in total
- **THEN** each of the four tiles is at least 120 pixels wide
- **AND** the captions `accepted`, `flagged`, `remaining` and `in total` and the numbers `768`, `3`, `469` and
  `1,240` are shown without an ellipsis

#### Scenario: The progress bar is never squeezed away

- **WHEN** the translating screen is shown in a window shorter than the screen's own height, at 42% progress
- **THEN** the progress bar is 9 pixels high and its filled part has a positive width

### Requirement: Report the build version in the window and in the log

The application SHALL offer an about action that names the product, its licence and the build version, and that
version SHALL read `dev` in any build that carries no injected release version.

WHEN the application starts, it SHALL write the same version to the log exactly once.

**Source:** FR-UI-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), DD-50,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-about`.
In plain words: when somebody reports a bug, the first question is which build they were running, and there are
exactly two places that answer can come from — the dialog they can read to you, and the log file they can send
you. Both have to say the same thing, and the log has to say it once rather than on every screen change.

#### Scenario: The about action reports the build

- **WHEN** the about action is used
- **THEN** a dialog names the product, its licence, and the build version, which is `dev` in a build that carries
  no injected version

#### Scenario: The version is logged once at startup

- **WHEN** the application starts and reaches its first shown window
- **THEN** exactly one log line at INFO carries the build version `dev`
- **AND** no further line repeats it while screens are changed

### Requirement: Group the navigation and number the workflow

The navigation column SHALL present its entries in two named groups — the workflow and the application — and
SHALL number the workflow entries in the order they are worked through.

The navigation SHALL mark exactly one entry as the current one, and that mark SHALL follow the screen being
shown, however the person arrived at it.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`.
In plain words: the numbers tell a first-time user that this is a sequence with an end, not a pile of unrelated
screens. One highlighted entry, always agreeing with what is on screen, is what stops the navigation from lying
after a jump. There are two groups and not the reference rendering's three because the third group there lists
the mockup's specimen sheets, which are drawings for the designer and not screens a person can use.

#### Scenario: The navigation has exactly two groups

- **WHEN** the navigation column is read
- **THEN** it carries exactly two group headings, `Workflow` and `Application`, in that order
- **AND** the application group lists the settings entry alone
- **AND** no entry is labelled `Component library`, `Dialogs & alerts` or `Notifications`

#### Scenario: The workflow entries are numbered in order

- **WHEN** the navigation column is read
- **THEN** its workflow group lists, in order, an entry for projects with no number, then numbered entries for
  importing a book, the book brief, the structure, names and style, translating, the review queue, and export

#### Scenario: The current entry follows the screen

- **WHEN** the translating screen is shown
- **THEN** the translating entry is marked as current and no other entry is
- **AND** the breadcrumb names the workflow group and the translating screen

### Requirement: Show an entry that has no working screen behind it as unavailable

WHERE a navigation entry names a capability this build does not implement, the application SHALL show that entry
in place and visibly unavailable rather than hiding it, and SHALL NOT navigate to it.

Such an entry SHALL be **inert**: activating it SHALL change neither the screen area nor which entry is marked
current, and the application SHALL NOT carry a screen, a placeholder screen or a view resource for it.

The entries covered by this are three, all in the workflow group: projects, names and style, and the review
queue.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-projects`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`.
In plain words: hiding the unfinished parts would make the application look smaller than it is going to be, and
would make the numbered steps skip. Showing them greyed says "this is coming, and here is where it will live",
which is the truth. "Inert" is spelled out because the alternative reading — build a screen that says "not
ready" — produces a view file no navigation can ever reach, which is a file with no reader and a test with
nothing to assert. Only planned product screens are listed this way: the reference rendering also draws three
specimen sheets under a heading of their own, and those are not listed at all, because a greyed entry promises a
screen that is coming and none of them ever will (see "Match the reference rendering").

#### Scenario: An unavailable entry cannot be opened

- **WHEN** the review-queue entry is activated
- **THEN** the screen area does not change and the review-queue entry is not marked current

#### Scenario: The unavailable entries are still listed

- **WHEN** the navigation column is read
- **THEN** entries for projects, names and style, and the review queue are present and marked unavailable
- **AND** exactly those three entries are marked unavailable; the settings entry and the five other workflow
  entries are not

#### Scenario: An inert entry has no screen behind it

- **WHEN** the application's view resources are read
- **THEN** there is no view resource for projects, names and style, or the review queue

### Requirement: Use the control each drawn widget stands for

WHERE the reference rendering draws a widget for which
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#control-mapping-summary` names a real control, the
screen SHALL be built from that control rather than from an approximation of it.

This obligation covers a drawn control whether it is available or unavailable: an unavailable region is built
from the same control it will be built from when it works.

**Source:** FR-UI-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#control-mapping-summary`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#controls-mapping`.
In plain words: a segmented picker drawn as three radio buttons and a toggle switch drawn as a checkbox both
"work", and both make the application read as a different program from the one that was designed. Building the
unavailable regions from the real control is the cheaper half of this: a greyed toggle switch says "this feature
is coming", and a greyed checkbox says "somebody gave up here".

#### Scenario: A quality dial is a segmented picker, not three buttons

- **WHEN** the book-brief screen's quality choice is rendered
- **THEN** it is a segmented picker carrying the three choices as one control, present and unavailable

#### Scenario: An auxiliary-text choice is a toggle switch

- **WHEN** the book-brief screen's auxiliary-text choices are rendered
- **THEN** each is a toggle switch, present and unavailable, not a checkbox

### Requirement: Match the reference rendering

For every screen the application shows, the arrangement of its parts and the roles they refer to SHALL match
`docs/specification/mockups/ui-mockup.html` for that screen, in both the light and the dark block.

The reference rendering SHALL be binding **except where a requirement in this change states a deviation from
it**. The deviations this change states are exactly five, each carrying its own reason in the requirement that
states it:

- the detected-file card omits the drawn cover image, chapter count and word count (`book-import`, "Open a book
  and report what was found");
- the export screen omits the drawn save-path field and Export button (`export`, "Report the finished file");
- the destination path and the overwrite choice appear on the book-brief screen, which the reference draws
  without them (`export`, "Decide where the translation is written before the run starts");
- the book-brief screen's quality card omits the drawn model row, because the model is chosen on the settings
  screen and this build has one place to choose it (`book-brief`, "Show the rest of the brief and make none of it
  available");
- the reference rendering's design-reference group — its component-library, dialogs-and-alerts and notifications
  specimen sheets — is not shipped, so the navigation has no third group (this requirement's sibling, "Group the
  navigation and number the workflow"), because those sheets document the design for the person building it and
  are not product screens.

Exact pixel placement is NOT part of this obligation.

**Source:** FR-UI-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), DD-28,
`docs/specification/mockups/README.md`.
In plain words: the mockup is the argument settler for how this looks, and it is checkable — which parts are
present, how they nest, and which role each one paints with. Where it is not checkable is the exact pixel, so
that is deliberately excluded. The five named deviations exist because the drawing shows behaviour the engine
cannot answer, a second place to do what one screen already does, or a sheet that was only ever a specimen; naming them here is what stops a conformance test and a behaviour requirement from both being
right and disagreeing.

#### Scenario: A screen's roles match the reference

- **WHEN** the settings screen is rendered under the light values
- **THEN** its card surfaces refer to the surface role, which resolves to `#ffffff`
- **AND** its separators refer to the border role, which resolves to `#ddd5c8`

#### Scenario: A stated deviation does not fail conformance

- **WHEN** the export screen is rendered and compared with the reference
- **THEN** the absence of the drawn save-path field and Export button does not fail this requirement, because
  `export`'s "Report the finished file" states that deviation
- **AND** every other part of the screen and its roles still match

#### Scenario: Pixel placement is not asserted

- **WHEN** a screen is rendered on a machine whose font rendering differs from the reference
- **THEN** the screen still satisfies this requirement, provided its parts and their roles match

### Requirement: Never make a person wait on the window

The application SHALL perform opening a book, checking a provider, listing a provider's models, translating and
exporting away from the thread that draws the window, and SHALL deliver every result back to the window through a
single point that is safe to call from that other work.

No call the application makes to a port SHALL be made on the thread that draws the window, except a running
job's own pause, resume and cancel requests and the registration of its progress listener, which only set a flag
or a list entry under a short lock and never wait on the job.

**Source:** FR-UI-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#state-mirror`,
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`.
In plain words: a book takes hours to translate. If any of that ran where the window is drawn, the window would
be a grey rectangle the operating system offers to force-quit. The obligation is stated as "which thread called
the port" rather than "the window kept redrawing" because the first is something a test can read off the call
itself, and the second is something no test can honestly observe.

#### Scenario: Opening a book does not call the port on the drawing thread

- **WHEN** a book is opened from the import screen
- **THEN** the thread on which the document port is called is not the thread that draws the window

#### Scenario: Checking a provider does not call the port on the drawing thread

- **WHEN** the selected provider is checked from the settings screen
- **THEN** the thread on which the verification port is called is not the thread that draws the window

#### Scenario: Every result arrives through one delivery point

- **WHEN** a run publishes progress from the thread it runs on
- **THEN** the window's properties are updated on the thread that draws the window, through the one delivery
  point, and not by the publishing thread itself

### Requirement: Report an action that has not answered yet

WHILE an action the person started is running away from the thread that draws the window and has not yet
returned, the screen SHALL report that it is in progress, and SHALL make the control that started it unavailable
until it answers.

This obligation covers opening a book, checking a provider and listing a provider's models.

WHEN the action answers — with a result or with a failure — the screen SHALL stop reporting it as in progress and
SHALL make its control available again.

**Source:** FR-UI-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#state-mirror`.
In plain words: the three actions moved off the drawing thread by the requirement above are the three that take
long enough to notice — checking a provider against a real local model is seconds, and listing models from a
server that is not answering runs to its timeout. Without this, the previous requirement's reward for doing the
work properly is a button that looks broken, and a person who presses it four more times. Making the control
unavailable is the same sentence as reporting progress because they are the same fact: the action already
started.

#### Scenario: Checking a provider reports itself while it runs

- **WHEN** the check is started on the selected provider and the verification port has not yet answered
- **THEN** the screen reports the check as in progress and the control that started it is unavailable

#### Scenario: A finished action releases its control

- **WHEN** the verification port answers with a report whose first finding is `ErrorCode.unreachable`
- **THEN** the screen stops reporting the check as in progress and the control that started it is available again
- **AND** the first finding is reported as a failure

#### Scenario: Opening a book reports itself while it runs

- **WHEN** a book is opened and the document port has not yet answered
- **THEN** the screen reports the open as in progress and neither the file chooser nor the drop target accepts a
  second book until it answers

### Requirement: Move through the workflow by its available steps

WHEN a person advances from a workflow screen, the application SHALL move to the next step that is available,
skipping any step whose navigation entry is inert.

The control that starts a run SHALL live on the translating screen, not on any earlier step.

The application SHALL NOT require a person to pass through the workflow in order to reach a screen: every
available entry SHALL remain reachable from the navigation column at any time.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`.
In plain words: the workflow is numbered one to seven and step four is inert in this build, so "continue" from
step three has to land on step five or the path stops at a wall. The reference rendering draws the start control
on step four, which does not exist here — so it moves to the screen that runs the thing, which is also the screen
that has to refuse a start with something missing. The last sentence says the numbering is a suggested order, not
a wizard: the navigation is always live, which is why the translating screen names what is missing rather than
assuming a person arrived through the front door.

#### Scenario: Continuing from the structure screen skips the inert step

- **WHEN** a person continues from the structure screen, which is step three, while step four is inert
- **THEN** the translating screen, which is step five, is shown and its navigation entry is marked current

#### Scenario: The run is started from the translating screen

- **WHEN** the workflow screens are read
- **THEN** the only control that starts a run is on the translating screen

#### Scenario: A screen is reachable without walking the workflow

- **WHEN** a book has been opened and the settings entry is activated directly from the navigation column
- **THEN** the settings screen is shown, without the brief or the structure screen having been visited
