# translation-pipeline Specification

## Purpose
Translates one book segment by segment through a chat model the caller supplies. It builds each prompt, decides each
segment's status, reports progress, returns a report and writes a diagnostic log of the run. The command line is its
first entry point.

## Requirements

### Requirement: Send each pending segment to the model in document order

WHEN a job runs, the system SHALL send each PENDING segment to the chat model in document order, one call per segment,
as the catalog's draft-translation prompt:

- a system message that names the source and target languages as English display names plus their raw BCP-47 tags
  (for example, `English (en)` and `Ukrainian (uk)`), carries a neutral default style sheet, and states the
  rules: keep every `⟦gN⟧` token exactly as written, same text, order and count; keep a passage deliberately in
  another language as it is; output only the required JSON object, with no commentary, fences or reasoning;
- a user message that omits unavailable context entirely, optionally precedes the source with the last three accepted
  targets from the same section, repeats this source's exact ordered `⟦gN⟧` sequence immediately before the source,
  renders the masked source verbatim inside `<Text>…</Text>`, and requests only `{"target":"<translation>"}`.

Each call SHALL carry the temperature `0.2` and a response format whose schema requires exactly an object with a
nonblank string `target` and no additional properties. The system message SHALL contain language-neutral structural
few-shots for paired ranges, multiple paired ranges, standalone protected content, and the final JSON shape; it SHALL
say that the few-shots teach token placement only, never their literal text.

The source language SHALL be the one the request gives, or else the one the book declares. Each available BCP-47 tag
SHALL be rendered for the model as its English `Locale` display name followed by the exact tag; a tag without a
display name SHALL render as `language tag "<tag>"`. When neither source is known, the prompt SHALL call it `the
language of this segment (infer it from its text)`.

**Source:** FR-ALGO-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), FR-DOC-03 (`#fr-doc`),
`01_Product/12_PROMPT_CATALOG.md#draft-translation`, `#output-contract`, `02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`.
In plain words: the model sees only text with numbered holes where the markup was, never the book's structure, and is
told the languages, the one rule that matters most, and the exact JSON shape to answer with. One segment per call is
the simplest loop that works; chunking and context fill the empty slots later. A language the caller names wins,
because a book's own declaration is often missing or wrong.

#### Scenario: The user message is the masked text

- **WHEN** a Markdown book `Book.md` whose only paragraph is `He opened the *old* door.` is translated from `en` to
  `uk`
- **THEN** the model receives one call whose user message contains `<Text>` around
  `He opened the ⟦g0⟧old⟦g1⟧ door.`, repeats `⟦g0⟧ ⟦g1⟧` immediately before it, and contains no source id,
  source JSON, empty context block, or batch wording
- **AND** its system message names `English (en)` and `Ukrainian (uk)` and the rule about `⟦gN⟧` tokens
- **AND** the call carries the temperature `0.2` and a response format

#### Scenario: No source language is named when none is known

- **WHEN** a Markdown book with no front matter is translated to `uk` and no source language is given
- **THEN** the system message says `from the language of this segment (infer it from its text) into Ukrainian (uk)`

#### Scenario: The requested source language wins over the book's

- **WHEN** a Markdown book whose front matter declares `lang: en` is translated to `uk` with the source language `de`
- **THEN** the system message names `German (de)` as the source language and does not name `English (en)`

#### Scenario: A variant tag stays precise for the model

- **WHEN** a book is translated from `zh-Hant` to `uk`
- **THEN** the system message says `from Chinese (Traditional) (zh-Hant) into Ukrainian (uk)`

### Requirement: Accept a translation whose markup restores

WHEN the model replies with a normal finish and the translation read from the reply is not empty after trimming, the
system SHALL trim it and put back the leading and trailing whitespace of the segment's masked text. It SHALL then
restore the segment's markup through the placeholder gate and Markdown restoration validation, and mark the segment
ACCEPTED with the restored translation.

**Source:** FR-DOC-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `#segment-status-machine`.
In plain words: models often add or drop spaces and line breaks around their answer, and the book's own spacing wins.
A reply is accepted only when every token comes back and its restored Markdown remains structurally meaningful, so its
markup can be put back where it was.

#### Scenario: A reply is restored and accepted

