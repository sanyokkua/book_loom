## MODIFIED Requirements

### Requirement: Choose the language from the operating system on first launch

WHEN the application starts and no language has been chosen in Settings, it SHALL display Ukrainian IF the operating system's language is Ukrainian, and English otherwise, without asking. WHEN a person picks a language in Settings › Appearance, the application SHALL store the choice, say it applies after a restart and offer "Restart now", and SHALL display that language, not the operating system's, on every later launch.

**Source**: FR-UI-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), FR-I18N-4 (`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`), `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`. In plain words: guessing from the desktop is right almost always, so nobody is asked on first launch; but a person who wants another language than their desktop's can say so once, and it is remembered. The switch applies after a restart because changing the language live would rebuild every screen, and the sentence beside the switch says so.

#### Scenario: A Ukrainian desktop gets a Ukrainian interface

- **WHEN** the operating system's language is Ukrainian, nothing is stored, and the application starts
- **THEN** the interface is displayed in Ukrainian

#### Scenario: Any other desktop gets English

- **WHEN** the operating system's language is German, nothing is stored, and the application starts
- **THEN** the interface is displayed in English

#### Scenario: No language question appears

- **WHEN** the application starts for the first time on a machine with no stored preferences
- **THEN** no dialog asks which language to use

#### Scenario: The chosen language applies after a restart

- **WHEN** the operating system's language is German and `Ukrainian` is picked in Settings › Appearance
- **THEN** the screen keeps its English words, a line reads "Applies after BookLoom restarts." and a `Restart now` button is offered
- **AND** after the restart the interface is displayed in Ukrainian

#### Scenario: No control changes the language

- **WHEN** every settings area is read
- **THEN** the language choice in Settings › Appearance is the only control that changes the displayed language

#### Scenario: The chosen language beats the operating system

- **WHEN** `English` was picked in Settings, the operating system's language is Ukrainian, and the application is launched again
- **THEN** the interface is displayed in English
