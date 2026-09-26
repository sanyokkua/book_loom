# Spec Delta

## Purpose

The visual token system behind every screen: one catalogue of role-named colours the whole interface refers to,
a light and a dark value for each of those roles, and the rules that keep a control from ever naming a colour of
its own — so re-theming the application is a change of values, never a change of components.

## ADDED Requirements

### Requirement: Name every colour by the role it plays

The application SHALL define its colours as a single catalogue of role-named looked-up colours declared on the
scene's root, and every styled element SHALL refer to a role from that catalogue.

A role is named for the job it does — background, surface, border, text, muted text, primary action, navigation
background, navigation foreground, focus ring, and the four status roles — never for the colour it currently
holds.

The application SHALL NOT set any visual style on an individual element from code, and SHALL NOT write a colour
value anywhere except inside a value block of this catalogue.

**Source:** FR-THEME-1, FR-THEME-9 (`docs/specification/01_Product/09_THEMING.md#token-model`,
`#token-naming`), `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#theming`.
In plain words: a component says "paint me with the primary-action colour", never "paint me `#a58075`". That one
discipline is what makes the second theme a list of values rather than a second copy of every screen, and it is
also what a conformance test can actually check — you can look a role up on the root and compare it, you cannot
look up a colour somebody typed into a component.

#### Scenario: A role resolves to its catalogue value

- **WHEN** the primary-action role is looked up on the scene root while the light values are in force
- **THEN** it resolves to `#a58075`

#### Scenario: A role is named for its job, not its hue

- **WHEN** the catalogue is read
- **THEN** it contains a role named for the primary action and a role named for the app background
- **AND** it contains no role whose name is a colour name such as `cognac`, `sand`, `slate` or `charcoal`, except
  the four brand anchors that every other role is derived from

#### Scenario: No element carries a colour of its own

- **WHEN** the interface's styling is read
- **THEN** no colour value appears outside a value block of the catalogue
- **AND** no element has a style applied to it individually from code

### Requirement: Carry the whole published catalogue, not a subset of it

The catalogue SHALL carry **every** role published by
`docs/specification/01_Product/09_THEMING.md#token-catalog` and `#status-colours` — 32 roles from the first and
12 from the second, 44 in total — under the published role names.

A role that the reference rendering declares and the catalogue omits is a defect, as is a role the catalogue
invents that the reference rendering does not declare.

**Source:** FR-THEME-1, FR-THEME-2 (`docs/specification/01_Product/09_THEMING.md#token-catalog`,
`#status-colours`), `docs/specification/mockups/ui-mockup.html` (the `:root[data-theme="light"]` and
`:root[data-theme="dark"]` blocks, 44 declarations each, identical in name).
In plain words: an incomplete catalogue does not fail — it quietly forces the next screen to invent a role,
which is a hard-coded colour wearing a role name. Fixing the count here, where it can be counted, is what stops
that: the number is 44, the names are published, and a screen that needs a forty-fifth is telling you the
reference rendering changed.

#### Scenario: The catalogue declares every published role

- **WHEN** the catalogue's role names are compared with the 44 declarations of the reference rendering's light
  block
- **THEN** the two sets are identical, with no role in only one

#### Scenario: The catalogue count is exact

- **WHEN** the catalogue's roles are counted
- **THEN** there are exactly 44, of which 12 are the status roles

### Requirement: Carry a light and a dark value for every role

The application SHALL supply exactly two value blocks for the one catalogue — light and dark — and every role
SHALL have a value in both. Switching the theme SHALL swap the block in force and change nothing else: no role is
added, removed or renamed, and no element is restyled.

**Source:** FR-THEME-2, FR-THEME-6, FR-THEME-7, FR-THEME-8
(`docs/specification/01_Product/09_THEMING.md#token-catalog`, `#light-and-dark`).
In plain words: the two themes are the same interface with different numbers. A role that exists in one block and
not the other is the defect this requirement exists to prevent, because it shows up as an invisible control in
exactly one theme and nowhere in any test that only ever renders the other one.

#### Scenario: Every role is defined in both blocks

- **WHEN** the light block and the dark block are compared
- **THEN** they define the same set of role names, with no role present in only one

#### Scenario: The app background differs between the blocks

- **WHEN** the app-background role is looked up under the light values
- **THEN** it resolves to `#f4f1ea`
- **WHEN** the same role is looked up under the dark values
- **THEN** it resolves to `#283237`

#### Scenario: Switching the theme restyles nothing

- **WHEN** the theme is switched from light to dark
- **THEN** every element keeps the same role references it had before
- **AND** only the resolved values change

### Requirement: Express the three elevation roles as an effect rather than a colour

The three elevation roles — `shadow-sm`, `shadow` and `shadow-lg` — SHALL be expressed as a drop-shadow effect
declared on a named style class, carrying the published colour, blur radius and vertical offset of that role for
the block in force.