- **WHEN** a segment `Book.md:0` has the masked text `He opened the ⟦g0⟧old⟦g1⟧ door.` and the model replies
  `{"target":"Він відчинив ⟦g0⟧старі⟦g1⟧ двері."}`
- **THEN** the segment is ACCEPTED with the translation `Він відчинив *старі* двері.`

#### Scenario: The segment's own whitespace wins

- **WHEN** a segment's masked text is two spaces, `Hello world` and a line feed, and the translation read from the
  reply is a line feed, `HELLO WORLD` and two spaces
- **THEN** the translation is two spaces, `HELLO WORLD` and a line feed

#### Scenario: A Markdown formatting failure receives one repair

- **WHEN** a Markdown reply returns every placeholder token but restores a paired range around only whitespace or
  moves a task-list marker away from the start of its list item
- **THEN** the system makes one formatting repair that repeats the rejected target and exact token sequence, explains
  the placement rule, and accepts only a structurally valid corrected target

### Requirement: Flag a segment the model could not translate, and continue

IF any of the following happens, THEN the system SHALL mark the segment FLAGGED with that error as its reason, keep no
translation for it, and continue with the next segment:

- restoring the reply's markup is refused with `ErrorCode.validation`, for example because a `⟦gN⟧` token is missing;
- the reply is empty or holds only whitespace (`ErrorCode.emptyCompletion`), whatever its finish;
- the reply's finish is not normal (`ErrorCode.validation`);
- the model answers `ErrorCode.validation`, `ErrorCode.emptyCompletion` or `ErrorCode.contextWindow`.

**Source:** FR-DOC-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`02_Architecture/03_DOCUMENT_MODEL.md#segment-status-machine`, `02_Architecture/09_ERROR_HANDLING.md#partial-results`.
In plain words: one bad segment must not stop a book. A flagged segment is exported with its source text, so a reader
sees the original rather than broken markup, and the report says why. A reply of only spaces counts as empty: accepting
it would silently delete the paragraph.

#### Scenario: A missing token flags the segment and the job goes on

- **WHEN** a Markdown book has the paragraphs `He opened the *old* door.` and `She left.`, and the model replies
  `HE OPENED THE ⟦g0⟧OLD DOOR.` to the first
- **THEN** the first segment is FLAGGED with `ErrorCode.validation`
- **AND** the second segment is still sent to the model and ACCEPTED

#### Scenario: A reply of only whitespace is flagged as empty

- **WHEN** a TXT book has the paragraphs `One.` and `Two.`, and the model replies to `One.` with two spaces and a line
  feed and a normal finish
- **THEN** the first segment is FLAGGED with `ErrorCode.emptyCompletion` and keeps no translation
- **AND** `Two.` is still sent to the model

#### Scenario: A reply cut off by length is flagged

- **WHEN** the model replies `HE OPENED` with a finish of cut off by length
- **THEN** the segment is FLAGGED with `ErrorCode.validation` and the next segment is sent

#### Scenario: A context-window error is flagged

- **WHEN** the model answers `ErrorCode.contextWindow` for a segment
- **THEN** that segment is FLAGGED with `ErrorCode.contextWindow` and the next segment is sent

### Requirement: Stop the job on any other failure

IF pause on error is off and one of the following happens, THEN the system SHALL end the job Failed with that error
(`ErrorCode.internal` for a thrown exception), leave that segment and every later one PENDING, and write nothing:

- the model answers any other error;
- the model call throws;
- restoring the markup fails with an error other than `ErrorCode.validation`.

IF the model answers `ErrorCode.cancelled`, THEN the system SHALL end the job Cancelled and write nothing.

**Source:** `02_Architecture/09_ERROR_HANDLING.md#partial-results`, `#boundary-discipline`,
`01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`.
In plain words: an unreachable server or a bug would make every following call fail too, so the job stops instead of
flagging the rest of the book. What was already decided stays in the report.

#### Scenario: An unreachable model fails the job

- **WHEN** a book has three segments and the model answers `ErrorCode.unreachable` for the second
- **THEN** the job ends Failed with `ErrorCode.unreachable`, with 1 accepted and 2 pending segments
- **AND** no file is written

#### Scenario: A thrown exception fails the job as internal

