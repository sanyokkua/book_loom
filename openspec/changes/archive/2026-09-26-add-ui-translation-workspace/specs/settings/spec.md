# Spec Delta

## Purpose

How a person chooses what actually translates their book, and how they see whether that choice will work: the
local providers the application knows from first launch, checking one of them stage by stage so a failure names
its own cause, choosing a model from what the server offers or naming one it does not list, and choosing how the
interface looks.

## ADDED Requirements

### Requirement: Offer two local providers from first launch

The application SHALL present, without any configuration, a provider named for Ollama at
`http://localhost:11434` and a provider named for LM Studio at `http://localhost:1234/v1`, and SHALL let a person
select which of the two is in use.

Exactly one provider SHALL be selected at any time.

The application SHALL NOT offer to add, edit or delete a provider, and SHALL show those actions as unavailable.

**Source:** FR-PROV-01, FR-PROV-02, FR-PROV-04
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`),
`docs/specification/01_Product/07_SETTINGS.md#providers-tab`.
In plain words: the two servers almost everybody runs locally are already known to the application, so the first
run needs no setup at all — pick one, pick a model, translate. Adding a third provider is a real feature with a
dialog, credential handling and validation behind it; leaving its buttons greyed is honest about that, and
hiding them would make the screen look finished when it is not.

#### Scenario: Both providers are listed with no setup

- **WHEN** the provider settings are opened on a first launch
- **THEN** a provider at `http://localhost:11434` and a provider at `http://localhost:1234/v1` are both listed

#### Scenario: Selecting one deselects the other

- **WHEN** the LM Studio provider is selected while the Ollama provider was selected
- **THEN** LM Studio is the selected provider and Ollama is not

#### Scenario: Adding a provider is unavailable

- **WHEN** the provider settings are shown
- **THEN** the actions to add, edit and delete a provider are present and unavailable

### Requirement: Check a provider in three stages and report each one

The application SHALL offer to check the selected provider, and that check SHALL report three findings
independently: whether the server answers at all, whether its list of models can be read and contains the chosen
model, and whether it returns a usable answer to a short request.

The check SHALL be unavailable until a model is chosen, because the second and third findings are both asked
about a particular model.

The check SHALL run all three stages rather than the reduced set a pre-run preflight uses.

WHEN a stage fails outright, the application SHALL report that stage's own failure and SHALL NOT report the
stages after it as passed.

WHEN the model listing cannot be read but the server answers, the application SHALL report that stage as a
qualified pass rather than a failure.

**Source:** FR-PROV-06, FR-PROV-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-add-edit-provider`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`.
In plain words: "it does not work" is useless; "the server answers but that model is not loaded" tells you what
to do next. A server with no listing endpoint is a normal, working configuration — treating it as a failure would
refuse the setups that manual model entry exists for. The check waits for a model because two of its three
questions are about one: "is this model in the list" and "does this model answer" have no meaning with nothing
chosen, and offering the button anyway would produce a check that reports one finding and two blanks.

#### Scenario: The check is unavailable until a model is chosen

- **WHEN** the Ollama provider is selected and no model has been chosen
- **THEN** the action to check the provider is present and unavailable
- **WHEN** `gemma3:12b` is then chosen
- **THEN** the action to check the provider becomes available

#### Scenario: An unreachable server fails the first stage only

- **WHEN** the selected provider is checked and nothing is listening at `http://localhost:11434`
- **THEN** the first finding reports `ErrorCode.unreachable`
- **AND** the second and third findings are reported as not attempted, not as passed

#### Scenario: A reachable server with the chosen model passes all three

- **WHEN** the selected provider answers, lists `gemma3:12b`, and `gemma3:12b` is chosen
- **THEN** all three findings are reported as passed

#### Scenario: A server with no model listing still passes

- **WHEN** the selected provider answers but its listing cannot be read
- **THEN** the second finding is reported as a qualified pass carrying a note
- **AND** the third finding is still attempted

#### Scenario: A check in flight reports itself

- **WHEN** the check has been started and the verification port has not yet answered
- **THEN** the screen reports the check as in progress and the control that started it is unavailable
- **AND** it becomes available again when the report arrives

### Requirement: Choose a model from the list, or name one the list does not hold

The application SHALL offer the models the selected provider reports, and SHALL also accept a model identifier
typed by hand.

IF the provider reports no models, or its listing cannot be read, THEN the application SHALL leave the typed
entry available and SHALL say that no list was obtained rather than presenting an empty choice as the whole
truth.

