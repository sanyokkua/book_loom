# Spec Delta

## ADDED Requirements

### Requirement: Show the run in the title bar while one exists

WHILE no run exists in the session, the title bar SHALL show only the product name, the theme control and the about
action.

WHILE a run exists in the session, the title bar SHALL show, ahead of the theme control and in this order: the file name
of the book being translated; a state text — "Progress 78%" while running, "Paused at 78%" while paused, "Stopped at
78%" while stopped, "Provider error" while paused on an error (every pause on error, a provider's `validation`
refusal included), "Finished" once every segment is decided, "Failed at 78%" once the run has ended in failure; the
elapsed time and, when the run reports one, the time left; and one run control.

The run control SHALL be Pause while the run is running, and Resume while it is paused, stopped or paused by a provider
failure; the title bar SHALL never show Pause and Resume together, and SHALL show no run control once the run has
finished or failed. WHILE the run is pausing or stopping, the run control SHALL be shown unavailable.

WHEN the title bar's run control is used, the application SHALL do exactly what the translating screen's Pause or Resume
does, and both SHALL then show the same state.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, the reference rendering's title-bar
Resume button.
In plain words: a book takes hours, and a person spends most of that time on other screens — settings, the brief, the
export screen. The title bar keeps the run in sight from anywhere and lets it be paused or resumed without going back to
the translating screen. It is empty when there is nothing to show, so a first launch is not cluttered with a dead
control, and the single button acts through the same path as the screen's so the two can never disagree.

#### Scenario: No run, no run status

- **WHEN** the application has just been launched and no run has been started
- **THEN** the title bar shows `BookLoom`, the theme control and the about action, and nothing else

#### Scenario: A running run is shown with Pause

- **WHEN** the run over `Frankenstein.epub` is running at 78% after 1h 02m with about 1h 20m left
- **THEN** the title bar shows `Frankenstein.epub`, `Progress 78%`, `1h 02m elapsed`, `~1h 20m left` and Pause, ahead of
  the theme control
- **AND** Resume is not shown

#### Scenario: A paused run is shown with Resume

- **WHEN** the run over `Frankenstein.epub` is paused at 78%
- **THEN** the title bar shows `Paused at 78%` and Resume, and Pause is not shown

#### Scenario: A provider failure is named in the title bar

- **WHEN** the run over `Frankenstein.epub` is paused because `http://localhost:11434` stopped answering
- **THEN** the title bar shows `Provider error` and Resume

#### Scenario: A finished run offers no run control

- **WHEN** every segment of `Frankenstein.epub` has been decided
- **THEN** the title bar shows `Finished` and neither Pause nor Resume

#### Scenario: A failed run offers no run control

- **WHEN** the run over `Frankenstein.epub` ends in failure at 78%
- **THEN** the title bar shows `Failed at 78%` and neither Pause nor Resume

#### Scenario: Pausing from another screen

- **WHEN** the settings screen is shown, the run is running at 42%, and the title bar's Pause is used
- **THEN** the run pauses at 42% and the title bar shows `Paused at 42%` and Resume
- **AND** the translating screen, when opened, shows the run paused

#### Scenario: The control waits for a pause to take effect

- **WHEN** Pause has been used and the request in flight has not yet been abandoned
- **THEN** the title bar's run control is shown unavailable

### Requirement: Search any fixed list by typing

WHERE a choice box offers a fixed list of values — a language, a genre, or any other predefined list — the application
SHALL narrow the entries shown to those whose displayed name contains the typed text anywhere, ignoring letter case and
accents.

WHERE the choice also accepts free text, as genre does, the application SHALL keep the typed text as the value when the
person leaves the box without picking an entry.

WHERE the choice accepts only listed values, as a language does, IF the person leaves the box without picking an entry,
THEN the application SHALL keep the value the box held before typing began.