- **WHEN** the model call throws an exception for the first segment
- **THEN** the job ends Failed with `ErrorCode.internal`

#### Scenario: A restore that fails unexpectedly fails the job

- **WHEN** restoring the markup of the first segment's reply fails with `ErrorCode.internal`
- **THEN** the job ends Failed with `ErrorCode.internal`, and that segment stays PENDING

#### Scenario: A cancelled model call cancels the job

- **WHEN** the model answers `ErrorCode.cancelled`
- **THEN** the job ends Cancelled and no file is written

### Requirement: Report progress and the outcome

WHILE a job runs, the system SHALL notify every subscriber, in order, when:

- a stage starts;
- a segment is decided, with the accepted, flagged and pending counts;
- the job pauses, with its reason, or resumes;
- the job finishes.

WHEN a job ends, the system SHALL return a successful result carrying:

- the book format;
- how the job ended: Completed, Cancelled or Failed;
- the number of segments, and the accepted and flagged counts;
- each flagged segment with its reason;
- the written file when Completed, and the error when Failed.

**Source:** `02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`, `02_Architecture/09_ERROR_HANDLING.md#partial-results`.
In plain words: a screen can follow a job live, and a job that stopped still tells its caller exactly what was done and
why it stopped. A caller that needs no live updates, like the command line, subscribes to nothing.

#### Scenario: Events for a three-segment book

- **WHEN** a book with three segments is translated with no pause points and every reply is accepted
- **THEN** a subscriber receives, in order:
  1. translation stage started;
  2. three segment-decided events, with pending counts 2, 1 and 0;
  3. export stage started;
  4. finished as Completed.

#### Scenario: A failed job still returns its report

- **WHEN** a three-segment job ends Failed because the model answered `ErrorCode.unreachable` for the second segment
- **THEN** the result is successful
- **AND** its report says Failed with `ErrorCode.unreachable`, 3 segments, 1 accepted, 0 flagged and no written file

### Requirement: Refuse a job that cannot start

IF any of the following holds, THEN the system SHALL return that failure without calling the model and without writing
anything. The failure is `ErrorCode.validation`, or whatever error opening the book returned.

- the target language, or a source language the request gives, is not a language code: two or three letters,
  optionally followed by subtags of two to eight letters or digits, each after a hyphen, such as `uk`, `en-US` or
  `zh-Hant`;
- the destination's file type differs from the source's; a zipped FB2 book (`.fb2.zip`) and a plain one (`.fb2`) are
  different file types, while `.md` and `.markdown` are both Markdown;
- the destination is the source file;
- the destination already exists and overwrite is off;
- the source book cannot be opened;
- the job has already run.

**Source:** FR-EXPORT-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`),
`02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`.
In plain words: a job that cannot finish should fail before it costs a single model call. The target language becomes
part of the output file name, so a value like `../x` must never get that far, and a translation must never be written
over the book it was made from. An FB2 book is written back in the container it came in, so a zipped book given a
plain name would fail its own re-open check only after the whole book was translated.

#### Scenario: A path-like language code is refused

- **WHEN** a job is requested with the target language `../x`
- **THEN** the result is `ErrorCode.validation` and the model is never called

#### Scenario: A zipped FB2 book is not written under a plain FB2 name

- **WHEN** a job from `Book.fb2.zip` to `Book.uk.fb2` is requested
- **THEN** the result is `ErrorCode.validation` and the model is never called

#### Scenario: A destination that is the source is refused

- **WHEN** a job from `Book.md` to `Book.md` is requested with overwrite on
- **THEN** the result is `ErrorCode.validation` and the model is never called
- **AND** `Book.md` is unchanged

#### Scenario: An existing destination is kept when overwrite is off

- **WHEN** `Book.uk.md` already exists and a job from `Book.md` to `Book.uk.md` runs with overwrite off
- **THEN** the result is `ErrorCode.validation` and the model is never called
- **AND** `Book.uk.md` is unchanged

#### Scenario: A job runs only once

- **WHEN** a job that has already finished is run again
- **THEN** the result is `ErrorCode.validation`

### Requirement: Translate one book from the command line

WHEN the translate command runs as `./gradlew :app:translate --args="<book> [--to <lang>] [--from <lang>]
[--overwrite] [--provider pseudo|ollama|lmstudio|openai-compatible] [--model <id>] [--base-url <url>]
[--timeout <seconds>]"` for a supported book, the system SHALL:

