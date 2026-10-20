## Purpose

Keeping the application's work on this computer so that a translation can be closed and continued after a restart: what is stored, how a crash or an unreadable database is handled, what is never stored (a credential), and what removing a project deletes. The command line is the one user of the application that keeps nothing on disk.

## ADDED Requirements

### Requirement: Keep the application's work across a restart

The system SHALL keep projects, books, briefs, the Glossary, the Recurring terms, segment decisions, the translation memory, summaries, run state, providers, per-model settings and preferences on this computer, so that each is present again after the application is closed and opened.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`, ADR-0043. In plain words: closing BookLoom no longer throws the translation away. Everything a person built up (the book, the names they fixed, the settings they chose, the segments already translated) is still there on the next launch.

#### Scenario: A translation and its names survive a restart

- **WHEN** a project is created for a 142-segment book, the Glossary gains the name `Mira Holt`, 74 segments are decided, and the application is closed and opened again
- **THEN** the project is listed with 74 of 142 segments decided and its Glossary still holds `Mira Holt`

#### Scenario: Preferences and providers survive a restart

- **WHEN** the dark theme is chosen, a provider named `Home Ollama` is saved, and the application is closed and opened again
- **THEN** the dark theme is in force and `Home Ollama` is listed among the providers

#### Scenario: The translation memory and the summary survive a restart

- **WHEN** the decided segment `Tarn walked to Velk.` → `Тарн пішов до Велька.` is in the translation memory, the rolling summary reads `Tarn reaches Velk and meets the warden.`, and the application is closed and opened again
- **THEN** a later segment with the same source text `Tarn walked to Velk.` reuses `Тарн пішов до Велька.` without a model call, and the summary still reads `Tarn reaches Velk and meets the warden.`

#### Scenario: Per-model settings survive a restart

- **WHEN** `gemma4:e4b` is given the context window `16384` and the temperature choice My value `0.5`, `gpt-oss:20b` has refused `temperature`, and the application is closed and opened again
- **THEN** Settings › Models shows `16384` and My value `0.5` for `gemma4:e4b`, and `temperature` under "Settings this model refused" for `gpt-oss:20b`

### Requirement: Write a chunk's decisions together

WHEN a chunk is decided, the system SHALL write its segment decisions, translation memory entries, deferrals, new Glossary names and run record in one step, so that a crash or a forced quit loses at most the decisions of the chunk in flight.

**Source**: FR-PERSIST-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`), ADR-0043. In plain words: a half-written chunk would leave a segment saved without the memory entry that goes with it. Writing them as one unit means the saved state is always one that makes sense. The guarantee is about the application dying; an operating-system crash or power cut may still lose the last chunk, and the application does not claim otherwise.

#### Scenario: A forced quit loses only the chunk in flight

- **WHEN** chunks 1 to 6 are decided, chunk 7 is being translated, and the process is killed
- **THEN** after the next launch the segments of chunks 1 to 6 are decided and the segments of chunk 7 are pending

#### Scenario: A chunk is never half saved

- **WHEN** the process is killed while chunk 7's decisions are being written
- **THEN** after the next launch either all of chunk 7's decisions and memory entries are present or none of them is

### Requirement: Set aside a database that cannot be used and start a fresh one

IF the stored database was made by another schema version, or cannot be read, THEN the system SHALL rename it to `bookloom.db.old-<yyyyMMdd-HHmmss>` beside the original, create a fresh database, write one warning line to the log, and show no dialog.

**Source**: ADR-0043, `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#corruption-and-recovery`. In plain words: before the first release the stored layout changes freely and no migrations are written. An old or damaged file is kept aside for diagnosis rather than deleted, and the application starts clean instead of failing on every launch. A fresh database counts as a first launch, so the first-launch look runs once more (the `llm-provider` capability).

#### Scenario: A database from another schema version is put aside

- **WHEN** the application starts at 2026-10-20 09:15:02 and `bookloom.db` carries schema version 3 while this build uses version 4
- **THEN** the file is renamed `bookloom.db.old-20261020-091502`, a fresh `bookloom.db` is created, and the log gets one warning line
- **AND** no dialog is shown and the Projects screen is empty

#### Scenario: A file that is not a database is put aside

- **WHEN** `bookloom.db` holds 2 kilobytes of text that is not a database
- **THEN** it is renamed `bookloom.db.old-<yyyyMMdd-HHmmss>` with the time of that launch and a fresh database is created

### Requirement: Store a credential never, only the name of an environment variable

The system SHALL store, for a provider that needs a key, only the name of the environment variable that holds it, and SHALL NOT write a key to the database, a log, an exported file or a backup.

