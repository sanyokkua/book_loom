## ADDED Requirements

### Requirement: Store which Prepare steps a project has finished

The system SHALL store with each project whether its brief step and its names step of the Prepare stage have finished, each written in the same all-or-nothing write as that step's result — for the names step, the glossary rows it removed, changed and added and the deferrals they raise — so that after a restart a finished step is known to be finished.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d20--prepare-steps-inside-the-run`, `design.md#d9--stored-shapes-that-change`. In plain words: a run now asks the model for the brief and the names before it translates, which takes minutes. A run that is stopped and continued, or an application that is closed and opened, must not ask again for what it already has, and must ask again for a step that never finished; the mark and the result are one write so they cannot disagree. A step that failed for a reason other than the provider being away is a different case: its result write stores nothing, and the run then marks the step finished by a separate save, with the glossary as it was, so that the step is not tried again by every continue.

#### Scenario: A finished names step survives a restart

- **WHEN** a run's names step finishes with 37 glossary rows, the application is closed during the Translate stage and opened again, and the run is continued
- **THEN** the project still says the names step is finished, and the continued run makes no names call

#### Scenario: A step that never finished is not marked

- **WHEN** the application is closed while the names step waits for its twentieth call
- **THEN** after the restart the project says the names step is not finished and the glossary holds what it held before the step began

#### Scenario: The mark and the result are one write

- **WHEN** storing the names step's result fails
- **THEN** that write stores no row, no deferral and no mark

#### Scenario: A refused names write leaves nothing behind after a restart

- **WHEN** the names step's write changes 12 glossary rows, adds 25 and names one changed row the project does not hold, so the write is refused with `ErrorCode.validation`, and the database file is closed and opened again before anything else is written
- **THEN** the glossary holds exactly the rows it held before the step, no deferral of the step exists, and the project says the names step is not finished

#### Scenario: A failed names step the run went on from is finished after a restart

- **WHEN** the names step's write was refused, the run then recorded the step as failed by its own separate save of the project (the `translation-pipeline` capability's "Go on with code defaults when a Prepare step fails") and went on, and the database file is closed and opened again
- **THEN** the glossary holds exactly the rows it held before the step, and the project says the names step is finished, so a continued run makes no names call

#### Scenario: A stored names write is whole after a restart

- **WHEN** the names step's write of 12 changed rows, 25 added rows and 3 deferrals succeeds, and the database file is closed and opened again
- **THEN** the glossary holds the 37 rows, the 3 deferrals are open, and the project says the names step is finished

### Requirement: Remember the target language last used

WHEN a translation starts, the system SHALL store its target language as the last one used, in the settings table, replacing the earlier value.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `research/review-digest.md#f16--defaults-work-against-the-goal`, `design.md#d21--defaults-instead-of-questions`. In plain words: the target language was the one decision the shortest path forced on the person for every book. Most people translate into one language, so the last one used is offered for the next book; it is a preference like the theme, so it lives in the settings table.

#### Scenario: The last target is kept across a restart

- **WHEN** a translation into `uk` starts, the application is closed and opened again, and a new book is imported
- **THEN** the settings table holds `uk` as the last target language

#### Scenario: A later run replaces it

- **WHEN** a translation into `uk` starts and later one into `pl`
- **THEN** the settings table holds `pl`

## MODIFIED Requirements

### Requirement: Keep the application's work across a restart

The system SHALL keep projects with the Prepare steps they have finished, books, briefs, the Glossary, segment decisions with their proofread verdicts, the translation memory, the fixed frames of the runs' prompts, run state, providers, per-model settings with the person's overrides and preferences on this computer, so that each is present again after the application is closed and opened.

**Source**: `design.md#d9--stored-shapes-that-change`. In plain words: closing BookLoom does not throw the translation away. Everything a person built up (the book, the names they fixed, the settings they chose, the segments already translated) is still there on the next launch. The list of recurring terms and the chapter summaries are no longer among them: both are removed (ADR-0043 sets aside the database an older build made).

