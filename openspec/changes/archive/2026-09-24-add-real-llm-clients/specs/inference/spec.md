## MODIFIED Requirements

### Requirement: Get a chat model by provider id and model id

WHEN a caller asks for a chat model with a provider id and a model id, the system SHALL return a model that answers
an ordered list of system, user and assistant messages. The answer SHALL be reply text plus a finish reason: normal,
cut off by length, or other. Whoever then uses the model never names the provider or the model.

WHEN the provider id is `pseudo`, the model SHALL be the built-in offline pseudo model, whatever the model id.

WHEN the provider id names a known provider description, the model SHALL send every call to that provider in its
dialect, bound to the given model id, through the single-flight gate and the retry policy, and SHALL sanitise every
reply. Creating the model SHALL open no connection; the first call does.

IF the provider id is unknown or the model id is blank, THEN the system SHALL return `ErrorCode.validation` and no
model.

**Source:** FR-INFER-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), FR-PROV-03 (`#fr-prov`),
`02_Architecture/04_LLM_INTEGRATION.md#provider-architecture`, `#chat-contracts`, ADR-0033.
In plain words: the caller that starts a translation picks the provider and the model once, and the translation engine
only talks to the model it is handed. Real providers plug in behind the same call: `ollama` and `lmstudio` work out of
the box, a registered description works the same way, and `pseudo` stays for a run without any server.

#### Scenario: The pseudo provider gives a working model

- **WHEN** a chat model is requested for provider `pseudo` and model `uppercase`
- **THEN** a model is returned
- **AND** when sent the single user message `hello`, it replies `HELLO` with a normal finish

#### Scenario: The Ollama preset gives a model that speaks the native dialect

- **WHEN** a chat model is requested for provider `ollama` and model `gemma4:e4b-mlx`
- **THEN** a model is returned without any request being sent
- **AND** its first call posts to `http://localhost:11434/api/chat` with `"model":"gemma4:e4b-mlx"`

#### Scenario: The LM Studio preset gives a model that speaks the OpenAI dialect

- **WHEN** a chat model is requested for provider `lmstudio` and model `google/gemma-4-e4b`
- **THEN** its first call posts to `http://localhost:1234/v1/chat/completions` with `"model":"google/gemma-4-e4b"`

#### Scenario: An unknown provider is refused

- **WHEN** a chat model is requested for provider `gemini` and model `gemini-2.5-flash`
- **THEN** the result is `ErrorCode.validation` and no model is returned

#### Scenario: A blank model id is refused

- **WHEN** a chat model is requested for provider `ollama` with an empty model id
- **THEN** the result is `ErrorCode.validation`

## ADDED Requirements

### Requirement: Carry a temperature and a response format per call

The system SHALL let a request carry, besides its messages, an optional temperature and an optional response format
made of a name and a JSON schema. A request built from messages alone SHALL carry neither. WHEN either is absent, the
model SHALL leave it out of what it sends; WHEN present, the model SHALL pass it on in its provider's dialect. The
pseudo model SHALL ignore both.

**Source:** FR-INFER-03, FR-INFER-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `#response-handling`, ADR-0033.
In plain words: the engine decides how creative the model may be and what shape it wants back, without knowing which
server answers. A setting that is not given is simply not sent, so an old call site keeps working and a strict server
never sees a `null`.

#### Scenario: A request built from messages alone carries no settings

- **WHEN** a request is built from the single user message `hello`
- **THEN** it has no temperature and no response format

#### Scenario: A temperature and a format reach the server

- **WHEN** a request with temperature `0.2` and the response format `draft` with the schema
  `{"type":"object","properties":{"segments":{"type":"array"}},"required":["segments"]}` is sent to the provider
  `lmstudio`
- **THEN** the posted body has `"temperature":0.2` and a `response_format` whose `json_schema.schema` is that object

#### Scenario: The pseudo model ignores both

- **WHEN** the same request is sent to the pseudo model
- **THEN** it replies `HELLO` with a normal finish

### Requirement: Sanitise a reply before handing it back

WHEN a real provider's reply text is not blank, the system SHALL, before returning it:

- remove every `<think>…</think>`, `<thinking>…</thinking>` and `<reasoning>…</reasoning>` block, matching the tag
  names in any letter case; an opener with no closer removes everything to the end of the text;
- remove a Markdown code fence that wraps the text, with or without a language tag, keeping what was inside;
- remove a leading case-insensitive `json` label when it is immediately followed by a JSON object opener;
- trim leading and trailing whitespace.

The system SHALL return the result even when it is empty, with the finish reason unchanged.

**Source:** FR-INFER-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `#empty-response-ordering`,
`01_Product/12_PROMPT_CATALOG.md#output-contract`, ADR-0013.
In plain words: some models narrate their thinking inline or wrap JSON in a fence even when told not to. The engine
should never have to know that. This strip is the only reasoning control the app has: it sends no `think` or
reasoning-effort parameter, and a separate reasoning channel in the reply is ignored. A reply that was all reasoning
comes back empty and is then treated as an empty translation by the engine's own rule, which is different from a
server that produced nothing at all.

#### Scenario: An inline thinking block is removed

- **WHEN** the server's reply text is `<think>The word is greeting.</think>{"segments":[{"id":"Book.md:0","target":"Привіт"}]}`
- **THEN** the returned text is `{"segments":[{"id":"Book.md:0","target":"Привіт"}]}`

#### Scenario: An unterminated opener strips to the end

- **WHEN** the reply text is `{"segments":[]}<THINKING>and then`
- **THEN** the returned text is `{"segments":[]}`

#### Scenario: A fenced reply is unwrapped

- **WHEN** the reply text is three backticks, `json`, a line feed, `{"segments":[]}`, a line feed and three backticks
- **THEN** the returned text is `{"segments":[]}`

#### Scenario: A bare JSON label is removed

- **WHEN** the reply text is `json{"segments":[]}`
- **THEN** the returned text is `{"segments":[]}`

#### Scenario: A reply that was only reasoning comes back empty

- **WHEN** the reply text is `<think>nothing to say</think>`
- **THEN** the returned text is empty with a normal finish, and the result is not an error

### Requirement: Request provider controls without trusting them as guarantees

WHEN a draft, repair, or structured verification request is sent, the request SHALL carry nullable reasoning control
set to disabled. An Ollama-native client SHALL send this as top-level `"think":false`; other clients SHALL omit it.
IF the native server explicitly rejects that control, THEN the system SHALL retry that one request once with the
control absent and record the downgrade without exposing the response body.

WHEN a request carries a response format and a provider explicitly rejects that format, THEN the system SHALL retry
the request once without the format. The downgrade is not a transport retry and SHALL not consume the retry-policy
attempt budget. Unrelated validation errors SHALL not trigger either downgrade.

**Source:** FR-INFER-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/04_LLM_INTEGRATION.md#response-handling`, ADR-0013.

#### Scenario: An unsupported native thinking control is omitted once

- **WHEN** Ollama rejects a request carrying `"think":false`
- **THEN** the next request has the same messages, temperature and response format but no `think` field

#### Scenario: A structured-output rejection downgrades once

- **WHEN** a provider explicitly rejects a JSON schema response format
- **THEN** the next request has no structured-output field and no retry delay
