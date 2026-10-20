## ADDED Requirements

### Requirement: Recognise a refused setting only when the request sent it

The system SHALL recognise as refused only an optional setting the request sent — the temperature, `top_p`, `top_k`, `min_p`, a repeat penalty, `seed`, the output-length field, `num_ctx`, the reasoning control or the structured-output format — named by the body of an HTTP `400` or `422` reply; IF a `400` names nothing the request sent, THEN the system SHALL answer `ErrorCode.validation`, repeat nothing and remember nothing.

**Source**: FR-INFER-09e (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`), S6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#refused-settings-s6`. In plain words: BookLoom drops a setting only when the server's own words name a setting BookLoom sent, so an unrelated 400 is never papered over or guessed at.

#### Scenario: A refused temperature is recognised

- **WHEN** an OpenAI-compatible server answers `400` with the message `Unsupported value: 'temperature' does not support 0.2 with this model. Only the default (1) value is supported.` to a request carrying `"temperature":0.2`
- **THEN** the refusal of `temperature` is recognised and the next request has the same messages and no `temperature` key

#### Scenario: A 422 is handled like a 400

- **WHEN** a server answers `422` with the message `unknown field top_p` to a request carrying `"top_p":0.9`
- **THEN** the next request has no `top_p` key

#### Scenario: A 400 that names nothing sent stays an error

- **WHEN** a server answers `400` with the message `invalid request` to a request that carried the temperature `0.2`
- **THEN** the result is `ErrorCode.validation`, the server received 1 request and nothing is remembered

#### Scenario: A setting that was not sent is not dropped

- **WHEN** a server answers `400` with the message `unsupported parameter: top_k` to a request that carried no `top_k`
- **THEN** the result is `ErrorCode.validation` and nothing is remembered

### Requirement: Keep everything else when a setting is dropped, and do not count the repeat as an attempt

A request sent again without a refused setting SHALL keep everything else it carried — its messages, its other settings, its context size and the output it expects — and that repeat SHALL NOT count as one of the retry policy's attempts.

**Source**: FR-INFER-09, FR-INFER-09e (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#effective-context`, `docs/specification/mockups/README.md#refused-settings-s6`. In plain words: dropping one knob must not quietly change the rest, such as shrinking Ollama's context back to its small default. Dropping a refused setting corrects the request; it is not a failure, so it must not use up one of the three tries. The `llm-provider` capability's retry rule defers to this one.

#### Scenario: Only the refused setting disappears

- **WHEN** a server refuses `top_p` in a request carrying `"temperature":0.2`, `"top_p":0.9` and 2 messages
- **THEN** the next request carries `"temperature":0.2` and the same 2 messages and no `top_p`

#### Scenario: The repeat is not an attempt

- **WHEN** the server answers `400` naming the sent setting `temperature`, then `503` twice, then `200`
- **THEN** the call succeeds after 4 requests

### Requirement: Remember a refused setting for the provider and model

WHEN a setting is refused and dropped, the system SHALL remember the refusal for that provider and that model across restarts, and SHALL leave the setting out of the first attempt of every later call to that model until the person chooses Send again.

**Source**: FR-INFER-09e (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`), S6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#refused-settings-s6`. In plain words: a model that rejects `temperature` once will reject it every time. Remembering saves a failed request at the start of every later call, and the person can undo it from Settings › Models.

#### Scenario: A later call leaves the setting out from the start

- **WHEN** `gpt-oss:20b` on the provider `office` refused `temperature` and the next draft call to `gpt-oss:20b` is made
- **THEN** its first request carries no `temperature` key and the server receives 1 request for it

#### Scenario: A refusal is remembered after a restart

- **WHEN** `gpt-oss:20b` on the provider `office` refused `temperature` and the application is restarted
- **THEN** the first request to `gpt-oss:20b` after the restart carries no temperature and is not rejected

#### Scenario: A refusal belongs to one model

- **WHEN** `gpt-oss:20b` refused `temperature` and a request goes to `gemma4:e4b` on the same provider
- **THEN** the request to `gemma4:e4b` still carries its temperature

### Requirement: Say a refusal once, quietly

WHEN a setting is refused and dropped, the system SHALL write one warning line to the activity log naming the setting and what was done instead, and SHALL NOT raise a toast while a run is in progress.

**Source**: S6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#refused-settings-s6`. In plain words: the person watching a long run is not interrupted by a technical detail that BookLoom already handled; it is in the log and the test details for whoever looks.

#### Scenario: One warning line, no toast

- **WHEN** `gpt-oss:20b` refuses `temperature` during a run
- **THEN** the activity log gets one warning line naming `temperature` and saying the request was repeated without it
- **AND** no toast is shown

#### Scenario: A remembered refusal is not announced again

- **WHEN** the next request to `gpt-oss:20b` leaves `temperature` out because of the remembered refusal
- **THEN** no further warning line is written for it

### Requirement: Apply a model's own settings to the segments not translated yet

WHEN a person saves a change to a model's settings (temperature choice, context window, answer length and its field) while a run is in progress, the system SHALL apply the context window from the next chunk that starts and the temperature choice, answer length and its field from the next request, and SHALL leave decided segments unchanged.

**Source**: decision 6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#models-decision-6`. In plain words: changing a setting mid-book steers the rest of the book without re-translating the part already done, and the screen says so ("to the segments not translated yet"). A chunk is packed for its context window when it starts, so the chunk in flight keeps that window; the other settings change nothing about packing and apply from the next request.

#### Scenario: A change saved during a run reaches the next chunk

- **WHEN** segments 1 to 48 are decided, chunk 7 (segments 49 to 56) is being drafted with `"num_ctx":8192`, and the person saves the context window `16384` for `gemma4:e4b`
- **THEN** the remaining calls of chunk 7 carry `"num_ctx":8192`, the first draft call of chunk 8 carries `"num_ctx":16384`, and segments 1 to 48 are unchanged

#### Scenario: A temperature change saved during a run reaches the next request

- **WHEN** chunk 7 is being drafted with the temperature choice "Let BookLoom choose", and the person saves "My value" `0.7` for `gemma4:e4b` between two calls of chunk 7
- **THEN** the next call of chunk 7 carries `"temperature":0.7` and the calls before the save are unchanged

### Requirement: Carry the reasoning level that the Generation setting chooses

Every call SHALL carry the reasoning level that Settings › Generation chooses under "Thinking before answering" — Off, Low or On, Off when nothing is stored — and WHERE the reasoning environment variable `BOOKLOOM_REASONING` (or its launch property) is set, it SHALL override the stored setting.

**Source**: S4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/specification/01_Product/07_SETTINGS.md#generation-tab`. In plain words: reasoning stays off by default because it only costs time and can leave the answer empty, but a person can now turn it on for a model that translates better with it. The evaluation scripts keep steering it with the environment variable. How each server is told the level is the `llm-provider` capability's rule.

#### Scenario: The default is off

- **WHEN** nothing is stored for Thinking before answering, the variable is unset, and a draft call is sent to the provider `lmstudio`
- **THEN** its body has `"reasoning_effort":"none"`

#### Scenario: The stored choice is sent

- **WHEN** Low is stored for Thinking before answering, the variable is unset, and a draft call is sent to the provider `lmstudio`
- **THEN** its body has `"reasoning_effort":"low"`

#### Scenario: The environment variable wins

- **WHEN** Low is stored for Thinking before answering and `BOOKLOOM_REASONING` turns reasoning off for the draft call
- **THEN** the draft request carries reasoning Off

## RENAMED Requirements

- FROM: `### Requirement: Send each call kind at its own temperature with reasoning off`
- TO: `### Requirement: Send each call kind at its own temperature`

## MODIFIED Requirements

### Requirement: Request provider controls without trusting them as guarantees

IF a server rejects a request with HTTP `400` or `422` whose body names an optional setting the request sent, THEN the system SHALL send the request once more without that setting, or, for `max_tokens`, with `max_completion_tokens` in its place, and SHALL record the downgrade without exposing the response body.

**Source**: FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), FR-INFER-09e (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `#effective-context`, ADR-0013, S6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: servers differ in which optional knobs they accept, and a refusal that names the knob is a hint, not a failure, so the request is sent once more without it. Which settings count and what the repeat keeps are stated in this capability's refused-setting requirements.

#### Scenario: An unsupported native thinking control is omitted once

- **WHEN** Ollama rejects a request carrying `"think":false` and the context size `8192`
- **THEN** the next request has the same messages, temperature, response format and `"num_ctx":8192` but no `think` field

#### Scenario: A structured-output rejection downgrades once

- **WHEN** a provider explicitly rejects a JSON schema response format
- **THEN** the next request has no structured-output field and no retry delay

#### Scenario: A request retried without structured output still carries its context size

- **WHEN** Ollama explicitly rejects the JSON-schema format of a draft request that carries the temperature `0.2`, the context size `8192` and 400 expected output tokens, from a provider with the default request timeout
- **THEN** the next request has no `format` field and has `"options":{"temperature":0.2,"num_ctx":8192}`
- **AND** its attempt waits up to 200 seconds, as its expected output gives

#### Scenario: A refused max_tokens becomes max_completion_tokens

- **WHEN** an OpenAI-compatible server answers `400` with the message `Unsupported parameter: 'max_tokens'. Use 'max_completion_tokens' instead.` to a request carrying `"max_tokens":400`
- **THEN** the next request carries `"max_completion_tokens":400` and no `max_tokens` key

### Requirement: Send each call kind at its own temperature

WHERE a model's temperature choice is Let BookLoom choose, the system SHALL send each call at the temperature of its kind: draft `0.2` (`0.1` for a review retry that asks for a lower one), reviewer `0` with a fixed seed, directed fix `0.2`, pre-scan, summary and revision `0.2`. WHERE it is My value, every request SHALL carry that value; WHERE it is Don't send, none SHALL.

**Source**: FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#generation-parameters`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#generation-parameters`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#directed-fix-repair`, S6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: by default every call stays low and near-deterministic for fidelity, a scorer being the steadiest. A person can instead fix one temperature for a model, or send none for models that refuse the setting. The reasoning level is a separate requirement ("Carry the reasoning level that the Generation setting chooses").

#### Scenario: A reviewer call is steadier than a draft

- **WHEN** a Balanced run drafts `ch07.xhtml:41` and then reviews its chunk through the provider `lmstudio` with the choice Let BookLoom choose
- **THEN** the draft request body has `"temperature":0.2` and the reviewer request body has `"temperature":0.0` and `"seed":15`

#### Scenario: A lower-temperature retry

- **WHEN** the person retries the segment `ch05.xhtml:11` in review and asks for a lower temperature
- **THEN** the retry's draft request carries the temperature `0.1`
- **AND** a retry of the same segment without that request carries `0.2`

#### Scenario: A directed fix stays low and reasoning is off

- **WHEN** a directed fix is sent to the provider `ollama` with the choice Let BookLoom choose and nothing stored for Thinking before answering
- **THEN** its body's `options` holds `"temperature":0.2` and the body has `"think":false`

#### Scenario: My value is sent on every request

- **WHEN** the model's choice is My value `0.5` and a draft and a reviewer call are made
- **THEN** both request bodies carry `"temperature":0.5`

#### Scenario: Don't send leaves the temperature out

- **WHEN** the model's choice is Don't send and a draft call is made
- **THEN** the request body has no `temperature` key
