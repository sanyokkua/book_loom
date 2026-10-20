## MODIFIED Requirements

### Requirement: Record each segment's findings for review and repair

The application SHALL record for every decided segment its confidence and each finding with the check or the reviewer
that raised it, its type, its severity and a short note, SHALL record every edit it applied as a low finding raised by
`reviewer-edit` that carries the quote and its replacement, and SHALL keep the findings of a FLAGGED segment for the
review dialog and for any later retry.

**Source**: FR-QA-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`. In plain words: a directed fix needs to know exactly what was wrong, and so does the person reviewing a flagged segment; a flag with no reason would leave them guessing, and an edit made for them should be shown as what it removed and what it put there.

#### Scenario: A flagged segment keeps its reasons

- **WHEN** segment `ch5 · p12` is flagged after its length ratio of `0.41` fell outside `0.7–1.8`
- **THEN** its record carries confidence and a medium `omission` finding from the length-ratio check

#### Scenario: An accepted segment keeps the edits made to it

- **WHEN** a segment is accepted after the reviewer's `gender` edit `Вона втомився` → `Вона втомилася` was applied
- **THEN** its record carries a low `gender` finding raised by `reviewer-edit` holding both texts

#### Scenario: An accepted segment keeps a low note

- **WHEN** a segment is accepted with a `style` remark from the reviewer
- **THEN** its record carries that remark as a low finding raised by `reviewer`