#### Scenario: A translation and its names survive a restart

- **WHEN** a project is created for a 142-segment book, the Glossary gains the name `Mira Holt`, 74 segments are decided, and the application is closed and opened again
- **THEN** the project is listed with 74 of 142 segments decided and its Glossary still holds `Mira Holt`

#### Scenario: Preferences and providers survive a restart

- **WHEN** the dark theme is chosen, a provider named `Home Ollama` is saved, and the application is closed and opened again
- **THEN** the dark theme is in force and `Home Ollama` is listed among the providers

#### Scenario: The translation memory and the summary survive a restart

- **WHEN** the decided segment `Tarn walked to Velk.` → `Тарн пішов до Велька.` is in the translation memory, and the application is closed and opened again
- **THEN** a later segment with the same source text `Tarn walked to Velk.` reuses `Тарн пішов до Велька.` without a model call, and the project holds no chapter summary

#### Scenario: Proofread verdicts survive a restart

- **WHEN** 3 segments hold proofread verdicts and the application is closed and opened again
- **THEN** the 3 segments still hold their verdicts

#### Scenario: Per-model settings survive a restart

- **WHEN** `gemma4:e4b` is given the context window `16384` and the temperature choice My value `0.5`, `gpt-oss:20b` has refused `temperature`, and the application is closed and opened again
- **THEN** Settings › Models shows `16384` and My value `0.5` for `gemma4:e4b`, and `temperature` under "Settings this model refused" for `gpt-oss:20b`

### Requirement: Keep no setting or record for a behaviour that is gone

The system SHALL keep no global Thinking or Sampling setting, no last-check setting, no per-segment count of repair rounds, no rolling summary, no chapter summary, no chapter-summaries switch, no story shape, no apostrophe, quotation-mark, address or hard-g choice of a brief, no list of recurring terms, no mark that a gender rule was tried on a glossary entry and no answer to a question about a name's meaning, and SHALL read none of them if an older file still held them.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`, `design.md#d26--the-book-brief-has-no-language-specific-rows`, `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d7--the-recurring-term-list-is-removed-whole`, `design.md#d9--stored-shapes-that-change`. In plain words: the per-model profile and overrides replace the global switches; the export no longer runs a last check; there is one repair, so a count of rounds says nothing; the rolling summary and the chapter summaries that replaced it are both gone, with their switch and the story shape; the Book Brief's language-specific choices are gone; and the recurring-term list, the gender marks of the removed rules and the answers to the removed Start questions are gone with the behaviour they served. Nothing stores them, and the database an older build made is set aside rather than read.

#### Scenario: Generation stores no thinking or sampling

- **WHEN** the person changes every control on Settings › Generation and the application is restarted
- **THEN** the settings table holds no thinking and no sampling entry

#### Scenario: No last-check entry

- **WHEN** the Automation tab is saved
- **THEN** the settings table holds no last-check entry

#### Scenario: A decided segment holds no round count

- **WHEN** a segment is accepted after its one repair
- **THEN** its record says it was repaired and holds no count of rounds

#### Scenario: No rolling summary is written

- **WHEN** a run decides 200 segments
- **THEN** the project holds no rolling summary and no chapter summary

#### Scenario: No recurring-term document is written

- **WHEN** a run prepares and translates a book of 200 segments
- **THEN** the project holds a glossary document and no recurring-term document

#### Scenario: A glossary entry holds no rule mark and no meaning answer

- **WHEN** the names pass has read the character `Brightwater` and found no gender
- **THEN** the stored entry holds its kind, its meaning note `bright water` and a gender of unknown, and no field that says a gender rule was tried or how its meaning was answered

#### Scenario: An older database is set aside

- **WHEN** the application starts on a database whose stored shapes still hold recurring terms
- **THEN** the file is renamed `bookloom.db.old-<time>` and a fresh database is created

### Requirement: Set aside a database that cannot be used and start a fresh one

