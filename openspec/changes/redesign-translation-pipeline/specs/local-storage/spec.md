## ADDED Requirements

### Requirement: Store each distinct fixed part of a run's prompts once per project

WHEN a run sends model calls, the system SHALL store each distinct fixed part of its prompts (the parts that stay the same across the run's calls) once per project, and store with each segment a key to the fixed part its call used, only the sections that differed for its call, and the keys of the chapter summaries the call carried.

**Source**: `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#record-shapes`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`, `design.md#d26--stored-shapes-and-database-resets`. In plain words: the writing rules, the style sheet and the examples are the same in every call of a run, so keeping them with every segment would copy them thousands of times; they can change between runs (a brief edit, another model), so a project may hold several fixed parts, and each segment points at the one its call used. The desk's "What the model saw" joins that fixed part and the segment's own sections back together.

#### Scenario: Two segments share one stored frame

- **WHEN** a run drafts `ch1 · p01` and `ch1 · p02` with the same writing rules and style sheet
- **THEN** the project holds one stored copy of those rules and style sheet, and each segment's record holds the key to it, its own window sections and the keys of the chapter summaries its call carried

#### Scenario: What the model saw is complete after a restart

- **WHEN** the application is restarted and the person opens "What the model saw" for `ch1 · p02`
- **THEN** it shows the stored frame together with that segment's own sections

#### Scenario: A segment with no chapter summary stores no key

- **WHEN** a call was made before any chapter was finished
- **THEN** its stored record holds no chapter-summary key

#### Scenario: A brief changed between two runs gives two frames

- **WHEN** the first run drafts `ch1 · p01` with the quote style `«…»`, the person changes the brief's quote style to `„…“`, and a second run drafts `ch9 · p01`
- **THEN** the project holds two stored frames; "What the model saw" for `ch1 · p01` shows the first and for `ch9 · p01` the second

### Requirement: Store each chapter summary as its own record

WHEN a chapter's summary is written, the system SHALL store it as its own record for the project, holding the chapter, its first segment, its segment count, the text and a fingerprint of the chapter's source text, written in its own step.

**Source**: `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#record-shapes`, `design.md#d10--chapter-summaries-and-the-recently-present-card`, `design.md#d26--stored-shapes-and-database-resets`. In plain words: a summary is expensive to make (one model call), so it is saved the moment it exists and is never made twice; the fingerprint tells whether the chapter it describes has changed since.

#### Scenario: A summary survives a restart

- **WHEN** the summary of Chapter 2 (`Mara reaches the lighthouse and finds the keeper gone.` in the target language), starting at segment 41 with 38 segments, is written and the application is closed and opened again
- **THEN** the project still holds that summary with chapter 2, first segment 41, 38 segments and the same text

#### Scenario: A summary is saved before the next draft needs it

- **WHEN** the process is killed right after Chapter 2's summary call returns
- **THEN** after the next launch the stored summary of Chapter 2 is present and is not requested again

#### Scenario: A chapter without a summary has no record

- **WHEN** the summary call of Chapter 3 failed
- **THEN** the project holds no record for Chapter 3

### Requirement: Store a proofread verdict with each segment the stage read

WHEN the Proofread stage decides a segment, the system SHALL store with the segment a proofread verdict holding the window's fingerprint, the verdict (kept, proposed, rejected or unchanged), the reason and, for a kept rewrite, the text it replaced and the status the segment had before it.

**Source**: `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#record-shapes`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`, `design.md#d26--stored-shapes-and-database-resets`. In plain words: the verdict is the stage's receipt for a segment it read (a proofread mark, by contrast, is the note that a segment still has to be read). It lets a restart skip work already done, lets the desk show what changed and why, and lets Undo proofread put the earlier text and status back.

#### Scenario: A kept rewrite stores the text it replaced

- **WHEN** the Proofread stage keeps `Тарн прийде завтра.` in place of `Тарн прийшов завтра.` on an ACCEPTED segment and the application is restarted
- **THEN** the segment holds the text `Тарн прийде завтра.`, the verdict kept, the reason, the fingerprint, `Тарн прийшов завтра.` as the text it replaced and ACCEPTED as the status before

#### Scenario: An unchanged verdict stores no earlier text

- **WHEN** the model answers unchanged for a segment
- **THEN** its verdict holds unchanged and no replaced text

#### Scenario: Removing the project removes the verdicts

- **WHEN** the project is removed
- **THEN** no proofread verdict of it remains

### Requirement: Keep the per-model overrides, probe verdicts and measured seconds on the model's settings

The system SHALL keep, on each model's stored settings, the person's thinking and sampling overrides for that model, what the checks of that model found (whether the thinking switch works, whether structured answers are honoured, which output cap field is used), the seconds measured for each kind of call, and each setting the model refused together with the kind of call it refused it for.

**Source**: `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#record-shapes`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`, `design.md#d26--stored-shapes-and-database-resets`. In plain words: what the person chose for a model, and what BookLoom learnt about it by testing, belongs to that model and is still there after a restart, so a model is not tested again for the same fact.

#### Scenario: An override survives a restart

- **WHEN** the person sets thinking to "Always" for `qwen2.5:1.5b` and the application is closed and opened again
- **THEN** Settings › Models shows "Always" for `qwen2.5:1.5b`

#### Scenario: A refusal remembers its call kind

- **WHEN** `gpt-oss:20b` refused `temperature` for the chapter-summary call and the application is restarted
- **THEN** the stored settings of `gpt-oss:20b` still hold `temperature` refused for the chapter-summary call, and a draft call still sends it

#### Scenario: Probe verdicts survive a restart

- **WHEN** the inference test found that `qwen2.5:1.5b` ignores a structured-answer request, and the application is restarted
- **THEN** the stored settings still say structured answers are ignored, and the verdict names the test as its source

#### Scenario: Measured seconds survive a restart

- **WHEN** a run measured 3.2 seconds per draft call and 6.1 per proofread call for `gemma4:e4b`
- **THEN** after a restart the next estimate uses those figures for each kind of call

### Requirement: Never store what a server says about a model

The system SHALL NOT store facts read from a server about a model (its context length, its capabilities, its size, its loaded state, its file name); it SHALL read them again at each check.

**Source**: `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#record-shapes`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`, `design.md#d26--stored-shapes-and-database-resets`. In plain words: a server's answer can be wrong tomorrow, since the person may reload the model with a larger window or swap the file under the same name; keeping yesterday's answer would hide that, so only what the person chose and what BookLoom's own tests found is kept.

#### Scenario: A reloaded model shows its new window

- **WHEN** `gemma4:e4b` was reported by Ollama with a 4096-token window, the application is restarted, and the model is then reloaded with 16384
- **THEN** the next check shows 16384 and the stored settings never held 4096

#### Scenario: The database holds no server answer

- **WHEN** a provider's models have been listed and their details read
- **THEN** the database holds no row with those details

### Requirement: Keep no setting or record for a behaviour that is gone

The system SHALL keep no global Thinking or Sampling setting, no last-check setting, no per-segment count of repair rounds and no rolling summary, and SHALL read none of them if an older file still held them.

**Source**: `design.md#d26--stored-shapes-and-database-resets`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: the per-model profile and overrides replace the global switches; the export no longer runs a last check; there is one repair, so a count of rounds says nothing; and chapter summaries replace the rolling summary. Nothing stores them, and the database an older build made is set aside rather than read, as the requirement on setting aside a database says.

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
- **THEN** the project holds chapter summaries for the finished chapters and no rolling summary

## MODIFIED Requirements

### Requirement: Keep the application's work across a restart

The system SHALL keep projects, books, briefs, the Glossary, the Recurring terms, segment decisions with their proofread verdicts, the translation memory, chapter summaries, the fixed frames of the runs' prompts, run state, providers, per-model settings with the person's overrides and preferences on this computer, so that each is present again after the application is closed and opened.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`, ADR-0043, `design.md#d26--stored-shapes-and-database-resets`. In plain words: closing BookLoom no longer throws the translation away. Everything a person built up (the book, the names they fixed, the settings they chose, the segments already translated) is still there on the next launch.

#### Scenario: A translation and its names survive a restart

- **WHEN** a project is created for a 142-segment book, the Glossary gains the name `Mira Holt`, 74 segments are decided, and the application is closed and opened again
- **THEN** the project is listed with 74 of 142 segments decided and its Glossary still holds `Mira Holt`

#### Scenario: Preferences and providers survive a restart

- **WHEN** the dark theme is chosen, a provider named `Home Ollama` is saved, and the application is closed and opened again
- **THEN** the dark theme is in force and `Home Ollama` is listed among the providers

#### Scenario: The translation memory and the summary survive a restart

- **WHEN** the decided segment `Tarn walked to Velk.` → `Тарн пішов до Велька.` is in the translation memory, the summary of Chapter 1 reads `Тарн приходить до Велька й зустрічає вартового.`, and the application is closed and opened again
- **THEN** a later segment with the same source text `Tarn walked to Velk.` reuses `Тарн пішов до Велька.` without a model call, and the summary of Chapter 1 still reads `Тарн приходить до Велька й зустрічає вартового.`

#### Scenario: Proofread verdicts survive a restart

- **WHEN** 3 segments hold proofread verdicts and the application is closed and opened again
- **THEN** the 3 segments still hold their verdicts

#### Scenario: Per-model settings survive a restart

- **WHEN** `gemma4:e4b` is given the context window `16384` and the temperature choice My value `0.5`, `gpt-oss:20b` has refused `temperature`, and the application is closed and opened again
- **THEN** Settings › Models shows `16384` and My value `0.5` for `gemma4:e4b`, and `temperature` under "Settings this model refused" for `gpt-oss:20b`

### Requirement: Write a chunk's decisions together

WHEN a chunk is decided, the system SHALL write its segment decisions, translation memory entries, deferrals, new Glossary names and run record in one step; WHEN a Proofread window is decided, it SHALL write the window's segment texts, proofread verdicts and the deferrals the window resolved in one step; a crash or a forced quit loses at most the decisions of the chunk or window in flight.

**Source**: FR-PERSIST-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-persist`), ADR-0043, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a half-written chunk would leave a segment saved without the memory entry that goes with it. Writing them as one unit means the saved state is always one that makes sense. The guarantee is about the application dying; an operating-system crash or power cut may still lose the last chunk, and the application does not claim otherwise. A proofread window is the same kind of unit: a rewritten text saved without its verdict, or a deferral marked resolved while its text is unchanged, would leave the stage unable to tell what it had done.

#### Scenario: A forced quit loses only the chunk in flight

- **WHEN** chunks 1 to 6 are decided, chunk 7 is being translated, and the process is killed
- **THEN** after the next launch the segments of chunks 1 to 6 are decided and the segments of chunk 7 are pending

#### Scenario: A chunk is never half saved

- **WHEN** the process is killed while chunk 7's decisions are being written
- **THEN** after the next launch either all of chunk 7's decisions and memory entries are present or none of them is

#### Scenario: A forced quit loses only the window in flight

- **WHEN** Proofread windows 1 to 5 are decided, window 6 is being read, and the process is killed
- **THEN** after the next launch the texts and verdicts of windows 1 to 5 are present and window 6's segments hold their earlier text and no new verdict

#### Scenario: A window is never half saved

- **WHEN** the process is killed while window 6 is being written, window 6 rewriting `ch2 · p08` and resolving the deferral of the gender of `Велемир`
- **THEN** after the next launch either the rewritten text, its verdict and the resolved deferral are all present or none of them is

### Requirement: Set aside a database that cannot be used and start a fresh one

IF the stored database was made by another schema version or with other stored record shapes than this build uses, or cannot be read, THEN the system SHALL rename it to `bookloom.db.old-<yyyyMMdd-HHmmss>` beside the original, create a fresh database, write one warning line to the log, show no dialog, and raise one warning message, once, that names the renamed file and says that providers and settings were reset with the saved work.

**Source**: ADR-0043, `design.md#d26--stored-shapes-and-database-resets`, `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#corruption-and-recovery`. In plain words: before the first release the stored layout changes freely and no migrations are written. An old or damaged file is kept aside for diagnosis rather than deleted, and the application starts clean instead of failing on every launch. A fresh database counts as a first launch, so the first-launch look runs once more (the `llm-provider` capability). Each build step of the pipeline redesign that changes a stored record (the glossary's entries, the lemma forms, the brief, a provider, a model's settings, a segment, a chapter summary, a proofread verdict, the export record) changes the shape fingerprint, so the next launch starts clean; providers and settings go with the saved work and must be entered again.

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

WHEN a person removes a project, the system SHALL delete that project's stored data — its brief, term lists, segment decisions with their proofread verdicts, translation memory, chapter summaries, the stored prompt frames and run — and SHALL NOT delete the book file, any exported file, or any other project of the same book.

**Source**: `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (Persistence), `design.md#d26--stored-shapes-and-database-resets`, `docs/specification/mockups/README.md#projects-new-screen--g6-p-pn-d`. In plain words: Remove forgets the translation inside BookLoom; it never reaches out to files the person owns. A second translation of the same book is a different project and stays.

#### Scenario: Removing one of two translations of a book

- **WHEN** the book `earth-gravity.epub` has the projects `Earth Gravity · EN → UK` and `Earth Gravity · EN → DE`, and the first is removed
- **THEN** its decisions, brief and term lists are gone and `Earth Gravity · EN → DE` is unchanged
- **AND** the book file `earth-gravity.epub` and a previously exported `earth-gravity.uk.epub` are untouched

#### Scenario: The database file gives back the removed project's space

- **WHEN** the only project, whose glossary takes most of a 3 MB `bookloom.db`, is removed
- **THEN** the file is rewritten without the freed pages (`VACUUM`, then the WAL is checkpointed), so the Storage tab's size falls to under a quarter; a rewrite another reader blocks only leaves the space, with one WARN

#### Scenario: Chapter summaries and verdicts go with the project

- **WHEN** the project `Earth Gravity · EN → UK` holds 4 chapter summaries and 3 proofread verdicts and is removed
- **THEN** none of them remains, and the projects of other books keep theirs
