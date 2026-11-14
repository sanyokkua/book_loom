## MODIFIED Requirements

### Requirement: Show a segment's findings, score and context in the compare

The compare SHALL show the segment's findings — each rewrite the Proofread stage kept as a highlighted diff headed "Edit applied", the words it removed in the error colour and the words it put there in the success colour, and every other finding as its kind, severity, note and the check that raised it — a badge with a stored score when the record has one, and a context line naming what the segment was translated with.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d12--what-a-translate-request-carries`. In plain words: the findings say why the segment is here, and the context line says what the model knew when it translated it. The verdict of the Proofread stage is shown by "Show a segment's proofread verdict and its reason in the compare". The context a call was sent no longer holds chapter summaries or a recently-present line, so the context line names neither.

#### Scenario: A flagged segment shows its score and context

- **WHEN** `ch5 · p12` is selected, flagged with a stored score of `0.58`, translated with the brief, two glossary terms and the previous paragraph
- **THEN** a badge shows the score `0.58` and the context line reads `brief · glossary(2) · previous paragraph`

#### Scenario: An applied edit is shown as a diff

- **WHEN** the selected segment's proofread verdict records a kept rewrite `Ліна сказала` that replaced `Ліна сказав`
- **THEN** the findings list shows `Edit applied`, then `− Ліна сказав` in the error colour, then `+ Ліна сказала` in the success colour
- **AND** a rewrite that only deleted words shows `+ (removed)`; the Ukrainian bundle shows `Правку застосовано`

#### Scenario: A finding is shown with the check that raised it

- **WHEN** the selected segment's record carries a REPAIR `terminal-mark` finding
- **THEN** the findings list shows its kind, its severity, its note and the check `terminal mark`

#### Scenario: No score badge without a stored score

- **WHEN** the selected segment was translated on the Fast dial, or its decision gave no score
- **THEN** no score badge is shown and its findings are listed

### Requirement: Retry a segment with a context rebuilt from the current glossary and settings

WHEN the person chooses Try again or Try again with a note… under the desk's More menu on a FLAGGED or ACCEPTED segment, the application SHALL draft that one segment again with a context built at that moment from the current glossary, brief settings and preceding accepted targets, adding the note as an extra instruction and lowering the temperature when `Lower temperature for this retry` is checked.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d12--what-a-translate-request-carries`, `design.md#d7--the-recurring-term-list-is-removed-whole`. In plain words: the owner's decision that Try again uses the current glossary and settings: a retry is the person's second attempt after they changed something, so it must see the change. The earlier rule replayed the context the first draft saw, which made a corrected name come back wrong. The temperature values are the inference capability's. The rebuilt context is what every translate request now carries: the name rows, the previous pairs and the next paragraph; there is no recurring-term list and no chapter summary to rebuild.

#### Scenario: Retry with a note passes

- **WHEN** the run is paused and the person chooses "Try again with a note…" on `ch5 · p12`, types `keep it more formal` and checks `Lower temperature for this retry`
- **AND** the new target passes the hard gates and every check
- **THEN** `ch5 · p12` becomes ACCEPTED with the new target, recorded as drafted and marked reviewed
- **AND** exactly one draft call was made, carrying `keep it more formal` and the temperature `0.1`

#### Scenario: A retry uses the current glossary

- **WHEN** `ch5 · p12` was drafted while the glossary held `Hale` → `Хейл`, the person has since changed it to `Гейл`, and chooses Try again on `ch5 · p12`
- **THEN** the prompt carries `Hale` → `Гейл`

#### Scenario: A retry uses the current recurring-term rendering

- **WHEN** `ch5 · p12` was drafted while the glossary's title row `warden` read `вартовий`, the consistency pass has since pinned `сторож` because the book agrees on it, and the person chooses Try again
- **THEN** the prompt carries `warden → сторож`

#### Scenario: A retry with nothing changed sees the same context

- **WHEN** nothing in the glossary or the settings changed since `ch5 · p12` was drafted
- **THEN** the retry's context equals the one the first draft saw

## REMOVED Requirements

### Requirement: Run the audit again on demand

**Reason**: no screen and no command calls it: the operation `ReviewDesk.audit` has an implementation and no caller in the window, the command line or the tests (`research/simplification-inventory.md`, section 1.2). The audit runs inside every run, after the consistency pass, and its doubts are on the review desk.

**Migration**: none. Nothing a person could do is taken away; a run, or a continued run, audits the book again.
