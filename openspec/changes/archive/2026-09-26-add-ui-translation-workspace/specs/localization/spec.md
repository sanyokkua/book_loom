# Spec Delta

## Purpose

The interface in two languages: every word a person reads comes from a translatable catalogue rather than being
written into a screen, sentences that count things get the grammar of the language they are read in, and the
language is chosen from the operating system without anybody being asked on first launch.

## ADDED Requirements

### Requirement: Draw every visible word from a catalogue

Every piece of text the application displays — labels, headings, button text, placeholder text, tooltips, log
entries, dialog titles and message bodies — SHALL come from a message catalogue addressed by a key.

No visible text SHALL be written directly into a screen, and no visible text SHALL be built by joining a
translated fragment to another fragment.

Names a person or a file supplies — a file path, a book title, a model identifier, a language code — are data
rather than text and SHALL be substituted into a catalogue message rather than concatenated with one.

A failure's own title, message and safe details SHALL be treated the same way: they are **data a port supplies**,
substituted into a catalogue-owned frame, not text the interface wrote. This build therefore displays them in the
language the port that built them wrote them in, which is English, and SHALL NOT attempt to translate them in the
window.

**Source:** FR-UI-06, FR-UI-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), FR-I18N-1,
FR-I18N-9 (`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`).
In plain words: a sentence assembled from pieces at runtime cannot be translated, because other languages put the
pieces in a different order and inflect them differently. One key, one whole sentence, with the variable parts
marked inside it, is the only shape a translator can actually work with. A failure's text is carved out because
of where it is built: `AppError` titles and messages are written in the modules that detect the failure, and
those modules are forbidden from reaching a resource bundle — they are the framework-free core. So the honest
options were to display them as they are, or to build a second error catalogue in the window keyed by error code,
which is a translation table that goes stale the first time a message is reworded. Displaying them as data is the
first option, named rather than discovered: a Ukrainian window will show a Ukrainian frame around an English
sentence until the error catalogue itself is translated, which is a change to three service modules.

#### Scenario: A screen's text is addressable by key

- **WHEN** the import screen is displayed
- **THEN** every visible string on it resolves from a catalogue key

#### Scenario: A file name is substituted, not joined

- **WHEN** the book `Frankenstein.epub` is opened and its name is reported
- **THEN** the message comes from one key carrying a placeholder for the name, not from a translated prefix
  joined to the name

#### Scenario: A failure's own words pass through as data

- **WHEN** an `AppError` titled `This book is protected` is surfaced while the interface is in Ukrainian
- **THEN** the dialog's own labels, its expander and its buttons resolve from Ukrainian catalogue keys
- **AND** the title `This book is protected` is shown as the port wrote it, not looked up in the catalogue

### Requirement: Ship English and Ukrainian, complete against each other

The application SHALL ship a catalogue in English and a catalogue in Ukrainian. English SHALL be the fallback.

Both catalogues SHALL define exactly the same set of keys: a key present in one and absent from the other is a
defect, as is a key defined in a catalogue that nothing displays.

**Source:** FR-UI-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), FR-I18N-2, FR-I18N-9
(`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`).
In plain words: a missing key does not fail loudly — it shows the raw key or an English word in the middle of a
Ukrainian sentence, and nobody notices until a user does. Checking both directions catches both the untranslated
string and the translation nobody uses any more.

#### Scenario: The two catalogues hold the same keys

- **WHEN** the English and Ukrainian catalogues are compared
- **THEN** they define the same set of keys, with none present in only one

#### Scenario: Every displayed key exists, and every key is displayed

- **WHEN** the keys the application addresses are compared with the keys the catalogues define
- **THEN** the two sets are identical

### Requirement: Give a counted sentence the grammar of its own language

A message whose wording depends on a number SHALL be expressed as a pattern that selects its form from that
number, in the language being displayed.

The Ukrainian catalogue SHALL provide every plural form Ukrainian distinguishes for such a message.

**Source:** FR-UI-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), FR-I18N-8, DD-48
(`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#icu-messages`).
In plain words: English has two forms and Ukrainian has four, so "1 segment / 2 segments" translated as a pair
is wrong for most numbers in Ukrainian. Naming the forms in the catalogue is what makes `21 сегмент`,
`23 сегменти` and `25 сегментів` all come out right from the same key.

#### Scenario: English selects between two forms

- **WHEN** the remaining-segments message is rendered in English with the number `1`
- **THEN** it reads `1 segment remaining`
- **WHEN** it is rendered with the number `469`
- **THEN** it reads `469 segments remaining`

#### Scenario: Ukrainian selects among its own forms

- **WHEN** the remaining-segments message is rendered in Ukrainian with the numbers `1`, `3` and `5`
- **THEN** three grammatically distinct forms are produced

#### Scenario: Every counted message carries every Ukrainian form

- **WHEN** the Ukrainian catalogue's number-dependent patterns are read
- **THEN** each one defines the one, few, many and other forms

### Requirement: Choose the language from the operating system on first launch

WHEN the application starts, it SHALL display Ukrainian IF the operating system's language is Ukrainian, and
English otherwise.

The application SHALL NOT ask which language to use on first launch.

The operating system SHALL be the only thing that chooses the displayed language in this build: the application
SHALL offer no control that changes it, and SHALL NOT record a chosen language anywhere.

**Source:** FR-UI-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), FR-I18N-4
(`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`).
In plain words: guessing correctly from the desktop is right almost always and costs the person nothing, whereas
a language question before the first screen is a dialog in a language they may not read. FR-UI-06 also asks for
an in-app switch in Settings -> Appearance, and this build does not have one: a switch is worth little until the
answer survives a restart, and nothing is stored yet. Both bundles ship complete, so adding the switch later is a
control and a stored value, not a re-translation.

#### Scenario: A Ukrainian desktop gets a Ukrainian interface

- **WHEN** the operating system's language is Ukrainian and the application starts
- **THEN** the interface is displayed in Ukrainian

#### Scenario: Any other desktop gets English

- **WHEN** the operating system's language is German and the application starts
- **THEN** the interface is displayed in English

#### Scenario: No control changes the language

- **WHEN** every settings area is read
- **THEN** no control offers to change the displayed language

#### Scenario: No language question appears

- **WHEN** the application starts for the first time on a machine with no stored preferences
- **THEN** no dialog asks which language to use

### Requirement: Format numbers and times for the language being displayed

A number, a count or a duration the application displays SHALL be formatted for the language currently being
displayed, not for the machine's own region.

**Source:** FR-I18N-6 (`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#locale-formatted-fields`).
In plain words: an interface reading Ukrainian with American thousands separators is half-translated. Following
the displayed language rather than the machine keeps a single choice in charge of everything a person reads.

#### Scenario: A count follows the displayed language

- **WHEN** the segment count `1240` is displayed while the interface is in English
- **THEN** it reads `1,240`
- **WHEN** the same count is displayed while the interface is in Ukrainian
- **THEN** it uses the Ukrainian grouping separator rather than a comma