WHEN the selected provider changes, the application SHALL discard the previously offered list and the chosen
model.

**Source:** FR-MODEL-01, FR-MODEL-02, FR-MODEL-03
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`), DD-38,
`docs/specification/01_Product/07_SETTINGS.md#models-tab`.
In plain words: some servers list what they hold and some do not, and a server that holds nothing is a valid
state rather than a broken one. Typing the identifier has to stay possible in all three cases, or the
application becomes unusable against exactly the servers it was written for. The chosen model is discarded on a
provider change because a model identifier means nothing on a different server.

#### Scenario: A listed model can be chosen

- **WHEN** the selected provider reports `gemma3:12b` and `qwen3:8b`
- **THEN** both are offered, and choosing `gemma3:12b` makes it the chosen model

#### Scenario: An unlisted model can be typed

- **WHEN** the selected provider reports no models and `mistral-small:24b` is typed
- **THEN** `mistral-small:24b` is the chosen model

#### Scenario: An unreadable listing says so

- **WHEN** the selected provider's listing cannot be read
- **THEN** the application reports that no list was obtained and keeps the typed entry available

#### Scenario: Changing provider clears the model

- **WHEN** `gemma3:12b` is chosen on the Ollama provider and the LM Studio provider is then selected
- **THEN** no model is chosen and the offered list is empty until it is fetched again

#### Scenario: A listing in flight reports itself

- **WHEN** the model list has been asked for and the catalogue port has not yet answered
- **THEN** the screen reports the listing as in progress
- **AND** the typed entry stays available throughout, because it needs no list

### Requirement: Show every settings area, and enable only what works

The settings screen SHALL present all six areas — providers, models, generation, appearance, automation, and
storage and logs — and SHALL show as unavailable every area this build does not implement.

The available areas SHALL be providers and appearance.

The appearance area SHALL carry the theme choice and, beside it, the accent shown as a fixed, read-only value,
because the accent is not selectable in this version.

**Source:** FR-SETTINGS-02, FR-SETTINGS-04
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-settings`), FR-THEME-4
(`docs/specification/01_Product/09_THEMING.md#token-model`),
`docs/specification/01_Product/07_SETTINGS.md#settings-tabs`,
`docs/specification/01_Product/07_SETTINGS.md#appearance-tab`.
In plain words: the same reasoning as the greyed navigation entries — showing the shape of the finished settings
screen tells a person what this application will eventually let them control, and an empty tab that opens is
worse than one that says it is not ready. The accent is named because the appearance tab has exactly three rows
in the specification and this change builds one of them; leaving the accent out entirely would read as an
oversight rather than as the deliberate "fixed in v1" that it is. It is read-only rather than disabled: there is
nothing coming to enable.

#### Scenario: All six areas are listed

- **WHEN** the settings screen is opened
- **THEN** areas for providers, models, generation, appearance, automation, and storage and logs are all present

#### Scenario: An unimplemented area does not open

- **WHEN** the generation area is activated
- **THEN** it does not open and the previously shown area stays

#### Scenario: The appearance area shows a fixed accent

- **WHEN** the appearance area is shown
- **THEN** it presents the theme choice and reports the accent as a fixed value
- **AND** no control offers to change the accent

### Requirement: Keep nothing chosen here beyond the session

The application SHALL apply a chosen provider, model and theme immediately, and SHALL NOT store any of them. A
subsequent launch SHALL start from the built-in providers with no model chosen, and SHALL take its theme and its
displayed language from the operating system.

This build offers no control that changes the displayed language; `localization` states that the language is
chosen from the operating system alone.

**Source:** FR-PROV-04, FR-MODEL-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`,
`#fr-model`), FR-UI-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`).
In plain words: the application has no storage yet, so it remembers nothing, and this says so instead of leaving
a person to discover it. Remembering the chosen model per provider is specified behaviour that arrives with local
storage; until then, pretending otherwise would be the more expensive mistake. The interface language is not
listed among the things applied here because nothing in this build chooses it — FR-UI-06's in-app switch needs
both a control and somewhere to remember the answer, and it is deferred whole rather than half-built.

#### Scenario: A chosen model is gone after a restart

- **WHEN** `gemma3:12b` is chosen, the application is closed, and it is launched again
- **THEN** no model is chosen

#### Scenario: Nothing chosen is written anywhere

- **WHEN** the Ollama provider, the model `gemma3:12b` and the dark theme have been chosen
- **THEN** the application's data directory contains no file recording a provider, a model, a theme or a language