**Source**: FR-PERSIST-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`), ADR-0043. In plain words: a saved settings file is easy to copy or share by mistake. Keeping only the variable's name means the secret stays in the person's own environment and never travels with their data.

#### Scenario: Only the variable name is saved

- **WHEN** a provider is saved with its key taken from the environment variable `HOME_API_KEY` whose value is `sk-test-123`
- **THEN** the stored provider holds `HOME_API_KEY`
- **AND** no stored value, log line or backup contains `sk-test-123`

#### Scenario: An exported book holds no key

- **WHEN** a book is translated through that provider and exported as `earth-gravity.uk.epub` with its side files
- **THEN** neither the exported book nor any side file contains `sk-test-123` or `HOME_API_KEY`

### Requirement: Require a credential-like header to name an environment variable

IF a custom header's name contains `authorization`, `key`, `token` or `secret` — compared ignoring letter case, as a part of the name — and its value is not a `${env:NAME}` reference, THEN the system SHALL refuse to save the provider and say which header needs a reference.

**Source**: ADR-0043, `openspec/changes/finalize-ui-and-persistence/design.md` "D13", `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#authentication-and-credentials`. In plain words: typing a secret straight into a header value would store it. Requiring a reference for any header that looks like a credential closes that route, and the message names the header so it is easy to fix. The match is a plain part of the name, so `X-Monkey` (it contains `key`) also needs a reference; a false alarm costs one `${env:NAME}`, a miss would store a secret.

#### Scenario: A literal key in a header is refused

- **WHEN** a provider is saved with the custom header `X-Api-Key` set to `abc123`
- **THEN** the save is refused with a message naming `X-Api-Key` and nothing is stored

#### Scenario: A reference is accepted

- **WHEN** a provider is saved with the custom header `X-Api-Key` set to `${env:HOME_API_KEY}`
- **THEN** the provider is saved and the header holds the text `${env:HOME_API_KEY}`

#### Scenario: A harmless header keeps its literal value

- **WHEN** a provider is saved with the custom header `X-Team` set to `translation`
- **THEN** the provider is saved with that literal value

#### Scenario: The match ignores case and finds a part of the name

- **WHEN** a provider is saved with the custom header `x-session-TOKEN` set to `t-1`, and another with `X-Monkey` set to `banana`
- **THEN** both saves are refused, one naming `x-session-TOKEN` and the other naming `X-Monkey`

### Requirement: Remove a project's saved work and nothing else

WHEN a person removes a project, the system SHALL delete that project's stored data — its brief, term lists, segment decisions, translation memory, summaries and run — and SHALL NOT delete the book file, any exported file, or any other project of the same book.

**Source**: `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (Persistence), `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`. In plain words: Remove forgets the translation inside BookLoom; it never reaches out to files the person owns. A second translation of the same book is a different project and stays.

#### Scenario: Removing one of two translations of a book

- **WHEN** the book `earth-gravity.epub` has the projects `Earth Gravity · EN → UK` and `Earth Gravity · EN → DE`, and the first is removed
- **THEN** its decisions, brief and term lists are gone and `Earth Gravity · EN → DE` is unchanged
- **AND** the book file `earth-gravity.epub` and a previously exported `earth-gravity.uk.epub` are untouched

### Requirement: Delete the stored book with its last project

WHEN the last project of a book is removed, the system SHALL also delete the stored book (its metadata and file location), and SHALL leave the book file on disk untouched.

**Source**: `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (Persistence), ADR-0043. In plain words: a stored book with no project would be an invisible leftover; the next import of the same file stores it again.

#### Scenario: The last project of a book takes the stored book with it

- **WHEN** `Earth Gravity · EN → UK` is the only project of `earth-gravity.epub` and it is removed
- **THEN** no stored book for `earth-gravity.epub` remains, Projects no longer shows the book, and `earth-gravity.epub` is still on disk

#### Scenario: A book with another project keeps its stored row

- **WHEN** `Earth Gravity · EN → UK` is removed while `Earth Gravity · EN → DE` remains
- **THEN** the stored book stays and Projects still shows it with one translation

### Requirement: Store a book once and reuse it by its content

The system SHALL store a book's metadata and the location of its file once, under the hash of its content, and SHALL let every project of that content use it; the book's bytes SHALL NOT be copied into the database, and the file SHALL be read again when a project opens.

**Source**: `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (Persistence), ADR-0043. In plain words: translating the same book into a second language must not copy the book again, and the same bytes under a new file name are still the same book.

#### Scenario: A second language reuses the stored book

- **WHEN** `earth-gravity.epub` is translated into Ukrainian and then into German
- **THEN** one book is stored, holding its metadata and the location of `earth-gravity.epub`, and two projects refer to it
- **AND** the database holds no copy of the book's bytes

#### Scenario: The file is read again when a project opens

- **WHEN** a project of `earth-gravity.epub` is opened after a restart
- **THEN** the book is read again from its stored location and its content hash is checked against the stored one

#### Scenario: The same bytes under another file name are the same book

- **WHEN** a copy of `earth-gravity.epub` named `earth-gravity-copy.epub`, with identical bytes, is imported
- **THEN** it matches the stored book by its content hash, no second book is stored, and Projects shows its translations under the one book

### Requirement: Accept a found book only when its content matches

WHEN the person chooses a file with Find the book… for a project whose book file is missing, the system SHALL accept it only when its content hash equals the stored one, and SHALL then store the new location for the book, so that every project of that book opens from it; IF the content differs, THEN it SHALL store nothing and say the file is a different book.

**Source**: `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (Persistence), `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`. In plain words: a moved book can be found again without importing it anew, but another book, or another edition, can never be attached to a translation by mistake.