- take `--provider` as `pseudo` (the default), `ollama`, `lmstudio` or `openai-compatible`; `--model` is required
  for every provider but `pseudo`; `--base-url` is required for `openai-compatible` and overrides the preset for
  `ollama` and `lmstudio`;
- register, for this run only, a provider description built from the flags: `--base-url` sets the base URL and
  `--timeout` sets the request timeout in seconds; no other setting exists;
- for a provider other than `pseudo`, run the preflight and print one line per stage that ran, in the form
  `<stage>: ok`, `<stage>: ok (<note>)`, `<stage>: skipped` or `<stage>: failed - <title>`;
- translate the book with no pause points;
- write `<name>.<to>.<ext>` beside it, with `--to` defaulting to `uk`;
- print one report line naming the output and the accepted and flagged counts;
- send diagnostics only to the log file, and open a connection only to the chosen provider;
- exit with code 0 when the job completed.

**Source:** FR-INFER-01, FR-INFER-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/01_SYSTEM_ARCHITECTURE.md#fx-free-core`, `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`.
In plain words: the whole pipeline can run on real books with a real model before any screen exists, and the same
command still runs offline with the pseudo model when no provider is named. The stage lines tell a person, before any
segment is sent, whether the server and the model are really there.

#### Scenario: A Markdown book is translated beside itself

- **WHEN** the command runs for `Book.md`, whose only paragraph is `He opened the *old* door.`, with no `--provider`
- **THEN** `Book.uk.md` appears beside it with the paragraph `HE OPENED THE *OLD* DOOR.`
- **AND** the command prints exactly one line, opens no connection, and exits with code 0

#### Scenario: A Markdown book is translated by a local Ollama model

- **WHEN** the command runs with `Book.md --provider ollama --model gemma4:e4b-mlx`, the server lists
  `gemma4:e4b-mlx`, and it answers each segment with a JSON reply
- **THEN** the command prints `connection: ok`, `models: ok` and `inference: skipped`, then the report line
- **AND** `Book.uk.md` holds the model's translation with `*old*` restored around the translated word
- **AND** it exits with code 0

#### Scenario: A custom OpenAI-compatible server by URL

- **WHEN** the command runs with `Book.md --provider openai-compatible --base-url http://localhost:8080/v1 --model qwen3`
- **THEN** every request goes to `http://localhost:8080/v1` and none carries an `Authorization` header

#### Scenario: A zipped FB2 book keeps its double extension

- **WHEN** the command runs for `Book.fb2.zip`
- **THEN** the output file is `Book.uk.fb2.zip`

### Requirement: Report command-line failures with exit codes

IF the arguments are invalid, THEN the translate command SHALL print the reason, then its usage, and exit with code 2
without writing anything and without opening a connection. Invalid arguments are:

- an unknown option;
- no book path, or a path that does not exist;
- a folder, or an unsupported file type;
- a `--to` or `--from` value that is not a language code;
- a `--provider` value other than `pseudo`, `ollama`, `lmstudio` or `openai-compatible`;
- a provider other than `pseudo` without `--model`, or `openai-compatible` without `--base-url`;
- `--timeout` that is not a positive whole number.

IF a preflight stage fails, the book cannot be opened, the job does not complete, the output exists and `--overwrite`
is absent, or BookLoom is already running, THEN the translate command SHALL print the error's title and message and
exit with code 1, leaving any existing output file unchanged. The error's technical details SHALL go only to the log
file.

**Source:** `02_Architecture/09_ERROR_HANDLING.md#app-error`, `#ui-surfacing`, ADR-0022
(`docs/adr/ADR-0022-busy-error-code-covers-the-single-instance-lock.md`).
In plain words: a script can tell a usage mistake (2) from a book or a server that did not make it (1), and a person
reads the same short title and message a screen would show. A missing model name or a mistyped provider is caught
before a single byte is sent. The command line and the desktop app share one data folder and its lock, so they never
run at the same time.

#### Scenario: An unknown option

