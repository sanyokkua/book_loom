# book-brief Specification

## Purpose
The instruction a person gives before a run: which language the book is translated into, what the book says its
own language is, and — visible but not yet answerable — the tone, the policies, the balance and the quality dial
that the engine will one day read.

## Requirements

### Requirement: Choose the target language and show the source the book declares

The book-brief screen SHALL let a person choose the target language, and SHALL show the language the book
declares as the source without letting it be edited.

WHERE the book declares no language, the screen SHALL show the source as undeclared rather than guessing one,
and SHALL still allow a target language to be chosen.

**Source:** FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`.
In plain words: the target is the one thing a run genuinely cannot start without, so it is the one control on
this screen that does anything. The source is shown and locked because the book already answered that question
and the application has no better answer — detection is not built, so an editable field would invite a person to
overrule a fact with a guess.

#### Scenario: The source language is shown but not editable

- **WHEN** a book declaring `en` is open
- **THEN** the source language shows `en` and cannot be changed

#### Scenario: A book that declares nothing still gets a target

- **WHEN** a plain-text book declaring no language is open and `uk` is chosen as the target
- **THEN** the source language is shown as undeclared and the target language is `uk`

### Requirement: Say so when no book is open

WHILE no book is open, the book-brief screen SHALL report that a book has not been opened yet and SHALL offer a
route to the import screen, and SHALL NOT present an empty brief.

The structure screen SHALL report the same state on the same condition.

**Source:** FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-DOC-01
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`.
In plain words: the navigation is always live, so a person can open the brief or the structure before opening any
book — and both screens are built entirely out of one. An empty brief with a greyed-out language picker looks
broken; saying "open a book first" and offering the way there is the same number of controls and tells the truth.
The export screen already has this state; these two are the other screens that need a book and lacked it.

#### Scenario: The brief with no book open

- **WHEN** the book-brief screen is opened and no book has been opened
- **THEN** it reports that no book is open and offers a route to the import screen
- **AND** no target language, destination or overwrite control is presented

#### Scenario: The structure screen with no book open

- **WHEN** the structure screen is opened and no book has been opened
- **THEN** it reports that no book is open and offers a route to the import screen

#### Scenario: Opening a book leaves the state

- **WHEN** a book is opened and the book-brief screen is shown again
- **THEN** the brief is presented and the no-book-open report is gone

### Requirement: Show the rest of the brief and make none of it available

Every other choice the book-brief screen displays — tone, register, narrative voice and audience, the name and
foreign-passage policies, the footnote and unit policies, the faithful-to-natural balance, the quality dial, and
the switches for auxiliary text — SHALL be shown and unavailable.

The application SHALL NOT read any of those choices when assembling a run.

The quality card SHALL NOT repeat the model row the reference draws beneath the dial: the model is chosen on the
settings screen, and a second, read-only copy of that choice on the brief would go stale the moment it was changed
there.

**Source:** FR-BRIEF-02, FR-BRIEF-03, FR-BRIEF-04, FR-BRIEF-05, FR-BRIEF-06, FR-BRIEF-07, FR-BRIEF-08
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`.
In plain words: the engine reads a source language, a target language and a destination, and nothing else on
this screen — so every other control is drawn and switched off. Showing them is how a person learns what the
brief is going to be; enabling one that nothing reads would be a control that silently does nothing, which is
the worst of the three options.

#### Scenario: The rest of the brief is visible and unavailable

- **WHEN** the book-brief screen is shown
- **THEN** the tone, policy, balance, quality and auxiliary-text controls are present and unavailable

#### Scenario: An unavailable choice reaches no run

- **WHEN** a run is started from a brief whose quality dial is drawn at its middle position
- **THEN** the instruction the run receives carries a source language, a target language and a destination, and
  nothing derived from the quality dial