These three roles SHALL NOT be declared as looked-up colours, and the obligation that every role resolves as a
colour on the scene's root SHALL NOT apply to them.

Each of the three SHALL carry a light and a dark value, exactly as every other role does.

**Source:** FR-THEME-1, FR-THEME-2 (`docs/specification/01_Product/09_THEMING.md#token-catalog`), design
decision D10.
In plain words: the published catalogue writes these three as `0 3px 10px rgba(58,74,82,.12)` — an offset, a
blur and a colour — which is a web shadow, not a colour. A looked-up colour cannot hold it: declared as one and
referenced from a colour property, the value is silently dropped and the element renders with nothing at all,
which is worse than an error because nothing reports it. The interface therefore names a style class per
elevation and puts a real drop-shadow effect on it, and the "every role resolves" check is stated to skip these
three rather than being quietly wrong about them.

#### Scenario: An elevation role applies a real shadow

- **WHEN** an element carrying the default-elevation style class is rendered under the light values
- **THEN** it carries a drop-shadow effect whose colour is `rgba(58,74,82,.12)`, whose blur radius is `10` and
  whose vertical offset is `3`

#### Scenario: The elevation roles change with the block

- **WHEN** the same element is rendered under the dark values
- **THEN** its drop-shadow effect carries the dark block's own colour, blur radius and offset rather than the
  light block's

#### Scenario: The elevation roles are exempt from the colour check

- **WHEN** every catalogued role is looked up as a colour on the scene's root
- **THEN** the 41 colour roles all resolve
- **AND** `shadow-sm`, `shadow` and `shadow-lg` are not looked up as colours at all

### Requirement: Carry the four status roles in three shades each

The application SHALL provide a success, a warning, a danger and an information role, and each SHALL carry three
values — a foreground, a soft background and a border — so a status is expressed the same way wherever it
appears.

**Source:** FR-THEME-10 (`docs/specification/01_Product/09_THEMING.md#status-colours`),
`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#toasts`.
In plain words: "this went wrong" has to look the same on a chip, a banner and a toast, or a person learns the
colour three times. Three shades per status is what lets a filled badge and an outlined banner both read as the
same severity.

#### Scenario: The danger role carries its three shades

- **WHEN** the danger foreground, background and border are looked up under the light values
- **THEN** they resolve to `#b0574c`, `#f7e4df` and `#e4b6ad`

#### Scenario: A status reads the same in both themes

- **WHEN** the success foreground is looked up under the light values
- **THEN** it resolves to `#5f8a6b`
- **WHEN** it is looked up under the dark values
- **THEN** it resolves to `#7faa8a`, a distinct value rather than the light one reused

### Requirement: Follow the operating system's colour scheme on first display

WHEN the window is first shown, the application SHALL apply the value block matching the operating system's
current colour scheme.

IF the operating system reports no preference, THEN the application SHALL apply the light block.

**Source:** FR-THEME-3 (`docs/specification/01_Product/09_THEMING.md#token-model`), FR-SETTINGS-04
(`docs/specification/01_Product/07_SETTINGS.md#appearance-tab`).
In plain words: somebody running a dark desktop should not be flashed a white window on launch. Light is the
fallback because it is the theme the mockup is drawn in, so an unknown preference lands on the reference
rendering rather than on a guess.

#### Scenario: A dark desktop opens a dark window

- **WHEN** the operating system reports a dark colour scheme and the window is first shown
- **THEN** the dark block is in force and the app background resolves to `#283237`

#### Scenario: No stated preference falls back to light

- **WHEN** the operating system reports no colour-scheme preference and the window is first shown
- **THEN** the light block is in force and the app background resolves to `#f4f1ea`

### Requirement: Let a person change the theme in two places

The application SHALL offer a two-state control in the window's title bar that moves directly between light and
dark, and a three-way choice in the appearance settings between light, dark and following the operating system.

WHEN either control is used, the application SHALL apply the new block immediately, without a restart and
without losing what is on screen.

The chosen theme SHALL apply for the current session only; the next launch SHALL again follow the operating
system.

**Source:** FR-SETTINGS-04 (`docs/specification/01_Product/07_SETTINGS.md#appearance-tab`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`.
In plain words: the title-bar control is the one you reach for when the room's light changes; the settings choice
is where you say "just follow my desktop". Nothing is remembered yet because nothing is stored yet — that arrives
with local storage, and saying so here is more honest than implying a preference survives a restart.

#### Scenario: The title-bar control flips the theme

- **WHEN** the light block is in force and the title-bar theme control is activated
- **THEN** the dark block is in force and the app background resolves to `#283237`

#### Scenario: The settings choice follows the desktop

- **WHEN** the appearance setting is set to follow the operating system, and the operating system reports light
- **THEN** the light block is in force

#### Scenario: A theme choice does not survive a restart

- **WHEN** the theme is set to dark, the application is closed, and it is launched again on a machine whose
  operating system reports light
- **THEN** the light block is in force
