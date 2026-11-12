# settings Specification

## Purpose
How a person chooses what actually translates their book, and how they see whether that choice will work: the
local providers the application knows from first launch, checking one of them stage by stage so a failure names
its own cause, choosing a model from what the server offers or naming one it does not list, and choosing how the
interface looks.

## Requirements

### Requirement: Check a provider in three stages and report each one

The application SHALL offer Test connection, Test models and Test inference on the provider being edited, each running on the form as it stands, saved or not, and each running its own stage and every stage before it; Test models SHALL run without a chosen model (then it only counts the listed models) and only Test inference SHALL wait for one. A failed connection SHALL read "does not answer" only when nothing answered: a missing key reads "the key is not set", a refused sign-in (401/403) "answered, but refused the sign-in", and any other answered error "answered, but the check did not pass", each with the reason under it.

**Source**: FR-PROV-06, FR-PROV-07 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), S2, S3 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#providers`. In plain words: a person can test a new provider before saving it, and the answer is a sentence they can act on, not a code. A server with no listing endpoint is a normal, working configuration, so an unreadable listing is a qualified pass, not a failure.

#### Scenario: The check is unavailable until a model is chosen

- **WHEN** the Ollama provider is being edited and no model has been chosen
- **THEN** Test connection and Test models are available and Test inference is present and unavailable
- **WHEN** Test models is used and the server lists 7 models
- **THEN** the result reads `7 models found`
- **WHEN** `gemma3:12b` is then chosen
- **THEN** Test inference becomes available

#### Scenario: A refused sign-in is not "does not answer"

- **WHEN** Test connection is used on `Office LM` and the server answers HTTP 401
- **THEN** the result reads `Office LM answered, but refused the sign-in` and the line under it reads "The server answered 401 (not authorised): the sign-in is missing or wrong. Check the sign-in type and the key variable."

#### Scenario: A passing connection shows its time

- **WHEN** Test connection is used and the server at `http://localhost:11434` answers in 41 ms
- **THEN** the result reads `Ollama answers (41 ms)`
- **AND** the models and inference results are not reported

#### Scenario: A failed Test connection rules the later tests out in the test dialog

- **WHEN** Test connection alone is used, nothing answers, and Details is opened and its Models tab pressed
- **THEN** the Models and Inference tabs can be pressed and each says `Not attempted — the connection failed first.`, with no request line

#### Scenario: An unreachable server fails the first stage only

- **WHEN** Test inference is used and nothing is listening at `http://localhost:11434`
- **THEN** the connection result reports that Ollama does not answer
- **AND** the models and inference results are reported as not attempted, not as passed

#### Scenario: A reachable server with the chosen model passes all three

- **WHEN** `gemma3:12b` is chosen, Test inference is used, and the server answers in 41 ms, lists 7 models including `gemma3:12b`, and answers the short request in 1.2 s
- **THEN** the results read `Ollama answers (41 ms)`, `7 models found — the chosen one is there` and `The model replied in 1.2 s`

#### Scenario: A server with no model listing still passes

- **WHEN** Test inference is used and the provider answers but its listing cannot be read
- **THEN** the models result is reported as a qualified pass carrying a note
- **AND** the inference result is still attempted

#### Scenario: A check in flight reports itself

- **WHEN** Test models has been started and the verification has not yet answered
- **THEN** the screen reports the test as in progress and the three test actions are unavailable
- **AND** they become available again when the report arrives

#### Scenario: An unsaved provider can be tested

- **WHEN** a new provider `Office LM` at `http://10.0.0.5:1234/v1` has not been saved and Test connection is used
- **THEN** the server at that address is asked and the result is shown, and `Office LM` is still not stored

### Requirement: Choose a model from the list, or name one the list does not hold

The application SHALL offer the models the provider being edited reports, SHALL accept a model identifier typed by hand, and SHALL also offer the provider's custom models.

**Source**: FR-MODEL-01, FR-MODEL-02, FR-MODEL-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`), DD-38, `docs/spec_baseline/specification/mockups/README.md#providers`. In plain words: some servers list what they hold and some do not, and a server that holds nothing is a valid state rather than a broken one. Typing the identifier has to stay possible in all three cases. The chosen model is discarded on a provider change because a model identifier means nothing on a different server.

#### Scenario: A listed model can be chosen

- **WHEN** the provider reports `gemma3:12b` and `qwen3:8b`
- **THEN** both are offered, and choosing `gemma3:12b` makes it the chosen model

#### Scenario: An unlisted model can be typed

- **WHEN** the provider reports no models and `mistral-small:24b` is typed
- **THEN** `mistral-small:24b` is the chosen model

#### Scenario: An unreadable listing says so

- **WHEN** the provider's listing cannot be read
- **THEN** the application reports that no list was obtained and keeps the typed entry available

#### Scenario: Changing provider clears the model

- **WHEN** `gemma3:12b` is chosen on the Ollama provider and the LM Studio provider is then edited
- **THEN** no model is chosen and the offered list is empty until it is fetched again

#### Scenario: A model note follows the chosen model

- **WHEN** the Ollama provider is edited and `gemma4:26b-mlx` is typed
- **THEN** a note under the chooser says it is recommended
- **AND** typing `gemma4:e2b-mlx` instead shows a warning, and an unknown model shows no note

#### Scenario: LM Studio's limits are stated

- **WHEN** the LM Studio provider is edited
- **THEN** a note says it is slower, does not stream its replies and sends more control codes, even before a model is chosen

#### Scenario: A listing in flight reports itself

- **WHEN** the model list has been asked for and has not yet been answered
- **THEN** the screen reports the listing as in progress
- **AND** the typed entry stays available throughout, because it needs no list

#### Scenario: The chooser lives on the providers card

- **WHEN** the settings screen is opened on the Providers area
- **THEN** the model chooser is the Model field of the Connection details fold
- **AND** Change model on the summary card opens the fold on that field

### Requirement: Describe each provider in its row and its detail card

The Providers area SHALL list each provider as a row showing its name, its endpoint's host and port, and a badge "current" or "idle"; and SHALL show a summary card — "Your AI model: Ollama · gemma4:e4b — ready ✓" for the current provider, "This provider's model: …" for another — with its speed and model count, or that no listing has been made yet, and the actions Test and Change model.

