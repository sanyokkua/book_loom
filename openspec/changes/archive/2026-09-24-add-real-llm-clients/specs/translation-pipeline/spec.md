## MODIFIED Requirements

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

## ADDED Requirements

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
