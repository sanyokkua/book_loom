## ADDED Requirements

### Requirement: Always show which segment of a segmented control is chosen

WHEN a segmented control is shown for a setting that has a value, the application SHALL show that value's segment as selected from the first moment the control is visible, in both themes, whether the value was stored, is a default, or was set before the control was laid out.

**Source**: `research/ui-audit-settings.md#defects` (S-01). In plain words: on Settings › Automation both segmented controls showed no selection at all while the title bar said "Balanced · Unattended", so the tab read as "nothing is set".

#### Scenario: Automation shows its defaults

- **WHEN** Settings › Automation is opened on a fresh data folder
- **THEN** "When to stop and ask you" shows `Unattended` selected and "Quality new books start at" shows "By the AI model (Fast on a small model, Balanced otherwise)" selected (the default the `settings` capability's "Let new books take their quality from the AI model" states), each with the selected fill and the 2-pixel bar

#### Scenario: A stored choice is shown after a restart

- **WHEN** `Max` was chosen as the default quality, the application is restarted and Automation is opened
- **THEN** `Max` is the selected segment

### Requirement: Draw every fold with the one flat fold look

Every fold (a titled section that opens and closes) SHALL be drawn as a flat card with the surface background, the hairline border, the card radius and a muted arrow, and its keyboard focus SHALL be the standard focus ring around the title with no effect on the arrow. No fold SHALL keep the toolkit's default gradient title bar.

**Source**: `research/ui-audit-workflow.md#defects` (W-01, W-34), `research/ui-audit-shell-dialogs.md#defects` (D-01). In plain words: "How progress is counted" was the one control that looked as if it came from another program — a grey gradient bar with a hard frame — on Translating and in every progress dialog, in both themes.

#### Scenario: How progress is counted is flat

- **WHEN** Translating is shown, in the light theme and in the dark theme
- **THEN** the title bar of "How progress is counted" has the surface colour of the card it stands in and no gradient, like "About this book"

#### Scenario: A focused fold keeps a crisp arrow

- **WHEN** "Prompt context" is opened and has the keyboard focus
- **THEN** its arrow has the same size and no shadow, as on the unfocused fold beside it, and the focus ring is drawn around the title

### Requirement: Draw a banner's mark from the icon set, centred on its first line

Every banner SHALL draw its mark with the icon set the navigation uses — one icon per tone: information, warning, success, failure — vertically centred on the first line of its text, SHALL set only a short title in bold, and SHALL put no more than 6 pixels between its title, its text and its action row.

**Source**: `research/ui-audit-workflow.md#defects` (W-33), `research/ui-audit-shell-dialogs.md#defects` (D-05, D-06), `research/ui-audit-settings.md#defects` (S-17). In plain words: the information mark was a small serif "i" hanging 6 pixels above its line on some banners and a circled "i" on others, and a banner of two short lines and a button was 120 pixels tall because of 35-pixel gaps.

#### Scenario: One information mark

- **WHEN** the Export information banner, the Models information banner and the keep-awake note are shown
- **THEN** all three carry the same information icon

#### Scenario: The mark sits on the first line

- **WHEN** the banner "Welcome back — 74 of 142 segments are done." is shown
- **THEN** the vertical centre of its icon is within 1 pixel of the vertical centre of that line

#### Scenario: A compact banner

- **WHEN** the welcome-back banner shows a title, one sentence and one button at 1440 pixels of window width
- **THEN** it is at most 96 pixels high

### Requirement: Keep edges and locked values readable in both themes

In the light and the dark theme alike, the edge of a red outlined button, of a field in error and of an unselected radio button SHALL reach a contrast of at least 3 to 1 against the surface it stands on, and the text of a locked (disabled) choice box that states a fact SHALL reach at least 4.5 to 1.

**Source**: `research/ui-audit-settings.md#dark-theme`, `research/ui-audit-settings.md#defects` (S-14), `research/ui-audit-workflow.md#dark-theme`. In plain words: in dark the border of "Delete…" almost vanished, unselected radio buttons were a dark ring on a dark card, a field in error had a paler edge than a normal field, and the locked languages on the Book Brief — the two facts that card exists to show — were barely readable.

#### Scenario: Delete… in dark

- **WHEN** Settings › Providers is shown in the dark theme
- **THEN** the border of `Delete…` reaches at least 3 to 1 against the card

#### Scenario: A field in error

- **WHEN** the provider's Address field is in error in the light theme
- **THEN** its border reaches at least 3 to 1 against the card and is not paler than the border of a field without an error

#### Scenario: Locked languages in dark

- **WHEN** the Book Brief of a project with decided segments is shown in the dark theme
- **THEN** `English` and `Ukrainian` in the locked language boxes reach at least 4.5 to 1 against their field