IF the stored database was made by another schema version or with other stored record shapes than this build uses, or cannot be read, THEN the system SHALL rename it to `bookloom.db.old-<yyyyMMdd-HHmmss>` beside the original, create a fresh database, write one warning line to the log, show no dialog, and raise one warning message, once, that names the renamed file and says that providers and settings were reset with the saved work.

**Source**: `design.md#d9--stored-shapes-that-change`. In plain words: before the first release the stored layout changes freely and no migrations are written. An old or damaged file is kept aside for diagnosis rather than deleted, and the application starts clean instead of failing on every launch. A fresh database counts as a first launch, so the first-launch look runs once more (the `llm-provider` capability). Each build step that changes a stored record (the glossary's entries, the lemma forms, the brief, a provider, a model's settings, a segment, a proofread verdict, the export record) changes the shape fingerprint, so the next launch starts clean; providers and settings go with the saved work and must be entered again. This change removes the chapter summary, the recurring-term list and several brief and glossary fields, so a database made by an earlier build is set aside at the first launch.

#### Scenario: A database from another schema version is put aside

- **WHEN** the application starts at 2026-10-20 09:15:02 and `bookloom.db` carries schema version 3 while this build uses version 4
- **THEN** the file is renamed `bookloom.db.old-20261020-091502`, a fresh `bookloom.db` is created, and the log gets one warning line
- **AND** no dialog is shown, the Projects screen is empty, and one warning message reads `Saved work could not be read` and names `bookloom.db.old-20261020-091502`

#### Scenario: A database with other stored record shapes is put aside

- **WHEN** the application starts at 2026-10-28 14:03:09 and `bookloom.db` holds the same schema number as this build but a glossary entry shape that this build no longer uses
- **THEN** the file is renamed `bookloom.db.old-20261028-140309`, a fresh `bookloom.db` is created, and the log gets one warning line
- **AND** the Projects screen is empty, the provider list is empty, and the one warning message names `bookloom.db.old-20261028-140309` and says that providers and settings were reset with the saved work

#### Scenario: A database of the same shapes is kept

- **WHEN** the application starts and `bookloom.db` was made by this build
- **THEN** it is opened as it is and nothing is renamed

#### Scenario: A file that is not a database is put aside

- **WHEN** `bookloom.db` holds 2 kilobytes of text that is not a database
- **THEN** it is renamed `bookloom.db.old-<yyyyMMdd-HHmmss>` with the time of that launch and a fresh database is created

### Requirement: Remove a project's saved work and nothing else

WHEN a person removes a project, the system SHALL delete that project's stored data — its brief, glossary, segment decisions with their proofread verdicts, translation memory, the stored prompt frames and run — and SHALL NOT delete the book file, any exported file, or any other project of the same book.

**Source**: `design.md#d9--stored-shapes-that-change`, `design.md#d7--the-recurring-term-list-is-removed-whole`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`. In plain words: Remove forgets the translation inside BookLoom; it never reaches out to files the person owns. A second translation of the same book is a different project and stays. A project holds one glossary and no chapter summaries any more, so neither a second term list nor a summary is named among what is removed.

#### Scenario: Removing one of two translations of a book

- **WHEN** the book `earth-gravity.epub` has the projects `Earth Gravity · EN → UK` and `Earth Gravity · EN → DE`, and the first is removed
- **THEN** its decisions, brief and glossary are gone and `Earth Gravity · EN → DE` is unchanged
- **AND** the book file `earth-gravity.epub` and a previously exported `earth-gravity.uk.epub` are untouched

#### Scenario: The database file gives back the removed project's space

- **WHEN** the only project, whose glossary takes most of a 3 MB `bookloom.db`, is removed
- **THEN** the file is rewritten without the freed pages (`VACUUM`, then the WAL is checkpointed), so the Storage tab's size falls to under a quarter; a rewrite another reader blocks only leaves the space, with one WARN

#### Scenario: Chapter summaries and verdicts go with the project

- **WHEN** the project `Earth Gravity · EN → UK` holds 3 proofread verdicts and 120 remembered passages and is removed
- **THEN** none of them remains, and the projects of other books keep theirs

### Requirement: Store each distinct fixed part of a run's prompts once per project

WHEN a run sends model calls, the system SHALL store each distinct fixed part of its prompts (the parts that stay the same across the run's calls) once per project, and store with each segment a key to the fixed part its call used and only the sections that differed for its call.

**Source**: `design.md#d12--what-a-translate-request-carries`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`, `design.md#d26--the-book-brief-has-no-language-specific-rows`. In plain words: the writing rules, the style sheet and the examples are the same in every call of a run, so keeping them with every segment would copy them thousands of times; they can change between runs (a brief edit, another model), so a project may hold several fixed parts, and each segment points at the one its call used. The desk's "What the model saw" joins that fixed part and the segment's own sections back together. No chapter summary is sent, so a segment's record names none; the example of a brief change is the register, because the brief no longer holds a quotation-mark choice.

