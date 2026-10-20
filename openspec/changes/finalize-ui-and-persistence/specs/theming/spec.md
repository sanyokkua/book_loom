## ADDED Requirements

### Requirement: Show every state in one of six colours, with an icon and a word

The application SHALL show every state marker, chip and badge as an icon plus a word, in one colour per meaning: grey for waiting, queued or idle; blue for working; green for done; amber for needs you or slow; red for failed or unreachable; sand for kept as is. A colour SHALL mean the same thing on every screen and SHALL take its value from the existing status roles.

**Source**: `docs/specification/mockups/README.md#g11--state-markers-and-one-colour-map`, FR-THEME-10 (`docs/specification/01_Product/09_THEMING.md#status-colours`). In plain words: a person learns the six colours once and they hold on a list row, a chip in the title bar and a banner. The word and the icon are always there too, so the state never depends on telling two colours apart.

#### Scenario: A queued segment is grey and says "waiting"

- **WHEN** a segment that has not been sent to the model yet is listed
- **THEN** its marker is grey and reads `waiting`
- **AND** the word `translating…` is never used for it

#### Scenario: The model at work is blue

- **WHEN** a call is being answered
- **THEN** its marker is blue with the word `translating…`, and the AI-model chip in the title bar is blue with `Model answering`

#### Scenario: A failure is red

- **WHEN** the AI model cannot be reached
- **THEN** the chip is red with `Cannot reach Ollama`, using the danger role, which resolves to `#b0574c` under the light values

#### Scenario: A kept segment is sand, a finished one is green

- **WHEN** a segment is kept as is, and another is translated
- **THEN** the first shows a sand marker with `kept as is` and the second a green marker with `translated`, which resolves to `#5f8a6b` under the light values

### Requirement: Underline every link and colour it for reading

The application SHALL underline every link and paint it with the information foreground role, which SHALL reach a contrast of at least 4.5 to 1 on every surface it appears on, in both the light and the dark block.

**Source**: `docs/specification/mockups/README.md#g11--state-markers-and-one-colour-map`, FR-A11Y-8 (`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md`). In plain words: colour alone must not say "this is a link", and the colour chosen must be readable on the white cards and on a selected row. The existing information role already passes, so no new colour is added.

#### Scenario: A link is underlined

- **WHEN** the link `Show them` on the Already-in-projects line is displayed
- **THEN** it is underlined and painted with the information foreground role

#### Scenario: The weakest surface still passes

- **WHEN** the information foreground is measured against every surface it appears on, in both blocks
- **THEN** the lowest contrast found is at least 4.5 to 1 (4.60 on a selected row)

### Requirement: Mark the selected choice of a segmented control with a bar

The application SHALL mark the selected segment of a segmented control with a 2-pixel bar in the strong-text role as well as its fill, because the fill alone differs from its neighbours by only 1.24 to 1.

**Source**: `docs/specification/mockups/README.md#g11--state-markers-and-one-colour-map`. In plain words: the chosen option of Fast · Balanced · Max must be visible to someone who cannot tell the two fills apart. A bar in the primary colour would reach only 2.86 to 1, so the strong-text role is used.

#### Scenario: The chosen quality shows a bar

- **WHEN** the quality choice shows `Balanced` selected
- **THEN** the `Balanced` segment carries a 2-pixel bar in the strong-text role and its neighbours `Fast` and `Max` carry none

### Requirement: Keep a scrollbar's thumb distinguishable from its track

The application SHALL paint a scrollbar's thumb with a contrast of at least 3 to 1 against its track in both the light and the dark block, in the navigation column as well as in the content.

**Source**: `docs/specification/mockups/README.md#g3--visible-scrollbars`, WCAG 2.1 non-text contrast. In plain words: a scrollbar that is always shown is useless if the thumb fades into the track, so its colours are held to the same 3 to 1 as other control edges.

#### Scenario: The thumb passes in both themes

- **WHEN** the thumb and the track are measured in the light block and in the dark block
- **THEN** each pair reaches at least 3 to 1

## MODIFIED Requirements

### Requirement: Follow the operating system's colour scheme on first display

WHEN the window is first shown and no theme has been chosen, the application SHALL apply the value block matching the operating system's current colour scheme, or the light block IF the system reports no preference. WHEN a theme has been chosen and stored, that choice SHALL win over the operating system's scheme on every later launch.

**Source**: FR-THEME-3 (`docs/specification/01_Product/09_THEMING.md#token-model`), FR-SETTINGS-04 (`docs/specification/01_Product/07_SETTINGS.md#appearance-tab`). In plain words: somebody on a dark desktop should not be flashed a white window the first time, and somebody who picked a theme should not have it overruled by the desktop afterwards. Light is the fallback because it is the theme the mockup is drawn in.

#### Scenario: A dark desktop opens a dark window

- **WHEN** the operating system reports a dark colour scheme, nothing is stored, and the window is first shown
- **THEN** the dark block is in force and the app background resolves to `#283237`

#### Scenario: No stated preference falls back to light

- **WHEN** the operating system reports no colour-scheme preference, nothing is stored, and the window is first shown
- **THEN** the light block is in force and the app background resolves to `#f4f1ea`

#### Scenario: A stored choice beats the desktop

- **WHEN** light was chosen and stored, and the operating system reports dark at the next launch
- **THEN** the light block is in force

### Requirement: Let a person change the theme in two places

The application SHALL offer a two-state control in the window's title bar that moves directly between light and dark, and a three-way choice in the appearance settings between light, dark and following the operating system. WHEN either control is used, the application SHALL apply the new block at once, without a restart, and SHALL store the choice so that every later launch applies it.

**Source**: FR-SETTINGS-04 (`docs/specification/01_Product/07_SETTINGS.md#appearance-tab`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`. In plain words: the title-bar control is the one you reach for when the room's light changes; the settings choice is where you say "just follow my desktop". Both are remembered now, because the application keeps its settings on this computer; until a choice is made, the desktop decides.

#### Scenario: The title-bar control flips the theme

- **WHEN** the light block is in force and the title-bar theme control is activated
- **THEN** the dark block is in force and the app background resolves to `#283237`

#### Scenario: The settings choice follows the desktop

- **WHEN** the appearance setting is set to follow the operating system, and the operating system reports light
- **THEN** the light block is in force

#### Scenario: A theme choice survives a restart

- **WHEN** the theme is set to dark, the application is closed, and it is launched again on a machine whose operating system reports light
- **THEN** the dark block is in force and the app background resolves to `#283237`

#### Scenario: A theme choice does not survive a restart

- **WHEN** the theme was stored as dark, but at the next launch the stored settings cannot be read and are replaced by empty ones, and the operating system reports light
- **THEN** the stored choice is gone and the light block is in force, because the operating system's scheme decides again

#### Scenario: Following the desktop is remembered too

- **WHEN** the theme was set to follow the operating system, the application is closed, and the desktop is switched to dark before the next launch
- **THEN** the dark block is in force on that launch