#### Scenario: Find the book updates the one location

- **WHEN** `earth-gravity.epub` was moved to `~/Books/earth-gravity.epub` and the person chooses that file with Find the book…
- **THEN** the stored location becomes `~/Books/earth-gravity.epub` and both projects of that book open from it

#### Scenario: A different file is refused

- **WHEN** the person chooses `other-book.epub`, whose content hash differs, with Find the book…
- **THEN** the stored location is unchanged and the person is told the file is a different book

### Requirement: Keep project names unique within one book, ignoring letter case

The system SHALL refuse a project name that equals the name of another project of the same book when letter case is ignored, Cyrillic letters folded like Latin ones, and SHALL report the duplicate instead of saving it; projects of different books MAY carry the same name.

**Source**: `openspec/changes/finalize-ui-and-persistence/design.md` "D10", `docs/specification/mockups/README.md#project-names--one-book-several-translations-pn`, decision 9 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: Projects groups translations by book, so two translations of one book that differ only by capitals would look the same under one heading. Two different books — two editions with the same title, for example — each keep their automatic name.

#### Scenario: Two books with the same title and pair both keep the name

- **WHEN** `earth-gravity.epub` and `earth-gravity-2nd-edition.epub`, whose contents differ, are each given a project for English to Ukrainian
- **THEN** both projects are stored with the name `Earth Gravity · EN → UK`

#### Scenario: A Cyrillic name differing only by case is refused in one book

- **WHEN** a project of `earth-gravity.epub` is named `Земля` and another project of the same book is renamed `земля`
- **THEN** the rename is refused as a duplicate name and `Земля` is unchanged

#### Scenario: A Latin name differing only by case is refused in one book

- **WHEN** a project of `earth-gravity.epub` is named `Earth Gravity · 26b` and another project of the same book is created as `EARTH GRAVITY · 26B`
- **THEN** the creation is reported as a duplicate name and nothing is stored

### Requirement: Keep the database in the per-user data folder under a single running instance

The system SHALL keep the database in the per-user data folder, and WHILE one BookLoom is running, a second launch SHALL show the dialog "BookLoom is already running" and exit without opening the database.

**Source**: FR-PERSIST-03, FR-PERSIST-04, FR-PERSIST-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`). In plain words: the saved work belongs to the person, in the place their operating system gives to applications, and two windows writing to one file would corrupt it, so a second launch is turned away with one plain sentence.

#### Scenario: The database is in the data folder

- **WHEN** the application runs on macOS for the user `ok`
- **THEN** the database is `~/Library/Application Support/BookLoom/bookloom.db` and nothing is written beside the book
- **AND** a development run uses `~/Library/Application Support/BookLoom-Dev/bookloom.db` instead

#### Scenario: A second launch is refused

- **WHEN** BookLoom is running and a second BookLoom is started
- **THEN** the second one shows the dialog "BookLoom is already running", and exits after it is closed without opening the database

### Requirement: Make a backup copy on request

WHEN a person chooses Make a backup… and a destination, the system SHALL write a copy of the stored work to that destination and SHALL leave the stored work unchanged.

**Source**: `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/specification/01_Product/07_SETTINGS.md#storage-and-logs-tab`. In plain words: a person can keep a safe copy before an experiment or a computer change. The copy is made only when they ask, and it contains no key because none is stored.

#### Scenario: A backup is written

- **WHEN** Make a backup… is chosen with the destination `~/Backups/bookloom-2026-10-20.db`
- **THEN** that file exists and, when the database held 3 projects, opens with exactly those 3 projects

#### Scenario: A cancelled backup writes nothing

- **WHEN** the destination dialog of Make a backup… is cancelled
- **THEN** no file is written and nothing is reported as an error

### Requirement: Keep nothing on disk from the command line

WHEN a book is translated from the command line, the system SHALL keep its working state in memory only, and SHALL NOT create, read or change the database.

**Source**: ADR-0043. In plain words: scripted and evaluation runs must never fill the person's Projects list or depend on what the window stored, so the command line starts clean every time.

#### Scenario: A command-line run leaves the Projects list unchanged

- **WHEN** the command line translates `fixture.md` while the window's database lists 3 projects
- **THEN** after it ends the database still lists exactly those 3 projects and was not opened by the run

### Requirement: Never freeze the window on storage work

The system SHALL run every database read and write away from the thread that draws the window, so that the screen stays responsive while storage work is in progress.

**Source**: `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#db-write-confinement`, ADR-0043. In plain words: saving after every chunk must not make the buttons and the scrolling stall, or a long book would feel broken.

#### Scenario: The window responds during a slow write

- **WHEN** a chunk's decisions take 800 ms to write
- **THEN** a click on Pause during those 800 ms is handled without waiting for the write