- **WHEN** the command runs with `Book.md --bogus`
- **THEN** it prints `Invalid command arguments: unknown option` and then its usage
- **AND** it exits with code 2 and writes no file

#### Scenario: A book path that does not exist

- **WHEN** the command runs with `Missing.md`, which does not exist
- **THEN** it exits with code 2 and writes no file

#### Scenario: A folder is not a book

- **WHEN** the command runs with the path of a folder
- **THEN** it exits with code 2 and writes no file

#### Scenario: A real provider without a model

- **WHEN** the command runs with `Book.md --provider ollama`
- **THEN** it prints `Invalid command arguments: --model is required for provider ollama` and then its usage
- **AND** it exits with code 2 and opens no connection

#### Scenario: An unknown provider

- **WHEN** the command runs with `Book.md --provider gemini --model gemini-2.5-flash`
- **THEN** it exits with code 2 and opens no connection

#### Scenario: A server that is not running

- **WHEN** the command runs with `Book.md --provider ollama --model gemma4:e4b-mlx` and nothing listens on
  `localhost:11434`
- **THEN** it prints `connection: failed - ` followed by the error's title, then the error's message
- **AND** it exits with code 1 and writes no file

#### Scenario: A model that is not installed

- **WHEN** the server lists only `llama3.2:3b` and the command runs with `--provider ollama --model gemma4:e4b-mlx`
- **THEN** it prints `connection: ok` and `models: failed - ` followed by the error's title
- **AND** it exits with code 1 and writes no file

#### Scenario: A book that cannot be opened

- **WHEN** the command runs for `Book.epub`, whose bytes are not a ZIP archive
- **THEN** it prints the error's title and message, exits with code 1 and writes no file

#### Scenario: The output exists and overwrite is not given

- **WHEN** `Book.uk.md` exists and the command runs for `Book.md` without `--overwrite`
- **THEN** it exits with code 1 and `Book.uk.md` is unchanged

#### Scenario: BookLoom is already running

- **WHEN** another BookLoom process holds the single-instance lock
- **THEN** the command exits with code 1 without translating anything

### Requirement: Write a diagnostic log of every run

The system SHALL write a diagnostic log of every command run and every job to its local log file, at these levels:

- INFO: the start and end of each command run and each job, with the book's format, the source and destination files,
  the languages, how the job ended and its counts;
- DEBUG: every check a request passes or fails, every stage change, pause, resume and cancellation, and every segment
  decision with the segment id, its status and its error code;
- TRACE: for each segment, the messages sent to the model, the model's reply and the restored translation;
- WARN: every flagged segment, a job that ends Failed, and every degraded step, such as a pause on error;
- ERROR: an unexpected fault, once, with its cause.

The system SHALL write book text only in TRACE lines, and SHALL never write a secret or a credential at any level.

**Source:** DD-23 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-23-slf4j-logback`), NFR-PRIV-04
(`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#no-telemetry`), NFR-PRIV-06 and NFR-PRIV-07 (`#secrets-never-stored`).
In plain words: the log is how a wrong book gets explained: which segment, what the model was asked, what it answered,
and which rule decided. It stays on the machine. Book text is written only at the most detailed level, which is off
unless someone turns it on, and credentials are never written at all.

#### Scenario: A TRACE run follows a segment from prompt to decision

- **WHEN** the command runs for `Book.md`, whose only paragraph is `He opened the *old* door.`, at the log level
  `TRACE`
- **THEN** `bookloom.log` contains an INFO line for the job's start naming `MARKDOWN` and an INFO line for its end
  naming `COMPLETED`
- **AND** it contains a DEBUG line naming the segment `Book.md:0` and `ACCEPTED`
- **AND** it contains TRACE lines holding `He opened the ⟦g0⟧old⟦g1⟧ door.` and `HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.`

#### Scenario: An INFO run keeps book text out of the log

- **WHEN** the same command runs at the log level `INFO`
- **THEN** `bookloom.log` contains the job's start and end lines
- **AND** it contains no DEBUG or TRACE line and no `He opened`

### Requirement: Choose the log level for a run

WHERE the environment variable `BOOKLOOM_LOG_LEVEL`, or else the system property `bookloom.log.level`, names `TRACE`,
`DEBUG`, `INFO`, `WARN` or `ERROR` in any letter case, the system SHALL log its own components at that level, in the
desktop app and on the command line alike, and SHALL log other libraries at `WARN`.

