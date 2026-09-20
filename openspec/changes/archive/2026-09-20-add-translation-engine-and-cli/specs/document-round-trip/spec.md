## ADDED Requirements

### Requirement: Release an opened book

WHEN a caller releases an opened book, the system SHALL drop what it holds in memory for that book and report that the
book was open. WHEN the same book is released again, the system SHALL report that it was not open. IF a released book
is written, THEN the system SHALL fail with `ErrorCode.internal` and create no file.

**Source:** `docs/implementation_plan/CHANGE_BACKLOG.md#decision-debt` (finding D3),
`02_Architecture/03_DOCUMENT_MODEL.md#data-model`.
In plain words: an opened EPUB holds every unzipped entry and a parsed tree per chapter until something lets it go.
Without a release, every translated book would stay in memory until the app quits.

#### Scenario: Releasing a book twice

- **WHEN** a TXT book is opened, released, and released again
- **THEN** the first release reports that it was open and the second reports that it was not

#### Scenario: Writing a released book

- **WHEN** an opened Markdown book is released and then written to `Book.uk.md`
- **THEN** the result is `ErrorCode.internal` and `Book.uk.md` is not created