#### Scenario: Two segments share one stored frame

- **WHEN** a run drafts `ch1 · p01` and `ch1 · p02` with the same writing rules and style sheet
- **THEN** the project holds one stored copy of those rules and style sheet, and each segment's record holds the key to it and its own window sections

#### Scenario: What the model saw is complete after a restart

- **WHEN** the application is restarted and the person opens "What the model saw" for `ch1 · p02`
- **THEN** it shows the stored frame together with that segment's own sections

#### Scenario: A segment with no chapter summary stores no key

- **WHEN** a call is made after three chapters were finished
- **THEN** its stored record holds the key to its fixed part and its own sections, and no chapter-summary key, because no chapter summary exists

#### Scenario: A brief changed between two runs gives two frames

- **WHEN** the first run drafts `ch1 · p01` with the register `Neutral`, the person changes the brief's register to `Casual`, and a second run drafts `ch9 · p01`
- **THEN** the project holds two stored frames; "What the model saw" for `ch1 · p01` shows the first and for `ch9 · p01` the second

### Requirement: Keep the per-model overrides, probe verdicts and measured seconds on the model's settings

The system SHALL keep, on each model's stored settings, the person's thinking and sampling overrides for that model, what the checks of that model found (whether the thinking switch works, whether structured answers are honoured, which output cap field is used), the seconds measured for each kind of call, and each setting the model refused together with the kind of call it refused it for.

**Source**: `design.md#d9--stored-shapes-that-change`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`. In plain words: what the person chose for a model, and what BookLoom learnt about it by testing, belongs to that model and is still there after a restart, so a model is not tested again for the same fact. The example of a call kind is a proofread call, because chapter-summary calls are gone.

#### Scenario: An override survives a restart

- **WHEN** the person sets thinking to "Always" for `qwen2.5:1.5b` and the application is closed and opened again
- **THEN** Settings › Models shows "Always" for `qwen2.5:1.5b`

#### Scenario: A refusal remembers its call kind

- **WHEN** `gpt-oss:20b` refused `temperature` for the proofread call and the application is restarted
- **THEN** the stored settings of `gpt-oss:20b` still hold `temperature` refused for the proofread call, and a draft call still sends it

#### Scenario: Probe verdicts survive a restart

- **WHEN** the inference test found that `qwen2.5:1.5b` ignores a structured-answer request, and the application is restarted
- **THEN** the stored settings still say structured answers are ignored, and the verdict names the test as its source

#### Scenario: Measured seconds survive a restart

- **WHEN** a run measured 3.2 seconds per draft call and 6.1 per proofread call for `gemma4:e4b`
- **THEN** after a restart the next estimate uses those figures for each kind of call

## REMOVED Requirements

### Requirement: Store each chapter summary as its own record

**Reason**: No chapter summary is written: the calls took 6.8 % of the traced run and no effect on the translated text could be measured (`research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`).

**Migration**: None for the person. The chapter-summary documents (rows of the project documents table) and their repository are deleted, and the schema file does not change; the database is set aside and started fresh by this change (ADR-0043).