**Source:** FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-UI-03
(`#fr-ui`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#control-mapping-summary`.
In plain words: the language list has 34 entries and the genre list about 40, and scrolling through them is slow; typing
three letters is not. Matching anywhere and ignoring accents means `bokmal` finds Norwegian Bokmål and `fran` finds
French without the person guessing how a name begins. Genre is the one list that also takes something the list does not
hold, so a typed genre is kept; a language cannot be invented, so an unfinished search leaves the previous one in place.

#### Scenario: Typing narrows the languages

- **WHEN** `ukr` is typed into the target-language box
- **THEN** the entries shown are narrowed to `Ukrainian`

#### Scenario: A match anywhere in the name

- **WHEN** `fran` is typed into the target-language box
- **THEN** `French` is among the entries shown

#### Scenario: Accents and case are ignored

- **WHEN** `BOKMAL` is typed into the target-language box
- **THEN** `Norwegian Bokmål` is among the entries shown

#### Scenario: A typed genre is kept

- **WHEN** `solarpunk novella` is typed into the genre box and the box is left without picking an entry
- **THEN** the genre is `solarpunk novella`

#### Scenario: An unfinished language search changes nothing

- **WHEN** the target language is `Ukrainian`, `xyz` is typed into its box, and the box is left without picking an entry
- **THEN** the target language is still `Ukrainian`

### Requirement: Close each workflow step with a Back and a forward action

Each workflow screen SHALL end with a footer holding a backward action on the left and a forward action on the right:

- the import screen: Cancel, which returns the screen to its empty drop area, and "Continue to Book Brief", available
  once a book is open — except that a refused book's footer holds only "Choose another file";
- the book brief: Back to the import screen, and Continue to the structure screen;
- the structure screen: Back to the book brief, and Continue to names and style;
- names and style: Back to the structure screen, and "Start translation";
- the translating screen: Back to names and style, and Next to the export screen;
- the export screen: Back to the translating screen, and Next shown unavailable, because export is the last step.

**Source:** FR-UI-01, FR-UI-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`, the reference rendering's step
actions.
In plain words: the same two buttons in the same two places on every step make the numbered workflow feel like one path
— back is always bottom left, onward always bottom right. Cancel on the import screen clears the file just inspected
rather than leaving the application, because the projects screen it leads to in the reference is not built.

#### Scenario: The brief's footer

- **WHEN** the book brief is shown
- **THEN** its footer holds Back on the left and Continue on the right
- **AND** Continue shows the structure screen

#### Scenario: Cancel clears the import screen

- **WHEN** `Frankenstein.epub` has been inspected on the import screen and Cancel is used
- **THEN** the import screen shows its empty drop area and no detected book

#### Scenario: A refused book offers only another file

- **WHEN** `Purchased_Novel.epub` is refused as DRM-protected on the import screen
- **THEN** its footer holds `Choose another file` and no `Continue to Book Brief`

#### Scenario: The last step cannot go forward

- **WHEN** the export screen is shown
- **THEN** its footer holds Back, which shows the translating screen, and Next, which is unavailable

### Requirement: Show the chosen provider and model in the navigation footer

The navigation column SHALL end with a footer line naming the currently selected provider's display name and the
chosen model identifier, separated by " · ", read from the current selection on the settings screen.

WHERE no model is chosen, the footer SHALL read "No model chosen" in place of a provider and model name.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), FR-MODEL-01
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`, the reference rendering's sidebar
footer.
In plain words: the navigation column is visible from every screen, so putting the active provider and model there
means a person never has to open Settings just to check what a run would actually use. Ollama and LM Studio are the
only two providers this build offers, so the name is one of those two; the model id is whatever was chosen from the
list or typed by hand on the providers area. Saying plainly that nothing is chosen, rather than leaving the line
blank, is what tells a person a run cannot start yet.

#### Scenario: The footer names the chosen provider and model

- **WHEN** Ollama is selected and `gemma4:26b` is the chosen model
- **THEN** the navigation footer reads `Ollama · gemma4:26b`

#### Scenario: No model chosen shows the empty state

- **WHEN** a provider is selected and no model has been chosen
- **THEN** the navigation footer reads `No model chosen`

#### Scenario: Choosing a model updates the footer

- **WHEN** the footer reads `No model chosen` and `qwen3:8b` is then chosen on the providers area
- **THEN** the footer reads `Ollama · qwen3:8b`

## MODIFIED Requirements

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

- **WHEN** the translating screen is shown in a content area 944 pixels wide with 768 segments auto-accepted, 41
  repaired, 3 flagged and 428 remaining
- **THEN** each of the four tiles is at least 120 pixels wide
- **AND** the captions `auto-accepted`, `repaired`, `flagged` and `remaining` and the numbers `768`, `41`, `3` and
  `428` are shown without an ellipsis

#### Scenario: The progress bar is never squeezed away

- **WHEN** the translating screen is shown in a window shorter than the screen's own height, at 42% progress
- **THEN** the progress bar is 9 pixels high and its filled part has a positive width

### Requirement: Group the navigation and number the workflow

The navigation column SHALL present its entries in two named groups — the workflow and the application — with each
group heading shown in capital letters, and SHALL number the workflow entries in the order they are worked through.

The workflow group SHALL list an entry for projects with no number, then six numbered entries: importing a book, the
book brief, the structure, names and style, translating, and export. It SHALL list no review entry, because review
happens inside the translating screen.

The navigation SHALL mark each workflow step the person has completed with a done mark: importing once a book is open;
the book brief and the structure once the person has continued from them; names and style once a run has started;
translating once the run has finished; export once a book has been written.

The navigation SHALL mark exactly one entry as the current one, and that mark SHALL follow the screen being
shown, however the person arrived at it.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`.
In plain words: the numbers tell a first-time user that this is a sequence with an end, not a pile of unrelated
screens, and the done marks tell a returning one how far they got. One highlighted entry, always agreeing with what is
on screen, is what stops the navigation from lying after a jump. There are two groups and not the reference rendering's
three because the third group there lists the mockup's specimen sheets, which are drawings for the designer and not
screens a person can use. The reference also numbers a separate review queue; review now lives beside the run it
reviews, so that step is gone and export becomes step six.

#### Scenario: The navigation has exactly two groups

- **WHEN** the navigation column is read
- **THEN** it carries exactly two group headings, shown as `WORKFLOW` and `APPLICATION`, in that order
- **AND** the application group lists the settings entry alone
- **AND** no entry is labelled `Component library`, `Dialogs & alerts` or `Notifications`

#### Scenario: The workflow entries are numbered in order

- **WHEN** the navigation column is read
- **THEN** its workflow group lists, in order, an entry for projects with no number, then entries numbered 1 to 6 for
  importing a book, the book brief, the structure, names and style, translating, and export
- **AND** no entry names a review queue

#### Scenario: Completed steps carry a done mark

- **WHEN** `Frankenstein.epub` is open, the person has continued from the book brief and the structure, and a run has
  started
- **THEN** importing, the book brief, the structure and names and style carry a done mark
- **AND** translating and export do not

#### Scenario: The current entry follows the screen

- **WHEN** the translating screen is shown
- **THEN** the translating entry is marked as current and no other entry is
- **AND** the breadcrumb names the workflow group and the translating screen

### Requirement: Show an entry that has no working screen behind it as unavailable

WHERE a navigation entry names a capability this build does not implement, the application SHALL show that entry
in place and visibly unavailable rather than hiding it, and SHALL NOT navigate to it.

Such an entry SHALL be **inert**: activating it SHALL change neither the screen area nor which entry is marked
current, and the application SHALL NOT carry a screen, a placeholder screen or a view resource for it.

The entry covered by this is one: projects, in the workflow group.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-projects`.
In plain words: hiding the unfinished part would make the application look smaller than it is going to be. Showing it
greyed says "this is coming, and here is where it will live", which is the truth. "Inert" is spelled out because the
alternative reading — build a screen that says "not ready" — produces a view file no navigation can ever reach, which is
a file with no reader and a test with nothing to assert. Names and style now has a working screen, and the review queue
entry no longer exists because review happens inside the translating screen, so projects is the only inert entry left.
Only planned product screens are listed this way: the reference rendering also draws three specimen sheets under a
heading of their own, and those are not listed at all, because a greyed entry promises a screen that is coming and none
of them ever will (see "Match the reference rendering").

#### Scenario: An unavailable entry cannot be opened

- **WHEN** the projects entry is activated while the structure screen is shown
- **THEN** the screen area still shows the structure screen and the projects entry is not marked current

#### Scenario: The unavailable entries are still listed

- **WHEN** the navigation column is read
- **THEN** the projects entry is present and marked unavailable
- **AND** it is the only entry marked unavailable; the settings entry and the six numbered workflow entries are not

#### Scenario: An inert entry has no screen behind it

- **WHEN** the application's view resources are read
- **THEN** there is no view resource for projects
- **AND** there is a working screen for names and style

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
- **THEN** it is a segmented picker carrying the three choices as one control

#### Scenario: An auxiliary-text choice is a toggle switch

- **WHEN** the book-brief screen's auxiliary-text choices are rendered
- **THEN** each is a toggle switch, not a checkbox

#### Scenario: An unavailable settings area uses its real control

- **WHEN** the settings screen's generation area, which this build does not implement, is rendered
- **THEN** it is a tab of the same tab control as the providers area, shown unavailable

### Requirement: Match the reference rendering

For every screen the application shows, the arrangement of its parts and the roles they refer to SHALL match
`docs/specification/mockups/ui-mockup.html` for that screen, in both the light and the dark block.

The reference rendering SHALL be binding **except where a requirement states a deviation from it**, and every deviation
SHALL be stated by a requirement that gives its reason. The deviations stated are:

- the reference rendering's design-reference group — its component-library, dialogs-and-alerts and notifications
  specimen sheets — is not shipped, so the navigation has no third group ("Group the navigation and number the
  workflow"), because those sheets document the design for the person building it and are not product screens;
- the "Preview state" switchers the reference draws on its screens, which flip a screen between its drawn states for the
  reader of the mockup, are not part of the application, because a screen's state comes from what is actually happening;
- the reference's separate review-queue screen and its numbered navigation entry are not shipped: review happens inside
  the translating screen instead ("Group the navigation and number the workflow", and `review-queue`), an
  owner-approved departure from the mockup;
- the export screen's drawn "EPUBCheck passed" line is replaced by the check the application actually runs, re-opening
  the written book (`export`, "Report the finished file");
- the reference's toolbar `Pause` and `Review (3)` buttons are not drawn on a toolbar: the run control lives in the
  title bar ("Show the run in the title bar while one exists") and review lives on the translating screen itself,
  not on a separate queue;
- the title bar's Resume is not always shown: it appears in the run status described above only while a run exists,
  not as a permanently drawn button;
- the reference's "Back to projects" link is not shown, because projects is not built;
- the sidebar's readiness dot and the book brief's `ready` model badge are not shown, because both need a kept
  verification state the application does not keep between checks;
- the import card's `valid` badge is not shown, because a book is checked by being opened, and nothing further
  validates it;
- the import screen's "Source language override" dropdown is not shown, because the source language is instead
  chosen on the book brief (`book-brief`, "Choose the source and target languages", ADR-0037);
- the `primes system prompt` note and the `Slower · Max-quality` note are not shown, because they are notes for the
  mockup's own reader; the consistency pass this build runs happens on any quality dial, started from Export;
- the structure tree's per-node formatting pills are not shown, because formatting is reported once for the whole
  book, in its statistics card, not per node;
- a transient message never carries the name of the requirement it demonstrates, unlike the reference's toast
  specimens, which are labelled that way for the person reading the mockup.

Exact pixel placement is NOT part of this obligation.

**Source:** FR-UI-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), DD-28,
`docs/specification/mockups/README.md`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`, ADR-0037
(`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`).
In plain words: the mockup is the argument settler for how this looks, and it is checkable — which parts are
present, how they nest, and which role each one paints with. Where it is not checkable is the exact pixel, so
that is deliberately excluded. Most of these deviations exist because this build tracks less state than the mockup
assumes — no kept verification, no per-node formatting report, no Projects — or because one drawn control does the
job of two (the title bar's run control instead of a toolbar, the brief's language field instead of an import-screen
override); one, the folded review step, is a deliberate departure the owner approved rather than a limit of this
build. Naming them here is what stops a conformance test and a behaviour requirement from both being right and
disagreeing.

#### Scenario: A screen's roles match the reference

- **WHEN** the settings screen is rendered under the light values
- **THEN** its card surfaces refer to the surface role, which resolves to `#ffffff`
- **AND** its separators refer to the border role, which resolves to `#ddd5c8`

#### Scenario: The export screen now carries what the reference draws

- **WHEN** the export screen is rendered for an open book and compared with the reference
- **THEN** it carries the Save to field, the Browse action and the Export book action the reference draws

#### Scenario: A stated deviation does not fail conformance

- **WHEN** the import screen is rendered and compared with the reference
- **THEN** the absence of the drawn `Preview state` switcher does not fail this requirement, because this requirement
  states that deviation
- **AND** every other part of the screen and its roles still match

#### Scenario: Pixel placement is not asserted

- **WHEN** a screen is rendered on a machine whose font rendering differs from the reference
- **THEN** the screen still satisfies this requirement, provided its parts and their roles match

### Requirement: Move through the workflow by its available steps

WHEN a person advances from a workflow screen, the application SHALL move to the next step that is available,
skipping any step whose navigation entry is inert.

WHEN "Start translation" is used on names and style while a start is offered, the application SHALL start a run and
show the translating screen; IF something the run needs is missing, THEN the application SHALL show the translating
screen naming what is missing and SHALL start nothing.

**Starting** a run SHALL mean beginning a new run at the project's first pending segment. A start SHALL be offered only
when the project has no run, when its last run COMPLETED and pending segments remain — for example after an "Also
translate" switch was turned on — or when its last run FAILED. WHILE the project's run is running, paused, stopped or in
the provider-error state, and after a COMPLETED run that left nothing pending, the application SHALL offer no start.
The only controls that start a run SHALL be names and style's "Start translation" and the translating screen's own
start control.

**Resuming** a run SHALL mean continuing a run that already exists for the book, through the Resume control on the
translating screen or on the title bar: WHILE that run is paused, or paused by a provider error, Resume SHALL continue
it in place; WHILE it is stopped, Resume SHALL begin a new run at the first pending segment, keeping every segment
already decided by the earlier run. Resume is not counted among the controls that start a run, even where it begins one
at the first pending segment: it is offered only while the book's run is paused, stopped or in the provider-error state,
and it is never labelled "Start". WHERE the title bar shows a run control, that control SHALL do exactly what the
translating screen's control of the same name does, so Start and Resume never mean something different depending on
where they are used.

The application SHALL NOT require a person to pass through the workflow in order to reach a screen: every
available entry SHALL remain reachable from the navigation column at any time.

**Source:** FR-UI-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, and translation-pipeline's "Start a
run and report its progress".
In plain words: names and style now has a screen, so continuing from the structure lands there, and its Start
translation — where the reference draws it — starts the run and takes the person to watch it. The translating screen
keeps its own start for a person who arrived by the navigation, and it is also where a start with something missing is
explained. Start and Resume read as the same idea from the workflow's point of view — both put the person in front of a
running translation — but they are different actions: Start is offered only when there is nothing to resume — no run
yet, a run that completed while segments are still pending, or a failed run; Resume always continues something, even
when what it continues was stopped and the continuation happens to be a new run underneath. That is why Resume is never
one of the "controls that start a run" even on a stopped book, and why the title bar's button is never a third start
control — it is the same Resume the screen offers, doing the same thing. While a run can be resumed, names and style's
"Start translation" starts nothing and only takes the person to the translating screen (glossary's "Show the glossary as
an editable table on Names & style"), so a second run never begins beside one that is waiting to continue. What each of
the seven states the translating screen can be in shows and offers — including what Resume and the screen's own start
control look like in each one — is stated by translation-pipeline's "Start a run and report its progress" (running, paused, stopped, provider error) and "Show the Translating screen's idle, completed and failed states"; this
requirement only fixes which controls may start a run and what starting and resuming each mean, not what the screen
draws. The last sentence about reachability says the numbering is a suggested order, not a wizard: the navigation is
always live.

#### Scenario: Continuing from the structure screen skips the inert step

- **WHEN** a person continues from the structure screen, which is step three
- **THEN** names and style, which is step four and no longer inert, is shown and its navigation entry is marked current
- **AND** no step is skipped, because the only inert entry, projects, comes before step one

#### Scenario: Start translation starts the run

- **WHEN** `Frankenstein.epub` is open, the model `gemma3:12b` is chosen, and "Start translation" is used on names and
  style
- **THEN** a run starts and the translating screen is shown with the run running

#### Scenario: The run is started from the translating screen

- **WHEN** the translating screen is opened from the navigation with `Frankenstein.epub` open and `gemma3:12b` chosen,
  and its start control is used
- **THEN** a run starts
- **AND** the workflow screens carry no other control that starts a run besides names and style's `Start translation`

#### Scenario: Start translation with no model starts nothing

- **WHEN** `Frankenstein.epub` is open, no model is chosen, and "Start translation" is used on names and style
- **THEN** the translating screen is shown naming the missing model, and no run exists

#### Scenario: Resuming a paused run continues it in place

- **WHEN** the run over `Frankenstein.epub` is paused at 55% and Resume is used, from the title bar or the
  translating screen
- **THEN** the same run continues from chunk 41/66, and no new run begins

#### Scenario: Resuming a stopped run begins a new one at the first pending segment

- **WHEN** the run over `Frankenstein.epub` was stopped with `ch12 · p03` as the first pending segment, and Resume is
  used
- **THEN** a new run begins at `ch12 · p03`
- **AND** every segment the earlier run had already decided stays decided

#### Scenario: A screen is reachable without walking the workflow

- **WHEN** a book has been opened and the settings entry is activated directly from the navigation column
- **THEN** the settings screen is shown, without the brief or the structure screen having been visited