WHEN neither names a level, the system SHALL log its own components at `INFO` in an installed app and at `DEBUG` in a
development run.

IF the value names no level, THEN the system SHALL use that default and write one WARN line naming the rejected value.

**Source:** FR-SETTINGS-06 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-settings`),
`01_Product/07_SETTINGS.md#storage-and-logs-tab`, DD-23 (`00_Foundation/04_DESIGN_DECISIONS.md#dd-23-slf4j-logback`).
In plain words: detailed lines are only useful if they can be switched on without rebuilding the app. The Settings
screen will offer the same choice later; until then an environment variable does it. A typo must not silently leave the
level where it was, and third-party libraries must not drown BookLoom's own lines.

#### Scenario: A lowercase level is accepted

- **WHEN** a run starts with `BOOKLOOM_LOG_LEVEL` set to `trace`
- **THEN** TRACE lines are written to `bookloom.log`

#### Scenario: An unknown level falls back to the default

- **WHEN** an installed app starts with `BOOKLOOM_LOG_LEVEL` set to `LOUD`
- **THEN** its components log at `INFO`
- **AND** `bookloom.log` contains one WARN line holding `LOUD`

#### Scenario: A development run logs at DEBUG by default

- **WHEN** a development run starts with no log level set
- **THEN** DEBUG lines are written to `bookloom.log` and TRACE lines are not

### Requirement: Read the translation out of the reply

WHEN a reply arrives for a segment, the system SHALL accept it only when it is one complete JSON object with exactly
one nonblank string property, `target`. Prose, embedded JSON, maps, arrays, extra fields, malformed JSON, and a blank
target SHALL be invalid structured output. No candidate reaches the document unmasker before this check passes.

**Source:** FR-INFER-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`01_Product/12_PROMPT_CATALOG.md#output-contract`, `#draft-translation`, ADR-0013.
In plain words: a provider schema constrains the envelope, while strict parsing prevents an accidental wrapper or
fallback from becoming book text. The placeholder gate remains the authority for the `target` string itself.

#### Scenario: The exact shape

- **WHEN** the reply is `{"target":"Привіт"}` for segment `Book.md:0`
- **THEN** the translation is `Привіт`

#### Scenario: A nonexact shape is rejected

- **WHEN** the reply is prose, `{"Book.md:0":"Привіт"}`, `{"target":"Привіт","note":"x"}`, or `{"target":""}`
- **THEN** it is invalid structured output and reaches neither unmasking nor an implicit text fallback

### Requirement: Repair invalid structured draft replies once

IF a reply is malformed or wrong-shaped, THEN the system SHALL issue exactly one structural repair for the same
segment. That repair SHALL use the same response format, include the delimited rejected reply and a parsing diagnosis,
and ask for only the exact JSON object. IF it is again invalid, THEN the segment SHALL be FLAGGED with
`ErrorCode.validation` and no wrapper text reaches the document unmasker.

IF a strictly valid `target` fails placeholder validation, THEN the system SHALL issue exactly one separate repair
containing the original source, rejected target, and exact required ordered token sequence. That repair SHALL again
pass strict parsing and the unchanged placeholder hard gate; a second failure is FLAGGED, with no retry loop.

**Source:** FR-INFER-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`01_Product/12_PROMPT_CATALOG.md#output-contract`, ADR-0013.

#### Scenario: A structural repair receives diagnostic data

- **WHEN** a reply begins `Here is the translation: {"target":"Привіт"}`
- **THEN** the repair includes that rejected reply and reports that the reply was not valid JSON

#### Scenario: A second invalid structured reply is flagged

- **WHEN** the original reply and its one repair reply are invalid structured output
- **THEN** the segment is FLAGGED with `ErrorCode.validation` and the model is called exactly twice

#### Scenario: A placeholder repair names the required sequence

- **WHEN** `{"target":"Привіт ⟦g0⟧"}` is valid JSON but a segment requires `⟦g0⟧ ⟦g1⟧`
- **THEN** the one repair contains the original source, rejected target, and `⟦g0⟧ ⟦g1⟧`; no unmask succeeds until a
  strict repaired target has that sequence