**Source**: FR-PROV-01, FR-PROV-04 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), S3, theme H (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#providers`, `docs/spec_baseline/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`. In plain words: a person sees at a glance which AI model is in use and whether it works, without opening the form. The host alone tells `127.0.0.1:11434` from `127.0.0.1:1234`.

#### Scenario: The rows name their hosts and status

- **WHEN** the Providers area is shown with the Ollama and LM Studio providers the first-launch look stored, Ollama current
- **THEN** the Ollama row shows `127.0.0.1:11434` and the badge `current`
- **AND** the LM Studio row shows `127.0.0.1:1234` and the badge `idle`

#### Scenario: The detail card after a listing

- **WHEN** Ollama is current with the model `gemma4:e4b`, it answered in about 1 second and its last listing returned 7 models
- **THEN** the summary card reads `Your AI model: Ollama · gemma4:e4b — ready ✓` with `Answers in about 1 second · 7 models available`, and offers Test and Change model

#### Scenario: The detail card before any listing

- **WHEN** LM Studio is current and its models have not been listed
- **THEN** the summary card says that no listing has been made yet and still offers Test and Change model

#### Scenario: The card of a provider that is not current

- **WHEN** the LM Studio row, which is not current, is selected
- **THEN** the card begins `This provider's model:` and names its model

#### Scenario: Save of the current provider's Model makes it the current model

- **WHEN** Ollama is current, its Model field is changed from `gemma4:e4b` to `gemma4:26b-mlx`, and Save is chosen
- **THEN** the summary card reads `Your AI model: Ollama · gemma4:26b-mlx` and `gemma4:26b-mlx` is the current AI model

#### Scenario: A provider that is not current and has no typed model

- **WHEN** the LM Studio row, which is not current, is selected and no model has been typed for it
- **THEN** the card reads `This provider's model: LM Studio · no model chosen yet`

### Requirement: Add, edit and delete providers

The settings screen SHALL let a person add a provider, open a stored provider for editing by choosing its row (there is no separate Edit button), and delete one after confirmation with Delete…; deleting a provider SHALL also remove its per-model settings, and deleting the current provider SHALL leave no current AI model.

**Source**: S3, decision 7 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#providers`. In plain words: a person whose AI program is not one of the two usual local ones can describe it themselves, removing one is never a single accidental click, and a deleted provider leaves nothing behind that could be mistaken for the model in use.

#### Scenario: A provider is added

- **WHEN** the person adds a provider named `Office LM` of kind OpenAI-compatible at `http://10.0.0.5:1234/v1` and saves
- **THEN** `Office LM` appears in the list with the host `10.0.0.5:1234`

#### Scenario: Choosing a row opens it for editing

- **WHEN** the person chooses the `Office LM` row
- **THEN** its form opens with the name `Office LM` and the address `http://10.0.0.5:1234/v1`

#### Scenario: Delete asks first

- **WHEN** the person chooses Delete… on `Office LM` and then cancels the confirmation
- **THEN** `Office LM` is still listed

#### Scenario: A confirmed delete removes it

- **WHEN** the person chooses Delete… on `Office LM` and confirms
- **THEN** `Office LM` is no longer listed

#### Scenario: Deleting the current provider

- **WHEN** Ollama is current with the model `gemma4:e4b` and Delete… on Ollama is confirmed
- **THEN** Ollama is no longer listed, no AI model is current, and the settings of `gemma4:e4b` are gone

#### Scenario: Deleting another provider keeps the current one

- **WHEN** Ollama is current with the model `gemma4:e4b` and Delete… on `Office LM` is confirmed
- **THEN** `Office LM` is no longer listed, Ollama is still current with `gemma4:e4b`, and the settings of `gemma4:e4b` are kept

### Requirement: Describe a provider's connection in a form

The provider form SHALL ask for a name, a kind (Ollama or OpenAI-compatible), an address, a connect timeout and a request timeout, and WHEN the address of an OpenAI-compatible provider does not end in `/v1`, the form SHALL warn that the address usually ends in `/v1`.

**Source**: S3, decision 7 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#providers`. In plain words: the form asks only for what the person can know, and the most common mistake with OpenAI-style servers, leaving off `/v1`, is flagged before a test fails mysteriously.

#### Scenario: A missing /v1 is flagged

- **WHEN** the kind is OpenAI-compatible and the address is `http://localhost:1234`
- **THEN** the form warns that the address usually ends in `/v1`

#### Scenario: An Ollama address is not flagged

- **WHEN** the kind is Ollama and the address is `http://localhost:11434`
- **THEN** no `/v1` warning is shown

### Requirement: Choose how a provider signs in without entering a key

The provider form SHALL offer the sign-in choices None, Bearer token and API-key header (with the header's name, `X-Api-Key` until the person types another), each with one line saying what it sends, SHALL take the key from the environment variable whose name the person enters, and SHALL show that variable as "set" or "not set" without showing its value, looked up the same way a request looks it up (the "Look a variable up where the person set it" requirement of `llm-provider`); WHILE it is "not set" the form SHALL say where to set it.

**Source**: S3, decision 7 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#settings--s1s5-h1-decisions-67` ("Keys are never saved, only environment-variable names"), ADR-0043. In plain words: the form never asks the person to type a secret. It asks where the secret lives and tells them whether BookLoom can find it.

#### Scenario: A variable that exists is shown as set

- **WHEN** Bearer token is chosen with the variable `HOME_API_KEY` and that variable has a value
- **THEN** the form shows `HOME_API_KEY` as "set" and does not show the value

#### Scenario: A variable that is missing is shown as not set, with where to set it

- **WHEN** API-key header `X-Api-Key` is chosen with the variable `OFFICE_KEY` and no such variable exists
- **THEN** the form shows `OFFICE_KEY` as "not set"
- **AND** a line under it says that an app opened from the Dock, the Start menu or an IDE does not see a shell profile's `export` lines and how to set `OFFICE_KEY` for the user account on macOS (`launchctl setenv`), Windows (`setx`) and Linux (`~/.config/environment.d/50-bookloom.conf`), pointing at "Provider keys" in the README

#### Scenario: Each sign-in says what it sends

- **WHEN** Bearer token is chosen
- **THEN** a line under the choice reads that it sends `Authorization: Bearer <key>`, for LM Studio with a key, OpenAI and most OpenAI-compatible servers
- **WHEN** API-key header is chosen and the header name is empty
- **THEN** the header name becomes `X-Api-Key` and the line names it as the example

#### Scenario: A variable written as a reference

- **WHEN** Bearer token is chosen and `$LMS_KEY`, `${LMS_KEY}`, `${env:LMS_KEY}` or `%LMS_KEY%` is typed as the key variable
- **THEN** the form says under the field "Enter only the variable's name, LMS_KEY, without $ or braces." and Save is held

### Requirement: Let the endpoints be overridden where the server needs it

The provider form SHALL offer a Models endpoint override and an Inference endpoint override, and WHILE the kind is Ollama SHALL show the Inference endpoint override as disabled with the reason that Ollama's chat address is fixed.

**Source**: S3, decision 7 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#providers`. In plain words: some OpenAI-style servers put their routes under unusual paths, so both can be changed. Ollama's route is fixed, and a greyed field that says why is clearer than a field that silently does nothing.

#### Scenario: An override is accepted for an OpenAI-compatible server

- **WHEN** the kind is OpenAI-compatible and the Models endpoint override is `/catalog/models`
- **THEN** the form accepts it and the Inference endpoint override is editable

#### Scenario: The inference override is disabled for Ollama

- **WHEN** the kind is changed to Ollama
- **THEN** the Inference endpoint override is disabled and a note states the reason

### Requirement: Take a provider's custom headers and models

The provider form SHALL let the person add custom headers as name and value rows, where a value MAY be `${env:NAME}`, and SHALL let the person add custom model identifiers shown as chips.

**Source**: S3, decision 7 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#providers`. In plain words: a company gateway may need an extra header, and some servers do not list the models they serve, so both can be added by hand and a secret header value can point at an environment variable.

#### Scenario: A header with an environment reference

- **WHEN** the header `X-Team-Key` is added with the value `${env:TEAM_KEY}`
- **THEN** the row is kept with that text, not with a resolved value

#### Scenario: A custom model chip

- **WHEN** the custom model `acme-translate-1` is added
- **THEN** it appears as a chip and is offered among the models of that provider

### Requirement: Refuse a header name or a timeout a request could not use

IF a custom header's name or the API-key header's name holds a space, a colon, a control character or another character an HTTP header name may not, is longer than 64 characters or reads like a key, or a connect or request timeout is not a whole number from 1 to 3600 seconds, or the address is not an absolute `http`/`https` address with a host, names a port outside 1 to 65535 or carries a user name or password, or the key variable is not the name of an environment variable (letters, digits and underscores, not starting with a digit) or reads like a key (a known key prefix such as `sk-`, `sk_`, `ghp_`, `AKIA…`, or 24 or more characters mixing lower case, upper case and digits), THEN the provider form SHALL show the finding under that field once the person has typed or pressed Save, never naming the refused text back, SHALL refuse Save while explaining on Save's hover what holds it, and the provider store SHALL refuse the same description with `ErrorCode.validation` so nothing of it is written; a stored provider that still carries one SHALL fail its call with `ErrorCode.validation` before anything is sent, and the test dialog and its curl line SHALL show its address without any user name or password.

**Source**: `docs/spec_baseline/specification/mockups/README.md#providers`, `docs/spec_baseline/specification/02_Architecture/09_ERROR_HANDLING.md`. In plain words: such a name or a timeout of years passes a save and then breaks every request as an unexplained transport error; the form says what to fix instead.

#### Scenario: A header name with a space

- **WHEN** the header `X Team` is added with the value `blue`
- **THEN** Save is held and the form says under the headers that a header name can hold only up to 64 Latin letters, digits and `- _ . ~`, without repeating `X Team`

#### Scenario: A key typed where the variable's name belongs is never shown back

- **WHEN** Bearer token is chosen, `sk_live_51HabcDEF123ghiJKL456` or `$sk_live_51HabcDEF123ghiJKL456` is typed as the key variable and Save is pressed
- **THEN** Save is held, the field says to type the NAME of a variable, not the key itself, the form shows neither "set" nor "not set" nor the line saying where to set it, no text or tooltip of the window holds the typed text, and the operating system's variables are not asked for it

#### Scenario: A timeout longer than an hour

- **WHEN** the connect timeout is `3601`
- **THEN** Save is held and the form asks under the connect timeout for at most 3600 seconds

#### Scenario: A key typed where its variable's name belongs

- **WHEN** Bearer token is chosen and `sk-literal-SECRET-abc123` or `sk_live_51HabcDEF123ghiJKL456` is typed as the key variable and Save is pressed
- **THEN** the form says under the field "Type the NAME of an environment variable that holds the key, not the key itself.", nothing is stored, and the value appears in no log line

#### Scenario: An address with a password or an impossible port

- **WHEN** the address is `http://user:pass@127.0.0.1:11434` or `http://127.0.0.1:65536`
- **THEN** Save is held with the finding under the address, and nothing is stored

#### Scenario: An address without a scheme

- **WHEN** the address is `127.0.0.1` or `localhost:11434`
- **THEN** Save is held and the form asks under the address for one that starts with `http://` or `https://`

### Requirement: Use one provider and ask first while the open project has stored decisions

WHEN the person chooses Use this one on a stored provider, the settings screen SHALL make it the current provider; and WHILE the open project has stored segment decisions, SHALL first ask "Use LM Studio for the rest of this book?" (naming the chosen provider) and change nothing unless the person confirms.

**Source**: S3, decision 7 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#changes-while-translating-i`, `docs/spec_baseline/specification/mockups/README.md#providers`. In plain words: switching the AI model in the middle of a book changes the voice of the rest, whether or not a run is going at that moment, so the person is asked once and told that segments already done keep their translation.

#### Scenario: Use this one with no project open

- **WHEN** no project is open and Use this one is chosen on `LM Studio`
- **THEN** `LM Studio` carries the badge `current` at once and the previous provider shows `idle`

#### Scenario: Use this one in the middle of a book asks first

- **WHEN** the open project `Earth Gravity · EN → UK` has 74 of 142 segments decided, no run is going, and Use this one is chosen on `LM Studio`
- **THEN** the question "Use LM Studio for the rest of this book?" is shown with "Using the same AI model keeps the style even. Segments already done keep their translation."
- **AND** the current provider changes only when the person confirms

#### Scenario: Keep changes nothing

- **WHEN** the question "Use LM Studio for the rest of this book?" is answered with Keep Ollama
- **THEN** `Ollama` stays current, the badges are as they were and nothing is stored

### Requirement: Show a provider's connection details and open them when they matter

The provider detail SHALL hold the connection form in a fold called Connection details, and the fold SHALL open by itself when the provider has a problem, when the provider is new, or when Show technical details is on.

**Source**: theme H (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`. In plain words: someone whose AI model works never needs to see the form, and someone who needs it finds it already open.

#### Scenario: A healthy provider keeps the fold closed

- **WHEN** the current provider `Ollama` passed its last check
- **THEN** the Connection details fold is closed

#### Scenario: A failed check opens the fold

- **WHEN** the last check of `Ollama` reported "Ollama does not answer"
- **THEN** the Connection details fold is open

#### Scenario: A new provider opens the fold

- **WHEN** the person starts adding a provider
- **THEN** the Connection details fold is open

### Requirement: Show a check's details in a test dialog

WHEN the person opens a check's details, the settings screen SHALL show a dialog with a verdict banner, the request line (the route actually used) with Copy as curl (key hidden), three columns Body, a table of Status, Time, First token, Speed, Tokens and Structured, and Response, and a footer that switches between the tests that have run and closes; a copy is confirmed in the footer, never a toast. A figure the engine does not report SHALL read –, never 0.

**Source**: S2 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#dialog--test-connection--models--inference-s2`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: when a test fails, a person or a helper can see exactly what was asked and what came back, and can reproduce it in a terminal without the secret being exposed. A speed the server never reported is shown as missing, not as zero, so it cannot be mistaken for a measurement.

#### Scenario: The dialog shows the request and the answer

- **WHEN** the inference check of `Ollama` with `gemma4:e4b` is opened
- **THEN** the banner reads "The model replied correctly. Translation can start."
- **AND** the request line shows `POST http://127.0.0.1:11434/api/chat` and the three columns are filled

#### Scenario: The request line shows the route the engine uses

- **WHEN** the inference check of `LM Studio` with `google/gemma-4-e4b` is opened
- **THEN** the request line shows `POST http://127.0.0.1:1234/api/v0/chat/completions`

#### Scenario: A figure the engine does not report is a dash

- **WHEN** the inference check of an Ollama provider reached through `/v1` is opened and that route reports no time to first token
- **THEN** the First token cell reads `–` and not `0`

#### Scenario: Prompt reading says it is uncached on MLX

- **WHEN** the inference check of `gemma4:e4b-mlx` on Ollama reports 600 prompt tokens of which 200 were cached
- **THEN** the table shows prompt reading labelled "uncached" with the cached share, 33 %, beside it

#### Scenario: Copy as curl hides the key

- **WHEN** Copy as curl is used for a provider that signs in with a bearer token from `HOME_API_KEY`
- **THEN** the copied command carries `Authorization: Bearer ••••` and no key value

#### Scenario: Copying says so in the dialog's footer

- **WHEN** Copy as curl is used in the test dialog
- **THEN** the footer reads "Copied as curl — the key is hidden." and no toast is shown

#### Scenario: A check result opens the test dialog

- **WHEN** the check result `The model replied in 1.2 s` is activated
- **THEN** the test dialog opens on the inference check

### Requirement: Describe each model on the Models tab and keep its own settings

The Models tab SHALL list the models across all providers, and SHALL keep for the chosen model its own optional settings, with the explanation "Each model keeps its own settings. All are optional — off means BookLoom picks the value per task."

**Source**: decision 6 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#models-decision-6`. In plain words: one model may refuse a setting that another accepts, so settings belong to a model, and leaving them off always means the safe default.

#### Scenario: Two models keep different settings

- **WHEN** `gemma4:e4b` is given the context window 16384 and `gpt-oss:20b` is left unchanged
- **THEN** the Models tab shows 16384 for `gemma4:e4b` and no value for `gpt-oss:20b`

### Requirement: Say which providers did not list their models on the Models tab

WHEN the Models tab reads the providers' models and a provider's program does not list them, the tab SHALL say under the chooser which providers did not answer and that only the models BookLoom already knows are shown for them, until a later reading has every provider answer.

**Source**: `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-settings`. In plain words: Refresh with Ollama closed changed nothing on the screen, so it looked broken; the line says why the list is short.

#### Scenario: Refresh while Ollama is closed

- **WHEN** Ollama does not answer and Refresh is pressed on the Models tab
- **THEN** the line under the chooser reads `Ollama did not answer, so only its models BookLoom already knows are shown.`

### Requirement: Say that Restart now is under way and bring the window back where it was

WHEN Restart now is pressed, the button SHALL read `Restarting...` until the window closes, and the next copy SHALL open its window at the place and size the closing window had (maximized when it was), unless that place is on no screen now. An ordinary launch SHALL open at the default size.

**Source**: `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-settings`. In plain words: for the two seconds before the window closed nothing changed on screen, and the new window came back at 1024 × 700 in the middle of the screen; the closing copy now hands its place to the next one through the next copy's environment (`BOOKLOOM_WINDOW`).

#### Scenario: The window comes back as it was

- **WHEN** the window is 1300 × 820 at 40, 60 and Restart now is pressed
- **THEN** the button reads `Restarting...`, and the next copy's window is 1300 × 820 at 40, 60

#### Scenario: A place on no screen is not used

- **WHEN** the closing window was on a display that is no longer connected
- **THEN** the next copy opens at the default size and place

### Requirement: Choose how the temperature is sent for a model

The Models tab SHALL offer for each model Let BookLoom choose (the default), My value (one value for every request) and Don't send (the temperature is left out entirely).

**Source**: S6 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#models-decision-6`. In plain words: most models want the tuned values, some people want one fixed value, and a few models refuse the setting altogether, so the choice is explicit and Don't send exists for them.

#### Scenario: My value sets one temperature

- **WHEN** My value is chosen for `gemma4:e4b` with `0.5`
- **THEN** the Models tab shows `0.5` as the value that every request to that model will carry

#### Scenario: Don't send is selectable

- **WHEN** Don't send is chosen for `gpt-oss:20b`
- **THEN** the Models tab shows that no temperature will be sent to that model

### Requirement: Set a model's context window and answer length

The Models tab SHALL offer for each model a Use context window number (filled in from what is loaded) from 512 to 1,048,576 tokens, which is the one size every call to that model uses and is read-only where BookLoom cannot set the window on that engine, a Limit answer length switch no larger than the window, and under Advanced the field that carries the answer length, `max_completion_tokens` or `max_tokens`, which SHALL NOT apply to Ollama.

**Source**: decision 6 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#models-decision-6`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a person can widen the window a model reads or cap how long it may answer, and for OpenAI-style servers choose the field name the server expects. Ollama has no such choice, so the field is not offered for it. Where the server owns the window, the number is shown but cannot be edited, so it never pretends to be a setting.

#### Scenario: A context window outside its range

- **WHEN** Use context window is on with `256`, or with `2000000`
- **THEN** Save is held and the tab asks for a context window from 512 to 1,048,576 tokens

#### Scenario: An answer cap larger than the window

- **WHEN** Use context window is `8192` and Limit answer length is `8193`
- **THEN** Save is held and the tab says the answer length limit cannot be larger than the context window

#### Scenario: The context window is filled in from the program

- **WHEN** `gemma4:e4b` has 16384 tokens loaded and the tab is opened
- **THEN** Use context window shows `16384`

#### Scenario: The window is read-only where BookLoom cannot set it

- **WHEN** the model belongs to LM Studio, which loads the window itself
- **THEN** the context window number is shown, cannot be edited, and the Context row says LM Studio sets it

#### Scenario: The field choice is absent for Ollama

- **WHEN** the model belongs to an Ollama provider, whether reached by its own connection or through `/v1`
- **THEN** the Advanced answer-length field choice is not offered

#### Scenario: The field choice is offered for OpenAI-compatible servers

- **WHEN** the model belongs to a generic OpenAI-compatible provider
- **THEN** Advanced offers `max_completion_tokens` and `max_tokens`

#### Scenario: A server that ignores the cap is named

- **WHEN** the Advanced features stage found that the server answered past an answer cap of 16 tokens
- **THEN** a note under the answer-length field says the server ignored the cap in the last Advanced features check

#### Scenario: The loaded figure is a suggestion while the window is off

- **WHEN** nothing is stored for `gpt-oss:20b`, 16384 tokens are loaded and Use context window is off
- **THEN** the number field shows `16384` disabled, the draft is unchanged and the footer shows no unsaved mark

### Requirement: List the settings a model refused and let the person send them again

The Models tab SHALL list under "Settings this model refused" each setting the model refused, its scope (the whole model, or one call kind or schema), when it was refused and what BookLoom did instead, with Send again on each row that acts on that scope only, and SHALL say "Nothing refused" when the list is empty.

**Source**: S6 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), FR-INFER-09e (`docs/spec_baseline/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`), `docs/spec_baseline/specification/mockups/README.md#refused-settings-s6`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: BookLoom learns by itself that a model rejects a setting, sometimes for every call and sometimes for one kind of call, and the person can see what it learned and undo exactly that part if the model was updated.

#### Scenario: A refusal is listed

- **WHEN** `gpt-oss:20b` refused `temperature` on 2026-10-20 and BookLoom stopped sending it
- **THEN** the list shows a row for `temperature` with the scope "whole model", that date and the action taken

#### Scenario: A refusal of one kind of call is scoped

- **WHEN** `qwen3.5-9b` refused a schema for Name reading on 2026-10-21 and BookLoom used plain JSON instead
- **THEN** the list shows a row for the schema with the scope "Name reading", that date and the action taken

#### Scenario: Send again forgets the refusal

- **WHEN** Send again is chosen on the `temperature` row
- **THEN** the row disappears and the next request to that model carries the temperature again

#### Scenario: Send again on a scoped row touches only that scope

- **WHEN** Send again is chosen on the Name reading schema row while the Translate schema row is also listed
- **THEN** the Name reading row disappears, the next Name reading call sends a schema again and the Translate row stays

#### Scenario: A folded system message is listed

- **WHEN** `gemma3:12b` refused the system role and BookLoom put the system text into the first user message
- **THEN** the list shows a row for the system message with the date and the action taken

#### Scenario: An empty list says so

- **WHEN** a model has refused nothing
- **THEN** the section reads "Nothing refused"

#### Scenario: Send again acts at once and leaves unsaved typing

- **WHEN** the temperature choice of `gpt-oss:20b` is changed to My value and Send again is chosen on its `temperature` row
- **THEN** the stored refusal is removed at once and the unsaved temperature choice stays in the form

### Requirement: Save Providers and Models through a pinned footer and guard leaving

The Providers and Models tabs SHALL change nothing until saved, SHALL show a pinned footer with "● Unsaved changes", Cancel and Save while they hold changes, and WHEN the person leaves with changes SHALL ask Keep editing, Discard or Save.

**Source**: theme H, option H1 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#settings--s1s5-h1-decisions-67`, `docs/spec_baseline/specification/mockups/README.md#other-dialogs`. In plain words: a provider's address typed halfway must not become live, and moving to another tab must not silently lose or silently apply what the person typed.

#### Scenario: A change shows the footer

- **WHEN** the request timeout of `Ollama` is changed from 180 to 240 seconds
- **THEN** the footer shows "● Unsaved changes", Cancel and Save, and the stored timeout is still 180

#### Scenario: Leaving asks what to do

- **WHEN** the person opens the Appearance tab with unsaved changes on Providers
- **THEN** the question offers Keep editing, Discard and Save, and the Appearance tab opens only after Discard or Save

#### Scenario: Switching provider asks naming it

- **WHEN** `Ollama` has unsaved changes and the `LM Studio` row is chosen
- **THEN** the question "Save the changes to Ollama?" is asked with Keep editing, Discard and Save
- **AND** `LM Studio` opens only after Discard or Save

#### Scenario: Switching model asks naming it

- **WHEN** `gemma4:e4b` of `Ollama` has unsaved changes and `gpt-oss:20b` is chosen in the model list
- **THEN** the question "Save the settings of gemma4:e4b · Ollama?" is asked once with Keep editing, Discard and Save
- **AND** `gpt-oss:20b` opens only after Discard or Save

#### Scenario: Leaving Models asks

- **WHEN** the Models tab holds unsaved changes and the Providers tab is opened
- **THEN** the question is asked and the Providers tab opens only after Discard or Save

#### Scenario: Save stores the change

- **WHEN** Save is chosen
- **THEN** the stored timeout is 240 and the footer disappears

### Requirement: Choose the theme, the language and technical details on Appearance

The Appearance tab SHALL offer the theme, the language of the app with the note "Applies after BookLoom restarts." and a Restart now action, and a Show technical details switch, and SHALL NOT offer an accent row.

**Source**: S5 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#appearance-tab`. In plain words: the language changes on the next launch because the whole window is built in one language, and Restart now saves the wait. The old fixed accent line is gone because nothing could change it.

#### Scenario: A language choice waits for a restart

- **WHEN** Ukrainian is chosen while the window is in English
- **THEN** the window stays in English, the note "Applies after BookLoom restarts." is shown and Restart now is offered

#### Scenario: Technical details are a switch

- **WHEN** Show technical details is switched on
- **THEN** technical details appear where the screens offer them and stay on after a restart

#### Scenario: There is no accent row

- **WHEN** the Appearance tab is opened
- **THEN** it has no accent row

### Requirement: Name the review modes with one set of words

The Automation tab's When to stop and ask you SHALL offer exactly these three choices, each standing for one review mode of the run and one launch value:

| Automation choice | Review mode of the run | Launch value |
|---|---|---|
| Never — translate unattended | Unattended | `unattended` |
| When something needs review | Assisted | `assisted` |
| After every segment | Manual | `manual` |

The long phrases belong to the Automation tab. Every other screen that names the mode (the title bar, the Translating screen) uses the short words Unattended, Assisted and Manual, and `BOOKLOOM_REVIEW_MODE` and `bookloom.review.mode` take the launch values.

**Source**: `docs/spec_baseline/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#automation-tab`, ADR-0045. In plain words: the tab, the other screens and the environment variable name the same three modes, so a person and a script mean the same thing: the tab says it in long sentences, the rest of the application in one word, and a script in the launch value. The default is Never — translate unattended.

#### Scenario: The words map to the modes

- **WHEN** When something needs review is chosen and a run is started
- **THEN** the run's review mode is Assisted

#### Scenario: Nothing stored means unattended

- **WHEN** nothing is stored for When to stop and ask you, no launch value is set, and a run is started
- **THEN** the run's review mode is Unattended

### Requirement: Show storage and logs on one tab

The Storage & logs tab SHALL show where the projects are saved, how large they are (the database with its write-ahead file) and how many projects it holds, an Open data folder action (it opens the data folder, never the log folder), Make a backup… and Delete all data…, and SHALL show the log folder, that 10 launches are kept, a Detailed diagnostic log switch that applies after BookLoom restarts (a launch value `BOOKLOOM_TRACE_FILE` or `bookloom.trace.file` wins over it and is named), Open log folder and Save a diagnostic bundle…, and SHALL show each path in short form with the full path as its tooltip. Make a backup… SHALL write only to a file the person picks, SHALL never replace an existing file, and SHALL NOT carry any key. Delete all data… SHALL ask first, with Cancel as the default and a Make a backup first answer, and SHALL delete only as the `local-storage` capability's "Delete all data on request" says.

**Source**: `docs/spec_baseline/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#storage-and-logs-tab`. In plain words: a person can find their saved work and the logs without hunting through the file system, and can make a backup or hand support a bundle. Long paths stay readable, and the whole path is one hover away.

#### Scenario: A long path is shortened

- **WHEN** the projects are saved in `~/Library/Application Support/BookLoom`
- **THEN** the tab shows a shortened form and the tooltip shows the full path

#### Scenario: Logs keep ten launches

- **WHEN** the Storage & logs tab is opened
- **THEN** it states that 10 launches are kept

#### Scenario: The detailed log switch applies after a restart

- **WHEN** Detailed diagnostic log is switched on and BookLoom is closed and opened again
- **THEN** the detailed log is written in the new launch, the tab said before the restart that the switch applies after BookLoom restarts, and the log written in the first launch did not change

#### Scenario: A launch value decides the detailed log

- **WHEN** BookLoom was launched with `BOOKLOOM_TRACE_FILE=0` while Detailed diagnostic log is switched on
- **THEN** the detailed log is not written, and a note under the switch names `BOOKLOOM_TRACE_FILE` as the value that decides this launch

#### Scenario: Make a backup writes a copy that opens

- **WHEN** Make a backup… is used with a new file name
- **THEN** the file holds all the projects and settings as they were, contains no key, and the saved work itself is unchanged and still writable

#### Scenario: Make a backup refuses an existing file

- **WHEN** Make a backup… is used with the name of a file that already exists
- **THEN** the file is left untouched and an error with the code `validation` says a file with that name is already there

#### Scenario: Delete all data asks first and Cancel changes nothing

- **WHEN** Delete all data… is used and the question is answered Cancel
- **THEN** the question had said that all projects, translations, glossaries, providers and settings are deleted and that the book files, the exported files and the logs are not touched, and nothing is deleted and BookLoom does not restart

#### Scenario: Make a backup first asks again

- **WHEN** the question of Delete all data… is answered Make a backup first and a new file name is chosen
- **THEN** the backup is written as Make a backup… writes it, nothing is deleted, and the question is asked again

### Requirement: Show all six settings areas, every one enabled

The settings screen SHALL carry a subtitle saying that books stay private when a local model is used and that BookLoom sends the text only to the AI model chosen here, and SHALL NOT say the text always stays on this computer; it SHALL present six areas — Providers, Models, Generation, Appearance, Automation, and Storage & logs — as a row of tabs whose selected tab is marked by an underline, every one enabled, with no accent row on Appearance.

**Source**: decision 6, S4, S5 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#settings--s1s5-h1-decisions-67`, `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#settings-tabs`, the owner's decision that privacy is stated as "private when used with local models". In plain words: with the models, generation, automation and storage tabs built, nothing is greyed any more. The subtitle must not promise more than BookLoom can keep: with a remote provider the book's text leaves the computer, so only the local-model case is called private.

#### Scenario: All six areas are listed

- **WHEN** the settings screen is opened
- **THEN** tabs for Providers, Models, Generation, Appearance, Automation, and Storage & logs are all present and enabled
- **AND** the subtitle reads `Your books stay private when you use a local model. BookLoom sends your text only to the AI model you choose here.`

#### Scenario: The subtitle never says always local

- **WHEN** the settings screen is opened in English or in Ukrainian
- **THEN** the subtitle does not claim that the books always stay on this computer

#### Scenario: The selected area is underlined

- **WHEN** the settings screen is opened on the Providers tab
- **THEN** the Providers tab is marked by an underline and no other tab is

#### Scenario: Every area opens

- **WHEN** the Generation tab is activated
- **THEN** it opens and shows its own settings

#### Scenario: The appearance area has no accent

- **WHEN** the Appearance area is shown
- **THEN** it presents the theme choice and no accent row

### Requirement: Apply the app language after a restart and the review mode at the next run start

The application SHALL apply a changed app language at the next launch and a changed When to stop and ask you at the next run start, leaving a running run in its mode; WHERE the review mode is set by the environment variable `BOOKLOOM_REVIEW_MODE` or the launch property `bookloom.review.mode`, that value SHALL win over the stored choice.

**Source**: `docs/spec_baseline/specification/mockups/README.md#settings--s1s5-h1-decisions-67`, `docs/spec_baseline/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D12". In plain words: the language needs a restart because every screen is built in one language; the review mode belongs to a run, so a change steers the next run rather than surprising the one going. Scripts and evaluations that set the mode explicitly keep control of it.

#### Scenario: A changed review mode reaches the next run

- **WHEN** a run is going in Unattended, the person chooses After every segment on Automation, the run ends, and a new run is started
- **THEN** the running run keeps translating without pausing, and the new run pauses after each decided segment

#### Scenario: An explicit launch value wins

- **WHEN** BookLoom was launched with `BOOKLOOM_REVIEW_MODE=unattended`, After every segment is stored on Automation, and a run is started
- **THEN** the run translates unattended

#### Scenario: The language waits for the next launch

- **WHEN** Ukrainian is chosen on Appearance while the window is in English, and the application is restarted
- **THEN** the window stays in English until the restart and is in Ukrainian after it

### Requirement: Release the keep-awake hold at once when the switch is turned off

WHEN Keep the computer awake is turned off while the application holds the computer awake, the application SHALL release the hold at once and SHALL NOT take it again while the switch is off.

**Source**: `docs/spec_baseline/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#automation-tab`. In plain words: the switch applies at once like the other Automation switches, so a person who wants the laptop to sleep is not overruled by a run that is already going. When the computer is held awake is the `app-shell` capability's rule.

#### Scenario: Turning the switch off releases the hold

- **WHEN** a run is translating with the computer held awake and Keep the computer awake is switched off
- **THEN** the hold is released at once, the run goes on, and no hold is taken for the rest of the run

### Requirement: Store the speed the inference test measured on the model

WHEN Test inference passes for a model of a stored provider, the application SHALL store the measured speed in seconds per segment on that model's settings, with where it was measured (the later measurement winning), and the estimate before Start SHALL say where. WHEN a translation ends it SHALL store its seconds per drafted segment, pauses left out, and nothing if none was drafted; a translation or names pass SHALL also store seconds per call of each kind it made.

**Source**: S2 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D13", `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: an estimate needs a measurement behind it. The test the person just ran is one, and a finished run measures each kind of call (draft, repair, proofread, names), which is more exact than one figure per segment. Storing them on the model keeps them after a restart and keeps two models apart.

#### Scenario: The measured speed is stored and named

- **WHEN** Test inference for `gemma4:e4b` passes and measures 3.0 seconds per segment, and the application is restarted
- **THEN** the settings of `gemma4:e4b` hold 3.0 seconds per segment, measured by the inference test, and the estimate before Start says it comes from the inference test

#### Scenario: A translation's own speed replaces the test's

- **WHEN** a translation of the book with `gemma4:e4b` ends after Test inference had stored a speed
- **THEN** the stored speed is the translation's and the estimate before Start says it comes from an earlier translation

#### Scenario: A translation stores its seconds per kind of call

- **WHEN** a translation with `gemma4:e4b` ends having made 300 draft calls in 900 s and 20 proofread calls in 120 s
- **THEN** the settings of `gemma4:e4b` hold 3.0 seconds per draft call and 6.0 seconds per proofread call, and the estimate before Start says they were measured on this model

#### Scenario: A names pass stores its own kinds

- **WHEN** a names pass with `gemma4:e4b` ends having made 40 name-reading calls in 200 s
- **THEN** the settings of `gemma4:e4b` hold 5.0 seconds per name-reading call

#### Scenario: A kind that made no call stores nothing

- **WHEN** a translation at Fast ends without a proofread call and `gemma4:e4b` held 6.0 seconds per proofread call before
- **THEN** the stored 6.0 seconds per proofread call is unchanged

#### Scenario: A run that drafted nothing stores nothing

- **WHEN** a translation is cancelled or refused before any segment was drafted
- **THEN** the speed stored on the model is unchanged

### Requirement: Show who a model is on the Models tab's identity line

The Models tab SHALL show for the chosen model one identity line, `Understood: {model} · {quant} · {match}`, where the match part reads "exact match", "recognised as {family}, not this exact model" or "recognised as a fine-tune of {base} (behaviour of {base})", and the quant part reads `{label} (server reports {base})` when the name's quant label differs from the one the server reports.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d19--model-identity-reading-model-names`. In plain words: before changing anything, the person sees how BookLoom read the model's name and whether it knows this exact model, a close relative, or only its architecture. That decides how much to trust the advice shown below the line.

#### Scenario: An exact match

- **WHEN** the Models tab is opened on `hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL` and the server reports the quant `Q4_K_M`
- **THEN** the identity line reads `Understood: GLM 4.7 Flash · UD-Q4_K_XL (server reports Q4_K_M) · exact match`

#### Scenario: A fine-tune is named with its base

- **WHEN** the chosen model is `hf.co/bartowski/NousResearch_Hermes-4-70B-GGUF:Q4_K_M`, a fine-tune with no row of its own
- **THEN** the identity line reads `Understood: Hermes 4 70B · Q4_K_M · recognised as a fine-tune of Llama 3.1 (behaviour of Llama 3.1)`

#### Scenario: Only the architecture is known

- **WHEN** the chosen model is an unknown build whose server reports the architecture `gemma3`
- **THEN** the match part reads `recognised as Gemma 3, not this exact model`

#### Scenario: Both quant labels agree

- **WHEN** the name's quant label and the server's label are both `Q8_0`
- **THEN** the quant part reads `Q8_0` with no "server reports" text

### Requirement: Say how good a model's quant is on the Models tab

The Models tab SHALL show under the identity line the sentence for the model's quant tier, none for excellent, naming the open project's target language where the sentence has one, and SHALL NOT block or change any setting because of it. The tier itself is decided by the llm-provider capability.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d22--quant-and-language-advice`. In plain words: a heavily compressed download translates worse, especially for names and word endings. The person is told plainly and still decides for themselves.

#### Scenario: A good quant

- **WHEN** the tier of `UD-Q4_K_XL` is good
- **THEN** the line under the identity line reads "Very close to full quality. Fine for translating a whole book."

#### Scenario: An acceptable quant names the target language

- **WHEN** the tier of `Q4_K_M` on `phi4-mini` is acceptable and the open project translates into Ukrainian
- **THEN** the line reads "Slightly reduced precision. Usually fine, but names and word endings may drift, especially for Ukrainian; a larger version translates more reliably."

#### Scenario: A three-bit file on a large model

- **WHEN** the tier of `Q3_K_M` on a 32B dense model is acceptable with a caution
- **THEN** the line reads "Usable at this size, but 3-bit files lose more than 4-bit ones; check a few paragraphs first."

#### Scenario: A poor quant

- **WHEN** the tier of `Q4_0` on `phi4-mini` is poor
- **THEN** the line reads "Noticeably reduced quality, especially for Ukrainian. Expect more mistakes and more segments to review. Use it only if nothing larger fits."

#### Scenario: An unusable quant still lets the model be chosen

- **WHEN** the tier of `Qwen_Qwen3.5-9B-Q2_K_L.gguf` is unusable
- **THEN** the line reads "This version is too compressed for translation. Choose a larger version or another model." and the model can still be chosen and saved

#### Scenario: A removed format, an unmeasured format and an excellent quant

- **WHEN** the tier is not supported (`Q4_0_4_4`), not measured (`Q1_0`) or excellent (`Q8_0`)
- **THEN** the lines read "This old format is no longer supported. Download the Q4_0 or Q4_K_M file from the same repository.", "BookLoom has no quality data for this format. Treat it like a 4-bit version." and nothing, respectively

### Requirement: Show the quant chip beside every model in every model picker

The application SHALL show beside each model in every model picker (the Providers model list, the Models chooser and the AI-model choice before Start) a chip with the quant token of the name, or the quant the server's facts report when the name has none, and its tier, such as `Q4_K_M · acceptable`; it SHALL show no chip while no quant is known, and the chip SHALL keep its full width and never be cut short.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d22--quant-and-language-advice`. In plain words: a person choosing among six downloads of one model sees at once which one is the compressed one. A chip that is cut to "Q4_K_…" would defeat the purpose.

#### Scenario: A good chip

- **WHEN** the Providers model list holds `hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL`
- **THEN** its row carries the chip `UD-Q4_K_XL · good`

#### Scenario: An acceptable chip and an unusable chip

- **WHEN** the Models chooser holds `phi4-mini` (`Q4_K_M`, 3.8B) and `Qwen_Qwen3.5-9B-Q2_K_L.gguf`
- **THEN** the rows carry `Q4_K_M · acceptable` and `Q2_K_L · unusable`

#### Scenario: The chip stays whole in a narrow window

- **WHEN** the window is narrowed so the model name needs the whole row
- **THEN** the name is cut or wrapped and the chip `Q4_K_M · acceptable` is shown in full

#### Scenario: The same chip before Start

- **WHEN** the AI-model choice before Start lists `gemma4:e4b-mlx`, whose name holds no quant token, after its facts were read and the server reported `nvfp4`
- **THEN** its row carries the chip `nvfp4 · acceptable`

#### Scenario: No chip before the facts are read

- **WHEN** the Providers model list shows `gemma4:e4b-mlx` and Ollama's model list gives it an empty quant, and the model has not been selected, tested or detected again
- **THEN** its row carries no chip, and selecting it reads its facts and shows `nvfp4 · acceptable`

### Requirement: Mark cloud models with a neutral label in every model picker

The application SHALL show the neutral label "cloud" beside each model of the Ollama cloud in every model picker, SHALL leave every model listed, and SHALL NOT ask for consent.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, the owner's decision that cloud models are shown with a neutral label, nothing hidden and nothing asked. In plain words: a person can see which models run on someone else's computer, without being warned off or made to click through a dialog.

#### Scenario: A cloud model is labelled

- **WHEN** the Ollama model list holds `gpt-oss:120b-cloud` and `gemma4:e4b`
- **THEN** the row of `gpt-oss:120b-cloud` carries the label `cloud` and the row of `gemma4:e4b` does not

#### Scenario: Choosing a cloud model asks nothing

- **WHEN** `gpt-oss:120b-cloud` is chosen and saved
- **THEN** no consent dialog and no acknowledgement checkbox is shown

### Requirement: Narrow every model picker by the typed model name

The application SHALL show a text field with the placeholder "Filter models by name" above the list of every model picker (the Providers model list, the Models chooser and the AI-model choice before Start), SHALL narrow the list as the person types to the models whose id contains the typed text ignoring letter case, and WHEN no id matches SHALL show `No model name contains "{text}".`

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`, the owner's decision that every model picker has a name filter. In plain words: a server with sixty downloads is hard to scan. Typing `e4b` should leave the e4b models.

#### Scenario: Typing narrows the list

- **WHEN** the Models chooser holds `gemma4:e4b`, `gemma4:26b` and `qwen3.5:9b` and `e4b` is typed
- **THEN** only `gemma4:e4b` is listed

#### Scenario: Letter case is ignored

- **WHEN** `GEMMA4` is typed over the same three models
- **THEN** `gemma4:e4b` and `gemma4:26b` are listed and `qwen3.5:9b` is not

#### Scenario: Nothing matches

- **WHEN** `llama` is typed over the same three models
- **THEN** the list is empty and the text `No model name contains "llama".` is shown

#### Scenario: Clearing the text restores the list

- **WHEN** the text `e4b` is deleted
- **THEN** all three models are listed again

#### Scenario: The filter works before Start too

- **WHEN** the AI-model choice before Start holds `mistral-small3.2:24b` and `llama3.1:8b` and `mistral` is typed
- **THEN** only `mistral-small3.2:24b` is listed

### Requirement: Let the keyboard and the selection work with the model-name filter

WHEN the person presses Esc in the model-name filter, the application SHALL clear the text and show the whole list; WHEN the person presses the Down key in it, focus SHALL move into the list; and the chosen model SHALL stay chosen while it still matches the typed text.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: a person at the keyboard can filter, step into the list and pick, and a half-typed filter never silently drops the choice already made.

#### Scenario: Esc clears the text

- **WHEN** `e4b` is typed in the filter of the Models chooser and Esc is pressed
- **THEN** the field is empty and the whole list is shown

#### Scenario: Down moves into the list

- **WHEN** `gemma` is typed and the Down key is pressed
- **THEN** focus is on the first listed model

#### Scenario: The selection survives a matching filter

- **WHEN** `gemma4:e4b` is chosen and `gemma` is typed
- **THEN** `gemma4:e4b` is still chosen and shown selected

#### Scenario: A filter that hides the chosen model does not change the choice

- **WHEN** `gemma4:e4b` is chosen and `qwen` is typed
- **THEN** the list shows only Qwen models and the chosen model is still `gemma4:e4b`

### Requirement: Say which languages a model declares on the Models tab

The Models tab SHALL show for the chosen model a neutral line saying whether its maker lists languages — "Declared languages: {count} (includes {codes})", "Declared languages: English only", "Declared languages: not stated by the maker" or "Declared for its base model {base}: {codes}" — and WHERE the open project's language pair is one BookLoom tested the model on, SHALL add "· BookLoom tested it for {source} → {target}".

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d22--quant-and-language-advice`. In plain words: the maker's list is what the maker says, not what was measured, so the line says "declared" and adds BookLoom's own test only where there is one, for the pair it covered. The state and the tested pairs are decided by the llm-provider capability ("Say whether a model's maker declares a language", "Count a language pair as tested only by BookLoom's own runs").

#### Scenario: A model with a list

- **WHEN** the chosen model lists 24 languages including Ukrainian and Russian
- **THEN** the line reads "Declared languages: 24 (includes uk, ru)"

#### Scenario: English only and not stated

- **WHEN** the chosen model is `phi4:14b`, which lists English only, and then `gemma4:12b`, whose maker gives no list
- **THEN** the lines read "Declared languages: English only" and "Declared languages: not stated by the maker"

#### Scenario: A fine-tune takes its base model's list

- **WHEN** the chosen model is a Hermes 4 14B download whose own metadata says nothing and its base model Qwen3 lists Ukrainian and Russian
- **THEN** the line reads "Declared for its base model Qwen3: uk, ru"

#### Scenario: BookLoom's own test is added

- **WHEN** `gemma4:e4b` is chosen and the open project translates English into Ukrainian
- **THEN** the line reads "Declared languages: not stated by the maker · BookLoom tested it for English → Ukrainian"
- **AND** for a Russian-to-Ukrainian project the line reads "Declared languages: not stated by the maker" with no tested part

### Requirement: Warn under the AI-model choice and at Start when the model does not declare the book's languages

WHERE the project's source and target languages are known, the application SHALL show under the AI-model choice and at Start one line for the side in the worse state, naming both languages when they tie: a warning when the maker lists languages without it, a quieter information line when the maker says nothing or only a base model lists it, and no line when the model's own row declares it or BookLoom tested it for that pair; the line SHALL NOT block Start.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d22--quant-and-language-advice`, the owner's decisions that only BookLoom's own tests count as tested and that a warning is shown only for a real gap. In plain words: a Llama model that lists eight languages, none of them Russian or Ukrainian, deserves a warning. A Gemma model whose maker says nothing deserves a quiet hint, and a pair BookLoom measured deserves silence.

#### Scenario: Both languages are missing from the maker's list

- **WHEN** a Russian to Ukrainian project has `llama3.1:8b` chosen
- **THEN** a warning reads "Llama 3.1 does not list Russian and Ukrainian among its languages. Translation may have problems." and Start stays available

#### Scenario: The maker says nothing

- **WHEN** an English to Ukrainian project has `gemma4:12b` chosen, which BookLoom has not tested
- **THEN** a quieter information line reads "The maker of Gemma 4 does not say which languages it supports. Check a few paragraphs first."

#### Scenario: BookLoom tested the pair

- **WHEN** an English to Ukrainian project has `gemma4:e4b` chosen
- **THEN** no language line is shown

#### Scenario: A pair BookLoom did not test keeps its line

- **WHEN** a Russian to Ukrainian project has `gemma4:e4b` chosen
- **THEN** the information line "The maker of Gemma 4 does not say which languages it supports. Check a few paragraphs first." is shown

#### Scenario: Only the source side is missing

- **WHEN** a Latin to Ukrainian project has `mistral-small3.2:24b` chosen, which lists Ukrainian but not Latin
- **THEN** the warning names Latin only: "Mistral Small 3.2 does not list Latin among its languages. Translation may have problems."

#### Scenario: A base model's list is named

- **WHEN** a Russian to Ukrainian project has a Hermes 4 14B download chosen and its base model Qwen3 lists both languages
- **THEN** an information line reads "Hermes 4 does not list its languages; its base model Qwen3 lists Russian and Ukrainian."

### Requirement: Show a quant line at Start for a model that needs one

WHEN a translation is about to start, the application SHALL show under the estimate one line with the sentence of the model's quant tier when the tier is poor, unusable or not supported, or acceptable on a small model of at most 5B, and SHALL NOT show it where BookLoom measured that model at that quant; the line SHALL NOT block Start.

**Source**: `docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `design.md#d22--quant-and-language-advice`. In plain words: a small model at four bits drifts on names and endings, so the person is told once before spending hours. Where BookLoom has itself measured the model at that quant, the warning would only be noise.

#### Scenario: A small model at four bits

- **WHEN** a Russian to Ukrainian project is about to start on `llama3.2:3b` (`Q4_K_M`)
- **THEN** a line under the estimate reads "Slightly reduced precision. Usually fine, but names and word endings may drift, especially for Ukrainian; a larger version translates more reliably."

#### Scenario: A poor quant

- **WHEN** Start is about to run on `phi4-mini` at `Q4_0`
- **THEN** the line carries the poor-quality sentence

#### Scenario: A medium model at four bits shows no line

- **WHEN** Start is about to run on a 12B model at `Q4_K_M`
- **THEN** no quant line is shown

#### Scenario: A measured model shows no line

- **WHEN** Start is about to run on `gemma4:e4b-mlx`, whose `nvfp4` quant BookLoom measured
- **THEN** no quant line is shown, while the chip and the Models tab sentence stay

### Requirement: Show the thinking hint for a download that cannot turn thinking off

WHERE an Ollama download belongs to a model family whose library model has a thinking switch but the download has none (its `/api/show` shows no renderer and no thinking capability), the Models tab and Start SHALL show the hint "This download cannot turn thinking off in {engine}, so it is slower. The library model {libraryTag} or a Modelfile with RENDERER and PARSER fixes it." and SHALL NOT block.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: some downloads think at length and cannot be told to stop, only because Ollama serves them without the library model's switch. The person is told why the run is slow and how to get a model that behaves. A model that always thinks by design, such as gpt-oss or a reasoning build, gets no such hint, because nothing fixes it; its Thinking row says so instead.

#### Scenario: The hint on the Models tab

- **WHEN** the Models tab shows an Ollama download of Qwen3.5 9B that cannot turn thinking off
- **THEN** it shows "This download cannot turn thinking off in Ollama, so it is slower. The library model qwen3.5:9b or a Modelfile with RENDERER and PARSER fixes it."

#### Scenario: The same hint at Start

- **WHEN** Start is about to run on the same download
- **THEN** the same hint is shown as a plain line under the estimate and Start stays available

#### Scenario: A model that can switch thinking off shows no hint

- **WHEN** the library model `qwen3.5:9b` is chosen
- **THEN** no such hint is shown

#### Scenario: A model that always thinks by design shows no hint

- **WHEN** `gpt-oss:20b` on Ollama, or `mistralai/ministral-3-14b-reasoning` on LM Studio, is chosen
- **THEN** no such hint is shown

### Requirement: Show the licence note of a model with a restricted licence

WHERE a model's licence allows only noncommercial use, the Models tab and Start SHALL show the line "{model} is licensed for noncommercial use only (PolyForm Strict 1.0.0)." and SHALL NOT block.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a person who translates a book to publish it should hear about a licence limit before the run, not after.

#### Scenario: The note on the Models tab

- **WHEN** `hf.co/bartowski/thomsonreuters_Thomson-1.0-Small-GGUF` is chosen
- **THEN** the Models tab shows "Thomson 1.0 Small is licensed for noncommercial use only (PolyForm Strict 1.0.0)."

#### Scenario: The same note at Start

- **WHEN** Start is about to run on that model
- **THEN** the same line is shown under the estimate and Start stays available

#### Scenario: An open licence shows nothing

- **WHEN** `gemma4:e4b` is chosen
- **THEN** no licence line is shown

### Requirement: Date the model facts at the foot of the Models tab

The Models tab SHALL show at its foot "Model facts as of {date}", the date of the model knowledge shipped with the application.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: the advice on this tab comes from tables shipped with the program. A person must be able to tell how old they are.

#### Scenario: The date is shown

- **WHEN** the Models tab is opened and the shipped model knowledge is dated 2026-10-28
- **THEN** its foot reads "Model facts as of 2026-10-28"

### Requirement: Recommend a model by the computer's memory where models are chosen

WHERE the provider is Ollama's own connection or a server detected as Ollama, the application SHALL mark as recommended, in the note under the model chooser and at Start, `gemma4:e4b` under 24 GB of memory and `gemma4:26b-a4b` from 24 GB, SHALL offer `gemma4:31b` as an opt-in "best quality, slower, not measured here" from 36 GB, and SHALL offer the `ollama pull` command as text and never run it; for any other provider it SHALL show no such note.

**Source**: `docs/spec_baseline/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a bigger model that does not fit in memory is slower than a smaller one that does. The computer's memory picks the default, and the person pulls the model themselves. The pull command is Ollama's, so under LM Studio or another server it would tell the person to run something wrong.

#### Scenario: A 16 GB computer

- **WHEN** the model chooser is shown on a computer with 16 GB of memory
- **THEN** the note names `gemma4:e4b` as recommended and shows the text `ollama pull gemma4:e4b`

#### Scenario: A 32 GB computer

- **WHEN** the model chooser is shown on a computer with 32 GB of memory
- **THEN** the note names `gemma4:26b-a4b` as recommended

#### Scenario: A 48 GB computer

- **WHEN** the model chooser is shown on a computer with 48 GB of memory
- **THEN** `gemma4:26b-a4b` is recommended and `gemma4:31b` is offered as "best quality, slower, not measured here"

#### Scenario: Nothing is pulled

- **WHEN** the pull command is shown
- **THEN** BookLoom runs nothing and starts no download

#### Scenario: No pull line for LM Studio

- **WHEN** the model-pick card or the model chooser is shown for the provider `lmstudio` on a computer with 16 GB of memory
- **THEN** no model is marked as recommended and no `ollama pull` text is shown

### Requirement: Show what a model offers for thinking and let the person override it

The Models tab SHALL show for the chosen model a Thinking row that says what thinking control the model offers and that BookLoom sends thinking off on every call, and SHALL offer "Recommended (off)", "Allow on proofreading and names" and "Always"; WHERE the model has no thinking control, or always thinks, the row SHALL say so, name the level BookLoom sends if any, and offer no choice.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`, the owner's decision that thinking and sampling are set per model on the Models tab. In plain words: thinking helps some tasks and slows all of them, and it differs from model to model. So the choice lives with the model, and the safe default is off.

#### Scenario: A model with a switch

- **WHEN** the Models tab is opened on `gemma4:e4b-mlx`, which offers thinking on and off
- **THEN** the Thinking row says it can be turned on and off, says BookLoom sends off on every call, and has "Recommended (off)" selected

#### Scenario: The override is stored

- **WHEN** "Allow on proofreading and names" is chosen for `gemma4:e4b-mlx` and Save is used
- **THEN** the choice is read again after a restart and applies from the next request to that model

#### Scenario: Another model keeps its own choice

- **WHEN** "Always" is stored for `gpt-oss:20b` and `gemma4:e4b-mlx` is opened
- **THEN** the Thinking row of `gemma4:e4b-mlx` still shows "Recommended (off)"

#### Scenario: A model with no thinking control

- **WHEN** the Models tab is opened on `qwen2.5:1.5b`, whose capabilities list no thinking
- **THEN** the Thinking row says the model has no thinking control and offers no choice

#### Scenario: A model that always thinks at a level

- **WHEN** the Models tab is opened on `gpt-oss:20b`, whose levels are `low`, `medium` and `high`
- **THEN** the Thinking row reads "Always thinks; BookLoom sends the lowest level, low." and offers no choice

#### Scenario: A model that always thinks with no level

- **WHEN** the Models tab is opened on `mistralai/ministral-3-14b-reasoning`, whose LM Studio reasoning options are `["on"]`
- **THEN** the Thinking row reads "Always thinks; this cannot be changed." and offers no choice

### Requirement: Show BookLoom's sampling choice for a model and let the person override it

The Models tab SHALL show for the chosen model a Sampling row that names what BookLoom sends (the temperature per call kind and the samplers its profile sets) and SHALL offer two choices, Profile default and Custom, Custom with fields for temperature, top_k, top_p and min_p where an empty field keeps the profile's value and a filled temperature is the tab's temperature choice My value.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`, the owner's decision that thinking and sampling are set per model on the Models tab. In plain words: sampling decides how adventurous the wording is. The profile's values are right for most models, and a person with a reason can type their own. There is no "server's own" choice: on LM Studio, llama-server and Ollama's OpenAI path the server's defaults are not neutral, so leaving samplers out would bring back the values the inference capability's "Choose sampling from the model profile" keeps out. The temperature field and the temperature choice already on the tab are one stored value, so they cannot disagree.

#### Scenario: The default is shown

- **WHEN** the Models tab is opened on a model with no stored choice
- **THEN** the Sampling row names the temperature per call kind and the samplers BookLoom sends, with Profile default selected

#### Scenario: Only two choices are offered

- **WHEN** the Sampling choice is opened for `gemma4:e4b-mlx`
- **THEN** it offers Profile default and Custom, and no choice that leaves sampling to the server

#### Scenario: Custom values are typed

- **WHEN** Custom is chosen with top_k `40` and top_p `0.9`, and temperature and min_p left empty
- **THEN** the row shows `top_k 40` and `top_p 0.9` as what every request to that model will carry, the profile's min_p, and the temperature per call kind unchanged

#### Scenario: A custom temperature is the temperature choice

- **WHEN** Custom is chosen with temperature `0.5` and saved
- **THEN** the tab's temperature choice reads My value `0.5`

#### Scenario: An invalid custom value holds Save

- **WHEN** Custom is chosen with top_p `1.5`
- **THEN** Save is held and the tab asks for a top_p from 0 to 1

### Requirement: Name an engine's address form in the address hint for llama-server

WHERE the provider is treated as llama-server, the address field SHALL show the hint `http://127.0.0.1:9931/v1`.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: llama-server has no fixed port and its address ends in `/v1`. A hint shows the shape of a working address.

#### Scenario: The hint appears for llama-server

- **WHEN** a provider is added and treated as llama-server
- **THEN** its empty address field shows the hint `http://127.0.0.1:9931/v1`

#### Scenario: Another engine keeps its own hint

- **WHEN** a provider is treated as LM Studio
- **THEN** its address hint is not `http://127.0.0.1:9931/v1`

### Requirement: Show the detected server engine under the provider's address

The Providers form SHALL show under the address one line naming the detected engine: "Detected: {engine} {version} · {connection}" for a recognised server, or "Not recognised: generic OpenAI-compatible server".

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Ollama, LM Studio and llama-server all speak a similar dialect but need different requests. The person sees which one BookLoom thinks is answering, and a wrong guess is visible. Detection itself is the llm-provider capability's "Detect the server engine only when the person saves, tests or asks"; a provider saved as Ollama that answers as another engine fails its test with the message of the llm-provider capability's "Fail the test of an Ollama provider whose server is another engine".

#### Scenario: Ollama is recognised

- **WHEN** Save is used on the address `http://127.0.0.1:11434` and Ollama 0.40.11 answers
- **THEN** the line reads "Detected: Ollama 0.40.11 · native connection"

#### Scenario: llama-server and LM Studio are recognised

- **WHEN** `http://127.0.0.1:9931/v1` answers as llama-server build b11669, and `http://127.0.0.1:1234/v1` answers as LM Studio
- **THEN** the lines read "Detected: llama-server b11669 · OpenAI-compatible" and "Detected: LM Studio · OpenAI-compatible"

#### Scenario: An unknown server

- **WHEN** the address answers like an OpenAI-compatible server that matches no known engine
- **THEN** the line reads "Not recognised: generic OpenAI-compatible server"

### Requirement: Let the person detect the engine again

WHEN the person uses "Detect again" beside the detected-engine line, the application SHALL ask the server again, SHALL update the line from the answer, and SHALL send no model request.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: after the person upgrades or swaps the program behind an address, one press makes BookLoom look again. It costs no model time.

#### Scenario: The engine changed behind the address

- **WHEN** the line reads "Detected: Ollama 0.40.11 · native connection", the program behind the address is replaced by llama-server b11669 and "Detect again" is used
- **THEN** the line reads "Detected: llama-server b11669 · OpenAI-compatible"

#### Scenario: Detecting again is allowed while a run holds the model

- **WHEN** a translation is running and "Detect again" is used
- **THEN** the line is updated and no "BookLoom is translating" message is shown

#### Scenario: The server does not answer

- **WHEN** "Detect again" is used and nothing answers at the address
- **THEN** the line keeps its last text and the screen says the server does not answer

### Requirement: Let the person name the engine of a server

The Providers form SHALL offer "Treat this server as…" listing the engines Ollama, LM Studio, llama-server, Unsloth, OpenRouter and generic OpenAI-compatible, SHALL show the stored choice as chosen, and SHALL NOT offer it for an app the llm-provider capability refuses as one that cannot translate a book.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: detection can be wrong behind a proxy. The person can say what the server really is; what the choice does to the requests, and that it lasts until Detect again, is the llm-provider capability's "Let the person choose the engine with Treat this server as…", and the apps it refuses are its "Refuse an app that cannot translate a book at the connection test".

#### Scenario: A choice is stored

- **WHEN** a provider behind a reverse proxy is not recognised and "Treat this server as…" is set to llama-server and saved
- **THEN** after a restart the form shows llama-server chosen under "Treat this server as…"

#### Scenario: The list of engines

- **WHEN** the choice is opened
- **THEN** it lists Ollama, LM Studio, llama-server, Unsloth, OpenRouter and generic OpenAI-compatible

#### Scenario: No offer for an unusable app

- **WHEN** the provider's address is AnythingLLM's `http://127.0.0.1:3001/api/v1/openai`
- **THEN** "Treat this server as…" is not offered

### Requirement: Offer Ollama's own connection once when Ollama is behind an OpenAI-compatible provider

WHEN the llm-provider capability makes its one offer of Ollama's own connection, the Providers form SHALL show it inline as "This is Ollama. Use Ollama's own connection (recommended)? It keeps the window, overflow and sampling controls." with the buttons Use it and Keep as is.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Ollama's own connection lets BookLoom set the window and handle overflow, and the OpenAI-style one does not. The person is told, and the choice stays theirs. When the offer is made, that it is made only once and that the provider never switches by itself are the llm-provider capability's "Offer Ollama's own connection once when Ollama answers behind an OpenAI-compatible provider".

#### Scenario: The offer is shown

- **WHEN** Test is used on a provider saved as OpenAI-compatible at `http://127.0.0.1:11434/v1` and Ollama answers
- **THEN** the inline offer is shown with Use it and Keep as is, and the provider's kind is unchanged

#### Scenario: Use it switches the connection

- **WHEN** Use it is chosen
- **THEN** the provider uses Ollama's own connection and the offer is gone

#### Scenario: Keep as is is remembered

- **WHEN** Keep as is is chosen and Test is used again
- **THEN** the offer is not shown again for that provider

#### Scenario: Detect again keeps the offer declined

- **WHEN** Keep as is was chosen and Detect again is used
- **THEN** the offer is not shown and "Treat this server as…" shows no engine chosen by the person

### Requirement: Say that LM Studio has no model with the typed id

WHEN the llm-provider capability's id check stops a test, or ends the first call of a run (which pauses), because LM Studio does not list the model id (reason `model-not-listed`), the application SHALL show `LM Studio has no model "{id}". It would answer with the model that is loaded. Pick a model from the list.`

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: LM Studio answers with whatever is loaded when the id is wrong, so a book could be translated by the wrong model without anyone noticing; the screen says why it stopped. The check itself is the llm-provider capability's "Check the model id against the server's own list where any id is served".

#### Scenario: An unknown id

- **WHEN** Test inference is used on LM Studio with the id `gemma-4-e4b-typo` and the server lists no such id
- **THEN** the test stops and the screen reads `LM Studio has no model "gemma-4-e4b-typo". It would answer with the model that is loaded. Pick a model from the list.`

#### Scenario: A listed id passes

- **WHEN** the id is `google/gemma-4-e4b` and the server lists it
- **THEN** no such message is shown

### Requirement: Say that a typed LM Studio variant was removed

WHEN the llm-provider capability removes a typed `@variant` from an LM Studio model id (after a passing models check or Save, not while typing), the Providers form SHALL say once `"@{variant}" was removed from the model id: it would load a second copy with default settings.`

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: the person typed something and BookLoom changed it, so the screen says what was changed and why. The removal itself is the llm-provider capability's "Never send an LM Studio quant variant".

#### Scenario: A variant is removed

- **WHEN** the typed id is `google/gemma-4-e4b@q4_k_m` and the server lists `google/gemma-4-e4b`
- **THEN** the id becomes `google/gemma-4-e4b` and the screen reads `"@q4_k_m" was removed from the model id: it would load a second copy with default settings.`

### Requirement: Say at Start that LM Studio will load a model with its own settings

WHEN a run has just started on an LM Studio model that is not loaded, the application SHALL show the line "LM Studio will load it with its own default settings; the window may differ." as an information toast (never blocking).

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: LM Studio loads a model on first use with settings of its own, so the window BookLoom budgets for may not be the one in use. The person hears it as the run starts.

#### Scenario: Toast shown after Start

- **WHEN** a run has just started on the LM Studio model `google/gemma-4-e4b` and it is not loaded
- **THEN** the line "LM Studio will load it with its own default settings; the window may differ." appears as an information toast and the run is not held

#### Scenario: A loaded model shows no such line

- **WHEN** the same model is loaded
- **THEN** the line is not shown

### Requirement: Run the optional Advanced features check for a model

The Providers test SHALL offer the optional stage "Advanced features (optional, uses the model)" for the selected model, never started by the other stages, and SHALL show one verdict line per check it ran, also on the Models tab of that model.

**Source**: `docs/spec_baseline/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#three-stage-verification`, `design.md#d17--server-engines-detection-and-traits`. In plain words: a server of unknown make may or may not honour structured answers, a thinking switch, extra samplers or an answer cap. A few short, invented-text calls tell, and the answer is remembered. They are optional because they use the model. Which checks are sent and how the verdicts are stored is the llm-provider capability's "Check what a server honours only when the person asks".

#### Scenario: Verdicts are listed

- **WHEN** the Advanced features stage runs for `qwen3.5-9b` on a generic server
- **THEN** the lines read "Structured answers: honoured", "Turning reasoning off: works with `reasoning_effort`", "top_k/min_p: ignored" and "Answer cap: `max_tokens`"

#### Scenario: The stage is optional

- **WHEN** Test connection, Test models and Test inference are used without the Advanced features stage
- **THEN** no Advanced features call is sent

#### Scenario: Verdicts survive a restart

- **WHEN** the stage ran for `qwen3.5-9b` and the application is restarted
- **THEN** the Models tab still shows the verdicts for that model

#### Scenario: A run holds the model

- **WHEN** a translation is running and the stage is started
- **THEN** the stage is refused before any call is sent, the same way as Test inference, and runs once the run is paused

### Requirement: Say that a check is not started while a run holds the model

WHILE a translation run is translating, the Providers screen SHALL refuse Test inference and the Advanced features stage before any model call is sent and SHALL show "BookLoom is translating. Try again when the run ends or while it is paused."; WHILE the run is paused, stopped or ended, both SHALL run.

**Source**: `docs/spec_baseline/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#three-stage-verification`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: a local model answers one call at a time, so BookLoom already refuses a model check while a run translates and lets it run while the run is paused. Two things change: the sentence the Providers screen shows (it read "Unavailable while “Translation” is running. Wait for it to finish or stop it.") and the new Advanced features stage, which is refused the same way. The other model actions keep the `app-shell` capability's "Disable model actions while a translation is at work".

#### Scenario: Test inference during a run

- **WHEN** a translation is running with `gemma4:e4b` and Test inference is used
- **THEN** no request reaches the model and the screen shows "BookLoom is translating. Try again when the run ends or while it is paused."

#### Scenario: A pause frees the model

- **WHEN** the run is paused and Test inference is used
- **THEN** the test runs

#### Scenario: Connection tests are not model calls

- **WHEN** a translation is running and Test connection or Test models is used
- **THEN** the test runs and no "BookLoom is translating" message is shown

### Requirement: Show the privacy line of a provider with its host

The Providers area SHALL show for a selected provider whose host is not a loopback address (`127.0.0.1`, `::1` or `localhost`) "Your book stays private when you use a local model. With a provider outside this computer, the text you translate is sent to {host}.", for a loopback host only its first sentence, and no consent dialog or acknowledgement checkbox for any provider.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`, the owner's decision that privacy is stated as "private when used with local models" and that no consent is asked. In plain words: BookLoom keeps books on the machine only when the model is local. For any other provider it says where the text goes, and leaves the choice to the person.

#### Scenario: A provider outside this computer

- **WHEN** the OpenRouter provider at `openrouter.ai` is selected
- **THEN** the line reads "Your book stays private when you use a local model. With a provider outside this computer, the text you translate is sent to openrouter.ai."

#### Scenario: A local provider shows its own host

- **WHEN** the Ollama provider at `127.0.0.1:11434` is selected
- **THEN** the line reads "Your book stays private when you use a local model." and names no host

#### Scenario: Saving a remote provider asks nothing

- **WHEN** the OpenRouter provider is saved and made current
- **THEN** no consent dialog and no acknowledgement checkbox is shown

### Requirement: Note that LM Studio keeps request text in its own log

WHERE the selected provider is LM Studio, the Providers area SHALL show "LM Studio keeps request text in its own server log unless you turn that off in LM Studio."

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: the book's text stays on the machine, but LM Studio may write it to a log file there. The person is told, and can turn that off in LM Studio.

#### Scenario: The note is shown for LM Studio

- **WHEN** the LM Studio provider is selected
- **THEN** the note is shown under the privacy line

#### Scenario: The note is not shown for Ollama

- **WHEN** the Ollama provider is selected
- **THEN** the note is not shown

### Requirement: Help the person set up an Unsloth server

WHERE the provider is treated as Unsloth, the Providers form SHALL show the key hint "Create a key in Unsloth › Settings › API and put its environment-variable name here, for example UNSLOTH_API_KEY.", the line "Unsloth's own chat tools (web search, code) are separate from BookLoom; BookLoom turns them off on every request.", and, WHEN the sign-in is refused, the sentence "Unsloth needs an API key. Create one in Unsloth › Settings › API and put its environment-variable name here."

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Unsloth needs a key and has its own chat tools. The form says where to make the key, that the key is named by a variable and never typed in, and that BookLoom keeps the tools out of the translation.

#### Scenario: The hint and the tools note

- **WHEN** a provider is treated as Unsloth
- **THEN** the form shows the key hint and the tools note

#### Scenario: A refused sign-in names the fix

- **WHEN** Test connection on the Unsloth provider is answered with HTTP 401
- **THEN** the result carries "Unsloth needs an API key. Create one in Unsloth › Settings › API and put its environment-variable name here."

#### Scenario: Another engine shows neither

- **WHEN** a provider is treated as Ollama
- **THEN** the key hint and the tools note are not shown

### Requirement: Say in one sentence why an app cannot be used

WHEN the llm-provider capability refuses an app at the connection test, the application SHALL show one plain sentence for its reason: "This app does not support structured replies, so BookLoom cannot use it." for `not-suitable-structured`, or "This address chats with documents, not with a model." for `not-suitable-documents`.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: some desktop apps look like an AI server but cannot do the job. A person told why gives up quickly and does not hunt through error codes. How the apps are recognised is the llm-provider capability's "Refuse an app that cannot translate a book at the connection test".

#### Scenario: GPT4All

- **WHEN** Test connection reaches GPT4All at `http://127.0.0.1:4891/v1`, whose model rows carry `"owned_by":"humanity"`
- **THEN** the result reads "This app does not support structured replies, so BookLoom cannot use it."

#### Scenario: AnythingLLM

- **WHEN** Test connection reaches AnythingLLM at `http://127.0.0.1:3001/api/v1/openai`
- **THEN** the result reads "This address chats with documents, not with a model."

#### Scenario: A usable server passes

- **WHEN** Test connection reaches a llama-server
- **THEN** neither sentence is shown

### Requirement: Understand a pasted model name in the Add a model id box

WHEN a Hugging Face URL, an `ollama run`, `llama-server -hf` or `lms get` command, a file name or a repository id is pasted into the "Add a model id" box, the application SHALL show one line under it saying what was understood, `Understood: Hugging Face repo {repo}, file {file} → {model} · {quant}`, SHALL never fetch the URL, and WHERE the id is not in the server's list SHALL add "Not in this server's list yet."

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d19--model-identity-reading-model-names`. In plain words: people copy a model's link or its pull command from a web page. BookLoom reads it, says what it made of it, and never goes to the internet to check.

#### Scenario: A repository and a file

- **WHEN** `https://huggingface.co/unsloth/GLM-4.7-Flash-GGUF/blob/main/GLM-4.7-Flash-UD-Q4_K_XL.gguf` is pasted
- **THEN** the line reads `Understood: Hugging Face repo unsloth/GLM-4.7-Flash-GGUF, file GLM-4.7-Flash-UD-Q4_K_XL.gguf → GLM 4.7 Flash · UD-Q4_K_XL` and the URL is not requested

#### Scenario: An Ollama pull command is offered as text

- **WHEN** the same paste is made for an Ollama provider
- **THEN** the box offers the text `ollama pull hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL` and runs nothing

#### Scenario: An id the server does not list

- **WHEN** the pasted id is not in the server's model list
- **THEN** the line ends with "Not in this server's list yet."

#### Scenario: Plain model ids pass through

- **WHEN** `gemma4:e4b` is typed
- **THEN** it is accepted as the id as before

### Requirement: Show what each quality choice does on Generation

The Generation tab SHALL show what Fast, Balanced and Max do as a read-only table with the rows "Fixing calls", "Proofread" and "Extra model calls", SHALL say under it "The same for every model.", and SHALL offer no Thinking and no Sampling choice.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#generation-tab`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`, the owner's decision that the dial means the same for every model. In plain words: the person sees what Fast, Balanced and Max do, and what each costs in calls, without being able to break them. Thinking and sampling moved to the model they belong to.

#### Scenario: The rows and their cells

- **WHEN** the Generation tab is opened
- **THEN** Fixing calls reads "none, marks for review (lost formatting marks retried once)" for Fast and "one repair per piece" for Balanced and Max
- **AND** Proofread reads "marks for review, no model calls" for Fast, "marked and doubtful windows" for Balanced and "marked and doubtful windows, then the whole book re-read" for Max

#### Scenario: Extra model calls count calls, not hours

- **WHEN** the Generation tab is opened and the current model's batch size is 4
- **THEN** Extra model calls reads "no extra calls" for Fast, "about one call per marked passage" for Balanced and "about one call per marked passage, plus one call per 4 paragraphs of the whole book" for Max

#### Scenario: The same for every model

- **WHEN** the Generation tab is opened
- **THEN** the line "The same for every model." is shown under the table and none of its cells can be edited

#### Scenario: Thinking and Sampling are not on Generation

- **WHEN** the Generation tab is opened
- **THEN** it holds no Thinking before answering choice and no Sampling choice

#### Scenario: The old rows are gone

- **WHEN** the Generation tab is opened
- **THEN** the table has no row for the reviewer, the backward read or a model-written summary

### Requirement: Show the time limits on Generation as fixed values

The Generation tab SHALL list the time limits as read-only rules: a prescan waits 120 s, longer for a model that always thinks; every other call waits 0.5 s per expected output token, at least the provider's request timeout and at most 600 s; a stream that sends nothing for 60 s is stopped. It SHALL offer no control to change them.

**Source**: `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#generation-tab`, S4 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`). In plain words: how long BookLoom waits for each kind of call follows fixed rules, and the tab says what they are rather than offering a knob that is not there. The rules themselves are the llm-provider capability's "Wait for a chat call by its kind, its expected output and its model" and "Read an Ollama-native reply as a stream that must keep arriving".

#### Scenario: The limits cannot be edited

- **WHEN** the Generation tab is opened
- **THEN** it lists "Prescan: 120 s (longer for a model that always thinks)", "Other calls: 0.5 s per expected token, at least the provider's request timeout, at most 600 s" and "Silence while streaming: 60 s", and none can be edited

### Requirement: Report what the program says about a model, engine by engine

The Models tab SHALL show, as found out from the program, how much the model can read at once, who sets that window on this engine, how much is loaded now where the program says so, and for each kind of call whether structured answers come as a schema, as plain JSON or not at all, each from a test or from a refusal. It SHALL NOT store what it read from a server.

**Source**: decision 6 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#models-decision-6`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: before changing a number, a person sees what the model itself reports about its limits and who controls them. The structured-answer row follows what BookLoom learned for each kind of call, since a server may take a schema for one and refuse it for another.

#### Scenario: Reported limits are shown

- **WHEN** `gemma4:e4b` reports a context length of 131072 and 16384 loaded
- **THEN** the Models tab shows both figures under "Found out from the program"

#### Scenario: Ollama through the OpenAI-compatible route says who sets the window

- **WHEN** the model belongs to an Ollama provider reached through `/v1`
- **THEN** the Context row reads "Window: set in Ollama, not by BookLoom"

#### Scenario: LM Studio shows the loaded figure

- **WHEN** `google/gemma-4-e4b` is loaded in LM Studio with a window of 8192
- **THEN** the Context row reads "loaded in LM Studio: 8,192"

#### Scenario: llama-server shows its start setting

- **WHEN** the model is served by llama-server started with 16384 tokens per slot
- **THEN** the Context row reads "set at llama-server start: 16,384 per slot"

#### Scenario: A generic server gets no loaded figure

- **WHEN** the model belongs to a generic OpenAI-compatible server
- **THEN** the Models tab shows only how much it can read at once and no loaded figure, and no `/api/ps` request is sent

#### Scenario: Structured answers are shown per kind of call

- **WHEN** a test found that `gpt-oss:20b` honours a schema, and Name reading was refused a schema and now uses plain JSON
- **THEN** the Models tab shows Translate as a schema and Name reading as plain JSON

#### Scenario: Nothing is known yet

- **WHEN** no test of `gemma4:e4b` has run and nothing was refused since the application started
- **THEN** the Models tab reads "structured answers: not checked yet" and names the Providers tab test that checks it

### Requirement: Choose when to stop and ask, and the defaults, on Automation

The Automation tab SHALL offer When to stop and ask you, a default quality, and switches to open the review desk when a translation finishes with segments to review, to Keep the computer awake (on by default) and to Reopen the last project (on by default), and SHALL NOT offer a switch for operating-system notifications or for a last check before saving.

**Source**: S4 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/spec_baseline/specification/01_Product/07_SETTINGS.md#automation-tab`, ADR-0045, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: how often BookLoom interrupts, and what it does at the end of a run, is the person's choice. Notifications are not offered because BookLoom sends none. The last-check switch is gone because export no longer makes a model call. What the window opens at launch with Reopen the last project on is the `app-shell` capability's rule.

#### Scenario: The stop-and-ask choice is stored

- **WHEN** After every segment is chosen
- **THEN** the next run pauses after each decided segment

#### Scenario: No notification switch

- **WHEN** the Automation tab is opened
- **THEN** it holds no switch for operating-system notifications

#### Scenario: No last-check switch

- **WHEN** the Automation tab is opened
- **THEN** it holds no switch for running a last check before saving

#### Scenario: Reopen the last project is a stored switch

- **WHEN** Reopen the last project is switched off and the application is closed and opened again
- **THEN** the Automation tab shows Reopen the last project switched off

#### Scenario: An explicit launch value is named on Automation

- **WHEN** BookLoom was launched with `BOOKLOOM_REVIEW_MODE=unattended` and the Automation tab is opened
- **THEN** a note under When to stop and ask you says `BOOKLOOM_REVIEW_MODE` is set for this launch, so it decides this, and that the choice made here is kept for launches without it

#### Scenario: A new book takes the default quality

- **WHEN** Max quality is chosen as the default quality for new books and a book is opened that has no stored project, or whose stored project has no target language yet (it is created again from the book)
- **THEN** that project's Book Brief holds Max quality, and a stored project that already has a target language keeps the quality its own Book Brief holds

### Requirement: Apply the Appearance, Automation and Storage tabs at once and say when

The Appearance, Automation and Storage & logs tabs SHALL apply a change at once and SHALL say when it takes effect: "Changes apply at once", "to the segments not translated yet", or "after BookLoom restarts".

**Source**: theme H, option H1 (`openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/proposal.md`), `docs/spec_baseline/specification/mockups/README.md#settings--s1s5-h1-decisions-67`. In plain words: a switch takes effect when flipped, and the screen is honest about the cases where the next launch is what changes. The Generation tab no longer holds a setting to flip, so it is not in this list.

#### Scenario: A theme change applies at once

- **WHEN** the dark theme is chosen on Appearance
- **THEN** the window turns dark immediately and the tab says "Changes apply at once"

#### Scenario: A language change waits for a restart

- **WHEN** Ukrainian is chosen as the language on Appearance
- **THEN** the tab says the change applies "after BookLoom restarts"
